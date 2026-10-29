import cors from "@fastify/cors";
import helmet from "@fastify/helmet";
import rateLimit from "@fastify/rate-limit";
import swagger from "@fastify/swagger";
import swaggerUi from "@fastify/swagger-ui";
import Fastify, { type FastifyServerOptions } from "fastify";
import { jsonSchemaTransform, serializerCompiler, validatorCompiler, type ZodTypeProvider } from "fastify-type-provider-zod";
import { registerAuthentication } from "./http/authenticate.js";
import { registerErrorHandler } from "./http/errors.js";
import { healthRoutes } from "./http/health.js";
import { accountRoutes } from "./modules/account/account.routes.js";
import { AccountService } from "./modules/account/account.service.js";
import { authRoutes } from "./modules/auth/auth.routes.js";
import { AuthService } from "./modules/auth/auth.service.js";
import { mediaRoutes } from "./modules/media/media.routes.js";
import { MediaService } from "./modules/media/media.service.js";
import { LocalDiskStorage, type MediaStorage } from "./modules/media/storage.js";
import { syncRoutes } from "./modules/sync/sync.routes.js";
import { SyncService } from "./modules/sync/sync.service.js";
import type { Config } from "./shared/config.js";
import type { Db } from "./shared/db.js";
import { ApiError } from "./shared/errors.js";

export interface AppDeps {
  config: Config;
  db: Db;
  storage?: MediaStorage;
  logger?: FastifyServerOptions["logger"];
}

/** Creates the services once; routes and jobs receive them explicitly. */
export function createServices({ config, db, storage }: Pick<AppDeps, "config" | "db" | "storage">) {
  const auth = new AuthService(db, config);
  const sync = new SyncService(db);
  const media = new MediaService(db, storage ?? new LocalDiskStorage(config.MEDIA_DIR), config);
  const account = new AccountService(db, auth, sync, media);
  return { auth, sync, media, account };
}

export type Services = ReturnType<typeof createServices>;

/** Builds the HTTP application. All API routes live under /v1. */
export async function buildApp(deps: AppDeps) {
  const { config, db } = deps;
  const services = createServices(deps);

  const app = Fastify({
    logger: deps.logger ?? { level: config.LOG_LEVEL, redact: ["req.headers.authorization", "req.body.password", "req.body.refreshToken"] },
    trustProxy: true,
  }).withTypeProvider<ZodTypeProvider>();

  app.setValidatorCompiler(validatorCompiler);
  app.setSerializerCompiler(serializerCompiler);
  registerErrorHandler(app);
  registerAuthentication(app, services.auth);

  await app.register(helmet);
  await app.register(cors, { origin: config.CORS_ORIGINS ? config.CORS_ORIGINS.split(",").map((o) => o.trim()) : false });
  await app.register(rateLimit, {
    max: config.API_RATE_LIMIT_PER_MINUTE,
    timeWindow: "1 minute",
    errorResponseBuilder: (_request, context) =>
      new ApiError(429, "rate_limited", `Too many requests; retry in ${Math.ceil(context.ttl / 1000)} seconds`),
  });
  await app.register(swagger, {
    openapi: {
      info: { title: "Daily Life Planner API", version: "0.1.0" },
      components: { securitySchemes: { bearer: { type: "http", scheme: "bearer" } } },
    },
    transform: jsonSchemaTransform,
  });
  await app.register(swaggerUi, { routePrefix: "/docs" });

  await app.register(healthRoutes(db));
  await app.register(async (v1) => {
    await v1.register(authRoutes(services.auth, config.AUTH_RATE_LIMIT_PER_MINUTE));
    await v1.register(syncRoutes(services.sync));
    await v1.register(mediaRoutes(services.media));
    await v1.register(accountRoutes(services.account));
  }, { prefix: "/v1" });

  return { app, services };
}
