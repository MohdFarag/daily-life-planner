package app.dailylife.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A short confirmation shown at the bottom of the screen. [key] and [argKey] are string keys; the
 * UI translates them, so controllers never format user-facing text.
 */
data class UiMessage(
    val key: String,
    val argKey: String? = null,
    val undo: (() -> Unit)? = null,
    val id: Long = System.nanoTime(),
)

class Messages {
    var current by mutableStateOf<UiMessage?>(null)
        private set

    fun show(key: String, argKey: String? = null, undo: (() -> Unit)? = null) {
        current = UiMessage(key, argKey, undo)
    }

    /** Dismisses [message] if it is still the one showing. */
    fun dismiss(message: UiMessage? = current) {
        if (current?.id == message?.id) current = null
    }
}
