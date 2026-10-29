import type { Db } from "../../shared/db.js";
import { authRepository } from "../auth/auth.repository.js";
import type { AuthService, Principal } from "../auth/auth.service.js";
import type { MediaService } from "../media/media.service.js";
import { syncRepository } from "../sync/sync.repository.js";

/** Data portability and account deletion (SEC02, SEC03). */
export class AccountService {
  constructor(private readonly db: Db, private readonly auth: AuthService, private readonly media: MediaService) {}

  /** Everything stored for the user in a documented, restorable format. Media bytes are listed, not embedded. */
  async export(principal: Principal) {
    const [user, records, media] = await Promise.all([
      this.auth.me(principal),
      syncRepository.liveRecords(this.db, principal.userId),
      this.media.list(principal.userId),
    ]);
    return {
      format: "daily-life-planner/1" as const,
      exportedAt: new Date().toISOString(),
      user,
      records: records.map((r) => ({ entity: r.entity, id: r.id, version: r.version, doc: r.doc })),
      media,
      notIncluded: "Media bytes are downloadable per item from /v1/media/{id}/content. Google data is not copied here.",
    };
  }

  /** Deletes the account and all of its data after re-checking the password. */
  async delete(principal: Principal, password: string): Promise<void> {
    await this.auth.verifyCurrentPassword(principal, password);
    await this.media.removeAllBytesForUser(principal.userId);
    await authRepository.deleteUser(this.db, principal.userId); // cascades to sessions, records, media rows
  }
}
