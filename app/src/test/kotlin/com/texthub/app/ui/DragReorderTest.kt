package com.texthub.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the drag arithmetic that drives the favourites reordering.
 *
 * The previous implementation advanced the dragged row as soon as half a row was covered and used a
 * step that ignored the gap between rows, so the target position flipped back and forth from one
 * frame to the next - the row visibly jumped up and down. These tests pin the behaviour that
 * replaced it: a dead zone around each position, a step that includes the gap, clamping at both
 * ends, and a sane edge auto-scroll.
 */
class DragReorderTest {

    private val step = 64f      // a 60 dp row plus the 4 dp gap, in pixels
    private val last = 6        // seven favourites

    // ------------------------------------------------------------------ dropTargetIndex

    @Test fun aSmallWobbleDoesNotChangeTheTarget() {
        // Up to 40% of a row either way still belongs to the current position.
        assertEquals(3, dropTargetIndex(3, 0f, step, last))
        assertEquals(3, dropTargetIndex(3, step * 0.3f, step, last))
        assertEquals(3, dropTargetIndex(3, -step * 0.3f, step, last))
        assertEquals(3, dropTargetIndex(3, step * 0.399f, step, last))
    }

    @Test fun passingSixtyPercentOfARowMovesExactlyOnePosition() {
        assertEquals(4, dropTargetIndex(3, step * 0.61f, step, last))
        assertEquals(2, dropTargetIndex(3, -step * 0.61f, step, last))
        assertEquals(3, dropTargetIndex(3, step * 0.59f, step, last))
    }

    @Test fun theTargetNeverOscillatesWhileTheOffsetGrows() {
        // The heart of the bug: as the row is dragged down one pixel at a time, the target index
        // must be non-decreasing. Any decrease is the jumpiness the user saw.
        var previous = dropTargetIndex(0, 0f, step, last)
        var offset = 0f
        while (offset < step * 6f) {
            val target = dropTargetIndex(0, offset, step, last)
            assert(previous <= target) { "target went back from $previous to $target at offset $offset" }
            previous = target
            offset += 1f
        }
        assertEquals(last, previous)
    }

    @Test fun draggingFurtherThanTheListIsClampedToTheEnds() {
        assertEquals(0, dropTargetIndex(2, -step * 50f, step, last))
        assertEquals(last, dropTargetIndex(2, step * 50f, step, last))
        assertEquals(last, dropTargetIndex(last, step * 3f, step, last))
    }

    @Test fun aLongDragLandsWhereTheFingerIs() {
        assertEquals(5, dropTargetIndex(1, step * 4f, step, last))
        assertEquals(1, dropTargetIndex(5, -step * 4f, step, last))
    }

    @Test fun nonsenseInputDoesNotProduceAMove() {
        assertEquals(-1, dropTargetIndex(3, 10f, step, -1))
        assertEquals(-1, dropTargetIndex(-1, 10f, step, last))
        assertEquals(-1, dropTargetIndex(9, 10f, step, last))
        assertEquals(3, dropTargetIndex(3, 100f, 0f, last))
        assertEquals(3, dropTargetIndex(3, Float.NaN, step, last))
        assertEquals(3, dropTargetIndex(3, Float.POSITIVE_INFINITY, step, last))
    }

    // ------------------------------------------------------------------ autoScrollDelta

    private val viewportStart = 0f
    private val viewportHeight = 640f
    private val edge = 56f
    private val maxStep = 12f

    @Test fun noScrollingInTheMiddleOfTheList() {
        assertEquals(
            0f,
            autoScrollDelta(300f, 60f, viewportStart, viewportHeight, edge, maxStep),
        )
    }

    @Test fun theListFollowsTheFingerNearTheBottomEdge() {
        // Row top at 600 with a height of 60 leaves 20 px less than the 56 px edge: scroll down.
        val delta = autoScrollDelta(600f, 60f, viewportStart, viewportHeight, edge, maxStep)
        assertEquals(maxStep, delta)
    }

    @Test fun theListFollowsTheFingerNearTheTopEdge() {
        val delta = autoScrollDelta(20f, 60f, viewportStart, viewportHeight, edge, maxStep)
        assertEquals(-maxStep, delta)
    }

    @Test fun theFirstNudgeIsGentleAndClamped() {
        // 1 px past the bottom edge scrolls 1 px; 61 px past it is capped at the maximum.
        assertEquals(
            1f,
            autoScrollDelta(525f, 60f, viewportStart, viewportHeight, edge, maxStep),
        )
        assertEquals(
            12f,
            autoScrollDelta(585f, 60f, viewportStart, viewportHeight, edge, maxStep),
        )
    }

    @Test fun scrollingOnlyStartsOnceTheRowReachesTheEdgeBand() {
        // Just inside the band: no scrolling yet, so a row resting near the edge does not creep.
        assertEquals(0f, autoScrollDelta(523f, 60f, viewportStart, viewportHeight, edge, maxStep))
    }

    @Test fun aViewportThatIsNotMeasuredYetDoesNotScroll() {
        assertEquals(0f, autoScrollDelta(10f, 60f, 0f, 0f, edge, maxStep))
        assertEquals(0f, autoScrollDelta(10f, 0f, 0f, viewportHeight, edge, maxStep))
        assertEquals(0f, autoScrollDelta(10f, 60f, 0f, viewportHeight, edge, 0f))
    }
}
