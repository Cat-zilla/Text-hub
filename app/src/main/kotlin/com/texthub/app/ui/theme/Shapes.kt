package com.texthub.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.texthub.core.prefs.CornerStyle
import com.texthub.core.prefs.UiSettings

/**
 * The corner/shape system of Text Hub: **one** place where the *Corner style* preference becomes
 * concrete radii, and one set of named tokens every component reads.
 *
 * Why it is built this way
 * -----------------------
 * A corner preference that is honoured by `if (cornerStyle == …) RoundedCornerShape(…)` scattered
 * over the screens would drift the moment one surface is added, and it would put a preference read
 * into every leaf composable. Instead:
 *
 *  1. [hubShapesFor] turns a [UiSettings] into a Material 3 [Shapes] value, which
 *     [TextHubTheme] hands to `MaterialTheme`. Everything that already reads
 *     `MaterialTheme.shapes.*` (cards, groups, dialogs, rows, snackbars, the Material components'
 *     own defaults) follows the preference for free.
 *  2. The radii that are *not* a Material shape slot - buttons, fields, chips, the number stepper,
 *     a bottom sheet's top corners - are named tokens in [HubCorners], all resolved from the same
 *     table, so a new component never invents its own radius.
 *
 * Shapes whose geometry has a meaning of its own are deliberately **not** in this table and are
 * exposed as constants: [HubCorners.circle] (icon buttons, switches, radio buttons, indicators,
 * the accent swatches) and [HubCorners.pill] (the drag handle, and chips/badges while the corner
 * style is rounded). They stay what they are whatever the preference says.
 */

/**
 * Every radius the app uses, for one corner style. A plain value class so the three styles are one
 * table that can be read at a glance (and asserted in a test) instead of three `when` branches
 * duplicated across the UI.
 */
internal data class CornerRadii(
    // Material 3 shape slots.
    val extraSmall: Dp,
    val small: Dp,
    val medium: Dp,
    val large: Dp,
    val extraLarge: Dp,
    // Named tokens for surfaces that are not a Material shape slot.
    val button: Dp,
    val field: Dp,
    val stepper: Dp,
    val segment: Dp,
    val monogram: Dp,
    val banner: Dp,
    val row: Dp,
    val sheet: Dp,
    /**
     * Radius of a chip/badge, or null for "the pill the app has always used". A pill is already the
     * most rounded shape there is, so *slightly rounded* has nothing to reduce; only *square*
     * turns it into a small rounded rectangle.
     */
    val chip: Dp?,
) {
    fun shape(value: Dp): Shape = RoundedCornerShape(value)
}

private val ROUNDED = CornerRadii(
    extraSmall = 8.dp, small = 12.dp, medium = 16.dp, large = 20.dp, extraLarge = 28.dp,
    button = 16.dp, field = 14.dp, stepper = 12.dp, segment = 13.dp, monogram = 12.dp,
    banner = 12.dp, row = 12.dp, sheet = 28.dp,
    chip = null,
)

private val SLIGHT = CornerRadii(
    extraSmall = 6.dp, small = 9.dp, medium = 11.dp, large = 14.dp, extraLarge = 18.dp,
    button = 11.dp, field = 10.dp, stepper = 9.dp, segment = 10.dp, monogram = 9.dp,
    banner = 9.dp, row = 9.dp, sheet = 18.dp,
    chip = null,
)

private val SQUARE = CornerRadii(
    extraSmall = 0.dp, small = 0.dp, medium = 0.dp, large = 0.dp, extraLarge = 0.dp,
    button = 0.dp, field = 0.dp, stepper = 0.dp, segment = 0.dp, monogram = 0.dp,
    banner = 0.dp, row = 0.dp, sheet = 0.dp,
    chip = 2.dp,
)

/** The radii of one corner style. Pure, so a test can assert the whole table. */
internal fun radiiOf(style: CornerStyle): CornerRadii = when (style) {
    CornerStyle.ROUNDED -> ROUNDED
    CornerStyle.SLIGHT -> SLIGHT
    CornerStyle.SQUARE -> SQUARE
}

/**
 * The Material 3 shape set for [settings], following its corner style. [TextHubTheme] passes this to
 * `MaterialTheme`, so the five slots (extraSmall … extraLarge) are the corner language everywhere.
 */
fun hubShapesFor(settings: UiSettings): Shapes = radiiOf(settings.cornerStyle).let { r ->
    Shapes(
        extraSmall = RoundedCornerShape(r.extraSmall),
        small = RoundedCornerShape(r.small),
        medium = RoundedCornerShape(r.medium),
        large = RoundedCornerShape(r.large),
        extraLarge = RoundedCornerShape(r.extraLarge),
    )
}

/**
 * The named shape tokens of Text Hub, resolved from the current corner style. Read these instead of
 * writing a `RoundedCornerShape(...)` by hand.
 *
 * The Material slots ([extraSmall] … [extraLarge]) are the same values `MaterialTheme.shapes`
 * carries; they are repeated here so a component can pick the token that names its role rather than
 * guess a slot.
 */
object HubCorners {

    /**
     * Intentionally circular and never follows the corner style: icon buttons, switches, radio
     * buttons, the accent swatches, the drag handle.
     */
    val circle: Shape = CircleShape

    /**
     * The pill used for chips, badges and the compact text actions while the corner style keeps
     * them rounded. Chips and badges read [chip] instead, which becomes a small rounded rectangle
     * under *Square*.
     */
    val pill: Shape = RoundedCornerShape(percent = 50)

    private val radii: CornerRadii
        @Composable @ReadOnlyComposable get() = radiiOf(LocalUiSettings.current.cornerStyle)

    val extraSmall: Shape @Composable @ReadOnlyComposable get() = MaterialTheme.shapes.extraSmall
    val small: Shape @Composable @ReadOnlyComposable get() = MaterialTheme.shapes.small
    val medium: Shape @Composable @ReadOnlyComposable get() = MaterialTheme.shapes.medium
    val large: Shape @Composable @ReadOnlyComposable get() = MaterialTheme.shapes.large
    val extraLarge: Shape @Composable @ReadOnlyComposable get() = MaterialTheme.shapes.extraLarge

    /** Primary and secondary action buttons. */
    val button: Shape @Composable @ReadOnlyComposable get() = RoundedCornerShape(radii.button)

    /** Text fields, dropdowns and any other input surface. */
    val field: Shape @Composable @ReadOnlyComposable get() = RoundedCornerShape(radii.field)

    /** The number stepper's container and its inner field. */
    val stepper: Shape @Composable @ReadOnlyComposable get() = RoundedCornerShape(radii.stepper)

    /** One segment of a segmented control. */
    val segment: Shape @Composable @ReadOnlyComposable get() = RoundedCornerShape(radii.segment)

    /** A tool monogram badge. */
    val monogram: Shape @Composable @ReadOnlyComposable get() = RoundedCornerShape(radii.monogram)

    /** The error banner. */
    val banner: Shape @Composable @ReadOnlyComposable get() = RoundedCornerShape(radii.banner)

    /** A clickable row inside a card (analysis rows, tool rows, candidate rows). */
    val row: Shape @Composable @ReadOnlyComposable get() = RoundedCornerShape(radii.row)

    /** Chips and small badges: a pill while rounded, a small rounded rectangle when square. */
    val chip: Shape
        @Composable @ReadOnlyComposable get() = radii.chip?.let { RoundedCornerShape(it) } ?: pill

    /** The top corners of a modal bottom sheet. */
    val sheetTop: Shape @Composable @ReadOnlyComposable get() = RoundedCornerShape(
        topStart = radii.sheet,
        topEnd = radii.sheet,
    )
}
