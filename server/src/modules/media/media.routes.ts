import type { FastifyPluginAsyncZod } from "fastify-type-provider-zod";
import { z } from "zod";
import { principalOf } from "../../http/authenticate.js";
import { ErrorBody } from "../../http/errors.js";
import { badRequest } from "../../shared/errors.js";
import { ChunkHeaders, CreateMediaBody, MediaInfo, MediaParams } from "./media.schemas.js";
import type { MediaService } from "./media.service.js";

const MAX_CHUNK_BYTES = 16 * 1024 * 1024;
const secured = [{ bearer: [] }];

export function mediaRoutes(media: MediaService): FastifyPluginAsyncZod {
  return async (app) => {
    app.addHook("preHandler", app.authenticate);
    // Chunks are raw bytes.
    app.addContentTypeParser("application/octet-stream", { parseAs: "buffer", bodyLimit: MAX_CHUNK_BYTES }, (_request, body, done) => {
      done(null, body);
    });

    app.post(
      "/media",
      {
        schema: {
          tags: ["media"],
          summary: "Start an upload",
          security: secured,
          body: CreateMediaBody,
          response: { 201: MediaInfo, 413: ErrorBody },
        },
      },
      async (request, reply) => reply.status(201).send(await media.create(principalOf(request).userId, request.body)),
    );

    app.get(
      "/media",
      {
        schema: { tags: ["media"], summary: "List uploads", security: secured, response: { 200: z.object({ media: z.array(MediaInfo) }) } },
      },
      async (request) => ({ media: await media.list(principalOf(request).userId) }),
    );

    app.get(
      "/media/:id",
      {
        schema: {
          tags: ["media"],
          summary: "Upload status (resume offset)",
          security: secured,
          params: MediaParams,
          response: { 200: MediaInfo, 404: ErrorBody },
        },
      },
      async (request) => media.get(principalOf(request).userId, request.params.id),
    );

    app.put(
      "/media/:id/content",
      {
        bodyLimit: MAX_CHUNK_BYTES,
        schema: {
          tags: ["media"],
          summary: "Upload a chunk at Upload-Offset",
          security: secured,
          params: MediaParams,
          headers: ChunkHeaders,
          response: { 200: MediaInfo, 409: ErrorBody, 422: ErrorBody },
        },
      },
      async (request) => {
        if (!Buffer.isBuffer(request.body) || request.body.length === 0) {
          throw badRequest("empty_chunk", "Send the chunk as application/octet-stream");
        }
        return media.appendChunk(principalOf(request).userId, request.params.id, request.headers["upload-offset"], request.body);
      },
    );

    app.get(
      "/media/:id/content",
      {
        schema: { tags: ["media"], summary: "Download", security: secured, params: MediaParams },
      },
      async (request, reply) => {
        const { info, stream } = await media.open(principalOf(request).userId, request.params.id);
        return reply
          .header("content-type", info.mimeType)
          .header("content-length", info.sizeBytes)
          .header("cache-control", "private, max-age=31536000, immutable")
          .send(stream);
      },
    );

    app.delete(
      "/media/:id",
      {
        schema: { tags: ["media"], summary: "Delete", security: secured, params: MediaParams, response: { 204: z.null(), 404: ErrorBody } },
      },
      async (request, reply) => {
        await media.remove(principalOf(request).userId, request.params.id);
        return reply.status(204).send(null);
      },
    );
  };
}
