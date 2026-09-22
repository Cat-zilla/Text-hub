import io

p = "app/src/main/kotlin/com/texthub/app/ui/MainScreen.kt"
s = io.open(p, encoding="utf-8").read()

# ------------------------------------------------------------------ direction switch
old = """                // ------------------------------------------------------- direction switch
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
"""
new = """                // ------------------------------------------------------- direction switch
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
                    if (meta.symmetric && state.showDirection) {
                        Text(
                            text = stringResource(R.string.msg_symmetric_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedTextColor,
                            modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xs),
                        )
                    }
                }
"""
assert old in s
s = s.replace(old, new, 1)

# ------------------------------------------------------------------ parameters
old = """                // ------------------------------------------------------------ parameters
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
"""
new = """                // ------------------------------------------------------------ parameters
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
"""
assert old in s
s = s.replace(old, new, 1)

# ------------------------------------------------------------------ swap button
old = """                    // Swap: moves the result into the input, flips the mode (Encode <-> Decode)
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
                    Spacer(Modifier.height(Spacing.sm))"""
new = """                    // Swap: moves the result into the input and, when the tool has a reverse
                    // direction, flips the mode and processes again - one tap for a round trip.
                    // Tools that produce a final answer (a digest, a measurement, a comparison)
                    // do not offer it at all.
                    if (state.showSwap) {
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
                    }"""
assert old in s
s = s.replace(old, new, 1)

# ------------------------------------------------------------------ param editor + inline validation
old = """@Composable
private fun ParameterEditor(
    spec: ParamSpec,
    value: String,
    onValueChange: (String) -> Unit,
) {"""
new = """/**
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
) {"""
assert old in s
s = s.replace(old, new, 1)

# ------------------------------------------------------------------ signature + imports
old = """    onClearInput: () -> Unit,
    onClearOutput: () -> Unit,
) {"""
new = """    onClearInput: () -> Unit,
    onClearOutput: () -> Unit,
    onResetParams: () -> Unit,
) {"""
assert old in s
s = s.replace(old, new, 1)

s = s.replace(
    "import androidx.compose.foundation.rememberScrollState",
    "import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.rememberScrollState",
)
s = s.replace(
    "import androidx.compose.material.icons.outlined.ExpandMore",
    "import androidx.compose.material.icons.outlined.ExpandLess\nimport androidx.compose.material.icons.outlined.ExpandMore",
)
s = s.replace(
    "import com.texthub.app.ui.components.ErrorBanner",
    "import com.texthub.app.ui.components.ErrorBanner\nimport com.texthub.app.ui.components.HubDivider",
)
s = s.replace(
    "import com.texthub.core.model.ParamSpec",
    "import com.texthub.core.model.ParamSpec\nimport com.texthub.core.model.ToolCategory",
)
s = s.replace(
    "import androidx.compose.runtime.remember",
    "import androidx.compose.runtime.getValue\nimport androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.remember\nimport androidx.compose.runtime.setValue",
)

io.open(p, "w", encoding="utf-8").write(s)
print("MainScreen updated")
