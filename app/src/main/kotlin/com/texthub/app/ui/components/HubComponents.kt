package com.texthub.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texthub.app.R
import com.texthub.app.ui.theme.Density
import com.texthub.app.ui.theme.HubCorners
import com.texthub.app.ui.theme.Spacing
import com.texthub.app.ui.theme.decorativeSpec
import com.texthub.app.ui.theme.touchTargetMin
import com.texthub.app.ui.theme.cardContainerColor
import com.texthub.app.ui.theme.mutedTextColor
import com.texthub.core.model.Choice
import com.texthub.core.model.Classification
import com.texthub.core.model.ToolMeta

/** Standard card used by every section of the app. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val colors = CardDefaults.cardColors(containerColor = cardContainerColor)
    // Only add a clickable modifier when the card really is a button: text fields inside a
    // card must not have a pointer-input ancestor competing for taps.
    val cardModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .clickable { onClick() }
    } else {
        modifier.fillMaxWidth()
    }
    Card(modifier = cardModifier, shape = shape, colors = colors) {
        Column(Modifier.padding(Density.cardPadding)) { content() }
    }
}

/** Monogram badge for a tool: a short glyph in a tinted rounded square. */
@Composable
fun ToolMonogram(
    glyph: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    highlighted: Boolean = false,
) {
    val container = if (highlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    }
    val content = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val fontSize = when {
        glyph.length <= 2 -> 17.sp
        glyph.length == 3 -> 13.sp
        else -> 11.sp
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(HubCorners.monogram)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
            ),
            color = content,
            maxLines = 1,
        )
    }
}

/** Small pill that states what a method actually is (encoding / cipher / encryption). */
@Composable
fun ClassificationChip(classification: Classification, modifier: Modifier = Modifier) {
    val secure = classification.secure
    val container = if (secure) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (secure) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = HubCorners.chip,
        color = container,
        contentColor = content,
        modifier = modifier,
    ) {
        Text(
            text = classification.label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/** Two (or more) option selector used for the Encrypt/Decrypt switch. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = HubCorners.field
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val bg by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    animationSpec = decorativeSpec(200),
                    label = "segmentBackground",
                )
                val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(HubCorners.segment)
                        .background(bg)
                        // Selectable, not merely clickable: TalkBack then announces which of the
                        // segments is the current one instead of reading two identical labels.
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        )
                        .padding(horizontal = Spacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Label + value row used by the statistics line. */
@Composable
fun StatsLine(stats: com.texthub.core.model.TextStats, modifier: Modifier = Modifier) {
    Text(
        text = "${formatNumber(stats.chars)} ${plural(stats.chars, "character")} · " +
            "${formatNumber(stats.words)} ${plural(stats.words, "word")} · " +
            "${formatNumber(stats.lines)} ${plural(stats.lines, "line")}",
        style = MaterialTheme.typography.labelMedium,
        color = mutedTextColor,
        modifier = modifier,
    )
}

private fun plural(value: Int, word: String) = if (value == 1) word else word + "s"

private fun formatNumber(value: Int): String = String.format("%,d", value)

/** Friendly, non-technical error message with an icon. */
@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(HubCorners.banner)
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Section title (Input / Output / Parameters ...). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = mutedTextColor,
        modifier = modifier,
    )
}

@Composable
fun HubDivider(modifier: Modifier = Modifier) {
    Divider(
        modifier = modifier.padding(vertical = Spacing.sm),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        thickness = 1.dp,
    )
}

/** Text field with the app's shared look. */
@Composable
fun HubTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    readOnly: Boolean = false,
    monospace: Boolean = false,
    minHeight: Dp = 132.dp,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = false,
    maxLines: Int = 12,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (singleLine) 56.dp else minHeight),
        readOnly = readOnly,
        enabled = true,
        textStyle = (if (monospace) {
            MaterialTheme.typography.bodyMedium.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        } else {
            MaterialTheme.typography.bodyLarge
        }),
        placeholder = placeholder?.let {
            { Text(it, style = MaterialTheme.typography.bodyMedium, color = mutedTextColor) }
        },
        shape = HubCorners.field,
        isError = isError,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            autoCorrect = false,
            keyboardType = keyboardType,
        ),
        visualTransformation = visualTransformation,
        trailingIcon = trailing,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
        maxLines = if (singleLine) 1 else maxLines,
    )
}

/** Number parameter editor with - / + steppers. */
@Composable
fun NumberStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int,
    modifier: Modifier = Modifier,
    label: String = "",
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
        // Resolved here: the semantics blocks inside the buttons are not composable scopes, and the
        // wording belongs to the string resources like every other description in the app.
        val decreaseLabel = stringResource(R.string.cd_decrease)
        val increaseLabel = stringResource(R.string.cd_increase)
        val shape = HubCorners.stepper
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onValueChange((value - 1).coerceAtLeast(min)) }) {
                    Icon(Icons.Outlined.Remove, contentDescription = decreaseLabel)
                }
                Box(
                    modifier = Modifier
                        .widthIn(min = 56.dp)
                        .height(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    var text by remember(value) { mutableStateOf(value.toString()) }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { raw ->
                            text = raw.filter { it.isDigit() || it == '-' }
                            val parsed = text.toIntOrNull()
                            if (parsed != null) onValueChange(parsed.coerceIn(min, max))
                        },
                        modifier = Modifier.widthIn(min = 56.dp, max = 92.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = HubCorners.stepper,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                    )
                }
                IconButton(onClick = { onValueChange((value + 1).coerceAtMost(max)) }) {
                    Icon(Icons.Outlined.Add, contentDescription = increaseLabel)
                }
            }
        }
    }
}

/** Dropdown for parameters with a fixed set of choices. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChoiceDropdown(
    label: String,
    options: List<Choice>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.id == selectedId } ?: options.first()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = current.label,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = HubCorners.field,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        onSelect(option.id)
                        expanded = false
                    },
                    contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
            }
        }
    }
}

/** Compact icon + label action used in the input/output headers. */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val tint = if (enabled) color else mutedTextColor.copy(alpha = 0.5f)
    Box(
        modifier = modifier
            // The pill stays visually the same size; only the touchable area grows to the
            // accessible minimum, so Copy / Paste / Clear are easy to hit and nothing shifts.
            .minimumInteractiveComponentSize()
            .heightIn(min = touchTargetMin)
            .clip(HubCorners.chip)
            .clickable(enabled = enabled) { onClick() }
            .semantics {
                // Distinguishes look-alike actions for TalkBack ("Copy public key" vs
                // "Copy private key"). Never carries content - only the label of the action.
                contentDescription?.let { this.contentDescription = it }
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
        )
    }
}

/** Outlined secondary action button. */
@Composable
fun SecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
) {
    val border = if (enabled) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }
    val contentColor = if (enabled) MaterialTheme.colorScheme.primary else mutedTextColor.copy(alpha = 0.6f)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = HubCorners.button,
        color = Color.Transparent,
        contentColor = contentColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, border),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Box(Modifier.padding(end = Spacing.sm)) { icon() }
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Primary action button. */
@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = HubCorners.button,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Box(Modifier.padding(end = Spacing.sm)) { icon() }
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
