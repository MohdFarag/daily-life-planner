package app.dailylife.android.ui

import app.dailylife.android.data.CITIES
import app.dailylife.android.data.Snapshot
import app.dailylife.android.domain.DayPlanner
import app.dailylife.android.ui.capture.CaptureMode
import app.dailylife.android.ui.capture.captureKind
import app.dailylife.android.ui.plan.ScheduleDraft
import app.dailylife.android.ui.plan.ScheduleMode
import app.dailylife.android.ui.plan.evaluateSchedule
import app.dailylife.android.ui.settings.PrayerSettingsEdits
import app.dailylife.android.ui.task.TaskEdits
import app.dailylife.core.model.Attachment
import app.dailylife.core.model.CaptureKind
import app.dailylife.core.model.PrayerName
import app.dailylife.core.model.PrayerPoint
import app.dailylife.core.model.Relation
import app.dailylife.core.model.Task
import app.dailylife.core.model.TaskList
import app.dailylife.core.prayer.PrayerSettings
import app.dailylife.core.schedule.Conflict
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenLogicTest {
    private val cairo = ZoneId.of("Africa/Cairo")
    private val day = LocalDate.of(2026, 10, 28)
    private val task =
        Task("t", "Read", listId = "work", estimateMin = 45, createdAt = Instant.EPOCH)
    private val snapshot =
        Snapshot(
            tasks = listOf(task),
            prayer = PrayerSettings(30.0444, 31.2357, cairo),
            loaded = true,
        )
    private val planner = DayPlanner()
    private val morning = day.atTime(9, 41).atZone(cairo).toInstant()

    @Test
    fun `schedule - a new draft starts at the next quarter hour with the task estimate`() {
        val draft = ScheduleDraft.initial(task, null, snapshot, day, morning)
        assertEquals(LocalTime.of(9, 45), draft.start)
        assertEquals(45, draft.minutes)
        assertEquals(60, draft.nextDuration().minutes)
    }

    @Test
    fun `schedule - a fixed time over a prayer conflicts and suggests free slots`() {
        val dhuhr =
            planner
                .anchors(snapshot, day)
                .first { it.prayer == PrayerName.DHUHR }
                .adhan
                .atZone(cairo)
                .toLocalTime()
        val draft = ScheduleDraft(ScheduleMode.FIXED, day, dhuhr, 30)
        val result = evaluateSchedule(draft, task, null, snapshot, planner, morning)
        assertFalse(result.check!!.ok)
        assertTrue(result.check!!.conflicts.single() is Conflict.Prayer)
        assertTrue(result.suggestions.isNotEmpty())
    }

    @Test
    fun `schedule - a prayer-relative draft resolves against that day's prayer`() {
        val draft =
            ScheduleDraft(
                ScheduleMode.RELATIVE,
                day,
                LocalTime.NOON,
                45,
                PrayerName.ASR,
                PrayerPoint.END,
                Relation.STARTS_AFTER,
                10,
            )
        val result = evaluateSchedule(draft, task, null, snapshot, planner, morning)
        val asrEnd = planner.anchors(snapshot, day).first { it.prayer == PrayerName.ASR }.end
        assertEquals(asrEnd.plusSeconds(10 * 60), result.slot!!.start)
        assertEquals(result.slot!!.start.plusSeconds(45 * 60), result.slot!!.end)
        assertTrue(result.suggestions.isEmpty())
    }

    @Test
    fun `task edits - estimates cycle back to not set`() {
        assertEquals(60, TaskEdits.nextEstimate(task).estimateMin)
        assertNull(TaskEdits.nextEstimate(task.copy(estimateMin = 120)).estimateMin)
        assertEquals(5, TaskEdits.nextEstimate(task.copy(estimateMin = null)).estimateMin)
    }

    @Test
    fun `task edits - the target never falls after the deadline`() {
        val withTarget = task.copy(targetDate = day.plusDays(10))
        assertNull(TaskEdits.withDeadline(withTarget, day.plusDays(5)).targetDate)
        val withDeadline = task.copy(deadline = day.plusDays(5))
        assertNull(TaskEdits.withTarget(withDeadline, day.plusDays(6)))
        assertEquals(
            day.plusDays(4),
            TaskEdits.withTarget(withDeadline, day.plusDays(4))?.targetDate,
        )
    }

    @Test
    fun `task edits - lists cycle and tags are trimmed`() {
        val lists = listOf(TaskList("work", "Work"), TaskList("personal", "Personal"))
        assertEquals("personal", TaskEdits.nextList(task, lists).listId)
        assertEquals("work", TaskEdits.nextList(task.copy(listId = "personal"), lists).listId)
        assertEquals(
            setOf("calls", "deep work"),
            TaskEdits.withTags(task, " calls, ,deep work ").tags,
        )
    }

    @Test
    fun `capture - the kind follows attachments, else the mode`() {
        val photo = Attachment("a", "/m/a.jpg", "image/jpeg", 1)
        val voice = Attachment("b", "/m/b.m4a", "audio/mp4", 1)
        assertEquals(CaptureKind.LIST, captureKind(CaptureMode.LIST, emptyList()))
        assertEquals(CaptureKind.TEXT, captureKind(CaptureMode.PHOTO, emptyList()))
        assertEquals(CaptureKind.IMAGE, captureKind(CaptureMode.TEXT, listOf(photo)))
        assertEquals(CaptureKind.FILE, captureKind(CaptureMode.VOICE, listOf(photo, voice)))
    }

    @Test
    fun `prayer settings - a new city keeps personal iqama and adjustments`() {
        val mine =
            PrayerSettingsEdits.nextAdjustment(
                PrayerSettingsEdits.nextIqama(snapshot.prayer, PrayerName.DHUHR),
                PrayerName.DHUHR,
            )
        val moved = PrayerSettingsEdits.withCity(mine, CITIES.first { it.name == "Riyadh" })
        assertEquals("Riyadh", moved.label)
        assertEquals(20, PrayerSettingsEdits.iqamaMinutes(moved, PrayerName.DHUHR))
        assertEquals(1, moved.adjustmentsMin[PrayerName.DHUHR])
    }

    @Test
    fun `prayer settings - adjustments wrap from +5 to -5 and Friday extra cycles`() {
        var settings = snapshot.prayer
        repeat(6) { settings = PrayerSettingsEdits.nextAdjustment(settings, PrayerName.FAJR) }
        assertEquals(-5, settings.adjustmentsMin[PrayerName.FAJR])
        val fridays =
            generateSequence(snapshot.prayer.copy(fridayExtraMin = 0)) {
                    PrayerSettingsEdits.nextFridayExtra(it)
                }
                .take(6)
                .map { it.fridayExtraMin }
                .toList()
        assertEquals(listOf(0, 15, 30, 45, 60, 0), fridays)
    }

    @Test
    fun `a typed place sets coordinates and zone, and rejects out-of-range values (PRY02)`() {
        val base = snapshot.prayer
        val ok =
            PrayerSettingsEdits.withLocation(
                base,
                " ٢١٫٤٢ ".replace('٫', ','),
                "39.82",
                "Asia/Riyadh",
            )!!
        assertEquals(21.42, ok.latitude)
        assertEquals(39.82, ok.longitude)
        assertEquals(java.time.ZoneId.of("Asia/Riyadh"), ok.zone)
        assertEquals("", ok.label)
        assertEquals(base.method, ok.method)
        assertEquals(null, PrayerSettingsEdits.withLocation(base, "91", "0", "UTC"))
        assertEquals(null, PrayerSettingsEdits.withLocation(base, "10", "181", "UTC"))
        assertEquals(null, PrayerSettingsEdits.withLocation(base, "10", "10", "Mars/Olympus"))
        assertEquals(null, PrayerSettingsEdits.withLocation(base, "", "10", "UTC"))
    }
}
