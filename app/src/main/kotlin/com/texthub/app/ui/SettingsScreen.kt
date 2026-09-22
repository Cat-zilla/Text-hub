package com.texthub.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.texthub.app.R
import com.texthub.app.ui.components.HubDivider
import com.texthub.app.ui.components.SectionCard
import com.texthub.app.ui.components.SectionTitle
import com.texthub.app.ui.components.TextAction
import com.texthub.app.ui.theme.AccentOption
import com.texthub.app.ui.theme.AppTheme
import com.texthub.app.ui.theme.HubPalette
import com.texthub.app.ui.theme.Spacing
import com.texthub.app.ui.theme.mutedTextColor
import com.texthub.app.viewmodel.HubUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: HubUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onThemeSelected: (AppTheme) -> Unit,
    onAccentSelected: (AccentOption) -> Unit,
    onAutoProcessChanged: (Boolean) -> Unit,
    onCopyConfirmationChanged: (Boolean) -> Unit,
    onClearTemporaryData: () -> Unit,
    versionName: String,
) {
    // The confirmation is a dialog, because "clear" used to happen silently and take the
    // favourites with it. Now the user sees exactly what goes and what stays before it does.
    var confirmingClear by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
          ) {
            // ------------------------------------------------------------- appearance
            SectionCard {
                SectionTitle(text = stringResource(R.string.settings_appearance))
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedTextColor,
                )
                AppTheme.values().forEach { theme ->
                    ThemeOptionRow(
                        label = when (theme) {
                            AppTheme.SYSTEM -> stringResource(R.string.theme_system)
                            AppTheme.DARK -> stringResource(R.string.theme_dark)
                            AppTheme.AMOLED -> stringResource(R.string.theme_amoled)
                            AppTheme.LIGHT -> stringResource(R.string.theme_light)
                        },
                        selected = state.theme == theme,
                        onSelect = { onThemeSelected(theme) },
                    )
                }
                HubDivider()
                Text(
                    text = stringResource(R.string.settings_accent),
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedTextColor,
                )
                Text(
                    text = stringResource(R.string.settings_accent_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = mutedTextColor,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                AccentPicker(selected = state.accent, onSelect = onAccentSelected)
            }

            // ------------------------------------------------------------- processing
            SectionCard {
                SectionTitle(text = stringResource(R.string.settings_processing))
                Spacer(Modifier.height(Spacing.sm))
                SwitchRow(
                    title = stringResource(R.string.settings_auto_process),
                    subtitle = if (state.autoProcess) {
                        stringResource(R.string.settings_auto_process_sub)
                    } else {
                        stringResource(R.string.settings_manual_process_sub)
                    },
                    checked = state.autoProcess,
                    onCheckedChange = onAutoProcessChanged,
                )
            }

            // -------------------------------------------------------------- clipboard
            SectionCard {
                SectionTitle(text = stringResource(R.string.settings_clipboard))
                Spacer(Modifier.height(Spacing.sm))
                SwitchRow(
                    title = stringResource(R.string.settings_copy_confirmation),
                    subtitle = stringResource(R.string.settings_copy_confirmation_sub),
                    checked = state.copyConfirmation,
                    onCheckedChange = onCopyConfirmationChanged,
                )
            }

            // ---------------------------------------------------------------- storage
            // Its own card, separate from the privacy explanation: the two used to share one box,
            // which made "Clear temporary data" look like part of the privacy text.
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.settings_storage),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.settings_storage_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = mutedTextColor,
                )
                HubDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_temporary_data),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        // Live size: it is recomputed whenever the store changes and right after a
                        // clear, so the number on screen is never stale.
                        Text(
                            text = stringResource(R.string.settings_temporary_data_size, state.temporaryDataSize),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextAction(
                        text = stringResource(R.string.action_clear),
                        onClick = { confirmingClear = true },
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.settings_temporary_data_body),
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedTextColor,
                )
                Text(
                    text = stringResource(R.string.settings_temporary_data_kept),
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedTextColor,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }

            // ---------------------------------------------------------------- privacy
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.settings_privacy),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.settings_privacy_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = mutedTextColor,
                )
                HubDivider()
                Text(
                    text = stringResource(R.string.settings_history_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = mutedTextColor,
                )
            }

            // ----------------------------------------------------------------- about
            SectionCard {
                SectionTitle(text = stringResource(R.string.settings_about))
                Spacer(Modifier.height(Spacing.sm))
                InfoRow(label = stringResource(R.string.settings_version), value = versionName)
                InfoRow(
                    label = stringResource(R.string.settings_developer),
                    value = stringResource(R.string.settings_developer_value),
                )
                InfoRow(
                    label = stringResource(R.string.settings_licenses),
                    value = stringResource(R.string.settings_licenses_sub),
                )
            }
          }
        }
    }

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text(stringResource(R.string.clear_data_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.clear_data_removed),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.clear_data_removed_list),
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedTextColor,
                        modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md),
                    )
                    Text(
                        text = stringResource(R.string.clear_data_kept),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.clear_data_kept_list),
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedTextColor,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingClear = false
                        onClearTemporaryData()
                    },
                ) {
                    Text(stringResource(R.string.clear_data_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * Accent chooser: a swatch per colour, chosen like a radio group. The selection is shown by a
 * check mark as well as by the border, so it never depends on colour alone.
 */
@Composable
private fun AccentPicker(selected: AccentOption, onSelect: (AccentOption) -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(modifier = Modifier.padding(top = Spacing.sm)) {
        HubPalette.Accents.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                row.forEach { accent ->
                    AccentSwatch(
                        accent = accent,
                        dark = dark,
                        selected = accent == selected,
                        onSelect = { onSelect(accent) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AccentSwatch(
    accent: AccentOption,
    dark: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(accentLabelRes(accent))
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = label }
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = accent.swatch(dark),
            border = if (selected) {
                androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.onBackground)
            } else {
                null
            },
            modifier = Modifier.size(40.dp),
        ) {
            if (selected) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = accent.contrastOn(dark),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onBackground else mutedTextColor,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

private fun accentLabelRes(accent: AccentOption): Int = when (accent) {
    AccentOption.TEAL -> R.string.accent_teal
    AccentOption.BLUE -> R.string.accent_blue
    AccentOption.INDIGO -> R.string.accent_indigo
    AccentOption.VIOLET -> R.string.accent_violet
    AccentOption.ROSE -> R.string.accent_rose
    AccentOption.AMBER -> R.string.accent_amber
    AccentOption.GREEN -> R.string.accent_green
    AccentOption.GRAPHITE -> R.string.accent_graphite
}

@Composable
private fun ThemeOptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = mutedTextColor,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = mutedTextColor,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = Spacing.md),
        )
    }
}
