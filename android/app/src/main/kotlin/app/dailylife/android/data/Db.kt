package app.dailylife.android.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/**
 * Local database. Each entity is stored as a JSON document keyed by id, with indexed columns
 * where queries need them. Writes are synchronous and durable before a save is confirmed (CAP02).
 */
class Db(context: Context) : SQLiteOpenHelper(context, "planner.db", null, 1) {

    enum class Table(val sql: String) {
        items("CREATE TABLE items (id TEXT PRIMARY KEY, state TEXT NOT NULL, captured_at TEXT NOT NULL, doc TEXT NOT NULL)"),
        tasks("CREATE TABLE tasks (id TEXT PRIMARY KEY, doc TEXT NOT NULL)"),
        lists("CREATE TABLE lists (id TEXT PRIMARY KEY, doc TEXT NOT NULL)"),
        sessions("CREATE TABLE sessions (id TEXT PRIMARY KEY, task_id TEXT NOT NULL, doc TEXT NOT NULL)"),
        rules("CREATE TABLE rules (id TEXT PRIMARY KEY, task_id TEXT NOT NULL, doc TEXT NOT NULL)"),
        blocks("CREATE TABLE blocks (id TEXT PRIMARY KEY, doc TEXT NOT NULL)"),
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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

    fun put(table: Table, id: String, doc: JSONObject, extra: Map<String, String> = emptyMap()) {
        val v = ContentValues().apply {
            put("id", id)
            put("doc", doc.toString())
            extra.forEach { (k, value) -> put(k, value) }
        }
        writableDatabase.insertWithOnConflict(table.name, null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun delete(table: Table, id: String) {
        writableDatabase.delete(table.name, "id = ?", arrayOf(id))
    }

    fun deleteWhere(table: Table, where: String, args: Array<String>) {
        writableDatabase.delete(table.name, where, args)
    }

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
}
