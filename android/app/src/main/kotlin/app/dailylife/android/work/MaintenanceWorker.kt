package app.dailylife.android.work

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.dailylife.android.LaunchExtras
import app.dailylife.android.PlannerApp
import app.dailylife.android.R
import app.dailylife.core.inbox.Triage
import java.time.Instant
import java.time.LocalDate

/**
 * Twice daily: refresh relative sessions, purge expired trash, surface due follow-ups, re-arm
 * reminders.
 */
class MaintenanceWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PlannerApp).container
        val repo = container.repository
        repo.load()
        val today = LocalDate.now(repo.state.value.prayer.zone)
        container.ruleScheduler.refreshAll(today)
        repo.purgeItems(Triage.purgeDue(repo.state.value.items, Instant.now()).map { it.id })
        val s = repo.state.value
        val due = Triage.followUpsDue(s.items, today)
        if (due.isNotEmpty() && canNotify(applicationContext)) {
            val n =
                NotificationCompat.Builder(applicationContext, PlannerApp.CHANNEL_FOLLOWUPS)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(
                        if (s.prefs.lang == "ar") "متابعات مستحقة" else "Follow-ups due"
                    )
                    .setContentText(due.joinToString(" · ") { it.title })
                    .setContentIntent(openApp(applicationContext, LaunchExtras.OPEN_WAITING to "1"))
                    .setAutoCancel(true)
                    .build()
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(applicationContext).notify(1001, n)
        }
        Reminders.sync(applicationContext, s)
        return Result.success()
    }
}
