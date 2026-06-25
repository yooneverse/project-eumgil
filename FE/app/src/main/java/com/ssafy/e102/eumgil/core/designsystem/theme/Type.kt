package com.ssafy.e102.eumgil.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ssafy.e102.eumgil.R

val PretendardFontFamily =
    FontFamily(
        Font(R.font.pretendard_regular, FontWeight.Normal),
        Font(R.font.pretendard_medium, FontWeight.Medium),
        Font(R.font.pretendard_semibold, FontWeight.SemiBold),
        Font(R.font.pretendard_bold, FontWeight.Bold),
    )

private fun pretendardTextStyle(
    fontWeight: FontWeight,
    fontSize: Int,
    lineHeight: Int,
) = TextStyle(
    fontFamily = PretendardFontFamily,
    fontWeight = fontWeight,
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp,
)

val PretendardTypography =
    Typography(
        displayLarge = pretendardTextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 32,
            lineHeight = 40,
        ),
        headlineLarge = pretendardTextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 30,
            lineHeight = 38,
        ),
        headlineMedium = pretendardTextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 28,
            lineHeight = 36,
        ),
        headlineSmall = pretendardTextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 24,
            lineHeight = 32,
        ),
        titleLarge = pretendardTextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 22,
            lineHeight = 30,
        ),
        titleMedium = pretendardTextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 18,
            lineHeight = 26,
        ),
        titleSmall = pretendardTextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 16,
            lineHeight = 24,
        ),
        bodyLarge = pretendardTextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 16,
            lineHeight = 24,
        ),
        bodyMedium = pretendardTextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 14,
            lineHeight = 22,
        ),
        bodySmall = pretendardTextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 12,
            lineHeight = 20,
        ),
        labelLarge = pretendardTextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 14,
            lineHeight = 20,
        ),
        labelMedium = pretendardTextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 12,
            lineHeight = 18,
        ),
        labelSmall = pretendardTextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 11,
            lineHeight = 16,
        ),
    )

/**
 * Compose `sp` already follows Android system font scale, so this app-level scale is multiplied
 * on top of the OS setting. QA should cover EXTRA_LARGE together with OS font scale 1.3x/1.5x.
 */
fun Typography.scaledBy(textSizeScale: Float): Typography {
    val scale = textSizeScale.takeIf { value -> value.isFinite() && value > 0f } ?: 1f
    if (scale == 1f) return this

    return copy(
        displayLarge = displayLarge.scaledBy(scale),
        displayMedium = displayMedium.scaledBy(scale),
        displaySmall = displaySmall.scaledBy(scale),
        headlineLarge = headlineLarge.scaledBy(scale),
        headlineMedium = headlineMedium.scaledBy(scale),
        headlineSmall = headlineSmall.scaledBy(scale),
        titleLarge = titleLarge.scaledBy(scale),
        titleMedium = titleMedium.scaledBy(scale),
        titleSmall = titleSmall.scaledBy(scale),
        bodyLarge = bodyLarge.scaledBy(scale),
        bodyMedium = bodyMedium.scaledBy(scale),
        bodySmall = bodySmall.scaledBy(scale),
        labelLarge = labelLarge.scaledBy(scale),
        labelMedium = labelMedium.scaledBy(scale),
        labelSmall = labelSmall.scaledBy(scale),
    )
}

private fun TextStyle.scaledBy(scale: Float): TextStyle =
    copy(
        fontSize = fontSize.scaledBy(scale),
        lineHeight = lineHeight.scaledBy(scale),
    )

private fun TextUnit.scaledBy(scale: Float): TextUnit =
    if (this == TextUnit.Unspecified) {
        this
    } else {
        (value * scale).sp
    }
