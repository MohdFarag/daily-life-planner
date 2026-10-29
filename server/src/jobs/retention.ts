import type { FastifyBaseLogger } from "fastify";
import { type Db, withTx } from "../shared/db.js";
import { authRepository } from "../modules/auth/auth.repository.js";
import type { MediaService } from "../modules/media/media.service.js";
import { syncRepository } from "../modules/sync/sync.repository.js";

const BATCH = 500;
const MUTATION_LOG_DAYS = 90;
const STALE_UPLOAD_DAYS = 7;

export interface RetentionReport {
  purgedItems: number;
  oldMutations: number;
  expiredSessions: number;
  staleUploads: number;
}

/** Media ids referenced by an inbox item's attachments (attachments[].mediaId). */
function attachmentMediaIds(doc: Record<string, unknown>): string[] {
  const attachments = Array.isArray(doc.attachments) ? doc.attachments : [];
  return attachments
    .map((a) => (a && typeof a === "object" ? (a as Record<string, unknown>).mediaId : undefined))
    .filter((id): id is string => typeof id === "string");
}

/**
 * Periodic housekeeping: purges trashed inbox items after their retention date (DEL01) — leaving
 * a tombstone and deleting their media (DEL03) — and trims logs, dead sessions and abandoned uploads.
 */
export class RetentionJob {
  constructor(private readonly db: Db, private readonly media: MediaService, private readonly log: FastifyBaseLogger) {}

  async run(now = new Date()): Promise<RetentionReport> {
    let purgedItems = 0;
    for (;;) {
      const due = await syncRepository.trashedItemsDue(this.db, now, BATCH);
      for (const item of due) {
        await withTx(this.db, async (tx) => {
          const version = await syncRepository.nextVersion(tx, item.userId);
          await syncRepository.writeTombstone(tx, item.userId, "items", item.id, version);
        });
        await this.media.removeMany(item.userId, attachmentMediaIds(item.doc));
      }
      purgedItems += due.length;
      if (due.length < BATCH) break;
    }
    const report: RetentionReport = {
      purgedItems,
      oldMutations: await syncRepository.deleteOldMutations(this.db, MUTATION_LOG_DAYS),
      expiredSessions: await authRepository.deleteExpiredSessions(this.db),
      staleUploads: await this.media.removeStaleUploads(STALE_UPLOAD_DAYS),
    };
    this.log.info({ report }, "retention job finished");
    return report;
  }
}
