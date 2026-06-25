package com.ssafy.e102.eumgil.core.designsystem.component.map

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class EumMapFloatingControlsAdoptionTest {
    @Test
    fun `route setting screen uses shared map floating controls`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt").readText()

        assertTrue(
            "Route setting map preview should use the shared floating control component so future map API screens stay aligned.",
            source.contains("EumMapFloatingControls("),
        )
        assertTrue(
            "Route setting should reuse the route-start icon for the current-location action card.",
            source.contains("R.drawable.ic_route_start_navigation_button"),
        )
        assertTrue(
            "Route setting should tint the current-location action blue on the white floating button.",
            source.contains("tint = MaterialTheme.colorScheme.primary"),
        )
    }

    @Test
    fun `navigation screen uses shared map floating controls`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationScreen.kt").readText()

        assertTrue(
            "Navigation map preview should use the shared floating control component so all map surfaces match.",
            source.contains("EumMapFloatingControls("),
        )
        assertTrue(
            "Navigation should reuse the route-start icon for the current-location action card.",
            source.contains("R.drawable.ic_route_start_navigation_button"),
        )
        assertTrue(
            "Navigation should tint the current-location action blue on the white floating button.",
            source.contains("tint = MaterialTheme.colorScheme.primary"),
        )
        assertTrue(
            "Navigation should place a guidance report shortcut in the shared top floating action slot.",
            source.contains("topActionButtonState =") &&
                source.contains("R.drawable.ic_nav_report") &&
                source.contains("R.string.navigation_map_control_report"),
        )
        assertTrue(
            "Navigation guidance report shortcut should be black and larger than the default map action icon without changing the current-location button.",
            source.contains("tint = Color.Black") &&
                source.contains("iconSize = 24.dp"),
        )
    }
}
