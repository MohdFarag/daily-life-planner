package app.dailylife.android.viewmodel

import app.dailylife.core.focus.FocusEngine
import app.dailylife.core.focus.FocusSession
import app.dailylife.core.model.Task
import app.dailylife.core.model.TaskStatus
import java.time.Duration
import kotlinx.coroutines.launch

private const val DEFAULT_FOCUS_MINUTES = 25L

/**
 * The focus timer (DO01, TIM01). Ending a session logs its work and break time; it never completes
 * the task.
 */
class FocusController(private val ctx: ControllerScope) {

    /** Starts focusing on [task], ending any other running session first. */
    fun start(task: Task) =
        ctx.scope.launch {
            val current = ctx.snapshot.focus
            if (current?.taskId == task.id) return@launch
            if (current != null) ctx.repository.finishFocus(FocusEngine.end(current, ctx.now()))
            ctx.repository.saveFocus(
                FocusEngine.start(task.id, Duration.ofMinutes(plannedMinutes(task)), ctx.now())
            )
            if (task.status == TaskStatus.NEEDS_ACTION)
                ctx.repository.saveTask(task.copy(status = TaskStatus.IN_PROGRESS))
        }

    fun pause() = update { FocusEngine.pause(it, ctx.now()) }

    fun resume() = update { FocusEngine.resume(it, ctx.now()) }

    fun takeBreak() = update { FocusEngine.takeBreak(it, ctx.now()) }

    fun extendFiveMinutes() = update { FocusEngine.extend(it, Duration.ofMinutes(5)) }

    fun end() {
        val focus = ctx.snapshot.focus ?: return
        ctx.scope.launch { ctx.repository.finishFocus(FocusEngine.end(focus, ctx.now())) }
    }

    /** The next upcoming session's length, else the estimate, else a default. */
    private fun plannedMinutes(task: Task): Long =
        ctx.snapshot.sessions
            .filter { it.taskId == task.id && it.end.isAfter(ctx.now()) }
            .minByOrNull { it.start }
            ?.let { Duration.between(it.start, it.end).toMinutes() }
            ?: task.estimateMin?.toLong()
            ?: DEFAULT_FOCUS_MINUTES

    /** Applies [change] to the stored session, so quick taps (pause, then resume) all count. */
    private fun update(change: (FocusSession) -> FocusSession) =
        ctx.scope.launch { ctx.repository.updateFocus(change) }
}
