package app.dailylife.android.data

import app.dailylife.core.model.Attachment
import app.dailylife.core.model.CaptureItem
import org.json.JSONObject

/**
 * A local change ready to push: its outbox entry, the server version it was based on, and the
 * document to send (null for a deletion).
 */
data class OutgoingChange(val entry: OutboxEntry, val baseVersion: Long, val doc: JSONObject?)

/** A record from the server's change log. [entity] matches a [Db.Table] name. */
data class RemoteChange(
    val entity: String,
    val id: String,
    val version: Long,
    val deleted: Boolean,
    val doc: JSONObject,
)

/** The server's verdict on one pushed change. */
sealed interface PushOutcome {
    /** Saved; [serverValues] are fields where the server kept its own, newer value. */
    data class Saved(val version: Long, val serverValues: Map<String, Any?> = emptyMap()) :
        PushOutcome

    data class Rejected(val reason: String) : PushOutcome
}

/** The device side of record sync (implemented by [Repository]). */
interface SyncStore {
    suspend fun outgoing(limit: Int): List<OutgoingChange>

    suspend fun settle(change: OutgoingChange, outcome: PushOutcome)

    suspend fun applyRemote(changes: List<RemoteChange>)
}

/** The device side of media sync (implemented by [Repository]). */
interface AttachmentStore {
    fun items(): List<CaptureItem>

    suspend fun updateAttachment(
        itemId: String,
        attachmentId: String,
        change: (Attachment) -> Attachment,
    )

    suspend fun setAttachmentPath(itemId: String, attachmentId: String, path: String)
}
