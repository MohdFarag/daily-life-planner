import { readdir, readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
import pg from "pg";

export type Db = pg.Pool;
export type Tx = pg.PoolClient;
/** Anything that can run a query: the pool, or a client inside a transaction. Repositories accept this. */
export type Queryable = Pick<pg.Pool, "query">;

// bigint columns (versions, sizes) arrive as strings by default; they fit in a JS number here.
pg.types.setTypeParser(pg.types.builtins.INT8, (v) => Number(v));

export function createPool(connectionString: string, options: pg.PoolConfig = {}): Db {
  return new pg.Pool({ connectionString, max: 10, ...options });
}

/** The single row a statement must return (INSERT/UPDATE ... RETURNING, or a lookup that must exist). */
export function one<T>(rows: T[]): T {
  const [row] = rows;
  if (row === undefined) throw new Error("Expected exactly one row");
  return row;
}

/** Runs [fn] in a transaction, rolling back on any error. */
export async function withTx<T>(db: Db, fn: (tx: Tx) => Promise<T>): Promise<T> {
  const client = await db.connect();
  try {
    await client.query("BEGIN");
    const result = await fn(client);
    await client.query("COMMIT");
    return result;
  } catch (err) {
    await client.query("ROLLBACK").catch(() => undefined);
    throw err;
  } finally {
    client.release();
  }
}

const migrationsDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../migrations");

/** Applies pending SQL migrations in file-name order, each in its own transaction. */
export async function migrate(db: Db, dir = migrationsDir): Promise<string[]> {
  await db.query(`CREATE TABLE IF NOT EXISTS schema_migrations (
    name text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now())`);
  const files = (await readdir(dir)).filter((f) => f.endsWith(".sql")).sort();
  const applied = new Set((await db.query<{ name: string }>("SELECT name FROM schema_migrations")).rows.map((r) => r.name));
  const ran: string[] = [];
  for (const file of files) {
    if (applied.has(file)) continue;
    const sql = await readFile(path.join(dir, file), "utf8");
    await withTx(db, async (tx) => {
      // Serialize concurrent migrators (e.g. several instances starting at once).
      await tx.query("SELECT pg_advisory_xact_lock(727274)");
      const again = await tx.query("SELECT 1 FROM schema_migrations WHERE name = $1", [file]);
      if (again.rowCount) return;
      await tx.query(sql);
      await tx.query("INSERT INTO schema_migrations (name) VALUES ($1)", [file]);
      ran.push(file);
    });
  }
  return ran;
}
