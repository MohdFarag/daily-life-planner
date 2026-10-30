import { buildApp } from "./app.js";
import { GoogleSyncJob } from "./jobs/google-sync.js";
import { RetentionJob } from "./jobs/retention.js";
import { loadConfig } from "./shared/config.js";
import { createPool, migrate } from "./shared/db.js";

const HOUR = 60 * 60 * 1000;

const config = loadConfig();
const db = createPool(config.DATABASE_URL);
await migrate(db);

const { app, services } = await buildApp({ config, db });
const retention = new RetentionJob(services.sync, services.auth, services.media, app.log.child({ job: "retention" }));
const timer = setInterval(() => {
  retention.run().catch((err: unknown) => {
    app.log.error({ err }, "retention job failed");
  });
}, HOUR);
timer.unref();

const googleSync = new GoogleSyncJob(services.google, app.log.child({ job: "google-sync" }));
const googleTimer = setInterval(() => {
  googleSync.run().catch((err: unknown) => {
    app.log.error({ err }, "Google sync job failed");
  });
}, config.GOOGLE_SYNC_INTERVAL_SECONDS * 1000);
googleTimer.unref();

async function shutdown(signal: string) {
  app.log.info({ signal }, "shutting down");
  clearInterval(timer);
  clearInterval(googleTimer);
  await app.close();
  await db.end();
  process.exit(0);
}
process.on("SIGTERM", () => void shutdown("SIGTERM"));
process.on("SIGINT", () => void shutdown("SIGINT"));

await app.listen({ port: config.PORT, host: config.HOST });
