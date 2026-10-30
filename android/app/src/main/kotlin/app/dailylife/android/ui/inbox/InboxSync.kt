package app.dailylife.android.ui.inbox

import app.dailylife.android.data.Db
import app.dailylife.android.data.SyncOutbox
import app.dailylife.core.model.CaptureItem
import app.dailylife.core.model.UploadState

/** Where an inbox item is in reaching the server (SYN01). */
enum class SyncBadge {
    /** Not signed in: the item lives on this device only. */
    LOCAL_ONLY,
    /** Saved here; the item or one of its files is still on its way. */
    PENDING,
    SYNCED,
    /** The server rejected the change or a file: the user has to decide. */
    NEEDS_ATTENTION,
}

fun syncBadge(item: CaptureItem, outbox: SyncOutbox, signedIn: Boolean): SyncBadge =
    when {
        !signedIn -> SyncBadge.LOCAL_ONLY
        outbox.failure(Db.Table.items, item.id) != null ||
            item.attachments.any { it.uploadState == UploadState.FAILED } ->
            SyncBadge.NEEDS_ATTENTION
        outbox.isQueued(Db.Table.items, item.id) ||
            item.attachments.any { it.uploadState != UploadState.UPLOADED } -> SyncBadge.PENDING
        else -> SyncBadge.SYNCED
    }

/** The inbox footer: the most urgent state among [items] and how many are not synced yet. */
data class InboxSyncSummary(val badge: SyncBadge, val waiting: Int)

fun inboxSyncSummary(
    items: List<CaptureItem>,
    outbox: SyncOutbox,
    signedIn: Boolean,
): InboxSyncSummary {
    val badges = items.map { syncBadge(it, outbox, signedIn) }
    val badge =
        when {
            !signedIn -> SyncBadge.LOCAL_ONLY
            SyncBadge.NEEDS_ATTENTION in badges -> SyncBadge.NEEDS_ATTENTION
            SyncBadge.PENDING in badges -> SyncBadge.PENDING
            else -> SyncBadge.SYNCED
        }
    return InboxSyncSummary(
        badge,
        badges.count { it == SyncBadge.PENDING || it == SyncBadge.NEEDS_ATTENTION },
    )
}
