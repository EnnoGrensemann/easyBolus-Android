package de.easybolus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val EasyBolusColorScheme = lightColorScheme(
    primary = HeaderGradientBottom,
    onPrimary = CardWhite,
    secondary = FabTeal,
    onSecondary = CardWhite,
    background = SurfaceLight,
    onBackground = TextPrimary,
    surface = CardWhite,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextSecondary
)

@Composable
fun EasyBolusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EasyBolusColorScheme,
        content = content
    )
}
