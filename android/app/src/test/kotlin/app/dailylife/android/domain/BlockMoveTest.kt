package app.dailylife.android.domain

import app.dailylife.core.model.BlockPolicy
import app.dailylife.core.model.PrayerName
import app.dailylife.core.model.PrayerPoint
import app.dailylife.core.model.Relation
import app.dailylife.core.model.RelativeRule
import app.dailylife.core.model.SessionState
import app.dailylife.core.model.TaskSession
import app.dailylife.core.model.TimeBlock
import app.dailylife.core.model.TimingRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockMoveTest {
    private val zone = ZoneId.of("Africa/Cairo")
    private val friday = LocalDate.of(2026, 10, 30)
    private val now = friday.minusDays(1).atStartOfDay(zone).toInstant()
    private val block =
        TimeBlock(
            "deep",
            "Deep work",
            LocalTime.of(9, 0),
            LocalTime.of(12, 0),
            setOf(DayOfWeek.FRIDAY),
            BlockPolicy.PROTECTED,
        )

    private fun at(date: LocalDate, hhmm: String) =
        date.atTime(LocalTime.parse(hhmm)).atZone(zone).toInstant()

    private fun session(
        id: String,
        from: String,
        to: String,
        date: LocalDate = friday,
        blockId: String? = null,
    ) = TaskSession(id, "t", at(date, from), at(date, to), blockId = blockId)

    @Test
    fun `sessions in the block move with its start and keep their length (BLK04)`() {
        val moved =
            BlockMove.preview(
                block,
                block.copy(start = LocalTime.of(10, 0), end = LocalTime.of(13, 0)),
                listOf(session("a", "09:30", "10:30")),
                zone,
                now,
            )
        assertEquals(
            at(friday, "10:30") to at(friday, "11:30"),
            moved.single().after.start to moved.single().after.end,
        )
        assertEquals(SessionState.PLANNED, moved.single().after.state)
    }

    @Test
    fun `a session that no longer fits is marked for review`() {
        val shorter = block.copy(start = LocalTime.of(10, 0), end = LocalTime.of(11, 0))
        val moved =
            BlockMove.preview(block, shorter, listOf(session("a", "10:30", "11:30")), zone, now)
        assertEquals(SessionState.NEEDS_REVIEW, moved.single().after.state)
    }

    @Test
    fun `other days, pinned, past and prayer-relative sessions are left alone`() {
        val rule =
            RelativeRule(
                "r",
                PrayerName.DHUHR,
                PrayerPoint.END,
                Relation.STARTS_AFTER,
                0,
                30,
                from = friday,
            )
        val sessions =
            listOf(
                session("thursday", "09:30", "10:00", date = friday.minusDays(1).plusDays(7)),
                session("pinned", "09:30", "10:00").copy(pinned = true),
                session("relative", "09:30", "10:00").copy(timing = TimingRule.Relative(rule)),
                session("outside", "14:00", "15:00"),
                session("linked", "08:00", "08:30", blockId = "deep"),
            )
        val moved =
            BlockMove.preview(block, block.copy(start = LocalTime.of(10, 0)), sessions, zone, now)
        assertEquals(listOf("linked"), moved.map { it.before.id })
        assertEquals(
            emptyList(),
            BlockMove.preview(block, block.copy(name = "Renamed"), sessions, zone, now),
        )
    }
}
