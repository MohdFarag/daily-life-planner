import { randomUUID } from "node:crypto";
import { startOfDay } from "../../shared/zoned-time.js";
import type { BlockDoc, EventDoc, SessionDoc, TaskDoc } from "../planner/planner.records.js";
import type { GCalendar, GEvent, GTask } from "./google.api.js";

/**
 * Pure translations between the app's records and Google objects. Only fields Google can hold are
 * mapped; the deadline and early target stay app fields, distinct from the Tasks date (NOT03, NOT04).
 */

/** Marks events the app created, so they are recognised when read back. */
export const SESSION_PROPERTY = "dlpSessionId";
export const BLOCK_PROPERTY = "dlpBlockId";

export function taskToGoogle(task: TaskDoc): Partial<GTask> {
  return { title: task.title, notes: task.notes || undefined, status: task.status === "COMPLETED" ? "completed" : "needsAction" };
}

/**
 * The record fields a Google task sets. "In progress" survives while Google still says "needs action",
 * because Google has no such state.
 */
export function taskFromGoogle(
  g: GTask,
  listId: string,
  parentId: string | undefined,
  current: TaskDoc | undefined,
): Record<string, unknown> {
  const completed = g.status === "completed";
  const status = completed ? "COMPLETED" : current?.status === "IN_PROGRESS" ? "IN_PROGRESS" : "NEEDS_ACTION";
  const keep = g.links?.find((l) => l.type === "keep_note" || isKeepUrl(l.link));
  return {
    title: g.title ?? "",
    notes: g.notes ?? "",
    listId,
    status,
    ...(completed && g.completed ? { completedAt: g.completed } : {}),
    parentId: parentId ?? null,
    position: g.position ?? null,
    // TSK02: the source note is linked, never copied into the inbox.
    keepUrl: keep?.link ?? null,
    googleUrl: g.webViewLink ?? null,
  };
}

/** Whether [link] points at Google Keep itself (compared by host, so look-alike hosts don't pass). */
export function isKeepUrl(link: string): boolean {
  try {
    const url = new URL(link);
    return url.protocol === "https:" && url.hostname === "keep.google.com";
  } catch {
    return false;
  }
}

/** A new local task for a task created in Google. */
export function newTaskFromGoogle(g: GTask, listId: string, parentId: string | undefined, now: Date): Record<string, unknown> {
  return {
    id: randomUUID(),
    tags: [],
    important: false,
    createdAt: now.toISOString(),
    ...taskFromGoogle(g, listId, parentId, undefined),
  };
}

/** A session as a normal event with its own reminder (SES01, NOT01). */
export function sessionToEvent(session: SessionDoc, taskTitle: string, reminderMinutes: number): Partial<GEvent> {
  return {
    summary: taskTitle,
    description: "Work session planned in Daily Life Planner.",
    start: { dateTime: session.start },
    end: { dateTime: session.end },
    reminders: { useDefault: false, overrides: reminderMinutes > 0 ? [{ method: "popup", minutes: reminderMinutes }] : [] },
    extendedProperties: { private: { [SESSION_PROPERTY]: session.id } },
  };
}

const RRULE_DAYS: Record<string, string> = {
  MONDAY: "MO",
  TUESDAY: "TU",
  WEDNESDAY: "WE",
  THURSDAY: "TH",
  FRIDAY: "FR",
  SATURDAY: "SA",
  SUNDAY: "SU",
};

/**
 * A time block as a weekly recurring event in the blocks calendar (BLK01). Protected blocks show as
 * busy to others; guiding blocks stay free (BLK03).
 */
export function blockToEvent(block: BlockDoc, timeZone: string, firstDay: string): Partial<GEvent> {
  const days = block.days.map((d) => RRULE_DAYS[d]).filter(Boolean);
  const local = (time: string) => `${firstDay}T${time.slice(0, 5)}:00`;
  const crossesMidnight = block.end <= block.start;
  const endDay = crossesMidnight ? nextDay(firstDay) : firstDay;
  return {
    summary: block.name,
    start: { dateTime: local(block.start), timeZone },
    end: { dateTime: `${endDay}T${block.end.slice(0, 5)}:00`, timeZone },
    recurrence: days.length ? [`RRULE:FREQ=WEEKLY;BYDAY=${days.join(",")}`] : [],
    transparency: block.policy === "PROTECTED" ? "opaque" : "transparent",
    reminders: { useDefault: false, overrides: [] },
    extendedProperties: { private: { [BLOCK_PROPERTY]: block.id } },
  };
}

/** An event the user created in the app, for Google. */
export function eventToGoogle(event: EventDoc, timeZone: string): Partial<GEvent> {
  const time = (iso: string): GEvent["start"] => (event.allDay ? { date: iso.slice(0, 10) } : { dateTime: iso, timeZone });
  return {
    summary: event.title,
    description: event.description ?? undefined,
    location: event.location ?? undefined,
    start: time(event.start),
    end: time(event.end),
  };
}

/** The record fields of a Google event. All-day events span local midnights in [timeZone]. */
export function eventFromGoogle(g: GEvent, calendar: GCalendar, timeZone: string): Record<string, unknown> | null {
  const start = instant(g.start, timeZone);
  const end = instant(g.end, timeZone);
  if (!start || !end) return null;
  return {
    title: g.summary ?? "",
    start,
    end,
    calendarId: calendar.id,
    readOnly: calendar.accessRole !== "owner" && calendar.accessRole !== "writer",
    allDay: Boolean(g.start?.date),
    description: g.description ?? null,
    location: g.location ?? null,
    googleId: g.id,
    recurringEventId: g.recurringEventId ?? null,
    htmlLink: g.htmlLink ?? null,
  };
}

export function newEventId(): string {
  return `g-${randomUUID()}`;
}

function instant(t: GEvent["start"], timeZone: string): string | null {
  if (t?.dateTime) return new Date(t.dateTime).toISOString();
  if (t?.date) return startOfDay(t.date, t.timeZone ?? timeZone).toISOString();
  return null;
}

function nextDay(date: string): string {
  const d = new Date(`${date}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + 1);
  return d.toISOString().slice(0, 10);
}
