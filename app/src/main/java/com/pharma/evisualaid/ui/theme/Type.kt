package com.pharma.evisualaid.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Brand typography.
 *
 * The brand spec calls for Nunito Sans (display), Inter (body) and Roboto Mono (metadata).
 * To keep the app 100% offline and build without bundled binaries, the families below map
 * to system equivalents by default. To use the exact brand fonts, drop the .ttf files into
 * app/src/main/res/font/ and replace the family definitions — e.g.
 *
 *   val DisplayFamily = FontFamily(Font(R.font.nunito_sans_bold, FontWeight.Bold))
 *   val BodyFamily    = FontFamily(Font(R.font.inter_regular), Font(R.font.inter_medium, FontWeight.Medium))
 *   val MonoFamily    = FontFamily(Font(R.font.roboto_mono))
 *
 * See README → "Brand fonts" for the full drop-in instructions.
 */
val DisplayFamily = FontFamily.SansSerif // Nunito Sans
val BodyFamily = FontFamily.SansSerif    // Inter
val MonoFamily = FontFamily.Monospace    // Roboto Mono (tabular page numbers/metadata)

val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = (-0.25).sp
    ),
    titleMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    labelMedium = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    )
)
