package app.dailylife.android.sync

import app.dailylife.android.data.AttachmentStore
import app.dailylife.core.model.Attachment
import app.dailylife.core.model.UploadState
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Uploads captured files in resumable chunks and downloads files captured on other devices (CAP02).
 * The text of an item never waits for its media: items sync first, files follow. A failed upload
 * stays visible as FAILED and keeps the local copy.
 */
class MediaSync(
    private val api: MediaApi,
    private val store: AttachmentStore,
    private val mediaDir: File,
) {
    suspend fun upload() {
        for (item in store.items()) {
            for (attachment in item.attachments) {
                if (!needsUpload(attachment)) continue
                try {
                    upload(item.id, attachment)
                } catch (e: ApiException) {
                    // Rejected for good (too large, over quota): show it and keep the file.
                    if (e.status in PERMANENT_FAILURES) {
                        store.updateAttachment(item.id, attachment.id) {
                            it.copy(uploadState = UploadState.FAILED)
                        }
                    } else throw e
                }
            }
        }
    }

    suspend fun download() {
        for (item in store.items()) {
            for (a in item.attachments) {
                if (a.mediaId == null || a.uploadState != UploadState.UPLOADED) continue
                if (a.localPath.isNotEmpty() && File(a.localPath).exists()) continue
                val target = File(mediaDir, a.id + extension(a.mimeType))
                api.download(a.mediaId!!, target)
                store.setAttachmentPath(item.id, a.id, target.path)
            }
        }
    }

    private fun needsUpload(a: Attachment) =
        a.uploadState != UploadState.UPLOADED &&
            a.uploadState != UploadState.FAILED &&
            a.localPath.isNotEmpty() &&
            File(a.localPath).exists()

    private suspend fun upload(itemId: String, attachment: Attachment) {
        val file = File(attachment.localPath)
        val start = attachment.mediaId?.let { resume(it) } ?: begin(itemId, attachment, file)
        val mediaId = start.id
        var info: MediaInfo = start
        var restarts = 0
        RandomAccessFile(file, "r").use { raf ->
            while (!info.complete) {
                val offset = info.receivedBytes
                val size = minOf(CHUNK_BYTES.toLong(), file.length() - offset).toInt()
                if (size <= 0) throw IOException("Upload of ${file.name} stalled at $offset")
                val chunk = ByteArray(size)
                raf.seek(offset)
                raf.readFully(chunk)
                info =
                    try {
                        api.uploadChunk(mediaId, offset, chunk)
                    } catch (e: ApiException) {
                        // Out of step (409) or bytes did not match the checksum (422): the server
                        // says where to continue from; give up after a few attempts.
                        if (e.status !in RESUMABLE || ++restarts > MAX_RESTARTS) throw e
                        api.mediaInfo(mediaId)
                    }
            }
        }
        store.updateAttachment(itemId, attachment.id) {
            it.copy(mediaId = mediaId, uploadState = UploadState.UPLOADED)
        }
    }

    /**
     * Starts an upload and records its id first, so an interruption resumes instead of restarting.
     */
    private suspend fun begin(itemId: String, attachment: Attachment, file: File): MediaInfo {
        val info =
            api.createMedia(attachment.name, attachment.mimeType, file.length(), sha256(file))
        val mediaId = info.id
        store.updateAttachment(itemId, attachment.id) {
            it.copy(mediaId = mediaId, uploadState = UploadState.UPLOADING)
        }
        return info
    }

    /** The upload's state on the server, or null if it no longer exists there. */
    private suspend fun resume(mediaId: String): MediaInfo? =
        try {
            api.mediaInfo(mediaId)
        } catch (e: ApiException) {
            if (e.status == 404) null else throw e
        }

    companion object {
        const val CHUNK_BYTES = 1 shl 20

        private val RESUMABLE = setOf(409, 422)
        private const val MAX_RESTARTS = 3

        /** Too large, over quota, unsupported: retrying will not help. */
        private val PERMANENT_FAILURES = setOf(400, 413, 415)

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun extension(mime: String): String =
            when (mime.substringBefore('/')) {
                "image" ->
                    ".jpg".takeIf { mime == "image/jpeg" } ?: ("." + mime.substringAfter('/'))
                "audio" -> ".m4a".takeIf { mime == "audio/mp4" } ?: ("." + mime.substringAfter('/'))
                "video" -> ".mp4".takeIf { mime == "video/mp4" } ?: ("." + mime.substringAfter('/'))
                else -> ""
            }
    }
}
