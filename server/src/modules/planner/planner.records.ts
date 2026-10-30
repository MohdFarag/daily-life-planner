import { randomUUID } from "node:crypto";
import { z } from "zod";

/**
 * The record documents the Android app stores and syncs (see its `data/Codec.kt`). Records written
 * here use exactly these shapes so every device can read them; reading is lenient, because records
 * come from devices and may carry fields from newer app versions.
 */

const iso = z.iso.datetime({ offset: true });
const day = z.iso.date();

export const ItemDoc = z.looseObject({
  id: z.string(),
  kind: z.string(),
  title: z.string(),
  body: z.string().default(""),
  url: z.string().optional(),
  source: z.looseObject({ service: z.string() }),
  state: z.string(),
  capturedAt: iso,
  derivedTaskIds: z.array(z.string()).default([]),
  waiting: z.looseObject({ who: z.string(), followUpOn: day.optional() }).optional(),
  reviewOn: day.optional(),
});
export type ItemDoc = z.infer<typeof ItemDoc>;

export const TaskDoc = z.looseObject({
  id: z.string(),
  title: z.string(),
  notes: z.string().default(""),
  listId: z.string(),
  status: z.enum(["NEEDS_ACTION", "IN_PROGRESS", "COMPLETED"]),
  important: z.boolean().default(false),
  estimateMin: z.number().int().optional(),
  deadline: day.optional(),
  sourceItemId: z.string().optional(),
  createdAt: iso,
  completedAt: iso.optional(),
});
export type TaskDoc = z.infer<typeof TaskDoc>;

export const SessionDoc = z.looseObject({
  id: z.string(),
  taskId: z.string(),
  start: iso,
  end: iso,
  state: z.enum(["PLANNED", "NEEDS_REVIEW", "DONE", "CANCELED"]),
});
export type SessionDoc = z.infer<typeof SessionDoc>;

export const ListDoc = z.looseObject({ id: z.string(), name: z.string() });
export type ListDoc = z.infer<typeof ListDoc>;

export const BlockDoc = z.looseObject({
  id: z.string(),
  name: z.string(),
  start: z.string().regex(/^\d{2}:\d{2}/),
  end: z.string().regex(/^\d{2}:\d{2}/),
  days: z.array(z.string()).default([]),
  policy: z.enum(["GUIDING", "PROTECTED"]),
});
export type BlockDoc = z.infer<typeof BlockDoc>;

/** A calendar event (CAL01). [googleId] is set once it exists in Google. */
export const EventDoc = z.looseObject({
  id: z.string(),
  title: z.string(),
  start: iso,
  end: iso,
  calendarId: z.string(),
  readOnly: z.boolean().default(false),
  allDay: z.boolean().default(false),
  description: z.string().nullish(),
  location: z.string().nullish(),
  googleId: z.string().nullish(),
});
export type EventDoc = z.infer<typeof EventDoc>;

/** Where records created through MCP say they came from (shown on the device). */
export const MCP_SOURCE = "mcp";

export function newItem(fields: { title: string; body?: string; url?: string }, now: Date): ItemDoc {
  return {
    id: randomUUID(),
    kind: fields.url ? "LINK" : "TEXT",
    title: fields.title,
    body: fields.body ?? "",
    ...(fields.url ? { url: fields.url } : {}),
    source: { service: MCP_SOURCE },
    state: "INBOX",
    capturedAt: now.toISOString(),
    attachments: [],
    entries: [],
    derivedTaskIds: [],
  };
}

export function newTask(
  fields: { title: string; notes?: string; listId: string; estimateMin?: number; sourceItemId?: string },
  now: Date,
): TaskDoc {
  return {
    id: randomUUID(),
    title: fields.title,
    notes: fields.notes ?? "",
    listId: fields.listId,
    tags: [],
    important: false,
    status: "NEEDS_ACTION",
    ...(fields.estimateMin ? { estimateMin: fields.estimateMin } : {}),
    ...(fields.sourceItemId ? { sourceItemId: fields.sourceItemId } : {}),
    createdAt: now.toISOString(),
  };
}

export function newSession(taskId: string, start: Date, end: Date): SessionDoc {
  return { id: randomUUID(), taskId, start: start.toISOString(), end: end.toISOString(), state: "PLANNED", pinned: false };
}
