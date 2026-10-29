import { z } from "zod";

export const CreateMediaBody = z.object({
  filename: z.string().trim().min(1).max(255).optional(),
  mimeType: z.string().regex(/^[\w.+-]+\/[\w.+-]+$/).max(127),
  sizeBytes: z.number().int().positive(),
  sha256: z.string().regex(/^[0-9a-fA-F]{64}$/),
});

export const MediaInfo = z.object({
  id: z.uuid(),
  filename: z.string().nullable(),
  mimeType: z.string(),
  sizeBytes: z.number().int(),
  sha256: z.string(),
  receivedBytes: z.number().int().describe("Resume the upload from this offset"),
  status: z.enum(["uploading", "complete"]),
  createdAt: z.string(),
});

export const MediaParams = z.object({ id: z.uuid() });
export const ChunkHeaders = z.object({ "upload-offset": z.coerce.number().int().min(0) });
