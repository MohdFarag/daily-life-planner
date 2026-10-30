import { fromMin, nowMin, overlaps, toMin } from "../lib/time";
import { type Prayer, PRAYER_MIN, PRAYERS, WORK_BLOCK, WORKDAY_END } from "./constants";
import type { Busy, Data, Session, Task } from "./types";

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
  for (const p of PRAYERS) out.push({ kind: "prayer", id: p.key, label: labels.prayer(p.key), ...prayerWindow(p) });
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
  const committed = busy.map((b) => Math.max(0, Math.min(b.end, to) - Math.max(b.start, from))).reduce((a, b) => a + b, 0);
  return Math.max(0, to - from - committed);
}

/** "HH:MM–HH:MM" for a span given in minutes since midnight. */
export const timeRange = (start: number, end: number) => `${fromMin(start)}–${fromMin(end)}`;

/** A prayer's protected window, in minutes since midnight. */
export const prayerWindow = (prayer: Prayer) => {
  const start = toMin(prayer.start);
  return { start, end: start + PRAYER_MIN };
};

export const prayerByKey = (key: string): Prayer => PRAYERS.find((p) => p.key === key) ?? PRAYERS[0];

/** Whether a span lies entirely inside the work block. */
export const inWorkBlock = (start: number, end: number) => start >= toMin(WORK_BLOCK.start) && end <= toMin(WORK_BLOCK.end);

/**
 * What a proposed session would collide with: everything busy except the task itself, and
 * prayers only while they are protected.
 */
export const conflictsWith = (busy: Busy[], start: number, end: number, taskId: string, protectPrayers: boolean) =>
  busy.filter(
    (b) => !(b.kind === "task" && b.id === taskId) && (b.kind !== "prayer" || protectPrayers) && overlaps(start, end, b.start, b.end),
  );

/** The default start for a new session: the next quarter hour at least five minutes away, within 09:00–22:00. */
export function suggestedStart(now: number): string {
  const next = Math.min(Math.ceil((now + 5) / 15) * 15, 22 * 60);
  return fromMin(Math.max(next, toMin("09:00")));
}

export type DayRow = { kind: "prayer"; prayer: Prayer; at: number } | { kind: "task"; task: Task; at: number };

/**
 * Today's plan: the first open scheduled task (up next), then the other scheduled tasks interleaved
 * with the prayers that fall before the last of them ends (or at least the next prayer).
 */
export function dayPlan(tasks: Task[], today: string, now: number): { upNext?: Task; rows: DayRow[] } {
  const scheduled = tasks
    .filter((x): x is Task & { session: Session } => x.session?.date === today)
    .sort((a, b) => toMin(a.session.start) - toMin(b.session.start));
  const upNext = scheduled.find((x) => !x.done);
  const lastEnd = Math.max(0, ...scheduled.map((x) => toMin(x.session.start) + x.session.duration));
  const ahead = PRAYERS.filter((p) => prayerWindow(p).end > now);
  const inDay = ahead.filter((p) => prayerWindow(p).start < lastEnd);
  const prayers = inDay.length ? inDay : ahead.slice(0, 1);
  const rows: DayRow[] = [
    ...prayers.map((prayer) => ({ kind: "prayer" as const, prayer, at: prayerWindow(prayer).start })),
    ...scheduled.filter((x) => x !== upNext).map((task) => ({ kind: "task" as const, task, at: toMin(task.session.start) })),
  ];
  return { upNext, rows: rows.sort((a, b) => a.at - b.at) };
}
