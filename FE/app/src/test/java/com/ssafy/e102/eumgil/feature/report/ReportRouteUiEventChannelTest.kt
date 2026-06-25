package com.ssafy.e102.eumgil.feature.report

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 1.1 — ReportRoute UI 이벤트 채널 일괄 연결 회귀 가드.
 *
 * `ReportViewModel`이 emit 중인 `AnnounceForAccessibility`(3곳) / `ScrollToFirstError`(1곳)
 * 이벤트가 다시 `Unit`으로 무시되는 상태로 회귀하지 않도록 막는다.
 */
class ReportRouteUiEventChannelTest {
    @Test
    fun `report route announces accessibility events through host view`() {
        val source = reportRouteSource()

        assertTrue(
            "ReportRoute should obtain LocalView so it can announce accessibility messages.",
            source.contains("val view = LocalView.current"),
        )
        assertTrue(
            "ReportRoute should announce AnnounceForAccessibility events via view.announceForAccessibility.",
            source.contains("view.announceForAccessibility(event.message)"),
        )
    }

    @Test
    fun `report route scrolls to top on ScrollToFirstError`() {
        val source = reportRouteSource()

        assertTrue(
            "ReportRoute should remember a ScrollState for hoisting to ReportScreen.",
            source.contains("val scrollState = rememberScrollState()"),
        )
        assertTrue(
            "ReportRoute should animate scroll to top when ScrollToFirstError is emitted.",
            source.contains("ReportUiEvent.ScrollToFirstError -> scrollState.animateScrollTo(0)"),
        )
    }

    @Test
    fun `report screen accepts scroll state from route`() {
        val source = reportScreenSource()

        assertTrue(
            "ReportScreen should accept a ScrollState so route can control scroll for ScrollToFirstError.",
            source.contains("scrollState: ScrollState"),
        )
        assertTrue(
            "ReportScreen should use the hoisted scrollState for the scrollable Column.",
            source.contains("Modifier.verticalScroll(scrollState)"),
        )
    }

    @Test
    fun `report home keeps fixed header content and scrolls recent reports inside remaining space`() {
        val source = reportScreenSource()
        val screenSection =
            source
                .substringAfter("fun ReportScreen(")
                .substringBefore("@Composable\nprivate fun ReportBottomBar")
        val homeStepSection =
            source
                .substringAfter("private fun ReportHomeStep(")
                .substringBefore("@Composable\nprivate fun ReportHomeCtaCard")
        val recentSection =
            source
                .substringAfter("private fun ReportHomeRecentSection(")
                .substringBefore("@Composable\nprivate fun ReportHomeRecentItem")

        assertTrue(
            "Top-level report screen should disable default system insets because AppNavHost already reserves the bottom tab area.",
            screenSection.contains("contentWindowInsets = WindowInsets(0, 0, 0, 0)"),
        )
        assertTrue(
            "Report home should be a flex step so the CTA/status sections stay fixed instead of being clipped by the shared vertical scroll.",
            screenSection.contains("val isHomeStep = uiState.currentStep == ReportStep.Home") &&
                screenSection.contains("val isFlexStep = isHomeStep || uiState.currentStep == ReportStep.TypeSelection"),
        )
        assertTrue(
            "Report home should fill the available content height and give recent reports the remaining space.",
            screenSection.contains("ReportHomeStep(") &&
                screenSection.contains("modifier = Modifier.weight(1f).fillMaxWidth()") &&
                homeStepSection.contains("modifier.fillMaxSize()") &&
                homeStepSection.contains("ReportHomeRecentSection(") &&
                homeStepSection.contains("modifier = Modifier.weight(1f)"),
        )
        assertTrue(
            "Recent reports should scroll inside their card instead of stretching the whole home page past the bottom edge.",
            recentSection.contains("LazyColumn(") &&
                recentSection.contains(".fillMaxSize()") &&
                recentSection.contains("itemsIndexed("),
        )
        assertTrue(
            "Recent reports should keep bottom padding so the final row can scroll clear of the rounded card edge.",
            recentSection.contains("bottom = EumSpacing.medium"),
        )
    }

    @Test
    fun `report non-home forms keep the shared scroll state`() {
        val source = reportScreenSource()
        val scrollableContentSection =
            source
                .substringAfter("val isFlexStep = isHomeStep || uiState.currentStep == ReportStep.TypeSelection")
                .substringBefore("verticalArrangement = Arrangement.spacedBy(EumSpacing.medium)")

        assertTrue(
            "Non-home report form steps should still use the hoisted scroll state for validation error scrolling.",
            scrollableContentSection.contains("Modifier.verticalScroll(scrollState)"),
        )
        assertTrue(
            "Report content should keep bottom padding inside its available viewport.",
            scrollableContentSection.contains("bottom = EumSpacing.medium"),
        )
    }

    @Test
    fun `start new request consume does not dispatch normal route enter again`() {
        val source = reportRouteSource()

        assertTrue(
            "Normal route entry should be keyed only by entryPoint so consuming startNewRequest=false does not re-enter Home.",
            source.contains("LaunchedEffect(entryPoint, viewModel)") &&
                source.contains("if (!startNewRequest)") &&
                source.contains("startNew = false"),
        )
        assertTrue(
            "Start-new route entry should be handled in a separate true-only effect before consuming the request.",
            source.contains("LaunchedEffect(entryPoint, startNewRequest, viewModel)") &&
                source.contains("if (startNewRequest)") &&
                source.contains("startNew = true") &&
                source.contains("onStartNewRequestConsumed()"),
        )
        assertTrue(
            "ReportRoute must not pass startNewRequest directly to RouteEntered because consume false would replay RouteEntered(startNew=false).",
            !source.contains("startNew = startNewRequest"),
        )
    }

    private fun reportRouteSource(): String =
        File("src/main/java/com/ssafy/e102/eumgil/feature/report/ReportRoute.kt").readText()

    private fun reportScreenSource(): String =
        File("src/main/java/com/ssafy/e102/eumgil/feature/report/ReportScreen.kt").readText()
}
