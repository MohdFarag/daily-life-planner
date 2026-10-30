import type { FastifyBaseLogger } from "fastify";
import type { AuthService } from "../modules/auth/auth.service.js";
import type { MediaService } from "../modules/media/media.service.js";
import type { SyncService } from "../modules/sync/sync.service.js";

const MUTATION_LOG_DAYS = 90;
const STALE_UPLOAD_DAYS = 7;

export interface RetentionReport {
  purgedItems: number;
  oldMutations: number;
  expiredSessions: number;
  staleUploads: number;
}

/**
 * Periodic housekeeping, delegated to the services that own the data: purge trashed inbox items
 * after their retention date along with their media (DEL01, DEL03), trim the idempotency log,
 * and remove dead sessions and abandoned uploads.
 */
export class RetentionJob {
  constructor(
    private readonly sync: SyncService,
    private readonly auth: AuthService,
    private readonly media: MediaService,
    private readonly log: FastifyBaseLogger,
  ) {}

  async run(now = new Date()): Promise<RetentionReport> {
    const purged = await this.sync.purgeExpiredTrash(now);
    for (const item of purged) await this.media.removeMany(item.userId, item.mediaIds);

    const report: RetentionReport = {
      purgedItems: purged.length,
      oldMutations: await this.sync.trimMutationLog(MUTATION_LOG_DAYS),
      expiredSessions: await this.auth.deleteExpiredSessions(),
      staleUploads: await this.media.removeStaleUploads(STALE_UPLOAD_DAYS),
    };
    this.log.info({ report }, "retention job finished");
    return report;
  }
}
