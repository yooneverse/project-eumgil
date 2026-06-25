package com.ssafy.e102.eumgil.feature.navigation

import androidx.compose.ui.unit.dp
import com.ssafy.e102.eumgil.feature.navigation.component.createNavigationSegmentRailSlots
import com.ssafy.e102.eumgil.feature.navigation.component.resolveGuideRailAutoScrollItemIndex
import com.ssafy.e102.eumgil.feature.navigation.component.resolveGuideRailEndSnapPadding
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSegmentRailLayoutTest {
    @Test
    fun `rail slots move first and last segments into waypoint slots`() {
        val slots =
            createNavigationSegmentRailSlots(
                NavigationSegmentSyncUiState(
                    railItems =
                        listOf(
                            railItem(index = 0, sequence = 1),
                            railItem(index = 1, sequence = 2),
                            railItem(index = 2, sequence = 3),
                            railItem(index = 3, sequence = 4),
                        ),
                ),
            )

        assertEquals(0, slots.originItem?.index)
        assertEquals(3, slots.destinationItem?.index)
        assertEquals(listOf(1, 2), slots.intermediateItems.map { item -> item.index })
        assertTrue(slots.canScrollToTop)
    }

    @Test
    fun `rail slots keep single segment available on both waypoint slots`() {
        val slots =
            createNavigationSegmentRailSlots(
                NavigationSegmentSyncUiState(
                    activeSegmentIndex = 0,
                    focusedSegmentIndex = 0,
                    railItems = listOf(railItem(index = 0, sequence = 1, isActive = true)),
                ),
            )

        assertEquals(0, slots.originItem?.index)
        assertEquals(0, slots.destinationItem?.index)
        assertTrue(slots.intermediateItems.isEmpty())
    }

    @Test
    fun `rail slots keep top action enabled while inspecting a moved waypoint segment`() {
        val slots =
            createNavigationSegmentRailSlots(
                NavigationSegmentSyncUiState(
                    activeSegmentIndex = 0,
                    focusedSegmentIndex = 2,
                    isInspectingSegments = true,
                    railItems =
                        listOf(
                            railItem(index = 0, sequence = 1, isActive = true),
                            railItem(index = 1, sequence = 2),
                            railItem(index = 2, sequence = 3, isFocused = true),
                        ),
                ),
            )

        assertTrue(slots.canScrollToTop)
        assertEquals(2, slots.destinationItem?.index)
    }

    @Test
    fun `collapsed rail paints a surface like route detail rail instead of bleeding into the map`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/component/NavigationSegmentRail.kt")
                .readText()
        val railSection =
            source
                .substringAfter("fun NavigationSegmentRail(")
                .substringBefore("@Composable\nprivate fun NavigationSegmentRailTopAction")

        assertTrue(railSection.contains(".background(MaterialTheme.colorScheme.surface)"))
        assertTrue(railSection.contains(".fillMaxHeight()"))
    }

    @Test
    fun `rail top action stays enabled and scrolls to the first item`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/component/NavigationSegmentRail.kt")
                .readText()

        assertTrue(source.contains("R.string.navigation_rail_scroll_to_top_label"))
        assertTrue(source.contains("RouteStepScrubberRail("))
        assertTrue(source.contains("onTopVisibleSegmentChanged(firstIndex)"))
        assertTrue(source.contains("onSegmentTapped(firstIndex)"))
    }

    @Test
    fun `rail removes the bottom route detail more action`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/component/NavigationSegmentRail.kt")
                .readText()
        val railSection =
            source
                .substringAfter("fun NavigationSegmentRail(")
                .substringBefore("@Composable\nprivate fun NavigationSegmentRailTopAction")

        assertFalse(railSection.contains("NavigationSegmentRailDetailAction("))
        assertFalse(source.contains("ic_navigation_detail_more"))
    }

    @Test
    fun `rail source delegates step inspection to the anchored scrubber`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/component/NavigationSegmentRail.kt")
                .readText()
        val scrubberSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/RouteStepScrubberRail.kt")
                .readText()

        assertTrue(source.contains("RouteStepScrubberRail("))
        assertTrue(source.contains("onFocusedItemChanged = onTopVisibleSegmentChanged"))
        assertTrue(source.contains("onItemClick = onSegmentTapped"))
        assertTrue(source.contains("NavigationSegmentRailItemHeight = 72.dp"))
        assertTrue(source.contains("NavigationSegmentRailTopActionHeight = NavigationSegmentRailItemHeight"))
        assertTrue(source.contains("val dividerColor = NavigationSegmentRailDividerColor"))
        assertTrue(source.contains("dividerColor = NavigationSegmentRailDividerColor"))
        assertTrue(source.contains("NavigationSegmentRailDividerColor = Color(0xFFD9D9D9)"))
        assertTrue(source.contains("R.drawable.ic_route_scroll_top"))
        assertTrue(
            "The shared rail should use anchors so scroll position is the route-step source of truth.",
            scrubberSource.contains("AnchoredDraggableState(") &&
                scrubberSource.contains("DraggableAnchors") &&
                scrubberSource.contains("anchoredDraggable("),
        )
        assertTrue(
            "The scrubber should update the top card from the nearest anchor while the user drags.",
            scrubberSource.contains("snapshotFlow") &&
                scrubberSource.contains("resolveRouteStepScrubberIndex(") &&
                scrubberSource.contains("currentOnFocusedItemChanged(index)"),
        )
        assertTrue(
            "The scroll-to-top action should focus the first guide card through the explicit segment tap path.",
            source.contains("onTopVisibleSegmentChanged(firstIndex)") &&
                source.contains("onSegmentTapped(firstIndex)"),
        )
    }

    @Test
    fun `rail items do not render separate active or focused selection blocks`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/component/NavigationSegmentRail.kt")
                .readText()
        val scrubberSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/RouteStepScrubberRail.kt")
                .readText()
        val railSection =
            source
                .substringAfter("fun NavigationSegmentRail(")
                .substringBefore("@Composable\nprivate fun NavigationSegmentRailTopAction")

        assertFalse(
            "Navigation rail waypoints should act as timeline ticks; active/focused state belongs to the top card and map.",
            scrubberSource.contains("isActive =") ||
                scrubberSource.contains("isFocused =") ||
                scrubberSource.contains("isSelected ="),
        )
        assertFalse(
            "Navigation rail segment icons should not create a second selected block while the user scrolls route steps.",
            railSection.contains("isActive = item.isActive") ||
                railSection.contains("isFocused = item.isFocused") ||
                railSection.contains("isSelected = item.isSelected"),
        )
        assertTrue(
            "Navigation rail must keep active/focused semantics even after removing the visual selection block.",
            railSection.contains("stateDescription = item.stateLabel"),
        )
    }

    @Test
    fun `rail disables fling inertia so inspection does not jump across many route steps`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/component/NavigationSegmentRail.kt")
                .readText()
        val railSection =
            source
                .substringAfter("fun NavigationSegmentRail(")
                .substringBefore("@Composable\nprivate fun NavigationSegmentRailTopAction")
        val scrubberSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/RouteStepScrubberRail.kt")
                .readText()

        assertTrue(
            "Navigation rail should use the anchored scrubber instead of LazyColumn fling inertia.",
            railSection.contains("RouteStepScrubberRail(") &&
                scrubberSource.contains("velocityThreshold = { Float.POSITIVE_INFINITY }"),
        )
    }

    @Test
    fun `rail external selection scrolls the next icon to the top while clamping the destination`() {
        assertEquals(1, resolveGuideRailAutoScrollItemIndex(focusedItemPosition = 0, itemCount = 4))
        assertEquals(3, resolveGuideRailAutoScrollItemIndex(focusedItemPosition = 2, itemCount = 4))
        assertEquals(3, resolveGuideRailAutoScrollItemIndex(focusedItemPosition = 3, itemCount = 4))
        assertEquals(null, resolveGuideRailAutoScrollItemIndex(focusedItemPosition = -1, itemCount = 4))
        assertEquals(null, resolveGuideRailAutoScrollItemIndex(focusedItemPosition = 0, itemCount = 0))
    }

    @Test
    fun `rail end padding lets destination reach the top but keeps the scroll top action below it`() {
        assertEquals(
            456.dp,
            resolveGuideRailEndSnapPadding(
                viewportHeight = 600.dp,
                guideItemHeight = 72.dp,
                trailingActionHeight = 72.dp,
            ),
        )
    }

    @Test
    fun `navigation rail can dispatch the synthetic origin item through the same up action`() {
        val viewModelSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationViewModel.kt")
                .readText()

        assertTrue(viewModelSource.contains("NavigationOriginSegmentIndex"))
        assertTrue(viewModelSource.contains("index == NavigationOriginSegmentIndex"))
        assertTrue(viewModelSource.contains("focusedSegmentIndex = NavigationOriginSegmentIndex"))
    }
}

private fun railItem(
    index: Int,
    sequence: Int,
    isActive: Boolean = false,
    isFocused: Boolean = false,
): NavigationSegmentRailItemUiState =
    NavigationSegmentRailItemUiState(
        index = index,
        sequence = sequence,
        instruction = "Segment $sequence",
        distanceLabel = "${sequence * 100}m",
        riskLabel = "낮음",
        isActive = isActive,
        isFocused = isFocused,
    )
