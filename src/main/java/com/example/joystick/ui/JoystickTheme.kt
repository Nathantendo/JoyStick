package com.example.joystick.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BlueBlackColorScheme = darkColorScheme(
    primary = Color(0xFF1C2A3A),         // Deep blue-black for top bar and accents
    onPrimary = Color(0xFFE0E0E0),       // Soft white text on primary

    secondary = Color(0xFF2979FF),       // Bright blue for buttons
    onSecondary = Color.White,

    background = Color(0xFF181E2A),      // Blue-tinted black background
    onBackground = Color(0xFFD0D0D0),    // Light grey text

    surface = Color(0xFF1A1F27),         // Slightly lighter than background
    onSurface = Color(0xFFD0D0D0)
)

@Composable
fun JoystickTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BlueBlackColorScheme,
        typography = Typography(),
        content = content
    )
}
