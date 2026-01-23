package com.astro5star.app.ui.theme

import androidx.compose.ui.graphics.Color

// ===========================================================================
// PROFESSIONAL DESIGN SYSTEM (Purple + White + Gray)
// ===========================================================================

// 1. Core Brand Palette
val BrandPurple = Color(0xFF7C3AED)        // Primary Vibrant Purple
val BrandPurpleDark = Color(0xFF5B21B6)    // Darker Purple (pressed states)
val BrandPurpleLight = Color(0xFFA78BFA)   // Light Purple (highlights)
val PureWhite = Color(0xFFFFFFFF)          // Pure White
val MilkPurple = Color(0xFFF5F3FF)         // Soft Lavender Background
val AppBackgroundWhite = MilkPurple        // App Background (Milk Purple)

// 2. Professional Grays
val Gray900 = Color(0xFF111827)   // Near Black (primary text)
val Gray700 = Color(0xFF374151)   // Dark Professional Gray
val Gray600 = Color(0xFF4B5563)   // Secondary text
val Gray500 = Color(0xFF6B7280)   // Muted Gray
val Gray400 = Color(0xFF9CA3AF)   // Placeholder text
val Gray200 = Color(0xFFE5E7EB)   // Borders/Dividers
val Gray100 = Color(0xFFF3F4F6)   // Surface Balance/Cards

// 3. Typography Colors
val TextPrimaryDark = Color(0xFF111827)    // Primary text on light bg
val TextSecondaryDark = Color(0xFF4B5563)  // Secondary text on light bg
val TextPlaceholder = Color(0xFF9CA3AF)    // Placeholder text
val TextOnPurple = Color(0xFFFFFFFF)       // Text on purple backgrounds

// 4. Status Colors
val StatusGreen = Color(0xFF10B981)  // Success, Online
val StatusRed = Color(0xFFEF4444)    // Error (internal use only)
val StatusYellow = Color(0xFFF59E0B) // Warning

// ===========================================================================
// LEGACY THEME COLORS (Mapped to new system for backward compatibility)
// ===========================================================================

// 1. Luxury (Default - Now mapped to Purple + White)
val LuxuryPrimary = BrandPurple
val LuxuryBackground = PureWhite
val LuxurySurface = PureWhite
val LuxuryOnSurface = Gray900
val PremiumGold = BrandPurple  // Gold replaced with Purple

// 2. Vedic (Traditional) - keeping for theme switching
val VedicSaffron = Color(0xFFFF9933)
val VedicMaroon = Color(0xFF800000)
val VedicCream = Color(0xFFFFFDD0)

// 3. Cosmic (Sci-Fi)
val CosmicNeon = Color(0xFF00E5FF)
val CosmicDark = Color(0xFF050510)
val CosmicPurple = BrandPurple

// 4. Solar (Energy)
val SolarRed = Color(0xFFFF3D00)
val SolarOrange = Color(0xFFFF9100)
val SolarYellow = Color(0xFFFFEA00)

// 5. Lunar (Calm)
val LunarSilver = Gray400
val LunarNight = Gray900
val LunarWhite = Gray100

// 6. Forest (Green Luxury) - mapped to Purple
val ForestGreen = BrandPurpleDark
val ForestDark = BrandPurpleDark
val ForestGold = BrandPurple
val ForestSurface = BrandPurpleLight

// 7. Ocean (Water)
val OceanBlue = Color(0xFF0288D1)
val OceanDeep = Color(0xFF01579B)
val OceanFoam = Color(0xFFE1F5FE)

// 8. Royal (Majestic) - mapped to Purple
val RoyalPurple = BrandPurpleDark
val RoyalGoldAccent = BrandPurple
val RoyalCream = Gray100

// 9. Mystic (Magic)
val MysticMagenta = Color(0xFFC2185B)
val MysticDark = BrandPurpleDark
val MysticPink = BrandPurpleLight

// 10. Midnight (Minimal)
val MidnightBlack = Gray900
val MidnightStar = PureWhite
val MidnightGray = Gray700

// Current Active Colors (Mapped to Professional Theme)
val DivineCream = PureWhite
val LuxuryCardGreen = PureWhite
val CharcoalDark = Gray900
val CardText = Gray900

// Current Active Colors for Compose Theme
val DeepSpaceNavy = PureWhite         // Base Background -> White
val CosmicBlue = PureWhite            // Secondary Background -> White
val NebulaPurple = Gray100            // Popup Surface -> Light Gray
val GalaxyViolet = BrandPurple        // Accents -> Purple
val ConstellationCyan = BrandPurpleLight
val StarWhite = Gray900               // Default Text -> Dark
val MetallicGold = BrandPurple        // Primary Accent -> Purple
val AntiqueGold = BrandPurpleLight    // Secondary Accent
val SunOrange = BrandPurple           // Contrast

// Aliases for Backward Compatibility
val RoyalMidnightBlue = PureWhite
val PeacockTeal = BrandPurple
val PeacockGreen = BrandPurpleLight
val RoyalGold = BrandPurple
val FeatherEye = BrandPurple
val SoftIvory = Gray900
val MoonDarkGreen = PureWhite

// Functional Colors
val VoidBlack = Color(0xFF000000)
val ErrorRed = StatusRed  // For compilation safety only
