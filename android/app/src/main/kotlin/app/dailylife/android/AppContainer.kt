package app.dailylife.android

import android.content.Context
import app.dailylife.android.data.Repository
import app.dailylife.android.domain.DayPlanner
import app.dailylife.android.domain.RuleScheduler
import app.dailylife.android.media.MediaFiles
import app.dailylife.android.sync.AccountService
import app.dailylife.android.sync.AccountStore
import app.dailylife.android.sync.ApiClient
import app.dailylife.android.sync.SyncRunner
import app.dailylife.android.work.SyncWorker
import java.io.File

/**
 * Creates the app's long-lived objects once; the UI and background workers receive them from here.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val repository = Repository(context)
    val dayPlanner = DayPlanner()
    val ruleScheduler = RuleScheduler(repository, dayPlanner)
    val mediaDir: File = MediaFiles.dir(context)
    val accounts = AccountStore(context)
    private val api = ApiClient(accounts)
    val syncRunner = SyncRunner(accounts, api, repository, mediaDir)
    val accountService = AccountService(accounts, api, repository)

    init {
        // Every local change is sent soon after it is saved, while signed in.
        repository.onLocalChange = {
            if (accounts.account.value != null) SyncWorker.requestSoon(appContext)
        }
    }
}
