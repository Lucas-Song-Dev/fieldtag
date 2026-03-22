package com.fieldtag.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val FieldTagLightColors = lightColorScheme(
    primary = Color(0xFF2D3748), // Deep Slate
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A5568),
    secondary = Color(0xFFFFB300), // High-Viz Yellow
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFFFE082),
    background = Color(0xFFF7FAFC), // Off-white/slate
    surface = Color.White,
    error = Color(0xFFE53E3E),
)

@Composable
fun FieldTagTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FieldTagLightColors,
        content = content,
    )
}
