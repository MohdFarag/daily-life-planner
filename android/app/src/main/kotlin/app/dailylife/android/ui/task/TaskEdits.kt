package app.dailylife.android.ui.task

import app.dailylife.core.model.Task
import app.dailylife.core.model.TaskList
import java.time.LocalDate

/** Edits offered on the task screen. Pure functions: each returns the updated task. */
object TaskEdits {
    private val ESTIMATES = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)

    /** Steps through common estimates; after the longest comes "not set". */
    fun nextEstimate(task: Task): Task {
        val index = ESTIMATES.indexOf(task.estimateMin)
        return task.copy(
            estimateMin = if (index == ESTIMATES.lastIndex) null else ESTIMATES[index + 1]
        )
    }

    fun nextList(task: Task, lists: List<TaskList>): Task {
        if (lists.isEmpty()) return task
        val index = lists.indexOfFirst { it.id == task.listId }
        return task.copy(listId = lists[(index + 1) % lists.size].id)
    }

    /**
     * Sets the real deadline; an early target after it no longer makes sense and is cleared
     * (PAR01).
     */
    fun withDeadline(task: Task, deadline: LocalDate): Task =
        task.copy(
            deadline = deadline,
            targetDate = task.targetDate?.takeIf { !it.isAfter(deadline) },
        )

    /**
     * Sets "my completion target"; refused (null) when it would fall after the real deadline
     * (PAR01).
     */
    fun withTarget(task: Task, target: LocalDate): Task? =
        if (task.deadline != null && target.isAfter(task.deadline)) null
        else task.copy(targetDate = target)

    fun withTags(task: Task, text: String): Task =
        task.copy(tags = text.split(',').map(String::trim).filter(String::isNotEmpty).toSet())
}
