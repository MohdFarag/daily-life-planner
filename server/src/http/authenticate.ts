import type { FastifyInstance, FastifyRequest } from "fastify";
import type { AuthService, Principal } from "../modules/auth/auth.service.js";
import { unauthorized } from "../shared/errors.js";

declare module "fastify" {
  interface FastifyRequest {
    principal?: Principal;
  }
  interface FastifyInstance {
    /** preHandler that requires a valid bearer access token. */
    authenticate: (request: FastifyRequest) => Promise<void>;
  }
}

/** The caller of an authenticated route. Only valid after the `authenticate` preHandler ran. */
export function principalOf(request: FastifyRequest): Principal {
  if (!request.principal) throw unauthorized();
  return request.principal;
}

export function registerAuthentication(app: FastifyInstance, auth: AuthService) {
  app.decorateRequest("principal", undefined);
  app.decorate("authenticate", async (request: FastifyRequest) => {
    const header = request.headers.authorization ?? "";
    const [scheme, token] = header.split(" ");
    if (scheme?.toLowerCase() !== "bearer" || !token) throw unauthorized();
    request.principal = await auth.authenticate(token);
  });
}
