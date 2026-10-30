package app.dailylife.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.dailylife.android.work.MaintenanceWorker
import java.util.concurrent.TimeUnit

class PlannerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SESSIONS,
                "Session reminders",
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FOLLOWUPS,
                "Follow-ups",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        // Keeps prayer-relative sessions current even if the app is not opened for days (REL02).
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "daily-maintenance",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MaintenanceWorker>(12, TimeUnit.HOURS).build(),
            )
    }

    companion object {
        const val CHANNEL_SESSIONS = "sessions"
        const val CHANNEL_FOLLOWUPS = "followups"
    }
}
