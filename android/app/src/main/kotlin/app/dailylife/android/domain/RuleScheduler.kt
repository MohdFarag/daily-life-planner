package app.dailylife.android.domain

import app.dailylife.android.data.Repository
import app.dailylife.android.data.RuleBinding
import app.dailylife.android.data.Snapshot
import app.dailylife.android.data.WORK_LIST
import app.dailylife.core.model.RelativeRule
import app.dailylife.core.model.TaskSession
import app.dailylife.core.schedule.Planner
import app.dailylife.core.schedule.RelativeScheduler
import java.time.LocalDate

/**
 * Keeps prayer-relative sessions published for a rolling window of days (REL02). Recalculation
 * changes only future, unpinned occurrences (REL03) and flags clashes for review (REL04).
 */
class RuleScheduler(private val repository: Repository, private val dayPlanner: DayPlanner) {

    /** Attaches [rule] to a task and publishes its occurrences. */
    suspend fun attach(taskId: String, rule: RelativeRule, today: LocalDate) {
        val binding = RuleBinding(taskId, rule)
        repository.saveRule(binding, window(repository.state.value, binding, today))
    }

    /** Removes a rule and its future occurrences; past ones stay as history. */
    suspend fun detach(ruleId: String, today: LocalDate) {
        val future =
            repository.state.value.sessions.filter {
                it.id.startsWith(occurrencePrefix(ruleId)) &&
                    it.occurrenceDate?.isBefore(today) == false &&
                    !it.pinned
            }
        repository.deleteRule(ruleId, future.map { it.id })
    }

    /** Recomputes every rule's window — daily, and after prayer settings change. */
    suspend fun refreshAll(today: LocalDate) {
        val snapshot = repository.state.value
        val sessions = snapshot.rules.flatMap { window(snapshot, it, today) }
        if (sessions.isNotEmpty()) repository.saveSessions(sessions)
        retimeEvents(today)
    }

    /** Prayer-relative events from today on follow changed prayer times (REL01, REL03). */
    private suspend fun retimeEvents(today: LocalDate) {
        val snapshot = repository.state.value
        snapshot.events.forEach { event ->
            val rule = event.rule ?: return@forEach
            if (rule.from.isBefore(today)) return@forEach
            val anchors = dayPlanner.anchorsOrNull(snapshot, rule.from) ?: return@forEach
            val slot = RelativeScheduler.resolve(rule, anchors)
            if (slot.start != event.start || slot.end != event.end) {
                repository.saveEvent(event.copy(start = slot.start, end = slot.end))
            }
        }
    }

    private fun window(
        snapshot: Snapshot,
        binding: RuleBinding,
        today: LocalDate,
    ): List<TaskSession> {
        val prefix = occurrencePrefix(binding.rule.id)
        val existing = snapshot.sessions.filter { it.id.startsWith(prefix) }
        val others =
            snapshot.copy(sessions = snapshot.sessions.filterNot { it.id.startsWith(prefix) })
        val listId = snapshot.tasks.firstOrNull { it.id == binding.taskId }?.listId ?: WORK_LIST
        return RelativeScheduler.refreshWindow(
            taskId = binding.taskId,
            rule = binding.rule,
            existing = existing,
            today = today,
            days = snapshot.prefs.windowDays,
            anchorsFor = { date -> dayPlanner.anchorsOrNull(snapshot, date) },
            conflictsWith = { slot ->
                val date = slot.start.atZone(snapshot.prayer.zone).toLocalDate()
                // The session is anchored to a prayer, so only meetings, other sessions and
                // protected blocks count.
                !Planner.check(
                        dayPlanner.dayContext(others, date).copy(protectPrayers = false),
                        slot,
                        listId,
                    )
                    .ok
            },
        )
    }

    private fun occurrencePrefix(ruleId: String) = "$ruleId@"
}
