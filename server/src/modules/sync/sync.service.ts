import { type Db, type Tx, withTx } from "../../shared/db.js";
import { type Entity, validateFields } from "./entities.js";
import { type FieldConflict, initialFieldVersions, mergeFields } from "./merge.js";
import { type RecordRow, syncRepository as repo } from "./sync.repository.js";

export interface Mutation {
  /** Client-generated idempotency key; retrying a mutation returns its first result. */
  mutationId: string;
  entity: Entity;
  id: string;
  op: "upsert" | "delete";
  /** The record version the client last saw (0 for a record it created). */
  baseVersion: number;
  /** Changed fields only (upsert). */
  fields?: Record<string, unknown>;
}

export interface MutationResult {
  mutationId: string;
  entity: Entity;
  id: string;
  /** applied: everything saved. conflict: some fields kept the server value. rejected: nothing saved. */
  status: "applied" | "conflict" | "rejected";
  version: number;
  conflicts?: FieldConflict[];
  reason?: string;
}

export interface Change {
  entity: Entity;
  id: string;
  version: number;
  deleted: boolean;
  doc: Record<string, unknown>;
}

interface Applied {
  result: MutationResult;
  /** Whether the mutation consumed the version it was offered. */
  usedVersion: boolean;
}

export interface PurgedItem {
  userId: string;
  itemId: string;
  mediaIds: string[];
}

/** Media ids referenced by an inbox item's attachments (attachments[].mediaId). */
function attachmentMediaIds(doc: Record<string, unknown>): string[] {
  const attachments: unknown[] = Array.isArray(doc.attachments) ? doc.attachments : [];
  return attachments
    .map((a) => (a !== null && typeof a === "object" ? (a as Record<string, unknown>).mediaId : undefined))
    .filter((id): id is string => typeof id === "string");
}

const toChange = (r: RecordRow): Change => ({ entity: r.entity, id: r.id, version: r.version, deleted: r.deleted, doc: r.doc });

/**
 * Two-way sync between devices and the server (SYN01, SYN04). Each user has one ordered change log;
 * devices push mutations and pull changes after their last cursor.
 */
export class SyncService {
  constructor(private readonly db: Db) {}

  /** Applies mutations in order within one transaction holding the user's version lock. */
  async push(userId: string, mutations: Mutation[]): Promise<{ results: MutationResult[]; version: number }> {
    return withTx(this.db, async (tx) => {
      let version = await repo.lockVersion(tx, userId);
      const results: MutationResult[] = [];
      for (const mutation of mutations) {
        const previous = await repo.findMutationResult<MutationResult>(tx, userId, mutation.mutationId);
        if (previous) {
          results.push(previous);
          continue;
        }
        const { result, usedVersion } = await this.apply(tx, userId, mutation, version + 1);
        if (usedVersion) version += 1;
        await repo.saveMutationResult(tx, userId, mutation.mutationId, result);
        results.push(result);
      }
      await repo.setVersion(tx, userId, version);
      return { results, version };
    });
  }

  /** Changes after [since], oldest first, plus the cursor to pass next time. */
  async pull(userId: string, since: number, limit: number): Promise<{ changes: Change[]; cursor: number; hasMore: boolean }> {
    const rows = await repo.changesSince(this.db, userId, since, limit + 1);
    const changes = rows.slice(0, limit).map(toChange);
    return { changes, cursor: changes.at(-1)?.version ?? since, hasMore: rows.length > limit };
  }

  /** Every record that has not been deleted, for export (SEC02). */
  async liveRecords(userId: string): Promise<Change[]> {
    return (await repo.liveRecords(this.db, userId)).map(toChange);
  }

  /**
   * Turns inbox items whose trash retention has passed into tombstones (DEL01, DEL03), in batches.
   * Returns, per purged item, the media ids its attachments referenced, so they can be deleted too.
   */
  async purgeExpiredTrash(now: Date, batchSize = 500): Promise<PurgedItem[]> {
    const purged: PurgedItem[] = [];
    for (;;) {
      const due = await repo.trashedItemsDue(this.db, now, batchSize);
      for (const item of due) {
        await withTx(this.db, async (tx) => {
          const version = await repo.nextVersion(tx, item.userId);
          await repo.writeTombstone(tx, item.userId, "items", item.id, version);
        });
        purged.push({ userId: item.userId, itemId: item.id, mediaIds: attachmentMediaIds(item.doc) });
      }
      if (due.length < batchSize) return purged;
    }
  }

  /** Forgets idempotency results older than [days]; clients do not retry that long. */
  async trimMutationLog(days: number): Promise<number> {
    return repo.deleteOldMutations(this.db, days);
  }

  private async apply(tx: Tx, userId: string, m: Mutation, next: number): Promise<Applied> {
    const record = await repo.findRecord(tx, userId, m.entity, m.id);
    const result = (status: MutationResult["status"], version: number, extra: Partial<MutationResult> = {}): MutationResult =>
      ({ mutationId: m.mutationId, entity: m.entity, id: m.id, status, version, ...extra });

    if (m.op === "delete") {
      if (record?.deleted) return { result: result("applied", record.version), usedVersion: false };
      // Content goes; the marker stays so other devices learn of the deletion (DEL03).
      await repo.writeTombstone(tx, userId, m.entity, m.id, next);
      return { result: result("applied", next), usedVersion: true };
    }

    const fields = m.fields ?? {};
    const invalid = validateFields(m.entity, fields);
    if (invalid) return { result: result("rejected", record?.version ?? 0, { reason: invalid }), usedVersion: false };
    // A stale copy never brings a deleted record back (DEL01).
    if (record?.deleted) return { result: result("rejected", record.version, { reason: "deleted" }), usedVersion: false };

    if (!record) {
      await repo.insertRecord(tx, userId, m.entity, m.id, { doc: fields, fieldVersions: initialFieldVersions(fields, next), version: next });
      return { result: result("applied", next), usedVersion: true };
    }

    const merged = mergeFields(record, fields, m.baseVersion, next);
    if (merged.changed) {
      await repo.updateRecord(tx, userId, m.entity, m.id, { doc: merged.doc, fieldVersions: merged.fieldVersions, version: next });
    }
    const version = merged.changed ? next : record.version;
    return merged.conflicts.length
      ? { result: result("conflict", version, { conflicts: merged.conflicts }), usedVersion: merged.changed }
      : { result: result("applied", version), usedVersion: merged.changed };
  }
}
