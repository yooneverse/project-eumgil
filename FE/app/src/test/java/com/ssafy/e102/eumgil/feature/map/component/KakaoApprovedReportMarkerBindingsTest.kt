package com.ssafy.e102.eumgil.feature.map.component

import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KakaoApprovedReportMarkerBindingsTest {
    @Test
    fun `approved report point converts to kakao approved report marker`() {
        val point =
            MapViewportPointOverlay(
                overlayId = "approved-report:42",
                coordinate = MapCoordinate(35.1796, 129.0756),
                kind = MapViewportPointKind.APPROVED_REPORT,
                reportTypeApiValue = "RAMP",
                label = "경사로 문제",
                includeInProjection = false,
                clickTargetId = "approved-report:42",
            )

        val marker = point.toKakaoProjectedPointMarkerState()

        assertEquals(KakaoOverlayMarkerKind.APPROVED_REPORT, marker?.kind)
        assertEquals("approved-report:42", marker?.markerId)
        assertEquals(32, marker?.sizeDp)
        assertNull(marker?.iconResId)
        assertEquals(0xFF111827.toInt(), marker?.strokeColorArgb)
        assertEquals("approved-report:42", marker?.clickTargetId)
    }

    @Test
    fun `approved report point keeps the shared warning marker even when report type is unknown`() {
        val point =
            MapViewportPointOverlay(
                overlayId = "approved-report:99",
                coordinate = MapCoordinate(35.1796, 129.0756),
                kind = MapViewportPointKind.APPROVED_REPORT,
                reportTypeApiValue = "UNKNOWN_TYPE",
                includeInProjection = false,
                clickTargetId = "approved-report:99",
            )

        val marker = point.toKakaoProjectedPointMarkerState()

        assertNull(marker?.iconResId)
    }

    @Test
    fun `approved hazard marker uses click target id as poi marker id when overlay id differs`() {
        val point =
            MapViewportPointOverlay(
                overlayId = "hazard-12",
                coordinate = MapCoordinate(35.1796, 129.0756),
                kind = MapViewportPointKind.APPROVED_REPORT,
                reportTypeApiValue = "RAMP",
                includeInProjection = false,
                clickTargetId = "hazard-report-12",
            )

        val marker = point.toKakaoProjectedPointMarkerState()

        assertEquals("hazard-report-12", marker?.markerId)
        assertEquals("hazard-report-12", marker?.clickTargetId)
    }

    @Test
    fun `approved report marker bitmap uses the shared black warning symbol color`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/KakaoMapViewport.kt")
                .readText()

        assertTrue(source.contains("APPROVED_REPORT_MARKER_SYMBOL = -15658713"))
    }

    @Test
    fun `approved report kakao layer stays above facility marker layer`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/KakaoMapViewport.kt")
                .readText()

        assertTrue(source.contains("KAKAO_APPROVED_REPORT_MARKER_LAYER_ID"))
        assertTrue(source.contains("KAKAO_APPROVED_REPORT_MARKER_LAYER_Z_ORDER = 1005"))
        assertTrue(source.contains("KAKAO_MARKER_LAYER_Z_ORDER = 1000"))
    }
}
