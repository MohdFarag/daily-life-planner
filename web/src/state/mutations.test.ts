import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as m from "./mutations";
import { data, item, task } from "./testData";

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date(2026, 9, 29, 10, 0));
});
afterEach(() => vi.useRealTimers());

describe("tasks", () => {
  it("completing a task stamps the day and ends its focus session", () => {
    const d = m.startFocus("t1")(data({ tasks: [task()] }));
    const done = m.toggleDone("t1")(d);
    expect(done.tasks[0]).toMatchObject({ done: true, doneOn: "2026-10-29" });
    expect(done.focus).toBeNull();
    expect(m.toggleDone("t1")(done).tasks[0]).toMatchObject({ done: false, doneOn: undefined });
  });

  it("deleting another task keeps the running focus session", () => {
    const d = m.startFocus("t1")(data({ tasks: [task(), task({ id: "t2" })] }));
    const after = m.deleteTask("t2")(d);
    expect(after.tasks.map((t) => t.id)).toEqual(["t1"]);
    expect(after.focus?.taskId).toBe("t1");
  });
});

describe("focus", () => {
  it("starts with the session length, falling back to the estimate", () => {
    const scheduled = task({ session: { date: "2026-10-29", start: "10:00", duration: 45 } });
    expect(m.startFocus("t1")(data({ tasks: [scheduled] })).focus?.totalSec).toBe(45 * 60);
    expect(m.startFocus("t1")(data({ tasks: [task({ estimate: 20 })] })).focus?.totalSec).toBe(20 * 60);
  });

  it("pausing freezes the remaining time and extending adds to it", () => {
    let d = m.startFocus("t1")(data({ tasks: [task({ estimate: 10 })] }));
    vi.advanceTimersByTime(60_000);
    d = m.pauseFocus(d);
    expect(d.focus).toMatchObject({ remainingSec: 540, runningSince: null });
    vi.advanceTimersByTime(60_000);
    expect(m.remaining(d.focus!)).toBe(540);
    d = m.extendFocus(300)(d);
    expect(d.focus).toMatchObject({ totalSec: 900, remainingSec: 840, runningSince: null });
  });
});

describe("triage", () => {
  it("splits an item into one task per non-empty line", () => {
    const created = m.tasksFor(item({ title: "Errands", body: "Buy milk\n\n Post letter " }), "split");
    expect(created.map((t) => t.title)).toEqual(["Errands", "Buy milk", "Post letter"]);
    expect(created[0].source).toMatchObject({ kind: "text", title: "Errands" });
  });

  it("a link task keeps the URL as its notes", () => {
    const [created] = m.tasksFor(item({ kind: "link", url: "https://example.com" }), "task");
    expect(created.notes).toBe("https://example.com");
  });

  it("filing creates no tasks and moves the item", () => {
    expect(m.tasksFor(item(), "someday")).toEqual([]);
    expect(m.statusAfter("someday")).toBe("someday");
    expect(m.statusAfter("now")).toBe("processed");
  });

  it("revert removes the created tasks and returns the item to the inbox", () => {
    const created = m.tasksFor(item(), "task");
    const applied = m.applyTriage("i1", "processed", created)(data({ inbox: [item()] }));
    expect(applied.inbox[0].status).toBe("processed");
    const reverted = m.revertTriage("i1", created.map((t) => t.id))(applied);
    expect(reverted.tasks).toEqual([]);
    expect(reverted.inbox[0].status).toBe("inbox");
  });
});

it("markSynced clears pending flags", () => {
  const d = m.markSynced(data({ inbox: [item({ pendingSync: true })] }));
  expect(d.inbox[0].pendingSync).toBe(false);
});
