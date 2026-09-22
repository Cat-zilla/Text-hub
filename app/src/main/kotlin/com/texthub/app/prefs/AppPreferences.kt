package com.texthub.app.prefs

import android.content.Context
import com.texthub.app.ui.theme.AccentOption
import com.texthub.app.ui.theme.AppTheme
import com.texthub.core.ToolRegistry
import com.texthub.core.prefs.PrefsData
import com.texthub.core.prefs.PrefsKeys
import com.texthub.core.prefs.formatDataSize

/**
 * Thin SharedPreferences adapter over [PrefsData], which holds all of the logic (and the unit
 * tests). This class only moves values between the platform store and that model.
 *
 * Stored: appearance, switches, last tool, favourites (ordered), recents, non-secret parameters.
 * Never stored: input text, output text, passwords, keys, decrypted data.
 */
class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The whole store as a plain map of strings, so [PrefsData] can work on it. */
    private fun read(): PrefsData {
        val entries = mutableMapOf<String, String>()
        prefs.all.forEach { (key, value) ->
            when (value) {
                // The legacy favourites key held a StringSet; keep it readable for the migration.
                is Set<*> -> entries[key] = value.filterIsInstance<String>().joinToString("|")
                is String -> entries[key] = value
                is Boolean -> entries[key] = value.toString()
                is Int -> entries[key] = value.toString()
                is Long -> entries[key] = value.toString()
                else -> Unit
            }
        }
        return PrefsData(entries)
    }

    private fun write(data: PrefsData) {
        val editor = prefs.edit()
        editor.clear()
        data.entries.forEach { (key, value) -> editor.putString(key, value) }
        editor.apply()
    }

    private fun update(block: (PrefsData) -> PrefsData) = write(block(read()))

    private val registryOrder: List<String> get() = ToolRegistry.all.map { it.meta.id }

    // ------------------------------------------------------------------ appearance + switches

    var theme: AppTheme
        get() = AppTheme.fromId(read().string(PrefsKeys.THEME) ?: AppTheme.SYSTEM.id)
        set(value) = update { it.with(PrefsKeys.THEME, value.id) }

    var accent: AccentOption
        get() = AccentOption.fromId(read().string(PrefsKeys.ACCENT) ?: AccentOption.TEAL.id)
        set(value) = update { it.with(PrefsKeys.ACCENT, value.id) }

    var autoProcess: Boolean
        get() = read().bool(PrefsKeys.AUTO_PROCESS, true)
        set(value) = update { it.with(PrefsKeys.AUTO_PROCESS, value.toString()) }

    var copyConfirmation: Boolean
        get() = read().bool(PrefsKeys.COPY_CONFIRMATION, true)
        set(value) = update { it.with(PrefsKeys.COPY_CONFIRMATION, value.toString()) }

    var lastTool: String?
        get() = read().string(PrefsKeys.LAST_TOOL)
        set(value) = update { it.with(PrefsKeys.LAST_TOOL, value) }

    // ------------------------------------------------------------------ favourites (ordered)

    fun favorites(): List<String> = read().favorites(registryOrder)

    fun setFavorites(ids: List<String>) = update { it.withFavorites(ids) }

    /** Adds or removes a favourite; a new favourite is appended at the end of the order. */
    fun toggleFavorite(id: String): List<String> {
        val data = read()
        val next = data.toggleFavorite(id, registryOrder)
        write(next)
        return next.favorites(registryOrder)
    }

    /** Moves a favourite from one position to another (drag and drop) and stores the new order. */
    fun moveFavorite(from: Int, to: Int): List<String> {
        val data = read()
        val next = data.moveFavorite(from, to, registryOrder)
        write(next)
        return next.favorites(registryOrder)
    }

    // ------------------------------------------------------------------ recents

    fun recents(): List<String> = read().recents()

    fun rememberRecent(id: String): List<String> {
        val data = read()
        val next = data.withRecent(id)
        write(next)
        return next.recents()
    }

    // ------------------------------------------------------------------ tool parameters

    fun paramsFor(toolId: String): Map<String, String> = read().paramsFor(toolId)

    /** Stores non-secret parameters only; the caller filters out sensitive keys. */
    fun saveParams(toolId: String, params: Map<String, String>) = update { it.withParams(toolId, params) }

    // ------------------------------------------------------------------ temporary data

    /** How much disposable data is stored right now, as a human-readable size. */
    fun temporaryDataSize(): String = formatDataSize(read().temporaryBytes())

    fun temporaryDataBytes(): Long = read().temporaryBytes()

    /**
     * Removes the remembered tool parameters and the recent-tool list.
     *
     * Favourites are **not** touched by this - they are the user's own curated list, and they can
     * only be removed by tapping their star. Appearance settings and the last tool are kept too.
     */
    fun clearTemporaryData() = update { it.clearTemporary() }

    companion object {
        private const val PREFS_NAME = "texthub_preferences"
    }
}
