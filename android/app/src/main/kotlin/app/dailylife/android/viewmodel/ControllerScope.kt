package app.dailylife.android.viewmodel

import app.dailylife.android.AppContainer
import app.dailylife.android.data.Snapshot
import app.dailylife.android.navigation.Navigator
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CoroutineScope

/**
 * What every feature controller needs: where to run work, data, navigation, messages and the clock.
 */
class ControllerScope(
    val scope: CoroutineScope,
    val container: AppContainer,
    val navigator: Navigator,
    val messages: Messages,
    private val clock: Clock,
) {
    val repository
        get() = container.repository

    val snapshot: Snapshot
        get() = repository.state.value

    fun now(): Instant = clock.instant()

    /** Today in the user's planning time zone (the prayer settings' zone). */
    fun today(): LocalDate = now().atZone(snapshot.prayer.zone).toLocalDate()

    fun newId(): String = UUID.randomUUID().toString()
}
