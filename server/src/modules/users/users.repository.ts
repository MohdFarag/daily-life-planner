import { one, type Queryable } from "../../shared/db.js";

export interface UserRow {
  id: string;
  email: string;
  passwordHash: string;
  displayName: string | null;
  locale: string;
  createdAt: Date;
}

const USER_COLUMNS = `id, email, password_hash AS "passwordHash", display_name AS "displayName", locale, created_at AS "createdAt"`;

/**
 * SQL for user rows. The user is the root every other table hangs off, so this is the one
 * repository other modules may use (for example to lock a user's row for a per-user check).
 */
export const usersRepository = {
  async findByEmail(q: Queryable, email: string): Promise<UserRow | undefined> {
    const { rows } = await q.query<UserRow>(`SELECT ${USER_COLUMNS} FROM users WHERE lower(email) = lower($1)`, [email]);
    return rows[0];
  },

  async findById(q: Queryable, id: string): Promise<UserRow | undefined> {
    const { rows } = await q.query<UserRow>(`SELECT ${USER_COLUMNS} FROM users WHERE id = $1`, [id]);
    return rows[0];
  },

  async insert(q: Queryable, user: { email: string; passwordHash: string; displayName: string | null; locale: string }): Promise<UserRow> {
    const { rows } = await q.query<UserRow>(
      `INSERT INTO users (email, password_hash, display_name, locale) VALUES ($1, $2, $3, $4) RETURNING ${USER_COLUMNS}`,
      [user.email, user.passwordHash, user.displayName, user.locale],
    );
    return one(rows);
  },

  /** Row-locks the user for the rest of the transaction, serializing per-user checks (e.g. quota). */
  async lock(q: Queryable, id: string): Promise<void> {
    await q.query("SELECT 1 FROM users WHERE id = $1 FOR UPDATE", [id]);
  },

  async delete(q: Queryable, id: string): Promise<void> {
    await q.query("DELETE FROM users WHERE id = $1", [id]);
  },
};
