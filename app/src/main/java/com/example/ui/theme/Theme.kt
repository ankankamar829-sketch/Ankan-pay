package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FinTechColorScheme = darkColorScheme(
    primary = MintNeon,
    onPrimary = CarbonBg,
    primaryContainer = MintGlow,
    onPrimaryContainer = MintNeon,
    secondary = SkyBlue,
    onSecondary = CarbonBg,
    tertiary = SolarAmber,
    onTertiary = CarbonBg,
    background = CarbonBg,
    onBackground = TextWhite,
    surface = CarbonSurface,
    onSurface = TextWhite,
    surfaceVariant = CarbonCard,
    onSurfaceVariant = TextGray,
    outline = CarbonBorder,
    error = AlertRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FinTechColorScheme,
        typography = Typography,
        content = content
    )
}
