package com.k410sh4.budsia.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF05070B)
val Panel = Color(0xFF10151D)
val Panel2 = Color(0xFF171D27)
val Cyan = Color(0xFF6DE7FF)
val Violet = Color(0xFF9B83FF)
val Green = Color(0xFF72E7A9)
val Amber = Color(0xFFFFC857)
val Red = Color(0xFFFF6C7C)
val TextPrimary = Color(0xFFF4F7FB)
val TextMuted = Color(0xFFA8B0BD)

private val dark = darkColorScheme(
    primary = Cyan,
    secondary = Violet,
    tertiary = Green,
    background = Ink,
    surface = Panel,
    surfaceVariant = Panel2,
    onPrimary = Ink,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextMuted,
    error = Red
)

@Composable
fun BudsIATheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = dark,
        typography = Typography(),
        content = content
    )
}
