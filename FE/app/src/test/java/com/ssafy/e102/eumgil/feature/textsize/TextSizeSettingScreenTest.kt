package com.ssafy.e102.eumgil.feature.textsize

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.ssafy.e102.eumgil.core.model.TextSizePreference
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSizeSettingScreenTest {
    @Test
    fun `setting controls neutralize actual applied app text scale`() {
        val density = Density(density = 2f, fontScale = 1.3f)

        val neutralized = density.neutralizedForTextSizeSettingControls(appliedTextSizeScale = 1.3f)

        assertEquals(2f, neutralized.density, 0.0f)
        assertEquals(1.0f, neutralized.fontScale, 0.001f)
    }

    @Test
    fun `setting screen does not derive control density from selected preference`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/textsize/TextSizeSettingScreen.kt")
                .readText()
        val screenSection =
            source
                .substringAfter("fun TextSizeSettingScreen(")
                .substringBefore("@Composable\nprivate fun TextSizeSettingScaffold")

        assertTrue(screenSection.contains("LocalAppTextSizeScale.current"))
        assertTrue(screenSection.contains("neutralizedForTextSizeSettingControls(appliedTextSizeScale)"))
        assertFalse(screenSection.contains("neutralizedForTextSizeSettingControls(uiState.selectedPreference)"))
    }

    @Test
    fun `text size setting removes snackbar feedback from screen and route`() {
        val screenSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/textsize/TextSizeSettingScreen.kt")
                .readText()
        val routeSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/textsize/TextSizeSettingRoute.kt")
                .readText()

        assertFalse(screenSource.contains("SnackbarHost("))
        assertFalse(screenSource.contains("SnackbarHostState"))
        assertFalse(routeSource.contains("showSnackbar("))
        assertFalse(routeSource.contains("SnackbarHostState"))
    }

    @Test
    fun `preview density reapplies selected text scale from neutralized controls`() {
        val neutralized = Density(density = 2f, fontScale = 1.0f)

        val preview = neutralized.appliedForTextSizeSettingPreview(TextSizePreference.LARGE)

        assertEquals(2f, preview.density, 0.0f)
        assertEquals(1.15f, preview.fontScale, 0.001f)
    }

    @Test
    fun `option row density applies each option scale exactly`() {
        val density = Density(density = 2f, fontScale = 1.0f)

        val default = density.appliedForTextSizeSettingOption(TextSizePreference.DEFAULT)
        val large = density.appliedForTextSizeSettingOption(TextSizePreference.LARGE)
        val extraLarge = density.appliedForTextSizeSettingOption(TextSizePreference.EXTRA_LARGE)

        assertEquals(1.00f, default.fontScale, 0.001f)
        assertEquals(1.15f, large.fontScale, 0.001f)
        assertEquals(1.30f, extraLarge.fontScale, 0.001f)
    }

    @Test
    fun `Aa sample font size follows the same option scale exactly`() {
        assertEquals(40.sp, TextSizePreference.DEFAULT.optionSampleFontSize())
        assertEquals(46.sp, TextSizePreference.LARGE.optionSampleFontSize())
        assertEquals(52.sp, TextSizePreference.EXTRA_LARGE.optionSampleFontSize())
    }

    @Test
    fun `option row applies same option scale to Aa and labels`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/textsize/TextSizeSettingScreen.kt")
                .readText()
        val rowSection =
            source
                .substringAfter("private fun TextSizeOptionRow(")
                .substringBefore("@Composable\nprivate fun TextSizePreviewCard")

        assertTrue(rowSection.contains("fontSize = option.preference.optionSampleFontSize()"))
        assertTrue(rowSection.contains("lineHeight = option.preference.optionSampleLineHeight()"))
        assertTrue(rowSection.contains("val labelDensity ="))
        assertTrue(rowSection.contains("CompositionLocalProvider(LocalDensity provides labelDensity)"))
        assertFalse(rowSection.contains("fontSize = TextSizeOptionSampleFontSize"))
    }
}
