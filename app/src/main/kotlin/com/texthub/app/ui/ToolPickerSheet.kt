package com.texthub.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.pluralStringResource
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
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex

private enum class PickerFilter { ALL, FAVORITES, RECENT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolPickerSheet(
    sheetState: SheetState,
    currentToolId: String,
    favorites: List<String>,
    recents: List<String>,
    onSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onMoveFavorite: (Int, Int) -> Unit,
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
            PickerFilter.FAVORITES -> favorites.mapNotNull { id ->
                ToolRegistry.all.firstOrNull { it.meta.id == id }?.meta
            }
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
                    text = pluralStringResource(R.plurals.picker_count, tools.size, tools.size),
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

            // Reordering only exists here, in the favourites list: the full tool list, search
            // results, the categories and the recents have a fixed, meaningful order, so they do
            // not accept a drag.
            if (filter == PickerFilter.FAVORITES && tools.size > 1 && query.isBlank()) {
                Text(
                    text = stringResource(R.string.picker_favorites_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedTextColor,
                    modifier = Modifier.padding(start = Spacing.sm, top = Spacing.xs),
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
                        text = when {
                            filter == PickerFilter.FAVORITES && favorites.isEmpty() ->
                                stringResource(R.string.picker_no_favorites)
                            else -> stringResource(R.string.picker_no_results, query)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = mutedTextColor,
                    )
                }
            } else if (filter == PickerFilter.FAVORITES) {
                // The Favourites section is the only list that can be dragged: the main list, search
                // results and the categories keep their fixed order.
                FavoritesToolList(
                    metas = tools,
                    currentToolId = currentToolId,
                    // Reordering is only offered on the plain favourites list: while a search is
                    // active the rows shown are a subset, and dropping one would move the wrong
                    // entry in the stored order.
                    reorderable = query.isBlank(),
                    onSelect = onSelect,
                    onToggleFavorite = onToggleFavorite,
                    onMove = onMoveFavorite,
                    listState = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
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

/**
 * The favourites list with long-press drag-and-drop reordering.
 *
 * The favourites list, and only the favourites list, can be reordered.
 *
 * How the gesture works, and why it no longer wobbles:
 *
 *  * long-pressing a row lifts it - it is drawn raised, slightly enlarged and above the others - and
 *    it then follows the finger exactly, with a haptic tick to confirm the lift;
 *  * **the list order does not change while the drag is in progress**. The previous version moved
 *    the row one slot at a time with a placement animation running at the same time as the visual
 *    translation, and it advanced the slot as soon as half a row had been covered. The animation and
 *    the translation fought each other and the half-row threshold flipped between two slots from
 *    one frame to the next, which is what made the row jump up and down;
 *  * the slot the row would land on is shown instead, as a highlighted row that the floating row
 *    moves over;
 *  * near the top or the bottom edge the list follows the finger, a controlled number of pixels per
 *    frame, and the visual offset is corrected by exactly the amount scrolled;
 *  * releasing the finger commits the move once ([onMove]), so the stored order is written a single
 *    time per drag rather than on every frame.
 *
 * Row height is measured from the real rows, and the step used for the arithmetic is that height
 * plus the gap between rows - the missing gap was the second reason the old threshold wobbled.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoritesToolList(
    metas: List<ToolMeta>,
    currentToolId: String,
    reorderable: Boolean,
    onSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val currentMetas by rememberUpdatedState(metas)
    val currentOnMove by rememberUpdatedState(onMove)

    val gapPx = with(density) { Spacing.xs.toPx() }
    val edgePx = with(density) { 56.dp.toPx() }
    val maxScrollStepPx = with(density) { 12.dp.toPx() }

    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var rowHeight by remember { mutableStateOf(0f) }
    var edgeScroll by remember { mutableStateOf(0f) }

    val stepPx = rowHeight + gapPx
    val startIndex = draggingId?.let { id -> currentMetas.indexOfFirst { it.id == id } } ?: -1
    val targetIndex = if (startIndex >= 0) {
        dropTargetIndex(startIndex, dragOffset, stepPx, currentMetas.lastIndex)
    } else {
        -1
    }

    // Edge auto-scroll: the list keeps moving while the finger stays near an edge, and the visual
    // offset is adjusted by exactly the amount that was scrolled, so the row stays under the finger.
    LaunchedEffect(draggingId) {
        while (draggingId != null) {
            val step = edgeScroll
            if (step != 0f) {
                dragOffset += listState.scrollBy(step)
            }
            withFrameNanos { }
        }
    }

    // A favourite that disappeared (star tapped during a drag) must not leave a floating row behind.
    if (draggingId != null && startIndex < 0) {
        draggingId = null
        dragOffset = 0f
        edgeScroll = 0f
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        itemsIndexed(items = metas, key = { _, meta -> meta.id }, contentType = { _, _ -> "tool" }) { index, meta ->
            val dragging = meta.id == draggingId
            val isDropSlot = !dragging && index == targetIndex && targetIndex != startIndex
            Surface(
                color = when {
                    dragging -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                    isDropSlot -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    else -> Color.Transparent
                },
                shadowElevation = if (dragging) 8.dp else 0.dp,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (dragging) Modifier else Modifier.animateItemPlacement())
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        if (dragging) {
                            translationY = dragOffset
                            scaleX = 1.02f
                            scaleY = 1.02f
                        }
                    }
                    .onGloballyPositioned { coordinates ->
                        // Rows are uniform; the measurement is taken while nothing is lifted, so a
                        // translated row can never feed its own offset back into the arithmetic.
                        if (draggingId == null) {
                            val height = coordinates.size.height.toFloat()
                            if (height > 0f && height != rowHeight) rowHeight = height
                        }
                    }
                    .pointerInput(meta.id, reorderable) {
                        if (!reorderable) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingId = meta.id
                                dragOffset = 0f
                                edgeScroll = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val from = currentMetas.indexOfFirst { it.id == meta.id }
                                if (from < 0 || rowHeight <= 0f) {
                                    // Nothing to compute with: end the drag rather than guess.
                                    draggingId = null
                                    dragOffset = 0f
                                    edgeScroll = 0f
                                    return@detectDragGesturesAfterLongPress
                                }
                                val info = listState.layoutInfo
                                edgeScroll = autoScrollDelta(
                                    rowTopPx = from * stepPx + dragOffset,
                                    rowHeightPx = rowHeight,
                                    viewportStartPx = info.viewportStartOffset.toFloat(),
                                    viewportHeightPx = (info.viewportEndOffset - info.viewportStartOffset).toFloat(),
                                    edgePx = edgePx,
                                    maxStepPx = maxScrollStepPx,
                                )
                            },
                            onDragEnd = {
                                val id = draggingId
                                if (id != null) {
                                    val from = currentMetas.indexOfFirst { it.id == id }
                                    val to = dropTargetIndex(from, dragOffset, stepPx, currentMetas.lastIndex)
                                    if (from >= 0 && to >= 0 && to != from) currentOnMove(from, to)
                                }
                                draggingId = null
                                dragOffset = 0f
                                edgeScroll = 0f
                            },
                            onDragCancel = {
                                draggingId = null
                                dragOffset = 0f
                                edgeScroll = 0f
                            },
                        )
                    },
            ) {
                ToolRow(
                    meta = meta,
                    selected = meta.id == currentToolId,
                    favorite = true,
                    reorderable = reorderable,
                    dragging = dragging,
                    onClick = { onSelect(meta.id) },
                    onToggleFavorite = { onToggleFavorite(meta.id) },
                )
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
    reorderable: Boolean = false,
    dragging: Boolean = false,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        Color.Transparent
    }
    // Resolved here: the semantics block below is not a composable scope.
    val dragLabel = stringResource(R.string.cd_drag_favorite)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (reorderable) {
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = dragLabel,
                tint = if (dragging) MaterialTheme.colorScheme.primary else mutedTextColor,
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 2.dp),
            )
        }
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
