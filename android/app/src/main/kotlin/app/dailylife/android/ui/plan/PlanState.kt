package app.dailylife.android.ui.plan

import app.dailylife.android.data.Snapshot
import app.dailylife.android.domain.DayPlanner
import app.dailylife.core.model.PrayerName
import app.dailylife.core.model.SessionState
import app.dailylife.core.model.Task
import app.dailylife.core.model.TaskStatus
import java.time.Instant
import java.time.LocalDate

enum class PlanView {
    DAY,
    WEEK,
    AGENDA,
}

enum class PlanItemKind {
    TASK,
    PRAYER,
    EVENT,
}

/** Something occupying time on the plan: a task session, a prayer period or a calendar event. */
data class PlanItem(
    val key: String,
    val kind: PlanItemKind,
    val start: Instant,
    val end: Instant,
    /** Task or event title; null for prayers, whose name is localized by the UI. */
    val title: String? = null,
    val prayer: PrayerName? = null,
    val taskId: String? = null,
    val eventId: String? = null,
    val done: Boolean = false,
    val needsReview: Boolean = false,
)

/** Everything on [date], in time order. Canceled sessions are left out. */
fun planItems(snapshot: Snapshot, dayPlanner: DayPlanner, date: LocalDate): List<PlanItem> {
    val day = dayPlanner.dayContext(snapshot, date)
    val tasks = snapshot.tasks.associateBy { it.id }
    val prayers =
        day.prayers.map {
            PlanItem("p${it.prayer}", PlanItemKind.PRAYER, it.adhan, it.end, prayer = it.prayer)
        }
    val events =
        day.events.map {
            PlanItem(
                "e${it.id}",
                PlanItemKind.EVENT,
                it.start,
                it.end,
                title = it.title,
                eventId = it.id,
            )
        }
    val sessions =
        day.sessions
            .map { it.first }
            .filter { it.state != SessionState.CANCELED }
            .mapNotNull { session ->
                val task = tasks[session.taskId] ?: return@mapNotNull null
                PlanItem(
                    "s${session.id}",
                    PlanItemKind.TASK,
                    session.start,
                    session.end,
                    title = task.title,
                    taskId = task.id,
                    done = task.status == TaskStatus.COMPLETED,
                    needsReview = session.state == SessionState.NEEDS_REVIEW,
                )
            }
    return (prayers + events + sessions).sortedBy { it.start }
}

/** Monday-to-Sunday week containing [date]. */
fun weekOf(date: LocalDate): List<LocalDate> {
    val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
    return (0L..6L).map(monday::plusDays)
}

fun hasSessionsOn(snapshot: Snapshot, date: LocalDate): Boolean =
    snapshot.sessions.any {
        it.state != SessionState.CANCELED &&
            it.start.atZone(snapshot.prayer.zone).toLocalDate() == date
    }

/** Open tasks with no upcoming session and no repeating rule. */
fun unplannedTasks(snapshot: Snapshot, now: Instant): List<Task> =
    snapshot.tasks.filter { task ->
        task.status != TaskStatus.COMPLETED &&
            snapshot.sessions.none {
                it.taskId == task.id && it.end.isAfter(now) && it.state != SessionState.CANCELED
            } &&
            snapshot.rules.none { it.taskId == task.id }
    }
