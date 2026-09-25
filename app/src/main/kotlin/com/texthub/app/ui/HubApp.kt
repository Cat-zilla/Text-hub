package com.texthub.app.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import com.texthub.app.ui.theme.TextHubTheme
import com.texthub.app.ui.settings.SettingsNavigation
import com.texthub.app.ui.support.openSupportPage
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.texthub.core.ToolRegistry
import com.texthub.app.viewmodel.RsaVaultEvent
import com.texthub.core.detector.UniversalDecoder
import com.texthub.app.viewmodel.HubViewModel
import kotlinx.coroutines.launch

private enum class Screen { MAIN, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubApp(
    viewModel: HubViewModel,
    versionName: String,
    versionCode: Int = 0,
    buildType: String = "",
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf(Screen.MAIN) }
    var pickerVisible by remember { mutableStateOf(false) }
    var infoVisible by remember { mutableStateOf(false) }
    // The Universal Decoder's manual override reuses the ordinary tool picker, in its own sheet.
    var overridePickerVisible by remember { mutableStateOf(false) }

    // skipPartiallyExpanded = true: the sheet opens at full height, so the first drag inside the
    // tool list scrolls the list instead of half-expanding the sheet (that fight was the jitter).
    val pickerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val infoState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val overridePickerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Settings is one screen with a small page stack: back goes up a level, and out from the root.
    var settingsNav by remember { mutableStateOf(SettingsNavigation()) }
    fun settingsBack() {
        val up = settingsNav.back()
        if (up != null) settingsNav = up else screen = Screen.MAIN
    }
    BackHandler(enabled = screen == Screen.SETTINGS) { settingsBack() }

    fun dismissPicker() {
        scope.launch { pickerState.hide() }.invokeOnCompletion { pickerVisible = false }
    }

    fun dismissInfo() {
        scope.launch { infoState.hide() }.invokeOnCompletion { infoVisible = false }
    }

    fun dismissOverridePicker() {
        scope.launch { overridePickerState.hide() }.invokeOnCompletion { overridePickerVisible = false }
    }

    // The one light haptic the app gives for a completed action, and only when the user kept
    // haptics on. Compose's haptics go through the view's, so the system haptic setting is
    // respected on top of this preference.
    fun confirmHaptic() {
        if (state.haptics) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    /**
     * Copies exactly the value it is given, through the ordinary clipboard path: the same
     * confirmation and haptic preferences as every other copy. Used by the RSA key generator's
     * sections, where "Copy" means the public key or the private key - never both together, and
     * never automatically.
     */
    fun copyValue(text: String) {
        clipboard.setText(AnnotatedString(text))
        confirmHaptic()
        if (state.copyConfirmation) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(com.texthub.app.R.string.msg_copied)) }
        }
    }

    fun copyOutput() {
        val text = state.output
        if (text.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(com.texthub.app.R.string.msg_nothing_to_copy)) }
            return
        }
        copyValue(text)
    }

    /**
     * The support action. Text Hub makes no request of its own: the URL is handed to whatever
     * browser the user already has, and nothing about the tap is stored. A device with no browser
     * at all gets a short message instead of a crash.
     */
    fun onSupport() {
        if (!openSupportPage(context)) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(com.texthub.app.R.string.support_no_browser))
            }
        }
    }

    fun shareOutput() {
        val text = state.output
        if (text.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // "Clear sensitive fields when the app goes to the background": ON_STOP is the moment the
    // activity is no longer visible (home, recents, another app). The ViewModel decides whether
    // anything happens, so with the setting off this observer is inert.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onAppBackground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    TextHubTheme(theme = state.theme, accent = state.accent, settings = state.settings) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = androidx.compose.material3.MaterialTheme.colorScheme.background,
        ) {
            // Plain `when` instead of AnimatedContent: an animated container recreates the
            // screen subtree, which can drop IME focus while the user is typing.
            when (screen) {
                    Screen.MAIN -> MainScreen(
                        state = state,
                        snackbarHostState = snackbarHostState,
                        onOpenPicker = { pickerVisible = true },
                        onOpenInfo = { infoVisible = true },
                        onSettings = { settingsNav = SettingsNavigation(); screen = Screen.SETTINGS },
                        onDirectionSelected = viewModel::setDirection,
                        onParamChange = viewModel::setParam,
                        onInputChange = viewModel::setInput,
                        onProcess = { viewModel.process(immediate = true) },
                        onGenerateKeys = viewModel::generateKeyPair,
                        onCopyValue = ::copyValue,
                        onSaveKey = viewModel::saveActiveRsaKey,
                        onLoadKey = viewModel::loadSavedRsaKey,
                        onDeleteKey = viewModel::deleteSavedRsaKey,
                        onClearKey = viewModel::clearActiveRsaKey,
                        onConsumeKeyEvent = viewModel::consumeRsaEvent,
                        onCopy = ::copyOutput,
                        onSwap = viewModel::swap,
                        onShare = ::shareOutput,
                        onPaste = {
                            val text = clipboard.getText()?.text
                            if (!text.isNullOrEmpty()) viewModel.setInput(state.input + text)
                        },
                        onClearInput = {
                            confirmHaptic()
                            viewModel.clearInput()
                        },
                        onClearOutput = {
                            confirmHaptic()
                            viewModel.clearOutput()
                        },
                        onResetParams = viewModel::resetParams,
                        onOpenOverridePicker = { overridePickerVisible = true },
                        onClearOverride = { viewModel.setParam(UniversalDecoder.PARAM_PREFER, "") },
                        onUseCandidate = { toolId ->
                            viewModel.setParam(UniversalDecoder.PARAM_PREFER, toolId)
                        },
                        onAnalyseAgain = viewModel::analyseResultAgain,
                        onUseSavedAnalysisKey = viewModel::useSavedRsaKeyForAnalysis,
                        onClearAnalysisKey = viewModel::clearAnalysisKey,
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        state = state,
                        snackbarHostState = snackbarHostState,
                        navigation = settingsNav,
                        onNavigate = { settingsNav = it },
                        onBack = { settingsBack() },
                        onThemeSelected = viewModel::setTheme,
                        onAccentSelected = viewModel::setAccent,
                        onAutoProcessChanged = viewModel::setAutoProcess,
                        onCopyConfirmationChanged = viewModel::setCopyConfirmation,
                        onHapticsChanged = viewModel::setHaptics,
                        onSettingsChange = viewModel::updateSettings,
                        onResetFavorites = {
                            viewModel.resetFavorites()
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(com.texthub.app.R.string.msg_favorites_reset)
                                )
                            }
                        },
                        onClearSavedRsaKeys = {
                            // The count comes back as RsaVaultEvent.VaultCleared; the main screen's
                            // key generator reports it, and here the settings screen does the same.
                            viewModel.clearSavedRsaKeys()
                        },
                        onClearEverything = {
                            viewModel.clearEverything()
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(com.texthub.app.R.string.msg_everything_cleared)
                                )
                            }
                        },
                        onSupport = ::onSupport,
                        onClearTemporaryData = {
                            viewModel.clearTemporaryData()
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(com.texthub.app.R.string.msg_data_cleared)
                                )
                            }
                        },
                        onRestoreDefaults = {
                            viewModel.restoreDefaults()
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(com.texthub.app.R.string.msg_defaults_restored)
                                )
                            }
                        },
                        versionName = versionName,
                        versionCode = versionCode,
                        toolCount = ToolRegistry.all.size,
                        buildType = buildType,
                    )
            }

            // The settings screen has no key generator on it, so the vault-cleared confirmation is
            // shown here when that is where the action came from.
            LaunchedEffect(state.rsaEvent, screen) {
                val event = state.rsaEvent
                if (screen == Screen.SETTINGS && event is RsaVaultEvent.VaultCleared) {
                    viewModel.consumeRsaEvent()
                    snackbarHostState.showSnackbar(
                        context.resources.getQuantityString(
                            com.texthub.app.R.plurals.msg_saved_keys_cleared, event.count, event.count,
                        )
                    )
                }
            }

            if (pickerVisible) {
                LaunchedEffect(Unit) { pickerState.show() }
                ToolPickerSheet(
                    sheetState = pickerState,
                    hapticsEnabled = state.haptics,
                    currentToolId = state.toolId,
                    favorites = state.favorites,
                    recents = state.recents,
                    onSelect = { toolId ->
                        viewModel.selectTool(toolId)
                        dismissPicker()
                    },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onMoveFavorite = viewModel::moveFavorite,
                    onDismiss = ::dismissPicker,
                )
            }

            if (overridePickerVisible) {
                LaunchedEffect(Unit) { overridePickerState.show() }
                ToolPickerSheet(
                    sheetState = overridePickerState,
                    hapticsEnabled = state.haptics,
                    // Nothing is selected: this sheet chooses a *format* for the analysis, not the
                    // tool the app is using, so no row is marked as current.
                    currentToolId = "",
                    favorites = state.favorites,
                    recents = state.recents,
                    onSelect = { toolId ->
                        viewModel.setParam(UniversalDecoder.PARAM_PREFER, toolId)
                        dismissOverridePicker()
                    },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onMoveFavorite = viewModel::moveFavorite,
                    onDismiss = ::dismissOverridePicker,
                )
            }

            if (infoVisible) {
                LaunchedEffect(Unit) { infoState.show() }
                ToolInfoSheet(
                    sheetState = infoState,
                    meta = state.meta,
                    onDismiss = ::dismissInfo,
                )
            }
        }
    }
}
