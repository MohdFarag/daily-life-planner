import type { FastifyPluginAsyncZod } from "fastify-type-provider-zod";
import { z } from "zod";
import { principalOf } from "../../http/authenticate.js";
import { ErrorBody } from "../../http/errors.js";
import type { AccountService } from "./account.service.js";

export function accountRoutes(account: AccountService): FastifyPluginAsyncZod {
  return async (app) => {
    app.addHook("preHandler", app.authenticate);

    app.get(
      "/account/export",
      {
        schema: { tags: ["account"], summary: "Export all data as JSON", security: [{ bearer: [] }] },
      },
      async (request, reply) => {
        const data = await account.export(principalOf(request));
        return reply
          .header("content-disposition", `attachment; filename="daily-life-planner-${data.exportedAt.slice(0, 10)}.json"`)
          .send(data);
      },
    );

    app.delete(
      "/account",
      {
        config: { rateLimit: { max: 5, timeWindow: "1 minute" } },
        schema: {
          tags: ["account"],
          summary: "Delete the account and all data",
          security: [{ bearer: [] }],
          body: z.object({ password: z.string().min(1).max(256) }),
          response: { 204: z.null(), 403: ErrorBody },
        },
      },
      async (request, reply) => {
        await account.delete(principalOf(request), request.body.password);
        return reply.status(204).send(null);
      },
    );
  };
}
