package com.yayo.sshtunneling.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = ConsoleBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E6FF),
    onPrimaryContainer = Color(0xFF0B2E61),
    secondary = SignalCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB7EBF0),
    onSecondaryContainer = Color(0xFF002F34),
    tertiary = PulseAmber,
    tertiaryContainer = Color(0xFFFFE48A),
    surface = ConsolePaper,
    surfaceVariant = Color(0xFFE8EDF4),
    onSurface = ConsoleInk,
    onSurfaceVariant = Color(0xFF46515E),
    outline = Color(0xFF74808E),
    outlineVariant = Color(0xFFC7D0DB),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = ConsoleBlueDark,
    onPrimary = Color(0xFF062C62),
    primaryContainer = Color(0xFF21477F),
    onPrimaryContainer = Color(0xFFD8E6FF),
    secondary = SignalCyanDark,
    onSecondary = Color(0xFF00363C),
    secondaryContainer = Color(0xFF00515A),
    onSecondaryContainer = Color(0xFFB7EBF0),
    tertiary = PulseAmberDark,
    tertiaryContainer = Color(0xFF594400),
    surface = ConsoleNight,
    surfaceVariant = Color(0xFF202832),
    onSurface = Color(0xFFE5EAF1),
    onSurfaceVariant = Color(0xFFBAC4D0),
    outline = Color(0xFF8994A2),
    outlineVariant = Color(0xFF394451),
    error = Color(0xFFFFB4AB),
)

private val ConsoleShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun SshTunnelingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = ConsoleShapes,
        content = content,
    )
}
