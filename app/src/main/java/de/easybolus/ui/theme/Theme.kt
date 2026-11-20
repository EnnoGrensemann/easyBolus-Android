package de.easybolus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import de.easybolus.data.settings.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = GmailPrimaryLight,
    onPrimary = CardWhite,
    primaryContainer = GmailContainerLight,
    onPrimaryContainer = OnGmailContainerLight,
    secondary = GmailPrimaryLight,
    onSecondary = CardWhite,
    secondaryContainer = GmailContainerLight,
    onSecondaryContainer = OnGmailContainerLight,
    background = SurfaceLight,
    onBackground = TextPrimary,
    surface = CardWhite,
    onSurface = TextPrimary,
    surfaceContainer = NavBarLight,
    surfaceContainerLow = CardWhite,
    surfaceContainerHigh = Color(0xFFE6E8EF),
    surfaceVariant = Color(0xFFE8EAEE),
    onSurfaceVariant = TextSecondary,
    outlineVariant = Color(0xFFDADCE0)
)

private val DarkColorScheme = darkColorScheme(
    primary = GmailPrimaryDark,
    onPrimary = Color(0xFF1A2949),
    primaryContainer = GmailContainerDark,
    onPrimaryContainer = OnGmailContainerDark,
    secondary = GmailPrimaryDark,
    onSecondary = Color(0xFF1A2949),
    secondaryContainer = GmailContainerDark,
    onSecondaryContainer = OnGmailContainerDark,
    background = SurfaceDark,
    onBackground = TextPrimaryDark,
    surface = CardDark,
    onSurface = TextPrimaryDark,
    surfaceContainer = NavBarDark,
    surfaceContainerLow = CardDark,
    surfaceContainerHigh = Color(0xFF2F3136),
    surfaceVariant = Color(0xFF303136),
    onSurfaceVariant = TextSecondaryDark,
    outlineVariant = Color(0xFF3A3C42)
)

@Composable
fun isEasyBolusDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun EasyBolusTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = isEasyBolusDarkTheme(themeMode)
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamic = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        // Gmail-like clear card vs canvas separation while preserving full Material You accents
        if (isDark) {
            dynamic.copy(
                background = dynamic.surfaceContainerLowest,
                surface = dynamic.surfaceContainerLow
            )
        } else {
            dynamic.copy(
                background = dynamic.surfaceContainerLow,
                surface = Color.White
            )
        }
    } else {
        if (isDark) DarkColorScheme else LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
