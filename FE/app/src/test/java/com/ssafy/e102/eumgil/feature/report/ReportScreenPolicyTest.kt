package com.ssafy.e102.eumgil.feature.report

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportScreenPolicyTest {
    @Test
    fun `report complete cta suppresses ripple because it opens report history`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/report/ReportScreen.kt")
                .readText()

        assertFalse(shouldSuppressReportPrimaryActionRipple(ReportStep.LocationConfirm))
        assertFalse(shouldSuppressReportPrimaryActionRipple(ReportStep.DetailInput))
        assertTrue(shouldSuppressReportPrimaryActionRipple(ReportStep.Complete))
        assertTrue(
            "Report screen should route completion CTA through a no-ripple navigation button helper.",
            source.contains("NoRippleReportPrimaryActionButton("),
        )
    }

    @Test
    fun `report step bottom cta stays above system navigation bar`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/report/ReportScreen.kt")
                .readText()
        val actionBarSection =
            source
                .substringAfter("private fun ReportPrimaryActionBar(")
                .substringBefore("@Composable\nprivate fun ReportPrimaryActionButton")

        assertTrue(
            "Report step CTA should reserve the system navigation safe zone when shown without the top-level tab bar.",
            actionBarSection.contains(".navigationBarsPadding()"),
        )
    }

    @Test
    fun `report step bottom safe zone is reserved only for navigation guidance entry`() {
        assertFalse(shouldReserveReportBottomNavigationInsets(ReportEntryPoint.TopLevel))
        assertTrue(shouldReserveReportBottomNavigationInsets(ReportEntryPoint.NavigationGuidance))
    }
}
