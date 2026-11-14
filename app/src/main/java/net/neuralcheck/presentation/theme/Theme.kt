package net.neuralcheck.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * NeuralCheck is **dark-only** by design. A consistent deep-space palette
 * makes the grade colours pop and — per the brand brief — reduces battery
 * draw during the 11-minute stress test where the screen may stay on.
 *
 * We therefore ignore `darkTheme` / dynamic-color and always apply the dark
 * scheme. The parameter is kept for API compatibility with callers that
 * still pass it (MainActivity, Navigation, tests).
 */
private val NeuralCheckDarkScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline
)

@Composable
fun NeuralCheckTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Phase 1.15 (edge-to-edge fix): window.statusBarColor /
            // navigationBarColor are deprecated no-ops on Android 15+ — the
            // platform forces transparent system bars under edge-to-edge — so
            // they were removed. We still set the bar ICON appearance (light
            // icons for the dark theme) via the insets controller, which works
            // on every supported API level.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
            // Suppress the "Build.VERSION check" lint warning — statusBarColor
            // and the light-icon toggle work on all supported API levels.
            @Suppress("UNUSED_VARIABLE")
            val apiLevel = Build.VERSION.SDK_INT
        }
    }

    MaterialTheme(
        colorScheme = NeuralCheckDarkScheme,
        typography = NeuralCheckTypography,
        content = content
    )
}
