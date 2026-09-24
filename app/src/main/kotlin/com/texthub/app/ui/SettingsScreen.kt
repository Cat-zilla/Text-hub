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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
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
import com.texthub.app.ui.components.SectionCard
import com.texthub.app.ui.components.TextAction
import com.texthub.app.ui.theme.AccentOption
import com.texthub.app.ui.theme.AppTheme
import com.texthub.app.ui.theme.HubPalette
import com.texthub.app.ui.theme.Spacing
import com.texthub.app.ui.theme.mutedTextColor
import com.texthub.app.viewmodel.HubUiState
import com.texthub.app.ui.settings.SettingsNavigation
import com.texthub.app.ui.settings.SettingsPage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.texthub.app.ui.theme.touchTargetMin
import android.os.Build
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import com.texthub.app.ui.components.SegmentedControl
import com.texthub.app.ui.theme.Density
import com.texthub.core.prefs.AnimationMode
import com.texthub.core.prefs.LayoutDensity
import com.texthub.core.prefs.UiSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: HubUiState,
    snackbarHostState: SnackbarHostState,
    navigation: SettingsNavigation,
    onNavigate: (SettingsNavigation) -> Unit,
    onBack: () -> Unit,
    onThemeSelected: (AppTheme) -> Unit,
    onAccentSelected: (AccentOption) -> Unit,
    onAutoProcessChanged: (Boolean) -> Unit,
    onCopyConfirmationChanged: (Boolean) -> Unit,
    onHapticsChanged: (Boolean) -> Unit,
    onSettingsChange: ((UiSettings) -> UiSettings) -> Unit,
    onClearTemporaryData: () -> Unit,
    onRestoreDefaults: () -> Unit,
    onResetFavorites: () -> Unit,
    onClearSavedRsaKeys: () -> Unit,
    onClearEverything: () -> Unit,
    versionName: String,
    versionCode: Int,
    toolCount: Int,
    buildType: String,
) {
    val settings = state.settings
    val page = navigation.page
    // Every destructive action is a dialog that says exactly what goes and what stays before it
    // does anything; each is also safe to confirm when there is nothing to remove.
    var confirmingClear by remember { mutableStateOf(false) }
    var confirmingRestore by remember { mutableStateOf(false) }
    var confirmingFavorites by remember { mutableStateOf(false) }
    var confirmingKeys by remember { mutableStateOf(false) }
    var confirmingEverything by remember { mutableStateOf(false) }
    var confirmingEverythingFinal by remember { mutableStateOf(false) }
    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val open: (SettingsPage) -> Unit = { onNavigate(navigation.open(it)) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(page.titleRes()),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    // One back arrow: up a level inside Settings, out of Settings from the root.
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
            verticalArrangement = Arrangement.spacedBy(Density.cardGap),
          ) {
            when (page) {
                // ------------------------------------------------------------------ root
                // Six destinations, one line each. Nothing here is a switch: the root answers
                // "what can I change?" and each row says what is behind it.
                SettingsPage.ROOT -> SectionCard {
                    SettingsPage.TOP_LEVEL.forEachIndexed { index, destination ->
                        if (index > 0) Spacer(Modifier.height(Spacing.xs))
                        NavRow(
                            icon = destination.icon(),
                            title = stringResource(destination.titleRes()),
                            summary = stringResource(destination.summaryRes()),
                            onClick = { open(destination) },
                        )
                    }
                }

                // ------------------------------------------------------------ appearance
                SettingsPage.APPEARANCE -> {
                    SectionCard {
                        GroupLabel(stringResource(R.string.settings_theme))
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
                        Spacer(Modifier.height(Spacing.sm))
                        SwitchRow(
                            title = stringResource(R.string.settings_dynamic_color),
                            subtitle = stringResource(
                                if (dynamicColorAvailable) R.string.settings_dynamic_color_sub else R.string.settings_dynamic_color_unavailable
                            ),
                            checked = settings.dynamicColor && dynamicColorAvailable,
                            enabled = dynamicColorAvailable,
                            onCheckedChange = { on -> onSettingsChange { it.copy(dynamicColor = on) } },
                        )
                    }
                    SectionCard {
                        GroupLabel(stringResource(R.string.settings_accent))
                        Text(
                            text = stringResource(
                                if (settings.dynamicColor && dynamicColorAvailable) R.string.settings_accent_dynamic_note else R.string.settings_accent_sub
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedTextColor,
                        )
                        // The accent stays selectable while dynamic colour is on: the choice is
                        // kept and returns the moment dynamic colour is switched off.
                        AccentPicker(selected = state.accent, onSelect = onAccentSelected)
                    }
                    SectionCard {
                        ChoiceRow(
                            title = stringResource(R.string.settings_text_size),
                            subtitle = stringResource(R.string.settings_text_size_sub),
                            options = listOf(stringResource(R.string.text_size_system), stringResource(R.string.text_size_large)),
                            selectedIndex = if (settings.largeText) 1 else 0,
                            onSelect = { i -> onSettingsChange { it.copy(largeText = i == 1) } },
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        NavRow(
                            title = stringResource(R.string.settings_interface),
                            summary = stringResource(R.string.settings_interface_sub),
                            onClick = { open(SettingsPage.INTERFACE) },
                        )
                    }
                }

                // ------------------------------------------------- appearance > interface
                SettingsPage.INTERFACE -> SectionCard {
                    ChoiceRow(
                        title = stringResource(R.string.settings_density),
                        subtitle = stringResource(R.string.settings_density_sub),
                        options = listOf(stringResource(R.string.density_comfortable), stringResource(R.string.density_compact)),
                        selectedIndex = if (settings.density == LayoutDensity.COMPACT) 1 else 0,
                        onSelect = { i -> onSettingsChange { it.copy(density = if (i == 1) LayoutDensity.COMPACT else LayoutDensity.COMFORTABLE) } },
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    ChoiceRow(
                        title = stringResource(R.string.settings_animation),
                        subtitle = stringResource(R.string.settings_animation_sub),
                        options = listOf(
                            stringResource(R.string.animation_full),
                            stringResource(R.string.animation_reduced),
                            stringResource(R.string.animation_off),
                        ),
                        selectedIndex = settings.animation.ordinal,
                        onSelect = { i -> onSettingsChange { it.copy(animation = AnimationMode.values()[i]) } },
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    SwitchRow(
                        title = stringResource(R.string.settings_show_tool_icons),
                        subtitle = stringResource(R.string.settings_show_tool_icons_sub),
                        checked = settings.showToolIcons,
                        onCheckedChange = { on -> onSettingsChange { it.copy(showToolIcons = on) } },
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_monospace_output),
                        subtitle = stringResource(R.string.settings_monospace_output_sub),
                        checked = settings.monospaceOutput,
                        onCheckedChange = { on -> onSettingsChange { it.copy(monospaceOutput = on) } },
                    )
                }

                // ----------------------------------------------------------- accessibility
                SettingsPage.ACCESSIBILITY -> {
                    SectionCard {
                        GroupLabel(stringResource(R.string.settings_group_text_display))
                        // Large text and Reduce animations are the same preferences as Text size
                        // and UI animation under Appearance - one value, shown where each kind of
                        // user looks for it; the subtitles say so.
                        SwitchRow(
                            title = stringResource(R.string.settings_large_text),
                            subtitle = stringResource(R.string.settings_large_text_sub),
                            checked = settings.largeText,
                            onCheckedChange = { on -> onSettingsChange { it.copy(largeText = on) } },
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_high_contrast),
                            subtitle = stringResource(R.string.settings_high_contrast_sub),
                            checked = settings.highContrast,
                            onCheckedChange = { on -> onSettingsChange { it.copy(highContrast = on) } },
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_icon_labels),
                            subtitle = stringResource(R.string.settings_icon_labels_sub),
                            checked = settings.iconLabels,
                            onCheckedChange = { on -> onSettingsChange { it.copy(iconLabels = on) } },
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_reduce_animations),
                            subtitle = stringResource(R.string.settings_reduce_animations_sub),
                            checked = settings.reduceAnimations,
                            onCheckedChange = { on -> onSettingsChange { it.withReduceAnimations(on) } },
                        )
                    }
                    SectionCard {
                        GroupLabel(stringResource(R.string.settings_group_interaction))
                        SwitchRow(
                            title = stringResource(R.string.settings_large_targets),
                            subtitle = stringResource(R.string.settings_large_targets_sub),
                            checked = settings.largeTouchTargets,
                            onCheckedChange = { on -> onSettingsChange { it.copy(largeTouchTargets = on) } },
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_haptics),
                            subtitle = stringResource(R.string.settings_haptics_sub),
                            checked = state.haptics,
                            onCheckedChange = onHapticsChanged,
                        )
                    }
                }

                // ------------------------------------------------------ privacy & security
                SettingsPage.PRIVACY -> {
                    SectionCard {
                        GroupLabel(stringResource(R.string.settings_group_privacy))
                        Text(
                            text = stringResource(R.string.settings_local_only),
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedTextColor,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        SwitchRow(
                            title = stringResource(R.string.settings_clear_on_switch),
                            subtitle = stringResource(R.string.settings_clear_on_switch_sub),
                            checked = settings.clearSecretsOnToolSwitch,
                            onCheckedChange = { on -> onSettingsChange { it.copy(clearSecretsOnToolSwitch = on) } },
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_clear_on_background),
                            subtitle = stringResource(R.string.settings_clear_on_background_sub),
                            checked = settings.clearSecretsOnBackground,
                            onCheckedChange = { on -> onSettingsChange { it.copy(clearSecretsOnBackground = on) } },
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_sensitive_warnings),
                            subtitle = stringResource(R.string.settings_sensitive_warnings_sub),
                            checked = settings.sensitiveWarnings,
                            onCheckedChange = { on -> onSettingsChange { it.copy(sensitiveWarnings = on) } },
                        )
                    }
                    SectionCard {
                        GroupLabel(stringResource(R.string.settings_group_private_keys))
                        SwitchRow(
                            title = stringResource(R.string.settings_confirm_private_copy),
                            subtitle = stringResource(R.string.settings_confirm_private_copy_sub),
                            checked = settings.confirmPrivateKeyCopy,
                            onCheckedChange = { on -> onSettingsChange { it.copy(confirmPrivateKeyCopy = on) } },
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_hide_private_preview),
                            subtitle = stringResource(R.string.settings_hide_private_preview_sub),
                            checked = settings.hidePrivateKeyPreview,
                            onCheckedChange = { on -> onSettingsChange { it.copy(hidePrivateKeyPreview = on) } },
                        )
                    }
                }

                // ------------------------------------------------------------ data & reset
                SettingsPage.DATA -> {
                    SectionCard {
                        GroupLabel(stringResource(R.string.settings_group_reset))
                        ActionRow(
                            title = stringResource(R.string.settings_restore),
                            subtitle = stringResource(R.string.settings_restore_sub),
                            action = stringResource(R.string.settings_restore_action),
                            onClick = { confirmingRestore = true },
                        )
                        ActionRow(
                            title = stringResource(R.string.settings_temporary_data),
                            subtitle = stringResource(R.string.settings_temporary_data_body) + " " +
                                stringResource(R.string.settings_temporary_data_size, state.temporaryDataSize),
                            action = stringResource(R.string.action_reset),
                            onClick = { confirmingClear = true },
                        )
                        ActionRow(
                            title = stringResource(R.string.settings_reset_favorites),
                            subtitle = if (state.favorites.isEmpty()) {
                                stringResource(R.string.settings_reset_favorites_none)
                            } else {
                                pluralStringResource(R.plurals.settings_reset_favorites_sub, state.favorites.size, state.favorites.size)
                            },
                            action = stringResource(R.string.action_reset),
                            onClick = { confirmingFavorites = true },
                        )
                    }
                    SectionCard {
                        GroupLabel(stringResource(R.string.settings_group_destructive), color = MaterialTheme.colorScheme.error)
                        ActionRow(
                            title = stringResource(R.string.settings_clear_keys),
                            subtitle = if (state.rsaSavedKeys.isEmpty()) {
                                stringResource(R.string.settings_clear_keys_none)
                            } else {
                                pluralStringResource(R.plurals.settings_clear_keys_sub, state.rsaSavedKeys.size, state.rsaSavedKeys.size)
                            },
                            action = stringResource(R.string.action_clear),
                            destructive = true,
                            onClick = { confirmingKeys = true },
                        )
                        ActionRow(
                            title = stringResource(R.string.settings_clear_everything),
                            subtitle = stringResource(R.string.settings_clear_everything_sub),
                            action = stringResource(R.string.action_clear),
                            destructive = true,
                            onClick = { confirmingEverything = true },
                        )
                    }
                }

                // ---------------------------------------------------------------- advanced
                SettingsPage.ADVANCED -> {
                    SectionCard {
                        GroupLabel(stringResource(R.string.settings_group_processing))
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
                        SwitchRow(
                            title = stringResource(R.string.settings_show_processing_time),
                            subtitle = stringResource(R.string.settings_show_processing_time_sub),
                            checked = settings.showProcessingTime,
                            onCheckedChange = { on -> onSettingsChange { it.copy(showProcessingTime = on) } },
                        )
                    }
                    SectionCard {
                        GroupLabel(stringResource(R.string.settings_interface))
                        SwitchRow(
                            title = stringResource(R.string.settings_copy_confirmation),
                            subtitle = stringResource(R.string.settings_copy_confirmation_sub),
                            checked = state.copyConfirmation,
                            onCheckedChange = onCopyConfirmationChanged,
                        )
                    }
                    SectionCard {
                        NavRow(
                            title = stringResource(R.string.settings_diagnostics),
                            summary = stringResource(R.string.settings_diagnostics_sub),
                            onClick = { open(SettingsPage.DIAGNOSTICS) },
                        )
                    }
                }

                // -------------------------------------------------- advanced > diagnostics
                SettingsPage.DIAGNOSTICS -> {
                    SectionCard {
                        Text(
                            text = stringResource(R.string.settings_diagnostics_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedTextColor,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        SwitchRow(
                            title = stringResource(R.string.settings_show_tool_id),
                            subtitle = stringResource(R.string.settings_show_tool_id_sub),
                            checked = settings.showToolId,
                            onCheckedChange = { on -> onSettingsChange { it.copy(showToolId = on) } },
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_show_detection),
                            subtitle = stringResource(R.string.settings_show_detection_sub),
                            checked = settings.showDetectionDetails,
                            onCheckedChange = { on -> onSettingsChange { it.copy(showDetectionDetails = on) } },
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_show_validation),
                            subtitle = stringResource(R.string.settings_show_validation_sub),
                            checked = settings.showValidationDetails,
                            onCheckedChange = { on -> onSettingsChange { it.copy(showValidationDetails = on) } },
                        )
                    }
                    SectionCard {
                        ActionRow(
                            title = stringResource(R.string.settings_reset_tool_settings),
                            subtitle = stringResource(R.string.settings_reset_tool_settings_sub),
                            action = stringResource(R.string.action_reset),
                            onClick = { confirmingClear = true },
                        )
                    }
                }

                // ------------------------------------------------------------------- about
                SettingsPage.ABOUT -> SectionCard {
                    InfoRow(label = stringResource(R.string.settings_app_name), value = stringResource(R.string.app_name))
                    InfoRow(label = stringResource(R.string.settings_version), value = versionName)
                    InfoRow(label = stringResource(R.string.settings_version_code), value = versionCode.toString())
                    InfoRow(label = stringResource(R.string.settings_tool_count), value = toolCount.toString())
                    InfoRow(label = stringResource(R.string.settings_build), value = buildType)
                    InfoRow(
                        label = stringResource(R.string.settings_developer),
                        value = stringResource(R.string.settings_developer_value),
                    )
                    InfoRow(
                        label = stringResource(R.string.settings_licenses),
                        value = stringResource(R.string.settings_licenses_sub),
                    )
                    Text(
                        text = stringResource(R.string.settings_local_only),
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedTextColor,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
          }
        }
    }

    if (confirmingClear) {
        TwoListDialog(
            title = stringResource(R.string.clear_data_title),
            removedList = stringResource(R.string.clear_data_removed_list),
            keptList = stringResource(R.string.clear_data_kept_list),
            confirmLabel = stringResource(R.string.clear_data_confirm),
            onConfirm = { confirmingClear = false; onClearTemporaryData() },
            onDismiss = { confirmingClear = false },
        )
    }

    if (confirmingRestore) {
        TwoListDialog(
            title = stringResource(R.string.restore_title),
            removedList = stringResource(R.string.restore_removed_list),
            keptList = stringResource(R.string.restore_kept_list),
            confirmLabel = stringResource(R.string.restore_confirm),
            onConfirm = { confirmingRestore = false; onRestoreDefaults() },
            onDismiss = { confirmingRestore = false },
        )
    }

    if (confirmingFavorites) {
        TwoListDialog(
            title = stringResource(R.string.reset_favorites_title),
            removedList = stringResource(R.string.reset_favorites_removed_list),
            keptList = stringResource(R.string.reset_favorites_kept_list),
            confirmLabel = stringResource(R.string.action_reset),
            onConfirm = { confirmingFavorites = false; onResetFavorites() },
            onDismiss = { confirmingFavorites = false },
        )
    }

    if (confirmingKeys) {
        TwoListDialog(
            title = stringResource(R.string.clear_keys_title),
            removedList = stringResource(R.string.clear_keys_removed_list),
            keptList = stringResource(R.string.clear_keys_kept_list),
            confirmLabel = stringResource(R.string.clear_keys_confirm),
            destructive = true,
            onConfirm = { confirmingKeys = false; onClearSavedRsaKeys() },
            onDismiss = { confirmingKeys = false },
        )
    }

    if (confirmingEverything) {
        TwoListDialog(
            title = stringResource(R.string.clear_everything_title),
            removedList = stringResource(R.string.clear_everything_removed_list),
            keptList = stringResource(R.string.clear_everything_kept_list),
            confirmLabel = stringResource(R.string.action_continue),
            destructive = true,
            onConfirm = { confirmingEverything = false; confirmingEverythingFinal = true },
            onDismiss = { confirmingEverything = false },
        )
    }

    if (confirmingEverythingFinal) {
        // The second step exists because saved private keys cannot be recovered afterwards.
        AlertDialog(
            onDismissRequest = { confirmingEverythingFinal = false },
            title = { Text(stringResource(R.string.clear_everything_final_title)) },
            text = { Text(stringResource(R.string.clear_everything_final_message)) },
            confirmButton = {
                TextButton(
                    onClick = { confirmingEverythingFinal = false; onClearEverything() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.clear_everything_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingEverythingFinal = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun SettingsPage.titleRes(): Int = when (this) {
    SettingsPage.ROOT -> R.string.settings_title
    SettingsPage.APPEARANCE -> R.string.settings_appearance
    SettingsPage.INTERFACE -> R.string.settings_interface
    SettingsPage.ACCESSIBILITY -> R.string.settings_accessibility
    SettingsPage.PRIVACY -> R.string.settings_security
    SettingsPage.DATA -> R.string.settings_data_reset
    SettingsPage.ADVANCED -> R.string.settings_advanced
    SettingsPage.DIAGNOSTICS -> R.string.settings_diagnostics
    SettingsPage.ABOUT -> R.string.settings_about
}

private fun SettingsPage.summaryRes(): Int = when (this) {
    SettingsPage.APPEARANCE -> R.string.settings_appearance_summary
    SettingsPage.ACCESSIBILITY -> R.string.settings_accessibility_summary
    SettingsPage.PRIVACY -> R.string.settings_security_summary
    SettingsPage.DATA -> R.string.settings_data_reset_summary
    SettingsPage.ADVANCED -> R.string.settings_advanced_summary
    SettingsPage.ABOUT -> R.string.settings_about_summary
    SettingsPage.INTERFACE -> R.string.settings_interface_sub
    SettingsPage.DIAGNOSTICS -> R.string.settings_diagnostics_sub
    SettingsPage.ROOT -> R.string.settings_title
}

private fun SettingsPage.icon(): ImageVector = when (this) {
    SettingsPage.APPEARANCE -> Icons.Outlined.Palette
    SettingsPage.ACCESSIBILITY -> Icons.Outlined.Accessibility
    SettingsPage.PRIVACY -> Icons.Outlined.Lock
    SettingsPage.DATA -> Icons.Outlined.Storage
    SettingsPage.ADVANCED -> Icons.Outlined.Tune
    SettingsPage.ABOUT -> Icons.Outlined.Info
    else -> Icons.Outlined.Info
}

/**
 * A row that opens a page: title, one-line summary and a chevron, with an optional leading icon on
 * the root list. Visibly different from a switch row (chevron, no control), and a single button
 * for accessibility that reads "title, summary".
 */
@Composable
private fun NavRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = touchTargetMin)
            .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(Spacing.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = summary, style = MaterialTheme.typography.bodySmall, color = mutedTextColor)
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = mutedTextColor,
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }
}

/** A small group heading inside a card; the only heading level the sub-pages use. */
@Composable
private fun GroupLabel(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier.padding(bottom = Spacing.xs),
    )
}

/** A "will be removed / will be kept" confirmation, the shape every reset in the app uses. */
@Composable
private fun TwoListDialog(
    title: String,
    removedList: String,
    keptList: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(text = stringResource(R.string.clear_data_removed), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = removedList,
                    style = MaterialTheme.typography.bodySmall,
                    color = mutedTextColor,
                    modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md),
                )
                Text(text = stringResource(R.string.clear_data_kept), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = keptList,
                    style = MaterialTheme.typography.bodySmall,
                    color = mutedTextColor,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** A titled row with one action at the end; destructive actions are drawn in the error colour. */
@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    action: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = mutedTextColor)
        }
        TextAction(
            text = action,
            onClick = onClick,
            contentDescription = "$action: $title",
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

/** A titled segmented choice (density, text size, animation). */
@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = mutedTextColor)
        Spacer(Modifier.height(Spacing.sm))
        SegmentedControl(options = options, selectedIndex = selectedIndex, onSelect = onSelect)
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
    enabled: Boolean = true,
) {
    // The whole row is the switch: one full-width, accessible target whose merged semantics read
    // "title, subtitle, on/off" as a single control, instead of a small switch beside passive text.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                enabled = enabled,
                onValueChange = onCheckedChange,
            )
            .alpha(if (enabled) 1f else 0.5f)
            .padding(vertical = Spacing.xs),
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
        // The row carries the state and the click; the switch is the visual of that one state.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
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
