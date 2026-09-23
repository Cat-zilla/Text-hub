package com.texthub.core.prefs

/**
 * The complete set of things Text Hub is allowed to remember, modelled as a plain map so it can be
 * unit tested without Android.
 *
 * Persisted here: the theme, the accent colour, the two switches, the last tool, the favourites
 * (as an **ordered** list), the recent tools and the parameter values of tools the user adjusted.
 *
 * Never persisted here: the input text, the output text, passwords or any other sensitive
 * parameter (see [PrefsData.withParams] and [ToolParamFilter]). Encryption keys and passwords live
 * only in memory for as long as one operation runs.
 */
object PrefsKeys {
    const val THEME = "theme"
    const val ACCENT = "accent"
    const val AUTO_PROCESS = "auto_process"
    const val COPY_CONFIRMATION = "copy_confirmation"
    const val LAST_TOOL = "last_tool"
    const val RECENTS = "recents"

    /** Ordered favourites, `id|id|id`. Added in 1.4.3; the old unordered set is migrated. */
    const val FAVORITES_ORDER = "favorites_order"

    /** Legacy key holding an unordered `StringSet` of favourites (1.4.2 and earlier). */
    const val FAVORITES_LEGACY = "favorites"

    const val PARAMS_PREFIX = "params_"

    /** Everything a tool remembers about itself; removed by "Clear temporary data". */
    fun temporaryKeys(entries: Map<String, String>): List<String> =
        entries.keys.filter { it.startsWith(PARAMS_PREFIX) || it == RECENTS }
}

/** Values of [PrefsKeys] that are *not* disposable: the user's own choices and their favourites. */
val KEPT_WHEN_CLEARING: List<String> = listOf(
    PrefsKeys.THEME,
    PrefsKeys.ACCENT,
    PrefsKeys.AUTO_PROCESS,
    PrefsKeys.COPY_CONFIRMATION,
    PrefsKeys.LAST_TOOL,
    PrefsKeys.FAVORITES_ORDER,
)

private const val SEPARATOR = "|"
private const val RECENT_LIMIT = 6

/** One stored preferences entry, so the size of each group can be reported to the user. */
data class PrefsEntry(val key: String, val value: String) {
    val bytes: Long get() = (key.length + value.length).toLong() * 2
}

/** The remembered settings, as a map, with the operations the app needs. */
/**
 * The pure move behind a favourites drag, shared by the storage layer and by the picker, so what the
 * list shows after a drop and what is written to disk cannot disagree.
 *
 * [order] is the stored order, [displayed] is the list as the user sees it (the same order, minus
 * favourites that are filtered out), [draggedId] is the row being moved and [anchorId] the row it
 * must end up in front of (null = move to the end).
 */
fun moveFavoriteInDisplayedOrder(
    order: List<String>,
    displayed: List<String>,
    draggedId: String,
    anchorId: String?,
): List<String> {
    if (displayed.isEmpty() || displayed.indexOf(draggedId) < 0) return order
    // The anchor is a row of the *displayed* list, which is a subsequence of the stored one, so the
    // same id is valid for the stored list as well - which is exactly why the move is done by id
    // and never by counting screen positions.
    if (anchorId != null && displayed.indexOf(anchorId) < 0) return order
    return moveFavoriteBefore(order, draggedId, anchorId)
}

/** Moves [draggedId] directly in front of [anchorId] (null = to the end). */
fun moveFavoriteBefore(order: List<String>, draggedId: String, anchorId: String?): List<String> {
    val from = order.indexOf(draggedId)
    if (from < 0) return order
    val rest = order.toMutableList().also { it.removeAt(from) }
    val at = if (anchorId == null) rest.size else rest.indexOf(anchorId).let { if (it < 0) return order else it }
    return rest.toMutableList().also { it.add(at, draggedId) }
}

data class PrefsData(val entries: Map<String, String> = emptyMap()) {

    // ------------------------------------------------------------------ favourites (ordered)

    /**
     * Favourites in the user's own order. Migrates the legacy unordered `StringSet` the first time
     * it is read, so an existing user keeps their favourites (ordered by the registry).
     */
    fun favorites(registryOrder: List<String> = emptyList()): List<String> {
        entries[PrefsKeys.FAVORITES_ORDER]?.let { stored ->
            return stored.split(SEPARATOR).filter { it.isNotBlank() }
        }
        val legacy = entries[PrefsKeys.FAVORITES_LEGACY]
        if (legacy.isNullOrBlank()) return emptyList()
        val ids = legacy.split(SEPARATOR).filter { it.isNotBlank() }
        return if (registryOrder.isEmpty()) ids else registryOrder.filter { it in ids }
    }

    fun withFavorites(ids: List<String>): PrefsData = copy(
        entries = entries - PrefsKeys.FAVORITES_LEGACY + (PrefsKeys.FAVORITES_ORDER to ids.joinToString(SEPARATOR)),
    )

    /** Adds or removes a favourite, keeping the existing order. */
    fun toggleFavorite(id: String, registryOrder: List<String> = emptyList()): PrefsData {
        val current = favorites(registryOrder)
        val next = if (id in current) current - id else current + id
        return withFavorites(next)
    }

    /**
     * Moves the favourite at [from] to [to], as a drag-and-drop reorder would. Out-of-range
     * indices are clamped instead of throwing, because a drag can end past the last row.
     */
    fun moveFavorite(from: Int, to: Int, registryOrder: List<String> = emptyList()): PrefsData {
        val current = favorites(registryOrder).toMutableList()
        if (from !in current.indices) return this
        val target = to.coerceIn(0, current.size - 1)
        if (from == target) return this
        val moved = current.removeAt(from)
        current.add(target, moved)
        return withFavorites(current)
    }

    /**
     * Moves [draggedId] so that it lands exactly where the drag released it, described with ids
     * instead of screen positions.
     *
     * The favourites list on screen is a *subsequence* of the stored order (a favourite whose tool
     * no longer exists is skipped, and a search hides rows), so an index counted on screen is not an
     * index in the stored list. [anchorId] is the id of the row the dragged one must end up in front
     * of, taken from the list as it is displayed - null means "at the end". An id that is not stored
     * any more moves nothing rather than moving an unrelated entry.
     */
    fun moveFavoriteBefore(
        draggedId: String,
        anchorId: String?,
        registryOrder: List<String> = emptyList(),
    ): PrefsData {
        val current = favorites(registryOrder)
        val next = moveFavoriteBefore(current, draggedId, anchorId)
        return if (next == current) this else withFavorites(next)
    }

    // ------------------------------------------------------------------ recents

    fun recents(): List<String> = (entries[PrefsKeys.RECENTS] ?: "").split(SEPARATOR).filter { it.isNotBlank() }

    fun withRecent(id: String): PrefsData = copy(
        entries = entries + (PrefsKeys.RECENTS to (listOf(id) + recents()).distinct().take(RECENT_LIMIT).joinToString(SEPARATOR)),
    )

    // ------------------------------------------------------------------ tool parameters

    fun paramsFor(toolId: String): Map<String, String> {
        val raw = entries["${PrefsKeys.PARAMS_PREFIX}$toolId"] ?: return emptyMap()
        return raw.split(";").mapNotNull { pair ->
            val cut = pair.indexOf('=')
            if (cut <= 0) null else pair.substring(0, cut) to pair.substring(cut + 1)
        }.toMap()
    }

    /**
     * Stores the parameters of a tool. Sensitive values (passwords, keys) are dropped by the
     * caller - see `ToolParamFilter.persistable` - so nothing secret can ever reach disk.
     */
    fun withParams(toolId: String, params: Map<String, String>): PrefsData {
        if (params.isEmpty()) return this
        val encoded = params.toSortedMap().entries.joinToString(";") { "${it.key}=${it.value}" }
        return copy(entries = entries + ("${PrefsKeys.PARAMS_PREFIX}$toolId" to encoded))
    }

    // ------------------------------------------------------------------ temporary data

    /** Keys that "Clear temporary data" is allowed to remove. */
    fun temporaryEntries(): List<PrefsEntry> = entries
        .filterKeys { it.startsWith(PrefsKeys.PARAMS_PREFIX) || it == PrefsKeys.RECENTS }
        .map { PrefsEntry(it.key, it.value) }
        .sortedBy { it.key }

    /** Bytes of temporary data currently stored (UTF-16 units of key + value, as prefs store them). */
    fun temporaryBytes(): Long = temporaryEntries().sumOf { it.bytes }

    /**
     * Removes genuinely temporary data: remembered tool parameters and the recent-tool list.
     *
     * Deliberately **kept**: favourites (the user curated them by hand), the chosen tool, and the
     * appearance settings. There is no way to remove a favourite here other than tapping its star.
     */
    fun clearTemporary(): PrefsData = copy(
        entries = entries.filterKeys { key ->
            !(key.startsWith(PrefsKeys.PARAMS_PREFIX) || key == PrefsKeys.RECENTS)
        },
    )

    // ------------------------------------------------------------------ small typed accessors

    fun string(key: String): String? = entries[key]
    fun bool(key: String, default: Boolean): Boolean = entries[key]?.toBooleanStrictOrNull() ?: default
    fun with(key: String, value: String?): PrefsData =
        if (value == null) copy(entries = entries - key) else copy(entries = entries + (key to value))
}

/** Human-readable size, in the units the settings screen shows. */
fun formatDataSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}
