package com.texthub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.texthub.app.R
import com.texthub.app.ui.components.ClassificationChip
import com.texthub.app.ui.components.ToolMonogram
import com.texthub.app.ui.theme.Spacing
import com.texthub.app.ui.theme.mutedTextColor
import com.texthub.core.ToolRegistry
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolMeta

private enum class PickerFilter { ALL, FAVORITES, RECENT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolPickerSheet(
    sheetState: SheetState,
    currentToolId: String,
    favorites: Set<String>,
    recents: List<String>,
    onSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(PickerFilter.ALL) }
    // Hoisted, so the scroll position survives recomposition (toggling a favourite redraws the list).
    val listState = rememberLazyListState()

    val tools = remember(query, filter, favorites, recents) {
        val searched = ToolRegistry.search(query)
        when (filter) {
            PickerFilter.ALL -> searched
            PickerFilter.FAVORITES -> searched.filter { it.id in favorites }
            PickerFilter.RECENT -> recents.mapNotNull { id -> ToolRegistry.all.firstOrNull { it.meta.id == id }?.meta }
                .filter { it.searchIndex.contains(query.trim().lowercase()) }
        }
    }

    val grouped = remember(tools) {
        tools.groupBy { it.category }.toList().sortedBy { (category, _) -> category.ordinal }
    }

    // A new search or filter starts at the top again instead of keeping a stale scroll offset.
    LaunchedEffect(query, filter) {
        if (tools.isNotEmpty()) listState.scrollToItem(0)
    }

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
        // The sheet gets a definite height and the list is `weight(1f)`. With `fillMaxSize()` the
        // list claimed the whole sheet height on top of the header, so the content overflowed the
        // sheet: the last rows were unreachable and the list re-measured on every drag, which is
        // what made scrolling stutter.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = Spacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.picker_count, tools.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = mutedTextColor,
                )
            }

            Spacer(Modifier.height(Spacing.md))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.picker_search), color = mutedTextColor) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            )

            Spacer(Modifier.height(Spacing.md))

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = filter == PickerFilter.ALL,
                    onClick = { filter = PickerFilter.ALL },
                    label = { Text(stringResource(R.string.picker_all)) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
                FilterChip(
                    selected = filter == PickerFilter.FAVORITES,
                    onClick = { filter = PickerFilter.FAVORITES },
                    label = { Text(stringResource(R.string.picker_favorites)) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
                FilterChip(
                    selected = filter == PickerFilter.RECENT,
                    onClick = { filter = PickerFilter.RECENT },
                    label = { Text(stringResource(R.string.picker_recent)) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }

            Spacer(Modifier.height(Spacing.sm))

            if (tools.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.picker_no_results, query),
                        style = MaterialTheme.typography.bodyMedium,
                        color = mutedTextColor,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = Spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    grouped.forEach { (category, items) ->
                        if (filter == PickerFilter.ALL) {
                            item(key = "header_${category.name}") {
                                Text(
                                    text = category.label.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = mutedTextColor,
                                    modifier = Modifier.padding(start = Spacing.sm, top = Spacing.md, bottom = Spacing.xs),
                                )
                            }
                        }
                        items(
                            items = items,
                            key = { it.id },
                            contentType = { "tool" },
                        ) { meta ->
                            ToolRow(
                                meta = meta,
                                selected = meta.id == currentToolId,
                                favorite = meta.id in favorites,
                                onClick = { onSelect(meta.id) },
                                onToggleFavorite = { onToggleFavorite(meta.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolRow(
    meta: ToolMeta,
    selected: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolMonogram(glyph = meta.glyph, highlighted = selected)
        Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
            Text(
                text = meta.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
            )
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClassificationChip(classification = meta.classification)
            }
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                contentDescription = stringResource(
                    if (favorite) R.string.cd_unfavorite else R.string.cd_favorite
                ),
                tint = if (favorite) MaterialTheme.colorScheme.primary else mutedTextColor,
            )
        }
    }
}
