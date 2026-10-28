package app.dailylife.android.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.dailylife.android.MainActivity
import app.dailylife.android.PlannerApp
import app.dailylife.android.R
import app.dailylife.android.data.Snapshot
import app.dailylife.core.inbox.Triage
import app.dailylife.core.model.SessionState
import app.dailylife.core.model.TaskStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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
            .filter { it.state == SessionState.PLANNED && it.taskId in open && it.start.isAfter(now) && it.start.isBefore(horizon) }
            .forEach { session ->
                val fireAt = session.start.minus(Duration.ofMinutes(s.prefs.reminderMinutes.toLong()))
                val delay = Duration.between(now, fireAt).toMillis().coerceAtLeast(0)
                wm.enqueue(
                    OneTimeWorkRequestBuilder<ReminderWorker>()
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .addTag(REMINDER_TAG)
                        .setInputData(workDataOf("sessionId" to session.id))
                        .build(),
                )
            }
    }
}

private fun canNotify(context: Context) =
    android.os.Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun openApp(context: Context, extra: Pair<String, String>? = null): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        extra?.let { putExtra(it.first, it.second) }
    }
    return PendingIntent.getActivity(context, extra?.second?.hashCode() ?: 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as PlannerApp).repository
        repo.load()
        val s = repo.state.value
        val session = s.sessions.firstOrNull { it.id == inputData.getString("sessionId") } ?: return Result.success()
        val task = s.tasks.firstOrNull { it.id == session.taskId } ?: return Result.success()
        if (task.status == TaskStatus.COMPLETED || session.state != SessionState.PLANNED || !canNotify(applicationContext)) return Result.success()
        val time = session.start.atZone(s.prayer.zone).toLocalTime().toString().take(5)
        val n = NotificationCompat.Builder(applicationContext, PlannerApp.CHANNEL_SESSIONS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(task.title)
            .setContentText(if (s.prefs.lang == "ar") "تبدأ الجلسة $time" else "Session starts at $time")
            .setContentIntent(openApp(applicationContext, "openTask" to task.id))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(applicationContext).notify(session.id.hashCode(), n)
        return Result.success()
    }
}

/** Twice daily: refresh relative sessions, purge expired trash, surface due follow-ups, re-arm reminders. */
class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as PlannerApp).repository
        repo.load()
        val today = LocalDate.now(repo.state.value.prayer.zone)
        repo.refreshAllRules(today)
        repo.purge(Triage.purgeDue(repo.state.value.items, Instant.now()).map { it.id })
        val s = repo.state.value
        val due = Triage.followUpsDue(s.items, today)
        if (due.isNotEmpty() && canNotify(applicationContext)) {
            val n = NotificationCompat.Builder(applicationContext, PlannerApp.CHANNEL_FOLLOWUPS)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(if (s.prefs.lang == "ar") "متابعات مستحقة" else "Follow-ups due")
                .setContentText(due.joinToString(" · ") { it.title })
                .setContentIntent(openApp(applicationContext, "openWaiting" to "1"))
                .setAutoCancel(true)
                .build()
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(applicationContext).notify(1001, n)
        }
        Reminders.sync(applicationContext, s)
        return Result.success()
    }
}
