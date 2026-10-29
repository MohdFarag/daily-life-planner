import type { Readable } from "node:stream";
import type { Config } from "../../shared/config.js";
import { type Db, type Queryable, withTx } from "../../shared/db.js";
import { ApiError, conflict, notFound } from "../../shared/errors.js";
import { authRepository } from "../auth/auth.repository.js";
import { type MediaRow, mediaRepository as repo } from "./media.repository.js";
import { type MediaStorage, OffsetMismatch } from "./storage.js";

export interface MediaInfo {
  id: string;
  filename: string | null;
  mimeType: string;
  sizeBytes: number;
  sha256: string;
  receivedBytes: number;
  status: "uploading" | "complete";
  createdAt: string;
}

export interface NewUpload {
  filename?: string;
  mimeType: string;
  sizeBytes: number;
  sha256: string;
}

const toInfo = (m: MediaRow): MediaInfo => ({
  id: m.id, filename: m.filename, mimeType: m.mimeType, sizeBytes: m.sizeBytes, sha256: m.sha256,
  receivedBytes: m.receivedBytes, status: m.status, createdAt: m.createdAt.toISOString(),
});

const storageKey = (userId: string, id: string) => `${userId}/${id}`;

/**
 * Resumable media uploads (CAP01, CAP02). A client declares size and SHA-256, sends chunks at the
 * offset the server reports, and the last chunk is verified before the upload counts as complete.
 */
export class MediaService {
  constructor(private readonly db: Db, private readonly storage: MediaStorage, private readonly config: Config) {}

  async create(userId: string, upload: NewUpload): Promise<MediaInfo> {
    if (upload.sizeBytes > this.config.MAX_FILE_BYTES) {
      throw new ApiError(413, "file_too_large", `Files are limited to ${this.config.MAX_FILE_BYTES} bytes`);
    }
    return withTx(this.db, async (tx) => {
      await authRepository.lockUser(tx, userId); // two concurrent uploads must not both pass the quota check
      const used = await repo.usedBytes(tx, userId);
      if (used + upload.sizeBytes > this.config.USER_QUOTA_BYTES) throw new ApiError(413, "quota_exceeded", "Storage quota exceeded");
      const row = await repo.insert(tx, {
        userId, filename: upload.filename ?? null, mimeType: upload.mimeType, sizeBytes: upload.sizeBytes, sha256: upload.sha256.toLowerCase(),
      });
      return toInfo(row);
    });
  }

  async get(userId: string, id: string): Promise<MediaInfo> {
    const row = await repo.find(this.db, userId, id);
    if (!row) throw notFound("Media");
    return toInfo(row);
  }

  async appendChunk(userId: string, id: string, offset: number, chunk: Buffer): Promise<MediaInfo> {
    // Failures that reset the upload are returned, not thrown, so the reset commits with the transaction.
    const outcome = await withTx(this.db, async (tx): Promise<MediaRow | "reset" | "checksum_mismatch"> => {
      const media = await repo.find(tx, userId, id, true);
      if (!media) throw notFound("Media");
      if (media.status === "complete") throw conflict("already_complete", "Upload is already complete");
      if (offset !== media.receivedBytes) throw new ApiError(409, "offset_mismatch", `Expected offset ${media.receivedBytes}`);
      if (offset + chunk.length > media.sizeBytes) throw new ApiError(400, "too_many_bytes", "Chunk exceeds the declared size");

      const received = await this.writeChunk(media, offset, chunk);
      if (received === "reset") {
        await this.restart(tx, media);
        return "reset";
      }
      if (received < media.sizeBytes) return repo.setReceived(tx, id, received);

      if ((await this.storage.sha256(storageKey(userId, id))) !== media.sha256) {
        await this.restart(tx, media);
        return "checksum_mismatch";
      }
      return repo.markComplete(tx, id, received);
    });
    if (outcome === "reset") throw new ApiError(409, "offset_mismatch", "Upload state was reset; start again from offset 0");
    if (outcome === "checksum_mismatch") {
      throw new ApiError(422, "checksum_mismatch", "Uploaded bytes do not match the declared SHA-256; upload again from offset 0");
    }
    return toInfo(outcome);
  }

  async open(userId: string, id: string): Promise<{ info: MediaInfo; stream: Readable }> {
    const info = await this.get(userId, id);
    if (info.status !== "complete") throw conflict("upload_incomplete", "Upload is not complete");
    return { info, stream: this.storage.read(storageKey(userId, id)) };
  }

  async remove(userId: string, id: string): Promise<void> {
    if (!(await repo.delete(this.db, userId, id))) throw notFound("Media");
    await this.storage.remove(storageKey(userId, id));
  }

  /** Deletes any of [ids] owned by the user; unknown ids are ignored (used by trash purge). */
  async removeMany(userId: string, ids: string[]): Promise<void> {
    for (const id of ids) {
      if (await repo.delete(this.db, userId, id)) await this.storage.remove(storageKey(userId, id));
    }
  }

  async removeAllBytesForUser(userId: string): Promise<void> {
    for (const m of await repo.listForUser(this.db, userId)) await this.storage.remove(storageKey(userId, m.id));
  }

  async list(userId: string): Promise<MediaInfo[]> {
    return (await repo.listForUser(this.db, userId)).map(toInfo);
  }

  /** Removes uploads never completed within [days]; returns how many. */
  async removeStaleUploads(days: number): Promise<number> {
    const stale = await repo.staleUploads(this.db, days);
    for (const m of stale) await this.removeMany(m.userId, [m.id]);
    return stale.length;
  }

  /** Appends the chunk; "reset" when stored bytes and the database disagree (e.g. after a crash mid-write). */
  private async writeChunk(media: MediaRow, offset: number, chunk: Buffer): Promise<number | "reset"> {
    try {
      return await this.storage.append(storageKey(media.userId, media.id), offset, chunk);
    } catch (err) {
      if (err instanceof OffsetMismatch) return "reset";
      throw err;
    }
  }

  private async restart(tx: Queryable, media: MediaRow): Promise<void> {
    await this.storage.remove(storageKey(media.userId, media.id));
    await repo.setReceived(tx, media.id, 0);
  }
}
