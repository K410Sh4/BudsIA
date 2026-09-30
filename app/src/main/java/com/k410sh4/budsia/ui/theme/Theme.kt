package com.k410sh4.budsia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BudsIADarkScheme = darkColorScheme(
    primary = Color(0xFF55E6FF),
    secondary = Color(0xFF9C7CFF),
    background = Color(0xFF05070B),
    surface = Color(0xFF0D1219),
    surfaceVariant = Color(0xFF151D27),
    onPrimary = Color(0xFF001F26),
    onBackground = Color(0xFFF4F7FA),
    onSurface = Color(0xFFF4F7FA),
    onSurfaceVariant = Color(0xFFAAB7C4),
    outline = Color(0xFF44515D),
    error = Color(0xFFFF6B7C)
)

@Composable
fun BudsIATheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BudsIADarkScheme,
        content = content
    )
}
