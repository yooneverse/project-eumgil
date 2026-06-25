package com.ssafy.e102.eumgil.feature.map

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MapViewportConfigurationTest {
    @Test
    fun `map viewport hides empty facility data status card`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapViewport.kt")
                .readText()

        assertTrue(
            "Map viewport should keep the empty facility data status hidden instead of rendering a status card.",
            source.contains("markerOverlayState.isEmptyData -> null"),
        )
    }
}
