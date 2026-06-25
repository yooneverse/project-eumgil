package com.ssafy.e102.eumgil.feature.map.component

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MapBottomSheetSurfaceConfigurationTest {
    @Test
    fun `map bottom sheet surface uses unified top spacing and handle height`() {
        val surfaceSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapBottomSheetSurface.kt").readText()

        assertTrue(
            "Shared map bottom sheet surface should define a single top padding token for unified sheet header spacing.",
            surfaceSource.contains("private val MapBottomSheetTopPadding = EumSpacing.small"),
        )
        assertTrue(
            "Shared map bottom sheet surface should define a tighter shared handle touch height for all map bottom sheets.",
            surfaceSource.contains("val MapBottomSheetHandleHeight = 16.dp"),
        )
        assertTrue(
            "Shared map bottom sheet surface should use the explicit asymmetric padding so top spacing is smaller than the side and bottom padding.",
            surfaceSource.contains("top = MapBottomSheetTopPadding") &&
                surfaceSource.contains("bottom = EumSpacing.medium") &&
                surfaceSource.contains("start = EumSpacing.medium") &&
                surfaceSource.contains("end = EumSpacing.medium"),
        )
    }

    @Test
    fun `facility and recent destination sheets reuse the shared unified handle height`() {
        val facilitySource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/FacilityDetailBottomSheetShell.kt").readText()
        val recentSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/RecentDestinationBottomSheetShell.kt").readText()

        assertTrue(
            "Facility detail sheet should reuse the shared bottom sheet handle height instead of its own larger value.",
            facilitySource.contains(".height(MapBottomSheetHandleHeight)"),
        )
        assertTrue(
            "Recent destination sheet should reuse the shared bottom sheet handle height instead of its own larger value.",
            recentSource.contains(".height(MapBottomSheetHandleHeight)"),
        )
    }
}
