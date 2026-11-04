package `is`.xyz.mpv.library

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(val uri: String, val title: String, val positionMs: Long, val durationMs: Long, val lastPlayed: Long)

/** A small bounded on-device playback history. No cloud account or tracking. */
class PlaybackHistory(context: Context) {
    private val prefs = context.getSharedPreferences("video_playback_history", Context.MODE_PRIVATE)

    @Synchronized
    fun entries(): List<HistoryEntry> = runCatching {
        val array = JSONArray(prefs.getString("items", "[]"))
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val uri = obj.optString("uri")
            if (uri.isBlank()) return@mapNotNull null
            HistoryEntry(uri, obj.optString("title", "Video"),
                obj.optLong("positionMs"), obj.optLong("durationMs"),
                obj.optLong("lastPlayed"))
        }.sortedByDescending { it.lastPlayed }
    }.getOrDefault(emptyList())

    @Synchronized
    fun record(uri: String, title: String, positionMs: Long, durationMs: Long) {
        if (uri.isBlank()) return
        val updated = mutableListOf(HistoryEntry(
            uri, title, positionMs.coerceAtLeast(0), durationMs.coerceAtLeast(0),
            System.currentTimeMillis()
        ))
        updated.addAll(entries().filterNot { it.uri == uri }.take(99))
        val array = JSONArray()
        updated.forEach {
            array.put(JSONObject().put("uri", it.uri).put("title", it.title)
                .put("positionMs", it.positionMs).put("durationMs", it.durationMs)
                .put("lastPlayed", it.lastPlayed))
        }
        prefs.edit().putString("items", array.toString()).apply()
    }

    @Synchronized
    fun clear() = prefs.edit().remove("items").apply()
}
