package com.ssafy.e102.eumgil.feature.navigation

import androidx.compose.ui.unit.dp
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.feature.navigation.component.NavigationSegmentRailTransitIconSize
import com.ssafy.e102.eumgil.feature.navigation.component.railIconSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.io.File
import org.junit.Test

class NavigationScreenPolicyTest {
    @Test
    fun `screen policy hides rail and return action when no segment sync data exists`() {
        val policy = navigationScreenPolicy(NavigationUiState())

        assertFalse(policy.showSegmentRail)
        assertFalse(policy.showReturnToActiveAction)
        assertFalse(policy.showFocusedSegmentCard)
    }

    @Test
    fun `screen policy shows rail and return action during inspect mode`() {
        val uiState =
            NavigationUiState(
                segmentSync =
                    NavigationSegmentSyncUiState(
                        activeSegmentIndex = 0,
                        focusedSegmentIndex = 1,
                        isInspectingSegments = true,
                        railItems =
                            listOf(
                                NavigationSegmentRailItemUiState(
                                    index = 0,
                                    sequence = 1,
                                    instruction = "Walk straight",
                                    distanceLabel = "120m",
                                    riskLabel = "Low",
                                    isActive = true,
                                ),
                                NavigationSegmentRailItemUiState(
                                    index = 1,
                                    sequence = 2,
                                    instruction = "Cross the street",
                                    distanceLabel = "80m",
                                    riskLabel = "Medium",
                                    isFocused = true,
                                ),
                            ),
                    ),
                    focusedSegmentCard =
                        NavigationFocusedSegmentCardUiState(
                            sequenceLabel = "2 / 2",
                            instruction = "Cross the street",
                            heroTitle = "횡단보도 건너기",
                            heroDescription = "Cross the street",
                            distanceLabel = "80m",
                            riskLabel = "Medium",
                            supportingText = "Focused segment details",
                        ),
            )

        val policy = navigationScreenPolicy(uiState)

        assertTrue(policy.showSegmentRail)
        assertTrue(policy.showReturnToActiveAction)
        assertFalse(policy.showFocusedSegmentCard)
    }

    @Test
    fun `screen policy does not show an empty rail only for the removed detail action`() {
        val policy =
            navigationScreenPolicy(
                NavigationUiState(
                    screenState = NavigationScreenState.Empty,
                    selectedRouteOption = RouteOption.SAFE,
                ),
            )

        assertFalse(policy.showSegmentRail)
    }

    @Test
    fun `hero layout policy uses compact current guidance card and removes divider`() {
        val policy = navigationHeroLayoutPolicy(800.dp)

        assertEquals(92.dp, policy.minHeight)
        assertEquals(128.dp, policy.maxHeight)
        assertEquals(44.dp, policy.directionIconSize)
        assertFalse(policy.showBottomDivider)
    }

    @Test
    fun `navigation bottom bar floats above system nav bar without top divider`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationScreen.kt")
                .readText()
        val bottomBarSection =
            source
                .substringAfter("private fun NavigationBottomBar(")
                .substringBefore("@Composable\nprivate fun NavigationExitConfirmDialog")
        val chromePolicy = navigationBottomBarChromePolicy()

        assertTrue(chromePolicy.usesNavigationBarPadding)
        assertFalse(chromePolicy.showTopDivider)
        assertTrue(bottomBarSection.contains(".navigationBarsPadding()"))
        assertTrue(bottomBarSection.contains("if (chromePolicy.showTopDivider)"))
    }

    @Test
    fun `hero layout policy keeps compact minimum max height on compact screens`() {
        val policy = navigationHeroLayoutPolicy(480.dp)

        assertEquals(108.dp, policy.maxHeight)
    }

    @Test
    fun `transit icons use slightly smaller hero and rail sizes than turn guidance icons`() {
        val navigationScreenSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationScreen.kt")
                .readText()
        val railSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/component/NavigationSegmentRail.kt")
                .readText()
        val guideSidePanelSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/GuideSidePanel.kt")
                .readText()
        val scrubberSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/RouteStepScrubberRail.kt")
                .readText()

        assertTrue(
            "Navigation hero icon sizing should route transit actions through a dedicated helper.",
            navigationScreenSource.contains("modifier = Modifier.size(guidanceAction.heroIconSize(defaultSize = iconSize))"),
        )
        assertTrue(
            "Navigation hero should document the slightly reduced transit icon token.",
            navigationScreenSource.contains("NavigationHeroTransitDirectionIconSize = 40.dp"),
        )
        assertTrue(
            "Navigation segment rail should delegate collapsed icon sizing through the shared scrubber item.",
            railSource.contains("RouteStepScrubberRail(") &&
                scrubberSource.contains("GuideCollapsedRailItem(") &&
                guideSidePanelSource.contains("action.collapsedIconSize()"),
        )
        assertTrue(
            "Shared collapsed rail icons should document the slightly reduced transit icon token.",
            guideSidePanelSource.contains("private val GuideCollapsedRailTransitIconSize = 22.dp"),
        )
        assertEquals(NavigationHeroTransitDirectionIconSize, NavigationGuidanceAction.BUS.heroIconSize(defaultSize = 44.dp))
        assertEquals(NavigationHeroTransitDirectionIconSize, NavigationGuidanceAction.SUBWAY.heroIconSize(defaultSize = 44.dp))
        assertEquals(44.dp, NavigationGuidanceAction.STRAIGHT.heroIconSize(defaultSize = 44.dp))
        assertEquals(NavigationSegmentRailTransitIconSize, NavigationGuidanceAction.BUS.railIconSize())
        assertTrue(NavigationGuidanceAction.STRAIGHT.railIconSize() > NavigationGuidanceAction.BUS.railIconSize())
    }

    @Test
    fun `navigation screen provides expandable side panel over map for walk and transit guidance`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationScreen.kt")
                .readText()
        val railSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/component/NavigationSegmentRail.kt")
                .readText()
        val scrubberSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/RouteStepScrubberRail.kt")
                .readText()
        val sidePanelPolicy = navigationSidePanelPolicy()

        assertTrue(
            "NAV-01 should keep the map as the base layer while the left rail or panel overlays it.",
            source.contains("NavigationMapStage(") &&
                source.contains("NavigationGuideSidePanel(") &&
                source.contains("NavigationSegmentRail("),
        )
        assertTrue(
            "The side panel should support horizontal swipe collapse.",
            source.contains("GuideSidePanelShell("),
        )
        assertTrue(
            "Rows should keep the panel open and reuse SegmentTapped so every rail interaction can preview that step.",
            source.contains("NavigationUiAction.SegmentTapped(index = index)"),
        )
        assertEquals(NavigationSidePanelSwipeAxis.Horizontal, sidePanelPolicy.swipeAxis)
        assertEquals(80f, sidePanelPolicy.swipeThresholdPx, 0f)
        assertFalse(sidePanelPolicy.collapseOnSegmentTap)
        assertTrue(
            "Transit guidance actions should use the same side panel row path as walk guidance.",
            source.contains("GuideSidePanelStepRow(") &&
                source.contains("uiState.segmentSync.railItems.forEach"),
        )
        assertTrue(
            "Collapsed rail scrubber should separate icon taps from drag focus so explicit taps drive map focus.",
            source.contains("onTopVisibleSegmentChanged = { index ->") &&
                railSource.contains("RouteStepScrubberRail(") &&
                railSource.contains("onItemClick = onSegmentTapped") &&
                scrubberSource.contains("snapshotFlow") &&
                scrubberSource.contains("anchoredDraggable(") &&
                railSource.contains("onTopVisibleSegmentChanged"),
        )
        assertFalse(
            "Expanded side panel rows should not keep the radio-like current-location button.",
            source
                .substringAfter("private fun NavigationSidePanelRow(")
                .substringBefore("private fun navigationRouteSummary")
                .contains("ic_map_current_location"),
        )
        assertFalse(sidePanelPolicy.showsProgressHeader)
    }

    @Test
    fun `navigation map controls and expanded rail top action are wired`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationScreen.kt")
                .readText()
        val mapStageSection =
            source
                .substringAfter("private fun NavigationMapStage(")
                .substringBefore("@Composable\nprivate fun NavigationMapBackdrop")
        val mapControlsSection =
            source
                .substringAfter("private fun NavigationMapControls(")
                .substringBefore("@Composable\nprivate fun NavigationBottomBar")
        val expandedPanelSection =
            source
                .substringAfter("private fun NavigationExpandedSidePanel(")
                .substringBefore("@Composable\nprivate fun NavigationSidePanelRow")

        assertTrue(mapStageSection.contains("rememberMapOverlayViewportControlState()"))
        assertTrue(mapStageSection.contains("mapControlState.zoomIn()"))
        assertTrue(mapStageSection.contains("mapControlState.zoomOut()"))
        assertTrue(mapStageSection.contains("mapControlState.recenter()"))
        assertTrue(mapControlsSection.contains("onActionClick = onActionClick"))
        assertTrue(mapControlsSection.contains("onZoomInClick = onZoomInClick"))
        assertTrue(mapControlsSection.contains("onZoomOutClick = onZoomOutClick"))
        assertTrue(expandedPanelSection.contains("scrollState.animateScrollTo(0)"))
        assertTrue(expandedPanelSection.contains("NavigationExpandedSidePanelScrollTopAction("))
    }

    @Test
    fun `navigation side panel and exit CTA match route detail reference shell`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationScreen.kt")
                .readText()
        val guideSidePanelSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/guidance/component/GuideSidePanel.kt")
                .readText()
        val expandedPanelSection =
            source
                .substringAfter("private fun NavigationExpandedSidePanel(")
                .substringBefore("@Composable\nprivate fun NavigationSidePanelRow")
        val rowSection =
            source
                .substringAfter("private fun NavigationSidePanelRow(")
                .substringBefore("private fun navigationRouteSummary")
        val bottomBarSection =
            source
                .substringAfter("private fun NavigationBottomBar(")
                .substringBefore("@Composable\nprivate fun NavigationMapStage")
        val sidePanelPolicy = navigationSidePanelPolicy()
        val bottomBarChromePolicy = navigationBottomBarChromePolicy()

        assertTrue(sidePanelPolicy.showsExpandedScrim)
        assertTrue(
            "Expanded panel rows should use the shared start/end and turn icon row as the collapsed rail.",
            expandedPanelSection.contains("isFirst = index == 0") &&
                expandedPanelSection.contains("isLast = index == uiState.segmentSync.railItems.lastIndex") &&
                rowSection.contains("GuideSidePanelStepRow(") &&
                guideSidePanelSource.contains("fun GuideSidePanelStepIcon("),
        )
        assertEquals(30.dp, bottomBarChromePolicy.bottomGap)
        assertTrue(bottomBarChromePolicy.usesNavigationBarPadding)
        assertTrue(
            "Exit CTA should share the full-width bottom placement and 30dp bottom gap used by route start.",
            bottomBarSection.contains(".fillMaxWidth()") &&
                bottomBarSection.contains("bottom = chromePolicy.bottomGap") &&
                source.contains("NavigationBottomBarBottomGap = 30.dp"),
        )
        assertFalse(
            "Exit CTA text should not be squeezed by the old fixed-width button.",
            source.contains(".width(NavigationBottomBarButtonWidth)"),
        )
        assertTrue(
            "Exit CTA should use the same 50dp narrower horizontal inset as the route start CTA.",
            source.contains("NavigationBottomBarHorizontalPadding = EumSpacing.medium + 50.dp") &&
                bottomBarSection.contains("start = NavigationBottomBarHorizontalPadding") &&
                bottomBarSection.contains("end = NavigationBottomBarHorizontalPadding"),
        )
    }

    @Test
    fun `exit CTA centers stop icon and label as a single group`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationScreen.kt")
                .readText()
        val bottomBarSection =
            source
                .substringAfter("private fun NavigationBottomBar(")
                .substringBefore("@Composable\nprivate fun NavigationExitConfirmDialog")

        assertTrue(
            "Exit CTA should wrap the stop icon and label in a single row so the combined content stays centered inside the full-width button.",
            bottomBarSection.contains(
                "Row(\n                        horizontalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),\n                        verticalAlignment = Alignment.CenterVertically,\n                    )",
            ),
        )
        assertTrue(
            "Exit CTA should keep the stop icon and centered titleMedium label together in that row.",
            bottomBarSection.contains("painter = painterResource(id = R.drawable.ic_control_stop)") &&
                bottomBarSection.contains(
                    "text = uiState.exitCta.label,\n                            style = MaterialTheme.typography.titleMedium,",
                ),
        )
    }

    @Test
    fun `hero content renders selected segment as sequence guidance and remaining time`() {
        val heroContent =
            navigationHeroContent(
                NavigationUiState(
                    stepCard =
                        NavigationStepCardUiState(
                            heroTitle = "직진 이동",
                            heroDescription = "Proceed straight on the current route",
                            instruction = "Proceed straight on the current route",
                            distanceLabel = "120m",
                            guidanceAction = NavigationGuidanceAction.STRAIGHT,
                        ),
                    focusedSegmentCard =
                        NavigationFocusedSegmentCardUiState(
                            sequenceLabel = "2 / 3",
                            instruction = "Cross the street and head toward the elevator",
                            heroTitle = "횡단보도 건너기",
                            heroDescription = "목적지까지 약 8분",
                            distanceLabel = "목적지까지 약 8분",
                            riskLabel = "Low",
                            supportingText = "Focused segment",
                            guidanceAction = NavigationGuidanceAction.CROSSWALK,
                        ),
                ),
            )

        assertEquals(NavigationGuidanceAction.CROSSWALK, heroContent.guidanceAction)
        assertEquals("횡단보도 건너기", heroContent.title)
        assertEquals("목적지까지 약 8분", heroContent.description)
        assertEquals("목적지까지 약 8분", heroContent.distanceLabel)
    }

    @Test
    fun `navigation hero animates selected card changes without replacing the fixed three line card`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationScreen.kt")
                .readText()
        val heroSection =
            source
                .substringAfter("private fun NavigationHeroCard(")
                .substringBefore("@Composable\nprivate fun NavigationHeroDirectionIcon")

        assertTrue(
            "Hero content changes should use vertical AnimatedContent so scroll-driven guidance changes are visible.",
            heroSection.contains("AnimatedContent(") &&
                heroSection.contains("slideInVertically") &&
                heroSection.contains("slideOutVertically"),
        )
        assertTrue(
            "Bus and subway boarding guidance should use the same transit detail content as the opened side rail card.",
            heroSection.contains("presentation.content.transitInfo?.let") &&
                heroSection.contains("NavigationTransitHeroContent("),
        )
        assertTrue(
            "The hero content model should prefer selected rail transit info and otherwise use the live active step transit info.",
            source.contains("transitInfo = focusedSegmentCard?.transitInfo ?: uiState.stepCard.transitInfo"),
        )
    }

    @Test
    fun `navigation screen returns selected side rail preview to live guidance`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationScreen.kt")
                .readText()

        assertTrue(
            "Selected rail preview should automatically return to current live guidance after thirty seconds without another rail interaction.",
            source.contains("LaunchedEffect(uiState.segmentSync.isInspectingSegments, inspectionInteractionVersion)") &&
                source.contains("delay(NavigationInspectAutoReturnMillis)") &&
                source.contains("NavigationInspectAutoReturnMillis = 30_000L") &&
                source.contains("NavigationUiAction.ReturnToActiveSegmentClicked"),
        )
        assertTrue(
            "Closing the side rail should immediately restore the current live guidance card.",
            source.contains("onExpandedChange = { expanded ->") &&
                source.contains("} else {\n                                    onAction(NavigationUiAction.ReturnToActiveSegmentClicked)\n                                }"),
        )
    }

    @Test
    fun `navigation transit rail cards reuse the same transit info model as the top card`() {
        val screenSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationScreen.kt")
                .readText()
        val viewModelSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationViewModel.kt")
                .readText()
        val contractSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationContract.kt")
                .readText()

        assertTrue(contractSource.contains("val transitInfo: NavigationTransitInfoUiState? = null"))
        assertTrue(viewModelSource.contains("transitInfo = selectedRoute.resolveFocusedSegmentTransitInfo(segment, transitPresentation)"))
        assertTrue(screenSource.contains("NavigationTransitSidePanelContent("))
        assertTrue(screenSource.contains("item.transitInfo"))
    }

    @Test
    fun `hero content falls back to the active step card when no focused segment is open`() {
        val heroContent =
            navigationHeroContent(
                NavigationUiState(
                    stepCard =
                        NavigationStepCardUiState(
                            heroTitle = "우회전",
                            heroDescription = "Turn right after the crosswalk",
                            instruction = "Turn right after the crosswalk",
                            distanceLabel = "240m",
                            guidanceAction = NavigationGuidanceAction.TURN_RIGHT,
                        ),
                ),
            )

        assertEquals(NavigationGuidanceAction.TURN_RIGHT, heroContent.guidanceAction)
        assertEquals("우회전", heroContent.title)
        assertEquals("Turn right after the crosswalk", heroContent.description)
        assertEquals("240m", heroContent.distanceLabel)
    }

    @Test
    fun `bottom bar layout policy starts divider after rail when segment rail is visible`() {
        val policy =
            navigationBottomBarLayoutPolicy(
                showSegmentRail = true,
                railWidth = 56.dp,
            )

        assertEquals(56.dp, policy.topDividerStartInset)
    }

    @Test
    fun `collapsed navigation rail uses the route detail rail width token`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationScreen.kt")
                .readText()

        assertTrue(source.contains("val railWidth = NavigationSegmentRailWidth"))
        assertTrue(source.contains("private val NavigationSegmentRailWidth = 58.dp"))
    }

    @Test
    fun `navigation rail opens and closes through the shared guide side panel shell`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationScreen.kt")
                .readText()
        val screenSection =
            source
                .substringAfter("if (screenPolicy.showSegmentRail) {")
                .substringBefore("NavigationBottomBar(")

        assertTrue(screenSection.contains("NavigationGuideSidePanel("))
        assertTrue(source.contains("GuideSidePanelShell("))
        assertTrue(source.contains("collapsedWidth = NavigationSegmentRailWidth"))
        assertTrue(source.contains("expandedWidthFraction = NavigationGuideSidePanelExpandedWidthFraction"))
        assertTrue(source.contains("private const val NavigationGuideSidePanelExpandedWidthFraction = 0.88f"))
    }

    @Test
    fun `approved hazard marker viewer is mounted after navigation bottom bar at screen root`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationScreen.kt")
                .readText()
        val screenSection =
            source
                .substringAfter("Box(\n        modifier = modifier.fillMaxSize(),\n    ) {")
                .substringBefore("if (uiState.isExitConfirmDialogVisible)")
        val mapStageSection =
            source
                .substringAfter("Box(\n                    modifier =\n                        Modifier\n                            .weight(1f)\n                            .fillMaxWidth(),\n                ) {")
                .substringBefore("            }\n        }\n\n        val bottomBarLayoutPolicy =")

        assertFalse(
            "The full-screen hazard image viewer should not be trapped inside the map-only Box.",
            mapStageSection.contains("ApprovedHazardMarkerBottomSheet("),
        )
        assertTrue(
            "The full-screen hazard image viewer should mount after the navigation bottom bar so it can cover the entire screen chrome.",
            screenSection.indexOf("NavigationBottomBar(") < screenSection.indexOf("ApprovedHazardMarkerBottomSheet("),
        )
        assertTrue(
            "The navigation hazard sheet should attach to the safe area instead of floating above the local CTA bar.",
            screenSection.contains("bottomInset = navigationHazardSheetBottomInset()"),
        )
    }

    @Test
    fun `focused navigation map inspection animates through camera target instead of fit projection jumps`() {
        val overlaySource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapViewportOverlay.kt")
                .readText()
        val viewModelSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationViewModel.kt")
                .readText()
        val navigationOverlaySection =
            overlaySource
                .substringAfter("internal fun createNavigationViewportOverlayState(")
                .substringBefore("private fun defaultMapViewportFallbackCamera")
        val mapOverlayBuilderSection =
            viewModelSource
                .substringAfter("private fun RouteNavigationRequest.toMapOverlayUiState(")
                .substringBefore("private fun RouteCandidate.toFallbackWalkingLegMapSegments")

        assertTrue(navigationOverlaySection.contains("fitToProjection = !useActiveCurrentFollow"))
        assertTrue(navigationOverlaySection.contains("toFocusedFallbackCamera()"))
        assertTrue(mapOverlayBuilderSection.contains("shouldAnimateCameraTransition = true"))
        assertFalse(mapOverlayBuilderSection.contains("IMMEDIATE_GUIDANCE_CAMERA_DISTANCE_THRESHOLD_METERS"))
    }

    @Test
    fun `bottom bar layout policy keeps full width divider when segment rail is hidden`() {
        val policy =
            navigationBottomBarLayoutPolicy(
                showSegmentRail = false,
                railWidth = 56.dp,
            )

        assertEquals(0.dp, policy.topDividerStartInset)
    }

    @Test
    fun `exit dialog policy follows navigation design tokens`() {
        val policy = navigationExitDialogPolicy()

        assertEquals(NavigationExitDialogShell.Dialog, policy.shell)
        assertEquals(360.dp, policy.maxWidth)
        assertEquals(EumRadius.scaleL, policy.containerCornerRadius)
        assertEquals(EumRadius.scaleM, policy.buttonCornerRadius)
        assertEquals(48.dp, policy.primaryButtonHeight)
        assertEquals(44.dp, policy.secondaryButtonHeight)
        assertEquals(10.dp, policy.shadowElevation)
    }

    @Test
    fun `exit dialog uses custom dialog shell instead of default alert dialog`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationScreen.kt")
                .readText()
        val dialogSection =
            source
                .substringAfter("private fun NavigationExitConfirmDialog(")
                .substringBefore("private fun DrawScope.drawNavigationMapGrid")

        assertTrue(dialogSection.contains("Dialog("))
        assertTrue(dialogSection.contains("navigationExitDialogPolicy()"))
        assertTrue(dialogSection.contains("BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f))"))
        assertTrue(dialogSection.contains("Arrangement.spacedBy(EumSpacing.large)"))
        assertTrue(dialogSection.contains("Arrangement.spacedBy(EumSpacing.medium)"))
        assertFalse(dialogSection.contains("NavigationExitDialogStopIcon("))
        assertFalse(dialogSection.contains("NavigationExitDialogConfirmIconSize"))
        assertFalse(dialogSection.contains(".background(MaterialTheme.colorScheme.error)"))
        assertFalse(dialogSection.contains("navigation_exit_confirm_dialog_supporting"))
        assertFalse(dialogSection.contains("navigation_exit_confirm_dialog_eyebrow"))
        assertFalse(dialogSection.contains("AlertDialog("))
    }

    @Test
    fun `screen scaffold disables default window insets to avoid header gap`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationScreen.kt")
                .readText()
        assertTrue(navigationUsesEmptyWindowInsets())
        assertTrue(source.contains(".statusBarsPadding()"))
    }
}
