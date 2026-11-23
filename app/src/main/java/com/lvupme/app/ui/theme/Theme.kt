package com.lvupme.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Mood C: Vibrant Gamification Design Tokens (Duolingo-inspired crisp colors & tactile contrast)
 */
data class GamePalette(
    val isDark: Boolean,
    val emerald: Color,
    val emeraldLip: Color,
    val emeraldSoft: Color,
    val sky: Color,
    val skyLip: Color,
    val skySoft: Color,
    val gold: Color,
    val goldLip: Color,
    val goldSoft: Color,
    val violet: Color,
    val violetLip: Color,
    val violetSoft: Color,
    val coral: Color,
    val coralLip: Color,
    val coralSoft: Color,
    val cardBg: Color,
    val cardBorder: Color,
    val cardLip: Color,
    val trackBg: Color,
    val subtleSurface: Color
)

private val DarkGamePalette = GamePalette(
    isDark = true,
    emerald = Color(0xFF58CC02),
    emeraldLip = Color(0xFF439E00),
    emeraldSoft = Color(0xFF193616),
    sky = Color(0xFF1CB0F6),
    skyLip = Color(0xFF1286BF),
    skySoft = Color(0xFF143144),
    gold = Color(0xFFFFB020),
    goldLip = Color(0xFFCC8200),
    goldSoft = Color(0xFF3A2D12),
    violet = Color(0xFFCE82FF),
    violetLip = Color(0xFF9E52D1),
    violetSoft = Color(0xFF2D1E42),
    coral = Color(0xFFFF4B4B),
    coralLip = Color(0xFFCC2E2E),
    coralSoft = Color(0xD93D1A1F),
    cardBg = Color(0xD9182932),
    cardBorder = Color(0xFF365363),
    cardLip = Color(0xFF233844),
    trackBg = Color(0xCC2A3E4A),
    subtleSurface = Color(0xD914232B)
)

private val LightGamePalette = GamePalette(
    isDark = false,
    emerald = Color(0xFF58CC02),
    emeraldLip = Color(0xFF46A302),
    emeraldSoft = Color(0xE8E7FAD6),
    sky = Color(0xFF1CB0F6),
    skyLip = Color(0xFF1899D6),
    skySoft = Color(0xE8DDF4FF),
    gold = Color(0xFFFF9600),
    goldLip = Color(0xFFCC7800),
    goldSoft = Color(0xE8FFF3D6),
    violet = Color(0xFFA560E8),
    violetLip = Color(0xFF8442C7),
    violetSoft = Color(0xE8F3E8FF),
    coral = Color(0xFFFF4B4B),
    coralLip = Color(0xFFD93636),
    coralSoft = Color(0xE8FFE5E5),
    cardBg = Color(0xE6FFFFFF),
    cardBorder = Color(0xFFD3E4EC),
    cardLip = Color(0xFFB7D1DE),
    trackBg = Color(0xCCDFECF3),
    subtleSurface = Color(0xE0F4F9FC)
)

val LocalGamePalette = staticCompositionLocalOf { DarkGamePalette }

val MaterialTheme.game: GamePalette
    @Composable
    get() = LocalGamePalette.current

private val DarkColors = darkColorScheme(
    primary = Color(0xFF58CC02),
    onPrimary = Color(0xFF0A1F00),
    primaryContainer = Color(0xFF1D3B16),
    onPrimaryContainer = Color(0xFFD8FFB8),
    secondary = Color(0xFF1CB0F6),
    onSecondary = Color(0xFF042233),
    secondaryContainer = Color(0xFF1E3847),
    onSecondaryContainer = Color(0xFFD6F1FF),
    tertiary = Color(0xFFFFB020),
    onTertiary = Color(0xFF2E1B00),
    tertiaryContainer = Color(0xFF3D2C0F),
    onTertiaryContainer = Color(0xFFFFE4AE),
    background = Color(0xFF131F24),
    onBackground = Color(0xFFF4F9FC),
    surface = Color(0xFF16242C),
    onSurface = Color(0xFFF4F9FC),
    surfaceVariant = Color(0xFF20333D),
    onSurfaceVariant = Color(0xFF94B0C0),
    surfaceContainerLowest = Color(0xFF0F191E),
    surfaceContainerLow = Color(0xFF16242C),
    surfaceContainer = Color(0xFF1C2C35),
    surfaceContainerHigh = Color(0xFF223540),
    surfaceContainerHighest = Color(0xFF2A404D),
    outline = Color(0xFF375363),
    outlineVariant = Color(0xFF283D49),
    error = Color(0xFFFF5C5C),
    onError = Color.White,
    errorContainer = Color(0xFF471A1E),
    onErrorContainer = Color(0xFFFFD6D6)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF58CC02),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7FAD6),
    onPrimaryContainer = Color(0xFF1E4D00),
    secondary = Color(0xFF1CB0F6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF4FF),
    onSecondaryContainer = Color(0xFF0A496B),
    tertiary = Color(0xFFFF9600),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF3D6),
    onTertiaryContainer = Color(0xFF5C3500),
    background = Color(0xFFF6F9FC),
    onBackground = Color(0xFF1E293B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFEDF3F8),
    onSurfaceVariant = Color(0xFF5B7083),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FBFD),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1F6FA),
    surfaceContainerHighest = Color(0xFFE7EFF5),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFEA2B2B),
    onError = Color.White,
    errorContainer = Color(0xFFFFE5E5),
    onErrorContainer = Color(0xFF7F1D1D)
)

@Composable
fun LvUpMeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    val palette = if (darkTheme) DarkGamePalette else LightGamePalette
    CompositionLocalProvider(LocalGamePalette provides palette) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content
        )
    }
}
