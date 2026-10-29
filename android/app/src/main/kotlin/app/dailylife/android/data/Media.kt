package app.dailylife.android.data

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import app.dailylife.core.model.Attachment
import java.io.File
import java.util.UUID

/** Copies shared or picked content into app storage while access is still valid (CAP05). */
object Media {

    fun displayName(resolver: ContentResolver, uri: Uri): String? =
        runCatching {
            val c = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null) ?: return null
            try {
                if (c.moveToFirst()) c.getString(0) else null
            } finally {
                c.close()
            }
        }.getOrNull()

    fun copyIn(context: Context, uri: Uri, dir: File, fallbackMime: String? = null): Attachment? {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: fallbackMime ?: "application/octet-stream"
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: displayName(resolver, uri)?.substringAfterLast('.', "") ?: "bin"
        val id = UUID.randomUUID().toString()
        val target = File(dir, "$id.$ext")
        val ok = runCatching {
            resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } != null
        }.getOrDefault(false)
        if (!ok) { target.delete(); return null }
        return Attachment(id, target.absolutePath, mime, target.length(), durationOf(target, mime), name = displayName(resolver, uri))
    }

    fun fromFile(file: File, mime: String) = Attachment(UUID.randomUUID().toString(), file.absolutePath, mime, file.length(), durationOf(file, mime))

    private fun durationOf(file: File, mime: String): Long? {
        if (!mime.startsWith("audio") && !mime.startsWith("video")) return null
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (_: Exception) {
            null
        } finally {
            r.release()
        }
    }
}

/** Records audio into app storage. Permission is requested by the caller at the moment of use. */
class AudioRecorder(private val context: Context, private val dir: File) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    var startedAt: Long = 0
        private set

    fun start() {
        val f = File(dir, "${UUID.randomUUID()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(64_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(f.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        file = f
        startedAt = System.currentTimeMillis()
    }

    fun stop(): File? {
        val r = recorder ?: return null
        recorder = null
        return runCatching { r.stop(); r.release(); file }.getOrElse { r.release(); file?.delete(); null }
    }

    val isRecording get() = recorder != null
}
