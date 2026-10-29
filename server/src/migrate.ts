import { loadConfig } from "./shared/config.js";
import { createPool, migrate } from "./shared/db.js";

const db = createPool(loadConfig().DATABASE_URL);
try {
  const applied = await migrate(db);
  console.log(applied.length ? `Applied: ${applied.join(", ")}` : "Database is up to date");
} finally {
  await db.end();
}
