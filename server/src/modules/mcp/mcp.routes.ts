import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import type { FastifyPluginAsyncZod } from "fastify-type-provider-zod";
import { z } from "zod";
import { principalOf } from "../../http/authenticate.js";
import { ErrorBody } from "../../http/errors.js";
import { unauthorized } from "../../shared/errors.js";
import type { PlannerService } from "../planner/planner.service.js";
import type { McpTokenService } from "./mcp.service.js";
import { buildMcpServer } from "./mcp.tools.js";

const secured = [{ bearer: [] }];
const TokenInfo = z.object({
  id: z.uuid(),
  name: z.string(),
  createdAt: z.string(),
  expiresAt: z.string(),
  lastUsedAt: z.string().nullable(),
});

/** Token management for the signed-in user, and the MCP endpoint for assistants. */
export function mcpRoutes(tokens: McpTokenService, planner: PlannerService): FastifyPluginAsyncZod {
  const tokenRoutes: FastifyPluginAsyncZod = async (managed) => {
    managed.addHook("preHandler", managed.authenticate);

    managed.post(
      "/mcp/tokens",
      {
        schema: {
          tags: ["mcp"],
          summary: "Create an MCP access token (shown once)",
          security: secured,
          body: z.object({ name: z.string().trim().min(1).max(100) }),
          response: { 201: TokenInfo.extend({ token: z.string() }) },
        },
      },
      async (request, reply) => reply.status(201).send(await tokens.create(principalOf(request).userId, request.body.name)),
    );

    managed.get(
      "/mcp/tokens",
      {
        schema: {
          tags: ["mcp"],
          summary: "List MCP access tokens",
          security: secured,
          response: { 200: z.object({ tokens: z.array(TokenInfo) }) },
        },
      },
      async (request) => ({ tokens: await tokens.list(principalOf(request).userId) }),
    );

    managed.delete(
      "/mcp/tokens/:id",
      {
        schema: {
          tags: ["mcp"],
          summary: "Revoke an MCP access token",
          security: secured,
          params: z.object({ id: z.uuid() }),
          response: { 204: z.null(), 404: ErrorBody },
        },
      },
      async (request, reply) => {
        await tokens.revoke(principalOf(request).userId, request.params.id);
        return reply.status(204).send(null);
      },
    );
  };

  return async (app) => {
    await app.register(tokenRoutes);

    // Streamable HTTP, stateless: each JSON-RPC request gets a fresh server bound to the token's user.
    app.post("/mcp", { schema: { hide: true } }, async (request, reply) => {
      const [scheme, token] = (request.headers.authorization ?? "").split(" ");
      if (scheme?.toLowerCase() !== "bearer" || !token) throw unauthorized("MCP token required");
      const userId = await tokens.authenticate(token);

      const server = buildMcpServer(planner, userId);
      const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined, enableJsonResponse: true });
      reply.hijack();
      reply.raw.on("close", () => {
        void transport.close();
        void server.close();
      });
      await server.connect(transport);
      await transport.handleRequest(request.raw, reply.raw, request.body);
    });

    // Stateless mode has no server-initiated stream or session to end.
    const notAllowed = { error: { code: "method_not_allowed", message: "Use POST" } };
    app.get("/mcp", { schema: { hide: true } }, async (_request, reply) => reply.status(405).header("allow", "POST").send(notAllowed));
    app.delete("/mcp", { schema: { hide: true } }, async (_request, reply) => reply.status(405).header("allow", "POST").send(notAllowed));
  };
}
