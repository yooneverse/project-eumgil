package com.ssafy.e102.eumgil.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.ssafy.e102.eumgil.core.model.TextSizePreference

val LocalAppTextSizeScale = staticCompositionLocalOf { 1f }

@Composable
fun BusanEumgilTheme(
    textSizePreference: TextSizePreference = TextSizePreference.DEFAULT,
    content: @Composable () -> Unit,
) {
    BusanEumgilTheme(
        textSizeScale = textSizePreference.scale,
        content = content,
    )
}

@Composable
fun BusanEumgilTheme(
    textSizeScale: Float,
    content: @Composable () -> Unit,
) {
    val typography = PretendardTypography
    val density = LocalDensity.current
    val scaledDensity = remember(density, textSizeScale) {
        density.scaledByTextSizeScale(textSizeScale)
    }

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalAppTextSizeScale provides textSizeScale,
    ) {
        MaterialTheme(
            colorScheme = BusanEumgilLightColorScheme,
            typography = typography,
            content = content,
        )
    }
}

internal fun Density.scaledByTextSizeScale(textSizeScale: Float): Density {
    val scale = textSizeScale.takeIf { value -> value.isFinite() && value > 0f } ?: 1f
    if (scale == 1f) return this

    return Density(
        density = density,
        fontScale = fontScale * scale,
    )
}
