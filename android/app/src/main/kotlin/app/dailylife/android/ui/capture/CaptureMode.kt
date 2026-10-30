package app.dailylife.android.ui.capture

import app.dailylife.core.model.Attachment
import app.dailylife.core.model.CaptureKind

/** The capture types offered in the sheet (CAP01). [labelKey] is a string key. */
enum class CaptureMode(val labelKey: String) {
    TEXT("text"),
    LIST("list"),
    VOICE("voice"),
    PHOTO("photo"),
    VIDEO("video"),
    FILE("file");

    val isWriting: Boolean
        get() = this == TEXT || this == LIST
}

/** What a capture is: decided by its attachments if any, else by the chosen mode. */
fun captureKind(mode: CaptureMode, attachments: List<Attachment>): CaptureKind =
    when {
        attachments.isEmpty() ->
            if (mode == CaptureMode.LIST) CaptureKind.LIST else CaptureKind.TEXT
        attachments.all { it.mimeType.startsWith("audio") } -> CaptureKind.AUDIO
        attachments.all { it.mimeType.startsWith("image") } -> CaptureKind.IMAGE
        attachments.all { it.mimeType.startsWith("video") } -> CaptureKind.VIDEO
        else -> CaptureKind.FILE
    }
