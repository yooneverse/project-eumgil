package com.ssafy.e102.eumgil.feature.map.component

import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.feature.map.model.ApprovedReportMarkerData
import com.ssafy.e102.eumgil.feature.map.model.MapCameraSource
import com.ssafy.e102.eumgil.feature.map.model.MapCameraTarget
import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerOverlayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MapViewportApprovedReportOverlayTest {
    @Test
    fun `approved report markers are added as non projection overlay points`() {
        val state =
            createMapMarkerViewportOverlayState(
                cameraTarget =
                    MapCameraTarget(
                        center = MapCoordinate(35.1796, 129.0756),
                        source = MapCameraSource.DEFAULT_BUSAN,
                        zoomLevel = 15,
                    ),
                markerOverlayState = MapMarkerOverlayState(),
                selectedMarkerId = null,
                currentLocation = null,
                currentLocationLabel = null,
                approvedReportMarkers =
                    listOf(
                        ApprovedReportMarkerData(
                            reportId = 42L,
                            reportTypeApiValue = "OBSTACLE",
                            reportTypeLabel = "보행 장애물",
                            coordinate = GeoCoordinate(35.1796, 129.0756),
                        ),
                    ),
            )

        val approvedPoint = state.points.single { it.kind == MapViewportPointKind.APPROVED_REPORT }
        assertEquals("approved-report:42", approvedPoint.overlayId)
        assertEquals("approved-report:42", approvedPoint.clickTargetId)
        assertFalse(approvedPoint.includeInProjection)
        assertEquals("OBSTACLE", approvedPoint.reportTypeApiValue)
    }
}
