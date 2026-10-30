package app.dailylife.android.ui.plan

import app.dailylife.core.model.CalendarEvent
import app.dailylife.core.model.PrayerName
import app.dailylife.core.model.PrayerPoint
import app.dailylife.core.model.Relation
import app.dailylife.core.model.RelativeRule
import app.dailylife.core.prayer.PrayerAnchor
import app.dailylife.core.schedule.RelativeScheduler
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val DURATIONS = listOf(15, 30, 45, 60, 90, 120, 180)
private val OFFSETS = listOf(0, 5, 10, 15, 20, 30, 45, 60)

/** The choices in the event sheet (CAL01, REL01, CAL03). Immutable; edits produce new drafts. */
data class EventDraft(
    val title: String,
    val date: LocalDate,
    val allDay: Boolean = false,
    val mode: ScheduleMode = ScheduleMode.FIXED,
    val start: LocalTime = LocalTime.of(9, 0),
    val minutes: Int = 60,
    val prayer: PrayerName = PrayerName.DHUHR,
    val anchor: PrayerPoint = PrayerPoint.END,
    val relation: Relation = Relation.STARTS_AFTER,
    val offsetMinutes: Int = 10,
    /** Empty: this device only, or the default calendar once Google is connected. */
    val calendarId: String = "",
    val location: String = "",
    val blockId: String? = null,
) {
    fun nextDuration() =
        copy(minutes = DURATIONS[(DURATIONS.indexOf(minutes) + 1) % DURATIONS.size])

    fun nextOffset() =
        copy(offsetMinutes = OFFSETS[(OFFSETS.indexOf(offsetMinutes) + 1) % OFFSETS.size])

    val canSave: Boolean
        get() = title.isNotBlank()

    /**
     * The event this draft describes, or null when it cannot be timed yet (prayer-relative on a day
     * whose prayer times are unknown). [existing] keeps the id and Google link of an edit.
     */
    fun toEvent(
        id: String,
        zone: ZoneId,
        anchors: List<PrayerAnchor>?,
        existing: CalendarEvent? = null,
    ): CalendarEvent? {
        val rule =
            if (mode == ScheduleMode.RELATIVE && !allDay)
                RelativeRule(
                    "event-$id",
                    prayer,
                    anchor,
                    relation,
                    offsetMinutes,
                    minutes,
                    from = date,
                    until = date,
                )
            else null
        val (start, end) =
            when {
                allDay ->
                    date.atStartOfDay(zone).toInstant() to
                        date.plusDays(1).atStartOfDay(zone).toInstant()
                rule != null ->
                    RelativeScheduler.resolve(rule, anchors ?: return null).let {
                        it.start to it.end
                    }
                else ->
                    date.atTime(this.start).atZone(zone).toInstant().let {
                        it to it.plus(Duration.ofMinutes(minutes.toLong()))
                    }
            }
        return CalendarEvent(
            id = id,
            title = title.trim(),
            start = start,
            end = end,
            calendarId = calendarId,
            allDay = allDay,
            location = location.trim().ifEmpty { null },
            description = existing?.description,
            googleId = existing?.googleId,
            rule = rule,
            blockId = blockId,
        )
    }

    companion object {
        fun from(event: CalendarEvent, zone: ZoneId): EventDraft {
            val start = event.start.atZone(zone)
            val rule = event.rule
            return EventDraft(
                title = event.title,
                date = start.toLocalDate(),
                allDay = event.allDay,
                mode = if (rule != null) ScheduleMode.RELATIVE else ScheduleMode.FIXED,
                start = start.toLocalTime(),
                minutes =
                    Duration.between(event.start, event.end).toMinutes().toInt().coerceAtLeast(5),
                prayer = rule?.prayer ?: PrayerName.DHUHR,
                anchor = rule?.anchor ?: PrayerPoint.END,
                relation = rule?.relation ?: Relation.STARTS_AFTER,
                offsetMinutes = rule?.offsetMin ?: 10,
                calendarId = event.calendarId,
                location = event.location.orEmpty(),
                blockId = event.blockId,
            )
        }
    }
}
