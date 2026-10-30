package app.dailylife.android.ui.capture

import android.content.Context
import app.dailylife.core.model.Attachment
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Unsaved capture survives the app being closed (CAP03). */
internal object CaptureDraft {
    private fun prefs(c: Context) = c.getSharedPreferences("capture-draft", Context.MODE_PRIVATE)

    fun load(c: Context): Pair<String, List<Attachment>> {
        val o = prefs(c).getString("draft", null)?.let(::JSONObject) ?: return "" to emptyList()
        val a = o.optJSONArray("attachments") ?: JSONArray()
        return o.optString("text") to
            (0 until a.length())
                .map { a.getJSONObject(it) }
                .filter { File(it.getString("path")).exists() }
                .map {
                    Attachment(
                        it.getString("id"),
                        it.getString("path"),
                        it.getString("mime"),
                        File(it.getString("path")).length(),
                    )
                }
    }

    fun save(c: Context, text: String, attachments: List<Attachment>) {
        val o =
            JSONObject()
                .put("text", text)
                .put(
                    "attachments",
                    JSONArray(
                        attachments.map {
                            JSONObject()
                                .put("id", it.id)
                                .put("path", it.localPath)
                                .put("mime", it.mimeType)
                        }
                    ),
                )
        prefs(c).edit().putString("draft", o.toString()).apply()
    }

    fun clear(c: Context) = prefs(c).edit().clear().apply()
}
