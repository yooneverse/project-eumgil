package com.ssafy.e102.eumgil.feature.map

import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.data.repository.ApprovedReportMapEntry
import com.ssafy.e102.eumgil.feature.map.model.APPROVED_REPORT_VISIBLE_MAX_ZOOM_LEVEL
import com.ssafy.e102.eumgil.feature.map.model.approvedReportClickTargetId
import com.ssafy.e102.eumgil.feature.map.model.parseApprovedReportClickTargetId
import com.ssafy.e102.eumgil.feature.map.model.shouldShowApprovedReportMarkers
import com.ssafy.e102.eumgil.feature.map.model.toApprovedReportMarkerDataOrNull
import com.ssafy.e102.eumgil.feature.map.model.toApprovedReportSheetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovedReportMarkerModelsTest {
    @Test
    fun `approved report markers are visible only when zoomed out enough`() {
        assertTrue(shouldShowApprovedReportMarkers(APPROVED_REPORT_VISIBLE_MAX_ZOOM_LEVEL))
        assertTrue(shouldShowApprovedReportMarkers(APPROVED_REPORT_VISIBLE_MAX_ZOOM_LEVEL - 1))
        assertFalse(shouldShowApprovedReportMarkers(APPROVED_REPORT_VISIBLE_MAX_ZOOM_LEVEL + 1))
    }

    @Test
    fun `approved report click target uses a distinct prefix`() {
        val clickTargetId = approvedReportClickTargetId(42L)

        assertEquals("approved-report:42", clickTargetId)
        assertEquals(42L, parseApprovedReportClickTargetId(clickTargetId))
        assertNull(parseApprovedReportClickTargetId("facility-42"))
        assertNull(parseApprovedReportClickTargetId("approved-report:not-a-number"))
    }

    @Test
    fun `only approved entries map to marker data and sheet state`() {
        val approved =
            ApprovedReportMapEntry(
                reportId = 42L,
                reportTypeApiValue = "BROKEN_BLOCK",
                statusApiValue = "APPROVED",
                coordinate = GeoCoordinate(latitude = 35.1796, longitude = 129.0756),
                address = "Busan central road",
                description = "Broken tactile block",
                imageUrls = listOf("https://example.com/report.jpg"),
                approvedAt = "2026-05-19T09:00:00Z",
            )
        val pending = approved.copy(reportId = 43L, statusApiValue = "PENDING")

        val marker = approved.toApprovedReportMarkerDataOrNull()
        val hiddenMarker = pending.toApprovedReportMarkerDataOrNull()
        val sheetState = toApprovedReportSheetState(listOfNotNull(marker), selectedReportId = 42L)

        assertEquals(42L, marker?.reportId)
        assertEquals("Broken tactile block", marker?.description)
        assertNull(hiddenMarker)
        assertTrue(sheetState.isVisible)
        assertEquals("Broken tactile block", sheetState.report?.description)
    }
}
