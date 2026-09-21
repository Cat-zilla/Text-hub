package com.texthub.app.prefs

import android.content.Context
import com.texthub.app.ui.theme.AccentOption
import com.texthub.app.ui.theme.AppTheme
import org.json.JSONObject

/**
 * Tiny SharedPreferences wrapper for harmless UI preferences only.
 *
 * Never stored here: input text, output text, encryption passwords, cipher keys.
 * The [saveParams] helper refuses to write sensitive parameters.
 */
class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var theme: AppTheme
        get() = AppTheme.fromId(prefs.getString(KEY_THEME, AppTheme.SYSTEM.id) ?: AppTheme.SYSTEM.id)
        set(value) = prefs.edit().putString(KEY_THEME, value.id).apply()

    var accent: AccentOption
        get() = AccentOption.fromId(prefs.getString(KEY_ACCENT, AccentOption.TEAL.id))
        set(value) = prefs.edit().putString(KEY_ACCENT, value.id).apply()

    var autoProcess: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PROCESS, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_PROCESS, value).apply()

    var copyConfirmation: Boolean
        get() = prefs.getBoolean(KEY_COPY_CONFIRMATION, true)
        set(value) = prefs.edit().putBoolean(KEY_COPY_CONFIRMATION, value).apply()

    var lastTool: String?
        get() = prefs.getString(KEY_LAST_TOOL, null)
        set(value) = prefs.edit().putString(KEY_LAST_TOOL, value).apply()

    var favorites: Set<String>
        get() = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_FAVORITES, value).apply()

    var recents: List<String>
        get() = (prefs.getString(KEY_RECENTS, "") ?: "").split("|").filter { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_RECENTS, value.take(RECENT_LIMIT).joinToString("|")).apply()

    fun paramsFor(toolId: String): Map<String, String> {
        val raw = prefs.getString("$KEY_PARAMS_PREFIX$toolId", null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** Stores non-secret parameters only (never keys or passwords). */
    fun saveParams(toolId: String, params: Map<String, String>) {
        val obj = JSONObject()
        params.forEach { (key, value) -> obj.put(key, value) }
        prefs.edit().putString("$KEY_PARAMS_PREFIX$toolId", obj.toString()).apply()
    }

    /** Clears remembered parameters, favourites and recents. Keeps the appearance settings. */
    fun clearTemporaryData() {
        val keysToRemove = prefs.all.keys.filter { it.startsWith(KEY_PARAMS_PREFIX) }
        val editor = prefs.edit()
        keysToRemove.forEach { editor.remove(it) }
        editor.remove(KEY_FAVORITES).remove(KEY_RECENTS)
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "texthub_preferences"
        private const val KEY_THEME = "theme"
        private const val KEY_ACCENT = "accent"
        private const val KEY_AUTO_PROCESS = "auto_process"
        private const val KEY_COPY_CONFIRMATION = "copy_confirmation"
        private const val KEY_LAST_TOOL = "last_tool"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_RECENTS = "recents"
        private const val KEY_PARAMS_PREFIX = "params_"
        private const val RECENT_LIMIT = 6
    }
}
