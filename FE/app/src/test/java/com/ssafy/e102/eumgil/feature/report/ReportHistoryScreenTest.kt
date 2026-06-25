package com.ssafy.e102.eumgil.feature.report

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportHistoryScreenTest {
    @Test
    fun `report history layout spec follows list card and button convention`() {
        val spec = reportHistoryLayoutSpec()

        assertEquals(18, spec.cardCornerRadiusDp)
        assertEquals(14, spec.thumbnailCornerRadiusDp)
        assertEquals(14, spec.buttonCornerRadiusDp)
        assertEquals(56, spec.buttonMinHeightDp)
        assertEquals(0, spec.cardShadowElevationDp)
    }

    @Test
    fun `content state keeps create report cta visible`() {
        assertTrue(shouldShowReportHistoryCreateCta(ReportHistoryScreenState.CONTENT))
    }

    @Test
    fun `non content states do not use the content create report cta`() {
        assertFalse(shouldShowReportHistoryCreateCta(ReportHistoryScreenState.LOADING))
        assertFalse(shouldShowReportHistoryCreateCta(ReportHistoryScreenState.EMPTY))
        assertFalse(shouldShowReportHistoryCreateCta(ReportHistoryScreenState.ERROR))
    }

    @Test
    fun `report history create report ctas suppress ripple when they open the report screen`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/report/ReportHistoryScreen.kt")
                .readText()
        val emptyStateSection =
            source
                .substringAfter("private fun ReportHistoryEmptyState(")
                .substringBefore("@Composable\nprivate fun ReportHistoryListCard")

        assertTrue(
            "Report-history empty state should use a dedicated no-ripple navigation button for report CTAs.",
            emptyStateSection.contains("NoRippleReportHistoryNavigationButton("),
        )
        assertTrue(
            "Report-history no-ripple CTA helper should disable ripple indication explicitly.",
            source.contains("indication = null"),
        )
    }

    @Test
    fun `report history loading states use shared centered loading style without card chrome`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/report/ReportHistoryScreen.kt")
                .readText()
        val listLoadingSection =
            source
                .substringAfter("ReportHistoryScreenState.LOADING ->")
                .substringBefore("ReportHistoryScreenState.EMPTY ->")
        val detailLoadingSection =
            source
                .substringAfter("if (detail == null) {")
                .substringBefore("} else {")
        val loadingStateSection =
            source
                .substringAfter("private fun ReportHistoryLoadingState(")
                .substringBefore("@Composable\nprivate fun ReportHistoryStateCard")

        assertTrue(
            "Report history list loading should use the shared loading state instead of the generic state card.",
            listLoadingSection.contains("ReportHistoryLoadingState(") &&
                !listLoadingSection.contains("ReportHistoryStateCard("),
        )
        assertTrue(
            "Report history detail loading should be centered inside the available detail area.",
            detailLoadingSection.contains("Modifier.fillParentMaxSize()") &&
                detailLoadingSection.contains("contentAlignment = Alignment.Center") &&
                detailLoadingSection.contains("ReportHistoryLoadingState("),
        )
        assertTrue(
            "Report history loading should share the app loading component.",
            loadingStateSection.contains("EumLoadingState("),
        )
        assertFalse(
            "Report history loading should not render card border, shadow, or local raw spinner chrome.",
            loadingStateSection.contains("Surface(") ||
                loadingStateSection.contains("BorderStroke(") ||
                loadingStateSection.contains("shadowElevation") ||
                loadingStateSection.contains("CircularProgressIndicator("),
        )
    }

    @Test
    fun `report history placeholder icon uses report specific drawable without changing tab icon`() {
        val reportScreenSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/report/ReportScreen.kt")
                .readText()
        val topLevelDestinationSource =
            File("src/main/java/com/ssafy/e102/eumgil/app/navigation/TopLevelDestination.kt")
                .readText()

        assertTrue(
            "Report location screen should use the dedicated report-map current-location drawable.",
            reportScreenSource.contains("R.drawable.ic_report_map_current_location"),
        )
        assertTrue(
            "The top-level report tab should keep the existing report navigation icon.",
            topLevelDestinationSource.contains("iconRes = R.drawable.ic_nav_report"),
        )
    }
}
