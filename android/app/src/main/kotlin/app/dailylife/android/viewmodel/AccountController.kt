package app.dailylife.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.dailylife.android.data.OutboxEntry
import app.dailylife.android.sync.McpToken
import app.dailylife.android.work.SyncWorker
import kotlinx.coroutines.launch

/** Account and sync actions for the account screen (UX03, SYN01, SEC03). */
class AccountController(private val ctx: ControllerScope) {
    private val accounts = ctx.container.accounts
    private val service = ctx.container.accountService

    val account = accounts.account
    val status = accounts.status

    private val action = ServerAction(ctx.scope)
    val busy: Boolean
        get() = action.busy

    val error: AccountError?
        get() = action.error

    /** Assistant (MCP) tokens of the signed-in account. */
    var assistantTokens by mutableStateOf<List<McpToken>>(emptyList())
        private set

    /** A token just created; its secret is shown once, then forgotten. */
    var newAssistantToken by mutableStateOf<String?>(null)
        private set

    fun signIn(server: String, email: String, password: String, create: Boolean) = act {
        if (create && password.length < MIN_PASSWORD) {
            action.fail(AccountError("passwordTooShort"))
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

    fun loadAssistantTokens() = act { assistantTokens = service.assistantTokens() }

    fun createAssistantToken(name: String) = act {
        newAssistantToken = service.createAssistantToken(name.trim()).second
        assistantTokens = service.assistantTokens()
    }

    fun revokeAssistantToken(id: String) = act {
        service.revokeAssistantToken(id)
        assistantTokens = service.assistantTokens()
    }

    fun dismissNewToken() {
        newAssistantToken = null
    }

    fun syncNow() = ctx.scope.launch { ctx.container.syncRunner.run() }

    /** Drops a change the server rejected (see [app.dailylife.android.data.Repository.discard]). */
    fun discard(entry: OutboxEntry) = ctx.scope.launch { ctx.repository.discard(entry) }

    fun clearError() = action.clearError()

    private fun act(block: suspend () -> Unit) = action.run(block)

    companion object {
        const val MIN_PASSWORD = 10
    }
}
