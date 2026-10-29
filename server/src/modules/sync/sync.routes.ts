import type { FastifyPluginAsyncZod } from "fastify-type-provider-zod";
import { principalOf } from "../../http/authenticate.js";
import type { SyncService } from "./sync.service.js";
import { PullQuery, PullResponse, PushBody, PushResponse } from "./sync.schemas.js";

export function syncRoutes(sync: SyncService): FastifyPluginAsyncZod {
  return async (app) => {
    app.addHook("preHandler", app.authenticate);

    app.post("/sync/push", {
      bodyLimit: 8 * 1024 * 1024,
      schema: { tags: ["sync"], summary: "Apply local changes", security: [{ bearer: [] }], body: PushBody, response: { 200: PushResponse } },
    }, async (request) => sync.push(principalOf(request).userId, request.body.mutations));

    app.get("/sync/pull", {
      schema: { tags: ["sync"], summary: "Fetch changes after a cursor", security: [{ bearer: [] }], querystring: PullQuery, response: { 200: PullResponse } },
    }, async (request) => sync.pull(principalOf(request).userId, request.query.since, request.query.limit));
  };
}
