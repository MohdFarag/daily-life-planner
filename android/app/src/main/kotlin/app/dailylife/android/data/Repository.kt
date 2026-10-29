package app.dailylife.android.data

import android.content.Context
import app.dailylife.android.data.Db.Table
import app.dailylife.core.focus.FocusSession
import app.dailylife.core.inbox.TriageOutcome
import app.dailylife.core.inbox.Undo
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
}

/**
 * Device storage and the single source of truth for the UI. Every write goes to SQLite in a
 * transaction before the new [Snapshot] is published, so anything shown as saved survives process
 * death (CAP02, AC01). This class only stores data; rules live in core and in `domain/`.
 */
class Repository(context: Context) {
    private val db = Db(context)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

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
            db.get(Table.items, id)?.let(Codec::item)?.attachments?.forEach {
                File(it.localPath).delete()
            }
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
        db.put(
            Table.rules,
            binding.rule.id,
            Codec.encode(binding),
            mapOf("task_id" to binding.taskId),
        )
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

    suspend fun saveFocus(focus: FocusSession) = write {
        putSetting(SettingKey.FOCUS, Codec.encode(focus))
    }

    /** Clears the running focus session and appends its intervals to the time log (TIM01). */
    suspend fun finishFocus(ended: FocusSession) = write {
        val log = read().timeLog + ended.entries
        putSetting(SettingKey.TIME_LOG, JSONObject().put("entries", Codec.encode(log)))
        db.delete(Table.settings, SettingKey.FOCUS)
    }

    // Internals ----------------------------------------------------------------------------

    /** Runs [block] in a transaction on the IO thread, then publishes the new snapshot. */
    private suspend fun <T> write(block: () -> T): T =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val result = db.tx(block)
                _state.value = read()
                result
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
            loaded = true,
        )
    }

    private fun seedDefaults() = db.tx {
        val city = defaultCity()
        defaultLists().forEach { db.put(Table.lists, it.id, Codec.encode(it)) }
        defaultBlocks(city.weekend).forEach { db.put(Table.blocks, it.id, Codec.encode(it)) }
        putSetting(SettingKey.PRAYER, Codec.encode(city.settings()))
    }

    private fun putItem(item: CaptureItem) =
        db.put(
            Table.items,
            item.id,
            Codec.encode(item),
            mapOf("state" to item.state.name, "captured_at" to item.capturedAt.toString()),
        )

    private fun putTask(task: Task) = db.put(Table.tasks, task.id, Codec.encode(task))

    private fun putSession(session: TaskSession) =
        db.put(
            Table.sessions,
            session.id,
            Codec.encode(session),
            mapOf("task_id" to session.taskId),
        )

    /** A task and everything scheduled for it. */
    private fun removeTaskRows(taskId: String) {
        db.delete(Table.tasks, taskId)
        db.deleteWhere(Table.sessions, "task_id = ?", arrayOf(taskId))
        db.deleteWhere(Table.rules, "task_id = ?", arrayOf(taskId))
    }

    private fun putSetting(key: String, doc: JSONObject) =
        db.put(Table.settings, key, doc.put("_key", key))
}
