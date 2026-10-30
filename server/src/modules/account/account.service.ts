import type { Db } from "../../shared/db.js";
import type { AuthService, Principal } from "../auth/auth.service.js";
import type { MediaService } from "../media/media.service.js";
import type { SyncService } from "../sync/sync.service.js";
import { usersRepository } from "../users/users.repository.js";

/** Data portability and account deletion (SEC02, SEC03). */
export class AccountService {
  constructor(
    private readonly db: Db,
    private readonly auth: AuthService,
    private readonly sync: SyncService,
    private readonly media: MediaService,
  ) {}

  /** Everything stored for the user in a documented, restorable format. Media bytes are listed, not embedded. */
  async export(principal: Principal) {
    const [user, records, media] = await Promise.all([
      this.auth.me(principal),
      this.sync.liveRecords(principal.userId),
      this.media.list(principal.userId),
    ]);
    return {
      format: "daily-life-planner/1" as const,
      exportedAt: new Date().toISOString(),
      user,
      records: records.map(({ entity, id, version, doc }) => ({ entity, id, version, doc })),
      media,
      notIncluded: "Media bytes are downloadable per item from /v1/media/{id}/content. Google data is not copied here.",
    };
  }

  /** Deletes the account and all of its data after re-checking the password. */
  async delete(principal: Principal, password: string): Promise<void> {
    await this.auth.verifyCurrentPassword(principal, password);
    await this.media.removeAllBytesForUser(principal.userId);
    await usersRepository.delete(this.db, principal.userId); // cascades to sessions, records and media rows
  }
}
