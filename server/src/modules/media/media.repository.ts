import { one, type Queryable } from "../../shared/db.js";

export interface MediaRow {
  id: string;
  userId: string;
  filename: string | null;
  mimeType: string;
  sizeBytes: number;
  sha256: string;
  receivedBytes: number;
  status: "uploading" | "complete";
  createdAt: Date;
}

const COLUMNS = `id, user_id AS "userId", filename, mime_type AS "mimeType", size_bytes AS "sizeBytes", sha256,
  received_bytes AS "receivedBytes", status, created_at AS "createdAt"`;

/** SQL for uploaded media metadata (the bytes live in MediaStorage). */
export const mediaRepository = {
  async insert(
    q: Queryable,
    m: { userId: string; filename: string | null; mimeType: string; sizeBytes: number; sha256: string },
  ): Promise<MediaRow> {
    const { rows } = await q.query<MediaRow>(
      `INSERT INTO media (user_id, filename, mime_type, size_bytes, sha256) VALUES ($1, $2, $3, $4, $5) RETURNING ${COLUMNS}`,
      [m.userId, m.filename, m.mimeType, m.sizeBytes, m.sha256],
    );
    return one(rows);
  },

  async find(q: Queryable, userId: string, id: string, forUpdate = false): Promise<MediaRow | undefined> {
    const { rows } = await q.query<MediaRow>(
      `SELECT ${COLUMNS} FROM media WHERE id = $1 AND user_id = $2${forUpdate ? " FOR UPDATE" : ""}`,
      [id, userId],
    );
    return rows[0];
  },

  async usedBytes(q: Queryable, userId: string): Promise<number> {
    const { rows } = await q.query<{ total: number }>(
      "SELECT COALESCE(SUM(size_bytes), 0)::bigint AS total FROM media WHERE user_id = $1",
      [userId],
    );
    return one(rows).total;
  },

  async setReceived(q: Queryable, id: string, receivedBytes: number): Promise<MediaRow> {
    const { rows } = await q.query<MediaRow>(`UPDATE media SET received_bytes = $2 WHERE id = $1 RETURNING ${COLUMNS}`, [
      id,
      receivedBytes,
    ]);
    return one(rows);
  },

  async markComplete(q: Queryable, id: string, receivedBytes: number): Promise<MediaRow> {
    const { rows } = await q.query<MediaRow>(
      `UPDATE media SET received_bytes = $2, status = 'complete', completed_at = now() WHERE id = $1 RETURNING ${COLUMNS}`,
      [id, receivedBytes],
    );
    return one(rows);
  },

  async delete(q: Queryable, userId: string, id: string): Promise<boolean> {
    const { rowCount } = await q.query("DELETE FROM media WHERE id = $1 AND user_id = $2", [id, userId]);
    return (rowCount ?? 0) > 0;
  },

  async listForUser(q: Queryable, userId: string): Promise<MediaRow[]> {
    const { rows } = await q.query<MediaRow>(`SELECT ${COLUMNS} FROM media WHERE user_id = $1 ORDER BY created_at`, [userId]);
    return rows;
  },

  /** Uploads abandoned before completion. */
  async staleUploads(q: Queryable, olderThanDays: number): Promise<MediaRow[]> {
    const { rows } = await q.query<MediaRow>(
      `SELECT ${COLUMNS} FROM media WHERE status = 'uploading' AND created_at < now() - make_interval(days => $1)`,
      [olderThanDays],
    );
    return rows;
  },
};
