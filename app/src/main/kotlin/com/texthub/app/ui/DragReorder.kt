package com.texthub.app.ui

import com.texthub.core.prefs.moveFavoriteInDisplayedOrder
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
 * How much of a uniform-row list has been scrolled past the top of the viewport, in pixels.
 *
 * Auto-scroll decisions are made in *viewport* coordinates (what is on screen), while a row's slot
 * and its drag offset live in *content* coordinates (distance from the very start of the list). For
 * a list whose rows are all the same height - which the favourites list is - the distance already
 * scrolled is exactly the height of the leading rows plus the partial offset of the first visible
 * one. Comparing content coordinates against viewport limits instead is what made a downward drag
 * in a scrolled list look like it was already past the bottom edge and start scrolling away.
 *
 * @param firstVisibleItemIndex index of the first visible row.
 * @param firstVisibleItemOffsetPx how much of that row is already scrolled out of view.
 * @param stepPx distance from one row's top to the next row's top (row height + gap).
 */
internal fun scrolledContentPx(firstVisibleItemIndex: Int, firstVisibleItemOffsetPx: Int, stepPx: Float): Float =
    if (stepPx <= 0f || firstVisibleItemIndex < 0) 0f else firstVisibleItemIndex * stepPx + firstVisibleItemOffsetPx

/**
 * The dragged row's top edge in viewport coordinates: its slot in content coordinates, plus how far
 * the finger has pulled it (including the auto-scroll compensation), minus everything the list has
 * already scrolled past. This - not the content position - is what decides whether the row is near
 * the visible edge.
 */
internal fun rowTopInViewport(slotContentTopPx: Float, dragOffsetPx: Float, scrolledPx: Float): Float =
    slotContentTopPx + dragOffsetPx - scrolledPx

/**
 * How far to scroll the list while the finger stays near an edge, in pixels per frame.
 *
 * @param rowTopPx top of the dragged row, in the viewport's coordinates (what is on screen).
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

/**
 * How much of a frame's automatic scroll to apply, given how long that frame actually took.
 *
 * [autoScrollDelta] is expressed per 60 Hz frame, which is what the arithmetic is easy to reason
 * about in - but a 120 Hz screen would then scroll twice as fast as a 60 Hz one, and a slow frame
 * would scroll too little. Scaling by the real frame time makes the list move at the same speed on
 * every device. A frame that took far too long (a stall while the list is being rebuilt) is capped,
 * so the row cannot shoot away from the finger when the app catches up.
 *
 * @param stepPerFramePx the scroll for a 60 Hz frame, from [autoScrollDelta].
 * @param frameSeconds how long the frame that is about to be drawn took.
 * @return the scroll to apply now, in pixels.
 */
internal fun autoScrollForFrame(stepPerFramePx: Float, frameSeconds: Float): Float {
    if (!stepPerFramePx.isFinite() || !frameSeconds.isFinite() || frameSeconds <= 0f) return 0f
    val frames = (frameSeconds * 60f).coerceIn(0f, MAX_FRAMES_PER_STEP)
    return stepPerFramePx * frames
}

/** The longest catch-up allowed after a stalled frame: four 60 Hz frames' worth. */
private const val MAX_FRAMES_PER_STEP = 4f

/**
 * One resolved drop.
 *
 * @property targetIndex the position the row lands on, counted in the list as displayed.
 * @property anchorId the id of the row the dragged one has to end up in front of (null = the end).
 * @property storedOrder the favourites order that must be written for this drop.
 */
internal data class DragDrop(
    val targetIndex: Int,
    val anchorId: String?,
    val storedOrder: List<String>,
)

/**
 * Resolves a released drag into both halves of the same answer: where the row sits on screen and
 * what the stored favourites order becomes.
 *
 * Both come from this one function, which is what makes "the position the user released on" and
 * "the stored order" impossible to disagree about - the earlier version computed the highlight
 * from one index and the move from another (screen positions against stored ones), and the row
 * could end up somewhere other than where it was dropped.
 *
 * @param stored the favourites order as it is stored.
 * @param displayed the ids as they are displayed, in the same relative order as [stored].
 * @param draggedId the row being dragged.
 * @param dragOffsetPx how far it has travelled from its starting slot (positive = down).
 * @param stepPx distance from one row's top to the next row's top (height + gap).
 */
internal fun resolveDragDrop(
    stored: List<String>,
    displayed: List<String>,
    draggedId: String,
    dragOffsetPx: Float,
    stepPx: Float,
): DragDrop? {
    val startIndex = displayed.indexOf(draggedId)
    if (startIndex < 0) return null
    val targetIndex = dropTargetIndex(startIndex, dragOffsetPx, stepPx, displayed.lastIndex)
    if (targetIndex < 0) return null
    // The row the dragged one is released in front of, counted in the displayed list *without* the
    // dragged row: that is the row that will follow it, and it is the same row in the stored list.
    val rest = displayed.toMutableList().also { it.removeAt(startIndex) }
    val anchorId = rest.getOrNull(targetIndex)
    return DragDrop(
        targetIndex = targetIndex,
        anchorId = anchorId,
        storedOrder = moveFavoriteInDisplayedOrder(stored, displayed, draggedId, anchorId),
    )
}

/**
 * How far each row moves aside to open the gap the dragged row is dropped into, in rows.
 *
 * Every row between the dragged one and its target shifts by exactly one row (never two, never
 * half): the gap is one row tall wherever it opens, so the list never over- or under-shoots. The
 * dragged row is not shifted here - it follows the finger instead.
 */
internal fun shiftRows(size: Int, startIndex: Int, targetIndex: Int): List<Int> {
    if (size <= 0) return emptyList()
    if (startIndex !in 0 until size || targetIndex !in 0 until size || startIndex == targetIndex) {
        return List(size) { 0 }
    }
    return List(size) { index ->
        when {
            index == startIndex -> 0
            startIndex < targetIndex && index in (startIndex + 1)..targetIndex -> -1
            targetIndex < startIndex && index in targetIndex until startIndex -> 1
            else -> 0
        }
    }
}

/**
 * Where the dragged row itself is drawn: exactly under the finger while it is held, and once it is
 * released, the leftover fraction between its final slot and the release point, which is animated
 * away. That is what makes the row settle into the real persisted position instead of leaving a
 * permanent offset or snapping back the whole way in one frame.
 */
internal fun settleOffsetPx(dragOffsetPx: Float, rowsMoved: Int, stepPx: Float): Float =
    dragOffsetPx - rowsMoved * stepPx
