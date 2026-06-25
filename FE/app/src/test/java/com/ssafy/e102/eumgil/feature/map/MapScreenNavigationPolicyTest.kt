package com.ssafy.e102.eumgil.feature.map

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MapScreenNavigationPolicyTest {
    @Test
    fun `facility detail keeps two route CTAs outside route endpoint picker mode`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/MapScreen.kt")
                .readText()
        val actionContentSection =
            source
                .substringAfter("actionContent = {")
                .substringBefore("facilityDetailSheetUiState.bookmarkErrorMessage")
        val defaultActionSection =
            actionContentSection
                .substringAfter("} else {")

        assertTrue(
            "Default facility detail should keep the two-button row for origin and destination assignment.",
            defaultActionSection.contains("Row(") &&
                defaultActionSection.contains("OutlinedButton(") &&
                defaultActionSection.contains("NoRippleMapPrimaryActionButton("),
        )
        assertTrue(
            "Default facility detail should keep separate origin and destination route targets.",
            defaultActionSection.contains("RouteEditingTarget.ORIGIN") &&
                defaultActionSection.contains("RouteEditingTarget.DESTINATION"),
        )
        assertTrue(
            "Default facility detail should keep the existing origin and destination labels.",
            defaultActionSection.contains("R.string.map_facility_detail_set_origin_action") &&
                defaultActionSection.contains("R.string.map_facility_detail_set_destination_action"),
        )
    }

    @Test
    fun `route endpoint picker facility detail renders one full width CTA for active target`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/MapScreen.kt")
                .readText()
        val actionContentSection =
            source
                .substringAfter("actionContent = {")
                .substringBefore("facilityDetailSheetUiState.bookmarkErrorMessage")
        val pickerActionSection =
            actionContentSection
                .substringAfter("if (pickerTarget != null) {")
                .substringBefore("} else {")

        assertTrue(
            "Facility detail should branch on the route endpoint picker target.",
            actionContentSection.contains("facilityDetailSheetUiState.routeEndpointPickerTarget") &&
                actionContentSection.contains("if (pickerTarget != null)"),
        )
        assertTrue(
            "Picker mode should use only the active target when applying the endpoint.",
            pickerActionSection.contains("FacilitySetRouteEndpointClicked") &&
                pickerActionSection.contains("pickerTarget"),
        )
        assertTrue(
            "Picker mode should expose the dedicated single CTA labels for origin and destination.",
            pickerActionSection.contains("R.string.map_route_endpoint_picker_set_origin_action") &&
                pickerActionSection.contains("R.string.map_route_endpoint_picker_set_destination_action"),
        )
        assertTrue(
            "Picker mode should render one full-width primary action instead of the default two-button row.",
            pickerActionSection.contains("NoRippleMapPrimaryActionButton(") &&
                pickerActionSection.contains(".fillMaxWidth()") &&
                !pickerActionSection.contains("OutlinedButton(") &&
                !pickerActionSection.contains("Row("),
        )
    }

    @Test
    fun `route endpoint picker renders dedicated map selection scaffold`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/MapScreen.kt")
                .readText()

        assertTrue(
            "Facility detail route CTA should keep using the destination route label from the map screen.",
            source.contains("R.string.map_facility_detail_set_destination_action"),
        )
        assertTrue(
            "Map picker mode should use a dedicated scaffold instead of the map home overlays.",
            source.contains("RouteEndpointMapPickerScaffold(") &&
                source.contains("RouteEndpointMapPickerTopOverlay(") &&
                source.contains("RouteEndpointMapPickerBottomSheet("),
        )
        assertTrue(
            "Map picker should keep the route endpoint marker fixed at the visual center.",
            source.contains("MapPickerCenterMarker(") &&
                source.contains(".align(Alignment.Center)"),
        )
        assertTrue(
            "Map picker should reuse the project centered top bar and dedicated copy.",
            source.contains("EumCenteredTopBar(") &&
                source.contains("R.string.map_route_endpoint_picker_title") &&
                source.contains("R.string.map_route_endpoint_picker_instruction") &&
                source.contains("R.string.map_route_endpoint_picker_select_action"),
        )
    }
}
