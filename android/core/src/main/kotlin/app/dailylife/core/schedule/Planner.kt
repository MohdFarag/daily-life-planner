package app.dailylife.core.schedule

import app.dailylife.core.model.BlockPolicy
import app.dailylife.core.model.CalendarEvent
import app.dailylife.core.model.SessionState
import app.dailylife.core.model.TaskSession
import app.dailylife.core.model.TimeBlock
import app.dailylife.core.prayer.PrayerAnchor
import app.dailylife.core.time.Interval
import app.dailylife.core.time.freeWithin
import app.dailylife.core.time.totalDuration
import app.dailylife.core.time.union
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/** Everything that occupies or shapes time on one local date. */
data class DayContext(
    val date: LocalDate,
    val zone: ZoneId,
    val events: List<CalendarEvent> = emptyList(),
    val sessions: List<Pair<TaskSession, String>> = emptyList(), // session to its task's list id
    val prayers: List<PrayerAnchor> = emptyList(),
    val blocks: List<TimeBlock> = emptyList(),
    val protectPrayers: Boolean = true,
) {
    fun blockInterval(b: TimeBlock) =
        Interval(
            date.atTime(b.start).atZone(zone).toInstant(),
            date.atTime(b.end).atZone(zone).toInstant(),
        )

    val activeBlocks
        get() = blocks.filter { date.dayOfWeek in it.days }

    val dayInterval
        get() =
            Interval(
                date.atStartOfDay(zone).toInstant(),
                date.plusDays(1).atStartOfDay(zone).toInstant(),
            )
}

sealed interface Conflict {
    val interval: Interval

    data class Prayer(val anchor: PrayerAnchor, override val interval: Interval) : Conflict

    data class Event(val event: CalendarEvent, override val interval: Interval) : Conflict

    data class Session(val session: TaskSession, override val interval: Interval) : Conflict

    data class ProtectedBlock(val block: TimeBlock, override val interval: Interval) : Conflict
}

/** Non-blocking guidance, e.g. personal work inside a work block (BLK03 guiding block). */
data class Advice(val block: TimeBlock, val message: String)

data class CheckResult(
    val conflicts: List<Conflict>,
    val advice: List<Advice>,
    val insideBlock: TimeBlock?,
) {
    val ok
        get() = conflicts.isEmpty()
}

object Planner {

    /**
     * Checks a proposed session (PLN02). Prayers, events and other sessions conflict when they
     * overlap. A protected block conflicts unless the task's list is allowed in it (BLK03); a
     * guiding block only produces advice.
     */
    fun check(
        ctx: DayContext,
        slot: Interval,
        listId: String,
        ignoreSessionId: String? = null,
    ): CheckResult {
        val conflicts = mutableListOf<Conflict>()
        if (ctx.protectPrayers)
            ctx.prayers.forEach { p ->
                val i = Interval(p.adhan, p.end)
                if (i.overlaps(slot)) conflicts += Conflict.Prayer(p, i)
            }
        ctx.events.forEach { e ->
            Interval(e.start, e.end).let {
                if (it.overlaps(slot)) conflicts += Conflict.Event(e, it)
            }
        }
        ctx.sessions.forEach { (s, _) ->
            if (s.id == ignoreSessionId || s.state == SessionState.CANCELED) return@forEach
            Interval(s.start, s.end).let {
                if (it.overlaps(slot)) conflicts += Conflict.Session(s, it)
            }
        }
        val advice = mutableListOf<Advice>()
        var inside: TimeBlock? = null
        ctx.activeBlocks.forEach { b ->
            val bi = ctx.blockInterval(b)
            if (!bi.overlaps(slot)) return@forEach
            if (slot in bi) inside = b
            val allowed = b.allowedListIds.isEmpty() || listId in b.allowedListIds
            if (!allowed)
                when (b.policy) {
                    BlockPolicy.PROTECTED -> conflicts += Conflict.ProtectedBlock(b, bi)
                    BlockPolicy.GUIDING -> advice += Advice(b, "${b.name} is meant for other work")
                }
        }
        return CheckResult(conflicts, advice, inside)
    }

    /** Time already used inside a block, counting overlaps once (BLK04). */
    fun blockUsage(ctx: DayContext, block: TimeBlock): Pair<Duration, Duration> {
        val bi = ctx.blockInterval(block)
        val used =
            ctx.sessions
                .filter { it.first.state != SessionState.CANCELED }
                .mapNotNull { Interval(it.first.start, it.first.end).intersect(bi) }
                .totalDuration()
        return used to bi.duration
    }

    fun busy(ctx: DayContext): List<Interval> = buildList {
        if (ctx.protectPrayers) ctx.prayers.forEach { add(Interval(it.adhan, it.end)) }
        ctx.events.forEach { add(Interval(it.start, it.end)) }
        ctx.sessions
            .filter { it.first.state != SessionState.CANCELED }
            .forEach { add(Interval(it.first.start, it.first.end)) }
    }
        .union()

    /** Uncommitted time within [window], e.g. "1h 20m left for unplanned work". */
    fun freeTime(ctx: DayContext, window: Interval): Duration =
        freeWithin(window, busy(ctx)).fold(Duration.ZERO) { acc, i -> acc + i.duration }

    /**
     * Up to [count] free slots of [duration] inside [window], preferring blocks that allow the list
     * (REL04 alternatives).
     */
    fun suggest(
        ctx: DayContext,
        window: Interval,
        duration: Duration,
        listId: String,
        count: Int = 3,
        step: Duration = Duration.ofMinutes(15),
    ): List<Interval> {
        val out = mutableListOf<Interval>()
        for (free in freeWithin(window, busy(ctx))) {
            var start = roundUp(free.start, step)
            while (!start.plus(duration).isAfter(free.end) && out.size < count) {
                val slot = Interval(start, start.plus(duration))
                if (check(ctx, slot, listId).ok) {
                    out += slot
                    start = slot.end
                } else start = start.plus(step)
            }
        }
        return out
    }

    private fun roundUp(t: java.time.Instant, step: Duration): java.time.Instant {
        val s = step.seconds
        val r = Math.floorMod(t.epochSecond, s)
        return if (r == 0L) t else t.plusSeconds(s - r)
    }
}
