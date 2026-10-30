import type { FastifyBaseLogger } from "fastify";
import type { GoogleService } from "../modules/google/google.service.js";

/** Keeps every connected account in step with Google between on-demand syncs (SYN02, SYN03). */
export class GoogleSyncJob {
  constructor(
    private readonly google: GoogleService,
    private readonly log: FastifyBaseLogger,
  ) {}

  async run(): Promise<void> {
    const users = await this.google.syncAll();
    if (users > 0) this.log.info({ users }, "Google sync finished");
  }
}
