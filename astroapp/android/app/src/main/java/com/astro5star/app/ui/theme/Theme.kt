package com.astro5star.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.core.view.WindowCompat
import com.astro5star.app.utils.ThemeManager

// Professional Purple + White + Gray Color Scheme
private val ProfessionalLightScheme = lightColorScheme(
    primary = BrandPurple,
    onPrimary = PureWhite,
    primaryContainer = BrandPurpleLight,
    onPrimaryContainer = PureWhite,

    secondary = BrandPurpleDark,
    onSecondary = PureWhite,
    secondaryContainer = Gray100,
    onSecondaryContainer = Gray900,

    tertiary = BrandPurpleLight,
    onTertiary = Gray900,

    background = MilkPurple,
    onBackground = Gray900,

    surface = PureWhite,
    onSurface = Gray900,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray700,

    outline = Gray200,
    error = StatusRed
)

@Composable
fun AstrologyPremiumTheme(
    darkTheme: Boolean = false, // Default to light theme
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

    // Use the professional light color scheme
    var colorScheme = ProfessionalLightScheme

    // Apply Overrides if present
    if (customBg != null) colorScheme = colorScheme.copy(background = customBg, surface = customBg)
    if (customCard != null) colorScheme = colorScheme.copy(surfaceVariant = customCard, surface = customCard)
    if (customPrimary != null) colorScheme = colorScheme.copy(primary = customPrimary)
    if (customSecondary != null) colorScheme = colorScheme.copy(secondary = customSecondary)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // White status bar with dark icons for professional look
            window.statusBarColor = PureWhite.toArgb()
            window.navigationBarColor = PureWhite.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
