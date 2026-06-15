package com.folhetosmart.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = FolhetoSmartGreen,
    onPrimary = Color.White,
    primaryContainer = SavingsBadge,
    onPrimaryContainer = FolhetoSmartGreenDark,
    secondary = FolhetoSmartGreenLight,
    onSecondary = Color.White,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = Color.White,
    onSurface = OnSurfaceLight,
    outline = OutlineLight,
    error = ErrorRed,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = FolhetoSmartGreenLight,
    onPrimary = Color.Black,
    secondary = FolhetoSmartGreen,
    background = Color(0xFF111411),
    surface = Color(0xFF1A1C19),
    error = ErrorRed
)

@Composable
fun FolhetoSmartTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
