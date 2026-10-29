import type { FastifyPluginAsyncZod } from "fastify-type-provider-zod";
import { z } from "zod";
import { principalOf } from "../../http/authenticate.js";
import { ErrorBody } from "../../http/errors.js";
import { notFound } from "../../shared/errors.js";
import type { AuthService } from "./auth.service.js";
import { AuthResult, DeviceSession, LoginBody, RefreshBody, RegisterBody, SessionIdParams, TokenPair, User } from "./auth.schemas.js";

export function authRoutes(auth: AuthService, credentialsPerMinute: number): FastifyPluginAsyncZod {
  // Credential endpoints get a tighter rate limit than the rest of the API.
  const credentialLimit = { rateLimit: { max: credentialsPerMinute, timeWindow: "1 minute" } };

  return async (app) => {
    app.post("/auth/register", {
      config: credentialLimit,
      schema: { tags: ["auth"], summary: "Create an account", body: RegisterBody, response: { 201: AuthResult, 409: ErrorBody } },
    }, async (request, reply) => reply.status(201).send(await auth.register(request.body)));

    app.post("/auth/login", {
      config: credentialLimit,
      schema: { tags: ["auth"], summary: "Sign in on a device", body: LoginBody, response: { 200: AuthResult, 401: ErrorBody } },
    }, async (request) => auth.login(request.body));

    app.post("/auth/refresh", {
      config: credentialLimit,
      schema: { tags: ["auth"], summary: "Rotate tokens", body: RefreshBody, response: { 200: TokenPair, 401: ErrorBody } },
    }, async (request) => auth.refresh(request.body.refreshToken));

    app.post("/auth/logout", {
      preHandler: app.authenticate,
      schema: { tags: ["auth"], summary: "Sign out this device", security: [{ bearer: [] }], response: { 204: z.null() } },
    }, async (request, reply) => {
      await auth.logout(principalOf(request));
      return reply.status(204).send(null);
    });

    app.get("/me", {
      preHandler: app.authenticate,
      schema: { tags: ["account"], summary: "Current user", security: [{ bearer: [] }], response: { 200: User } },
    }, async (request) => auth.me(principalOf(request)));

    app.get("/sessions", {
      preHandler: app.authenticate,
      schema: { tags: ["auth"], summary: "Signed-in devices", security: [{ bearer: [] }], response: { 200: z.object({ sessions: z.array(DeviceSession) }) } },
    }, async (request) => ({ sessions: await auth.listSessions(principalOf(request)) }));

    app.delete("/sessions/:id", {
      preHandler: app.authenticate,
      schema: { tags: ["auth"], summary: "Sign out another device", security: [{ bearer: [] }], params: SessionIdParams, response: { 204: z.null(), 404: ErrorBody } },
    }, async (request, reply) => {
      if (!(await auth.revokeSession(principalOf(request), request.params.id))) throw notFound("Session");
      return reply.status(204).send(null);
    });
  };
}
