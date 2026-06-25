package com.ssafy.e102.eumgil.feature.lowvision

import androidx.annotation.FontRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Typography
import com.ssafy.e102.eumgil.R

internal data class LowVisionFontResource(
    @FontRes val fontRes: Int,
    val weight: FontWeight,
)

internal fun lowVisionFontResources(): List<LowVisionFontResource> =
    listOf(
        LowVisionFontResource(R.font.koddi_ud_on_gothic_regular, FontWeight.Normal),
        LowVisionFontResource(R.font.koddi_ud_on_gothic_bold, FontWeight.Bold),
        LowVisionFontResource(R.font.koddi_ud_on_gothic_extra_bold, FontWeight.ExtraBold),
        LowVisionFontResource(R.font.koddi_ud_on_gothic_extra_bold, FontWeight.Black),
    )

internal val LowVisionFontFamily =
    FontFamily(
        lowVisionFontResources().map { fontResource ->
            Font(
                resId = fontResource.fontRes,
                weight = fontResource.weight,
            )
        },
    )

@Composable
internal fun LowVisionFontTheme(content: @Composable () -> Unit) {
    val baseTypography = MaterialTheme.typography
    val lowVisionTypography =
        remember(baseTypography) {
            baseTypography.withLowVisionFontFamily()
        }

    ProvideTextStyle(value = LocalTextStyle.current.merge(TextStyle(fontFamily = LowVisionFontFamily))) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            typography = lowVisionTypography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}

private fun Typography.withLowVisionFontFamily(): Typography =
    Typography(
        displayLarge = displayLarge.copy(fontFamily = LowVisionFontFamily),
        displayMedium = displayMedium.copy(fontFamily = LowVisionFontFamily),
        displaySmall = displaySmall.copy(fontFamily = LowVisionFontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = LowVisionFontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = LowVisionFontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = LowVisionFontFamily),
        titleLarge = titleLarge.copy(fontFamily = LowVisionFontFamily),
        titleMedium = titleMedium.copy(fontFamily = LowVisionFontFamily),
        titleSmall = titleSmall.copy(fontFamily = LowVisionFontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = LowVisionFontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = LowVisionFontFamily),
        bodySmall = bodySmall.copy(fontFamily = LowVisionFontFamily),
        labelLarge = labelLarge.copy(fontFamily = LowVisionFontFamily),
        labelMedium = labelMedium.copy(fontFamily = LowVisionFontFamily),
        labelSmall = labelSmall.copy(fontFamily = LowVisionFontFamily),
    )
