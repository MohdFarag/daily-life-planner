import { describe, expect, it } from "vitest";
import { GoogleApiError, GoogleHttpApi } from "../src/modules/google/google.api.js";
import { blockToEvent, eventFromGoogle, isKeepUrl, taskFromGoogle, taskToGoogle } from "../src/modules/google/google.mapping.js";
import { SecretBox } from "../src/shared/secret-box.js";

describe("Google mapping", () => {
  const task = {
    id: "t",
    title: "Call",
    notes: "",
    listId: "l",
    status: "IN_PROGRESS" as const,
    important: false,
    createdAt: "2026-10-01T00:00:00Z",
  };

  it("keeps the app's in-progress state while Google says needs action, and never sends deadlines", () => {
    expect(taskFromGoogle({ id: "g", status: "needsAction", title: "Call" }, "l", undefined, task).status).toBe("IN_PROGRESS");
    expect(taskFromGoogle({ id: "g", status: "completed", completed: "2026-10-30T10:00:00Z" }, "l", undefined, task)).toMatchObject({
      status: "COMPLETED",
      completedAt: "2026-10-30T10:00:00Z",
    });
    expect(taskToGoogle({ ...task, deadline: "2026-11-01" })).toEqual({ title: "Call", status: "needsAction" });
  });

  it("recognises Keep links by host only", () => {
    expect(isKeepUrl("https://keep.google.com/#NOTE/abc")).toBe(true);
    expect(isKeepUrl("https://keep.google.com.evil.example/x")).toBe(false);
    expect(isKeepUrl("http://keep.google.com/x")).toBe(false);
    expect(isKeepUrl("not a url")).toBe(false);
  });

  it("publishes guiding blocks as free and overnight blocks ending the next day (BLK03)", () => {
    const event = blockToEvent(
      { id: "b", name: "Night", start: "22:00", end: "06:00", days: ["FRIDAY"], policy: "GUIDING" },
      "Asia/Riyadh",
      "2026-10-30",
    );
    expect(event).toMatchObject({
      transparency: "transparent",
      recurrence: ["RRULE:FREQ=WEEKLY;BYDAY=FR"],
      start: { dateTime: "2026-10-30T22:00:00", timeZone: "Asia/Riyadh" },
      end: { dateTime: "2026-10-31T06:00:00" },
    });
  });

  it("reads all-day events at local midnight and marks events of read-only calendars", () => {
    const fields = eventFromGoogle(
      { id: "e", summary: "Holiday", start: { date: "2026-12-25" }, end: { date: "2026-12-26" } },
      { id: "c", summary: "Holidays", accessRole: "reader" },
      "Africa/Cairo",
    );
    expect(fields).toMatchObject({ allDay: true, readOnly: true, start: "2026-12-24T22:00:00.000Z", end: "2026-12-25T22:00:00.000Z" });
  });
});

describe("Google HTTP client", () => {
  it("sends the bearer token, follows pages and reports expired sync tokens", async () => {
    const calls: { url: string; auth: string | null }[] = [];
    const fetchImpl: typeof fetch = async (input, init) => {
      const url = input instanceof Request ? input.url : input.toString();
      calls.push({ url, auth: new Headers(init?.headers).get("authorization") });
      if (url.includes("syncToken=old")) return new Response("gone", { status: 410 });
      if (url.includes("/lists?") && !url.includes("pageToken"))
        return Response.json({ items: [{ id: "a", title: "A" }], nextPageToken: "p2" });
      if (url.includes("/lists?")) return Response.json({ items: [{ id: "b", title: "B" }] });
      if (url.includes("/fail")) return new Response("nope", { status: 403 });
      return Response.json({});
    };
    const api = new GoogleHttpApi(async () => "token-1", fetchImpl);

    expect((await api.taskLists()).map((l) => l.id)).toEqual(["a", "b"]);
    expect(calls[0]!.auth).toBe("Bearer token-1");
    expect(await api.events("cal", "old", { from: "x", to: "y" })).toEqual({ events: [], nextSyncToken: null });
    await expect(api.patchTaskList("fail", "x").catch((e: unknown) => e)).resolves.toBeInstanceOf(GoogleApiError);
  });
});

describe("secret box", () => {
  it("round-trips and detects tampering", () => {
    const box = new SecretBox(Buffer.alloc(32, 1).toString("base64"));
    const sealed = box.seal("refresh-token");
    expect(box.open(sealed)).toBe("refresh-token");
    sealed[sealed.length - 1]! ^= 1;
    expect(() => box.open(sealed)).toThrow();
    expect(() => new SecretBox("c2hvcnQ=")).toThrow(/32 bytes/);
  });
});
