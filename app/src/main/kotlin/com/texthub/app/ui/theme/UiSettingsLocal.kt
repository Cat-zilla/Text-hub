package com.texthub.app.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.texthub.core.prefs.AnimationMode
import com.texthub.core.prefs.LayoutDensity
import com.texthub.core.prefs.UiSettings

/**
 * The 1.7.0 settings, available to every composable without threading them through parameters.
 * Provided once by [TextHubTheme]; the default is [UiSettings.DEFAULT], so previews and tests that
 * do not provide it get the app as it always was.
 */
val LocalUiSettings = staticCompositionLocalOf { UiSettings.DEFAULT }

/** Multiplier for the Large text option; sp-based, so the system font scale still applies on top. */
private const val LARGE_TEXT_SCALE = 1.15f

/** The type scale, enlarged for the Large text option. Line heights scale with the size, so nothing clips. */
fun Typography.scaled(factor: Float): Typography {
    fun TextStyle.s(): TextStyle = copy(
        fontSize = if (fontSize.isSpecified) fontSize * factor else fontSize,
        lineHeight = if (lineHeight.isSpecified) lineHeight * factor else lineHeight,
    )
    return Typography(
        displayLarge = displayLarge.s(), displayMedium = displayMedium.s(), displaySmall = displaySmall.s(),
        headlineLarge = headlineLarge.s(), headlineMedium = headlineMedium.s(), headlineSmall = headlineSmall.s(),
        titleLarge = titleLarge.s(), titleMedium = titleMedium.s(), titleSmall = titleSmall.s(),
        bodyLarge = bodyLarge.s(), bodyMedium = bodyMedium.s(), bodySmall = bodySmall.s(),
        labelLarge = labelLarge.s(), labelMedium = labelMedium.s(), labelSmall = labelSmall.s(),
    )
}

fun typographyFor(settings: UiSettings): Typography =
    if (settings.largeText) HubTypography.scaled(LARGE_TEXT_SCALE) else HubTypography

/** Vertical rhythm tokens that follow the layout density. Touch targets are not part of these. */
object Density {
    /** Inner padding of a section card. */
    val cardPadding: Dp
        @Composable @ReadOnlyComposable get() = if (LocalUiSettings.current.density == LayoutDensity.COMPACT) Spacing.md else Spacing.lg

    /** Gap between stacked section cards. */
    val cardGap: Dp
        @Composable @ReadOnlyComposable get() = if (LocalUiSettings.current.density == LayoutDensity.COMPACT) Spacing.sm else Spacing.md

    /** Gap between rows inside a card. */
    val rowGap: Dp
        @Composable @ReadOnlyComposable get() = if (LocalUiSettings.current.density == LayoutDensity.COMPACT) Spacing.sm else Spacing.md
}

/** Minimum height of the app's text actions; 48dp is the platform minimum and is never gone below. */
val touchTargetMin: Dp
    @Composable @ReadOnlyComposable get() = if (LocalUiSettings.current.largeTouchTargets) 56.dp else 48.dp

/**
 * An animation spec for *decorative* motion, following the UI animation setting: full, half-length
 * or an immediate snap. Not used for drag tracking or processing indicators, which are state, not
 * decoration.
 */
@Composable
@ReadOnlyComposable
fun <T> decorativeSpec(nominalMs: Int): AnimationSpec<T> {
    val ms = LocalUiSettings.current.animation.duration(nominalMs)
    return if (ms <= 0) snap() else tween(durationMillis = ms)
}

/** The scaled duration of a decorative animation, for code that drives an Animatable itself. */
@Composable
@ReadOnlyComposable
fun decorativeDuration(nominalMs: Int): Int = LocalUiSettings.current.animation.duration(nominalMs)

@Suppress("unused")
private val unusedAnimationMode: AnimationMode = AnimationMode.FULL
