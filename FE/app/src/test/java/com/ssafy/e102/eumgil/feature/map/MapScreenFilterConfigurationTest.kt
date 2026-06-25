package com.ssafy.e102.eumgil.feature.map

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapScreenFilterConfigurationTest {
    @Test
    fun `map screen keeps only the shortcut filter row in the top overlay`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/MapScreen.kt")
                .readText()

        assertTrue(
            "MapScreen should render the shortcut filter row in the map top overlay.",
            source.contains("MapShortcutFilterRow("),
        )
        assertFalse(
            "MapScreen should not render the removed lower category filter bar.",
            source.contains("MapCategoryFilterBar("),
        )
    }
}
