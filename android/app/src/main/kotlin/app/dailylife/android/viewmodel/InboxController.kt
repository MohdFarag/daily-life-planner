package app.dailylife.android.viewmodel

import app.dailylife.android.data.WORK_LIST
import app.dailylife.android.navigation.Screen
import app.dailylife.core.inbox.Triage
import app.dailylife.core.inbox.TriageOutcome
import app.dailylife.core.inbox.Undo
import app.dailylife.core.model.CaptureItem
import app.dailylife.core.model.CaptureKind
import app.dailylife.core.model.CaptureState
import app.dailylife.core.model.WaitingFor
import java.time.LocalDate
import kotlinx.coroutines.launch

/** Triage decisions (TRI01–TRI07), undo, and managing items in collections and trash. */
class InboxController(private val ctx: ControllerScope) {
    private var lastUndo: Undo? = null
    val canUndo: Boolean
        get() = lastUndo != null

    // Triage decisions ---------------------------------------------------------------------

    /** Creates one task and opens it so the user can plan it. */
    fun toTask(item: CaptureItem, listId: String = WORK_LIST) {
        val outcome = Triage.toTasks(item, listOf(item.title), listId, ctx.now())
        apply(outcome) {
            ctx.navigator.replace(Screen.TaskDetail(outcome.newTasks.first().id, fresh = true))
        }
    }

    fun startDoNow(item: CaptureItem) = apply(Triage.startDoNow(item))

    fun confirmDoNow(item: CaptureItem) {
        val next = nextAfter(item)
        apply(Triage.confirmDoNow(item, ctx.now()), message = "completed") { showNext(next) }
    }

    fun toReference(item: CaptureItem) = moveTo(item, Triage.toReference(item), "references")

    fun toSomeday(item: CaptureItem, reviewOn: LocalDate?) =
        moveTo(item, Triage.toSomeday(item, reviewOn), "someday")

    fun toWaiting(item: CaptureItem, waiting: WaitingFor) =
        moveTo(item, Triage.toWaiting(item, waiting), "waitingFor")

    fun trash(item: CaptureItem) = moveTo(item, Triage.trash(item, ctx.now()), "trash")

    /**
     * Splits text into separate inbox items; a plain-text original is resolved once its parts
     * exist.
     */
    fun split(item: CaptureItem, parts: List<String>) {
        val outcome =
            Triage.split(item, parts, ctx.now()).let {
                if (item.entries.isEmpty())
                    it.copy(item = it.item.copy(state = CaptureState.PROCESSED))
                else it
            }
        apply(outcome, message = "movedTo", argKey = "inbox") {
            ctx.navigator.replace(Screen.Process(null))
        }
    }

    /** Resolves list entries one by one: some become tasks, some references (AC03). */
    fun resolveEntries(
        item: CaptureItem,
        taskEntryIds: List<String>,
        referenceEntryIds: List<String>,
        listId: String = WORK_LIST,
    ) =
        apply(
            Triage.resolveEntries(item, taskEntryIds, referenceEntryIds, listId, ctx.now()),
            message = "movedTo",
            argKey = "tasks",
        )

    fun convertToList(item: CaptureItem) =
        ctx.scope.launch {
            val text = listOf(item.title, item.body).filter { it.isNotBlank() }.joinToString("\n")
            ctx.repository.saveItem(
                item.copy(
                    kind = CaptureKind.LIST,
                    entries = Triage.textToEntries(text) { "${item.id}:e$it" },
                )
            )
        }

    fun undoLast() {
        val undo = lastUndo ?: return
        lastUndo = null
        ctx.scope.launch {
            ctx.repository.undo(undo)
            ctx.messages.dismiss()
            val screen = ctx.navigator.current
            if (screen is Screen.Process || screen is Screen.TaskDetail)
                ctx.navigator.replace(Screen.Process(undo.previous.id))
        }
    }

    // Collections and trash ----------------------------------------------------------------

    fun restore(item: CaptureItem) =
        ctx.scope.launch { ctx.repository.saveItem(Triage.restore(item)) }

    fun returnToInbox(item: CaptureItem) =
        ctx.scope.launch { ctx.repository.saveItem(item.copy(state = CaptureState.INBOX)) }

    fun deleteForever(ids: List<String>) = ctx.scope.launch { ctx.repository.purgeItems(ids) }

    // Internals ----------------------------------------------------------------------------

    private fun moveTo(item: CaptureItem, outcome: TriageOutcome, labelKey: String) {
        val next = nextAfter(item)
        apply(outcome, message = "movedTo", argKey = labelKey) {
            if (ctx.navigator.current is Screen.Process) showNext(next)
        }
    }

    private fun apply(
        outcome: TriageOutcome,
        message: String? = null,
        argKey: String? = null,
        then: () -> Unit = {},
    ) =
        ctx.scope.launch {
            ctx.repository.apply(outcome)
            lastUndo = outcome.undo
            then()
            if (message != null) ctx.messages.show(message, argKey) { undoLast() }
        }

    private fun showNext(nextItemId: String?) = ctx.navigator.replace(Screen.Process(nextItemId))

    private fun nextAfter(item: CaptureItem): String? {
        val queue = Triage.queue(ctx.snapshot.items)
        return queue.getOrNull(queue.indexOfFirst { it.id == item.id } + 1)?.id
    }
}
