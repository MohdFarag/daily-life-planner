package app.dailylife.android.ui.capture

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import app.dailylife.android.media.AudioRecorder
import app.dailylife.android.media.MediaFiles
import app.dailylife.core.model.Attachment
import java.io.File
import java.util.UUID

/**
 * Everything the capture sheet is editing. Every change is saved as a draft, so an unsaved capture
 * survives the app being closed (CAP03).
 */
class CaptureSheetState(private val context: Context, private val mediaDir: File) {
    private val recorder = AudioRecorder(context, mediaDir)
    private var pendingCameraFile: File? = null

    var mode by mutableStateOf(CaptureMode.TEXT)
    var text by mutableStateOf("")
        private set

    val attachments = mutableStateListOf<Attachment>()
    var recording by mutableStateOf(false)
        private set

    var recordedSeconds by mutableLongStateOf(0L)
        private set

    var micUnavailable by mutableStateOf(false)
        private set

    val canSave: Boolean
        get() = text.isNotBlank() || attachments.isNotEmpty()

    init {
        val (draftText, draftAttachments) = CaptureDraft.load(context)
        text = draftText
        attachments += draftAttachments
    }

    fun updateText(value: String) {
        text = value
        saveDraft()
    }

    fun startRecording() {
        runCatching { recorder.start() }
            .onSuccess {
                recording = true
                recordedSeconds = 0
            }
            .onFailure { micUnavailable = true }
    }

    /** Stops a running recording and keeps it as an attachment. */
    fun stopRecording() {
        if (!recording) return
        recorder.stop()?.let { attachments += MediaFiles.fromFile(it, "audio/mp4") }
        recording = false
        saveDraft()
    }

    fun refreshRecordingTime() {
        recordedSeconds = (System.currentTimeMillis() - recorder.startedAt) / 1000
    }

    fun onMicPermissionDenied() {
        micUnavailable = true
    }

    /** A new file for the camera to write into, shared with the camera app through FileProvider. */
    fun newCameraFile(extension: String): Uri {
        val file = File(mediaDir, "${UUID.randomUUID()}.$extension")
        pendingCameraFile = file
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    fun onCameraResult(saved: Boolean, mimeType: String) {
        pendingCameraFile?.let {
            if (saved && it.length() > 0) attachments += MediaFiles.fromFile(it, mimeType)
            else it.delete()
        }
        pendingCameraFile = null
        saveDraft()
    }

    fun onFilesPicked(uris: List<Uri>) {
        attachments += uris.mapNotNull { MediaFiles.copyIn(context, it, mediaDir) }
        saveDraft()
    }

    fun saveDraft() = CaptureDraft.save(context, text, attachments)

    fun clearDraft() = CaptureDraft.clear(context)
}

@Composable
fun rememberCaptureSheetState(mediaDir: File): CaptureSheetState {
    val context = LocalContext.current
    return remember { CaptureSheetState(context, mediaDir) }
}
