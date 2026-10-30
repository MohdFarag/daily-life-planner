package app.dailylife.android.data

import app.dailylife.core.focus.FocusSession
import app.dailylife.core.focus.TimeEntry
import app.dailylife.core.model.CalendarEvent
import app.dailylife.core.model.CaptureItem
import app.dailylife.core.model.RelativeRule
import app.dailylife.core.model.Task
import app.dailylife.core.model.TaskList
import app.dailylife.core.model.TaskSession
import app.dailylife.core.model.TimeBlock
import app.dailylife.core.prayer.PrayerSettings

/** User preferences stored on the device. */
data class Prefs(
    val lang: String = "en",
    val protectPrayers: Boolean = true,
    val prayerConfirmed: Boolean = false,
    val reminderMinutes: Int = 10,
    val windowDays: Int = 30,
)

/** A prayer-relative rule and the task whose sessions it generates. */
data class RuleBinding(val taskId: String, val rule: RelativeRule)

/** Everything stored on the device, as one immutable value the UI renders from. */
data class Snapshot(
    val items: List<CaptureItem> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val lists: List<TaskList> = emptyList(),
    val sessions: List<TaskSession> = emptyList(),
    val rules: List<RuleBinding> = emptyList(),
    val blocks: List<TimeBlock> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val timeLog: List<TimeEntry> = emptyList(),
    val prayer: PrayerSettings = defaultCity().settings(),
    val prefs: Prefs = Prefs(),
    val focus: FocusSession? = null,
    val loaded: Boolean = false,
) {
    val lang: String
        get() = prefs.lang
}
