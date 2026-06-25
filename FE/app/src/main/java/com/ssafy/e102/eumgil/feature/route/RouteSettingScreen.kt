package com.ssafy.e102.eumgil.feature.route

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.designsystem.component.dialog.EumDuribalCallConfirmDialog
import com.ssafy.e102.eumgil.core.designsystem.component.dialog.EumDuribalCallConfirmDismissStyle
import com.ssafy.e102.eumgil.core.designsystem.component.map.EumMapFloatingActionButtonState
import com.ssafy.e102.eumgil.core.designsystem.component.map.EumMapFloatingControls
import com.ssafy.e102.eumgil.core.designsystem.component.navigation.EumCenteredTopBar
import com.ssafy.e102.eumgil.core.designsystem.theme.EumBorderSubtle
import com.ssafy.e102.eumgil.core.designsystem.theme.EumPrimary600
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSurfaceMuted
import com.ssafy.e102.eumgil.core.designsystem.theme.EumTextPrimary
import com.ssafy.e102.eumgil.core.designsystem.theme.EumTextTertiary
import com.ssafy.e102.eumgil.core.designsystem.theme.EumWhite
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.LowFloorBusReservation
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.core.model.RouteRiskLevel
import com.ssafy.e102.eumgil.data.repository.ReportRepository
import com.ssafy.e102.eumgil.data.repository.RouteEditingTarget
import com.ssafy.e102.eumgil.feature.guidance.component.GuideSidePanelShell
import com.ssafy.e102.eumgil.feature.guidance.component.GuideSidePanelStepRow
import com.ssafy.e102.eumgil.feature.guidance.component.RouteStepScrubberItem
import com.ssafy.e102.eumgil.feature.guidance.component.RouteStepScrubberRail
import com.ssafy.e102.eumgil.feature.map.component.ApprovedHazardMarkerBottomSheet
import com.ssafy.e102.eumgil.feature.map.component.MapOverlayViewport
import com.ssafy.e102.eumgil.feature.map.component.MapOverlayViewportControlState
import com.ssafy.e102.eumgil.feature.map.component.MapViewportBounds
import com.ssafy.e102.eumgil.feature.map.component.MapViewportOverlayTone
import com.ssafy.e102.eumgil.feature.map.component.MapViewportPointKind
import com.ssafy.e102.eumgil.feature.map.component.MapViewportPointOverlay
import com.ssafy.e102.eumgil.feature.map.component.MapViewportPolylineOverlay
import com.ssafy.e102.eumgil.feature.map.component.MapViewportPolylineStyle
import com.ssafy.e102.eumgil.feature.map.component.MapViewportTransitMarker
import com.ssafy.e102.eumgil.feature.map.component.MapViewportTransitMarkerKind
import com.ssafy.e102.eumgil.feature.map.component.MapViewportTransitMarkerLeg
import com.ssafy.e102.eumgil.feature.map.component.createRoutePreviewViewportOverlayState
import com.ssafy.e102.eumgil.feature.map.component.rememberApprovedHazardMarkerOverlayState
import com.ssafy.e102.eumgil.feature.map.component.rememberMapOverlayViewportControlState
import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import com.ssafy.e102.eumgil.feature.navigation.NavigationGuidanceAction
import com.ssafy.e102.eumgil.feature.navigation.component.resolveGuideRailAutoScrollItemIndex
import com.ssafy.e102.eumgil.feature.navigation.component.resolveGuideRailEndSnapPadding
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun RouteSettingScreen(
    uiState: RouteSettingUiState,
    reportRepository: ReportRepository,
    onAction: (RouteSettingUiAction) -> Unit,
    isDuribalConfirmDialogVisible: Boolean = false,
    onDuribalCallClick: () -> Unit = {},
    onDuribalConfirmDismiss: () -> Unit = {},
    onDuribalConfirm: () -> Unit = {},
    pendingLowFloorReservation: LowFloorBusReservation? = null,
    isLowFloorReservationRequesting: Boolean = false,
    completedLowFloorReservationKeys: Set<String> = emptySet(),
    onLowFloorReservationClick: (LowFloorBusReservation) -> Unit = {},
    onLowFloorReservationDismiss: () -> Unit = {},
    onLowFloorReservationConfirm: () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    onDisabledStartClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val hazardMarkerState = rememberApprovedHazardMarkerOverlayState(reportRepository = reportRepository)
    val showsRouteLoadingScreen = uiState.shouldShowRouteLoadingScreen()
    val showsRouteUnsupportedAreaScreen = uiState.shouldShowRouteUnsupportedAreaScreen()
    val showsRouteFailureScreen = uiState.shouldShowRouteFailureScreen()
    val ctaSupportingText: String? = null
    val disablesDefaultWindowInsets = routeSettingUsesEmptyWindowInsets()
    var isDuribalPromptDismissed by remember(
        uiState.selectedTravelMode,
        uiState.loadErrorMessage,
        uiState.showsDuribalCallAction,
    ) {
        mutableStateOf(false)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.matchParentSize(),
            contentWindowInsets =
                if (disablesDefaultWindowInsets) {
                    WindowInsets(0, 0, 0, 0)
                } else {
                    WindowInsets(0, 0, 0, 0)
                },
            topBar = {
                RouteSearchHeaderKakao(
                    uiState = uiState,
                    onBackClick = { onAction(RouteSettingUiAction.BackClicked) },
                    onOriginClick = {
                        onAction(RouteSettingUiAction.WaypointClicked(RouteEditingTarget.ORIGIN))
                    },
                    onDestinationClick = {
                        onAction(RouteSettingUiAction.WaypointClicked(RouteEditingTarget.DESTINATION))
                    },
                    onSwapClick = { onAction(RouteSettingUiAction.WaypointsSwapClicked) },
                    onModeSelected = { mode ->
                        onAction(RouteSettingUiAction.TravelModeSelected(mode))
                    },
                )
            },
        ) { innerPadding ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                if (showsRouteLoadingScreen && uiState.selectedTravelMode != RouteTravelMode.TRANSIT) {
                    RouteLoadingScreen(
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (showsRouteUnsupportedAreaScreen) {
                    RouteUnsupportedAreaScreen(
                        onSelectPlaceClick = {
                            onAction(
                                RouteSettingUiAction.WaypointClicked(
                                    uiState.unsupportedArea?.editingTarget ?: RouteEditingTarget.DESTINATION,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (showsRouteFailureScreen) {
                    RouteFailureScreen(
                        uiState = uiState,
                        onDuribalCallClick = onDuribalCallClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (uiState.selectedTravelMode == RouteTravelMode.TRANSIT) {
                    RouteSettingTransitResultPane(
                        uiState = uiState,
                        completedLowFloorReservationKeys = completedLowFloorReservationKeys,
                        onLowFloorReservationClick = onLowFloorReservationClick,
                        onDuribalCallClick = onDuribalCallClick,
                        onDuribalCancelClick = { isDuribalPromptDismissed = true },
                        showDuribalCallPrompt = !isDuribalPromptDismissed,
                        onOptionClick = { routeOption ->
                            onAction(RouteSettingUiAction.RouteOptionSelected(routeOption))
                        },
                        onOptionDetailClick = { routeOption ->
                            onAction(RouteSettingUiAction.RouteOptionDetailClicked(routeOption))
                        },
                        onRefresh = {
                            onAction(RouteSettingUiAction.RouteRefreshClicked)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    RouteMapStage(
                        uiState = uiState,
                        hazardOverlayPoints = hazardMarkerState.overlayPoints,
                        onHazardMarkerClick = hazardMarkerState::onMarkerClick,
                        onViewportBoundsChanged = hazardMarkerState::onViewportBoundsChanged,
                        modifier = Modifier.fillMaxSize(),
                        onOptionClick = { routeOption ->
                            onAction(RouteSettingUiAction.RouteOptionSelected(routeOption))
                        },
                        onOptionDetailClick = { routeOption ->
                            onAction(RouteSettingUiAction.RouteOptionDetailClicked(routeOption))
                        },
                        onCurrentLocationClick = {
                            onAction(RouteSettingUiAction.CurrentLocationClicked)
                        },
                    )
                }
                if (!showsRouteLoadingScreen && !showsRouteUnsupportedAreaScreen && !showsRouteFailureScreen) {
                    RouteSettingBottomBar(
                        buttonLabel = uiState.cta.label,
                        enabled = uiState.isStartEnabled,
                        supportingText = ctaSupportingText,
                        selectedRoute = uiState.selectedRoute,
                        onStartClick = { onAction(RouteSettingUiAction.StartNavigationClicked) },
                        onDisabledStartClick = onDisabledStartClick,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .zIndex(RouteSettingBottomBarZIndex),
                    )
                }
            }
        }
        snackbarHostState?.let { hostState ->
            SnackbarHost(
                hostState = hostState,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .wrapContentWidth()
                        .padding(
                            start = EumSpacing.medium,
                            end = EumSpacing.medium,
                            bottom = routeSettingBottomBarOverlayClearance(extraSpacing = EumSpacing.small),
                        ),
            )
        }
        ApprovedHazardMarkerBottomSheet(
            marker = hazardMarkerState.selectedMarker,
            onDismiss = hazardMarkerState::dismissSelection,
            bottomInset = routeSettingHazardSheetBottomInset(),
            modifier = Modifier.matchParentSize(),
        )
    }

    if (isDuribalConfirmDialogVisible) {
        EumDuribalCallConfirmDialog(
            onDismiss = onDuribalConfirmDismiss,
            onConfirm = onDuribalConfirm,
            dismissStyle = EumDuribalCallConfirmDismissStyle.TextButton,
        )
    }
    pendingLowFloorReservation?.let { reservation ->
        LowFloorReservationConfirmDialog(
            reservation = reservation,
            isRequesting = isLowFloorReservationRequesting,
            onDismiss = onLowFloorReservationDismiss,
            onConfirm = onLowFloorReservationConfirm,
        )
    }
}

@Composable
fun RouteDetailScreen(
    uiState: RouteSettingUiState,
    reportRepository: ReportRepository,
    onBackClick: () -> Unit,
    onCloseClick: () -> Unit = onBackClick,
    onStartClick: () -> Unit,
    onCurrentLocationClick: () -> Unit = {},
    pendingLowFloorReservation: LowFloorBusReservation? = null,
    isLowFloorReservationRequesting: Boolean = false,
    completedLowFloorReservationKeys: Set<String> = emptySet(),
    onLowFloorReservationClick: (LowFloorBusReservation) -> Unit = {},
    onLowFloorReservationDismiss: () -> Unit = {},
    onLowFloorReservationConfirm: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val hazardMarkerState = rememberApprovedHazardMarkerOverlayState(reportRepository = reportRepository)
    val selectedRoute = uiState.selectedRoute
    val returnToRoutesLabel = stringResource(id = R.string.route_setting_detail_return_action)
    val ctaSupportingText =
        if (uiState.cta.isEnabled) {
            null
        } else {
            uiState.cta.supportingText
        }

    var isDetailSidePanelExpanded by remember(selectedRoute?.routeOption, selectedRoute?.detailSteps) { mutableStateOf(true) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            RouteDetailTopBar(
                originName = uiState.origin.name,
                destinationName = selectedRoute?.destination?.name ?: uiState.destination.name,
                onBackClick = onBackClick,
                onCloseClick = onCloseClick,
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            val detailRoutePath =
                selectedRoute?.previewPoints
                    ?.takeIf { it.size >= 2 }
                    ?: uiState.routePreviewMap.polyline
            val mapControlState = rememberMapOverlayViewportControlState()

            var focusedDetailStepIndex by remember(selectedRoute?.routeOption, selectedRoute?.detailSteps) { mutableStateOf<Int?>(null) }
            val guidanceMarkers =
                remember(selectedRoute?.detailSteps, focusedDetailStepIndex) {
                    if (focusedDetailStepIndex == null) {
                        emptyList()
                    } else {
                        selectedRoute.detailGuidanceMarkers(focusedDetailStepIndex)
                    }
                }

                RouteMapBackdrop(
                    previewMap = uiState.routePreviewMap,
                    routePath = detailRoutePath,
                    detailPolylines = selectedRoute?.detailPolylines.orEmpty(),
                    travelMode = selectedRoute?.routeOption.toRouteDetailTravelMode(),
                    guidanceMarkers = guidanceMarkers,
                    hazardOverlayPoints = hazardMarkerState.overlayPoints,
                    controlState = mapControlState,
                    onViewportBoundsChanged = hazardMarkerState::onViewportBoundsChanged,
                    onMarkerClick = { markerId ->
                        if (hazardMarkerState.onMarkerClick(markerId)) {
                            return@RouteMapBackdrop
                        }
                        focusedDetailStepIndex = markerId.routeDetailStepMarkerIndexOrNull()
                        if (focusedDetailStepIndex != null) {
                            mapControlState.clearManualCamera()
                            isDetailSidePanelExpanded = false
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

            RouteMapControls(
                onActionClick = {
                    onCurrentLocationClick()
                    mapControlState.recenterToCurrentLocationOrRoute(uiState.currentLocationRecenterCoordinate)
                },
                onZoomInClick = { mapControlState.zoomIn() },
                onZoomOutClick = { mapControlState.zoomOut() },
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = EumSpacing.small),
            )

            if (isDetailSidePanelExpanded && selectedRoute != null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(RouteDetailExpandedSidePanelScrimColor),
                )
            }

            when {
                uiState.isLoading ->
                    RouteStateCard(
                        title = stringResource(id = R.string.route_setting_detail_loading_title),
                        description = stringResource(id = R.string.route_setting_detail_loading_description),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.24f),
                        borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(EumSpacing.medium),
                    )

                uiState.loadErrorMessage != null ->
                    RouteStateCard(
                        title = stringResource(id = R.string.route_setting_detail_error_title),
                        description = uiState.loadErrorMessage,
                        actionLabel = returnToRoutesLabel,
                        onActionClick = onBackClick,
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.24f),
                        borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(EumSpacing.medium),
                    )

                selectedRoute == null ->
                    RouteStateCard(
                        title = stringResource(id = R.string.route_setting_detail_empty_title),
                        description = stringResource(id = R.string.route_setting_detail_empty_description),
                        actionLabel = returnToRoutesLabel,
                        onActionClick = onBackClick,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(EumSpacing.medium),
                    )

                else ->
                    RouteDetailSidePanel(
                        selectedRoute = selectedRoute,
                        origin = uiState.origin,
                        isExpanded = isDetailSidePanelExpanded,
                        focusedStepIndex = focusedDetailStepIndex,
                        onExpandedChange = { expanded -> isDetailSidePanelExpanded = expanded },
                        onStepClick = { index ->
                            mapControlState.clearManualCamera()
                            focusedDetailStepIndex = index
                            isDetailSidePanelExpanded = false
                        },
                        onTopVisibleStepChanged = { index ->
                            focusedDetailStepIndex = index
                        },
                        completedLowFloorReservationKeys = completedLowFloorReservationKeys,
                        onLowFloorReservationClick = onLowFloorReservationClick,
                        modifier =
                            Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .then(
                                    if (!isDetailSidePanelExpanded) Modifier.padding(top = RouteDetailCollapsedGuideCardMinHeight) else Modifier,
                                ),
                    )
            }
            if (!isDetailSidePanelExpanded && selectedRoute != null) {
                RouteDetailCollapsedGuideCard(
                    origin = uiState.origin,
                    selectedRoute = selectedRoute,
                    focusedStepIndex = focusedDetailStepIndex,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth(),
                )
            }
            RouteSettingBottomBar(
                buttonLabel = uiState.cta.label,
                enabled = uiState.isStartEnabled,
                supportingText = ctaSupportingText,
                selectedRoute = selectedRoute,
                onStartClick = onStartClick,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            ApprovedHazardMarkerBottomSheet(
                marker = hazardMarkerState.selectedMarker,
                onDismiss = hazardMarkerState::dismissSelection,
                bottomInset = routeSettingHazardSheetBottomInset(),
                modifier = Modifier.matchParentSize(),
            )
        }
    }
    pendingLowFloorReservation?.let { reservation ->
        LowFloorReservationConfirmDialog(
            reservation = reservation,
            isRequesting = isLowFloorReservationRequesting,
            onDismiss = onLowFloorReservationDismiss,
            onConfirm = onLowFloorReservationConfirm,
        )
    }
}

@Composable
private fun RouteDetailTopBar(
    originName: String,
    destinationName: String,
    onBackClick: () -> Unit,
    onCloseClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = RouteSearchHeaderContainerColor,
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
            IconButton(onClick = onBackClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_action_back),
                    contentDescription = stringResource(id = R.string.route_setting_back),
                    tint = Color.White,
                )
            }
            Text(
                text = "${originName.ifBlank { "출발지" }} -> ${destinationName.ifBlank { "도착지" }}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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

@Composable
private fun RouteDetailCollapsedGuideCard(
    origin: RouteLocationUiState,
    selectedRoute: RouteSelectedRouteUiState,
    focusedStepIndex: Int?,
    modifier: Modifier = Modifier,
) {
    val step =
        selectedRoute.detailSteps.getOrNull(focusedStepIndex ?: 0)
            ?: selectedRoute.detailSteps.firstOrNull()
    Surface(
        modifier = modifier,
        color = RouteDetailCollapsedGuideCardContainerColor,
        shadowElevation = 0.dp,
    ) {
        Column {
        AnimatedContent(
            targetState = step,
            label = "route-detail-collapsed-guide",
            transitionSpec = {
                (slideInVertically { height -> height / 3 } + fadeIn())
                    .togetherWith(slideOutVertically { height -> -height / 3 } + fadeOut())
            },
        ) { targetStep ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = RouteDetailCollapsedGuideCardMinHeight)
                    .padding(horizontal = EumSpacing.medium, vertical = EumSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EumSpacing.medium),
        ) {
            if (targetStep != null && targetStep.kind.isTransitStep()) {
                RouteDetailTransitGuideCardContent(
                    step = targetStep,
                    modifier = Modifier.weight(1f),
                    contentColor = RouteDetailCollapsedGuideCardContentColor,
                    secondaryContentColor = RouteDetailCollapsedGuideCardContentColor,
                    iconTint = RouteDetailCollapsedGuideCardContentColor,
                )
            } else {
            RouteDetailCollapsedGuideCardIcon(
                kind = targetStep?.kind ?: RouteDetailStepKind.START,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = targetStep?.title ?: origin.name.ifBlank { "출발지" },
                    style = MaterialTheme.typography.titleLarge,
                    color = RouteDetailCollapsedGuideCardContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = targetStep?.description ?: "출발지에서 안내를 시작합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RouteDetailCollapsedGuideCardContentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            }
        }
        }
            HorizontalDivider(
                modifier = Modifier.padding(start = RouteDetailCollapsedRailWidth),
                thickness = RouteDetailCollapsedGuideCardBottomStrokeWidth,
                color = RouteDetailCollapsedGuideCardBottomStrokeColor,
            )
        }
    }
}

@Composable
private fun RouteDetailCollapsedGuideCardIcon(
    kind: RouteDetailStepKind,
    modifier: Modifier = Modifier,
) {
    if (kind.usesLabeledWaypointPinIcon()) {
        Image(
            painter = painterResource(id = routeDetailStepIconRes(kind)),
            contentDescription = null,
            modifier = modifier.size(RouteDetailCollapsedGuideIconSize),
            contentScale = ContentScale.Fit,
        )
    } else {
        Icon(
            painter = painterResource(id = routeDetailStepIconRes(kind)),
            contentDescription = null,
            tint = RouteDetailCollapsedGuideCardContentColor,
            modifier = modifier.size(RouteDetailCollapsedGuideIconSize),
        )
    }
}

@Composable
private fun RouteDetailTransitGuideCardContent(
    step: RouteDetailStepUiState,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    secondaryContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    showIcon: Boolean = true,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcon) {
            Icon(
                painter = painterResource(id = routeDetailStepIconRes(step.kind)),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(RouteDetailCollapsedGuideIconSize),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = step.transitStartName ?: step.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = "->",
                    style = MaterialTheme.typography.labelMedium,
                    color = secondaryContentColor,
                )
                Text(
                    text = step.transitEndName ?: step.description,
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                step.transitDurationLabel?.let { duration ->
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                        maxLines = 1,
                    )
                }
            }
            RouteTransitOptionSummary(
                stopLabel = step.transitStartName ?: step.title,
                optionLabels = step.transitOptionLabels.ifEmpty { step.toFallbackTransitOptionLabels() },
            )
        }
    }
}

@Composable
private fun RouteDetailMapBottomSheet(
    selectedRoute: RouteSelectedRouteUiState,
    origin: RouteLocationUiState,
    onStepClick: (Int) -> Unit,
    onCloseClick: () -> Unit,
    completedLowFloorReservationKeys: Set<String> = emptySet(),
    onLowFloorReservationClick: (LowFloorBusReservation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetMaxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.62f
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = RouteDetailBottomSheetMinHeight, max = sheetMaxHeight),
        shape =
            RoundedCornerShape(
                topStart = RouteBottomSheetTopCornerRadius,
                topEnd = RouteBottomSheetTopCornerRadius,
            ),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = RouteBottomSheetElevation,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = EumSpacing.medium, vertical = EumSpacing.small),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
        ) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(RouteDetailBottomSheetHandleWidth)
                        .height(RouteDetailBottomSheetHandleHeight)
                        .background(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(RouteDetailBottomSheetHandleHeight / 2),
                        ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCloseClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_action_close),
                        contentDescription = stringResource(id = R.string.map_facility_detail_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
            ) {
                RouteDetailSummaryCard(selectedRoute = selectedRoute)
                RouteDetailTimelineBar(steps = selectedRoute.detailSteps)
                RouteDetailTransitActionRow(steps = selectedRoute.detailSteps)
                LowFloorReservationSection(
                    reservations = selectedRoute.lowFloorReservations,
                    completedReservationKeys = completedLowFloorReservationKeys,
                    onReservationClick = onLowFloorReservationClick,
                )
                RouteDetailStepsSection(
                    origin = origin,
                    steps = selectedRoute.detailSteps,
                    fallbackMessage = selectedRoute.detailFallbackMessage,
                    completedLowFloorReservationKeys = completedLowFloorReservationKeys,
                    onStepClick = onStepClick,
                )
            }
        }
    }
}

@Composable
private fun RouteDetailSidePanel(
    selectedRoute: RouteSelectedRouteUiState,
    origin: RouteLocationUiState,
    isExpanded: Boolean,
    focusedStepIndex: Int?,
    onExpandedChange: (Boolean) -> Unit,
    onStepClick: (Int) -> Unit,
    onTopVisibleStepChanged: (Int) -> Unit,
    completedLowFloorReservationKeys: Set<String>,
    onLowFloorReservationClick: (LowFloorBusReservation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelStateDescription =
        if (isExpanded) {
            "전체 안내 패널 펼쳐짐"
        } else {
            "전체 안내 패널 접힘"
        }

    GuideSidePanelShell(
        isExpanded = isExpanded,
        onExpandedChange = onExpandedChange,
        collapsedWidth = RouteDetailCollapsedRailWidth,
        stateDescription = panelStateDescription,
        modifier = modifier,
    ) {
        if (isExpanded) {
            RouteDetailTimelinePanelContent(
                origin = origin,
                steps = selectedRoute.detailSteps,
                badges = selectedRoute.badges,
                fallbackMessage = selectedRoute.detailFallbackMessage,
                lowFloorReservations = selectedRoute.lowFloorReservations,
                completedLowFloorReservationKeys = completedLowFloorReservationKeys,
                onStepClick = { index ->
                    onStepClick(index)
                },
                onLowFloorReservationClick = onLowFloorReservationClick,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            RouteDetailIconRail(
                steps = selectedRoute.detailSteps,
                focusedStepIndex = focusedStepIndex,
                onStepClick = onStepClick,
                onTopVisibleStepChanged = onTopVisibleStepChanged,
                modifier =
                    Modifier
                        .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun RouteDetailTimelinePanelContent(
    origin: RouteLocationUiState,
    steps: List<RouteDetailStepUiState>,
    badges: List<RouteOptionBadge>,
    fallbackMessage: String?,
    lowFloorReservations: List<LowFloorBusReservation>,
    completedLowFloorReservationKeys: Set<String>,
    onStepClick: (Int) -> Unit,
    onLowFloorReservationClick: (LowFloorBusReservation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val bottomBarOverlayClearance = routeSettingBottomBarOverlayClearance()
    val startStep = steps.firstOrNull()
    val renderedSteps =
        if (steps.size > 1) {
            steps.filterIndexed { index, _ -> index > 0 }
        } else {
            emptyList()
        }
    val stepsAccessibilityDescription =
        stringResource(
            id = R.string.route_setting_detail_steps_a11y,
            steps.size,
        )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = stepsAccessibilityDescription
                },
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = bottomBarOverlayClearance),
        ) {
            if (startStep == null) {
                item(key = "route-detail-empty") {
                    RouteDetailFallbackRow(
                        title = stringResource(id = R.string.route_setting_detail_steps_fallback_title),
                        description = stringResource(id = R.string.route_setting_detail_steps_supporting),
                    )
                }
            } else {
                val visibleBadges = badges.filterNot { badge -> badge == RouteOptionBadge.SAFE_PRIORITY }
                if (visibleBadges.isNotEmpty()) {
                    item(key = "route-detail-badges") {
                        RouteDetailBadgeHeader(badges = visibleBadges)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
                    }
                }
                item(key = "route-detail-origin") {
                    RouteDetailOriginStepRow(
                        origin = origin,
                        step = startStep,
                        onClick = { onStepClick(0) },
                    )
                }
                fallbackMessage?.takeIf(String::isNotBlank)?.let { message ->
                    item(key = "route-detail-fallback-message") {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
                        RouteDetailFallbackRow(
                            title = stringResource(id = R.string.route_setting_detail_steps_fallback_title),
                            description = message,
                            containerColor = MaterialTheme.colorScheme.surface,
                        )
                    }
                }
                if (renderedSteps.isEmpty()) {
                    item(key = "route-detail-fallback-empty") {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
                        RouteDetailFallbackRow(
                            title = stringResource(id = R.string.route_setting_detail_steps_fallback_title),
                            description = stringResource(id = R.string.route_setting_detail_steps_supporting),
                        )
                    }
                } else {
                    itemsIndexed(
                        items = renderedSteps,
                        key = { renderedIndex, step ->
                            "route-detail-step-${renderedIndex + 1}-${step.indexLabel}-${step.kind}"
                        },
                    ) { renderedIndex, step ->
                        val stepIndex = renderedIndex + 1
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
                        RouteDetailStepRow(
                            step = step,
                            onClick = { onStepClick(stepIndex) },
                        )
                    }
                }
            }
            item(key = "route-detail-low-floor-reservations") {
                LowFloorReservationSection(
                    reservations = lowFloorReservations,
                    completedReservationKeys = completedLowFloorReservationKeys,
                    onReservationClick = onLowFloorReservationClick,
                )
            }
            item(key = "route-detail-arrival-divider") {
                HorizontalDivider(color = RouteDetailGuideDividerColor)
            }
            item(key = "route-detail-scroll-top") {
                RouteDetailScrollTopAction(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = RouteDetailPanelBottomActionTopPadding),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteDetailBadgeHeader(
    badges: List<RouteOptionBadge>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(RouteDetailFeatureCardContainerColor)
                .padding(
                    horizontal = EumSpacing.large,
                    vertical = EumSpacing.medium,
                ),
        verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
    ) {
        Text(
            text = "경로 특징",
            style = MaterialTheme.typography.labelLarge.copy(fontSize = RouteDetailFeatureTitleFontSize),
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
        ) {
            badges.forEach { badge ->
                val (containerColor, contentColor) = routeAccessibilityLabelColors(badge)
                RouteBadgeChip(
                    label = routeBadgeText(badge),
                    containerColor = containerColor,
                    contentColor = contentColor,
                    borderColor = Color.Transparent,
                )
            }
        }
    }
}

@Composable
private fun RouteDetailScrollTopAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(vertical = RouteDetailScrollTopActionVerticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier
                    .size(RouteDetailScrollTopButtonSize)
                    .clickable(role = Role.Button, onClick = onClick)
                    .semantics {
                        contentDescription = "안내 목록 맨 위로 이동"
                    },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, RouteDetailGuideDividerColor),
            shadowElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_route_scroll_top),
                    contentDescription = null,
                    tint = RouteDetailGuideIconColor,
                    modifier = Modifier.size(RouteDetailScrollTopIconSize),
                )
            }
        }
    }
}

@Composable
private fun RouteDetailIconRail(
    steps: List<RouteDetailStepUiState>,
    focusedStepIndex: Int?,
    onStepClick: (Int) -> Unit,
    onTopVisibleStepChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrubberItems =
        remember(steps, focusedStepIndex) {
            steps.mapIndexed { index, step ->
                RouteStepScrubberItem(
                    index = index,
                    action = step.kind.toNavigationGuidanceAction(),
                    isOrigin = step.kind == RouteDetailStepKind.START,
                    isDestination = step.kind == RouteDetailStepKind.ARRIVAL,
                    contentDescription = "${step.title} ${step.description}",
                    stateDescription = if (focusedStepIndex == index) "focused guide step" else "guide step",
                )
            }
        }
    RouteStepScrubberRail(
        items = scrubberItems,
        focusedItemIndex = focusedStepIndex,
        onFocusedItemChanged = onTopVisibleStepChanged,
        onItemClick = onStepClick,
        itemHeight = RouteDetailCollapsedRailItemSize,
        trailingActionHeight = RouteDetailCollapsedRailItemSize,
        dividerColor = RouteDetailGuideDividerColor,
        trailingAction = {
            RouteDetailCollapsedRailScrollTopAction(
                onClick = { onStepClick(0) },
            )
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun RouteDetailCollapsedRailScrollTopAction(
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(RouteDetailCollapsedRailItemSize),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier
                    .size(RouteDetailCollapsedRailScrollTopButtonSize)
                    .clickable(role = Role.Button, onClick = onClick)
                    .semantics {
                        contentDescription = "?덈궡 紐⑸줉 留??꾨줈 ?대룞"
                    },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, RouteDetailGuideDividerColor),
            shadowElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_route_scroll_top),
                    contentDescription = null,
                    tint = RouteDetailGuideIconColor,
                    modifier = Modifier.size(RouteDetailCollapsedRailScrollTopIconSize),
                )
            }
        }
    }
}

@Composable
private fun RouteDetailTimelineBar(
    steps: List<RouteDetailStepUiState>,
) {
    val timelineSegments =
        steps
            .filterNot { step -> step.kind == RouteDetailStepKind.START || step.kind == RouteDetailStepKind.ARRIVAL }
            .ifEmpty { steps }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(RouteDetailTimelineBarHeight),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        timelineSegments.forEach { step ->
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            color = routeDetailTimelineColor(step.kind),
                            shape = RoundedCornerShape(RouteDetailTimelineBarHeight / 2),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (step.kind == RouteDetailStepKind.BUS || step.kind == RouteDetailStepKind.SUBWAY) {
                    Icon(
                        painter = painterResource(id = routeDetailStepIconRes(step.kind)),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteDetailTransitActionRow(
    steps: List<RouteDetailStepUiState>,
) {
    val hasTransit = steps.any { step -> step.kind == RouteDetailStepKind.BUS || step.kind == RouteDetailStepKind.SUBWAY }
    if (!hasTransit) {
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(RouteCompactChipCornerRadius),
            color = RouteTransitNavy.copy(alpha = 0.10f),
        ) {
            Text(
                text = "도착정보",
                modifier = Modifier.padding(horizontal = EumSpacing.small, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = RouteTransitNavy,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Surface(
            modifier = Modifier.size(RouteDetailRefreshButtonSize),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_status_refresh),
                    contentDescription = "도착정보 새로고침",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun LowFloorReservationSection(
    reservations: List<LowFloorBusReservation>,
    completedReservationKeys: Set<String> = emptySet(),
    onReservationClick: (LowFloorBusReservation) -> Unit,
) {
    if (reservations.isEmpty()) {
        return
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RouteStandardCardCornerRadius),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
    ) {
        Column(
            modifier = Modifier.padding(EumSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
        ) {
            Text(
                text = stringResource(id = R.string.route_setting_low_floor_reservation_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            reservations.take(MAX_LOW_FLOOR_RESERVATION_COUNT).forEach { reservation ->
                LowFloorReservationRow(
                    reservation = reservation,
                    isCompleted = reservation.stableReservationKey() in completedReservationKeys,
                    onReservationClick = onReservationClick,
                )
            }
        }
    }
}

@Composable
private fun LowFloorReservationRow(
    reservation: LowFloorBusReservation,
    isCompleted: Boolean,
    onReservationClick: (LowFloorBusReservation) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text =
                    stringResource(
                        id = R.string.route_setting_low_floor_reservation_summary,
                        reservation.routeNo,
                        reservation.vehicleNo,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    stringResource(
                        id = R.string.route_setting_low_floor_reservation_meta,
                        reservation.stopName,
                        reservation.remainingMinute,
                        reservation.remainingStopCount ?: 1,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = {
                if (!isCompleted) {
                    onReservationClick(reservation)
                }
            },
            enabled = !isCompleted,
            shape = RoundedCornerShape(EumRadius.small),
            colors =
                ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            modifier = Modifier.height(RouteInlineButtonHeight),
        ) {
            Text(
                text =
                    stringResource(
                        id =
                            if (isCompleted) {
                                R.string.route_setting_low_floor_reservation_completed
                            } else {
                                R.string.route_setting_low_floor_reservation_action
                            },
                    ),
            )
        }
    }
}

@Composable
private fun LowFloorReservationConfirmDialog(
    reservation: LowFloorBusReservation,
    isRequesting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isRequesting) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = stringResource(id = R.string.route_setting_low_floor_reservation_dialog_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                text =
                    stringResource(
                        id = R.string.route_setting_low_floor_reservation_dialog_message,
                        reservation.stopName,
                        reservation.routeNo,
                        reservation.vehicleNo,
                        reservation.remainingMinute,
                        reservation.remainingStopCount ?: 1,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isRequesting,
                shape = RoundedCornerShape(EumRadius.scaleM),
            ) {
                Text(
                    text =
                        stringResource(
                            id =
                                if (isRequesting) {
                                    R.string.route_setting_low_floor_reservation_dialog_loading
                                } else {
                                    R.string.route_setting_low_floor_reservation_dialog_confirm
                                },
                        ),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isRequesting,
            ) {
                Text(text = stringResource(id = R.string.route_setting_low_floor_reservation_dialog_dismiss))
            }
        },
    )
}

@Composable
private fun RouteDetailSummaryCard(
    selectedRoute: RouteSelectedRouteUiState,
) {
    val accessibilitySummary =
        if (selectedRoute.detailAccessibilityChips.isEmpty()) {
            stringResource(
                id = R.string.route_setting_detail_summary_a11y,
                selectedRoute.optionTitle,
                selectedRoute.estimatedTimeLabel,
                selectedRoute.distanceLabel,
                selectedRoute.riskLabel,
            )
        } else {
            stringResource(
                id = R.string.route_setting_detail_summary_a11y_with_accessibility,
                selectedRoute.optionTitle,
                selectedRoute.estimatedTimeLabel,
                selectedRoute.distanceLabel,
                selectedRoute.riskLabel,
                selectedRoute.detailAccessibilityChips.joinToString(separator = ", ") { chip -> chip.label },
            )
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibilitySummary
                    stateDescription = selectedRoute.summaryLabel
                },
        verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
    ) {
        RouteDetailMetricRow(selectedRoute = selectedRoute)
        RouteDetailChipSection(chips = selectedRoute.detailAccessibilityChips)
    }
}

@Composable
private fun RouteDetailMetricRow(
    selectedRoute: RouteSelectedRouteUiState,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RouteDetailMetricCard(
            title = stringResource(id = R.string.route_setting_detail_metric_time),
            value = selectedRoute.estimatedTimeLabel,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier =
                Modifier
                    .width(1.dp)
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        )
        RouteDetailMetricCard(
            title = stringResource(id = R.string.route_setting_detail_metric_distance),
            value = selectedRoute.distanceLabel,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RouteDetailMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .padding(horizontal = EumSpacing.small)
                .semantics(mergeDescendants = true) {
                    contentDescription = "$title $value"
                },
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RouteDetailChipSection(
    chips: List<RouteDetailChipUiState>,
) {
    if (chips.isEmpty()) {
        return
    }

    val accessibilityDescription =
        stringResource(
            id = R.string.route_setting_detail_accessibility_a11y,
            chips.joinToString(separator = ", ") { chip -> chip.label },
        )

    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibilityDescription
                },
        horizontalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
        verticalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
    ) {
        chips.forEach { chip ->
            RouteDetailSummaryChip(chip = chip)
        }
    }
}

@Composable
private fun RouteDetailSummaryChip(
    chip: RouteDetailChipUiState,
) {
    val (containerColor, contentColor) = routeDetailToneColors(tone = chip.tone)
    val stateDescription =
        when (chip.tone) {
            RouteDetailTone.WARNING -> stringResource(id = R.string.route_setting_detail_step_state_warning)
            RouteDetailTone.INFO -> stringResource(id = R.string.route_setting_detail_step_state_info)
            RouteDetailTone.NEUTRAL -> stringResource(id = R.string.route_setting_detail_step_state_default)
        }

    Surface(
        modifier =
            Modifier.semantics {
                contentDescription = chip.label
                this.stateDescription = stateDescription
            },
        shape = RoundedCornerShape(RouteCompactChipCornerRadius),
        color = containerColor.copy(alpha = if (chip.tone == RouteDetailTone.INFO) 0.72f else 0.92f),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = EumSpacing.small,
                    vertical = 6.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = routeDetailChipIconRes(kind = chip.kind)),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = contentColor,
            )
            Text(
                text = chip.label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun RouteDetailHighlightSection(
    highlights: List<RouteDetailHighlightUiState>,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
    ) {
        Text(
            text = stringResource(id = R.string.route_setting_detail_highlight_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (highlights.isEmpty()) {
            RouteStateCard(
                title = stringResource(id = R.string.route_setting_detail_highlight_empty_title),
                description = stringResource(id = R.string.route_setting_detail_highlight_empty_description),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(EumSpacing.small)) {
                highlights.forEach { highlight ->
                    RouteDetailHighlightCard(highlight = highlight)
                }
            }
        }
    }
}

@Composable
private fun RouteDetailHighlightCard(
    highlight: RouteDetailHighlightUiState,
) {
    val (containerColor, contentColor) = routeDetailToneColors(tone = highlight.tone)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RouteStandardCardCornerRadius),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.2f)),
    ) {
        Column(
            modifier = Modifier.padding(EumSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
        ) {
            RouteBadgeChip(
                label = highlight.badgeLabel,
                containerColor = contentColor.copy(alpha = 0.14f),
                contentColor = contentColor,
            )
            Text(
                text = highlight.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = highlight.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RouteDetailStepsSection(
    origin: RouteLocationUiState,
    steps: List<RouteDetailStepUiState>,
    fallbackMessage: String?,
    lowFloorReservations: List<LowFloorBusReservation> = emptyList(),
    completedLowFloorReservationKeys: Set<String> = emptySet(),
    onStepClick: (Int) -> Unit = {},
    onLowFloorReservationClick: (LowFloorBusReservation) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val startStep = steps.firstOrNull()
    val renderedSteps =
        if (steps.size > 1) {
            steps.filterIndexed { index, _ -> index > 0 }
        } else {
            emptyList()
        }
    val stepsAccessibilityDescription =
        stringResource(
            id = R.string.route_setting_detail_steps_a11y,
            steps.size,
        )

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = stepsAccessibilityDescription
                },
        verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
    ) {
        Text(
            text = stringResource(id = R.string.route_setting_detail_steps_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Surface(
            shape = RoundedCornerShape(RouteSectionCardCornerRadius),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
            shadowElevation = 0.dp,
        ) {
            Column {
                if (startStep == null) {
                    RouteDetailFallbackRow(
                        title = stringResource(id = R.string.route_setting_detail_steps_fallback_title),
                        description = stringResource(id = R.string.route_setting_detail_steps_supporting),
                    )
                } else {
                    RouteDetailOriginStepRow(
                        origin = origin,
                        step = startStep,
                        onClick = { onStepClick(0) },
                    )
                    fallbackMessage?.takeIf(String::isNotBlank)?.let { message ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
                        RouteDetailFallbackRow(
                            title = stringResource(id = R.string.route_setting_detail_steps_fallback_title),
                            description = message,
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
                        )
                    }
                    if (renderedSteps.isEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
                        RouteDetailFallbackRow(
                            title = stringResource(id = R.string.route_setting_detail_steps_fallback_title),
                            description = stringResource(id = R.string.route_setting_detail_steps_supporting),
                        )
                    } else {
                        renderedSteps.forEach { step ->
                            val stepIndex = steps.indexOf(step)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
                            RouteDetailStepRow(
                                step = step,
                                onClick = { onStepClick(stepIndex) },
                            )
                        }
                    }
                }
            }
        }
        LowFloorReservationSection(
            reservations = lowFloorReservations,
            completedReservationKeys = completedLowFloorReservationKeys,
            onReservationClick = onLowFloorReservationClick,
        )
    }
}

@Composable
private fun RouteDetailOriginStepRow(
    origin: RouteLocationUiState,
    step: RouteDetailStepUiState,
    onClick: () -> Unit = {},
) {
    val defaultStateDescription = stringResource(id = R.string.route_setting_detail_step_state_default)
    val accessibilityDescription =
        buildString {
            append(step.indexLabel)
            append(' ')
            append(step.title)
            append(". ")
            append(origin.name)
            origin.supportingText?.takeIf(String::isNotBlank)?.let { supportingText ->
                append(". ")
                append(supportingText)
            }
            append(". ")
            append(step.description)
        }

    GuideSidePanelStepRow(
        title = origin.name,
        description = origin.supportingText ?: step.description,
        action = step.kind.toNavigationGuidanceAction(),
        isOrigin = step.kind == RouteDetailStepKind.START,
        isDestination = step.kind == RouteDetailStepKind.ARRIVAL,
        leadingContentColor = RouteWaypointOriginColor,
        contentDescription = accessibilityDescription,
        stateDescription = defaultStateDescription,
        onClick = onClick,
        minHeight = RouteDetailGuideRowMinHeight,
    )
}

@Composable
private fun RouteDetailFallbackRow(
    title: String,
    description: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(containerColor)
                .padding(EumSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RouteDetailStepRow(
    step: RouteDetailStepUiState,
    onClick: () -> Unit = {},
) {
    val badgeContainerColor = RouteDetailGuideBadgeContainerColor
    val badgeContentColor = RouteDetailGuideBadgeContentColor
    val stepStateDescription =
        when (step.tone) {
            RouteDetailTone.WARNING -> stringResource(id = R.string.route_setting_detail_step_state_warning)
            RouteDetailTone.INFO -> stringResource(id = R.string.route_setting_detail_step_state_info)
            RouteDetailTone.NEUTRAL -> stringResource(id = R.string.route_setting_detail_step_state_default)
        }
    val stepAccessibilityDescription =
        buildString {
            append(step.indexLabel)
            append(' ')
            append(step.title)
            append(". ")
            append(step.description)
            step.metaLabel?.let { metaLabel ->
                append(' ')
                append(metaLabel)
            }
            step.badgeLabel?.let { badgeLabel ->
                append(". ")
                append(badgeLabel)
            }
        }

    GuideSidePanelStepRow(
        title = step.title,
        description = step.description,
        action = step.kind.toNavigationGuidanceAction(),
        isOrigin = step.kind == RouteDetailStepKind.START,
        isDestination = step.kind == RouteDetailStepKind.ARRIVAL,
        leadingContentColor = RouteDetailGuideIconColor,
        contentDescription = stepAccessibilityDescription,
        stateDescription = stepStateDescription,
        onClick = onClick,
        minHeight = RouteDetailGuideRowMinHeight,
        supportingContent = {
            step.badgeLabel?.let { badgeLabel ->
                RouteBadgeChip(
                    label = badgeLabel,
                    containerColor = badgeContainerColor,
                    contentColor = badgeContentColor,
                    borderColor = RouteDetailGuideBadgeBorderColor,
                )
            }
            if (step.kind == RouteDetailStepKind.BUS || step.kind == RouteDetailStepKind.SUBWAY) {
                RouteDetailTransitGuideCardContent(
                    step = step,
                    showIcon = false,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    secondaryContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun RouteDetailArrivalInfoChip(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(RouteDetailArrivalInfoChipRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, RouteDetailGuideDividerColor),
        shadowElevation = 0.dp,
    ) {
        Text(
            text = "상세정보",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun RouteDetailRowAccessoryIcon(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(RouteDetailRowAccessorySize),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, RouteDetailGuideDividerColor),
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(id = R.drawable.ic_route_detail_row_marker),
                contentDescription = null,
                tint = RouteDetailRowAccessoryIconColor,
                modifier = Modifier.size(RouteDetailRowAccessoryIconSize),
            )
        }
    }
}

@Composable
private fun RouteDetailTransitTagRow(step: RouteDetailStepUiState) {
    if (step.transitOptionLabels.isNotEmpty()) {
        RouteTransitOptionSummary(
            stopLabel = step.transitStartName ?: step.title,
            optionLabels = step.transitOptionLabels,
        )
        return
    }
    val transitLabel = step.transitLabel?.takeIf(String::isNotBlank) ?: return
    RouteTransitOptionSummary(
        stopLabel = step.title,
        optionLabels =
            listOf(
                RouteTransitOptionLabelUiState(
                    typeLabel = if (step.kind == RouteDetailStepKind.BUS) "일반" else "지하철",
                    routeNo = transitLabel,
                    arrivalLabel = step.metaLabel,
                ),
            ),
    )
}

private fun RouteDetailStepUiState.toFallbackTransitOptionLabels(): List<RouteTransitOptionLabelUiState> =
    transitLabel
        ?.takeIf(String::isNotBlank)
        ?.let { transitLabel ->
            listOf(
                RouteTransitOptionLabelUiState(
                    typeLabel = if (kind == RouteDetailStepKind.BUS) "일반" else "지하철",
                    routeNo = transitLabel,
                    arrivalLabel = metaLabel,
                ),
            )
        }
        .orEmpty()

private fun RouteDetailStepKind.isTransitStep(): Boolean =
    this == RouteDetailStepKind.BUS || this == RouteDetailStepKind.SUBWAY

private fun RouteDetailStepKind.toNavigationGuidanceAction(): NavigationGuidanceAction =
    when (this) {
        RouteDetailStepKind.ARRIVAL -> NavigationGuidanceAction.ARRIVAL
        RouteDetailStepKind.ALIGHT -> NavigationGuidanceAction.ALIGHT
        RouteDetailStepKind.BUS -> NavigationGuidanceAction.BUS
        RouteDetailStepKind.SUBWAY -> NavigationGuidanceAction.SUBWAY
        RouteDetailStepKind.CROSSWALK -> NavigationGuidanceAction.CROSSWALK
        RouteDetailStepKind.TURN_LEFT -> NavigationGuidanceAction.TURN_LEFT
        RouteDetailStepKind.TURN_RIGHT -> NavigationGuidanceAction.TURN_RIGHT
        RouteDetailStepKind.TACTILE_GUIDE -> NavigationGuidanceAction.TACTILE_GUIDE
        RouteDetailStepKind.ELEVATOR -> NavigationGuidanceAction.ELEVATOR
        RouteDetailStepKind.CONSTRUCTION -> NavigationGuidanceAction.CONSTRUCTION
        RouteDetailStepKind.CURB_GAP -> NavigationGuidanceAction.CURB_GAP
        RouteDetailStepKind.STAIRS -> NavigationGuidanceAction.STAIRS
        RouteDetailStepKind.FALLBACK -> NavigationGuidanceAction.FALLBACK
        else -> NavigationGuidanceAction.STRAIGHT
    }

@Composable
private fun RouteDetailStepLeadingIcon(
    kind: RouteDetailStepKind,
    tone: RouteDetailTone,
    contentColor: Color,
) {
    val usesLabeledWaypointPinIcon = kind.usesLabeledWaypointPinIcon()
    val usesBareTransitIcon = kind == RouteDetailStepKind.BUS || kind == RouteDetailStepKind.SUBWAY
    val containerColor =
        if (usesBareTransitIcon) {
            Color.Transparent
        } else if (usesLabeledWaypointPinIcon || tone == RouteDetailTone.NEUTRAL) {
            MaterialTheme.colorScheme.surface
        } else {
            contentColor.copy(alpha = 0.14f)
        }
    val borderColor =
        if (usesLabeledWaypointPinIcon) {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
        } else {
            contentColor.copy(alpha = 0.16f)
        }

    Surface(
        modifier = Modifier.size(RouteDetailStepLeadingIconContainerSize),
        shape = RoundedCornerShape(RouteStandardCardCornerRadius),
        color = containerColor,
        border = if (usesBareTransitIcon) null else BorderStroke(1.dp, borderColor),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (usesLabeledWaypointPinIcon) {
                Image(
                    painter = painterResource(id = routeDetailStepIconRes(kind = kind)),
                    contentDescription = null,
                    modifier = Modifier.size(width = RouteDetailWaypointPinWidth, height = RouteDetailWaypointPinHeight),
                    contentScale = ContentScale.FillBounds,
                )
            } else {
                Icon(
                    painter = painterResource(id = routeDetailStepIconRes(kind = kind)),
                    contentDescription = null,
                    modifier = Modifier.size(kind.leadingIconSize()),
                    tint =
                        if (kind.usesDirectionalStepIcon()) {
                            Color.Unspecified
                        } else {
                            contentColor
                        },
                )
            }
        }
    }
}

@Composable
private fun RouteSearchHeaderKakao(
    uiState: RouteSettingUiState,
    onBackClick: () -> Unit,
    onOriginClick: () -> Unit,
    onDestinationClick: () -> Unit,
    onSwapClick: () -> Unit,
    onModeSelected: (RouteTravelMode) -> Unit,
    showModeTabs: Boolean = true,
) {
    val layoutPolicy = routeSettingLayoutPolicy()
    val headerPolicy = routeSearchHeaderPolicy(showModeTabs = showModeTabs)
    val modeTabs = routeSearchHeaderModeTabPolicies()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = headerPolicy.containerColor,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        start = EumSpacing.medium,
                        top = RouteSearchHeaderVerticalPadding,
                        end = EumSpacing.medium,
                        bottom = headerPolicy.contentBottomPadding,
                    ),
            verticalArrangement = Arrangement.Top,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_action_back),
                        contentDescription = stringResource(id = R.string.route_setting_back),
                        tint = Color.White,
                    )
                }
                Text(
                    text = stringResource(id = headerPolicy.titleResId),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(RouteSearchHeaderTopToSummaryGap))
            Surface(
                modifier =
                    (
                        if (headerPolicy.summaryUsesFullWidth) {
                            Modifier.fillMaxWidth()
                        } else {
                            Modifier
                        }
                    )
                        .heightIn(min = headerPolicy.summaryMinHeight),
                shape = RoundedCornerShape(RouteSearchHeaderSummaryCornerRadius),
                color = headerPolicy.summaryContainerColor,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = EumSpacing.small, vertical = EumSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(RouteSearchHeaderWaypointGap),
                    ) {
                        RouteSearchHeaderWaypointLine(
                            roleLabel = layoutPolicy.originLabel,
                            waypoint = uiState.origin,
                            onClick = onOriginClick,
                        )
                        HorizontalDivider(color = RouteSearchHeaderDividerColor)
                        RouteSearchHeaderWaypointLine(
                            roleLabel = layoutPolicy.destinationLabel,
                            waypoint = uiState.destination,
                            onClick = onDestinationClick,
                        )
                    }
                    IconButton(onClick = onSwapClick) {
                        RouteWaypointSwapIcon(
                            color = RouteSearchHeaderAccentColor,
                            modifier = Modifier.size(width = RouteWaypointSwapIconWidth, height = RouteWaypointSwapIconHeight),
                        )
                    }
                }
            }
            if (showModeTabs) {
                Spacer(modifier = Modifier.height(headerPolicy.summaryToModeTabsGap))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    modeTabs.forEach { tabPolicy ->
                        RouteSearchHeaderModeTab(
                            label = stringResource(id = tabPolicy.labelResId),
                            modifier = Modifier.weight(1f),
                            iconResId = tabPolicy.iconResId,
                            iconSize = tabPolicy.iconSize,
                            selected = uiState.selectedTravelMode == tabPolicy.mode,
                            enabled = routeSearchHeaderModeTabEnabled(uiState, tabPolicy.mode),
                            onClick = { onModeSelected(tabPolicy.mode) },
                        )
                    }
                }
            }
        }
    }
}

private fun routeSearchHeaderModeTabEnabled(
    state: RouteSettingUiState,
    mode: RouteTravelMode,
): Boolean =
    !state.showsDuribalCallAction || mode == RouteTravelMode.TRANSIT

@Composable
private fun RouteSearchHeaderModeTab(
    modifier: Modifier = Modifier,
    label: String,
    iconResId: Int,
    iconSize: Dp,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val headerPolicy = routeSearchHeaderPolicy()
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .height(headerPolicy.modeTabHeight)
                .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
                .semantics {
                    contentDescription = label
                },
        shape = RoundedCornerShape(headerPolicy.modeTabCornerRadius),
        color = if (selected) headerPolicy.summaryContainerColor else headerPolicy.inactiveTabContainerColor,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = EumSpacing.small),
            horizontalArrangement = Arrangement.spacedBy(EumSpacing.xSmall, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint =
                    when {
                        selected -> RouteSearchHeaderAccentColor
                        enabled -> RouteSearchHeaderInactiveContentColor
                        else -> RouteSearchHeaderDisabledContentColor
                    },
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color =
                    when {
                        selected -> RouteSearchHeaderAccentColor
                        enabled -> RouteSearchHeaderInactiveContentColor
                        else -> RouteSearchHeaderDisabledContentColor
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RouteSearchHeaderWaypointLine(
    roleLabel: String,
    waypoint: RouteLocationUiState,
    onClick: () -> Unit,
) {
    val headerPolicy = routeSearchHeaderPolicy()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(headerPolicy.roleLabelGap),
    ) {
        Text(
            text = roleLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = routeSearchHeaderRoleLabelColor(roleLabel),
        )
        Text(
            text = waypoint.name.takeIf(String::isNotBlank) ?: roleLabel,
            style =
                if (headerPolicy.usesCompactWaypointTitle) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.titleMedium
                },
            color = RouteSearchHeaderEmphasizedContentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RouteScreenTopBar(
    title: String,
    onBackClick: () -> Unit,
) {
    val policy = routeScreenTopBarPolicy()
    EumCenteredTopBar(
        title = title,
        onBackClick = if (policy.showBackButton) onBackClick else null,
        backContentDescription =
            if (policy.showBackButton) {
                stringResource(id = R.string.route_setting_back)
            } else {
                null
            },
        titleFontWeight = policy.titleFontWeight,
    )
}

internal data class RouteScreenTopBarPolicy(
    val showBackButton: Boolean,
    val titleFontWeight: FontWeight,
)

internal fun routeScreenTopBarPolicy(): RouteScreenTopBarPolicy =
    RouteScreenTopBarPolicy(
        showBackButton = true,
        titleFontWeight = FontWeight.SemiBold,
    )

internal data class RouteSearchHeaderPolicy(
    @StringRes val titleResId: Int,
    val containerColor: Color,
    val summaryContainerColor: Color,
    val inactiveTabContainerColor: Color,
    val contentBottomPadding: Dp,
    val summaryMinHeight: Dp,
    val summaryToModeTabsGap: Dp,
    val modeTabHeight: Dp,
    val modeTabCornerRadius: Dp,
    val roleLabelGap: Dp,
    val usesCompactWaypointTitle: Boolean,
    val summaryUsesFullWidth: Boolean,
    val showsCloseAction: Boolean,
    val showsMoreAction: Boolean,
)

internal data class RouteSearchHeaderModeTabPolicy(
    val mode: RouteTravelMode,
    @StringRes val labelResId: Int,
    @DrawableRes val iconResId: Int,
    val iconSize: Dp,
)

internal fun routeSettingUsesEmptyWindowInsets(): Boolean = true

@Composable
private fun routeSettingBottomBarOverlayClearance(extraSpacing: Dp = EumSpacing.medium): Dp {
    val density = LocalDensity.current
    val navigationBarInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    return RouteSettingBottomBarButtonHeight + RouteSettingBottomBarBottomGap + extraSpacing + navigationBarInset
}

@Composable
private fun routeSettingHazardSheetBottomInset(): Dp {
    val density = LocalDensity.current
    return with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
}

internal fun routeSearchHeaderPolicy(showModeTabs: Boolean = true): RouteSearchHeaderPolicy =
    RouteSearchHeaderPolicy(
        titleResId = R.string.route_setting_screen_title,
        containerColor = RouteSearchHeaderContainerColor,
        summaryContainerColor = RouteSearchHeaderEmphasizedBoxColor,
        inactiveTabContainerColor = RouteSearchHeaderInactiveBoxColor,
        contentBottomPadding = if (showModeTabs) RouteSearchHeaderModeTabsBottomPadding else RouteSearchHeaderVerticalPadding,
        summaryMinHeight = RouteSearchHeaderSummaryMinHeight,
        summaryToModeTabsGap = RouteSearchHeaderSummaryToModeTabsGap,
        modeTabHeight = RouteSearchHeaderModeTabHeight,
        modeTabCornerRadius = RouteSearchHeaderModeTabCornerRadius,
        roleLabelGap = RouteSearchHeaderRoleLabelGap,
        usesCompactWaypointTitle = true,
        summaryUsesFullWidth = true,
        showsCloseAction = false,
        showsMoreAction = false,
    )

internal fun routeSearchHeaderModeTabPolicies(): List<RouteSearchHeaderModeTabPolicy> =
    listOf(
        RouteSearchHeaderModeTabPolicy(
            mode = RouteTravelMode.TRANSIT,
            labelResId = R.string.route_setting_travel_mode_transit,
            iconResId = R.drawable.ic_route_mode_transit,
            iconSize = RouteSearchHeaderTransitTabIconSize,
        ),
        RouteSearchHeaderModeTabPolicy(
            mode = RouteTravelMode.WALK,
            labelResId = R.string.route_setting_travel_mode_walk,
            iconResId = R.drawable.ic_route_mode_walk,
            iconSize = RouteSearchHeaderWalkTabIconSize,
        ),
    )

internal data class RouteSettingLayoutPolicy(
    val allowsDefaultVerticalScroll: Boolean,
    val ctaPlacement: RouteSettingCtaPlacement,
    val mapHeightPolicy: RouteSettingMapHeightPolicy,
    val maxVisibleOptionCards: Int,
    val showsOptionSectionSupportingText: Boolean,
    val originLabel: String,
    val destinationLabel: String,
    val showsWaypointSwapButton: Boolean,
    val waypointMarkerStyle: RouteWaypointMarkerStyle,
    val waypointConnectorStyle: RouteWaypointConnectorStyle,
    val showsWaypointDivider: Boolean,
    val waypointSwapButtonStyle: RouteWaypointSwapButtonStyle,
    val optionContainer: RouteSettingOptionContainer,
    val travelModeTabShape: RouteSettingTravelModeTabShape,
    val visibleAccessibilityChipCount: Int,
    val mapFillsRemainingCenterSpace: Boolean,
    val bottomSheetEdgeToEdge: Boolean,
    val sheetContainerColor: RouteSettingSheetContainerColor,
    val showsRecommendedBadge: Boolean,
    val startCtaIcon: RouteSettingStartCtaIcon,
    val startCtaIconTint: RouteSettingCtaIconTint,
    val bottomSheetFlushToWindowBottom: Boolean,
    val sheetElevation: RouteSettingSheetElevation,
    val sheetBorder: RouteSettingSheetBorder,
    val optionCardContainerColor: RouteSettingOptionCardContainerColor,
    val optionDetailButtonChrome: RouteSettingOptionDetailButtonChrome,
    val optionDetailButtonAlignment: RouteSettingOptionDetailButtonAlignment,
    val walkTabIcon: RouteSettingTravelModeIcon,
    val transitTabIcon: RouteSettingTravelModeIcon,
    val travelModeActiveColor: RouteSettingTravelModeActiveColor,
    val travelModeInactiveColor: RouteSettingTravelModeInactiveColor,
    val travelModeIconSize: RouteSettingTravelModeIconSize,
)

internal enum class RouteSettingCtaPlacement {
    BottomBar,
}

internal enum class RouteSettingMapHeightPolicy {
    FillRemainingCenterSpace,
}

internal enum class RouteWaypointMarkerStyle {
    LinkedPin,
}

internal enum class RouteWaypointConnectorStyle {
    VerticalLine,
}

internal enum class RouteWaypointSwapButtonStyle {
    Borderless,
}

internal enum class RouteSettingOptionContainer {
    BottomSheet,
}

internal enum class RouteSettingTravelModeTabShape {
    SegmentedPill,
}

internal enum class RouteSettingTravelModeIcon {
    WalkImage,
    TransitImage,
}

internal enum class RouteSettingTravelModeActiveColor {
    PrimaryBlue,
}

internal enum class RouteSettingTravelModeInactiveColor {
    Grey700,
}

internal enum class RouteSettingTravelModeIconSize {
    Emphasized,
}

internal enum class RouteSettingSheetContainerColor {
    White,
}

internal enum class RouteSettingStartCtaIcon {
    NavigationPointer,
}

internal enum class RouteSettingCtaIconTint {
    OnPrimary,
}

internal enum class RouteSettingSheetElevation {
    None,
}

internal enum class RouteSettingSheetBorder {
    None,
}

internal enum class RouteSettingOptionCardContainerColor {
    White,
}

internal enum class RouteSettingOptionDetailButtonChrome {
    Borderless,
}

internal enum class RouteSettingOptionDetailButtonAlignment {
    RightCenter,
}

internal fun routeSettingLayoutPolicy(): RouteSettingLayoutPolicy =
    RouteSettingLayoutPolicy(
        allowsDefaultVerticalScroll = false,
        ctaPlacement = RouteSettingCtaPlacement.BottomBar,
        mapHeightPolicy = RouteSettingMapHeightPolicy.FillRemainingCenterSpace,
        maxVisibleOptionCards = MAX_VISIBLE_OPTION_CARD_COUNT,
        showsOptionSectionSupportingText = false,
        originLabel = "출발",
        destinationLabel = "도착",
        showsWaypointSwapButton = true,
        waypointMarkerStyle = RouteWaypointMarkerStyle.LinkedPin,
        waypointConnectorStyle = RouteWaypointConnectorStyle.VerticalLine,
        showsWaypointDivider = false,
        waypointSwapButtonStyle = RouteWaypointSwapButtonStyle.Borderless,
        optionContainer = RouteSettingOptionContainer.BottomSheet,
        travelModeTabShape = RouteSettingTravelModeTabShape.SegmentedPill,
        visibleAccessibilityChipCount = MAX_VISIBLE_ROUTE_CHIP_COUNT,
        mapFillsRemainingCenterSpace = true,
        bottomSheetEdgeToEdge = true,
        sheetContainerColor = RouteSettingSheetContainerColor.White,
        showsRecommendedBadge = false,
        startCtaIcon = RouteSettingStartCtaIcon.NavigationPointer,
        startCtaIconTint = RouteSettingCtaIconTint.OnPrimary,
        bottomSheetFlushToWindowBottom = false,
        sheetElevation = RouteSettingSheetElevation.None,
        sheetBorder = RouteSettingSheetBorder.None,
        optionCardContainerColor = RouteSettingOptionCardContainerColor.White,
        optionDetailButtonChrome = RouteSettingOptionDetailButtonChrome.Borderless,
        optionDetailButtonAlignment = RouteSettingOptionDetailButtonAlignment.RightCenter,
        walkTabIcon = RouteSettingTravelModeIcon.WalkImage,
        transitTabIcon = RouteSettingTravelModeIcon.TransitImage,
        travelModeActiveColor = RouteSettingTravelModeActiveColor.PrimaryBlue,
        travelModeInactiveColor = RouteSettingTravelModeInactiveColor.Grey700,
        travelModeIconSize = RouteSettingTravelModeIconSize.Emphasized,
    )

@Composable
private fun RouteWaypointCard(
    origin: RouteLocationUiState,
    originStatus: RouteOriginStatusUiState?,
    destination: RouteLocationUiState,
    supportingMessage: String?,
    onOriginClick: () -> Unit,
    onDestinationClick: () -> Unit,
    onSwapClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val policy = routeSettingLayoutPolicy()
    val originPresentation = resolveOriginWaypointPresentation(
        name = origin.name,
        status = originStatus,
        supportingText = origin.supportingText,
    )
    val originLabel = stringResource(id = R.string.route_setting_origin_label)
    val destinationLabel = stringResource(id = R.string.route_setting_destination_label)
    val labelTextStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelColumnWidth =
        with(density) {
            maxOf(
                textMeasurer.measure(text = AnnotatedString(originLabel), style = labelTextStyle).size.width,
                textMeasurer.measure(text = AnnotatedString(destinationLabel), style = labelTextStyle).size.width,
            ).toDp()
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RouteSectionCardCornerRadius),
        color = RouteWaypointInputContainerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier.padding(
                    horizontal = EumSpacing.small,
                    vertical = RouteWaypointCardVerticalPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(RouteWaypointGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier =
                    Modifier
                            .weight(1f)
                            .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(RouteWaypointIndicatorGap),
                    verticalAlignment = Alignment.Top,
                ) {
                    RouteWaypointLinkedMarkers(
                        originColor = RouteWaypointOriginColor,
                        destinationColor = RouteWaypointDestinationColor,
                        markerStyle = policy.waypointMarkerStyle,
                        connectorStyle = policy.waypointConnectorStyle,
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .padding(vertical = RouteWaypointMarkerColumnVerticalInset),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(RouteWaypointGap),
                    ) {
                        RouteWaypointRow(
                            label = originLabel,
                            name = originPresentation.name,
                            status = originPresentation.status,
                            supportingText = originPresentation.supportingText,
                            labelWidth = labelColumnWidth,
                            onClick = onOriginClick,
                        )
                        if (policy.showsWaypointDivider) {
                            Spacer(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            )
                        }
                        RouteWaypointRow(
                            label = destinationLabel,
                            name = destination.name,
                            status = null,
                            supportingText = destination.supportingText,
                            labelWidth = labelColumnWidth,
                            onClick = onDestinationClick,
                        )
                    }
                }
                if (policy.showsWaypointSwapButton) {
                    RouteWaypointSwapButton(
                        onClick = onSwapClick,
                        style = policy.waypointSwapButtonStyle,
                    )
                }
            }
            supportingMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun resolveOriginWaypointPresentation(
    name: String,
    status: RouteOriginStatusUiState?,
    supportingText: String?,
): RouteWaypointPresentation {
    if (status?.label == CURRENT_LOCATION_WAYPOINT_NAME) {
        val resolvedSupportingText =
            (supportingText?.takeIf(String::isNotBlank) ?: name)
                .takeUnless { it == CURRENT_LOCATION_WAYPOINT_NAME }
        return RouteWaypointPresentation(
            name = CURRENT_LOCATION_WAYPOINT_NAME,
            supportingText = resolvedSupportingText,
            status = null,
        )
    }
    return RouteWaypointPresentation(
        name = name,
        supportingText = supportingText,
        status = status,
    )
}

private data class RouteWaypointPresentation(
    val name: String,
    val supportingText: String?,
    val status: RouteOriginStatusUiState?,
)

@Composable
private fun RouteWaypointLinkedMarkers(
    originColor: Color,
    destinationColor: Color,
    markerStyle: RouteWaypointMarkerStyle,
    connectorStyle: RouteWaypointConnectorStyle,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(RouteWaypointMarkerColumnWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RouteWaypointMarker(
            color = originColor,
            style = markerStyle,
        )
        Spacer(
            modifier =
                Modifier
                    .weight(1f)
                    .width(RouteWaypointConnectorWidth)
                    .background(
                        color =
                            when (connectorStyle) {
                                RouteWaypointConnectorStyle.VerticalLine -> RouteWaypointConnectorColor
                            },
                        shape = RoundedCornerShape(RouteWaypointConnectorWidth),
                    ),
        )
        RouteWaypointMarker(
            color = destinationColor,
            style = markerStyle,
        )
    }
}

@Composable
private fun RouteWaypointMarker(
    color: Color,
    style: RouteWaypointMarkerStyle,
    modifier: Modifier = Modifier,
) {
    when (style) {
        RouteWaypointMarkerStyle.LinkedPin -> RouteWaypointPinMarker(color = color, modifier = modifier)
    }
}

@Composable
private fun RouteWaypointPinMarker(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(id = R.drawable.ic_route_waypoint_pin),
        contentDescription = null,
        modifier = modifier.size(width = RouteWaypointPinWidth, height = RouteWaypointPinHeight),
        contentScale = ContentScale.FillBounds,
        colorFilter = ColorFilter.tint(color),
    )
}

@Composable
private fun RouteWaypointRow(
    label: String,
    name: String,
    status: RouteOriginStatusUiState?,
    supportingText: String?,
    labelWidth: Dp,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(RouteWaypointSupportingGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RouteWaypointTextGap),
        ) {
            Text(
                text = label,
                modifier =
                    Modifier
                        .width(labelWidth)
                        .alignByBaseline(),
                style = MaterialTheme.typography.labelMedium,
                color = RouteWaypointInputLabelColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = name,
                modifier =
                    Modifier
                        .weight(1f)
                        .alignByBaseline(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        supportingText?.takeIf(String::isNotBlank)?.let { value ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RouteWaypointTextGap),
            ) {
                Spacer(modifier = Modifier.width(labelWidth))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(RouteWaypointSupportingGap),
                ) {
                    status?.let { uiState ->
                        RouteOriginStatusText(uiState = uiState)
                    }
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelSmall,
                        color = RouteWaypointSupportingTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (status != null && supportingText.isNullOrBlank() && shouldShowStandaloneOriginStatus(status = status, name = name)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RouteWaypointTextGap),
            ) {
                Spacer(modifier = Modifier.width(labelWidth))
                RouteOriginStatusText(uiState = status)
            }
        }
    }
}

@Composable
private fun RouteOriginStatusText(
    uiState: RouteOriginStatusUiState,
) {
    val contentColor =
        when (uiState.tone) {
            RouteOriginStatusTone.INFO -> MaterialTheme.colorScheme.primary
            RouteOriginStatusTone.WARNING -> MaterialTheme.colorScheme.tertiary
            RouteOriginStatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Text(
        text = uiState.label,
        style = MaterialTheme.typography.labelSmall,
        color = contentColor,
        fontWeight = FontWeight.Medium,
    )
}

private fun shouldShowStandaloneOriginStatus(
    status: RouteOriginStatusUiState,
    name: String,
): Boolean = !name.contains(status.label)

@Composable
private fun RouteWaypointSwapButton(
    onClick: () -> Unit,
    style: RouteWaypointSwapButtonStyle,
) {
    val a11yLabel = stringResource(id = R.string.route_setting_waypoint_swap)

    Box(
        modifier =
            Modifier
                .size(width = RouteWaypointSwapButtonWidth, height = RouteWaypointSwapButtonHeight)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = a11yLabel
                },
        contentAlignment = Alignment.Center,
    ) {
        RouteWaypointSwapIcon(
            color =
                when (style) {
                    RouteWaypointSwapButtonStyle.Borderless -> MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

@Composable
private fun RouteWaypointSwapIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(width = RouteWaypointSwapIconWidth, height = RouteWaypointSwapIconHeight)) {
        val strokeWidth = RouteWaypointSwapIconStrokeWidth.toPx()
        routeWaypointSwapIconSegments(size).forEach { segment ->
            drawLine(
                color = color,
                start = segment.start,
                end = segment.end,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

internal data class RouteWaypointSwapSegment(
    val start: Offset,
    val end: Offset,
)

internal fun routeWaypointSwapIconSegments(size: Size): List<RouteWaypointSwapSegment> {
    val arrowHeadHalfWidth = size.width * 0.2f
    val upperStemX = size.width * 0.23f
    val lowerStemX = size.width * 0.77f
    val upperStemTopY = size.height * 0.12f
    val upperStemBottomY = size.height * 0.36f
    val upperHeadBaseY = size.height * 0.26f
    val lowerStemTopY = size.height * 0.68f
    val lowerStemBottomY = size.height * 0.92f
    val lowerHeadBaseY = size.height * 0.78f

    return listOf(
        RouteWaypointSwapSegment(
            start = Offset(x = upperStemX, y = upperStemBottomY),
            end = Offset(x = upperStemX, y = upperStemTopY),
        ),
        RouteWaypointSwapSegment(
            start = Offset(x = upperStemX, y = upperStemTopY),
            end = Offset(x = upperStemX - arrowHeadHalfWidth, y = upperHeadBaseY),
        ),
        RouteWaypointSwapSegment(
            start = Offset(x = upperStemX, y = upperStemTopY),
            end = Offset(x = upperStemX + arrowHeadHalfWidth, y = upperHeadBaseY),
        ),
        RouteWaypointSwapSegment(
            start = Offset(x = lowerStemX, y = lowerStemTopY),
            end = Offset(x = lowerStemX, y = lowerStemBottomY),
        ),
        RouteWaypointSwapSegment(
            start = Offset(x = lowerStemX, y = lowerStemBottomY),
            end = Offset(x = lowerStemX - arrowHeadHalfWidth, y = lowerHeadBaseY),
        ),
        RouteWaypointSwapSegment(
            start = Offset(x = lowerStemX, y = lowerStemBottomY),
            end = Offset(x = lowerStemX + arrowHeadHalfWidth, y = lowerHeadBaseY),
        ),
    )
}

@Composable
private fun RouteTravelModeTabs(
    selectedMode: RouteTravelMode,
    onModeSelected: (RouteTravelMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EumSpacing.xxSmall),
    ) {
        RouteTravelModeTab(
            label = stringResource(id = R.string.route_setting_travel_mode_walk),
            iconResId = R.drawable.ic_route_mode_walk,
            iconSize = RouteTravelModeWalkTabIconSize,
            isSelected = selectedMode == RouteTravelMode.WALK,
            modifier = Modifier.weight(1f),
            onClick = { onModeSelected(RouteTravelMode.WALK) },
        )
        RouteTravelModeTab(
            label = stringResource(id = R.string.route_setting_travel_mode_transit),
            iconResId = R.drawable.ic_route_mode_transit,
            iconSize = RouteTravelModeTransitTabIconSize,
            isSelected = selectedMode == RouteTravelMode.TRANSIT,
            modifier = Modifier.weight(1f),
            onClick = { onModeSelected(RouteTravelMode.TRANSIT) },
        )
    }
}

@Composable
private fun RouteTravelModeTab(
    label: String,
    iconResId: Int,
    iconSize: Dp,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier.clickable(
                role = Role.Button,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(RouteButtonCornerRadius),
        color =
            if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        border =
            BorderStroke(
                1.dp,
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                },
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = RouteTravelModeTabVerticalPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = routeTravelModeTabContentColor(isSelected = isSelected),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = routeTravelModeTabContentColor(isSelected = isSelected),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun routeTravelModeTabContentColor(isSelected: Boolean): Color =
    if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        RouteTravelModeInactiveContentColor
    }

private val RouteSettingUiState.currentLocationRecenterCoordinate: GeoCoordinate?
    get() =
        currentLocationCoordinate ?: origin.coordinate.takeIf {
            originState == RouteOriginState.CURRENT_LOCATION_RESOLVED
        }

private fun MapOverlayViewportControlState.recenterToCurrentLocationOrRoute(currentLocation: GeoCoordinate?) {
    if (currentLocation == null) {
        recenter()
        return
    }
    recenterToCurrentLocation(
        MapCoordinate(
            latitude = currentLocation.latitude,
            longitude = currentLocation.longitude,
        ),
    )
}

@Composable
private fun RouteMapStage(
    uiState: RouteSettingUiState,
    hazardOverlayPoints: List<MapViewportPointOverlay>,
    onHazardMarkerClick: (String) -> Boolean,
    onViewportBoundsChanged: (MapViewportBounds?) -> Unit,
    modifier: Modifier = Modifier,
    onOptionClick: (RouteOption) -> Unit = {},
    onOptionDetailClick: (RouteOption) -> Unit = {},
    onCurrentLocationClick: () -> Unit = {},
) {
    val selectedRoute = uiState.selectedRoute
    val previewMap = uiState.routePreviewMap
    val mapControlState = rememberMapOverlayViewportControlState()
    val navigationBarBottomInset =
        with(LocalDensity.current) {
            WindowInsets.navigationBars.getBottom(this).toDp()
        }
    val walkPreviewBottomPadding = routeWalkPreviewCarouselBottomPadding(navigationBarBottomInset)
    val walkPreviewMapBottomClearance = routeWalkPreviewMapBottomClearance(navigationBarBottomInset)
    val mapControlsBottomPadding = routeWalkMapControlsBottomPadding(navigationBarBottomInset)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RouteMapStageCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        border = null,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxSize(),
        ) {
            RouteMapBackdrop(
                previewMap = previewMap,
                routePath =
                    if (previewMap.isDisplayable) {
                        previewMap.polyline
                    } else {
                        emptyList()
                    },
                hazardOverlayPoints = hazardOverlayPoints,
                controlState = mapControlState,
                onViewportBoundsChanged = onViewportBoundsChanged,
                onMarkerClick = { markerId ->
                    onHazardMarkerClick(markerId)
                },
                originIsCurrentLocation = uiState.originState == RouteOriginState.CURRENT_LOCATION_RESOLVED,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = walkPreviewMapBottomClearance)
                        .clipToBounds()
                        .zIndex(RouteMapBackdropZIndex),
            )

            if (shouldShowRouteMapMessageCard(selectedRoute = selectedRoute, previewMap = previewMap)) {
                RouteMapMessageCard(
                    title = routePreviewFallbackTitle(previewMap.status),
                    description = routePreviewFallbackDescription(previewMap),
                    showNoRouteImage = previewMap.status == RoutePreviewMapStatus.NO_ROUTE,
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(EumSpacing.medium),
                )
            }

            val showsWalkPreviewCards = uiState.selectedTravelMode == RouteTravelMode.WALK && uiState.optionCards.isNotEmpty()
            val mapControlsModifier =
                if (showsWalkPreviewCards) {
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = EumSpacing.small,
                            bottom = mapControlsBottomPadding,
                        )
                } else {
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = EumSpacing.small)
                }
            if (showsWalkPreviewCards) {
                RouteMapZoomControls(
                    onZoomInClick = { mapControlState.zoomIn() },
                    onZoomOutClick = { mapControlState.zoomOut() },
                    modifier = mapControlsModifier.zIndex(RouteMapControlsZIndex),
                )
            } else {
                RouteMapControls(
                    onActionClick = {
                        onCurrentLocationClick()
                        mapControlState.recenterToCurrentLocationOrRoute(uiState.currentLocationRecenterCoordinate)
                    },
                    onZoomInClick = { mapControlState.zoomIn() },
                    onZoomOutClick = { mapControlState.zoomOut() },
                    modifier = mapControlsModifier.zIndex(RouteMapControlsZIndex),
                )
            }

            if (showsWalkPreviewCards) {
                RouteWalkPreviewCarousel(
                    optionCards = uiState.optionCards,
                    onOptionClick = onOptionClick,
                    onOptionDetailClick = onOptionDetailClick,
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(
                                start = EumSpacing.medium,
                                end = EumSpacing.medium,
                                bottom = walkPreviewBottomPadding,
                            )
                            .zIndex(RouteWalkPreviewCarouselZIndex),
                )
            }
        }
    }
}

private fun shouldShowRouteMapMessageCard(
    selectedRoute: RouteSelectedRouteUiState?,
    previewMap: RoutePreviewMapUiState,
): Boolean =
    previewMap.status != RoutePreviewMapStatus.NO_DESTINATION &&
        (selectedRoute == null || !previewMap.isDisplayable)

@Composable
private fun RouteMapMessageCard(
    title: String,
    description: String,
    showNoRouteImage: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.wrapContentWidth(),
        shape = RoundedCornerShape(RouteSectionCardCornerRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        shadowElevation = RouteOverlayCardElevation,
    ) {
        Column(
            modifier = Modifier.padding(EumSpacing.small),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = if (showNoRouteImage) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            if (showNoRouteImage) {
                RouteNoRouteIllustration(
                    visualOffsetY = RouteMapMessageIllustrationVisualOffset,
                    modifier = Modifier.size(RouteMapMessageIllustrationSize),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                textAlign = if (showNoRouteImage) TextAlign.Center else TextAlign.Start,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (showNoRouteImage) TextAlign.Center else TextAlign.Start,
            )
        }
    }
}

@Composable
private fun RouteWalkPreviewCarousel(
    optionCards: List<RouteOptionCardUiState>,
    onOptionClick: (RouteOption) -> Unit,
    onOptionDetailClick: (RouteOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleCards = optionCards.take(RouteWalkPreviewVisibleCardCount)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RouteWalkPreviewCardGap),
        verticalAlignment = Alignment.Bottom,
    ) {
        visibleCards.forEach { card ->
            RouteWalkPreviewSummaryCard(
                card = card,
                onClick = { onOptionClick(card.routeOption) },
                onDetailClick = { onOptionDetailClick(card.routeOption) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteWalkPreviewSummaryCard(
    card: RouteOptionCardUiState,
    onClick: () -> Unit,
    onDetailClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = optionAccentColor(card.routeOption)
    val visibleBadges = routeCardVisibleAccessibilityBadges(card.badges)

    Surface(
        modifier =
            modifier
                .heightIn(min = RouteWalkPreviewCardMinHeight)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    selected = card.isSelected
                    stateDescription = card.selectionLabel
                },
        shape = RoundedCornerShape(RouteSectionCardCornerRadius),
        color = Color.White.copy(alpha = 0.96f),
        border =
            BorderStroke(
                width = if (card.isSelected) 2.dp else 1.dp,
                color = if (card.isSelected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
            ),
        shadowElevation = RouteOverlayCardElevation,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            PaddingValues(
                                start = RouteWalkPreviewCardStartPadding,
                                top = RouteWalkPreviewCardVerticalPadding,
                                end = RouteWalkPreviewTopRowEndPadding,
                                bottom = RouteWalkPreviewTopRowBottomPadding,
                            ),
                        ),
                horizontalArrangement = Arrangement.spacedBy(RouteWalkPreviewContentGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(RouteWalkPreviewTextGap),
                ) {
                    Text(
                        text = walkPreviewLabel(card.routeOption),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        maxLines = 1,
                    )
                    Text(
                        text = compactEstimatedTimeLabel(card.estimatedTimeMinutes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = compactDistanceLabel(card.distanceMeters),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .size(RouteWalkPreviewChevronTouchTargetSize)
                            .clickable(role = Role.Button, onClick = onDetailClick)
                            .semantics {
                                contentDescription = "${walkPreviewLabel(card.routeOption)} 경로 상세 보기"
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_route_card_chevron),
                        contentDescription = null,
                        tint = RouteWalkPreviewChevronColor,
                        modifier = Modifier.size(RouteWalkPreviewChevronIconSize),
                    )
                }
            }
            RouteWalkPreviewBadgeRow(
                visibleBadges = visibleBadges,
                riskLevel = card.riskLevel,
                modifier =
                    Modifier.padding(
                        PaddingValues(
                            start = RouteWalkPreviewCardStartPadding,
                            end = RouteWalkPreviewBadgeHorizontalPadding,
                            bottom = RouteWalkPreviewCardVerticalPadding,
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun RouteWalkPreviewBadgeRow(
    visibleBadges: List<RouteOptionBadge>,
    riskLevel: RouteRiskLevel,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RouteAccessibilityLabelGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (visibleBadges.isEmpty()) {
            RouteRiskChip(riskLevel = riskLevel)
            Spacer(modifier = Modifier.weight(1f))
        } else {
            visibleBadges.forEach { badge ->
                RouteAccessibilityLabelChip(
                    label = routeBadgeText(badge),
                    badge = badge,
                    modifier = Modifier.weight(1f),
                )
            }
            if (visibleBadges.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun RouteSettingUiState.shouldShowRouteSheet(): Boolean =
    selectedTravelMode == RouteTravelMode.TRANSIT &&
        !shouldShowRouteLoadingScreen() &&
        !shouldShowRouteUnsupportedAreaScreen() &&
        !shouldShowRouteFailureScreen()

private fun RouteSettingUiState.shouldShowRouteLoadingScreen(): Boolean =
    isLoading && optionCards.isEmpty()

private fun RouteSettingUiState.shouldShowRouteUnsupportedAreaScreen(): Boolean =
    unsupportedArea != null

private fun RouteSettingUiState.shouldShowRouteFailureScreen(): Boolean =
    unsupportedArea == null &&
    selectedRoute == null &&
        (
            routePreviewMap.status == RoutePreviewMapStatus.NO_ROUTE ||
                routePreviewMap.status == RoutePreviewMapStatus.ERROR ||
                loadErrorMessage != null
        )

@Composable
private fun RouteMapControls(
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
                contentDescription = stringResource(id = R.string.route_setting_map_control_recenter),
                enabled = true,
            ),
        onActionClick = onActionClick,
        modifier = modifier,
        onZoomInClick = onZoomInClick,
        onZoomOutClick = onZoomOutClick,
    )
}

@Composable
private fun RouteMapZoomControls(
    onZoomInClick: () -> Unit,
    onZoomOutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(48.dp),
        shape = RoundedCornerShape(EumRadius.scaleS),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
        shadowElevation = 6.dp,
    ) {
        Column {
            RouteMapZoomControlButton(
                label = "+",
                onClick = onZoomInClick,
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
            )
            RouteMapZoomControlButton(
                label = "-",
                onClick = onZoomOutClick,
            )
        }
    }
}

@Composable
private fun RouteMapZoomControlButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable(
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 24.sp,
                ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RouteSettingTransitResultPane(
    uiState: RouteSettingUiState,
    completedLowFloorReservationKeys: Set<String>,
    onLowFloorReservationClick: (LowFloorBusReservation) -> Unit,
    onDuribalCallClick: () -> Unit,
    onDuribalCancelClick: () -> Unit,
    showDuribalCallPrompt: Boolean,
    onOptionClick: (RouteOption) -> Unit,
    onOptionDetailClick: (RouteOption) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomBarOverlayClearance = routeSettingBottomBarOverlayClearance()
    val scrollState = rememberScrollState()
    val pullRefreshConnection =
        rememberTransitRoutePullRefreshConnection(
            scrollState = scrollState,
            isRefreshing = uiState.isRouteRefreshing,
            onRefresh = onRefresh,
        )
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.White,
        shadowElevation = 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(pullRefreshConnection)
                        .verticalScroll(scrollState)
                        .padding(
                            start = EumSpacing.small,
                            end = EumSpacing.small,
                            top = RouteSettingSheetVerticalPadding,
                            bottom = bottomBarOverlayClearance,
                        ),
                verticalArrangement = Arrangement.spacedBy(RouteSettingSheetGap),
            ) {
                RouteOptionSection(
                    uiState = uiState,
                    completedLowFloorReservationKeys = completedLowFloorReservationKeys,
                    onLowFloorReservationClick = onLowFloorReservationClick,
                    onDuribalCallClick = onDuribalCallClick,
                    onDuribalCancelClick = onDuribalCancelClick,
                    showDuribalCallPrompt = showDuribalCallPrompt,
                    onOptionClick = onOptionClick,
                    onOptionDetailClick = onOptionDetailClick,
                )
            }
            if (uiState.isRouteRefreshing) {
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = EumSpacing.small)
                            .size(RouteTransitPullRefreshIndicatorSize),
                    strokeWidth = RouteTransitPullRefreshIndicatorStrokeWidth,
                )
            }
        }
    }
}

@Composable
private fun rememberTransitRoutePullRefreshConnection(
    scrollState: androidx.compose.foundation.ScrollState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
): NestedScrollConnection {
    val latestOnRefresh by rememberUpdatedState(onRefresh)
    val thresholdPx =
        with(LocalDensity.current) {
            RouteTransitPullRefreshThreshold.toPx()
        }
    var pullDistancePx by remember { mutableStateOf(0f) }

    return remember(scrollState, isRefreshing, thresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.Drag) return Offset.Zero
                if (available.y > 0f && scrollState.value == 0 && !isRefreshing) {
                    pullDistancePx += available.y
                    if (pullDistancePx >= thresholdPx) {
                        pullDistancePx = 0f
                        latestOnRefresh()
                    }
                } else if (available.y < 0f) {
                    pullDistancePx = (pullDistancePx + available.y).coerceAtLeast(0f)
                }
                return Offset.Zero
            }
        }
    }
}

@Composable
private fun RouteOptionSection(
    uiState: RouteSettingUiState,
    completedLowFloorReservationKeys: Set<String>,
    onLowFloorReservationClick: (LowFloorBusReservation) -> Unit,
    onDuribalCallClick: () -> Unit,
    onDuribalCancelClick: () -> Unit,
    showDuribalCallPrompt: Boolean,
    onOptionClick: (RouteOption) -> Unit,
    onOptionDetailClick: (RouteOption) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(RouteOptionCardGap),
    ) {
        when {
            uiState.isLoading && uiState.optionCards.isEmpty() -> RouteSearchLoadingState()

            uiState.routePreviewMap.status == RoutePreviewMapStatus.NO_ROUTE ->
                RouteFailureFallbackState(
                    title = stringResource(id = R.string.route_setting_no_route_result_title),
                    description = stringResource(id = R.string.route_setting_no_route_result_description),
                    onDuribalCallClick = onDuribalCallClick,
                )

            uiState.loadErrorMessage != null && uiState.showsDuribalCallAction && showDuribalCallPrompt ->
                RouteDuribalCallPromptCard(
                    onCallClick = onDuribalCallClick,
                    onCancelClick = onDuribalCancelClick,
                )

            uiState.loadErrorMessage != null ->
                RouteFailureFallbackState(
                    title = stringResource(id = R.string.route_setting_summary_error_title),
                    description = uiState.loadErrorMessage,
                    onDuribalCallClick = onDuribalCallClick,
                )

            uiState.optionCards.isEmpty() ->
                RouteStateCard(
                    title = stringResource(id = R.string.route_setting_summary_empty_title),
                    description = stringResource(id = R.string.route_setting_summary_empty_description),
                )

            else ->
                Column(verticalArrangement = Arrangement.spacedBy(RouteOptionCardGap)) {
                    uiState.optionCards.take(MAX_VISIBLE_OPTION_CARD_COUNT).forEach { optionCard ->
                        RouteCompactOptionCard(
                            card = optionCard,
                            onClick = { onOptionClick(optionCard.routeOption) },
                            onDetailClick = { onOptionDetailClick(optionCard.routeOption) },
                        )
                    }
                    uiState.selectedRoute?.let { selectedRoute ->
                        LowFloorReservationSection(
                            reservations = selectedRoute.lowFloorReservations,
                            completedReservationKeys = completedLowFloorReservationKeys,
                            onReservationClick = onLowFloorReservationClick,
                        )
                    }
                }
        }
    }
}

@Composable
private fun RouteLoadingScreen(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
    ) {
        RouteSettingLoadingState(
            title = stringResource(id = R.string.route_setting_summary_loading_title),
            description = stringResource(id = R.string.route_setting_summary_loading_description),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun RouteSettingLoadingState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = EumSpacing.large, vertical = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RouteUnsupportedAreaScreen(
    onSelectPlaceClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = EumSpacing.large, vertical = EumSpacing.xLarge)
                    .offset(y = -RouteFailureScreenContentOffset),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            RouteNoRouteIllustration(
                visualOffsetY = RouteFailureIllustrationVisualOffset,
                modifier = Modifier.size(RouteFailureScreenIllustrationSize),
            )
            Spacer(modifier = Modifier.height(RouteFailureImageToTextGap))
            Text(
                text = stringResource(id = R.string.route_setting_unsupported_area_title),
                style = routeFailureTitleTextStyle(),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(EumSpacing.small))
            Text(
                text = stringResource(id = R.string.route_setting_unsupported_area_description),
                style = routeFailureDescriptionTextStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(EumSpacing.large))
            Button(
                onClick = onSelectPlaceClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = RouteSettingBottomBarButtonHeight),
                shape = RoundedCornerShape(RouteStandardCardCornerRadius),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text(
                    text = stringResource(id = R.string.route_setting_unsupported_area_action),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun RouteFailureScreen(
    uiState: RouteSettingUiState,
    onDuribalCallClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isNoRoute = uiState.routePreviewMap.status == RoutePreviewMapStatus.NO_ROUTE
    val title =
        if (isNoRoute) {
            stringResource(id = R.string.route_setting_no_route_result_title)
        } else {
            stringResource(id = R.string.route_setting_summary_error_title)
        }
    val description =
        if (isNoRoute) {
            stringResource(id = R.string.route_setting_no_route_result_description)
        } else {
            uiState.loadErrorMessage ?: stringResource(id = R.string.route_setting_summary_error_title)
        }
    val showsDuribalCallAction = uiState.selectedTravelMode == RouteTravelMode.TRANSIT
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = EumSpacing.large, vertical = EumSpacing.xLarge)
                    .offset(y = -RouteFailureScreenContentOffset),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            RouteNoRouteIllustration(
                visualOffsetY = RouteFailureIllustrationVisualOffset,
                modifier = Modifier.size(RouteFailureScreenIllustrationSize),
            )
            Spacer(modifier = Modifier.height(RouteFailureImageToTextGap))
            Text(
                text = title,
                style = routeFailureTitleTextStyle(),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(EumSpacing.small))
            Text(
                text = description,
                style = routeFailureDescriptionTextStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(EumSpacing.large))
            if (showsDuribalCallAction) {
                Button(
                    onClick = onDuribalCallClick,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = RouteSettingBottomBarButtonHeight),
                    shape = RoundedCornerShape(RouteStandardCardCornerRadius),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                ) {
                    Text(
                        text = stringResource(id = R.string.route_setting_duribal_call_prompt_call),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteFailureFallbackState(
    title: String,
    description: String,
    onDuribalCallClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RouteStateCard(
        title = title,
        description = description,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.32f),
        actionLabel = stringResource(id = R.string.route_setting_duribal_call_prompt_call),
        onActionClick = onDuribalCallClick,
        useCompactFailureTextStyle = true,
        leadingContent = {
            RouteNoRouteIllustration(
                visualOffsetY = RouteFailureFallbackIllustrationVisualOffset,
                modifier = Modifier.size(RouteFailureFallbackIllustrationSize),
            )
        },
    )
}

@Composable
private fun RouteNoRouteIllustration(
    visualOffsetY: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(id = R.drawable.route_no_route_error_illustration),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.offset(y = visualOffsetY),
    )
}

@Composable
private fun routeFailureTitleTextStyle() =
    MaterialTheme.typography.titleLarge.copy(
        fontSize = RouteFailureTitleFontSize,
        lineHeight = RouteFailureTitleLineHeight,
    )

@Composable
private fun routeFailureDescriptionTextStyle() =
    MaterialTheme.typography.bodyMedium.copy(
        fontSize = RouteFailureDescriptionFontSize,
        lineHeight = RouteFailureDescriptionLineHeight,
    )

@Composable
private fun RouteDuribalCallPromptCard(
    onCallClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RouteStandardCardCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(EumSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
        ) {
            Text(
                text = stringResource(id = R.string.route_setting_duribal_call_prompt_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(id = R.string.route_setting_duribal_call_prompt_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EumSpacing.small, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancelClick) {
                    Text(text = stringResource(id = R.string.route_setting_duribal_call_prompt_cancel))
                }
                Button(
                    onClick = onCallClick,
                    shape = RoundedCornerShape(RouteButtonCornerRadius),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = EumPrimary600,
                            contentColor = EumWhite,
                        ),
                    elevation =
                        ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp,
                            disabledElevation = 0.dp,
                        ),
                ) {
                    Text(text = stringResource(id = R.string.route_setting_duribal_call_prompt_call))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteCompactOptionCard(
    card: RouteOptionCardUiState,
    onClick: () -> Unit,
    onDetailClick: () -> Unit,
) {
    val accentColor = optionAccentColor(card.routeOption)
    val titleColor = if (card.isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val isTransitCard = card.travelMode == RouteTravelMode.TRANSIT
    val containerColor = Color.White
    val borderColor = if (card.isSelected) accentColor.copy(alpha = 0.72f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
    val detailArrowColor = if (card.isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
    val visibleAccessibilityBadges = routeCardVisibleAccessibilityBadges(card.badges)
    val detailContentDescription =
        stringResource(
            id = R.string.route_setting_card_detail_a11y,
            card.title,
        )
    val cardContentDescription =
        stringResource(
            id =
                if (card.isSelected) {
                    R.string.route_setting_card_a11y_selected
                } else {
                    R.string.route_setting_card_a11y_available
                },
            card.title,
            card.summaryLabel,
        )

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    selected = card.isSelected
                    stateDescription = card.selectionLabel
                    contentDescription = cardContentDescription
                }
                .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(RouteSectionCardCornerRadius),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = EumSpacing.small,
                        vertical = RouteOptionCardVerticalPadding,
                    ),
            horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RouteOptionPrefixBadge(
                        label = routeOptionCompactPrefix(card.routeOption),
                        accentColor = accentColor,
                        isEmphasized = isTransitCard,
                    )
                    if (!isTransitCard) {
                        Text(
                            text = card.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = compactEstimatedTimeLabel(card.estimatedTimeMinutes),
                        style =
                            if (isTransitCard) {
                                MaterialTheme.typography.titleSmall.copy(fontSize = RouteTransitOptionEstimatedTimeFontSize)
                            } else {
                                MaterialTheme.typography.titleSmall
                            },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = compactDistanceLabel(card.distanceMeters),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (isTransitCard) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
                        verticalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
                    ) {
                        card.badges.forEach { badge ->
                            RouteBadgeChip(
                                label = routeBadgeText(badge),
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                contentColor = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(RouteAccessibilityLabelGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (visibleAccessibilityBadges.isEmpty()) {
                            RouteRiskChip(riskLevel = card.riskLevel)
                            Spacer(modifier = Modifier.weight(1f))
                        } else {
                            visibleAccessibilityBadges.forEach { badge ->
                                RouteAccessibilityLabelChip(
                                    label = routeBadgeText(badge),
                                    badge = badge,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (visibleAccessibilityBadges.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (isTransitCard && card.segmentBars.isNotEmpty()) {
                    RouteTransitSegmentRatioBar(
                        segmentBars = card.segmentBars,
                        modifier = Modifier.padding(top = EumSpacing.small),
                    )
                }
                if (card.transitStopLabel != null || card.transitOptionLabels.isNotEmpty()) {
                    RouteTransitOptionSummary(
                        stopLabel = card.transitStopLabel,
                        optionLabels = card.transitOptionLabels,
                    )
                }
            }
            RouteOptionDetailArrowButton(
                a11yLabel = detailContentDescription,
                accentColor = detailArrowColor,
                onClick = onDetailClick,
            )
        }
    }
}

@Composable
private fun RouteSearchLoadingState() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(RouteSearchLoadingHeight),
        contentAlignment = Alignment.Center,
    ) {
        RouteSettingLoadingState(
            title = stringResource(id = R.string.route_setting_summary_loading_title),
            description = stringResource(id = R.string.route_setting_summary_loading_description),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun RouteTransitResultControls(optionCards: List<RouteOptionCardUiState>) {
    val busCount =
        optionCards.count { card ->
            card.segmentBars.any { segment -> segment.kind == RouteOptionSegmentKind.BUS }
        }
    val subwayCount =
        optionCards.count { card ->
            card.segmentBars.any { segment -> segment.kind == RouteOptionSegmentKind.SUBWAY }
        }
    val mixedCount =
        optionCards.count { card ->
            card.segmentBars.any { segment -> segment.kind == RouteOptionSegmentKind.BUS } &&
                card.segmentBars.any { segment -> segment.kind == RouteOptionSegmentKind.SUBWAY }
        }

    Column(verticalArrangement = Arrangement.spacedBy(EumSpacing.xSmall)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EumSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RouteTransitFilterLabel(label = "전체 ${optionCards.size}", selected = true)
            RouteTransitFilterLabel(label = "버스 $busCount", selected = false)
            RouteTransitFilterLabel(label = "지하철 $subwayCount", selected = false)
            RouteTransitFilterLabel(label = "버스+지하철 $mixedCount", selected = false)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "현재 출발",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "추천순",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RouteTransitFilterLabel(
    label: String,
    selected: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier =
                Modifier
                    .width(28.dp)
                    .height(2.dp)
                    .background(
                        color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = RoundedCornerShape(1.dp),
                    ),
        )
    }
}

@Composable
private fun RouteTransitSegmentRatioBar(
    segmentBars: List<RouteOptionSegmentBarUiState>,
    modifier: Modifier = Modifier,
) {
    val bars = segmentBars.take(MAX_TRANSIT_SEGMENT_BAR_COUNT)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(RouteTransitSegmentTimelineRadius))
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(RouteTransitSegmentTimelineRadius),
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            bars.forEach { segment ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .weight(segment.weight)
                            .background(color = routeSegmentBarColor(segment)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RouteTransitSegmentStartIcon(segment = segment)
                        Text(
                            text = segment.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = routeSegmentBarContentColor(segment.kind),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteTransitSegmentStartIcon(segment: RouteOptionSegmentBarUiState) {
    when (segment.kind) {
        RouteOptionSegmentKind.WALK -> Unit
        RouteOptionSegmentKind.BUS ->
            Surface(
                modifier = Modifier.size(20.dp),
                shape = RoundedCornerShape(5.dp),
                color = RouteTransitNavy,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_place_bus),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(3.dp),
                )
            }

        RouteOptionSegmentKind.SUBWAY ->
            Surface(
                modifier = Modifier.size(20.dp),
                shape = RoundedCornerShape(5.dp),
                color = subwayLineColor(segment.routeLabel),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = subwayLineShortLabel(segment.routeLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
    }
}

@Composable
private fun RouteTransitOptionSummary(
    stopLabel: String?,
    optionLabels: List<RouteTransitOptionLabelUiState>,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .semantics {
                    contentDescription = stopLabel ?: "대중교통 도착 정보"
                },
        horizontalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        optionLabels.forEach { option ->
            RouteTransitOptionChip(option = option)
        }
    }
}

@Composable
private fun RouteTransitOptionChip(option: RouteTransitOptionLabelUiState) {
    Surface(
        shape = RoundedCornerShape(RouteTransitTagCornerRadius),
        color = Color.White,
        border = BorderStroke(RouteTransitTagStrokeWidth, RouteTransitTagBorderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(RouteTransitTagCornerRadius),
                color = routeTransitOptionTypeColor(option.typeLabel),
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
                color = RouteTransitTagRouteNumberColor,
                maxLines = 1,
            )
            option.arrivalLabel?.let { arrival ->
                Text(
                    text = arrival,
                    style = MaterialTheme.typography.labelMedium,
                    color = RouteTransitTagArrivalColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RouteOptionPrefixBadge(
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    isEmphasized: Boolean = false,
) {
    Text(
        text = label,
        modifier = modifier,
        style =
            if (isEmphasized) {
                MaterialTheme.typography.labelSmall.copy(fontSize = RouteTransitOptionTitleFontSize)
            } else {
                MaterialTheme.typography.labelSmall
            },
        fontWeight = FontWeight.SemiBold,
        color = accentColor,
    )
}

@Composable
private fun RouteOptionDetailArrowButton(
    a11yLabel: String,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier =
            Modifier
                .size(RouteOptionDetailButtonTouchTargetSize)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics {
                    contentDescription = a11yLabel
                },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_control_next),
            contentDescription = null,
            modifier = Modifier.size(RouteOptionDetailButtonIconSize),
            tint = accentColor,
        )
    }
}

/*
@Composable
private fun RouteOptionDetailButton(
    a11yLabel: String,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(RouteOptionDetailButtonTouchTargetSize)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = a11yLabel
                },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "›",
                color = accentColor,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
*/

@Composable
private fun RouteMetricTile(
    label: String,
    value: String,
    accentColor: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(RouteStandardCardCornerRadius),
        color =
            if (isSelected) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        border =
            BorderStroke(
                1.dp,
                if (isSelected) accentColor.copy(alpha = 0.32f) else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = EumSpacing.small, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RouteSettingBottomBar(
    buttonLabel: String,
    enabled: Boolean,
    supportingText: String?,
    selectedRoute: RouteSelectedRouteUiState?,
    onStartClick: () -> Unit,
    onDisabledStartClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier =
                Modifier
                    .navigationBarsPadding()
                    .padding(
                        start = RouteSettingBottomBarHorizontalPadding,
                        end = RouteSettingBottomBarHorizontalPadding,
                        top = RouteSettingBottomBarTopGap,
                        bottom = RouteSettingBottomBarBottomGap,
                    ),
        ) {
            RouteSettingCtaContent(
                buttonLabel = buttonLabel,
                enabled = enabled,
                supportingText = supportingText,
                selectedRoute = selectedRoute,
                buttonHeight = RouteSettingBottomBarButtonHeight,
                verticalGap = EumSpacing.small,
                compactSupportingText = false,
                onStartClick = onStartClick,
                onDisabledStartClick = onDisabledStartClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RouteSettingCtaContent(
    buttonLabel: String,
    enabled: Boolean,
    supportingText: String?,
    selectedRoute: RouteSelectedRouteUiState?,
    buttonHeight: Dp,
    verticalGap: Dp,
    compactSupportingText: Boolean,
    onStartClick: () -> Unit,
    onDisabledStartClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctaContentDescription =
        when {
            enabled && selectedRoute != null ->
                stringResource(
                    id = R.string.route_setting_cta_a11y_enabled,
                    buttonLabel,
                    selectedRoute.optionTitle,
                    selectedRoute.summaryLabel,
                )

            supportingText != null ->
                stringResource(
                    id = R.string.route_setting_cta_a11y_disabled,
                    buttonLabel,
                    supportingText,
                )

            else -> buttonLabel
        }
    val ctaStateDescription =
        if (enabled) {
            stringResource(id = R.string.route_setting_cta_state_enabled)
        } else {
            supportingText ?: stringResource(id = R.string.route_setting_cta_state_disabled)
        }
    val interactionSource = remember { MutableInteractionSource() }
    val buttonShape = RoundedCornerShape(RouteButtonCornerRadius)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(verticalGap),
    ) {
        supportingText?.let { text ->
            Text(
                text = text,
                style =
                    if (compactSupportingText) {
                        MaterialTheme.typography.labelSmall
                    } else {
                        MaterialTheme.typography.bodySmall
                    },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (compactSupportingText) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(buttonHeight)
                    .semantics {
                        contentDescription = ctaContentDescription
                        stateDescription = ctaStateDescription
                    }
                    .clip(buttonShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = if (enabled) onStartClick else onDisabledStartClick,
                    ),
            shape = buttonShape,
            color = routeSettingCtaContainerColor(enabled = enabled),
            contentColor = routeSettingCtaContentColor(enabled = enabled),
            border =
                if (enabled) {
                    null
                } else {
                    BorderStroke(1.dp, EumBorderSubtle)
                },
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = EumSpacing.medium),
                horizontalArrangement = Arrangement.spacedBy(EumSpacing.xSmall, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_route_start_navigation_button),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = routeSettingCtaContentColor(enabled = enabled),
                )
                Text(
                    text = buttonLabel,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun routeSettingCtaContainerColor(enabled: Boolean): Color =
    if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        EumSurfaceMuted
    }

@Composable
private fun routeSettingCtaContentColor(enabled: Boolean): Color =
    if (enabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        EumTextTertiary
    }

@Composable
private fun RouteMapBackdrop(
    previewMap: RoutePreviewMapUiState,
    routePath: List<GeoCoordinate>,
    detailPolylines: List<RouteDetailPolylineUiState> = emptyList(),
    travelMode: RouteTravelMode = RouteTravelMode.WALK,
    guidanceMarkers: List<MapViewportPointOverlay> = emptyList(),
    hazardOverlayPoints: List<MapViewportPointOverlay> = emptyList(),
    controlState: MapOverlayViewportControlState? = null,
    onViewportBoundsChanged: (MapViewportBounds?) -> Unit = {},
    originIsCurrentLocation: Boolean = false,
    onMarkerClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val mapDescription = stringResource(id = R.string.route_setting_preview_title)
    val hasFocusedGuidanceMarker = guidanceMarkers.any { marker -> marker.isSelected }
    val routePolylineOverlays =
        detailPolylines.toRouteDetailPolylineOverlays(
            includeInProjection = !hasFocusedGuidanceMarker,
            showDirectionArrows = true,
            travelMode = travelMode,
        )
    val shouldShowRouteDirectionArrows = routePolylineOverlays.isNotEmpty() || hasFocusedGuidanceMarker
    val overlayState =
        createRoutePreviewViewportOverlayState(
            previewMap =
                previewMap.copy(
                    polyline =
                        if (routePath.isNotEmpty()) {
                            routePath
                        } else {
                            previewMap.polyline
                        },
                ),
            routePolylineOverlays = routePolylineOverlays,
            guidanceMarkers = guidanceMarkers,
            originIsCurrentLocation = originIsCurrentLocation,
            focusSelectedGuidanceMarker = hasFocusedGuidanceMarker,
            showDetailedRouteOverlay = shouldShowRouteDirectionArrows,
        ).let { baseOverlayState ->
            baseOverlayState.copy(points = baseOverlayState.points + hazardOverlayPoints)
        }
    MapOverlayViewport(
        overlayState = overlayState,
        modifier = modifier,
        contentDescription = mapDescription,
        onMarkerClick = onMarkerClick,
        onViewportBoundsChanged = onViewportBoundsChanged,
        controlState = controlState,
    )
}

private fun List<RouteDetailPolylineUiState>.toRouteDetailPolylineOverlays(
    includeInProjection: Boolean,
    showDirectionArrows: Boolean,
    travelMode: RouteTravelMode,
): List<MapViewportPolylineOverlay> =
    mapIndexedNotNull { index, detailPolyline ->
        if (detailPolyline.points.size < 2) return@mapIndexedNotNull null
        MapViewportPolylineOverlay(
            overlayId = "route-detail-${detailPolyline.kind.name.lowercase(Locale.US)}-$index",
            points =
                detailPolyline.points.map { point ->
                    MapCoordinate(latitude = point.latitude, longitude = point.longitude)
                },
            style = MapViewportPolylineStyle.ROUTE_PREVIEW,
            tone = detailPolyline.kind.toMapViewportOverlayTone(travelMode),
            includeInProjection = includeInProjection,
            showDirectionArrows = showDirectionArrows,
        )
    }

private fun RouteDetailPolylineKind.toMapViewportOverlayTone(travelMode: RouteTravelMode): MapViewportOverlayTone =
    when (this) {
        RouteDetailPolylineKind.WALK ->
            if (travelMode == RouteTravelMode.TRANSIT) {
                MapViewportOverlayTone.TRANSIT_WALK
            } else {
                MapViewportOverlayTone.NAVIGATION_WALK
            }

        RouteDetailPolylineKind.TRANSIT -> MapViewportOverlayTone.NAVY
    }

private fun RouteOption?.toRouteDetailTravelMode(): RouteTravelMode =
    when (this) {
        RouteOption.SAFE,
        RouteOption.SHORTEST,
        null,
            -> RouteTravelMode.WALK

        RouteOption.RECOMMENDED,
        RouteOption.MIN_TRANSFER,
        RouteOption.MIN_WALK,
            -> RouteTravelMode.TRANSIT
    }

@Composable
private fun RoutePreviewMapMarker(
    label: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(RoutePreviewMarkerSize),
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
private fun routePreviewFallbackTitle(status: RoutePreviewMapStatus): String =
    when (status) {
        RoutePreviewMapStatus.LOADING -> stringResource(id = R.string.route_setting_preview_loading_title)
        RoutePreviewMapStatus.NO_DESTINATION -> stringResource(id = R.string.route_setting_preview_no_destination_title)
        RoutePreviewMapStatus.INVALID_DESTINATION -> stringResource(id = R.string.route_setting_preview_invalid_destination_title)
        RoutePreviewMapStatus.NO_ROUTE -> stringResource(id = R.string.route_setting_preview_no_route_title)
        RoutePreviewMapStatus.POLYLINE_UNAVAILABLE -> stringResource(id = R.string.route_setting_preview_placeholder_title)
        RoutePreviewMapStatus.ERROR -> stringResource(id = R.string.route_setting_preview_error_title)
        RoutePreviewMapStatus.READY -> stringResource(id = R.string.route_setting_preview_title)
    }

@Composable
private fun routePreviewFallbackDescription(previewMap: RoutePreviewMapUiState): String =
    when (previewMap.status) {
        RoutePreviewMapStatus.LOADING -> stringResource(id = R.string.route_setting_preview_loading_description)
        RoutePreviewMapStatus.NO_DESTINATION -> stringResource(id = R.string.route_setting_preview_no_destination_description)
        RoutePreviewMapStatus.INVALID_DESTINATION -> stringResource(id = R.string.route_setting_preview_invalid_destination_description)
        RoutePreviewMapStatus.NO_ROUTE -> stringResource(id = R.string.route_setting_preview_no_route_description)
        RoutePreviewMapStatus.POLYLINE_UNAVAILABLE -> stringResource(id = R.string.route_setting_preview_placeholder_description)
        RoutePreviewMapStatus.ERROR ->
            previewMap.fallbackMessage ?: stringResource(id = R.string.route_setting_preview_error_description)
        RoutePreviewMapStatus.READY -> stringResource(id = R.string.route_setting_preview_placeholder_description)
    }

@Composable
private fun RouteRiskChip(
    riskLevel: RouteRiskLevel,
) {
    val (containerColor, contentColor) =
        when (riskLevel) {
            RouteRiskLevel.LOW ->
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) to MaterialTheme.colorScheme.primary

            RouteRiskLevel.MEDIUM ->
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f) to MaterialTheme.colorScheme.tertiary

            RouteRiskLevel.HIGH ->
                MaterialTheme.colorScheme.error.copy(alpha = 0.10f) to MaterialTheme.colorScheme.error
        }

    RouteBadgeChip(
        label = riskLevelText(riskLevel = riskLevel),
        containerColor = containerColor,
        contentColor = contentColor,
        borderColor = contentColor.copy(alpha = 0.22f),
    )
}

@Composable
private fun RouteBadgeChip(
    label: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.26f),
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(RouteCompactChipCornerRadius),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
    ) {
        Text(
            text = label,
            modifier =
                Modifier.padding(
                    horizontal = EumSpacing.small,
                    vertical = EumSpacing.xSmall,
                ),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RouteAccessibilityLabelChip(
    label: String,
    badge: RouteOptionBadge,
    modifier: Modifier = Modifier,
) {
    val (containerColor, contentColor) = routeAccessibilityLabelColors(badge)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RouteAccessibilityLabelCornerRadius),
        color = containerColor,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = RouteAccessibilityLabelMinHeight)
                    .padding(horizontal = RouteAccessibilityLabelHorizontalPadding, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RouteStateCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    useCompactFailureTextStyle: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RouteStandardCardCornerRadius),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(EumSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
            horizontalAlignment = if (leadingContent != null) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            leadingContent?.invoke()
            Text(
                text = title,
                style =
                    if (useCompactFailureTextStyle) {
                        routeFailureTitleTextStyle()
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = if (leadingContent != null) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = description,
                style =
                    if (useCompactFailureTextStyle) {
                        routeFailureDescriptionTextStyle()
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (leadingContent != null) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            if (actionLabel != null && onActionClick != null) {
                TextButton(onClick = onActionClick) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}

@Composable
private fun riskLevelText(riskLevel: RouteRiskLevel): String =
    when (riskLevel) {
        RouteRiskLevel.LOW -> stringResource(id = R.string.route_setting_risk_low)
        RouteRiskLevel.MEDIUM -> stringResource(id = R.string.route_setting_risk_medium)
        RouteRiskLevel.HIGH -> stringResource(id = R.string.route_setting_risk_high)
    }

@Composable
private fun routeBadgeText(badge: RouteOptionBadge): String =
    when (badge) {
        RouteOptionBadge.SAFE_PRIORITY -> stringResource(id = R.string.route_setting_badge_safe_priority)
        RouteOptionBadge.STEP_FREE -> stringResource(id = R.string.route_setting_badge_step_free)
        RouteOptionBadge.AUDIO_SIGNAL -> stringResource(id = R.string.route_setting_badge_audio_signal)
        RouteOptionBadge.BRAILLE_BLOCK -> stringResource(id = R.string.route_setting_badge_braille_block)
        RouteOptionBadge.SIGNAL_CROSSWALK -> stringResource(id = R.string.route_setting_badge_signal_crosswalk)
        RouteOptionBadge.CURB_GAP -> stringResource(id = R.string.route_setting_badge_curb_gap)
        RouteOptionBadge.UNSIGNALIZED_CROSSWALK ->
            stringResource(id = R.string.route_setting_badge_unsignalized_crosswalk)
        RouteOptionBadge.LOW_SLOPE -> "낮은 경사"
        RouteOptionBadge.MIDDLE_SLOPE -> "중간 경사"
        RouteOptionBadge.STAIR -> "계단"
        RouteOptionBadge.CROSSWALK -> "횡단보도"
        RouteOptionBadge.ELEVATOR -> "엘리베이터"
        RouteOptionBadge.NARROW_SIDEWALK -> "좁은 보도"
        RouteOptionBadge.UNPAVED -> "비포장"
    }

@Composable
private fun routeAccessibilityLabelColors(badge: RouteOptionBadge): Pair<Color, Color> =
    when (badge) {
        RouteOptionBadge.CURB_GAP,
        RouteOptionBadge.UNSIGNALIZED_CROSSWALK,
        RouteOptionBadge.MIDDLE_SLOPE,
        RouteOptionBadge.STAIR,
        RouteOptionBadge.NARROW_SIDEWALK,
        RouteOptionBadge.UNPAVED,
            ->
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f) to MaterialTheme.colorScheme.tertiary

        RouteOptionBadge.SAFE_PRIORITY,
        RouteOptionBadge.STEP_FREE,
        RouteOptionBadge.AUDIO_SIGNAL,
        RouteOptionBadge.BRAILLE_BLOCK,
        RouteOptionBadge.SIGNAL_CROSSWALK,
        RouteOptionBadge.LOW_SLOPE,
        RouteOptionBadge.CROSSWALK,
        RouteOptionBadge.ELEVATOR,
            ->
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) to MaterialTheme.colorScheme.primary
    }

private fun routeSearchHeaderRoleLabelColor(roleLabel: String): Color =
    when (roleLabel) {
        "출발" -> RouteWaypointOriginLabelColor
        "도착" -> RouteWaypointDestinationLabelColor
        else -> RouteSearchHeaderAccentColor
    }

private fun routeCardVisibleAccessibilityBadges(badges: List<RouteOptionBadge>): List<RouteOptionBadge> {
    val prioritizedBadges = badges.filterNot { it == RouteOptionBadge.SAFE_PRIORITY }
    if (prioritizedBadges.size >= MAX_COMPACT_ACCESSIBILITY_BADGE_COUNT) {
        return prioritizedBadges.take(MAX_COMPACT_ACCESSIBILITY_BADGE_COUNT)
    }

    return (prioritizedBadges + badges.filter { it == RouteOptionBadge.SAFE_PRIORITY })
        .distinct()
        .take(MAX_COMPACT_ACCESSIBILITY_BADGE_COUNT)
}

@Composable
private fun optionAccentColor(routeOption: RouteOption): Color =
    when (routeOption) {
        RouteOption.SAFE -> RouteSafeBlue
        RouteOption.SHORTEST -> RouteFastOrange
        RouteOption.RECOMMENDED -> RouteSafeBlue
        RouteOption.MIN_TRANSFER -> MaterialTheme.colorScheme.secondary
        RouteOption.MIN_WALK -> RouteFastOrange
    }

@Composable
private fun routeSegmentBarColor(segment: RouteOptionSegmentBarUiState): Color =
    when (segment.kind) {
        RouteOptionSegmentKind.WALK -> RouteTransitWalkGray
        RouteOptionSegmentKind.BUS -> RouteTransitNavy
        RouteOptionSegmentKind.SUBWAY -> subwayLineColor(segment.routeLabel)
    }

@Composable
private fun routeSegmentBarContentColor(kind: RouteOptionSegmentKind): Color =
    when (kind) {
        RouteOptionSegmentKind.WALK -> MaterialTheme.colorScheme.onSurfaceVariant
        RouteOptionSegmentKind.BUS,
        RouteOptionSegmentKind.SUBWAY,
            -> Color.White
    }

private fun routeTransitOptionTypeColor(typeLabel: String): Color =
    if (typeLabel.contains("저상")) {
        RouteTransitTagLowFloorColor
    } else {
        RouteTransitTagNormalColor
    }

private fun subwayLineColor(routeLabel: String?): Color =
    when {
        routeLabel == null -> RouteTransitNavy
        routeLabel.contains("부산김해", ignoreCase = true) ||
            routeLabel.contains("김해", ignoreCase = true) ||
            routeLabel.contains("BGL", ignoreCase = true) -> RouteSubwayBusanGimhae
        routeLabel.contains("1") -> RouteSubwayLine1
        routeLabel.contains("2") -> RouteSubwayLine2
        routeLabel.contains("3") -> RouteSubwayLine3
        routeLabel.contains("4") -> RouteSubwayLine4
        else -> RouteTransitNavy
    }

private fun subwayLineShortLabel(routeLabel: String?): String =
    when {
        routeLabel == null -> "?"
        routeLabel.contains("부산김해", ignoreCase = true) ||
            routeLabel.contains("김해", ignoreCase = true) ||
            routeLabel.contains("BGL", ignoreCase = true) -> "김"
        routeLabel.contains("1") -> "1"
        routeLabel.contains("2") -> "2"
        routeLabel.contains("3") -> "3"
        routeLabel.contains("4") -> "4"
        else -> routeLabel.take(2)
    }

@Composable
private fun routeDetailToneColors(tone: RouteDetailTone): Pair<Color, Color> =
    when (tone) {
        RouteDetailTone.NEUTRAL ->
            MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurfaceVariant

        RouteDetailTone.INFO ->
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f) to MaterialTheme.colorScheme.primary

        RouteDetailTone.WARNING ->
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.36f) to MaterialTheme.colorScheme.tertiary
    }

private fun routeDetailStepIconRes(kind: RouteDetailStepKind): Int =
    when (kind) {
        RouteDetailStepKind.START -> R.drawable.ic_navigation_rail_origin_pin
        RouteDetailStepKind.ARRIVAL -> R.drawable.ic_navigation_rail_destination_pin

        RouteDetailStepKind.ALIGHT -> R.drawable.ic_route_alight
        RouteDetailStepKind.BUS -> R.drawable.ic_place_bus
        RouteDetailStepKind.SUBWAY -> R.drawable.ic_route_subway
        RouteDetailStepKind.STRAIGHT -> R.drawable.ic_direction_straight
        RouteDetailStepKind.FALLBACK -> R.drawable.ic_status_help_circle

        RouteDetailStepKind.TURN_LEFT -> R.drawable.ic_direction_turn_left
        RouteDetailStepKind.TURN_RIGHT -> R.drawable.ic_direction_turn_right

        RouteDetailStepKind.TACTILE_GUIDE -> R.drawable.ic_route_tactile_blocks
        RouteDetailStepKind.CROSSWALK -> R.drawable.ic_direction_crosswalk
        RouteDetailStepKind.ELEVATOR -> R.drawable.ic_route_elevator
        RouteDetailStepKind.CONSTRUCTION -> R.drawable.ic_route_construction
        RouteDetailStepKind.CURB_GAP -> R.drawable.ic_status_warning
        RouteDetailStepKind.STAIRS -> R.drawable.ic_route_stairs
    }

private fun RouteDetailStepKind.usesDirectionalStepIcon(): Boolean =
    this == RouteDetailStepKind.STRAIGHT ||
        this == RouteDetailStepKind.TURN_LEFT ||
        this == RouteDetailStepKind.CROSSWALK ||
        this == RouteDetailStepKind.TURN_RIGHT

private fun RouteDetailStepKind.usesLabeledWaypointPinIcon(): Boolean =
    this == RouteDetailStepKind.START || this == RouteDetailStepKind.ARRIVAL

private fun RouteDetailStepKind.leadingIconSize(): Dp =
    if (this == RouteDetailStepKind.BUS || this == RouteDetailStepKind.SUBWAY) {
        RouteDetailTransitLeadingIconSize
    } else {
        RouteDetailStepLeadingIconSize
    }

private fun List<RouteDetailStepUiState>.hasTransitDetailStep(): Boolean =
    any { step -> step.kind == RouteDetailStepKind.BUS || step.kind == RouteDetailStepKind.SUBWAY }

private fun RouteSelectedRouteUiState?.detailGuidanceMarkers(
    focusedStepIndex: Int?,
): List<MapViewportPointOverlay> =
    this?.detailSteps
        ?.let { steps ->
            buildList {
                steps.mapIndexedNotNullTo(this) { index, step ->
                    val coordinate = step.coordinate ?: return@mapIndexedNotNullTo null
                    MapViewportPointOverlay(
                        overlayId = routeDetailStepMarkerId(index),
                        coordinate = MapCoordinate(latitude = coordinate.latitude, longitude = coordinate.longitude),
                        kind = step.kind.toMapViewportPointKind(),
                        tone =
                            if (focusedStepIndex == index) {
                                MapViewportOverlayTone.PRIMARY
                            } else {
                                routeDetailStepMarkerTone(step.kind)
                            },
                        label = step.indexLabel,
                        contentDescription = step.title,
                        isSelected = focusedStepIndex == index,
                        clickTargetId = routeDetailStepMarkerId(index),
                        transitMarker = step.toTransitMarker(),
                    )
                }
                steps.zipWithNext().forEachIndexed { index, pair ->
                    val previous = pair.first
                    val next = pair.second
                    val coordinate = next.coordinate ?: return@forEachIndexed
                    val transferMarker = previous.toTransferMarker(next) ?: return@forEachIndexed
                    add(
                        MapViewportPointOverlay(
                            overlayId = "route-detail-transfer-$index",
                            coordinate = MapCoordinate(latitude = coordinate.latitude, longitude = coordinate.longitude),
                            kind = MapViewportPointKind.TRANSIT_TRANSFER,
                            tone = MapViewportOverlayTone.NAVY,
                            label = null,
                            contentDescription = "${previous.title} -> ${next.title}",
                            isSelected = focusedStepIndex == index + 1,
                            clickTargetId = routeDetailStepMarkerId(index + 1),
                            transitMarker = transferMarker,
                        ),
                    )
                }
            }
        }
        .orEmpty()

private fun RouteDetailStepKind.toMapViewportPointKind(): MapViewportPointKind =
    when (this) {
        RouteDetailStepKind.BUS -> MapViewportPointKind.TRANSIT_BUS_STOP
        RouteDetailStepKind.SUBWAY -> MapViewportPointKind.TRANSIT_SUBWAY_STATION
        else -> MapViewportPointKind.SEGMENT_JUNCTION
    }

private fun RouteDetailStepUiState.toTransitMarker(): MapViewportTransitMarker? =
    when (kind) {
        RouteDetailStepKind.BUS ->
            MapViewportTransitMarker(
                from = MapViewportTransitMarkerLeg(MapViewportTransitMarkerKind.BUS, transitLabel),
            )
        RouteDetailStepKind.SUBWAY ->
            MapViewportTransitMarker(
                from = MapViewportTransitMarkerLeg(MapViewportTransitMarkerKind.SUBWAY, transitLabel),
            )
        else -> null
    }

private fun RouteDetailStepUiState.toTransferMarker(next: RouteDetailStepUiState): MapViewportTransitMarker? {
    val from = toTransitMarker()?.from ?: return null
    val to = next.toTransitMarker()?.from ?: return null
    if (from.kind == to.kind && from.label == to.label) return null
    return MapViewportTransitMarker(from = from, to = to)
}

private fun routeDetailStepMarkerId(index: Int): String = "$ROUTE_DETAIL_STEP_MARKER_PREFIX$index"

private fun String.routeDetailStepMarkerIndexOrNull(): Int? =
    takeIf { value -> value.startsWith(ROUTE_DETAIL_STEP_MARKER_PREFIX) }
        ?.removePrefix(ROUTE_DETAIL_STEP_MARKER_PREFIX)
        ?.toIntOrNull()

private fun routeDetailStepMarkerTone(kind: RouteDetailStepKind): MapViewportOverlayTone =
    when (kind) {
        RouteDetailStepKind.BUS,
        RouteDetailStepKind.ALIGHT,
        RouteDetailStepKind.SUBWAY,
            -> MapViewportOverlayTone.NAVY

        RouteDetailStepKind.CROSSWALK,
        RouteDetailStepKind.STAIRS,
        RouteDetailStepKind.CURB_GAP,
        RouteDetailStepKind.CONSTRUCTION,
            -> MapViewportOverlayTone.TERTIARY

        RouteDetailStepKind.START,
        RouteDetailStepKind.ARRIVAL,
            -> MapViewportOverlayTone.SECONDARY

        else -> MapViewportOverlayTone.PRIMARY
    }

@Composable
private fun routeDetailTimelineColor(kind: RouteDetailStepKind): Color =
    when (kind) {
        RouteDetailStepKind.BUS,
        RouteDetailStepKind.ALIGHT,
        RouteDetailStepKind.SUBWAY,
        -> RouteTransitNavy

        RouteDetailStepKind.START,
        RouteDetailStepKind.ARRIVAL,
        -> MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)

        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.92f)
    }

@Composable
private fun routeDetailIconRailTint(kind: RouteDetailStepKind): Color =
    when (kind) {
        RouteDetailStepKind.START -> RouteSafeBlue
        RouteDetailStepKind.ARRIVAL -> RouteWaypointDestinationColor
        RouteDetailStepKind.BUS,
        RouteDetailStepKind.ALIGHT,
        RouteDetailStepKind.SUBWAY,
            -> RouteTransitNavy
        else -> Color(0xFF333333)
    }

private fun routeDetailChipIconRes(kind: RouteDetailChipKind): Int =
    when (kind) {
        RouteDetailChipKind.STEP_FREE -> R.drawable.ic_status_safe_info
        RouteDetailChipKind.ELEVATOR -> R.drawable.ic_route_elevator
        RouteDetailChipKind.AUDIO_SIGNAL -> R.drawable.ic_status_safe_info
        RouteDetailChipKind.BRAILLE_BLOCK -> R.drawable.ic_route_tactile_blocks
        RouteDetailChipKind.CONSTRUCTION -> R.drawable.ic_route_construction
        RouteDetailChipKind.SIGNAL_CROSSWALK -> R.drawable.ic_route_crosswalk
        RouteDetailChipKind.UNSIGNALIZED_CROSSWALK -> R.drawable.ic_status_warning
        RouteDetailChipKind.CURB_GAP -> R.drawable.ic_status_warning
        RouteDetailChipKind.STAIRS -> R.drawable.ic_route_stairs
        RouteDetailChipKind.PENDING -> R.drawable.ic_status_neutral
    }

@Composable
private fun RouteOption.routeOptionTitle(): String =
    when (this) {
        RouteOption.SAFE -> stringResource(id = R.string.route_setting_option_safe_title)
        RouteOption.SHORTEST -> stringResource(id = R.string.route_setting_option_shortest_title)
        RouteOption.RECOMMENDED -> "추천 경로"
        RouteOption.MIN_TRANSFER -> "최소 환승"
        RouteOption.MIN_WALK -> "최소 도보"
    }

@Composable
private fun routeOptionCompactPrefix(routeOption: RouteOption): String =
    when (routeOption) {
        RouteOption.SAFE -> stringResource(id = R.string.route_setting_card_recommended_route)
        RouteOption.SHORTEST -> stringResource(id = R.string.route_setting_card_alternative_route)
        RouteOption.RECOMMENDED -> "추천 경로"
        RouteOption.MIN_TRANSFER -> "최소 환승 경로"
        RouteOption.MIN_WALK -> "최소 도보 경로"
    }

private fun compactEstimatedTimeLabel(minutes: Int): String =
    if (minutes > 0) {
        "${minutes}분"
    } else {
        "--"
    }

private fun compactDistanceLabel(distanceMeters: Int): String =
    when {
        distanceMeters <= 0 -> "--"
        distanceMeters < METERS_PER_KILOMETER -> "${distanceMeters}m"
        else -> String.format(Locale.US, "%.1fkm", distanceMeters / METERS_PER_KILOMETER.toFloat())
    }

private fun walkPreviewLabel(routeOption: RouteOption): String =
    when (routeOption) {
        RouteOption.SHORTEST -> "최단 경로"
        RouteOption.SAFE -> "안전한 경로"
        RouteOption.RECOMMENDED -> "추천 경로"
        RouteOption.MIN_TRANSFER -> "최소 환승"
        RouteOption.MIN_WALK -> "최소 도보"
    }

private fun DrawScope.drawRoutePreviewMapGrid(outline: Color) {
    val verticalStep = size.width / 5f
    val horizontalStep = size.height / 6f
    val strokeWidth = 1.dp.toPx()

    for (index in 0..5) {
        val x = index * verticalStep
        drawLine(
            color = outline.copy(alpha = 0.18f),
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

private fun List<GeoCoordinate>.toRoutePreviewPath(
    bounds: RoutePreviewProjectionBounds,
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

private fun RoutePreviewProjectionPoint.toCanvasOffset(size: Size): Offset =
    Offset(
        x = size.width * xRatio,
        y = size.height * yRatio,
    )

private fun Modifier.offsetWithinRoutePreviewMap(
    point: RoutePreviewProjectionPoint,
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

private fun routePreviewProjectionBounds(previewMap: RoutePreviewMapUiState): RoutePreviewProjectionBounds {
    val coordinates =
        buildList {
            addAll(previewMap.polyline)
            previewMap.originCoordinate?.let(::add)
            previewMap.destinationCoordinate?.let(::add)
        }

    if (coordinates.isEmpty()) {
        return RoutePreviewProjectionBounds(
            minLatitude = DEFAULT_PREVIEW_CENTER_LATITUDE - (MIN_ROUTE_PREVIEW_LATITUDE_SPAN / 2.0),
            maxLatitude = DEFAULT_PREVIEW_CENTER_LATITUDE + (MIN_ROUTE_PREVIEW_LATITUDE_SPAN / 2.0),
            minLongitude = DEFAULT_PREVIEW_CENTER_LONGITUDE - (MIN_ROUTE_PREVIEW_LONGITUDE_SPAN / 2.0),
            maxLongitude = DEFAULT_PREVIEW_CENTER_LONGITUDE + (MIN_ROUTE_PREVIEW_LONGITUDE_SPAN / 2.0),
        )
    }

    val latitudeBounds =
        expandedRoutePreviewBounds(
            minValue = coordinates.minOf { coordinate -> coordinate.latitude },
            maxValue = coordinates.maxOf { coordinate -> coordinate.latitude },
            minimumSpan = MIN_ROUTE_PREVIEW_LATITUDE_SPAN,
        )
    val longitudeBounds =
        expandedRoutePreviewBounds(
            minValue = coordinates.minOf { coordinate -> coordinate.longitude },
            maxValue = coordinates.maxOf { coordinate -> coordinate.longitude },
            minimumSpan = MIN_ROUTE_PREVIEW_LONGITUDE_SPAN,
        )

    return RoutePreviewProjectionBounds(
        minLatitude = latitudeBounds.first,
        maxLatitude = latitudeBounds.second,
        minLongitude = longitudeBounds.first,
        maxLongitude = longitudeBounds.second,
    )
}

private fun expandedRoutePreviewBounds(
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

private data class RoutePreviewProjectionBounds(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
) {
    private val latitudeSpan: Double
        get() = (maxLatitude - minLatitude).coerceAtLeast(MIN_ROUTE_PREVIEW_LATITUDE_SPAN)

    private val longitudeSpan: Double
        get() = (maxLongitude - minLongitude).coerceAtLeast(MIN_ROUTE_PREVIEW_LONGITUDE_SPAN)

    fun project(coordinate: GeoCoordinate): RoutePreviewProjectionPoint {
        val longitudeRatio =
            ((coordinate.longitude - minLongitude) / longitudeSpan)
                .toFloat()
                .coerceIn(0.08f, 0.92f)
        val latitudeRatio =
            (1f - ((coordinate.latitude - minLatitude) / latitudeSpan).toFloat())
                .coerceIn(0.1f, 0.9f)

        return RoutePreviewProjectionPoint(
            xRatio = longitudeRatio,
            yRatio = latitudeRatio,
        )
    }
}

private data class RoutePreviewProjectionPoint(
    val xRatio: Float,
    val yRatio: Float,
)

private const val METERS_PER_KILOMETER = 1_000
private const val MAX_VISIBLE_OPTION_CARD_COUNT = 3
private const val MAX_VISIBLE_ROUTE_CHIP_COUNT = 2
private const val MAX_COMPACT_ACCESSIBILITY_BADGE_COUNT = 2
private const val MAX_TRANSIT_SEGMENT_BAR_COUNT = 5
private const val MAX_LOW_FLOOR_RESERVATION_COUNT = 2
private const val ROUTE_DETAIL_STEP_MARKER_PREFIX = "route-detail-step-"
private const val CURRENT_LOCATION_WAYPOINT_NAME = "현재 위치"
private const val DEFAULT_PREVIEW_CENTER_LATITUDE = 35.1796
private const val DEFAULT_PREVIEW_CENTER_LONGITUDE = 129.0756
private val RouteStandardCardCornerRadius = 12.dp
private val RouteSectionCardCornerRadius = 16.dp
private val RouteMapStageCornerRadius = 0.dp
private val RouteButtonCornerRadius = 12.dp
private val RouteCompactChipCornerRadius = 8.dp
private val RouteSearchHeaderVerticalPadding = 8.dp
private val RouteSearchHeaderSummaryToModeTabsGap = 12.dp
private val RouteSearchHeaderModeTabsBottomPadding = 12.dp
private val RouteSearchHeaderModeTabHeight = 36.dp
private val RouteSearchHeaderModeTabCornerRadius = 10.dp
private val RouteSearchHeaderTransitTabIconSize = 22.dp
private val RouteSearchHeaderWalkTabIconSize = 26.dp
private val RouteTravelModeTransitTabIconSize = 20.dp
private val RouteTravelModeWalkTabIconSize = 22.dp
private val RouteSearchHeaderTopToSummaryGap = 6.dp
private val RouteSearchHeaderSummaryMinHeight = 92.dp
private val RouteSearchHeaderWaypointGap = 10.dp
private val RouteSearchHeaderRoleLabelGap = 12.dp
private val RouteSearchHeaderSummaryCornerRadius = 8.dp
private val RouteBottomSheetTopCornerRadius = 16.dp
private val RouteFloatingControlCornerRadius = 24.dp
private val RouteOverlayCardElevation = 6.dp
private val RouteFloatingControlElevation = 6.dp
private val RouteBottomSheetElevation = 6.dp
private val RouteMapMessageIllustrationSize = 144.dp
private val RouteMapMessageIllustrationVisualOffset = 10.dp
private val RouteFailureScreenIllustrationSize = 280.dp
private val RouteFailureFallbackIllustrationSize = 160.dp
private val RouteFailureScreenContentOffset = 48.dp
private val RouteFailureImageToTextGap = 4.dp
private val RouteFailureIllustrationVisualOffset = 24.dp
private val RouteFailureFallbackIllustrationVisualOffset = 14.dp
private val RouteFailureTitleFontSize = 19.sp
private val RouteFailureTitleLineHeight = 25.sp
private val RouteFailureDescriptionFontSize = 13.sp
private val RouteFailureDescriptionLineHeight = 18.sp
private val RouteAccessibilityLabelCornerRadius = 10.dp
private val RouteAccessibilityLabelMinHeight = 24.dp
private val RouteAccessibilityLabelHorizontalPadding = 4.dp
private val RouteAccessibilityLabelGap = 2.dp
private val RouteTravelModeInactiveContentColor = Color(0xFF374151)
private val RouteSearchHeaderContainerColor = EumPrimary600
private val RouteSearchHeaderEmphasizedBoxColor = Color(0xFFF5F8FF)
private val RouteSearchHeaderInactiveBoxColor = Color.White.copy(alpha = 0.18f)
private val RouteSearchHeaderAccentColor = EumPrimary600
private val RouteSearchHeaderEmphasizedContentColor = EumTextPrimary
private val RouteSearchHeaderInactiveContentColor = Color.White
private val RouteSearchHeaderDisabledContentColor = Color.White.copy(alpha = 0.54f)
private val RouteSearchHeaderDividerColor = EumPrimary600.copy(alpha = 0.16f)
private val RouteWaypointCardVerticalPadding = 8.dp
private val RouteWaypointGap = 4.dp
private val RouteWaypointIndicatorGap = 12.dp
private val RouteWaypointTextGap = 8.dp
private val RouteWaypointSupportingGap = 3.dp
private val RouteWaypointMarkerColumnWidth = 24.dp
private val RouteWaypointMarkerColumnVerticalInset = 5.dp
private val RouteWaypointConnectorWidth = 2.dp
private val RouteWaypointPinWidth = 22.dp
private val RouteWaypointPinHeight = 24.dp
private val RouteWaypointSwapButtonWidth = 36.dp
private val RouteWaypointSwapButtonHeight = 56.dp
private val RouteWaypointSwapIconWidth = 18.dp
private val RouteWaypointSwapIconHeight = 24.dp
private val RouteWaypointSwapIconStrokeWidth = 2.25.dp
private val RouteWaypointOriginLabelColor = Color(0xFF16A34A)
private val RouteWaypointDestinationLabelColor = Color(0xFFF14337)
private val RouteWaypointOriginColor = Color(0xFF006BE0)
private val RouteWaypointDestinationColor = Color(0xFFF14337)
private val RouteWaypointConnectorColor = Color(0xFFD9E2EF)
private val RouteWaypointSupportingTextColor = Color(0xFF6B7280)
private val RouteDetailStepLeadingIconContainerSize = 40.dp
private val RouteDetailStepLeadingIconSize = 24.dp
private val RouteDetailTransitLeadingIconSize = 20.dp
private val RouteDetailBottomSheetMinHeight = 280.dp
private val RouteDetailBottomSheetHandleWidth = 48.dp
private val RouteDetailBottomSheetHandleHeight = 5.dp
private val RouteDetailTimelineBarHeight = 34.dp
private val RouteDetailFocusedRowColor = Color(0xFFE5E7EB)
private val RouteDetailRefreshButtonSize = 44.dp
private val RouteTransitPullRefreshThreshold = 72.dp
private val RouteTransitPullRefreshIndicatorSize = 24.dp
private val RouteTransitPullRefreshIndicatorStrokeWidth = 2.5.dp
private val RouteDetailWaypointPinWidth = 22.dp
private val RouteDetailWaypointPinHeight = 24.dp
private val RouteDetailCollapsedRailWidth = 58.dp
private val RouteDetailCollapsedRailItemSize = 72.dp
private val RouteDetailCollapsedGuideCardMinHeight = 92.dp
private val RouteDetailCollapsedGuideIconSize = 44.dp
private val RouteDetailCollapsedGuideCardContainerColor = Color.White
private val RouteDetailCollapsedGuideCardContentColor = Color(0xFF333333)
private val RouteDetailCollapsedGuideCardBottomStrokeWidth = 0.5.dp
private val RouteDetailCollapsedGuideCardBottomStrokeColor = Color(0xFFD9D9D9)
private val RouteDetailScrollTopButtonSize = 48.dp
private val RouteDetailScrollTopIconSize = 24.dp
private val RouteDetailCollapsedRailScrollTopButtonSize = 36.dp
private val RouteDetailCollapsedRailScrollTopIconSize = 22.dp
private val RouteDetailScrollTopActionVerticalPadding = 0.dp
private val RouteDetailPanelBottomActionTopPadding = 24.dp
private val RouteDetailGuideRowMinHeight = 76.dp
private val RouteDetailGuideIconColor = Color(0xFF2B2B2B)
private val RouteDetailGuideBadgeContainerColor = Color.White
private val RouteDetailGuideBadgeContentColor = Color(0xFF666666)
private val RouteDetailGuideBadgeBorderColor = Color(0xFFD9D9D9)
private val RouteDetailGuideDividerColor = Color(0xFFD9D9D9)
private val RouteDetailRowAccessorySize = 36.dp
private val RouteDetailRowAccessoryIconSize = 20.dp
private val RouteDetailRowAccessoryIconColor = Color(0xFF9A9A9A)
private val RouteWaypointInputContainerColor = Color(0xFFF5F8FF)
private val RouteWaypointInputLabelColor = Color(0xFF94A3B8)
private val RouteDetailArrivalInfoChipRadius = 18.dp
private val RouteTimelineDividerColor = Color(0xFFD9D9D9)
private val RouteTravelModeTabVerticalPadding = 7.dp
private val RouteMapControlButtonSize = 36.dp
private val RouteSettingSheetVerticalPadding = 8.dp
private val RouteSettingSheetGap = 6.dp
private val RouteOptionCardGap = 6.dp
private val RouteOptionCardVerticalPadding = 6.dp
private val RouteTransitOptionTitleFontSize = 13.sp
private val RouteTransitOptionEstimatedTimeFontSize = 20.sp
private val RouteSearchLoadingHeight = 156.dp
private val RouteSafeBlue = Color(0xFF006BE0)
private val RouteFastOrange = Color(0xFFF9AB4D)
private const val RouteWalkPreviewVisibleCardCount = 2
private val RouteWalkPreviewCardGap = 14.dp
private val RouteWalkPreviewCardMinHeight = 116.dp
private val RouteWalkPreviewMapToCardGap = 16.dp
private val RouteWalkPreviewCardStartPadding = 14.dp
private val RouteWalkPreviewTopRowEndPadding = 4.dp
private val RouteWalkPreviewBadgeHorizontalPadding = RouteWalkPreviewCardStartPadding
private val RouteWalkPreviewCardVerticalPadding = 10.dp
private val RouteWalkPreviewTopRowBottomPadding = 6.dp
private val RouteWalkPreviewContentGap = 4.dp
private val RouteWalkPreviewTextGap = 4.dp
private val RouteWalkPreviewChevronTouchTargetSize = 36.dp
private val RouteWalkPreviewChevronIconSize = 18.dp
private val RouteWalkPreviewChevronColor = Color(0xFF111827)
private val RouteTransitNavy = Color(0xFF005391)
private val RouteTransitWalkGray = Color(0xFF99B5D1)
private val RouteSubwayLine1 = Color(0xFFFF7F00)
private val RouteSubwayLine2 = Color(0xFF3ED93B)
private val RouteSubwayLine3 = Color(0xFFE8AB56)
private val RouteSubwayLine4 = Color(0xFF32B1FF)
private val RouteSubwayBusanGimhae = Color(0xFF8200FF)
private val RouteTransitSegmentTimelineRadius = 5.dp
private val RouteTransitTagCornerRadius = 10.dp
private val RouteTransitTagStrokeWidth = 0.5.dp
private val RouteTransitTagBorderColor = Color(0xFFD9D9D9)
private val RouteTransitTagLowFloorColor = Color(0xFF2671A8)
private val RouteTransitTagNormalColor = Color(0xFF4B9EDC)
private val RouteTransitTagRouteNumberColor = Color(0xFF333333)
private val RouteTransitTagArrivalColor = Color(0xFFF94D4D)
private val RouteOptionDetailButtonTouchTargetSize = 48.dp
private val RouteOptionDetailButtonIconSize = 24.dp
private val RouteInlineButtonHeight = 44.dp
private val RouteSettingBottomBarButtonHeight = 50.dp
private val RouteSettingBottomBarHorizontalPadding = EumSpacing.medium + 50.dp
private val RouteSettingBottomBarTopGap = EumSpacing.small
private val RouteSettingBottomBarBottomGap = 30.dp
private val RouteDetailSidePanelBottomClearance =
    RouteSettingBottomBarButtonHeight + RouteSettingBottomBarBottomGap + EumSpacing.medium
// Match the tighter card-to-CTA spacing users currently see on devices with a visible system nav bar.
private val RouteWalkPreviewToStartButtonGap = 22.dp
private val RouteWalkMapControlsToPreviewGap = 70.dp
private const val RouteMapBackdropZIndex = 0f
private const val RouteMapControlsZIndex = 2f
private const val RouteWalkPreviewCarouselZIndex = 3f
private const val RouteSettingBottomBarZIndex = 4f
private val RouteDetailFeatureCardContainerColor = Color(0xFFE9ECF3)
private val RouteDetailFeatureTitleFontSize = 18.sp
private val RouteDetailExpandedSidePanelScrimColor = Color(0x66000000)
private val RoutePreviewMarkerSize = 38.dp
private const val MIN_ROUTE_PREVIEW_LATITUDE_SPAN = 0.0035
private const val MIN_ROUTE_PREVIEW_LONGITUDE_SPAN = 0.0045

internal fun routeWalkPreviewCarouselBottomPadding(navigationBarBottomInset: Dp): Dp =
    RouteSettingBottomBarButtonHeight +
        RouteSettingBottomBarBottomGap +
        RouteWalkPreviewToStartButtonGap +
        navigationBarBottomInset

internal fun routeWalkPreviewMapBottomClearance(navigationBarBottomInset: Dp): Dp =
    routeWalkPreviewCarouselBottomPadding(navigationBarBottomInset) +
        RouteWalkPreviewCardMinHeight +
        RouteWalkPreviewMapToCardGap

internal fun routeWalkMapControlsBottomPadding(navigationBarBottomInset: Dp): Dp =
    routeWalkPreviewCarouselBottomPadding(navigationBarBottomInset) +
        RouteWalkPreviewCardMinHeight +
        RouteWalkMapControlsToPreviewGap
