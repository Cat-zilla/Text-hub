package com.texthub.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.compositeOver
import com.texthub.core.prefs.UiSettings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class AppTheme(val id: String) {
    SYSTEM("system"),
    DARK("dark"),
    AMOLED("amoled"),
    LIGHT("light");

    companion object {
        fun fromId(id: String): AppTheme = values().firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * Consistent corner radii across the whole app, as the app has always looked. This is the
 * *Rounded* corner style; [hubShapesFor] derives the other two from the same table.
 */
val HubShapes = hubShapesFor(UiSettings.DEFAULT)

/** Spacing scale: one system, used everywhere. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
}

/**
 * @param theme  System / Dark / AMOLED / Light - controls the neutral surfaces.
 * @param accent the single accent colour; only primary/secondary roles are recoloured.
 */
@Composable
fun TextHubTheme(
    theme: AppTheme = AppTheme.SYSTEM,
    accent: AccentOption = AccentOption.TEAL,
    settings: UiSettings = UiSettings.DEFAULT,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (theme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK, AppTheme.AMOLED -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val baseScheme = when (theme) {
        AppTheme.AMOLED -> AmoledColorScheme
        AppTheme.DARK -> DarkColorScheme
        AppTheme.LIGHT -> LightColorScheme
        AppTheme.SYSTEM -> if (darkTheme) DarkColorScheme else LightColorScheme
    }
    val context = LocalContext.current
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = if (settings.dynamicColor && dynamicAvailable) {
        // Dynamic colour: the system palette replaces the accent *while enabled*. The stored accent
        // is untouched, so switching dynamic colour off brings the user's choice straight back.
        // AMOLED keeps its black background over the dynamic palette.
        val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        if (theme == AppTheme.AMOLED) dynamic.copy(background = baseScheme.background, surface = baseScheme.surface) else dynamic
    } else {
        baseScheme.withAccent(accent, darkTheme)
    }.let { scheme -> if (settings.highContrast) scheme.withHighContrast(darkTheme) else scheme }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val background = colorScheme.background
            window.statusBarColor = background.toArgb()
            window.navigationBarColor = background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalUiSettings provides settings) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typographyFor(settings),
            // The corner style is resolved here, once, exactly like the type scale: every
            // MaterialTheme.shapes.* read below this point follows the preference, and changing it
            // recomposes the tree without recreating the activity.
            shapes = hubShapesFor(settings),
            content = content,
        )
    }
}

/**
 * High-contrast controls: stronger outlines and borders, a more distinct container for selected
 * states and a firmer muted text - on top of whatever theme and accent are active, so light, dark,
 * AMOLED and dynamic colour all keep their own look.
 */
fun ColorScheme.withHighContrast(dark: Boolean): ColorScheme = copy(
    outline = onBackground.copy(alpha = 0.85f),
    outlineVariant = onBackground.copy(alpha = 0.45f),
    onSurfaceVariant = onSurface.copy(alpha = 0.92f),
    surfaceVariant = if (dark) Color.White.copy(alpha = 0.16f).compositeOver(surface) else Color.Black.copy(alpha = 0.10f).compositeOver(surface),
    primaryContainer = if (dark) primary.copy(alpha = 0.35f).compositeOver(surface) else primary.copy(alpha = 0.22f).compositeOver(surface),
)

/** Card container colour that keeps a subtle lift in every theme. */
val cardContainerColor: Color
    @Composable get() = MaterialTheme.colorScheme.surface

/** Muted text colour used for hints, stats and metadata. */
val mutedTextColor: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

/** A soft translucent panel used for monograms and badges. */
val softPanelColor: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
