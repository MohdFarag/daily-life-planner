package app.dailylife.android.ui.today

import app.dailylife.android.data.Snapshot
import app.dailylife.android.domain.DayPlanner
import app.dailylife.core.inbox.Triage
import app.dailylife.core.model.SessionState
import app.dailylife.core.model.Task
import app.dailylife.core.model.TaskSession
import app.dailylife.core.model.TaskStatus
import app.dailylife.core.prayer.PrayerAnchor
import app.dailylife.core.schedule.Planner
import app.dailylife.core.time.Interval
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** The hours used for "time left for unplanned work". */
private val WORKDAY_START: LocalTime = LocalTime.of(9, 0)
private val WORKDAY_END: LocalTime = LocalTime.of(21, 0)

data class ScheduledTask(val session: TaskSession, val task: Task)

/** One row under "Your day": a prayer or a planned session, in time order. */
sealed interface DayRow {
    val at: Instant

    data class Prayer(val anchor: PrayerAnchor) : DayRow {
        override val at: Instant
            get() = anchor.adhan
    }

    data class Session(val item: ScheduledTask) : DayRow {
        override val at: Instant
            get() = item.session.start
    }
}

data class TodayState(
    val tasksDone: Int,
    val tasksTotal: Int,
    val upNext: ScheduledTask?,
    val rows: List<DayRow>,
    val followUpsDue: Int,
    val freeTime: Duration,
)

/** Everything the Today screen shows, computed from stored data — no UI code here. */
fun buildTodayState(
    snapshot: Snapshot,
    dayPlanner: DayPlanner,
    today: LocalDate,
    now: Instant,
): TodayState {
    val zone = snapshot.prayer.zone
    val dayStart = today.atStartOfDay(zone).toInstant()
    val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant()
    val tasksById = snapshot.tasks.associateBy { it.id }

    val scheduled =
        snapshot.sessions
            .filter {
                it.state != SessionState.CANCELED && it.start >= dayStart && it.start < dayEnd
            }
            .mapNotNull { session -> tasksById[session.taskId]?.let { ScheduledTask(session, it) } }
            .sortedBy { it.session.start }

    // Each task counts once, even with several sessions (AC07).
    val completedToday =
        snapshot.tasks.filter { it.completedAt?.atZone(zone)?.toLocalDate() == today }
    val todaysTasks = (scheduled.map { it.task } + completedToday).associateBy { it.id }.values
    val upNext = scheduled.firstOrNull {
        it.task.status != TaskStatus.COMPLETED && it.session.end.isAfter(now)
    }

    return TodayState(
        tasksDone = todaysTasks.count { it.status == TaskStatus.COMPLETED },
        tasksTotal = todaysTasks.size,
        upNext = upNext,
        rows =
            dayRows(
                scheduled.filter { it != upNext },
                dayPlanner.anchors(snapshot, today),
                scheduled,
                now,
            ),
        followUpsDue = Triage.followUpsDue(snapshot.items, today).size,
        freeTime = freeTime(snapshot, dayPlanner, today, now),
    )
}

/**
 * Sessions plus the prayers that fall before the day's last session (or at least the next prayer).
 */
private fun dayRows(
    sessions: List<ScheduledTask>,
    anchors: List<PrayerAnchor>,
    allScheduled: List<ScheduledTask>,
    now: Instant,
): List<DayRow> {
    val lastEnd = allScheduled.maxOfOrNull { it.session.end } ?: now
    val upcoming = anchors.filter { it.end.isAfter(now) }
    val prayers = upcoming.filter { it.adhan.isBefore(lastEnd) }.ifEmpty { upcoming.take(1) }
    return (prayers.map(DayRow::Prayer) + sessions.map(DayRow::Session)).sortedBy { it.at }
}

private fun freeTime(
    snapshot: Snapshot,
    dayPlanner: DayPlanner,
    today: LocalDate,
    now: Instant,
): Duration {
    val zone = snapshot.prayer.zone
    val end = today.atTime(WORKDAY_END).atZone(zone).toInstant()
    if (!now.isBefore(end)) return Duration.ZERO
    val start = maxOf(now, today.atTime(WORKDAY_START).atZone(zone).toInstant())
    return Planner.freeTime(dayPlanner.dayContext(snapshot, today), Interval(start, end))
}
