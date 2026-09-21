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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.runtime.remember
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
                Column {
                    SegmentedControl(
                        options = listOf(meta.encodeLabel, meta.decodeLabel),
                        selectedIndex = if (state.direction == Direction.ENCODE) 0 else 1,
                        onSelect = { index ->
                            onDirectionSelected(if (index == 0) Direction.ENCODE else Direction.DECODE)
                        },
                    )
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
                if (meta.params.isNotEmpty()) {
                    SectionCard {
                        SectionTitle(text = stringResource(R.string.label_parameters))
                        Spacer(Modifier.height(Spacing.md))
                        meta.params.forEachIndexed { index, spec ->
                            if (index > 0) Spacer(Modifier.height(Spacing.md))
                            ParameterEditor(
                                spec = spec,
                                value = state.params[spec.key] ?: spec.defaultValue,
                                onValueChange = { onParamChange(spec.key, it) },
                            )
                            spec.helper?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = mutedTextColor,
                                    modifier = Modifier.padding(start = Spacing.xs, top = 4.dp),
                                )
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatsLine(stats = state.inputStats, modifier = Modifier.weight(1f))
                        TextAction(
                            text = stringResource(R.string.action_type_here),
                            onClick = { focusInput() },
                        )
                    }
                }

                // --------------------------------------------------------- process action
                if (!state.autoProcess) {
                    PrimaryAction(
                        text = stringResource(R.string.action_process),
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
                        TextAction(
                            text = stringResource(R.string.action_clear),
                            onClick = onClearOutput,
                            enabled = state.output.isNotEmpty(),
                        )
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

                    // Swap: moves the result into the input, flips the mode (Encode <-> Decode)
                    // and immediately processes again, so a round trip is one tap.
                    SecondaryAction(
                        text = state.swapLabel,
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
