package app.dailylife.android.sync

import app.dailylife.android.data.AttachmentStore
import app.dailylife.android.data.Codec
import app.dailylife.android.data.Db
import app.dailylife.android.data.OutboxEntry
import app.dailylife.android.data.OutgoingChange
import app.dailylife.android.data.PushOutcome
import app.dailylife.android.data.RemoteChange
import app.dailylife.android.data.SyncStore
import app.dailylife.core.model.Attachment
import app.dailylife.core.model.CaptureItem
import app.dailylife.core.model.CaptureKind
import app.dailylife.core.model.Task
import app.dailylife.core.model.UploadState
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * Runs the real client against a running server. Skipped unless SYNC_SERVER_URL is set, e.g.
 * SYNC_SERVER_URL=http://127.0.0.1:8080 with the server from server/README.md.
 */
class SyncEndToEndTest {
    private val server: String? = System.getenv("SYNC_SERVER_URL")

    private class MemorySession(account: Account, tokens: Tokens) : SessionStore {
        override val account = MutableStateFlow<Account?>(account)
        override var accessToken: String? = tokens.access
        override var refreshToken: String? = tokens.refresh

        override fun saveTokens(tokens: Tokens) {
            accessToken = tokens.access
            refreshToken = tokens.refresh
        }

        override fun signedOut() {
            account.value = null
        }
    }

    /** A device's records in memory, with an outbox of upserts and deletes. */
    private class Device : SyncStore, AttachmentStore, CursorStore {
        val docs = mutableMapOf<Pair<String, String>, JSONObject>()
        val versions = mutableMapOf<Pair<String, String>, Long>()
        val outbox = mutableListOf<OutboxEntry>()
        val outcomes = mutableMapOf<String, PushOutcome>()
        override var cursor = 0L

        fun write(table: Db.Table, id: String, doc: JSONObject?) {
            if (doc == null) docs.remove(table.name to id) else docs[table.name to id] = doc
            outbox.removeAll { it.table == table && it.id == id }
            outbox +=
                OutboxEntry(
                    table,
                    id,
                    if (doc == null) Db.OP_DELETE else Db.OP_UPSERT,
                    UUID.randomUUID().toString(),
                )
        }

        override suspend fun outgoing(limit: Int) =
            outbox.take(limit).map {
                OutgoingChange(
                    it,
                    versions[it.table.name to it.id] ?: 0,
                    docs[it.table.name to it.id],
                )
            }

        override suspend fun settle(change: OutgoingChange, outcome: PushOutcome) {
            outbox.remove(change.entry)
            outcomes[change.entry.id] = outcome
            if (outcome is PushOutcome.Saved)
                versions[change.entry.table.name to change.entry.id] = outcome.version
        }

        override suspend fun applyRemote(changes: List<RemoteChange>) {
            changes.forEach {
                if (it.deleted) docs.remove(it.entity to it.id)
                else docs[it.entity to it.id] = it.doc
                versions[it.entity to it.id] = it.version
            }
        }

        override fun items() = docs.filterKeys { it.first == "items" }.values.map(Codec::item)

        override suspend fun updateAttachment(
            itemId: String,
            attachmentId: String,
            change: (Attachment) -> Attachment,
        ) {
            val item = Codec.item(docs.getValue("items" to itemId))
            write(
                Db.Table.items,
                itemId,
                Codec.encode(item.copy(attachments = item.attachments.map(change))),
            )
        }

        override suspend fun setAttachmentPath(itemId: String, attachmentId: String, path: String) {
            val item = Codec.item(docs.getValue("items" to itemId))
            docs["items" to itemId] =
                Codec.encode(
                    item.copy(attachments = item.attachments.map { it.copy(localPath = path) })
                )
        }
    }

    @Test
    fun `two devices sync records and media through the real server`() = runBlocking {
        val url = server ?: return@runBlocking
        val email = "e2e-${UUID.randomUUID()}@example.com"
        val registered =
            Http(url)
                .json(
                    "POST",
                    "/v1/auth/register",
                    Body.Json(
                        JSONObject()
                            .put("email", email)
                            .put("password", "correct horse battery")
                            .put("deviceName", "A")
                    ),
                )!!
        val account = Account(url, email, registered.getJSONObject("user").getString("id"))
        val sessionA = MemorySession(account, ApiClient.tokens(registered.getJSONObject("tokens")))
        val apiA = ApiClient(sessionA)
        val a = Device()
        val dir = Files.createTempDirectory("e2e").toFile()

        // Device A: a task and a photo item, encoded exactly as the app stores them.
        val photo =
            File(dir, "p.jpg").apply {
                writeBytes(ByteArray(MediaSync.CHUNK_BYTES + 4321) { it.toByte() })
            }
        a.write(
            Db.Table.tasks,
            "t1",
            Codec.encode(
                Task("t1", "Call the plumber", listId = "personal", createdAt = Instant.EPOCH)
            ),
        )
        val item =
            CaptureItem(
                "i1",
                CaptureKind.IMAGE,
                "Photo",
                capturedAt = Instant.parse("2026-10-30T08:00:00Z"),
                attachments = listOf(Attachment("att1", photo.path, "image/jpeg", photo.length())),
            )
        a.write(Db.Table.items, "i1", Codec.encode(item))

        // An expired access token is refreshed transparently on the first call.
        sessionA.saveTokens(Tokens("expired-token-value-xxxxxxxx", sessionA.refreshToken!!))
        val engineA = SyncEngine(apiA, a, a)
        assertEquals(2, engineA.run().pushed)
        assertNotEquals("expired-token-value-xxxxxxxx", sessionA.accessToken)

        MediaSync(apiA, a, dir).upload()
        engineA.run()
        val uploaded = Codec.item(a.docs.getValue("items" to "i1")).attachments.single()
        assertEquals(UploadState.UPLOADED, uploaded.uploadState)

        // Device B signs in to the same account and receives both records and the photo.
        val login =
            Http(url)
                .json(
                    "POST",
                    "/v1/auth/login",
                    Body.Json(
                        JSONObject()
                            .put("email", email)
                            .put("password", "correct horse battery")
                            .put("deviceName", "B")
                    ),
                )!!
        val apiB =
            ApiClient(MemorySession(account, ApiClient.tokens(login.getJSONObject("tokens"))))
        val b = Device()
        val engineB = SyncEngine(apiB, b, b)
        assertEquals(2, engineB.run().pulled)
        assertEquals("Call the plumber", Codec.task(b.docs.getValue("tasks" to "t1")).title)
        val dirB = Files.createTempDirectory("e2eB").toFile()
        MediaSync(apiB, b, dirB).download()
        val copy = File(Codec.item(b.docs.getValue("items" to "i1")).attachments.single().localPath)
        assertContentEquals(photo.readBytes(), copy.readBytes())

        // A deletes the task; B learns of it, and a stale edit from B is rejected (DEL01).
        a.write(Db.Table.tasks, "t1", null)
        engineA.run()
        b.outbox += OutboxEntry(Db.Table.tasks, "t1", Db.OP_UPSERT, UUID.randomUUID().toString())
        b.docs["tasks" to "t1"] = JSONObject().put("title", "edited offline")
        engineB.run()
        assertEquals(PushOutcome.Rejected("deleted"), b.outcomes["t1"])
        assertTrue(("tasks" to "t1") !in b.docs)
    }
}
