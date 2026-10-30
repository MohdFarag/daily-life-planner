import { one, type Queryable } from "../../shared/db.js";
import type { Entity } from "./entities.js";

export interface RecordRow {
  entity: Entity;
  id: string;
  doc: Record<string, unknown>;
  fieldVersions: Record<string, number>;
  version: number;
  deleted: boolean;
}

export interface RecordWrite {
  doc: Record<string, unknown>;
  fieldVersions: Record<string, number>;
  version: number;
}

export interface TrashedItem {
  userId: string;
  id: string;
  doc: Record<string, unknown>;
}

const RECORD_COLUMNS = `entity, id, doc, field_versions AS "fieldVersions", version, deleted`;

/** SQL for synchronized records, the per-user version counter and the idempotency log. */
export const syncRepository = {
  /** Locks the user's counter for the rest of the transaction, so versions commit in order. */
  async lockVersion(q: Queryable, userId: string): Promise<number> {
    const { rows } = await q.query<{ v: number }>("SELECT sync_version AS v FROM users WHERE id = $1 FOR UPDATE", [userId]);
    return one(rows).v;
  },

  async setVersion(q: Queryable, userId: string, version: number): Promise<void> {
    await q.query("UPDATE users SET sync_version = $2 WHERE id = $1", [userId, version]);
  },

  /** Takes the next version for a server-side change outside a client push. */
  async nextVersion(q: Queryable, userId: string): Promise<number> {
    const { rows } = await q.query<{ v: number }>(
      "UPDATE users SET sync_version = sync_version + 1 WHERE id = $1 RETURNING sync_version AS v",
      [userId],
    );
    return one(rows).v;
  },

  async findMutationResult<T>(q: Queryable, userId: string, mutationId: string): Promise<T | undefined> {
    const { rows } = await q.query<{ result: T }>("SELECT result FROM mutations WHERE user_id = $1 AND mutation_id = $2", [
      userId,
      mutationId,
    ]);
    return rows[0]?.result;
  },

  async saveMutationResult(q: Queryable, userId: string, mutationId: string, result: unknown): Promise<void> {
    await q.query("INSERT INTO mutations (user_id, mutation_id, result) VALUES ($1, $2, $3)", [userId, mutationId, result]);
  },

  async findRecord(q: Queryable, userId: string, entity: Entity, id: string): Promise<RecordRow | undefined> {
    const { rows } = await q.query<RecordRow>(`SELECT ${RECORD_COLUMNS} FROM records WHERE user_id = $1 AND entity = $2 AND id = $3`, [
      userId,
      entity,
      id,
    ]);
    return rows[0];
  },

  async insertRecord(q: Queryable, userId: string, entity: Entity, id: string, w: RecordWrite): Promise<void> {
    await q.query("INSERT INTO records (user_id, entity, id, doc, field_versions, version) VALUES ($1, $2, $3, $4, $5, $6)", [
      userId,
      entity,
      id,
      w.doc,
      w.fieldVersions,
      w.version,
    ]);
  },

  async updateRecord(q: Queryable, userId: string, entity: Entity, id: string, w: RecordWrite): Promise<void> {
    await q.query(
      "UPDATE records SET doc = $4, field_versions = $5, version = $6, updated_at = now() WHERE user_id = $1 AND entity = $2 AND id = $3",
      [userId, entity, id, w.doc, w.fieldVersions, w.version],
    );
  },

  /** Replaces a record with a content-free deletion marker (creating it if needed). */
  async writeTombstone(q: Queryable, userId: string, entity: Entity, id: string, version: number): Promise<void> {
    await q.query(
      `INSERT INTO records (user_id, entity, id, doc, field_versions, version, deleted) VALUES ($1, $2, $3, '{}', '{}', $4, true)
       ON CONFLICT (user_id, entity, id) DO UPDATE SET doc = '{}', field_versions = '{}', version = $4, deleted = true, updated_at = now()`,
      [userId, entity, id, version],
    );
  },

  async changesSince(q: Queryable, userId: string, since: number, limit: number): Promise<RecordRow[]> {
    const { rows } = await q.query<RecordRow>(
      `SELECT ${RECORD_COLUMNS} FROM records WHERE user_id = $1 AND version > $2 ORDER BY version LIMIT $3`,
      [userId, since, limit],
    );
    return rows;
  },

  async liveRecords(q: Queryable, userId: string): Promise<RecordRow[]> {
    const { rows } = await q.query<RecordRow>(
      `SELECT ${RECORD_COLUMNS} FROM records WHERE user_id = $1 AND NOT deleted ORDER BY entity, id`,
      [userId],
    );
    return rows;
  },

  /** Inbox items in trash whose retention date has passed, across all users. */
  async trashedItemsDue(q: Queryable, now: Date, limit: number): Promise<TrashedItem[]> {
    const { rows } = await q.query<TrashedItem>(
      `SELECT user_id AS "userId", id, doc FROM records
       WHERE entity = 'items' AND NOT deleted AND doc ->> 'state' = 'TRASHED'
         AND (doc ->> 'purgeAt')::timestamptz <= $1
       LIMIT $2`,
      [now, limit],
    );
    return rows;
  },

  async deleteOldMutations(q: Queryable, olderThanDays: number): Promise<number> {
    const { rowCount } = await q.query("DELETE FROM mutations WHERE created_at < now() - make_interval(days => $1)", [olderThanDays]);
    return rowCount ?? 0;
  },
};
