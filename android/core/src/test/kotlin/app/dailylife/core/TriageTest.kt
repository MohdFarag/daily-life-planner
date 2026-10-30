package app.dailylife.core

import app.dailylife.core.inbox.Triage
import app.dailylife.core.model.CaptureItem
import app.dailylife.core.model.CaptureKind
import app.dailylife.core.model.CaptureState
import app.dailylife.core.model.SourceRef
import app.dailylife.core.model.WaitingFor
import java.time.Duration
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TriageTest {
    private val now = at(LocalDate.of(2026, 10, 28), "09:41")

    private fun item(id: String = "i1", body: String = "", kind: CaptureKind = CaptureKind.TEXT) =
        CaptureItem(id, kind, "Review project brief", body, capturedAt = now)

    @Test
    fun `AC03 mixed list becomes two tasks and a reference, linked to the source`() {
        val entries =
            Triage.textToEntries(
                "- Call the supplier\n- [ ] Read an article\n3. Save the order number"
            ) {
                "e$it"
            }
        assertEquals(
            listOf("Call the supplier", "Read an article", "Save the order number"),
            entries.map { it.text },
        )
        val list = item(kind = CaptureKind.LIST).copy(entries = entries)

        val partial = Triage.resolveEntries(list, listOf("e0"), emptyList(), "work", now)
        assertEquals(
            CaptureState.INBOX,
            partial.item.state,
            "unresolved entries keep the item in the inbox",
        )

        val done = Triage.resolveEntries(partial.item, listOf("e1"), listOf("e2"), "work", now)
        assertEquals(CaptureState.PROCESSED, done.item.state)
        assertEquals(listOf("i1:task:0", "i1:task:1"), done.item.derivedTaskIds)
        assertEquals(CaptureState.REFERENCE, done.newItems.single().state)
        assertTrue(
            done.newTasks.all { it.sourceItemId == "i1" } &&
                done.newItems.all { it.parentId == "i1" }
        )
    }

    @Test
    fun `TRI03 entries can become ordered subtasks of one task named after the item`() {
        val entries = Triage.textToEntries("- Book venue\n- Send invites") { "e$it" }
        val list = item(kind = CaptureKind.LIST).copy(entries = entries)

        val out =
            Triage.resolveEntries(
                list,
                listOf("e0", "e1"),
                emptyList(),
                "work",
                now,
                asSubtasks = true,
            )

        val (parent, first, second) = out.newTasks
        assertEquals(list.title, parent.title)
        assertEquals(null, parent.parentId)
        assertEquals(listOf(parent.id, parent.id), listOf(first.parentId, second.parentId))
        assertEquals(listOf("Book venue", "Send invites"), listOf(first.title, second.title))
        assertTrue(first.position!! < second.position!!)
        assertEquals(out.newTasks.map { it.id }, out.item.derivedTaskIds)
        assertEquals(out.newTasks.map { it.id }, out.undo.createdTaskIds)
        assertEquals(CaptureState.PROCESSED, out.item.state)
    }

    @Test
    fun `AC04 Do Now never completes on its own`() {
        val started = Triage.startDoNow(item()).item
        assertEquals(CaptureState.DOING_NOW, started.state)
        assertEquals(1, Triage.queue(listOf(started)).size, "still awaiting a decision")
        val confirmed = Triage.confirmDoNow(started, now).item
        assertEquals(CaptureState.PROCESSED, confirmed.state)
        assertNotNull(confirmed.completedAt)
    }

    @Test
    fun `converting twice derives the same task id so retries do not duplicate`() {
        val a = Triage.toTasks(item(), listOf("Review"), "work", now)
        val b = Triage.toTasks(item(), listOf("Review"), "work", now)
        assertEquals(a.newTasks.map { it.id }, b.newTasks.map { it.id })
    }

    @Test
    fun `undo carries the previous item and created ids`() {
        val o = Triage.toTasks(item(), listOf("A", "B"), "work", now)
        assertEquals(CaptureState.INBOX, o.undo.previous.state)
        assertEquals(o.newTasks.map { it.id }, o.undo.createdTaskIds)
    }

    @Test
    fun `AC05 waiting follow-ups appear when due and nothing is sent`() {
        val w =
            Triage.toWaiting(
                    item(),
                    WaitingFor(
                        "Client",
                        "Approval",
                        LocalDate.of(2026, 10, 28),
                        LocalDate.of(2026, 10, 30),
                    ),
                )
                .item
        assertTrue(Triage.followUpsDue(listOf(w), LocalDate.of(2026, 10, 29)).isEmpty())
        assertEquals(1, Triage.followUpsDue(listOf(w), LocalDate.of(2026, 10, 30)).size)
    }

    @Test
    fun `split keeps unselected entries unprocessed`() {
        val list =
            item(kind = CaptureKind.LIST).copy(entries = Triage.textToEntries("a\nb") { "e$it" })
        val o = Triage.split(list, listOf("a"), now, listOf("e0"))
        assertEquals(CaptureState.INBOX, o.item.state)
        assertEquals("i1", o.newItems.single().parentId)
    }

    @Test
    fun `DEL01 trash keeps 30 days, restores, then purges`() {
        val t = Triage.trash(item().copy(state = CaptureState.REFERENCE), now).item
        assertEquals(now.plus(Duration.ofDays(30)), t.purgeAt)
        assertEquals(CaptureState.REFERENCE, Triage.restore(t).state)
        assertTrue(Triage.purgeDue(listOf(t), now.plus(Duration.ofDays(29))).isEmpty())
        assertEquals(1, Triage.purgeDue(listOf(t), now.plus(Duration.ofDays(30))).size)
    }

    @Test
    fun `shares are matched by source id, never by title`() {
        val existing = item().copy(source = SourceRef("gmail", "msg-1", account = "me"))
        assertNotNull(
            Triage.findDuplicate(listOf(existing), SourceRef("gmail", "msg-1", account = "me"))
        )
        assertNull(
            Triage.findDuplicate(listOf(existing), SourceRef("gmail", "msg-2", account = "me"))
        )
        assertNull(
            Triage.findDuplicate(listOf(existing), SourceRef("keep")),
            "Keep shares have no stable id",
        )
    }
}
