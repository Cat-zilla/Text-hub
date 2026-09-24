package com.texthub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import com.texthub.app.ui.theme.Density
import com.texthub.app.ui.theme.LocalUiSettings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.texthub.core.detector.SecretKind
import com.texthub.core.detector.UniversalDecoder
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.texthub.app.R
import com.texthub.app.ui.components.ClassificationChip
import com.texthub.app.ui.components.ChoiceDropdown
import com.texthub.app.ui.components.ErrorBanner
import com.texthub.app.ui.components.HubDivider
import com.texthub.app.ui.components.HubTextField
import com.texthub.app.ui.components.NumberStepper
import com.texthub.app.ui.components.PrimaryAction
import com.texthub.app.ui.components.SecondaryAction
import com.texthub.app.ui.components.SectionCard
import com.texthub.app.ui.components.SectionTitle
import com.texthub.app.ui.components.SegmentedControl
import com.texthub.app.ui.components.StatsLine
import com.texthub.app.ui.components.TextAction
import com.texthub.app.ui.components.ToolMonogram
import com.texthub.app.ui.theme.Spacing
import com.texthub.app.ui.theme.mutedTextColor
import com.texthub.app.viewmodel.HubUiState
import com.texthub.app.viewmodel.RsaVaultEvent
import com.texthub.core.model.Direction
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.ToolCategory

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MainScreen(
    state: HubUiState,
    snackbarHostState: SnackbarHostState,
    onOpenPicker: () -> Unit,
    onOpenInfo: () -> Unit,
    onSettings: () -> Unit,
    onDirectionSelected: (Direction) -> Unit,
    onParamChange: (String, String) -> Unit,
    onInputChange: (String) -> Unit,
    onProcess: () -> Unit,
    onGenerateKeys: () -> Unit,
    onCopyValue: (String) -> Unit,
    onSaveKey: (String, Boolean) -> Unit,
    onLoadKey: (String) -> Unit,
    onDeleteKey: (String) -> Unit,
    onClearKey: () -> Unit,
    onConsumeKeyEvent: () -> Unit,
    onCopy: () -> Unit,
    onSwap: () -> Unit,
    onShare: () -> Unit,
    onPaste: () -> Unit,
    onClearInput: () -> Unit,
    onClearOutput: () -> Unit,
    onResetParams: () -> Unit,
    onOpenOverridePicker: () -> Unit,
    onClearOverride: () -> Unit,
    onUseCandidate: (String) -> Unit,
    onAnalyseAgain: () -> Unit,
    onUseSavedAnalysisKey: (String) -> Unit = {},
    onClearAnalysisKey: () -> Unit = {},
) {
    val meta = state.meta
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val inputFocus = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    fun focusInput() {
        inputFocus.requestFocus()
        keyboard?.show()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(R.string.app_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = mutedTextColor,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.cd_settings),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        // The window resizes for the keyboard (adjustResize in the manifest) and this column
        // scrolls, so the focused field always stays reachable. No extra imePadding() here:
        // applying it on top of adjustResize pushes the field out of the visible area.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(Density.cardGap),
            ) {
                val settings = LocalUiSettings.current
                // -------------------------------------------------------- tool selector
                SectionCard(onClick = onOpenPicker) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (settings.showToolIcons) ToolMonogram(glyph = meta.glyph, size = 46.dp, highlighted = true)
                        Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                            Text(text = meta.name, style = MaterialTheme.typography.titleMedium)
                            Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                ClassificationChip(classification = meta.classification)
                                if (settings.showToolId) {
                                    // Advanced > Show tool ID: the registry identifier, for bug reports.
                                    Text(
                                        text = meta.id,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = mutedTextColor,
                                        maxLines = 1,
                                        modifier = Modifier.padding(start = Spacing.sm),
                                    )
                                }
                            }
                        }
                        IconButton(onClick = onOpenInfo) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = stringResource(R.string.cd_tool_info),
                                tint = mutedTextColor,
                            )
                        }
                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = stringResource(R.string.cd_select_tool),
                            tint = mutedTextColor,
                        )
                    }
                }

                // ------------------------------------------------------- direction switch
                // Only shown when the tool really has two different directions. A symmetric tool
                // (ROT13, Atbash) computes the same thing both ways and a one-way tool (a digest, a
                // comparison, key generation) has no reverse at all, so neither gets a switch that
                // would do nothing. In their place the single operation is named.
                Column {
                    if (state.showDirection) {
                        SegmentedControl(
                            options = listOf(meta.encodeLabel, meta.decodeLabel),
                            selectedIndex = if (state.direction == Direction.ENCODE) 0 else 1,
                            onSelect = { index ->
                                onDirectionSelected(if (index == 0) Direction.ENCODE else Direction.DECODE)
                            },
                        )
                    } else {
                        Text(
                            text = if (meta.oneWay) {
                                stringResource(R.string.msg_single_operation, meta.encodeLabel)
                            } else {
                                stringResource(R.string.msg_symmetric_operation, meta.encodeLabel)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = mutedTextColor,
                            modifier = Modifier.padding(start = Spacing.xs),
                        )
                    }
                    // A symmetric tool says once, next to its single operation name, that both
                    // directions are the same. (It has no switch, so this is not a hint about one.)
                    if (meta.symmetric) {
                        Text(
                            text = stringResource(R.string.msg_symmetric_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedTextColor,
                            modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xs),
                        )
                    }
                }

                // ------------------------------------------------------------ parameters
                // Primary settings stay in view; the advanced ones (external formats, digests,
                // explicit IVs) live under one collapsible heading so the common path stays short.
                // Nothing sensitive is ever written to disk, and every setting that changes the
                // result is a parameter of this tool rather than a separate tool.
                if (meta.params.isNotEmpty()) {
                    var advancedExpanded by remember(meta.id) { mutableStateOf(false) }
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle(
                                text = stringResource(R.string.label_parameters),
                                modifier = Modifier.weight(1f),
                            )
                            if (meta.canResetParams) {
                                TextAction(
                                    text = stringResource(R.string.action_reset),
                                    onClick = onResetParams,
                                )
                            }
                        }
                        Spacer(Modifier.height(Spacing.md))
                        meta.primaryParams.forEachIndexed { index, spec ->
                            if (index > 0) Spacer(Modifier.height(Spacing.md))
                            if (spec.key == UniversalDecoder.PARAM_SECRET && state.analysisUsesRsaKeyEditor) {
                                // The detected payload needs RSA key material: a PEM block is not
                                // a password, so it gets the multi-line key editor (same value,
                                // same parameter, same processing path as the password editor).
                                RsaKeyParameterField(
                                    state = state,
                                    snackbarHostState = snackbarHostState,
                                    onValueChange = { onParamChange(spec.key, it) },
                                    onUseSavedKey = onUseSavedAnalysisKey,
                                    onClear = onClearAnalysisKey,
                                    onConsumeEvent = onConsumeKeyEvent,
                                )
                            } else {
                                ParameterField(
                                    spec = spec,
                                    value = state.params[spec.key] ?: spec.defaultValue,
                                    issue = state.issueFor(spec.key),
                                    onValueChange = { onParamChange(spec.key, it) },
                                )
                            }
                        }
                        if (settings.showValidationDetails) {
                            // Advanced > Show validation details: one line per parameter warning,
                            // or a short "all valid" - the same messages the fields show inline.
                            Spacer(Modifier.height(Spacing.sm))
                            val summary = if (state.paramIssues.isEmpty()) {
                                pluralStringResource(R.plurals.msg_validation_ok, meta.params.size, meta.params.size)
                            } else {
                                state.paramIssues.joinToString("\n") { "${'$'}{meta.params.firstOrNull { p -> p.key == it.key }?.label ?: it.key}: ${'$'}{it.message}" }
                            }
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.paramIssues.isEmpty()) mutedTextColor else MaterialTheme.colorScheme.error,
                            )
                        }
                        if (meta.advancedParams.isNotEmpty()) {
                            Spacer(Modifier.height(Spacing.md))
                            HubDivider()
                            Spacer(Modifier.height(Spacing.sm))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { advancedExpanded = !advancedExpanded },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (advancedExpanded) {
                                        Icons.Outlined.ExpandLess
                                    } else {
                                        Icons.Outlined.ExpandMore
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm)) {
                                    Text(
                                        text = stringResource(
                                            if (meta.category == ToolCategory.SECURE) {
                                                R.string.label_advanced_encryption
                                            } else {
                                                R.string.label_advanced_settings
                                            }
                                        ),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = stringResource(R.string.label_advanced_sub),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = mutedTextColor,
                                    )
                                }
                            }
                            if (advancedExpanded) {
                                meta.advancedParams.forEach { spec ->
                                    Spacer(Modifier.height(Spacing.md))
                                    ParameterField(
                                        spec = spec,
                                        value = state.params[spec.key] ?: spec.defaultValue,
                                        issue = state.issueFor(spec.key),
                                        onValueChange = { onParamChange(spec.key, it) },
                                    )
                                }
                            }
                        }
                    }
                }

                if (!state.isRsaKeyGen) {
                // ----------------------------------------------------------------- input
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(
                            text = stringResource(R.string.label_input),
                            modifier = Modifier.weight(1f),
                        )
                        TextAction(text = stringResource(R.string.action_paste), onClick = onPaste)
                        TextAction(
                            text = stringResource(R.string.action_clear),
                            onClick = {
                                focusManager.clearFocus()
                                onClearInput()
                            },
                            enabled = state.input.isNotEmpty(),
                        )
                        IconButton(
                            onClick = {
                                keyboard?.hide()
                                focusManager.clearFocus()
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Keyboard,
                                contentDescription = stringResource(R.string.cd_hide_keyboard),
                                tint = mutedTextColor,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    HubTextField(
                        value = state.input,
                        onValueChange = onInputChange,
                        modifier = Modifier.focusRequester(inputFocus),
                        placeholder = stringResource(R.string.hint_input),
                        minHeight = 108.dp,
                        maxLines = 8,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    // The field is directly above, so a "Type here" button only duplicated the tap.
                    StatsLine(stats = state.inputStats)
                }

                // --------------------------------------------------------- process action
                if (!state.autoProcess) {
                    PrimaryAction(
                        // The tool's own verb: "Encrypt", "Decode", "Hash", "Compare" - never a
                        // generic "Process" that tells the user nothing about what will happen.
                        text = state.actionLabel,
                        onClick = {
                            keyboard?.hide()
                            focusManager.clearFocus()
                            onProcess()
                        },
                        icon = {
                            Icon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                } else if (state.input.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.msg_auto_process_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedTextColor,
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
                }

                // --------------------------------------------------- the RSA key generator
                // Its result has its own Public key / Private key / Key information sections and
                // is created only through its own action - never through the generic flow.
                if (state.isRsaKeyGen) {
                    RsaKeyGenSections(
                        state = state,
                        snackbarHostState = snackbarHostState,
                        onGenerate = onGenerateKeys,
                        onCopy = onCopyValue,
                        onSaveKey = onSaveKey,
                        onLoadKey = onLoadKey,
                        onDeleteKey = onDeleteKey,
                        onClearKey = onClearKey,
                        onConsumeEvent = onConsumeKeyEvent,
                    )
                }

                // --------------------------------------------- Universal Decoder empty state
                // Before anything has been typed, say what this tool is for instead of leaving the
                // screen blank - two quiet lines in the app's usual style, never a dialog.
                if (state.isUniversalDecoder && state.input.isBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    ) {
                        Text(
                            text = stringResource(R.string.ud_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedTextColor,
                        )
                        Text(
                            text = stringResource(R.string.ud_empty_examples),
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedTextColor,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }

                // ------------------------------------------------------------- analysis
                // Only the Universal Decoder has anything to analyse, and only while there is input:
                // everything here is derived from that input and never stored.
                state.analysis?.let { diagnosis ->
                    AnalysisCard(
                        diagnosis = diagnosis,
                        forcedToolId = state.forcedToolId,
                        canAnalyseAgain = state.canAnalyseAgain,
                        onOverride = onOpenOverridePicker,
                        onClearOverride = onClearOverride,
                        onUseCandidate = onUseCandidate,
                        onAnalyseAgain = onAnalyseAgain,
                    )
                }

                // ----------------------------------------------------------------- output
                // Hidden for the RSA key pair generator: its output is the key pair, which the
                // dedicated sections above already present exactly once.
                if (!state.isRsaKeyGen) {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(
                            text = stringResource(R.string.label_output),
                            modifier = Modifier.weight(1f),
                        )
                        TextAction(
                            text = stringResource(R.string.action_copy),
                            onClick = onCopy,
                            enabled = state.output.isNotEmpty(),
                        )
                        // Clear lives once, as the X in the card footer below, next to Share: two
                        // identical buttons inside one card was one too many.
                    }
                    if (state.processing) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.sm),
                        )
                    }
                    state.error?.let { error ->
                        ErrorBanner(
                            message = error,
                            modifier = Modifier.padding(bottom = Spacing.sm),
                        )
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    HubTextField(
                        value = state.output,
                        onValueChange = {},
                        placeholder = stringResource(R.string.hint_output),
                        readOnly = true,
                        monospace = settings.monospaceOutput,
                        minHeight = 108.dp,
                        maxLines = 10,
                    )
                    if (settings.showProcessingTime && state.lastDurationMs != null && state.output.isNotEmpty()) {
                        // Advanced > Show processing time: the engine's own measurement of the last run.
                        Text(
                            text = stringResource(R.string.msg_processing_time, state.lastDurationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedTextColor,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                    Spacer(Modifier.height(Spacing.md))

                    // Swap: moves the result into the input and, when the tool has a reverse
                    // direction, flips the mode and processes again - one tap for a round trip.
                    // Tools that produce a final answer (a digest, a measurement, a comparison)
                    // do not offer it at all.
                    if (state.showSwap) {
                        SecondaryAction(
                            text = state.swapTarget?.let { target ->
                                stringResource(R.string.action_swap_into, target)
                            } ?: stringResource(R.string.action_use_as_input),
                            onClick = {
                                keyboard?.hide()
                                focusManager.clearFocus()
                                onSwap()
                            },
                            enabled = state.canSwap,
                            icon = {
                                Icon(
                                    Icons.Outlined.SwapVert,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                        )
                        Spacer(Modifier.height(Spacing.sm))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatsLine(stats = state.outputStats, modifier = Modifier.weight(1f))
                        if (settings.iconLabels) {
                            // Accessibility > Always show text labels: the two icon-only buttons
                            // become labelled text actions (same callbacks, same enabled state).
                            TextAction(
                                text = stringResource(R.string.action_share),
                                onClick = onShare,
                                enabled = state.output.isNotEmpty(),
                            )
                            TextAction(
                                text = stringResource(R.string.action_clear),
                                onClick = onClearOutput,
                                enabled = state.output.isNotEmpty(),
                                contentDescription = stringResource(R.string.cd_clear_output),
                            )
                        } else {
                        IconButton(
                            onClick = onShare,
                            enabled = state.output.isNotEmpty(),
                            modifier = Modifier.size(if (settings.largeTouchTargets) 48.dp else 40.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Share,
                                contentDescription = stringResource(R.string.cd_share_output),
                                tint = if (state.output.isNotEmpty()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    mutedTextColor
                                },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(
                            onClick = onClearOutput,
                            enabled = state.output.isNotEmpty(),
                            modifier = Modifier.size(if (settings.largeTouchTargets) 48.dp else 40.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.cd_clear_output),
                                tint = mutedTextColor,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        }
                    }
                }
                }

                // ---------------------------------------------------------- privacy note
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.privacy_footer),
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedTextColor,
                    )
                }
            }
        }
    }
}

/**
 * The RSA Key Pair Generator's own sections, in the app's usual card language.
 *
 * The **active** pair is session state of its own (`HubUiState.rsaActive`): it is created only by
 * the explicit Generate action, survives tool switches, settings changes and recomposition, and is
 * replaced only by generating, loading or clearing it. Saving copies it into the encrypted saved
 * collection under a user-chosen name; loading makes a saved pair active; deleting removes one
 * saved record. Private material appears only in the Private key card and only for the active
 * pair - the saved list shows name, size and fingerprint, never a key.
 */
@Composable
private fun RsaKeyGenSections(
    state: HubUiState,
    snackbarHostState: SnackbarHostState,
    onGenerate: () -> Unit,
    onCopy: (String) -> Unit,
    onSaveKey: (String, Boolean) -> Unit,
    onLoadKey: (String) -> Unit,
    onDeleteKey: (String) -> Unit,
    onClearKey: () -> Unit,
    onConsumeEvent: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = snackbarHostState

    // Dialog state. The save dialog stays open while the replace question is on top of it, so a
    // cancelled replace simply returns to the naming step.
    var saveDialogVisible by remember { mutableStateOf(false) }
    var pendingReplaceName by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmPrivateCopy by remember { mutableStateOf(false) }
    var pendingLoadName by remember { mutableStateOf<String?>(null) }
    var pendingDeleteName by remember { mutableStateOf<String?>(null) }

    // One completed vault operation at a time, acknowledged here exactly once.
    LaunchedEffect(state.rsaEvent) {
        when (val event = state.rsaEvent) {
            is RsaVaultEvent.Saved -> {
                snackbarHostState.showSnackbar(
                    context.getString(
                        if (event.replaced) R.string.msg_key_saved_replaced else R.string.msg_key_saved,
                    )
                )
            }
            is RsaVaultEvent.NameConflict -> pendingReplaceName = event.name
            is RsaVaultEvent.SameKeyExists -> snackbarHostState.showSnackbar(
                context.getString(R.string.msg_key_same_exists, event.name)
            )
            is RsaVaultEvent.LoadFailed -> snackbarHostState.showSnackbar(
                context.getString(R.string.msg_key_load_failed)
            )
            is RsaVaultEvent.Failed -> snackbarHostState.showSnackbar(event.message)
            is RsaVaultEvent.VaultCleared -> snackbarHostState.showSnackbar(
                context.resources.getQuantityString(R.plurals.msg_saved_keys_cleared, event.count, event.count)
            )
            null -> Unit
        }
        if (state.rsaEvent != null) onConsumeEvent()
    }

    val keys = state.rsaActive

    // ------------------------------------------------------------- action / state card
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(text = stringResource(R.string.rsa_title), modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.rsa_action_sub),
                style = MaterialTheme.typography.labelSmall,
                color = mutedTextColor,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        when {
            state.processing -> {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.sm),
                )
                Text(
                    text = stringResource(R.string.rsa_generating),
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedTextColor,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            keys != null -> {
                Text(
                    text = stringResource(R.string.rsa_info_size, keys.bits),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.rsa_ready_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedTextColor,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            state.error != null -> ErrorBanner(message = state.error)
            else -> Text(
                text = stringResource(R.string.rsa_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = mutedTextColor,
            )
        }
        Spacer(Modifier.height(Spacing.md))
        PrimaryAction(
            text = stringResource(R.string.rsa_generate),
            onClick = onGenerate,
            enabled = !state.processing,
        )
        if (keys != null && !state.processing) {
            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
            ) {
                TextAction(
                    text = stringResource(R.string.rsa_action_save),
                    onClick = { saveDialogVisible = true },
                )
                TextAction(
                    text = stringResource(R.string.rsa_action_clear),
                    onClick = { confirmClear = true },
                )
            }
        }
    }

    // ------------------------------------------------------------------ key information
    if (keys != null) {
        SectionCard {
            SectionTitle(text = stringResource(R.string.rsa_info_title))
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.rsa_info_size, keys.bits),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.rsa_info_fingerprint),
                style = MaterialTheme.typography.labelSmall,
                color = mutedTextColor,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            // The fingerprint describes the public key only, so it may be read out and selected freely.
            Text(
                text = keys.fingerprint,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                ),
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        // ----------------------------------------------------------------------- public key
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(text = stringResource(R.string.rsa_public_title), modifier = Modifier.weight(1f))
                TextAction(
                    text = stringResource(R.string.action_copy),
                    onClick = { onCopy(keys.publicPem) },
                    contentDescription = stringResource(R.string.cd_copy_public),
                )
            }
            Text(
                text = stringResource(R.string.rsa_public_helper),
                style = MaterialTheme.typography.labelSmall,
                color = mutedTextColor,
            )
            Spacer(Modifier.height(Spacing.xs))
            HubTextField(
                value = keys.publicPem,
                onValueChange = {},
                readOnly = true,
                monospace = true,
                minHeight = 120.dp,
                maxLines = 8,
            )
        }

        // ---------------------------------------------------------------------- private key
        // Security & Privacy: the preview is masked until Reveal (session state, per pair) and
        // Copy asks first, each behind its own setting. The key itself is identical either way.
        val settings = LocalUiSettings.current
        var privateRevealed by remember(keys.fingerprint) { mutableStateOf(!settings.hidePrivateKeyPreview) }
        val masked = settings.hidePrivateKeyPreview && !privateRevealed
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(text = stringResource(R.string.rsa_private_title), modifier = Modifier.weight(1f))
                if (settings.hidePrivateKeyPreview) {
                    TextAction(
                        text = stringResource(if (masked) R.string.action_reveal else R.string.action_hide),
                        onClick = { privateRevealed = masked },
                        contentDescription = stringResource(if (masked) R.string.cd_reveal_private else R.string.cd_hide_private),
                    )
                }
                TextAction(
                    text = stringResource(R.string.action_copy),
                    onClick = { if (settings.confirmPrivateKeyCopy) confirmPrivateCopy = true else onCopy(keys.privatePem) },
                    contentDescription = stringResource(R.string.cd_copy_private),
                )
            }
            Text(
                text = stringResource(R.string.rsa_private_helper),
                style = MaterialTheme.typography.labelSmall,
                color = mutedTextColor,
            )
            if (settings.sensitiveWarnings) {
                Text(
                    text = stringResource(R.string.rsa_private_warning),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            if (masked) {
                // A placeholder, not a transformation of the key: nothing derived from the private
                // material is drawn, measured or exposed to accessibility while hidden.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .clickable(onClickLabel = stringResource(R.string.cd_reveal_private)) { privateRevealed = true }
                        .padding(Spacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.rsa_private_hidden, keys.bits),
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedTextColor,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                HubTextField(
                    value = keys.privatePem,
                    onValueChange = {},
                    readOnly = true,
                    monospace = true,
                    minHeight = 160.dp,
                    maxLines = 10,
                )
            }
        }
    }

    // ------------------------------------------------------------------ saved key pairs
    SectionCard {
        SectionTitle(text = stringResource(R.string.rsa_saved_title))
        Spacer(Modifier.height(Spacing.sm))
        if (state.rsaSavedKeys.isEmpty()) {
            Text(
                text = stringResource(R.string.rsa_saved_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = mutedTextColor,
            )
        } else {
            state.rsaSavedKeys.forEach { saved ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = saved.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(R.string.rsa_info_size, saved.bits),
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedTextColor,
                        )
                        Text(
                            text = saved.fingerprint,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            ),
                            color = mutedTextColor,
                            maxLines = 1,
                        )
                        Text(
                            text = stringResource(
                                R.string.rsa_saved_created,
                                java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                                    .format(java.util.Date(saved.createdAt)),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedTextColor,
                        )
                    }
                    TextAction(
                        text = stringResource(R.string.rsa_saved_load),
                        // An unsaved active pair must not be lost to a stray tap: the question is
                        // asked here, and the load itself happens on confirmation.
                        onClick = {
                            if (state.rsaSavedName == null && keys != null) {
                                pendingLoadName = saved.name
                            } else {
                                onLoadKey(saved.name)
                            }
                        },
                    )
                    TextAction(
                        text = stringResource(R.string.rsa_saved_delete),
                        onClick = { pendingDeleteName = saved.name },
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------------ dialogs
    if (saveDialogVisible) {
        var name by remember { mutableStateOf("") }
        var showBlankError by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { saveDialogVisible = false; showBlankError = false },
            title = { Text(stringResource(R.string.rsa_save_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; showBlankError = false },
                        label = { Text(stringResource(R.string.rsa_save_name_label)) },
                        placeholder = { Text(stringResource(R.string.rsa_save_name_hint)) },
                        singleLine = true,
                        isError = showBlankError,
                        supportingText = if (showBlankError) {
                            { Text(stringResource(R.string.rsa_save_name_blank)) }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isEmpty()) {
                            showBlankError = true
                        } else {
                            saveDialogVisible = false
                            onSaveKey(trimmed, false)
                        }
                    },
                ) {
                    Text(stringResource(R.string.rsa_save_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { saveDialogVisible = false; showBlankError = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    pendingReplaceName?.let { conflictName ->
        AlertDialog(
            onDismissRequest = { pendingReplaceName = null },
            title = { Text(stringResource(R.string.rsa_save_replace_title)) },
            text = { Text(stringResource(R.string.rsa_save_replace_message, conflictName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingReplaceName = null
                        saveDialogVisible = false
                        onSaveKey(conflictName, true)
                    },
                ) {
                    Text(stringResource(R.string.rsa_save_replace_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingReplaceName = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (confirmPrivateCopy && keys != null) {
        AlertDialog(
            onDismissRequest = { confirmPrivateCopy = false },
            title = { Text(stringResource(R.string.rsa_copy_private_title)) },
            text = { Text(stringResource(R.string.rsa_copy_private_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmPrivateCopy = false
                        onCopy(keys.privatePem)
                    },
                ) { Text(stringResource(R.string.action_copy)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmPrivateCopy = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.rsa_clear_title)) },
            text = { Text(stringResource(R.string.rsa_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClearKey()
                    },
                ) {
                    Text(stringResource(R.string.rsa_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    pendingLoadName?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingLoadName = null },
            title = { Text(stringResource(R.string.rsa_load_title)) },
            text = { Text(stringResource(R.string.rsa_load_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingLoadName = null
                        onLoadKey(name)
                    },
                ) {
                    Text(stringResource(R.string.rsa_load_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingLoadName = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    pendingDeleteName?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDeleteName = null },
            title = { Text(stringResource(R.string.rsa_delete_title)) },
            text = { Text(stringResource(R.string.rsa_delete_message, name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteName = null
                        onDeleteKey(name)
                    },
                ) {
                    Text(stringResource(R.string.rsa_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteName = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * The Universal Decoder's key field while the detected payload needs an RSA key.
 *
 * It edits the very same parameter as the password editor - only the presentation changes: a
 * multi-line, vertically scrolling monospace field with no length limit, the label says which key
 * (private or public) the operation needs, and two actions sit under it: *Use saved key* (the
 * existing saved key pairs, listed by name, size and fingerprint - never by content) and *Clear*
 * (empties the key only; the input text stays). The value is passed through untouched: nothing
 * is trimmed, wrapped or truncated, and nothing here is persisted.
 */
@Composable
private fun RsaKeyParameterField(
    state: HubUiState,
    snackbarHostState: SnackbarHostState,
    onValueChange: (String) -> Unit,
    onUseSavedKey: (String) -> Unit,
    onClear: () -> Unit,
    onConsumeEvent: () -> Unit,
) {
    val context = LocalContext.current
    val isPublic = state.analysisSecretKind == SecretKind.PUBLIC_KEY
    val label = stringResource(if (isPublic) R.string.ud_key_public else R.string.ud_key_private)
    val value = state.analysisSecret
    var pickerVisible by remember { mutableStateOf(false) }

    // A saved key that could not be opened is reported here, once (the RSA key pair generator's
    // sections are not on screen for this tool, so this is the one consumer).
    LaunchedEffect(state.rsaEvent) {
        if (state.rsaEvent is RsaVaultEvent.LoadFailed) {
            onConsumeEvent()
            snackbarHostState.showSnackbar(context.getString(R.string.msg_key_load_failed))
        }
    }

    HubTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = stringResource(if (isPublic) R.string.ud_key_hint_public else R.string.ud_key_hint_private),
        monospace = true,
        minHeight = 120.dp,
        maxLines = 8,
        // The label only - never the key - is what accessibility services get in addition to the
        // editable text itself.
        modifier = Modifier.semantics { contentDescription = label },
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            FieldCaption(label)
            if (value.isNotEmpty()) {
                Text(
                    text = if (state.analysisUsesSavedKey) {
                        stringResource(R.string.ud_key_status_saved, state.analysisSavedKeyName.orEmpty())
                    } else {
                        stringResource(R.string.ud_key_status_manual)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = Spacing.xs, top = 2.dp),
                )
            }
        }
        TextAction(
            text = stringResource(R.string.ud_key_use_saved),
            onClick = { pickerVisible = true },
            contentDescription = stringResource(R.string.cd_ud_key_use_saved),
        )
        TextAction(
            text = stringResource(R.string.action_clear),
            onClick = onClear,
            enabled = value.isNotEmpty(),
            contentDescription = stringResource(R.string.cd_ud_key_clear),
        )
    }
    Text(
        text = stringResource(if (isPublic) R.string.ud_key_helper_public else R.string.ud_key_helper_private),
        style = MaterialTheme.typography.labelSmall,
        color = mutedTextColor,
        modifier = Modifier.padding(start = Spacing.xs, top = 4.dp),
    )

    if (pickerVisible) {
        SavedRsaKeyPicker(
            keys = state.rsaSavedKeys,
            inUse = state.analysisSavedKeyName,
            onPick = { name ->
                pickerVisible = false
                onUseSavedKey(name)
            },
            onDismiss = { pickerVisible = false },
        )
    }
}

/**
 * The saved key pairs as a choice list: name, size, fingerprint and save date - the collection's
 * listing metadata, which never includes key material. Tapping one entry loads exactly that
 * record; nothing is tried automatically.
 */
@Composable
private fun SavedRsaKeyPicker(
    keys: List<com.texthub.core.keys.SavedRsaKeyMeta>,
    inUse: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ud_key_picker_title)) },
        text = {
            if (keys.isEmpty()) {
                Text(
                    text = stringResource(R.string.rsa_saved_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = mutedTextColor,
                )
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    keys.forEach { saved ->
                        val description = stringResource(
                            R.string.cd_ud_key_picker_entry, saved.name, saved.bits, saved.fingerprint,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(saved.name) }
                                .semantics(mergeDescendants = true) { contentDescription = description }
                                .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = saved.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                if (saved.name == inUse) {
                                    Text(
                                        text = stringResource(R.string.ud_key_picker_in_use),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.rsa_info_size, saved.bits),
                                style = MaterialTheme.typography.labelSmall,
                                color = mutedTextColor,
                            )
                            Text(
                                text = saved.fingerprint,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                ),
                                color = mutedTextColor,
                            )
                            Text(
                                text = stringResource(
                                    R.string.rsa_saved_created,
                                    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                                        .format(java.util.Date(saved.createdAt)),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = mutedTextColor,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * One parameter: the editor itself, then the inline problem (if any) and then the helper text.
 * A problem is shown where it belongs - next to the field it is about - instead of only in a
 * generic banner at the bottom of the screen.
 */
@Composable
private fun ParameterField(
    spec: ParamSpec,
    value: String,
    issue: String?,
    onValueChange: (String) -> Unit,
) {
    ParameterEditor(spec = spec, value = value, onValueChange = onValueChange)
    if (issue != null) {
        Text(
            text = issue,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = Spacing.xs, top = 4.dp),
        )
    }
    spec.helper?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.labelSmall,
            color = mutedTextColor,
            modifier = Modifier.padding(start = Spacing.xs, top = 4.dp),
        )
    }
}

@Composable
private fun ParameterEditor(
    spec: ParamSpec,
    value: String,
    onValueChange: (String) -> Unit,
) {
    when (spec.kind) {
        ParamKind.NUMBER -> {
            NumberStepper(
                value = value.toIntOrNull() ?: spec.defaultValue.toIntOrNull() ?: 0,
                onValueChange = { onValueChange(it.toString()) },
                min = if (spec.min == Int.MIN_VALUE) Int.MIN_VALUE / 4 else spec.min,
                max = if (spec.max == Int.MAX_VALUE) Int.MAX_VALUE / 4 else spec.max,
                label = spec.label,
            )
        }
        ParamKind.CHOICE -> {
            ChoiceDropdown(
                label = spec.label,
                options = spec.choices,
                selectedId = value,
                onSelect = onValueChange,
            )
        }
        ParamKind.PASSWORD -> {
            HubTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = spec.hint,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            FieldCaption(spec.label)
        }
        ParamKind.ALPHABET -> {
            HubTextField(
                value = value,
                onValueChange = { onValueChange(it.uppercase()) },
                placeholder = spec.hint,
                monospace = true,
                singleLine = true,
            )
            FieldCaption(spec.label)
        }
        ParamKind.TEXT -> {
            HubTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = spec.hint,
                singleLine = true,
            )
            FieldCaption(spec.label)
        }
        ParamKind.MULTILINE -> {
            // PEM keys and other long blocks: monospace, scrollable, never one long line.
            HubTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = spec.hint,
                monospace = true,
                minHeight = 120.dp,
                maxLines = 8,
            )
            FieldCaption(spec.label)
        }
    }
}

@Composable
private fun FieldCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = mutedTextColor,
        modifier = Modifier.padding(start = Spacing.xs, top = 4.dp),
    )
}
