package com.astro5star.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.core.view.WindowCompat
import com.astro5star.app.utils.ThemeManager

private val LuxuryAstrologyScheme = darkColorScheme(
    primary = MetallicGold,
    onPrimary = DeepSpaceNavy,
    primaryContainer = CosmicBlue,
    onPrimaryContainer = StarWhite,

    secondary = AntiqueGold,
    onSecondary = DeepSpaceNavy,
    secondaryContainer = NebulaPurple,
    onSecondaryContainer = StarWhite,

    tertiary = ConstellationCyan,
    onTertiary = DeepSpaceNavy,

    background = DeepSpaceNavy,
    onBackground = StarWhite,

    surface = CosmicBlue,
    onSurface = StarWhite,
    surfaceVariant = NebulaPurple,
    onSurfaceVariant = StarWhite,

    outline = AntiqueGold,
    error = GalaxyViolet // using a theme color instead of Red
)

@Composable
fun AstrologyPremiumTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    customBg: androidx.compose.ui.graphics.Color? = null,
    customCard: androidx.compose.ui.graphics.Color? = null,
    customPrimary: androidx.compose.ui.graphics.Color? = null,
    customSecondary: androidx.compose.ui.graphics.Color? = null,
    content: @Composable () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentThemeState = ThemeManager.themeFlow.collectAsState()
    val themeId = currentThemeState.value

    // Initialize ThemeManager if needed
    LaunchedEffect(Unit) {
        ThemeManager.init(context)
    }

    var colorScheme = ThemeManager.getColorScheme(themeId)

    // Apply Overrides if present
    if (customBg != null) colorScheme = colorScheme.copy(background = customBg, surface = customBg)
    if (customCard != null) colorScheme = colorScheme.copy(surfaceVariant = customCard, surface = customCard) // Assuming Card uses surface
    if (customPrimary != null) colorScheme = colorScheme.copy(primary = customPrimary, onBackground = customPrimary)
    if (customSecondary != null) colorScheme = colorScheme.copy(secondary = customSecondary)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
