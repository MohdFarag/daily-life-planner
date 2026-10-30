package app.dailylife.android.viewmodel

import app.dailylife.android.ui.plan.EventDraft
import java.time.LocalDate
import kotlinx.coroutines.launch

/** Which day a new event is for, or which event is being edited. */
data class EventTarget(val date: LocalDate, val eventId: String? = null)

/**
 * Standalone calendar events (CAL01, CAL03, REL01); with Google connected they reach Google too.
 */
class EventController(private val ctx: ControllerScope) {

    /** Saves the draft; false when it cannot be timed (no prayer times for that day). */
    fun save(draft: EventDraft, eventId: String?): Boolean {
        val existing = eventId?.let { id -> ctx.snapshot.events.firstOrNull { it.id == id } }
        val anchors = ctx.container.dayPlanner.anchorsOrNull(ctx.snapshot, draft.date)
        val event =
            draft.toEvent(eventId ?: ctx.newId(), ctx.snapshot.prayer.zone, anchors, existing)
                ?: return false
        ctx.scope.launch { ctx.repository.saveEvent(event) }
        return true
    }

    fun delete(eventId: String) = ctx.scope.launch { ctx.repository.deleteEvent(eventId) }
}
