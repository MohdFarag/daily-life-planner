import { localDate } from "../../shared/zoned-time.js";
import { BlockDoc, EventDoc, SessionDoc, TaskDoc } from "../planner/planner.records.js";
import type { GCalendar, GEvent } from "./google.api.js";
import type { SyncContext } from "./google.context.js";
import { blockToEvent, eventFromGoogle, eventToGoogle, newEventId, sessionToEvent } from "./google.mapping.js";
import type { LinkKind, LinkRow } from "./google.repository.js";

const DAY_MS = 86_400_000;
/** Events kept on devices: a week back, three months ahead. */
const WINDOW_BACK_DAYS = 7;
const WINDOW_AHEAD_DAYS = 90;
const SETTINGS_KEY = "calendars";

/** The app's own calendars, whose events are mirrors of local records and never read back. */
export interface AppCalendars {
  sessions: string;
  blocks: string;
}

/** A calendar as devices see it (settings record "calendars"); the user picks visibility and default (CAL02). */
interface CalendarEntry {
  id: string;
  summary: string;
  primary: boolean;
  accessRole: string;
  color: string | null;
  visible: boolean;
  appRole: "sessions" | "blocks" | null;
}

/**
 * Refreshes the calendar list in the user's settings, keeping their visibility choices and
 * default calendar. Returns the calendars whose events should be read.
 */
export async function syncCalendarList(ctx: SyncContext, app: AppCalendars): Promise<GCalendar[]> {
  const calendars = await ctx.api.calendars();
  const current = ctx.raw("settings", SETTINGS_KEY)?.doc;
  const previous = new Map((Array.isArray(current?.list) ? (current.list as CalendarEntry[]) : []).map((c) => [c.id, c]));
  const list: CalendarEntry[] = calendars.map((c) => ({
    id: c.id,
    summary: c.summary,
    primary: c.primary === true,
    accessRole: c.accessRole,
    color: c.backgroundColor ?? null,
    // New calendars show when Google shows them; the app's own are shown as sessions and blocks instead.
    visible: previous.get(c.id)?.visible ?? (c.selected === true || c.primary === true),
    appRole: c.id === app.sessions ? "sessions" : c.id === app.blocks ? "blocks" : null,
  }));
  const writable = list.filter((c) => (c.accessRole === "owner" || c.accessRole === "writer") && !c.appRole);
  const chosen = typeof current?.defaultCalendarId === "string" ? current.defaultCalendarId : undefined;
  const defaultCalendarId = writable.some((c) => c.id === chosen) ? chosen : ((writable.find((c) => c.primary) ?? writable[0])?.id ?? null);
  if (JSON.stringify(current?.list) !== JSON.stringify(list) || current?.defaultCalendarId !== defaultCalendarId) {
    await ctx.upsert("settings", SETTINGS_KEY, { _key: SETTINGS_KEY, list, defaultCalendarId });
  }
  const readable = new Set(list.filter((c) => c.visible && !c.appRole).map((c) => c.id));
  return calendars.filter((c) => readable.has(c.id));
}

/** The default calendar for events created on a device without one. */
export function defaultCalendarId(ctx: SyncContext): string | undefined {
  const id = ctx.raw("settings", SETTINGS_KEY)?.doc.defaultCalendarId;
  return typeof id === "string" ? id : undefined;
}

/**
 * Publishes planned sessions as events in the sessions calendar, with a Google reminder (SES01,
 * NOT01). The session records which event mirrors it; until then it shows as waiting (NOT02).
 */
export async function pushSessions(ctx: SyncContext, calendarId: string, reminderMinutes: number): Promise<void> {
  const tasks = ctx.all("tasks", TaskDoc);
  const from = ctx.now.getTime() - DAY_MS;
  const live = [...ctx.all("sessions", SessionDoc)].filter(
    ([, s]) =>
      s.doc.state !== "CANCELED" &&
      Date.parse(s.doc.end) > from &&
      Date.parse(s.doc.start) < ctx.now.getTime() + WINDOW_AHEAD_DAYS * DAY_MS,
  );
  await mirror(ctx, "session", calendarId, live, (id, doc) => {
    const title = tasks.get(doc.taskId)?.doc.title ?? "Session";
    return {
      event: sessionToEvent(doc, title, reminderMinutes),
      writeBack: (googleId) => ctx.upsert("sessions", id, { googleEventId: googleId }),
    };
  });
}

/** Publishes time blocks as weekly recurring events in the blocks calendar (BLK01, BLK03). */
export async function pushBlocks(ctx: SyncContext, calendarId: string, timeZone: string): Promise<void> {
  const today = localDate(ctx.now, timeZone);
  await mirror(ctx, "block", calendarId, [...ctx.all("blocks", BlockDoc)], (_id, doc) => ({ event: blockToEvent(doc, timeZone, today) }));
}

/**
 * Keeps one Google event per local record: inserts new ones, patches changed ones, deletes those
 * whose record is gone or no longer qualifies.
 */
async function mirror<T>(
  ctx: SyncContext,
  kind: LinkKind,
  calendarId: string,
  records: [string, { doc: T; version: number }][],
  build: (id: string, doc: T) => { event: Partial<GEvent>; writeBack?: (googleId: string) => Promise<number> },
): Promise<void> {
  const links = new Map((await ctx.links(kind)).map((l) => [l.localId, l]));
  const wanted = new Set(records.map(([id]) => id));
  for (const [id, { doc, version }] of records) {
    const link = links.get(id);
    if (link && link.containerId === calendarId && version <= link.localVersion) continue;
    await ctx.attempt(`${kind} ${id}`, async () => {
      const { event, writeBack } = build(id, doc);
      let saved: GEvent;
      if (link && link.containerId === calendarId) {
        saved = await ctx.api.patchEvent(calendarId, link.googleId, event);
      } else {
        if (link) await ctx.api.deleteEvent(link.containerId, link.googleId);
        saved = await ctx.api.insertEvent(calendarId, event);
      }
      const localVersion = writeBack && saved.id !== link?.googleId ? await writeBack(saved.id) : version;
      await ctx.saveLink({ kind, localId: id, googleId: saved.id, containerId: calendarId, etag: saved.etag ?? null, localVersion });
    });
  }
  for (const link of links.values()) {
    if (wanted.has(link.localId)) continue;
    await ctx.attempt(`removed ${kind} ${link.localId}`, async () => {
      await ctx.api.deleteEvent(link.containerId, link.googleId);
      await ctx.deleteLink(kind, link.localId);
    });
  }
}

/**
 * Sends events created or edited on a device to Google (CAL01): to their calendar, or the
 * default one. Read-only events and events from hidden calendars stay as they are.
 */
export async function pushLocalEvents(ctx: SyncContext, timeZone: string): Promise<void> {
  const links = new Map((await ctx.links("event")).map((l) => [l.localId, l]));
  const fallback = defaultCalendarId(ctx);
  for (const [id, { doc, version }] of ctx.all("events", EventDoc)) {
    const link = links.get(id);
    if (doc.readOnly || (link && version <= link.localVersion)) continue;
    const calendarId = doc.calendarId || fallback;
    if (!calendarId) continue;
    await ctx.attempt(`event ${doc.title}`, async () => {
      const body = eventToGoogle(doc, timeZone);
      const saved = link ? await ctx.api.patchEvent(link.containerId, link.googleId, body) : await ctx.api.insertEvent(calendarId, body);
      const v = link ? version : await ctx.upsert("events", id, { googleId: saved.id, calendarId });
      await ctx.saveLink({
        kind: "event",
        localId: id,
        googleId: saved.id,
        containerId: link?.containerId ?? calendarId,
        etag: saved.etag ?? null,
        localVersion: v,
      });
    });
  }
  for (const link of links.values()) {
    if (ctx.raw("events", link.localId)) continue;
    await ctx.attempt(`deleted event ${link.localId}`, async () => {
      await ctx.api.deleteEvent(link.containerId, link.googleId);
      await ctx.deleteLink("event", link.localId);
    });
  }
}

/**
 * Reads changed events from the visible calendars with incremental sync tokens (SYN02); an expired
 * token starts a full listing of the window. Events of calendars the user hid are removed.
 */
export async function pullEvents(ctx: SyncContext, calendars: GCalendar[], timeZone: string): Promise<void> {
  const window = {
    from: new Date(ctx.now.getTime() - WINDOW_BACK_DAYS * DAY_MS).toISOString(),
    to: new Date(ctx.now.getTime() + WINDOW_AHEAD_DAYS * DAY_MS).toISOString(),
  };
  const links = await ctx.links("event");
  const byGoogle = new Map(links.map((l) => [`${l.containerId}/${l.googleId}`, l]));
  for (const calendar of calendars) {
    await ctx.attempt(`calendar ${calendar.summary}`, async () => {
      const scope = `events:${calendar.id}`;
      let page = await ctx.api.events(calendar.id, await ctx.cursor(scope), window);
      if (page.nextSyncToken === null) page = await ctx.api.events(calendar.id, undefined, window);
      for (const g of page.events) await applyGoogleEvent(ctx, g, calendar, timeZone, byGoogle);
      await ctx.saveCursor(scope, page.nextSyncToken);
    });
  }
  const visible = new Set(calendars.map((c) => c.id));
  for (const link of links) {
    if (visible.has(link.containerId)) continue;
    const doc = ctx.raw("events", link.localId)?.doc;
    // Only events read from a calendar are removed when it is hidden, not ones made on a device.
    if (doc && doc.calendarId === link.containerId) {
      await ctx.attempt(`hidden event ${link.localId}`, async () => {
        await ctx.remove("events", link.localId);
        await ctx.deleteLink("event", link.localId);
        await ctx.saveCursor(`events:${link.containerId}`, null);
      });
    }
  }
}

async function applyGoogleEvent(ctx: SyncContext, g: GEvent, calendar: GCalendar, timeZone: string, byGoogle: Map<string, LinkRow>) {
  const key = `${calendar.id}/${g.id}`;
  const link = byGoogle.get(key);
  if (g.status === "cancelled") {
    if (link) {
      await ctx.remove("events", link.localId);
      await ctx.deleteLink("event", link.localId);
      byGoogle.delete(key);
    }
    return;
  }
  if (link && g.etag && g.etag === link.etag) return;
  const fields = eventFromGoogle(g, calendar, timeZone);
  if (!fields) return;
  const id = link?.localId ?? newEventId();
  const v = await ctx.upsert("events", id, link ? fields : { id, ...fields });
  const next: LinkRow = { kind: "event", localId: id, googleId: g.id, containerId: calendar.id, etag: g.etag ?? null, localVersion: v };
  byGoogle.set(key, next);
  await ctx.saveLink(next);
}
