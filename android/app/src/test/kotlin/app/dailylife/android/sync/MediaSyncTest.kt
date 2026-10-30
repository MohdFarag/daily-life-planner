package app.dailylife.android.sync

import app.dailylife.android.data.AttachmentStore
import app.dailylife.core.model.Attachment
import app.dailylife.core.model.CaptureItem
import app.dailylife.core.model.CaptureKind
import app.dailylife.core.model.UploadState
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class MediaSyncTest {
    private val dir: File = Files.createTempDirectory("media").toFile()
    private val bytes = ByteArray(MediaSync.CHUNK_BYTES * 2 + 100) { (it % 251).toByte() }
    private val file = File(dir, "photo.jpg").apply { writeBytes(bytes) }

    private class Store(var item: CaptureItem) : AttachmentStore {
        val history = mutableListOf<Attachment>()

        override fun items() = listOf(item)

        override suspend fun updateAttachment(
            itemId: String,
            attachmentId: String,
            change: (Attachment) -> Attachment,
        ) {
            item = item.copy(attachments = item.attachments.map { change(it) })
            history += item.attachments.single()
        }

        override suspend fun setAttachmentPath(itemId: String, attachmentId: String, path: String) {
            item = item.copy(attachments = item.attachments.map { it.copy(localPath = path) })
        }
    }

    /** A server that stores chunks; can fail the first chunk once with [failFirstWith]. */
    private class Server(var failFirstWith: Int? = null) : MediaApi {
        val received = java.io.ByteArrayOutputStream()
        var created = 0
        var complete = false
        var total = 0L
        var reject: Int? = null

        override suspend fun createMedia(
            name: String?,
            mimeType: String,
            sizeBytes: Long,
            sha256: String,
        ): MediaInfo {
            reject?.let { throw ApiException(it, "too_large", "no") }
            created++
            total = sizeBytes
            return info()
        }

        override suspend fun mediaInfo(id: String) = info()

        override suspend fun uploadChunk(id: String, offset: Long, bytes: ByteArray): MediaInfo {
            failFirstWith?.let {
                failFirstWith = null
                throw ApiException(it, "offset_mismatch", "again")
            }
            check(offset == received.size().toLong()) { "offset $offset" }
            received.write(bytes)
            complete = received.size().toLong() == total
            return info()
        }

        override suspend fun download(id: String, target: File) {
            target.writeBytes(byteArrayOf(1, 2, 3))
        }

        private fun info() = MediaInfo("m1", received.size().toLong(), complete)
    }

    private fun item(attachment: Attachment) =
        CaptureItem(
            "i1",
            CaptureKind.IMAGE,
            "Photo",
            capturedAt = Instant.EPOCH,
            attachments = listOf(attachment),
        )

    private fun local() = Attachment("a1", file.path, "image/jpeg", file.length())

    @Test
    fun `uploads in chunks, recording the upload id before sending bytes`() = runBlocking {
        val store = Store(item(local()))
        val server = Server()
        MediaSync(server, store, dir).upload()

        assertContentEquals(bytes, server.received.toByteArray())
        assertEquals(UploadState.UPLOADING, store.history.first().uploadState)
        assertEquals("m1", store.history.first().mediaId)
        assertEquals(UploadState.UPLOADED, store.item.attachments.single().uploadState)
    }

    @Test
    fun `resumes an interrupted upload from the server's offset`() = runBlocking {
        val server =
            Server().apply {
                total = bytes.size.toLong()
                received.write(bytes, 0, MediaSync.CHUNK_BYTES)
            }
        val store = Store(item(local().copy(mediaId = "m1", uploadState = UploadState.UPLOADING)))
        MediaSync(server, store, dir).upload()

        assertEquals(0, server.created)
        assertContentEquals(bytes, server.received.toByteArray())
        assertEquals(UploadState.UPLOADED, store.item.attachments.single().uploadState)
    }

    @Test
    fun `an out-of-step chunk is retried from where the server is`() = runBlocking {
        val store = Store(item(local()))
        val server = Server(failFirstWith = 409)
        MediaSync(server, store, dir).upload()
        assertContentEquals(bytes, server.received.toByteArray())
    }

    @Test
    fun `a file the server refuses is marked failed and kept`() = runBlocking {
        val store = Store(item(local()))
        MediaSync(Server().apply { reject = 413 }, store, dir).upload()
        assertEquals(UploadState.FAILED, store.item.attachments.single().uploadState)
        assertTrue(file.exists())
    }

    @Test
    fun `downloads files captured on another device`() = runBlocking {
        val remote =
            Attachment(
                "a2",
                "",
                "image/jpeg",
                3,
                uploadState = UploadState.UPLOADED,
                mediaId = "m9",
            )
        val store = Store(item(remote))
        MediaSync(Server(), store, dir).download()
        val path = store.item.attachments.single().localPath
        assertEquals("a2.jpg", File(path).name)
        assertContentEquals(byteArrayOf(1, 2, 3), File(path).readBytes())
    }

    @Test
    fun `sha256 matches a known digest`() {
        val f = File(dir, "abc.txt").apply { writeText("abc") }
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            MediaSync.sha256(f),
        )
    }
}
