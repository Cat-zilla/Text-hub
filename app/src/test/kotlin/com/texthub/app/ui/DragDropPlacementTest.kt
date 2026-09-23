package com.texthub.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the *drop placement* of the favourites drag.
 *
 * The reported bug: dragging a row downward sometimes stored it at the very bottom of the list
 * instead of where the finger released it. Two defects fed the same symptom:
 *
 *  1. the gesture handlers were created once per row with `rowHeight` still 0, so the `stepPx` they
 *     captured was just the 4 dp gap - a real drag then read as dozens of rows and the target was
 *     clamped to the end of the list. The handlers must read the *current* step (the composable now
 *     goes through `rememberUpdatedState`; the arithmetic here pins what a correct step must
 *     produce);
 *  2. the auto-scroll trigger compared the row's *content* position against *viewport* limits, so
 *     in a scrolled list the row always looked past the bottom edge and the list scrolled away under
 *     the finger, inflating the offset the drop was computed from. `scrolledContentPx` and
 *     `rowTopInViewport` are the corrected coordinate conversion, tested here.
 */
class DragDropPlacementTest {

    private val step = 64f          // one row plus the gap, in px
    private val displayed = listOf("A", "B", "C", "D", "E")

    // ------------------------------------------------------------------ the handoff scenarios

    @Test fun releaseBetweenCAndDLeavesTheRowThere() {
        // B dragged down and released between C and D: the finger travelled 1.5 rows.
        val drop = resolveDragDrop(displayed, displayed, "B", dragOffsetPx = step * 1.5f, stepPx = step)!!
        assertEquals("A C B D E", drop.storedOrder.joinToString(" "))
        assertEquals(2, drop.targetIndex)
        assertEquals("D", drop.anchorId)
    }

    @Test fun releaseBetweenDAndELeavesTheRowThere() {
        val drop = resolveDragDrop(displayed, displayed, "B", dragOffsetPx = step * 2.5f, stepPx = step)!!
        assertEquals("A C D B E", drop.storedOrder.joinToString(" "))
        assertEquals(3, drop.targetIndex)
    }

    @Test fun onlyAGenuineBottomReleaseSendsTheRowToTheBottom() {
        // 1.55 rows is "between C and D", not the bottom - the old bug clamped this to the end.
        assertEquals(2, dropTargetIndex(1, step * 1.55f, step, displayed.lastIndex))
        // Only a release clearly past the last row is a bottom drop.
        val drop = resolveDragDrop(displayed, displayed, "B", dragOffsetPx = step * 3.6f, stepPx = step)!!
        assertEquals("A C D E B", drop.storedOrder.joinToString(" "))
        assertNull(drop.anchorId)
    }

    @Test fun downwardOnePositionAndDownwardSeveralPositions() {
        assertEquals(2, dropTargetIndex(1, step * 0.7f, step, displayed.lastIndex))
        assertEquals(3, dropTargetIndex(0, step * 3.2f, step, displayed.lastIndex))
    }

    @Test fun releasingNearACentreAndNearABoundary() {
        // Just over 60% of a row moves one slot; just under stays (the 60% dead zone).
        assertEquals(3, dropTargetIndex(2, step * 0.61f, step, displayed.lastIndex))
        assertEquals(2, dropTargetIndex(2, step * 0.59f, step, displayed.lastIndex))
        // Half a row is still the dead zone of the current slot.
        assertEquals(2, dropTargetIndex(2, step * 0.5f, step, displayed.lastIndex))
    }

    @Test fun draggingUpwardLandsWhereReleased() {
        // D released between A and B (travel -2.2 rows).
        val drop = resolveDragDrop(displayed, displayed, "D", dragOffsetPx = -step * 2.2f, stepPx = step)!!
        assertEquals("A D B C E", drop.storedOrder.joinToString(" "))

        // D released just past B's centre (travel -1.55 rows): one slot up.
        val drop2 = resolveDragDrop(displayed, displayed, "D", dragOffsetPx = -step * 1.55f, stepPx = step)!!
        assertEquals("A B D C E", drop2.storedOrder.joinToString(" "))
    }

    @Test fun adjacentSwapDownwardAndUpward() {
        val down = resolveDragDrop(displayed, displayed, "B", dragOffsetPx = step * 0.7f, stepPx = step)!!
        assertEquals("A C B D E", down.storedOrder.joinToString(" "))
        val up = resolveDragDrop(displayed, displayed, "B", dragOffsetPx = -step * 0.7f, stepPx = step)!!
        assertEquals("B A C D E", up.storedOrder.joinToString(" "))
    }

    @Test fun firstAndLastRowDragsStayInsideTheList() {
        assertEquals(0, dropTargetIndex(0, -step * 3f, step, displayed.lastIndex))
        assertEquals(0, resolveDragDrop(displayed, displayed, "A", dragOffsetPx = -step * 3f, stepPx = step)!!.targetIndex)
        assertEquals(displayed.lastIndex, dropTargetIndex(displayed.lastIndex, step * 3f, step, displayed.lastIndex))
    }

    @Test fun longListsStayExact() {
        val longList = (0 until 40).map { "row$it" }
        // drag row5 down 7.5 rows -> lands at index 12, in front of row13
        val drop = resolveDragDrop(longList, longList, "row5", dragOffsetPx = step * 7.5f, stepPx = step)!!
        assertEquals(12, drop.targetIndex)
        assertEquals("row13", drop.anchorId)
        assertEquals("row13", drop.storedOrder[13])
        assertEquals("row5", drop.storedOrder[12])
    }

    @Test fun anIntentionalBottomDropStillWorks() {
        val bottom = resolveDragDrop(displayed, displayed, "A", dragOffsetPx = step * 4.5f, stepPx = step)!!
        assertNull(bottom.anchorId)
        assertEquals("B C D E A", bottom.storedOrder.joinToString(" "))
    }

    // ------------------------------------------------------------------ the coordinate fixes

    @Test fun theStepIncludesTheGapNotTheGapAlone() {
        // The stale-capture failure mode: with the 4 dp gap (here 12 px) mistaken for the whole
        // step, a 1.5-row drag reads as eight rows and clamps to the end of the list. The handler
        // must always be given rowHeight + gap as the step - this is the arithmetic that has to
        // receive it.
        val gapAlone = 12f
        val withGapMistakenForStep = dropTargetIndex(1, step * 1.5f, gapAlone, displayed.lastIndex)
        assertEquals(displayed.lastIndex, withGapMistakenForStep) // what the bug produced
        assertEquals(2, dropTargetIndex(1, step * 1.5f, step, displayed.lastIndex)) // what must happen
    }

    @Test fun aScrolledListNoLongerLooksPastTheBottomEdge() {
        // Viewport 800 px tall, edge zone 168 px. The dragged row sits at content slot 12 * step -
        // far below any viewport limit - but the list is scrolled so the row is visually in the
        // middle of the screen: no auto-scroll may trigger.
        val scrolled = scrolledContentPx(7, 20, step)               // 7 rows + 20 px scrolled away
        val rowTop = rowTopInViewport(12 * step, 0f, scrolled)      // 300 px from the top of the screen
        val edge = autoScrollDelta(
            rowTopPx = rowTop, rowHeightPx = step - 12f, viewportStartPx = 0f,
            viewportHeightPx = 800f, edgePx = 168f, maxStepPx = 36f,
        )
        assertEquals(0f, edge, 0.001f)

        // The old conversion (no scroll correction) reported the same row as far past the bottom.
        val uncorrected = autoScrollDelta(
            rowTopPx = 12 * step, rowHeightPx = step - 12f, viewportStartPx = 0f,
            viewportHeightPx = 800f, edgePx = 168f, maxStepPx = 36f,
        )
        assertTrue("the uncorrected position must look past the bottom edge", uncorrected > 0f)
    }

    @Test fun autoScrollWhileDraggingStillLandsWhereTheFingerEndedUp() {
        // During auto-scroll the offset grows by the amount scrolled (the row stays under the
        // finger); the drop is computed in content coordinates, so this is where the row really is.
        val longList = (0 until 8).map { "r$it" }
        val scrolled = scrolledContentPx(3, 0, step)                 // three rows scrolled away
        val dragOffsetWithScroll = step * 1.5f + scrolled            // 1.5 rows of finger + scroll
        val drop = resolveDragDrop(longList, longList, "r1", dragOffsetPx = dragOffsetWithScroll, stepPx = step)!!
        // content position 1 + 1.5 rows of finger + 3 rows scrolled = between r4 and r5
        assertEquals(5, drop.targetIndex)
        assertEquals("r6", drop.anchorId)
        assertEquals(listOf("r0", "r2", "r3", "r4", "r5", "r1", "r6", "r7"), drop.storedOrder)
    }

    @Test fun aRowVisibleAfterScrollReportsItsTrueViewportPosition() {
        // 3 rows scrolled away; the row in slot 4 with the finger 0.5 rows down is 1.5 rows from
        // the top of the viewport.
        val scrolled = scrolledContentPx(3, 0, step)
        assertEquals(1.5f * step, rowTopInViewport(4 * step, step * 0.5f, scrolled), 0.001f)
    }

    @Test fun scrolledContentHandlesDegenerateInputs() {
        assertEquals(0f, scrolledContentPx(0, 0, step), 0.001f)
        assertEquals(0f, scrolledContentPx(2, 0, 0f), 0.001f)
        assertEquals(0f, scrolledContentPx(-1, 10, step), 0.001f)
        assertEquals(step * 2 + 5f, scrolledContentPx(2, 5, step), 0.001f)
    }

    @Test fun rowShiftsMatchTheDropExactly() {
        // The gap the user watches while dragging must be the slot the row is stored in.
        val shifts = shiftRows(displayed.size, 1, 3)
        assertEquals(listOf(0, 0, -1, -1, 0), shifts)
        val drop = resolveDragDrop(displayed, displayed, "B", dragOffsetPx = step * 2.5f, stepPx = step)!!
        assertEquals(3, drop.targetIndex)
    }

    @Test fun displayedSubsequenceMapsBackToTheStoredOrder() {
        // The stored order skips entries the list does not show (a favourite whose tool is gone):
        // dropping "b" before "d" must not move anything else - including the hidden "y".
        val stored = listOf("x", "a", "b", "y", "c", "d", "e")
        val shown = listOf("a", "b", "c", "d", "e")
        val drop = resolveDragDrop(stored, shown, "b", dragOffsetPx = step * 1.5f, stepPx = step)!!
        assertEquals(listOf("x", "a", "y", "c", "b", "d", "e"), drop.storedOrder)
    }

    @Test fun anUnknownRowResolvesToNothing() {
        assertNull(resolveDragDrop(displayed, displayed, "nope", dragOffsetPx = 10f, stepPx = step))
    }
}
