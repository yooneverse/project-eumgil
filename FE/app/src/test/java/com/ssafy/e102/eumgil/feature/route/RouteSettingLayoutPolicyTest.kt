package com.ssafy.e102.eumgil.feature.route

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.designsystem.theme.EumPrimary600
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSettingLayoutPolicyTest {
    @Test
    fun `route setting keeps default content within one non scrolling screen`() {
        val policy = routeSettingLayoutPolicy()

        assertFalse(policy.allowsDefaultVerticalScroll)
        assertEquals(RouteSettingCtaPlacement.BottomBar, policy.ctaPlacement)
        assertEquals(RouteSettingMapHeightPolicy.FillRemainingCenterSpace, policy.mapHeightPolicy)
        assertEquals(3, policy.maxVisibleOptionCards)
        assertFalse(policy.showsOptionSectionSupportingText)
        assertEquals("출발", policy.originLabel)
        assertEquals("도착", policy.destinationLabel)
        assertTrue(policy.showsWaypointSwapButton)
        assertEquals(RouteWaypointMarkerStyle.LinkedPin, policy.waypointMarkerStyle)
        assertEquals(RouteWaypointConnectorStyle.VerticalLine, policy.waypointConnectorStyle)
        assertFalse(policy.showsWaypointDivider)
        assertEquals(RouteWaypointSwapButtonStyle.Borderless, policy.waypointSwapButtonStyle)
        assertEquals(RouteSettingOptionContainer.BottomSheet, policy.optionContainer)
        assertEquals(RouteSettingTravelModeTabShape.SegmentedPill, policy.travelModeTabShape)
        assertEquals(2, policy.visibleAccessibilityChipCount)
        assertTrue(policy.mapFillsRemainingCenterSpace)
        assertTrue(policy.bottomSheetEdgeToEdge)
        assertEquals(RouteSettingSheetContainerColor.White, policy.sheetContainerColor)
        assertFalse(policy.showsRecommendedBadge)
        assertEquals(RouteSettingStartCtaIcon.NavigationPointer, policy.startCtaIcon)
        assertEquals(RouteSettingCtaIconTint.OnPrimary, policy.startCtaIconTint)
        assertFalse(policy.bottomSheetFlushToWindowBottom)
        assertEquals(RouteSettingSheetElevation.None, policy.sheetElevation)
        assertEquals(RouteSettingSheetBorder.None, policy.sheetBorder)
        assertEquals(RouteSettingOptionCardContainerColor.White, policy.optionCardContainerColor)
        assertEquals(RouteSettingTravelModeIcon.WalkImage, policy.walkTabIcon)
        assertEquals(RouteSettingTravelModeIcon.TransitImage, policy.transitTabIcon)
        assertEquals(RouteSettingTravelModeActiveColor.PrimaryBlue, policy.travelModeActiveColor)
        assertEquals(RouteSettingTravelModeInactiveColor.Grey700, policy.travelModeInactiveColor)
        assertEquals(RouteSettingTravelModeIconSize.Emphasized, policy.travelModeIconSize)
    }

    @Test
    fun `route setting waypoint input uses muted labels and pale blue background`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val headerSection =
            source
                .substringAfter("private fun RouteSearchHeaderKakao(")
                .substringBefore("@Composable\nprivate fun RouteSearchHeaderModeTab")
        val waypointCardSection =
            source
                .substringAfter("private fun RouteWaypointCard(")
                .substringBefore("private fun resolveOriginWaypointPresentation")
        val waypointRowSection =
            source
                .substringAfter("private fun RouteWaypointRow(")
                .substringBefore("@Composable\nprivate fun RouteOriginStatusText")

        assertTrue(source.contains("RouteWaypointInputContainerColor = Color(0xFFF5F8FF)"))
        assertTrue(source.contains("RouteWaypointInputLabelColor = Color(0xFF94A3B8)"))
        assertTrue(source.contains("RouteSearchHeaderEmphasizedBoxColor = Color(0xFFF5F8FF)"))
        assertTrue(source.contains("RouteWaypointOriginLabelColor = Color(0xFF16A34A)"))
        assertTrue(source.contains("RouteWaypointDestinationLabelColor = Color(0xFFF14337)"))
        assertTrue(headerSection.contains("color = headerPolicy.summaryContainerColor"))
        assertTrue(waypointCardSection.contains("color = RouteWaypointInputContainerColor"))
        assertTrue(waypointRowSection.contains("color = RouteWaypointInputLabelColor"))
    }

    @Test
    fun `transit route option cards emphasize label and travel time typography`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val compactCardSection =
            source
                .substringAfter("private fun RouteCompactOptionCard(")
                .substringBefore("@Composable\nprivate fun RouteSearchLoadingState")

        assertTrue(source.contains("RouteTransitOptionTitleFontSize = 13.sp"))
        assertTrue(source.contains("RouteTransitOptionEstimatedTimeFontSize = 20.sp"))
        assertTrue(compactCardSection.contains("isEmphasized = isTransitCard"))
        assertTrue(compactCardSection.contains("fontSize = RouteTransitOptionEstimatedTimeFontSize"))
        assertTrue(source.contains("fontSize = RouteTransitOptionTitleFontSize"))
    }

    @Test
    fun `route setting screen keeps shared bottom bar and removes card inline cta`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val screenSection =
            source
                .substringAfter("fun RouteSettingScreen(")
                .substringBefore("@Composable\nfun RouteDetailScreen")
        val sheetSection =
            source
                .substringAfter("private fun RouteSettingTransitResultPane(")
                .substringBefore("@Composable\nprivate fun RouteOptionSection")

        assertTrue(
            "Route selection should render the start CTA as a floating overlay aligned to the bottom.",
            screenSection.contains("RouteSettingBottomBar(") &&
                screenSection.contains(".align(Alignment.BottomCenter)") &&
                screenSection.contains(".zIndex(RouteSettingBottomBarZIndex)"),
        )
        assertTrue(
            "Route selection should reuse the shared bottom bar component.",
            screenSection.contains("RouteSettingBottomBar("),
        )
        assertFalse(
            "The route sheet should no longer render the inline CTA content inside the sheet.",
            sheetSection.contains("RouteSettingCtaContent("),
        )
        assertFalse(
            "Transit result cards should not render an inline start button; start remains in the shared bottom bar.",
            source.contains("RouteInlineStartActionButton(onClick = onStartClick)"),
        )
    }

    @Test
    fun `route setting scaffold disables default bottom insets so the fixed cta owns the bottom gap`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val screenSection =
            source
                .substringAfter("fun RouteSettingScreen(")
                .substringBefore("@Composable\nfun RouteDetailScreen")

        assertTrue(routeSettingUsesEmptyWindowInsets())
        assertFalse(
            "Route selection should not leave extra top padding between the blue header and the map stage.",
            screenSection.contains(".padding(top = RouteSettingScreenVerticalPadding)"),
        )
    }

    @Test
    fun `route detail scaffold disables default bottom insets so start cta aligns with navigation exit cta`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val detailScreenSection =
            source
                .substringAfter("fun RouteDetailScreen(")
                .substringBefore("@Composable\nprivate fun RouteDetailTopBar")
        val routeBottomBarSection =
            source
                .substringAfter("private fun RouteSettingBottomBar(")
                .substringBefore("@Composable\nprivate fun RouteSettingCtaContent")
        val navigationSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationScreen.kt")
                .readText()
        val navigationBottomBarSection =
            navigationSource
                .substringAfter("private fun NavigationBottomBar(")
                .substringBefore("@Composable\nprivate fun NavigationMapStage")

        assertTrue(detailScreenSection.contains("contentWindowInsets = WindowInsets(0, 0, 0, 0)"))
        assertTrue(routeBottomBarSection.contains(".navigationBarsPadding()"))
        assertTrue(navigationBottomBarSection.contains(".navigationBarsPadding()"))
        assertTrue(source.contains("RouteSettingBottomBarHorizontalPadding = EumSpacing.medium + 50.dp"))
        assertTrue(navigationSource.contains("NavigationBottomBarHorizontalPadding = EumSpacing.medium + 50.dp"))
        assertTrue(source.contains("RouteSettingBottomBarBottomGap = 30.dp"))
        assertTrue(navigationSource.contains("NavigationBottomBarBottomGap = 30.dp"))
    }

    @Test
    fun `route setting transit result list implements UIUX plan skeleton`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val optionSection =
            source
                .substringAfter("private fun RouteOptionSection(")
                .substringBefore("@OptIn(ExperimentalLayoutApi::class)")

        assertFalse("Transit results should not render filter and sort controls in the sheet.", optionSection.contains("RouteTransitResultControls("))
        assertTrue("Transit results should render the segment ratio bar.", source.contains("RouteTransitSegmentRatioBar("))
        assertTrue("Transit results should render bus or subway option labels.", source.contains("RouteTransitOptionSummary("))
        assertTrue("Initial route loading should use a full-screen spinner state instead of the map.", source.contains("RouteLoadingScreen("))
        assertFalse("Transit cards should not show inline start on selected routes.", source.contains("card.travelMode == RouteTravelMode.TRANSIT && card.isSelected"))
        assertFalse("Transit cards should not keep the left radio selection indicator.", optionSection.contains("RouteOptionSelectionIndicator("))
        assertTrue("Visible route options should stay capped at three.", source.contains("take(MAX_VISIBLE_OPTION_CARD_COUNT)"))
        assertTrue("The max visible route option count should remain three.", source.contains("private const val MAX_VISIBLE_OPTION_CARD_COUNT = 3"))
    }

    @Test
    fun `route setting keeps kakao header shell and walk map preview carousel`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val screenSection =
            source
                .substringAfter("fun RouteSettingScreen(")
                .substringBefore("if (isDuribalConfirmDialogVisible)")
        val mapStageSection =
            source
                .substringAfter("private fun RouteMapStage(")
                .substringBefore("@Composable\nprivate fun RouteMapMessageCard")

        assertTrue("Route setting top bar should use the blue route search header.", screenSection.contains("RouteSearchHeaderKakao("))
        assertTrue("Walk mode should render map-anchored preview cards instead of the transit bottom sheet.", mapStageSection.contains("RouteWalkPreviewCarousel("))
        assertTrue("Walk preview cards should expose the route detail arrow CTA.", source.contains("경로 상세 보기"))
        assertFalse("Walk preview should remove kcal text beside distance.", source.contains("estimatedWalkCaloriesLabel("))
        assertTrue(
            "Walk preview cards should expose at least two accessibility labels through the shared route label helper.",
            source.contains("routeCardVisibleAccessibilityBadges(card.badges)") &&
                source.contains("RouteAccessibilityLabelChip("),
        )
        assertTrue(
            "Walk preview cards should split the available row width equally like the reference mock.",
            source.contains("private fun RouteWalkPreviewCarousel") &&
                source.contains("optionCards.take(RouteWalkPreviewVisibleCardCount)") &&
                source.contains("modifier = Modifier.weight(1f)"),
        )
        assertTrue(
            "Transit mode should use a map-free pane, while loading, unsupported-area, and failure states replace the map and hide the shared CTA.",
            screenSection.contains("RouteSettingTransitResultPane(") &&
                screenSection.contains("RouteLoadingScreen(") &&
                screenSection.contains("RouteUnsupportedAreaScreen(") &&
                screenSection.contains("RouteFailureScreen(") &&
                source.contains("selectedTravelMode == RouteTravelMode.TRANSIT") &&
                source.contains("selectedRoute == null") &&
                screenSection.contains("if (!showsRouteLoadingScreen && !showsRouteUnsupportedAreaScreen && !showsRouteFailureScreen)") &&
                source.contains("routePreviewMap.status == RoutePreviewMapStatus.NO_ROUTE") &&
                source.contains("loadErrorMessage != null"),
        )
    }

    @Test
    fun `route setting no destination state keeps map clear and uses non modal snackbar feedback`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val entryRouteSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingEntryRoute.kt")
                .readText()
        val mapStageSection =
            source
                .substringAfter("private fun RouteMapStage(")
                .substringBefore("@Composable\nprivate fun RouteMapMessageCard")
        val ctaContentSection =
            source
                .substringAfter("private fun RouteSettingCtaContent(")
                .substringBefore("@Composable\nprivate fun RouteMapBackdrop")

        assertTrue(
            "No-destination copy should not cover the map; disabled-start guidance moves to snackbar feedback.",
            mapStageSection.contains("shouldShowRouteMapMessageCard(") &&
                source.contains("private fun shouldShowRouteMapMessageCard(") &&
                source.contains("previewMap.status != RoutePreviewMapStatus.NO_DESTINATION"),
        )
        assertTrue(
            "Disabled CTA taps should use the existing muted button token style while keeping the snackbar tap target active.",
            ctaContentSection.contains("onDisabledStartClick") &&
                ctaContentSection.contains("Surface(") &&
                ctaContentSection.contains("indication = null") &&
                ctaContentSection.contains("onClick = if (enabled) onStartClick else onDisabledStartClick") &&
                ctaContentSection.contains("routeSettingCtaContainerColor(enabled = enabled)") &&
                ctaContentSection.contains("routeSettingCtaContentColor(enabled = enabled)") &&
                source.contains("EumSurfaceMuted") &&
                source.contains("EumTextTertiary") &&
                !source.contains("RouteStartButtonDisabledContainerColor") &&
                !source.contains("RouteStartButtonDisabledContentColor") &&
                !ctaContentSection.contains("enabled = enabled,"),
        )
        assertTrue(
            "Route snackbars should dismiss the current item instead of queueing and should not install a full-screen touch blocker.",
            entryRouteSource.contains("snackbarHostState.currentSnackbarData?.dismiss()") &&
                source.contains("SnackbarHost(") &&
                !source.contains("SnackbarHost(\n            hostState = snackbarHostState,\n            modifier = Modifier.fillMaxSize()"),
        )
    }

    @Test
    fun `route setting physical back uses the same navigation callbacks as visible back controls`() {
        val entryRouteSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingEntryRoute.kt")
                .readText()
        val routeSettingEntry =
            entryRouteSource
                .substringAfter("fun RouteSettingEntryRoute(")
                .substringBefore("@Composable\nfun RouteDetailEntryRoute(")
        val routeDetailEntry =
            entryRouteSource
                .substringAfter("fun RouteDetailEntryRoute(")
                .substringBefore("@Composable\nprivate fun RouteSettingViewModelFactory")

        assertTrue(
            "Route setting should intercept Android physical back and run the configured map-home callback.",
            routeSettingEntry.contains("BackHandler {") &&
                routeSettingEntry.contains("onNavigateBack()"),
        )
        assertTrue(
            "Route detail should also use the injected back callback for Android physical back.",
            routeDetailEntry.contains("BackHandler {") &&
                routeDetailEntry.contains("onNavigateBack()"),
        )
    }

    @Test
    fun `route loading replaces the map so search transitions do not flicker kakao tiles`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val screenSection =
            source
                .substringAfter("fun RouteSettingScreen(")
                .substringBefore("if (isDuribalConfirmDialogVisible)")
        val loadingScreen =
            source
                .substringAfter("private fun RouteLoadingScreen(")
                .substringBefore("@Composable\nprivate fun RouteUnsupportedAreaScreen")

        assertTrue(
            "Route search loading should render before the map and hide the floating start CTA.",
            screenSection.indexOf("RouteLoadingScreen(") in 0 until screenSection.indexOf("RouteMapStage(") &&
                screenSection.contains("if (!showsRouteLoadingScreen && !showsRouteUnsupportedAreaScreen && !showsRouteFailureScreen)") &&
                source.contains("isLoading && optionCards.isEmpty()"),
        )
        assertTrue(
            "The loading replacement should be a stable non-map surface with progress and route loading copy.",
            source.contains("private fun RouteSettingLoadingState(") &&
                loadingScreen.contains("RouteSettingLoadingState(") &&
                loadingScreen.contains("route_setting_summary_loading_title") &&
                loadingScreen.contains("route_setting_summary_loading_description") &&
                loadingScreen.contains("MaterialTheme.colorScheme.background"),
        )
        assertTrue(
            "Route loading should match the shared bookmark loading style without card chrome or custom spinner sizing.",
            source.contains("LiveRegionMode.Polite") &&
                source.contains("MaterialTheme.typography.titleMedium") &&
                source.contains("MaterialTheme.typography.bodyMedium") &&
                !loadingScreen.contains("headlineSmall") &&
                !loadingScreen.contains("Modifier.size(44.dp)") &&
                !loadingScreen.contains("strokeWidth = 4.dp"),
        )
    }

    @Test
    fun `unsupported area replaces route map and opens place selection instead of Duribal fallback`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val screenSection =
            source
                .substringAfter("fun RouteSettingScreen(")
                .substringBefore("if (isDuribalConfirmDialogVisible)")
        val unsupportedScreen =
            source
                .substringAfter("private fun RouteUnsupportedAreaScreen(")
                .substringBefore("@Composable\nprivate fun RouteFailureScreen")

        assertTrue(
            "Unsupported area should render before route failure and hide the floating start CTA.",
            screenSection.indexOf("RouteUnsupportedAreaScreen(") in 0 until screenSection.indexOf("RouteFailureScreen(") &&
                screenSection.contains("showsRouteUnsupportedAreaScreen") &&
                screenSection.contains("if (!showsRouteLoadingScreen && !showsRouteUnsupportedAreaScreen && !showsRouteFailureScreen)"),
        )
        assertTrue(
            "Unsupported area CTA should send the user back to waypoint selection and avoid Duribal copy.",
            unsupportedScreen.contains("route_setting_unsupported_area_title") &&
                unsupportedScreen.contains("route_setting_unsupported_area_description") &&
                unsupportedScreen.contains("route_setting_unsupported_area_action") &&
                screenSection.contains("uiState.unsupportedArea?.editingTarget ?: RouteEditingTarget.DESTINATION") &&
                !unsupportedScreen.contains("route_setting_duribal_call_prompt_call"),
        )
        assertTrue(
            "Unsupported area empty state should use the same centered illustration, text, and inline CTA structure as the route failure state.",
            unsupportedScreen.contains(".fillMaxSize()") &&
                unsupportedScreen.contains(".padding(horizontal = EumSpacing.large, vertical = EumSpacing.xLarge)") &&
                unsupportedScreen.contains("verticalArrangement = Arrangement.Center") &&
                unsupportedScreen.contains("RouteFailureScreenIllustrationSize") &&
                !unsupportedScreen.contains(".align(Alignment.BottomCenter)") &&
                !unsupportedScreen.contains(".navigationBarsPadding()"),
        )
    }

    @Test
    fun `transit route selection renders a scrollable white result pane instead of the map`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val screenSection =
            source
                .substringAfter("fun RouteSettingScreen(")
                .substringBefore("if (isDuribalConfirmDialogVisible)")
        val transitPaneSection =
            source
                .substringAfter("private fun RouteSettingTransitResultPane(")
                .substringBefore("@Composable\nprivate fun RouteOptionSection")

        assertTrue(
            "Transit route selection should branch to a dedicated full-height result pane instead of placing a map above a bottom sheet.",
            screenSection.contains("else if (uiState.selectedTravelMode == RouteTravelMode.TRANSIT)") &&
                screenSection.contains("RouteSettingTransitResultPane(") &&
                screenSection.contains("RouteMapStage("),
        )
        assertFalse(
            "The transit result pane should not render the map backdrop.",
            transitPaneSection.contains("RouteMapBackdrop(") || transitPaneSection.contains("RouteMapControls("),
        )
        assertTrue(
            "Transit options should scroll above the fixed start button on a white surface.",
            transitPaneSection.contains("color = Color.White") &&
                transitPaneSection.contains(".nestedScroll(pullRefreshConnection)") &&
                transitPaneSection.contains(".verticalScroll(scrollState)") &&
                transitPaneSection.contains("val bottomBarOverlayClearance = routeSettingBottomBarOverlayClearance()") &&
                transitPaneSection.contains("bottom = bottomBarOverlayClearance"),
        )
        assertTrue(
            "Transit result pane should support pull-to-refresh without adding a floating CTA-adjacent refresh button.",
            transitPaneSection.contains("rememberTransitRoutePullRefreshConnection(") &&
                source.contains("onRefresh = {") &&
                source.contains("RouteSettingUiAction.RouteRefreshClicked"),
        )
    }

    @Test
    fun `route search header policy keeps compact kakao geometry and shared tokens`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val headerPolicy = routeSearchHeaderPolicy(showModeTabs = true)
        val modeTabs = routeSearchHeaderModeTabPolicies()
        val headerModeTabSection =
            source
                .substringAfter("private fun RouteSearchHeaderModeTab(")
                .substringBefore("@Composable\nprivate fun RouteSearchHeaderWaypointLine")

        assertEquals(R.string.route_setting_screen_title, headerPolicy.titleResId)
        assertEquals(EumPrimary600, headerPolicy.containerColor)
        assertEquals(Color(0xFFF5F8FF), headerPolicy.summaryContainerColor)
        assertEquals(2, modeTabs.size)
        assertEquals(RouteTravelMode.TRANSIT, modeTabs[0].mode)
        assertEquals(RouteTravelMode.WALK, modeTabs[1].mode)
        assertEquals(R.string.route_setting_travel_mode_transit, modeTabs[0].labelResId)
        assertEquals(R.string.route_setting_travel_mode_walk, modeTabs[1].labelResId)
        assertEquals(22.dp, modeTabs[0].iconSize)
        assertEquals(26.dp, modeTabs[1].iconSize)
        assertEquals(92.dp, headerPolicy.summaryMinHeight)
        assertEquals(12.dp, headerPolicy.summaryToModeTabsGap)
        assertEquals(36.dp, headerPolicy.modeTabHeight)
        assertEquals(10.dp, headerPolicy.modeTabCornerRadius)
        assertEquals(12.dp, headerPolicy.roleLabelGap)
        assertTrue(headerPolicy.summaryUsesFullWidth)
        assertTrue(headerPolicy.usesCompactWaypointTitle)
        assertFalse(headerPolicy.showsCloseAction)
        assertFalse(headerPolicy.showsMoreAction)
        assertTrue(
            "Mode tabs should keep the walk/transit icon and label centered as one group.",
            headerModeTabSection.contains("Icon(") &&
                headerModeTabSection.contains("modifier = Modifier.size(iconSize)") &&
                headerModeTabSection.contains("horizontalArrangement = Arrangement.spacedBy(EumSpacing.xSmall, Alignment.CenterHorizontally)"),
        )
    }

    @Test
    fun `route search header policy uses plain bottom padding when tabs are hidden`() {
        val headerPolicy = routeSearchHeaderPolicy(showModeTabs = false)

        assertEquals(8.dp, headerPolicy.contentBottomPadding)
    }

    @Test
    fun `route setting walk preview reserves space for full width bottom CTA`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val mapStageSection =
            source
                .substringAfter("private fun RouteMapStage(")
                .substringBefore("@Composable\nprivate fun RouteMapMessageCard")
        val ctaSection =
            source
                .substringAfter("private fun RouteSettingBottomBar(")
                .substringBefore("@Composable\nprivate fun RouteSettingCtaContent")
        val carouselSection =
            source
                .substringAfter("private fun RouteWalkPreviewCarousel(")
                .substringBefore("@OptIn(ExperimentalLayoutApi::class)")
        val cardSection =
            source
                .substringAfter("private fun RouteWalkPreviewSummaryCard(")
                .substringBefore("@Composable\nprivate fun RouteMapControls")

        assertTrue(
            "Walk preview cards should sit above the fixed bottom CTA instead of being covered by it.",
            mapStageSection.contains("bottom = walkPreviewBottomPadding"),
        )
        assertTrue(
            "Walk preview cards should use the same navigation-bar inset basis as the shared bottom CTA.",
            mapStageSection.contains("WindowInsets.navigationBars.getBottom(this).toDp()") &&
                mapStageSection.contains("val walkPreviewBottomPadding = routeWalkPreviewCarouselBottomPadding(navigationBarBottomInset)") &&
                mapStageSection.contains("val walkPreviewMapBottomClearance = routeWalkPreviewMapBottomClearance(navigationBarBottomInset)") &&
                source.contains("RouteWalkPreviewToStartButtonGap = 22.dp"),
        )
        assertTrue(
            "Walk preview map should stop above the route cards so origin and destination labels are not projected behind bottom controls.",
            mapStageSection.contains(".padding(bottom = walkPreviewMapBottomClearance)") &&
                mapStageSection.contains(".clipToBounds()") &&
                source.contains("RouteWalkPreviewMapToCardGap = 16.dp"),
        )
        assertTrue(
            "Walk preview layers should keep the cards and bottom CTA above the map backdrop.",
            mapStageSection.contains(".zIndex(RouteMapBackdropZIndex)") &&
                mapStageSection.contains(".zIndex(RouteWalkPreviewCarouselZIndex)") &&
                source.contains(".zIndex(RouteSettingBottomBarZIndex)"),
        )
        assertTrue(
            "Walk preview cards should use a compact fixed minimum height.",
            cardSection.contains(".heightIn(min = RouteWalkPreviewCardMinHeight)") &&
                source.contains("RouteWalkPreviewCardMinHeight = 116.dp") &&
                !source.contains("RouteWalkPreviewCarouselBottomPadding = RouteSettingBottomBarButtonHeight + RouteSettingBottomBarBottomGap + 12.dp"),
        )
        assertTrue(
            "Walk preview should not compose the floating recenter action, so the circular button cannot sit behind route cards.",
                mapStageSection.contains("val showsWalkPreviewCards = uiState.selectedTravelMode == RouteTravelMode.WALK && uiState.optionCards.isNotEmpty()") &&
                mapStageSection.contains("if (showsWalkPreviewCards)") &&
                mapStageSection.contains("RouteMapZoomControls(") &&
                mapStageSection.contains("RouteMapControls(") &&
                !mapStageSection.contains("showActionButton") &&
                source.contains("private fun RouteMapZoomControls("),
        )
        assertTrue(
            "Walk preview should show exactly two equal-width option cards with symmetric horizontal padding.",
            carouselSection.contains("optionCards.take(RouteWalkPreviewVisibleCardCount)") &&
                carouselSection.contains("Modifier.weight(1f)") &&
                !carouselSection.contains(".horizontalScroll(rememberScrollState())") &&
                !source.contains("RouteWalkPreviewCompactCardWidth"),
        )
        assertTrue(
            "Walk preview detail affordance should use the compact chevron, not the long arrow icon.",
            cardSection.contains("R.drawable.ic_route_card_chevron"),
        )
        assertTrue(
            "Route start CTA should match the reference full-width bottom button.",
            ctaSection.contains(".fillMaxWidth()") &&
                !ctaSection.contains(".width(RouteSettingBottomBarButtonWidth)"),
        )
        assertTrue(
            "Route start CTA should keep the requested 30dp bottom gap.",
            source.contains("RouteSettingBottomBarBottomGap = 30.dp"),
        )
        assertTrue(
            "Route start CTA should reserve the system navigation safe zone before the explicit 30dp app-bottom gap.",
            ctaSection.contains(".navigationBarsPadding()"),
        )
    }

    @Test
    fun `walk route map controls sit above the raised preview cards`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val mapStageSection =
            source
                .substringAfter("private fun RouteMapStage(")
                .substringBefore("@Composable\nprivate fun RouteMapMessageCard")

        assertEquals(102.dp, routeWalkPreviewCarouselBottomPadding(0.dp))
        assertEquals(150.dp, routeWalkPreviewCarouselBottomPadding(48.dp))
        assertEquals(234.dp, routeWalkPreviewMapBottomClearance(0.dp))
        assertEquals(282.dp, routeWalkPreviewMapBottomClearance(48.dp))
        assertEquals(288.dp, routeWalkMapControlsBottomPadding(0.dp))
        assertEquals(336.dp, routeWalkMapControlsBottomPadding(48.dp))
        assertTrue(
            "Map controls should use a bottom padding derived from the inset-aware preview card offset plus an extra gap.",
            mapStageSection.contains("bottom = mapControlsBottomPadding"),
        )
    }

    @Test
    fun `route search and detail map controls are wired to the overlay viewport controller`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val mapStageSection =
            source
                .substringAfter("private fun RouteMapStage(")
                .substringBefore("@Composable\nprivate fun RouteMapMessageCard")
        val detailScreenSection =
            source
                .substringAfter("fun RouteDetailScreen(")
                .substringBefore("@Composable\nprivate fun RouteDetailMapBottomSheet")
        val mapControlsSection =
            source
                .substringAfter("private fun RouteMapControls(")
                .substringBefore("@Composable\nprivate fun RouteSettingTransitResultPane")
        val routeMapBackdropSection =
            source
                .substringAfter("private fun RouteMapBackdrop(")
                .substringBefore("private fun List<RouteDetailPolylineUiState>.toRouteDetailPolylineOverlays")

        assertTrue(mapStageSection.contains("rememberMapOverlayViewportControlState()"))
        assertTrue(detailScreenSection.contains("rememberMapOverlayViewportControlState()"))
        assertTrue(source.contains("mapControlState.zoomIn()"))
        assertTrue(source.contains("mapControlState.zoomOut()"))
        assertTrue(source.contains("RouteSettingUiAction.CurrentLocationClicked"))
        assertTrue(source.contains("recenterToCurrentLocation("))
        assertTrue(source.contains("recenter()"))
        assertTrue(mapControlsSection.contains("onActionClick = onActionClick"))
        assertTrue(mapControlsSection.contains("onZoomInClick = onZoomInClick"))
        assertTrue(mapControlsSection.contains("onZoomOutClick = onZoomOutClick"))
        assertTrue(routeMapBackdropSection.contains("controlState = controlState"))
        assertTrue(routeMapBackdropSection.contains("originIsCurrentLocation = originIsCurrentLocation"))
        assertTrue(mapStageSection.contains("originIsCurrentLocation = uiState.originState == RouteOriginState.CURRENT_LOCATION_RESOLVED"))
        assertTrue(mapControlsSection.contains("iconRes = R.drawable.ic_route_start_navigation_button"))
        assertFalse(
            "Route preview should not draw a separate current-location marker that competes with the origin marker.",
            routeMapBackdropSection.contains("currentLocation = currentLocationCoordinate") ||
                mapStageSection.contains("currentLocationCoordinate = uiState.currentLocationCoordinate"),
        )
    }

    @Test
    fun `route preview labels use revised shortest copy without drawing duplicate current position marker`() {
        val viewModelSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingViewModel.kt")
                .readText()
        val overlaySource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapViewportOverlay.kt")
                .readText()

        assertTrue(viewModelSource.contains("OPTION_TITLE_SHORTEST = \"최단 경로\""))
        assertFalse(viewModelSource.contains("OPTION_TITLE_SHORTEST = \"최단거리\""))
        assertFalse(overlaySource.contains("overlayId = \"route-current-location\""))
        assertTrue(overlaySource.contains("originIsCurrentLocation: Boolean = false"))
        assertTrue(overlaySource.contains("MapViewportPolylineStyle.ROUTE_CONNECTOR"))
    }

    @Test
    fun `walk preview and route sheet cards use smaller metrics with shared accessibility labels`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val walkCardSection =
            source
                .substringAfter("private fun RouteWalkPreviewSummaryCard(")
                .substringBefore("@Composable\nprivate fun RouteMapControls")
        val badgeRowSection =
            source
                .substringAfter("private fun RouteWalkPreviewBadgeRow(")
                .substringBefore("@Composable\nprivate fun RouteMapControls")
        val compactCardSection =
            source
                .substringAfter("private fun RouteCompactOptionCard(")
                .substringBefore("@Composable\nprivate fun RouteSearchLoadingState")
        val accessibilityLabelSection =
            source
                .substringAfter("private fun RouteAccessibilityLabelChip(")
                .substringBefore("@Composable\nprivate fun RouteStateCard")

        assertTrue(
            "Walk preview metrics should step down slightly so the time and distance don't dominate the card.",
            walkCardSection.contains("style = MaterialTheme.typography.titleLarge") &&
                walkCardSection.contains("style = MaterialTheme.typography.bodySmall") &&
                walkCardSection.contains("style = MaterialTheme.typography.labelMedium") &&
                walkCardSection.contains("start = RouteWalkPreviewCardStartPadding") &&
                walkCardSection.contains("end = RouteWalkPreviewTopRowEndPadding"),
        )
        assertTrue(
            "Walk preview accessibility labels should sit in their own full-width row so two chips can stretch to the same right margin as the left side.",
            walkCardSection.contains("RouteWalkPreviewBadgeRow(") &&
                walkCardSection.contains("Modifier.padding(") &&
                walkCardSection.contains("end = RouteWalkPreviewBadgeHorizontalPadding"),
        )
        assertTrue(
            "Walk preview detail affordance should shrink so the right-side chevron stops consuming too much width.",
            source.contains("RouteWalkPreviewChevronTouchTargetSize = 36.dp") &&
                source.contains("RouteWalkPreviewChevronIconSize = 18.dp"),
        )
        assertTrue(
            "Compact route sheet cards should also reduce the time and distance typography.",
            compactCardSection.contains("MaterialTheme.typography.titleSmall") &&
                compactCardSection.contains("style = MaterialTheme.typography.labelMedium"),
        )
        assertTrue(
            "Walk preview labels should stay in a fixed two-column row instead of wrapping unpredictably.",
            source.contains("private const val MAX_COMPACT_ACCESSIBILITY_BADGE_COUNT = 2") &&
                badgeRowSection.contains("Row(") &&
                badgeRowSection.contains("RouteAccessibilityLabelChip(") &&
                badgeRowSection.contains("modifier = Modifier.weight(1f)"),
        )
        assertTrue(
            "Accessibility labels should reuse the facility-detail style tone while filling each weighted slot with slightly wider text room.",
            accessibilityLabelSection.contains("shape = RoundedCornerShape(RouteAccessibilityLabelCornerRadius)") &&
                accessibilityLabelSection.contains("style = MaterialTheme.typography.labelSmall") &&
                accessibilityLabelSection.contains(".fillMaxWidth()") &&
                source.contains("RouteAccessibilityLabelHorizontalPadding = 4.dp") &&
                source.contains("RouteAccessibilityLabelMinHeight = 24.dp"),
        )
        assertTrue(
            "Both walk preview and compact route cards should render the shared accessibility label chip helper.",
            badgeRowSection.contains("RouteAccessibilityLabelChip(") &&
                compactCardSection.contains("RouteAccessibilityLabelChip(") &&
                compactCardSection.contains("modifier = Modifier.weight(1f)"),
        )
    }

    @Test
    fun `walk preview badge row uses two weighted slots without experimental flow layout`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val badgeRowSection =
            source
                .substringAfter("private fun RouteWalkPreviewBadgeRow(")
                .substringBefore("@Composable\nprivate fun RouteMapControls")

        assertTrue(
            "RouteWalkPreviewBadgeRow should reserve a second slot with weight so two accessibility labels stay on one line.",
            badgeRowSection.contains("Spacer(modifier = Modifier.weight(1f))"),
        )
        assertFalse(
            "RouteWalkPreviewBadgeRow should no longer rely on FlowRow now that the card forces a fixed two-chip layout.",
            source.contains("@OptIn(ExperimentalLayoutApi::class)\n@Composable\nprivate fun RouteWalkPreviewBadgeRow(") ||
                badgeRowSection.contains("FlowRow("),
        )
    }

    @Test
    fun `walk preview cards navigate to detail only from the chevron action`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val carouselSection =
            source
                .substringAfter("private fun RouteWalkPreviewCarousel(")
                .substringBefore("@OptIn(ExperimentalLayoutApi::class)\n@Composable\nprivate fun RouteWalkPreviewSummaryCard")

        assertTrue(
            "Tapping a walk preview card body should only select that route, while the chevron keeps the detail navigation.",
            carouselSection.contains("onClick = { onOptionClick(card.routeOption) }") &&
                carouselSection.contains("onDetailClick = { onOptionDetailClick(card.routeOption) }"),
        )
        assertFalse(
            "The walk preview card body should no longer redirect to route detail when the card is already selected.",
            carouselSection.contains("if (card.isSelected)"),
        )
    }

    @Test
    fun `route option cards keep bordered selection states with softer section radius`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val walkCardSection =
            source
                .substringAfter("private fun RouteWalkPreviewSummaryCard(")
                .substringBefore("@Composable\nprivate fun RouteMapControls")
        val compactCardSection =
            source
                .substringAfter("private fun RouteCompactOptionCard(")
                .substringBefore("@Composable\nprivate fun RouteSearchLoadingState")

        assertTrue(
            "Walk preview cards should use the shared 16dp section radius instead of the previous extra-round preview shape.",
            walkCardSection.contains("shape = RoundedCornerShape(RouteSectionCardCornerRadius)"),
        )
        assertTrue(
            "Compact option cards should also adopt the softer section radius while preserving the selection border treatment.",
            compactCardSection.contains("shape = RoundedCornerShape(RouteSectionCardCornerRadius)") &&
                compactCardSection.contains("border = BorderStroke(1.dp, borderColor)"),
        )
    }

    @Test
    fun `transit route result pane reserves room for the shared bottom CTA`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val routeSheetSection =
            source
                .substringAfter("private fun RouteSettingTransitResultPane(")
                .substringBefore("@Composable\nprivate fun RouteOptionSection")

        assertTrue(
            "Transit bottom sheet content should reserve clearance so low-floor reservations are not hidden behind the shared CTA.",
            routeSheetSection.contains("val bottomBarOverlayClearance = routeSettingBottomBarOverlayClearance()") &&
                routeSheetSection.contains("bottom = bottomBarOverlayClearance"),
        )
        assertTrue(
            "Transit CTA clearance should be derived from the actual CTA height plus the live navigation bar inset, not a fixed extra dp token.",
            source.contains("private fun routeSettingBottomBarOverlayClearance(") &&
                source.contains("WindowInsets.navigationBars.getBottom(density).toDp()") &&
                !source.contains("RouteSettingBottomBarOverlayClearance = RouteSettingBottomBarButtonHeight + RouteSettingBottomBarBottomGap + EumSpacing.medium"),
        )
    }

    @Test
    fun `low floor reservation button stays disabled after successful request`() {
        val screenSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val entrySource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingEntryRoute.kt")
                .readText()
        val lowFloorRowSection =
            screenSource
                .substringAfter("private fun LowFloorReservationRow(")
                .substringBefore("@Composable\nprivate fun LowFloorReservationConfirmDialog")

        assertTrue(
            "Successful low-floor reservations should be remembered by stable vehicle key and passed down to the screen.",
            entrySource.contains("completedLowFloorReservationKeys") &&
                entrySource.contains("reservation.stableReservationKey()") &&
                entrySource.contains("completedLowFloorReservationKeys + completedKey") &&
                screenSource.contains("completedReservationKeys = completedLowFloorReservationKeys"),
        )
        assertTrue(
            "Completed low-floor reservation rows should disable the action and show a completed label.",
            lowFloorRowSection.contains("enabled = !isCompleted") &&
                lowFloorRowSection.contains("route_setting_low_floor_reservation_completed"),
        )
    }

    @Test
    fun `route failure replaces map with a clean duribal fallback screen`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val screenSection =
            source
                .substringAfter("fun RouteSettingScreen(")
                .substringBefore("if (isDuribalConfirmDialogVisible)")
        val headerModeTabSection =
            source
                .substringAfter("private fun RouteSearchHeaderKakao(")
                .substringBefore("@Composable\nprivate fun RouteSearchHeaderModeTab")
        val routeOptionSection =
            source
                .substringAfter("private fun RouteOptionSection(")
                .substringBefore("@Composable\nprivate fun RouteLoadingScreen")
        val failureScreen =
            source
                .substringAfter("private fun RouteFailureScreen(")
                .substringBefore("@Composable\nprivate fun RouteFailureFallbackState")

        assertTrue(
            "No-route recovery should keep only the transit tab active so users land on the Duribal fallback path.",
            headerModeTabSection.contains("enabled = routeSearchHeaderModeTabEnabled(") &&
                source.contains("private fun routeSearchHeaderModeTabEnabled(") &&
                source.contains("mode == RouteTravelMode.TRANSIT") &&
                source.contains("state.showsDuribalCallAction"),
        )
        assertTrue(
            "When the backend reports no route, the transit tab should surface a dedicated Duribal card with call and cancel actions.",
            routeOptionSection.contains("RouteDuribalCallPromptCard(") &&
                source.contains("private fun RouteDuribalCallPromptCard(") &&
                source.contains("route_setting_duribal_call_prompt_title") &&
                source.contains("route_setting_duribal_call_prompt_call") &&
                source.contains("route_setting_duribal_call_prompt_cancel"),
        )
        assertTrue(
            "When route search fails, the screen should render a full failure state instead of the map and keep the start CTA hidden.",
            screenSection.indexOf("RouteFailureScreen(") in 0 until screenSection.indexOf("RouteMapStage(") &&
                screenSection.contains("if (!showsRouteLoadingScreen && !showsRouteUnsupportedAreaScreen && !showsRouteFailureScreen)") &&
                source.contains("private fun RouteFailureScreen("),
        )
        assertTrue(
            "The full failure screen should show image and text, with Duribal limited to transit failures.",
            failureScreen.contains("RouteNoRouteIllustration(") &&
                failureScreen.contains("route_setting_no_route_result_title") &&
                failureScreen.contains("route_setting_no_route_result_description") &&
                failureScreen.contains("selectedTravelMode == RouteTravelMode.TRANSIT") &&
                failureScreen.contains("route_setting_duribal_call_prompt_call") &&
                failureScreen.contains("Button(") &&
                failureScreen.contains("onClick = onDuribalCallClick"),
        )
        assertTrue(
            "Transit loading should stay in the empty result area instead of stacking a full-screen overlay.",
            !screenSection.contains("RouteSearchFullscreenLoadingOverlay(") &&
                !source.contains("private fun RouteSearchFullscreenLoadingOverlay(") &&
                source.contains("private fun RouteSearchLoadingState()") &&
                routeOptionSection.contains(
                    "uiState.isLoading && uiState.optionCards.isEmpty() -> RouteSearchLoadingState()",
                ),
        )
    }

    @Test
    fun `collapsed guide rails reserve viewport end padding so the destination can snap to the top card`() {
        val routeDetailSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val navigationRailSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/component/NavigationSegmentRail.kt")
                .readText()
        val routeDetailRailSection =
            routeDetailSource
                .substringAfter("private fun RouteDetailIconRail(")
                .substringBefore("@Composable\nprivate fun RouteDetailCollapsedRailScrollTopAction")

        assertTrue(
            "Route detail collapsed rail must render through the anchored scrubber so the arrival icon can become the focused top-card step.",
            routeDetailRailSection.contains("RouteStepScrubberRail(") &&
                routeDetailRailSection.contains("trailingActionHeight = RouteDetailCollapsedRailItemSize"),
        )
        assertTrue(
            "Navigation collapsed rail must use the same anchored scrubber so the final destination segment can be focused.",
            navigationRailSource.contains("RouteStepScrubberRail(") &&
                navigationRailSource.contains("trailingActionHeight = NavigationSegmentRailTopActionHeight"),
        )
    }

    @Test
    fun `transit option timeline uses fixed radius transport icons and route option colors`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val segmentBarSection =
            source
                .substringAfter("private fun RouteTransitSegmentRatioBar(")
                .substringBefore("@Composable\nprivate fun RouteTransitOptionSummary")

        assertTrue(
            "Transit segment timeline should clip all four corners to the 5dp token.",
            segmentBarSection.contains(".clip(RoundedCornerShape(RouteTransitSegmentTimelineRadius))"),
        )
        assertTrue(
            "Transit segment timeline should render a start icon for bus and subway segments.",
            segmentBarSection.contains("RouteTransitSegmentStartIcon(segment = segment)"),
        )
        assertTrue(
            "Recommended route color should use the confirmed blue token.",
            source.contains("RouteSafeBlue = Color(0xFF006BE0)"),
        )
        assertTrue(
            "Minimum-walk/fast route color should use the confirmed orange token.",
            source.contains("RouteFastOrange = Color(0xFFF9AB4D)"),
        )
        assertTrue(
            "Transit option route bar should split walking and public transit path colors.",
            source.contains("RouteTransitWalkGray = Color(0xFF99B5D1)") &&
                source.contains("RouteTransitNavy = Color(0xFF005391)"),
        )
        assertTrue(
            "Subway line colors should include the confirmed Busan line tokens.",
            source.contains("RouteSubwayLine1 = Color(0xFFFF7F00)") &&
                source.contains("RouteSubwayBusanGimhae = Color(0xFF8200FF)"),
        )
        assertTrue(
            "Transit bus tags should use Phase 3 color, radius, and border tokens.",
            source.contains("RouteTransitTagLowFloorColor = Color(0xFF2671A8)") &&
                source.contains("RouteTransitTagNormalColor = Color(0xFF4B9EDC)") &&
                source.contains("RouteTransitTagBorderColor = Color(0xFFD9D9D9)") &&
                source.contains("RouteTransitTagArrivalColor = Color(0xFFF94D4D)") &&
                source.contains("RouteTransitTagCornerRadius = 10.dp"),
        )
        assertTrue(
            "Transit option cards should suppress stop origin-destination text and expose horizontal tag scrolling.",
            source.contains("horizontalScroll(rememberScrollState())") &&
                !source
                    .substringAfter("private fun RouteTransitOptionSummary(")
                    .substringBefore("@Composable\nprivate fun RouteTransitOptionChip")
                    .contains("stopLabel?.let"),
        )
    }

    @Test
    fun `route detail map keeps direction arrows visible before a guidance marker is focused`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val routeMapBackdropSection =
            source
                .substringAfter("private fun RouteMapBackdrop(")
                .substringBefore("private fun List<RouteDetailPolylineUiState>.toRouteDetailPolylineOverlays")

        assertTrue(
            "Route detail polylines should keep direction arrows visible even before a guidance marker is selected.",
            routeMapBackdropSection.contains("val shouldShowRouteDirectionArrows =") &&
                routeMapBackdropSection.contains("routePolylineOverlays.isNotEmpty() || hasFocusedGuidanceMarker") &&
                routeMapBackdropSection.contains("showDirectionArrows = true") &&
                routeMapBackdropSection.contains("showDetailedRouteOverlay = shouldShowRouteDirectionArrows"),
        )
    }

    @Test
    fun `route waypoint pin marker uses shared png asset`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val asset = File("src/main/res/drawable-nodpi/ic_route_waypoint_pin.png")

        assertTrue(
            "Waypoint pin marker should render the shared PNG asset instead of a reconstructed Canvas path.",
            source.contains("painterResource(id = R.drawable.ic_route_waypoint_pin)"),
        )
        assertTrue("Waypoint pin asset should exist in drawable-nodpi.", asset.exists())
    }

    @Test
    fun `route waypoint swap icon uses separate up and down arrows`() {
        val segments = routeWaypointSwapIconSegments(Size(width = 18f, height = 24f))

        assertEquals(6, segments.size)

        val upperStem = segments[0]
        val upperLeftHead = segments[1]
        val upperRightHead = segments[2]
        val lowerStem = segments[3]
        val lowerLeftHead = segments[4]
        val lowerRightHead = segments[5]

        assertTrue(upperStem.end.y < upperStem.start.y)
        assertEquals(upperStem.end, upperLeftHead.start)
        assertEquals(upperStem.end, upperRightHead.start)
        assertTrue(upperLeftHead.end.x < upperStem.end.x)
        assertTrue(upperRightHead.end.x > upperStem.end.x)
        assertTrue(upperLeftHead.end.y > upperStem.end.y)
        assertTrue(upperRightHead.end.y > upperStem.end.y)

        assertTrue(lowerStem.end.y > lowerStem.start.y)
        assertEquals(lowerStem.end, lowerLeftHead.start)
        assertEquals(lowerStem.end, lowerRightHead.start)
        assertTrue(lowerLeftHead.end.x < lowerStem.end.x)
        assertTrue(lowerRightHead.end.x > lowerStem.end.x)
        assertTrue(lowerLeftHead.end.y < lowerStem.end.y)
        assertTrue(lowerRightHead.end.y < lowerStem.end.y)

        assertTrue(upperStem.start.x < lowerStem.start.x)
        assertTrue(lowerStem.start.x - upperStem.start.x >= 9f)
        assertTrue(lowerStem.start.y - upperStem.start.y >= 6f)
    }

    @Test
    fun `route waypoint supporting text aligns under the place name column and uses lighter meta color`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()

        assertTrue(
            "Waypoint rows should measure the widest label instead of relying on a hardcoded width.",
            source.contains("rememberTextMeasurer()"),
        )
        assertTrue(
            "Waypoint rows should baseline-align the status label with the place name.",
            source.contains("alignByBaseline()"),
        )
        assertTrue(
            "Waypoint address text should start under the place name column using the measured label width.",
            source.contains("Spacer(modifier = Modifier.width(labelWidth))"),
        )
        assertTrue(
            "Waypoint supporting text should use the lighter neutral meta color from the design convention.",
            source.contains("RouteWaypointSupportingTextColor = Color(0xFF6B7280)"),
        )
    }

    @Test
    fun `route surfaces use documented corner and shadow tokens`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()

        assertTrue(
            "Route cards should use the section container radius token.",
            source.contains("shape = RoundedCornerShape(RouteSectionCardCornerRadius)"),
        )
        assertTrue(
            "Route CTA should use the standard button corner radius token.",
            source.contains("shape = RoundedCornerShape(RouteButtonCornerRadius)"),
        )
        assertTrue(
            "Route map should delegate floating control shape to the shared map floating controls component.",
            source.contains("EumMapFloatingControls("),
        )
        assertTrue(
            "Route map floating controls should keep the documented floating control elevation token available to the shared component.",
            source.contains("RouteFloatingControlElevation = 6.dp"),
        )
        assertTrue(
            "The sticky bottom CTA bar should remain flat and avoid a separating shadow seam.",
            source.contains("shadowElevation = 0.dp"),
        )
        assertTrue(
            "Route badges should use the compact chip radius token instead of pill rounding.",
            source.contains("shape = RoundedCornerShape(RouteCompactChipCornerRadius)"),
        )
    }

    @Test
    fun `route start cta uses provided png icon without tint override`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val asset = File("src/main/res/drawable/ic_route_start_navigation_button.png")

        assertTrue(
            "Route start CTA should use the provided PNG icon asset for the button.",
            source.contains("R.drawable.ic_route_start_navigation_button"),
        )
        assertTrue(
            "Route start CTA should tint the button icon white on the primary background.",
            source.contains("tint = routeSettingCtaContentColor(enabled = enabled)") &&
                source.contains("MaterialTheme.colorScheme.onPrimary"),
        )
        assertTrue("Route start CTA PNG icon should exist in drawable.", asset.exists())
    }

    @Test
    fun `route option prefix stays plain text while labels keep compact chip styling`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val prefixSection =
            source
                .substringAfter("private fun RouteOptionPrefixBadge(")
                .substringBefore("@Composable\nprivate fun RouteOptionDetailArrowButton")

        assertFalse(
            "The recommended prefix text should not render as a filled chip background.",
            prefixSection.contains("Surface("),
        )
        assertTrue(
            "The recommended prefix should stay as colored text only.",
            prefixSection.contains("Text("),
        )
    }

    @Test
    fun `route detail summary removes headline copy and border for flat metrics layout`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val summarySection =
            source
                .substringAfter("private fun RouteDetailSummaryCard(")
                .substringBefore("@Composable\nprivate fun RouteDetailMetricRow")

        assertFalse(
            "Detail summary should not keep the route risk badge when the header copy is removed.",
            summarySection.contains("RouteRiskChip("),
        )
        assertFalse(
            "Detail summary should not render the destination headline copy under the title divider.",
            summarySection.contains("route_setting_detail_summary_destination"),
        )
        assertFalse(
            "Detail summary should not render the destination supporting address in the flat layout.",
            summarySection.contains("selectedRoute.destination.supportingText"),
        )
        assertFalse(
            "Detail summary should not keep a card border in the refactored flat layout.",
            summarySection.contains("border = BorderStroke"),
        )
    }

    @Test
    fun `route detail feature header uses requested warm background and larger title`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val featureHeaderSection =
            source
                .substringAfter("private fun RouteDetailBadgeHeader(")
                .substringBefore("@Composable\nprivate fun RouteDetailScrollTopAction")

        assertTrue(
            "Opened route detail side rail should use the requested warm feature-card background.",
            source.contains("RouteDetailFeatureCardContainerColor = Color(0xFFE9ECF3)") &&
                featureHeaderSection.contains(".background(RouteDetailFeatureCardContainerColor)"),
        )
        assertTrue(
            "The route feature title should be four pixels larger than the previous label-large treatment.",
            source.contains("RouteDetailFeatureTitleFontSize = 18.sp") &&
                featureHeaderSection.contains("style = MaterialTheme.typography.labelLarge.copy(fontSize = RouteDetailFeatureTitleFontSize)"),
        )
    }

    @Test
    fun `route setting hides walk fallback notice and debug card from the screen`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()

        assertFalse(
            "The route screen should not render the walk-first fallback notice card while transit recovery is in progress.",
            source.contains("route_setting_fallback_notice_title"),
        )
        assertFalse(
            "The route screen should not expose the route debug card in the FE UI.",
            source.contains("RouteDebugStateCard(debugMessage = uiState.loadDebugMessage)"),
        )
    }

    @Test
    fun `route preview map does not show selected route badge overlay`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val previewMapSection =
            source
                .substringAfter("if (previewMap.coordinates.isNotEmpty()) {")
                .substringBefore("RouteMapControls(")

        assertFalse(
            "Preview map should not render the selected route status badge over the map.",
            previewMapSection.contains("RouteMapStatusBadge("),
        )
    }

    @Test
    fun `route detail screen uses map backed side panel and attached guide rows`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val guideSidePanelSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/GuideSidePanel.kt")
                .readText()
        val detailScreenSection =
            source
                .substringAfter("fun RouteDetailScreen(")
                .substringBefore("@Composable\nprivate fun RouteDetailMapBottomSheet")
        val stepsSection =
            source
                .substringAfter("private fun RouteDetailStepsSection(")
                .substringBefore("@Composable\nprivate fun RouteDetailOriginHeader")
        val sidePanelCollapsedRailSection =
            source
                .substringAfter("RouteDetailIconRail(")
                .substringBefore("@Composable\nprivate fun RouteDetailTimelinePanelContent")

        assertTrue(
            "Detail screen should keep the map visible behind the detail side panel.",
            detailScreenSection.contains("RouteMapBackdrop("),
        )
        assertTrue(
            "Detail screen should render both walk and transit details through the left side panel.",
            detailScreenSection.contains("RouteDetailSidePanel("),
        )
        assertFalse(
            "Detail screen should not branch transit routes into a bottom sheet.",
            detailScreenSection.contains("RouteDetailMapBottomSheet("),
        )
        assertFalse(
            "Detail screen should remove the origin-destination edit header.",
            detailScreenSection.contains("RouteSearchHeaderKakao("),
        )
        assertTrue(
            "Detail side panel should delegate horizontal swipe collapse and expansion to the shared guide shell.",
            source.contains("GuideSidePanelShell(") &&
                guideSidePanelSource.contains("detectHorizontalDragGestures(") &&
                guideSidePanelSource.contains("GuideSidePanelSwipeThresholdPx"),
        )
        assertTrue(
            "Collapsed detail state should render an icon-only rail.",
            source.contains("private fun RouteDetailIconRail(") &&
                source.contains("RouteDetailCollapsedRailWidth"),
        )
        assertTrue(
            "Collapsed detail side rail should start directly below the top guide card instead of being covered by it.",
            detailScreenSection.contains("if (!isDetailSidePanelExpanded) Modifier.padding(top = RouteDetailCollapsedGuideCardMinHeight) else Modifier"),
        )
        assertFalse(
            "Collapsed detail rail must not add status bar padding because the top guide card already owns the top inset.",
            sidePanelCollapsedRailSection.contains(".statusBarsPadding()"),
        )
        assertTrue(
            "Collapsed detail rail should drive the focused top card and map marker from the anchored scrubber step.",
            source.contains("onTopVisibleStepChanged = { index ->") &&
                source.contains("RouteStepScrubberRail("),
        )
        assertTrue(
            "Collapsed detail rail should expose an up action below the destination item that focuses the origin.",
            source.contains("RouteDetailCollapsedRailScrollTopAction(") &&
                source.contains("onStepClick(0)"),
        )
        assertTrue(
            "Detail bottom sheet should expose a segment ratio/timeline bar.",
            source.contains("private fun RouteDetailTimelineBar("),
        )
        assertTrue(
            "Transit detail sheet should reserve arrival info and refresh affordances.",
            source.contains("private fun RouteDetailTransitActionRow("),
        )
        assertFalse(
            "Guide rows should start from the departure row instead of dropping the first step.",
            stepsSection.contains("steps.drop(1)"),
        )
        assertFalse(
            "The refactored guide list should not keep a separate origin header card.",
            stepsSection.contains("RouteDetailOriginHeader("),
        )
        assertFalse(
            "Guide rows should no longer nest each step inside its own card.",
            stepsSection.contains("RouteDetailStepCard(step = step)"),
        )
        assertTrue(
            "The first attached row should render the departure cell explicitly.",
            stepsSection.contains("RouteDetailOriginStepRow("),
        )
        assertTrue(
            "Subsequent rows should render as attached guide rows.",
            stepsSection.contains("RouteDetailStepRow("),
        )
        assertTrue(
            "Attached guide rows should be visually separated with dividers, not nested cards.",
            stepsSection.contains("HorizontalDivider("),
        )
    }

    @Test
    fun `route detail departure and arrival reuse labeled rail pins with detail icon sizing`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val leadingIconSection =
            source
                .substringAfter("private fun RouteDetailStepLeadingIcon(")
                .substringBefore("@Composable\nprivate fun RouteScreenTopBar")

        assertTrue(
            "Route detail start should reuse the in-use labeled origin pin asset.",
            source.contains("RouteDetailStepKind.START -> R.drawable.ic_navigation_rail_origin_pin"),
        )
        assertTrue(
            "Route detail arrival should reuse the in-use labeled destination pin asset.",
            source.contains("RouteDetailStepKind.ARRIVAL -> R.drawable.ic_navigation_rail_destination_pin"),
        )
        assertTrue(
            "Transit detail rows should reuse the shared bus icon from the route map surface.",
            source.contains("RouteDetailStepKind.BUS -> R.drawable.ic_place_bus"),
        )
        assertTrue(
            "Transit detail rows should map subway boarding to the dedicated subway asset.",
            source.contains("RouteDetailStepKind.SUBWAY -> R.drawable.ic_route_subway"),
        )
        assertTrue(
            "Fallback detail rows should not masquerade as straight movement.",
            source.contains("RouteDetailStepKind.FALLBACK -> R.drawable.ic_status_help_circle"),
        )
        assertFalse(
            "Fallback detail rows should not receive directional icon treatment after using a neutral status icon.",
            source
                .substringAfter("private fun RouteDetailStepKind.usesDirectionalStepIcon(): Boolean =")
                .substringBefore("private fun RouteDetailStepKind.usesLabeledWaypointPinIcon(): Boolean =")
                .contains("RouteDetailStepKind.FALLBACK"),
        )
        assertTrue(
            "Route detail leading icons should branch explicitly for the labeled rail pin treatment.",
            leadingIconSection.contains("if (usesLabeledWaypointPinIcon)"),
        )
        assertTrue(
            "Labeled rail pins in route detail should keep the shared 22x24 aspect ratio while matching the other detail icon height.",
            leadingIconSection.contains("modifier = Modifier.size(width = RouteDetailWaypointPinWidth, height = RouteDetailWaypointPinHeight)"),
        )
        assertTrue(
            "Route detail leading glyph icons should route through a dedicated size helper so transit icons can be slightly reduced.",
            leadingIconSection.contains("modifier = Modifier.size(kind.leadingIconSize())"),
        )
        assertTrue(
            "Route detail should preserve a dedicated helper for labeled start/destination pin steps.",
            source.contains("private fun RouteDetailStepKind.usesLabeledWaypointPinIcon(): Boolean ="),
        )
        assertTrue(
            "Route detail should keep a dedicated helper for smaller transit icon sizing.",
            source.contains("private fun RouteDetailStepKind.leadingIconSize(): Dp ="),
        )
        assertTrue(
            "Route detail should document the reduced transit icon size token.",
            source.contains("private val RouteDetailTransitLeadingIconSize = 20.dp"),
        )
        assertTrue(
            "Route detail should document the waypoint pin width token.",
            source.contains("private val RouteDetailWaypointPinWidth = 22.dp"),
        )
        assertTrue(
            "Route detail should document the waypoint pin height token.",
            source.contains("private val RouteDetailWaypointPinHeight = 24.dp"),
        )
    }

    @Test
    fun `route detail side panel keeps arrival visible above the fixed start button`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val timelinePanelSection =
            source
                .substringAfter("private fun RouteDetailTimelinePanelContent(")
                .substringBefore("@Composable\nprivate fun RouteDetailIconRail")
        val collapsedCardSection =
            source
                .substringAfter("private fun RouteDetailCollapsedGuideCard(")
                .substringBefore("@Composable\nprivate fun RouteDetailTransitGuideCardContent")

        assertTrue(
            "The open detail side panel must reserve bottom clearance so the arrival row is not hidden by the fixed CTA.",
            timelinePanelSection.contains("val bottomBarOverlayClearance = routeSettingBottomBarOverlayClearance()") &&
                timelinePanelSection.contains("contentPadding = PaddingValues(bottom = bottomBarOverlayClearance)") &&
                !source.contains("RouteDetailSidePanelBottomActionSpace"),
        )
        assertTrue(
            "The open detail side panel should draw a divider directly below the arrival row before the scroll-top action.",
            timelinePanelSection.contains("item(key = \"route-detail-arrival-divider\")") &&
                timelinePanelSection.contains("HorizontalDivider(color = RouteDetailGuideDividerColor)"),
        )
        assertTrue(
            "The bottom of the guide list should expose an in-panel scroll-to-top affordance like the reference.",
            timelinePanelSection.contains("RouteDetailScrollTopAction(") &&
                timelinePanelSection.contains("listState.animateScrollToItem(0)") &&
                !timelinePanelSection.contains("if (false)"),
        )
        assertTrue(
            "The open detail side panel should virtualize long guide lists to avoid blocking route-detail entry.",
            timelinePanelSection.contains("LazyColumn(") &&
                timelinePanelSection.contains("rememberLazyListState()") &&
                timelinePanelSection.contains("itemsIndexed("),
        )
        assertFalse(
            "The open detail side panel should not eagerly compose every guide row through a verticalScroll Column.",
            timelinePanelSection.contains(".verticalScroll(listState)") ||
                timelinePanelSection.contains(".verticalScroll(scrollState)"),
        )
        assertTrue(
            "Collapsed top guide card should animate vertical changes when rail scrolling changes the focused step.",
            collapsedCardSection.contains("AnimatedContent(") &&
                collapsedCardSection.contains("slideInVertically") &&
                collapsedCardSection.contains("slideOutVertically"),
        )
        assertTrue(
            "Collapsed route-detail guide card should match navigation card scale while using white/neutral detail styling.",
            collapsedCardSection.contains("color = RouteDetailCollapsedGuideCardContainerColor") &&
                collapsedCardSection.contains("contentColor = RouteDetailCollapsedGuideCardContentColor") &&
                collapsedCardSection.contains("secondaryContentColor = RouteDetailCollapsedGuideCardContentColor") &&
                collapsedCardSection.contains("iconTint = RouteDetailCollapsedGuideCardContentColor") &&
                source.contains("RouteDetailCollapsedGuideCardContainerColor = Color.White") &&
                source.contains("RouteDetailCollapsedGuideCardContentColor = Color(0xFF333333)") &&
                source.contains("RouteDetailCollapsedGuideCardMinHeight = 92.dp") &&
                source.contains("RouteDetailCollapsedGuideIconSize = 44.dp"),
        )
        assertTrue(
            "Collapsed route-detail guide card should draw only a thin bottom divider so it does not double with the rail.",
            collapsedCardSection.contains("HorizontalDivider(") &&
                collapsedCardSection.contains("modifier = Modifier.padding(start = RouteDetailCollapsedRailWidth)") &&
                collapsedCardSection.contains("thickness = RouteDetailCollapsedGuideCardBottomStrokeWidth") &&
                source.contains("RouteDetailCollapsedGuideCardBottomStrokeWidth = 0.5.dp") &&
                source.contains("RouteDetailCollapsedGuideCardBottomStrokeColor = Color(0xFFD9D9D9)"),
        )
        assertTrue(
            "Collapsed route-detail start and arrival card icons should use the colored rail pin assets without tinting.",
            collapsedCardSection.contains("RouteDetailCollapsedGuideCardIcon(") &&
                source.contains("targetStep?.kind ?: RouteDetailStepKind.START") &&
                source.contains("Image(") &&
                source.contains("routeDetailStepIconRes(kind)") &&
                source.contains("modifier = Modifier.size(RouteDetailCollapsedGuideIconSize)"),
        )
    }

    @Test
    fun `route detail defers guidance marker creation until a step is focused`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val detailScreenSection =
            source
                .substringAfter("fun RouteDetailScreen(")
                .substringBefore("@Composable\nprivate fun RouteDetailMapBottomSheet")

        assertTrue(
            "Route detail entry should not build every guidance marker before one is visible on the map.",
            detailScreenSection.contains("if (focusedDetailStepIndex == null)") &&
                detailScreenSection.contains("emptyList()") &&
                detailScreenSection.contains("selectedRoute.detailGuidanceMarkers(focusedDetailStepIndex)"),
        )
    }

    @Test
    fun `route detail guide rows use white Kakao style list rows and neutral icons`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val guideSidePanelSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/GuideSidePanel.kt")
                .readText()
        val originRowSection =
            source
                .substringAfter("private fun RouteDetailOriginStepRow(")
                .substringBefore("@Composable\nprivate fun RouteDetailFallbackRow")
        val stepRowSection =
            source
                .substringAfter("private fun RouteDetailStepRow(")
                .substringBefore("@Composable\nprivate fun RouteDetailArrivalInfoChip")

        assertTrue(
            "Origin and guide rows should render on white surfaces instead of warning-colored cards.",
            originRowSection.contains("GuideSidePanelStepRow(") &&
                stepRowSection.contains("GuideSidePanelStepRow(") &&
                guideSidePanelSource.contains("expandedContainerColor = MaterialTheme.colorScheme.surface"),
        )
        assertFalse(
            "Open detail rows should not show the raw route step sequence numbers from the backend.",
            originRowSection.contains("text = step.indexLabel") ||
                stepRowSection.contains("text = step.indexLabel"),
        )
        assertTrue(
            "Warning badges in the guide list should be neutral chips, not red/pink panels.",
            stepRowSection.contains("RouteDetailGuideBadgeContainerColor") &&
                stepRowSection.contains("RouteDetailGuideBadgeContentColor"),
        )
        assertTrue(
            "Open detail side-panel direction icons should use the neutral reference tint.",
            guideSidePanelSource.contains("tint = contentColor") &&
                !guideSidePanelSource.contains("Color.Unspecified") &&
                stepRowSection.contains("leadingContentColor = RouteDetailGuideIconColor"),
        )
    }

    @Test
    fun `route detail side panel removes trailing map affordances and footer clutter`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val timelinePanelSection =
            source
                .substringAfter("private fun RouteDetailTimelinePanelContent(")
                .substringBefore("@Composable\nprivate fun RouteDetailScrollTopAction")
        val originRowSection =
            source
                .substringAfter("private fun RouteDetailOriginStepRow(")
                .substringBefore("@Composable\nprivate fun RouteDetailFallbackRow")
        val stepRowSection =
            source
                .substringAfter("private fun RouteDetailStepRow(")
                .substringBefore("@Composable\nprivate fun RouteDetailArrivalInfoChip")

        assertTrue(
            "Origin row should use the same compact shared row contract as other guidance rows.",
            originRowSection.contains("title = origin.name") &&
                originRowSection.contains("description = origin.supportingText ?: step.description") &&
                originRowSection.contains("minHeight = RouteDetailGuideRowMinHeight") &&
                !originRowSection.contains("supportingContent ="),
        )
        assertFalse(
            "Guidance rows should not render the right-side map marker affordance in the open side panel.",
            stepRowSection.contains("RouteDetailRowAccessoryIcon("),
        )
        assertFalse(
            "Arrival row should not expose the detail-info chip inside the side panel.",
            stepRowSection.contains("RouteDetailArrivalInfoChip("),
        )
        assertFalse(
            "Side panel footer should not render information-correction suggestions below the scroll-top button.",
            timelinePanelSection.contains("RouteDetailPanelFeedbackFooter("),
        )
        assertTrue(
            "Scroll-top affordance should sit close to the final row instead of leaving an oversized gap.",
            source.contains("RouteDetailPanelBottomActionTopPadding = 24.dp") &&
                source.contains("RouteDetailScrollTopActionVerticalPadding = 0.dp"),
        )
    }

    @Test
    fun `route detail transit top and rail cards share the same content renderer`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val collapsedCardSection =
            source
                .substringAfter("private fun RouteDetailCollapsedGuideCard(")
                .substringBefore("@Composable\nprivate fun RouteDetailTransitGuideCardContent")
        val stepRowSection =
            source
                .substringAfter("private fun RouteDetailStepRow(")
                .substringBefore("@Composable\nprivate fun RouteDetailArrivalInfoChip")

        assertTrue(collapsedCardSection.contains("RouteDetailTransitGuideCardContent("))
        assertTrue(stepRowSection.contains("RouteDetailTransitGuideCardContent("))
    }

    @Test
    fun `route detail rail delegates step inspection to the anchored scrubber`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val scrubberSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/RouteStepScrubberRail.kt")
                .readText()
        val railSection =
            source
                .substringAfter("private fun RouteDetailIconRail(")
                .substringBefore("@Composable\nprivate fun RouteDetailCollapsedRailScrollTopAction")

        assertTrue(
            "Route detail rail should use the shared scrubber so scroll position is the focused step source of truth.",
            railSection.contains("RouteStepScrubberRail(") &&
                railSection.contains("onFocusedItemChanged = onTopVisibleStepChanged") &&
                railSection.contains("onItemClick = onStepClick") &&
                railSection.contains("dividerColor = RouteDetailGuideDividerColor"),
        )
        assertTrue(
            "The shared scrubber should promote the nearest anchored step while dragging.",
            scrubberSource.contains("snapshotFlow") &&
                scrubberSource.contains("resolveRouteStepScrubberIndex(") &&
                scrubberSource.contains("currentOnFocusedItemChanged(index)"),
        )
        assertTrue(
            "The shared scrubber must ignore its first offset observation so route-detail click focus is not reset back to the first item.",
            scrubberSource.contains("var hasObservedInitialPosition = false") &&
                scrubberSource.contains("!hasObservedInitialPosition") &&
                scrubberSource.contains("index != currentResolvedFocusedIndex"),
        )
        assertFalse(
            "Route detail rail items must not force a fixed outer height because hidden top-card items need to collapse out of the rail.",
            railSection.contains("modifier = Modifier.size(RouteDetailCollapsedRailItemSize)"),
        )
        assertTrue(
            "Route detail scroll-to-top should promote the first guide card.",
            railSection.contains("RouteDetailCollapsedRailScrollTopAction(") &&
                railSection.contains("onStepClick(0)"),
        )
    }

    @Test
    fun `route detail rail does not render a separate focused selection block`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val railItemSection =
            source
                .substringAfter("private fun RouteDetailIconRail(")
                .substringBefore("@Composable\nprivate fun RouteDetailCollapsedRailScrollTopAction")
        val scrubberSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/RouteStepScrubberRail.kt")
                .readText()

        assertFalse(
            "The collapsed route-detail rail should behave as a timeline axis; current step is conveyed by the top card and map, not another selected block.",
            scrubberSource.contains("isFocused =") ||
                scrubberSource.contains("isSelected =") ||
                railItemSection.contains("isSelected = isFocused"),
        )
        assertTrue(
            "Route-detail rail should still keep the promoted item's accessibility state in sync with the top card.",
            railItemSection.contains("stateDescription = if (focusedStepIndex == index) \"focused guide step\" else \"guide step\""),
        )
    }

    @Test
    fun `route detail rail disables fling inertia during step inspection`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val railSection =
            source
                .substringAfter("private fun RouteDetailIconRail(")
                .substringBefore("@Composable\nprivate fun RouteDetailCollapsedRailScrollTopAction")
        val scrubberSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/RouteStepScrubberRail.kt")
                .readText()

        assertTrue(
            "Route detail rail should keep inspection tied to anchored drag position instead of LazyColumn fling inertia.",
            railSection.contains("RouteStepScrubberRail(") &&
                scrubberSource.contains("velocityThreshold = { Float.POSITIVE_INFINITY }"),
        )
    }

    @Test
    fun `route detail separates pure walking line color from transit walking line color`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()

        assertTrue(source.contains("travelMode = selectedRoute?.routeOption.toRouteDetailTravelMode()"))
        assertTrue(source.contains("private fun RouteDetailPolylineKind.toMapViewportOverlayTone(travelMode: RouteTravelMode)"))
        assertTrue(
            source.contains("MapViewportOverlayTone.TRANSIT_WALK") &&
                source.contains("MapViewportOverlayTone.NAVIGATION_WALK"),
        )
    }

    @Test
    fun `route CTAs use shared screen horizontal insets across search and detail`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val bottomBarSection =
            source
                .substringAfter("private fun RouteSettingBottomBar(")
                .substringBefore("@Composable\nprivate fun RouteSettingCtaContent")

        assertTrue(
            "Route start CTA should match the navigation exit CTA width while preserving the shared inset constant.",
            source.contains("RouteSettingBottomBarHorizontalPadding = EumSpacing.medium + 50.dp") &&
                bottomBarSection.contains("start = RouteSettingBottomBarHorizontalPadding") &&
                bottomBarSection.contains("end = RouteSettingBottomBarHorizontalPadding"),
        )
    }

    @Test
    fun `route setting does not render a floating route refresh action above the start cta`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val screenSection =
            source
                .substringAfter("fun RouteSettingScreen(")
                .substringBefore("@Composable\nfun RouteDetailScreen")
        val bottomBarSection =
            source
                .substringAfter("private fun RouteSettingBottomBar(")
                .substringBefore("@Composable\nprivate fun RouteSettingCtaContent")

        assertFalse("Route selection should not pass a floating refresh action into the bottom bar.", screenSection.contains("showRefreshAction ="))
        assertTrue("Transit pull-to-refresh may dispatch route refresh from the result pane.", screenSection.contains("RouteSettingUiAction.RouteRefreshClicked"))
        assertFalse("The bottom bar should not render a floating refresh button over the start CTA.", bottomBarSection.contains("RouteRefreshFloatingButton("))
        assertFalse("The floating refresh composable should be removed from the route selection screen.", source.contains("private fun RouteRefreshFloatingButton("))
    }

    @Test
    fun `approved hazard marker viewer mounts at the screen root instead of inside route map stage`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val routeSettingSection =
            source
                .substringAfter("fun RouteSettingScreen(")
                .substringBefore("@Composable\nfun RouteDetailScreen")
        val routeMapStageSection =
            source
                .substringAfter("private fun RouteMapStage(")
                .substringBefore("@Composable\nprivate fun RouteMapMessageCard")
        val routeDetailSection =
            source
                .substringAfter("fun RouteDetailScreen(")
                .substringBefore("@Composable\nprivate fun RouteDetailTopBar")

        assertFalse(
            "The route preview map stage should not own the hazard bottom sheet because that limits the full-screen viewer to the map subtree.",
            routeMapStageSection.contains("ApprovedHazardMarkerBottomSheet("),
        )
        assertTrue(
            "The route preview screen should mount the hazard viewer after the shared bottom bar so it can cover controls and cards.",
            routeSettingSection.indexOf("RouteSettingBottomBar(") < routeSettingSection.indexOf("ApprovedHazardMarkerBottomSheet("),
        )
        assertTrue(
            "The route preview hazard sheet should attach to the safe area instead of floating above the route start CTA.",
            routeSettingSection.contains("bottomInset = routeSettingHazardSheetBottomInset()"),
        )
        assertTrue(
            "The route detail screen should also keep the hazard viewer as the last sibling above side panels and the start CTA.",
            routeDetailSection.indexOf("RouteSettingBottomBar(") < routeDetailSection.indexOf("ApprovedHazardMarkerBottomSheet("),
        )
        assertTrue(
            "The route detail hazard sheet should use the same safe-area inset as the preview screen.",
            routeDetailSection.contains("bottomInset = routeSettingHazardSheetBottomInset()"),
        )
    }

    @Test
    fun `route start CTA centers icon and label as a single group`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val ctaSection =
            source
                .substringAfter("private fun RouteSettingCtaContent(")
                .substringBefore("@Composable\nprivate fun RouteMapBackdrop")

        assertTrue(
            "Route start CTA should wrap the icon and label in a single row so the combined content stays centered inside the full-width button.",
            ctaSection.contains("Row(") &&
                ctaSection.contains(".fillMaxSize()") &&
                ctaSection.contains(
                    "horizontalArrangement = Arrangement.spacedBy(EumSpacing.xSmall, Alignment.CenterHorizontally)",
                ) &&
                ctaSection.contains("verticalAlignment = Alignment.CenterVertically"),
        )
        assertTrue(
            "Route start CTA should keep the navigation-start icon and labelLarge text together in that centered content row.",
            ctaSection.contains("painter = painterResource(id = R.drawable.ic_route_start_navigation_button)") &&
                ctaSection.contains(
                    "Text(\n                    text = buttonLabel,\n                    style = MaterialTheme.typography.labelLarge,",
                ),
        )
    }

    @Test
    fun `route setting suppresses ripple only on taps that open other route screens`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val waypointSection =
            source
                .substringAfter("private fun RouteWaypointRow(")
                .substringBefore("@Composable\nprivate fun RouteOriginStatusText")
        val detailArrowSection =
            source
                .substringAfter("private fun RouteOptionDetailArrowButton(")
                .substringBefore("/*")

        assertTrue(
            "Waypoint rows should suppress ripple because they open the search screen for origin/destination editing.",
            waypointSection.contains("indication = null"),
        )
        assertTrue(
            "Waypoint rows should keep a dedicated interaction source when ripple is suppressed.",
            waypointSection.contains("MutableInteractionSource()"),
        )
        assertTrue(
            "Route option detail arrows should suppress ripple because they open the route detail screen.",
            detailArrowSection.contains("indication = null"),
        )
        assertTrue(
            "Route option detail arrows should keep a dedicated interaction source when ripple is suppressed.",
            detailArrowSection.contains("MutableInteractionSource()"),
        )
    }

    @Test
    fun `resolved current location origin promotes current location label and moves address below`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt")
                .readText()
        val waypointCardSection =
            source
                .substringAfter("private fun RouteWaypointCard(")
                .substringBefore("@Composable\nprivate fun RouteWaypointLinkedMarkers")
        val originPresentationSection =
            source
                .substringAfter("private fun resolveOriginWaypointPresentation(")
                .substringBefore("@Composable\nprivate fun RouteWaypointLinkedMarkers")

        assertTrue(
            "Origin rows should resolve a dedicated current-location presentation before rendering the waypoint text.",
            waypointCardSection.contains("val originPresentation = resolveOriginWaypointPresentation("),
        )
        assertTrue(
            "The origin row should render the resolved current-location title instead of the raw place name.",
            waypointCardSection.contains("name = originPresentation.name"),
        )
        assertTrue(
            "The origin row should render the resolved supporting address under the current-location title.",
            waypointCardSection.contains("supportingText = originPresentation.supportingText"),
        )
        assertTrue(
            "The origin row should be able to suppress the blue current-location status once it becomes the primary title.",
            waypointCardSection.contains("status = originPresentation.status"),
        )
        assertTrue(
            "Resolved current-location rows should detect the current-location status label explicitly.",
            originPresentationSection.contains("status?.label == CURRENT_LOCATION_WAYPOINT_NAME"),
        )
        assertTrue(
            "Resolved current-location rows should keep the address on the supporting line and fall back to the original name when needed.",
            originPresentationSection.contains("supportingText?.takeIf(String::isNotBlank) ?: name"),
        )
    }
}
