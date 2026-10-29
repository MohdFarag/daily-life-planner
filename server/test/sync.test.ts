import { randomUUID } from "node:crypto";
import { describe, expect, it } from "vitest";
import { signUp, useTestApp } from "./helpers.js";

const ctx = useTestApp();

type Headers = { authorization: string };
const mid = () => randomUUID();

async function push(headers: Headers, mutations: object[]) {
  const res = await ctx.app.inject({ method: "POST", url: "/v1/sync/push", headers, payload: { mutations } });
  expect(res.statusCode).toBe(200);
  return res.json<{ version: number; results: { status: string; version: number; conflicts?: unknown[]; reason?: string }[] }>();
}

async function pull(headers: Headers, since = 0, limit = 500) {
  const res = await ctx.app.inject({ method: "GET", url: `/v1/sync/pull?since=${since}&limit=${limit}`, headers });
  expect(res.statusCode).toBe(200);
  return res.json<{ changes: { entity: string; id: string; version: number; deleted: boolean; doc: Record<string, unknown> }[]; cursor: number; hasMore: boolean }>();
}

const task = (id: string, fields: object, baseVersion = 0) => ({ mutationId: mid(), entity: "tasks", id, op: "upsert", baseVersion, fields });

describe("sync", () => {
  it("creates records and returns them on pull with a cursor", async () => {
    const { headers } = await signUp(ctx);
    const pushed = await push(headers, [task("t1", { title: "Review brief", listId: "work", status: "NEEDS_ACTION" })]);
    expect(pushed.results[0]).toMatchObject({ status: "applied", version: 1 });

    const first = await pull(headers);
    expect(first.changes).toEqual([{ entity: "tasks", id: "t1", version: 1, deleted: false, doc: { title: "Review brief", listId: "work", status: "NEEDS_ACTION" } }]);
    expect(first.cursor).toBe(1);
    expect((await pull(headers, first.cursor)).changes).toEqual([]);
  });

  it("returns the stored result when a mutation is retried (no duplicate apply)", async () => {
    const { headers } = await signUp(ctx);
    const m = task("t1", { title: "Once" });
    const a = await push(headers, [m]);
    const b = await push(headers, [m]);
    expect(b.results).toEqual(a.results);
    expect(b.version).toBe(1);
  });

  it("merges edits to different fields from two devices", async () => {
    const { headers } = await signUp(ctx);
    await push(headers, [task("t1", { title: "Read", notes: "ch. 1" })]); // v1
    await push(headers, [task("t1", { title: "Read 10 pages" }, 1)]);      // phone, v2
    const laptop = await push(headers, [task("t1", { notes: "ch. 2" }, 1)]); // laptop saw v1 only
    expect(laptop.results[0]!.status).toBe("applied");
    expect((await pull(headers)).changes[0]!.doc).toMatchObject({ title: "Read 10 pages", notes: "ch. 2" });
  });

  it("reports a same-field conflict and keeps the server value (SYN04)", async () => {
    const { headers } = await signUp(ctx);
    await push(headers, [task("t1", { title: "A" })]);
    await push(headers, [task("t1", { title: "B" }, 1)]);
    const stale = await push(headers, [task("t1", { title: "C" }, 1)]);
    expect(stale.results[0]).toMatchObject({ status: "conflict", conflicts: [{ field: "title", serverValue: "B", serverVersion: 2 }] });
    expect((await pull(headers)).changes[0]!.doc.title).toBe("B");
  });

  it("deletes as a tombstone that a stale copy cannot resurrect (DEL01, DEL03)", async () => {
    const { headers } = await signUp(ctx);
    await push(headers, [task("t1", { title: "Temporary" })]);
    await push(headers, [{ mutationId: mid(), entity: "tasks", id: "t1", op: "delete", baseVersion: 1 }]);
    const change = (await pull(headers)).changes.find((c) => c.id === "t1")!;
    expect(change).toMatchObject({ deleted: true, doc: {} });

    const stale = await push(headers, [task("t1", { title: "Edited offline" }, 1)]);
    expect(stale.results[0]).toMatchObject({ status: "rejected", reason: "deleted" });
  });

  it("rejects invalid fields without saving anything", async () => {
    const { headers } = await signUp(ctx);
    const r = await push(headers, [task("t1", { title: 42 }), task("t2", { "bad-name": 1 })]);
    expect(r.results.map((x) => x.status)).toEqual(["rejected", "rejected"]);
    expect((await pull(headers)).changes).toEqual([]);
  });

  it("rejects malformed dates, including purgeAt, so server jobs can rely on them", async () => {
    const { headers } = await signUp(ctx);
    const item = (fields: object) => ({ mutationId: mid(), entity: "items", id: "i1", op: "upsert", baseVersion: 0, fields });
    const r = await push(headers, [
      item({ title: "x", state: "TRASHED", purgeAt: "soon" }),
      item({ title: "x", capturedAt: "2026-13-45T99:00:00Z" }),
      { mutationId: mid(), entity: "sessions", id: "s1", op: "upsert", baseVersion: 0, fields: { taskId: "t1", start: "10:00", end: "10:45" } },
    ]);
    expect(r.results.map((x) => x.status)).toEqual(["rejected", "rejected", "rejected"]);
    const ok = await push(headers, [item({ title: "x", state: "TRASHED", purgeAt: "2026-11-28T09:41:00.123456Z", capturedAt: "2026-10-29T09:41:00+03:00" })]);
    expect(ok.results[0]!.status).toBe("applied");
  });

  it("rejects unknown entities and malformed batches at the API boundary", async () => {
    const { headers } = await signUp(ctx);
    const unknown = await ctx.app.inject({ method: "POST", url: "/v1/sync/push", headers, payload: { mutations: [{ ...task("x", { a: 1 }), entity: "secrets" }] } });
    expect(unknown.statusCode).toBe(400);
    const noFields = await ctx.app.inject({ method: "POST", url: "/v1/sync/push", headers, payload: { mutations: [{ mutationId: mid(), entity: "tasks", id: "t", op: "upsert", baseVersion: 0 }] } });
    expect(noFields.statusCode).toBe(400);
  });

  it("keeps each user's data separate", async () => {
    const a = await signUp(ctx);
    const b = await signUp(ctx);
    await push(a.headers, [task("shared-id", { title: "A's task" })]);
    await push(b.headers, [task("shared-id", { title: "B's task" })]);
    expect((await pull(a.headers)).changes[0]!.doc.title).toBe("A's task");
    expect((await pull(b.headers)).changes[0]!.doc.title).toBe("B's task");
  });

  it("pages through changes with hasMore", async () => {
    const { headers } = await signUp(ctx);
    await push(headers, Array.from({ length: 5 }, (_, i) => task(`t${i}`, { title: `Task ${i}` })));
    const page1 = await pull(headers, 0, 2);
    const page2 = await pull(headers, page1.cursor, 2);
    const page3 = await pull(headers, page2.cursor, 2);
    expect([page1.hasMore, page2.hasMore, page3.hasMore]).toEqual([true, true, false]);
    expect([...page1.changes, ...page2.changes, ...page3.changes].map((c) => c.id)).toEqual(["t0", "t1", "t2", "t3", "t4"]);
  });

  it("assigns gap-free, ordered versions under concurrent pushes", async () => {
    const { headers } = await signUp(ctx);
    await Promise.all(Array.from({ length: 10 }, (_, i) => push(headers, [task(`c${i}`, { title: `${i}` })])));
    const versions = (await pull(headers)).changes.map((c) => c.version);
    expect(versions).toEqual(Array.from({ length: 10 }, (_, i) => i + 1));
  });

  it("requires authentication", async () => {
    expect((await ctx.app.inject({ method: "GET", url: "/v1/sync/pull" })).statusCode).toBe(401);
  });
});
