import { createHash, randomBytes, randomUUID } from "node:crypto";
import { describe, expect, it } from "vitest";
import { RetentionJob } from "../src/jobs/retention.js";
import { signUp, useTestApp } from "./helpers.js";

const ctx = useTestApp();

describe("retention job", () => {
  it("purges trashed items past their date, leaves a tombstone and deletes their media (DEL01, DEL03)", async () => {
    const { headers } = await signUp(ctx);
    const bytes = randomBytes(64);
    const media = (await ctx.app.inject({
      method: "POST", url: "/v1/media", headers,
      payload: { mimeType: "image/jpeg", sizeBytes: bytes.length, sha256: createHash("sha256").update(bytes).digest("hex") },
    })).json();
    await ctx.app.inject({ method: "PUT", url: `/v1/media/${media.id}/content`, headers: { ...headers, "content-type": "application/octet-stream", "upload-offset": "0" }, payload: bytes });

    const item = (id: string, purgeAt: string) => ({
      mutationId: randomUUID(), entity: "items", id, op: "upsert", baseVersion: 0,
      fields: { kind: "IMAGE", title: id, state: "TRASHED", capturedAt: "2026-09-01T10:00:00Z", purgeAt, attachments: [{ mediaId: media.id }] },
    });
    await ctx.app.inject({ method: "POST", url: "/v1/sync/push", headers, payload: { mutations: [item("expired", "2026-10-01T00:00:00Z"), item("recent", "2099-01-01T00:00:00Z")] } });

    const job = new RetentionJob(ctx.services.sync, ctx.services.auth, ctx.services.media, ctx.app.log);
    const report = await job.run(new Date("2026-10-29T00:00:00Z"));
    expect(report.purgedItems).toBe(1);

    const changes = (await ctx.app.inject({ method: "GET", url: "/v1/sync/pull?since=2", headers })).json().changes;
    expect(changes).toEqual([{ entity: "items", id: "expired", version: 3, deleted: true, doc: {} }]);
    expect((await ctx.app.inject({ method: "GET", url: `/v1/media/${media.id}`, headers })).statusCode).toBe(404);

    // Running again changes nothing.
    expect((await job.run(new Date("2026-10-29T00:00:00Z"))).purgedItems).toBe(0);
  });
});
