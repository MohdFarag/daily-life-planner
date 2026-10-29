package app.dailylife.core.schedule

import app.dailylife.core.model.Relation
import app.dailylife.core.model.RelativeRule
import app.dailylife.core.model.SessionState
import app.dailylife.core.model.TaskSession
import app.dailylife.core.model.TimingRule
import app.dailylife.core.prayer.PrayerAnchor
import app.dailylife.core.time.Interval
import java.time.LocalDate

object RelativeScheduler {

    /**
     * Resolves a prayer-relative rule against that day's anchors (REL01). "Ends before" places the
     * session so it finishes [RelativeRule.offsetMin] before the anchor, which avoids an unintended
     * overlap with the prayer.
     */
    fun resolve(rule: RelativeRule, anchors: List<PrayerAnchor>): Interval {
        val anchor = anchors.first { it.prayer == rule.prayer }.at(rule.anchor)
        val offset = rule.offsetMin * 60L
        val duration = rule.durationMin * 60L
        return when (rule.relation) {
            Relation.STARTS_AFTER ->
                anchor.plusSeconds(offset).let { Interval(it, it.plusSeconds(duration)) }
            Relation.STARTS_BEFORE ->
                anchor.minusSeconds(offset).let { Interval(it, it.plusSeconds(duration)) }
            Relation.ENDS_BEFORE ->
                anchor.minusSeconds(offset).let { Interval(it.minusSeconds(duration), it) }
        }
    }

    fun appliesOn(rule: RelativeRule, date: LocalDate) =
        !date.isBefore(rule.from) &&
            (rule.until == null || !date.isAfter(rule.until)) &&
            (rule.days == null || date.dayOfWeek in rule.days)

    /**
     * Stable id per rule occurrence, so re-running the engine updates instead of duplicating
     * (REL02).
     */
    fun occurrenceId(ruleId: String, date: LocalDate) = "$ruleId@$date"

    /**
     * Rebuilds the rolling window of sessions for a rule (REL02/REL03).
     * - Only dates from [today] onward change; past sessions are left untouched.
     * - Pinned sessions (manual exceptions) and finished/canceled sessions keep their times.
     * - Running this twice yields the same result (idempotent).
     */
    fun refreshWindow(
        taskId: String,
        rule: RelativeRule,
        existing: List<TaskSession>,
        today: LocalDate,
        days: Int,
        anchorsFor: (LocalDate) -> List<PrayerAnchor>?,
        conflictsWith: (Interval) -> Boolean = { false },
    ): List<TaskSession> {
        val byId = existing.associateBy { it.id }
        val out = mutableListOf<TaskSession>()
        for (d in 0 until days) {
            val date = today.plusDays(d.toLong())
            if (!appliesOn(rule, date)) continue
            val id = occurrenceId(rule.id, date)
            val prev = byId[id]
            if (
                prev != null &&
                    (prev.pinned ||
                        prev.state == SessionState.DONE ||
                        prev.state == SessionState.CANCELED)
            ) {
                out += prev
                continue
            }
            // No reliable timetable: keep the last valid session rather than publishing a guess
            // (REL04).
            val anchors =
                anchorsFor(date)
                    ?: run {
                        prev?.let { out += it }
                        continue
                    }
            val slot = resolve(rule, anchors)
            out +=
                TaskSession(
                    id = id,
                    taskId = taskId,
                    start = slot.start,
                    end = slot.end,
                    timing = TimingRule.Relative(rule),
                    occurrenceDate = date,
                    state =
                        if (conflictsWith(slot)) SessionState.NEEDS_REVIEW
                        else SessionState.PLANNED,
                )
        }
        val untouched = existing.filter {
            it.occurrenceDate != null && it.occurrenceDate.isBefore(today)
        }
        return untouched + out
    }
}
