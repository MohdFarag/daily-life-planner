package app.dailylife.core.time

import java.time.Duration
import java.time.Instant

data class Interval(val start: Instant, val end: Instant) {
    init { require(!end.isBefore(start)) { "end before start: $start..$end" } }

    val duration: Duration get() = Duration.between(start, end)
    fun overlaps(other: Interval) = start < other.end && other.start < end
    fun intersect(other: Interval): Interval? =
        if (overlaps(other)) Interval(maxOf(start, other.start), minOf(end, other.end)) else null
    operator fun contains(other: Interval) = !other.start.isBefore(start) && !other.end.isAfter(end)
}

/** Merges overlapping or touching intervals, so concurrent time is counted once (BLK04, CTX01). */
fun List<Interval>.union(): List<Interval> {
    val sorted = sortedBy { it.start }
    val out = mutableListOf<Interval>()
    for (i in sorted) {
        val last = out.lastOrNull()
        if (last != null && !i.start.isAfter(last.end)) out[out.lastIndex] = Interval(last.start, maxOf(last.end, i.end))
        else out += i
    }
    return out
}

fun List<Interval>.totalDuration(): Duration = union().fold(Duration.ZERO) { acc, i -> acc + i.duration }

/** Parts of [window] not covered by any of [busy]. */
fun freeWithin(window: Interval, busy: List<Interval>): List<Interval> {
    val out = mutableListOf<Interval>()
    var cursor = window.start
    for (b in busy.mapNotNull { it.intersect(window) }.union()) {
        if (b.start > cursor) out += Interval(cursor, b.start)
        cursor = maxOf(cursor, b.end)
    }
    if (cursor < window.end) out += Interval(cursor, window.end)
    return out
}
