package com.ssafy.e102.eumgil.feature.map

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapBookmarkActionButtonConfigurationTest {
    @Test
    fun `facility detail bookmark action button uses transparent container`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/MapScreen.kt").readText()

        assertTrue(
            "Facility detail bookmark button should keep a transparent container behind the icon.",
            source.contains("color = Color.Transparent"),
        )
        assertTrue(
            "Facility detail bookmark button should use a compact transparent icon target.",
            source.contains("color = Color.Transparent") &&
                source.contains(".size(48.dp)"),
        )
        assertFalse(
            "Facility detail bookmark button should not draw a separate circular outline around the icon.",
            source.contains("border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)"),
        )
        assertTrue(
            "Facility detail bookmark icon should use the primary tint in both saved and unsaved enabled states.",
            source.contains("state.isBookmarked -> MaterialTheme.colorScheme.primary") &&
                source.contains("else -> MaterialTheme.colorScheme.primary"),
        )
        assertFalse(
            "Facility detail bookmark button should not render a selected-state tint background.",
            source.contains("primaryContainer.copy(alpha = 0.5f)"),
        )
    }

    @Test
    fun `facility detail bookmark accessibility copy comes from string resources`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/MapScreen.kt").readText()
        val stringsSource = File("src/main/res/values/strings.xml").readText()

        assertFalse(
            "Bookmark state copy should not be hardcoded inside MapScreen.",
            source.contains("\"Bookmark is unavailable for this place.\""),
        )
        assertTrue(
            "Bookmark unavailable state copy should live in string resources with the other bookmark labels.",
            stringsSource.contains("map_facility_detail_bookmark_state_unavailable"),
        )
    }
}
