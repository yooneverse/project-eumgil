package com.ssafy.e102.eumgil.feature.map.component

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentDestinationBottomSheetShellPolicyTest {
    private val source =
        File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/RecentDestinationBottomSheetShell.kt")
            .readText()

    @Test
    fun `recent destination sheet header keeps a compact title and emphasized expand action`() {
        assertTrue(
            "Recent destination header title should use a more compact typography level than titleLarge.",
            source.contains("style = MaterialTheme.typography.titleMedium"),
        )
        assertTrue(
            "View-all CTA should keep the requested Korean label.",
            source.contains("map_recent_destination_expand"),
        )
        assertTrue(
            "View-all CTA should include a chevron icon instead of a raw text marker.",
            source.contains("R.drawable.ic_route_card_chevron"),
        )
        assertTrue(
            "Recent destination sheet should use a downward chevron for view-all and rotate it upward for collapse.",
            source.contains("map_recent_destination_collapse") &&
                source.contains(".rotate(") &&
                source.contains("90f") &&
                source.contains("-90f"),
        )
        assertTrue(
            "View-all CTA should use the design convention blue accent token.",
            source.contains("color = MaterialTheme.colorScheme.secondary"),
        )
    }

    @Test
    fun `recent destination row uses a larger bare icon without a tinted background chip`() {
        assertTrue(
            "Recent destination rows should enlarge the category icon to the large icon size.",
            source.contains(".size(36.dp)"),
        )
        assertFalse(
            "Recent destination icons should no longer sit on a tinted background surface.",
            source.contains("color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)"),
        )
    }

    @Test
    fun `recent destination navigation actions suppress ripple while keeping the sheet animation`() {
        assertTrue(
            "Recent destination sheet should keep AnimatedVisibility for the bottom sheet slide motion.",
            source.contains("AnimatedVisibility("),
        )
        assertTrue(
            "Sheet expand/collapse action should suppress ripple because it changes the sheet state in place.",
            source.contains("private fun RecentDestinationSheetToggleAction(") &&
                source.contains("indication = null"),
        )
        assertTrue(
            "Route CTA should suppress ripple because it opens route setting from the map sheet.",
            source.contains("private fun RecentDestinationRouteButton(") &&
                source.contains("indication = null"),
        )
    }

    @Test
    fun `recent destination sheet leaves a restore handle after user dismissal`() {
        assertTrue(
            "Recent destination dismissal should switch to a compact restore handle instead of making the sheet unreachable.",
            source.contains("val isRestoreHandleVisible = state.isVisible && isDismissedByUser") &&
                source.contains("RecentDestinationRestoreHandle("),
        )
        assertTrue(
            "The restore handle should support both tap and upward drag to reopen recent destinations.",
            source.contains("isDismissedByUser = false") &&
                source.contains("if (delta < 0f)") &&
                source.contains("map_recent_destination_sheet_restore"),
        )
    }

    @Test
    fun `recent destination tags stay on one line in the compact home sheet`() {
        assertTrue(
            "Recent destination tag text should not wrap vertically on narrow devices.",
            source.contains("maxLines = 1") &&
                source.contains("softWrap = false"),
        )
    }

    @Test
    fun `recent destination sheet expands in place and scrolls up to ten items`() {
        assertTrue(
            "Collapsed home sheet should keep the compact three-item preview.",
            source.contains("private const val RecentDestinationCollapsedItemCount = 3") &&
                source.contains("state.items.take(RecentDestinationCollapsedItemCount)"),
        )
        assertTrue(
            "Expanded sheet should cap the full recent destination list at ten entries.",
            source.contains("private const val RecentDestinationExpandedItemLimit = 10") &&
                source.contains("state.items.take(RecentDestinationExpandedItemLimit)"),
        )
        assertTrue(
            "Expanded sheet should take roughly two thirds of the viewport and scroll internally.",
            source.contains("private const val RecentDestinationExpandedSheetMaxHeightFraction = 0.65f") &&
                source.contains(".verticalScroll(listScrollState)"),
        )
    }
}
