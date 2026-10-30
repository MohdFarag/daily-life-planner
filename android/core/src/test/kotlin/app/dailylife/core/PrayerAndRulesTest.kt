package app.dailylife.core

import app.dailylife.core.model.PrayerName
import app.dailylife.core.model.PrayerPoint
import app.dailylife.core.model.Relation
import app.dailylife.core.model.RelativeRule
import app.dailylife.core.model.SessionState
import app.dailylife.core.prayer.AdhanSource
import app.dailylife.core.prayer.EndRule
import app.dailylife.core.prayer.InvalidPrayerSettings
import app.dailylife.core.prayer.IqamaRule
import app.dailylife.core.prayer.PrayerEngine
import app.dailylife.core.prayer.PrayerSettings
import app.dailylife.core.schedule.RelativeScheduler
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PrayerAndRulesTest {
    private val day = LocalDate.of(2026, 10, 28)

    /** Fictional timetable from the PRD's illustrative calculation. */
    private fun fixedSource(asrAdhan: String) = AdhanSource { d, _ ->
        mapOf(
            PrayerName.FAJR to at(d, "05:00"),
            PrayerName.DHUHR to at(d, "12:00"),
            PrayerName.ASR to at(d, asrAdhan),
            PrayerName.MAGHRIB to at(d, "17:30"),
            PrayerName.ISHA to at(d, "19:00"),
        )
    }

    private val settings = PrayerSettings(0.0, 0.0, UTC)

    @Test
    fun `REL01 example - starts 10 minutes after end of Asr for 45 minutes`() {
        val rule =
            RelativeRule(
                "r1",
                PrayerName.ASR,
                PrayerPoint.END,
                Relation.STARTS_AFTER,
                10,
                45,
                from = day,
            )
        // Asr 15:30, iqama +15 = 15:45, end +15 = 16:00
        val anchors = PrayerEngine(fixedSource("15:30")).anchors(day, settings)
        val slot = RelativeScheduler.resolve(rule, anchors)
        assertEquals("16:10" to "16:55", local(slot.start) to local(slot.end))
        // A later day where the period ends at 16:04
        val later = PrayerEngine(fixedSource("15:34")).anchors(day.plusDays(1), settings)
        val slot2 = RelativeScheduler.resolve(rule.copy(), later)
        assertEquals("16:14" to "16:59", local(slot2.start) to local(slot2.end))
    }

    @Test
    fun `ends before keeps the session clear of the prayer`() {
        val rule =
            RelativeRule(
                "r2",
                PrayerName.ASR,
                PrayerPoint.ADHAN,
                Relation.ENDS_BEFORE,
                15,
                45,
                from = day,
            )
        val slot =
            RelativeScheduler.resolve(
                rule,
                PrayerEngine(fixedSource("15:30")).anchors(day, settings),
            )
        assertEquals("14:30" to "15:15", local(slot.start) to local(slot.end))
    }

    @Test
    fun `rolling window is idempotent and keeps pinned and past sessions`() {
        val rule =
            RelativeRule(
                "r3",
                PrayerName.ASR,
                PrayerPoint.END,
                Relation.STARTS_AFTER,
                10,
                45,
                from = day.minusDays(5),
            )
        val engine = PrayerEngine(fixedSource("15:30"))
        val anchors = { d: LocalDate -> engine.anchors(d, settings) }
        val first = RelativeScheduler.refreshWindow("t1", rule, emptyList(), day, 30, anchors)
        assertEquals(30, first.size)
        val second = RelativeScheduler.refreshWindow("t1", rule, first, day, 30, anchors)
        assertEquals(
            first,
            second,
            "running the engine twice must not change or duplicate sessions",
        )

        val pinned =
            first[3].copy(
                start = first[3].start.plusSeconds(3600),
                end = first[3].end.plusSeconds(3600),
                pinned = true,
            )
        val past = first[0].copy(id = "r3@${day.minusDays(1)}", occurrenceDate = day.minusDays(1))
        val changedEngine = PrayerEngine(fixedSource("15:40")) // settings changed: Asr 10 min later
        val third =
            RelativeScheduler.refreshWindow(
                "t1",
                rule,
                listOf(past) + first.toMutableList().also { it[3] = pinned },
                day,
                30,
                { changedEngine.anchors(it, settings) },
            )
        assertTrue(past in third, "past sessions are not rewritten")
        assertEquals(
            pinned,
            third.first { it.id == pinned.id },
            "manual exception survives recalculation",
        )
        assertEquals(
            "16:20",
            local(third.first { it.id == first[1].id }.start),
            "dependent future sessions move",
        )
    }

    @Test
    fun `REL04 conflict marks the session for review instead of moving anything`() {
        val rule =
            RelativeRule(
                "r4",
                PrayerName.ASR,
                PrayerPoint.END,
                Relation.STARTS_AFTER,
                10,
                45,
                from = day,
            )
        val engine = PrayerEngine(fixedSource("15:30"))
        val out =
            RelativeScheduler.refreshWindow(
                "t1",
                rule,
                emptyList(),
                day,
                1,
                { engine.anchors(it, settings) },
                conflictsWith = { true },
            )
        assertEquals(SessionState.NEEDS_REVIEW, out.single().state)
    }

    @Test
    fun `missing timetable keeps the last valid session instead of guessing`() {
        val rule =
            RelativeRule(
                "r5",
                PrayerName.ASR,
                PrayerPoint.END,
                Relation.STARTS_AFTER,
                10,
                45,
                from = day,
            )
        val engine = PrayerEngine(fixedSource("15:30"))
        val first =
            RelativeScheduler.refreshWindow(
                "t1",
                rule,
                emptyList(),
                day,
                2,
                { engine.anchors(it, settings) },
            )
        val again = RelativeScheduler.refreshWindow("t1", rule, first, day, 2, { null })
        assertEquals(first, again)
    }

    @Test
    fun `invalid iqama and end ordering is rejected`() {
        val bad =
            settings.copy(
                end = PrayerName.entries.associateWith { EndRule.At(java.time.LocalTime.of(3, 0)) }
            )
        assertFailsWith<InvalidPrayerSettings> {
            PrayerEngine(fixedSource("15:30")).anchors(day, bad)
        }
        assertTrue(PrayerEngine(fixedSource("15:30")).validate(day, bad).isNotEmpty())
    }

    @Test
    fun `fixed local iqama applies daylight saving once`() {
        val london = ZoneId.of("Europe/London")
        val s =
            PrayerSettings(
                51.5,
                -0.12,
                london,
                iqama =
                    PrayerName.entries.associateWith { IqamaRule.AfterAdhan(10) } +
                        (PrayerName.DHUHR to IqamaRule.At(java.time.LocalTime.of(13, 15))),
            )
        val engine = PrayerEngine()
        // UK clocks go back on 25 October 2026
        val before =
            engine.anchors(LocalDate.of(2026, 10, 24), s).first { it.prayer == PrayerName.DHUHR }
        val after =
            engine.anchors(LocalDate.of(2026, 10, 26), s).first { it.prayer == PrayerName.DHUHR }
        assertEquals("13:15", local(before.iqama, london))
        assertEquals("13:15", local(after.iqama, london))
        // astronomical Dhuhr shifts by roughly an hour on the local clock across the change, not
        // two
        val shift =
            java.time.Duration.between(
                    before.adhan.atZone(london).toLocalTime(),
                    after.adhan.atZone(london).toLocalTime(),
                )
                .toMinutes()
        assertTrue(shift in -62..-58, "local Dhuhr should move back ~60 minutes, was $shift")
    }

    @Test
    fun `astronomical times for Cairo are ordered and plausible`() {
        val cairo = ZoneId.of("Africa/Cairo")
        val anchors = PrayerEngine().anchors(day, PrayerSettings(30.0444, 31.2357, cairo))
        val adhans = anchors.map { it.adhan }
        assertEquals(adhans.sorted(), adhans)
        val dhuhr =
            anchors.first { it.prayer == PrayerName.DHUHR }.adhan.atZone(cairo).toLocalTime()
        assertTrue(
            dhuhr.isAfter(java.time.LocalTime.of(11, 0)) &&
                dhuhr.isBefore(java.time.LocalTime.of(13, 0)),
            "Dhuhr was $dhuhr",
        )
    }

    @Test
    fun `Friday extends the Dhuhr period`() {
        val friday = LocalDate.of(2026, 10, 30)
        val a =
            PrayerEngine(fixedSource("15:30")).anchors(friday, settings).first {
                it.prayer == PrayerName.DHUHR
            }
        assertEquals("13:00", local(a.end)) // 12:00 + 15 iqama + 15 end + 30 Friday
    }
}
