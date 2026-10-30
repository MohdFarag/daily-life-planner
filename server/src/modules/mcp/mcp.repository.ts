import { one, type Queryable } from "../../shared/db.js";

export interface McpTokenRow {
  id: string;
  name: string;
  createdAt: Date;
  expiresAt: Date;
  lastUsedAt: Date | null;
}

const COLUMNS = `id, name, created_at AS "createdAt", expires_at AS "expiresAt", last_used_at AS "lastUsedAt"`;

/** SQL for MCP access tokens. */
export const mcpRepository = {
  async insert(q: Queryable, userId: string, name: string, tokenHash: Buffer, ttlDays: number): Promise<McpTokenRow> {
    const { rows } = await q.query<McpTokenRow>(
      `INSERT INTO mcp_tokens (user_id, name, token_hash, expires_at)
       VALUES ($1, $2, $3, now() + make_interval(days => $4)) RETURNING ${COLUMNS}`,
      [userId, name, tokenHash, ttlDays],
    );
    return one(rows);
  },

  async listActive(q: Queryable, userId: string): Promise<McpTokenRow[]> {
    const { rows } = await q.query<McpTokenRow>(
      `SELECT ${COLUMNS} FROM mcp_tokens
       WHERE user_id = $1 AND revoked_at IS NULL AND expires_at > now() ORDER BY created_at`,
      [userId],
    );
    return rows;
  },

  /** The owner of a live token, recording its use. */
  async touch(q: Queryable, tokenHash: Buffer): Promise<{ userId: string; tokenId: string } | undefined> {
    const { rows } = await q.query<{ userId: string; tokenId: string }>(
      `UPDATE mcp_tokens SET last_used_at = now()
       WHERE token_hash = $1 AND revoked_at IS NULL AND expires_at > now()
       RETURNING user_id AS "userId", id AS "tokenId"`,
      [tokenHash],
    );
    return rows[0];
  },

  async revoke(q: Queryable, userId: string, id: string): Promise<boolean> {
    const { rowCount } = await q.query("UPDATE mcp_tokens SET revoked_at = now() WHERE id = $1 AND user_id = $2 AND revoked_at IS NULL", [
      id,
      userId,
    ]);
    return (rowCount ?? 0) > 0;
  },
};
