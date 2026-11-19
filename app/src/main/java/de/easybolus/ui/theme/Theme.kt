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
    primaryContainer = TealContainerLight,
    onPrimaryContainer = OnTealContainerLight,
    secondary = FabTeal,
    onSecondary = CardWhite,
    secondaryContainer = TealContainerLight,
    onSecondaryContainer = OnTealContainerLight,
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
    primaryContainer = TealContainerDark,
    onPrimaryContainer = OnTealContainerDark,
    secondary = FabTeal,
    onSecondary = CardWhite,
    secondaryContainer = TealContainerDark,
    onSecondaryContainer = OnTealContainerDark,
    background = SurfaceDark,
    onBackground = TextPrimaryDark,
    surface = CardDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = TextSecondaryDark,
    outlineVariant = Color(0xFF2E2E2E)
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
