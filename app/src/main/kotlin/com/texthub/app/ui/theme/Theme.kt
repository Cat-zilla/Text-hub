package com.texthub.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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

/** Consistent corner radii across the whole app. */
val HubShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

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
    val colorScheme = baseScheme.withAccent(accent, darkTheme)

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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HubTypography,
        shapes = HubShapes,
        content = content,
    )
}

/** Card container colour that keeps a subtle lift in every theme. */
val cardContainerColor: Color
    @Composable get() = MaterialTheme.colorScheme.surface

/** Muted text colour used for hints, stats and metadata. */
val mutedTextColor: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

/** A soft translucent panel used for monograms and badges. */
val softPanelColor: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
