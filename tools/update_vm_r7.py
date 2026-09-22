import io, re

p = "app/src/main/kotlin/com/texthub/app/viewmodel/HubViewModel.kt"
s = io.open(p, encoding="utf-8").read()

# ---------------- state: ordered favourites, temp size, validation issues ----------------
old = """    val params: Map<String, String> = emptyMap(),
    val favorites: Set<String> = emptySet(),
    val recents: List<String> = emptyList(),"""
new = """    val params: Map<String, String> = emptyMap(),
    /** Favourites in the user's own order (drag and drop reorders this list). */
    val favorites: List<String> = emptyList(),
    val recents: List<String> = emptyList(),
    /** Human-readable size of the disposable data the app is holding. */
    val temporaryDataSize: String = "0 B",
    /** Parameter problems, so the UI can mark the offending field instead of showing a banner. */
    val paramIssues: List<ParamIssue> = emptyList(),"""
assert old in s
s = s.replace(old, new)

old = """    /** Swapping is only offered when there is a result to move into the input. */
    val canSwap: Boolean get() = output.isNotEmpty() && error == null

    /** Label for the swap button: it always names the mode the text is about to be processed in. */
    val swapLabel: String
        get() {
            val target = if (direction == Direction.ENCODE) meta.decodeLabel else meta.encodeLabel
            return "Swap & " + target
        }
}"""
new = """    /** Swapping is offered only when the tool has a reverse direction and there is a result. */
    val canSwap: Boolean get() = meta.supportsSwap && output.isNotEmpty() && error == null

    /** The direction switch is hidden for symmetric and one-way tools: they have nothing to switch. */
    val showDirection: Boolean get() = meta.hasDirectionChoice

    val showSwap: Boolean get() = meta.supportsSwap

    /**
     * Label for the swap button. It names the mode the text is about to be processed in, or simply
     * says what happens when the tool has no direction to flip (a symmetric cipher).
     */
    val swapLabel: String
        get() {
            if (!meta.hasDirectionChoice) return "Use result as input"
            val target = if (direction == Direction.ENCODE) meta.decodeLabel else meta.encodeLabel
            return "Swap & " + target
        }

    /** True when the tool has parameters the user can adjust. */
    val hasParams: Boolean get() = meta.params.isNotEmpty()

    /** True when there are advanced settings worth collapsing. */
    val hasAdvancedParams: Boolean get() = meta.advancedParams.isNotEmpty()

    /** True when every parameter value is usable. */
    val paramsValid: Boolean get() = paramIssues.isEmpty()

    /** The inline message for one parameter, or null when it is fine. */
    fun issueFor(key: String): String? = paramIssues.firstOrNull { it.key == key }?.message
}"""
assert old in s
s = s.replace(old, new)

old = """            favorites = prefs.favorites,
            recents = prefs.recents,
        )"""
new = """            favorites = prefs.favorites(),
            recents = prefs.recents(),
            temporaryDataSize = prefs.temporaryDataSize(),
        )"""
assert old in s
s = s.replace(old, new)

s = s.replace("import com.texthub.core.model.ParamSpec",
              "import com.texthub.core.model.ParamIssue\nimport com.texthub.core.model.ParamSpec\nimport com.texthub.core.model.validateParams")

# ---------------- favourites ------------------------------------------------------------
old = """    fun toggleFavorite(toolId: String) {
        val next = _uiState.value.favorites.toMutableSet()
        if (!next.add(toolId)) next.remove(toolId)
        _uiState.update { it.copy(favorites = next) }
        prefs.favorites = next
    }"""
new = """    fun toggleFavorite(toolId: String) {
        val next = prefs.toggleFavorite(toolId)
        _uiState.update { it.copy(favorites = next) }
    }

    /** Drag-and-drop reorder of the favourites list; the new order is stored immediately. */
    fun moveFavorite(from: Int, to: Int) {
        if (from == to) return
        val next = prefs.moveFavorite(from, to)
        _uiState.update { it.copy(favorites = next) }
    }"""
assert old in s
s = s.replace(old, new)

# ---------------- recents + params ------------------------------------------------------
old = """        if (rememberRecent) {
            val updated = (listOf(toolId) + _uiState.value.recents).distinct().take(6)
            _uiState.update { it.copy(recents = updated) }
            prefs.recents = updated
        }"""
new = """        if (rememberRecent) {
            val updated = prefs.rememberRecent(toolId)
            _uiState.update { it.copy(recents = updated) }
        }"""
assert old in s
s = s.replace(old, new)

old = """    fun setParam(key: String, value: String) {
        _uiState.update { it.copy(params = it.params + (key to value)) }
        persistParams()
        if (_uiState.value.autoProcess) process()
    }"""
new = """    fun setParam(key: String, value: String) {
        _uiState.update { it.copy(params = it.params + (key to value)).withValidatedParams() }
        persistParams()
        if (_uiState.value.autoProcess) process()
    }

    /** Restores every parameter of the current tool to its default value. */
    fun resetParams() {
        val processor = ToolRegistry.get(_uiState.value.toolId)
        _uiState.update { it.copy(params = processor.defaultParams()).withValidatedParams() }
        // The stored copy is dropped too, so a reset is not undone by the next launch.
        prefs.saveParams(_uiState.value.toolId, emptyMap())
        process(immediate = true)
    }

    /** Recomputes the inline parameter warnings for the current values. */
    private fun HubUiState.withValidatedParams(): HubUiState =
        copy(paramIssues = meta.validateParams(params))"""
assert old in s
s = s.replace(old, new)

# ---------------- clear temporary data --------------------------------------------------
old = """    fun clearTemporaryData() {
        prefs.clearTemporaryData()
        _uiState.update {
            it.copy(
                favorites = emptySet(),
                recents = emptyList(),
                params = ToolRegistry.get(it.toolId).defaultParams(),
            )
        }
        process(immediate = true)
    }"""
new = """    /**
     * Clears disposable data only: remembered tool parameters and the recent-tool list.
     *
     * Favourites are deliberately kept - they are the user's own curated list and can only be
     * removed by tapping their star - and so are the appearance settings and the selected tool.
     * The displayed size is refreshed in the same step, so the number can never be stale.
     */
    fun clearTemporaryData() {
        prefs.clearTemporaryData()
        _uiState.update {
            it.copy(
                recents = emptyList(),
                params = ToolRegistry.get(it.toolId).defaultParams(),
                temporaryDataSize = prefs.temporaryDataSize(),
            ).withValidatedParams()
        }
        process(immediate = true)
    }"""
assert old in s
s = s.replace(old, new)

io.open(p, "w", encoding="utf-8").write(s)
print("HubViewModel updated")
