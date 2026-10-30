package app.dailylife.android.domain

import app.dailylife.core.model.Attachment
import app.dailylife.core.model.CaptureItem
import app.dailylife.core.model.CaptureKind
import app.dailylife.core.model.CaptureState
import app.dailylife.core.model.SourceRef
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShareInterpreterTest {
    private fun share(
        text: String? = null,
        subject: String? = null,
        attachments: List<Attachment> = emptyList(),
        from: String? = "com.google.android.keep",
    ) = ShareInterpreter.toCapture(SharedContent(text, subject, attachments, from))

    @Test
    fun `known apps map to source services`() {
        assertEquals("keep", share("a").source.service)
        assertEquals("gmail", share("a", from = "com.google.android.gm").source.service)
        assertEquals("share:org.example", share("a", from = "org.example").source.service)
        assertEquals("share", share("a", from = null).source.service)
    }

    @Test
    fun `the subject is prepended unless the text already starts with it`() {
        assertEquals("Groceries\nMilk", share("Milk", subject = "Groceries").text)
        assertEquals("Groceries list", share("Groceries list", subject = "Groceries").text)
    }

    @Test
    fun `Keep checklists become lists`() {
        assertEquals(CaptureKind.LIST, share("☐ Milk\n☐ Eggs").kind)
        assertEquals(CaptureKind.TEXT, share("Just one thought").kind)
    }

    @Test
    fun `the kind follows the attachments`() {
        val image = Attachment("a", "/m/a.jpg", "image/jpeg", 1)
        val pdf = Attachment("b", "/m/b.pdf", "application/pdf", 1)
        assertEquals(CaptureKind.IMAGE, share(attachments = listOf(image)).kind)
        assertEquals(CaptureKind.FILE, share(attachments = listOf(image, pdf)).kind)
    }

    @Test
    fun `similar earlier shares match by source and content, not by title alone`() {
        val earlier =
            CaptureItem(
                "old",
                CaptureKind.TEXT,
                "Milk",
                "Eggs",
                source = SourceRef("keep"),
                capturedAt = Instant.EPOCH,
            )
        val input = share("Milk\nEggs")
        assertEquals("old", ShareInterpreter.findSimilar(listOf(earlier), input)?.id)
        assertNull(
            ShareInterpreter.findSimilar(listOf(earlier.copy(state = CaptureState.TRASHED)), input)
        )
        assertNull(
            ShareInterpreter.findSimilar(listOf(earlier.copy(source = SourceRef("gmail"))), input)
        )
        assertNull(ShareInterpreter.findSimilar(listOf(earlier), share("Milk")))
    }
}
