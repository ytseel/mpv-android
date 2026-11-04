package `is`.xyz.mpv.subtitles

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SubtitleMatch(val fileId: Int, val label: String, val language: String)

/**
 * OpenSubtitles REST API v1. All calls must run off the UI thread.
 * Users supply their own API key; quotas and availability are provider-controlled.
 */
class OpenSubtitlesClient(private val context: Context, private val apiKey: String) {
    private val root = "https://api.opensubtitles.com/api/v1"

    private fun connect(url: String, method: String = "GET"): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 12000
        connection.readTimeout = 20000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Api-Key", apiKey)
        connection.setRequestProperty("User-Agent", "mpv-android-custom v1")
        connection.setRequestProperty("Accept", "application/json")
        return connection
    }

    private fun getJson(connection: HttpURLConnection): JSONObject {
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val message = connection.errorStream?.bufferedReader()?.use { it.readText().take(250) }
                throw IllegalStateException("OpenSubtitles HTTP $status: ${message ?: "Request failed"}")
            }
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    fun search(query: String, language: String): List<SubtitleMatch> {
        val q = URLEncoder.encode(query.take(180), "UTF-8")
        val l = URLEncoder.encode(language, "UTF-8")
        val json = getJson(connect("$root/subtitles?query=$q&languages=$l"))
        val data = json.optJSONArray("data") ?: return emptyList()
        val result = mutableListOf<SubtitleMatch>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val attributes = item.optJSONObject("attributes") ?: continue
            val files = attributes.optJSONArray("files") ?: continue
            val first = files.optJSONObject(0) ?: continue
            val fileId = first.optInt("file_id")
            if (fileId <= 0) continue
            result.add(SubtitleMatch(fileId,
                attributes.optString("release", "Subtitle #$fileId"),
                attributes.optString("language", language)))
        }
        return result
    }

    fun download(fileId: Int): File {
        require(fileId > 0)
        val conn = connect("$root/download", "POST")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        try {
            conn.outputStream.use {
                it.write(JSONObject().put("file_id", fileId).toString().toByteArray(Charsets.UTF_8))
            }
            val json = getJson(conn)
            val link = json.getString("link")
            require(URL(link).protocol == "https") { "Subtitle download must be HTTPS" }
            val directory = File(context.filesDir, "downloaded_subtitles").apply { mkdirs() }
            val destination = File(directory, "subtitle_$fileId.srt")
            val transfer = URL(link).openConnection() as HttpURLConnection
            transfer.connectTimeout = 12000
            transfer.readTimeout = 20000
            try {
                if (transfer.responseCode !in 200..299) throw IllegalStateException("Subtitle file download failed")
                destination.outputStream().use { out ->
                    transfer.inputStream.use { input -> input.copyTo(out) }
                }
                return destination
            } finally { transfer.disconnect() }
        } catch (e: Exception) {
            throw e
        }
    }
}
