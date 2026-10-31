package app.dailylife.android.sync

import android.content.Context
import androidx.core.content.edit
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The signed-in account as the UI sees it; tokens never leave [AccountStore]. */
data class Account(val serverUrl: String, val email: String, val userId: String)

/** Where the device is in syncing; shown on the account screen. */
data class SyncStatus(
    val running: Boolean = false,
    val lastSyncedAt: Long? = null,
    /** The last run's error, cleared by a successful run. */
    val error: String? = null,
)

/** What [ApiClient] needs from the signed-in session. */
interface SessionStore {
    val account: StateFlow<Account?>
    val accessToken: String?
    val refreshToken: String?

    fun saveTokens(tokens: Tokens)

    fun signedOut()
}

/**
 * The account, its tokens (encrypted, see [TokenCipher]) and sync bookkeeping, in private
 * preferences. Signing out keeps the device's data and its link to the account it last synced with,
 * so signing back in to the same account does not re-upload everything.
 */
class AccountStore(context: Context) : SessionStore, CursorStore {
    private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val _account = MutableStateFlow(readAccount())
    override val account: StateFlow<Account?> = _account.asStateFlow()
    private val _status = MutableStateFlow(SyncStatus(lastSyncedAt = lastSyncedAt()))
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** Identifies this installation to the server, for its list of signed-in devices. */
    val deviceId: String
        get() =
            prefs.getString(DEVICE_ID, null)
                ?: UUID.randomUUID().toString().also { prefs.edit { putString(DEVICE_ID, it) } }

    override var accessToken: String?
        get() = prefs.getString(ACCESS, null)?.let(TokenCipher::decrypt)
        private set(value) = prefs.edit { putString(ACCESS, value?.let(TokenCipher::encrypt)) }

    override var refreshToken: String?
        get() = prefs.getString(REFRESH, null)?.let(TokenCipher::decrypt)
        private set(value) = prefs.edit { putString(REFRESH, value?.let(TokenCipher::encrypt)) }

    override var cursor: Long
        get() = prefs.getLong(CURSOR, 0)
        set(value) = prefs.edit { putLong(CURSOR, value) }

    /** The user this device's data was last synced with; null before the first sign-in. */
    val lastUserId: String?
        get() = prefs.getString(LAST_USER, null)

    fun signedIn(account: Account, tokens: Tokens) {
        if (account.userId != lastUserId) cursor = 0
        prefs.edit {
            putString(SERVER, account.serverUrl)
            putString(EMAIL, account.email)
            putString(USER, account.userId)
            putString(LAST_USER, account.userId)
        }
        saveTokens(tokens)
        _account.value = account
    }

    override fun saveTokens(tokens: Tokens) {
        accessToken = tokens.access
        refreshToken = tokens.refresh
    }

    /** Forgets the session; the data on the device stays. */
    override fun signedOut() {
        prefs.edit {
            remove(USER)
            remove(ACCESS)
            remove(REFRESH)
        }
        _account.value = null
        _status.value = SyncStatus(lastSyncedAt = lastSyncedAt())
    }

    fun syncStarted() {
        _status.value = _status.value.copy(running = true)
    }

    /** A run that was stopped before it ended: nothing synced, nothing failed. */
    fun syncStopped() {
        _status.value = _status.value.copy(running = false)
    }

    fun syncFinished(error: String?) {
        val now = if (error == null) System.currentTimeMillis() else lastSyncedAt()
        if (error == null) prefs.edit { putLong(LAST_SYNC, now ?: 0) }
        _status.value = SyncStatus(running = false, lastSyncedAt = now, error = error)
    }

    private fun lastSyncedAt(): Long? = prefs.getLong(LAST_SYNC, 0).takeIf { it > 0 }

    private fun readAccount(): Account? {
        val user = prefs.getString(USER, null) ?: return null
        return Account(prefs.getString(SERVER, "")!!, prefs.getString(EMAIL, "")!!, user)
    }

    private companion object {
        const val SERVER = "server_url"
        const val EMAIL = "email"
        const val USER = "user_id"
        const val LAST_USER = "last_user_id"
        const val ACCESS = "access_token"
        const val REFRESH = "refresh_token"
        const val CURSOR = "cursor"
        const val LAST_SYNC = "last_sync_at"
        const val DEVICE_ID = "device_id"
    }
}

data class Tokens(val access: String, val refresh: String)
