import type { Queryable } from "../../shared/db.js";

export interface GoogleAccountRow {
  userId: string;
  googleSub: string;
  email: string | null;
  scopes: string;
  refreshTokenEnc: Buffer;
  accessTokenEnc: Buffer | null;
  accessExpiresAt: Date | null;
  sessionsCalendar: string | null;
  blocksCalendar: string | null;
  connectedAt: Date;
  lastSyncAt: Date | null;
  lastError: string | null;
}

export interface LinkRow {
  kind: LinkKind;
  localId: string;
  googleId: string;
  containerId: string;
  etag: string | null;
  localVersion: number;
}

export type LinkKind = "list" | "task" | "session" | "block" | "event";

const ACCOUNT = `user_id AS "userId", google_sub AS "googleSub", email, scopes, refresh_token_enc AS "refreshTokenEnc",
  access_token_enc AS "accessTokenEnc", access_expires_at AS "accessExpiresAt", sessions_calendar AS "sessionsCalendar",
  blocks_calendar AS "blocksCalendar", connected_at AS "connectedAt", last_sync_at AS "lastSyncAt", last_error AS "lastError"`;

const LINK = `kind, local_id AS "localId", google_id AS "googleId", container_id AS "containerId", etag,
  local_version::int AS "localVersion"`;

/** SQL for the Google connection, pending authorizations, object links and sync positions. */
export const googleRepository = {
  // Accounts ---------------------------------------------------------------------------------

  async account(q: Queryable, userId: string): Promise<GoogleAccountRow | undefined> {
    const { rows } = await q.query<GoogleAccountRow>(`SELECT ${ACCOUNT} FROM google_accounts WHERE user_id = $1`, [userId]);
    return rows[0];
  },

  async connectedUserIds(q: Queryable): Promise<string[]> {
    const { rows } = await q.query<{ userId: string }>(`SELECT user_id AS "userId" FROM google_accounts`);
    return rows.map((r) => r.userId);
  },

  async saveAccount(
    q: Queryable,
    a: {
      userId: string;
      googleSub: string;
      email: string | null;
      scopes: string;
      refreshTokenEnc: Buffer;
      accessTokenEnc: Buffer;
      accessExpiresAt: Date;
    },
  ): Promise<void> {
    await q.query(
      `INSERT INTO google_accounts (user_id, google_sub, email, scopes, refresh_token_enc, access_token_enc, access_expires_at)
       VALUES ($1, $2, $3, $4, $5, $6, $7)
       ON CONFLICT (user_id) DO UPDATE SET google_sub = $2, email = $3, scopes = $4, refresh_token_enc = $5,
         access_token_enc = $6, access_expires_at = $7, connected_at = now(), last_error = NULL`,
      [a.userId, a.googleSub, a.email, a.scopes, a.refreshTokenEnc, a.accessTokenEnc, a.accessExpiresAt],
    );
  },

  async saveAccessToken(q: Queryable, userId: string, accessTokenEnc: Buffer, expiresAt: Date): Promise<void> {
    await q.query("UPDATE google_accounts SET access_token_enc = $2, access_expires_at = $3 WHERE user_id = $1", [
      userId,
      accessTokenEnc,
      expiresAt,
    ]);
  },

  async saveCalendars(q: Queryable, userId: string, sessions: string, blocks: string): Promise<void> {
    await q.query("UPDATE google_accounts SET sessions_calendar = $2, blocks_calendar = $3 WHERE user_id = $1", [userId, sessions, blocks]);
  },

  async syncFinished(q: Queryable, userId: string, error: string | null): Promise<void> {
    await q.query(
      "UPDATE google_accounts SET last_error = $2, last_sync_at = CASE WHEN $2::text IS NULL THEN now() ELSE last_sync_at END WHERE user_id = $1",
      [userId, error],
    );
  },

  /** Forgets the connection and everything derived from it. */
  async deleteAccount(q: Queryable, userId: string): Promise<void> {
    await q.query("DELETE FROM google_links WHERE user_id = $1", [userId]);
    await q.query("DELETE FROM google_sync_state WHERE user_id = $1", [userId]);
    await q.query("DELETE FROM google_accounts WHERE user_id = $1", [userId]);
  },

  // OAuth states -----------------------------------------------------------------------------

  async saveState(q: Queryable, state: string, userId: string, verifier: string, ttlSeconds: number): Promise<void> {
    await q.query("DELETE FROM google_oauth_states WHERE expires_at < now()");
    await q.query(
      "INSERT INTO google_oauth_states (state, user_id, code_verifier, expires_at) VALUES ($1, $2, $3, now() + make_interval(secs => $4))",
      [state, userId, verifier, ttlSeconds],
    );
  },

  /** Consumes a state: valid once, and only before it expires. */
  async takeState(q: Queryable, state: string): Promise<{ userId: string; verifier: string } | undefined> {
    const { rows } = await q.query<{ userId: string; verifier: string }>(
      `DELETE FROM google_oauth_states WHERE state = $1 AND expires_at > now()
       RETURNING user_id AS "userId", code_verifier AS "verifier"`,
      [state],
    );
    return rows[0];
  },

  // Links ------------------------------------------------------------------------------------

  async links(q: Queryable, userId: string, kind: LinkKind): Promise<LinkRow[]> {
    const { rows } = await q.query<LinkRow>(`SELECT ${LINK} FROM google_links WHERE user_id = $1 AND kind = $2`, [userId, kind]);
    return rows;
  },

  async saveLink(q: Queryable, userId: string, link: LinkRow): Promise<void> {
    await q.query(
      `INSERT INTO google_links (user_id, kind, local_id, google_id, container_id, etag, local_version)
       VALUES ($1, $2, $3, $4, $5, $6, $7)
       ON CONFLICT (user_id, kind, local_id) DO UPDATE SET google_id = $4, container_id = $5, etag = $6, local_version = $7`,
      [userId, link.kind, link.localId, link.googleId, link.containerId, link.etag, link.localVersion],
    );
  },

  async deleteLink(q: Queryable, userId: string, kind: LinkKind, localId: string): Promise<void> {
    await q.query("DELETE FROM google_links WHERE user_id = $1 AND kind = $2 AND local_id = $3", [userId, kind, localId]);
  },

  // Sync positions ---------------------------------------------------------------------------

  async cursor(q: Queryable, userId: string, scope: string): Promise<string | undefined> {
    const { rows } = await q.query<{ cursor: string }>("SELECT cursor FROM google_sync_state WHERE user_id = $1 AND scope = $2", [
      userId,
      scope,
    ]);
    return rows[0]?.cursor;
  },

  async saveCursor(q: Queryable, userId: string, scope: string, cursor: string | null): Promise<void> {
    if (cursor === null) {
      await q.query("DELETE FROM google_sync_state WHERE user_id = $1 AND scope = $2", [userId, scope]);
      return;
    }
    await q.query(
      `INSERT INTO google_sync_state (user_id, scope, cursor) VALUES ($1, $2, $3)
       ON CONFLICT (user_id, scope) DO UPDATE SET cursor = $3`,
      [userId, scope, cursor],
    );
  },
};
