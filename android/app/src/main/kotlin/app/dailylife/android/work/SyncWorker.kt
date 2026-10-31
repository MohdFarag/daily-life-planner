package app.dailylife.android.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.dailylife.android.PlannerApp
import app.dailylife.android.sync.SyncResult
import java.util.concurrent.TimeUnit

/** Runs a sync when the network is available; WorkManager retries with backoff when offline. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PlannerApp).container
        container.repository.load()
        return when (container.syncRunner.run()) {
            SyncResult.DONE,
            SyncResult.NOT_SIGNED_IN,
            SyncResult.FAILED -> Result.success()
            SyncResult.RETRY -> Result.retry()
        }
    }

    companion object {
        private const val PERIODIC = "sync-periodic"
        private const val SOON = "sync-soon"
        private val online =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Background sync every 30 minutes while signed in. */
        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                        .setConstraints(online)
                        .build(),
                )
        }

        /**
         * Syncs shortly after the last local change: each request restarts the short delay, so a
         * burst of edits becomes one sync. A run already in progress is replaced; pushes are
         * idempotent and pulls resume, so that only repeats safe work.
         */
        fun requestSoon(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    SOON,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<SyncWorker>()
                        .setConstraints(online)
                        .setInitialDelay(2, TimeUnit.SECONDS)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .build(),
                )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(SOON)
        }
    }
}
