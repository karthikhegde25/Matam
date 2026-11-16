package com.kARThik.vote.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kARThik.vote.settings.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFF1F3A93), onPrimary = Color.White, primaryContainer = Color(0xFFDCE3FF), onPrimaryContainer = Color(0xFF001551),
    secondary = Color(0xFF00796B), secondaryContainer = Color(0xFFD7EEEA), onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFFE65100), background = Color(0xFFF6F7FB), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE3E6F0)
)
private val Dark = darkColorScheme(
    primary = Color(0xFFB4C3FF), onPrimary = Color(0xFF00227F), primaryContainer = Color(0xFF2B3E86), onPrimaryContainer = Color(0xFFDCE3FF),
    secondary = Color(0xFF7CD6C8), secondaryContainer = Color(0xFF1F4F49), onSecondaryContainer = Color(0xFFD7EEEA),
    tertiary = Color(0xFFFFB68A), background = Color(0xFF10131C), surface = Color(0xFF181C27), surfaceVariant = Color(0xFF2A2F3D)
)

@Composable
fun MatamTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> isSystemInDarkTheme() }
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)),
        content = content
    )
}
