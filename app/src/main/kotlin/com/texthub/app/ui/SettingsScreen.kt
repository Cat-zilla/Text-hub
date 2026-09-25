package com.texthub.app.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.texthub.app.R
import com.texthub.app.ui.components.SegmentedControl
import com.texthub.app.ui.settings.AccentPicker
import com.texthub.app.ui.settings.SettingsActionRow
import com.texthub.app.ui.settings.SettingsAccentRow
import com.texthub.app.ui.settings.SettingsChoiceDialog
import com.texthub.app.ui.settings.SettingsEmphasisGroup
import com.texthub.app.ui.settings.SettingsGroup
import com.texthub.app.ui.settings.SettingsGroupGap
import com.texthub.app.ui.settings.SettingsInfoRow
import com.texthub.app.ui.settings.SettingsNavRow
import com.texthub.app.ui.settings.SettingsNavigation
import com.texthub.app.ui.settings.SettingsNote
import com.texthub.app.ui.settings.SettingsPage
import com.texthub.app.ui.settings.SettingsSupportRow
import com.texthub.app.ui.settings.SettingsSwitchRow
import com.texthub.app.ui.settings.SettingsValueRow
import com.texthub.app.ui.support.openSupportPage
import com.texthub.app.ui.theme.AccentOption
import com.texthub.app.ui.theme.AppTheme
import com.texthub.app.ui.theme.Spacing
import com.texthub.app.ui.theme.mutedTextColor
import com.texthub.app.viewmodel.HubUiState
import com.texthub.core.prefs.AnimationMode
import com.texthub.core.prefs.CornerStyle
import com.texthub.core.prefs.LayoutDensity
import com.texthub.core.prefs.UiSettings

/** The order themes are offered in: System first as the default, then light to dark. */
private val THEME_ORDER = listOf(AppTheme.SYSTEM, AppTheme.LIGHT, AppTheme.DARK, AppTheme.AMOLED)

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
    /** Opens the support page in the user's browser. Called only from the two allowed places. */
    onSupport: () -> Unit,
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
                    // One back arrow: out of Settings from any page (every page is one level deep).
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
                .padding(top = Spacing.sm, bottom = Spacing.xxl),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
          // A page is a short, quiet stack of labelled groups. Whitespace separates the groups;
          // there are no cards around ordinary settings and no dividers at all.
          Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(SettingsGroupGap),
          ) {
            when (page) {
                // ------------------------------------------------------------------ root
                // Six destinations, nothing else - one list, so it is a single block (the wide
                // group gap is for separating pages' sections, not for spreading one list out).
                // The chevron (not a switch) says "this opens".
                SettingsPage.ROOT -> Column {
                    SettingsPage.TOP_LEVEL.forEach { destination ->
                        SettingsNavRow(
                            icon = destination.icon(),
                            title = stringResource(destination.titleRes()),
                            summary = stringResource(destination.summaryRes()),
                            onClick = { open(destination) },
                        )
                    }
                    // The support action, once, at the bottom of the root list - and nowhere else
                    // but About. Spaced away from the destinations so it reads as a closing note,
                    // not as a seventh destination.
                    Spacer(Modifier.height(Spacing.xxl))
                    SettingsSupportRow(
                        title = stringResource(R.string.support_buy_coffee),
                        subtitle = stringResource(R.string.support_buy_coffee_sub),
                        contentDescription = stringResource(R.string.support_buy_coffee_cd),
                        onClick = { onSupport() },
                    )
                }

                // ------------------------------------------------------------ appearance
                SettingsPage.APPEARANCE -> {
                    // Chooser dialogs on this page: theme is a segmented control on the page,
                    // the rarer choices live behind a value row each.
                    var choosingAccent by remember { mutableStateOf(false) }
                    var choosingTextSize by remember { mutableStateOf(false) }
                    var choosingDensity by remember { mutableStateOf(false) }
                    var choosingAnimation by remember { mutableStateOf(false) }
                    var choosingCornerStyle by remember { mutableStateOf(false) }

                    SettingsGroup(label = stringResource(R.string.settings_theme)) {
                        SegmentedControl(
                            options = THEME_ORDER.map {
                                when (it) {
                                    AppTheme.SYSTEM -> stringResource(R.string.theme_system_short)
                                    AppTheme.LIGHT -> stringResource(R.string.theme_light)
                                    AppTheme.DARK -> stringResource(R.string.theme_dark)
                                    AppTheme.AMOLED -> stringResource(R.string.theme_amoled)
                                }
                            },
                            selectedIndex = THEME_ORDER.indexOf(state.theme).coerceAtLeast(0),
                            onSelect = { onThemeSelected(THEME_ORDER[it]) },
                        )
                    }
                    SettingsGroup(label = stringResource(R.string.settings_group_colour)) {
                        // The accent stays selectable while dynamic colour is on: the choice is
                        // kept and returns the moment dynamic colour is switched off.
                        SettingsAccentRow(
                            title = stringResource(R.string.settings_accent),
                            accent = state.accent,
                            subtitle = if (settings.dynamicColor && dynamicColorAvailable) {
                                stringResource(R.string.settings_accent_kept)
                            } else {
                                null
                            },
                            onClick = { choosingAccent = true },
                        )
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_dynamic_color),
                            subtitle = stringResource(
                                if (dynamicColorAvailable) R.string.settings_dynamic_color_sub else R.string.settings_dynamic_color_unavailable
                            ),
                            checked = settings.dynamicColor && dynamicColorAvailable,
                            enabled = dynamicColorAvailable,
                            onCheckedChange = { on -> onSettingsChange { it.copy(dynamicColor = on) } },
                        )
                    }
                    SettingsGroup(label = stringResource(R.string.settings_group_text)) {
                        SettingsValueRow(
                            title = stringResource(R.string.settings_text_size),
                            value = stringResource(if (settings.largeText) R.string.text_size_large else R.string.text_size_system),
                            onClick = { choosingTextSize = true },
                        )
                    }
                    SettingsGroup(label = stringResource(R.string.settings_interface)) {
                        // Corner style changes the app itself, not a preview of it: the value is
                        // handed to the shape tokens through the theme (see HubCorners).
                        SettingsValueRow(
                            title = stringResource(R.string.settings_corner_style),
                            value = stringResource(cornerStyleLabel(settings.cornerStyle)),
                            onClick = { choosingCornerStyle = true },
                        )
                        SettingsValueRow(
                            title = stringResource(R.string.settings_density),
                            value = stringResource(
                                if (settings.density == LayoutDensity.COMPACT) R.string.density_compact else R.string.density_comfortable
                            ),
                            onClick = { choosingDensity = true },
                        )
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_show_tool_icons),
                            subtitle = stringResource(R.string.settings_show_tool_icons_sub),
                            checked = settings.showToolIcons,
                            onCheckedChange = { on -> onSettingsChange { it.copy(showToolIcons = on) } },
                        )
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_monospace_output),
                            subtitle = stringResource(R.string.settings_monospace_output_sub),
                            checked = settings.monospaceOutput,
                            onCheckedChange = { on -> onSettingsChange { it.copy(monospaceOutput = on) } },
                        )
                    }
                    SettingsGroup(label = stringResource(R.string.settings_group_motion)) {
                        SettingsValueRow(
                            title = stringResource(R.string.settings_animation),
                            value = when (settings.animation) {
                                AnimationMode.FULL -> stringResource(R.string.animation_full)
                                AnimationMode.REDUCED -> stringResource(R.string.animation_reduced)
                                AnimationMode.OFF -> stringResource(R.string.animation_off)
                            },
                            onClick = { choosingAnimation = true },
                        )
                    }

                    if (choosingAccent) {
                        AlertDialog(
                            onDismissRequest = { choosingAccent = false },
                            title = { Text(stringResource(R.string.settings_accent)) },
                            text = {
                                Column {
                                    Text(
                                        text = stringResource(R.string.settings_accent_sub),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = mutedTextColor,
                                        modifier = Modifier.padding(bottom = Spacing.md),
                                    )
                                    AccentPicker(
                                        selected = state.accent,
                                        onSelect = {
                                            onAccentSelected(it)
                                            choosingAccent = false
                                        },
                                    )
                                }
                            },
                            confirmButton = {},
                        )
                    }
                    if (choosingTextSize) {
                        SettingsChoiceDialog(
                            title = stringResource(R.string.settings_text_size),
                            message = stringResource(R.string.settings_text_size_sub),
                            options = listOf(
                                stringResource(R.string.text_size_system),
                                stringResource(R.string.text_size_large),
                            ),
                            selectedIndex = if (settings.largeText) 1 else 0,
                            onSelect = { i -> onSettingsChange { it.copy(largeText = i == 1) } },
                            onDismiss = { choosingTextSize = false },
                        )
                    }
                    if (choosingDensity) {
                        SettingsChoiceDialog(
                            title = stringResource(R.string.settings_density),
                            message = stringResource(R.string.settings_density_sub),
                            options = listOf(
                                stringResource(R.string.density_comfortable),
                                stringResource(R.string.density_compact),
                            ),
                            selectedIndex = if (settings.density == LayoutDensity.COMPACT) 1 else 0,
                            onSelect = { i ->
                                onSettingsChange {
                                    it.copy(density = if (i == 1) LayoutDensity.COMPACT else LayoutDensity.COMFORTABLE)
                                }
                            },
                            onDismiss = { choosingDensity = false },
                        )
                    }
                    if (choosingCornerStyle) {
                        SettingsChoiceDialog(
                            title = stringResource(R.string.settings_corner_style),
                            message = stringResource(R.string.settings_corner_style_sub),
                            options = listOf(
                                stringResource(R.string.corner_style_rounded),
                                stringResource(R.string.corner_style_slight),
                                stringResource(R.string.corner_style_square),
                            ),
                            selectedIndex = settings.cornerStyle.ordinal,
                            onSelect = { i -> onSettingsChange { it.copy(cornerStyle = CornerStyle.values()[i]) } },
                            onDismiss = { choosingCornerStyle = false },
                        )
                    }
                    if (choosingAnimation) {
                        SettingsChoiceDialog(
                            title = stringResource(R.string.settings_animation),
                            message = stringResource(R.string.settings_animation_sub),
                            options = listOf(
                                stringResource(R.string.animation_full),
                                stringResource(R.string.animation_reduced),
                                stringResource(R.string.animation_off),
                            ),
                            selectedIndex = settings.animation.ordinal,
                            onSelect = { i -> onSettingsChange { it.copy(animation = AnimationMode.values()[i]) } },
                            onDismiss = { choosingAnimation = false },
                        )
                    }
                }

                // ----------------------------------------------------------- accessibility
                SettingsPage.ACCESSIBILITY -> {
                    SettingsGroup(label = stringResource(R.string.settings_group_text)) {
                        // Large text is the same preference as Text size under Appearance - one
                        // value, shown where each kind of user looks for it; the subtitle says so.
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_large_text),
                            subtitle = stringResource(R.string.settings_large_text_sub),
                            checked = settings.largeText,
                            onCheckedChange = { on -> onSettingsChange { it.copy(largeText = on) } },
                        )
                    }
                    SettingsGroup(label = stringResource(R.string.settings_group_motion)) {
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_reduce_animations),
                            subtitle = stringResource(R.string.settings_reduce_animations_sub),
                            checked = settings.reduceAnimations,
                            onCheckedChange = { on -> onSettingsChange { it.withReduceAnimations(on) } },
                        )
                    }
                    SettingsGroup(label = stringResource(R.string.settings_group_visual)) {
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_high_contrast),
                            subtitle = stringResource(R.string.settings_high_contrast_sub),
                            checked = settings.highContrast,
                            onCheckedChange = { on -> onSettingsChange { it.copy(highContrast = on) } },
                        )
                    }
                    SettingsGroup(label = stringResource(R.string.settings_group_interaction)) {
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_large_targets),
                            subtitle = stringResource(R.string.settings_large_targets_sub),
                            checked = settings.largeTouchTargets,
                            onCheckedChange = { on -> onSettingsChange { it.copy(largeTouchTargets = on) } },
                        )
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_icon_labels),
                            subtitle = stringResource(R.string.settings_icon_labels_sub),
                            checked = settings.iconLabels,
                            onCheckedChange = { on -> onSettingsChange { it.copy(iconLabels = on) } },
                        )
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_haptics),
                            subtitle = stringResource(R.string.settings_haptics_sub),
                            checked = state.haptics,
                            onCheckedChange = onHapticsChanged,
                        )
                    }
                }

                // ------------------------------------------------------ privacy & security
                SettingsPage.PRIVACY -> {
                    // One statement at the top does the explaining; the rows stay short.
                    SettingsNote(
                        text = stringResource(R.string.settings_privacy_intro),
                        icon = Icons.Outlined.Shield,
                    )
                    SettingsGroup(label = stringResource(R.string.settings_group_privacy)) {
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_sensitive_warnings),
                            subtitle = stringResource(R.string.settings_sensitive_warnings_sub),
                            checked = settings.sensitiveWarnings,
                            onCheckedChange = { on -> onSettingsChange { it.copy(sensitiveWarnings = on) } },
                        )
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_clear_on_switch),
                            subtitle = stringResource(R.string.settings_clear_on_switch_sub),
                            checked = settings.clearSecretsOnToolSwitch,
                            onCheckedChange = { on -> onSettingsChange { it.copy(clearSecretsOnToolSwitch = on) } },
                        )
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_clear_on_background),
                            subtitle = stringResource(R.string.settings_clear_on_background_sub),
                            checked = settings.clearSecretsOnBackground,
                            onCheckedChange = { on -> onSettingsChange { it.copy(clearSecretsOnBackground = on) } },
                        )
                    }
                    // The only tinted group on the page: private-key protection is meant to be
                    // felt as security-sensitive before it is read.
                    SettingsEmphasisGroup(
                        label = stringResource(R.string.settings_group_private_keys),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        labelIcon = Icons.Outlined.Lock,
                    ) {
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_hide_private_preview),
                            subtitle = stringResource(R.string.settings_hide_private_preview_sub),
                            checked = settings.hidePrivateKeyPreview,
                            onCheckedChange = { on -> onSettingsChange { it.copy(hidePrivateKeyPreview = on) } },
                        )
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_confirm_private_copy),
                            subtitle = stringResource(R.string.settings_confirm_private_copy_sub),
                            checked = settings.confirmPrivateKeyCopy,
                            onCheckedChange = { on -> onSettingsChange { it.copy(confirmPrivateKeyCopy = on) } },
                        )
                    }
                }

                // ------------------------------------------------------------ data & reset
                SettingsPage.DATA -> {
                    SettingsGroup(label = stringResource(R.string.settings_group_data)) {
                        SettingsActionRow(
                            title = stringResource(R.string.settings_restore),
                            subtitle = stringResource(R.string.settings_restore_sub),
                            onClick = { confirmingRestore = true },
                        )
                        SettingsActionRow(
                            title = stringResource(R.string.settings_temporary_data),
                            subtitle = stringResource(R.string.settings_temporary_data_size, state.temporaryDataSize),
                            onClick = { confirmingClear = true },
                        )
                        SettingsActionRow(
                            title = stringResource(R.string.settings_reset_favorites),
                            subtitle = if (state.favorites.isEmpty()) {
                                // English maps 0 to "other", so the empty state needs its own string.
                                stringResource(R.string.settings_favourites_none)
                            } else {
                                pluralStringResource(
                                    R.plurals.settings_favourites_count, state.favorites.size, state.favorites.size,
                                )
                            },
                            onClick = { confirmingFavorites = true },
                        )
                    }
                    SettingsGroup(label = stringResource(R.string.settings_group_saved_data)) {
                        SettingsActionRow(
                            title = stringResource(R.string.settings_clear_keys),
                            subtitle = if (state.rsaSavedKeys.isEmpty()) {
                                stringResource(R.string.settings_saved_keys_none)
                            } else {
                                pluralStringResource(
                                    R.plurals.settings_saved_keys_count, state.rsaSavedKeys.size, state.rsaSavedKeys.size,
                                )
                            },
                            destructive = true,
                            onClick = { confirmingKeys = true },
                        )
                    }
                    // Destructive styling is reserved for where data actually dies - and this is
                    // the only group that wraps itself in warning colour.
                    SettingsEmphasisGroup(
                        label = stringResource(R.string.settings_group_danger_zone),
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        labelColor = MaterialTheme.colorScheme.error,
                        labelIcon = Icons.Outlined.WarningAmber,
                    ) {
                        SettingsActionRow(
                            title = stringResource(R.string.settings_clear_everything),
                            subtitle = stringResource(R.string.settings_clear_everything_sub),
                            destructive = true,
                            onClick = { confirmingEverything = true },
                        )
                    }
                }

                // ---------------------------------------------------------------- advanced
                SettingsPage.ADVANCED -> {
                    SettingsGroup(label = stringResource(R.string.settings_group_processing)) {
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_auto_process),
                            subtitle = stringResource(
                                if (state.autoProcess) R.string.settings_auto_process_sub else R.string.settings_manual_process_sub
                            ),
                            checked = state.autoProcess,
                            onCheckedChange = onAutoProcessChanged,
                        )
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_show_processing_time),
                            subtitle = stringResource(R.string.settings_show_processing_time_sub),
                            checked = settings.showProcessingTime,
                            onCheckedChange = { on -> onSettingsChange { it.copy(showProcessingTime = on) } },
                        )
                    }
                    SettingsGroup(label = stringResource(R.string.settings_group_feedback)) {
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_copy_confirmation),
                            subtitle = stringResource(R.string.settings_copy_confirmation_sub),
                            checked = state.copyConfirmation,
                            onCheckedChange = onCopyConfirmationChanged,
                        )
                    }
                    // Troubleshooting detail: present, but deliberately quiet (plain label, an
                    // intro line instead of a card) so it never dominates the page.
                    SettingsGroup(
                        label = stringResource(R.string.settings_diagnostics),
                        labelColor = mutedTextColor,
                    ) {
                        SettingsNote(text = stringResource(R.string.settings_diagnostics_body))
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_show_tool_id),
                            subtitle = stringResource(R.string.settings_show_tool_id_sub),
                            checked = settings.showToolId,
                            onCheckedChange = { on -> onSettingsChange { it.copy(showToolId = on) } },
                        )
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_show_detection),
                            subtitle = stringResource(R.string.settings_show_detection_sub),
                            checked = settings.showDetectionDetails,
                            onCheckedChange = { on -> onSettingsChange { it.copy(showDetectionDetails = on) } },
                        )
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_show_validation),
                            subtitle = stringResource(R.string.settings_show_validation_sub),
                            checked = settings.showValidationDetails,
                            onCheckedChange = { on -> onSettingsChange { it.copy(showValidationDetails = on) } },
                        )
                    }
                    SettingsGroup(label = stringResource(R.string.settings_group_tool_config)) {
                        // The same "reset remembered tool settings" as in Data & reset, offered
                        // where someone troubleshooting actually looks for it.
                        SettingsActionRow(
                            title = stringResource(R.string.settings_reset_tool_settings),
                            subtitle = stringResource(R.string.settings_reset_tool_settings_sub),
                            onClick = { confirmingClear = true },
                        )
                    }
                }

                // ------------------------------------------------------------------- about
                // A short identity block, then the facts as quiet label/value rows, then the
                // local-only statement. No settings, so no group labels.
                SettingsPage.ABOUT -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            text = stringResource(R.string.settings_about_version, versionName, versionCode),
                            style = MaterialTheme.typography.bodyMedium,
                            color = mutedTextColor,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                        Text(
                            text = pluralStringResource(R.plurals.settings_tools_count, toolCount, toolCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedTextColor,
                        )
                    }
                    Column {
                        SettingsInfoRow(label = stringResource(R.string.settings_build), value = buildType)
                        SettingsInfoRow(
                            label = stringResource(R.string.settings_developer),
                            value = stringResource(R.string.settings_developer_value),
                        )
                        SettingsInfoRow(
                            label = stringResource(R.string.settings_licenses),
                            value = stringResource(R.string.settings_licenses_sub),
                        )
                    }
                    SettingsNote(
                        text = stringResource(R.string.settings_local_only),
                        icon = Icons.Outlined.Info,
                    )
                    // The same support action as on the Settings root, kept at the very bottom of
                    // About so it never competes with the version, build and licence facts above.
                    SettingsSupportRow(
                        title = stringResource(R.string.support_buy_coffee),
                        subtitle = stringResource(R.string.support_buy_coffee_sub),
                        contentDescription = stringResource(R.string.support_buy_coffee_cd),
                        onClick = { onSupport() },
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

/** The label of a corner style, in the order the chooser lists them. */
private fun cornerStyleLabel(style: CornerStyle): Int = when (style) {
    CornerStyle.ROUNDED -> R.string.corner_style_rounded
    CornerStyle.SLIGHT -> R.string.corner_style_slight
    CornerStyle.SQUARE -> R.string.corner_style_square
}

private fun SettingsPage.titleRes(): Int = when (this) {
    SettingsPage.ROOT -> R.string.settings_title
    SettingsPage.APPEARANCE -> R.string.settings_appearance
    SettingsPage.ACCESSIBILITY -> R.string.settings_accessibility
    SettingsPage.PRIVACY -> R.string.settings_security
    SettingsPage.DATA -> R.string.settings_data_reset
    SettingsPage.ADVANCED -> R.string.settings_advanced
    SettingsPage.ABOUT -> R.string.settings_about
}

private fun SettingsPage.summaryRes(): Int = when (this) {
    SettingsPage.APPEARANCE -> R.string.settings_appearance_summary
    SettingsPage.ACCESSIBILITY -> R.string.settings_accessibility_summary
    SettingsPage.PRIVACY -> R.string.settings_security_summary
    SettingsPage.DATA -> R.string.settings_data_reset_summary
    SettingsPage.ADVANCED -> R.string.settings_advanced_summary
    SettingsPage.ABOUT -> R.string.settings_about_summary
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
