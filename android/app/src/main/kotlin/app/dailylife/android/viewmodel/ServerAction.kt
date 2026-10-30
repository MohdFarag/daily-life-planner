package app.dailylife.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.dailylife.android.sync.ApiException
import app.dailylife.android.sync.SignedOutException
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** An error to show on the account screen: a string key, plus the server's text when useful. */
data class AccountError(val key: String, val detail: String? = null)

/** Runs a request to the server for a screen: tracks [busy] and turns failures into [error]. */
class ServerAction(private val scope: CoroutineScope) {
    var busy by mutableStateOf(false)
        private set

    var error by mutableStateOf<AccountError?>(null)
        private set

    fun run(block: suspend () -> Unit) = scope.launch {
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

    fun fail(error: AccountError) {
        this.error = error
    }

    fun clearError() {
        error = null
    }

    companion object {
        fun errorFor(e: ApiException): AccountError =
            when (e.code) {
                "unauthorized" -> AccountError("wrongCredentials")
                "email_taken" -> AccountError("emailTaken")
                "wrong_password" -> AccountError("wrongPassword")
                "rate_limited" -> AccountError("tooManyAttempts")
                "google_not_configured" -> AccountError("googleNotConfigured")
                else -> AccountError("serverError", e.message)
            }
    }
}
