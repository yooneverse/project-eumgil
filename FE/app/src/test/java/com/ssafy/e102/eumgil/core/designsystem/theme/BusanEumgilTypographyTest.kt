package com.ssafy.e102.eumgil.core.designsystem.theme

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BusanEumgilTypographyTest {
    @Test
    fun `general mode font family is wired to Pretendard resources`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/core/designsystem/theme/Type.kt")
                .readText()

        assertTrue(source.contains("val PretendardFontFamily"))
        assertTrue(source.contains("R.font.pretendard_regular"))
        assertTrue(source.contains("R.font.pretendard_medium"))
        assertTrue(source.contains("R.font.pretendard_semibold"))
        assertTrue(source.contains("R.font.pretendard_bold"))
    }

    @Test
    fun `typography explicitly declares every role used by general mode screens`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/core/designsystem/theme/Type.kt")
                .readText()

        listOf(
            "displayLarge =",
            "headlineLarge =",
            "headlineMedium =",
            "headlineSmall =",
            "titleLarge =",
            "titleMedium =",
            "titleSmall =",
            "bodyLarge =",
            "bodyMedium =",
            "bodySmall =",
            "labelLarge =",
            "labelMedium =",
            "labelSmall =",
        ).forEach { roleAssignment ->
            assertTrue(
                "Type.kt must explicitly assign $roleAssignment so general mode screens do not fall back to Material defaults.",
                source.contains(roleAssignment),
            )
        }
    }

    @Test
    fun `general mode typography follows the Pretendard-ready weight matrix`() {
        assertEquals(FontWeight.Bold, PretendardTypography.displayLarge.fontWeight)
        assertEquals(FontWeight.Bold, PretendardTypography.headlineLarge.fontWeight)
        assertEquals(FontWeight.Bold, PretendardTypography.headlineMedium.fontWeight)
        assertEquals(FontWeight.SemiBold, PretendardTypography.headlineSmall.fontWeight)
        assertEquals(FontWeight.SemiBold, PretendardTypography.titleLarge.fontWeight)
        assertEquals(FontWeight.SemiBold, PretendardTypography.titleMedium.fontWeight)
        assertEquals(FontWeight.SemiBold, PretendardTypography.titleSmall.fontWeight)
        assertEquals(FontWeight.Normal, PretendardTypography.bodyLarge.fontWeight)
        assertEquals(FontWeight.Normal, PretendardTypography.bodyMedium.fontWeight)
        assertEquals(FontWeight.Normal, PretendardTypography.bodySmall.fontWeight)
        assertEquals(FontWeight.SemiBold, PretendardTypography.labelLarge.fontWeight)
        assertEquals(FontWeight.Medium, PretendardTypography.labelMedium.fontWeight)
        assertEquals(FontWeight.Medium, PretendardTypography.labelSmall.fontWeight)
    }

    @Test
    fun `general mode typography sizes form a complete readable scale`() {
        assertEquals(32.sp, PretendardTypography.displayLarge.fontSize)
        assertEquals(30.sp, PretendardTypography.headlineLarge.fontSize)
        assertEquals(28.sp, PretendardTypography.headlineMedium.fontSize)
        assertEquals(24.sp, PretendardTypography.headlineSmall.fontSize)
        assertEquals(22.sp, PretendardTypography.titleLarge.fontSize)
        assertEquals(18.sp, PretendardTypography.titleMedium.fontSize)
        assertEquals(16.sp, PretendardTypography.titleSmall.fontSize)
        assertEquals(16.sp, PretendardTypography.bodyLarge.fontSize)
        assertEquals(14.sp, PretendardTypography.bodyMedium.fontSize)
        assertEquals(12.sp, PretendardTypography.bodySmall.fontSize)
        assertEquals(14.sp, PretendardTypography.labelLarge.fontSize)
        assertEquals(12.sp, PretendardTypography.labelMedium.fontSize)
        assertEquals(11.sp, PretendardTypography.labelSmall.fontSize)
    }

    @Test
    fun `typography scale helper scales font size and line height only`() {
        val scaled = PretendardTypography.scaledBy(1.15f)

        assertEquals(18f * 1.15f, scaled.titleMedium.fontSize.value, 0.001f)
        assertEquals(26f * 1.15f, scaled.titleMedium.lineHeight.value, 0.001f)
        assertEquals(PretendardTypography.titleMedium.letterSpacing, scaled.titleMedium.letterSpacing)
    }

    @Test
    fun `typography scale helper keeps default scale as the base typography`() {
        assertSame(PretendardTypography, PretendardTypography.scaledBy(1.0f))
        assertSame(PretendardTypography, PretendardTypography.scaledBy(0f))
    }

    @Test
    fun `text size preference scales local density so direct sp tokens follow app setting`() {
        val scaled = Density(density = 2f, fontScale = 1.1f).scaledByTextSizeScale(1.3f)

        assertEquals(2f, scaled.density, 0.001f)
        assertEquals(1.1f * 1.3f, scaled.fontScale, 0.001f)
    }

    @Test
    fun `invalid text size scale keeps existing density unchanged`() {
        val base = Density(density = 2f, fontScale = 1.1f)

        assertSame(base, base.scaledByTextSizeScale(0f))
        assertSame(base, base.scaledByTextSizeScale(Float.NaN))
    }

    @Test
    fun `general mode typography tokens do not default to extra bold or black`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/core/designsystem/theme/Type.kt")
                .readText()
        val typographyBlock = source.substringAfter("val PretendardTypography =")

        assertFalse(typographyBlock.contains("FontWeight.ExtraBold"))
        assertFalse(typographyBlock.contains("FontWeight.Black"))
    }

    @Test
    fun `general theme wires MaterialTheme to Pretendard typography`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/core/designsystem/theme/Theme.kt")
                .readText()

        assertTrue(source.contains("textSizePreference.scale"))
        assertTrue(source.contains("LocalAppTextSizeScale provides textSizeScale"))
        assertTrue(source.contains("LocalDensity provides scaledDensity"))
        assertTrue(source.contains("typography = typography"))
    }
}
