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
    displayLarge = interStyle(FontWeight.Bold, 48.sp, 56.sp),
    displayMedium = interStyle(FontWeight.Bold, 40.sp, 48.sp),
    displaySmall = interStyle(FontWeight.Bold, 34.sp, 42.sp),
    headlineLarge = interStyle(FontWeight.Bold, 28.sp, 36.sp),
    headlineMedium = interStyle(FontWeight.Bold, 24.sp, 32.sp),
    headlineSmall = interStyle(FontWeight.SemiBold, 22.sp, 28.sp),
    titleLarge = interStyle(FontWeight.SemiBold, 18.sp, 24.sp),
    titleMedium = interStyle(FontWeight.SemiBold, 16.sp, 22.sp),
    titleSmall = interStyle(FontWeight.Medium, 14.sp, 20.sp),
    bodyLarge = interStyle(FontWeight.Normal, 15.sp, 22.sp),
    bodyMedium = interStyle(FontWeight.Normal, 14.sp, 20.sp),
    bodySmall = interStyle(FontWeight.Normal, 12.sp, 18.sp),
    labelLarge = interStyle(FontWeight.SemiBold, 14.sp, 20.sp),
    labelMedium = interStyle(FontWeight.Medium, 12.sp, 16.sp),
    labelSmall = interStyle(FontWeight.Medium, 11.sp, 16.sp),
)

/** Semantic POS typography tokens for financial displays, tables, and operational hierarchy. */
object PosType {
    val screenTitle = interStyle(FontWeight.Bold, 22.sp, 28.sp)
    val sectionHeading = interStyle(FontWeight.SemiBold, 16.sp, 22.sp)
    val cardTitle = interStyle(FontWeight.SemiBold, 15.sp, 20.sp)
    val bodyPrimary = interStyle(FontWeight.Normal, 14.sp, 20.sp)
    val bodySecondary = interStyle(FontWeight.Normal, 12.sp, 18.sp)
    val inputLabel = interStyle(FontWeight.Medium, 13.sp, 18.sp)
    val buttonLabel = interStyle(FontWeight.SemiBold, 14.sp, 20.sp)
    val tableHeader = interStyle(FontWeight.SemiBold, 12.sp, 16.sp)
    val monetaryAmount = interStyle(FontWeight.Bold, 16.sp, 22.sp)
    val transactionTotal = interStyle(FontWeight.Bold, 26.sp, 32.sp)
    val dialogTitle = interStyle(FontWeight.Bold, 18.sp, 24.sp)
    val errorMessage = interStyle(FontWeight.Medium, 13.sp, 18.sp)
}
