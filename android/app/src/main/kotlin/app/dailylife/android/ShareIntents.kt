package app.dailylife.android

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import app.dailylife.android.viewmodel.SharePayload

/**
 * Reads an ACTION_SEND / ACTION_SEND_MULTIPLE intent; null if it is not a share or carries nothing.
 */
fun Intent.toSharePayload(fromPackage: String?): SharePayload? {
    val uris: List<Uri> =
        when (action) {
            Intent.ACTION_SEND ->
                listOfNotNull(
                    IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
                )
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
                    .orEmpty()
            else -> return null
        }
    val text = getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
    if (text.isNullOrBlank() && uris.isEmpty()) return null
    return SharePayload(text, getStringExtra(Intent.EXTRA_SUBJECT), uris, type, fromPackage)
}

/** Extras set on notification taps, so the app opens on the relevant screen. */
object LaunchExtras {
    const val OPEN_TASK = "openTask"
    const val OPEN_WAITING = "openWaiting"
}
