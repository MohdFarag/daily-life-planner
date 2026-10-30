package app.dailylife.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Kinds of material that can be captured into the inbox (CAP01). */
enum class CaptureKind {
    TEXT,
    LIST,
    LINK,
    AUDIO,
    IMAGE,
    VIDEO,
    FILE,
}

/**
 * Lifecycle of a captured item. PROCESSED items keep their history and links; TRASHED items are
 * restorable until [CaptureItem.purgeAt] (DEL01).
 */
enum class CaptureState {
    INBOX,
    DOING_NOW,
    PROCESSED,
    REFERENCE,
    SOMEDAY,
    WAITING,
    TRASHED,
}

/** Where a capture came from. Keep shares are copies; no stable note URL is invented (CAP05). */
data class SourceRef(
    val service: String, // "app", "keep", "share:<package>", "gmail", ...
    val externalId: String? = null,
    val url: String? = null,
    val account: String? = null,
)

enum class UploadState {
    LOCAL_ONLY,
    UPLOADING,
    UPLOADED,
    FAILED,
}

/** Binary media stays in app storage until upload completes (CAP01/CAP02). */
data class Attachment(
    val id: String,
    val localPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long? = null,
    val uploadState: UploadState = UploadState.LOCAL_ONLY,
    /**
     * Original file name when known (picked or shared files); null for camera and microphone
     * captures.
     */
    val name: String? = null,
    /** The server's id for the uploaded copy; set once an upload has started (CAP02). */
    val mediaId: String? = null,
)

/** One entry of a captured list; each has its own id so it can be processed alone (CAP04). */
data class ListEntry(val id: String, val text: String, val resolvedAs: String? = null)

data class WaitingFor(
    val who: String,
    val expected: String? = null,
    val requestedOn: LocalDate,
    val followUpOn: LocalDate?,
)

data class CaptureItem(
    val id: String,
    val kind: CaptureKind,
    val title: String,
    val body: String = "",
    val url: String? = null,
    val source: SourceRef = SourceRef("app"),
    val state: CaptureState = CaptureState.INBOX,
    val capturedAt: Instant,
    val attachments: List<Attachment> = emptyList(),
    val entries: List<ListEntry> = emptyList(),
    val parentId: String? = null, // set on items created by Split (TRI07)
    val derivedTaskIds: List<String> = emptyList(),
    val waiting: WaitingFor? = null,
    val reviewOn: LocalDate? = null, // Someday review date (TRI05)
    val completedAt: Instant? = null, // Do Now completion record (TRI02)
    val trashedAt: Instant? = null,
    val purgeAt: Instant? = null,
    val stateBeforeTrash: CaptureState? = null,
)

enum class TaskStatus {
    NEEDS_ACTION,
    IN_PROGRESS,
    COMPLETED,
}

data class Task(
    val id: String,
    val title: String,
    val notes: String = "",
    val listId: String,
    val tags: Set<String> = emptySet(),
    val important: Boolean = false,
    val status: TaskStatus = TaskStatus.NEEDS_ACTION,
    val estimateMin: Int? = null,
    val deadline: LocalDate? = null, // real deadline (NOT04)
    val targetDate: LocalDate? = null, // early "my completion target"
    val sourceItemId: String? = null,
    val createdAt: Instant,
    val completedAt: Instant? = null,
)

data class TaskList(val id: String, val name: String)

/** How a session's time is determined (REL01). */
sealed interface TimingRule {
    data object Fixed : TimingRule

    data class Relative(val rule: RelativeRule) : TimingRule
}

enum class PrayerName {
    FAJR,
    DHUHR,
    ASR,
    MAGHRIB,
    ISHA,
}

enum class PrayerPoint {
    ADHAN,
    IQAMA,
    END,
}

enum class Relation {
    STARTS_AFTER,
    STARTS_BEFORE,
    ENDS_BEFORE,
}

data class RelativeRule(
    val id: String,
    val prayer: PrayerName,
    val anchor: PrayerPoint,
    val relation: Relation,
    val offsetMin: Int,
    val durationMin: Int,
    val days: Set<java.time.DayOfWeek>? = null, // null = every day
    val from: LocalDate,
    val until: LocalDate? = null,
)

enum class SessionState {
    PLANNED,
    NEEDS_REVIEW,
    DONE,
    CANCELED,
}

/** A planned work period for a task; a task may have several (SES01, AC07). */
data class TaskSession(
    val id: String,
    val taskId: String,
    val start: Instant,
    val end: Instant,
    val timing: TimingRule = TimingRule.Fixed,
    val occurrenceDate: LocalDate? = null, // for rule-generated sessions
    val pinned: Boolean = false, // manual exception: fixed time for this occurrence (REL03)
    val state: SessionState = SessionState.PLANNED,
    val blockId: String? = null,
)

enum class BlockPolicy {
    GUIDING,
    PROTECTED,
}

/** A recurring window allocated to an activity category (BLK01–04). */
data class TimeBlock(
    val id: String,
    val name: String,
    val start: LocalTime,
    val end: LocalTime,
    val days: Set<java.time.DayOfWeek>,
    val policy: BlockPolicy,
    val allowedListIds: Set<String> = emptySet(),
    val color: Long = 0xFFE6F0E7,
)

/** A standalone appointment, possibly mirrored from Google Calendar. */
data class CalendarEvent(
    val id: String,
    val title: String,
    val start: Instant,
    val end: Instant,
    val calendarId: String,
    val readOnly: Boolean = false,
)
