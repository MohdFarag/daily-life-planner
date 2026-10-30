package app.dailylife.android.work

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.dailylife.android.LaunchExtras
import app.dailylife.android.PlannerApp
import app.dailylife.android.R
import app.dailylife.core.model.SessionState
import app.dailylife.core.model.TaskStatus

class ReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as PlannerApp).container.repository
        repo.load()
        val s = repo.state.value
        val session =
            s.sessions.firstOrNull { it.id == inputData.getString("sessionId") }
                ?: return Result.success()
        val task = s.tasks.firstOrNull { it.id == session.taskId } ?: return Result.success()
        if (
            task.status == TaskStatus.COMPLETED ||
                session.state != SessionState.PLANNED ||
                !canNotify(applicationContext)
        )
            return Result.success()
        val time = session.start.atZone(s.prayer.zone).toLocalTime().toString().take(5)
        val n =
            NotificationCompat.Builder(applicationContext, PlannerApp.CHANNEL_SESSIONS)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(task.title)
                .setContentText(
                    if (s.prefs.lang == "ar") "تبدأ الجلسة $time" else "Session starts at $time"
                )
                .setContentIntent(openApp(applicationContext, LaunchExtras.OPEN_TASK to task.id))
                .setAutoCancel(true)
                .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(applicationContext).notify(session.id.hashCode(), n)
        return Result.success()
    }
}
