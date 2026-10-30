package app.dailylife.android.domain

import app.dailylife.android.data.Snapshot
import app.dailylife.android.data.WORK_LIST
import app.dailylife.core.prayer.PrayerAnchor
import app.dailylife.core.prayer.PrayerEngine
import app.dailylife.core.schedule.DayContext
import app.dailylife.core.time.Interval
import java.time.LocalDate

/** Builds the planning view of a day — prayers, events, sessions, blocks — from stored data. */
class DayPlanner(private val engine: PrayerEngine = PrayerEngine()) {

    /** Prayer anchors for [date]; empty if the settings cannot produce a valid timetable. */
    fun anchors(snapshot: Snapshot, date: LocalDate): List<PrayerAnchor> = runCatching {
        engine.anchors(date, snapshot.prayer)
    }
        .getOrDefault(emptyList())

    /**
     * Like [anchors], but null when no reliable timetable exists, so callers can keep the last
     * valid one (REL04).
     */
    fun anchorsOrNull(snapshot: Snapshot, date: LocalDate): List<PrayerAnchor>? = runCatching {
        engine.anchors(date, snapshot.prayer)
    }
        .getOrNull()

    fun dayContext(snapshot: Snapshot, date: LocalDate): DayContext {
        val zone = snapshot.prayer.zone
        val day =
            Interval(
                date.atStartOfDay(zone).toInstant(),
                date.plusDays(1).atStartOfDay(zone).toInstant(),
            )
        val listOfTask = snapshot.tasks.associate { it.id to it.listId }
        return DayContext(
            date = date,
            zone = zone,
            events = snapshot.events.filter { Interval(it.start, it.end).overlaps(day) },
            sessions =
                snapshot.sessions
                    .filter { Interval(it.start, it.end).overlaps(day) }
                    .map { it to (listOfTask[it.taskId] ?: WORK_LIST) },
            prayers = anchors(snapshot, date),
            blocks = snapshot.blocks,
            protectPrayers = snapshot.prefs.protectPrayers,
        )
    }
}
