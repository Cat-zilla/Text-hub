package com.texthub.app.ui.theme

import com.texthub.core.prefs.CornerStyle
import com.texthub.core.prefs.UiSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Test

/**
 * The centralized corner/shape system: one preference, one table, one set of tokens.
 *
 * The point of these tests is that the corner language of the whole app is decided in exactly one
 * place. `hubShapesFor` is what `TextHubTheme` hands to `MaterialTheme`, so a change here is a
 * change everywhere - and `radiiOf` is the table every named token reads.
 */
class HubCornersTest {

    private val rounded = radiiOf(CornerStyle.ROUNDED)
    private val slight = radiiOf(CornerStyle.SLIGHT)
    private val square = radiiOf(CornerStyle.SQUARE)

    // --------------------------------------------------------------- the three styles

    @Test fun roundedKeepsTheAppAsItAlwaysWas() {
        assertEquals(8f.dp, rounded.extraSmall)
        assertEquals(12f.dp, rounded.small)
        assertEquals(16f.dp, rounded.medium)
        assertEquals(20f.dp, rounded.large)
        assertEquals(28f.dp, rounded.extraLarge)
        // The named tokens the app used before the preference existed.
        assertEquals(16f.dp, rounded.button)
        assertEquals(14f.dp, rounded.field)
        assertEquals(12f.dp, rounded.stepper)
        assertEquals(13f.dp, rounded.segment)
        assertEquals(12f.dp, rounded.monogram)
        assertEquals(28f.dp, rounded.sheet)
        // Chips and badges stay the pill they have always been while the style is rounded.
        assertEquals(null, rounded.chip)
    }

    @Test fun slightlyReducedIsStrictlySmallerOnEveryToken() {
        listOf(
            "extraSmall" to (slight.extraSmall to rounded.extraSmall),
            "small" to (slight.small to rounded.small),
            "medium" to (slight.medium to rounded.medium),
            "large" to (slight.large to rounded.large),
            "extraLarge" to (slight.extraLarge to rounded.extraLarge),
            "button" to (slight.button to rounded.button),
            "field" to (slight.field to rounded.field),
            "stepper" to (slight.stepper to rounded.stepper),
            "segment" to (slight.segment to rounded.segment),
            "monogram" to (slight.monogram to rounded.monogram),
            "banner" to (slight.banner to rounded.banner),
            "row" to (slight.row to rounded.row),
            "sheet" to (slight.sheet to rounded.sheet),
        ).forEach { (name, pair) ->
            assertTrue("$name must shrink for 'slightly rounded'", pair.first < pair.second)
        }
        // A pill has no radius left to reduce, so it stays a pill.
        assertEquals(null, slight.chip)
    }

    @Test fun squareIsSquare() {
        listOf(
            "extraSmall" to square.extraSmall,
            "small" to square.small,
            "medium" to square.medium,
            "large" to square.large,
            "extraLarge" to square.extraLarge,
            "button" to square.button,
            "field" to square.field,
            "stepper" to square.stepper,
            "segment" to square.segment,
            "monogram" to square.monogram,
            "banner" to square.banner,
            "row" to square.row,
            "sheet" to square.sheet,
        ).forEach { (name, value) ->
            assertEquals("$name must be pointy for 'square'", 0f.dp, value)
        }
        // Chips become a small rounded rectangle rather than a sliver.
        assertEquals(2f.dp, square.chip)
    }

    @Test fun theThreeStylesAreGenuinelyDifferent() {
        assertNotSame(rounded, slight)
        assertNotSame(slight, square)
        assertNotSame(rounded, square)
    }

    // ------------------------------------------------------- what the theme receives

    @Test fun theDefaultSettingsProduceTheShapesTheAppHasAlwaysUsed() {
        // The theme is handed this value, so the out-of-the-box app is pixel-identical to before.
        assertEquals(HubShapes.extraLarge.toString(), hubShapesFor(UiSettings.DEFAULT).extraLarge.toString())
        assertEquals(HubShapes.medium.toString(), hubShapesFor(UiSettings.DEFAULT).medium.toString())
        assertEquals(
            CornerStyle.ROUNDED,
            UiSettings.DEFAULT.cornerStyle,
        )
    }

    @Test fun everyCornerStyleProducesItsOwnShapeSet() {
        val sets = CornerStyle.values().map { hubShapesFor(UiSettings(cornerStyle = it)) }
        assertEquals(3, sets.map { it.medium.toString() }.toSet().size)
        assertEquals(3, sets.map { it.large.toString() }.toSet().size)
    }

    @Test fun theMaterialSlotsFollowThePreference() {
        // MaterialTheme.shapes is filled from hubShapesFor, so the five slots - and therefore every
        // component that already reads them (cards, groups, dialogs, rows, snackbars) - follow it.
        CornerStyle.values().forEach { style ->
            val shapes = hubShapesFor(UiSettings(cornerStyle = style))
            val r = radiiOf(style)
            // A shape slot built from the token must equal the slot the theme receives.
            assertEquals(RoundedCornerShape(r.extraSmall).toString(), shapes.extraSmall.toString())
            assertEquals(RoundedCornerShape(r.medium).toString(), shapes.medium.toString())
            assertEquals(RoundedCornerShape(r.extraLarge).toString(), shapes.extraLarge.toString())
        }
    }
}
