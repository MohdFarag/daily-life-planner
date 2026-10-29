import { randomBytes, scrypt as scryptCb, timingSafeEqual } from "node:crypto";
import { promisify } from "node:util";

const scrypt = promisify(scryptCb) as (pw: string, salt: Buffer, len: number, opts: object) => Promise<Buffer>;

// OWASP-recommended scrypt cost (N=2^17, r=8, p=1); needs ~128 MiB, so maxmem is raised.
const PARAMS = { N: 2 ** 17, r: 8, p: 1, maxmem: 256 * 1024 * 1024 };
const KEY_LEN = 32;

/** Encoded as `scrypt$N$r$p$salt$hash` (base64) so parameters can change later. */
export async function hashPassword(password: string, params = PARAMS): Promise<string> {
  const salt = randomBytes(16);
  const hash = await scrypt(password, salt, KEY_LEN, params);
  return ["scrypt", params.N, params.r, params.p, salt.toString("base64"), hash.toString("base64")].join("$");
}

export async function verifyPassword(password: string, encoded: string): Promise<boolean> {
  const [alg, n, r, p, salt, hash] = encoded.split("$");
  if (alg !== "scrypt" || !n || !r || !p || !salt || !hash) return false;
  const expected = Buffer.from(hash, "base64");
  const actual = await scrypt(password, Buffer.from(salt, "base64"), expected.length, {
    N: Number(n),
    r: Number(r),
    p: Number(p),
    maxmem: PARAMS.maxmem,
  });
  return timingSafeEqual(actual, expected);
}
