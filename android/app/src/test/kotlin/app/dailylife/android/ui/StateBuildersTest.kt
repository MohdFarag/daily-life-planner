package app.dailylife.android.ui

import app.dailylife.android.data.RuleBinding
import app.dailylife.android.data.Snapshot
import app.dailylife.android.domain.DayPlanner
import app.dailylife.android.ui.plan.PlanItemKind
import app.dailylife.android.ui.plan.planItems
import app.dailylife.android.ui.plan.unplannedTasks
import app.dailylife.android.ui.plan.weekOf
import app.dailylife.android.ui.task.taskSchedule
import app.dailylife.android.ui.today.DayRow
import app.dailylife.android.ui.today.buildTodayState
import app.dailylife.core.model.CaptureItem
import app.dailylife.core.model.CaptureKind
import app.dailylife.core.model.CaptureState
import app.dailylife.core.model.PrayerName
import app.dailylife.core.model.PrayerPoint
import app.dailylife.core.model.Relation
import app.dailylife.core.model.RelativeRule
import app.dailylife.core.model.SessionState
import app.dailylife.core.model.Task
import app.dailylife.core.model.TaskSession
import app.dailylife.core.model.TaskStatus
import app.dailylife.core.model.WaitingFor
import app.dailylife.core.prayer.PrayerSettings
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StateBuildersTest {
    private val cairo = ZoneId.of("Africa/Cairo")
    private val today = LocalDate.of(2026, 10, 28) // a Wednesday

    private fun at(hhmm: String): Instant =
        today.atTime(java.time.LocalTime.parse(hhmm)).atZone(cairo).toInstant()

    private fun task(
        id: String,
        status: TaskStatus = TaskStatus.NEEDS_ACTION,
        completedAt: Instant? = null,
    ) =
        Task(
            id,
            "Task $id",
            listId = "work",
            status = status,
            createdAt = Instant.EPOCH,
            completedAt = completedAt,
        )

    private fun session(
        id: String,
        taskId: String,
        from: String,
        to: String,
        state: SessionState = SessionState.PLANNED,
    ) = TaskSession(id, taskId, at(from), at(to), state = state)

    private val snapshot =
        Snapshot(
            tasks =
                listOf(
                    task("done", TaskStatus.COMPLETED, at("08:00")),
                    task("next"),
                    task("later"),
                    task("dropped"),
                    task("loose"),
                ),
            sessions =
                listOf(
                    session("s1", "done", "07:00", "07:30"),
                    session("s2", "next", "10:00", "10:45"),
                    session("s3", "later", "13:30", "13:45"),
                    session("s4", "dropped", "11:00", "11:30", SessionState.CANCELED),
                ),
            items =
                listOf(
                    CaptureItem(
                        "w",
                        CaptureKind.TEXT,
                        "Client approval",
                        state = CaptureState.WAITING,
                        capturedAt = Instant.EPOCH,
                        waiting = WaitingFor("Client", null, today.minusDays(3), today),
                    )
                ),
            prayer = PrayerSettings(30.0444, 31.2357, cairo),
            loaded = true,
        )
    private val planner = DayPlanner()
    private val rule =
        RelativeRule(
            "r",
            PrayerName.ASR,
            PrayerPoint.END,
            Relation.STARTS_AFTER,
            10,
            30,
            from = today,
        )

    @Test
    fun `today - progress counts each task once and up next is the first open future session`() {
        val state = buildTodayState(snapshot, planner, today, at("09:41"))
        assertEquals(
            1 to 3,
            state.tasksDone to state.tasksTotal,
        ) // done, next, later; canceled and unscheduled excluded
        assertEquals("next", state.upNext?.task?.id)
        assertEquals(1, state.followUpsDue)
    }

    @Test
    fun `today - rows are in time order and include the prayers before the last session`() {
        val rows = buildTodayState(snapshot, planner, today, at("09:41")).rows
        assertEquals(rows.sortedBy { it.at }, rows)
        val prayers = rows.filterIsInstance<DayRow.Prayer>().map { it.anchor.prayer }
        assertEquals(
            listOf(PrayerName.DHUHR),
            prayers,
        ) // Dhuhr falls between 09:41 and the last session at 13:45
        assertEquals(
            listOf("s1", "s3"),
            rows.filterIsInstance<DayRow.Session>().map { it.item.session.id },
        )
    }

    @Test
    fun `today - no free time after the workday ends`() {
        assertEquals(Duration.ZERO, buildTodayState(snapshot, planner, today, at("21:30")).freeTime)
    }

    @Test
    fun `plan - items for a day are sorted, typed, and skip canceled sessions`() {
        val items = planItems(snapshot, planner, today)
        assertEquals(items.sortedBy { it.start }, items)
        assertEquals(5, items.count { it.kind == PlanItemKind.PRAYER })
        assertEquals(
            listOf("s1", "s2", "s3"),
            items.filter { it.kind == PlanItemKind.TASK }.map { it.key.removePrefix("s") },
        )
        assertTrue(items.first { it.taskId == "done" }.done)
    }

    @Test
    fun `plan - the week runs Monday to Sunday`() {
        val week = weekOf(today)
        assertEquals(DayOfWeek.MONDAY, week.first().dayOfWeek)
        assertEquals(7, week.size)
        assertTrue(today in week)
    }

    @Test
    fun `plan - unplanned tasks exclude scheduled, completed and rule-driven tasks`() {
        val withRule =
            snapshot.copy(
                tasks = snapshot.tasks + task("ruled"),
                rules = listOf(RuleBinding("ruled", rule)),
            )
        assertEquals(
            listOf("dropped", "loose"),
            unplannedTasks(withRule, at("09:41")).map { it.id },
        )
    }

    @Test
    fun `task schedule - lists only the task's live upcoming sessions, soonest first`() {
        val busy =
            snapshot.copy(
                sessions =
                    listOf(
                        session("past", "next", "07:00", "07:30"),
                        session("late", "next", "15:00", "15:30"),
                        session("soon", "next", "10:00", "10:45"),
                        session("off", "next", "11:00", "11:30", SessionState.CANCELED),
                        session("other", "later", "12:00", "12:30"),
                    ),
                rules = listOf(RuleBinding("next", rule), RuleBinding("later", rule)),
            )
        val schedule = taskSchedule(busy, "next", at("09:41"))
        assertEquals(listOf("soon", "late"), schedule.upcoming.map { it.id })
        assertEquals(listOf("next"), schedule.rules.map { it.taskId })
    }
}
