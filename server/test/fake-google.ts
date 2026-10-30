import type { EventPage, GCalendar, GEvent, GoogleApi, GTask, GTaskList } from "../src/modules/google/google.api.js";

/**
 * An in-memory Google Tasks and Calendar with etags, update times and sync tokens, recording every
 * write so tests can check that a sync sends each change once.
 */
export class FakeGoogle implements GoogleApi {
  lists: GTaskList[] = [{ id: "gl-default", title: "My Tasks" }];
  tasksByList = new Map<string, GTask[]>([["gl-default", []]]);
  calendarList: GCalendar[] = [{ id: "primary-cal", summary: "me@example.com", primary: true, accessRole: "owner", selected: true }];
  eventsByCalendar = new Map<string, (GEvent & { changedAt: number })[]>([["primary-cal", []]]);
  writes: string[] = [];
  private clock = 1;

  private tick() {
    return this.clock++;
  }

  // Tasks ------------------------------------------------------------------------------------

  async taskLists() {
    return this.lists.map((l) => ({ ...l }));
  }

  async insertTaskList(title: string) {
    const list = { id: `gl-${this.tick()}`, title };
    this.lists.push(list);
    this.tasksByList.set(list.id, []);
    this.writes.push(`insertTaskList ${title}`);
    return list;
  }

  async patchTaskList(id: string, title: string) {
    const list = this.lists.find((l) => l.id === id)!;
    list.title = title;
    this.writes.push(`patchTaskList ${title}`);
    return { ...list };
  }

  async tasks(listId: string, updatedMin?: string) {
    return (this.tasksByList.get(listId) ?? []).filter((t) => !updatedMin || (t.updated ?? "") >= updatedMin).map((t) => ({ ...t }));
  }

  async insertTask(listId: string, task: Partial<GTask>, parent?: string) {
    const created = this.stamp({ ...task, id: `gt-${this.tick()}`, ...(parent ? { parent } : {}) });
    this.tasksByList.get(listId)!.push(created);
    this.writes.push(`insertTask ${task.title ?? ""}`);
    return { ...created };
  }

  async patchTask(listId: string, taskId: string, patch: Partial<GTask>) {
    const task = this.task(listId, taskId);
    Object.assign(task, patch);
    this.stamp(task);
    this.writes.push(`patchTask ${task.title ?? ""}`);
    return { ...task };
  }

  async deleteTask(listId: string, taskId: string) {
    const task = this.task(listId, taskId);
    task.deleted = true;
    this.stamp(task);
    this.writes.push(`deleteTask ${task.title ?? ""}`);
  }

  async moveTask(listId: string, taskId: string, parent?: string, previous?: string) {
    const task = this.task(listId, taskId);
    task.parent = parent;
    task.position = previous ? `${this.task(listId, previous).position ?? "0"}5` : "0";
    this.stamp(task);
    this.writes.push(`moveTask ${task.title ?? ""}`);
    return { ...task };
  }

  /** A change made by the user in Google (not through the API under test). */
  userEditsTask(listId: string, taskId: string, patch: Partial<GTask>) {
    this.stamp(Object.assign(this.task(listId, taskId), patch));
  }

  task(listId: string, taskId: string): GTask {
    return this.tasksByList.get(listId)!.find((t) => t.id === taskId)!;
  }

  private stamp<T extends GTask>(task: T): T {
    const n = this.tick();
    task.etag = `"e${n}"`;
    task.updated = new Date(Date.now() + n).toISOString();
    return task;
  }

  // Calendar ---------------------------------------------------------------------------------

  async calendars() {
    return this.calendarList.map((c) => ({ ...c }));
  }

  async insertCalendar(summary: string) {
    const calendar: GCalendar = { id: `cal-${this.tick()}`, summary, accessRole: "owner", selected: true };
    this.calendarList.push(calendar);
    this.eventsByCalendar.set(calendar.id, []);
    this.writes.push(`insertCalendar ${summary}`);
    return { ...calendar };
  }

  async events(calendarId: string, syncToken: string | undefined): Promise<EventPage> {
    const since = syncToken ? Number(syncToken) : 0;
    const events = (this.eventsByCalendar.get(calendarId) ?? []).filter((e) => e.changedAt > since);
    return { events: events.map(({ changedAt: _, ...e }) => ({ ...e })), nextSyncToken: String(this.clock) };
  }

  async insertEvent(calendarId: string, event: Partial<GEvent>) {
    const created = { ...event, id: `ev-${this.tick()}`, status: "confirmed" as const, etag: "", changedAt: 0 };
    this.touch(created);
    this.eventsByCalendar.get(calendarId)!.push(created);
    this.writes.push(`insertEvent ${calendarId} ${event.summary ?? ""}`);
    return { ...created };
  }

  async patchEvent(calendarId: string, eventId: string, patch: Partial<GEvent>) {
    const event = this.event(calendarId, eventId);
    Object.assign(event, patch);
    this.touch(event);
    this.writes.push(`patchEvent ${event.summary ?? ""}`);
    return { ...event };
  }

  async deleteEvent(calendarId: string, eventId: string) {
    const event = this.event(calendarId, eventId);
    event.status = "cancelled";
    this.touch(event);
    this.writes.push(`deleteEvent ${event.summary ?? ""}`);
  }

  userAddsEvent(calendarId: string, event: Partial<GEvent>): string {
    const created = { ...event, id: `ev-${this.tick()}`, status: "confirmed" as const, etag: "", changedAt: 0 };
    this.touch(created);
    this.eventsByCalendar.get(calendarId)!.push(created);
    return created.id;
  }

  userCancelsEvent(calendarId: string, eventId: string) {
    const event = this.event(calendarId, eventId);
    event.status = "cancelled";
    this.touch(event);
  }

  event(calendarId: string, eventId: string) {
    return this.eventsByCalendar.get(calendarId)!.find((e) => e.id === eventId)!;
  }

  live(calendarId: string) {
    return (this.eventsByCalendar.get(calendarId) ?? []).filter((e) => e.status !== "cancelled");
  }

  private touch(event: GEvent & { changedAt: number }) {
    event.changedAt = this.tick();
    event.etag = `"v${event.changedAt}"`;
  }
}
