import { randomUUID } from "node:crypto";
import type { z } from "zod";
import { badRequest, conflict, notFound } from "../../shared/errors.js";
import { isTimeZone, localDate, startOfDay } from "../../shared/zoned-time.js";
import type { Change, SyncService } from "../sync/sync.service.js";
import type { Entity } from "../sync/entities.js";
import { ItemDoc, ListDoc, newItem, newSession, newTask, SessionDoc, TaskDoc } from "./planner.records.js";

const DAY_MS = 86_400_000;
const DEFAULT_LIST = "personal";

/** A user's planning data, read from their synced records. */
export interface PlannerData {
  items: ItemDoc[];
  tasks: TaskDoc[];
  sessions: SessionDoc[];
  lists: ListDoc[];
  timeZone: string;
  versions: Map<string, number>;
}

export interface PlannedSession {
  sessionId: string;
  taskId: string;
  title: string;
  start: string;
  end: string;
  done: boolean;
}

/**
 * Planning operations on the user's synced records: the application services MCP clients use
 * (MCP01). Every write goes through [SyncService], like a device's, so it reaches all devices.
 */
export class PlannerService {
  constructor(
    private readonly sync: SyncService,
    private readonly clock: () => Date = () => new Date(),
  ) {}

  async load(userId: string): Promise<PlannerData> {
    const records = await this.sync.liveRecords(userId);
    const of = <S extends z.ZodType>(entity: Entity, schema: S): z.infer<S>[] =>
      records.filter((r) => r.entity === entity).flatMap((r) => parse(schema, r));
    const prayer = records.find((r) => r.entity === "settings" && r.id === "prayer")?.doc.zone;
    return {
      items: of("items", ItemDoc),
      tasks: of("tasks", TaskDoc),
      sessions: of("sessions", SessionDoc),
      lists: of("lists", ListDoc),
      timeZone: typeof prayer === "string" && isTimeZone(prayer) ? prayer : "UTC",
      versions: new Map(records.map((r) => [key(r.entity, r.id), r.version])),
    };
  }

  /** Sessions on a local date (default today), in time order, with their task titles. */
  async dailyPlan(userId: string, date?: string) {
    const data = await this.load(userId);
    const day = date ?? localDate(this.clock(), data.timeZone);
    const from = startOfDay(day, data.timeZone).getTime();
    const titles = new Map(data.tasks.map((t) => [t.id, t]));
    const sessions: PlannedSession[] = data.sessions
      .filter((s) => s.state !== "CANCELED" && Date.parse(s.start) < from + DAY_MS && Date.parse(s.end) > from)
      .sort((a, b) => Date.parse(a.start) - Date.parse(b.start))
      .map((s) => ({
        sessionId: s.id,
        taskId: s.taskId,
        title: titles.get(s.taskId)?.title ?? "(deleted task)",
        start: s.start,
        end: s.end,
        done: s.state === "DONE" || titles.get(s.taskId)?.status === "COMPLETED",
      }));
    return { date: day, timeZone: data.timeZone, sessions };
  }

  async inbox(userId: string, limit = 50) {
    const { items } = await this.load(userId);
    const open = items.filter((i) => i.state === "INBOX").sort((a, b) => a.capturedAt.localeCompare(b.capturedAt));
    return {
      total: open.length,
      items: open.slice(0, limit).map(({ id, kind, title, body, url, capturedAt }) => ({ id, kind, title, body, url, capturedAt })),
    };
  }

  async addInboxItem(userId: string, fields: { title: string; body?: string; url?: string }) {
    const item = newItem(fields, this.clock());
    await this.save(userId, "items", item.id, item, 0);
    return { id: item.id };
  }

  /** Turns an inbox item into one or several tasks and marks it processed (TRI03). */
  async convertItem(userId: string, itemId: string, tasks: { title: string; notes?: string; listId?: string; estimateMin?: number }[]) {
    const data = await this.load(userId);
    const item = data.items.find((i) => i.id === itemId);
    if (!item) throw notFound("Inbox item");
    if (item.state !== "INBOX") throw conflict("not_in_inbox", "The item has already been processed");
    const lists = new Set(data.lists.map((l) => l.id));
    const now = this.clock();
    const created = tasks.map((t) => {
      const listId = t.listId ?? DEFAULT_LIST;
      if (!lists.has(listId)) throw badRequest("unknown_list", `No list with id ${listId}`);
      return newTask({ ...t, listId, sourceItemId: item.id }, now);
    });
    for (const task of created) await this.save(userId, "tasks", task.id, task, 0);
    await this.save(
      userId,
      "items",
      item.id,
      { state: "PROCESSED", completedAt: now.toISOString(), derivedTaskIds: [...item.derivedTaskIds, ...created.map((t) => t.id)] },
      data.versions.get(key("items", item.id)) ?? 0,
    );
    return { taskIds: created.map((t) => t.id) };
  }

  async tasks(userId: string, filter: { status?: "open" | "completed" | "all"; listId?: string } = {}) {
    const { tasks, lists } = await this.load(userId);
    const status = filter.status ?? "open";
    const names = new Map(lists.map((l) => [l.id, l.name]));
    return tasks
      .filter((t) => (status === "all" ? true : status === "open" ? t.status !== "COMPLETED" : t.status === "COMPLETED"))
      .filter((t) => !filter.listId || t.listId === filter.listId)
      .map(({ id, title, notes, listId, status: s, important, estimateMin, deadline }) => ({
        id,
        title,
        notes,
        list: names.get(listId) ?? listId,
        status: s,
        important,
        estimateMin,
        deadline,
      }));
  }

  async completeTask(userId: string, taskId: string) {
    const data = await this.load(userId);
    const task = data.tasks.find((t) => t.id === taskId);
    if (!task) throw notFound("Task");
    if (task.status === "COMPLETED") return { alreadyCompleted: true };
    await this.save(
      userId,
      "tasks",
      taskId,
      { status: "COMPLETED", completedAt: this.clock().toISOString() },
      data.versions.get(key("tasks", taskId)) ?? 0,
    );
    return { alreadyCompleted: false };
  }

  /**
   * Checks a proposed session against the other planned sessions. Prayer times and time blocks are
   * calculated on the device, which re-checks them and marks conflicts "Needs review" (REL04).
   */
  async previewSession(userId: string, taskId: string, start: Date, durationMin: number) {
    const data = await this.load(userId);
    if (!data.tasks.some((t) => t.id === taskId)) throw notFound("Task");
    const end = new Date(start.getTime() + durationMin * 60_000);
    const titles = new Map(data.tasks.map((t) => [t.id, t.title]));
    const conflicts = data.sessions
      .filter((s) => s.taskId !== taskId && s.state !== "CANCELED")
      .filter((s) => Date.parse(s.start) < end.getTime() && Date.parse(s.end) > start.getTime())
      .map((s) => ({ taskId: s.taskId, title: titles.get(s.taskId) ?? "", start: s.start, end: s.end }));
    return { start: start.toISOString(), end: end.toISOString(), conflicts };
  }

  /** Plans a session; overlapping sessions need [allowConflicts] (PLN02, AC11). */
  async scheduleSession(userId: string, taskId: string, start: Date, durationMin: number, allowConflicts: boolean) {
    const preview = await this.previewSession(userId, taskId, start, durationMin);
    if (preview.conflicts.length > 0 && !allowConflicts) {
      throw conflict("schedule_conflict", `Overlaps ${preview.conflicts.map((c) => c.title).join(", ")}; preview first or allow conflicts`);
    }
    const session = newSession(taskId, start, new Date(preview.end));
    await this.save(userId, "sessions", session.id, session, 0);
    return { sessionId: session.id, ...preview };
  }

  /** What a review should look at (PLN03): unprocessed items, deadlines, follow-ups, someday. */
  async review(userId: string) {
    const data = await this.load(userId);
    const today = localDate(this.clock(), data.timeZone);
    const weekAhead = localDate(new Date(startOfDay(today, data.timeZone).getTime() + 7 * DAY_MS), data.timeZone);
    const open = data.tasks.filter((t) => t.status !== "COMPLETED");
    const inbox = data.items.filter((i) => i.state === "INBOX");
    const brief = (t: TaskDoc) => ({ id: t.id, title: t.title, deadline: t.deadline });
    return {
      today,
      inbox: { count: inbox.length, oldest: inbox.map((i) => i.capturedAt).sort()[0] ?? null },
      overdue: open.filter((t) => t.deadline && t.deadline < today).map(brief),
      dueThisWeek: open.filter((t) => t.deadline && t.deadline >= today && t.deadline <= weekAhead).map(brief),
      followUpsDue: data.items
        .filter((i) => i.state === "WAITING" && i.waiting?.followUpOn && i.waiting.followUpOn <= today)
        .map((i) => ({ id: i.id, title: i.title, who: i.waiting?.who })),
      somedayToReview: data.items
        .filter((i) => i.state === "SOMEDAY" && i.reviewOn && i.reviewOn <= today)
        .map((i) => ({ id: i.id, title: i.title })),
    };
  }

  private async save(userId: string, entity: Entity, id: string, fields: Record<string, unknown>, baseVersion: number) {
    const { results } = await this.sync.push(userId, [
      { mutationId: `server-${randomUUID()}`, entity, id, op: "upsert", baseVersion, fields },
    ]);
    const result = results[0];
    if (result?.status === "rejected") throw conflict("rejected", result.reason ?? "The change was rejected");
  }
}

const key = (entity: string, id: string) => `${entity}/${id}`;

function parse<S extends z.ZodType>(schema: S, record: Change): z.infer<S>[] {
  const parsed = schema.safeParse({ ...record.doc, id: record.doc.id ?? record.id });
  return parsed.success ? [parsed.data] : [];
}
