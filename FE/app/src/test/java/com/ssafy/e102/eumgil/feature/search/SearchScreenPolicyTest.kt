package com.ssafy.e102.eumgil.feature.search

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchScreenPolicyTest {
    @Test
    fun `home search entry title uses place search copy`() {
        val stringsSource = File("src/main/res/values/strings.xml").readText()

        assertTrue(
            "Home search should title the search screen as place search because result taps open a detail preview first.",
            stringsSource.contains("<string name=\"search_screen_title\">장소 검색</string>"),
        )
    }

    @Test
    fun `apply to route search exposes route endpoint quick actions`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/search/SearchScreen.kt")
                .readText()

        assertTrue(
            "Apply-to-route search entry should show quick actions for both route endpoint assignment targets.",
            source.contains("shouldShowRouteEndpointQuickActions(uiState.selectionMode, uiState.editingTarget)") &&
                source.contains("selectionMode == SearchSelectionMode.APPLY_TO_ROUTE") &&
                source.contains("editingTarget == RouteEditingTarget.ORIGIN") &&
                source.contains("editingTarget == RouteEditingTarget.DESTINATION") &&
                source.contains("RouteEndpointQuickActionSection("),
        )
        assertTrue(
            "Route endpoint quick actions should dispatch dedicated current-location and map-picker actions.",
            source.contains("SearchUiAction.CurrentLocationClicked") &&
                source.contains("SearchUiAction.MapPickerClicked"),
        )
        val quickActionSection =
            source
                .substringAfter("private fun RouteEndpointQuickActionSection(")
                .substringBefore("@Composable\nprivate fun RouteEndpointCurrentLocationButton")
        assertTrue(
            "Route endpoint quick actions should place current-location and map-picker buttons on one row.",
            quickActionSection.contains("Row(") &&
                quickActionSection.contains("horizontalArrangement = Arrangement.spacedBy(EumSpacing.small)") &&
                quickActionSection.contains("modifier = Modifier.weight(1f)"),
        )
        assertTrue(
            "Route endpoint quick actions should use target-specific visible labels and accessibility copy.",
            source.contains("R.string.search_screen_current_location_origin_action") &&
                source.contains("R.string.search_screen_current_location_destination_action") &&
                source.contains("R.string.search_screen_map_picker_origin_action") &&
                source.contains("R.string.search_screen_map_picker_destination_action") &&
                source.contains("R.string.search_screen_map_picker_origin_a11y") &&
                source.contains("R.string.search_screen_map_picker_destination_a11y"),
        )
        assertTrue(
            "Current-location failures should remain visible on the screen instead of being only transient feedback.",
            source.contains("currentLocationQuickActionState") &&
                source.contains("resolveSearchCurrentLocationStatusContent("),
        )
    }

    @Test
    fun `search results screen never renders route endpoint quick actions`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/search/SearchScreen.kt")
                .readText()
        val searchResultsContentSection =
            source
                .substringAfter("private fun SearchResultsContent(")
                .substringBefore("@Composable\nprivate fun SearchInputField")

        assertFalse(
            "Search results should keep route endpoint shortcuts out of the result list surface.",
            searchResultsContentSection.contains("RouteEndpointQuickActionSection(") ||
                searchResultsContentSection.contains("shouldShowRouteEndpointQuickActions("),
        )
    }

    @Test
    fun `search screen suppresses ripple on row taps that navigate away from the current view`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/search/SearchScreen.kt")
                .readText()
        val recentVisitItemSection =
            source
                .substringAfter("private fun RecentVisitItem(")
                .substringBefore("@Composable\nprivate fun DestinationPromoBanner")
        val searchResultItemSection =
            source
                .substringAfter("private fun SearchResultItem(")
                .substringBefore("private fun SearchResultAccessibilityTagRow")

        assertTrue(
            "Recent search rows should suppress ripple because they push the user into the results route.",
            recentVisitItemSection.contains("indication = null"),
        )
        assertTrue(
            "Recent search rows should keep a dedicated interaction source when ripple is suppressed.",
            recentVisitItemSection.contains("MutableInteractionSource()"),
        )
        assertTrue(
            "Search result rows should suppress ripple because they navigate to map preview from the search flow.",
            searchResultItemSection.contains("indication = null"),
        )
        assertTrue(
            "Search result rows should keep a dedicated interaction source when ripple is suppressed.",
            searchResultItemSection.contains("MutableInteractionSource()"),
        )
        assertTrue(
            "Search result rows should use a list item divider instead of card chrome.",
            searchResultItemSection.contains("HorizontalDivider("),
        )
        assertFalse(
            "Search result rows should not render each result inside a card-like Surface.",
            searchResultItemSection.contains("Surface(") ||
                searchResultItemSection.contains("shadowElevation") ||
                searchResultItemSection.contains("shape = RoundedCornerShape(EumRadius.large)"),
        )
    }

    @Test
    fun `recent search rows scroll inside the available entry space`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/search/SearchScreen.kt")
                .readText()
        val recentVisitSection =
            source
                .substringAfter("private fun RecentVisitSection(")
                .substringBefore("@Composable\nprivate fun RecentVisitItem")

        assertTrue(
            "Recent search rows should be rendered in a weighted LazyColumn so overflowing rows scroll instead of being clipped above the promo banner.",
            recentVisitSection.contains("LazyColumn(") &&
                recentVisitSection.contains(".weight(1f)") &&
                recentVisitSection.contains("items("),
        )
        assertFalse(
            "Recent search rows should not be appended directly to the parent Column because that clips the last row when the banner is visible.",
            recentVisitSection.contains("recentSearches.forEach"),
        )
    }

    @Test
    fun `results screen loading state uses spinner without illustration or placeholder card`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/search/SearchScreen.kt")
                .readText()
        val searchResultsContentSection =
            source
                .substringAfter("private fun SearchResultsContent(")
                .substringBefore("@Composable\nprivate fun SearchInputField")
        val loadingStateSection =
            searchResultsContentSection
                .substringAfter("is SearchResultUiState.Loading ->")
                .substringBefore("is SearchResultUiState.Success ->")

        assertTrue(
            "Search results loading state should use the centered branded state message.",
            loadingStateSection.contains("SearchCenteredStateMessage("),
        )
        assertTrue(
            "Search results loading state should keep a spinner affordance inside the centered state.",
            loadingStateSection.contains("showLoadingIndicator = true") &&
                source.contains("EumCircularLoadingIndicator("),
        )
        assertTrue(
            "Search results loading state should not render the centered illustration while the request is in progress.",
            loadingStateSection.contains("showIllustration = false"),
        )
        assertFalse(
            "Search results loading state should not fall back to the boxed SearchStateCard placeholder.",
            loadingStateSection.contains("SearchStateCard("),
        )
        assertFalse(
            "Search results loading should not use an oversized custom spinner.",
            source.contains("SearchResultsLoadingIndicatorSize"),
        )
    }

    @Test
    fun `search result sort control matches saved bookmark segmented button motion`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/search/SearchScreen.kt")
                .readText()
        val sortControlSection =
            source
                .substringAfter("private fun SearchSortControl(")
                .substringBefore("@Composable\nprivate fun SearchSortOptionButton")
        val sortButtonSection =
            source
                .substringAfter("private fun SearchSortOptionButton(")
                .substringBefore("@Composable\nprivate fun SearchNextPageLoadingIndicator")

        assertTrue(
            "Search sort control should use the same segmented shell and animated indicator pattern as the saved bookmark tab control.",
            sortControlSection.contains("BoxWithConstraints(") &&
                sortControlSection.contains("animateDpAsState(") &&
                sortControlSection.contains("SearchSortOptionIndicatorOffset") &&
                sortControlSection.contains("RoundedCornerShape(EumRadius.full)") &&
                sortControlSection.contains("SearchSortOptionButtonGap") &&
                !sortControlSection.contains("RoundedCornerShape(EumRadius.scaleM)"),
        )
        assertTrue(
            "Search sort buttons should be transparent hit targets over the moving selected indicator.",
            sortButtonSection.contains(".clip(RoundedCornerShape(EumRadius.full))") &&
                sortButtonSection.contains("this.selected = selected") &&
                !sortButtonSection.contains("color = containerColor"),
        )
    }

    @Test
    fun `empty result state renders as centered branded message without card chrome`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/search/SearchScreen.kt")
                .readText()
        val searchResultsContentSection =
            source
                .substringAfter("private fun SearchResultsContent(")
                .substringBefore("@Composable\nprivate fun SearchInputField")
        val emptyStateSection =
            searchResultsContentSection
                .substringAfter("is SearchResultUiState.Empty ->")
                .substringBefore("is SearchResultUiState.Error ->")
        val emptyMessageSection =
            source
                .substringAfter("private fun SearchCenteredStateMessage(")
                .substringBefore("@Composable\nprivate fun SearchStateCard")

        assertTrue(
            "Empty search results should use a centered branded message block.",
            emptyStateSection.contains("SearchCenteredStateMessage("),
        )
        assertTrue(
            "Empty search results should be centered inside the remaining result area below the controls.",
            emptyStateSection.contains("SearchResultStateBox {"),
        )
        assertTrue(
            "The centered state should include the Busan Eumgil character asset.",
            emptyMessageSection.contains("R.drawable.manual_galmaegi"),
        )
        assertFalse(
            "Empty search results should not render inside SearchStateCard card chrome.",
            emptyStateSection.contains("SearchStateCard("),
        )
        assertFalse(
            "The plain empty-result message should not add card border or shadow.",
            emptyMessageSection.contains("Surface(") ||
                emptyMessageSection.contains("BorderStroke(") ||
                emptyMessageSection.contains("shadowElevation"),
        )
    }

    @Test
    fun `empty result state uses dedicated no result illustration`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/search/SearchScreen.kt")
                .readText()
        val emptyStateSection =
            source
                .substringAfter("is SearchResultUiState.Empty ->")
                .substringBefore("is SearchResultUiState.Error ->")

        assertTrue(
            "Empty search results should use the dedicated no-result illustration instead of the shared mobility image.",
            emptyStateSection.contains("illustrationRes = R.drawable.search_empty_result_illustration"),
        )
        assertTrue(
            "The no-result illustration should use the dedicated larger empty-state size.",
            emptyStateSection.contains("illustrationSize = SearchEmptyResultIllustrationSize") &&
                source.contains("private val SearchEmptyResultIllustrationSize: Dp = 300.dp"),
        )
        assertTrue(
            "The no-result illustration and title block should be nudged upward from the centered baseline.",
            emptyStateSection.contains("contentOffsetY = SearchEmptyResultContentOffsetY") &&
                source.contains("private val SearchEmptyResultContentOffsetY: Dp = (-32).dp"),
        )
        assertTrue(
            "The no-result title should sit closer to the illustration than the default centered states.",
            emptyStateSection.contains("titleTopPadding = SearchEmptyResultTitleTopPadding") &&
                source.contains("private val SearchEmptyResultTitleTopPadding: Dp = 0.dp"),
        )
        assertTrue(
            "The dedicated no-result illustration PNG should be checked into drawable-nodpi.",
            File("src/main/res/drawable-nodpi/search_empty_result_illustration.png").exists(),
        )
    }

    @Test
    fun `search result error state avoids card chrome`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/search/SearchScreen.kt")
                .readText()
        val searchResultsContentSection =
            source
                .substringAfter("private fun SearchResultsContent(")
                .substringBefore("@Composable\nprivate fun SearchInputField")
        val errorStateSection =
            searchResultsContentSection
                .substringAfter("is SearchResultUiState.Error ->")
                .substringBefore("        }\n    }")

        assertTrue(
            "Search result errors should use the same centered branded state as empty/loading states.",
            errorStateSection.contains("SearchCenteredStateMessage("),
        )
        assertFalse(
            "Search result errors should not render as tinted cards over the results screen.",
            errorStateSection.contains("SearchStateCard(") ||
                errorStateSection.contains("errorContainer") ||
                errorStateSection.contains("BorderStroke("),
        )
        assertFalse(
            "Search result errors should not surface low-level supporting messages such as location preconditions.",
            errorStateSection.contains("supportingText = resultState.message"),
        )
    }

    @Test
    fun `search empty and error states use large split titles`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/search/SearchScreen.kt")
                .readText()
        val stringsSource = File("src/main/res/values/strings.xml").readText()

        assertTrue(
            "Search empty and error titles should use a larger centered title style.",
            source.contains("MaterialTheme.typography.headlineSmall.copy(lineHeight = SearchStateTitleLineHeight)"),
        )
        assertTrue(
            "Search empty and error copy should use explicit line breaks requested for the empty/error states.",
            stringsSource.contains("<string name=\"search_screen_empty_result_title\">검색 결과가\\n존재하지 않습니다</string>") &&
                stringsSource.contains("<string name=\"search_screen_error_title\">검색 결과를\\n불러오지 못했습니다</string>"),
        )
    }

    @Test
    fun `empty result state omits helper description and dims title`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/search/SearchScreen.kt")
                .readText()
        val emptyStateSection =
            source
                .substringAfter("is SearchResultUiState.Empty ->")
                .substringBefore("is SearchResultUiState.Error ->")
        val centeredStateSection =
            source
                .substringAfter("private fun SearchCenteredStateMessage(")
                .substringBefore("@Composable\nprivate fun SearchStateCard")

        assertTrue(
            "Empty result state should opt into the dedicated compact title typography.",
            emptyStateSection.contains("useEmptyResultTypography = true"),
        )
        assertTrue(
            "Empty result title should be compact bold text.",
            centeredStateSection.contains("fontSize = 26.sp") &&
                centeredStateSection.contains("lineHeight = SearchEmptyResultTitleLineHeight") &&
                centeredStateSection.contains("fontWeight = FontWeight.Bold"),
        )
        assertTrue(
            "Empty result title should use the same subdued gray tone as the removed helper description.",
            centeredStateSection.contains("val titleColor =") &&
                centeredStateSection.contains("if (useEmptyResultTypography)") &&
                centeredStateSection.contains("MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)") &&
                centeredStateSection.contains("color = titleColor"),
        )
        assertTrue(
            "Empty result state should intentionally omit the helper description below the title.",
            emptyStateSection.contains("description = null"),
        )
        assertFalse(
            "Empty result state should no longer render the retry helper copy.",
            emptyStateSection.contains("description = stringResource(id = copy.emptyResultDescriptionRes)"),
        )
    }

    @Test
    fun `voice input action dismisses keyboard before opening bottom sheet`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/search/SearchScreen.kt")
                .readText()
        val inputFieldSection =
            source
                .substringAfter("private fun SearchInputField(")
                .substringBefore("@OptIn(ExperimentalMaterial3Api::class)")

        assertTrue(
            "Voice input should clear TextField focus and hide the IME before navigating to the bottom sheet route.",
            source.contains("import androidx.compose.ui.platform.LocalFocusManager") &&
                source.contains("import androidx.compose.ui.platform.LocalSoftwareKeyboardController") &&
                inputFieldSection.contains("focusManager.clearFocus(force = true)") &&
                inputFieldSection.contains("keyboardController?.hide()") &&
                inputFieldSection.indexOf("focusManager.clearFocus(force = true)") <
                inputFieldSection.indexOf("onVoiceInputClick()") &&
                inputFieldSection.indexOf("keyboardController?.hide()") <
                inputFieldSection.indexOf("onVoiceInputClick()"),
        )
        assertTrue(
            "The microphone trailing icon should use the keyboard-safe voice input handler.",
            inputFieldSection.contains("IconButton(onClick = dismissKeyboardBeforeVoiceInput)"),
        )
    }
}
