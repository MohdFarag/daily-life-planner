package app.dailylife.android.sync

import app.dailylife.android.data.Db
import app.dailylife.android.data.OutgoingChange
import app.dailylife.android.data.PushOutcome
import app.dailylife.android.data.RemoteChange
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Whether Google can be used on this server, is connected, and the last sync problem. */
data class GoogleStatus(
    val enabled: Boolean,
    val connected: Boolean,
    val email: String?,
    val lastError: String?,
)

/** An access token for an assistant (MCP); the secret itself is only shown when created. */
data class McpToken(
    val id: String,
    val name: String,
    val expiresAt: String,
    val lastUsedAt: String?,
)

/** The server's view of an upload. */
data class MediaInfo(val id: String, val receivedBytes: Long, val complete: Boolean)

/** Media endpoints used by [MediaSync]. */
interface MediaApi {
    suspend fun createMedia(
        name: String?,
        mimeType: String,
        sizeBytes: Long,
        sha256: String,
    ): MediaInfo

    suspend fun mediaInfo(id: String): MediaInfo

    suspend fun uploadChunk(id: String, offset: Long, bytes: ByteArray): MediaInfo

    suspend fun download(id: String, target: File)
}

/**
 * The backend API for the signed-in account. Requests carry the access token; when it has expired
 * the refresh token is used once, and if that fails the account is signed out locally.
 */
class ApiClient(private val accounts: SessionStore) : SyncApi, MediaApi {
    private val refreshLock = Mutex()

    // Sync -----------------------------------------------------------------------------------

    override suspend fun push(changes: List<OutgoingChange>): List<PushOutcome> {
        val mutations =
            JSONArray(
                changes.map { c ->
                    JSONObject()
                        .put("mutationId", c.entry.mutationId)
                        .put("entity", c.entry.table.name)
                        .put("id", c.entry.id)
                        .put("op", if (c.doc == null) Db.OP_DELETE else Db.OP_UPSERT)
                        .put("baseVersion", c.baseVersion)
                        .put("fields", c.doc)
                }
            )
        val response =
            authorized("POST", "/v1/sync/push", Body.Json(JSONObject().put("mutations", mutations)))
        val results = response.getJSONArray("results")
        return List(results.length()) { i -> outcome(results.getJSONObject(i)) }
    }

    override suspend fun pull(since: Long, limit: Int): PullPage {
        val r = authorized("GET", "/v1/sync/pull?since=$since&limit=$limit")
        val changes = r.getJSONArray("changes")
        return PullPage(
            List(changes.length()) { i ->
                val c = changes.getJSONObject(i)
                RemoteChange(
                    c.getString("entity"),
                    c.getString("id"),
                    c.getLong("version"),
                    c.getBoolean("deleted"),
                    c.getJSONObject("doc"),
                )
            },
            r.getLong("cursor"),
            r.getBoolean("hasMore"),
        )
    }

    private fun outcome(r: JSONObject): PushOutcome =
        when (r.getString("status")) {
            "rejected" -> PushOutcome.Rejected(r.optString("reason", "rejected"))
            else -> {
                val conflicts = r.optJSONArray("conflicts") ?: JSONArray()
                val serverValues =
                    (0 until conflicts.length()).associate { i ->
                        val c = conflicts.getJSONObject(i)
                        c.getString("field") to c.opt("serverValue")
                    }
                PushOutcome.Saved(r.getLong("version"), serverValues)
            }
        }

    // Media ----------------------------------------------------------------------------------

    override suspend fun createMedia(
        name: String?,
        mimeType: String,
        sizeBytes: Long,
        sha256: String,
    ): MediaInfo {
        val body =
            JSONObject()
                .put("filename", name)
                .put("mimeType", mimeType)
                .put("sizeBytes", sizeBytes)
                .put("sha256", sha256)
        return media(authorized("POST", "/v1/media", Body.Json(body)))
    }

    override suspend fun mediaInfo(id: String) = media(authorized("GET", "/v1/media/$id"))

    override suspend fun uploadChunk(id: String, offset: Long, bytes: ByteArray) =
        media(
            authorized(
                "PUT",
                "/v1/media/$id/content",
                Body.Bytes(bytes),
                mapOf("Upload-Offset" to offset.toString()),
            )
        )

    override suspend fun download(id: String, target: File) {
        withSession { http, token -> http.download("/v1/media/$id/content", token, target) }
    }

    private fun media(o: JSONObject) =
        MediaInfo(
            o.getString("id"),
            o.getLong("receivedBytes"),
            o.getString("status") == "complete",
        )

    // Account --------------------------------------------------------------------------------

    suspend fun logout() {
        runCatching { authorized("POST", "/v1/auth/logout") }
    }

    suspend fun deleteAccount(password: String) {
        authorized("DELETE", "/v1/account", Body.Json(JSONObject().put("password", password)))
    }

    // Google (§6) --------------------------------------------------------------------------------

    suspend fun googleStatus(): GoogleStatus {
        val o = authorized("GET", "/v1/google/status")
        return GoogleStatus(
            o.getBoolean("enabled"),
            o.getBoolean("connected"),
            if (o.isNull("email")) null else o.getString("email"),
            if (o.isNull("lastError")) null else o.getString("lastError"),
        )
    }

    /** The Google consent page to open in a browser. */
    suspend fun googleConnectUrl(): String =
        authorized("POST", "/v1/google/connect").getString("url")

    suspend fun googleSync() {
        authorized("POST", "/v1/google/sync")
    }

    suspend fun googleDisconnect() {
        authorized("DELETE", "/v1/google")
    }

    suspend fun createGoogleCalendar(name: String) {
        authorized("POST", "/v1/google/calendars", Body.Json(JSONObject().put("summary", name)))
    }

    // Assistant access (MCP03) -----------------------------------------------------------------

    suspend fun mcpTokens(): List<McpToken> {
        val list = authorized("GET", "/v1/mcp/tokens").getJSONArray("tokens")
        return List(list.length()) { mcpToken(list.getJSONObject(it)) }
    }

    /** Creates a token; returns it with the secret, which the server never shows again. */
    suspend fun createMcpToken(name: String): Pair<McpToken, String> {
        val o = authorized("POST", "/v1/mcp/tokens", Body.Json(JSONObject().put("name", name)))
        return mcpToken(o) to o.getString("token")
    }

    suspend fun revokeMcpToken(id: String) {
        authorized("DELETE", "/v1/mcp/tokens/$id")
    }

    private fun mcpToken(o: JSONObject) =
        McpToken(
            o.getString("id"),
            o.getString("name"),
            o.getString("expiresAt"),
            if (o.isNull("lastUsedAt")) null else o.getString("lastUsedAt"),
        )

    // Session handling -----------------------------------------------------------------------

    private suspend fun authorized(
        method: String,
        path: String,
        body: Body? = null,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject = withSession { http, token ->
        http.json(method, path, body, token, headers) ?: JSONObject()
    }

    /** Runs [call] with the access token, refreshing it once if the server says it expired. */
    private suspend fun <T> withSession(call: suspend (Http, String) -> T): T {
        val account = accounts.account.value ?: throw SignedOutException()
        val http = Http(account.serverUrl)
        val token = accounts.accessToken ?: throw SignedOutException()
        return try {
            call(http, token)
        } catch (e: ApiException) {
            if (e.status != 401) throw e
            call(http, refresh(http, token))
        }
    }

    private suspend fun refresh(http: Http, expired: String): String = refreshLock.withLock {
        // Another request may have refreshed while this one waited.
        accounts.accessToken
            ?.takeIf { it != expired }
            ?.let {
                return it
            }
        val refresh = accounts.refreshToken ?: throw SignedOutException()
        try {
            val pair =
                http.json(
                    "POST",
                    "/v1/auth/refresh",
                    Body.Json(JSONObject().put("refreshToken", refresh)),
                )!!
            val tokens = tokens(pair)
            accounts.saveTokens(tokens)
            tokens.access
        } catch (e: ApiException) {
            if (e.status == 401) {
                accounts.signedOut()
                throw SignedOutException()
            }
            throw e
        }
    }

    companion object {
        fun tokens(pair: JSONObject) =
            Tokens(pair.getString("accessToken"), pair.getString("refreshToken"))
    }
}
