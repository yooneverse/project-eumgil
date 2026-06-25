package com.ssafy.e102.eumgil.feature.map.component

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapFloatingControlsConfigurationTest {
    @Test
    fun `shared map floating controls use compact grouped layout`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/core/designsystem/component/map/EumMapFloatingControls.kt")
                .readText()

        assertTrue(
            "Shared map floating controls should keep the compact 48dp zoom stack width.",
            source.contains("modifier = Modifier.width(48.dp)"),
        )
        assertTrue(
            "Shared map floating controls should keep each zoom action at 48dp height.",
            source.contains(".height(48.dp)"),
        )
        assertTrue(
            "Shared map floating controls should keep the current-location action at a 48dp square size.",
            source.contains("modifier = Modifier.size(48.dp)"),
        )
        assertTrue(
            "Shared map floating controls should expose an optional same-shape top action for contextual map shortcuts.",
            source.contains("topActionButtonState: EumMapFloatingActionButtonState? = null") &&
                source.contains("onTopActionClick: () -> Unit = {}"),
        )
        assertTrue(
            "Shared map floating controls should group zoom in and zoom out inside one stacked card.",
            source.contains("HorizontalDivider"),
        )
    }

    @Test
    fun `shared map floating controls use fe rounding token and updated location icon`() {
        val sharedSource =
            File("src/main/java/com/ssafy/e102/eumgil/core/designsystem/component/map/EumMapFloatingControls.kt")
                .readText()
        val mapSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapFloatingControls.kt")
                .readText()

        assertTrue(
            "Shared map floating controls should keep the zoom stack with a subtle small corner radius.",
            sharedSource.contains("RoundedCornerShape(EumRadius.scaleS)"),
        )
        assertTrue(
            "Shared map floating controls should give the current-location action the same corner radius as the zoom stack.",
            sharedSource.split("RoundedCornerShape(EumRadius.scaleS)").size - 1 >= 2,
        )
        assertTrue(
            "Shared map floating controls should preserve the floating overlay elevation.",
            sharedSource.contains("shadowElevation = 6.dp"),
        )
        assertTrue(
            "Shared map floating controls should keep a shared icon frame size so loading and retry assets render at the same display size.",
            sharedSource.contains("private val MAP_FLOATING_ACTION_ICON_SIZE = 18.dp"),
        )
        assertTrue(
            "Shared map floating controls should keep the default icon size token while allowing contextual actions to override it.",
            sharedSource.contains("val iconSize: Dp = MAP_FLOATING_ACTION_ICON_SIZE") &&
                sharedSource.contains("modifier = Modifier.size(state.iconSize)"),
        )
        assertTrue(
            "MAP screen map controls should delegate to the shared component.",
            mapSource.contains("EumMapFloatingControls("),
        )
        assertTrue(
            "MAP screen should distinguish between an available current-location action and an actively selected one.",
            mapSource.contains("isRecenterButtonActive: Boolean"),
        )
        assertTrue(
            "MAP request-permission state should reuse the disabled-look PNG so the default inactive appearance matches the design.",
            mapSource.split("iconRes = R.drawable.ic_map_current_location_disabled").size - 1 >= 2,
        )
        assertTrue(
            "MAP enabled current-location button should reuse the route-start button icon.",
            mapSource.contains("R.drawable.ic_route_start_navigation_button"),
        )
        assertTrue(
            "MAP enabled current-location button should only show the filled route-start icon after the user explicitly activates current-location recentering.",
            mapSource.contains("if (isRecenterButtonActive)"),
        )
        assertTrue(
            "MAP loading current-location state should use the dedicated replacement PNG asset.",
            mapSource.contains("R.drawable.ic_map_current_location_loading"),
        )
        assertTrue(
            "MAP retry current-location state should use the dedicated replacement PNG asset.",
            mapSource.contains("R.drawable.ic_map_current_location_retry"),
        )
        assertFalse(
            "MAP loading current-location state should no longer use the generic hourglass status icon.",
            mapSource.contains("R.drawable.ic_status_hourglass"),
        )
        assertFalse(
            "MAP retry current-location state should no longer use the generic refresh status icon.",
            mapSource.contains("R.drawable.ic_status_refresh"),
        )
        assertTrue(
            "MAP enabled current-location button should tint the route-start icon with the primary color.",
            mapSource.contains("tint = MaterialTheme.colorScheme.primary"),
        )
        assertTrue(
            "MAP disabled current-location button should use the replacement PNG asset.",
            mapSource.contains("R.drawable.ic_map_current_location_disabled"),
        )
        assertTrue(
            "MAP disabled current-location button should render the provided image as-is without tinting it.",
            mapSource.contains("tint = Color.Unspecified"),
        )
        assertTrue(
            "The disabled current-location icon asset should exist in drawable.",
            File("src/main/res/drawable/ic_map_current_location_disabled.png").exists(),
        )
        assertTrue(
            "The loading current-location icon asset should exist in drawable.",
            File("src/main/res/drawable/ic_map_current_location_loading.png").exists(),
        )
        assertTrue(
            "The retry current-location icon asset should exist in drawable.",
            File("src/main/res/drawable/ic_map_current_location_retry.png").exists(),
        )
    }

    @Test
    fun `shared map floating controls use opaque surfaces over the map`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/core/designsystem/component/map/EumMapFloatingControls.kt")
                .readText()

        assertTrue(
            "Zoom and recenter controls should use the base surface color to keep the chrome visually solid.",
            source.contains("color = MaterialTheme.colorScheme.surface,"),
        )
        assertFalse(
            "Zoom and recenter controls should not use translucent surfaces over the map.",
            source.contains("surface.copy(alpha = 0.98f)"),
        )
    }

    @Test
    fun `map location panel reuses replacement loading and retry icons`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/MapScreen.kt")
                .readText()

        assertTrue(
            "MAP location loading panel should reuse the replacement loading PNG asset.",
            source.contains("actionIconRes = R.drawable.ic_map_current_location_loading"),
        )
        assertTrue(
            "MAP location ready and retry panels should reuse the replacement retry PNG asset.",
            source.split("R.drawable.ic_map_current_location_retry").size - 1 >= 2,
        )
        assertFalse(
            "MAP location panel should no longer use the generic hourglass status icon.",
            source.contains("R.drawable.ic_status_hourglass"),
        )
        assertFalse(
            "MAP location panel should no longer use the generic refresh status icon.",
            source.contains("R.drawable.ic_status_refresh"),
        )
    }
}
