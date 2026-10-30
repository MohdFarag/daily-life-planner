import { uid } from "../lib/ids";
import { todayISO } from "../lib/time";
import type { Data, Focus, InboxItem, ItemStatus, Source, Task, TriageAction } from "./types";

// Pure transforms of the app data. Each takes the current data and returns the next version.

export const patchTask =
  (id: string, patch: Partial<Task>) =>
  (d: Data): Data => ({
    ...d,
    tasks: d.tasks.map((x) => (x.id === id ? { ...x, ...patch } : x)),
  });

export const patchItem =
  (id: string, patch: Partial<InboxItem>) =>
  (d: Data): Data => ({
    ...d,
    inbox: d.inbox.map((x) => (x.id === id ? { ...x, ...patch } : x)),
  });

/** Completing or deleting the focused task also ends the focus session. */
const withoutFocusOn = (d: Data, taskIds: string[]) => (d.focus && taskIds.includes(d.focus.taskId) ? null : d.focus);

export const toggleDone =
  (id: string) =>
  (d: Data): Data => ({
    ...d,
    tasks: d.tasks.map((x) => (x.id === id ? { ...x, done: !x.done, doneOn: !x.done ? todayISO() : undefined } : x)),
    focus: withoutFocusOn(d, [id]),
  });

export const deleteTask =
  (id: string) =>
  (d: Data): Data => ({
    ...d,
    tasks: d.tasks.filter((x) => x.id !== id),
    focus: withoutFocusOn(d, [id]),
  });

/** Items captured offline are marked until the connection returns. */
export const markSynced = (d: Data): Data => ({
  ...d,
  inbox: d.inbox.map((i) => (i.pendingSync ? { ...i, pendingSync: false } : i)),
});

// Focus ---------------------------------------------------------------------------------

/** Seconds left, counting time elapsed since the session last resumed. */
export const remaining = (f: Focus) => (f.runningSince ? f.remainingSec - (Date.now() - f.runningSince) / 1000 : f.remainingSec);

export const startFocus =
  (taskId: string) =>
  (d: Data): Data => {
    const task = d.tasks.find((x) => x.id === taskId);
    const totalSec = (task?.session?.duration ?? task?.estimate ?? 25) * 60;
    return { ...d, focus: { taskId, totalSec, remainingSec: totalSec, runningSince: Date.now() } };
  };

const mapFocus =
  (fn: (f: Focus) => Focus) =>
  (d: Data): Data =>
    d.focus ? { ...d, focus: fn(d.focus) } : d;

export const pauseFocus = mapFocus((f) => ({ ...f, remainingSec: Math.max(0, remaining(f)), runningSince: null }));

export const resumeFocus = mapFocus((f) => ({ ...f, runningSince: Date.now() }));

export const extendFocus = (sec: number) =>
  mapFocus((f) => ({
    ...f,
    totalSec: f.totalSec + sec,
    remainingSec: Math.max(0, remaining(f)) + sec,
    runningSince: f.runningSince ? Date.now() : null,
  }));

export const endFocus = (d: Data): Data => ({ ...d, focus: null });

// Triage --------------------------------------------------------------------------------

const sourceOf = (item: InboxItem): Source => ({
  kind: item.kind,
  title: item.title,
  body: item.body,
  url: item.url,
  createdAt: item.createdAt,
  blobId: item.blobId,
  fileName: item.fileName,
});

const taskFromItem = (item: InboxItem, title = item.title): Task => ({
  id: uid(),
  title,
  notes: item.kind === "link" ? (item.url ?? "") : (item.body.split("\n")[0] ?? ""),
  list: "Work",
  estimate: 30,
  important: false,
  done: false,
  source: sourceOf(item),
});

/** The tasks a triage decision creates: one, one per line (split), or none. */
export function tasksFor(item: InboxItem, action: TriageAction): Task[] {
  if (action === "task" || action === "now") return [taskFromItem(item)];
  if (action === "split") {
    const parts = [item.title, ...item.body.split("\n")].map((s) => s.trim()).filter(Boolean);
    return parts.map((p) => taskFromItem(item, p));
  }
  return [];
}

export const statusAfter = (action: TriageAction): ItemStatus =>
  action === "task" || action === "now" || action === "split" ? "processed" : action;

export const applyTriage =
  (itemId: string, status: ItemStatus, created: Task[]) =>
  (d: Data): Data => ({
    ...d,
    tasks: [...d.tasks, ...created],
    inbox: d.inbox.map((i) => (i.id === itemId ? { ...i, status } : i)),
  });

export const revertTriage =
  (itemId: string, taskIds: string[]) =>
  (d: Data): Data => ({
    ...d,
    tasks: d.tasks.filter((x) => !taskIds.includes(x.id)),
    inbox: d.inbox.map((i) => (i.id === itemId ? { ...i, status: "inbox" } : i)),
    focus: withoutFocusOn(d, taskIds),
  });
