import { afterEach, describe, expect, it, vi } from "vitest";
import { busyOn, conflictsWith, dayPlan, freeMinutes, inWorkBlock, suggestedStart, timeRange, type BusyLabels } from "./schedule";
import { data, task } from "./testData";

const labels: BusyLabels = { task: (t) => t.title, prayer: (k) => k, event: (e) => e.title };
const at = (start: string, duration = 30, date = "2026-10-29") => ({ date, start, duration });

afterEach(() => vi.useRealTimers());

describe("busyOn", () => {
  const d = data({
    tasks: [task({ id: "a", session: at("10:00") }), task({ id: "b", done: true, session: at("11:00") })],
    events: [{ id: "e", title: "Sync", date: "2026-10-29", start: "13:00", duration: 30 }],
  });

  it("lists tasks, prayers and events sorted by start, skipping done tasks by default", () => {
    const kinds = busyOn(d, "2026-10-29", labels).map((b) => `${b.kind}:${b.id}`);
    expect(kinds).toEqual(["prayer:fajr", "task:a", "prayer:dhuhr", "google:e", "prayer:asr", "prayer:maghrib", "prayer:isha"]);
    expect(busyOn(d, "2026-10-29", labels, { includeDone: true }).some((b) => b.id === "b")).toBe(true);
  });

  it("hides calendar events when the calendar is off", () => {
    expect(busyOn({ ...d, showGoogle: false }, "2026-10-29", labels).some((b) => b.kind === "google")).toBe(false);
  });
});

describe("conflicts", () => {
  const busy = busyOn(data({ tasks: [task({ id: "a", session: at("12:00") })] }), "2026-10-29", labels);

  it("ignores the task being scheduled", () => {
    expect(conflictsWith(busy, 12 * 60, 12 * 60 + 5, "a", false)).toEqual([]);
  });

  it("reports protected prayers only while protection is on", () => {
    expect(conflictsWith(busy, 12 * 60 + 15, 12 * 60 + 20, "x", true).map((b) => b.id)).toEqual(["a", "dhuhr"]);
    expect(conflictsWith(busy, 12 * 60 + 15, 12 * 60 + 20, "x", false).map((b) => b.id)).toEqual(["a"]);
  });
});

it("formats ranges and checks the work block", () => {
  expect(timeRange(9 * 60, 9 * 60 + 45)).toBe("09:00–09:45");
  expect(inWorkBlock(9 * 60, 12 * 60)).toBe(true);
  expect(inWorkBlock(11 * 60 + 30, 12 * 60 + 30)).toBe(false);
});

it("suggests the next quarter hour, within 09:00–22:00", () => {
  expect(suggestedStart(10 * 60 + 2)).toBe("10:15");
  expect(suggestedStart(10 * 60 + 12)).toBe("10:30");
  expect(suggestedStart(6 * 60)).toBe("09:00");
  expect(suggestedStart(23 * 60)).toBe("22:00");
});

describe("dayPlan", () => {
  const tasks = [
    task({ id: "late", session: at("14:00") }),
    task({ id: "early", session: at("09:30") }),
    task({ id: "done", done: true, session: at("08:00") }),
    task({ id: "tomorrow", session: at("10:00", 30, "2026-10-30") }),
  ];

  it("picks the first open task as up next and interleaves prayers up to the last task", () => {
    const { upNext, rows } = dayPlan(tasks, "2026-10-29", 8 * 60);
    expect(upNext?.id).toBe("early");
    expect(rows.map((r) => (r.kind === "task" ? r.task.id : r.prayer.key))).toEqual(["done", "dhuhr", "late"]);
  });

  it("still shows the next prayer when nothing else is planned", () => {
    const { upNext, rows } = dayPlan([], "2026-10-29", 13 * 60);
    expect(upNext).toBeUndefined();
    expect(rows.map((r) => (r.kind === "prayer" ? r.prayer.key : "")).join()).toBe("asr");
  });
});

it("free minutes count the rest of the workday minus what is booked", () => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date(2026, 9, 29, 16, 0));
  const busy = [{ kind: "task" as const, id: "a", label: "", start: 16 * 60 + 30, end: 18 * 60 }];
  expect(freeMinutes(busy)).toBe(30);
});
