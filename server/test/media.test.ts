import { createHash, randomBytes } from "node:crypto";
import { describe, expect, it } from "vitest";
import { signUp, useTestApp } from "./helpers.js";

const ctx = useTestApp({ USER_QUOTA_BYTES: "1000000", MAX_FILE_BYTES: "600000" });

const sha256 = (b: Buffer) => createHash("sha256").update(b).digest("hex");
type Headers = { authorization: string };

async function start(headers: Headers, bytes: Buffer, declaredSha = sha256(bytes)) {
  const res = await ctx.app.inject({
    method: "POST",
    url: "/v1/media",
    headers,
    payload: { filename: "voice.m4a", mimeType: "audio/mp4", sizeBytes: bytes.length, sha256: declaredSha },
  });
  return res;
}

const chunk = (headers: Headers, id: string, offset: number, body: Buffer) =>
  ctx.app.inject({
    method: "PUT",
    url: `/v1/media/${id}/content`,
    headers: { ...headers, "content-type": "application/octet-stream", "upload-offset": String(offset) },
    payload: body,
  });

describe("media uploads", () => {
  it("uploads in chunks, resumes from the reported offset, verifies, and downloads", async () => {
    const { headers } = await signUp(ctx);
    const bytes = randomBytes(300_000);
    const created = (await start(headers, bytes)).json();
    expect(created).toMatchObject({ status: "uploading", receivedBytes: 0 });

    expect((await chunk(headers, created.id, 0, bytes.subarray(0, 100_000))).json().receivedBytes).toBe(100_000);
    // A retried chunk at a stale offset is refused; the client asks where to resume.
    expect((await chunk(headers, created.id, 0, bytes.subarray(0, 100_000))).json().error.code).toBe("offset_mismatch");
    const status = (await ctx.app.inject({ method: "GET", url: `/v1/media/${created.id}`, headers })).json();
    expect(status.receivedBytes).toBe(100_000);

    const done = (await chunk(headers, created.id, 100_000, bytes.subarray(100_000))).json();
    expect(done).toMatchObject({ status: "complete", receivedBytes: 300_000 });

    const download = await ctx.app.inject({ method: "GET", url: `/v1/media/${created.id}/content`, headers });
    expect(download.statusCode).toBe(200);
    expect(download.headers["content-type"]).toBe("audio/mp4");
    expect(download.rawPayload.equals(bytes)).toBe(true);
  });

  it("rejects bytes that do not match the declared checksum and restarts the upload", async () => {
    const { headers } = await signUp(ctx);
    const bytes = randomBytes(1000);
    const created = (await start(headers, bytes, sha256(Buffer.from("something else")))).json();
    const res = await chunk(headers, created.id, 0, bytes);
    expect(res.statusCode).toBe(422);
    expect(res.json().error.code).toBe("checksum_mismatch");
    expect((await ctx.app.inject({ method: "GET", url: `/v1/media/${created.id}`, headers })).json().receivedBytes).toBe(0);
  });

  it("commits the reset when a multi-chunk upload fails its checksum, so it can resume from 0", async () => {
    const { headers } = await signUp(ctx);
    const bytes = randomBytes(2000);
    const created = (await start(headers, bytes, sha256(Buffer.from("something else")))).json();
    expect((await chunk(headers, created.id, 0, bytes.subarray(0, 1000))).json().receivedBytes).toBe(1000);
    expect((await chunk(headers, created.id, 1000, bytes.subarray(1000))).json().error.code).toBe("checksum_mismatch");
    expect((await ctx.app.inject({ method: "GET", url: `/v1/media/${created.id}`, headers })).json().receivedBytes).toBe(0);
    expect((await chunk(headers, created.id, 0, bytes.subarray(0, 1000))).json().receivedBytes).toBe(1000);
  });

  it("will not download an incomplete upload", async () => {
    const { headers } = await signUp(ctx);
    const created = (await start(headers, randomBytes(10))).json();
    expect((await ctx.app.inject({ method: "GET", url: `/v1/media/${created.id}/content`, headers })).json().error.code).toBe(
      "upload_incomplete",
    );
  });

  it("enforces the per-file limit and the account quota", async () => {
    const { headers } = await signUp(ctx);
    expect((await start(headers, randomBytes(700_000))).json().error.code).toBe("file_too_large");
    expect((await start(headers, randomBytes(500_000))).statusCode).toBe(201);
    expect((await start(headers, randomBytes(500_000))).statusCode).toBe(201);
    expect((await start(headers, randomBytes(10))).json().error.code).toBe("quota_exceeded");
  });

  it("refuses chunks beyond the declared size", async () => {
    const { headers } = await signUp(ctx);
    const created = (await start(headers, randomBytes(10))).json();
    expect((await chunk(headers, created.id, 0, randomBytes(11))).json().error.code).toBe("too_many_bytes");
  });

  it("hides other users' media and deletes on request", async () => {
    const owner = await signUp(ctx);
    const other = await signUp(ctx);
    const bytes = randomBytes(50);
    const created = (await start(owner.headers, bytes)).json();
    await chunk(owner.headers, created.id, 0, bytes);
    expect((await ctx.app.inject({ method: "GET", url: `/v1/media/${created.id}/content`, headers: other.headers })).statusCode).toBe(404);
    expect((await ctx.app.inject({ method: "DELETE", url: `/v1/media/${created.id}`, headers: owner.headers })).statusCode).toBe(204);
    expect((await ctx.app.inject({ method: "GET", url: `/v1/media/${created.id}`, headers: owner.headers })).statusCode).toBe(404);
  });
});
