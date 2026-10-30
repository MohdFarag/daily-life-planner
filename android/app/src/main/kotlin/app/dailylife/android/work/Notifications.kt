package app.dailylife.android.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.dailylife.android.MainActivity

/** Whether the app may post notifications (Android 13+ needs the runtime permission). */
internal fun canNotify(context: Context) =
    android.os.Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

/** Opens the app; [extra] tells MainActivity which screen to show. */
internal fun openApp(context: Context, extra: Pair<String, String>? = null): PendingIntent {
    val intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            extra?.let { putExtra(it.first, it.second) }
        }
    return PendingIntent.getActivity(
        context,
        extra?.second?.hashCode() ?: 0,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
