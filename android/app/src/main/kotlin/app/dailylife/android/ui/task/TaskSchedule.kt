package app.dailylife.android.ui.task

import app.dailylife.android.data.RuleBinding
import app.dailylife.android.data.Snapshot
import app.dailylife.core.model.SessionState
import app.dailylife.core.model.TaskSession
import java.time.Instant

/** How many upcoming sessions the task screen lists. */
private const val SHOWN_SESSIONS = 5

/** A task's repeating rules and its next sessions (not canceled, not yet over), soonest first. */
data class TaskSchedule(val rules: List<RuleBinding>, val upcoming: List<TaskSession>)

fun taskSchedule(s: Snapshot, taskId: String, now: Instant): TaskSchedule =
    TaskSchedule(
        rules = s.rules.filter { it.taskId == taskId },
        upcoming =
            s.sessions
                .filter {
                    it.taskId == taskId && it.state != SessionState.CANCELED && it.end.isAfter(now)
                }
                .sortedBy { it.start }
                .take(SHOWN_SESSIONS),
    )
