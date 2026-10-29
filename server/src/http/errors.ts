import type { FastifyError, FastifyInstance } from "fastify";
import { hasZodFastifySchemaValidationErrors } from "fastify-type-provider-zod";
import { z } from "zod";
import { ApiError } from "../shared/errors.js";

/** Every error response has this shape. */
export const ErrorBody = z.object({
  error: z.object({ code: z.string(), message: z.string(), details: z.unknown().optional() }),
});

/** Maps domain, validation and framework errors to the common error body; hides internals on 5xx. */
export function registerErrorHandler(app: FastifyInstance) {
  app.setErrorHandler((err: FastifyError, request, reply) => {
    if (err instanceof ApiError) {
      return reply.status(err.statusCode).send({ error: { code: err.code, message: err.message } });
    }
    if (hasZodFastifySchemaValidationErrors(err)) {
      return reply.status(400).send({
        error: { code: "invalid_request", message: "Request does not match the schema", details: err.validation },
      });
    }
    const status = err.statusCode ?? 500;
    if (status >= 500) {
      request.log.error({ err }, "unhandled error");
      return reply.status(500).send({ error: { code: "internal", message: "Something went wrong" } });
    }
    return reply.status(status).send({ error: { code: err.code?.toLowerCase() ?? "error", message: err.message } });
  });

  app.setNotFoundHandler((_request, reply) => reply.status(404).send({ error: { code: "not_found", message: "Route not found" } }));
}
