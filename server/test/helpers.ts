import { randomBytes } from "node:crypto";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { afterAll, beforeAll } from "vitest";
import { buildApp, type Services } from "../src/app.js";
import { loadConfig, type Config } from "../src/shared/config.js";
import { createPool, type Db, migrate } from "../src/shared/db.js";

const TEST_DATABASE_URL = process.env.TEST_DATABASE_URL ?? "postgres://postgres@127.0.0.1:5433/planner_test";

export interface TestContext {
  app: Awaited<ReturnType<typeof buildApp>>["app"];
  services: Services;
  db: Db;
  config: Config;
}

/** Registers hooks giving the test file its own schema, media folder and app instance. */
export function useTestApp(overrides: Partial<Record<keyof Config, string>> = {}): TestContext {
  const ctx = {} as TestContext;
  const schema = `test_${randomBytes(6).toString("hex")}`;
  let admin: Db;
  let mediaDir: string;

  beforeAll(async () => {
    admin = createPool(TEST_DATABASE_URL, { max: 1 });
    await admin.query(`CREATE SCHEMA ${schema}`);
    mediaDir = await mkdtemp(path.join(tmpdir(), "planner-media-"));
    ctx.config = loadConfig({
      DATABASE_URL: TEST_DATABASE_URL,
      MEDIA_DIR: mediaDir,
      LOG_LEVEL: "silent",
      AUTH_RATE_LIMIT_PER_MINUTE: "1000",
      ...overrides,
    });
    ctx.db = createPool(TEST_DATABASE_URL, { options: `-c search_path=${schema}` });
    await migrate(ctx.db);
    const built = await buildApp({ config: ctx.config, db: ctx.db, logger: false });
    ctx.app = built.app;
    ctx.services = built.services;
    await ctx.app.ready();
  });

  afterAll(async () => {
    await ctx.app?.close();
    await ctx.db?.end();
    await admin.query(`DROP SCHEMA ${schema} CASCADE`);
    await admin.end();
    await rm(mediaDir, { recursive: true, force: true });
  });

  return ctx;
}

let counter = 0;

/** Registers a fresh user and returns bearer headers for it. */
export async function signUp(ctx: TestContext, email = `user${Date.now()}${counter++}@example.com`, password = "correct horse battery") {
  const res = await ctx.app.inject({ method: "POST", url: "/v1/auth/register", payload: { email, password, deviceName: "Pixel" } });
  if (res.statusCode !== 201) throw new Error(`register failed: ${res.statusCode} ${res.body}`);
  const body = res.json();
  return { email, password, user: body.user, tokens: body.tokens, headers: { authorization: `Bearer ${body.tokens.accessToken}` } };
}
