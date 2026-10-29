import { one, type Queryable } from "../../shared/db.js";

export interface UserRow {
  id: string;
  email: string;
  passwordHash: string;
  displayName: string | null;
  locale: string;
  createdAt: Date;
}

export interface SessionRow {
  id: string;
  userId: string;
  deviceName: string | null;
  createdAt: Date;
  lastUsedAt: Date;
  revokedAt: Date | null;
  refreshExpired: boolean;
}

export interface TokenHashes {
  accessHash: Buffer;
  accessTtlSeconds: number;
  refreshHash: Buffer;
  refreshTtlDays: number;
}

const USER_COLUMNS = `id, email, password_hash AS "passwordHash", display_name AS "displayName", locale, created_at AS "createdAt"`;
const SESSION_COLUMNS = `id, user_id AS "userId", device_name AS "deviceName", created_at AS "createdAt",
  last_used_at AS "lastUsedAt", revoked_at AS "revokedAt", refresh_expires_at < now() AS "refreshExpired"`;

/** SQL for users, sessions and rotated refresh tokens. */
export const authRepository = {
  async findUserByEmail(q: Queryable, email: string): Promise<UserRow | undefined> {
    const { rows } = await q.query<UserRow>(`SELECT ${USER_COLUMNS} FROM users WHERE lower(email) = lower($1)`, [email]);
    return rows[0];
  },

  async findUserById(q: Queryable, id: string): Promise<UserRow | undefined> {
    const { rows } = await q.query<UserRow>(`SELECT ${USER_COLUMNS} FROM users WHERE id = $1`, [id]);
    return rows[0];
  },

  async insertUser(q: Queryable, user: { email: string; passwordHash: string; displayName: string | null; locale: string }): Promise<UserRow> {
    const { rows } = await q.query<UserRow>(
      `INSERT INTO users (email, password_hash, display_name, locale) VALUES ($1, $2, $3, $4) RETURNING ${USER_COLUMNS}`,
      [user.email, user.passwordHash, user.displayName, user.locale]);
    return one(rows);
  },

  /** Row-locks the user for the rest of the transaction, serializing per-user checks (e.g. quota). */
  async lockUser(q: Queryable, id: string): Promise<void> {
    await q.query("SELECT 1 FROM users WHERE id = $1 FOR UPDATE", [id]);
  },

  async deleteUser(q: Queryable, id: string): Promise<void> {
    await q.query("DELETE FROM users WHERE id = $1", [id]);
  },

  async insertSession(q: Queryable, userId: string, deviceName: string | null, t: TokenHashes): Promise<void> {
    await q.query(
      `INSERT INTO sessions (user_id, device_name, access_hash, access_expires_at, refresh_hash, refresh_expires_at)
       VALUES ($1, $2, $3, now() + make_interval(secs => $4), $5, now() + make_interval(days => $6))`,
      [userId, deviceName, t.accessHash, t.accessTtlSeconds, t.refreshHash, t.refreshTtlDays]);
  },

  async lockSessionByRefreshHash(q: Queryable, refreshHash: Buffer): Promise<SessionRow | undefined> {
    const { rows } = await q.query<SessionRow>(`SELECT ${SESSION_COLUMNS} FROM sessions WHERE refresh_hash = $1 FOR UPDATE`, [refreshHash]);
    return rows[0];
  },

  async rotateTokens(q: Queryable, sessionId: string, t: TokenHashes): Promise<void> {
    await q.query(
      `UPDATE sessions SET access_hash = $2, access_expires_at = now() + make_interval(secs => $3),
         refresh_hash = $4, refresh_expires_at = now() + make_interval(days => $5), last_used_at = now()
       WHERE id = $1`,
      [sessionId, t.accessHash, t.accessTtlSeconds, t.refreshHash, t.refreshTtlDays]);
  },

  async retireRefreshHash(q: Queryable, refreshHash: Buffer, sessionId: string): Promise<void> {
    await q.query("INSERT INTO retired_refresh_tokens (refresh_hash, session_id) VALUES ($1, $2)", [refreshHash, sessionId]);
  },

  async findSessionIdByRetiredHash(q: Queryable, refreshHash: Buffer): Promise<string | undefined> {
    const { rows } = await q.query<{ sessionId: string }>(
      `SELECT session_id AS "sessionId" FROM retired_refresh_tokens WHERE refresh_hash = $1`, [refreshHash]);
    return rows[0]?.sessionId;
  },

  /** Returns the active session for an access token and records its use. */
  async touchSessionByAccessHash(q: Queryable, accessHash: Buffer): Promise<{ id: string; userId: string } | undefined> {
    const { rows } = await q.query<{ id: string; userId: string }>(
      `UPDATE sessions SET last_used_at = now()
       WHERE access_hash = $1 AND access_expires_at > now() AND revoked_at IS NULL
       RETURNING id, user_id AS "userId"`,
      [accessHash]);
    return rows[0];
  },

  async revokeSession(q: Queryable, sessionId: string, userId?: string): Promise<boolean> {
    const { rowCount } = await q.query(
      "UPDATE sessions SET revoked_at = now() WHERE id = $1 AND ($2::uuid IS NULL OR user_id = $2) AND revoked_at IS NULL",
      [sessionId, userId ?? null]);
    return (rowCount ?? 0) > 0;
  },

  async listActiveSessions(q: Queryable, userId: string): Promise<SessionRow[]> {
    const { rows } = await q.query<SessionRow>(
      `SELECT ${SESSION_COLUMNS} FROM sessions
       WHERE user_id = $1 AND revoked_at IS NULL AND refresh_expires_at > now() ORDER BY last_used_at DESC`,
      [userId]);
    return rows;
  },

  /** Housekeeping: sessions that can no longer be refreshed. */
  async deleteExpiredSessions(q: Queryable): Promise<number> {
    const { rowCount } = await q.query("DELETE FROM sessions WHERE refresh_expires_at < now() - interval '7 days' OR revoked_at < now() - interval '7 days'");
    return rowCount ?? 0;
  },
};
