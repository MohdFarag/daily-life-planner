package app.dailylife.android

import android.content.Context
import app.dailylife.android.data.Repository
import app.dailylife.android.domain.DayPlanner
import app.dailylife.android.domain.RuleScheduler
import app.dailylife.android.media.MediaFiles
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
}
