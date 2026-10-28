package app.dailylife.core

import app.dailylife.core.focus.FocusEngine
import app.dailylife.core.model.BlockPolicy
import app.dailylife.core.model.CalendarEvent
import app.dailylife.core.model.TaskSession
import app.dailylife.core.model.TimeBlock
import app.dailylife.core.schedule.Conflict
import app.dailylife.core.schedule.DayContext
import app.dailylife.core.schedule.Planner
import app.dailylife.core.time.Interval
import app.dailylife.core.time.totalDuration
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlannerTest {
    private val day = LocalDate.of(2026, 10, 28)
    private val all = DayOfWeek.entries.toSet()
    private val work = TimeBlock("b1", "Work", LocalTime.of(9, 0), LocalTime.of(12, 0), all, BlockPolicy.GUIDING, setOf("work"))
    private val family = TimeBlock("b2", "Family", LocalTime.of(18, 0), LocalTime.of(20, 0), all, BlockPolicy.PROTECTED, setOf("personal"))
    private fun session(id: String, from: String, to: String) = TaskSession(id, "t-$id", at(day, from), at(day, to))

    @Test fun `BLK04 block capacity counts a session once`() {
        val ctx = DayContext(day, UTC, sessions = listOf(session("s", "09:00", "10:00") to "work"), blocks = listOf(work))
        val (used, total) = Planner.blockUsage(ctx, work)
        assertEquals(Duration.ofHours(1), used)
        assertEquals(Duration.ofHours(3), total)
    }

    @Test fun `AC11 protected block rejects disallowed work but allows its own lists`() {
        val ctx = DayContext(day, UTC, blocks = listOf(family))
        val r = Planner.check(ctx, Interval(at(day, "18:30"), at(day, "19:00")), "work")
        assertTrue(r.conflicts.single() is Conflict.ProtectedBlock)
        assertTrue(Planner.check(ctx, Interval(at(day, "18:30"), at(day, "19:00")), "personal").ok)
    }

    @Test fun `guiding block only advises`() {
        val r = Planner.check(DayContext(day, UTC, blocks = listOf(work)), Interval(at(day, "10:00"), at(day, "10:30")), "personal")
        assertTrue(r.ok)
        assertEquals(1, r.advice.size)
    }

    @Test fun `meetings and sessions conflict, suggestions avoid them`() {
        val meeting = CalendarEvent("e1", "Design review", at(day, "13:00"), at(day, "13:30"), "primary")
        val ctx = DayContext(day, UTC, events = listOf(meeting), sessions = listOf(session("a", "12:00", "13:00") to "work"))
        val r = Planner.check(ctx, Interval(at(day, "12:45"), at(day, "13:15")), "work")
        assertEquals(2, r.conflicts.size)
        val s = Planner.suggest(ctx, Interval(at(day, "12:00"), at(day, "15:00")), Duration.ofMinutes(45), "work")
        assertEquals(local(s.first().start), "13:30")
        assertTrue(s.none { it.overlaps(Interval(meeting.start, meeting.end)) })
    }

    @Test fun `free time subtracts overlapping commitments once`() {
        val ctx = DayContext(day, UTC, sessions = listOf(session("a", "09:00", "10:00") to "work", session("b", "09:30", "10:30") to "work"))
        assertEquals(Duration.ofMinutes(90), Planner.freeTime(ctx, Interval(at(day, "09:00"), at(day, "12:00"))))
    }

    @Test fun `concurrent activities never make a day longer than its span`() {
        val travel = Interval(at(day, "08:00"), at(day, "09:00"))
        val listening = Interval(at(day, "08:10"), at(day, "08:40"))
        assertEquals(Duration.ofHours(1), listOf(travel, listening).totalDuration())
    }

    @Test fun `timer - 25 work, 5 break, 10 more gives 35 work and 5 rest`() {
        val t0 = at(day, "10:00")
        var s = FocusEngine.start("t1", Duration.ofMinutes(25), t0)
        s = FocusEngine.takeBreak(s, t0.plusSeconds(25 * 60))
        s = FocusEngine.resume(s, t0.plusSeconds(30 * 60))
        s = FocusEngine.end(s, t0.plusSeconds(40 * 60))
        assertEquals(Duration.ofMinutes(35), s.workDone)
        assertEquals(Duration.ofMinutes(5), s.breakDone)
    }

    @Test fun `paused time does not count and overtime is still work`() {
        val t0 = at(day, "10:00")
        var s = FocusEngine.start("t1", Duration.ofMinutes(10), t0)
        s = FocusEngine.pause(s, t0.plusSeconds(5 * 60))
        assertEquals(Duration.ofMinutes(5), s.worked(t0.plusSeconds(60 * 60)))
        s = FocusEngine.resume(s, t0.plusSeconds(60 * 60))
        val later = t0.plusSeconds(70 * 60)
        assertTrue(s.overtime(later))
        assertEquals(Duration.ofMinutes(15), s.worked(later))
    }
}
