package de.easybolus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import de.easybolus.data.settings.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = HeaderGradientBottom,
    onPrimary = CardWhite,
    secondary = FabTeal,
    onSecondary = CardWhite,
    background = SurfaceLight,
    onBackground = TextPrimary,
    surface = CardWhite,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextSecondary,
    outlineVariant = Color(0xFFE2E8F0)
)

private val DarkColorScheme = darkColorScheme(
    primary = FabTeal,
    onPrimary = CardWhite,
    secondary = FabTeal,
    onSecondary = CardWhite,
    background = SurfaceDark,
    onBackground = TextPrimaryDark,
    surface = CardDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = TextSecondaryDark,
    outlineVariant = Color(0xFF334155)
)

@Composable
fun EasyBolusTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
        content = content
    )
}
