package app.dailylife.android.data

import app.dailylife.android.data.Db.Table

/** A local change waiting in the outbox. [error] is set once the server rejected it. */
data class OutboxEntry(
    val table: Table,
    val id: String,
    val op: String,
    val mutationId: String,
    val error: String? = null,
)

/**
 * Reads and settles the outbox and the per-record server versions that [Db] maintains. All calls
 * run inside the repository's lock.
 */
internal class SyncLedger(private val db: Db) {

    /** Changes still to push, oldest first; rejected ones wait for the user instead. */
    fun pending(limit: Int): List<OutboxEntry> =
        query("WHERE error IS NULL ORDER BY seq LIMIT $limit")

    /** Changes the server rejected (SYN01 "Needs intervention"). */
    fun failures(): List<OutboxEntry> = query("WHERE error IS NOT NULL ORDER BY seq")

    /** Keys ("table/id") of every record with an unsent or rejected change. */
    fun queuedKeys(): Set<String> = query("").map { key(it.table, it.id) }.toSet()

    fun isQueued(table: Table, id: String): Boolean =
        count("SELECT COUNT(*) FROM outbox WHERE entity = ? AND id = ?", table.name, id) > 0

    /** Removes the entry if it was not replaced by a newer edit while it was being pushed. */
    fun settle(entry: OutboxEntry) {
        db.writableDatabase.execSQL(
            "DELETE FROM outbox WHERE entity = ? AND id = ? AND mutation_id = ?",
            arrayOf(entry.table.name, entry.id, entry.mutationId),
        )
    }

    fun reject(entry: OutboxEntry, reason: String) {
        db.writableDatabase.execSQL(
            "UPDATE outbox SET error = ? WHERE entity = ? AND id = ? AND mutation_id = ?",
            arrayOf(reason, entry.table.name, entry.id, entry.mutationId),
        )
    }

    fun drop(table: Table, id: String) {
        db.writableDatabase.execSQL(
            "DELETE FROM outbox WHERE entity = ? AND id = ?",
            arrayOf(table.name, id),
        )
    }

    fun version(table: Table, id: String): Long =
        count("SELECT version FROM sync_meta WHERE entity = ? AND id = ?", table.name, id)

    fun setVersion(table: Table, id: String, version: Long) {
        db.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO sync_meta (entity, id, version) VALUES (?, ?, ?)",
            arrayOf<Any>(table.name, id, version),
        )
    }

    /**
     * Prepares for a different account: forgets server versions and queues every local record, so
     * this device's data is uploaded as new.
     */
    fun restartForNewAccount() {
        val w = db.writableDatabase
        w.execSQL("DELETE FROM sync_meta")
        w.execSQL("DELETE FROM outbox")
        Table.entries.forEach { table ->
            w.execSQL(
                "INSERT INTO outbox (entity, id, op, mutation_id, seq) " +
                    "SELECT '${table.name}', id, '${Db.OP_UPSERT}', " +
                    "lower(hex(randomblob(16))), rowid FROM ${table.name}"
            )
        }
    }

    private fun query(clause: String): List<OutboxEntry> {
        val c =
            db.readableDatabase.rawQuery(
                "SELECT entity, id, op, mutation_id, error FROM outbox $clause",
                null,
            )
        try {
            return buildList {
                while (c.moveToNext()) {
                    val table = Table.entries.firstOrNull { it.name == c.getString(0) } ?: continue
                    add(
                        OutboxEntry(
                            table,
                            c.getString(1),
                            c.getString(2),
                            c.getString(3),
                            if (c.isNull(4)) null else c.getString(4),
                        )
                    )
                }
            }
        } finally {
            c.close()
        }
    }

    private fun count(sql: String, vararg args: String): Long {
        val c = db.readableDatabase.rawQuery(sql, args)
        try {
            return if (c.moveToFirst()) c.getLong(0) else 0
        } finally {
            c.close()
        }
    }

    companion object {
        fun key(table: Table, id: String) = "${table.name}/$id"
    }
}
