import type { Config } from "../../shared/config.js";
import { type Db, type Queryable, withTx } from "../../shared/db.js";
import { conflict, forbidden, unauthorized } from "../../shared/errors.js";
import { type UserRow, usersRepository as users } from "../users/users.repository.js";
import { authRepository as repo, type TokenHashes } from "./auth.repository.js";
import { hashPassword, verifyPassword } from "./passwords.js";
import { digest, newToken } from "../../shared/tokens.js";

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  refreshExpiresIn: number;
}

/** The authenticated caller of a request. */
export interface Principal {
  userId: string;
  sessionId: string;
}

export interface PublicUser {
  id: string;
  email: string;
  displayName: string | null;
  locale: string;
  createdAt: string;
}

export interface DeviceSession {
  id: string;
  deviceName: string | null;
  createdAt: string;
  lastUsedAt: string;
  current: boolean;
}

const UNIQUE_VIOLATION = "23505";

const toPublicUser = (u: UserRow): PublicUser => ({
  id: u.id,
  email: u.email,
  displayName: u.displayName,
  locale: u.locale,
  createdAt: u.createdAt.toISOString(),
});

/**
 * Accounts and device sessions. Access tokens are short-lived; refresh tokens rotate on every use,
 * and presenting a rotated refresh token again revokes the session (theft detection).
 */
export class AuthService {
  // Checked when the email is unknown, so response timing does not reveal which accounts exist.
  private dummyHash?: Promise<string>;

  constructor(
    private readonly db: Db,
    private readonly config: Config,
  ) {}

  async register(input: { email: string; password: string; displayName?: string; locale: string; deviceName?: string }) {
    const passwordHash = await hashPassword(input.password);
    try {
      return await withTx(this.db, async (tx) => {
        if (await users.findByEmail(tx, input.email)) throw this.emailTaken();
        const user = await users.insert(tx, {
          email: input.email,
          passwordHash,
          displayName: input.displayName ?? null,
          locale: input.locale,
        });
        const tokens = await this.startSession(tx, user.id, input.deviceName);
        return { user: toPublicUser(user), tokens };
      });
    } catch (err) {
      // Two simultaneous sign-ups with one email: the unique index decides.
      if ((err as { code?: string }).code === UNIQUE_VIOLATION) throw this.emailTaken();
      throw err;
    }
  }

  async login(input: { email: string; password: string; deviceName?: string }) {
    const user = await users.findByEmail(this.db, input.email);
    const valid = user
      ? await verifyPassword(input.password, user.passwordHash)
      : await verifyPassword(input.password, await (this.dummyHash ??= hashPassword("unused"))).then(() => false);
    if (!user || !valid) throw unauthorized("Email or password is incorrect");
    const tokens = await this.startSession(this.db, user.id, input.deviceName);
    return { user: toPublicUser(user), tokens };
  }

  async refresh(refreshToken: string): Promise<TokenPair> {
    const presented = digest(refreshToken);
    const outcome = await withTx(this.db, async (tx): Promise<TokenPair | "reused" | "invalid"> => {
      const session = await repo.lockSessionByRefreshHash(tx, presented);
      if (!session) {
        const reusedSessionId = await repo.findSessionIdByRetiredHash(tx, presented);
        if (!reusedSessionId) return "invalid";
        // Revocation must commit, so this branch returns instead of throwing inside the transaction.
        await repo.revokeSession(tx, reusedSessionId);
        return "reused";
      }
      if (session.revokedAt || session.refreshExpired) return "invalid";

      const { pair, hashes } = this.issueTokens();
      await repo.retireRefreshHash(tx, presented, session.id);
      await repo.rotateTokens(tx, session.id, hashes);
      return pair;
    });
    if (outcome === "reused") throw unauthorized("Refresh token was already used; the session has been signed out", "token_reused");
    if (outcome === "invalid") throw unauthorized("Refresh token is invalid or expired");
    return outcome;
  }

  async authenticate(accessToken: string): Promise<Principal> {
    const session = await repo.touchSessionByAccessHash(this.db, digest(accessToken));
    if (!session) throw unauthorized("Access token is invalid or expired");
    return { userId: session.userId, sessionId: session.id };
  }

  async logout(principal: Principal): Promise<void> {
    await repo.revokeSession(this.db, principal.sessionId);
  }

  async listSessions(principal: Principal): Promise<DeviceSession[]> {
    const sessions = await repo.listActiveSessions(this.db, principal.userId);
    return sessions.map((s) => ({
      id: s.id,
      deviceName: s.deviceName,
      createdAt: s.createdAt.toISOString(),
      lastUsedAt: s.lastUsedAt.toISOString(),
      current: s.id === principal.sessionId,
    }));
  }

  /** Signs out one of the caller's devices. Returns false if it was not an active session of theirs. */
  async revokeSession(principal: Principal, sessionId: string): Promise<boolean> {
    return repo.revokeSession(this.db, sessionId, principal.userId);
  }

  async me(principal: Principal): Promise<PublicUser> {
    const user = await users.findById(this.db, principal.userId);
    if (!user) throw unauthorized();
    return toPublicUser(user);
  }

  /** Housekeeping: removes sessions that can no longer be used. */
  async deleteExpiredSessions(): Promise<number> {
    return repo.deleteExpiredSessions(this.db);
  }

  async verifyCurrentPassword(principal: Principal, password: string): Promise<void> {
    const user = await users.findById(this.db, principal.userId);
    if (!user || !(await verifyPassword(password, user.passwordHash))) {
      throw forbidden("wrong_password", "Password is incorrect");
    }
  }

  private async startSession(q: Queryable, userId: string, deviceName?: string) {
    const { pair, hashes } = this.issueTokens();
    await repo.insertSession(q, userId, deviceName ?? null, hashes);
    return pair;
  }

  private issueTokens(): { pair: TokenPair; hashes: TokenHashes } {
    const pair: TokenPair = {
      accessToken: newToken(),
      refreshToken: newToken(),
      tokenType: "Bearer",
      expiresIn: this.config.ACCESS_TOKEN_TTL_SECONDS,
      refreshExpiresIn: this.config.REFRESH_TOKEN_TTL_DAYS * 86_400,
    };
    const hashes: TokenHashes = {
      accessHash: digest(pair.accessToken),
      accessTtlSeconds: this.config.ACCESS_TOKEN_TTL_SECONDS,
      refreshHash: digest(pair.refreshToken),
      refreshTtlDays: this.config.REFRESH_TOKEN_TTL_DAYS,
    };
    return { pair, hashes };
  }

  private emailTaken() {
    return conflict("email_taken", "An account with this email already exists");
  }
}
