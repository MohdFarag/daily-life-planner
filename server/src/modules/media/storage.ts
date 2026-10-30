import { createHash } from "node:crypto";
import { createReadStream } from "node:fs";
import { mkdir, open, rm, stat } from "node:fs/promises";
import path from "node:path";
import type { Readable } from "node:stream";

/** Where uploaded bytes live. Local disk now; an object-store driver can implement the same contract. */
export interface MediaStorage {
  /** Appends [chunk] at [offset]; fails unless the stored object currently has exactly [offset] bytes. */
  append(key: string, offset: number, chunk: Buffer): Promise<number>;
  sha256(key: string): Promise<string>;
  read(key: string): Readable;
  remove(key: string): Promise<void>;
}

export class OffsetMismatch extends Error {
  constructor(readonly actual: number) {
    super(`Stored size is ${actual}`);
  }
}

export class LocalDiskStorage implements MediaStorage {
  constructor(private readonly root: string) {}

  private file(key: string) {
    if (!/^[0-9a-f-]+\/[0-9a-f-]+$/.test(key)) throw new Error("Invalid storage key");
    const root = path.resolve(this.root);
    const file = path.resolve(root, key);
    if (!file.startsWith(root + path.sep)) throw new Error("Invalid storage key");
    return file;
  }

  async append(key: string, offset: number, chunk: Buffer): Promise<number> {
    const file = this.file(key);
    await mkdir(path.dirname(file), { recursive: true });
    const handle = await open(file, "a");
    try {
      const { size } = await handle.stat();
      if (size !== offset) throw new OffsetMismatch(size);
      await handle.write(chunk);
      await handle.sync(); // durable before acknowledging the chunk
      return size + chunk.length;
    } finally {
      await handle.close();
    }
  }

  async sha256(key: string): Promise<string> {
    const hash = createHash("sha256");
    for await (const part of createReadStream(this.file(key))) hash.update(part as Buffer);
    return hash.digest("hex");
  }

  read(key: string): Readable {
    return createReadStream(this.file(key));
  }

  async remove(key: string): Promise<void> {
    await rm(this.file(key), { force: true });
  }

  async exists(key: string): Promise<boolean> {
    return stat(this.file(key)).then(
      () => true,
      () => false,
    );
  }
}
