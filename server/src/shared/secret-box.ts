import { createCipheriv, createDecipheriv, randomBytes } from "node:crypto";

const IV_BYTES = 12;
const TAG_BYTES = 16;

/** Authenticated encryption (AES-256-GCM) for secrets stored in the database, such as OAuth tokens. */
export class SecretBox {
  private readonly key: Buffer;

  /** [base64Key] is 32 random bytes, base64-encoded (e.g. `openssl rand -base64 32`). */
  constructor(base64Key: string) {
    this.key = Buffer.from(base64Key, "base64");
    if (this.key.length !== 32) throw new Error("SECRETS_KEY must be 32 bytes, base64-encoded");
  }

  seal(plain: string): Buffer {
    const iv = randomBytes(IV_BYTES);
    const cipher = createCipheriv("aes-256-gcm", this.key, iv);
    const body = Buffer.concat([cipher.update(plain, "utf8"), cipher.final()]);
    return Buffer.concat([iv, cipher.getAuthTag(), body]);
  }

  open(sealed: Buffer): string {
    const decipher = createDecipheriv("aes-256-gcm", this.key, sealed.subarray(0, IV_BYTES));
    decipher.setAuthTag(sealed.subarray(IV_BYTES, IV_BYTES + TAG_BYTES));
    return Buffer.concat([decipher.update(sealed.subarray(IV_BYTES + TAG_BYTES)), decipher.final()]).toString("utf8");
  }
}
