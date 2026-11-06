package com.adeshayat.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.adeshayat.app.R

val Emerald900 = Color(0xFF062A20)
val Emerald800 = Color(0xFF0B3D2E)
val Emerald700 = Color(0xFF105440)
val Emerald600 = Color(0xFF17694F)
val Emerald50 = Color(0xFFE8F3EE)
val Gold = Color(0xFFD4AF37)
val GoldLight = Color(0xFFF1D98A)
val Cream = Color(0xFFFBF7EE)
val Ink = Color(0xFF1E2B26)
val Muted = Color(0xFF6B7A73)

val HindSiliguri = FontFamily(
    Font(R.font.hindsiliguri_regular, FontWeight.Normal),
    Font(R.font.hindsiliguri_semibold, FontWeight.SemiBold),
    Font(R.font.hindsiliguri_bold, FontWeight.Bold),
)

val Amiri = FontFamily(
    Font(R.font.amiri_regular, FontWeight.Normal),
    Font(R.font.amiri_bold, FontWeight.Bold),
)

private val base = Typography()

private val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = HindSiliguri),
    displayMedium = base.displayMedium.copy(fontFamily = HindSiliguri),
    displaySmall = base.displaySmall.copy(fontFamily = HindSiliguri),
    headlineLarge = base.headlineLarge.copy(fontFamily = HindSiliguri),
    headlineMedium = base.headlineMedium.copy(fontFamily = HindSiliguri),
    headlineSmall = base.headlineSmall.copy(fontFamily = HindSiliguri),
    titleLarge = base.titleLarge.copy(fontFamily = HindSiliguri),
    titleMedium = base.titleMedium.copy(fontFamily = HindSiliguri),
    titleSmall = base.titleSmall.copy(fontFamily = HindSiliguri),
    bodyLarge = base.bodyLarge.copy(fontFamily = HindSiliguri),
    bodyMedium = base.bodyMedium.copy(fontFamily = HindSiliguri),
    bodySmall = base.bodySmall.copy(fontFamily = HindSiliguri),
    labelLarge = base.labelLarge.copy(fontFamily = HindSiliguri),
    labelMedium = base.labelMedium.copy(fontFamily = HindSiliguri),
    labelSmall = base.labelSmall.copy(fontFamily = HindSiliguri),
)

private val AppColors = lightColorScheme(
    primary = Emerald700,
    onPrimary = Color.White,
    secondary = Gold,
    onSecondary = Emerald900,
    background = Cream,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
)

@Composable
fun AdeshTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = AppTypography,
        content = content,
    )
}
