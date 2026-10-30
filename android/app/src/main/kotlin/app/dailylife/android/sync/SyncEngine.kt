package app.dailylife.android.sync

import app.dailylife.android.data.OutgoingChange
import app.dailylife.android.data.PushOutcome
import app.dailylife.android.data.RemoteChange
import app.dailylife.android.data.SyncStore

/** One page of the server's change log. */
data class PullPage(val changes: List<RemoteChange>, val cursor: Long, val hasMore: Boolean)

/** The server side of sync. */
interface SyncApi {
    /** Pushes changes in order; returns one outcome per change, in the same order. */
    suspend fun push(changes: List<OutgoingChange>): List<PushOutcome>

    suspend fun pull(since: Long, limit: Int): PullPage
}

/** Where the last pulled position in the server's change log is kept. */
interface CursorStore {
    var cursor: Long
}

data class SyncReport(val pushed: Int, val rejected: Int, val pulled: Int)

/**
 * Two-way sync (SYN01, SYN04): push the outbox first, so the server merges local edits, then pull
 * everything after the last cursor. Safe to repeat: pushes are idempotent and pulls resume.
 */
class SyncEngine(
    private val api: SyncApi,
    private val store: SyncStore,
    private val cursorStore: CursorStore,
) {
    suspend fun run(): SyncReport {
        var pushed = 0
        var rejected = 0
        for (round in 1..MAX_PUSH_BATCHES) {
            val batch = store.outgoing(PUSH_BATCH)
            if (batch.isEmpty()) break
            val outcomes = api.push(batch)
            check(outcomes.size == batch.size) {
                "Server answered ${outcomes.size} of ${batch.size}"
            }
            batch.zip(outcomes).forEach { (change, outcome) ->
                store.settle(change, outcome)
                if (outcome is PushOutcome.Rejected) rejected++ else pushed++
            }
            if (batch.size < PUSH_BATCH) break
        }

        var pulled = 0
        do {
            val page = api.pull(cursorStore.cursor, PULL_PAGE)
            store.applyRemote(page.changes)
            cursorStore.cursor = page.cursor
            pulled += page.changes.size
        } while (page.hasMore)
        return SyncReport(pushed, rejected, pulled)
    }

    companion object {
        const val PUSH_BATCH = 200
        const val PULL_PAGE = 500
        /** Bounds one run; anything left goes out on the next. */
        const val MAX_PUSH_BATCHES = 20
    }
}
