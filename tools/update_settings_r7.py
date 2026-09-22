import io

p = "app/src/main/kotlin/com/texthub/app/ui/SettingsScreen.kt"
s = io.open(p, encoding="utf-8").read()

# ---------------------------------------------------------------- imports
s = s.replace(
    "import androidx.compose.foundation.rememberScrollState",
    "import androidx.compose.foundation.rememberScrollState\nimport androidx.compose.material3.AlertDialog\nimport androidx.compose.material3.TextButton",
)
s = s.replace(
    "import androidx.compose.runtime.Composable",
    "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.getValue\nimport androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.remember\nimport androidx.compose.runtime.setValue",
)
s = s.replace(
    "import androidx.compose.material.icons.outlined.DeleteSweep",
    "import androidx.compose.material.icons.outlined.DeleteSweep\nimport androidx.compose.material.icons.outlined.Storage",
)

# ---------------------------------------------------------------- screen signature
old_sig = """    onCopyConfirmationChanged: (Boolean) -> Unit,
    onClearTemporaryData: () -> Unit,
    versionName: String,
) {"""
new_sig = """    onCopyConfirmationChanged: (Boolean) -> Unit,
    onClearTemporaryData: () -> Unit,
    versionName: String,
) {
    // The confirmation is a dialog, because "clear" used to happen silently and take the
    // favourites with it. Now the user sees exactly what goes and what stays before it does.
    var confirmingClear by remember { mutableStateOf(false) }"""
assert old_sig in s
s = s.replace(old_sig, new_sig)

# ---------------------------------------------------------------- replace the privacy card
old_card = s[s.index("            // ---------------------------------------------------------------- privacy"):s.index("            // ----------------------------------------------------------------- about")]
new_card = """            // ---------------------------------------------------------------- storage
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

"""
s = s.replace(old_card, new_card)

# ---------------------------------------------------------------- dialog
old_tail = """        }
    }
}

/**
 * Accent chooser"""
new_tail = """        }
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
 * Accent chooser"""
assert old_tail in s
s = s.replace(old_tail, new_tail, 1)

io.open(p, "w", encoding="utf-8").write(s)
print("SettingsScreen rewritten")
