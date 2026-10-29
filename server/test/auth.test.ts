import { describe, expect, it } from "vitest";
import { signUp, useTestApp } from "./helpers.js";

const ctx = useTestApp();

describe("auth", () => {
  it("registers, then rejects the same email in another case", async () => {
    const { email } = await signUp(ctx);
    const res = await ctx.app.inject({ method: "POST", url: "/v1/auth/register", payload: { email: email.toUpperCase(), password: "another long password" } });
    expect(res.statusCode).toBe(409);
    expect(res.json()).toEqual({ error: { code: "email_taken", message: expect.any(String) } });
  });

  it("validates input with a consistent error body", async () => {
    const res = await ctx.app.inject({ method: "POST", url: "/v1/auth/register", payload: { email: "nope", password: "short" } });
    expect(res.statusCode).toBe(400);
    expect(res.json().error.code).toBe("invalid_request");
  });

  it("logs in and rejects a wrong password with the same message as an unknown email", async () => {
    const { email, password } = await signUp(ctx);
    const ok = await ctx.app.inject({ method: "POST", url: "/v1/auth/login", payload: { email, password } });
    expect(ok.statusCode).toBe(200);
    const wrong = await ctx.app.inject({ method: "POST", url: "/v1/auth/login", payload: { email, password: "wrong password!" } });
    const unknown = await ctx.app.inject({ method: "POST", url: "/v1/auth/login", payload: { email: "ghost@example.com", password: "wrong password!" } });
    expect(wrong.statusCode).toBe(401);
    expect(unknown.json()).toEqual(wrong.json());
  });

  it("returns the current user and never the password hash", async () => {
    const { headers, email } = await signUp(ctx);
    const res = await ctx.app.inject({ method: "GET", url: "/v1/me", headers });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toMatchObject({ email, locale: "en" });
    expect(res.body).not.toContain("scrypt");
  });

  it("requires a bearer token", async () => {
    expect((await ctx.app.inject({ method: "GET", url: "/v1/me" })).statusCode).toBe(401);
    expect((await ctx.app.inject({ method: "GET", url: "/v1/me", headers: { authorization: "Bearer not-a-token" } })).statusCode).toBe(401);
  });

  it("rotates refresh tokens and signs the session out when an old one is reused", async () => {
    const { tokens } = await signUp(ctx);
    const first = await ctx.app.inject({ method: "POST", url: "/v1/auth/refresh", payload: { refreshToken: tokens.refreshToken } });
    expect(first.statusCode).toBe(200);
    const rotated = first.json();
    expect(rotated.refreshToken).not.toBe(tokens.refreshToken);

    // The pre-rotation access token no longer works; the new one does.
    expect((await ctx.app.inject({ method: "GET", url: "/v1/me", headers: { authorization: `Bearer ${tokens.accessToken}` } })).statusCode).toBe(401);
    expect((await ctx.app.inject({ method: "GET", url: "/v1/me", headers: { authorization: `Bearer ${rotated.accessToken}` } })).statusCode).toBe(200);

    // Replaying the old refresh token looks like theft: the whole session is revoked.
    const replay = await ctx.app.inject({ method: "POST", url: "/v1/auth/refresh", payload: { refreshToken: tokens.refreshToken } });
    expect(replay.json().error.code).toBe("token_reused");
    expect((await ctx.app.inject({ method: "GET", url: "/v1/me", headers: { authorization: `Bearer ${rotated.accessToken}` } })).statusCode).toBe(401);
    expect((await ctx.app.inject({ method: "POST", url: "/v1/auth/refresh", payload: { refreshToken: rotated.refreshToken } })).statusCode).toBe(401);
  });

  it("lists devices and signs one out", async () => {
    const phone = await signUp(ctx);
    const laptop = await ctx.app.inject({ method: "POST", url: "/v1/auth/login", payload: { email: phone.email, password: phone.password, deviceName: "Laptop" } });
    const laptopHeaders = { authorization: `Bearer ${laptop.json().tokens.accessToken}` };

    const list = await ctx.app.inject({ method: "GET", url: "/v1/sessions", headers: phone.headers });
    const sessions = list.json().sessions as { id: string; deviceName: string; current: boolean }[];
    expect(sessions.map((s) => s.deviceName).sort()).toEqual(["Laptop", "Pixel"]);

    const laptopSession = sessions.find((s) => s.deviceName === "Laptop")!;
    expect((await ctx.app.inject({ method: "DELETE", url: `/v1/sessions/${laptopSession.id}`, headers: phone.headers })).statusCode).toBe(204);
    expect((await ctx.app.inject({ method: "GET", url: "/v1/me", headers: laptopHeaders })).statusCode).toBe(401);
  });

  it("cannot sign out another user's session", async () => {
    const a = await signUp(ctx);
    const b = await signUp(ctx);
    const bSession = (await ctx.app.inject({ method: "GET", url: "/v1/sessions", headers: b.headers })).json().sessions[0].id;
    expect((await ctx.app.inject({ method: "DELETE", url: `/v1/sessions/${bSession}`, headers: a.headers })).statusCode).toBe(404);
  });

  it("logs out", async () => {
    const { headers } = await signUp(ctx);
    expect((await ctx.app.inject({ method: "POST", url: "/v1/auth/logout", headers })).statusCode).toBe(204);
    expect((await ctx.app.inject({ method: "GET", url: "/v1/me", headers })).statusCode).toBe(401);
  });
});

describe("credential rate limit", () => {
  const limited = useTestApp({ AUTH_RATE_LIMIT_PER_MINUTE: "2" });

  it("answers 429 with a rate_limited code after the limit", async () => {
    const attempt = () => limited.app.inject({ method: "POST", url: "/v1/auth/login", payload: { email: "a@example.com", password: "whatever-password" } });
    await attempt();
    await attempt();
    const third = await attempt();
    expect(third.statusCode).toBe(429);
    expect(third.json().error.code).toBe("rate_limited");
  });
});
