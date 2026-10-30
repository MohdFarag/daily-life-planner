package app.dailylife.android.ui

import app.dailylife.android.ui.plan.TimelineScale
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineScaleTest {
    private val scale = TimelineScale(LocalDate.of(2026, 10, 30), ZoneId.of("Africa/Cairo"))

    @Test
    fun `a drop height maps back to the time drawn there, to the quarter hour (PLN02)`() {
        for (time in listOf("06:00", "09:15", "13:30", "21:45")) {
            assertEquals(LocalTime.parse(time), scale.timeAt(scale.y(LocalTime.parse(time))))
        }
        assertEquals(LocalTime.of(10, 15), scale.timeAt(scale.y(LocalTime.of(10, 11))))
    }

    @Test
    fun `drops above or below the timeline clamp to its ends`() {
        assertEquals(LocalTime.of(5, 0), scale.timeAt(-50f))
        assertEquals(LocalTime.of(22, 45), scale.timeAt(scale.totalHeight + 100f))
    }
}
