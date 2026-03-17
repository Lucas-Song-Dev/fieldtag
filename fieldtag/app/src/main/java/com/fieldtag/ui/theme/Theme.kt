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
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFFF57C00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0B2),
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    error = Color(0xFFB71C1C),
)

@Composable
fun FieldTagTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FieldTagLightColors,
        content = content,
    )
}
