package com.texthub.app.ui

import kotlin.math.ceil
import kotlin.math.floor

/**
 * The arithmetic behind the favourites drag, kept out of the composable so it can be unit tested.
 *
 * The previous version reordered the list *while* the row was being dragged and re-based the visual
 * offset on every move, using a step that ignored the gap between rows. Rounding then flipped
 * between two positions on almost every frame, which is what made the row jump up and down. These
 * two functions describe a drag that cannot do that: the list keeps its order during the gesture,
 * the row follows the finger exactly, and the drop position is computed once, when the finger is
 * released.
 */

/**
 * Which position the dragged row would land on.
 *
 * @param startIndex the index the row had when the drag began.
 * @param dragOffsetPx how far the row has been moved from its starting position (positive = down).
 * @param stepPx the distance from one row's top to the next row's top (row height + gap).
 * @param lastIndex the last valid index.
 * @return the target index, clamped to the list, or [startIndex] when the move is not large enough
 *   to reach the neighbouring row (the hysteresis that stops border-line flicker).
 */
internal fun dropTargetIndex(startIndex: Int, dragOffsetPx: Float, stepPx: Float, lastIndex: Int): Int {
    if (lastIndex < 0 || startIndex < 0 || startIndex > lastIndex) return -1
    if (stepPx <= 0f || !dragOffsetPx.isFinite()) return startIndex
    // 60% of a row has to be covered before the row changes place (rather than a bare half), so a
    // shaky finger sitting on a boundary cannot make the highlighted slot flicker.
    val rows = dragOffsetPx / stepPx
    val moved = if (rows >= 0f) floor(rows + 0.4f).toInt() else ceil(rows - 0.4f).toInt()
    return (startIndex + moved).coerceIn(0, lastIndex)
}

/**
 * How far to scroll the list while the finger stays near an edge, in pixels per frame.
 *
 * @param rowTopPx top of the dragged row, in the list's content coordinates.
 * @param rowHeightPx height of the dragged row.
 * @param viewportStartPx content coordinate of the viewport's top edge.
 * @param viewportHeightPx visible height of the list.
 * @param edgePx how close to an edge the row has to be before the list follows.
 * @param maxStepPx the fastest scroll, in pixels per frame (kept small so it stays controllable).
 * @return a positive value to scroll down, a negative one to scroll up, or 0.
 */
internal fun autoScrollDelta(
    rowTopPx: Float,
    rowHeightPx: Float,
    viewportStartPx: Float,
    viewportHeightPx: Float,
    edgePx: Float,
    maxStepPx: Float,
): Float {
    if (rowHeightPx <= 0f || viewportHeightPx <= 0f || maxStepPx <= 0f) return 0f
    val rowBottom = rowTopPx + rowHeightPx
    val topLimit = viewportStartPx + edgePx
    val bottomLimit = viewportStartPx + viewportHeightPx - edgePx
    return when {
        rowTopPx < topLimit -> -(topLimit - rowTopPx).coerceAtMost(maxStepPx)
        rowBottom > bottomLimit -> (rowBottom - bottomLimit).coerceAtMost(maxStepPx)
        else -> 0f
    }
}
