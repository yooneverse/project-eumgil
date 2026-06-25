package com.ssafy.e102.eumgil.feature.navigation

import com.ssafy.e102.eumgil.feature.guidance.component.resolveGuideRailPromotedItemIndex
import com.ssafy.e102.eumgil.feature.guidance.component.shouldHideGuideRailItemForTopCard
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideSidePanelPolicyTest {
    @Test
    fun `route detail and navigation side panels share guide primitives`() {
        val shared =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/GuideSidePanel.kt")
        val routeDetail =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val navigation =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationScreen.kt")
                .readText()
        val rail =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/component/NavigationSegmentRail.kt")
                .readText()
        val scrubber =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/RouteStepScrubberRail.kt")
                .readText()

        assertTrue("Guide side panel primitives should live in a neutral feature package.", shared.exists())
        val sharedSource = shared.readText()

        assertTrue(
            "The shared module should own the expanded panel shell, step row, collapsed rail item, icon, and handle.",
            sharedSource.contains("fun GuideSidePanelShell(") &&
                sharedSource.contains("fun GuideSidePanelStepRow(") &&
                sharedSource.contains("fun GuideCollapsedRailItem(") &&
                sharedSource.contains("fun GuideSidePanelStepIcon(") &&
                sharedSource.contains("fun GuideSidePanelHandle(") &&
                sharedSource.contains("detectHorizontalDragGestures("),
        )
        assertTrue(
            "Shared guide icons should use the navigation icon set for origin, destination, and guidance steps.",
            sharedSource.contains("R.drawable.ic_navigation_rail_origin_pin") &&
                sharedSource.contains("R.drawable.ic_navigation_rail_destination_pin") &&
                sharedSource.contains("NavigationGuidanceAction"),
        )
        assertTrue(
            "Route detail should render its side panel through the shared shell, row, and anchored collapsed rail primitive.",
            routeDetail.contains("GuideSidePanelShell(") &&
                routeDetail.contains("GuideSidePanelStepRow(") &&
                routeDetail.contains("RouteStepScrubberRail(") &&
                scrubber.contains("GuideCollapsedRailItem("),
        )
        assertTrue(
            "Navigation guidance should render its side panel through the shared shell and row primitives.",
            navigation.contains("GuideSidePanelShell(") &&
                navigation.contains("GuideSidePanelStepRow("),
        )
        assertTrue(
            "Navigation collapsed rail should use the shared anchored scrubber while keeping only the scroll-top action local.",
            rail.contains("RouteStepScrubberRail(") &&
                scrubber.contains("GuideCollapsedRailItem(") &&
                rail.contains("NavigationSegmentRailTopAction(") &&
                !rail.contains("NavigationSegmentRailDetailAction("),
        )
        assertFalse(
            "Route detail should no longer own a private side-panel icon implementation.",
            routeDetail.contains("private fun RouteDetailSidePanelStepIcon("),
        )
        assertFalse(
            "Navigation should no longer own a private expanded side-panel icon implementation.",
            navigation.contains("private fun NavigationSidePanelStepIcon("),
        )
        assertFalse(
            "Route detail should no longer own a private side-panel handle or swipe threshold.",
            routeDetail.contains("private fun RouteDetailSidePanelToggleHandle(") ||
                routeDetail.contains("RouteDetailSidePanelSwipeThresholdPx"),
        )
        assertFalse(
            "Navigation should no longer own a private side-panel handle or swipe threshold.",
            navigation.contains("private fun NavigationSidePanelExpandHandle(") ||
                navigation.contains("NavigationSidePanelSwipeThresholdPx"),
        )
    }

    @Test
    fun `marker focus remains screen owned while side panel UI is shared`() {
        val shared =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/GuideSidePanel.kt")
        val routeDetail =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val detailScreenSection =
            routeDetail
                .substringAfter("fun RouteDetailScreen(")
                .substringBefore("@Composable\nprivate fun RouteDetailMapBottomSheet")

        assertTrue(
            "Route detail should keep marker focus state near the map so teammate marker-card work has a stable hook.",
            detailScreenSection.contains("focusedDetailStepIndex") &&
                detailScreenSection.contains("RouteMapBackdrop(") &&
                detailScreenSection.contains("onMarkerClick"),
        )
        assertTrue("Guide side panel primitives should exist before checking their scope.", shared.exists())
        val sharedSource = shared.readText()
        assertFalse("The shared side panel should not own map marker click logic.", sharedSource.contains("onMarkerClick"))
        assertFalse("The shared side panel should not own RouteMapBackdrop.", sharedSource.contains("RouteMapBackdrop"))
        assertFalse("The shared side panel should not own focusedDetailStepIndex.", sharedSource.contains("focusedDetailStepIndex"))
    }

    @Test
    fun `shared waypoint pins use route detail marker color tokens`() {
        val sharedSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/GuideSidePanel.kt")
                .readText()
        val originPin =
            File("src/main/res/drawable-nodpi/ic_navigation_rail_origin_pin.png")
        val destinationPin =
            File("src/main/res/drawable-nodpi/ic_navigation_rail_destination_pin.png")

        assertTrue("Origin pin asset should exist.", originPin.exists())
        assertTrue("Destination pin asset should exist.", destinationPin.exists())
        assertTrue(
            "Shared side panel should document the requested origin and destination pin colors.",
            sharedSource.contains("GuideWaypointOriginColor = Color(0xFF4D8FF9)") &&
                sharedSource.contains("GuideWaypointDestinationColor = Color(0xFFF94D4D)"),
        )
        assertTrue(
            "Expanded and collapsed side-panel waypoint pins should share the same visual size.",
            sharedSource.contains("GuideSidePanelPinWidth = GuideCollapsedRailPinWidth") &&
                sharedSource.contains("GuideSidePanelPinHeight = GuideCollapsedRailPinHeight"),
        )
        assertTrue(
            "Waypoint pins should preserve the original bitmap ratio instead of stretching the marker.",
            sharedSource.contains("contentScale = ContentScale.Fit") &&
                sharedSource.contains("GuideCollapsedRailPinHeight = GuideCollapsedRailPinWidth"),
        )
        assertFalse(
            "Waypoint pins must not be vertically stretched through FillBounds.",
            sharedSource.contains("contentScale = ContentScale.FillBounds") ||
                sharedSource.contains("GuideCollapsedRailPinHeight = 50.dp"),
        )
    }

    @Test
    fun `rail promotion advances only after the visible item crosses half height`() {
        assertEquals(
            0,
            resolveGuideRailPromotedItemIndex(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 28,
                firstVisibleItemSizePx = 56,
                itemCount = 4,
            ),
        )
        assertEquals(
            1,
            resolveGuideRailPromotedItemIndex(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 29,
                firstVisibleItemSizePx = 56,
                itemCount = 4,
            ),
        )
        assertEquals(
            3,
            resolveGuideRailPromotedItemIndex(
                firstVisibleItemIndex = 3,
                firstVisibleItemScrollOffset = 40,
                firstVisibleItemSizePx = 56,
                itemCount = 4,
            ),
        )
        assertNull(
            resolveGuideRailPromotedItemIndex(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 40,
                firstVisibleItemSizePx = 56,
                itemCount = 0,
            ),
        )
    }

    @Test
    fun `rail promotion clamps fast fling beyond the last guide item to the destination`() {
        assertEquals(
            3,
            resolveGuideRailPromotedItemIndex(
                firstVisibleItemIndex = 6,
                firstVisibleItemScrollOffset = 12,
                firstVisibleItemSizePx = 56,
                itemCount = 4,
            ),
        )
    }

    @Test
    fun `rail item promoted to the top card is hidden from the rail`() {
        assertTrue(shouldHideGuideRailItemForTopCard(itemIndex = 2, promotedItemIndex = 2))
        assertFalse(shouldHideGuideRailItemForTopCard(itemIndex = 1, promotedItemIndex = 2))
        assertFalse(shouldHideGuideRailItemForTopCard(itemIndex = 1, promotedItemIndex = null))
    }

    @Test
    fun `promoted rail item collapses out of layout instead of leaving a blank selected row`() {
        val sharedSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/GuideSidePanel.kt")
                .readText()
        val collapsedItemSection =
            sharedSource
                .substringAfter("fun GuideCollapsedRailItem(")
                .substringBefore("@Composable\nfun GuideSidePanelStepIcon")

        assertTrue(
            "The top-card item should be removed from the visible rail flow so the next guide icon sits directly under the top card.",
            collapsedItemSection.contains("val resolvedHeight = if (isContentHidden) 0.dp else height") &&
                collapsedItemSection.contains(".height(resolvedHeight)") &&
                collapsedItemSection.contains("if (!isContentHidden) {") &&
                collapsedItemSection.contains("HorizontalDivider(color = dividerColor)"),
        )
    }

    @Test
    fun `collapsed rail spacing supports picker style scrolling`() {
        val sharedSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/GuideSidePanel.kt")
                .readText()

        assertTrue(
            "Collapsed side-tab icons should use the requested 1.5x spacing so the top card is not visually clipped by adjacent icons.",
            sharedSource.contains("GuideCollapsedRailItemHeight = 96.dp"),
        )
    }
}
