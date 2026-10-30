package app.dailylife.android

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.dailylife.android.data.Media
import app.dailylife.android.data.Prefs
import app.dailylife.android.data.Repository
import app.dailylife.android.data.Snapshot
import app.dailylife.android.data.WORK_LIST
import app.dailylife.android.work.Reminders
import app.dailylife.core.focus.FocusEngine
import app.dailylife.core.inbox.Triage
import app.dailylife.core.inbox.TriageOutcome
import app.dailylife.core.inbox.Undo
import app.dailylife.core.model.Attachment
import app.dailylife.core.model.CaptureItem
import app.dailylife.core.model.CaptureKind
import app.dailylife.core.model.CaptureState
import app.dailylife.core.model.RelativeRule
import app.dailylife.core.model.SessionState
import app.dailylife.core.model.SourceRef
import app.dailylife.core.model.Task
import app.dailylife.core.model.TaskList
import app.dailylife.core.model.TaskSession
import app.dailylife.core.model.TaskStatus
import app.dailylife.core.model.TimeBlock
import app.dailylife.core.model.WaitingFor
import app.dailylife.core.prayer.PrayerSettings
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

sealed interface Screen {
    data object Today : Screen
    data object Inbox : Screen
    data object Plan : Screen
    data object More : Screen
    data class Process(val itemId: String? = null) : Screen
    data class TaskDetail(val id: String, val fresh: Boolean = false) : Screen
    data object Focus : Screen
    data object Search : Screen
    data object Tasks : Screen
    data object Collections : Screen
    data object Trash : Screen
    data object PrayerSettings : Screen
    data object Blocks : Screen
    data object Lists : Screen
    data object Preferences : Screen
}

enum class Tab(val screen: Screen) { TODAY(Screen.Today), INBOX(Screen.Inbox), PLAN(Screen.Plan), MORE(Screen.More) }

/** Content handed to the app by the Android share sheet (CAP05). */
data class SharePayload(val text: String?, val subject: String?, val uris: List<Uri>, val mime: String?, val fromPackage: String?)

data class Toast(val message: String, val undo: (() -> Unit)? = null, val id: Long = System.nanoTime())

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo: Repository = (app as PlannerApp).repository
    val state: StateFlow<Snapshot> = repo.state

    val stack = mutableStateListOf<Screen>(Screen.Today)
    var captureOpen by mutableStateOf(false)
    var pendingShare by mutableStateOf<SharePayload?>(null)
    /** An earlier capture that looks like the share just saved, and the new copy's id. */
    var duplicateOf by mutableStateOf<Pair<CaptureItem, String>?>(null)
    var scheduleTaskId by mutableStateOf<String?>(null)
    var toast by mutableStateOf<Toast?>(null)
    private var lastUndo: Undo? = null

    init {
        viewModelScope.launch {
            repo.load()
            val today = today()
            repo.refreshAllRules(today)
            repo.purge(Triage.purgeDue(state.value.items, Instant.now()).map { it.id })
        }
        // Re-arm local reminders whenever sessions or tasks change.
        viewModelScope.launch { state.drop(1).collect { if (it.loaded) Reminders.sync(getApplication(), it) } }
    }

    val s get() = state.value
    val lang get() = s.prefs.lang
    fun now(): Instant = Instant.now()
    fun today(): LocalDate = LocalDate.now(s.prayer.zone)
    private fun id() = UUID.randomUUID().toString()
    private fun t(key: String, vararg a: Any) = app.dailylife.android.ui.translate(lang, key, *a)

    /** Title for a capture with no text or file name, e.g. "Photo · 30 Oct, 00:07". */
    private fun untitled(kind: CaptureKind): String {
        val at = now().atZone(s.prayer.zone)
        val time = app.dailylife.android.ui.localizeDigits(at.toLocalTime().withSecond(0).withNano(0).toString(), lang)
        return "${t(app.dailylife.android.ui.kindKey(kind))} · ${app.dailylife.android.ui.shortDate(at.toLocalDate(), lang)}, $time"
    }

    // Navigation ---------------------------------------------------------------------------
    val screen get() = stack.last()
    fun go(sc: Screen) { stack += sc }
    fun replace(sc: Screen) { stack[stack.lastIndex] = sc }
    fun back(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
    fun tab(t: Tab) { stack.clear(); stack += t.screen }
    fun currentTab(): Tab = when (stack.first()) { Screen.Inbox -> Tab.INBOX; Screen.Plan -> Tab.PLAN; Screen.More -> Tab.MORE; else -> Tab.TODAY }

    fun show(message: String, undo: (() -> Unit)? = null) { toast = Toast(message, undo) }

    // Capture (CAP01–CAP05) -----------------------------------------------------------------

    /** Saves a capture; returns only after the durable local write (CAP02). */
    fun capture(kind: CaptureKind, text: String, attachments: List<Attachment> = emptyList(), source: SourceRef = SourceRef("app"), itemId: String = id(), onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            val trimmed = text.trim()
            val firstLine = trimmed.lineSequence().firstOrNull().orEmpty()
            val url = Regex("""https?://\S+""").find(trimmed)?.value
            val effectiveKind = if (kind == CaptureKind.TEXT && url != null && trimmed == url) CaptureKind.LINK else kind
            val entries = if (effectiveKind == CaptureKind.LIST) Triage.textToEntries(trimmed) { "$itemId:e$it" } else emptyList()
            val title = when {
                effectiveKind == CaptureKind.LINK -> runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull() ?: url!!
                effectiveKind == CaptureKind.LIST -> entries.firstOrNull()?.text?.let { "$it…" } ?: firstLine
                firstLine.isNotEmpty() -> firstLine.take(120)
                else -> attachments.firstOrNull()?.name ?: untitled(effectiveKind)
            }
            val body = when (effectiveKind) {
                CaptureKind.LIST, CaptureKind.LINK -> ""
                else -> trimmed.lines().drop(1).joinToString("\n").trim().ifEmpty { if (firstLine.length > 120) trimmed else "" }
            }
            repo.saveItem(
                CaptureItem(
                    id = itemId, kind = effectiveKind, title = title, body = body, url = url, source = source,
                    capturedAt = now(), attachments = attachments, entries = entries,
                ),
            )
            onSaved()
            show(t("savedToInbox"))
        }
    }

    /** Handles an incoming share: copies files now, while the temporary grant is valid (CAP05). */
    fun receiveShare(p: SharePayload) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val attachments = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                p.uris.mapNotNull { Media.copyIn(ctx, it, repo.mediaDir, p.mime) }
            }
            val service = when (p.fromPackage) {
                null -> "share"
                "com.google.android.keep" -> "keep"
                "com.google.android.gm" -> "gmail"
                "com.google.android.youtube" -> "youtube"
                else -> "share:${p.fromPackage}"
            }
            val text = listOfNotNull(p.subject?.takeIf { it.isNotBlank() && p.text?.startsWith(it) != true }, p.text).joinToString("\n")
            val kind = when {
                attachments.isEmpty() -> if (text.lines().count { it.isNotBlank() } > 1 && text.lines().any { it.trimStart().startsWith("☐") || it.trimStart().startsWith("- ") }) CaptureKind.LIST else CaptureKind.TEXT
                attachments.all { it.mimeType.startsWith("image") } -> CaptureKind.IMAGE
                attachments.all { it.mimeType.startsWith("video") } -> CaptureKind.VIDEO
                attachments.all { it.mimeType.startsWith("audio") } -> CaptureKind.AUDIO
                else -> CaptureKind.FILE
            }
            // A similar earlier share: offer to open it or keep a new copy; never match by title (CAP05).
            val similar = s.items.firstOrNull { it.state != CaptureState.TRASHED && it.source.service == service && text.isNotBlank() && (it.title + "\n" + it.body).trim() == text.trim() }
            val newId = id()
            capture(kind, text, attachments, SourceRef(service, url = Regex("""https?://\S+""").find(text)?.value), newId)
            if (similar != null) duplicateOf = similar to newId
        }
    }

    // Triage (TRI01–TRI07) ------------------------------------------------------------------

    private fun applyOutcome(o: TriageOutcome, message: String? = null, then: () -> Unit = {}) {
        viewModelScope.launch {
            repo.apply(o)
            lastUndo = o.undo
            then()
            if (message != null) show(message) { undoLast() }
        }
    }

    val canUndo get() = lastUndo != null

    fun undoLast() {
        val u = lastUndo ?: return
        lastUndo = null
        viewModelScope.launch {
            repo.undo(u)
            toast = null
            if (screen is Screen.Process || screen is Screen.TaskDetail) replace(Screen.Process(u.previous.id))
        }
    }

    private fun nextAfter(item: CaptureItem): String? {
        val q = Triage.queue(s.items)
        return q.getOrNull(q.indexOfFirst { it.id == item.id } + 1)?.id
    }

    fun toTask(item: CaptureItem, listId: String = WORK_LIST) {
        val o = Triage.toTasks(item, listOf(item.title), listId, now())
        applyOutcome(o) { replace(Screen.TaskDetail(o.newTasks.first().id, fresh = true)) }
    }

    fun toTasks(item: CaptureItem, titles: List<String>, listId: String = WORK_LIST) {
        val next = nextAfter(item)
        applyOutcome(Triage.toTasks(item, titles, listId, now()), t("movedTo", t("tasks"))) { replace(Screen.Process(next)) }
    }

    fun startDoNow(item: CaptureItem) = applyOutcome(Triage.startDoNow(item))

    fun confirmDoNow(item: CaptureItem) {
        val next = nextAfter(item)
        applyOutcome(Triage.confirmDoNow(item, now()), t("completed")) { replace(Screen.Process(next)) }
    }

    fun move(item: CaptureItem, where: CaptureState, waiting: WaitingFor? = null, reviewOn: LocalDate? = null) {
        val next = nextAfter(item)
        val o = when (where) {
            CaptureState.REFERENCE -> Triage.toReference(item)
            CaptureState.SOMEDAY -> Triage.toSomeday(item, reviewOn)
            CaptureState.WAITING -> Triage.toWaiting(item, waiting!!)
            CaptureState.TRASHED -> Triage.trash(item, now())
            else -> error("Unsupported move to $where")
        }
        val label = when (where) {
            CaptureState.REFERENCE -> t("references"); CaptureState.SOMEDAY -> t("someday")
            CaptureState.WAITING -> t("waitingFor"); else -> t("trash")
        }
        applyOutcome(o, t("movedTo", label)) { if (screen is Screen.Process) replace(Screen.Process(next)) }
    }

    fun split(item: CaptureItem, parts: List<String>) {
        applyOutcome(Triage.split(item, parts, now()).let { o ->
            // Plain-text split resolves the original once its parts exist as their own items.
            if (item.entries.isEmpty()) o.copy(item = o.item.copy(state = CaptureState.PROCESSED)) else o
        }, t("movedTo", t("inbox"))) { replace(Screen.Process(null)) }
    }

    fun resolveEntries(item: CaptureItem, taskIds: List<String>, refIds: List<String>, listId: String = WORK_LIST) =
        applyOutcome(Triage.resolveEntries(item, taskIds, refIds, listId, now()), t("movedTo", t("tasks")))

    fun convertToList(item: CaptureItem) = viewModelScope.launch {
        val text = listOf(item.title, item.body).filter { it.isNotBlank() }.joinToString("\n")
        repo.saveItem(item.copy(kind = CaptureKind.LIST, entries = Triage.textToEntries(text) { "${item.id}:e$it" }))
    }

    fun restore(item: CaptureItem) = viewModelScope.launch { repo.saveItem(Triage.restore(item)) }
    fun returnToInbox(item: CaptureItem) = viewModelScope.launch { repo.saveItem(item.copy(state = CaptureState.INBOX)) }
    fun deleteForever(ids: List<String>) = viewModelScope.launch { repo.purge(ids) }

    // Tasks ---------------------------------------------------------------------------------

    fun saveTask(task: Task) = viewModelScope.launch { repo.saveTask(task) }

    fun addTask(title: String, listId: String): String {
        val task = Task(id = id(), title = title, listId = listId, createdAt = now())
        viewModelScope.launch { repo.saveTask(task) }
        return task.id
    }

    /** Completion only follows an explicit tap and cancels reminders of future sessions (§6.3). */
    fun toggleDone(task: Task) = viewModelScope.launch {
        val done = task.status != TaskStatus.COMPLETED
        repo.saveTask(task.copy(status = if (done) TaskStatus.COMPLETED else TaskStatus.NEEDS_ACTION, completedAt = if (done) now() else null))
        if (done && s.focus?.taskId == task.id) endFocus()
    }

    fun deleteTask(task: Task) = viewModelScope.launch { repo.deleteTask(task.id); back() }

    // Sessions and scheduling ---------------------------------------------------------------

    fun saveFixedSession(taskId: String, start: Instant, end: Instant, replaceId: String? = null) = viewModelScope.launch {
        repo.saveSession(TaskSession(id = replaceId ?: id(), taskId = taskId, start = start, end = end, pinned = replaceId?.contains('@') == true,
            occurrenceDate = replaceId?.substringAfter('@', "")?.takeIf { it.isNotEmpty() }?.let(LocalDate::parse)))
    }

    fun saveRule(taskId: String, rule: RelativeRule) = viewModelScope.launch { repo.setRule(taskId, rule, today()) }
    fun removeRule(ruleId: String) = viewModelScope.launch { repo.removeRule(ruleId, today()) }
    fun deleteSession(session: TaskSession) = viewModelScope.launch {
        // A rule occurrence is canceled (kept as an exception), not deleted, so the engine won't recreate it.
        if (session.id.contains('@')) repo.saveSession(session.copy(state = SessionState.CANCELED)) else repo.deleteSession(session.id)
    }

    // Focus (DO01, TIM01) ---------------------------------------------------------------------

    fun startFocus(task: Task) = viewModelScope.launch {
        val current = s.focus
        if (current?.taskId == task.id) return@launch
        if (current != null) repo.logFocus(FocusEngine.end(current, now()))
        val minutes = s.sessions.filter { it.taskId == task.id && it.end.isAfter(now()) }.minByOrNull { it.start }
            ?.let { Duration.between(it.start, it.end).toMinutes() } ?: task.estimateMin?.toLong() ?: 25L
        repo.setFocus(FocusEngine.start(task.id, Duration.ofMinutes(minutes), now()))
        if (task.status == TaskStatus.NEEDS_ACTION) repo.saveTask(task.copy(status = TaskStatus.IN_PROGRESS))
    }

    fun pauseFocus() = s.focus?.let { f -> viewModelScope.launch { repo.setFocus(FocusEngine.pause(f, now())) } }
    fun resumeFocus() = s.focus?.let { f -> viewModelScope.launch { repo.setFocus(FocusEngine.resume(f, now())) } }
    fun breakFocus() = s.focus?.let { f -> viewModelScope.launch { repo.setFocus(FocusEngine.takeBreak(f, now())) } }
    fun extendFocus() = s.focus?.let { f -> viewModelScope.launch { repo.setFocus(FocusEngine.extend(f, Duration.ofMinutes(5))) } }
    fun endFocus() = s.focus?.let { f -> viewModelScope.launch { repo.logFocus(FocusEngine.end(f, now())) } }

    // Settings ------------------------------------------------------------------------------

    fun setPrefs(p: Prefs) = viewModelScope.launch { repo.setPrefs(p) }
    fun setPrayer(p: PrayerSettings) = viewModelScope.launch {
        repo.setPrayer(p, LocalDate.now(p.zone))
        repo.setPrefs(s.prefs.copy(prayerConfirmed = true))
    }
    fun saveBlock(b: TimeBlock) = viewModelScope.launch { repo.saveBlock(b) }
    fun deleteBlock(b: TimeBlock) = viewModelScope.launch { repo.deleteBlock(b.id) }
    fun addList(name: String) = viewModelScope.launch { repo.saveList(TaskList(id(), name)) }
}
