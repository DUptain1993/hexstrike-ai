package com.hexstrike.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val HexGreen = Color(0xFF39FF88)
val HexGreenDark = Color(0xFF1AAE5C)
val HexBackground = Color(0xFF0B0F0D)
val HexSurface = Color(0xFF141A17)
val HexSurfaceVariant = Color(0xFF1D2621)
val HexError = Color(0xFFFF5C5C)
val HexOnSurface = Color(0xFFE3F5EA)

private val HexColorScheme = darkColorScheme(
    primary = HexGreen,
    onPrimary = Color.Black,
    secondary = HexGreenDark,
    onSecondary = Color.Black,
    background = HexBackground,
    onBackground = HexOnSurface,
    surface = HexSurface,
    onSurface = HexOnSurface,
    surfaceVariant = HexSurfaceVariant,
    onSurfaceVariant = HexOnSurface,
    error = HexError,
)

private val HexTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 22.sp),
)

@Composable
fun HexStrikeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HexColorScheme,
        typography = HexTypography,
        content = content,
    )
}
