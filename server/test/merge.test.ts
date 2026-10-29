import { describe, expect, it } from "vitest";
import { initialFieldVersions, mergeFields } from "../src/modules/sync/merge.js";

describe("mergeFields", () => {
  const stored = { doc: { title: "Read", notes: "ch. 1", listId: "work" }, fieldVersions: { title: 1, notes: 1, listId: 5 } };

  it("applies fields the server has not changed since the client's base version", () => {
    const r = mergeFields(stored, { title: "Read 10 pages" }, 3, 6);
    expect(r).toMatchObject({ changed: true, conflicts: [], doc: { title: "Read 10 pages", listId: "work" } });
    expect(r.fieldVersions.title).toBe(6);
  });

  it("keeps the server value and reports a conflict when the same field changed after the base", () => {
    const r = mergeFields(stored, { listId: "personal", notes: "ch. 2" }, 3, 6);
    expect(r.doc).toMatchObject({ listId: "work", notes: "ch. 2" });
    expect(r.conflicts).toEqual([{ field: "listId", serverValue: "work", serverVersion: 5 }]);
  });

  it("treats identical values as no change, even with a stale base", () => {
    const r = mergeFields(stored, { listId: "work" }, 0, 6);
    expect(r).toMatchObject({ changed: false, conflicts: [] });
  });

  it("compares nested values structurally", () => {
    const withRule = { doc: { rule: { prayer: "ASR", offsetMin: 10 } }, fieldVersions: { rule: 9 } };
    expect(mergeFields(withRule, { rule: { prayer: "ASR", offsetMin: 10 } }, 0, 10).changed).toBe(false);
  });

  it("starts every field of a new record at its version", () => {
    expect(initialFieldVersions({ a: 1, b: 2 }, 7)).toEqual({ a: 7, b: 7 });
  });
});
