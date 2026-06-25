package com.ssafy.e102.eumgil.feature.map

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapScreenSearchBarStateConfigurationTest {
    @Test
    fun `map search entry button keeps a static busan eumgil search label`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/MapScreen.kt")
                .readText()
        val functionSource =
            Regex(
                """private fun mapSearchBarState\(uiState: MapUiState\): MapSearchBarState \{[\s\S]*?\n\}""",
            ).find(source)?.value
                ?: error("mapSearchBarState should exist in MapScreen.")

        assertTrue(
            "Map search entry button should use the shared search title string.",
            functionSource.contains("title = stringResource(id = R.string.map_shell_search_title)"),
        )
        assertTrue(
            "Map search entry button should not expose a secondary address line.",
            functionSource.contains("subtitle = null"),
        )
        assertTrue(
            "Map search entry button should keep the generic search entry accessibility label.",
            functionSource.contains("accessibilityLabel = stringResource(id = R.string.map_shell_search_a11y_label)"),
        )
        assertFalse(
            "Map search entry button should not surface the selected destination name.",
            functionSource.contains("selectedDestination.name"),
        )
        assertFalse(
            "Map search entry button should not surface the selected destination address.",
            functionSource.contains("selectedDestination.address"),
        )
    }

    @Test
    fun `map voice search hides overlapping home bottom sheets`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/MapScreen.kt")
                .readText()

        assertTrue(
            "Map home should suppress the recent-destination sheet while the voice-search sheet is visible.",
            source.contains("uiState.isVoiceSearchVisible.not()"),
        )
        assertTrue(
            "Map top search bar should pass the shared voice-search accessibility copy into the dedicated mic button.",
            source.contains("voiceInputAccessibilityLabel = stringResource(id = R.string.search_screen_voice_input)"),
        )
    }
}
