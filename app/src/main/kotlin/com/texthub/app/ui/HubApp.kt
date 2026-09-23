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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.texthub.app.ui.theme.TextHubTheme
import com.texthub.core.detector.UniversalDecoder
import com.texthub.app.viewmodel.HubViewModel
import kotlinx.coroutines.launch

private enum class Screen { MAIN, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubApp(
    viewModel: HubViewModel,
    versionName: String,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
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

    BackHandler(enabled = screen == Screen.SETTINGS) { screen = Screen.MAIN }

    fun dismissPicker() {
        scope.launch { pickerState.hide() }.invokeOnCompletion { pickerVisible = false }
    }

    fun dismissInfo() {
        scope.launch { infoState.hide() }.invokeOnCompletion { infoVisible = false }
    }

    fun dismissOverridePicker() {
        scope.launch { overridePickerState.hide() }.invokeOnCompletion { overridePickerVisible = false }
    }

    fun copyOutput() {
        val text = state.output
        if (text.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(com.texthub.app.R.string.msg_nothing_to_copy)) }
            return
        }
        clipboard.setText(AnnotatedString(text))
        if (state.copyConfirmation) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(com.texthub.app.R.string.msg_copied)) }
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

    TextHubTheme(theme = state.theme, accent = state.accent) {
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
                        onSettings = { screen = Screen.SETTINGS },
                        onDirectionSelected = viewModel::setDirection,
                        onParamChange = viewModel::setParam,
                        onInputChange = viewModel::setInput,
                        onProcess = { viewModel.process(immediate = true) },
                        onCopy = ::copyOutput,
                        onSwap = viewModel::swap,
                        onShare = ::shareOutput,
                        onPaste = {
                            val text = clipboard.getText()?.text
                            if (!text.isNullOrEmpty()) viewModel.setInput(state.input + text)
                        },
                        onClearInput = viewModel::clearInput,
                        onClearOutput = viewModel::clearOutput,
                        onResetParams = viewModel::resetParams,
                        onOpenOverridePicker = { overridePickerVisible = true },
                        onClearOverride = { viewModel.setParam(UniversalDecoder.PARAM_PREFER, "") },
                        onUseCandidate = { toolId ->
                            viewModel.setParam(UniversalDecoder.PARAM_PREFER, toolId)
                        },
                        onAnalyseAgain = viewModel::analyseResultAgain,
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        state = state,
                        snackbarHostState = snackbarHostState,
                        onBack = { screen = Screen.MAIN },
                        onThemeSelected = viewModel::setTheme,
                        onAccentSelected = viewModel::setAccent,
                        onAutoProcessChanged = viewModel::setAutoProcess,
                        onCopyConfirmationChanged = viewModel::setCopyConfirmation,
                        onClearTemporaryData = {
                            viewModel.clearTemporaryData()
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(com.texthub.app.R.string.msg_data_cleared)
                                )
                            }
                        },
                        versionName = versionName,
                    )
            }

            if (pickerVisible) {
                LaunchedEffect(Unit) { pickerState.show() }
                ToolPickerSheet(
                    sheetState = pickerState,
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
