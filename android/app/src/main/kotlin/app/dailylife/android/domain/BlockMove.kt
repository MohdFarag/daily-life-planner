package app.dailylife.android.domain

import app.dailylife.core.model.SessionState
import app.dailylife.core.model.TaskSession
import app.dailylife.core.model.TimeBlock
import app.dailylife.core.model.TimingRule
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** A session before and after its block moves. */
data class SessionMove(val before: TaskSession, val after: TaskSession)

/**
 * What moving [old] to [new] does to its flexible sessions (BLK04): future, fixed-time, unpinned
 * sessions on the block's days that belong to it (linked, or planned inside its hours) shift with
 * its start. A session that no longer fits inside the block is marked for review.
 */
object BlockMove {
    fun preview(
        old: TimeBlock,
        new: TimeBlock,
        sessions: List<TaskSession>,
        zone: ZoneId,
        now: Instant,
    ): List<SessionMove> {
        val shift = Duration.between(old.start, new.start)
        if (shift.isZero && old.end == new.end) return emptyList()
        return sessions
            .filter {
                it.state == SessionState.PLANNED &&
                    it.timing is TimingRule.Fixed &&
                    !it.pinned &&
                    it.start.isAfter(now)
            }
            .filter { belongsTo(it, old, zone) }
            .map { session ->
                val moved =
                    session.copy(
                        start = session.start.plus(shift),
                        end = session.end.plus(shift),
                        blockId = old.id,
                    )
                val fits = inside(moved, new, zone)
                SessionMove(
                    session,
                    if (fits) moved else moved.copy(state = SessionState.NEEDS_REVIEW),
                )
            }
    }

    private fun belongsTo(session: TaskSession, block: TimeBlock, zone: ZoneId): Boolean {
        val start = session.start.atZone(zone)
        if (start.dayOfWeek !in block.days) return false
        return session.blockId == block.id || inside(session, block, zone)
    }

    private fun inside(session: TaskSession, block: TimeBlock, zone: ZoneId): Boolean {
        val start = session.start.atZone(zone).toLocalTime()
        val end = session.end.atZone(zone).toLocalTime()
        return !start.isBefore(block.start) && !end.isAfter(block.end) && !end.isBefore(start)
    }
}
