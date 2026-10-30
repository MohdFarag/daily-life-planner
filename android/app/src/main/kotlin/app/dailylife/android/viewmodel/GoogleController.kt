package app.dailylife.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.dailylife.android.data.CalendarSettings
import app.dailylife.android.sync.GoogleStatus
import kotlinx.coroutines.launch

/**
 * Connecting Google and choosing calendars (§6, CAL02). The server holds the Google tokens and does
 * the syncing; the app starts it, opens Google's consent page, and edits which calendars show.
 */
class GoogleController(private val ctx: ControllerScope) {
    private val service = ctx.container.accountService
    private val action = ServerAction(ctx.scope)

    var status by mutableStateOf<GoogleStatus?>(null)
        private set

    val busy: Boolean
        get() = action.busy

    val error: AccountError?
        get() = action.error

    fun refresh() = action.run { status = service.googleStatus() }

    /** Gets Google's consent page; [open] shows it in the browser. */
    fun connect(open: (String) -> Unit) = action.run { open(service.googleConnectUrl()) }

    /** Back from the browser: if connected, sync Google and then this device. */
    fun returned(result: String?) = action.run {
        status = service.googleStatus()
        when (result) {
            "connected" -> {
                ctx.messages.show("googleConnected")
                syncEverything()
            }
            "denied" -> action.fail(AccountError("googleDenied"))
            else -> action.fail(AccountError("googleFailed"))
        }
    }

    fun syncNow() = action.run { syncEverything() }

    fun disconnect() = action.run {
        service.googleDisconnect()
        status = service.googleStatus()
        ctx.container.syncRunner.run()
    }

    fun setVisible(calendarId: String, visible: Boolean) = updateCalendars {
        it.copy(
            list = it.list.map { c -> if (c.id == calendarId) c.copy(visible = visible) else c }
        )
    }

    fun setDefault(calendarId: String) = updateCalendars { it.copy(defaultCalendarId = calendarId) }

    fun createCalendar(name: String) = action.run {
        service.createGoogleCalendar(name.trim())
        syncEverything()
    }

    /** The server syncs Google; the device then pulls what changed. */
    private suspend fun syncEverything() {
        ctx.container.syncRunner.run()
        service.googleSync()
        ctx.container.syncRunner.run()
    }

    private fun updateCalendars(change: (CalendarSettings) -> CalendarSettings) =
        ctx.scope.launch {
            val current = ctx.snapshot.calendars ?: return@launch
            ctx.repository.saveCalendarSettings(change(current))
        }
}
