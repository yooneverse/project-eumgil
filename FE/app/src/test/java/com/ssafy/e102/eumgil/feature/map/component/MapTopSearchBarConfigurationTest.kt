package com.ssafy.e102.eumgil.feature.map.component

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapTopSearchBarConfigurationTest {
    @Test
    fun `map top search bar disables ripple indication for entry tap`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapTopSearchBar.kt")
                .readText()

        assertTrue(
            "Map top search bar should keep search navigation tap behavior without showing a pressed ripple.",
            source.contains("indication = null"),
        )
        assertTrue(
            "Map top search bar should provide its own interaction source when ripple indication is disabled.",
            source.contains("MutableInteractionSource"),
        )
        assertTrue(
            "Map top search bar should expose a dedicated mic button callback instead of routing every tap through the search entry action.",
            source.contains("onVoiceInputClick"),
        )
        assertTrue(
            "Map top search bar should keep the voice icon as its own button target without reintroducing the larger Material icon button height.",
            source.contains(".defaultMinSize(minWidth = 32.dp, minHeight = 32.dp)"),
        )
    }

    @Test
    fun `map top search bar uses an opaque surface over the map`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapTopSearchBar.kt")
                .readText()

        assertTrue(
            "Map top search bar should use the base surface color without transparency so the map does not bleed through.",
            source.contains("color = MaterialTheme.colorScheme.surface,"),
        )
        assertTrue(
            "Map top search bar should match the quick filter chip rounding token.",
            source.contains("RoundedCornerShape(EumRadius.scaleS)"),
        )
        assertFalse(
            "Map top search bar should not use a translucent surface over the moving map.",
            source.contains("surface.copy(alpha = 0.98f)"),
        )
        assertFalse(
            "Map top search bar should not apply tonal elevation because it makes the white surface look slightly tinted against the other map controls.",
            source.contains("tonalElevation ="),
        )
        assertTrue(
            "Map top search bar should match the shared map control elevation tier.",
            source.contains("shadowElevation = 6.dp"),
        )
    }
}
