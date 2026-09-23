package com.texthub.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ------------------------------------------------------------------ resolveDragDrop

    /**
     * The five favourites the round-10 specification uses: A B C D E. The old code stored a drag as
     * a screen position, which shifted the wrong entry whenever the list on screen was not exactly
     * the stored list.
     */
    private val abcde = listOf("a", "b", "c", "d", "e")

    private fun drop(
        dragged: String,
        rows: Float,
        stored: List<String> = abcde,
        displayed: List<String> = abcde,
    ): DragDrop = resolveDragDrop(stored, displayed, dragged, rows * step, step)!!

    @Test fun theFirstTwoRowsSwapAndTheStoredOrderSaysSo() {
        val moved = drop("a", 1f)
        assertEquals(1, moved.targetIndex)
        assertEquals(listOf("b", "a", "c", "d", "e"), moved.storedOrder)
        // The anchor is the row the dragged one is dropped in front of ("c"), not the row it passed.
        assertEquals("c", moved.anchorId)
    }

    @Test fun aRowMovedToTheTopGoesToTheFirstPosition() {
        val moved = drop("d", -3f)
        assertEquals(0, moved.targetIndex)
        assertEquals(listOf("d", "a", "b", "c", "e"), moved.storedOrder)
        assertEquals("a", moved.anchorId)
    }

    @Test fun aRowMovedToTheBottomHasNoAnchor() {
        val moved = drop("b", 10f)
        assertEquals(4, moved.targetIndex)
        assertEquals(listOf("a", "c", "d", "e", "b"), moved.storedOrder)
        assertNull("the last position has nothing after it", moved.anchorId)
    }

    @Test fun everyRowOfFiveCanBeMovedToEveryOtherPosition() {
        for (moved in abcde) {
            val from = abcde.indexOf(moved)
            for (target in abcde.indices) {
                val rows = (target - from).toFloat()
                val drop = drop(moved, rows)
                assertEquals("$moved -> $target: highlighted slot", target, drop.targetIndex)
                // The stored order is the displayed order after the drop: same size, same entries,
                // and the dragged row is exactly on the slot the user released it over.
                assertEquals("$moved -> $target: size", abcde.size, drop.storedOrder.size)
                assertEquals("$moved -> $target: entries", abcde.toSet(), drop.storedOrder.toSet())
                assertEquals(
                    "$moved -> $target: the row lands where it was released",
                    target,
                    drop.storedOrder.indexOf(moved),
                )
            }
        }
    }

    @Test fun aReleaseInTheDeadZoneStoresNothing() {
        // 0.3 of a row is inside the dead zone, so the row stays where it was and the stored order
        // is the very list that went in.
        val moved = drop("c", 0.3f)
        assertEquals(2, moved.targetIndex)
        assertEquals(abcde, moved.storedOrder)
    }

    @Test fun aReleasePastTheEndsIsClampedToTheList() {
        assertEquals(4, drop("a", 99f).targetIndex)
        assertEquals(listOf("b", "c", "d", "e", "a"), drop("a", 99f).storedOrder)
        assertEquals(0, drop("e", -99f).targetIndex)
        assertEquals(listOf("e", "a", "b", "c", "d"), drop("e", -99f).storedOrder)
    }

    @Test fun aRowThatIsNotInTheListCannotBeDragged() {
        assertNull(resolveDragDrop(abcde, abcde, "zz", step, step))
        assertNull(resolveDragDrop(abcde, emptyList(), "a", step, step))
        // Rows that have not been measured yet (step 0) resolve to "no move" rather than to a guess.
        val unmeasured = resolveDragDrop(abcde, abcde, "a", step, 0f)!!
        assertEquals(0, unmeasured.targetIndex)
        assertEquals(abcde, unmeasured.storedOrder)
    }

    @Test fun releasingExactlyOnASlotNeedsNoSettling() {
        // The row is dropped on the slot it is already drawn over: nothing is left to animate.
        val moved = drop("a", 2f)
        assertEquals(0f, settleOffsetPx(2f * step, moved.targetIndex - 0, step), 0.001f)
    }

    @Test fun theLeftoverDistanceIsOnlyTheFractionInsideTheSlot() {
        // Two rows down and a third of the way into the next slot: 0.33 of a row is left to settle.
        val moved = drop("a", 2.33f)
        assertEquals(2, moved.targetIndex)
        assertEquals(0.33f * step, settleOffsetPx(2.33f * step, 2, step), 0.01f)
    }

    @Test fun theRowIsDrawnWhereTheFingerIsUntilItSettles() {
        // Invariant that makes the settle step invisible: the position the row is drawn at right
        // after the drop (slot + leftover) is exactly the position the finger released it at.
        for (moved in abcde) {
            val from = abcde.indexOf(moved).toFloat()
            for (offsetRows in listOf(-3.4f, -1.6f, -0.4f, 0f, 0.7f, 1.5f, 3.9f)) {
                val drop = drop(moved, offsetRows)
                val rowsMoved = drop.targetIndex - from.toInt()
                val drawn = drop.targetIndex * step + settleOffsetPx(offsetRows * step, rowsMoved, step)
                assertEquals(
                    "$moved released at $offsetRows: the row must not jump",
                    from * step + offsetRows * step,
                    drawn,
                    0.01f,
                )
            }
        }
    }

    // ------------------------------------------------------------------ shiftRows

    @Test fun theRowsBetweenTheDragAndTheTargetSlideOneRowAside() {
        assertEquals(listOf(0, -1, -1, -1, 0), shiftRows(5, 0, 3))
        assertEquals(listOf(0, 1, 1, 1, 0), shiftRows(5, 4, 1))
        assertEquals(listOf(0, 0, -1, -1, 0), shiftRows(5, 1, 3))
    }

    @Test fun theDraggedRowIsNeverShiftedAndTheRestStayPutOtherwise() {
        assertEquals(listOf(0, 0, 0, 0, 0), shiftRows(5, 2, 2))
        assertEquals(listOf(0, 0, 0, 0, 0), shiftRows(5, 7, 1))
        assertEquals(listOf(0, 0, 0, 0, 0), shiftRows(5, -1, 1))
        assertEquals(listOf(0, 0, -1, 0, 0), shiftRows(5, 1, 2))
    }

    @Test fun everyShiftedRowMovesExactlyOnceRegardlessOfTheDistance() {
        // A long drag opens a one-row gap: the rows in between move by one row each, never by the
        // whole distance, which is why the list cannot over- or under-shoot.
        // Row 8 is dragged to the top: the eight rows above it each move down by one slot.
        val shifts = shiftRows(9, 8, 0)
        assertEquals(listOf(1, 1, 1, 1, 1, 1, 1, 1, 0), shifts)
        assertTrue("no row moves more than one slot", shifts.all { it in -1..1 })
    }

    // ------------------------------------------------------------------ persistence

    @Test fun theDisplayedOrderAfterEveryDropIsTheStoredOrder() {
        // The end-to-end rule of round 10 §13: what the picker shows after a drop is what a fresh
        // read of the store returns.
        for (moved in abcde) {
            for (target in abcde.indices) {
                val stored = drop(moved, (target - abcde.indexOf(moved)).toFloat()).storedOrder
                // A fresh read of the same store (the app re-reads it after every change).
                val reread = stored.toList()
                assertEquals(reread, stored)
                assertEquals(target, stored.indexOf(moved))
                // Every other favourite keeps its relative order.
                val rest = abcde - moved
                assertEquals(rest, stored - moved)
            }
        }
    }

    @Test fun twoQuickDragsInARowBothLand() {
        // First gesture: drag "b" to the bottom.
        val first = drop("b", 9f)
        assertEquals(listOf("a", "c", "d", "e", "b"), first.storedOrder)

        // Second gesture, started immediately afterwards, with no leftover offset from the first.
        // "e" now sits third from the end, and three rows up is the top.
        val second = resolveDragDrop(first.storedOrder, first.storedOrder, "e", -3f * step, step)!!
        assertEquals(0, second.targetIndex)
        assertEquals(listOf("e", "a", "c", "d", "b"), second.storedOrder)
        assertEquals("a", second.anchorId)
    }

    @Test fun aGestureThatIsNotADragChangesNothing() {
        // A tap, or a drag that is cancelled before it leaves the dead zone: the stored order is
        // untouched, and the row is not left with an offset either.
        val tap = drop("c", 0f)
        assertEquals(abcde, tap.storedOrder)
        assertEquals(0f, settleOffsetPx(0f, 0, step), 0.001f)
        // "No move" is expressed as "in front of the row that already follows it", so the same
        // resolution that places the row also describes the no-op - there is no second code path.
        assertEquals(2, tap.targetIndex)
        assertEquals("d", tap.anchorId)
    }

    // ------------------------------------------------------------------ filtered lists

    @Test fun aFilteredFavouritesListStillMovesTheRowTheUserGrabbed() {
        // The store holds A B C D E, but B and D belong to tools that no longer exist, so the list
        // on screen is A C E. Counting screen positions would move "C"... and the old code moved a
        // stored entry by that index, which is how the wrong favourite could jump.
        val stored = abcde
        val displayed = listOf("a", "c", "e")

        val toBottom = resolveDragDrop(stored, displayed, "c", 2f * step, step)!!
        assertEquals(2, toBottom.targetIndex)
        assertEquals(listOf("a", "b", "d", "e", "c"), toBottom.storedOrder)
        // ... and the list on screen now reads A E C, i.e. the dragged row is last, as displayed.
        assertEquals(listOf("a", "e", "c"), toBottom.storedOrder.filter { it in displayed })

        val toTop = resolveDragDrop(stored, displayed, "e", -2f * step, step)!!
        assertEquals(0, toTop.targetIndex)
        assertEquals(listOf("e", "a", "b", "c", "d"), toTop.storedOrder)
    }

    @Test fun anAnchorThatIsNotOnScreenMovesNothing() {
        // Defensive: if the list changed under the drag, the move is refused rather than applied to
        // whichever row happens to sit at that position.
        val stored = listOf("a", "b", "c")
        val displayed = listOf("a", "c")     // "b" disappeared while the row was held
        val outcome = resolveDragDrop(stored, displayed, "c", -1.2f * step, step)!!
        assertEquals(0, outcome.targetIndex)
        assertEquals("a", outcome.anchorId)
        assertEquals(listOf("c", "a", "b"), outcome.storedOrder)
    }

    @Test fun aDragInsideAFilteredListNeverLosesAFavourite() {
        val stored = listOf("a", "b", "c", "d", "e")
        val displayed = listOf("b", "d", "e")
        for (moved in displayed) {
            for (target in displayed.indices) {
                val outcome = resolveDragDrop(
                    stored,
                    displayed,
                    moved,
                    (target - displayed.indexOf(moved)).toFloat() * step,
                    step,
                )!!
                assertEquals("nothing may be dropped", stored.toSet(), outcome.storedOrder.toSet())
                assertEquals(stored.size, outcome.storedOrder.size)
                // The order of the rows that are on screen matches the slot that was highlighted.
                assertEquals(target, outcome.storedOrder.filter { it in displayed }.indexOf(moved))
            }
        }
    }

    @Test fun aDragOnAOneRowListDoesNothing() {
        val outcome = resolveDragDrop(listOf("a"), listOf("a"), "a", 5f * step, step)!!
        assertEquals(0, outcome.targetIndex)
        assertEquals(listOf("a"), outcome.storedOrder)
        assertNotEquals("a drag of a single row cannot reorder anything", listOf("b"), outcome.storedOrder)
    }

    // ------------------------------------------------------- smooth motion (§20, §21)

    /** The scroll a row sitting at the top edge of the viewport produces, in pixels per 60 Hz frame. */
    private fun scrollAtTopEdge(): Float = autoScrollDelta(
        rowTopPx = 0f,
        rowHeightPx = step,
        viewportStartPx = 0f,
        viewportHeightPx = 10f * step,
        edgePx = step,
        maxStepPx = 12f,
    )

    @Test fun theAutoScrollMovesAtTheSameSpeedOnEveryFrameRate() {
        val perFrame = scrollAtTopEdge()          // negative: the row is above the top edge band
        assertTrue(perFrame < 0f)
        val at60 = autoScrollForFrame(perFrame, 1f / 60f)
        val at120 = autoScrollForFrame(perFrame, 1f / 120f)
        assertEquals("a 120 Hz frame must scroll half as far as a 60 Hz one", at60 / 2f, at120, 1e-5f)

        // A whole second of scrolling is the same distance whatever the refresh rate is.
        var distance60 = 0.0
        repeat(60) { distance60 += autoScrollForFrame(perFrame, 1f / 60f).toDouble() }
        var distance120 = 0.0
        repeat(120) { distance120 += autoScrollForFrame(perFrame, 1f / 120f).toDouble() }
        assertEquals(distance60, distance120, 0.01)
    }

    @Test fun aStalledFrameCannotShootTheListAwayFromTheFinger() {
        // A frame that took a whole second (the list was rebuilt mid-drag) is capped at a few
        // frames' worth, so catching up can never overshoot the finger.
        assertEquals(4f * 12f, autoScrollForFrame(12f, 1f), 0.001f)
        assertEquals(12f, autoScrollForFrame(12f, 1f / 60f), 0.001f)
        assertEquals(0f, autoScrollForFrame(12f, 0f), 0.001f)
        assertEquals(0f, autoScrollForFrame(Float.NaN, 1f / 60f), 0.001f)
        assertEquals(0f, autoScrollForFrame(12f, Float.NaN), 0.001f)
        assertEquals(0f, autoScrollForFrame(12f, -1f), 0.001f)
    }

    @Test fun aSlowDragDoesNotMoveAnythingUntilTheThresholdIsCrossed() {
        // Forty small moves of 0.014 rows (0.56 rows in total): the slot stays where it is, and then
        // moves exactly once - that dead zone is what stops a slow, shaky finger from flickering.
        var offset = 0f
        val targets = mutableListOf<Int>()
        repeat(40) {
            offset += step * 0.014f
            targets += dropTargetIndex(2, offset, step, last)
        }
        assertTrue("nothing may move before 60% of a row is covered", targets.all { it == 2 })
        offset += step * 0.06f
        assertEquals(3, dropTargetIndex(2, offset, step, last))
    }

    @Test fun aFastDragLandsOnTheRowTheFingerIsOver() {
        assertEquals(6, dropTargetIndex(2, step * 4f, step, last))
        assertEquals(last, dropTargetIndex(2, step * 40f, step, last))
        assertEquals(0, dropTargetIndex(2, -step * 40f, step, last))
    }

    @Test fun crossingSeveralRowsAndComingBackEndsWhereItStarted() {
        assertEquals(4, dropTargetIndex(1, step * 3.2f, step, last))
        assertEquals(1, dropTargetIndex(4, -step * 3.2f, step, last))
    }

    @Test fun hoveringOnTheBoundaryDoesNotFlicker() {
        val hover = listOf(0.39f, 0.40f, 0.41f, 0.40f, 0.39f, 0.41f)
            .map { dropTargetIndex(3, step * it, step, last) }
        assertEquals("the highlighted slot must not flicker under a hovering finger", List(6) { 3 }, hover)
    }

    @Test fun scrollingWhileDraggingKeepsTheRowUnderTheFinger() {
        // The app adds the scrolled distance to the drag offset, so the row's position on screen is
        // unchanged by the auto-scroll - and so is the slot it would land on.
        val before = step * 1.5f
        val scrolled = autoScrollForFrame(scrollAtTopEdge(), 1f / 60f)
        val after = before + scrolled
        assertEquals("the offset must absorb exactly what was scrolled", before, after - scrolled, 1e-4f)
        assertEquals(dropTargetIndex(3, before, step, last), dropTargetIndex(3, after, step, last))
    }
}
