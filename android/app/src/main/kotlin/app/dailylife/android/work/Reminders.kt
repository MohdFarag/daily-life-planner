package app.dailylife.android.work

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.dailylife.android.data.Snapshot
import app.dailylife.core.model.SessionState
import app.dailylife.core.model.TaskStatus
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

private const val REMINDER_TAG = "session-reminder"

/**
 * Local reminders for sessions. Once Google Calendar sync exists, Calendar becomes the default
 * reminder source and these only cover sessions not yet published (NOT02).
 */
object Reminders {
    fun sync(context: Context, s: Snapshot, now: Instant = Instant.now()) {
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWorkByTag(REMINDER_TAG)
        val horizon = now.plus(Duration.ofHours(48))
        val open = s.tasks.filter { it.status != TaskStatus.COMPLETED }.associateBy { it.id }
        s.sessions
            .filter {
                it.state == SessionState.PLANNED &&
                    it.taskId in open &&
                    it.start.isAfter(now) &&
                    it.start.isBefore(horizon)
            }
            .forEach { session ->
                val fireAt =
                    session.start.minus(Duration.ofMinutes(s.prefs.reminderMinutes.toLong()))
                val delay = Duration.between(now, fireAt).toMillis().coerceAtLeast(0)
                wm.enqueue(
                    OneTimeWorkRequestBuilder<ReminderWorker>()
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .addTag(REMINDER_TAG)
                        .setInputData(workDataOf("sessionId" to session.id))
                        .build()
                )
            }
    }
}
