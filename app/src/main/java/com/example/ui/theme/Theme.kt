package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = TgCyanPrimary,
    onPrimary = TgCyanOnPrimary,
    primaryContainer = TgDarkSurfaceContainer,
    onPrimaryContainer = TgCyanPrimary,
    secondary = TgBlueSecondary,
    onSecondary = Color.Black,
    secondaryContainer = TgDarkSurfaceVariant,
    onSecondaryContainer = TgBlueSecondary,
    tertiary = TgRustAccent,
    onTertiary = TgRustOnAccent,
    background = TgDarkBackground,
    onBackground = TgTextPrimary,
    surface = TgDarkSurface,
    onSurface = TgTextPrimary,
    surfaceVariant = TgDarkSurfaceVariant,
    onSurfaceVariant = TgTextSecondary,
    error = TgRedError,
    onError = Color.White,
    outline = TgBorder
)

// We optimize for dark developer/terminal mode by default
private val LightColorScheme = DarkColorScheme

@Composable
fun TelegramCoreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
