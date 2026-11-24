// Generated with AI assistance (Claude Code, Claude Opus 5.5); reviewed by group
package com.group5.signaltrail.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Primary = Color(0xFF1565C0)
    val Accent = Color(0xFF00897B)
    val Background = Color(0xFFF5F7FA)
    val Surface = Color(0xFFFFFFFF)
    val Text = Color(0xFF1F2933)
    val Success = Color(0xFF2E7D32)
    val Warning = Color(0xFFF9A825)
    val Error = Color(0xFFC62828)
    val Neutral = Color(0xFF607D8B)
}

private val LightColors = lightColorScheme(
    primary = Palette.Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3F8),
    onPrimaryContainer = Color(0xFF0B3D78),
    secondary = Palette.Accent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCEDE9),
    onSecondaryContainer = Color(0xFF00423B),
    background = Palette.Background,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Color(0xFFE7ECF2),
    onSurfaceVariant = Color(0xFF4A5560),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F3F7),
    surfaceContainerHighest = Color(0xFFE7ECF2),
    error = Palette.Error,
    onError = Color.White,
    outline = Color(0xFF9AA5B1)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0B2E55),
    primaryContainer = Color(0xFF1E4C80),
    onPrimaryContainer = Color(0xFFD3E3F8),
    secondary = Color(0xFF4DB6AC),
    onSecondary = Color(0xFF00332E),
    secondaryContainer = Color(0xFF00574F),
    onSecondaryContainer = Color(0xFFCCEDE9),
    background = Color(0xFF111820),
    onBackground = Color(0xFFE3E8EE),
    surface = Color(0xFF1A232D),
    onSurface = Color(0xFFE3E8EE),
    surfaceVariant = Color(0xFF26313C),
    onSurfaceVariant = Color(0xFFB6C0CA),
    surfaceContainer = Color(0xFF1A232D),
    surfaceContainerLow = Color(0xFF1A232D),
    surfaceContainerHigh = Color(0xFF222C37),
    surfaceContainerHighest = Color(0xFF26313C),
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF5F1010),
    outline = Color(0xFF6B7784)
)

/** Status colours; always shown together with a text label (colour-blind friendly). */
data class StatusColors(val success: Color, val warning: Color, val error: Color, val neutral: Color)

@Composable
fun statusColors(): StatusColors =
    if (isSystemInDarkTheme()) StatusColors(Color(0xFF66BB6A), Color(0xFFFFCA28), Color(0xFFEF5350), Color(0xFF90A4AE))
    else StatusColors(Palette.Success, Palette.Warning, Palette.Error, Palette.Neutral)

@Composable
fun SignalTrailTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
