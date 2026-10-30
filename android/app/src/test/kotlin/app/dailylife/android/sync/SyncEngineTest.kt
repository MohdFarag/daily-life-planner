package app.dailylife.android.sync

import app.dailylife.android.data.Db
import app.dailylife.android.data.OutboxEntry
import app.dailylife.android.data.OutgoingChange
import app.dailylife.android.data.PushOutcome
import app.dailylife.android.data.RemoteChange
import app.dailylife.android.data.SyncStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class SyncEngineTest {
    private fun change(id: String, deleted: Boolean = false) =
        OutgoingChange(
            OutboxEntry(Db.Table.tasks, id, if (deleted) Db.OP_DELETE else Db.OP_UPSERT, "m-$id"),
            0,
            if (deleted) null else JSONObject().put("title", id),
        )

    private class FakeStore(var outbox: MutableList<OutgoingChange>) : SyncStore {
        val settled = mutableListOf<Pair<String, PushOutcome>>()
        val applied = mutableListOf<RemoteChange>()

        override suspend fun outgoing(limit: Int) = outbox.take(limit)

        override suspend fun settle(change: OutgoingChange, outcome: PushOutcome) {
            settled += change.entry.id to outcome
            outbox.remove(change)
        }

        override suspend fun applyRemote(changes: List<RemoteChange>) {
            applied += changes
        }
    }

    private class FakeApi(private val pages: List<PullPage>) : SyncApi {
        val pushed = mutableListOf<List<String>>()
        val pulledFrom = mutableListOf<Long>()

        override suspend fun push(changes: List<OutgoingChange>): List<PushOutcome> {
            pushed += changes.map { it.entry.id }
            return changes.map {
                if (it.entry.id == "bad") PushOutcome.Rejected("invalid") else PushOutcome.Saved(1)
            }
        }

        override suspend fun pull(since: Long, limit: Int): PullPage {
            pulledFrom += since
            return pages[pulledFrom.size - 1]
        }
    }

    private class MemoryCursor(override var cursor: Long = 0) : CursorStore

    private fun remote(id: String, version: Long) =
        RemoteChange("tasks", id, version, false, JSONObject())

    @Test
    fun `pushes the outbox in batches, then pulls every page and keeps the cursor`() = runBlocking {
        val outbox = (1..SyncEngine.PUSH_BATCH + 3).map { change("t$it") }.toMutableList()
        outbox += change("bad")
        val store = FakeStore(outbox)
        val api =
            FakeApi(
                listOf(
                    PullPage(listOf(remote("a", 5)), cursor = 5, hasMore = true),
                    PullPage(listOf(remote("b", 9)), cursor = 9, hasMore = false),
                )
            )
        val cursor = MemoryCursor(2)

        val report = SyncEngine(api, store, cursor).run()

        assertEquals(listOf(SyncEngine.PUSH_BATCH, 4), api.pushed.map { it.size })
        assertEquals(
            PushOutcome.Rejected("invalid"),
            store.settled.single { it.first == "bad" }.second,
        )
        assertEquals(
            SyncReport(pushed = SyncEngine.PUSH_BATCH + 3, rejected = 1, pulled = 2),
            report,
        )
        assertEquals(listOf(2L, 5L), api.pulledFrom)
        assertEquals(9L, cursor.cursor)
        assertEquals(listOf("a", "b"), store.applied.map { it.id })
    }

    @Test
    fun `nothing to push still pulls`() = runBlocking {
        val api = FakeApi(listOf(PullPage(emptyList(), cursor = 3, hasMore = false)))
        val report = SyncEngine(api, FakeStore(mutableListOf()), MemoryCursor(3)).run()
        assertEquals(emptyList(), api.pushed)
        assertEquals(SyncReport(0, 0, 0), report)
    }
}
