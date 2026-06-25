package com.ssafy.e102.eumgil.feature.map.component

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovedReportBottomSheetShellPolicyTest {
    private val source =
        File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/ApprovedReportBottomSheetShell.kt")
            .readText()
    private val stringsSource =
        File("src/main/res/values/strings.xml")
            .readText()

    @Test
    fun `approved report sheet renders report fields without route cta`() {
        assertTrue(
            "Approved report sheet should consume the approved report sheet state from the map contract.",
            source.contains("state: ApprovedReportSheetState") &&
                source.contains("val report = state.report"),
        )
        assertTrue(
            "Approved report sheet should render type, location, and description sections.",
            source.contains("map_approved_report_sheet_type_label") &&
                source.contains("map_approved_report_sheet_location_label") &&
                source.contains("map_approved_report_sheet_description_label"),
        )
        assertTrue(
            "Approved report sheet should fall back when description is blank.",
            source.contains("map_approved_report_sheet_no_description"),
        )
        assertFalse(
            "Approved report sheet must not expose route, preview, bookmark, or phone CTAs.",
            Regex("(?<!Icon)Button\\(").containsMatchIn(source) ||
                source.contains("OutlinedButton(") ||
                source.contains("FacilitySetRouteEndpointClicked") ||
                source.contains("FacilityBookmarkClicked") ||
                source.contains("FacilityPhoneClicked"),
        )
    }

    @Test
    fun `approved report photo uses coil image with loading and error fallback`() {
        assertTrue(
            "Approved report photo should use Coil's composable slots so failed images do not render as broken images.",
            source.contains("SubcomposeAsyncImage(") &&
                source.contains("ImageRequest.Builder(context)") &&
                source.contains("loading = { ApprovedReportPhotoFallback(") &&
                source.contains("error = { ApprovedReportPhotoFallback("),
        )
        assertTrue(
            "Approved report sheet should show the same fallback surface when there is no photo URL.",
            source.contains("val photoUrl = report.imageUrls.firstOrNull") &&
                source.contains("ApprovedReportPhotoFallback(") &&
                source.contains("map_approved_report_sheet_no_photo"),
        )
    }

    @Test
    fun `approved report sheet strings are declared in resources`() {
        listOf(
            "map_approved_report_marker_content_description",
            "map_approved_report_sheet_title",
            "map_approved_report_sheet_type_label",
            "map_approved_report_sheet_location_label",
            "map_approved_report_sheet_description_label",
            "map_approved_report_sheet_no_description",
            "map_approved_report_sheet_no_photo",
            "map_approved_report_sheet_close",
        ).forEach { stringName ->
            assertTrue(
                "Missing string resource: $stringName",
                stringsSource.contains("<string name=\"$stringName\">"),
            )
        }
    }
}
