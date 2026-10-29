import { createHash, randomBytes } from "node:crypto";

/** Opaque bearer tokens: 256 random bits; only a SHA-256 digest is stored. */
export function newToken(): string {
  return randomBytes(32).toString("base64url");
}

export function digest(token: string): Buffer {
  return createHash("sha256").update(token).digest();
}
