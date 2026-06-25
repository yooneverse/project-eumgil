package com.ssafy.e102.eumgil.feature.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.designsystem.component.map.EumMapFloatingActionButtonState
import com.ssafy.e102.eumgil.core.designsystem.component.map.EumMapFloatingControls
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.data.repository.ReportRepository
import com.ssafy.e102.eumgil.feature.guidance.component.GuideSidePanelShell
import com.ssafy.e102.eumgil.feature.guidance.component.GuideSidePanelStepRow
import com.ssafy.e102.eumgil.feature.map.component.ApprovedHazardMarkerBottomSheet
import com.ssafy.e102.eumgil.feature.map.component.MapOverlayViewport
import com.ssafy.e102.eumgil.feature.map.component.MapOverlayViewportControlState
import com.ssafy.e102.eumgil.feature.map.component.MapViewportBounds
import com.ssafy.e102.eumgil.feature.map.component.MapViewportPointOverlay
import com.ssafy.e102.eumgil.feature.map.component.createNavigationViewportOverlayState
import com.ssafy.e102.eumgil.feature.map.component.rememberApprovedHazardMarkerOverlayState
import com.ssafy.e102.eumgil.feature.map.component.rememberMapOverlayViewportControlState
import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import com.ssafy.e102.eumgil.feature.navigation.component.NavigationSegmentRail
import com.ssafy.e102.eumgil.feature.route.RouteTransitOptionLabelUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NavigationScreen(
    uiState: NavigationUiState,
    reportRepository: ReportRepository,
    onAction: (NavigationUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hazardMarkerState = rememberApprovedHazardMarkerOverlayState(reportRepository = reportRepository)
    val screenPolicy = navigationScreenPolicy(uiState)
    val disablesDefaultWindowInsets = navigationUsesEmptyWindowInsets()
    val sidePanelPolicy = navigationSidePanelPolicy()
    val railWidth = NavigationSegmentRailWidth
    var isSidePanelExpanded by remember(uiState.screenState) { mutableStateOf(false) }
    var inspectionInteractionVersion by remember(uiState.screenState) { mutableStateOf(0) }

    LaunchedEffect(uiState.segmentSync.isInspectingSegments, inspectionInteractionVersion) {
        if (uiState.segmentSync.isInspectingSegments) {
            delay(NavigationInspectAutoReturnMillis)
            onAction(NavigationUiAction.ReturnToActiveSegmentClicked)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets =
                if (disablesDefaultWindowInsets) {
                    WindowInsets(0, 0, 0, 0)
                } else {
                    WindowInsets(0, 0, 0, 0)
                },
            topBar = {
                NavigationTopBar(
                    uiState = uiState,
                    onBackClick = { onAction(NavigationUiAction.BackClicked) },
                    onCloseClick = { onAction(NavigationUiAction.ExitNavigationClicked) },
                )
            },
        ) { innerPadding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                NavigationHeroCard(
                    uiState = uiState,
                    onAction = onAction,
                )
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                ) {
                    NavigationMapStage(
                        uiState = uiState,
                        hazardOverlayPoints = hazardMarkerState.overlayPoints,
                        onSegmentTapped = { index ->
                            onAction(NavigationUiAction.SegmentTapped(index = index))
                        },
                        onHazardMarkerClick = hazardMarkerState::onMarkerClick,
                        onViewportBoundsChanged = hazardMarkerState::onViewportBoundsChanged,
                        onReportClick = {
                            onAction(NavigationUiAction.ReportClicked)
                        },
                        onCurrentLocationClick = {
                            onAction(NavigationUiAction.CurrentLocationClicked)
                        },
                        onUserCameraGesture = {
                            onAction(NavigationUiAction.MapCameraMovedByUser)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (screenPolicy.showSegmentRail) {
                        if (isSidePanelExpanded) {
                            if (sidePanelPolicy.showsExpandedScrim) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .background(NavigationExpandedSidePanelScrimColor),
                                )
                            }
                        }
                        NavigationGuideSidePanel(
                            uiState = uiState,
                            isExpanded = isSidePanelExpanded,
                            onExpandedChange = { expanded ->
                                isSidePanelExpanded = expanded
                                if (expanded) {
                                    inspectionInteractionVersion += 1
                                    onAction(
                                        NavigationUiAction.SegmentTapped(
                                            index =
                                                uiState.segmentSync.focusedSegmentIndex
                                                    .takeIf { index -> index != NavigationOriginSegmentIndex }
                                                    ?: uiState.segmentSync.activeSegmentIndex,
                                        ),
                                    )
                                } else {
                                    onAction(NavigationUiAction.ReturnToActiveSegmentClicked)
                                }
                            },
                            onSegmentTapped = { index ->
                                if (isSidePanelExpanded && sidePanelPolicy.collapseOnSegmentTap) {
                                    isSidePanelExpanded = false
                                }
                                inspectionInteractionVersion += 1
                                onAction(NavigationUiAction.SegmentTapped(index = index))
                            },
                            onTopVisibleSegmentChanged = { index ->
                                inspectionInteractionVersion += 1
                                onAction(NavigationUiAction.SegmentTapped(index = index))
                            },
                            modifier =
                                Modifier
                                    .align(Alignment.CenterStart)
                                    .fillMaxHeight(),
                        )
                    }
                }
            }
        }

        val bottomBarLayoutPolicy =
            navigationBottomBarLayoutPolicy(
                showSegmentRail = screenPolicy.showSegmentRail,
                railWidth = railWidth,
            )
        NavigationBottomBar(
            uiState = uiState,
            onAction = onAction,
            layoutPolicy = bottomBarLayoutPolicy,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        ApprovedHazardMarkerBottomSheet(
            marker = hazardMarkerState.selectedMarker,
            onDismiss = hazardMarkerState::dismissSelection,
            bottomInset = navigationHazardSheetBottomInset(),
            modifier = Modifier.matchParentSize(),
        )

        if (uiState.isExitConfirmDialogVisible) {
            NavigationExitConfirmDialog(
                onDismiss = { onAction(NavigationUiAction.ExitNavigationDismissed) },
                onConfirm = { onAction(NavigationUiAction.ConfirmExitNavigationClicked) },
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun NavigationTopBar(
    uiState: NavigationUiState,
    onBackClick: () -> Unit,
    onCloseClick: () -> Unit,
) {
    val policy = navigationTopBarPolicy()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = EumSpacing.small, vertical = EumSpacing.xSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
        ) {
            if (policy.showBackButton) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_action_back),
                        contentDescription = stringResource(id = R.string.navigation_back),
                        tint = Color.White,
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
            Text(
                text = navigationRouteSummary(uiState),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = policy.titleFontWeight,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            IconButton(onClick = onCloseClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_action_close),
                    contentDescription = stringResource(id = R.string.map_facility_detail_close),
                    tint = Color.White,
                )
            }
        }
    }
}

internal data class NavigationTopBarPolicy(
    val showBackButton: Boolean,
    val showBookmarkAction: Boolean,
    val titleFontWeight: FontWeight,
)

internal fun navigationTopBarPolicy(): NavigationTopBarPolicy =
    NavigationTopBarPolicy(
        showBackButton = false,
        showBookmarkAction = false,
        titleFontWeight = FontWeight.SemiBold,
    )

internal data class NavigationScreenPolicy(
    val showSegmentRail: Boolean,
    val showFocusedSegmentCard: Boolean,
    val showReturnToActiveAction: Boolean,
)

internal data class NavigationHeroLayoutPolicy(
    val minHeight: Dp,
    val maxHeight: Dp,
    val directionIconSize: Dp,
    val showBottomDivider: Boolean,
)

internal data class NavigationHeroContentUiState(
    val guidanceAction: NavigationGuidanceAction,
    val title: String,
    val description: String,
    val distanceLabel: String,
    val transitInfo: NavigationTransitInfoUiState? = null,
)

private data class NavigationHeroPresentation(
    val content: NavigationHeroContentUiState,
)

internal data class NavigationBottomBarLayoutPolicy(
    val topDividerStartInset: Dp,
)

internal enum class NavigationSidePanelSwipeAxis {
    Horizontal,
}

internal data class NavigationSidePanelPolicy(
    val swipeAxis: NavigationSidePanelSwipeAxis,
    val swipeThresholdPx: Float,
    val showsExpandedScrim: Boolean,
    val collapseOnSegmentTap: Boolean,
    val showsProgressHeader: Boolean,
)

internal data class NavigationBottomBarChromePolicy(
    val bottomGap: Dp,
    val usesNavigationBarPadding: Boolean,
    val showTopDivider: Boolean,
)

internal fun navigationScreenPolicy(uiState: NavigationUiState): NavigationScreenPolicy =
    NavigationScreenPolicy(
        showSegmentRail = uiState.segmentSync.railItems.isNotEmpty(),
        showFocusedSegmentCard = false,
        showReturnToActiveAction = uiState.segmentSync.isInspectingSegments,
    )

internal fun navigationUsesEmptyWindowInsets(): Boolean = true

internal fun navigationSidePanelPolicy(): NavigationSidePanelPolicy =
    NavigationSidePanelPolicy(
        swipeAxis = NavigationSidePanelSwipeAxis.Horizontal,
        swipeThresholdPx = 80f,
        showsExpandedScrim = true,
        collapseOnSegmentTap = false,
        showsProgressHeader = false,
    )

internal fun navigationHeroLayoutPolicy(screenHeight: Dp): NavigationHeroLayoutPolicy =
    NavigationHeroLayoutPolicy(
        minHeight = 92.dp,
        maxHeight = (screenHeight * 0.16f).coerceIn(108.dp, 132.dp),
        directionIconSize = 44.dp,
        showBottomDivider = false,
    )

internal val NavigationHeroTransitDirectionIconSize = 40.dp

internal fun navigationHeroContent(uiState: NavigationUiState): NavigationHeroContentUiState {
    val focusedSegmentCard = uiState.focusedSegmentCard

    return NavigationHeroContentUiState(
        guidanceAction = focusedSegmentCard?.guidanceAction ?: uiState.stepCard.guidanceAction,
        title = focusedSegmentCard?.heroTitle ?: uiState.stepCard.heroTitle,
        description = focusedSegmentCard?.heroDescription ?: uiState.stepCard.heroDescription,
        distanceLabel = focusedSegmentCard?.distanceLabel ?: uiState.stepCard.distanceLabel,
        transitInfo = focusedSegmentCard?.transitInfo ?: uiState.stepCard.transitInfo,
    )
}

internal fun navigationBottomBarLayoutPolicy(
    showSegmentRail: Boolean,
    railWidth: Dp,
): NavigationBottomBarLayoutPolicy =
    NavigationBottomBarLayoutPolicy(
        topDividerStartInset = if (showSegmentRail) railWidth else 0.dp,
    )

internal fun navigationBottomBarChromePolicy(): NavigationBottomBarChromePolicy =
    NavigationBottomBarChromePolicy(
        bottomGap = NavigationBottomBarBottomGap,
        usesNavigationBarPadding = true,
        showTopDivider = false,
    )

@Composable
private fun navigationHazardSheetBottomInset(): Dp {
    val density = LocalDensity.current
    return with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
}

internal enum class NavigationExitDialogShell {
    Dialog,
}

internal data class NavigationExitDialogPolicy(
    val shell: NavigationExitDialogShell,
    val maxWidth: Dp,
    val containerCornerRadius: Dp,
    val buttonCornerRadius: Dp,
    val primaryButtonHeight: Dp,
    val secondaryButtonHeight: Dp,
    val shadowElevation: Dp,
)

internal fun navigationExitDialogPolicy(): NavigationExitDialogPolicy =
    NavigationExitDialogPolicy(
        shell = NavigationExitDialogShell.Dialog,
        maxWidth = 360.dp,
        containerCornerRadius = EumRadius.scaleL,
        buttonCornerRadius = EumRadius.scaleM,
        primaryButtonHeight = 48.dp,
        secondaryButtonHeight = 44.dp,
        shadowElevation = 10.dp,
    )

@Composable
private fun NavigationGuideSidePanel(
    uiState: NavigationUiState,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSegmentTapped: (Int) -> Unit,
    onTopVisibleSegmentChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    GuideSidePanelShell(
        isExpanded = isExpanded,
        onExpandedChange = onExpandedChange,
        collapsedWidth = NavigationSegmentRailWidth,
        expandedWidthFraction = NavigationGuideSidePanelExpandedWidthFraction,
        stateDescription = if (isExpanded) "expanded guide panel" else "collapsed guide rail",
        modifier = modifier,
    ) {
        if (isExpanded) {
            NavigationExpandedSidePanelContent(
                uiState = uiState,
                onSegmentTapped = onSegmentTapped,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            NavigationSegmentRail(
                uiState = uiState.segmentSync,
                onSegmentTapped = onSegmentTapped,
                onTopVisibleSegmentChanged = onTopVisibleSegmentChanged,
                modifier =
                    Modifier
                        .width(NavigationSegmentRailWidth)
                        .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun NavigationExpandedSidePanelContent(
    uiState: NavigationUiState,
    onSegmentTapped: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
    ) {
        uiState.segmentSync.railItems.forEachIndexed { index, item ->
            NavigationSidePanelRow(
                item = item,
                isFirst = index == 0,
                isLast = index == uiState.segmentSync.railItems.lastIndex,
                onClick = { onSegmentTapped(item.index) },
            )
        }
        NavigationExpandedSidePanelScrollTopAction(
            enabled = uiState.segmentSync.railItems.isNotEmpty(),
            onClick = {
                coroutineScope.launch {
                    scrollState.animateScrollTo(0)
                }
                uiState.segmentSync.railItems.firstOrNull()?.let { item ->
                    onSegmentTapped(item.index)
                }
            },
        )
    }
}

@Composable
private fun NavigationExpandedSidePanelScrollTopAction(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(id = R.string.navigation_rail_scroll_to_top_label)

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .semantics {
                    contentDescription = label
                }
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)),
            shadowElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_control_previous),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .size(22.dp)
                            .rotate(90f),
                )
            }
        }
    }
}

@Composable
private fun NavigationSidePanelRow(
    item: NavigationSegmentRailItemUiState,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val isSelected = item.isFocused || item.isActive
    val transitInfo = item.transitInfo
    val sidePanelTitle = item.sidePanelTitle.takeIf(String::isNotBlank) ?: item.instruction
    val sidePanelDescription = item.sidePanelDescription
    val stateLabel =
        when {
            item.isFocused -> "Selected segment"
            item.isActive -> "Current segment"
            item.isCompleted -> "Completed segment"
            else -> "Guidance segment"
        }

    Column {
        GuideSidePanelStepRow(
            title =
                transitInfo?.let { info -> "${info.startName} -> ${info.endName}" }
                    ?: sidePanelTitle,
            description = transitInfo?.durationLabel ?: sidePanelDescription ?: item.distanceLabel,
            action = transitInfo?.guidanceAction ?: item.guidanceAction,
            isOrigin = isFirst,
            isDestination = isLast,
            isActive = item.isActive,
            isFocused = item.isFocused,
            isSelected = isSelected,
            contentDescription = "${item.instruction} ${item.distanceLabel}",
            stateDescription = stateLabel,
            onClick = onClick,
            supportingContent = {
                transitInfo?.let { info ->
                    NavigationTransitSidePanelContent(transitInfo = info)
                }
            },
            trailingContent = {
                if (transitInfo == null && sidePanelDescription == null) item.distanceLabel.takeIf(String::isNotBlank)?.let { distance ->
                    Text(
                        text = distance,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                    )
                }
            },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.84f))
    }
}

@Composable
private fun NavigationTransitSidePanelContent(transitInfo: NavigationTransitInfoUiState) {
    NavigationTransitOptionSummary(optionLabels = transitInfo.optionLabels)
}

private fun navigationRouteSummary(uiState: NavigationUiState): String =
    uiState.focusedSegmentCard?.sequenceLabel?.takeIf { it.isNotBlank() }
        ?: uiState.progressLabel.takeIf { it.isNotBlank() && it != "-" }
        ?: "1 / 1"

@Composable
private fun NavigationHeroCard(
    uiState: NavigationUiState,
    onAction: (NavigationUiAction) -> Unit,
) {
    val heroContent = navigationHeroContent(uiState)
    val heroPresentation =
        NavigationHeroPresentation(
            content = heroContent,
        )
    val layoutPolicy = navigationHeroLayoutPolicy(LocalConfiguration.current.screenHeightDp.dp)

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = layoutPolicy.minHeight, max = layoutPolicy.maxHeight),
        shape = RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = EumSpacing.medium,
                            vertical = EumSpacing.medium,
                        ),
                horizontalArrangement = Arrangement.spacedBy(EumSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = heroPresentation,
                    label = "navigation-hero-guidance",
                    transitionSpec = {
                        (slideInVertically { height -> height / 3 } + fadeIn())
                            .togetherWith(slideOutVertically { height -> -height / 3 } + fadeOut())
                    },
                    modifier = Modifier.weight(1f),
                ) { presentation ->
                    presentation.content.transitInfo?.let { transitInfo ->
                        NavigationTransitHeroContent(
                            transitInfo = transitInfo,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } ?: Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(EumSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                            NavigationHeroDirectionIcon(
                                guidanceAction = presentation.content.guidanceAction,
                                iconSize = layoutPolicy.directionIconSize,
                            )
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = presentation.content.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    maxLines = 1,
                                )
                                Text(
                                    text = presentation.content.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    maxLines = 2,
                                )
                            }
                        }
                }
                NavigationVoiceControl(
                    uiState = uiState,
                    onAction = onAction,
                )
            }
            if (layoutPolicy.showBottomDivider) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.82f),
                )
            }
        }
    }
}

@Composable
private fun NavigationHeroDirectionIcon(
    guidanceAction: NavigationGuidanceAction,
    iconSize: Dp,
) {
    Icon(
        painter = painterResource(id = guidanceAction.iconRes()),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(guidanceAction.heroIconSize(defaultSize = iconSize)),
    )
}

internal fun NavigationGuidanceAction.heroIconSize(defaultSize: Dp): Dp =
    if (this == NavigationGuidanceAction.BUS || this == NavigationGuidanceAction.SUBWAY) {
        NavigationHeroTransitDirectionIconSize
    } else {
        defaultSize
    }

private fun navigationTransitOptionTypeColor(typeLabel: String): Color =
    if (typeLabel.contains("저상")) {
        NavigationTransitTagLowFloorColor
    } else {
        NavigationTransitTagNormalColor
    }

@Composable
private fun NavigationTransitHeroContent(
    transitInfo: NavigationTransitInfoUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavigationHeroDirectionIcon(
            guidanceAction = transitInfo.guidanceAction,
            iconSize = NavigationHeroTransitDirectionIconSize,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = transitInfo.startName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = "->",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                )
                Text(
                    text = transitInfo.endName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                transitInfo.durationLabel?.let { duration ->
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                    )
                }
            }
            NavigationTransitOptionSummary(optionLabels = transitInfo.optionLabels)
        }
    }
}

@Composable
private fun NavigationTransitOptionSummary(optionLabels: List<RouteTransitOptionLabelUiState>) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        optionLabels.forEach { option ->
            NavigationTransitOptionChip(option = option)
        }
    }
}

@Composable
private fun NavigationTransitOptionChip(option: RouteTransitOptionLabelUiState) {
    Surface(
        shape = RoundedCornerShape(NavigationTransitTagCornerRadius),
        color = Color.White,
        border = BorderStroke(NavigationTransitTagStrokeWidth, NavigationTransitTagBorderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(NavigationTransitTagCornerRadius),
                color = navigationTransitOptionTypeColor(option.typeLabel),
            ) {
                Text(
                    text = option.typeLabel,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                )
            }
            Text(
                text = option.routeNo,
                style = MaterialTheme.typography.labelMedium,
                color = NavigationTransitTagRouteNumberColor,
                maxLines = 1,
            )
            option.arrivalLabel?.let { arrival ->
                Text(
                    text = arrival,
                    style = MaterialTheme.typography.labelMedium,
                    color = NavigationTransitTagArrivalColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun NavigationVoiceControl(
    uiState: NavigationUiState,
    onAction: (NavigationUiAction) -> Unit,
) {
    val isEnabled = uiState.tts.isEnabled
    val containerColor =
        if (isEnabled) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        }
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)
    val iconRes =
        if (isEnabled) {
            R.drawable.ic_control_voice
        } else {
            R.drawable.ic_navigation_tts_off
        }
    val iconTint =
        if (isEnabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    val contentDescription =
        if (isEnabled) {
            stringResource(id = R.string.navigation_tts_toggle_content_description_on)
        } else {
            stringResource(id = R.string.navigation_tts_toggle_content_description_off)
        }

    Surface(
        modifier =
            Modifier
                .size(44.dp)
                .semantics {
                    this.contentDescription = contentDescription
                }
                .clickable(
                    role = Role.Button,
                    onClick = {
                        onAction(
                            NavigationUiAction.VoiceGuidanceToggled(
                                enabled = !isEnabled,
                            ),
                        )
                    },
                ),
        shape = RoundedCornerShape(EumRadius.scaleM),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(if (isEnabled) 26.dp else 28.dp),
            )
        }
    }
}

@Composable
private fun NavigationMapStage(
    uiState: NavigationUiState,
    hazardOverlayPoints: List<MapViewportPointOverlay>,
    onSegmentTapped: (Int) -> Unit,
    onHazardMarkerClick: (String) -> Boolean,
    onViewportBoundsChanged: (MapViewportBounds?) -> Unit,
    onReportClick: () -> Unit,
    onCurrentLocationClick: () -> Unit,
    onUserCameraGesture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mapControlState = rememberMapOverlayViewportControlState()
    LaunchedEffect(uiState.segmentSync.isInspectingSegments, uiState.segmentSync.focusedSegmentIndex) {
        if (uiState.segmentSync.isInspectingSegments) {
            mapControlState.clearManualCamera()
        }
    }
    LaunchedEffect(uiState.mapOverlay.trackingMode, uiState.segmentSync.isInspectingSegments) {
        if (!uiState.segmentSync.isInspectingSegments && uiState.mapOverlay.trackingMode != NavigationTrackingMode.IDLE) {
            mapControlState.clearManualCamera()
        }
    }
    LaunchedEffect(uiState.locationRecenterRequestId) {
        if (uiState.locationRecenterRequestId == 0L) return@LaunchedEffect
        val currentLocation = uiState.mapOverlay.currentLocation?.coordinate
        if (currentLocation != null) {
            mapControlState.recenterToCurrentLocation(
                MapCoordinate(
                    latitude = currentLocation.latitude,
                    longitude = currentLocation.longitude,
                ),
            )
        } else {
            mapControlState.recenter()
        }
    }
    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        NavigationMapBackdrop(
            mapOverlay = uiState.mapOverlay,
            hazardOverlayPoints = hazardOverlayPoints,
            onSegmentTapped = onSegmentTapped,
            onHazardMarkerClick = onHazardMarkerClick,
            onViewportBoundsChanged = onViewportBoundsChanged,
            onUserCameraGesture = onUserCameraGesture,
            controlState = mapControlState,
            modifier = Modifier.fillMaxSize(),
        )
        if (uiState.mapOverlay.shouldUsePlaceholder) {
            NavigationMapMessageCard(
                title = uiState.mapPlaceholderTitle,
                description = uiState.mapPlaceholderDescription,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(EumSpacing.small),
            )
        }
        NavigationMapControls(
            onReportClick = onReportClick,
            onActionClick = onCurrentLocationClick,
            onZoomInClick = {
                mapControlState.zoomIn()
                onUserCameraGesture()
            },
            onZoomOutClick = {
                mapControlState.zoomOut()
                onUserCameraGesture()
            },
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = EumSpacing.small),
        )
    }
}

@Composable
private fun NavigationMapBackdrop(
    mapOverlay: NavigationMapOverlayUiState,
    hazardOverlayPoints: List<MapViewportPointOverlay>,
    onSegmentTapped: (Int) -> Unit,
    onHazardMarkerClick: (String) -> Boolean,
    onViewportBoundsChanged: (MapViewportBounds?) -> Unit,
    onUserCameraGesture: () -> Unit,
    controlState: MapOverlayViewportControlState? = null,
    modifier: Modifier = Modifier,
) {
    val mapDescription = stringResource(id = R.string.navigation_map_section_title)
    val overlayState =
        createNavigationViewportOverlayState(mapOverlay).let { baseOverlayState ->
            baseOverlayState.copy(points = baseOverlayState.points + hazardOverlayPoints)
        }
    MapOverlayViewport(
        overlayState = overlayState,
        modifier = modifier,
        contentDescription = mapDescription,
        onMarkerClick = { markerId ->
            if (onHazardMarkerClick(markerId)) {
                return@MapOverlayViewport
            }
            markerId.toNavigationSegmentMarkerIndexOrNull()?.let { segmentIndex ->
                onSegmentTapped(segmentIndex)
            }
        },
        onViewportBoundsChanged = onViewportBoundsChanged,
        onUserCameraGesture = onUserCameraGesture,
        controlState = controlState,
    )
}

@Composable
private fun NavigationMapMarker(
    label: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(NavigationMapMarkerSize),
        shape = CircleShape,
        color = containerColor,
        shadowElevation = 6.dp,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NavigationMapMessageCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(EumRadius.scaleM),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.36f)),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(EumSpacing.small),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NavigationMapControls(
    onReportClick: () -> Unit = {},
    onActionClick: () -> Unit = {},
    onZoomInClick: () -> Unit = {},
    onZoomOutClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    EumMapFloatingControls(
        actionButtonState =
            EumMapFloatingActionButtonState(
                iconRes = R.drawable.ic_route_start_navigation_button,
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = stringResource(id = R.string.navigation_return_to_active_segment_label),
                enabled = true,
            ),
        onActionClick = onActionClick,
        topActionButtonState =
            EumMapFloatingActionButtonState(
                iconRes = R.drawable.ic_nav_report,
                tint = Color.Black,
                iconSize = 24.dp,
                contentDescription = stringResource(id = R.string.navigation_map_control_report),
                enabled = true,
            ),
        onTopActionClick = onReportClick,
        modifier = modifier,
        onZoomInClick = onZoomInClick,
        onZoomOutClick = onZoomOutClick,
        zoomInLabel = stringResource(id = R.string.navigation_map_control_zoom_in),
        zoomOutLabel = stringResource(id = R.string.navigation_map_control_zoom_out),
    )
}

@Composable
private fun NavigationBottomBar(
    uiState: NavigationUiState,
    onAction: (NavigationUiAction) -> Unit,
    layoutPolicy: NavigationBottomBarLayoutPolicy,
    modifier: Modifier = Modifier,
) {
    val chromePolicy = navigationBottomBarChromePolicy()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth(),
        ) {
            if (chromePolicy.showTopDivider) {
                HorizontalDivider(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = layoutPolicy.topDividerStartInset),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.82f),
                )
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (chromePolicy.usesNavigationBarPadding) {
                                Modifier.navigationBarsPadding()
                            } else {
                                Modifier
                            },
                        )
                        .padding(
                            start = NavigationBottomBarHorizontalPadding,
                            end = NavigationBottomBarHorizontalPadding,
                            top = EumSpacing.small,
                            bottom = chromePolicy.bottomGap,
                        ),
            ) {
                Button(
                    onClick = { onAction(NavigationUiAction.ExitNavigationClicked) },
                    enabled = uiState.isExitEnabled,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.36f),
                            disabledContentColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.7f),
                        ),
                    elevation =
                        ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp,
                            disabledElevation = 0.dp,
                        ),
                    shape = RoundedCornerShape(EumRadius.scaleM),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(NavigationBottomBarButtonHeight)
                            .align(Alignment.Center),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_control_stop),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = uiState.exitCta.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationExitConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val policy = navigationExitDialogPolicy()

    when (policy.shell) {
        NavigationExitDialogShell.Dialog ->
            Dialog(
                onDismissRequest = onDismiss,
            ) {
                Surface(
                    modifier =
                        modifier
                            .fillMaxWidth()
                            .padding(horizontal = EumSpacing.medium)
                            .widthIn(max = policy.maxWidth),
                    shape = RoundedCornerShape(policy.containerCornerRadius),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
                    shadowElevation = policy.shadowElevation,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = EumSpacing.medium,
                                    end = EumSpacing.medium,
                                    top = EumSpacing.large,
                                    bottom = EumSpacing.medium,
                                ),
                        verticalArrangement = Arrangement.spacedBy(EumSpacing.large),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
                        ) {
                            Text(
                                text = stringResource(id = R.string.navigation_exit_confirm_dialog_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(id = R.string.navigation_exit_confirm_dialog_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
                        ) {
                            Button(
                                onClick = onConfirm,
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    ),
                                elevation =
                                    ButtonDefaults.buttonElevation(
                                        defaultElevation = 0.dp,
                                        pressedElevation = 0.dp,
                                        focusedElevation = 0.dp,
                                        hoveredElevation = 0.dp,
                                        disabledElevation = 0.dp,
                                    ),
                                shape = RoundedCornerShape(policy.buttonCornerRadius),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(policy.primaryButtonHeight),
                            ) {
                                Text(
                                    text = stringResource(id = R.string.navigation_exit_confirm_dialog_confirm),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Button(
                                onClick = onDismiss,
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
                                elevation =
                                    ButtonDefaults.buttonElevation(
                                        defaultElevation = 0.dp,
                                        pressedElevation = 0.dp,
                                        focusedElevation = 0.dp,
                                        hoveredElevation = 0.dp,
                                        disabledElevation = 0.dp,
                                    ),
                                shape = RoundedCornerShape(policy.buttonCornerRadius),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(policy.secondaryButtonHeight),
                            ) {
                                Text(
                                    text = stringResource(id = R.string.navigation_exit_confirm_dialog_cancel),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
    }
}

private fun DrawScope.drawNavigationMapGrid(outline: Color) {
    val verticalStep = size.width / 5f
    val horizontalStep = size.height / 6f
    val strokeWidth = 1.dp.toPx()

    for (index in 0..5) {
        val x = index * verticalStep
        drawLine(
            color = outline.copy(alpha = 0.16f),
            start = Offset(x, 0f),
            end = Offset(x - (size.height * 0.16f), size.height),
            strokeWidth = strokeWidth,
        )
    }

    for (index in 0..6) {
        val y = index * horizontalStep
        drawLine(
            color = outline.copy(alpha = 0.12f),
            start = Offset(0f, y),
            end = Offset(size.width, y + (size.width * 0.08f)),
            strokeWidth = strokeWidth,
        )
    }
}

private fun List<GeoCoordinate>.toNavigationPreviewPath(
    bounds: NavigationProjectionBounds,
    canvasSize: Size,
): Path =
    Path().also { path ->
        forEachIndexed { index, coordinate ->
            val offset = bounds.project(coordinate).toCanvasOffset(canvasSize)
            if (index == 0) {
                path.moveTo(offset.x, offset.y)
            } else {
                path.lineTo(offset.x, offset.y)
            }
        }
    }

private fun NavigationProjectionPoint.toCanvasOffset(size: Size): Offset =
    Offset(
        x = size.width * xRatio,
        y = size.height * yRatio,
    )

private fun Modifier.offsetWithinNavigationMap(
    point: NavigationProjectionPoint,
    areaWidth: Dp,
    areaHeight: Dp,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    elementSize: Dp,
): Modifier =
    offset(
        x = horizontalPadding + (areaWidth * point.xRatio) - (elementSize / 2),
        y = verticalPadding + (areaHeight * point.yRatio) - (elementSize / 2),
    )

private fun navigationProjectionBounds(mapOverlay: NavigationMapOverlayUiState): NavigationProjectionBounds {
    val coordinates =
        buildList {
            when (mapOverlay.mapFocusMode) {
                NavigationMapFocusMode.ACTIVE -> {
                    addAll(mapOverlay.selectedRoutePolyline)
                    mapOverlay.currentLocation?.let { point -> add(point.coordinate) }
                    mapOverlay.origin?.let { point -> add(point.coordinate) }
                    mapOverlay.destination?.let { point -> add(point.coordinate) }
                }

                NavigationMapFocusMode.FOCUSED -> {
                    addAll(mapOverlay.focusedSegmentPolyline)
                    if (mapOverlay.activeSegmentPolyline != mapOverlay.focusedSegmentPolyline) {
                        addAll(mapOverlay.activeSegmentPolyline)
                    }
                    mapOverlay.focusCoordinate?.let { coordinate -> add(coordinate) }
                    mapOverlay.currentLocation?.let { point -> add(point.coordinate) }
                }
            }
        }

    if (coordinates.isEmpty()) {
        return NavigationProjectionBounds(
            minLatitude = DEFAULT_NAVIGATION_CENTER_LATITUDE - (MIN_NAVIGATION_LATITUDE_SPAN / 2.0),
            maxLatitude = DEFAULT_NAVIGATION_CENTER_LATITUDE + (MIN_NAVIGATION_LATITUDE_SPAN / 2.0),
            minLongitude = DEFAULT_NAVIGATION_CENTER_LONGITUDE - (MIN_NAVIGATION_LONGITUDE_SPAN / 2.0),
            maxLongitude = DEFAULT_NAVIGATION_CENTER_LONGITUDE + (MIN_NAVIGATION_LONGITUDE_SPAN / 2.0),
        )
    }

    val latitudeBounds =
        expandedNavigationBounds(
            minValue = coordinates.minOf { coordinate -> coordinate.latitude },
            maxValue = coordinates.maxOf { coordinate -> coordinate.latitude },
            minimumSpan = MIN_NAVIGATION_LATITUDE_SPAN,
        )
    val longitudeBounds =
        expandedNavigationBounds(
            minValue = coordinates.minOf { coordinate -> coordinate.longitude },
            maxValue = coordinates.maxOf { coordinate -> coordinate.longitude },
            minimumSpan = MIN_NAVIGATION_LONGITUDE_SPAN,
        )

    return NavigationProjectionBounds(
        minLatitude = latitudeBounds.first,
        maxLatitude = latitudeBounds.second,
        minLongitude = longitudeBounds.first,
        maxLongitude = longitudeBounds.second,
    )
}

private fun expandedNavigationBounds(
    minValue: Double,
    maxValue: Double,
    minimumSpan: Double,
): Pair<Double, Double> {
    val center = (minValue + maxValue) / 2.0
    val paddedSpan = (maxValue - minValue) * 1.42
    val finalSpan = maxOf(paddedSpan, minimumSpan)
    val halfSpan = finalSpan / 2.0

    return (center - halfSpan) to (center + halfSpan)
}

private data class NavigationProjectionBounds(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
) {
    private val latitudeSpan: Double
        get() = (maxLatitude - minLatitude).coerceAtLeast(MIN_NAVIGATION_LATITUDE_SPAN)

    private val longitudeSpan: Double
        get() = (maxLongitude - minLongitude).coerceAtLeast(MIN_NAVIGATION_LONGITUDE_SPAN)

    fun project(coordinate: GeoCoordinate): NavigationProjectionPoint {
        val longitudeRatio =
            ((coordinate.longitude - minLongitude) / longitudeSpan)
                .toFloat()
                .coerceIn(0.08f, 0.92f)
        val latitudeRatio =
            (1f - ((coordinate.latitude - minLatitude) / latitudeSpan).toFloat())
                .coerceIn(0.1f, 0.9f)

        return NavigationProjectionPoint(
            xRatio = longitudeRatio,
            yRatio = latitudeRatio,
        )
    }
}

private data class NavigationProjectionPoint(
    val xRatio: Float,
    val yRatio: Float,
)

private const val DEFAULT_NAVIGATION_CENTER_LATITUDE = 35.1796
private const val DEFAULT_NAVIGATION_CENTER_LONGITUDE = 129.0756
private const val MIN_NAVIGATION_LATITUDE_SPAN = 0.0035
private const val MIN_NAVIGATION_LONGITUDE_SPAN = 0.0045
private val NavigationMapMarkerSize = 38.dp
private val NavigationBottomBarButtonHeight = 50.dp
private val NavigationBottomBarHorizontalPadding = EumSpacing.medium + 50.dp
private val NavigationBottomBarBottomGap = 30.dp
private val NavigationSegmentRailWidth = 58.dp
private const val NavigationGuideSidePanelExpandedWidthFraction = 0.88f
private const val NavigationInspectAutoReturnMillis = 30_000L
private val NavigationExpandedSidePanelScrimColor = Color(0x66000000)
private val NavigationTransitTagCornerRadius = 10.dp
private val NavigationTransitTagStrokeWidth = 0.5.dp
private val NavigationTransitTagBorderColor = Color(0xFFD9D9D9)
private val NavigationTransitTagLowFloorColor = Color(0xFF2671A8)
private val NavigationTransitTagNormalColor = Color(0xFF4B9EDC)
private val NavigationTransitTagRouteNumberColor = Color(0xFF333333)
private val NavigationTransitTagArrivalColor = Color(0xFFF94D4D)
