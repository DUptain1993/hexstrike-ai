package com.vulnrbot.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val VulnrGreen = Color(0xFF39FF88)
val VulnrGreenDark = Color(0xFF1AAE5C)
val VulnrBackground = Color(0xFF0B0F0D)
val VulnrSurface = Color(0xFF141A17)
val VulnrSurfaceVariant = Color(0xFF1D2621)
val VulnrError = Color(0xFFFF5C5C)
val VulnrOnSurface = Color(0xFFE3F5EA)

private val VulnrColorScheme = darkColorScheme(
    primary = VulnrGreen,
    onPrimary = Color.Black,
    secondary = VulnrGreenDark,
    onSecondary = Color.Black,
    background = VulnrBackground,
    onBackground = VulnrOnSurface,
    surface = VulnrSurface,
    onSurface = VulnrOnSurface,
    surfaceVariant = VulnrSurfaceVariant,
    onSurfaceVariant = VulnrOnSurface,
    error = VulnrError,
)

private val VulnrTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 22.sp),
)

@Composable
fun VulnrBotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VulnrColorScheme,
        typography = VulnrTypography,
        content = content,
    )
}
