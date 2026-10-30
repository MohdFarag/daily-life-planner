import { z } from "zod";

/**
 * Synchronized entity types and the shape of their known fields. Documents are partial on push
 * (only changed fields are sent) and may carry extra fields from newer app versions.
 */
// Timestamps are ISO-8601 instants (e.g. Kotlin Instant.toString()); server jobs parse them.
const iso = z.iso.datetime({ offset: true });
const shapes = {
  items: {
    kind: z.string().max(16),
    title: z.string().max(2000),
    body: z.string().max(200_000),
    state: z.string().max(16),
    capturedAt: iso,
    purgeAt: iso,
  },
  tasks: { title: z.string().max(2000), notes: z.string().max(200_000), listId: z.string().max(128), status: z.string().max(16) },
  lists: { name: z.string().max(200) },
  sessions: { taskId: z.string().max(128), start: iso, end: iso, state: z.string().max(16) },
  rules: { taskId: z.string().max(128) },
  blocks: { name: z.string().max(200), start: z.string().max(8), end: z.string().max(8), policy: z.string().max(16) },
  // Calendar events: read from Google, or created on a device and sent to Google (CAL01).
  events: { title: z.string().max(2000), start: iso, end: iso, calendarId: z.string().max(1024) },
  settings: {},
  timeEntries: { taskId: z.string().max(128).nullable(), kind: z.string().max(16), start: iso, end: iso },
} satisfies Record<string, z.ZodRawShape>;

export const ENTITIES = Object.keys(shapes) as [keyof typeof shapes, ...(keyof typeof shapes)[]];
export type Entity = (typeof ENTITIES)[number];
export const EntityName = z.enum(ENTITIES);

const FIELD_NAME = /^[A-Za-z_][A-Za-z0-9_]{0,63}$/;
const MAX_FIELDS_BYTES = 512 * 1024;

/** Validates the fields of an upsert against the entity's known field types. */
export function validateFields(entity: Entity, fields: Record<string, unknown>): string | null {
  const names = Object.keys(fields);
  if (names.length === 0) return "No fields to update";
  if (names.length > 100) return "Too many fields";
  const bad = names.find((n) => !FIELD_NAME.test(n));
  if (bad) return `Invalid field name: ${bad}`;
  if (Buffer.byteLength(JSON.stringify(fields)) > MAX_FIELDS_BYTES) return "Document is too large";
  const result = z.object(shapes[entity]).partial().loose().safeParse(fields);
  return result.success ? null : result.error.issues.map((i) => `${i.path.join(".")}: ${i.message}`).join("; ");
}
