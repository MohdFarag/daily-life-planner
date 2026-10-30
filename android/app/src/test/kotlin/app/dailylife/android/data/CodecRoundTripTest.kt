package app.dailylife.android.data

import app.dailylife.core.model.*
import app.dailylife.core.prayer.EndRule
import app.dailylife.core.prayer.IqamaRule
import java.time.*
import kotlin.test.Test
import kotlin.test.assertEquals

class CodecRoundTripTest {
    private val now = Instant.parse("2026-10-28T09:41:00Z")
    private val d = LocalDate.of(2026, 10, 28)

    @Test fun items() {
        val item = CaptureItem(
            "i1", CaptureKind.LIST, "Groceries", "body", "https://x.y", SourceRef("keep", null, null, "me"), CaptureState.WAITING, now,
            listOf(Attachment("a", "/p/a.m4a", "audio/mp4", 10, 2000, UploadState.FAILED, "Voice memo.m4a"), Attachment("b", "/p/b.jpg", "image/jpeg", 5)),
            listOf(ListEntry("e0", "Milk", "task:t1"), ListEntry("e1", "Eggs")), "parent", listOf("t1"),
            WaitingFor("Client", null, d, d.plusDays(3)), d.plusDays(7), now, now, now.plusSeconds(60), CaptureState.INBOX,
        )
        assertEquals(item, Codec.item(Codec.encode(item)))
        val minimal = CaptureItem("i2", CaptureKind.TEXT, "Note", capturedAt = now)
        assertEquals(minimal, Codec.item(Codec.encode(minimal)))
    }

    @Test fun tasksSessionsRules() {
        val t = Task("t1", "Read", "notes", "work", setOf("a", "b"), true, TaskStatus.IN_PROGRESS, 45, d, d.minusDays(1), "i1", now, null)
        assertEquals(t, Codec.task(Codec.encode(t)))
        val rule = RelativeRule("r", PrayerName.ASR, PrayerPoint.END, Relation.ENDS_BEFORE, 15, 45, setOf(DayOfWeek.MONDAY), d, d.plusDays(30))
        assertEquals(rule, Codec.rule(Codec.encode(rule)))
        val s1 = TaskSession("r@$d", "t1", now, now.plusSeconds(2700), TimingRule.Relative(rule), d, true, SessionState.NEEDS_REVIEW, "b")
        assertEquals(s1, Codec.session(Codec.encode(s1)))
        val s2 = TaskSession("s2", "t1", now, now.plusSeconds(600))
        assertEquals(s2, Codec.session(Codec.encode(s2)))
    }

    @Test fun blocksAndPrayer() {
        defaultBlocks(setOf(DayOfWeek.FRIDAY)).forEach { assertEquals(it, Codec.block(Codec.encode(it))) }
        val p = CITIES.first().settings().copy(
            adjustmentsMin = mapOf(PrayerName.FAJR to -2),
            iqama = mapOf(PrayerName.DHUHR to IqamaRule.At(LocalTime.of(13, 15)), PrayerName.ASR to IqamaRule.AfterAdhan(10)),
            end = mapOf(PrayerName.DHUHR to EndRule.AfterIqama(20), PrayerName.ASR to EndRule.At(LocalTime.of(16, 0))),
            fridayExtraMin = 45,
        )
        assertEquals(p, Codec.prayer(Codec.encode(p)))
    }
}
