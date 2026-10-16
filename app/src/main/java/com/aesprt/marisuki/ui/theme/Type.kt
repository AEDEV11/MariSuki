package com.aesprt.marisuki.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aesprt.marisuki.R

/** Bundled static Inter weights; Android supplies its normal fallback for missing glyphs. */
val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun interStyle(
    fontWeight: FontWeight,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
) = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = fontWeight,
    fontSize = fontSize,
    lineHeight = lineHeight,
    // Inter includes tabular figures, which keep changing peso values easy to compare.
    fontFeatureSettings = "tnum",
)

/** A readable POS type scale shared by Material 3 components and app screens. */
val Typography = Typography(
    displayLarge = interStyle(FontWeight.Normal, 57.sp, 64.sp),
    displayMedium = interStyle(FontWeight.Normal, 45.sp, 52.sp),
    displaySmall = interStyle(FontWeight.Normal, 36.sp, 44.sp),
    headlineLarge = interStyle(FontWeight.SemiBold, 30.sp, 38.sp),
    headlineMedium = interStyle(FontWeight.SemiBold, 26.sp, 34.sp),
    headlineSmall = interStyle(FontWeight.SemiBold, 24.sp, 32.sp),
    titleLarge = interStyle(FontWeight.SemiBold, 20.sp, 27.sp),
    titleMedium = interStyle(FontWeight.Medium, 18.sp, 24.sp),
    titleSmall = interStyle(FontWeight.Medium, 16.sp, 22.sp),
    bodyLarge = interStyle(FontWeight.Normal, 16.sp, 24.sp),
    bodyMedium = interStyle(FontWeight.Normal, 14.sp, 20.sp),
    bodySmall = interStyle(FontWeight.Normal, 13.sp, 18.sp),
    labelLarge = interStyle(FontWeight.SemiBold, 16.sp, 20.sp),
    labelMedium = interStyle(FontWeight.Medium, 14.sp, 18.sp),
    labelSmall = interStyle(FontWeight.Medium, 12.sp, 16.sp),
)
