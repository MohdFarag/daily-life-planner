package app.dailylife.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.dailylife.android.data.OutboxEntry
import app.dailylife.android.sync.ApiException
import app.dailylife.android.sync.SignedOutException
import app.dailylife.android.work.SyncWorker
import java.io.IOException
import kotlinx.coroutines.launch

/** An error to show on the account screen: a string key, plus the server's text when useful. */
data class AccountError(val key: String, val detail: String? = null)

/** Account and sync actions for the account screen (UX03, SYN01, SEC03). */
class AccountController(private val ctx: ControllerScope) {
    private val accounts = ctx.container.accounts
    private val service = ctx.container.accountService

    val account = accounts.account
    val status = accounts.status

    var busy by mutableStateOf(false)
        private set

    var error by mutableStateOf<AccountError?>(null)
        private set

    fun signIn(server: String, email: String, password: String, create: Boolean) = act {
        if (create && password.length < MIN_PASSWORD) {
            error = AccountError("passwordTooShort")
            return@act
        }
        service.signIn(server, email, password, create, ctx.snapshot.lang)
        SyncWorker.schedulePeriodic(ctx.container.appContext)
        ctx.messages.show("signedIn")
        ctx.container.syncRunner.run()
    }

    fun signOut() = act {
        SyncWorker.cancel(ctx.container.appContext)
        service.signOut()
    }

    fun deleteAccount(password: String) = act {
        SyncWorker.cancel(ctx.container.appContext)
        service.deleteAccount(password)
        ctx.messages.show("accountDeleted")
    }

    fun syncNow() = ctx.scope.launch { ctx.container.syncRunner.run() }

    /** Drops a change the server rejected (see [app.dailylife.android.data.Repository.discard]). */
    fun discard(entry: OutboxEntry) = ctx.scope.launch { ctx.repository.discard(entry) }

    fun clearError() {
        error = null
    }

    private fun act(block: suspend () -> Unit) =
        ctx.scope.launch {
            busy = true
            error = null
            try {
                block()
            } catch (e: ApiException) {
                error = errorFor(e)
            } catch (e: SignedOutException) {
                error = AccountError("signedOutError")
            } catch (e: IOException) {
                error = AccountError("serverUnreachable")
            } catch (e: IllegalArgumentException) {
                error = AccountError("invalidServer")
            } finally {
                busy = false
            }
        }

    companion object {
        const val MIN_PASSWORD = 10

        fun errorFor(e: ApiException): AccountError =
            when (e.code) {
                "unauthorized" -> AccountError("wrongCredentials")
                "email_taken" -> AccountError("emailTaken")
                "wrong_password" -> AccountError("wrongPassword")
                "rate_limited" -> AccountError("tooManyAttempts")
                else -> AccountError("serverError", e.message)
            }
    }
}
