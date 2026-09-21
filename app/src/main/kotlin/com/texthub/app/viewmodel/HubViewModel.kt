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
import com.texthub.core.model.ParamSpec
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
    val favorites: Set<String> = emptySet(),
    val recents: List<String> = emptyList(),
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

    /** Swapping is only offered when there is a result to move into the input. */
    val canSwap: Boolean get() = output.isNotEmpty() && error == null

    /** Label for the swap button: it always names the mode the text is about to be processed in. */
    val swapLabel: String
        get() {
            val target = if (direction == Direction.ENCODE) meta.decodeLabel else meta.encodeLabel
            return "Swap & " + target
        }
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

    private val _uiState = MutableStateFlow(
        HubUiState(
            theme = prefs.theme,
            accent = prefs.accent,
            autoProcess = prefs.autoProcess,
            copyConfirmation = prefs.copyConfirmation,
            favorites = prefs.favorites,
            recents = prefs.recents,
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
        val saved = prefs.paramsFor(toolId).filterKeys { key ->
            // Never restore a sensitive value from disk.
            processor.meta.params.any { it.key == key && !it.sensitive }
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
            )
        }
        prefs.lastTool = toolId
        if (rememberRecent) {
            val updated = (listOf(toolId) + _uiState.value.recents).distinct().take(6)
            _uiState.update { it.copy(recents = updated) }
            prefs.recents = updated
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
        val next = _uiState.value.favorites.toMutableSet()
        if (!next.add(toolId)) next.remove(toolId)
        _uiState.update { it.copy(favorites = next) }
        prefs.favorites = next
    }

    // ------------------------------------------------------------------- text + params

    fun setInput(text: String) {
        _uiState.update { it.copy(input = text, inputStats = computeStats(text)) }
        if (_uiState.value.autoProcess) process()
    }

    fun setParam(key: String, value: String) {
        _uiState.update { it.copy(params = it.params + (key to value)) }
        persistParams()
        if (_uiState.value.autoProcess) process()
    }

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
            _uiState.update { it.copy(output = "", error = null, outputStats = TextStats(0, 0, 0), processing = false) }
            return
        }

        val heavy = input.length > LARGE_INPUT_THRESHOLD
        if (heavy) _uiState.update { it.copy(processing = true) }

        // Never touch the main thread with real work.
        val outcome = withContext(Dispatchers.Default) {
            ProcessingEngine.run(processor, input, params, direction)
        }

        _uiState.update {
            it.copy(
                output = outcome.output,
                error = outcome.error,
                outputStats = computeStats(outcome.output),
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

    fun clearTemporaryData() {
        prefs.clearTemporaryData()
        _uiState.update {
            it.copy(
                favorites = emptySet(),
                recents = emptyList(),
                params = ToolRegistry.get(it.toolId).defaultParams(),
            )
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
    }
}
