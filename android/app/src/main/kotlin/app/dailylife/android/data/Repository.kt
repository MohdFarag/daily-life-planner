package app.dailylife.android.data

import android.content.Context
import app.dailylife.android.data.Db.Table
import app.dailylife.core.focus.EntryKind
import app.dailylife.core.focus.FocusSession
import app.dailylife.core.focus.FocusState
import app.dailylife.core.focus.TimeEntry
import app.dailylife.core.inbox.TriageOutcome
import app.dailylife.core.inbox.Undo
import app.dailylife.core.model.CalendarEvent
import app.dailylife.core.model.CaptureItem
import app.dailylife.core.model.RelativeRule
import app.dailylife.core.model.Task
import app.dailylife.core.model.TaskList
import app.dailylife.core.model.TaskSession
import app.dailylife.core.model.TimeBlock
import app.dailylife.core.prayer.PrayerAnchor
import app.dailylife.core.prayer.PrayerEngine
import app.dailylife.core.prayer.PrayerSettings
import app.dailylife.core.schedule.DayContext
import app.dailylife.core.schedule.Planner
import app.dailylife.core.schedule.RelativeScheduler
import app.dailylife.core.time.Interval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

data class Prefs(
    val lang: String = "en",
    val protectPrayers: Boolean = true,
    val prayerConfirmed: Boolean = false,
    val reminderMinutes: Int = 10,
    val windowDays: Int = 30,
)

data class RuleBinding(val taskId: String, val rule: RelativeRule)

data class Snapshot(
    val items: List<CaptureItem> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val lists: List<TaskList> = emptyList(),
    val sessions: List<TaskSession> = emptyList(),
    val rules: List<RuleBinding> = emptyList(),
    val blocks: List<TimeBlock> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val timeLog: List<TimeEntry> = emptyList(),
    val prayer: PrayerSettings = defaultCity().settings(),
    val prefs: Prefs = Prefs(),
    val focus: FocusSession? = null,
    val loaded: Boolean = false,
)

/**
 * Single source of truth on the device. Every mutation is written to SQLite first and only then
 * published, so anything the UI shows as saved survives process death (CAP02, AC01).
 */
class Repository(private val context: Context) {
    private val db = Db(context)
    private val mutex = Mutex()
    private val engine = PrayerEngine()
    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    val mediaDir: File get() = File(context.filesDir, "media").apply { mkdirs() }

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (db.all(Table.lists).isEmpty()) seed()
            _state.value = read()
        }
    }

    private fun seed() = db.tx {
        val city = defaultCity()
        defaultLists().forEach { db.put(Table.lists, it.id, Codec.encode(it)) }
        defaultBlocks(city.weekend).forEach { db.put(Table.blocks, it.id, Codec.encode(it)) }
        putSetting("prayer", Codec.encode(city.settings()))
    }

    private fun read(): Snapshot {
        val settings = db.all(Table.settings).associateBy { it.getString("_key") }
        return Snapshot(
            items = db.all(Table.items).map(Codec::item),
            tasks = db.all(Table.tasks).map(Codec::task),
            lists = db.all(Table.lists).map(Codec::list),
            sessions = db.all(Table.sessions).map(Codec::session),
            rules = db.all(Table.rules).map { RuleBinding(it.getString("taskId"), Codec.rule(it.getJSONObject("rule"))) },
            blocks = db.all(Table.blocks).map(Codec::block),
            events = db.all(Table.events).map(Codec::event),
            timeLog = settings["timelog"]?.optJSONArray("entries")?.let(::decodeEntries) ?: emptyList(),
            prayer = settings["prayer"]?.let(Codec::prayer) ?: defaultCity().settings(),
            prefs = settings["prefs"]?.let(::decodePrefs) ?: Prefs(),
            focus = settings["focus"]?.let(::decodeFocus),
            loaded = true,
        )
    }

    /** Runs a write on the IO thread, then reloads the published snapshot. */
    private suspend fun <T> write(block: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            val r = db.tx(block)
            _state.value = read()
            r
        }
    }

    private fun putSetting(key: String, doc: JSONObject) = db.put(Table.settings, key, doc.put("_key", key))

    // Capture and triage ------------------------------------------------------------------

    suspend fun saveItem(item: CaptureItem) = write { putItem(item) }
    private fun putItem(i: CaptureItem) = db.put(Table.items, i.id, Codec.encode(i), mapOf("state" to i.state.name, "captured_at" to i.capturedAt.toString()))

    suspend fun apply(outcome: TriageOutcome) = write {
        putItem(outcome.item)
        outcome.newItems.forEach(::putItem)
        outcome.newTasks.forEach { db.put(Table.tasks, it.id, Codec.encode(it)) }
    }

    suspend fun undo(u: Undo) = write {
        putItem(u.previous)
        u.createdItemIds.forEach { db.delete(Table.items, it) }
        u.createdTaskIds.forEach { id ->
            db.delete(Table.tasks, id)
            db.deleteWhere(Table.sessions, "task_id = ?", arrayOf(id))
            db.deleteWhere(Table.rules, "task_id = ?", arrayOf(id))
        }
    }

    /** Permanently deletes an item and its local media (DEL03). */
    suspend fun purge(ids: List<String>) = write {
        ids.forEach { id ->
            db.get(Table.items, id)?.let(Codec::item)?.attachments?.forEach { File(it.localPath).delete() }
            db.delete(Table.items, id)
        }
    }

    // Tasks, lists and sessions -----------------------------------------------------------

    suspend fun saveTask(t: Task) = write { db.put(Table.tasks, t.id, Codec.encode(t)) }

    suspend fun deleteTask(id: String) = write {
        db.delete(Table.tasks, id)
        db.deleteWhere(Table.sessions, "task_id = ?", arrayOf(id))
        db.deleteWhere(Table.rules, "task_id = ?", arrayOf(id))
    }

    suspend fun saveList(l: TaskList) = write { db.put(Table.lists, l.id, Codec.encode(l)) }
    suspend fun saveBlock(b: TimeBlock) = write { db.put(Table.blocks, b.id, Codec.encode(b)) }
    suspend fun deleteBlock(id: String) = write { db.delete(Table.blocks, id) }

    suspend fun saveSession(s: TaskSession) = write { db.put(Table.sessions, s.id, Codec.encode(s), mapOf("task_id" to s.taskId)) }
    suspend fun deleteSession(id: String) = write { db.delete(Table.sessions, id) }

    /** Attaches a prayer-relative rule to a task and publishes its rolling window (REL02). */
    suspend fun setRule(taskId: String, rule: RelativeRule, today: LocalDate) = write {
        db.put(Table.rules, rule.id, JSONObject().put("taskId", taskId).put("rule", Codec.encode(rule)), mapOf("task_id" to taskId))
        refreshRule(taskId, rule, today)
    }

    suspend fun removeRule(ruleId: String, today: LocalDate) = write {
        db.delete(Table.rules, ruleId)
        // Future occurrences go away; past ones stay as history (REL03).
        db.all(Table.sessions).map(Codec::session)
            .filter { it.id.startsWith("$ruleId@") && it.occurrenceDate?.isBefore(today) == false && !it.pinned }
            .forEach { db.delete(Table.sessions, it.id) }
    }

    /** Daily and on settings change: recompute every rule's future window without duplicates. */
    suspend fun refreshAllRules(today: LocalDate) = write {
        db.all(Table.rules).forEach { refreshRule(it.getString("taskId"), Codec.rule(it.getJSONObject("rule")), today) }
    }

    private fun refreshRule(taskId: String, rule: RelativeRule, today: LocalDate) {
        val snapshot = read()
        val existing = snapshot.sessions.filter { it.id.startsWith("${rule.id}@") }
        val listId = snapshot.tasks.firstOrNull { it.id == taskId }?.listId ?: WORK_LIST
        val others = snapshot.copy(sessions = snapshot.sessions.filterNot { it.id.startsWith("${rule.id}@") })
        val window = RelativeScheduler.refreshWindow(
            taskId, rule, existing, today, snapshot.prefs.windowDays,
            anchorsFor = { d -> runCatching { engine.anchors(d, snapshot.prayer) }.getOrNull() },
            conflictsWith = { slot ->
                val date = slot.start.atZone(snapshot.prayer.zone).toLocalDate()
                // The rule is anchored to a prayer, so only meetings, other sessions and protected blocks count here.
                !Planner.check(dayContext(others, date).copy(protectPrayers = false), slot, listId).ok
            },
        )
        window.forEach { db.put(Table.sessions, it.id, Codec.encode(it), mapOf("task_id" to it.taskId)) }
    }

    // Settings, focus and time log --------------------------------------------------------

    suspend fun setPrayer(p: PrayerSettings, today: LocalDate) {
        write { putSetting("prayer", Codec.encode(p)) }
        refreshAllRules(today)
    }

    suspend fun setPrefs(p: Prefs) = write { putSetting("prefs", encodePrefs(p)) }

    suspend fun setFocus(f: FocusSession?) = write {
        if (f == null) db.delete(Table.settings, "focus") else putSetting("focus", encodeFocus(f))
    }

    /** Ends a focus session and appends its intervals to the time log (TIM01). */
    suspend fun logFocus(f: FocusSession) = write {
        val log = read().timeLog + f.entries
        putSetting("timelog", JSONObject().put("entries", encodeEntries(log)))
        db.delete(Table.settings, "focus")
    }

    // Queries ----------------------------------------------------------------------------

    fun anchors(date: LocalDate, s: Snapshot = state.value): List<PrayerAnchor> =
        runCatching { engine.anchors(date, s.prayer) }.getOrDefault(emptyList())

    fun dayContext(s: Snapshot, date: LocalDate): DayContext {
        val zone = s.prayer.zone
        val day = Interval(date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant())
        val listOf = s.tasks.associate { it.id to it.listId }
        return DayContext(
            date = date, zone = zone,
            events = s.events.filter { Interval(it.start, it.end).overlaps(day) },
            sessions = s.sessions.filter { Interval(it.start, it.end).overlaps(day) }.map { it to (listOf[it.taskId] ?: WORK_LIST) },
            prayers = anchors(date, s),
            blocks = s.blocks,
            protectPrayers = s.prefs.protectPrayers,
        )
    }

    /** Full local export including relationships and rules (SEC02). */
    fun exportJson(): String {
        val s = state.value
        return JSONObject()
            .put("format", "daily-life-planner/1")
            .put("exportedAt", Instant.now().toString())
            .put("items", JSONArray(s.items.map(Codec::encode)))
            .put("tasks", JSONArray(s.tasks.map(Codec::encode)))
            .put("lists", JSONArray(s.lists.map(Codec::encode)))
            .put("sessions", JSONArray(s.sessions.map(Codec::encode)))
            .put("rules", JSONArray(s.rules.map { JSONObject().put("taskId", it.taskId).put("rule", Codec.encode(it.rule)) }))
            .put("blocks", JSONArray(s.blocks.map(Codec::encode)))
            .put("prayer", Codec.encode(s.prayer))
            .put("timeLog", encodeEntries(s.timeLog))
            .put("notIncluded", "Attachment binaries are referenced by path and not embedded.")
            .toString(2)
    }

    // Encoding helpers ---------------------------------------------------------------------

    private fun encodePrefs(p: Prefs) = JSONObject().put("lang", p.lang).put("protectPrayers", p.protectPrayers)
        .put("prayerConfirmed", p.prayerConfirmed).put("reminderMinutes", p.reminderMinutes).put("windowDays", p.windowDays)
    private fun decodePrefs(o: JSONObject) = Prefs(o.optString("lang", "en"), o.optBoolean("protectPrayers", true),
        o.optBoolean("prayerConfirmed"), o.optInt("reminderMinutes", 10), o.optInt("windowDays", 30))

    private fun encodeEntries(list: List<TimeEntry>) = JSONArray(list.map {
        JSONObject().put("taskId", it.taskId).put("kind", it.kind.name).put("start", it.start.toString()).put("end", it.end.toString())
    })
    private fun decodeEntries(a: JSONArray) = (0 until a.length()).map { a.getJSONObject(it) }.map {
        TimeEntry(it.optString("taskId").ifEmpty { null }, EntryKind.valueOf(it.getString("kind")), Instant.parse(it.getString("start")), Instant.parse(it.getString("end")))
    }

    private fun encodeFocus(f: FocusSession) = JSONObject().put("taskId", f.taskId).put("planned", f.planned.seconds)
        .put("state", f.state.name).put("segmentStart", f.segmentStart?.toString()).put("entries", encodeEntries(f.entries))
    private fun decodeFocus(o: JSONObject) = FocusSession(
        taskId = o.optString("taskId").ifEmpty { null }, planned = Duration.ofSeconds(o.getLong("planned")),
        state = FocusState.valueOf(o.getString("state")),
        segmentStart = o.optString("segmentStart").takeIf { it.isNotEmpty() && it != "null" }?.let(Instant::parse),
        entries = decodeEntries(o.optJSONArray("entries") ?: JSONArray()),
    )
}
