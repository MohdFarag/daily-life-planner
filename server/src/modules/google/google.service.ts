import { randomBytes } from "node:crypto";
import type { Config } from "../../shared/config.js";
import { googleEnabled } from "../../shared/config.js";
import type { Db } from "../../shared/db.js";
import { ApiError, badRequest } from "../../shared/errors.js";
import { SecretBox } from "../../shared/secret-box.js";
import { isTimeZone } from "../../shared/zoned-time.js";
import type { SyncService } from "../sync/sync.service.js";
import { type GoogleApi, GoogleApiError, GoogleHttpApi } from "./google.api.js";
import { pullEvents, pushBlocks, pushLocalEvents, pushSessions, syncCalendarList } from "./google.calendar-sync.js";
import { SyncContext } from "./google.context.js";
import { GoogleOAuth, pkce } from "./google.oauth.js";
import { type GoogleAccountRow, googleRepository as repo } from "./google.repository.js";
import { syncLists, syncTasks } from "./google.tasks-sync.js";

const STATE_TTL_SECONDS = 600;
const REFRESH_MARGIN_MS = 60_000;
const SESSIONS_CALENDAR = "Daily Life Planner · Sessions";
const BLOCKS_CALENDAR = "Daily Life Planner · Time blocks";

export interface GoogleStatus {
  enabled: boolean;
  connected: boolean;
  email: string | null;
  lastSyncAt: string | null;
  lastError: string | null;
}

export interface GoogleSyncReport {
  errors: string[];
}

/** For tests: replace the OAuth client and the API with fakes. */
export interface GoogleDeps {
  oauth?: GoogleOAuth;
  api?: (accessToken: () => Promise<string>) => GoogleApi;
}

const notConfigured = () => new ApiError(503, "google_not_configured", "Google integration is not configured on this server");

/**
 * Connecting a Google account and keeping Tasks and Calendar in sync with the user's records.
 * Tokens are encrypted at rest and never leave the server (SEC01). Disabled unless configured.
 */
export class GoogleService {
  readonly enabled: boolean;
  private readonly box?: SecretBox;
  private readonly oauth?: GoogleOAuth;
  /** Runs in progress per user; a second request waits for the running one. */
  private readonly running = new Map<string, Promise<GoogleSyncReport>>();

  constructor(
    private readonly db: Db,
    private readonly sync: SyncService,
    config: Config,
    private readonly deps: GoogleDeps = {},
  ) {
    this.enabled = googleEnabled(config);
    if (this.enabled) {
      this.box = new SecretBox(config.SECRETS_KEY);
      this.oauth =
        deps.oauth ??
        new GoogleOAuth({
          clientId: config.GOOGLE_CLIENT_ID,
          clientSecret: config.GOOGLE_CLIENT_SECRET,
          redirectUri: config.GOOGLE_REDIRECT_URI,
        });
    }
  }

  /** The Google consent page for this user; [state] binds the answer to them (CSRF) with PKCE. */
  async startConnect(userId: string): Promise<{ url: string }> {
    const oauth = this.requireOAuth();
    const state = randomBytes(24).toString("base64url");
    const { verifier, challenge } = pkce();
    await repo.saveState(this.db, state, userId, verifier, STATE_TTL_SECONDS);
    return { url: oauth.authorizationUrl(state, challenge) };
  }

  /** Completes the consent: stores the tokens and returns the user it was for. */
  async completeConnect(state: string, code: string): Promise<string> {
    const oauth = this.requireOAuth();
    const pending = await repo.takeState(this.db, state);
    if (!pending) throw badRequest("invalid_state", "This link has expired; start connecting again from the app");
    const tokens = await oauth.exchange(code, pending.verifier);
    if (!tokens.refreshToken || !tokens.sub) throw badRequest("incomplete_grant", "Google did not grant offline access");
    const box = this.requireBox();
    await repo.saveAccount(this.db, {
      userId: pending.userId,
      googleSub: tokens.sub,
      email: tokens.email ?? null,
      scopes: tokens.scope,
      refreshTokenEnc: box.seal(tokens.refreshToken),
      accessTokenEnc: box.seal(tokens.accessToken),
      accessExpiresAt: tokens.expiresAt,
    });
    return pending.userId;
  }

  async status(userId: string): Promise<GoogleStatus> {
    const account = this.enabled ? await repo.account(this.db, userId) : undefined;
    return {
      enabled: this.enabled,
      connected: Boolean(account),
      email: account?.email ?? null,
      lastSyncAt: account?.lastSyncAt?.toISOString() ?? null,
      lastError: account?.lastError ?? null,
    };
  }

  /**
   * Revokes access at Google and forgets the connection. Calendar events read from Google are
   * removed from devices; tasks and sessions stay, as they belong to the user here too.
   */
  async disconnect(userId: string): Promise<void> {
    const account = await repo.account(this.db, userId);
    if (!account) return;
    const ctx = new SyncContext(userId, this.unavailableApi(), this.db, this.sync, new Date());
    await ctx.load();
    for (const link of await ctx.links("event")) await ctx.attempt("event", () => ctx.remove("events", link.localId));
    // Devices treat the calendar list as "Google is connected".
    await ctx.attempt("calendar list", () => ctx.remove("settings", "calendars"));
    for (const link of await ctx.links("session"))
      await ctx.attempt("session", async () => {
        await ctx.upsert("sessions", link.localId, { googleEventId: null });
      });
    if (this.oauth && this.box) await this.oauth.revoke(this.box.open(account.refreshTokenEnc)).catch(() => undefined);
    await repo.deleteAccount(this.db, userId);
  }

  /** Creates a calendar in the user's Google account (CAL02); it appears in the list after syncing. */
  async createCalendar(userId: string, summary: string): Promise<{ id: string }> {
    const account = await this.requireAccount(userId);
    const ctx = await this.context(account);
    const calendar = await ctx.api.insertCalendar(summary, timeZoneOf(ctx));
    return { id: calendar.id };
  }

  /** One full sync for a user. A request while one is running gets that run's result. */
  syncUser(userId: string): Promise<GoogleSyncReport> {
    const inFlight = this.running.get(userId);
    if (inFlight) return inFlight;
    const run = this.runSync(userId).finally(() => this.running.delete(userId));
    this.running.set(userId, run);
    return run;
  }

  private async runSync(userId: string): Promise<GoogleSyncReport> {
    if (!this.enabled) return { errors: [] };
    const account = await repo.account(this.db, userId);
    if (!account) return { errors: [] };
    try {
      const ctx = await this.context(account);
      const timeZone = timeZoneOf(ctx);
      const prefs = ctx.raw("settings", "prefs")?.doc;
      const reminderMinutes = typeof prefs?.reminderMinutes === "number" ? prefs.reminderMinutes : 10;

      await syncLists(ctx);
      await syncTasks(ctx);
      const app = await this.appCalendars(ctx, account, timeZone);
      const readable = await syncCalendarList(ctx, app);
      await pushSessions(ctx, app.sessions, reminderMinutes);
      await pushBlocks(ctx, app.blocks, timeZone);
      await pushLocalEvents(ctx, timeZone);
      await pullEvents(ctx, readable, timeZone);

      await repo.syncFinished(this.db, userId, ctx.errors.length ? ctx.errors.slice(0, 5).join("; ") : null);
      return { errors: ctx.errors };
    } catch (err) {
      const message =
        err instanceof GoogleApiError && err.status === 400 ? "reconnect_required" : err instanceof Error ? err.message : String(err);
      await repo.syncFinished(this.db, userId, message);
      return { errors: [message] };
    }
  }

  async syncAll(): Promise<number> {
    if (!this.enabled) return 0;
    const users = await repo.connectedUserIds(this.db);
    for (const userId of users) await this.syncUser(userId);
    return users.length;
  }

  private async context(account: GoogleAccountRow): Promise<SyncContext> {
    const token = () => this.accessToken(account.userId);
    const api = this.deps.api ? this.deps.api(token) : new GoogleHttpApi(token);
    const ctx = new SyncContext(account.userId, api, this.db, this.sync, new Date());
    await ctx.load();
    return ctx;
  }

  /** The app's two calendars, created on first sync and recreated if the user deleted them. */
  private async appCalendars(ctx: SyncContext, account: GoogleAccountRow, timeZone: string) {
    const existing = new Set((await ctx.api.calendars()).map((c) => c.id));
    const ensure = async (id: string | null, summary: string) =>
      id && existing.has(id) ? id : (await ctx.api.insertCalendar(summary, timeZone)).id;
    const sessions = await ensure(account.sessionsCalendar, SESSIONS_CALENDAR);
    const blocks = await ensure(account.blocksCalendar, BLOCKS_CALENDAR);
    if (sessions !== account.sessionsCalendar || blocks !== account.blocksCalendar) {
      await repo.saveCalendars(this.db, account.userId, sessions, blocks);
      account.sessionsCalendar = sessions;
      account.blocksCalendar = blocks;
    }
    return { sessions, blocks };
  }

  /** A valid access token, refreshed shortly before it expires. */
  private async accessToken(userId: string): Promise<string> {
    const box = this.requireBox();
    const account = await this.requireAccount(userId);
    if (account.accessTokenEnc && account.accessExpiresAt && account.accessExpiresAt.getTime() - REFRESH_MARGIN_MS > Date.now()) {
      return box.open(account.accessTokenEnc);
    }
    const tokens = await this.requireOAuth().refresh(box.open(account.refreshTokenEnc));
    await repo.saveAccessToken(this.db, userId, box.seal(tokens.accessToken), tokens.expiresAt);
    return tokens.accessToken;
  }

  private async requireAccount(userId: string): Promise<GoogleAccountRow> {
    if (!this.enabled) throw notConfigured();
    const account = await repo.account(this.db, userId);
    if (!account) throw new ApiError(409, "google_not_connected", "Connect a Google account first");
    return account;
  }

  private requireOAuth(): GoogleOAuth {
    if (!this.oauth) throw notConfigured();
    return this.oauth;
  }

  private requireBox(): SecretBox {
    if (!this.box) throw notConfigured();
    return this.box;
  }

  /** For local-only work during disconnect, where Google must not be called. */
  private unavailableApi(): GoogleApi {
    return new Proxy({} as GoogleApi, {
      get: () => () => Promise.reject(new Error("Google is not available while disconnecting")),
    });
  }
}

function timeZoneOf(ctx: SyncContext): string {
  const zone = ctx.raw("settings", "prayer")?.doc.zone;
  return typeof zone === "string" && isTimeZone(zone) ? zone : "UTC";
}
