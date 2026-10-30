package app.dailylife.android.sync

import android.os.Build
import app.dailylife.android.data.Repository
import java.net.URI

/** Signing in, out and deleting the account (UX03, SEC03). Data on the device is never deleted. */
class AccountService(
    private val accounts: AccountStore,
    private val api: ApiClient,
    private val repository: Repository,
) {
    /**
     * Signs in, or creates the account when [create] is true. Throws [ApiException] for a server
     * answer (wrong password, email taken), [java.io.IOException] when the server is unreachable.
     */
    suspend fun signIn(
        serverUrl: String,
        email: String,
        password: String,
        create: Boolean,
        locale: String,
    ) {
        val server = requireNotNull(normalizeServerUrl(serverUrl)) { "Invalid server address" }
        val body =
            org.json
                .JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .put("deviceName", Build.MODEL.take(100))
        if (create) body.put("locale", locale)
        val result =
            Http(server)
                .json(
                    "POST",
                    if (create) "/v1/auth/register" else "/v1/auth/login",
                    Body.Json(body),
                )!!
        val user = result.getJSONObject("user")
        val account = Account(server, user.getString("email"), user.getString("id"))
        // This device's data last synced with another account: upload all of it to this one.
        if (accounts.lastUserId != null && accounts.lastUserId != account.userId) {
            repository.restartSyncForNewAccount()
        }
        accounts.signedIn(account, ApiClient.tokens(result.getJSONObject("tokens")))
    }

    /** Ends the session on the server (best effort) and here; the device keeps its data. */
    suspend fun signOut() {
        api.logout()
        accounts.signedOut()
    }

    /** Deletes the account and its server data after the password is confirmed. */
    suspend fun deleteAccount(password: String) {
        api.deleteAccount(password)
        accounts.signedOut()
    }

    suspend fun googleStatus() = api.googleStatus()

    suspend fun googleConnectUrl() = api.googleConnectUrl()

    suspend fun googleSync() = api.googleSync()

    suspend fun googleDisconnect() = api.googleDisconnect()

    suspend fun createGoogleCalendar(name: String) = api.createGoogleCalendar(name)

    suspend fun assistantTokens() = api.mcpTokens()

    suspend fun createAssistantToken(name: String) = api.createMcpToken(name)

    suspend fun revokeAssistantToken(id: String) = api.revokeMcpToken(id)

    companion object {
        private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

        /**
         * A usable base URL, or null. HTTPS is assumed when no scheme is given; plain HTTP is only
         * accepted for a server on this machine or the emulator's host (development).
         */
        fun normalizeServerUrl(input: String): String? {
            val raw = input.trim().trimEnd('/')
            if (raw.isEmpty()) return null
            val withScheme = if ("://" in raw) raw else "https://$raw"
            val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
            val host = uri.host ?: return null
            return when (uri.scheme?.lowercase()) {
                "https" -> withScheme
                "http" -> withScheme.takeIf { host in LOCAL_HOSTS }
                else -> null
            }
        }
    }
}
