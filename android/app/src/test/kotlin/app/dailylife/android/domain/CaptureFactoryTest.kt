package app.dailylife.android.domain

import app.dailylife.core.model.Attachment
import app.dailylife.core.model.CaptureKind
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class CaptureFactoryTest {
    private val at = Instant.parse("2026-10-30T00:07:00Z")

    private fun create(
        kind: CaptureKind,
        text: String,
        attachments: List<Attachment> = emptyList(),
    ) =
        CaptureFactory.create("i1", CaptureInput(kind, text, attachments), at) {
            "Untitled ${it.name}"
        }

    @Test
    fun `a bare URL becomes a link titled by its host`() {
        val item = create(CaptureKind.TEXT, " https://www.example.com/winter-reading ")
        assertEquals(CaptureKind.LINK, item.kind)
        assertEquals("example.com", item.title)
        assertEquals("https://www.example.com/winter-reading", item.url)
        assertEquals("", item.body)
    }

    @Test
    fun `text keeps its first line as title and the rest as body`() {
        val item = create(CaptureKind.TEXT, "Call the plumber\nAsk about Thursday\nGet a quote")
        assertEquals("Call the plumber", item.title)
        assertEquals("Ask about Thursday\nGet a quote", item.body)
    }

    @Test
    fun `a long single line is truncated in the title and kept whole in the body`() {
        val line = "x".repeat(150)
        val item = create(CaptureKind.TEXT, line)
        assertEquals(120, item.title.length)
        assertEquals(line, item.body)
    }

    @Test
    fun `lists get one entry per line with stable ids`() {
        val item = create(CaptureKind.LIST, "- Milk\n- [ ] Eggs")
        assertEquals(listOf("Milk", "Eggs"), item.entries.map { it.text })
        assertEquals(listOf("i1:e0", "i1:e1"), item.entries.map { it.id })
        assertEquals("Milk…", item.title)
    }

    @Test
    fun `media without text uses the file name, else a generated title`() {
        val named = Attachment("a", "/m/1.pdf", "application/pdf", 10, name = "Invoice.pdf")
        assertEquals("Invoice.pdf", create(CaptureKind.FILE, "", listOf(named)).title)
        val photo = Attachment("b", "/m/2.jpg", "image/jpeg", 10)
        assertEquals("Untitled IMAGE", create(CaptureKind.IMAGE, "", listOf(photo)).title)
    }
}
