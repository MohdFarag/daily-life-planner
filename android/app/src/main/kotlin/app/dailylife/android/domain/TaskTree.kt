package app.dailylife.android.domain

import app.dailylife.core.inbox.Triage
import app.dailylife.core.model.Task

/** A task in a list, with how deep it sits under parent tasks. */
data class TaskNode(val task: Task, val depth: Int)

/** Siblings in their saved order (position, as Google Tasks keeps it), then oldest first. */
val siblingOrder: Comparator<Task> =
    compareBy<Task, String?>(nullsLast()) { it.position }.thenBy { it.createdAt }

/**
 * Tasks as lists show them: top-level tasks in [rootOrder], each followed by its subtasks (TSK03).
 * A subtask whose parent is not in [tasks] (filtered out, or deleted) shows at the top level.
 */
fun taskTree(tasks: List<Task>, rootOrder: Comparator<Task>): List<TaskNode> {
    val ids = tasks.map { it.id }.toSet()
    val children = tasks.filter { it.parentId in ids }.groupBy { it.parentId!! }
    val out = mutableListOf<TaskNode>()
    fun add(task: Task, depth: Int) {
        out += TaskNode(task, depth)
        children[task.id].orEmpty().sortedWith(siblingOrder).forEach { add(it, depth + 1) }
    }
    tasks
        .filter { it.parentId == null || it.parentId !in ids }
        .sortedWith(rootOrder)
        .forEach { add(it, 0) }
    return out
}

/**
 * Moves [id] by [delta] places among [siblings] and renumbers them; returns only the tasks whose
 * position changed (so only those are saved and synced).
 */
fun moveAmongSiblings(siblings: List<Task>, id: String, delta: Int): List<Task> {
    val ordered = siblings.sortedWith(siblingOrder).toMutableList()
    val from = ordered.indexOfFirst { it.id == id }
    if (from < 0) return emptyList()
    val to = (from + delta).coerceIn(0, ordered.lastIndex)
    if (to == from) return emptyList()
    ordered.add(to, ordered.removeAt(from))
    return ordered.mapIndexedNotNull { i, task ->
        val position = Triage.position(i)
        if (task.position == position) null else task.copy(position = position)
    }
}
