package app.dailylife.android.sync

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** An error answer from the server (its `{ error: { code, message } }` body). */
class ApiException(val status: Int, val code: String, message: String) : Exception(message)

/** The account's session ended on the server; the user has to sign in again. */
class SignedOutException : Exception("Signed out")

/** A request body: JSON, or raw bytes for media chunks. */
sealed interface Body {
    data class Json(val value: JSONObject) : Body

    class Bytes(val value: ByteArray) : Body
}

/**
 * Minimal blocking HTTP over [HttpURLConnection], run on the IO dispatcher. Network failures throw
 * [IOException] (retry later); server errors throw [ApiException].
 */
internal class Http(private val baseUrl: String) {

    suspend fun json(
        method: String,
        path: String,
        body: Body? = null,
        token: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject? =
        withContext(Dispatchers.IO) {
            exchange(method, path, body, token, headers) { conn ->
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                if (text.isBlank()) null else JSONObject(text)
            }
        }

    /** Streams a response body into [target], replacing it only once complete. */
    suspend fun download(path: String, token: String, target: File) =
        withContext(Dispatchers.IO) {
            exchange("GET", path, null, token, emptyMap()) { conn ->
                val partial = File(target.path + ".part")
                conn.inputStream.use { input -> partial.outputStream().use { input.copyTo(it) } }
                if (!partial.renameTo(target)) throw IOException("Could not save $target")
            }
        }

    private fun <T> exchange(
        method: String,
        path: String,
        body: Body?,
        token: String?,
        headers: Map<String, String>,
        read: (HttpURLConnection) -> T,
    ): T {
        val conn = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json")
            token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            // Android's HttpURLConnection labels a body-less POST as a form, which the server
            // rejects (415); such requests send an empty JSON object instead.
            val payload = body ?: if (method in BODY_METHODS) Body.Json(JSONObject()) else null
            when (payload) {
                is Body.Json ->
                    send(conn, "application/json", payload.value.toString().toByteArray())
                is Body.Bytes -> send(conn, "application/octet-stream", payload.value)
                null -> Unit
            }
            val status = conn.responseCode
            if (status >= 400) throw error(status, conn)
            return read(conn)
        } finally {
            conn.disconnect()
        }
    }

    private fun send(conn: HttpURLConnection, type: String, bytes: ByteArray) {
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", type)
        conn.setFixedLengthStreamingMode(bytes.size)
        conn.outputStream.use { it.write(bytes) }
    }

    private fun error(status: Int, conn: HttpURLConnection): ApiException {
        val text = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        val error = runCatching { JSONObject(text).getJSONObject("error") }.getOrNull()
        return ApiException(
            status,
            error?.optString("code").orEmpty().ifEmpty { "http_$status" },
            error?.optString("message").orEmpty().ifEmpty { "Request failed ($status)" },
        )
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
        val BODY_METHODS = setOf("POST", "PUT", "PATCH")
    }
}
