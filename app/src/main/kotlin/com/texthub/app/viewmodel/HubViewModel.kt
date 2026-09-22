package com.texthub.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.texthub.app.prefs.AppPreferences
import com.texthub.app.ui.theme.AccentOption
import com.texthub.app.ui.theme.AppTheme
import com.texthub.core.ProcessingEngine
import com.texthub.core.TextProcessor
import com.texthub.core.ToolRegistry
import com.texthub.core.defaultParams
import com.texthub.core.model.Direction
import com.texthub.core.model.ParamIssue
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.validateParams
import com.texthub.core.model.TextStats
import com.texthub.core.model.ToolMeta
import com.texthub.core.model.computeStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HubUiState(
    val toolId: String = "base64",
    val direction: Direction = Direction.ENCODE,
    val input: String = "",
    val output: String = "",
    val error: String? = null,
    val params: Map<String, String> = emptyMap(),
    /** Favourites in the user's own order (drag and drop reorders this list). */
    val favorites: List<String> = emptyList(),
    val recents: List<String> = emptyList(),
    /** Human-readable size of the disposable data the app is holding. */
    val temporaryDataSize: String = "0 B",
    /** Parameter problems, so the UI can mark the offending field instead of showing a banner. */
    val paramIssues: List<ParamIssue> = emptyList(),
    val autoProcess: Boolean = true,
    val copyConfirmation: Boolean = true,
    val theme: AppTheme = AppTheme.SYSTEM,
    val accent: AccentOption = AccentOption.TEAL,
    val processing: Boolean = false,
    val inputStats: TextStats = TextStats(0, 0, 0),
    val outputStats: TextStats = TextStats(0, 0, 0),
) {
    val meta: ToolMeta get() = ToolRegistry.metaOf(toolId)
    val hasOutput: Boolean get() = output.isNotEmpty() || error != null

    /** Swapping is offered only when the tool has a reverse direction and there is a result. */
    val canSwap: Boolean get() = meta.supportsSwap && output.isNotEmpty() && error == null

    /** The direction switch is hidden for symmetric and one-way tools: they have nothing to switch. */
    val showDirection: Boolean get() = meta.hasDirectionChoice

    val showSwap: Boolean get() = meta.supportsSwap

    /**
     * The mode the swap button would flip to, or null when the tool has no direction to flip (a
     * symmetric cipher such as ROT13). The text itself is built in the UI from string resources, so
     * every label stays translatable.
     */
    val swapTarget: String?
        get() = if (!meta.hasDirectionChoice) {
            null
        } else {
            if (direction == Direction.ENCODE) meta.decodeLabel else meta.encodeLabel
        }

    /**
     * The label of the single action button, taken from the tool itself: the operation it performs
     * in the current direction. No tool shows a generic "Process" button, and a tool with one
     * operation never shows two different labels for it.
     */
    val actionLabel: String
        get() = if (meta.hasDirectionChoice) {
            if (direction == Direction.ENCODE) meta.encodeLabel else meta.decodeLabel
        } else {
            meta.encodeLabel
        }

    /** True when the tool has parameters the user can adjust. */
    val hasParams: Boolean get() = meta.params.isNotEmpty()

    /** True when there are advanced settings worth collapsing. */
    val hasAdvancedParams: Boolean get() = meta.advancedParams.isNotEmpty()

    /** True when every parameter value is usable. */
    val paramsValid: Boolean get() = paramIssues.isEmpty()

    /** The inline message for one parameter, or null when it is fine. */
    fun issueFor(key: String): String? = paramIssues.firstOrNull { it.key == key }?.message
}

/**
 * Pure state transition for the Swap action, kept separate so it can be unit tested:
 *
 *  1. the result becomes the new input,
 *  2. the mode flips (Encode <-> Decode, Encrypt <-> Decrypt),
 *  3. the previous input is shown until the re-processed result arrives.
 */
fun applySwap(state: HubUiState): HubUiState {
    val nextInput = state.output
    return state.copy(
        input = nextInput,
        inputStats = computeStats(nextInput),
        output = state.input,
        outputStats = computeStats(state.input),
        error = null,
        direction = if (state.direction == Direction.ENCODE) Direction.DECODE else Direction.ENCODE,
    )
}

/**
 * Owns the whole main screen. Text, keys and passwords live only in memory for as long as
 * the process exists; nothing sensitive is ever persisted or logged.
 */
class HubViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)

    private var statsJob: Job? = null

    private val _uiState = MutableStateFlow(
        HubUiState(
            theme = prefs.theme,
            accent = prefs.accent,
            autoProcess = prefs.autoProcess,
            copyConfirmation = prefs.copyConfirmation,
            favorites = prefs.favorites(),
            recents = prefs.recents(),
            temporaryDataSize = prefs.temporaryDataSize(),
        )
    )
    val uiState: StateFlow<HubUiState> = _uiState.asStateFlow()

    private var processJob: Job? = null

    init {
        val saved = prefs.lastTool
        val startTool = if (saved != null && ToolRegistry.all.any { it.meta.id == saved }) saved else "base64"
        selectTool(startTool, rememberRecent = false)
    }

    // ------------------------------------------------------------- tool + direction

    fun selectTool(toolId: String, rememberRecent: Boolean = true) {
        val processor = ToolRegistry.get(toolId)
        val defaults = processor.defaultParams()
        // Restore what was remembered, minus anything that no longer fits this tool: a sensitive
        // value is never restored, and a value that the current tool would reject (a choice that was
        // removed, a number now out of range) is dropped instead of being handed to the user as a
        // broken setting. The defaults fill in whatever is left.
        val stored = prefs.paramsFor(toolId)
        val saved = stored.filter { (key, value) ->
            val spec = processor.meta.params.firstOrNull { it.key == key }
            spec != null && !spec.sensitive && value.isNotEmpty() &&
                processor.meta.validateParams(mapOf(key to value)).none { it.key == key }
        }
        val merged = defaults + saved
        _uiState.update {
            it.copy(
                toolId = toolId,
                params = merged,
                output = "",
                error = null,
                outputStats = TextStats(0, 0, 0),
                direction = Direction.ENCODE,
            ).withValidatedParams()
        }
        prefs.lastTool = toolId
        if (rememberRecent) {
            val updated = prefs.rememberRecent(toolId)
            _uiState.update { it.copy(recents = updated) }
        }
        process(immediate = true)
    }

    fun setDirection(direction: Direction) {
        _uiState.update { it.copy(direction = direction, error = null) }
        process(immediate = true)
    }

    fun flipDirection() {
        setDirection(if (_uiState.value.direction == Direction.ENCODE) Direction.DECODE else Direction.ENCODE)
    }

    fun toggleFavorite(toolId: String) {
        val next = prefs.toggleFavorite(toolId)
        _uiState.update { it.copy(favorites = next) }
    }

    /** Drag-and-drop reorder of the favourites list; the new order is stored immediately. */
    fun moveFavorite(from: Int, to: Int) {
        if (from == to) return
        val next = prefs.moveFavorite(from, to)
        _uiState.update { it.copy(favorites = next) }
    }

    // ------------------------------------------------------------------- text + params

    fun setInput(text: String) {
        if (text.length <= STATS_ON_MAIN_THREAD_LIMIT) {
            // Short text: counting is free, so the handy character/word/line line stays instant.
            _uiState.update { it.copy(input = text, inputStats = computeStats(text)) }
        } else {
            // Large paste: count off the main thread, so typing never waits for a scan of the text.
            _uiState.update { it.copy(input = text) }
            statsJob?.cancel()
            statsJob = viewModelScope.launch {
                val stats = withContext(Dispatchers.Default) { computeStats(text) }
                _uiState.update { it.copy(inputStats = stats) }
            }
        }
        if (_uiState.value.autoProcess) process()
    }

    fun setParam(key: String, value: String) {
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
        copy(paramIssues = meta.validateParams(params))

    private fun persistParams() {
        val state = _uiState.value
        val sensitive = state.meta.params.filter { it.sensitive }.map { it.key }.toSet()
        prefs.saveParams(state.toolId, state.params.filterKeys { it !in sensitive })
    }

    fun swap() {
        if (!_uiState.value.canSwap) return
        _uiState.update { applySwap(it) }
        // Re-process immediately in the new direction so the swap both moves the text and
        // decodes/encodes it, instead of leaving the old result on screen.
        process(immediate = true)
    }

    fun clearInput() {
        _uiState.update { it.copy(input = "", inputStats = TextStats(0, 0, 0), output = "", error = null, outputStats = TextStats(0, 0, 0)) }
    }

    fun clearOutput() {
        _uiState.update { it.copy(output = "", error = null, outputStats = TextStats(0, 0, 0)) }
    }

    // --------------------------------------------------------------------- processing

    /** Debounced when triggered by typing, immediate for button/direction/param changes. */
    fun process(immediate: Boolean = false) {
        processJob?.cancel()
        processJob = viewModelScope.launch {
            if (!immediate) delay(DEBOUNCE_MS)
            runProcessor()
        }
    }

    private suspend fun runProcessor() {
        val state = _uiState.value
        val processor: TextProcessor = ToolRegistry.get(state.toolId)
        val input = state.input
        val params = state.params
        val direction = state.direction

        // Tools such as the RSA key generator work without input text, so an empty input box is
        // only a reason to stop for the tools that actually need text.
        if (input.isEmpty() && !processor.meta.inputOptional) {
            _uiState.update {
                it.copy(
                    output = "",
                    error = null,
                    inputStats = TextStats(0, 0, 0),
                    outputStats = TextStats(0, 0, 0),
                    processing = false,
                )
            }
            return
        }

        val heavy = input.length > LARGE_INPUT_THRESHOLD
        if (heavy) _uiState.update { it.copy(processing = true) }

        // Never touch the main thread with real work - including the word/line count of a large
        // result, which used to be measured on the main thread after the work came back.
        val outcome = withContext(Dispatchers.Default) {
            val result = ProcessingEngine.run(processor, input, params, direction)
            result to computeStats(result.output)
        }

        _uiState.update {
            it.copy(
                output = outcome.first.output,
                error = outcome.first.error,
                outputStats = outcome.second,
                processing = false,
            )
        }
    }

    // ------------------------------------------------------------------------ settings

    fun setTheme(theme: AppTheme) {
        _uiState.update { it.copy(theme = theme) }
        prefs.theme = theme
    }

    fun setAccent(accent: AccentOption) {
        _uiState.update { it.copy(accent = accent) }
        prefs.accent = accent
    }

    fun setAutoProcess(enabled: Boolean) {
        _uiState.update { it.copy(autoProcess = enabled) }
        prefs.autoProcess = enabled
        if (enabled) process(immediate = true)
    }

    fun setCopyConfirmation(enabled: Boolean) {
        _uiState.update { it.copy(copyConfirmation = enabled) }
        prefs.copyConfirmation = enabled
    }

    /**
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
    }

    /** Validation hint for the current parameter values (used by the info sheet). */
    fun isSatisfied(spec: ParamSpec): Boolean {
        val value = _uiState.value.params[spec.key] ?: return false
        return value.isNotBlank()
    }

    companion object {
        private const val DEBOUNCE_MS = 140L
        private const val LARGE_INPUT_THRESHOLD = 60_000

        /** Beyond this many characters the statistics line is counted off the main thread. */
        private const val STATS_ON_MAIN_THREAD_LIMIT = 20_000
    }
}
