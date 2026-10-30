import type { FastifyPluginAsyncZod } from "fastify-type-provider-zod";
import { z } from "zod";
import { principalOf } from "../../http/authenticate.js";
import { ErrorBody } from "../../http/errors.js";
import type { GoogleService } from "./google.service.js";

const secured = [{ bearer: [] }];
const Status = z.object({
  enabled: z.boolean(),
  connected: z.boolean(),
  email: z.string().nullable(),
  lastSyncAt: z.string().nullable(),
  lastError: z.string().nullable(),
});

/** Connecting Google and syncing on demand. The callback is opened by the browser, not the app. */
export function googleRoutes(google: GoogleService, appReturnUri: string): FastifyPluginAsyncZod {
  const authenticated: FastifyPluginAsyncZod = async (app) => {
    app.addHook("preHandler", app.authenticate);

    app.get(
      "/google/status",
      { schema: { tags: ["google"], summary: "Google connection status", security: secured, response: { 200: Status } } },
      async (request) => google.status(principalOf(request).userId),
    );

    app.post(
      "/google/connect",
      {
        schema: {
          tags: ["google"],
          summary: "Start connecting Google: open the returned URL in a browser",
          security: secured,
          response: { 200: z.object({ url: z.string() }), 503: ErrorBody },
        },
      },
      async (request) => google.startConnect(principalOf(request).userId),
    );

    app.post(
      "/google/sync",
      {
        schema: {
          tags: ["google"],
          summary: "Sync Tasks and Calendar now",
          security: secured,
          response: { 200: z.object({ errors: z.array(z.string()) }) },
        },
      },
      async (request) => google.syncUser(principalOf(request).userId),
    );

    app.post(
      "/google/calendars",
      {
        schema: {
          tags: ["google"],
          summary: "Create a calendar in the user's Google account",
          security: secured,
          body: z.object({ summary: z.string().trim().min(1).max(200) }),
          response: { 201: z.object({ id: z.string() }), 409: ErrorBody },
        },
      },
      async (request, reply) => {
        const userId = principalOf(request).userId;
        const created = await google.createCalendar(userId, request.body.summary);
        void google.syncUser(userId);
        return reply.status(201).send(created);
      },
    );

    app.delete(
      "/google",
      { schema: { tags: ["google"], summary: "Disconnect Google and revoke access", security: secured, response: { 204: z.null() } } },
      async (request, reply) => {
        await google.disconnect(principalOf(request).userId);
        return reply.status(204).send(null);
      },
    );
  };

  return async (app) => {
    await app.register(authenticated);

    // Google redirects the browser here; the page hands control back to the app.
    app.get(
      "/google/callback",
      {
        schema: {
          hide: true,
          querystring: z.object({
            state: z.string().max(200).optional(),
            code: z.string().max(2000).optional(),
            error: z.string().max(200).optional(),
          }),
        },
      },
      async (request, reply) => {
        const { state, code, error } = request.query;
        let status = "connected";
        if (error || !state || !code) {
          status = error === "access_denied" ? "denied" : "error";
        } else {
          try {
            const userId = await google.completeConnect(state, code);
            void google.syncUser(userId).catch((err: unknown) => {
              request.log.error({ err }, "first Google sync failed");
            });
          } catch (err) {
            request.log.warn({ err }, "Google connection failed");
            status = "error";
          }
        }
        const target = `${appReturnUri}?status=${status}`;
        return reply
          .header("content-security-policy", "default-src 'none'; style-src 'unsafe-inline'")
          .type("text/html")
          .send(returnPage(status, target));
      },
    );
  };
}

function returnPage(status: string, target: string): string {
  const message =
    status === "connected" ? "Google is connected." : status === "denied" ? "Access was not granted." : "Connecting Google did not work.";
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="refresh" content="0;url=${target}"><title>Daily Life Planner</title></head>
<body style="font-family:sans-serif;padding:32px"><p>${message}</p><p><a href="${target}">Return to the app</a></p></body></html>`;
}
