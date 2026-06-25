package com.ssafy.e102.eumgil.feature.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationLiveGuidanceFormatterTest {
    @Test
    fun `crosswalk card uses compact noun phrase`() {
        assertEquals(
            "60m 앞 횡단보도",
            formatNavigationLiveGuidanceCardText(
                action = NavigationGuidanceAction.CROSSWALK,
                displayDistanceMeters = 60,
                fallbackTitle = "횡단보도 건너기",
            ),
        )
    }

    @Test
    fun `display distance over 100 meters floors to 20 meter bucket`() {
        assertEquals(140, toLiveGuidanceDisplayDistanceMeters(156))
    }

    @Test
    fun `display distance at or under 100 meters floors to 10 meter bucket`() {
        assertEquals(40, toLiveGuidanceDisplayDistanceMeters(47))
    }

    @Test
    fun `display distance at or under 10 meters is hidden from card text`() {
        assertEquals(0, toLiveGuidanceDisplayDistanceMeters(10))
        assertEquals(
            "좌회전",
            formatNavigationLiveGuidanceCardTextFromRawDistance(
                action = NavigationGuidanceAction.TURN_LEFT,
                rawDistanceMeters = 10,
                fallbackTitle = "좌회전",
            ),
        )
    }

    @Test
    fun `speech formatter keeps sentence style`() {
        assertEquals(
            "60m 앞 횡단보도가 있습니다.",
            formatNavigationLiveGuidanceSpeechText(
                action = NavigationGuidanceAction.CROSSWALK,
                rawDistanceMeters = 60,
                stage = NavigationLiveGuidanceSpeechStage.INITIAL,
                fallbackTitle = "횡단보도 건너기",
            ),
        )
    }

    @Test
    fun `speech formatter keeps raw distance for threshold stages`() {
        assertEquals(
            "19m 후 우회전입니다.",
            formatNavigationLiveGuidanceSpeechText(
                action = NavigationGuidanceAction.TURN_RIGHT,
                rawDistanceMeters = 19,
                stage = NavigationLiveGuidanceSpeechStage.APPROACH_30M,
                fallbackTitle = "우회전",
            ),
        )
        assertEquals(
            "곧 우회전입니다.",
            formatNavigationLiveGuidanceSpeechText(
                action = NavigationGuidanceAction.TURN_RIGHT,
                rawDistanceMeters = 10,
                stage = NavigationLiveGuidanceSpeechStage.NEAR_10M,
                fallbackTitle = "우회전",
            ),
        )
    }
}
