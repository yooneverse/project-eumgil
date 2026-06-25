package com.ssafy.e102.eumgil.feature.navigation

import com.ssafy.e102.eumgil.feature.guidance.component.resolveRouteStepScrubberAnchor
import com.ssafy.e102.eumgil.feature.guidance.component.resolveRouteStepScrubberIndex
import com.ssafy.e102.eumgil.feature.guidance.component.resolveRouteStepScrubberVisualOffset
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteStepScrubberRailTest {
    @Test
    fun `scrubber offset resolves to nearest route step and clamps to bounds`() {
        assertEquals(0, resolveRouteStepScrubberIndex(offsetPx = -30f, itemHeightPx = 96f, itemCount = 4))
        assertEquals(0, resolveRouteStepScrubberIndex(offsetPx = 47f, itemHeightPx = 96f, itemCount = 4))
        assertEquals(1, resolveRouteStepScrubberIndex(offsetPx = 49f, itemHeightPx = 96f, itemCount = 4))
        assertEquals(2, resolveRouteStepScrubberIndex(offsetPx = 96f * 2.49f, itemHeightPx = 96f, itemCount = 4))
        assertEquals(3, resolveRouteStepScrubberIndex(offsetPx = 96f * 9f, itemHeightPx = 96f, itemCount = 4))
        assertEquals(null, resolveRouteStepScrubberIndex(offsetPx = 0f, itemHeightPx = 96f, itemCount = 0))
        assertEquals(null, resolveRouteStepScrubberIndex(offsetPx = 0f, itemHeightPx = 0f, itemCount = 4))
    }

    @Test
    fun `scrubber index resolves to stable anchor positions`() {
        assertEquals(0f, resolveRouteStepScrubberAnchor(index = 0, itemHeightPx = 96f))
        assertEquals(288f, resolveRouteStepScrubberAnchor(index = 3, itemHeightPx = 96f))
        assertEquals(0f, resolveRouteStepScrubberAnchor(index = -1, itemHeightPx = 96f))
        assertEquals(0f, resolveRouteStepScrubberAnchor(index = 3, itemHeightPx = -1f))
    }

    @Test
    fun `scrubber visual offset moves icons upward when the focused offset increases`() {
        assertEquals(
            96f,
            resolveRouteStepScrubberVisualOffset(anchorPx = 288f, scrubberOffsetPx = 96f, itemHeightPx = 96f),
        )
        assertEquals(
            0f,
            resolveRouteStepScrubberVisualOffset(anchorPx = 288f, scrubberOffsetPx = 192f, itemHeightPx = 96f),
        )
    }

    @Test
    fun `scrubber collapses the hidden focused item so the next icon starts at the rail top`() {
        assertEquals(
            0f,
            resolveRouteStepScrubberVisualOffset(anchorPx = 96f, scrubberOffsetPx = 0f, itemHeightPx = 96f),
        )
        assertEquals(
            0f,
            resolveRouteStepScrubberVisualOffset(anchorPx = 192f, scrubberOffsetPx = 96f, itemHeightPx = 96f),
        )
        assertEquals(
            -96f,
            resolveRouteStepScrubberVisualOffset(anchorPx = 0f, scrubberOffsetPx = 96f, itemHeightPx = 96f),
        )
    }

    @Test
    fun `route detail and navigation render the shared anchored scrubber rail`() {
        val routeSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val navigationSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/component/NavigationSegmentRail.kt")
                .readText()
        val scrubberSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/RouteStepScrubberRail.kt")
                .readText()

        assertTrue(routeSource.contains("RouteStepScrubberRail("))
        assertTrue(navigationSource.contains("RouteStepScrubberRail("))
        assertTrue(scrubberSource.contains("anchoredDraggable("))
        assertTrue(scrubberSource.contains("reverseDirection = true"))
        assertTrue(scrubberSource.contains("DraggableAnchors"))
        assertTrue(scrubberSource.contains(".clipToBounds()"))
        assertTrue(scrubberSource.contains("dividerColor: Color ="))
        assertTrue(scrubberSource.contains("dividerColor = dividerColor"))
        assertTrue(scrubberSource.contains("snapshotFlow"))
        assertTrue(scrubberSource.contains("currentRouteStepScrubberOffsetPx(state, maxScrubberOffsetPx)"))
        assertTrue(scrubberSource.contains(".offset {"))
    }

    @Test
    fun `scrubber does not restart programmatic focus animation while the user is dragging`() {
        val scrubberSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/RouteStepScrubberRail.kt")
                .readText()

        assertTrue(scrubberSource.contains("MutableInteractionSource()"))
        assertTrue(scrubberSource.contains("collectIsDraggedAsState()"))
        assertTrue(scrubberSource.contains("interactionSource = interactionSource"))
        assertTrue(scrubberSource.contains("if (!isDragged"))
    }

    @Test
    fun `scrubber treats fallback focused item as hidden so route detail starts flush under the card`() {
        val scrubberSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/RouteStepScrubberRail.kt")
                .readText()

        assertTrue(scrubberSource.contains("item.index != resolvedFocusedIndex"))
    }

    @Test
    fun `programmatic scroll to origin is not overwritten by scrub callbacks during animation`() {
        val scrubberSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/RouteStepScrubberRail.kt")
                .readText()

        assertTrue(scrubberSource.contains("isProgrammaticScroll"))
        assertTrue(scrubberSource.contains("if (!isProgrammaticScroll && index != currentResolvedFocusedIndex)"))
    }

    @Test
    fun `clicking a rail icon animates it to the card slot before publishing focus`() {
        val scrubberSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/RouteStepScrubberRail.kt")
                .readText()
        val clickSection =
            scrubberSource
                .substringAfter("onClick = {")
                .substringBefore("},\n                    )")

        assertTrue(clickSection.indexOf("state.animateTo(item.index)") < clickSection.indexOf("currentOnItemClick?.invoke(item.index)"))
        assertTrue(clickSection.contains("?: currentOnFocusedItemChanged(item.index)"))
    }

    @Test
    fun `scrubber clamps visual offsets so the trailing up action cannot scroll past the top slot`() {
        val scrubberSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/RouteStepScrubberRail.kt")
                .readText()

        assertTrue(scrubberSource.contains("maxScrubberOffsetPx"))
        assertTrue(scrubberSource.contains(".coerceIn(0f, maxScrubberOffsetPx)"))
    }
}
