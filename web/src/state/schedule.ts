import { nowMin, toMin } from "../lib/time";
import { PRAYER_MIN, PRAYERS, WORKDAY_END } from "./constants";
import type { Busy, Data } from "./types";

/** How busy items are labelled; supplied by the caller so this stays independent of i18n. */
export interface BusyLabels {
  task: (task: Data["tasks"][number]) => string;
  prayer: (key: string) => string;
  event: (event: Data["events"][number]) => string;
}

/** Everything occupying time on [date] (tasks, prayers, calendar events), sorted by start. */
export function busyOn(data: Data, date: string, labels: BusyLabels, opts: { includeDone?: boolean } = {}): Busy[] {
  const out: Busy[] = [];
  for (const task of data.tasks) {
    if (task.session?.date !== date || (task.done && !opts.includeDone)) continue;
    const start = toMin(task.session.start);
    out.push({ kind: "task", id: task.id, label: labels.task(task), start, end: start + task.session.duration, done: task.done });
  }
  for (const p of PRAYERS) {
    const start = toMin(p.start);
    out.push({ kind: "prayer", id: p.key, label: labels.prayer(p.key), start, end: start + PRAYER_MIN });
  }
  if (data.showGoogle)
    for (const e of data.events) {
      if (e.date !== date) continue;
      const start = toMin(e.start);
      out.push({ kind: "google", id: e.id, label: labels.event(e), start, end: start + e.duration });
    }
  return out.sort((a, b) => a.start - b.start);
}

/** Minutes left in today's workday that aren't already committed. */
export function freeMinutes(busy: Busy[]): number {
  const from = Math.max(nowMin(), toMin("09:00"));
  const to = toMin(WORKDAY_END);
  if (from >= to) return 0;
  const committed = busy
    .map((b) => Math.max(0, Math.min(b.end, to) - Math.max(b.start, from)))
    .reduce((a, b) => a + b, 0);
  return Math.max(0, to - from - committed);
}
