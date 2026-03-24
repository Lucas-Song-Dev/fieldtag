package com.fieldtag.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
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

private val FieldTagDarkColors = darkColorScheme(
    primary = Color(0xFFCBD5E0),
    onPrimary = Color(0xFF1A202C),
    primaryContainer = Color(0xFF4A5568),
    secondary = Color(0xFFFFB300),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF5C4A00),
    background = Color(0xFF1A202C),
    surface = Color(0xFF2D3748),
    error = Color(0xFFFC8181),
)

@Composable
fun FieldTagTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** When true and API 31+, uses system dynamic colors instead of the fixed industrial palette. */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> FieldTagDarkColors
        else -> FieldTagLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FieldTagTypography,
        content = content,
    )
}
