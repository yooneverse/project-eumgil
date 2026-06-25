package com.ssafy.e102.eumgil.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BusanEumgilColorSchemeTest {
    @Test
    fun `light color scheme maps action tokens from design convention`() {
        assertEquals(
            Color(0xFF2563EB).value,
            BusanEumgilLightColorScheme.primary.value,
        )
        assertEquals(
            Color(0xFF3B82F6).value,
            BusanEumgilLightColorScheme.secondary.value,
        )
        assertEquals(
            Color(0xFFDBEAFE).value,
            BusanEumgilLightColorScheme.primaryContainer.value,
        )
        assertEquals(
            Color(0xFFD1D5DB).value,
            BusanEumgilLightColorScheme.outline.value,
        )
        assertEquals(
            Color(0xFFF3F4F6).value,
            BusanEumgilLightColorScheme.surfaceVariant.value,
        )
    }

    @Test
    fun `map top search bar uses primary support accent for microphone icon`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapTopSearchBar.kt")
                .readText()

        assertTrue(
            "Search bar microphone icon should use the design convention primary-support accent token.",
            source.contains("tint = MaterialTheme.colorScheme.secondary"),
        )
    }
}
