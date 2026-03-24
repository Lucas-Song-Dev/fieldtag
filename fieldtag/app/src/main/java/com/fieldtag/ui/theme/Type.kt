package com.fieldtag.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.fieldtag.R

/**
 * Lexend (variable TTF in [R.font.lexend_variable]) — bundled for offline / industrial field use.
 * See plan: Google Fonts OFL; optimized for reading comfort.
 */
private val LexendFamily = FontFamily(
    Font(R.font.lexend_variable, FontWeight.Normal),
    Font(R.font.lexend_variable, FontWeight.Medium),
    Font(R.font.lexend_variable, FontWeight.SemiBold),
    Font(R.font.lexend_variable, FontWeight.Bold),
)

private fun Typography.withLexend(): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = LexendFamily),
    displayMedium = displayMedium.copy(fontFamily = LexendFamily),
    displaySmall = displaySmall.copy(fontFamily = LexendFamily),
    headlineLarge = headlineLarge.copy(fontFamily = LexendFamily),
    headlineMedium = headlineMedium.copy(fontFamily = LexendFamily),
    headlineSmall = headlineSmall.copy(fontFamily = LexendFamily),
    titleLarge = titleLarge.copy(fontFamily = LexendFamily),
    titleMedium = titleMedium.copy(fontFamily = LexendFamily),
    titleSmall = titleSmall.copy(fontFamily = LexendFamily),
    bodyLarge = bodyLarge.copy(fontFamily = LexendFamily),
    bodyMedium = bodyMedium.copy(fontFamily = LexendFamily),
    bodySmall = bodySmall.copy(fontFamily = LexendFamily),
    labelLarge = labelLarge.copy(fontFamily = LexendFamily),
    labelMedium = labelMedium.copy(fontFamily = LexendFamily),
    labelSmall = labelSmall.copy(fontFamily = LexendFamily),
)

val FieldTagTypography: Typography = Typography().withLexend()
