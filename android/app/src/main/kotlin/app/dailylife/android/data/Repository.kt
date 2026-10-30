package app.dailylife.android.data

import android.content.Context
import app.dailylife.android.data.Db.Table
import app.dailylife.core.focus.FocusSession
import app.dailylife.core.inbox.TriageOutcome
import app.dailylife.core.inbox.Undo
import app.dailylife.core.model.Attachment
import app.dailylife.core.model.CalendarEvent
import app.dailylife.core.model.CaptureItem
import app.dailylife.core.model.Task
import app.dailylife.core.model.TaskList
import app.dailylife.core.model.TaskSession
import app.dailylife.core.model.TimeBlock
import app.dailylife.core.prayer.PrayerSettings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

private object SettingKey {
    const val PRAYER = "prayer"
    const val PREFS = "prefs"
    const val FOCUS = "focus"
    const val TIME_LOG = "timelog"
    const val CALENDARS = "calendars"
}

/**
 * Device storage and the single source of truth for the UI. Every write goes to SQLite in a
 * transaction before the new [Snapshot] is published, so anything shown as saved survives process
 * death (CAP02, AC01). This class only stores data; rules live in core and in `domain/`.
 */
class Repository(context: Context) : SyncStore, AttachmentStore {
    private val db = Db(context)
    private val ledger = SyncLedger(db)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    /** Called after each local change is stored, so a sync can be scheduled. */
    var onLocalChange: (() -> Unit)? = null

    /** Loads stored data, seeding defaults on first launch. */
    suspend fun load() =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (db.all(Table.lists).isEmpty()) seedDefaults()
                _state.value = read()
            }
        }

    // Inbox --------------------------------------------------------------------------------

    suspend fun saveItem(item: CaptureItem) = write { putItem(item) }

    /** Stores a triage decision: the updated item plus any tasks or items it created. */
    suspend fun apply(outcome: TriageOutcome) = write {
        putItem(outcome.item)
        outcome.newItems.forEach(::putItem)
        outcome.newTasks.forEach(::putTask)
    }

    suspend fun undo(undo: Undo) = write {
        putItem(undo.previous)
        undo.createdItemIds.forEach { db.delete(Table.items, it) }
        undo.createdTaskIds.forEach(::removeTaskRows)
    }

    /** Permanently deletes items and their local media (DEL03). */
    suspend fun purgeItems(ids: List<String>) = write {
        ids.forEach { id ->
            deleteMedia(id)
            db.delete(Table.items, id)
        }
    }

    // Tasks, lists, blocks -----------------------------------------------------------------

    suspend fun saveTask(task: Task) = write { putTask(task) }

    suspend fun deleteTask(id: String) = write { removeTaskRows(id) }

    suspend fun saveList(list: TaskList) = write {
        db.put(Table.lists, list.id, Codec.encode(list))
    }

    suspend fun saveBlock(block: TimeBlock) = write {
        db.put(Table.blocks, block.id, Codec.encode(block))
    }

    suspend fun deleteBlock(id: String) = write { db.delete(Table.blocks, id) }

    // Sessions and rules -------------------------------------------------------------------

    suspend fun saveSessions(sessions: List<TaskSession>) = write { sessions.forEach(::putSession) }

    suspend fun deleteSession(id: String) = write { db.delete(Table.sessions, id) }

    /** Stores a rule together with the sessions it generated, in one transaction. */
    suspend fun saveRule(binding: RuleBinding, sessions: List<TaskSession>) = write {
        putDoc(Table.rules, binding.rule.id, Codec.encode(binding))
        sessions.forEach(::putSession)
    }

    suspend fun deleteRule(ruleId: String, sessionIds: List<String>) = write {
        db.delete(Table.rules, ruleId)
        sessionIds.forEach { db.delete(Table.sessions, it) }
    }

    // Settings, focus and time log ---------------------------------------------------------

    suspend fun savePrayerSettings(settings: PrayerSettings) = write {
        putSetting(SettingKey.PRAYER, Codec.encode(settings))
    }

    suspend fun savePrefs(prefs: Prefs) = write {
        putSetting(SettingKey.PREFS, Codec.encode(prefs))
    }

    /** Which calendars to show and where new events go (CAL02). */
    suspend fun saveCalendarSettings(calendars: CalendarSettings) = write {
        putSetting(SettingKey.CALENDARS, Codec.encode(calendars))
    }

    suspend fun saveEvent(event: CalendarEvent) = write {
        db.put(Table.events, event.id, Codec.encode(event))
    }

    suspend fun deleteEvent(id: String) = write { db.delete(Table.events, id) }

    suspend fun saveFocus(focus: FocusSession) = write {
        putSetting(SettingKey.FOCUS, Codec.encode(focus))
    }

    /** Clears the running focus session and appends its intervals to the time log (TIM01). */
    suspend fun finishFocus(ended: FocusSession) = write {
        val log = read().timeLog + ended.entries
        putSetting(SettingKey.TIME_LOG, JSONObject().put("entries", Codec.encode(log)))
        db.delete(Table.settings, SettingKey.FOCUS)
    }

    // Sync (SYN01) -------------------------------------------------------------------------

    override fun items(): List<CaptureItem> = state.value.items

    /** Local changes to push, oldest first. */
    override suspend fun outgoing(limit: Int): List<OutgoingChange> = locked {
        ledger.pending(limit).map { entry ->
            val doc =
                if (entry.op == Db.OP_UPSERT) db.get(entry.table, entry.id)?.let(::forServer)
                else null
            OutgoingChange(entry, ledger.version(entry.table, entry.id), doc)
        }
    }

    /** Records the server's answer to a pushed change. */
    override suspend fun settle(change: OutgoingChange, outcome: PushOutcome) = remoteWrite {
        val entry = change.entry
        when (outcome) {
            is PushOutcome.Saved -> {
                ledger.settle(entry)
                ledger.setVersion(entry.table, entry.id, outcome.version)
                // Fields where the server kept a newer value: take them, unless edited again.
                if (outcome.serverValues.isNotEmpty() && !ledger.isQueued(entry.table, entry.id)) {
                    db.get(entry.table, entry.id)?.let { doc ->
                        outcome.serverValues.forEach { (field, value) -> doc.put(field, value) }
                        db.put(
                            entry.table,
                            entry.id,
                            doc,
                            extrasFor(entry.table, doc),
                            record = false,
                        )
                    }
                }
            }
            is PushOutcome.Rejected -> ledger.reject(entry, outcome.reason)
        }
    }

    /**
     * Applies changes from other devices. A record with an unsent local edit keeps the local
     * version; the push that follows merges it field by field on the server.
     */
    override suspend fun applyRemote(changes: List<RemoteChange>) = remoteWrite {
        changes.forEach { change ->
            val table = Table.entries.firstOrNull { it.name == change.entity }
            if (table == null || ledger.isQueued(table, change.id)) return@forEach
            if (change.deleted) {
                if (table == Table.items) deleteMedia(change.id)
                db.delete(table, change.id, record = false)
            } else {
                val doc =
                    if (table == Table.items) keepLocalPaths(change.id, change.doc) else change.doc
                db.put(table, change.id, doc, extrasFor(table, doc), record = false)
            }
            ledger.setVersion(table, change.id, change.version)
        }
    }

    /** Gives up a change the server rejected; a record deleted elsewhere is removed here too. */
    suspend fun discard(entry: OutboxEntry) = remoteWrite {
        ledger.drop(entry.table, entry.id)
        if (entry.error == REJECTED_DELETED) db.delete(entry.table, entry.id, record = false)
    }

    /** Before syncing with a different account: upload everything on this device as new. */
    suspend fun restartSyncForNewAccount() = remoteWrite { ledger.restartForNewAccount() }

    /**
     * Updates one attachment from the stored item (not a possibly stale copy), so upload progress
     * never overwrites an edit made meanwhile. Synced like any local change.
     */
    override suspend fun updateAttachment(
        itemId: String,
        attachmentId: String,
        change: (Attachment) -> Attachment,
    ) = write {
        val item = db.get(Table.items, itemId)?.let(Codec::item) ?: return@write
        putItem(
            item.copy(
                attachments = item.attachments.map { if (it.id == attachmentId) change(it) else it }
            )
        )
    }

    /** Points an attachment at its downloaded copy (a device-local detail, so not synced). */
    override suspend fun setAttachmentPath(itemId: String, attachmentId: String, path: String) =
        remoteWrite {
            val item = db.get(Table.items, itemId)?.let(Codec::item) ?: return@remoteWrite
            val attachments =
                item.attachments.map {
                    if (it.id == attachmentId) it.copy(localPath = path) else it
                }
            val doc = Codec.encode(item.copy(attachments = attachments))
            db.put(Table.items, itemId, doc, extrasFor(Table.items, doc), record = false)
        }

    // Internals ----------------------------------------------------------------------------

    /** Runs [block] in a transaction on the IO thread, then publishes the new snapshot. */
    private suspend fun <T> write(block: () -> T): T {
        val result = remoteWrite(block)
        onLocalChange?.invoke()
        return result
    }

    /** Like [write], for changes that come from, or settle with, the server. */
    private suspend fun <T> remoteWrite(block: () -> T): T =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val result = db.tx(block)
                _state.value = read()
                result
            }
        }

    private suspend fun <T> locked(block: () -> T): T =
        withContext(Dispatchers.IO) { mutex.withLock { block() } }

    /** Indexed columns derived from a document, for local and remote writes alike. */
    private fun extrasFor(table: Table, doc: JSONObject): Map<String, String> =
        when (table) {
            Table.items ->
                mapOf(
                    "state" to doc.getString("state"),
                    "captured_at" to doc.getString("capturedAt"),
                )
            Table.sessions,
            Table.rules -> mapOf("task_id" to doc.getString("taskId"))
            else -> emptyMap()
        }

    /** File paths only mean something on this device, so they are not sent. */
    private fun forServer(doc: JSONObject): JSONObject {
        val out = JSONObject(doc.toString())
        out.optJSONArray("attachments")?.let { list ->
            for (i in 0 until list.length()) list.getJSONObject(i).remove("localPath")
        }
        return out
    }

    /** Keeps this device's file paths for attachments it already has. */
    private fun keepLocalPaths(itemId: String, incoming: JSONObject): JSONObject {
        val local = db.get(Table.items, itemId)?.let(Codec::item) ?: return incoming
        val paths = local.attachments.associate { it.id to it.localPath }
        incoming.optJSONArray("attachments")?.let { list ->
            for (i in 0 until list.length()) {
                val a = list.getJSONObject(i)
                paths[a.optString("id")]?.let { a.put("localPath", it) }
            }
        }
        return incoming
    }

    private fun deleteMedia(itemId: String) {
        db.get(Table.items, itemId)?.let(Codec::item)?.attachments?.forEach {
            if (it.localPath.isNotEmpty()) File(it.localPath).delete()
        }
    }

    private fun read(): Snapshot {
        val settings = db.all(Table.settings).associateBy { it.getString("_key") }
        return Snapshot(
            items = db.all(Table.items).map(Codec::item),
            tasks = db.all(Table.tasks).map(Codec::task),
            lists = db.all(Table.lists).map(Codec::list),
            sessions = db.all(Table.sessions).map(Codec::session),
            rules = db.all(Table.rules).map(Codec::ruleBinding),
            blocks = db.all(Table.blocks).map(Codec::block),
            events = db.all(Table.events).map(Codec::event),
            timeLog = Codec.timeEntries(settings[SettingKey.TIME_LOG]?.optJSONArray("entries")),
            prayer = settings[SettingKey.PRAYER]?.let(Codec::prayer) ?: defaultCity().settings(),
            prefs = settings[SettingKey.PREFS]?.let(Codec::prefs) ?: Prefs(),
            focus = settings[SettingKey.FOCUS]?.let(Codec::focus),
            calendars = settings[SettingKey.CALENDARS]?.let(Codec::calendars),
            outbox = SyncOutbox(ledger.queuedKeys(), ledger.failures()),
            loaded = true,
        )
    }

    private fun seedDefaults() = db.tx {
        val city = defaultCity()
        defaultLists().forEach { db.put(Table.lists, it.id, Codec.encode(it)) }
        defaultBlocks(city.weekend).forEach { db.put(Table.blocks, it.id, Codec.encode(it)) }
        putSetting(SettingKey.PRAYER, Codec.encode(city.settings()))
    }

    private fun putItem(item: CaptureItem) = putDoc(Table.items, item.id, Codec.encode(item))

    private fun putTask(task: Task) = putDoc(Table.tasks, task.id, Codec.encode(task))

    private fun putSession(session: TaskSession) =
        putDoc(Table.sessions, session.id, Codec.encode(session))

    private fun putDoc(table: Table, id: String, doc: JSONObject) =
        db.put(table, id, doc, extrasFor(table, doc))

    /** A task, its subtasks (as in Google Tasks) and everything scheduled for them. */
    private fun removeTaskRows(taskId: String) {
        db.all(Table.tasks)
            .map(Codec::task)
            .filter { it.parentId == taskId }
            .forEach { removeTaskRows(it.id) }
        db.delete(Table.tasks, taskId)
        db.deleteWhere(Table.sessions, "task_id = ?", arrayOf(taskId))
        db.deleteWhere(Table.rules, "task_id = ?", arrayOf(taskId))
    }

    private fun putSetting(key: String, doc: JSONObject) =
        db.put(Table.settings, key, doc.put("_key", key))

    companion object {
        /** The server's reason when a change targets a record deleted on another device. */
        const val REJECTED_DELETED = "deleted"
    }
}
