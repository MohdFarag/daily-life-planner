import { isDeepStrictEqual } from "node:util";

export interface StoredRecord {
  doc: Record<string, unknown>;
  /** Version at which each field last changed. */
  fieldVersions: Record<string, number>;
}

export interface FieldConflict {
  field: string;
  serverValue: unknown;
  serverVersion: number;
}

export interface MergeResult {
  doc: Record<string, unknown>;
  fieldVersions: Record<string, number>;
  changed: boolean;
  conflicts: FieldConflict[];
}

/**
 * Field-level merge (SYN04). A field the client changed is applied unless the server changed that
 * same field after the client's [baseVersion]; then the server value is kept and reported as a
 * conflict. Edits to different fields from different devices therefore both survive.
 */
export function mergeFields(
  stored: StoredRecord,
  incoming: Record<string, unknown>,
  baseVersion: number,
  nextVersion: number,
): MergeResult {
  // Maps, not objects: field names come from the client, so writing them as object keys could
  // reach Object.prototype ("__proto__").
  const doc = new Map(Object.entries(stored.doc));
  const fieldVersions = new Map(Object.entries(stored.fieldVersions));
  const conflicts: FieldConflict[] = [];
  let changed = false;

  for (const [field, value] of Object.entries(incoming)) {
    if (isDeepStrictEqual(doc.get(field), value)) continue;
    const serverVersion = fieldVersions.get(field) ?? 0;
    if (serverVersion > baseVersion) {
      conflicts.push({ field, serverValue: doc.get(field) ?? null, serverVersion });
      continue;
    }
    doc.set(field, value);
    fieldVersions.set(field, nextVersion);
    changed = true;
  }
  return {
    doc: Object.fromEntries(doc),
    fieldVersions: Object.fromEntries(fieldVersions),
    changed,
    conflicts,
  };
}

/** Field versions for a newly created record: every field starts at [version]. */
export function initialFieldVersions(fields: Record<string, unknown>, version: number): Record<string, number> {
  return Object.fromEntries(Object.keys(fields).map((field) => [field, version]));
}
