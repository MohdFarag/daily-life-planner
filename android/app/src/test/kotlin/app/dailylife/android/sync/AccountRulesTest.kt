package app.dailylife.android.sync

import app.dailylife.android.data.Db
import app.dailylife.android.data.OutboxEntry
import app.dailylife.android.data.SyncLedger
import app.dailylife.android.data.SyncOutbox
import app.dailylife.android.ui.inbox.SyncBadge
import app.dailylife.android.ui.inbox.inboxSyncSummary
import app.dailylife.android.ui.inbox.syncBadge
import app.dailylife.android.viewmodel.AccountError
import app.dailylife.android.viewmodel.ServerAction
import app.dailylife.core.model.Attachment
import app.dailylife.core.model.CaptureItem
import app.dailylife.core.model.CaptureKind
import app.dailylife.core.model.UploadState
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountRulesTest {
    @Test
    fun `server addresses default to https and allow http only for local development`() {
        val n = AccountService::normalizeServerUrl
        assertEquals("https://api.example.com", n(" api.example.com/ "))
        assertEquals("https://api.example.com:8443/base", n("https://api.example.com:8443/base"))
        assertEquals("http://10.0.2.2:3000", n("http://10.0.2.2:3000"))
        assertNull(n("http://api.example.com"))
        assertNull(n("ftp://api.example.com"))
        assertNull(n(""))
    }

    @Test
    fun `server error codes map to translated messages`() {
        fun err(code: String) = ServerAction.errorFor(ApiException(400, code, "detail"))
        assertEquals(AccountError("wrongCredentials"), err("unauthorized"))
        assertEquals(AccountError("emailTaken"), err("email_taken"))
        assertEquals(AccountError("serverError", "detail"), err("invalid_request"))
    }

    private fun item(id: String, upload: UploadState? = null) =
        CaptureItem(
            id,
            CaptureKind.TEXT,
            id,
            capturedAt = Instant.EPOCH,
            attachments =
                listOfNotNull(
                    upload?.let { Attachment("a", "/f", "image/jpeg", 1, uploadState = it) }
                ),
        )

    @Test
    fun `items show their sync state`() {
        val outbox =
            SyncOutbox(
                queued =
                    setOf(
                        SyncLedger.key(Db.Table.items, "queued"),
                        SyncLedger.key(Db.Table.items, "bad"),
                    ),
                failures = listOf(OutboxEntry(Db.Table.items, "bad", Db.OP_UPSERT, "m", "invalid")),
            )
        assertEquals(SyncBadge.LOCAL_ONLY, syncBadge(item("queued"), outbox, signedIn = false))
        assertEquals(SyncBadge.PENDING, syncBadge(item("queued"), outbox, true))
        assertEquals(
            SyncBadge.PENDING,
            syncBadge(item("photo", UploadState.UPLOADING), outbox, true),
        )
        assertEquals(SyncBadge.NEEDS_ATTENTION, syncBadge(item("bad"), outbox, true))
        assertEquals(
            SyncBadge.NEEDS_ATTENTION,
            syncBadge(item("big", UploadState.FAILED), outbox, true),
        )
        assertEquals(SyncBadge.SYNCED, syncBadge(item("done", UploadState.UPLOADED), outbox, true))

        val summary =
            inboxSyncSummary(listOf(item("queued"), item("bad"), item("done")), outbox, true)
        assertEquals(SyncBadge.NEEDS_ATTENTION, summary.badge)
        assertEquals(2, summary.waiting)
    }
}
