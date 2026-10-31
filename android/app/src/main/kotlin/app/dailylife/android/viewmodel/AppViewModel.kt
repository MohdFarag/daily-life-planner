package app.dailylife.android.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.dailylife.android.PlannerApp
import app.dailylife.android.data.Snapshot
import app.dailylife.android.domain.DayPlanner
import app.dailylife.android.navigation.Navigator
import app.dailylife.android.work.Reminders
import app.dailylife.core.inbox.Triage
import app.dailylife.core.model.TaskSession
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/** Which task the scheduling sheet plans, and the session being edited if any. */
data class ScheduleTarget(
    val taskId: String,
    val editing: TaskSession? = null,
    /** A start proposed by dropping the task on the timeline (PLN02). */
    val proposed: LocalDateTime? = null,
)

/**
 * Screen-level state shared by the whole app, and the feature controllers screens call into.
 * Business rules live in core and `domain/`; this class only wires them to the UI.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as PlannerApp).container
    private val clock: Clock = Clock.systemDefaultZone()

    val state: StateFlow<Snapshot> = container.repository.state
    val navigator = Navigator()
    val messages = Messages()
    val dayPlanner: DayPlanner = container.dayPlanner
    val mediaDir: File = container.mediaDir

    // Sheets shown over the current screen.
    var captureOpen by mutableStateOf(false)
    var scheduling by mutableStateOf<ScheduleTarget?>(null)
    var editingEvent by mutableStateOf<EventTarget?>(null)

    /**
     * Controller work is fire-and-forget; a failure there (storage full, unreadable data) is logged
     * and reported instead of crashing the app.
     */
    private val failures = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "Action failed", e)
        messages.show("actionFailed")
    }
    private val ctx =
        ControllerScope(viewModelScope + failures, container, navigator, messages, clock)
    val capture = CaptureController(ctx)
    val inbox = InboxController(ctx)
    val focus = FocusController(ctx)
    val tasks = TaskController(ctx, focus)
    val settings = SettingsController(ctx)
    val account = AccountController(ctx)
    val google = GoogleController(ctx)
    val events = EventController(ctx)

    init {
        ctx.scope.launch {
            container.repository.load()
            container.ruleScheduler.refreshAll(today())
            container.repository.purgeItems(Triage.purgeDue(state.value.items, now()).map { it.id })
        }
        // Re-arm local reminders when what they depend on changes. Tables a write did not touch
        // keep
        // the same list instance, so the identity checks skip all other writes cheaply.
        viewModelScope.launch {
            state
                .filter { it.loaded }
                .distinctUntilChanged { a, b ->
                    a.tasks === b.tasks &&
                        a.sessions === b.sessions &&
                        a.prefs.reminderMinutes == b.prefs.reminderMinutes
                }
                .collect { Reminders.sync(getApplication(), it) }
        }
    }

    /** The UI language ("en" or "ar"). */
    val lang: String
        get() = state.value.lang

    fun now(): Instant = ctx.now()

    fun today(): LocalDate = ctx.today()

    private companion object {
        const val TAG = "AppViewModel"
    }
}
