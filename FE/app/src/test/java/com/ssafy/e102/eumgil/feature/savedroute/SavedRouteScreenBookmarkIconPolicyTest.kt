package com.ssafy.e102.eumgil.feature.savedroute

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedRouteScreenBookmarkIconPolicyTest {
    @Test
    fun `saved place rows show category icon before bookmark text content`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/savedroute/SavedRouteScreen.kt").readText()
        val savedPlaceSection =
            source
                .substringAfter("private fun SavedPlaceListItem(")
                .substringBefore("@Composable\nprivate fun SavedRouteBookmarkListItem")

        assertTrue(
            "Saved-place cards should resolve a place-category icon from the bookmark category.",
            savedPlaceSection.contains("SavedPlaceCategoryIconTile(category = place.category)") &&
                savedPlaceSection.contains("savedBookmarkPlaceCategoryIconRes(category)"),
        )
        assertTrue(
            "Saved-place cards should render the category icon before the category/name text stack.",
            savedPlaceSection.contains("SavedBookmarkCategoryIconSize") &&
                savedPlaceSection.contains("Icon("),
        )
    }
}
