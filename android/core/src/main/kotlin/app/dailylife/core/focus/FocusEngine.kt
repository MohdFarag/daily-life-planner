package app.dailylife.core.focus

import java.time.Duration
import java.time.Instant

enum class FocusState { RUNNING, PAUSED, BREAK, ENDED }
enum class EntryKind { WORK, BREAK }
enum class EntrySource { TIMER, MANUAL }

/** An actual interval of work or rest. The timer and manual logging produce the same records (TIM01). */
data class TimeEntry(val taskId: String?, val kind: EntryKind, val start: Instant, val end: Instant, val source: EntrySource = EntrySource.TIMER) {
    val duration: Duration get() = Duration.between(start, end)
}

/**
 * One focus session. Paused time is not counted; break time is recorded separately;
 * reaching the planned duration never completes the task by itself (DO01, TIM01).
 */
data class FocusSession(
    val taskId: String?,
    val planned: Duration,
    val state: FocusState,
    val segmentStart: Instant?,          // start of the current running/break segment
    val entries: List<TimeEntry> = emptyList(),
) {
    val workDone: Duration get() = entries.filter { it.kind == EntryKind.WORK }.fold(Duration.ZERO) { a, e -> a + e.duration }
    val breakDone: Duration get() = entries.filter { it.kind == EntryKind.BREAK }.fold(Duration.ZERO) { a, e -> a + e.duration }

    fun worked(now: Instant): Duration =
        workDone + if (state == FocusState.RUNNING && segmentStart != null) Duration.between(segmentStart, now) else Duration.ZERO

    /** Negative once the planned time is exceeded (overtime keeps counting as real work, TIM02). */
    fun remaining(now: Instant): Duration = planned - worked(now)
    fun overtime(now: Instant) = remaining(now).isNegative
}

object FocusEngine {
    fun start(taskId: String?, planned: Duration, now: Instant) = FocusSession(taskId, planned, FocusState.RUNNING, now)

    private fun close(s: FocusSession, now: Instant): FocusSession {
        val begin = s.segmentStart ?: return s
        val kind = when (s.state) { FocusState.RUNNING -> EntryKind.WORK; FocusState.BREAK -> EntryKind.BREAK; else -> return s }
        if (!now.isAfter(begin)) return s.copy(segmentStart = null)
        return s.copy(entries = s.entries + TimeEntry(s.taskId, kind, begin, now), segmentStart = null)
    }

    fun pause(s: FocusSession, now: Instant) = if (s.state != FocusState.RUNNING) s else close(s, now).copy(state = FocusState.PAUSED)
    fun resume(s: FocusSession, now: Instant) =
        if (s.state == FocusState.PAUSED || s.state == FocusState.BREAK) close(s, now).copy(state = FocusState.RUNNING, segmentStart = now) else s
    fun takeBreak(s: FocusSession, now: Instant) =
        if (s.state == FocusState.RUNNING || s.state == FocusState.PAUSED) close(s, now).copy(state = FocusState.BREAK, segmentStart = now) else s
    fun extend(s: FocusSession, by: Duration) = s.copy(planned = s.planned + by)
    fun end(s: FocusSession, now: Instant) = close(s, now).copy(state = FocusState.ENDED)
}
