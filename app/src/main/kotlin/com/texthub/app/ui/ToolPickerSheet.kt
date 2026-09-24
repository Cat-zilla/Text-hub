package com.texthub.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.derivedStateOf
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
import com.texthub.app.ui.theme.LocalUiSettings
import com.texthub.app.ui.theme.decorativeDuration
import com.texthub.app.ui.theme.decorativeSpec
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
    hapticsEnabled: Boolean,
    currentToolId: String,
    favorites: List<String>,
    recents: List<String>,
    onSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onMoveFavorite: (String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(PickerFilter.ALL) }
    val haptics = LocalHapticFeedback.current

    // Every favourite toggle - in the search list, the recents and the favourites - answers with
    // the same light tick, and only when the user kept haptics on. Compose's haptics go through
    // the view's, so the system haptic setting is respected on top of this preference.
    val toggleFavoriteWithTick: (String) -> Unit = { id ->
        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onToggleFavorite(id)
    }
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
                    hapticsEnabled = hapticsEnabled,
                    // Reordering is only offered on the plain favourites list. While a search is
                    // active the rows shown are a subset, and a drag would be a guess at where the
                    // hidden rows belong. The move itself is stored by id, so even the favourites
                    // list, which can skip entries, is mapped back to the stored order exactly.
                    reorderable = query.isBlank(),
                    favorites = favorites,
                    onSelect = onSelect,
                    onToggleFavorite = toggleFavoriteWithTick,
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
                                onToggleFavorite = { toggleFavoriteWithTick(meta.id) },
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
 * The favourites list, and only the favourites list, can be reordered. How one drag works:
 *
 *  * long-pressing a row lifts it - drawn raised, above the others - and from then on it follows the
 *    finger exactly (`translationY = dragOffset`), with a haptic tick to confirm the lift;
 *  * the list order does **not** change while the gesture runs. The rows between the dragged row and
 *    its target slide one row aside - animated, exactly one row each - to open the gap it will be
 *    dropped into. Nothing is scaled, nothing moves twice, and no row keeps an offset afterwards;
 *  * the gap and the stored order come from the same [resolveDragDrop] call, so the row is dropped
 *    exactly where the user saw the gap open;
 *  * releasing writes the new order once ([onMove]) and then animates the small leftover distance
 *    between the release point and the final slot down to zero: the row *settles* into the position
 *    that was just persisted, instead of snapping there or keeping a permanent offset;
 *  * a cancelled drag only clears the state - the stored order is untouched;
 *  * near the top or the bottom edge the list scrolls under the finger, and the visual offset is
 *    corrected by exactly the amount that was scrolled.
 *
 * The move is stored by **id** (the dragged row, and the row it is dropped in front of), never by
 * counting screen positions: the favourites list on screen can be a subsequence of the stored one
 * (a favourite whose tool no longer exists is skipped), and positions would then move the wrong
 * entry. Row height is measured from the real rows while nothing is lifted, and the step used for
 * the arithmetic is that height plus the gap between rows.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoritesToolList(
    metas: List<ToolMeta>,
    currentToolId: String,
    reorderable: Boolean,
    hapticsEnabled: Boolean,
    favorites: List<String>,
    onSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onMove: (String, String?) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    val displayedIds = metas.map { it.id }
    val currentIds by rememberUpdatedState(displayedIds)
    val currentFavorites by rememberUpdatedState(favorites)
    val currentOnMove by rememberUpdatedState(onMove)

    val gapPx = with(density) { Spacing.xs.toPx() }
    val edgePx = with(density) { 56.dp.toPx() }
    val maxScrollStepPx = with(density) { 12.dp.toPx() }

    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var rowHeight by remember { mutableStateOf(0f) }
    var edgeScroll by remember { mutableStateOf(0f) }
    // After the drop: the row that was just moved, and the distance left between the release point
    // and the slot that was stored. Animating that to zero is the "settle" step.
    var settlingId by remember { mutableStateOf<String?>(null) }
    var settleFrom by remember { mutableStateOf(0f) }
    val settleAnimation = remember { Animatable(0f) }

    val stepPx = rowHeight + gapPx
    val dragged = draggingId
    val startIndex = if (dragged == null) -1 else displayedIds.indexOf(dragged)

    // Event-time reads for everything the gesture handlers and the gap calculation use.
    //
    // The pointerInput block below only restarts when its *keys* change (meta.id, reorderable) -
    // recomposition swaps in a new lambda, but with equal keys the running handler keeps the one it
    // was started with, along with whatever local values it captured. At the first composition
    // rowHeight is still 0, so a captured `stepPx` would be just the gap between rows for the whole
    // life of the row: every real drag would read as dozens of rows and the drop would land at the
    // end of the list. Reading through `rememberUpdatedState` (and the delegated state above) gives
    // the handlers the values of *this* moment, which is what the arithmetic assumes.
    val currentStepPx by rememberUpdatedState(stepPx)
    val currentDisplayedIds by rememberUpdatedState(displayedIds)
    val currentEdgePx by rememberUpdatedState(edgePx)
    val currentMaxScrollStepPx by rememberUpdatedState(maxScrollStepPx)

    // The offset changes on every pointer event, but the *gap* only changes when the target slot
    // does. Keeping the resolution behind derivedStateOf means the rows are recomposed when they
    // really move - not sixty times a second while nothing changes - which is what keeps the drag
    // even on long lists. Inside the row, the offset itself is read in the graphics layer, so
    // following the finger costs a redraw rather than a recomposition.
    val shifts by remember {
        derivedStateOf {
            // Reads go through the remembered State holders (not the composition's locals), so the
            // gap is computed from the current rows, the current step and the current offset.
            val moving = draggingId
            if (moving == null) {
                List(currentDisplayedIds.size) { 0 }
            } else {
                val from = currentDisplayedIds.indexOf(moving)
                if (from < 0 || currentStepPx <= 0f) {
                    List(currentDisplayedIds.size) { 0 }
                } else {
                    val to = resolveDragDrop(
                        stored = currentFavorites,
                        displayed = currentDisplayedIds,
                        draggedId = moving,
                        dragOffsetPx = dragOffset,
                        stepPx = currentStepPx,
                    )?.targetIndex ?: from
                    shiftRows(currentDisplayedIds.size, from, to)
                }
            }
        }
    }

    // Edge auto-scroll: the list keeps moving while the finger stays near an edge, and the visual
    // offset is adjusted by exactly the amount that was scrolled, so the row stays under the finger.
    // The scroll amount is scaled by the real frame time, so a 120 Hz screen follows at the same
    // speed as a 60 Hz one instead of twice as fast.
    LaunchedEffect(draggingId) {
        var previousFrame = 0L
        while (draggingId != null) {
            val frame = withFrameNanos { it }
            val seconds = if (previousFrame == 0L) 0f else ((frame - previousFrame) / 1_000_000_000.0).toFloat()
            previousFrame = frame
            // autoScrollDelta is expressed per 60 Hz frame; a frame's worth of movement is that
            // value scaled by how long the frame actually took, so the list follows at the same
            // speed on a 60 Hz and a 120 Hz screen.
            val step = autoScrollForFrame(edgeScroll, seconds)
            if (step != 0f) dragOffset += listState.scrollBy(step)
        }
    }

    // The settle step: snap to the leftover distance, animate it away, then forget the row.
    val settleMs = decorativeDuration(160)
    LaunchedEffect(settlingId, settleFrom) {
        if (settlingId != null) {
            settleAnimation.snapTo(settleFrom)
            if (settleMs <= 0) settleAnimation.snapTo(0f) else settleAnimation.animateTo(0f, tween(durationMillis = settleMs, easing = LinearOutSlowInEasing))
            settlingId = null
            settleFrom = 0f
        }
    }

    // A favourite that disappeared (star tapped during a drag, or the tool list changed under us)
    // must not leave a floating row behind.
    if (draggingId != null && startIndex < 0) {
        draggingId = null
        dragOffset = 0f
        edgeScroll = 0f
    }
    if (settlingId != null && settlingId !in displayedIds) {
        settlingId = null
        settleFrom = 0f
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        itemsIndexed(items = metas, key = { _, meta -> meta.id }, contentType = { _, _ -> "tool" }) { index, meta ->
            val dragging = meta.id == draggingId
            val settling = meta.id == settlingId
            val moving = dragging || settling
            // Rows move aside by exactly one row each, and only while a drag is running. The change
            // is animated so the list eases into its new shape instead of jumping.
            val shiftTarget = if (moving || draggingId == null) 0f else (shifts.getOrElse(index) { 0 }).toFloat() * stepPx
            val shift by animateFloatAsState(
                targetValue = if (draggingId != null) shiftTarget else 0f,
                animationSpec = decorativeSpec(140),
                label = "favouritesRowShift",
            )
            Surface(
                color = if (moving) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                } else {
                    Color.Transparent
                },
                shadowElevation = if (moving) 8.dp else 0.dp,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (moving) 1f else 0f)
                    .graphicsLayer {
                        translationY = when {
                            // Under the finger, one pixel per pixel.
                            dragging -> dragOffset
                            // Settling into the position that was just stored.
                            settling -> settleAnimation.value
                            // Sliding aside to open the gap (no offset at all once the drag is over:
                            // the list itself has already taken the new order by then).
                            draggingId != null -> shift
                            else -> 0f
                        }
                    }
                    .onGloballyPositioned { coordinates ->
                        // Rows are uniform, and the measurement is taken only while nothing is
                        // lifted or settling, so a translated row can never feed its own offset
                        // back into the arithmetic.
                        if (draggingId == null && settlingId == null) {
                            val height = coordinates.size.height.toFloat()
                            if (height > 0f && height != rowHeight) rowHeight = height
                        }
                    }
                    .pointerInput(meta.id, reorderable) {
                        if (!reorderable) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                // A new gesture always starts from a clean state, so two quick drags
                                // in a row cannot inherit an offset from the previous one.
                                draggingId = meta.id
                                settlingId = null
                                settleFrom = 0f
                                dragOffset = 0f
                                edgeScroll = 0f
                                if (hapticsEnabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val step = currentStepPx
                                val from = currentIds.indexOf(meta.id)
                                if (from < 0 || rowHeight <= 0f || step <= 0f) {
                                    // Nothing to compute with: end the drag rather than guess.
                                    draggingId = null
                                    dragOffset = 0f
                                    edgeScroll = 0f
                                    return@detectDragGesturesAfterLongPress
                                }
                                // The edge decision is made where the user is looking: the row's
                                // position on screen, not its distance from the start of the list.
                                // With the content position alone, a list that had been scrolled
                                // always looked "past the bottom edge" and started scrolling away
                                // under the finger - which is how a downward drag could end up at
                                // the end of the list without the finger ever going there.
                                val scrolled = scrolledContentPx(
                                    firstVisibleItemIndex = listState.firstVisibleItemIndex,
                                    firstVisibleItemOffsetPx = listState.firstVisibleItemScrollOffset,
                                    stepPx = step,
                                )
                                val info = listState.layoutInfo
                                edgeScroll = autoScrollDelta(
                                    rowTopPx = rowTopInViewport(
                                        slotContentTopPx = from * step,
                                        dragOffsetPx = dragOffset,
                                        scrolledPx = scrolled,
                                    ),
                                    rowHeightPx = rowHeight,
                                    viewportStartPx = info.viewportStartOffset.toFloat(),
                                    viewportHeightPx = (info.viewportEndOffset - info.viewportStartOffset).toFloat(),
                                    edgePx = currentEdgePx,
                                    maxStepPx = currentMaxScrollStepPx,
                                )
                            },
                            onDragEnd = {
                                val id = draggingId
                                val step = currentStepPx
                                if (id != null && step > 0f) {
                                    val resolved = resolveDragDrop(
                                        stored = currentFavorites,
                                        displayed = currentIds,
                                        draggedId = id,
                                        dragOffsetPx = dragOffset,
                                        stepPx = step,
                                    )
                                    val from = currentIds.indexOf(id)
                                    if (resolved != null && from >= 0) {
                                        val rowsMoved = resolved.targetIndex - from
                                        // The stored order and the position on screen come from the
                                        // same resolution, so they cannot disagree.
                                        if (rowsMoved != 0) currentOnMove(id, resolved.anchorId)
                                        settleFrom = settleOffsetPx(dragOffset, rowsMoved, step)
                                        settlingId = id
                                    }
                                }
                                draggingId = null
                                dragOffset = 0f
                                edgeScroll = 0f
                            },
                            onDragCancel = {
                                // Cancelled: only the state is cleared, the order stays as it was.
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
        // "Show tool icons" hides the monogram in the list only; the name and classification chip
        // carry the meaning, and the row's semantics do not depend on the glyph.
        if (LocalUiSettings.current.showToolIcons) ToolMonogram(glyph = meta.glyph, highlighted = selected)
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
