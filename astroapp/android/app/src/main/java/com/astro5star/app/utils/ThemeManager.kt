package com.astro5star.app.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.astro5star.app.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeID {
    LUXURY, VEDIC, COSMIC, SOLAR, LUNAR, FOREST, OCEAN, ROYAL, MYSTIC, MIDNIGHT
}

object ThemeManager {
    private const val PREF_NAME = "theme_prefs"
    private const val KEY_THEME = "selected_theme"
    private const val KEY_BANNER_URI = "banner_uri"
    private const val KEY_CUSTOM_FONT_COLOR = "custom_font_color"

    private val _themeFlow = MutableStateFlow(AppThemeID.LUXURY)
    val themeFlow: StateFlow<AppThemeID> = _themeFlow.asStateFlow()

    private val _bannerUriFlow = MutableStateFlow<String?>(null)
    val bannerUriFlow: StateFlow<String?> = _bannerUriFlow.asStateFlow()

    private val _customFontColorFlow = MutableStateFlow<Color?>(null)
    val customFontColorFlow: StateFlow<Color?> = _customFontColorFlow.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val themeName = prefs.getString(KEY_THEME, AppThemeID.LUXURY.name) ?: AppThemeID.LUXURY.name
        _themeFlow.value = AppThemeID.valueOf(themeName)

        val banner = prefs.getString(KEY_BANNER_URI, null)
        _bannerUriFlow.value = banner

        val fontColor = prefs.getInt(KEY_CUSTOM_FONT_COLOR, -1)
        if (fontColor != -1) {
            _customFontColorFlow.value = Color(fontColor)
        }
    }

    fun setTheme(context: Context, theme: AppThemeID) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, theme.name).apply()
        _themeFlow.value = theme
    }

    fun setBannerUri(context: Context, uri: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BANNER_URI, uri).apply()
        _bannerUriFlow.value = uri
    }

    fun setCustomFontColor(context: Context, color: Int) {
         val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_CUSTOM_FONT_COLOR, color).apply()
        _customFontColorFlow.value = Color(color)
    }

    fun getColorScheme(theme: AppThemeID): ColorScheme {
        return when (theme) {
            AppThemeID.LUXURY -> darkColorScheme(
                primary = LuxuryPrimary, background = LuxuryBackground, surface = LuxurySurface,
                onPrimary = Color.White, onBackground = CharcoalDark, onSurface = LuxuryOnSurface
            )
            AppThemeID.VEDIC -> darkColorScheme(
                primary = VedicSaffron, background = VedicMaroon, surface = Color(0xFF600000), // Darker Maroon
                onPrimary = Color.White, onBackground = VedicCream, onSurface = VedicCream
            )
            AppThemeID.COSMIC -> darkColorScheme(
                primary = CosmicNeon, background = CosmicDark, surface = Color(0xFF101020),
                onPrimary = Color.Black, onBackground = Color.White, onSurface = Color.White
            )
            AppThemeID.SOLAR -> darkColorScheme(
                primary = SolarYellow, background = Color(0xFFBF360C), surface = Color(0xFFD84315),
                onPrimary = Color.Black, onBackground = Color.White, onSurface = Color.White
            )
            AppThemeID.LUNAR -> darkColorScheme(
                primary = LunarSilver, background = LunarNight, surface = Color(0xFF37474F),
                onPrimary = Color.Black, onBackground = LunarWhite, onSurface = LunarWhite
            )
            AppThemeID.FOREST -> darkColorScheme(
                primary = ForestGold, background = ForestDark, surface = ForestGreen,
                onPrimary = Color.Black, onBackground = Color.White, onSurface = Color.White
            )
            AppThemeID.OCEAN -> darkColorScheme(
                primary = OceanFoam, background = OceanDeep, surface = OceanBlue,
                onPrimary = OceanDeep, onBackground = Color.White, onSurface = Color.White
            )
            AppThemeID.ROYAL -> darkColorScheme(
                primary = RoyalGoldAccent, background = RoyalPurple, surface = Color(0xFF6A1B9A),
                onPrimary = RoyalPurple, onBackground = RoyalCream, onSurface = RoyalCream
            )
            AppThemeID.MYSTIC -> darkColorScheme(
                primary = MysticPink, background = MysticDark, surface = MysticMagenta,
                onPrimary = MysticDark, onBackground = Color.White, onSurface = Color.White
            )
            AppThemeID.MIDNIGHT -> darkColorScheme(
                primary = MidnightStar, background = MidnightBlack, surface = MidnightGray,
                onPrimary = MidnightBlack, onBackground = MidnightStar, onSurface = MidnightStar
            )
        }
    }

    fun applyTheme(activity: android.app.Activity) {
        val prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val themeName = prefs.getString(KEY_THEME, AppThemeID.LUXURY.name) ?: AppThemeID.LUXURY.name
        val themeId = try {
            AppThemeID.valueOf(themeName)
        } catch (e: Exception) {
            AppThemeID.LUXURY
        }

        val styleId = when (themeId) {
            AppThemeID.LUXURY -> com.astro5star.app.R.style.Theme_FCMCallApp_Luxury
            AppThemeID.VEDIC -> com.astro5star.app.R.style.Theme_FCMCallApp_Vedic
            AppThemeID.COSMIC -> com.astro5star.app.R.style.Theme_FCMCallApp_Cosmic
            AppThemeID.SOLAR -> com.astro5star.app.R.style.Theme_FCMCallApp_Solar
            AppThemeID.LUNAR -> com.astro5star.app.R.style.Theme_FCMCallApp_Lunar
            AppThemeID.FOREST -> com.astro5star.app.R.style.Theme_FCMCallApp_Forest
            AppThemeID.OCEAN -> com.astro5star.app.R.style.Theme_FCMCallApp_Ocean
            AppThemeID.ROYAL -> com.astro5star.app.R.style.Theme_FCMCallApp_Royal
            AppThemeID.MYSTIC -> com.astro5star.app.R.style.Theme_FCMCallApp_Mystic
            AppThemeID.MIDNIGHT -> com.astro5star.app.R.style.Theme_FCMCallApp_Midnight
        }
        activity.setTheme(styleId)
    }
}
