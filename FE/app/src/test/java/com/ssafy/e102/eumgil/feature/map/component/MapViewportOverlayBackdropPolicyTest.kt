package com.ssafy.e102.eumgil.feature.map.component

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MapViewportOverlayBackdropPolicyTest {
    @Test
    fun `route guidance junction markers use the shared round gray stroke token`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapViewportOverlayBackdrop.kt")
                .readText()
        val markerSection =
            source
                .substringAfter("MapViewportPointKind.SEGMENT_JUNCTION ->")
                .substringBefore("MapViewportPointKind.TRANSIT_BUS_STOP ->")

        assertTrue(
            "Guidance junction markers should use the requested 2dp gray stroke token.",
            source.contains("GuidanceJunctionMarkerStrokeWidth = 2.dp") &&
                source.contains("GuidanceJunctionMarkerStrokeColor = Color(0xFF9CA3AF)") &&
                markerSection.contains("borderColor = GuidanceJunctionMarkerStrokeColor"),
        )
        assertTrue(
            "Guidance junction markers should stay circular instead of using the diamond marker branch.",
            markerSection.contains("isDiamond = false"),
        )
    }

    @Test
    fun `approved report fallback marker uses shared triangle warning style`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapViewportOverlayBackdrop.kt")
                .readText()
        val markerSection =
            source
                .substringAfter("MapViewportPointKind.APPROVED_REPORT ->")
                .substringBefore("MapViewportPointKind.CAMERA_FOCUS ->")

        assertTrue(markerSection.contains("containerColor = Color(0xFFFFD84D)"))
        assertTrue(markerSection.contains("borderColor = Color(0xFF111827)"))
        assertTrue(markerSection.contains("contentColor = Color(0xFF111827)"))
        assertTrue(markerSection.contains("shape = ViewportPointMarkerShape.TRIANGLE_WARNING"))
        assertTrue(markerSection.contains("label = \"!\""))
    }
}
