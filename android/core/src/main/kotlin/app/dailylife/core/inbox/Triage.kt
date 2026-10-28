package app.dailylife.core.inbox

import app.dailylife.core.model.CaptureItem
import app.dailylife.core.model.CaptureKind
import app.dailylife.core.model.CaptureState
import app.dailylife.core.model.ListEntry
import app.dailylife.core.model.SourceRef
import app.dailylife.core.model.Task
import app.dailylife.core.model.WaitingFor
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Result of a triage decision: what changed, what was created, and how to undo it (TRI01). */
data class TriageOutcome(
    val item: CaptureItem,
    val newTasks: List<Task> = emptyList(),
    val newItems: List<CaptureItem> = emptyList(),
    val undo: Undo,
)

/** Restores the item and removes anything the decision created. */
data class Undo(val previous: CaptureItem, val createdTaskIds: List<String>, val createdItemIds: List<String>)

val TRASH_RETENTION: Duration = Duration.ofDays(30)

private val BULLET = Regex("""^\s*(?:(?:[-*•▪◦]|\[[ xX]?]|☐|☑|✓|\d+[.)])\s*)+""")

object Triage {

    /** Turns plain text into reviewable list entries (CAP04). */
    fun textToEntries(text: String, idFor: (Int) -> String): List<ListEntry> =
        text.lines().map { it.replace(BULLET, "").trim() }.filter { it.isNotEmpty() }
            .mapIndexed { i, line -> ListEntry(idFor(i), line) }

    /** An incoming share matching an existing item by source identity, never by title (CAP05, GML01). */
    fun findDuplicate(items: List<CaptureItem>, source: SourceRef): CaptureItem? =
        if (source.externalId == null) null
        else items.firstOrNull { it.source.service == source.service && it.source.externalId == source.externalId && it.source.account == source.account && it.state != CaptureState.TRASHED }

    /** Do Now starts work but the item stays in the inbox until completion is confirmed (TRI02, AC04). */
    fun startDoNow(item: CaptureItem) = TriageOutcome(item.copy(state = CaptureState.DOING_NOW), undo = Undo(item, emptyList(), emptyList()))

    fun confirmDoNow(item: CaptureItem, now: Instant) =
        TriageOutcome(item.copy(state = CaptureState.PROCESSED, completedAt = now), undo = Undo(item, emptyList(), emptyList()))

    /**
     * Converts an item into one or more tasks linked to the source (TRI03).
     * Task ids are derived from the item id, so a retried conversion cannot create duplicates.
     */
    fun toTasks(item: CaptureItem, titles: List<String>, listId: String, now: Instant, estimateMin: Int? = null): TriageOutcome {
        require(titles.isNotEmpty()) { "At least one task title is required" }
        val tasks = titles.mapIndexed { i, title ->
            Task(
                id = "${item.id}:task:${i + item.derivedTaskIds.size}",
                title = title.trim(),
                notes = if (titles.size == 1) item.body.takeIf { item.kind != CaptureKind.LIST } ?: "" else "",
                listId = listId,
                estimateMin = estimateMin,
                sourceItemId = item.id,
                createdAt = now,
            )
        }
        val updated = item.copy(state = CaptureState.PROCESSED, derivedTaskIds = item.derivedTaskIds + tasks.map { it.id })
        return TriageOutcome(updated, newTasks = tasks, undo = Undo(item, tasks.map { it.id }, emptyList()))
    }

    fun toReference(item: CaptureItem) = move(item, CaptureState.REFERENCE)
    fun toSomeday(item: CaptureItem, reviewOn: LocalDate? = null) = move(item, CaptureState.SOMEDAY).let { it.copy(item = it.item.copy(reviewOn = reviewOn)) }
    fun toWaiting(item: CaptureItem, waiting: WaitingFor) = move(item, CaptureState.WAITING).let { it.copy(item = it.item.copy(waiting = waiting)) }

    private fun move(item: CaptureItem, to: CaptureState) = TriageOutcome(item.copy(state = to), undo = Undo(item, emptyList(), emptyList()))

    /**
     * Splits selected parts of an item into linked inbox items (TRI07). Each part can then get its own
     * decision. With list entries, the original becomes PROCESSED only once every entry is resolved.
     */
    fun split(item: CaptureItem, parts: List<String>, now: Instant, entryIds: List<String> = emptyList()): TriageOutcome {
        val children = parts.mapIndexed { i, text ->
            CaptureItem(
                id = "${item.id}:part:${item.entries.count { it.resolvedAs != null } + i}",
                kind = CaptureKind.TEXT,
                title = text.lineSequence().first().take(120),
                body = text,
                source = item.source,
                capturedAt = now,
                parentId = item.id,
            )
        }
        val entries = item.entries.map { e ->
            val idx = entryIds.indexOf(e.id)
            if (idx >= 0) e.copy(resolvedAs = "item:${children[idx].id}") else e
        }
        val allResolved = entries.isEmpty() || entries.all { it.resolvedAs != null }
        val updated = item.copy(entries = entries, state = if (allResolved) CaptureState.PROCESSED else item.state)
        return TriageOutcome(updated, newItems = children, undo = Undo(item, emptyList(), children.map { it.id }))
    }

    /**
     * Resolves individual list entries: some become tasks, some a reference (AC03).
     * Unselected entries stay unprocessed and the item stays in the inbox until all are resolved.
     */
    fun resolveEntries(item: CaptureItem, taskEntryIds: List<String>, referenceEntryIds: List<String>, listId: String, now: Instant): TriageOutcome {
        val existingTasks = item.derivedTaskIds.size
        val tasks = item.entries.filter { it.id in taskEntryIds }.mapIndexed { i, e ->
            Task(id = "${item.id}:task:${existingTasks + i}", title = e.text, listId = listId, sourceItemId = item.id, createdAt = now)
        }
        val refs = item.entries.filter { it.id in referenceEntryIds }.map { e ->
            CaptureItem(id = "${item.id}:ref:${e.id}", kind = CaptureKind.TEXT, title = e.text, source = item.source, state = CaptureState.REFERENCE, capturedAt = now, parentId = item.id)
        }
        val taskByEntry = item.entries.filter { it.id in taskEntryIds }.zip(tasks).associate { (e, t) -> e.id to t.id }
        val entries = item.entries.map { e ->
            when (e.id) {
                in taskByEntry -> e.copy(resolvedAs = "task:${taskByEntry.getValue(e.id)}")
                in referenceEntryIds -> e.copy(resolvedAs = "item:${item.id}:ref:${e.id}")
                else -> e
            }
        }
        val updated = item.copy(
            entries = entries,
            derivedTaskIds = item.derivedTaskIds + tasks.map { it.id },
            state = if (entries.all { it.resolvedAs != null }) CaptureState.PROCESSED else item.state,
        )
        return TriageOutcome(updated, tasks, refs, Undo(item, tasks.map { it.id }, refs.map { it.id }))
    }

    /** Moves an item to app trash with a visible purge date (DEL01). The external source is never touched. */
    fun trash(item: CaptureItem, now: Instant) = TriageOutcome(
        item.copy(state = CaptureState.TRASHED, trashedAt = now, purgeAt = now.plus(TRASH_RETENTION), stateBeforeTrash = item.state),
        undo = Undo(item, emptyList(), emptyList()),
    )

    fun restore(item: CaptureItem): CaptureItem {
        check(item.state == CaptureState.TRASHED) { "Item is not in trash" }
        return item.copy(state = item.stateBeforeTrash ?: CaptureState.INBOX, trashedAt = null, purgeAt = null, stateBeforeTrash = null)
    }

    /** Trashed items whose retention has expired and may be permanently deleted. */
    fun purgeDue(items: List<CaptureItem>, now: Instant) = items.filter { it.state == CaptureState.TRASHED && it.purgeAt != null && !now.isBefore(it.purgeAt) }

    /** Items due for a decision, oldest first, as shown in triage mode. */
    fun queue(items: List<CaptureItem>) = items.filter { it.state == CaptureState.INBOX || it.state == CaptureState.DOING_NOW }.sortedBy { it.capturedAt }

    /** Waiting-for items whose follow-up date has arrived (AC05). Nothing is sent to anyone. */
    fun followUpsDue(items: List<CaptureItem>, today: LocalDate) =
        items.filter { it.state == CaptureState.WAITING && it.waiting?.followUpOn?.let { d -> !d.isAfter(today) } == true }
}
