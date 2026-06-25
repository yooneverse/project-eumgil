package com.ssafy.e102.eumgil.feature.lowvision

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.e102.eumgil.feature.navigation.NavigationGuidanceAction
import com.ssafy.e102.eumgil.feature.navigation.NavigationScreenState
import com.ssafy.e102.eumgil.feature.navigation.NavigationStepCardUiState
import com.ssafy.e102.eumgil.feature.navigation.NavigationUiAction
import com.ssafy.e102.eumgil.feature.navigation.NavigationUiState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LowVisionNavigationScreenTest {
    @Test
    fun `navigation screen keeps only exit action card for low vision mode`() {
        assertEquals(
            listOf("\uC548\uB0B4 \uC644\uB8CC"),
            lowVisionNavigationActionCards().map(LowVisionNavigationActionCard::label),
        )
    }

    @Test
    fun `navigation screen exposes distance and eta as middle summary metrics`() {
        assertEquals(
            listOf("\uB0A8\uC740 \uAC70\uB9AC", "\uB0A8\uC740 \uC2DC\uAC04"),
            lowVisionNavigationMetricSections().map(LowVisionNavigationMetricSection::label),
        )
    }

    @Test
    fun `navigation exit card completes navigation directly without hidden confirm dialog`() {
        assertEquals(NavigationUiAction.NavigationCompleteClicked, lowVisionNavigationExitAction())
    }

    @Test
    fun `navigation exit card uses brand yellow as the action color`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/lowvision/LowVisionNavigationScreen.kt")
                .readText()
        val exitCardSource =
            source.substringAfter("private fun LowVisionExitNavigationCard")
                .substringBefore("private fun lowVisionGuidanceIconSize")

        assertTrue(exitCardSource.contains("color = LowVisionNavigationYellow,"))
        assertTrue(exitCardSource.contains("color = Color.Black.copy(alpha = contentAlpha)"))
    }

    @Test
    fun `navigation live guidance prioritizes current action and detail text`() {
        val display =
            lowVisionNavigationLiveGuidanceDisplay(
                NavigationUiState(
                    screenState = NavigationScreenState.Ready,
                    stepCard =
                        NavigationStepCardUiState(
                            heroTitle = "\uD6A1\uB2E8\uBCF4\uB3C4 \uAC74\uB108\uAE30",
                            heroDescription = "\uC2E0\uD638\uB97C \uD655\uC778\uD558\uACE0 \uD6A1\uB2E8\uBCF4\uB3C4\uB97C \uAC74\uB108\uC138\uC694.",
                            instruction = "50m \uC9C1\uC9C4 \uD6C4 \uD6A1\uB2E8\uBCF4\uB3C4",
                            guidanceAction = NavigationGuidanceAction.CROSSWALK,
                        ),
                ),
            )

        assertEquals("50m", display.segmentDistanceText)
        assertEquals("50m \uC9C1\uC9C4 \uD6C4 \uD6A1\uB2E8\uBCF4\uB3C4", display.actionText)
        assertEquals("\uC774\uBC88 \uAD6C\uAC04 50m \uC815\uB3C4 \uD6A1\uB2E8\uBCF4\uB3C4\uB97C \uAC74\uB108\uC138\uC694.", display.talkBackText)
        assertEquals(NavigationGuidanceAction.CROSSWALK, display.guidanceAction)
    }

    @Test
    fun `navigation live guidance card renders action and detail copy`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/lowvision/LowVisionNavigationScreen.kt")
                .readText()
        val liveGuidanceCardSource =
            source.substringAfter("private fun LowVisionNavigationLiveGuidanceCard")
                .substringBefore("private fun LowVisionNavigationStatusCard")

        assertTrue(liveGuidanceCardSource.contains("text = display.actionText"))
        assertTrue(liveGuidanceCardSource.contains("text = display.detailText"))
    }

    @Test
    fun `navigation loading state guidance falls back to preparing copy`() {
        val display = lowVisionNavigationLiveGuidanceDisplay(NavigationUiState())

        assertEquals("\uD655\uC778 \uC911", display.segmentDistanceText)
        assertEquals("\uC774\uBC88 \uAD6C\uAC04 \uD655\uC778 \uC911 \uC548\uB0B4\uB97C \uC900\uBE44\uD558\uACE0 \uC788\uC5B4\uC694.", display.talkBackText)
    }

    @Test
    fun `navigation status display prefers supporting text then progress label`() {
        val display =
            lowVisionNavigationStatusDisplay(
                NavigationUiState(
                    screenState = NavigationScreenState.Ready,
                    stepCard =
                        NavigationStepCardUiState(
                            heroTitle = "\uC6B0\uCE21 \uACBD\uC0AC\uB85C \uC774\uC6A9",
                            supportingText = "\uACC4\uB2E8 \uC5C6\uB294 \uACBD\uB85C\uB85C \uC548\uB0B4 \uC911\uC785\uB2C8\uB2E4.",
                            metrics = listOf(
                                com.ssafy.e102.eumgil.feature.navigation.NavigationStepMetricUiState(label = "\uB0A8\uC740 \uAC70\uB9AC", value = "-"),
                                com.ssafy.e102.eumgil.feature.navigation.NavigationStepMetricUiState(label = "\uB0A8\uC740 \uC2DC\uAC04", value = "-"),
                                com.ssafy.e102.eumgil.feature.navigation.NavigationStepMetricUiState(label = "\uC9C4\uD589 \uB2E8\uACC4", value = "2 / 5"),
                            ),
                        ),
                ),
            )

        assertEquals(
            "\uC774\uB3D9 \uC0C1\uD0DC",
            display.header,
        )
        assertEquals("\uB0A8\uC740 \uAC70\uB9AC - \u00B7 \uB0A8\uC740 \uC2DC\uAC04 -", display.metricSummary)
        assertEquals("\uC6B0\uCE21 \uACBD\uC0AC\uB85C \uC774\uC6A9", display.title)
        assertEquals("\uACC4\uB2E8 \uC5C6\uB294 \uACBD\uB85C\uB85C \uC548\uB0B4 \uC911\uC785\uB2C8\uB2E4.", display.body)
    }

    @Test
    fun `navigation distance metric appends meters and converts long distances to kilometers`() {
        val section = LowVisionNavigationMetricSection(label = "\uB0A8\uC740 \uAC70\uB9AC", metricIndex = 0)

        assertEquals("98m", lowVisionNavigationDisplayMetric(section, "98"))
        assertEquals("980m", lowVisionNavigationDisplayMetric(section, "980m"))
        assertEquals("1.2km", lowVisionNavigationDisplayMetric(section, "1200"))
        assertEquals("1.5km", lowVisionNavigationDisplayMetric(section, "1500m"))
    }

    @Test
    fun `navigation time metric always displays minutes`() {
        val section = LowVisionNavigationMetricSection(label = "\uB0A8\uC740 \uC2DC\uAC04", metricIndex = 1)

        assertEquals("16\uBD84", lowVisionNavigationDisplayMetric(section, "16"))
        assertEquals("2\uBD84", lowVisionNavigationDisplayMetric(section, "2\uBD84"))
    }

    @Test
    fun `navigation card content is sized to avoid clipping live guidance copy`() {
        assertEquals(388.dp, LowVisionNavigationLayoutDefaults.liveGuidanceMinHeight)
        assertEquals(108.dp, LowVisionNavigationLayoutDefaults.liveGuidanceIconSize)
        assertEquals(72.sp, LowVisionNavigationLayoutDefaults.liveGuidanceSegmentDistanceFontSize)
        assertEquals(36.sp, LowVisionNavigationLayoutDefaults.exitLabelFontSize)
        assertEquals(42.sp, LowVisionNavigationLayoutDefaults.exitLabelLineHeight)
        assertEquals(40.sp, LowVisionNavigationLayoutDefaults.statusCardTitleFontSize)
        assertEquals(22.dp, LowVisionNavigationLayoutDefaults.exitCardVerticalPadding)
        assertEquals(92.dp, LowVisionNavigationLayoutDefaults.exitIconContainerSize)
        assertEquals(14.dp, LowVisionNavigationLayoutDefaults.exitIconTextGap)
    }

    @Test
    fun `navigation screen keeps low vision bottom tab order`() {
        assertEquals(
            listOf(
                LowVisionBottomTab.HOME,
                LowVisionBottomTab.BOOKMARK,
                LowVisionBottomTab.CATEGORY,
                LowVisionBottomTab.MY_PAGE,
            ),
            lowVisionNavigationBottomTabs(),
        )
    }

    @Test
    fun `navigation screen shows load error only while route guidance is still loading`() {
        assertTrue(
            shouldShowLowVisionNavigationLoadError(
                uiState = NavigationUiState(screenState = NavigationScreenState.Loading),
                loadErrorMessage = LOW_VISION_NAVIGATION_LOAD_ERROR_MESSAGE,
            ),
        )
        assertFalse(
            shouldShowLowVisionNavigationLoadError(
                uiState = NavigationUiState(screenState = NavigationScreenState.Ready),
                loadErrorMessage = LOW_VISION_NAVIGATION_LOAD_ERROR_MESSAGE,
            ),
        )
        assertFalse(
            shouldShowLowVisionNavigationLoadError(
                uiState = NavigationUiState(screenState = NavigationScreenState.Loading),
                loadErrorMessage = null,
            ),
        )
    }

    @Test
    fun `navigation route wires low vision tts events and initial briefing trigger`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/lowvision/LowVisionNavigationRoute.kt")
                .readText()

        assertTrue(source.contains("AndroidTextToSpeechController"))
        assertTrue(source.contains("NavigationRouteChangeAlertPlayer"))
        assertTrue(source.contains("viewModel.updateTextToSpeechState"))
        assertTrue(source.contains("LaunchedEffect(textToSpeechController, uiState.tts.isEnabled)"))
        assertTrue(source.contains("textToSpeechController.setEnabled(uiState.tts.isEnabled)"))
        assertTrue(source.contains("is NavigationUiEvent.SpeakBriefing -> textToSpeechController.speak(event.text)"))
        assertTrue(source.contains("NavigationUiEvent.PlayRouteChangeAlert -> routeChangeAlertPlayer.play()"))
        assertTrue(source.contains("NavigationUiEvent.StopBriefing -> textToSpeechController.stop()"))
        assertTrue(source.contains("NavigationUiAction.NavigationEntered"))
    }
}
