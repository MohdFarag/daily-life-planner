import { randomUUID } from "node:crypto";
import { describe, expect, it } from "vitest";
import { signUp, useTestApp } from "./helpers.js";

const ctx = useTestApp();

describe("account", () => {
  it("exports live records and a media manifest, without deleted records", async () => {
    const { headers, email } = await signUp(ctx);
    await ctx.app.inject({
      method: "POST", url: "/v1/sync/push", headers,
      payload: { mutations: [
        { mutationId: randomUUID(), entity: "lists", id: "work", op: "upsert", baseVersion: 0, fields: { name: "Work" } },
        { mutationId: randomUUID(), entity: "lists", id: "old", op: "upsert", baseVersion: 0, fields: { name: "Old" } },
        { mutationId: randomUUID(), entity: "lists", id: "old", op: "delete", baseVersion: 2 },
      ] },
    });
    const res = await ctx.app.inject({ method: "GET", url: "/v1/account/export", headers });
    expect(res.statusCode).toBe(200);
    expect(res.headers["content-disposition"]).toContain("attachment");
    const data = res.json();
    expect(data).toMatchObject({ format: "daily-life-planner/1", user: { email }, media: [] });
    expect(data.records).toEqual([{ entity: "lists", id: "work", version: 1, doc: { name: "Work" } }]);
  });

  it("deletes the account only with the right password", async () => {
    const { headers, email, password } = await signUp(ctx);
    const wrong = await ctx.app.inject({ method: "DELETE", url: "/v1/account", headers, payload: { password: "not my password" } });
    expect(wrong.statusCode).toBe(403);

    expect((await ctx.app.inject({ method: "DELETE", url: "/v1/account", headers, payload: { password } })).statusCode).toBe(204);
    expect((await ctx.app.inject({ method: "GET", url: "/v1/me", headers })).statusCode).toBe(401);
    expect((await ctx.app.inject({ method: "POST", url: "/v1/auth/login", payload: { email, password } })).statusCode).toBe(401);
  });
});
