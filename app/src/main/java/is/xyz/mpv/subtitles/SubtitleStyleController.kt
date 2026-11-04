package `is`.xyz.mpv.subtitles

import android.content.Context
import `is`.xyz.mpv.MPVLib

/** Preferences for unstyled text subtitles. ASS and bitmap subtitles may override them. */
class SubtitleStyleController(context: Context) {
    private val prefs = context.getSharedPreferences("subtitle_style", Context.MODE_PRIVATE)

    fun apply() {
        MPVLib.setPropertyInt("sub-font-size", prefs.getInt("fontSize", 42))
        MPVLib.setPropertyInt("sub-pos", prefs.getInt("position", 100))
        MPVLib.setPropertyString("sub-color", prefs.getString("color", "#FFFFFF") ?: "#FFFFFF")
        MPVLib.setPropertyInt("sub-border-size", prefs.getInt("outline", 2))
        MPVLib.setPropertyInt("sub-shadow-offset", prefs.getInt("shadow", 1))
    }

    fun setSize(value: Int) { prefs.edit().putInt("fontSize", value.coerceIn(16, 90)).apply(); apply() }
    fun setPosition(value: Int) { prefs.edit().putInt("position", value.coerceIn(0, 150)).apply(); apply() }
    fun setOutline(value: Int) { prefs.edit().putInt("outline", value.coerceIn(0, 8)).apply(); apply() }
    fun setShadow(value: Int) { prefs.edit().putInt("shadow", value.coerceIn(0, 8)).apply(); apply() }
    fun setColor(value: String) {
        require(value in listOf("#FFFFFF", "#FFFF00", "#00FFFF", "#00FF00"))
        prefs.edit().putString("color", value).apply(); apply()
    }
}
