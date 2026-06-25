package com.ssafy.e102.eumgil.feature.map

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapRouteApprovedReportBackHandlerPolicyTest {
    private val routeSource =
        File("src/main/java/com/ssafy/e102/eumgil/feature/map/MapRoute.kt")
            .readText()
    private val screenSource =
        File("src/main/java/com/ssafy/e102/eumgil/feature/map/MapScreen.kt")
            .readText()

    @Test
    fun `approved report sheet back handler lives in route layer`() {
        assertTrue(
            "MapRoute should dismiss the approved report sheet on back only after voice search and picker are inactive.",
            Regex(
                "BackHandler\\(\\s*enabled\\s*=\\s*uiState\\.approvedReportSheetState\\.isVisible\\s*&&\\s*" +
                    "uiState\\.routeEndpointMapPickerState == null\\s*&&\\s*" +
                    "uiState\\.isVoiceSearchVisible\\.not\\(\\),\\s*\\)\\s*\\{\\s*" +
                    "viewModel\\.onAction\\(MapUiAction\\.ApprovedReportSheetDismissed\\)\\s*\\}",
            ).containsMatchIn(routeSource),
        )
        assertFalse(
            "MapScreen should stay a stateless renderer and must not own BackHandler policy.",
            screenSource.contains("BackHandler("),
        )
    }

    @Test
    fun `app background back handler does not run while approved report sheet is open`() {
        assertTrue(
            "The fallback back handler should be disabled when the approved report sheet is visible.",
            Regex(
                "BackHandler\\(\\s*enabled\\s*=\\s*uiState\\.facilityDetailSheetState\\.isVisible\\.not\\(\\)\\s*&&\\s*" +
                    "uiState\\.approvedReportSheetState\\.isVisible\\.not\\(\\)\\s*&&\\s*" +
                    "uiState\\.routeEndpointMapPickerState == null\\s*&&\\s*" +
                    "uiState\\.isVoiceSearchVisible\\.not\\(\\),",
            ).containsMatchIn(routeSource),
        )
    }
}
