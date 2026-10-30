/** The parts of the Google Tasks and Calendar APIs the integration uses (v1 / v3 resources). */

export interface GTaskList {
  id: string;
  title: string;
  updated?: string;
}

export interface GTask {
  id: string;
  etag?: string;
  title?: string;
  notes?: string;
  status?: "needsAction" | "completed";
  completed?: string;
  due?: string;
  parent?: string;
  position?: string;
  deleted?: boolean;
  hidden?: boolean;
  updated?: string;
  webViewLink?: string;
  links?: { type: string; description?: string; link: string }[];
}

export interface GCalendar {
  id: string;
  summary: string;
  primary?: boolean;
  accessRole: "freeBusyReader" | "reader" | "writer" | "owner";
  backgroundColor?: string;
  selected?: boolean;
  timeZone?: string;
}

export interface GEventTime {
  dateTime?: string;
  date?: string;
  timeZone?: string;
}

export interface GEvent {
  id: string;
  etag?: string;
  status?: "confirmed" | "tentative" | "cancelled";
  summary?: string;
  description?: string;
  location?: string;
  start?: GEventTime;
  end?: GEventTime;
  recurrence?: string[];
  recurringEventId?: string;
  transparency?: "opaque" | "transparent";
  htmlLink?: string;
  reminders?: { useDefault: boolean; overrides?: { method: "popup" | "email"; minutes: number }[] };
  extendedProperties?: { private?: Record<string, string> };
}

export interface EventPage {
  events: GEvent[];
  /** Present on the last page; null when Google asks for a full resync (410 Gone). */
  nextSyncToken: string | null;
}

export interface GoogleApi {
  taskLists(): Promise<GTaskList[]>;
  insertTaskList(title: string): Promise<GTaskList>;
  patchTaskList(id: string, title: string): Promise<GTaskList>;
  /** Tasks changed since [updatedMin] (all when undefined), including completed, hidden and deleted. */
  tasks(listId: string, updatedMin?: string): Promise<GTask[]>;
  insertTask(listId: string, task: Partial<GTask>, parent?: string): Promise<GTask>;
  patchTask(listId: string, taskId: string, task: Partial<GTask>): Promise<GTask>;
  deleteTask(listId: string, taskId: string): Promise<void>;

  calendars(): Promise<GCalendar[]>;
  insertCalendar(summary: string, timeZone: string): Promise<GCalendar>;
  /** Changed events: incremental with [syncToken], otherwise a full listing within the window. */
  events(calendarId: string, syncToken: string | undefined, window: { from: string; to: string }): Promise<EventPage>;
  insertEvent(calendarId: string, event: Partial<GEvent>): Promise<GEvent>;
  patchEvent(calendarId: string, eventId: string, event: Partial<GEvent>): Promise<GEvent>;
  deleteEvent(calendarId: string, eventId: string): Promise<void>;
}

/** An error answer from Google. */
export class GoogleApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

type Fetch = typeof fetch;

const TASKS = "https://tasks.googleapis.com/tasks/v1";
const CALENDAR = "https://www.googleapis.com/calendar/v3";

/** [GoogleApi] over HTTPS with a bearer token from [accessToken]. */
export class GoogleHttpApi implements GoogleApi {
  constructor(
    private readonly accessToken: () => Promise<string>,
    private readonly fetchImpl: Fetch = fetch,
  ) {}

  async taskLists() {
    return this.pages<GTaskList>(`${TASKS}/users/@me/lists`, { maxResults: "100" });
  }

  insertTaskList(title: string) {
    return this.call<GTaskList>("POST", `${TASKS}/users/@me/lists`, { title });
  }

  patchTaskList(id: string, title: string) {
    return this.call<GTaskList>("PATCH", `${TASKS}/users/@me/lists/${enc(id)}`, { title });
  }

  tasks(listId: string, updatedMin?: string) {
    return this.pages<GTask>(`${TASKS}/lists/${enc(listId)}/tasks`, {
      maxResults: "100",
      showCompleted: "true",
      showHidden: "true",
      showDeleted: "true",
      ...(updatedMin ? { updatedMin } : {}),
    });
  }

  insertTask(listId: string, task: Partial<GTask>, parent?: string) {
    const query = parent ? `?${new URLSearchParams({ parent }).toString()}` : "";
    return this.call<GTask>("POST", `${TASKS}/lists/${enc(listId)}/tasks${query}`, task);
  }

  patchTask(listId: string, taskId: string, task: Partial<GTask>) {
    return this.call<GTask>("PATCH", `${TASKS}/lists/${enc(listId)}/tasks/${enc(taskId)}`, task);
  }

  async deleteTask(listId: string, taskId: string) {
    await this.call("DELETE", `${TASKS}/lists/${enc(listId)}/tasks/${enc(taskId)}`, undefined, [404, 410]);
  }

  calendars() {
    return this.pages<GCalendar>(`${CALENDAR}/users/me/calendarList`, { maxResults: "250" });
  }

  insertCalendar(summary: string, timeZone: string) {
    return this.call<GCalendar>("POST", `${CALENDAR}/calendars`, { summary, timeZone }).then((c) => ({
      ...c,
      accessRole: "owner" as const,
    }));
  }

  async events(calendarId: string, syncToken: string | undefined, window: { from: string; to: string }): Promise<EventPage> {
    const params: Record<string, string> = { maxResults: "250", singleEvents: "true", showDeleted: "true" };
    if (syncToken) params.syncToken = syncToken;
    else Object.assign(params, { timeMin: window.from, timeMax: window.to });
    const events: GEvent[] = [];
    let pageToken: string | undefined;
    for (;;) {
      const url = `${CALENDAR}/calendars/${enc(calendarId)}/events?${new URLSearchParams({ ...params, ...(pageToken ? { pageToken } : {}) }).toString()}`;
      let page: { items?: GEvent[]; nextPageToken?: string; nextSyncToken?: string };
      try {
        page = await this.call("GET", url);
      } catch (err) {
        // The sync token expired: the caller starts a full sync.
        if (err instanceof GoogleApiError && err.status === 410) return { events: [], nextSyncToken: null };
        throw err;
      }
      events.push(...(page.items ?? []));
      if (!page.nextPageToken) return { events, nextSyncToken: page.nextSyncToken ?? null };
      pageToken = page.nextPageToken;
    }
  }

  insertEvent(calendarId: string, event: Partial<GEvent>) {
    return this.call<GEvent>("POST", `${CALENDAR}/calendars/${enc(calendarId)}/events`, event);
  }

  patchEvent(calendarId: string, eventId: string, event: Partial<GEvent>) {
    return this.call<GEvent>("PATCH", `${CALENDAR}/calendars/${enc(calendarId)}/events/${enc(eventId)}`, event);
  }

  async deleteEvent(calendarId: string, eventId: string) {
    await this.call("DELETE", `${CALENDAR}/calendars/${enc(calendarId)}/events/${enc(eventId)}`, undefined, [404, 410]);
  }

  private async pages<T>(url: string, params: Record<string, string>): Promise<T[]> {
    const all: T[] = [];
    let pageToken: string | undefined;
    do {
      const query = new URLSearchParams({ ...params, ...(pageToken ? { pageToken } : {}) });
      const page = await this.call<{ items?: T[]; nextPageToken?: string }>("GET", `${url}?${query.toString()}`);
      all.push(...(page.items ?? []));
      pageToken = page.nextPageToken;
    } while (pageToken);
    return all;
  }

  /** One request; [ignore] lists statuses treated as success (e.g. deleting what is already gone). */
  private async call<T>(method: string, url: string, body?: unknown, ignore: number[] = []): Promise<T> {
    const res = await this.fetchImpl(url, {
      method,
      headers: {
        authorization: `Bearer ${await this.accessToken()}`,
        ...(body === undefined ? {} : { "content-type": "application/json" }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (ignore.includes(res.status)) return undefined as T;
    if (!res.ok) throw new GoogleApiError(res.status, `${method} ${new URL(url).pathname} failed: ${res.status} ${await res.text()}`);
    return (res.status === 204 ? undefined : await res.json()) as T;
  }
}

const enc = encodeURIComponent;
