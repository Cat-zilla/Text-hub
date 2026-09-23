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
import com.texthub.core.crypto.RsaKeyGen
import com.texthub.core.detector.Diagnosis
import com.texthub.core.detector.UniversalDecoder
import com.texthub.core.keys.RsaKeyCollection
import com.texthub.core.keys.RsaKeySession
import com.texthub.core.keys.SaveResult
import com.texthub.core.keys.SavedRsaKeyMeta
import com.texthub.app.keys.AndroidRsaKeyVault
import com.texthub.core.model.Direction
import com.texthub.core.model.Errors
import com.texthub.core.model.ParamIssue
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.validateParams
import com.texthub.core.model.TextStats
import com.texthub.core.model.ToolMeta
import com.texthub.core.model.computeStats
import com.texthub.core.processors.RsaKeyGenProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The outcome of one vault operation, for the UI to show once and then acknowledge. */
sealed class RsaVaultEvent {
    data class Saved(val name: String, val replaced: Boolean) : RsaVaultEvent()
    data class NameConflict(val name: String) : RsaVaultEvent()
    data class SameKeyExists(val name: String) : RsaVaultEvent()
    data class LoadFailed(val name: String) : RsaVaultEvent()
    data class Failed(val message: String) : RsaVaultEvent()
}

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
    /** Light haptic response for copy, favourites and the drag; off means completely silent. */
    val haptics: Boolean = true,
    val theme: AppTheme = AppTheme.SYSTEM,
    val accent: AccentOption = AccentOption.TEAL,
    val processing: Boolean = false,
    val inputStats: TextStats = TextStats(0, 0, 0),
    val outputStats: TextStats = TextStats(0, 0, 0),
    /**
     * The structured result of the Universal Decoder (detected formats, confidence, the chain of
     * layers and what is missing). Null for every other tool, which is also what the analysis card
     * keys off. It is derived from the input in memory only - never stored.
     */
    val analysis: Diagnosis? = null,
    /**
     * The active RSA key pair: the one pair the user is working with. It is deliberately
     * independent of the per-tool output box - which is cleared when another tool is selected -
     * so switching tools, changing settings and recomposition never lose it. It lives in memory
     * only: a saved pair survives restarts because it is saved, an unsaved pair does not outlive
     * the process, because an unsaved private key is never written anywhere.
     */
    val rsaActive: RsaKeyGen.Generated? = null,
    /** The saved record the active pair came from, or null while it is unsaved. */
    val rsaSavedName: String? = null,
    /** The saved collection's listing metadata (never any private material), newest first. */
    val rsaSavedKeys: List<SavedRsaKeyMeta> = emptyList(),
    /** One completed save/load/delete event for the UI to acknowledge (dialogs, snackbars). */
    val rsaEvent: RsaVaultEvent? = null,
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

    /** True for the Universal Decoder, the one tool that shows an analysis card. */
    val isUniversalDecoder: Boolean get() = toolId == UniversalDecoder.TOOL_ID

    /**
     * True for the RSA key pair generator, whose result is presented in dedicated Public key /
     * Private key / Key information sections instead of the generic output box.
     */
    val isRsaKeyGen: Boolean get() = toolId == RsaKeyGenProcessor.TOOL_ID


    /**
     * True for a tool that may only run through its own explicit action. The app hides every
     * automatic processing path for it; see [ToolMeta.explicitActionOnly] for why.
     */
    val isExplicitActionTool: Boolean get() = meta.explicitActionOnly

    /** The tool the user forced by hand, or null while detection decides. */
    val forcedToolId: String?
        get() = params[UniversalDecoder.PARAM_PREFER]?.trim()?.takeIf { it.isNotEmpty() }

    /** True when there is a decoded result to analyse once more. */
    val canAnalyseAgain: Boolean get() = isUniversalDecoder && output.isNotEmpty() && error == null
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
            haptics = prefs.haptics,
            favorites = prefs.favorites(),
            recents = prefs.recents(),
            temporaryDataSize = prefs.temporaryDataSize(),
        )
    )
    val uiState: StateFlow<HubUiState> = _uiState.asStateFlow()

    private var processJob: Job? = null

    /**
     * The saved-key repository (Android Keystore cipher + private file) and the active pair's
     * session state. Both are independent of the per-tool output: the active pair survives tool
     * switches, and the saved collection survives restarts.
     */
    private val keyCollection: RsaKeyCollection =
        RsaKeyCollection(AndroidRsaKeyVault.FileStorage(application), AndroidRsaKeyVault.KeystoreCipher())

    private var keySession = RsaKeySession()

    private fun applySession(session: RsaKeySession) {
        keySession = session
        _uiState.update {
            it.copy(rsaActive = session.active, rsaSavedName = session.savedName)
        }
    }

    /** Reads the saved collection's listing metadata; no private key is decrypted for this. */
    private fun refreshSavedKeys() {
        viewModelScope.launch {
            val metas = withContext(Dispatchers.Default) { keyCollection.keys() }
            _uiState.update { it.copy(rsaSavedKeys = metas) }
        }
    }

    init {
        // The saved-key list is read once at start-up; opening a private key stays an explicit
        // "Load" action of the user. Nothing is generated here.
        refreshSavedKeys()
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
            spec != null && !spec.sensitive && !spec.sessionOnly && value.isNotEmpty() &&
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
                analysis = null,
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

    /**
     * Completes a drag-and-drop reorder of the favourites list; the new order is stored immediately
     * and is exactly the order the picker is showing afterwards.
     *
     * @param draggedId the row that was dragged, [anchorId] the row it is dropped in front of
     *   (null = the end of the list).
     */
    fun moveFavorite(draggedId: String, anchorId: String?) {
        val next = prefs.moveFavoriteBefore(draggedId, anchorId)
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
        // What may be remembered is a property of the parameter, declared with the rest of the tool's
        // metadata: a secret is never written, and neither is a value that only belongs to this
        // session (the Universal Decoder's manual override, which would otherwise force the same tool
        // on every analysis - and on every later launch).
        prefs.saveParams(state.toolId, state.meta.rememberedParams(state.params))
    }

    fun swap() {
        if (!_uiState.value.canSwap) return
        _uiState.update { applySwap(it) }
        // Re-process immediately in the new direction so the swap both moves the text and
        // decodes/encodes it, instead of leaving the old result on screen.
        process(immediate = true)
    }

    fun clearInput() {
        _uiState.update {
            it.copy(
                input = "",
                inputStats = TextStats(0, 0, 0),
                output = "",
                error = null,
                outputStats = TextStats(0, 0, 0),
                // Nothing is left to describe once the input is gone.
                analysis = null,
            )
        }
    }

    fun clearOutput() {
        _uiState.update { it.copy(output = "", error = null, outputStats = TextStats(0, 0, 0)) }
    }

    // --------------------------------------------------------------------- processing

    /** Debounced when triggered by typing, immediate for button/direction/param changes. */
    fun process(immediate: Boolean = false) {
        // A key generator runs only when its own action is pressed: selecting the tool, changing
        // the key size, restoring defaults - nothing but that action may create a key pair.
        if (!_uiState.value.meta.shouldRunAutomatically) return
        launchProcess(immediate)
    }

    /**
     * The explicit action of the RSA key pair generator: the only way a key pair is ever created.
     * The new pair becomes the **active** pair - it replaces the one on screen (the section hint
     * says so) and never touches the saved collection. The active pair then survives tool
     * switches, settings changes and recomposition, because it is session state of its own and
     * not the per-tool output box. One generation at a time; a restart loses an unsaved pair
     * on purpose - an unsaved private key is never persisted.
     */
    fun generateKeyPair() {
        val state = _uiState.value
        if (state.processing) return
        val bits = state.params["size"]?.toIntOrNull() ?: 2048
        _uiState.update { it.copy(processing = true) }
        viewModelScope.launch {
            try {
                val pair = withContext(Dispatchers.Default) { RsaKeyGen.generatePair(bits) }
                applySession(keySession.generate(pair))
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // The same recoverable-failure principle as everywhere else: the generator's own
                // ToolExceptions arrive with their own wording, anything unforeseen is degraded to
                // the friendly message. Key material never appears in either.
                _uiState.update {
                    it.copy(rsaEvent = RsaVaultEvent.Failed(Errors.rsaKey().message!!))
                }
            } finally {
                _uiState.update { it.copy(processing = false) }
            }
        }
    }

    /**
     * Saves the active pair under [name]. The UI asks for a replacement first when the name is
     * taken, so this is called with [replace] only after an explicit confirmation.
     */
    fun saveActiveRsaKey(name: String, replace: Boolean = false) {
        val pair = keySession.active ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                keyCollection.save(name, pair, createdAt = System.currentTimeMillis(), replace = replace)
            }
            when (result) {
                is SaveResult.Saved -> {
                    applySession(keySession.savedAs(result.name))
                    refreshSavedKeys()
                    _uiState.update {
                        it.copy(rsaEvent = RsaVaultEvent.Saved(result.name, result.replaced))
                    }
                }
                is SaveResult.NameConflict ->
                    _uiState.update { it.copy(rsaEvent = RsaVaultEvent.NameConflict(result.name)) }
                is SaveResult.SameKeyExists ->
                    _uiState.update { it.copy(rsaEvent = RsaVaultEvent.SameKeyExists(result.name)) }
                is SaveResult.InvalidName ->
                    _uiState.update { it.copy(rsaEvent = RsaVaultEvent.NameConflict("")) }
            }
        }
    }

    /**
     * Makes the named saved pair the active one. Only this call decrypts that one pair. No key is
     * generated, and the saved collection is unchanged.
     */
    fun loadSavedRsaKey(name: String) {
        viewModelScope.launch {
            val pair = withContext(Dispatchers.Default) { keyCollection.load(name) }
            if (pair == null) {
                _uiState.update { it.copy(rsaEvent = RsaVaultEvent.LoadFailed(name)) }
            } else {
                applySession(keySession.loaded(pair, name))
            }
        }
    }

    /**
     * Deletes one saved record - only that one. If the deleted record is the source of the active
     * pair, the pair stays on screen but becomes unsaved: its persisted half no longer exists,
     * and the state says so honestly.
     */
    fun deleteSavedRsaKey(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.Default) { keyCollection.delete(name) }
            applySession(keySession.savedRecordDeleted(name))
            refreshSavedKeys()
        }
    }

    /** Removes the active pair (after the UI's confirmation). Saved pairs are not touched. */
    fun clearActiveRsaKey() {
        applySession(keySession.clear())
    }

    /** The UI has shown [event]; forget it so it is not shown twice. */
    fun consumeRsaEvent() {
        _uiState.update { it.copy(rsaEvent = null) }
    }

    private fun launchProcess(immediate: Boolean) {
        processJob?.cancel()
        processJob = viewModelScope.launch {
            if (!immediate) delay(DEBOUNCE_MS)
            try {
                runProcessor()
            } catch (e: CancellationException) {
                // A newer run replaced this one (the user kept typing): normal, not an error.
                throw e
            } catch (t: Throwable) {
                // Last ring of the error boundary, not the first: the processing engine already
                // converts processor failures into friendly messages, and the analysis is guarded
                // where it runs. This catches anything that could still escape - so a defect can
                // degrade one result into an error message, never take the app down with it.
                _uiState.update {
                    it.copy(
                        output = "",
                        error = Errors.unexpectedFailure().message,
                        analysis = null,
                        processing = false,
                    )
                }
            }
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
                    analysis = null,
                    processing = false,
                )
            }
            return
        }

        val heavy = input.length > LARGE_INPUT_THRESHOLD || processor.meta.explicitActionOnly
        if (heavy) _uiState.update { it.copy(processing = true) }

        // Never touch the main thread with real work - including the word/line count of a large
        // result, which used to be measured on the main thread after the work came back.
        val outcome = withContext(Dispatchers.Default) {
            val result = ProcessingEngine.run(processor, input, params, direction)
            // The Universal Decoder reports what it found as text; the same analysis is kept in a
            // structured form here so the card can show the confidence, the reason and the chain.
            // Both come from one call, so the card can never describe something else than the
            // result next to it. A diagnosis that fails for an unforeseen reason is simply not
            // shown - the tool's own output above carries the friendly error.
            val analysis = if (processor.meta.id == UniversalDecoder.TOOL_ID) {
                analyzeSafely(input, params)
            } else {
                null
            }
            Triple(result, computeStats(result.output), analysis)
        }

        _uiState.update {
            it.copy(
                output = outcome.first.output,
                error = outcome.first.error,
                outputStats = outcome.second,
                analysis = outcome.third,
                processing = false,
            )
        }
    }

    /**
     * The structured Universal Decoder result, guarded: an analysis that fails for an unforeseen
     * reason yields null (the analysis card is hidden) instead of an exception. Cancellation is
     * never treated as a failure - it means a newer run replaced this one.
     */
    private suspend fun analyzeSafely(input: String, params: Map<String, String>): Diagnosis? =
        try {
            UniversalDecoder.analyze(input, params)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
        }

    /**
     * Analyses the current result as if it had just been pasted: the decoded text becomes the input
     * and is analysed again. Nothing is decrypted a second time behind the user's back - this is the
     * "analyse the result again" step of a nested payload.
     */
    fun analyseResultAgain() {
        val state = _uiState.value
        if (!state.canAnalyseAgain) return
        val nextInput = state.output
        _uiState.update {
            it.copy(
                input = nextInput,
                inputStats = computeStats(nextInput),
                output = "",
                outputStats = TextStats(0, 0, 0),
                error = null,
            )
        }
        process(immediate = true)
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

    fun setHaptics(enabled: Boolean) {
        _uiState.update { it.copy(haptics = enabled) }
        prefs.haptics = enabled
    }

    /**
     * "Restore defaults": every user-configurable preference returns to its out-of-the-box value.
     * Favourites and their order are kept - the documented behaviour of every reset in this app -
     * and nothing outside the preference store is touched. The screen keeps the text in front of
     * the user; the current tool simply continues with its default parameters.
     */
    fun restoreDefaults() {
        // Saved RSA key pairs are user-created data, not a preference: they live in their own
        // encrypted store and survive this reset, exactly like the favourites do.
        prefs.restoreDefaults()
        _uiState.update {
            it.copy(
                theme = AppTheme.SYSTEM,
                accent = AccentOption.TEAL,
                autoProcess = true,
                copyConfirmation = true,
                haptics = true,
                recents = emptyList(),
                params = ToolRegistry.get(it.toolId).defaultParams(),
                temporaryDataSize = prefs.temporaryDataSize(),
            ).withValidatedParams()
        }
        // The tool the app opens with is a preference too: cleared here, so the next launch starts
        // from the beginning, while the tool on screen stays where it is until the user moves.
        prefs.lastTool = null
        process(immediate = true)
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
                analysis = null,
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
