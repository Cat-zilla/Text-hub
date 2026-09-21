package com.texthub.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.texthub.app.R
import com.texthub.app.ui.components.ClassificationChip
import com.texthub.app.ui.components.HubDivider
import com.texthub.app.ui.components.ToolMonogram
import com.texthub.app.ui.theme.Spacing
import com.texthub.app.ui.theme.mutedTextColor
import com.texthub.core.model.ToolMeta

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolInfoSheet(
    sheetState: SheetState,
    meta: ToolMeta,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                shape = RoundedCornerShape(50),
            ) {
                Box(Modifier.size(width = 36.dp, height = 4.dp))
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xxl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToolMonogram(glyph = meta.glyph, size = 48.dp, highlighted = true)
                Column(modifier = Modifier.padding(start = Spacing.md).weight(1f)) {
                    Text(text = meta.name, style = MaterialTheme.typography.titleLarge)
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        ClassificationChip(classification = meta.classification)
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            Text(text = meta.info.summary, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(Spacing.md))

            FactRow(
                icon = Icons.Outlined.Key,
                label = stringResource(R.string.info_requires_key),
                value = if (meta.info.requiresKey) "Yes" else "No",
            )
            FactRow(
                icon = if (meta.info.useCases.isEmpty()) Icons.Outlined.Info else Icons.Outlined.Shield,
                label = stringResource(R.string.info_security),
                value = stringResource(
                    if (meta.classification.secure) R.string.info_secure else R.string.info_not_secure
                ),
            )

            if (meta.info.useCases.isNotEmpty()) {
                HubDivider()
                SectionLabel(stringResource(R.string.info_use_cases))
                meta.info.useCases.forEach { useCase ->
                    BulletLine(text = useCase)
                }
            }

            if (meta.info.warnings.isNotEmpty()) {
                HubDivider()
                SectionLabel(stringResource(R.string.info_warnings))
                meta.info.warnings.forEach { warning ->
                    Row(modifier = Modifier.padding(top = 6.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp).padding(top = 2.dp),
                        )
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = Spacing.sm).weight(1f),
                        )
                    }
                }
            }

            meta.info.convention?.let { convention ->
                HubDivider()
                SectionLabel(stringResource(R.string.info_convention))
                Text(
                    text = convention,
                    style = MaterialTheme.typography.bodySmall,
                    color = mutedTextColor,
                )
            }
        }
    }
}

@Composable
private fun FactRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = mutedTextColor,
            modifier = Modifier.padding(start = Spacing.sm).weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = mutedTextColor,
        modifier = Modifier.padding(bottom = Spacing.xs),
    )
}

@Composable
private fun BulletLine(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}
