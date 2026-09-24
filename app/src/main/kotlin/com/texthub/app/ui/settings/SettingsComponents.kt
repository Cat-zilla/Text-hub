package com.texthub.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.texthub.app.R
import com.texthub.app.ui.theme.AccentOption
import com.texthub.app.ui.theme.HubPalette
import com.texthub.app.ui.theme.LocalUiSettings
import com.texthub.app.ui.theme.Spacing
import com.texthub.app.ui.theme.mutedTextColor
import com.texthub.app.ui.theme.touchTargetMin
import com.texthub.core.prefs.LayoutDensity

/**
 * The Settings design system. A Settings page is a short stack of labelled groups on the plain
 * background - whitespace does the separating, so there are no cards around ordinary groups and no
 * dividers at all. Three pieces do all the work:
 *
 *  * [SettingsGroup]        - a small heading plus its rows. Used for every ordinary group.
 *  * [SettingsEmphasisGroup] - the same group in a quiet tinted container. Reserved for groups
 *    whose meaning needs visual weight (private keys, the danger zone); using it anywhere else
 *    would take its meaning away.
 *  * the row vocabulary     - [SettingsSwitchRow] for booleans, [SettingsValueRow] for choices
 *    (the current value is shown instead of a control), [SettingsNavRow] for destinations,
 *    [SettingsActionRow] for one-shot actions.
 *
 * Subtitles exist only where they say something the title and the current value do not.
 */

// --------------------------------------------------------------- spacing

/** Distance between two groups on a Settings page; the main separator of the visual hierarchy. */
internal val SettingsGroupGap: Dp
    @Composable @ReadOnlyComposable
    get() = if (LocalUiSettings.current.density == LayoutDensity.COMPACT) Spacing.xl else 28.dp

/** Vertical padding inside one row; rows touch each other, the padding keeps them apart. */
private val rowPaddingVertical: Dp
    @Composable @ReadOnlyComposable
    get() = if (LocalUiSettings.current.density == LayoutDensity.COMPACT) Spacing.sm else 10.dp

private val IconTileSize = 40.dp
private val DialogRowMinHeight = 48.dp

// --------------------------------------------------------------- groups

/**
 * One labelled group of settings, topmost separator of a page: a small heading and its rows,
 * separated from the next group by whitespace alone.
 */
@Composable
internal fun SettingsGroup(
    label: String,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsGroupLabel(label, color = labelColor)
        Column(content = content)
    }
}

/**
 * [SettingsGroup] inside a quiet tinted container. Reserved for [SettingsGroup.PRIVATE_KEYS] and
 * [SettingsGroup.DANGER_ZONE]: security-sensitive and destructive groups are meant to be felt
 * before they are read. Icon and tint say why; the rows inside look like rows anywhere else.
 */
@Composable
internal fun SettingsEmphasisGroup(
    label: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.primary,
    labelIcon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            SettingsGroupLabel(label, color = labelColor, icon = labelIcon)
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsGroupLabel(
    text: String,
    color: Color,
    icon: ImageVector? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = Spacing.xs),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(text = text, style = MaterialTheme.typography.titleSmall, color = color)
    }
}

/**
 * A short statement that is not a setting (the local-only note, the diagnostics intro). Read-only,
 * quiet, and clearly not a row: no control, no click, smaller type.
 */
@Composable
internal fun SettingsNote(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = mutedTextColor,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(16.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = mutedTextColor)
    }
}

// --------------------------------------------------------------- row frame

/**
 * The shared shape of every Settings row: optional leading slot, title (+ optional one-line
 * subtitle), optional trailing control. Title is bodyLarge; the subtitle is bodySmall and appears
 * only when it carries information the title and the control do not.
 */
@Composable
private fun SettingsRowFrame(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    titleColor: Color = Color.Unspecified,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = touchTargetMin)
            .padding(vertical = rowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Spacing.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = mutedTextColor,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Spacing.md))
            trailing()
        }
    }
}

// ----------------------------------------------------------------- rows

/** A boolean. The whole row is the switch: one full-width accessible target. */
@Composable
internal fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    SettingsRowFrame(
        title = title,
        subtitle = subtitle,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .toggleable(
                value = checked,
                role = Role.Switch,
                enabled = enabled,
                onValueChange = onCheckedChange,
            )
            .alpha(if (enabled) 1f else 0.5f),
        trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

/**
 * A setting whose current value matters more than its control: the value is shown on the right
 * ("Text size — System >") and the row opens a [SettingsChoiceDialog]. Scans far better than a
 * segmented control per setting and keeps the page shallow.
 */
@Composable
internal fun SettingsValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    SettingsRowFrame(
        title = title,
        subtitle = subtitle,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) mutedTextColor else mutedTextColor.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = mutedTextColor,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(18.dp),
                )
            }
        },
    )
}

/** The accent row: an ordinary value row whose value is the colour dot plus its name. */
@Composable
internal fun SettingsAccentRow(
    title: String,
    accent: AccentOption,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    SettingsRowFrame(
        title = title,
        subtitle = subtitle,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = accent.swatch(dark),
                    modifier = Modifier.size(14.dp),
                ) {}
                Text(
                    text = stringResource(accentLabelRes(accent)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = mutedTextColor,
                    maxLines = 1,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = mutedTextColor,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(18.dp),
                )
            }
        },
    )
}

/**
 * A destination on the root page: a tonal icon tile, title and one-line summary, chevron. The
 * chevron is what separates "opens a page" from [SettingsActionRow] ("does something here").
 */
@Composable
internal fun SettingsNavRow(
    title: String,
    summary: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    SettingsRowFrame(
        title = title,
        subtitle = summary,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick),
        leading = {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.size(IconTileSize),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        trailing = {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = mutedTextColor,
                modifier = Modifier.size(20.dp),
            )
        },
    )
}

/**
 * A one-shot action (Restore, Reset, Clear). The whole row is the button; there is deliberately
 * no trailing widget, no chevron and no wordy action button, so actions are not mistaken for
 * switches or navigation. Only destructive actions are tinted - and they are the only red text
 * in Settings.
 */
@Composable
internal fun SettingsActionRow(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    SettingsRowFrame(
        title = title,
        subtitle = subtitle,
        titleColor = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f),
    )
}

/** A read-only fact about the app (About page): label on the left, value on the right. */
@Composable
internal fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = touchTargetMin)
            .padding(vertical = rowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = mutedTextColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

// --------------------------------------------------------------- choices

/**
 * The chooser behind a [SettingsValueRow]: a quiet dialog of radio rows. Picking a value applies
 * it and closes the dialog; there is no second step for changing one's mind, because choosing is
 * already one tap.
 */
@Composable
internal fun SettingsChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (message != null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedTextColor,
                        modifier = Modifier.padding(bottom = Spacing.md),
                    )
                }
                // A radio group: picking applies immediately, so the options are selectable rows
                // with the selected state announced, not bare buttons.
                Column(modifier = Modifier.selectableGroup()) {
                    options.forEachIndexed { index, label ->
                        val selected = index == selectedIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = DialogRowMinHeight)
                                .clip(MaterialTheme.shapes.medium)
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onSelect(index)
                                        onDismiss()
                                    },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = Spacing.sm),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}

/**
 * Accent chooser: a swatch per colour, chosen like a radio group. The selection is shown by a
 * check mark as well as by the border, so it never depends on colour alone. Shown inside the
 * accent dialog rather than on the page, where a two-row grid would dominate everything else.
 */
@Composable
internal fun AccentPicker(selected: AccentOption, onSelect: (AccentOption) -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    HubPalette.Accents.chunked(4).forEach { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs),
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

internal fun accentLabelRes(accent: AccentOption): Int = when (accent) {
    AccentOption.TEAL -> R.string.accent_teal
    AccentOption.BLUE -> R.string.accent_blue
    AccentOption.INDIGO -> R.string.accent_indigo
    AccentOption.VIOLET -> R.string.accent_violet
    AccentOption.ROSE -> R.string.accent_rose
    AccentOption.AMBER -> R.string.accent_amber
    AccentOption.GREEN -> R.string.accent_green
    AccentOption.GRAPHITE -> R.string.accent_graphite
}
