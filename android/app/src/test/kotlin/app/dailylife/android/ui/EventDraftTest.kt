package app.dailylife.android.ui

import app.dailylife.android.ui.plan.EventDraft
import app.dailylife.android.ui.plan.ScheduleMode
import app.dailylife.core.model.CalendarEvent
import app.dailylife.core.model.PrayerName
import app.dailylife.core.model.PrayerPoint
import app.dailylife.core.model.Relation
import app.dailylife.core.prayer.PrayerAnchor
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventDraftTest {
    private val zone = ZoneId.of("Africa/Cairo")
    private val date = LocalDate.of(2026, 10, 30)

    private fun at(hhmm: String): Instant =
        date.atTime(LocalTime.parse(hhmm)).atZone(zone).toInstant()

    @Test
    fun `fixed events start at the chosen time and last the chosen minutes`() {
        val event =
            EventDraft(
                    "Dentist",
                    date,
                    start = LocalTime.of(14, 30),
                    minutes = 45,
                    location = " Clinic ",
                )
                .toEvent("e1", zone, null)!!
        assertEquals(at("14:30") to at("15:15"), event.start to event.end)
        assertEquals("Clinic", event.location)
        assertNull(event.rule)
    }

    @Test
    fun `all-day events span local midnights`() {
        val event = EventDraft("Holiday", date, allDay = true).toEvent("e2", zone, null)!!
        assertEquals(date.atStartOfDay(zone).toInstant(), event.start)
        assertEquals(date.plusDays(1).atStartOfDay(zone).toInstant(), event.end)
    }

    @Test
    fun `prayer-relative events are timed from that day's prayer and keep their rule (REL01)`() {
        val anchors =
            listOf(PrayerAnchor(PrayerName.DHUHR, date, at("11:40"), at("12:00"), at("12:30")))
        val draft =
            EventDraft(
                "Family call",
                date,
                mode = ScheduleMode.RELATIVE,
                prayer = PrayerName.DHUHR,
                anchor = PrayerPoint.END,
                relation = Relation.STARTS_AFTER,
                offsetMinutes = 15,
                minutes = 30,
            )
        val event = draft.toEvent("e3", zone, anchors)!!
        assertEquals(at("12:45") to at("13:15"), event.start to event.end)
        assertEquals(PrayerName.DHUHR, event.rule!!.prayer)
        assertNull(
            draft.toEvent("e3", zone, anchors = null),
            "cannot be timed without prayer times",
        )
        assertEquals(draft.copy(start = LocalTime.of(12, 45)), EventDraft.from(event, zone))
    }

    @Test
    fun `editing keeps the Google link`() {
        val existing =
            CalendarEvent(
                "e4",
                "Old",
                at("09:00"),
                at("10:00"),
                "primary",
                googleId = "g1",
                description = "notes",
            )
        val edited =
            EventDraft.from(existing, zone)
                .copy(title = "New")
                .toEvent("e4", zone, null, existing)!!
        assertEquals("g1" to "notes", edited.googleId to edited.description)
    }
}
