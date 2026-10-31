package app.dailylife.android.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID
import org.json.JSONObject

/**
 * Local database. Each entity is stored as a JSON document keyed by id, with indexed columns where
 * queries need them. Writes are synchronous and durable before a save is confirmed (CAP02).
 *
 * Every local write is also recorded in the outbox, in the same transaction, so it reaches the
 * server even if the app is closed before syncing (SYN01).
 */
class Db(context: Context) : SQLiteOpenHelper(context, "planner.db", null, 2) {
    private val changed = mutableSetOf<Table>()

    /** Tables, named like the server's entities; all of them sync. */
    enum class Table(val sql: String) {
        items(
            "CREATE TABLE items (id TEXT PRIMARY KEY, state TEXT NOT NULL, captured_at TEXT NOT NULL, doc TEXT NOT NULL)"
        ),
        tasks("CREATE TABLE tasks (id TEXT PRIMARY KEY, doc TEXT NOT NULL)"),
        lists("CREATE TABLE lists (id TEXT PRIMARY KEY, doc TEXT NOT NULL)"),
        sessions(
            "CREATE TABLE sessions (id TEXT PRIMARY KEY, task_id TEXT NOT NULL, doc TEXT NOT NULL)"
        ),
        rules("CREATE TABLE rules (id TEXT PRIMARY KEY, task_id TEXT NOT NULL, doc TEXT NOT NULL)"),
        blocks("CREATE TABLE blocks (id TEXT PRIMARY KEY, doc TEXT NOT NULL)"),
        // Calendar events: read from Google by the server, or created here and sent to Google.
        events("CREATE TABLE events (id TEXT PRIMARY KEY, doc TEXT NOT NULL)"),
        settings("CREATE TABLE settings (id TEXT PRIMARY KEY, doc TEXT NOT NULL)"),
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        Table.entries.forEach { db.execSQL(it.sql) }
        db.execSQL("CREATE INDEX items_state ON items(state)")
        db.execSQL("CREATE INDEX sessions_task ON sessions(task_id)")
        createSyncTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createSyncTables(db)
            // Everything created before sync existed still has to reach the server.
            Table.entries.forEach { table ->
                db.execSQL(
                    "INSERT INTO outbox (entity, id, op, mutation_id, seq) " +
                        "SELECT '${table.name}', id, 'upsert', lower(hex(randomblob(16))), rowid " +
                        "FROM ${table.name}"
                )
            }
        }
    }

    private fun createSyncTables(db: SQLiteDatabase) {
        // One row per changed record; a later edit replaces the row with a new mutation id, which
        // is the server's idempotency key and tells a push whether the record changed in flight.
        db.execSQL(
            "CREATE TABLE outbox (entity TEXT NOT NULL, id TEXT NOT NULL, op TEXT NOT NULL, " +
                "mutation_id TEXT NOT NULL, seq INTEGER NOT NULL, error TEXT, " +
                "PRIMARY KEY (entity, id))"
        )
        // The server version each record was last synced at (the base for the next push).
        db.execSQL(
            "CREATE TABLE sync_meta (entity TEXT NOT NULL, id TEXT NOT NULL, " +
                "version INTEGER NOT NULL, PRIMARY KEY (entity, id))"
        )
    }

    fun all(table: Table): List<JSONObject> {
        val c = readableDatabase.rawQuery("SELECT doc FROM ${table.name}", null)
        try {
            return buildList { while (c.moveToNext()) add(JSONObject(c.getString(0))) }
        } finally {
            c.close()
        }
    }

    fun get(table: Table, id: String): JSONObject? {
        val c = readableDatabase.rawQuery("SELECT doc FROM ${table.name} WHERE id = ?", arrayOf(id))
        try {
            return if (c.moveToFirst()) JSONObject(c.getString(0)) else null
        } finally {
            c.close()
        }
    }

    /** Stores a document; [record] is false when the change came from the server. */
    fun put(
        table: Table,
        id: String,
        doc: JSONObject,
        extra: Map<String, String> = emptyMap(),
        record: Boolean = true,
    ) {
        val v =
            ContentValues().apply {
                put("id", id)
                put("doc", doc.toString())
                extra.forEach { (k, value) -> put(k, value) }
            }
        writableDatabase.insertWithOnConflict(table.name, null, v, SQLiteDatabase.CONFLICT_REPLACE)
        changed += table
        if (record) queue(table, id, OP_UPSERT)
    }

    fun delete(table: Table, id: String, record: Boolean = true) {
        val removed = writableDatabase.delete(table.name, "id = ?", arrayOf(id))
        if (removed > 0) changed += table
        if (record && removed > 0) queue(table, id, OP_DELETE)
    }

    fun deleteWhere(table: Table, where: String, args: Array<String>) {
        ids(table, where, args).forEach { delete(table, it) }
    }

    private fun ids(table: Table, where: String, args: Array<String>): List<String> {
        val c = readableDatabase.rawQuery("SELECT id FROM ${table.name} WHERE $where", args)
        try {
            return buildList { while (c.moveToNext()) add(c.getString(0)) }
        } finally {
            c.close()
        }
    }

    private fun queue(table: Table, id: String, op: String) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO outbox (entity, id, op, mutation_id, seq, error) " +
                "VALUES (?, ?, ?, ?, (SELECT COALESCE(MAX(seq), 0) + 1 FROM outbox), NULL)",
            arrayOf(table.name, id, op, UUID.randomUUID().toString()),
        )
    }

    /** Tables written since the last call, so a reader can refresh only those. */
    fun takeChanged(): Set<Table> = changed.toSet().also { changed.clear() }

    /** Runs [block] in one transaction so related rows change together. */
    fun <T> tx(block: () -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val r = block()
            db.setTransactionSuccessful()
            return r
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        const val OP_UPSERT = "upsert"
        const val OP_DELETE = "delete"
    }
}
