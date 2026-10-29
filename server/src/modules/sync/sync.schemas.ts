import { z } from "zod";
import { EntityName } from "./entities.js";

const RecordId = z.string().min(1).max(200);

export const MutationBody = z
  .object({
    mutationId: z.string().min(8).max(128).describe("Client-generated idempotency key"),
    entity: EntityName,
    id: RecordId,
    op: z.enum(["upsert", "delete"]),
    baseVersion: z.number().int().min(0),
    fields: z.record(z.string(), z.unknown()).optional(),
  })
  .refine((m) => m.op === "delete" || m.fields !== undefined, { message: "fields are required for upsert", path: ["fields"] });

export const PushBody = z.object({ mutations: z.array(MutationBody).min(1).max(500) });

const FieldConflict = z.object({ field: z.string(), serverValue: z.unknown(), serverVersion: z.number().int() });

export const PushResponse = z.object({
  version: z.number().int(),
  results: z.array(
    z.object({
      mutationId: z.string(),
      entity: EntityName,
      id: z.string(),
      status: z.enum(["applied", "conflict", "rejected"]),
      version: z.number().int(),
      conflicts: z.array(FieldConflict).optional(),
      reason: z.string().optional(),
    }),
  ),
});

export const PullQuery = z.object({
  since: z.coerce.number().int().min(0).default(0),
  limit: z.coerce.number().int().min(1).max(1000).default(500),
});

export const PullResponse = z.object({
  changes: z.array(
    z.object({
      entity: EntityName,
      id: z.string(),
      version: z.number().int(),
      deleted: z.boolean(),
      doc: z.record(z.string(), z.unknown()),
    }),
  ),
  cursor: z.number().int(),
  hasMore: z.boolean(),
});
