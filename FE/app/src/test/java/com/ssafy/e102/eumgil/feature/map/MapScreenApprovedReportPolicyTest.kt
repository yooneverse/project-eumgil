package com.ssafy.e102.eumgil.feature.map

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MapScreenApprovedReportPolicyTest {
    private val source =
        File("src/main/java/com/ssafy/e102/eumgil/feature/map/MapScreen.kt")
            .readText()

    @Test
    fun `approved report marker click is routed to the dedicated action`() {
        assertTrue(
            "MapScreen should include approved reports in the viewport overlay and parse their click target before falling back to facility marker taps.",
            source.contains("approvedReportMarkers = uiState.approvedReportMarkerState.visibleReports") &&
                source.contains("parseApprovedReportClickTargetId(clickTargetId)?.let { reportId ->") &&
                source.contains("MapUiAction.ApprovedReportMarkerTapped(reportId = reportId)") &&
                source.contains("MapUiAction.MarkerTapped(clickTargetId)"),
        )
    }

    @Test
    fun `approved hazard marker sheet takes precedence over recent destination and facility sheets`() {
        assertTrue(
            "MapScreen should define a dedicated visibility flag for the approved hazard marker bottom sheet.",
            source.contains("val isApprovedHazardMarkerSheetVisible = hazardMarkerState.selectedMarker != null"),
        )
        assertTrue(
            "Recent destination sheet should be hidden while the approved hazard marker sheet is visible.",
            Regex(
                "recentDestinationSheetState\\.isVisible\\s*&&\\s*" +
                    "isApprovedHazardMarkerSheetVisible\\.not\\(\\)\\s*&&\\s*" +
                    "isLegacyApprovedReportSheetVisible\\.not\\(\\)\\s*&&\\s*" +
                    "facilityDetailSheetUiState\\.isVisible\\.not\\(\\)",
            ).containsMatchIn(source),
        )
        assertTrue(
            "Facility detail sheet should be hidden while the approved hazard marker sheet is visible.",
            Regex(
                "facilityDetailSheetUiState\\.isVisible\\s*&&\\s*" +
                    "isApprovedHazardMarkerSheetVisible\\.not\\(\\)\\s*&&\\s*" +
                    "isLegacyApprovedReportSheetVisible\\.not\\(\\)\\s*&&\\s*" +
                    "uiState\\.isVoiceSearchVisible\\.not\\(\\)",
            ).containsMatchIn(source),
        )
        assertTrue(
            "Approved hazard marker bottom sheet should stay mounted while selected and should only yield to higher-priority shells.",
            source.contains("ApprovedHazardMarkerBottomSheet(") &&
                source.contains("isFacilityDetailSheetVisible ||") &&
                source.contains("isLegacyApprovedReportSheetVisible ||") &&
                source.contains("uiState.routeEndpointMapPickerState != null ||") &&
                source.contains("hazardMarkerState.selectedMarker"),
        )
        assertTrue(
            "Legacy approved report sheet should not render on top of the new approved hazard marker bottom sheet.",
            Regex(
                "ApprovedReportBottomSheetShell\\([\\s\\S]*" +
                    "if \\(isLegacyApprovedReportSheetVisible\\s*&&\\s*" +
                    "isApprovedHazardMarkerSheetVisible\\.not\\(\\)",
            ).containsMatchIn(source),
        )
    }
}
