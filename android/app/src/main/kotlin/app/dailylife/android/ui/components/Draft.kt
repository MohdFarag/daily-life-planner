package app.dailylife.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

private const val SAVE_AFTER_MS = 400L

/**
 * Text for a field bound to stored data. Typing updates the field at once and is saved after a
 * short pause (and when the field leaves the screen), so the field never waits on the database and
 * never loses keystrokes to it. A change arriving from elsewhere (sync) replaces the text only
 * while the user has no unsaved edit.
 */
@Composable
fun rememberDraft(key: Any, stored: String, onSave: (String) -> Unit): MutableState<String> {
    val draft = rememberSaveable(key) { mutableStateOf(stored) }
    var base by remember(key) { mutableStateOf(stored) }
    val save by rememberUpdatedState(onSave)
    val latestStored by rememberUpdatedState(stored)

    LaunchedEffect(key, stored) {
        if (draft.value == base) draft.value = stored
        base = stored
    }
    LaunchedEffect(key, draft.value) {
        if (draft.value == latestStored) return@LaunchedEffect
        delay(SAVE_AFTER_MS)
        save(draft.value)
    }
    DisposableEffect(key) { onDispose { if (draft.value != latestStored) save(draft.value) } }
    return draft
}
