package app.dailylife.android.viewmodel

import android.app.Application
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Which task the scheduling sheet plans, and the session being edited if any. */
data class ScheduleTarget(val taskId: String, val editing: TaskSession? = null)

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

    private val ctx = ControllerScope(viewModelScope, container, navigator, messages, clock)
    val capture = CaptureController(ctx)
    val inbox = InboxController(ctx)
    val focus = FocusController(ctx)
    val tasks = TaskController(ctx, focus)
    val settings = SettingsController(ctx)
    val account = AccountController(ctx)
    val google = GoogleController(ctx)

    init {
        viewModelScope.launch {
            container.repository.load()
            container.ruleScheduler.refreshAll(today())
            container.repository.purgeItems(Triage.purgeDue(state.value.items, now()).map { it.id })
        }
        // Re-arm local reminders whenever tasks or sessions change.
        viewModelScope.launch {
            state.drop(1).collect { if (it.loaded) Reminders.sync(getApplication(), it) }
        }
    }

    /** The UI language ("en" or "ar"). */
    val lang: String
        get() = state.value.lang

    fun now(): Instant = ctx.now()

    fun today(): LocalDate = ctx.today()
}
