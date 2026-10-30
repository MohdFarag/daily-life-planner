import { randomUUID } from "node:crypto";
import { ListDoc, TaskDoc } from "../planner/planner.records.js";
import type { GTask } from "./google.api.js";
import type { SyncContext } from "./google.context.js";
import { newTaskFromGoogle, taskFromGoogle, taskToGoogle } from "./google.mapping.js";
import type { LinkRow } from "./google.repository.js";

/** The local list that adopts the user's default Google list on first connection. */
const DEFAULT_LOCAL_LIST = "personal";

/**
 * Task lists both ways: local lists become Google lists (the default list adopts Google's first
 * list), Google lists become local lists, and renames follow whichever side changed (TSK01).
 */
export async function syncLists(ctx: SyncContext): Promise<void> {
  const local = ctx.all("lists", ListDoc);
  const remote = await ctx.api.taskLists();
  const links = await ctx.links("list");
  const byLocal = new Map(links.map((l) => [l.localId, l]));
  const linkedGoogle = new Set(links.map((l) => l.googleId));

  for (const [id, { doc, version }] of local) {
    const link = byLocal.get(id);
    await ctx.attempt(`list ${doc.name}`, async () => {
      if (!link) {
        const adopt =
          id === DEFAULT_LOCAL_LIST
            ? remote.find((r) => !linkedGoogle.has(r.id))
            : remote.find((r) => r.title === doc.name && !linkedGoogle.has(r.id));
        const target = adopt ?? (await ctx.api.insertTaskList(doc.name));
        linkedGoogle.add(target.id);
        await ctx.saveLink({
          kind: "list",
          localId: id,
          googleId: target.id,
          containerId: target.id,
          etag: target.title,
          localVersion: version,
        });
        return;
      }
      const google = remote.find((r) => r.id === link.googleId);
      if (!google) {
        // Deleted in Google: the local list stays; link it again next run.
        await ctx.deleteLink("list", id);
        return;
      }
      if (version > link.localVersion && google.title !== doc.name) {
        await ctx.api.patchTaskList(google.id, doc.name);
        await ctx.saveLink({ ...link, etag: doc.name, localVersion: version });
      } else if (google.title !== link.etag) {
        const v = await ctx.upsert("lists", id, { name: google.title });
        await ctx.saveLink({ ...link, etag: google.title, localVersion: v });
      }
    });
  }

  for (const google of remote) {
    if (linkedGoogle.has(google.id)) continue;
    await ctx.attempt(`list ${google.title}`, async () => {
      const id = randomUUID();
      const v = await ctx.upsert("lists", id, { id, name: google.title });
      await ctx.saveLink({ kind: "list", localId: id, googleId: google.id, containerId: google.id, etag: google.title, localVersion: v });
    });
  }
}

/**
 * Tasks both ways (TSK01–TSK03). Local changes go out first; Google changes since the last run
 * come back, skipping our own writes by etag. Subtask parents and positions are kept.
 */
export async function syncTasks(ctx: SyncContext): Promise<void> {
  const lists = await ctx.links("list");
  const listByLocal = new Map(lists.map((l) => [l.localId, l]));
  let links = await ctx.links("task");
  const tasks = ctx.all("tasks", TaskDoc);

  // Out: new, changed and deleted local tasks. Parents go before their subtasks.
  const ordered = [...tasks].sort(([, a], [, b]) => Number(Boolean(a.doc.parentId)) - Number(Boolean(b.doc.parentId)));
  const byLocal = new Map(links.map((l) => [l.localId, l]));
  for (const [id, { doc, version }] of ordered) {
    const list = listByLocal.get(doc.listId);
    if (!list) continue;
    const link = byLocal.get(id);
    if (link && version <= link.localVersion) continue;
    await ctx.attempt(`task ${doc.title}`, async () => {
      const body = taskToGoogle(doc);
      let saved: GTask;
      if (link && link.containerId === list.googleId) {
        saved = await ctx.api.patchTask(list.googleId, link.googleId, body);
      } else {
        // New, or moved to another list: Google tasks cannot change list, so recreate.
        if (link) await ctx.api.deleteTask(link.containerId, link.googleId);
        const parentId = typeof doc.parentId === "string" ? byLocal.get(doc.parentId)?.googleId : undefined;
        saved = await ctx.api.insertTask(list.googleId, body, parentId);
      }
      const next: LinkRow = {
        kind: "task",
        localId: id,
        googleId: saved.id,
        containerId: list.googleId,
        etag: saved.etag ?? null,
        localVersion: version,
      };
      byLocal.set(id, next);
      await ctx.saveLink(next);
    });
  }
  for (const link of links) {
    // Only a record that is really gone counts as deleted (not one this version cannot read).
    if (ctx.raw("tasks", link.localId)) continue;
    await ctx.attempt(`deleted task ${link.localId}`, async () => {
      await ctx.api.deleteTask(link.containerId, link.googleId);
      await ctx.deleteLink("task", link.localId);
    });
  }

  // In: changes made in Google since the last run.
  links = await ctx.links("task");
  const byGoogle = new Map(links.map((l) => [l.googleId, l]));
  for (const list of lists) {
    await ctx.attempt(`tasks of list ${list.localId}`, async () => {
      const scope = `tasks:${list.googleId}`;
      const since = await ctx.cursor(scope);
      const startedAt = new Date(ctx.now.getTime() - 60_000).toISOString();
      const changed = (await ctx.api.tasks(list.googleId, since)).sort((a, b) => Number(Boolean(a.parent)) - Number(Boolean(b.parent)));
      for (const g of changed) {
        await ctx.attempt(`google task ${g.id}`, () => applyGoogleTask(ctx, g, list.localId, list.googleId, byGoogle));
      }
      await ctx.saveCursor(scope, startedAt);
    });
  }
}

async function applyGoogleTask(ctx: SyncContext, g: GTask, localListId: string, googleListId: string, byGoogle: Map<string, LinkRow>) {
  const link = byGoogle.get(g.id);
  if (g.deleted) {
    if (link) {
      await ctx.remove("tasks", link.localId);
      await ctx.deleteLink("task", link.localId);
      byGoogle.delete(g.id);
    }
    return;
  }
  if (link && g.etag && g.etag === link.etag) return; // our own write, or unchanged
  const parentLocal = g.parent ? byGoogle.get(g.parent)?.localId : undefined;
  if (link) {
    const current = ctx.all("tasks", TaskDoc).get(link.localId)?.doc;
    const v = await ctx.upsert("tasks", link.localId, taskFromGoogle(g, localListId, parentLocal, current));
    const next = { ...link, etag: g.etag ?? null, containerId: googleListId, localVersion: v };
    byGoogle.set(g.id, next);
    await ctx.saveLink(next);
  } else {
    const fields = newTaskFromGoogle(g, localListId, parentLocal, ctx.now);
    const id = String(fields.id);
    const v = await ctx.upsert("tasks", id, fields);
    const next: LinkRow = { kind: "task", localId: id, googleId: g.id, containerId: googleListId, etag: g.etag ?? null, localVersion: v };
    byGoogle.set(g.id, next);
    await ctx.saveLink(next);
  }
}
