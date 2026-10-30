package app.dailylife.android.media

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import app.dailylife.core.model.Attachment
import java.io.File
import java.util.UUID

/**
 * Files in app storage: copying shared or picked content in while access is still valid (CAP05).
 */
object MediaFiles {
    /** Folder for captured media inside app-private storage. */
    fun dir(context: Context): File = File(context.filesDir, "media").apply { mkdirs() }

    fun displayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
        val c =
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?: return null
        try {
            if (c.moveToFirst()) c.getString(0) else null
        } finally {
            c.close()
        }
    }
        .getOrNull()

    fun copyIn(context: Context, uri: Uri, dir: File, fallbackMime: String? = null): Attachment? {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: fallbackMime ?: "application/octet-stream"
        val ext =
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                ?: displayName(resolver, uri)?.substringAfterLast('.', "")
                ?: "bin"
        val id = UUID.randomUUID().toString()
        val target = File(dir, "$id.$ext")
        val ok = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } != null
        }
            .getOrDefault(false)
        if (!ok) {
            target.delete()
            return null
        }
        return Attachment(
            id,
            target.absolutePath,
            mime,
            target.length(),
            durationOf(target, mime),
            name = displayName(resolver, uri),
        )
    }

    fun fromFile(file: File, mime: String) =
        Attachment(
            UUID.randomUUID().toString(),
            file.absolutePath,
            mime,
            file.length(),
            durationOf(file, mime),
        )

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
