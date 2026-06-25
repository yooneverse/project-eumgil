package com.ssafy.e102.eumgil.feature.lowvision

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LowVisionFontProtectionTest {
    @Test
    fun `low vision font theme also rebinds Material typography to On Gothic`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/lowvision/LowVisionTypography.kt")
                .readText()

        assertTrue(source.contains("MaterialTheme("))
        assertTrue(source.contains("LowVisionFontFamily"))
        assertTrue(source.contains("fontFamily = LowVisionFontFamily"))
    }

    @Test
    fun `voice input route stays inside low vision font protection scope`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/lowvision/LowVisionVoiceInputRoute.kt")
                .readText()

        assertTrue(
            "LowVisionVoiceInputRoute must wrap its screen with LowVisionFontTheme so generic font changes do not leak into the low vision flow.",
            source.contains("LowVisionFontTheme {"),
        )
    }

    @Test
    fun `terms guide route stays inside low vision font protection scope`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/terms/TermsGuideRoute.kt")
                .readText()

        assertTrue(
            "TermsGuideRoute must wrap its screen with LowVisionFontTheme so the low vision onboarding guide keeps On Gothic even after general mode font changes.",
            source.contains("LowVisionFontTheme {"),
        )
    }
}
