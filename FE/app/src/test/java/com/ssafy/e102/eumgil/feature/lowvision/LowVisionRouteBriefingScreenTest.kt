package com.ssafy.e102.eumgil.feature.lowvision

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LowVisionRouteBriefingScreenTest {
    @Test
    fun `briefing step rows prioritize showing instruction sentences`() {
        assertEquals(2, LowVisionRouteBriefingLayoutDefaults.stepInstructionMaxLines)
        assertEquals(118.dp, LowVisionRouteBriefingLayoutDefaults.stepRowMinHeight)
        assertTrue(LowVisionRouteBriefingLayoutDefaults.stepInstructionFontSize <= 34.sp)
        assertTrue(LowVisionRouteBriefingLayoutDefaults.stepInstructionLineHeight <= 40.sp)
    }

    @Test
    fun `playback button explains start and stop behavior before route briefing starts`() {
        assertEquals("\uC2DC\uC791", routeBriefingPlaybackButtonLabel(isPlaying = false))
        assertEquals(
            "두 번 탭하면 경로 안내를 시작합니다. 안내 중에는 화면 항목 안내를 줄입니다. 다시 누르면 중지합니다.",
            routeBriefingPlaybackButtonActionHint(isPlaying = false),
        )
        assertEquals("\uC911\uC9C0", routeBriefingPlaybackButtonLabel(isPlaying = true))
        assertEquals(
            "두 번 탭하면 경로 안내 음성을 중지합니다.",
            routeBriefingPlaybackButtonActionHint(isPlaying = true),
        )
    }

    @Test
    fun `briefing screen suppresses non control talkback descriptions while playback is active`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/lowvision/LowVisionRouteBriefingScreen.kt")
                .readText()

        assertTrue(
            "Route briefing should pass playback state down to each step row so route speech is not interrupted by row announcements.",
            source.contains("BriefingStepRow(step = step, suppressTalkBack = isPlaying)"),
        )
        assertTrue(
            "Playback-active step rows should clear semantics instead of exposing fresh contentDescription changes.",
            source.contains("clearAndSetSemantics {}"),
        )
        assertFalse(
            "The playback button must stay accessible as the dedicated stop control while speech is playing.",
            routeBriefingPlaybackButtonLabel(isPlaying = true).isBlank(),
        )
    }

    @Test
    fun `briefing screen shows route loading errors instead of an empty step list`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/lowvision/LowVisionRouteBriefingScreen.kt")
                .readText()

        assertTrue(source.contains("uiState.errorMessage?.let"))
        assertTrue(source.contains("BriefingStatusMessage"))
    }

    @Test
    fun `briefing route configures tts at normal speech speed`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/lowvision/LowVisionRouteBriefingRoute.kt")
                .readText()

        assertTrue(source.contains("speechRate = ROUTE_BRIEFING_TTS_SPEECH_RATE"))
    }

    @Test
    fun `briefing route waits for current location before loading route details`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/lowvision/LowVisionRouteBriefingRoute.kt")
                .readText()

        assertTrue(source.contains("currentLocationManager.startLocationUpdates()"))
        assertTrue(source.contains("currentLocationManager.refreshLatestLocation()"))
        assertTrue(source.contains("awaitLowVisionOriginSnapshot("))
    }

    @Test
    fun `briefing route does not replace a missing current location with the default origin`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/lowvision/LowVisionRouteBriefingRoute.kt")
                .readText()

        assertTrue(source.contains("toLowVisionRouteOriginWaypointOrNull()"))
        assertTrue(source.contains("showLocationRequired()"))
        assertFalse(source.contains(").toLowVisionRouteOriginWaypoint()"))
    }

    @Test
    fun `briefing and navigation route loading are not keyed by live location updates`() {
        val briefingRouteSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/lowvision/LowVisionRouteBriefingRoute.kt")
                .readText()
        val navigationRouteSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/lowvision/LowVisionNavigationRoute.kt")
                .readText()

        assertFalse(briefingRouteSource.contains("collectAsStateWithLifecycle()") && briefingRouteSource.contains("latestLocation by"))
        assertFalse(navigationRouteSource.contains("collectAsStateWithLifecycle()") && navigationRouteSource.contains("latestLocation by"))
        assertFalse(briefingRouteSource.contains("LaunchedEffect(viewModel, selectedDestination, latestLocation)"))
        assertFalse(navigationRouteSource.contains("LaunchedEffect(selectedDestination, latestLocation)"))
    }
}
