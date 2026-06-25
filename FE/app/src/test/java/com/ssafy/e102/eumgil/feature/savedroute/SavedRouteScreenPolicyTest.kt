package com.ssafy.e102.eumgil.feature.savedroute

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedRouteScreenPolicyTest {
    @Test
    fun `saved route navigate button uses provided png icon without tint override`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/savedroute/SavedRouteScreen.kt")
                .readText()
        val asset = File("src/main/res/drawable/ic_route_start_navigation_button.png")

        assertTrue(
            "Saved route navigate button should use the provided PNG icon asset for the button.",
            source.contains("R.drawable.ic_route_start_navigation_button"),
        )
        assertTrue(
            "Saved route navigate button should tint the icon blue on the white button background.",
            source.contains("tint = MaterialTheme.colorScheme.primary"),
        )
        assertTrue("Saved route navigate button PNG icon should exist in drawable.", asset.exists())
    }

    @Test
    fun `saved place rows suppress ripple when tapping through to the map screen`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/savedroute/SavedRouteScreen.kt")
                .readText()
        val savedPlaceSection =
            source
                .substringAfter("private fun SavedPlaceListItem(")
                .substringBefore("@Composable\nprivate fun SavedRouteBookmarkListItem")

        assertTrue(
            "Saved place rows should suppress ripple because the content tap navigates back to the map.",
            savedPlaceSection.contains("indication = null"),
        )
        assertTrue(
            "Saved place rows should keep a dedicated interaction source when ripple is suppressed.",
            savedPlaceSection.contains("MutableInteractionSource()"),
        )
    }

    @Test
    fun `saved place category icon stays larger and vertically centered`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/savedroute/SavedRouteScreen.kt")
                .readText()
        val savedPlaceSection =
            source
                .substringAfter("private fun SavedPlaceListItem(")
                .substringBefore("@Composable\nprivate fun SavedRouteBookmarkListItem")

        assertTrue(
            "Saved-place category icon row should center the icon against the full bookmark text stack.",
            savedPlaceSection.contains("verticalAlignment = Alignment.CenterVertically"),
        )
        assertTrue(
            "Saved-place category icon should sit inside the target-style icon tile instead of floating as a bare row icon.",
            savedPlaceSection.contains("SavedPlaceCategoryIconTile(") &&
                savedPlaceSection.contains("SavedBookmarkPlaceIconTileSize"),
        )
        assertTrue(
            "Saved-place category icon token should stay noticeably larger than the previous compact size.",
            source.contains("private val SavedBookmarkCategoryIconSize = 40.dp"),
        )
    }

    @Test
    fun `saved place rows omit accessibility chips and section divider`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/savedroute/SavedRouteScreen.kt")
                .readText()
        val savedPlaceSection =
            source
                .substringAfter("private fun SavedPlaceListItem(")
                .substringBefore("@Composable\nprivate fun SavedRouteBookmarkListItem")

        assertFalse(
            "Saved-place cards should not render accessibility label chips in the compact bookmark list.",
            savedPlaceSection.contains("SavedRouteTagChip(") ||
                savedPlaceSection.contains("accessibilityFeatures"),
        )
        assertFalse(
            "Saved-place cards should not render the old section divider between content and actions.",
            savedPlaceSection.contains("HorizontalDivider") ||
                savedPlaceSection.contains("Divider("),
        )
    }

    @Test
    fun `saved bookmark cards follow the target visual hierarchy`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/savedroute/SavedRouteScreen.kt")
                .readText()
        val savedPlaceSection =
            source
                .substringAfter("private fun SavedPlaceListItem(")
                .substringBefore("@Composable\nprivate fun SavedRouteBookmarkListItem")
        val routeBookmarkSection =
            source
                .substringAfter("private fun SavedRouteBookmarkListItem(")
                .substringBefore("@Composable\nprivate fun SavedBookmarkEditBottomBar")

        assertTrue(
            "Saved bookmark cards should keep the larger target corner but remove card shadow.",
            source.contains("private val SavedBookmarkCardCornerRadius = 24.dp") &&
                source.contains("private val SavedBookmarkCardElevation = 0.dp") &&
                savedPlaceSection.contains("EumBorderSubtle.copy(alpha = 0.65f)") &&
                routeBookmarkSection.contains("EumBorderSubtle.copy(alpha = 0.65f)") &&
                savedPlaceSection.contains("shadowElevation = SavedBookmarkCardElevation") &&
                routeBookmarkSection.contains("shadowElevation = SavedBookmarkCardElevation") &&
                !savedPlaceSection.contains("val cardElevation =") &&
                !routeBookmarkSection.contains("val cardElevation ="),
        )
        assertTrue(
            "Normal place and route cards should expose the guide CTA as a prominent full-width filled button.",
            savedPlaceSection.contains(".fillMaxWidth()") &&
                savedPlaceSection.contains("SavedBookmarkPrimaryCtaHeight") &&
                savedPlaceSection.contains("isOutlined = false") &&
                routeBookmarkSection.contains(".fillMaxWidth()") &&
                routeBookmarkSection.contains("SavedBookmarkPrimaryCtaHeight") &&
                routeBookmarkSection.contains("isOutlined = false"),
        )
    }

    @Test
    fun `saved bookmark lists keep bottom breathing room outside edit mode`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/savedroute/SavedRouteScreen.kt")
                .readText()
        val screenSection =
            source
                .substringAfter("fun SavedRouteScreen(")
                .substringBefore("@Composable\nprivate fun SavedBookmarkSectionHeader")
        val placeContentSection =
            source
                .substringAfter("private fun SavedPlaceContent(")
                .substringBefore("@Composable\nprivate fun SavedRouteBookmarkContent")
        val routeContentSection =
            source
                .substringAfter("private fun SavedRouteBookmarkContent(")
                .substringBefore("@Composable\nprivate fun SavedBookmarkEmptyState")

        assertTrue(
            "Top-level bookmark screen should disable default system insets because AppNavHost already reserves the bottom tab area.",
            screenSection.contains("contentWindowInsets = WindowInsets(0, 0, 0, 0)"),
        )
        assertTrue(
            "Saved place list should add bottom content padding so the final card can scroll clear of the list edge.",
            placeContentSection.contains("contentPadding = PaddingValues(bottom = SavedBookmarkListBottomContentPadding)"),
        )
        assertTrue(
            "Saved route list should add bottom content padding so the final card is not clipped against the bottom tab area.",
            routeContentSection.contains("contentPadding = PaddingValues(bottom = SavedBookmarkListBottomContentPadding)"),
        )
        assertTrue(
            "Saved bookmark bottom padding should be larger than the ordinary 16dp gap because the final card needs scroll clearance.",
            source.contains("private val SavedBookmarkListBottomContentPadding = 80.dp"),
        )
        assertTrue(
            "Edit delete bottom bar should still only be composed in edit mode.",
            source.contains("bottomBar = {") &&
                source.contains("if (uiState.isEditMode)") &&
                source.contains("SavedBookmarkEditBottomBar("),
        )
    }

    @Test
    fun `saved bookmark edit action only appears when selected tab has content`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/savedroute/SavedRouteScreen.kt")
                .readText()
        val screenSection =
            source
                .substringAfter("fun SavedRouteScreen(")
                .substringBefore("@Composable\nprivate fun SavedBookmarkSectionHeader")
        val topBarActionEnabledSection =
            screenSection
                .substringAfter("isActionEnabled =")
                .substringBefore("onActionClick =")

        assertTrue(
            "Bookmark edit action should be tied to the currently selected tab so empty place or route tabs do not expose edit.",
            screenSection.contains("val hasSelectedTabContent =") &&
                screenSection.contains("SavedBookmarkTab.PLACE -> uiState.placeContent.places.isNotEmpty()") &&
                screenSection.contains("SavedBookmarkTab.ROUTE -> uiState.routeContent.routes.isNotEmpty()") &&
                topBarActionEnabledSection.contains("hasSelectedTabContent"),
        )
        assertFalse(
            "Bookmark edit action should not stay enabled just because the other tab has content.",
            topBarActionEnabledSection.contains("uiState.placeContent.places.isNotEmpty()") ||
                topBarActionEnabledSection.contains("uiState.routeContent.routes.isNotEmpty()"),
        )
    }

    @Test
    fun `saved bookmark tab shell stays flat and animates selected button indicator`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/savedroute/SavedRouteScreen.kt")
                .readText()
        val screenContentSection =
            source
                .substringAfter("fun SavedRouteScreen(")
                .substringBefore("@Composable\nprivate fun SavedBookmarkSectionHeader")
        val tabRowSection =
            source
                .substringAfter("private fun SavedBookmarkTabRow(")
                .substringBefore("@Composable\nprivate fun SavedBookmarkTabButton")
        val tabButtonSection =
            source
                .substringAfter("private fun SavedBookmarkTabButton(")
                .substringBefore("@Composable\nprivate fun SavedPlaceContent")

        assertTrue(
            "Place and route tab content should swap normally while the segmented button handles the animation.",
            !screenContentSection.contains("AnimatedContent(") &&
                screenContentSection.contains("when (uiState.selectedTab)") &&
                screenContentSection.contains("modifier = Modifier.weight(1f)"),
        )
        assertTrue(
            "The selected place-route button indicator should slide horizontally inside the segmented control.",
            tabRowSection.contains("BoxWithConstraints(") &&
                tabRowSection.contains("animateDpAsState(") &&
                tabRowSection.contains("targetValue = targetIndicatorOffset") &&
                tabRowSection.contains("label = \"SavedBookmarkTabIndicatorOffset\"") &&
                tabRowSection.contains(".offset(x = animatedIndicatorOffset)") &&
                source.contains("private val SavedBookmarkTabButtonGap = 4.dp") &&
                source.contains("private const val SavedBookmarkTabButtonAnimationMillis = 220"),
        )
        assertTrue(
            "The place-route segmented control should explicitly remove internal elevation from both the shell and tab buttons.",
            tabRowSection.contains("tonalElevation = 0.dp") &&
                tabRowSection.contains("shadowElevation = 0.dp") &&
                !tabButtonSection.contains("Surface("),
        )
    }

    @Test
    fun `saved bookmark section header keeps edit and normal spacing aligned`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/savedroute/SavedRouteScreen.kt")
                .readText()
        val sectionHeaderSection =
            source
                .substringAfter("private fun SavedBookmarkSectionHeader(")
                .substringBefore("@Composable\nprivate fun SavedRouteTopBar")

        assertTrue(
            "Saved bookmark section header should keep a stable minimum height so edit mode does not reduce the vertical margin around the title.",
            sectionHeaderSection.contains(".heightIn(min = SavedBookmarkSectionHeaderMinHeight)") &&
                source.contains("private val SavedBookmarkSectionHeaderMinHeight = 48.dp"),
        )
    }

    @Test
    fun `saved bookmark names use compact wrapping rules`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/savedroute/SavedRouteScreen.kt")
                .readText()
        val savedPlaceSection =
            source
                .substringAfter("private fun SavedPlaceListItem(")
                .substringBefore("@Composable\nprivate fun SavedRouteBookmarkListItem")
        val routeWaypointInfoSection =
            source
                .substringAfter("private fun SavedRouteWaypointInfoRow(")
                .substringBefore("private fun routeOptionCompactLabel(")

        assertTrue(
            "Saved-place names should use a dedicated compact line-height token instead of the default title spacing.",
            savedPlaceSection.contains("SavedBookmarkPlaceNameLineHeight"),
        )
        assertTrue(
            "Saved-place names should clamp to two lines inside bookmark cards.",
            savedPlaceSection.contains("maxLines = SavedBookmarkPrimaryTextMaxLines"),
        )
        assertTrue(
            "Saved-place names should end with ellipsis when the card width is too narrow.",
            savedPlaceSection.contains("overflow = TextOverflow.Ellipsis"),
        )
        assertTrue(
            "Saved-route waypoint values should use a dedicated compact line-height token instead of the default body spacing.",
            routeWaypointInfoSection.contains("SavedBookmarkWaypointValueLineHeight"),
        )
        assertTrue(
            "Saved-route waypoint values should stay on a single line inside bookmark cards.",
            routeWaypointInfoSection.contains("maxLines = SavedBookmarkWaypointValueMaxLines"),
        )
        assertTrue(
            "Saved-route waypoint values should end with ellipsis when the card width is too narrow.",
            routeWaypointInfoSection.contains("overflow = TextOverflow.Ellipsis"),
        )
    }

    @Test
    fun `saved route navigation ctas suppress ripple when leaving the bookmark screen`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/savedroute/SavedRouteScreen.kt")
                .readText()
        val stateActionsSection =
            source
                .substringAfter("private fun SavedBookmarkStateActions(")
                .substringBefore("@Composable\nprivate fun SavedRouteInlineMessage")
        val primaryActionSection =
            source
                .substringAfter("private fun SavedBookmarkPrimaryActionButton(")
                .substringBefore("@Composable\nprivate fun SavedRoutePathDecoration")
        val editBottomBarSection =
            source
                .substringAfter("private fun SavedBookmarkEditBottomBar(")
                .substringBefore("@Composable\nprivate fun SavedBookmarkPrimaryActionButton")

        assertTrue(
            "Saved-route empty and error CTAs should use a no-ripple navigation button because they jump back to the map screen.",
            stateActionsSection.contains("NoRippleSavedRouteNavigationButton("),
        )
        assertTrue(
            "Saved-route empty and error CTAs should center content across the weighted button width.",
            stateActionsSection.contains("fullWidthContent = true"),
        )
        assertTrue(
            "Saved-route primary CTA should use the no-ripple navigation button when it opens route guidance.",
            primaryActionSection.contains("NoRippleSavedRouteNavigationButton("),
        )
        assertTrue(
            "Saved-route primary CTA should use a less-rounded shape than the old pill button.",
            primaryActionSection.contains("val navigationButtonShape = RoundedCornerShape(EumRadius.small)"),
        )
        assertTrue(
            "Saved-route primary CTA should reduce its minimum height slightly inside bookmark rows.",
            primaryActionSection.contains("sharedModifier.heightIn(min = 38.dp)"),
        )
        assertTrue(
            "Saved-route primary CTA should tighten horizontal and vertical padding to read smaller.",
            primaryActionSection.contains("contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)"),
        )
        assertTrue(
            "Saved-route list action buttons should avoid forcing full-width content inside list rows.",
            !primaryActionSection.contains("fullWidthContent = true"),
        )
        assertTrue(
            "Saved-route edit mode should use a sticky bottom delete CTA instead of per-card delete buttons.",
            editBottomBarSection.contains("R.string.saved_route_delete_selected") &&
                source.contains("SavedRouteUiAction.DeleteSelectedClicked"),
        )
        assertTrue(
            "Outlined saved-route navigation buttons should use the same subtle border family as the report tab.",
            source.contains("EumBorderSubtle.copy(alpha = 0.75f)"),
        )
        assertTrue(
            "Saved-route no-ripple CTA helper should disable ripple indication explicitly.",
            source.contains("indication = null"),
        )
        assertTrue(
            "Saved-route no-ripple CTA helper should keep a dedicated interaction source.",
            source.contains("MutableInteractionSource()"),
        )
    }

    @Test
    fun `route tab empty state opens route setting instead of map exploration`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/savedroute/SavedRouteScreen.kt")
                .readText()
        val routeContentSection =
            source
                .substringAfter("private fun SavedRouteBookmarkContent(")
                .substringBefore("@Composable\nprivate fun SavedBookmarkStateCard")
        val routeEmptySection =
            routeContentSection
                .substringAfter("SavedBookmarkContentState.EMPTY ->")
                .substringBefore("SavedBookmarkContentState.ERROR ->")

        assertTrue(
            "Saved route tab empty/error CTA should use route-setting wording.",
            routeContentSection.contains("R.string.saved_route_route_setting_action"),
        )
        assertTrue(
            "Saved route tab empty/error CTA should navigate to route setting, not map exploration.",
            routeContentSection.contains("SavedRouteUiAction.RouteSettingClicked"),
        )
        assertFalse(
            "Saved route empty state should not show transport or route-option label chips.",
            routeEmptySection.contains("saved_route_transport_mode_walk") ||
                routeEmptySection.contains("saved_route_route_option_safe_compact") ||
                routeEmptySection.contains("saved_route_route_option_fast_compact"),
        )
        assertFalse(
            "Saved route empty state should not show a recent guidance history action.",
            routeEmptySection.contains("최근 길안내") ||
                routeEmptySection.contains("recent", ignoreCase = true) ||
                routeEmptySection.contains("history", ignoreCase = true),
        )
    }

    @Test
    fun `bookmark loading states render inline feedback without card chrome`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/savedroute/SavedRouteScreen.kt")
                .readText()
        val placeLoadingSection =
            source
                .substringAfter("private fun SavedPlaceContent(")
                .substringBefore("SavedBookmarkContentState.EMPTY ->")
        val routeLoadingSection =
            source
                .substringAfter("private fun SavedRouteBookmarkContent(")
                .substringBefore("SavedBookmarkContentState.EMPTY ->")
        val loadingStateSection =
            source
                .substringAfter("private fun SavedBookmarkLoadingState(")
                .substringBefore("@Composable\nprivate fun SavedBookmarkStateCard")

        assertTrue(
            "Saved bookmark loading branches should use the inline loading state instead of the card state component.",
            placeLoadingSection.contains("SavedBookmarkLoadingState(") &&
                routeLoadingSection.contains("SavedBookmarkLoadingState("),
        )
        assertFalse(
            "Saved bookmark loading branches should not request the card loading mode.",
            placeLoadingSection.contains("SavedBookmarkStateCard(") ||
                routeLoadingSection.contains("SavedBookmarkStateCard(") ||
                source.contains("isLoading = true"),
        )
        assertTrue(
            "Inline bookmark loading feedback should keep a progress indicator and polite live region without a Surface card.",
            loadingStateSection.contains("CircularProgressIndicator()") &&
                loadingStateSection.contains("LiveRegionMode.Polite") &&
                !loadingStateSection.contains("Surface("),
        )
    }

    @Test
    fun `saved route shows labeled origin and destination rows with stretched path decoration`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/savedroute/SavedRouteScreen.kt")
                .readText()
        val routeBookmarkSection =
            source
                .substringAfter("private fun SavedRouteBookmarkListItem(")
                .substringBefore("@Composable\nprivate fun SavedBookmarkEditBottomBar")
        val pathDecorationSection =
            source
                .substringAfter("private fun SavedRoutePathDecoration(")
                .substringBefore("@Composable\nprivate fun SavedRouteMetaRow")
        val routeWaypointInfoSection =
            source
                .substringAfter("private fun SavedRouteWaypointInfoRow(")
                .substringBefore("@Composable\nprivate fun routeOptionCompactLabel")

        assertTrue(
            "Saved-route cards should render a dedicated origin label beside its value.",
            routeBookmarkSection.contains("R.string.route_setting_origin_label"),
        )
        assertTrue(
            "Saved-route cards should render a dedicated destination label beside its value.",
            routeBookmarkSection.contains("R.string.route_setting_destination_label"),
        )
        assertTrue(
            "Saved-route waypoint rows should place the label and waypoint text on the same horizontal line.",
            routeWaypointInfoSection.contains("Row(") &&
                routeWaypointInfoSection.contains("verticalAlignment = Alignment.CenterVertically") &&
                routeWaypointInfoSection.contains("modifier = Modifier.weight(1f)"),
        )
        assertFalse(
            "Saved-route cards should remove the large route-name headline once the labeled origin and destination rows are shown.",
            Regex("""\btext\s*=\s*routeBookmark\.routeName""").containsMatchIn(routeBookmarkSection),
        )
        assertTrue(
            "Saved-route cards should group the left path decoration with the waypoint rows so the decoration can match the content height.",
            routeBookmarkSection.contains(".height(IntrinsicSize.Min)"),
        )
        assertTrue(
            "The left path decoration should fill the available waypoint height instead of keeping a fixed connector segment.",
            routeBookmarkSection.contains(".fillMaxHeight()"),
        )
        assertTrue(
            "The left path decoration should stretch its connector using weight between origin and destination markers.",
            pathDecorationSection.contains(".weight(1f)"),
        )
        assertTrue(
            "Saved-route card body should align route content to the top so the destination row does not drift downward.",
            routeBookmarkSection.contains("verticalAlignment = Alignment.Top"),
        )
        assertTrue(
            "Saved-route destination row should have a small explicit gap while the left dots align with row centers.",
            routeBookmarkSection.contains("SavedBookmarkRouteWaypointGap") &&
                source.contains("private val SavedBookmarkRouteWaypointGap = 8.dp") &&
                source.contains("private val SavedBookmarkRoutePathVerticalPadding = 7.dp"),
        )
        assertFalse(
            "Saved-route cards should not keep the old raw one-line summary once labeled rows are shown.",
            routeBookmarkSection.contains("R.string.saved_route_route_summary"),
        )
    }

    @Test
    fun `saved route cards show distance but omit duration meta labels`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/savedroute/SavedRouteScreen.kt")
                .readText()
        val routeBookmarkSection =
            source
                .substringAfter("private fun SavedRouteBookmarkListItem(")
                .substringBefore("@Composable\nprivate fun SavedBookmarkEditBottomBar")

        assertTrue(
            "Saved-route cards should surface saved distance in the transport meta row.",
            routeBookmarkSection.contains("bookmarkDistanceLabel(routeBookmark.distanceMeters)") &&
                routeBookmarkSection.contains("SavedRouteMetaRow(") &&
                source.contains("saved_route_distance_kilometers"),
        )
        assertFalse(
            "Saved-route cards should not render duration labels such as 약 18분 in the compact bookmark row.",
            routeBookmarkSection.contains("durationMinutes") ||
                routeBookmarkSection.contains("saved_route_meta_duration"),
        )
        assertTrue(
            "Saved-route cards should only attach compact safe or fast route labels in the meta text.",
            routeBookmarkSection.contains("routeOptionCompactLabel(") &&
                source.contains("saved_route_route_option_safe_compact") &&
                source.contains("saved_route_route_option_fast_compact"),
        )
        assertTrue(
            "Saved-route cards should normalize Korean route labels to compact safe or fast chips only.",
            source.contains("\"안전\", \"안전한 길\"") &&
                source.contains("\"빠른\", \"빠른 길\", \"최단거리\""),
        )
    }

    @Test
    fun `saved route card body opens route detail on tap`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/savedroute/SavedRouteScreen.kt")
                .readText()
        val routeContentSection =
            source
                .substringAfter("private fun SavedRouteBookmarkContent(")
                .substringBefore("@Composable\nprivate fun SavedBookmarkStateCard")
        val routeBookmarkSection =
            source
                .substringAfter("private fun SavedRouteBookmarkListItem(")
                .substringBefore("@Composable\nprivate fun SavedBookmarkEditBottomBar")

        assertTrue(
            "Saved-route list should dispatch a dedicated route-card tap action instead of reusing only the start button action.",
            routeContentSection.contains("SavedRouteUiAction.RouteClicked(bookmarkId = routeBookmark.bookmarkId)"),
        )
        assertTrue(
            "Saved-route card body should expose its own click callback so the bookmark summary opens route detail.",
            routeBookmarkSection.contains("onRouteClick: (() -> Unit)?"),
        )
        assertTrue(
            "Saved-route card body should suppress ripple when tapping through to route detail, matching the other saved navigation transitions.",
            routeBookmarkSection.contains("indication = null"),
        )
    }

    @Test
    fun `saved route edit mode uses red pending deletion state instead of checkboxes`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/savedroute/SavedRouteScreen.kt")
                .readText()
        val topBarSection =
            source
                .substringAfter("private fun SavedRouteTopBar(")
                .substringBefore("@Composable\nprivate fun SavedBookmarkTabRow")

        assertTrue(
            "Edit mode should close from a back icon on the top-left instead of a Done text action.",
            topBarSection.contains("Alignment.CenterStart") &&
                topBarSection.contains("R.drawable.ic_action_back") &&
                !topBarSection.contains("R.string.saved_route_done"),
        )
        assertFalse(
            "Bookmark edit cards should not render checkbox controls.",
            source.contains("Role.Checkbox") ||
                source.contains("SavedBookmarkSelectionButton("),
        )
        assertTrue(
            "Selected edit items should show a flat red-tint pending deletion visual state with a subtle red border and no gray elevation.",
            !source.contains("SavedBookmarkPendingDeleteBadge(") &&
                !source.contains("R.string.saved_route_pending_delete") &&
                source.contains("MaterialTheme.colorScheme.error.copy(alpha = 0.28f)") &&
                source.contains("SavedBookmarkPendingDeleteContainerColor") &&
                source.contains("private val SavedBookmarkCardElevation = 0.dp"),
        )
        assertFalse(
            "Edit delete CTA should not reserve system navigation padding inside the top-level tab shell.",
            source.contains(".navigationBarsPadding()"),
        )
    }

    @Test
    fun `saved route meta row places option text beside transport mode and excludes secondary options`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/savedroute/SavedRouteScreen.kt")
                .readText()
        val routeBookmarkSection =
            source
                .substringAfter("private fun SavedRouteBookmarkListItem(")
                .substringBefore("@Composable\nprivate fun SavedBookmarkEditBottomBar")
        val routeMetaRowSection =
            source
                .substringAfter("private fun SavedRouteMetaRow(")
                .substringBefore("@Composable\nprivate fun SavedRouteWaypointInfoRow")
        val routeOptionSection =
            source
                .substringAfter("private fun routeOptionCompactLabel(")
                .substringBefore("@Composable\nprivate fun transportModeLabel")

        assertTrue(
            "Saved-route option text should render in the same meta row as the transport mode.",
            routeBookmarkSection.indexOf("transportModeLabel = transportModeLabel(routeBookmark.transportMode)") <
                routeBookmarkSection.indexOf("routeOptionLabel = routeOptionLabel") &&
                routeBookmarkSection.indexOf("routeOptionLabel = routeOptionLabel") <
                routeBookmarkSection.indexOf("if (!isEditMode)"),
        )
        assertTrue(
            "Saved-route meta should read like plain text instead of separate button-like chips.",
            routeMetaRowSection.contains("joinToString(separator = \" · \")") &&
                routeMetaRowSection.contains("R.drawable.ic_route_walk") &&
                !routeBookmarkSection.contains("SavedRouteTagChip("),
        )
        assertTrue(
            "Only safe and fast route labels should be shown; min-walk and similar labels should be suppressed.",
            routeOptionSection.contains("\"MIN_WALK\"") &&
                routeOptionSection.contains("\"무단차 우선\"") &&
                routeOptionSection.contains("-> null"),
        )
    }
}
