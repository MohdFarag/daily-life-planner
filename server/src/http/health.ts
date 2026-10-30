import type { FastifyPluginAsyncZod } from "fastify-type-provider-zod";
import { z } from "zod";
import type { Db } from "../shared/db.js";

/** Liveness (process up) and readiness (database reachable) probes. */
export function healthRoutes(db: Db): FastifyPluginAsyncZod {
  return async (app) => {
    app.get("/healthz", { schema: { hide: true, response: { 200: z.object({ status: z.literal("ok") }) } } }, async () => ({
      status: "ok" as const,
    }));
    app.get("/readyz", { schema: { hide: true } }, async (_request, reply) => {
      try {
        await db.query("SELECT 1");
        return { status: "ok" };
      } catch {
        return reply.status(503).send({ status: "unavailable" });
      }
    });
  };
}
