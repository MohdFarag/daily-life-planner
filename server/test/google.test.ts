import { randomUUID } from "node:crypto";
import { describe, expect, it } from "vitest";
import { GoogleOAuth } from "../src/modules/google/google.oauth.js";
import { FakeGoogle } from "./fake-google.js";
import { signUp, useTestApp } from "./helpers.js";

const google = new FakeGoogle();
const tokenRequests: URLSearchParams[] = [];
const revoked: string[] = [];

/** Google's token and revocation endpoints. */
const fakeFetch: typeof fetch = async (input, init) => {
  const url = input instanceof Request ? input.url : input.toString();
  const body = new URLSearchParams(typeof init?.body === "string" ? init.body : "");
  if (url.endsWith("/revoke")) {
    revoked.push(body.get("token") ?? "");
    return new Response(null, { status: 200 });
  }
  tokenRequests.push(body);
  const idToken = ["e30", Buffer.from(JSON.stringify({ sub: "g-123", email: "me@example.com" })).toString("base64url"), "sig"].join(".");
  return Response.json({
    access_token: `access-${randomUUID()}`,
    expires_in: 3600,
    scope: "openid email tasks calendar",
    ...(body.get("grant_type") === "authorization_code" ? { refresh_token: "refresh-secret-1", id_token: idToken } : {}),
  });
};

const settings = { clientId: "client", clientSecret: "secret", redirectUri: "https://api.test/v1/google/callback" };
const ctx = useTestApp(
  {
    GOOGLE_CLIENT_ID: settings.clientId,
    GOOGLE_CLIENT_SECRET: settings.clientSecret,
    GOOGLE_REDIRECT_URI: settings.redirectUri,
    SECRETS_KEY: Buffer.alloc(32, 7).toString("base64"),
  },
  { google: { oauth: new GoogleOAuth(settings, fakeFetch), api: () => google } },
);
const off = useTestApp();

type Headers = { authorization: string };

async function push(headers: Headers, mutations: { entity: string; id: string; fields?: object; op?: string; baseVersion?: number }[]) {
  const res = await ctx.app.inject({
    method: "POST",
    url: "/v1/sync/push",
    headers,
    payload: { mutations: mutations.map((m) => ({ mutationId: randomUUID(), op: "upsert", baseVersion: 0, ...m })) },
  });
  expect(res.statusCode).toBe(200);
  return res.json<{ results: { status: string; version: number }[] }>().results;
}

async function records(headers: Headers) {
  const res = await ctx.app.inject({ method: "GET", url: "/v1/sync/pull?since=0", headers });
  const changes = res.json<{ changes: { entity: string; id: string; version: number; deleted: boolean; doc: Record<string, unknown> }[] }>()
    .changes;
  return {
    of: (entity: string) => changes.filter((c) => c.entity === entity && !c.deleted),
    get: (entity: string, id: string) => changes.find((c) => c.entity === entity && c.id === id),
  };
}

async function connect(headers: Headers) {
  const start = await ctx.app.inject({ method: "POST", url: "/v1/google/connect", headers });
  const url = new URL(start.json<{ url: string }>().url);
  const state = url.searchParams.get("state")!;
  const back = await ctx.app.inject({ method: "GET", url: `/v1/google/callback?state=${state}&code=auth-code` });
  return { url, state, back };
}

async function sync(headers: Headers) {
  const res = await ctx.app.inject({ method: "POST", url: "/v1/google/sync", headers });
  expect(res.statusCode).toBe(200);
  return res.json<{ errors: string[] }>().errors;
}

const iso = (offsetHours: number) => new Date(Date.now() + offsetHours * 3_600_000).toISOString();

describe("Google connection", () => {
  it("is reported as unavailable when the server is not configured", async () => {
    const { headers } = await signUp(off);
    const status = await off.app.inject({ method: "GET", url: "/v1/google/status", headers });
    expect(status.json()).toMatchObject({ enabled: false, connected: false });
    const connectRes = await off.app.inject({ method: "POST", url: "/v1/google/connect", headers });
    expect(connectRes.statusCode).toBe(503);
  });

  it("connects with PKCE and a single-use state, storing tokens only encrypted (SEC01)", async () => {
    const { headers, user } = await signUp(ctx);
    const { url, state, back } = await connect(headers);

    expect(url.origin + url.pathname).toBe("https://accounts.google.com/o/oauth2/v2/auth");
    expect(url.searchParams.get("code_challenge_method")).toBe("S256");
    expect(url.searchParams.get("access_type")).toBe("offline");
    expect(url.searchParams.get("scope")).toContain("https://www.googleapis.com/auth/tasks");
    expect(back.statusCode).toBe(200);
    expect(back.body).toContain("dailylife://google-connected?status=connected");

    const exchange = tokenRequests.find((r) => r.get("code") === "auth-code")!;
    expect(exchange.get("code_verifier")).toHaveLength(43);

    const row = await ctx.db.query<{ refresh_token_enc: Buffer }>("SELECT refresh_token_enc FROM google_accounts WHERE user_id = $1", [
      user.id,
    ]);
    expect(row.rows[0]!.refresh_token_enc.toString("latin1")).not.toContain("refresh-secret-1");

    const status = await ctx.app.inject({ method: "GET", url: "/v1/google/status", headers });
    expect(status.json()).toMatchObject({ enabled: true, connected: true, email: "me@example.com" });

    const replay = await ctx.app.inject({ method: "GET", url: `/v1/google/callback?state=${state}&code=auth-code` });
    expect(replay.body).toContain("status=error");
  });
});

describe("Google sync", () => {
  it("mirrors tasks, sessions, blocks and events both ways, once", async () => {
    const { headers, user } = await signUp(ctx);
    await push(headers, [
      { entity: "lists", id: "personal", fields: { id: "personal", name: "Personal" } },
      { entity: "settings", id: "prefs", fields: { _key: "prefs", reminderMinutes: 15 } },
      { entity: "settings", id: "prayer", fields: { _key: "prayer", zone: "Africa/Cairo" } },
      {
        entity: "tasks",
        id: "report",
        fields: { id: "report", title: "Write report", listId: "personal", status: "NEEDS_ACTION", createdAt: iso(-48) },
      },
      { entity: "sessions", id: "s1", fields: { id: "s1", taskId: "report", start: iso(24), end: iso(25), state: "PLANNED" } },
      {
        entity: "blocks",
        id: "deep",
        fields: { id: "deep", name: "Deep work", start: "09:00", end: "12:00", days: ["MONDAY", "WEDNESDAY"], policy: "PROTECTED" },
      },
    ]);
    google.tasksByList.get("gl-default")!.push({
      id: "gt-keep",
      etag: '"k1"',
      title: "Buy stamps",
      status: "needsAction",
      updated: iso(-1),
      links: [{ type: "keep_note", link: "https://keep.google.com/#NOTE/abc" }],
    });
    const dentist = google.userAddsEvent("primary-cal", { summary: "Dentist", start: { dateTime: iso(30) }, end: { dateTime: iso(31) } });
    google.calendarList.push({ id: "holidays", summary: "Holidays", accessRole: "reader", selected: false });
    google.eventsByCalendar.set("holidays", []);
    google.userAddsEvent("holidays", { summary: "Hidden holiday", start: { date: "2026-12-25" }, end: { date: "2026-12-26" } });

    await connect(headers);
    expect(await sync(headers)).toEqual([]);

    // Out: the task, the session (with its reminder) and the block.
    expect(
      google.tasksByList
        .get("gl-default")!
        .map((t) => t.title)
        .sort(),
    ).toEqual(["Buy stamps", "Write report"]);
    const own = await ctx.db.query<{ sessions: string; blocks: string }>(
      "SELECT sessions_calendar AS sessions, blocks_calendar AS blocks FROM google_accounts WHERE user_id = $1",
      [user.id],
    );
    const sessionsCal = { id: own.rows[0]!.sessions };
    const blocksCal = { id: own.rows[0]!.blocks };
    const [sessionEvent] = google.live(sessionsCal.id);
    expect(sessionEvent).toMatchObject({
      summary: "Write report",
      reminders: { useDefault: false, overrides: [{ method: "popup", minutes: 15 }] },
    });
    const [blockEvent] = google.live(blocksCal.id);
    expect(blockEvent).toMatchObject({ summary: "Deep work", transparency: "opaque", recurrence: ["RRULE:FREQ=WEEKLY;BYDAY=MO,WE"] });
    expect(blockEvent!.start).toMatchObject({ timeZone: "Africa/Cairo" });

    // In: the Google task with its Keep link, and events of visible calendars only.
    let r = await records(headers);
    expect(r.of("tasks").find((t) => t.doc.title === "Buy stamps")!.doc).toMatchObject({
      listId: "personal",
      status: "NEEDS_ACTION",
      keepUrl: "https://keep.google.com/#NOTE/abc",
    });
    expect(r.get("sessions", "s1")!.doc.googleEventId).toBe(sessionEvent!.id);
    expect(r.of("events").map((e) => e.doc.title)).toEqual(["Dentist"]);
    const calendars = r.get("settings", "calendars")!.doc as {
      list: { id: string; visible: boolean; appRole: string | null }[];
      defaultCalendarId: string;
    };
    expect(calendars.defaultCalendarId).toBe("primary-cal");
    expect(calendars.list.find((c) => c.id === "holidays")!.visible).toBe(false);
    expect(calendars.list.find((c) => c.id === sessionsCal.id)!.appRole).toBe("sessions");

    // Nothing changed: nothing is written again (no echo).
    const writes = google.writes.length;
    expect(await sync(headers)).toEqual([]);
    expect(google.writes.slice(writes)).toEqual([]);

    // Changes on both sides.
    const keepId = r.of("tasks").find((t) => t.doc.title === "Buy stamps")!.id;
    google.userEditsTask("gl-default", "gt-keep", { status: "completed", completed: iso(0) });
    google.userCancelsEvent("primary-cal", dentist);
    const report = r.get("tasks", "report")!;
    const s1 = r.get("sessions", "s1")!;
    await push(headers, [
      { entity: "tasks", id: "report", fields: { status: "COMPLETED" }, baseVersion: report.version },
      { entity: "sessions", id: "s1", fields: { state: "CANCELED" }, baseVersion: s1.version },
      { entity: "events", id: "trip", fields: { id: "trip", title: "Trip", start: iso(72), end: iso(96), calendarId: "" } },
    ]);
    expect(await sync(headers)).toEqual([]);

    r = await records(headers);
    expect(r.get("tasks", keepId)!.doc.status).toBe("COMPLETED");
    expect(r.of("events").map((e) => e.doc.title)).toEqual(["Trip"]);
    expect(r.get("events", "trip")!.doc).toMatchObject({ calendarId: "primary-cal", googleId: expect.any(String) });
    expect(google.tasksByList.get("gl-default")!.find((t) => t.title === "Write report")!.status).toBe("completed");
    expect(google.live(sessionsCal.id)).toEqual([]);
    expect(google.live("primary-cal").map((e) => e.summary)).toEqual(["Trip"]);

    // A task deleted on a device is deleted in Google.
    await push(headers, [{ entity: "tasks", id: "report", op: "delete", baseVersion: r.get("tasks", "report")!.version }]);
    await sync(headers);
    expect(google.tasksByList.get("gl-default")!.find((t) => t.title === "Write report")!.deleted).toBe(true);

    // Disconnecting revokes access and removes Google's events from devices.
    const before = revoked.length;
    const res = await ctx.app.inject({ method: "DELETE", url: "/v1/google", headers });
    expect(res.statusCode).toBe(204);
    expect(revoked.slice(before)).toEqual(["refresh-secret-1"]);
    r = await records(headers);
    expect(r.of("events")).toEqual([]);
    expect(r.of("tasks").map((t) => t.doc.title)).toEqual(["Buy stamps"]);
    const status = await ctx.app.inject({ method: "GET", url: "/v1/google/status", headers });
    expect(status.json()).toMatchObject({ connected: false });
  });

  it("revokes Google access when the account is deleted (SEC03)", async () => {
    const { headers, password } = await signUp(ctx);
    await connect(headers);
    const before = revoked.length;
    const res = await ctx.app.inject({ method: "DELETE", url: "/v1/account", headers, payload: { password } });
    expect(res.statusCode).toBe(204);
    expect(revoked.length).toBe(before + 1);
  });
});
