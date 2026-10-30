import { randomUUID } from "node:crypto";
import type { z } from "zod";
import type { Db } from "../../shared/db.js";
import type { Entity } from "../sync/entities.js";
import type { SyncService } from "../sync/sync.service.js";
import type { GoogleApi } from "./google.api.js";
import { googleRepository as repo, type LinkKind, type LinkRow } from "./google.repository.js";

export interface LocalRecord<T> {
  doc: T;
  version: number;
}

/**
 * What one Google sync run works with: the user's records as loaded at the start, writes back to
 * them through [SyncService] (so devices receive them like any change), the Google API, and the
 * links between both. Steps record their failures instead of stopping the run.
 */
export class SyncContext {
  readonly errors: string[] = [];
  private records = new Map<string, Map<string, LocalRecord<Record<string, unknown>>>>();

  constructor(
    readonly userId: string,
    readonly api: GoogleApi,
    private readonly db: Db,
    private readonly sync: SyncService,
    readonly now: Date,
  ) {}

  async load(): Promise<void> {
    this.records.clear();
    for (const r of await this.sync.liveRecords(this.userId)) {
      const byId = this.records.get(r.entity) ?? new Map<string, LocalRecord<Record<string, unknown>>>();
      byId.set(r.id, { doc: { ...r.doc, id: r.doc.id ?? r.id }, version: r.version });
      this.records.set(r.entity, byId);
    }
  }

  /** Records of [entity] that match [schema]; others (from newer app versions, damaged) are skipped. */
  all<S extends z.ZodType>(entity: Entity, schema: S): Map<string, LocalRecord<z.infer<S>>> {
    const out = new Map<string, LocalRecord<z.infer<S>>>();
    for (const [id, r] of this.records.get(entity) ?? []) {
      const parsed = schema.safeParse(r.doc);
      if (parsed.success) out.set(id, { doc: parsed.data, version: r.version });
    }
    return out;
  }

  raw(entity: Entity, id: string): LocalRecord<Record<string, unknown>> | undefined {
    return this.records.get(entity)?.get(id);
  }

  /** Writes fields of a record (creating it when new); returns the record's new version. */
  async upsert(entity: Entity, id: string, fields: Record<string, unknown>): Promise<number> {
    const current = this.raw(entity, id);
    const { results } = await this.sync.push(this.userId, [
      { mutationId: `google-${randomUUID()}`, entity, id, op: "upsert", baseVersion: current?.version ?? 0, fields },
    ]);
    const result = results[0];
    if (!result || result.status === "rejected") throw new Error(`${entity}/${id} rejected: ${result?.reason ?? "unknown"}`);
    const byId = this.records.get(entity) ?? new Map<string, LocalRecord<Record<string, unknown>>>();
    byId.set(id, { doc: { ...(current?.doc ?? {}), ...fields, id }, version: result.version });
    this.records.set(entity, byId);
    return result.version;
  }

  async remove(entity: Entity, id: string): Promise<void> {
    const current = this.raw(entity, id);
    if (!current) return;
    await this.sync.push(this.userId, [{ mutationId: `google-${randomUUID()}`, entity, id, op: "delete", baseVersion: current.version }]);
    this.records.get(entity)?.delete(id);
  }

  links(kind: LinkKind): Promise<LinkRow[]> {
    return repo.links(this.db, this.userId, kind);
  }

  saveLink(link: LinkRow): Promise<void> {
    return repo.saveLink(this.db, this.userId, link);
  }

  deleteLink(kind: LinkKind, localId: string): Promise<void> {
    return repo.deleteLink(this.db, this.userId, kind, localId);
  }

  cursor(scope: string): Promise<string | undefined> {
    return repo.cursor(this.db, this.userId, scope);
  }

  saveCursor(scope: string, cursor: string | null): Promise<void> {
    return repo.saveCursor(this.db, this.userId, scope, cursor);
  }

  /** Runs one item's work; a failure is recorded and the rest of the run continues. */
  async attempt(what: string, work: () => Promise<void>): Promise<void> {
    try {
      await work();
    } catch (err) {
      this.errors.push(`${what}: ${err instanceof Error ? err.message : String(err)}`);
    }
  }
}
