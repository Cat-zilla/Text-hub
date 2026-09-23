package com.texthub.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
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
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                // -------------------------------------------------------- tool selector
                SectionCard(onClick = onOpenPicker) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ToolMonogram(glyph = meta.glyph, size = 46.dp, highlighted = true)
                        Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                            Text(text = meta.name, style = MaterialTheme.typography.titleMedium)
                            Row(modifier = Modifier.padding(top = 4.dp)) {
                                ClassificationChip(classification = meta.classification)
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
                            ParameterField(
                                spec = spec,
                                value = state.params[spec.key] ?: spec.defaultValue,
                                issue = state.issueFor(spec.key),
                                onValueChange = { onParamChange(spec.key, it) },
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
                        monospace = true,
                        minHeight = 108.dp,
                        maxLines = 10,
                    )
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
                        IconButton(
                            onClick = onShare,
                            enabled = state.output.isNotEmpty(),
                            modifier = Modifier.size(40.dp),
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
                            modifier = Modifier.size(40.dp),
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
