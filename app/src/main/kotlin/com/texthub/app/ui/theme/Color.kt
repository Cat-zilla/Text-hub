package com.texthub.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Neutral surfaces plus a choice of accents.
 *
 * The neutrals never change: AMOLED is true black, dark is a deep slate, light is a soft grey.
 * Only the accent colour (buttons, chips, highlights, selection) moves, so switching accents
 * cannot hurt readability.
 */
object HubPalette {
    // Neutrals used by custom components (chips, badges, monograms)
    val Slate900 = Color(0xFF0B0F14)
    val Slate800 = Color(0xFF121821)
    val Slate700 = Color(0xFF1B2430)
    val Gray600 = Color(0xFFA9B5B2)
    val Gray400 = Color(0xFF6D7A78)

    /** Accent colours offered in Settings, in display order. */
    val Accents = listOf(
        AccentOption.TEAL,
        AccentOption.BLUE,
        AccentOption.INDIGO,
        AccentOption.VIOLET,
        AccentOption.ROSE,
        AccentOption.AMBER,
        AccentOption.GREEN,
        AccentOption.GRAPHITE,
    )
}

/**
 * An accent = one seed colour. Everything else (container shades, the colour that sits on top of
 * the accent) is derived from it, which keeps contrast predictable in dark, AMOLED and light.
 */
enum class AccentOption(val id: String, val seed: Color) {
    TEAL("teal", Color(0xFF0D9488)),
    BLUE("blue", Color(0xFF2563EB)),
    INDIGO("indigo", Color(0xFF4F46E5)),
    VIOLET("violet", Color(0xFF7C3AED)),
    ROSE("rose", Color(0xFFE11D48)),
    AMBER("amber", Color(0xFFB45309)),
    GREEN("green", Color(0xFF15803D)),
    GRAPHITE("graphite", Color(0xFF475569));

    /** Swatch shown in Settings, lifted a little so it stays visible on a black background. */
    fun swatch(dark: Boolean): Color = if (dark) seed.mix(Color.White, 0.30f) else seed

    /** Primary text colour that always contrasts with [swatch]. */
    fun contrastOn(dark: Boolean): Color = if (dark) seed.mix(Color.Black, 0.72f) else Color.White

    companion object {
        fun fromId(id: String?): AccentOption = values().firstOrNull { it.id == id } ?: TEAL
    }
}

private fun Color.mix(other: Color, fraction: Float): Color = Color(
    red = red + (other.red - red) * fraction,
    green = green + (other.green - green) * fraction,
    blue = blue + (other.blue - blue) * fraction,
    alpha = 1f,
)

/**
 * Re-colours a Material 3 scheme around an accent. Neutrals (background/surface/outline) are kept
 * exactly as they were so the app keeps its one consistent dark, AMOLED or light look.
 */
fun ColorScheme.withAccent(accent: AccentOption, dark: Boolean): ColorScheme {
    val seed = accent.seed
    return if (dark) {
        copy(
            primary = seed.mix(Color.White, 0.42f),
            onPrimary = seed.mix(Color.Black, 0.70f),
            primaryContainer = seed.mix(Color.Black, 0.52f),
            onPrimaryContainer = seed.mix(Color.White, 0.76f),
            secondary = seed.mix(Color.White, 0.55f),
            onSecondary = seed.mix(Color.Black, 0.70f),
            secondaryContainer = seed.mix(Color.Black, 0.62f),
            onSecondaryContainer = seed.mix(Color.White, 0.80f),
            surfaceTint = seed.mix(Color.White, 0.42f),
            inversePrimary = seed.mix(Color.Black, 0.30f),
        )
    } else {
        copy(
            primary = seed.mix(Color.Black, 0.10f),
            onPrimary = Color.White,
            primaryContainer = seed.mix(Color.White, 0.80f),
            onPrimaryContainer = seed.mix(Color.Black, 0.62f),
            secondary = seed.mix(Color.Black, 0.45f),
            onSecondary = Color.White,
            secondaryContainer = seed.mix(Color.White, 0.86f),
            onSecondaryContainer = seed.mix(Color.Black, 0.65f),
            surfaceTint = seed.mix(Color.Black, 0.10f),
            inversePrimary = seed.mix(Color.White, 0.55f),
        )
    }
}

val LightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = AccentOption.TEAL.seed,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC9EAE4),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF4A5C58),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2DFDB),
    onSecondaryContainer = Color(0xFF0B1F1B),
    background = Color(0xFFF6F8F7),
    onBackground = Color(0xFF161D1B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF161D1B),
    surfaceVariant = Color(0xFFEFF3F1),
    onSurfaceVariant = Color(0xFF3F4A47),
    surfaceTint = Color(0xFF0D6E63),
    outline = Color(0xFFBFCBC7),
    outlineVariant = Color(0xFFDDE6E3),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE3E0),
    onErrorContainer = Color(0xFF410E0B),
    scrim = Color(0xFF000000),
)

val DarkColorScheme = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFF5AD9C6),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFFB8F2E6),
    secondary = Color(0xFFB2CCC5),
    onSecondary = Color(0xFF1D352F),
    secondaryContainer = Color(0xFF344B45),
    onSecondaryContainer = Color(0xFFCFE8E1),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE1E6E4),
    surface = Color(0xFF121821),
    onSurface = Color(0xFFE1E6E4),
    surfaceVariant = Color(0xFF1B2430),
    onSurfaceVariant = Color(0xFFA9B5B2),
    surfaceTint = Color(0xFF5AD9C6),
    outline = Color(0xFF3B4753),
    outlineVariant = Color(0xFF232E39),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
)

/** AMOLED: identical to dark except every neutral surface is pushed to true black. */
val AmoledColorScheme = DarkColorScheme.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF0A0D12),
    outlineVariant = Color(0xFF1A1F27),
    outline = Color(0xFF2C3540),
)
