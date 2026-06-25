package com.ssafy.e102.eumgil.feature.navigation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ssafy.e102.eumgil.BuildConfig
import com.ssafy.e102.eumgil.core.location.CurrentHeadingManager
import com.ssafy.e102.eumgil.core.location.CurrentLocationManager
import com.ssafy.e102.eumgil.core.location.HeadingSnapshot
import com.ssafy.e102.eumgil.core.location.LocationPermissionManager
import com.ssafy.e102.eumgil.core.location.LocationPermissionState
import com.ssafy.e102.eumgil.core.location.LocationSnapshot
import com.ssafy.e102.eumgil.core.location.LocationUpdateProfile
import com.ssafy.e102.eumgil.core.location.NoOpCurrentHeadingManager
import com.ssafy.e102.eumgil.core.location.isFreshCurrentLocation
import com.ssafy.e102.eumgil.core.location.normalizeHeadingDegrees
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.RouteCandidate
import com.ssafy.e102.eumgil.core.model.RouteBookmarkDraft
import com.ssafy.e102.eumgil.core.model.RouteGuidanceFeature
import com.ssafy.e102.eumgil.core.model.RouteGuidanceType
import com.ssafy.e102.eumgil.core.model.RouteLeg
import com.ssafy.e102.eumgil.core.model.RouteLegRole
import com.ssafy.e102.eumgil.core.model.RouteLegType
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.core.model.RoutePolyline
import com.ssafy.e102.eumgil.core.model.RouteRiskLevel
import com.ssafy.e102.eumgil.core.model.RouteSearchData
import com.ssafy.e102.eumgil.core.model.RouteSearchQuery
import com.ssafy.e102.eumgil.core.model.RouteSegment
import com.ssafy.e102.eumgil.core.model.RouteSegmentSafetyFlags
import com.ssafy.e102.eumgil.core.model.RouteTransportMode
import com.ssafy.e102.eumgil.core.model.RouteWaypoint
import com.ssafy.e102.eumgil.data.repository.BookmarkData
import com.ssafy.e102.eumgil.data.repository.BookmarkRepository
import com.ssafy.e102.eumgil.data.repository.ReportDraftData
import com.ssafy.e102.eumgil.data.repository.ReportOutboxData
import com.ssafy.e102.eumgil.data.repository.ReportRepository
import com.ssafy.e102.eumgil.data.repository.RouteRepository
import com.ssafy.e102.eumgil.data.repository.RouteTransitRefreshData
import com.ssafy.e102.eumgil.data.repository.toBookmarkDataOrNull
import com.ssafy.e102.eumgil.feature.route.RouteDetailStepKind
import com.ssafy.e102.eumgil.feature.route.RouteNavigationRequest
import com.ssafy.e102.eumgil.feature.route.RouteTransitOptionLabelUiState
import com.ssafy.e102.eumgil.feature.route.toRouteDetailStepKind
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal const val NavigationOriginSegmentIndex = -1
private const val NavigationOriginHeroTitle = "\uCD9C\uBC1C"
private const val NavigationOriginHeroDescription =
    "\uD604\uC7AC \uC704\uCE58\uC5D0\uC11C \uC120\uD0DD\uD55C \uACBD\uB85C \uC548\uB0B4\uB97C \uC2DC\uC791\uD569\uB2C8\uB2E4."
private const val NAVIGATION_ROUTE_START_JOIN_RADIUS_METERS = 8.0
private const val NAVIGATION_ROUTE_PROGRESS_SNAP_DISTANCE_METERS = 15.0
private const val NAVIGATION_ROUTE_REALTIME_ENTER_DISTANCE_METERS = 15.0
private const val NAVIGATION_ROUTE_DETAIL_EXIT_DISTANCE_METERS = 40.0
private const val NAVIGATION_ROUTE_JOIN_STABLE_UPDATE_COUNT = 2
private const val NAVIGATION_ROUTE_JOIN_STABLE_DURATION_MILLIS = 3_000L
private const val NAVIGATION_DESTINATION_AUTO_ARRIVAL_RADIUS_METERS = 10.0
private const val NAVIGATION_DESTINATION_SOON_RADIUS_METERS = 25.0
private const val NAVIGATION_DESTINATION_AUTO_ARRIVAL_STABLE_UPDATE_COUNT = 2
private const val NAVIGATION_DESTINATION_AUTO_ARRIVAL_STABLE_DURATION_MILLIS = 3_000L
private const val NAVIGATION_AUTO_TTS_NEAR_DISTANCE_METERS = 10
private const val NAVIGATION_GPS_BEARING_MIN_SPEED_METERS_PER_SECOND = 0.5f
private const val NAVIGATION_NODE_TRANSITION_RADIUS_METERS = 10.0
private const val NAVIGATION_NODE_TRANSITION_ENTER_DISTANCE_METERS = 12.0
private const val NAVIGATION_NODE_TRANSITION_DEFAULT_ADVANCE_METERS = 8.0
private const val NAVIGATION_NODE_TRANSITION_MIN_ADVANCE_METERS = 3.0
private const val NAVIGATION_NODE_TRANSITION_SEGMENT_ADVANCE_RATIO = 0.4
private const val NAVIGATION_NODE_TRANSITION_PASSED_DISTANCE_METERS = 3.0
private const val NAVIGATION_NODE_TRANSITION_COURSE_MIN_MOVE_METERS = 2.0
private const val NAVIGATION_NODE_TRANSITION_COURSE_MAX_ANGLE_DEGREES = 60.0
private const val NAVIGATION_NODE_TRANSITION_TURN_MAX_SIDE_DISTANCE_METERS = 25.0
private const val NAVIGATION_NODE_TRANSITION_STABLE_UPDATE_COUNT = 2
private const val NAVIGATION_NODE_TRANSITION_STABLE_DURATION_MILLIS = 3_000L
private const val NAVIGATION_MAX_ACCEPTED_ACCURACY_METERS = 35f
private const val NAVIGATION_WALKING_MAX_ACCEPTED_SPEED_METERS_PER_SECOND = 8.0
private const val NAVIGATION_TRANSIT_MAX_ACCEPTED_SPEED_METERS_PER_SECOND = 45.0
private const val NAVIGATION_IMPOSSIBLE_JUMP_MIN_DISTANCE_METERS = 30.0
private const val NAVIGATION_JITTER_FREEZE_SPEED_METERS_PER_SECOND = 0.4f
private const val NAVIGATION_JITTER_FREEZE_DISTANCE_METERS = 3.0
private const val NAVIGATION_SMOOTHING_NEAR_DISTANCE_METERS = 8.0
private const val NAVIGATION_SMOOTHING_MID_DISTANCE_METERS = 20.0
private const val NAVIGATION_SMOOTHING_NEAR_ALPHA = 0.55
private const val NAVIGATION_SMOOTHING_MID_ALPHA = 0.75
private const val NAVIGATION_FOLLOW_LOOKAHEAD_METERS = 12.0
private const val NAVIGATION_LIVE_GUIDANCE_TARGET_AHEAD_TOLERANCE_METERS = 1.0
private const val NAVIGATION_LIVE_GUIDANCE_DISPLAY_THROTTLE_MILLIS = 3_000L
private const val NAVIGATION_COMPLETION_TTS_NAVIGATION_DELAY_MILLIS = 5_000L
private const val NAVIGATION_LOCATION_DEBUG_TAG = "NavigationLocation"
private const val NAVIGATION_ROUTE_START_GUIDANCE_TEXT = "경로 시작 지점까지 이동하세요"
private const val NAVIGATION_DESTINATION_SOON_TTS_TEXT = "목적지에 곧 도착합니다."
private const val NAVIGATION_ARRIVAL_COMPLETION_TTS_TEXT = "목적지에 도착했습니다. 안내를 종료합니다."
private const val NAVIGATION_REROUTE_TTS_TEXT = "경로를 벗어났습니다. 경로를 다시 탐색합니다."
private const val NAVIGATION_AUTO_TTS_APPROACH_DISTANCE_METERS = 30

private enum class NavigationGuidanceMode {
    RouteDetail,
    Realtime,
}

private data class NavigationLiveGuidanceDisplayState(
    val segmentIndex: Int,
    val displayDistanceMeters: Int,
    val updatedAtEpochMillis: Long,
)

private data class NavigationNodeTransitionCandidate(
    val segmentIndex: Int,
    val updateCount: Int,
    val stableSinceEpochMillis: Long,
)

class NavigationViewModel(
    private val currentLocationManager: CurrentLocationManager,
    private val currentHeadingManager: CurrentHeadingManager = NoOpCurrentHeadingManager,
    private val locationPermissionManager: LocationPermissionManager? = null,
    private val bookmarkRepository: BookmarkRepository,
    private val routeRepository: RouteRepository = NoOpRouteRepository,
    private val reportRepository: ReportRepository = NoOpReportRepository,
    initialLowVisionMode: Boolean = false,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = mutableUiState.asStateFlow()

    private val mutableUiEvent = MutableSharedFlow<NavigationUiEvent>()
    val uiEvent: SharedFlow<NavigationUiEvent> = mutableUiEvent.asSharedFlow()

    private var initialBriefingRequested = false
    private var hasPendingBriefingPlayback = false
    private var hasPendingInitialBriefing = false
    private var hasAutoPlayedInitialBriefing = false
    private var navigationRequest: RouteNavigationRequest? = null
    private var routeSession: NavigationRouteSession? = null
    private var lastProcessedLocationEpochMillis: Long? = null
    private var deviationState = NavigationDeviationState()
    private var activeSegmentIndex: Int = 0
    private var focusedSegmentIndex: Int = NavigationOriginSegmentIndex
    private var isInspectingSegments: Boolean = false
    private var hasPendingActiveChange: Boolean = false
    private var isExitConfirmDialogVisible: Boolean = false
    private var isTransitRefreshInFlight: Boolean = false
    private var isRerouteInFlight: Boolean = false
    private var isEndNavigationInFlight: Boolean = false
    private var pendingHazardReportRerouteId: Long? = null
    private var guidanceMode: NavigationGuidanceMode = NavigationGuidanceMode.RouteDetail
    private var routeJoinStableUpdateCount: Int = 0
    private var routeJoinStableSinceEpochMillis: Long? = null
    private var hasJoinedRealtimeRouteLine: Boolean = false
    private var destinationArrivalStableUpdateCount: Int = 0
    private var destinationArrivalStableSinceEpochMillis: Long? = null
    private var hasSpokenDestinationSoon: Boolean = false
    private var nodeTransitionCandidate: NavigationNodeTransitionCandidate? = null
    private val routeMatcher = NavigationRouteMatcher()
    private var latestRouteMatch: NavigationRouteMatchResult? = null
    private var latestLocationCoordinate: GeoCoordinate? = null
    private var latestNavigationPose: NavigationPose? = null
    private var latestHeadingDegrees: Double? = null
    private var latestGpsBearingDegrees: Double? = null
    private var trackingMode: NavigationTrackingMode = NavigationTrackingMode.FOLLOW
    private var pendingCurrentLocationRecenter: Boolean = false
    private var locationRecenterRequestId: Long = 0L
    private var latestProgress: NavigationProgressSnapshot? = null
    private var latestRemainingDistanceMeters: Int? = null
    private var latestEstimatedMinutes: Int? = null
    private var latestRemainingMetricsSource: NavigationRemainingMetricsSource =
        NavigationRemainingMetricsSource.ProjectedRoute
    private var latestLiveGuidanceRawDistanceMeters: Int? = null
    private var latestLiveGuidanceDisplayDistanceMeters: Int? = null
    private var liveGuidanceDisplayState: NavigationLiveGuidanceDisplayState? = null
    private var latestTransitPresentation: NavigationTransitPresentation? = null
    private var lastSegmentMarkerDebugSummary: String? = null
    private var isLowVisionMode: Boolean = initialLowVisionMode
    private var lowVisionActualMetricsCacheKey: LowVisionActualMetricsKey? = null
    private var lowVisionActualMetricsCache: NavigationRemainingMetrics? = null
    private var lowVisionActualMetricsCacheCoordinate: GeoCoordinate? = null
    private var lowVisionActualMetricsCacheRecordedAtMillis: Long? = null
    private var lowVisionActualMetricsInFlightKey: LowVisionActualMetricsKey? = null
    private var lowVisionActualMetricsFailedKey: LowVisionActualMetricsKey? = null
    private var lowVisionActualMetricsLastAttemptCoordinate: GeoCoordinate? = null
    private var lowVisionActualMetricsLastAttemptRecordedAtMillis: Long? = null
    private var lastLowVisionRouteChangeAlertSegmentIndex: Int? = null
    private val spokenInitialGuidanceKeys = mutableSetOf<String>()
    private val spokenApproachGuidanceKeys = mutableSetOf<String>()
    private val spokenNearGuidanceKeys = mutableSetOf<String>()
    init {
        collectLocationUpdates()
        collectHeadingUpdates()
    }

    fun setLowVisionMode(enabled: Boolean) {
        if (isLowVisionMode == enabled) return
        isLowVisionMode = enabled
        if (!enabled) {
            lowVisionActualMetricsCacheKey = null
            lowVisionActualMetricsCache = null
            lowVisionActualMetricsCacheCoordinate = null
            lowVisionActualMetricsCacheRecordedAtMillis = null
            lowVisionActualMetricsInFlightKey = null
            lowVisionActualMetricsFailedKey = null
            lowVisionActualMetricsLastAttemptCoordinate = null
            lowVisionActualMetricsLastAttemptRecordedAtMillis = null
            lastLowVisionRouteChangeAlertSegmentIndex = null
        }
        publishNavigationState()
    }

    fun bindNavigationRequest(request: RouteNavigationRequest) {
        val normalizedRequest = request.withTransitAlightingGuidanceSegments()
        navigationRequest = normalizedRequest
        routeSession =
            NavigationRouteSession(
                route = normalizedRequest.selectedRoute,
                routeId = normalizedRequest.selectionHandoff?.routeId ?: normalizedRequest.selectedRoute.serverRouteId,
                sessionId = normalizedRequest.selectionHandoff?.sessionId,
            )
        lastProcessedLocationEpochMillis = null
        deviationState = NavigationDeviationState()
        activeSegmentIndex = NavigationOriginSegmentIndex
        focusedSegmentIndex = NavigationOriginSegmentIndex
        isInspectingSegments = false
        hasPendingActiveChange = false
        isExitConfirmDialogVisible = false
        isTransitRefreshInFlight = false
        isRerouteInFlight = false
        isEndNavigationInFlight = false
        pendingHazardReportRerouteId = null
        guidanceMode = NavigationGuidanceMode.RouteDetail
        routeJoinStableUpdateCount = 0
        routeJoinStableSinceEpochMillis = null
        hasJoinedRealtimeRouteLine = false
        resetDestinationArrivalStability()
        hasSpokenDestinationSoon = false
        resetNodeTransitionStability()
        latestRouteMatch = null
        latestLocationCoordinate = null
        latestNavigationPose = null
        latestHeadingDegrees = null
        latestGpsBearingDegrees = null
        trackingMode = NavigationTrackingMode.FOLLOW
        pendingCurrentLocationRecenter = false
        locationRecenterRequestId = 0L
        currentLocationManager.latestLocation.value
            ?.takeIf { snapshot -> snapshot.isFreshCurrentLocation() }
            ?.let { snapshot -> seedLatestLocationSnapshot(snapshot, normalizedRequest.selectedRoute) }
        val initialProgressCoordinate = latestLocationCoordinate ?: normalizedRequest.origin.coordinate
        latestProgress = routeSession?.route?.evaluateProgress(initialProgressCoordinate)
        resetLiveGuidancePresentation()
        val initialRemainingMetrics =
            latestProgress?.let { progress ->
                routeSession?.route?.resolveRemainingMetrics(
                    progress = progress,
                    destination = normalizedRequest.destination.coordinate,
                    useLowVisionWalkingPace = isLowVisionMode,
                )
            }
        latestRemainingDistanceMeters =
            normalizedRequest.selectionHandoff?.initialRemainingDistanceMeters
                ?.takeIf { distanceMeters -> distanceMeters >= 0 }
                ?: initialRemainingMetrics?.distanceMeters
                ?: normalizedRequest.selectedRoute.totalDistanceMeters()
        latestEstimatedMinutes =
            normalizedRequest.selectionHandoff?.initialRemainingDurationSeconds
                ?.takeIf { durationSeconds -> durationSeconds >= 0 }
                ?.toEtaMinutes()
                ?: initialRemainingMetrics?.estimatedMinutes
                ?: normalizedRequest.selectedRoute.summary.estimatedTimeMinutes
        latestRemainingMetricsSource =
            if (normalizedRequest.selectionHandoff?.initialRemainingDistanceMeters != null ||
                normalizedRequest.selectionHandoff?.initialRemainingDurationSeconds != null
            ) {
                NavigationRemainingMetricsSource.ProjectedRoute
            } else {
                initialRemainingMetrics?.source ?: NavigationRemainingMetricsSource.ProjectedRoute
            }
        lowVisionActualMetricsCacheKey = null
        lowVisionActualMetricsCache = null
        lowVisionActualMetricsCacheCoordinate = null
        lowVisionActualMetricsCacheRecordedAtMillis = null
        lowVisionActualMetricsInFlightKey = null
        lowVisionActualMetricsFailedKey = null
        lowVisionActualMetricsLastAttemptCoordinate = null
        lowVisionActualMetricsLastAttemptRecordedAtMillis = null
        lastLowVisionRouteChangeAlertSegmentIndex = null
        latestTransitPresentation =
            routeSession?.resolveTransitPresentation(latestProgress?.activeLegIndex ?: 0)
        initialBriefingRequested = false
        hasPendingBriefingPlayback = false
        hasPendingInitialBriefing = false
        hasAutoPlayedInitialBriefing = false
        resetAutomaticTtsHistory()
        publishNavigationState()

        requestCurrentLocationRefresh()
    }

    fun currentRouteDetailRequest(): RouteNavigationRequest? {
        val request = navigationRequest ?: return null
        val currentSession = routeSession
        val currentRoute = currentSession?.route ?: request.selectedRoute
        val currentSelectionHandoff =
            request.selectionHandoff?.let { selectionHandoff ->
                selectionHandoff.copy(
                    routeId = currentSession?.routeId ?: selectionHandoff.routeId,
                    sessionId = currentSession?.sessionId ?: selectionHandoff.sessionId,
                )
            }
        return if (currentRoute == request.selectedRoute && currentSelectionHandoff == request.selectionHandoff) {
            request
        } else {
            request.copy(
                selectedRoute = currentRoute,
                selectionHandoff = currentSelectionHandoff,
            )
        }
    }

    fun currentRouteBookmarkDraft(): RouteBookmarkDraft? =
        navigationRequest?.toRouteBookmarkDraft(routeSession = routeSession)

    fun currentRatingSessionId(): String? =
        routeSession?.latestEndedSessionId ?: routeSession?.sessionId

    fun onAction(action: NavigationUiAction) {
        when (action) {
            NavigationUiAction.NavigationEntered -> {
                requestCurrentLocationRefresh()
                requestInitialBriefingIfNeeded()
            }
            is NavigationUiAction.HazardReportSubmitted -> handleHazardReportSubmitted(action.reportId)
            NavigationUiAction.BackClicked -> requestExitNavigationConfirmation()
            NavigationUiAction.RouteDetailClicked -> {
                uiState.value.selectedRouteOption?.let { routeOption ->
                    if (uiState.value.canOpenRouteDetail) {
                        emitUiEvent(NavigationUiEvent.NavigateToRouteDetail(routeOption))
                    }
                }
            }
            NavigationUiAction.ReportClicked -> {
                emitUiEvent(NavigationUiEvent.NavigateToReport)
            }
            NavigationUiAction.CurrentLocationClicked -> {
                currentHeadingManager.startHeadingUpdates()
                trackingMode = trackingMode.nextOnCurrentLocationClick()
                if (latestLocationCoordinate != null) {
                    pendingCurrentLocationRecenter = false
                    locationRecenterRequestId += 1
                    publishNavigationState()
                } else {
                    val didRequestRefresh = requestCurrentLocationRefresh()
                    if (didRequestRefresh) {
                        pendingCurrentLocationRecenter = true
                    }
                }
            }
            NavigationUiAction.MapCameraMovedByUser -> {
                if (trackingMode != NavigationTrackingMode.IDLE) {
                    trackingMode = NavigationTrackingMode.IDLE
                    publishNavigationState()
                }
            }
            NavigationUiAction.ExitNavigationClicked -> requestExitNavigationConfirmation()
            NavigationUiAction.ExitNavigationDismissed -> {
                if (isExitConfirmDialogVisible) {
                    isExitConfirmDialogVisible = false
                    publishNavigationState()
                }
            }
            NavigationUiAction.ConfirmExitNavigationClicked -> {
                if (uiState.value.isExitEnabled) {
                    isExitConfirmDialogVisible = false
                    publishNavigationState()
                    finishNavigation(NavigationUiEvent.NavigateToArrival)
                }
            }
            NavigationUiAction.SaveBookmarkClicked -> {
                if (uiState.value.isExitEnabled) {
                    saveDestinationBookmarkAndNavigate()
                }
            }
            NavigationUiAction.NavigationCompleteClicked -> {
                if (uiState.value.isExitEnabled) {
                    finishNavigation(NavigationUiEvent.NavigateToArrival)
                }
            }
            is NavigationUiAction.SegmentTapped -> onSegmentTapped(action.index)
            NavigationUiAction.ReturnToActiveSegmentClicked -> returnToActiveSegment()
            is NavigationUiAction.VoiceGuidanceToggled -> onVoiceGuidanceToggled(action.enabled)
            NavigationUiAction.BriefingReplayClicked -> requestBriefing()
            NavigationUiAction.StopBriefingClicked -> emitUiEvent(NavigationUiEvent.StopBriefing)
        }
    }

    fun updateTextToSpeechState(
        isEnabled: Boolean,
        canSpeak: Boolean,
        status: NavigationTtsStatus,
    ) {
        mutableUiState.update { state ->
            val nextTts =
                state.tts.copy(
                    isEnabled = isEnabled,
                    canSpeak = canSpeak,
                    status = status,
                )
            state.copy(tts = nextTts.copy(fallbackMessage = nextTts.toFallbackMessage()))
        }
        maybeSpeakRealtimeGuidanceAutomatically()
        playPendingBriefingIfPossible()
    }

    override fun onCleared() {
        currentLocationManager.stopLocationUpdates()
        currentHeadingManager.stopHeadingUpdates()
        super.onCleared()
    }

    private fun requestCurrentLocationRefresh(): Boolean {
        val permissionManager = locationPermissionManager
        if (permissionManager == null) {
            currentLocationManager.startLocationUpdates(LocationUpdateProfile.NAVIGATION)
            currentHeadingManager.startHeadingUpdates()
            currentLocationManager.refreshLatestLocation()
            return true
        }

        permissionManager.refreshPermissionState()
        return if (permissionManager.permissionState.value is LocationPermissionState.Granted) {
            currentLocationManager.startLocationUpdates(LocationUpdateProfile.NAVIGATION)
            currentHeadingManager.startHeadingUpdates()
            currentLocationManager.refreshLatestLocation()
            true
        } else {
            false
        }
    }

    private fun collectLocationUpdates() {
        viewModelScope.launch {
            currentLocationManager.latestLocation
                .filterNotNull()
                .collect { snapshot ->
                    onLocationUpdated(snapshot)
                }
        }
    }

    private fun collectHeadingUpdates() {
        viewModelScope.launch {
            currentHeadingManager.latestHeading
                .filterNotNull()
                .collect { snapshot ->
                    onHeadingUpdated(snapshot)
                }
        }
    }

    private fun onHeadingUpdated(snapshot: HeadingSnapshot) {
        latestHeadingDegrees = snapshot.azimuthDegrees
        latestNavigationPose =
            latestNavigationPose?.withHeading(
                resolveNavigationHeading(
                    gpsBearingDegrees = latestGpsBearingDegrees,
                    sensorHeadingDegrees = latestHeadingDegrees,
                    routeFallbackDegrees = null,
                ),
            )
        if (navigationRequest != null) {
            publishNavigationState()
        }
    }

    private fun onLocationUpdated(snapshot: LocationSnapshot) {
        val currentSession = routeSession ?: return
        if (!shouldProcessLocation(snapshot, currentSession.route)) return

        val previousCoordinate = latestLocationCoordinate
        val previousProgressCoordinate = latestProgress?.coordinate
        val currentCoordinate = seedLatestLocationSnapshot(snapshot, currentSession.route)
        val routeMatch =
            routeMatcher.match(
                route = currentSession.route,
                snapshot = snapshot,
                previousMatch = latestRouteMatch,
            )
        latestRouteMatch = routeMatch
        val progressCoordinate = routeMatch?.matchedCoordinate ?: currentSession.route.resolveProgressCoordinate(currentCoordinate)
        latestProgress =
            currentSession.route
                .evaluateProgress(
                    current = progressCoordinate,
                    rawCurrent = currentCoordinate,
                    routeMatch = routeMatch,
                )
                ?.withStableNodeTransition(
                    route = currentSession.route,
                    previousCoordinate = previousProgressCoordinate ?: previousCoordinate,
                    currentCoordinate = currentCoordinate,
                    routeMatch = routeMatch,
                    recordedAtEpochMillis = snapshot.recordedAtEpochMillis,
                )
        val destinationCoordinate = navigationRequest?.destination?.coordinate ?: currentCoordinate

        val progress = latestProgress
        if (progress != null) {
            val nextGuidanceMode =
                resolveGuidanceMode(
                    route = currentSession.route,
                    progress = progress,
                    origin = navigationRequest?.origin?.coordinate,
                    recordedAtEpochMillis = snapshot.recordedAtEpochMillis,
                )
            if (nextGuidanceMode == NavigationGuidanceMode.RouteDetail) {
                resetLiveGuidancePresentation()
                applyRouteDetailGuidance(currentSession)
            } else if (isLowVisionMode && !progress.shouldUseProjectedRemainingMetrics()) {
                applyLowVisionActualRemainingMetrics(
                    current = currentCoordinate,
                    destination = destinationCoordinate,
                    recordedAtEpochMillis = snapshot.recordedAtEpochMillis,
                )
            } else {
                val remainingMetrics =
                    currentSession.route.resolveRemainingMetrics(
                        progress = progress,
                        destination = destinationCoordinate,
                        useLowVisionWalkingPace = isLowVisionMode,
                    )
                latestRemainingDistanceMeters = remainingMetrics.distanceMeters
                latestEstimatedMinutes = remainingMetrics.estimatedMinutes
                latestRemainingMetricsSource = remainingMetrics.source
            }
            latestTransitPresentation = currentSession.resolveTransitPresentation(progress.activeLegIndex)
            if (nextGuidanceMode == NavigationGuidanceMode.Realtime) {
                val liveDistanceMeters = currentSession.route.distanceToLiveGuidanceMeters(progress)
                latestLiveGuidanceRawDistanceMeters = liveDistanceMeters
                updateLiveGuidancePresentation(
                    segmentIndex = progress.activeSegmentIndex,
                    rawDistanceMeters = liveDistanceMeters,
                    recordedAtEpochMillis = snapshot.recordedAtEpochMillis,
                )
                maybePlayLowVisionRouteChangeAlert(currentSession.route, progress)
                syncActiveSegment(progress.activeSegmentIndex)
                maybeRefreshTransit(currentSession, progress, snapshot)
            }
            if (hasJoinedRealtimeRouteLine) {
                maybeRequestReroute(currentSession, progress, snapshot)
            }
            maybeSpeakDestinationSoon(
                current = currentCoordinate,
                progress = progress,
            )
            maybeCompleteNavigationAtDestination(
                current = currentCoordinate,
                progress = progress,
                recordedAtEpochMillis = snapshot.recordedAtEpochMillis,
            )
        } else {
            guidanceMode = NavigationGuidanceMode.RouteDetail
            resetRouteJoinStability()
            resetDestinationArrivalStability()
            resetLiveGuidancePresentation()
            applyRouteDetailGuidance(currentSession)
        }

        if (pendingCurrentLocationRecenter) {
            pendingCurrentLocationRecenter = false
            locationRecenterRequestId += 1
        }
        logLocationDebug(
            snapshot = snapshot,
            route = currentSession.route,
            progress = progress,
            guidanceMode = guidanceMode,
        )
        publishNavigationState()
        processPendingHazardReportReroute()
        maybeSpeakRealtimeGuidanceAutomatically()
    }

    private fun maybeCompleteNavigationAtDestination(
        current: GeoCoordinate,
        progress: NavigationProgressSnapshot,
        recordedAtEpochMillis: Long,
    ) {
        if (isEndNavigationInFlight) return
        val currentSession = routeSession ?: return
        if (!currentSession.route.isNearFinalRouteProgress(progress)) {
            resetDestinationArrivalStability()
            return
        }
        val finalRouteEndpoint = currentSession.route.finalRouteEndpoint() ?: return
        val distanceToFinalEndpointMeters = haversineDistanceMeters(current, finalRouteEndpoint)
        if (distanceToFinalEndpointMeters > NAVIGATION_DESTINATION_AUTO_ARRIVAL_RADIUS_METERS) {
            resetDestinationArrivalStability()
            return
        }

        destinationArrivalStableUpdateCount += 1
        val stableSince =
            destinationArrivalStableSinceEpochMillis
                ?: recordedAtEpochMillis.also { firstStableEpochMillis ->
                    destinationArrivalStableSinceEpochMillis = firstStableEpochMillis
                }
        val isStableEnough =
            destinationArrivalStableUpdateCount >= NAVIGATION_DESTINATION_AUTO_ARRIVAL_STABLE_UPDATE_COUNT ||
                recordedAtEpochMillis - stableSince >= NAVIGATION_DESTINATION_AUTO_ARRIVAL_STABLE_DURATION_MILLIS
        if (!isStableEnough) return

        resetDestinationArrivalStability()
        finishNavigation(
            event = NavigationUiEvent.NavigateToArrival,
            completionBriefingText = NAVIGATION_ARRIVAL_COMPLETION_TTS_TEXT,
        )
    }

    private fun maybeSpeakDestinationSoon(
        current: GeoCoordinate,
        progress: NavigationProgressSnapshot,
    ) {
        if (hasSpokenDestinationSoon) return
        if (!uiState.value.tts.canRequestBriefing) return
        val currentSession = routeSession ?: return
        if (!currentSession.route.isNearFinalRouteProgress(progress)) return
        val finalRouteEndpoint = currentSession.route.finalRouteEndpoint() ?: return
        if (haversineDistanceMeters(current, finalRouteEndpoint) > NAVIGATION_DESTINATION_SOON_RADIUS_METERS) return

        hasSpokenDestinationSoon = true
        emitUiEvent(NavigationUiEvent.SpeakBriefing(NAVIGATION_DESTINATION_SOON_TTS_TEXT))
    }

    private fun resolveGuidanceMode(
        route: RouteCandidate,
        progress: NavigationProgressSnapshot,
        origin: GeoCoordinate?,
        recordedAtEpochMillis: Long,
    ): NavigationGuidanceMode {
        val isInsideRealtimeEntryDistance =
            progress.distanceToRouteMeters <= NAVIGATION_ROUTE_REALTIME_ENTER_DISTANCE_METERS
        val hasOnRouteMatch = progress.routeMatchState == NavigationRouteMatchState.OnRoute
        val isOutsideRouteDetailDistance =
            progress.distanceToRouteMeters >= NAVIGATION_ROUTE_DETAIL_EXIT_DISTANCE_METERS
        val hasRenderableRealtimeProgress = route.hasRenderableRealtimeProgress(progress)
        val isWaitingForInitialRouteStart =
            !hasJoinedRealtimeRouteLine &&
                !hasOnRouteMatch &&
                route.shouldWaitForInitialRouteStartJoin(
                    origin = origin,
                    current = progress.coordinate,
                )

        return when (guidanceMode) {
            NavigationGuidanceMode.Realtime -> {
                if (progress.routeMatchState == NavigationRouteMatchState.OffRoute || isOutsideRouteDetailDistance || !hasRenderableRealtimeProgress) {
                    guidanceMode = NavigationGuidanceMode.RouteDetail
                    resetRouteJoinStability()
                } else if (isInsideRealtimeEntryDistance) {
                    resetRouteJoinStability()
                }
                guidanceMode
            }
            NavigationGuidanceMode.RouteDetail -> {
                if (isWaitingForInitialRouteStart) {
                    resetRouteJoinStability()
                } else if (isInsideRealtimeEntryDistance && hasOnRouteMatch && hasRenderableRealtimeProgress) {
                    routeJoinStableUpdateCount += 1
                    val stableSince =
                        routeJoinStableSinceEpochMillis
                            ?: recordedAtEpochMillis.also { firstStableEpochMillis ->
                                routeJoinStableSinceEpochMillis = firstStableEpochMillis
                            }
                    val isStableEnough =
                        routeJoinStableUpdateCount >= NAVIGATION_ROUTE_JOIN_STABLE_UPDATE_COUNT ||
                            recordedAtEpochMillis - stableSince >= NAVIGATION_ROUTE_JOIN_STABLE_DURATION_MILLIS
                    if (isStableEnough) {
                        guidanceMode = NavigationGuidanceMode.Realtime
                        hasJoinedRealtimeRouteLine = true
                        resetRouteJoinStability()
                    }
                } else if (isOutsideRouteDetailDistance) {
                    resetRouteJoinStability()
                } else if (!hasRenderableRealtimeProgress) {
                    resetRouteJoinStability()
                }
                guidanceMode
            }
        }
    }

    private fun resetRouteJoinStability() {
        routeJoinStableUpdateCount = 0
        routeJoinStableSinceEpochMillis = null
    }

    private fun resetDestinationArrivalStability() {
        destinationArrivalStableUpdateCount = 0
        destinationArrivalStableSinceEpochMillis = null
    }

    private fun resetNodeTransitionStability() {
        nodeTransitionCandidate = null
    }

    private fun NavigationProgressSnapshot.withStableNodeTransition(
        route: RouteCandidate,
        previousCoordinate: GeoCoordinate?,
        currentCoordinate: GeoCoordinate,
        routeMatch: NavigationRouteMatchResult?,
        recordedAtEpochMillis: Long,
    ): NavigationProgressSnapshot {
        if (guidanceMode != NavigationGuidanceMode.Realtime) {
            resetNodeTransitionStability()
            return this
        }
        if (routeMatch?.state == NavigationRouteMatchState.OffRoute) {
            resetNodeTransitionStability()
            return copy(activeSegmentIndex = this@NavigationViewModel.activeSegmentIndex)
        }
        if (distanceToRouteMeters >= NAVIGATION_ROUTE_DETAIL_EXIT_DISTANCE_METERS) {
            resetNodeTransitionStability()
            return copy(activeSegmentIndex = this@NavigationViewModel.activeSegmentIndex)
        }
        val currentActiveSegmentIndex = this@NavigationViewModel.activeSegmentIndex
        if (currentActiveSegmentIndex !in route.segments.indices || currentActiveSegmentIndex >= route.segments.lastIndex) {
            resetNodeTransitionStability()
            return this
        }
        if (activeSegmentIndex > currentActiveSegmentIndex && currentActiveSegmentIndex == 0) {
            resetNodeTransitionStability()
            return copy(activeSegmentIndex = currentActiveSegmentIndex + 1)
        }
        val gatedProgress =
            if (activeSegmentIndex != currentActiveSegmentIndex) {
                copy(activeSegmentIndex = currentActiveSegmentIndex)
            } else {
                this
            }

        val transitionCandidateIndex = currentActiveSegmentIndex + 1
        val shouldAdvance =
            route.shouldAdvancePastGuidanceNode(
                activeSegmentIndex = currentActiveSegmentIndex,
                previousCoordinate = previousCoordinate,
                currentCoordinate = currentCoordinate,
                progress = gatedProgress,
            )
        if (!shouldAdvance) {
            resetNodeTransitionStability()
            return gatedProgress
        }

        val previousCandidate = nodeTransitionCandidate
        val stableSince =
            if (previousCandidate?.segmentIndex == transitionCandidateIndex) {
                previousCandidate.stableSinceEpochMillis
            } else {
                recordedAtEpochMillis
            }
        val updateCount =
            if (previousCandidate?.segmentIndex == transitionCandidateIndex) {
                previousCandidate.updateCount + 1
            } else {
                1
            }
        nodeTransitionCandidate =
            NavigationNodeTransitionCandidate(
                segmentIndex = transitionCandidateIndex,
                updateCount = updateCount,
                stableSinceEpochMillis = stableSince,
            )
        val isStableEnough =
            updateCount >= NAVIGATION_NODE_TRANSITION_STABLE_UPDATE_COUNT ||
                recordedAtEpochMillis - stableSince >= NAVIGATION_NODE_TRANSITION_STABLE_DURATION_MILLIS
        if (!isStableEnough) return gatedProgress

        resetNodeTransitionStability()
        return gatedProgress.copy(activeSegmentIndex = transitionCandidateIndex)
    }

    private fun updateLiveGuidancePresentation(
        segmentIndex: Int,
        rawDistanceMeters: Int?,
        recordedAtEpochMillis: Long,
    ) {
        val displayDistanceMeters = rawDistanceMeters?.let(::toLiveGuidanceDisplayDistanceMeters)
        latestLiveGuidanceDisplayDistanceMeters = displayDistanceMeters
        if (displayDistanceMeters == null) {
            liveGuidanceDisplayState = null
            return
        }

        val previous = liveGuidanceDisplayState
        val shouldUpdate =
            previous == null ||
                previous.segmentIndex != segmentIndex ||
                previous.displayDistanceMeters != displayDistanceMeters ||
                recordedAtEpochMillis - previous.updatedAtEpochMillis >= NAVIGATION_LIVE_GUIDANCE_DISPLAY_THROTTLE_MILLIS

        if (shouldUpdate) {
            liveGuidanceDisplayState =
                NavigationLiveGuidanceDisplayState(
                    segmentIndex = segmentIndex,
                    displayDistanceMeters = displayDistanceMeters,
                    updatedAtEpochMillis = recordedAtEpochMillis,
                )
        } else {
            latestLiveGuidanceDisplayDistanceMeters = previous?.displayDistanceMeters
        }
    }

    private fun resetLiveGuidancePresentation() {
        latestLiveGuidanceRawDistanceMeters = null
        latestLiveGuidanceDisplayDistanceMeters = null
        liveGuidanceDisplayState = null
    }

    private fun logLocationDebug(
        snapshot: LocationSnapshot,
        route: RouteCandidate,
        progress: NavigationProgressSnapshot?,
        guidanceMode: NavigationGuidanceMode,
    ) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            val activeSegmentPolylineSize =
                progress
                    ?.activeSegmentIndex
                    ?.let(route::resolveSegmentDisplayPolyline)
                    ?.size
                    ?: 0
            Log.d(
                NAVIGATION_LOCATION_DEBUG_TAG,
                buildString {
                    append("lat=")
                    append(String.format(Locale.US, "%.6f", snapshot.latitude))
                    append(" lng=")
                    append(String.format(Locale.US, "%.6f", snapshot.longitude))
                    append(" accuracyMeters=")
                    append(snapshot.accuracyMeters)
                    append(" distanceToRouteMeters=")
                    append(progress?.distanceToRouteMeters)
                    append(" activeSegmentIndex=")
                    append(progress?.activeSegmentIndex)
                    append(" activeSegmentPolylineSize=")
                    append(activeSegmentPolylineSize)
                    append(" selectedRoutePolylineSize=")
                    append(route.navigationPolylinePoints().size)
                    append(" liveGuidanceDistanceRaw=")
                    append(latestLiveGuidanceRawDistanceMeters)
                    append(" liveGuidanceDistanceDisplay=")
                    append(latestLiveGuidanceDisplayDistanceMeters)
                    append(" guidanceMode=")
                    append(guidanceMode.name)
                    append(" routeJoinStableUpdateCount=")
                    append(routeJoinStableUpdateCount)
                },
            )
        }
    }

    private fun applyRouteDetailGuidance(currentSession: NavigationRouteSession) {
        latestRemainingDistanceMeters = currentSession.route.totalDistanceMeters()
        latestEstimatedMinutes = currentSession.route.summary.estimatedTimeMinutes
        latestRemainingMetricsSource = NavigationRemainingMetricsSource.ProjectedRoute
        latestTransitPresentation = currentSession.resolveTransitPresentation(activeLegIndex = 0)
        if (!isInspectingSegments) {
            activeSegmentIndex = NavigationOriginSegmentIndex
            focusedSegmentIndex = NavigationOriginSegmentIndex
            hasPendingActiveChange = false
        }
    }

    private fun applyLowVisionActualRemainingMetrics(
        current: GeoCoordinate,
        destination: GeoCoordinate,
        recordedAtEpochMillis: Long,
    ) {
        val key = LowVisionActualMetricsKey.from(current = current, destination = destination)
        val cachedMetrics = resolveReusableLowVisionActualMetrics(key, current, recordedAtEpochMillis)
        if (cachedMetrics != null) {
            latestRemainingDistanceMeters = cachedMetrics.distanceMeters
            latestEstimatedMinutes = cachedMetrics.estimatedMinutes
            latestRemainingMetricsSource = cachedMetrics.source
            return
        }

        latestRemainingDistanceMeters = null
        latestEstimatedMinutes = null
        latestRemainingMetricsSource = NavigationRemainingMetricsSource.Unavailable

        if (lowVisionActualMetricsInFlightKey == key) {
            return
        }
        if (!shouldRequestLowVisionActualMetrics(current = current, recordedAtEpochMillis = recordedAtEpochMillis)) {
            return
        }
        if (lowVisionActualMetricsFailedKey == key) {
            lowVisionActualMetricsFailedKey = null
        }

        requestLowVisionActualRemainingMetrics(
            key = key,
            current = current,
            destination = destination,
            recordedAtEpochMillis = recordedAtEpochMillis,
        )
    }

    private fun requestLowVisionActualRemainingMetrics(
        key: LowVisionActualMetricsKey,
        current: GeoCoordinate,
        destination: GeoCoordinate,
        recordedAtEpochMillis: Long,
    ) {
        lowVisionActualMetricsInFlightKey = key
        lowVisionActualMetricsLastAttemptCoordinate = current
        lowVisionActualMetricsLastAttemptRecordedAtMillis = recordedAtEpochMillis
        viewModelScope.launch {
            val metrics =
                try {
                    resolveLowVisionActualRemainingMetrics(
                        current = current,
                        destination = destination,
                    )
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    null
                }

            if (lowVisionActualMetricsInFlightKey != key) {
                return@launch
            }
            lowVisionActualMetricsInFlightKey = null

            if (metrics == null) {
                lowVisionActualMetricsFailedKey = key
                publishNavigationState()
                return@launch
            }

            lowVisionActualMetricsFailedKey = null
            lowVisionActualMetricsCacheKey = key
            lowVisionActualMetricsCache = metrics
            lowVisionActualMetricsCacheCoordinate = current
            lowVisionActualMetricsCacheRecordedAtMillis = recordedAtEpochMillis

            val latestCoordinate = latestLocationCoordinate ?: return@launch
            val latestKey = LowVisionActualMetricsKey.from(current = latestCoordinate, destination = destination)
            if (isLowVisionMode && latestKey == key && latestProgress?.shouldUseProjectedRemainingMetrics() == false) {
                latestRemainingDistanceMeters = metrics.distanceMeters
                latestEstimatedMinutes = metrics.estimatedMinutes
                latestRemainingMetricsSource = metrics.source
                publishNavigationState()
            }
        }
    }

    private suspend fun resolveLowVisionActualRemainingMetrics(
        current: GeoCoordinate,
        destination: GeoCoordinate,
    ): NavigationRemainingMetrics? {
        val originWaypoint =
            RouteWaypoint(
                name = "\uD604\uC7AC \uC704\uCE58",
                address = "\uD604\uC7AC \uC704\uCE58",
                coordinate = current,
            )
        val destinationWaypoint =
            RouteWaypoint(
                name = navigationRequest?.destination?.name.orEmpty().ifBlank { "\uBAA9\uC801\uC9C0" },
                placeId = navigationRequest?.destination?.placeId,
                address = navigationRequest?.destination?.address,
                coordinate = destination,
            )
        val walkRoute =
            try {
                routeRepository
                    .getFreshRouteSearchData(
                        RouteSearchQuery(
                            origin = originWaypoint,
                            destination = destinationWaypoint,
                            requestedOptions = LOW_VISION_ACTUAL_WALK_OPTIONS,
                        ),
                    ).selectLowVisionActualRoute(preferredOption = RouteOption.SAFE)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                null
            }
        if (walkRoute != null && walkRoute.summary.distanceMeters <= LOW_VISION_WALKABLE_DISTANCE_THRESHOLD_METERS) {
            return walkRoute.toActualRouteSearchMetrics()
        }

        val transitRoute =
            try {
                routeRepository
                    .getFreshTransitRouteSearchData(
                        RouteSearchQuery(
                            origin = originWaypoint,
                            destination = destinationWaypoint,
                            requestedOptions = LOW_VISION_ACTUAL_TRANSIT_OPTIONS,
                        ),
                    ).selectLowVisionActualRoute(preferredOption = RouteOption.RECOMMENDED)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                null
            }
        return transitRoute?.toActualRouteSearchMetrics()
    }

    private fun resolveReusableLowVisionActualMetrics(
        key: LowVisionActualMetricsKey,
        current: GeoCoordinate,
        recordedAtEpochMillis: Long,
    ): NavigationRemainingMetrics? {
        val cacheKey = lowVisionActualMetricsCacheKey ?: return null
        val cachedMetrics = lowVisionActualMetricsCache ?: return null
        if (!cacheKey.hasSameDestinationAs(key)) return null

        val cachedCoordinate = lowVisionActualMetricsCacheCoordinate ?: return null
        val cachedAt = lowVisionActualMetricsCacheRecordedAtMillis ?: return null
        if (recordedAtEpochMillis - cachedAt > LOW_VISION_ACTUAL_METRICS_CACHE_MAX_AGE_MILLIS) {
            return null
        }
        if (haversineDistanceMeters(cachedCoordinate, current) > LOW_VISION_ACTUAL_METRICS_REUSE_DISTANCE_METERS) {
            return null
        }
        return cachedMetrics
    }

    private fun shouldRequestLowVisionActualMetrics(
        current: GeoCoordinate,
        recordedAtEpochMillis: Long,
    ): Boolean {
        val lastAttemptAt = lowVisionActualMetricsLastAttemptRecordedAtMillis ?: return true
        val lastAttemptCoordinate = lowVisionActualMetricsLastAttemptCoordinate ?: return true
        val elapsedMillis = recordedAtEpochMillis - lastAttemptAt
        if (elapsedMillis >= LOW_VISION_ACTUAL_METRICS_MIN_REQUEST_INTERVAL_MILLIS) {
            return true
        }
        return haversineDistanceMeters(lastAttemptCoordinate, current) >= LOW_VISION_ACTUAL_METRICS_MIN_REQUEST_DISTANCE_METERS
    }

    private fun shouldProcessLocation(
        snapshot: LocationSnapshot,
        route: RouteCandidate,
    ): Boolean {
        if (snapshot.accuracyMeters != null && snapshot.accuracyMeters > NAVIGATION_MAX_ACCEPTED_ACCURACY_METERS) {
            return false
        }
        val lastProcessed = lastProcessedLocationEpochMillis
        if (lastProcessed != null) {
            if (snapshot.recordedAtEpochMillis <= lastProcessed) return false
            val elapsedMillis = snapshot.recordedAtEpochMillis - lastProcessed
            if (elapsedMillis < MIN_LOCATION_UPDATE_INTERVAL_MILLIS) {
                return false
            }
            val latestCoordinate = latestLocationCoordinate
            if (latestCoordinate != null &&
                snapshot.isImpossibleNavigationJumpFrom(
                    previousCoordinate = latestCoordinate,
                    elapsedMillis = elapsedMillis,
                    route = route,
                    activeSegmentIndex = activeSegmentIndex,
                )
            ) {
                return false
            }
        }
        return true
    }

    private fun onSegmentTapped(index: Int) {
        val request = routeSession?.route ?: navigationRequest?.selectedRoute ?: return
        if (index == NavigationOriginSegmentIndex) {
            focusedSegmentIndex = NavigationOriginSegmentIndex
            isInspectingSegments = true
            hasPendingActiveChange = false
            publishNavigationState()
            speakFocusedSegmentFromSideRail()
            return
        }
        if (index !in request.segments.indices) return

        focusedSegmentIndex = index
        isInspectingSegments = true
        hasPendingActiveChange = false
        publishNavigationState()
        speakFocusedSegmentFromSideRail()
    }

    private fun returnToActiveSegment() {
        if (navigationRequest == null) return

        focusedSegmentIndex = activeSegmentIndex
        isInspectingSegments = false
        hasPendingActiveChange = false
        currentHeadingManager.startHeadingUpdates()
        trackingMode = NavigationTrackingMode.FOLLOW
        publishNavigationState()
    }

    private fun seedLatestLocationSnapshot(
        snapshot: LocationSnapshot,
        route: RouteCandidate,
    ): GeoCoordinate {
        val currentCoordinate = GeoCoordinate(latitude = snapshot.latitude, longitude = snapshot.longitude)
        latestLocationCoordinate = currentCoordinate
        latestGpsBearingDegrees = snapshot.toUsableNavigationBearingDegrees()
        val displayCoordinate =
            snapshot.resolveSmoothedDisplayCoordinate(
                rawCoordinate = currentCoordinate,
                previousPose = latestNavigationPose,
            )
        latestNavigationPose =
            NavigationPose(
                rawLocation = currentCoordinate,
                displayLocation = displayCoordinate,
                heading =
                    resolveNavigationHeading(
                        gpsBearingDegrees = latestGpsBearingDegrees,
                        sensorHeadingDegrees = latestHeadingDegrees,
                        routeFallbackDegrees =
                            route.resolveNavigationRouteFallbackBearingDegrees(
                                current = currentCoordinate,
                            ),
                    ),
                recordedAtEpochMillis = snapshot.recordedAtEpochMillis,
            )
        lastProcessedLocationEpochMillis = snapshot.recordedAtEpochMillis
        return currentCoordinate
    }

    private fun publishNavigationState() {
        val request = navigationRequest ?: return
        val runtimeRequest = request.withSelectedRoute(routeSession?.route ?: request.selectedRoute)
        val screenState = runtimeRequest.toScreenState()
        val maxSegmentIndex = runtimeRequest.selectedRoute.segments.lastIndex.coerceAtLeast(0)

        activeSegmentIndex =
            if (activeSegmentIndex == NavigationOriginSegmentIndex) {
                NavigationOriginSegmentIndex
            } else {
                activeSegmentIndex.coerceIn(0, maxSegmentIndex)
            }
        focusedSegmentIndex =
            if (focusedSegmentIndex == NavigationOriginSegmentIndex) {
                NavigationOriginSegmentIndex
            } else {
                focusedSegmentIndex.coerceIn(0, maxSegmentIndex)
            }

        val mapFocusMode =
            if (isInspectingSegments) {
                NavigationMapFocusMode.FOCUSED
            } else {
                NavigationMapFocusMode.ACTIVE
            }
        val destinationEndpointDistanceMeters =
            latestProgress?.let { progress ->
                if (!runtimeRequest.selectedRoute.isNearFinalRouteProgress(progress)) return@let null
                val currentCoordinate = latestLocationCoordinate ?: latestNavigationPose?.displayLocation ?: return@let null
                runtimeRequest.selectedRoute.finalRouteEndpoint()?.let { endpoint ->
                    haversineDistanceMeters(currentCoordinate, endpoint).roundToInt()
                }
            }
        val destinationSoon =
            destinationEndpointDistanceMeters
                ?.let { distanceMeters -> distanceMeters <= NAVIGATION_DESTINATION_SOON_RADIUS_METERS }
                ?: false
        val hasPassedFinalRouteEndpoint =
            latestProgress?.let { progress ->
                runtimeRequest.selectedRoute.isNearFinalRouteProgress(progress) &&
                    progress.remainingRouteDistanceMeters <= NAVIGATION_NODE_TRANSITION_PASSED_DISTANCE_METERS.roundToInt()
            } ?: false
        val stepCard =
            runtimeRequest.toStepCardUiState(
                screenState = screenState,
                activeSegmentIndex = activeSegmentIndex,
                remainingDistanceMeters = latestRemainingDistanceMeters,
                estimatedMinutes = latestEstimatedMinutes,
                remainingMetricsSource = latestRemainingMetricsSource,
                transitPresentation = latestTransitPresentation,
                liveGuidanceRawDistanceMeters =
                    if (guidanceMode == NavigationGuidanceMode.Realtime) {
                        latestLiveGuidanceRawDistanceMeters
                    } else {
                        null
                    },
                liveGuidanceDisplayDistanceMeters =
                    if (guidanceMode == NavigationGuidanceMode.Realtime) {
                        latestLiveGuidanceDisplayDistanceMeters
                    } else {
                        null
                    },
                destinationDistanceMeters = destinationEndpointDistanceMeters,
                destinationSoon = destinationSoon,
                hasPassedFinalRouteEndpoint = hasPassedFinalRouteEndpoint,
            )
        val briefingText = stepCard.toNavigationBriefingText()
        val mapOverlay =
            runtimeRequest.toMapOverlayUiState(
                currentLocationCoordinate = latestNavigationPose?.displayLocation ?: latestLocationCoordinate,
                activeSegmentIndex = activeSegmentIndex,
                focusedSegmentIndex = focusedSegmentIndex,
                mapFocusMode = mapFocusMode,
                trackingMode = trackingMode,
                headingDegrees = null,
            )
        logSegmentMarkerDebugSummary(mapOverlay)

        mutableUiState.update { state ->
            state.copy(
                screenState = screenState,
                selectedRouteOption = runtimeRequest.selectedRoute.routeOption,
                mapPlaceholderTitle =
                    runtimeRequest.selectedRoute.title.toNavigationRouteTitle(
                        runtimeRequest.selectedRoute.routeOption,
                    ),
                mapPlaceholderDescription = runtimeRequest.toMapPlaceholderDescription(screenState),
                mapOverlay = mapOverlay,
                segmentSync =
                    runtimeRequest.toSegmentSyncUiState(
                        activeSegmentIndex = activeSegmentIndex,
                        focusedSegmentIndex = focusedSegmentIndex,
                        isInspectingSegments = isInspectingSegments,
                        hasPendingActiveChange = hasPendingActiveChange,
                        mapFocusMode = mapFocusMode,
                        transitPresentation = latestTransitPresentation,
                    ),
                focusedSegmentCard =
                    if (isInspectingSegments) {
                        runtimeRequest.toFocusedSegmentCardUiState(
                            focusedSegmentIndex = focusedSegmentIndex,
                            estimatedMinutes = latestEstimatedMinutes,
                            transitPresentation = latestTransitPresentation,
                        )
                    } else {
                        null
                    },
                pendingActiveChangeLabel =
                    if (hasPendingActiveChange) {
                        PENDING_ACTIVE_SEGMENT_LABEL
                    } else {
                        null
                    },
                stepCard = stepCard,
                locationRecenterRequestId = locationRecenterRequestId,
                exitCta = screenState.toExitCtaUiState(),
                isExitConfirmDialogVisible = isExitConfirmDialogVisible,
                tts =
                    state.tts.copy(
                        briefingText = briefingText,
                        fallbackMessage = state.tts.toFallbackMessage(),
                    ),
            )
        }
    }

    private fun logSegmentMarkerDebugSummary(mapOverlay: NavigationMapOverlayUiState) {
        if (!BuildConfig.DEBUG) return
        val summary = createNavigationSegmentMarkerDebugSummary(mapOverlay)
        if (summary == lastSegmentMarkerDebugSummary) return
        lastSegmentMarkerDebugSummary = summary
        runCatching {
            Log.d(NAVIGATION_LOCATION_DEBUG_TAG, "SegmentMarkerTrace[NavigationViewModel] $summary")
        }
    }

    private fun syncActiveSegment(nextActiveSegmentIndex: Int) {
        if (nextActiveSegmentIndex == activeSegmentIndex) return

        activeSegmentIndex = nextActiveSegmentIndex
        resetNodeTransitionStability()
        val activeSegment = routeSession?.route?.segments?.getOrNull(activeSegmentIndex)
        if (isInspectingSegments && activeSegment?.isRiskPrioritySegment() == true) {
            focusedSegmentIndex = activeSegmentIndex
            isInspectingSegments = false
            hasPendingActiveChange = false
        } else if (isInspectingSegments) {
            hasPendingActiveChange = focusedSegmentIndex != activeSegmentIndex
        } else {
            focusedSegmentIndex = activeSegmentIndex
            hasPendingActiveChange = false
        }
    }

    private fun maybePlayLowVisionRouteChangeAlert(
        route: RouteCandidate,
        progress: NavigationProgressSnapshot,
    ) {
        if (!isLowVisionMode) return
        val targetSegmentIndex = progress.activeSegmentIndex
        if (targetSegmentIndex !in route.segments.indices) return
        if (lastLowVisionRouteChangeAlertSegmentIndex == targetSegmentIndex) return
        if (!uiState.value.tts.isEnabled) return

        val distanceToBoundaryMeters = route.distanceToLiveGuidanceMeters(progress) ?: return
        if (distanceToBoundaryMeters !in 0..LOW_VISION_ROUTE_CHANGE_ALERT_DISTANCE_METERS) return

        lastLowVisionRouteChangeAlertSegmentIndex = targetSegmentIndex
        emitUiEvent(NavigationUiEvent.PlayRouteChangeAlert)
    }

    private fun emitUiEvent(event: NavigationUiEvent) {
        viewModelScope.launch {
            mutableUiEvent.emit(event)
        }
    }

    private fun emitUiEvents(vararg events: NavigationUiEvent) {
        viewModelScope.launch {
            events.forEach { event -> mutableUiEvent.emit(event) }
        }
    }

    private fun requestInitialBriefingIfNeeded() {
        if (initialBriefingRequested) return
        initialBriefingRequested = true
        hasPendingInitialBriefing = true
        playPendingBriefingIfPossible()
    }

    private fun requestExitNavigationConfirmation() {
        if (!uiState.value.isExitEnabled) return
        isExitConfirmDialogVisible = true
        publishNavigationState()
    }

    private fun onVoiceGuidanceToggled(enabled: Boolean) {
        mutableUiState.update { state ->
            val nextTts = state.tts.copy(isEnabled = enabled)
            state.copy(tts = nextTts.copy(fallbackMessage = nextTts.toFallbackMessage()))
        }

        if (enabled) {
            requestBriefingPlayback()
            val briefingText = consumePendingBriefingTextIfPossible()
            if (briefingText != null) {
                emitUiEvents(
                    NavigationUiEvent.SetVoiceGuidanceEnabled(enabled = true),
                    NavigationUiEvent.SpeakBriefing(briefingText),
                )
            } else {
                emitUiEvent(NavigationUiEvent.SetVoiceGuidanceEnabled(enabled = true))
            }
        } else {
            emitUiEvents(
                NavigationUiEvent.SetVoiceGuidanceEnabled(enabled = false),
                NavigationUiEvent.StopBriefing,
            )
        }
    }

    private fun requestBriefing() {
        requestBriefingPlayback()
        val briefingText = consumePendingBriefingTextIfPossible() ?: return
        emitUiEvent(NavigationUiEvent.SpeakBriefing(briefingText))
    }

    private fun speakFocusedSegmentFromSideRail() {
        val tts = uiState.value.tts
        if (!tts.canRequestBriefing) return
        val focusedCard = uiState.value.focusedSegmentCard ?: return
        val speechText = focusedCard.toSpeechText() ?: return
        emitUiEvents(
            NavigationUiEvent.StopBriefing,
            NavigationUiEvent.SpeakBriefing(speechText),
        )
    }

    private fun requestBriefingPlayback() {
        hasPendingBriefingPlayback = true
    }

    private fun playPendingBriefingIfPossible() {
        val briefingText = consumePendingBriefingTextIfPossible() ?: return
        emitUiEvent(NavigationUiEvent.SpeakBriefing(briefingText))
    }

    private fun consumePendingBriefingTextIfPossible(): String? {
        val tts = uiState.value.tts
        if (!tts.canRequestBriefing) return null
        val shouldAutoPlayInitialBriefing = hasPendingInitialBriefing && !hasAutoPlayedInitialBriefing
        if (!hasPendingBriefingPlayback && !shouldAutoPlayInitialBriefing) return null

        val briefingText =
            tts.briefingText
                .trim()
                .takeIf(String::isNotEmpty)
                ?: return null
        if (shouldAutoPlayInitialBriefing) {
            hasPendingInitialBriefing = false
            hasAutoPlayedInitialBriefing = true
        }
        hasPendingBriefingPlayback = false
        return briefingText
    }

    private fun resetAutomaticTtsHistory() {
        spokenInitialGuidanceKeys.clear()
        spokenApproachGuidanceKeys.clear()
        spokenNearGuidanceKeys.clear()
    }

    private fun maybeSpeakRealtimeGuidanceAutomatically() {
        val tts = uiState.value.tts
        if (!tts.canRequestBriefing) return
        if (guidanceMode != NavigationGuidanceMode.Realtime) return
        if (isInspectingSegments) return
        val currentSession = routeSession ?: return
        val route = currentSession.route
        val progress = latestProgress ?: return
        if (progress.activeSegmentIndex != activeSegmentIndex) return
        if (progress.distanceToRouteMeters > NAVIGATION_ROUTE_DETAIL_EXIT_DISTANCE_METERS) return
        if (
            route.isNearFinalRouteProgress(progress) &&
            latestLocationCoordinate?.let { current ->
                route.finalRouteEndpoint()?.let { endpoint ->
                    haversineDistanceMeters(current, endpoint) <= NAVIGATION_DESTINATION_SOON_RADIUS_METERS
                }
            } == true
        ) {
            return
        }

        val segment = route.segments.getOrNull(activeSegmentIndex) ?: return
        val heroDetail = route.toNavigationHeroDetail(segment)
        val guidanceKey =
            currentSession.toAutomaticTtsGuidanceKey(
                segmentIndex = activeSegmentIndex,
                segment = segment,
                guidanceAction = heroDetail.guidanceAction,
            )
        val distanceToGuidanceMeters =
            route.distanceToLiveGuidanceMeters(progress)
                ?: progress.remainingRouteDistanceMeters.takeIf {
                    progress.activeSegmentIndex >= route.segments.lastIndex
                }
                ?: segment.guidanceDisplayDistanceMeters()

        val speechStage =
            when {
                distanceToGuidanceMeters <= NAVIGATION_AUTO_TTS_NEAR_DISTANCE_METERS &&
                    !spokenNearGuidanceKeys.contains(guidanceKey) -> NavigationLiveGuidanceSpeechStage.NEAR_10M
                distanceToGuidanceMeters <= NAVIGATION_AUTO_TTS_APPROACH_DISTANCE_METERS &&
                    !spokenApproachGuidanceKeys.contains(guidanceKey) -> NavigationLiveGuidanceSpeechStage.APPROACH_30M
                !spokenInitialGuidanceKeys.contains(guidanceKey) -> NavigationLiveGuidanceSpeechStage.INITIAL
                else -> return
            }
        val speechText =
            formatNavigationLiveGuidanceSpeechText(
                action = heroDetail.guidanceAction,
                rawDistanceMeters = distanceToGuidanceMeters,
                stage = speechStage,
                fallbackTitle = heroDetail.title,
            )
        if (speechText.isBlank()) return

        when (speechStage) {
            NavigationLiveGuidanceSpeechStage.INITIAL -> {
                if (!spokenInitialGuidanceKeys.add(guidanceKey)) return
            }
            NavigationLiveGuidanceSpeechStage.APPROACH_30M -> {
                if (!spokenApproachGuidanceKeys.add(guidanceKey)) return
                spokenInitialGuidanceKeys += guidanceKey
            }
            NavigationLiveGuidanceSpeechStage.NEAR_10M -> {
                if (!spokenNearGuidanceKeys.add(guidanceKey)) return
                spokenApproachGuidanceKeys += guidanceKey
                spokenInitialGuidanceKeys += guidanceKey
            }
        }
        emitUiEvent(NavigationUiEvent.SpeakBriefing(speechText))
    }

    private fun saveDestinationBookmarkAndNavigate() {
        viewModelScope.launch {
            val bookmark = navigationRequest?.toDestinationBookmarkDataOrNull()
            if (bookmark == null) {
                emitUiEvent(NavigationUiEvent.ShowToast(NAVIGATION_BOOKMARK_UNAVAILABLE_MESSAGE))
                return@launch
            }
            val saveResult =
                runCatching {
                    bookmarkRepository.saveBookmark(bookmark)
                }

            saveResult
                .onSuccess {
                    println(
                        "BookmarkSaveTrace[NavigationViewModel] result=success placeId=${bookmark.placeId}",
                    )
                    completeNavigation(NavigationUiEvent.NavigateToSavedRoute)
                }.onFailure { throwable ->
                    println(
                        "BookmarkSaveTrace[NavigationViewModel] result=failure placeId=${bookmark.placeId} message=${throwable.message.orEmpty()}",
                    )
                    emitUiEvent(NavigationUiEvent.ShowToast(NAVIGATION_BOOKMARK_SAVE_FAILURE_MESSAGE))
                }
        }
    }

    private fun finishNavigation(
        event: NavigationUiEvent,
        completionBriefingText: String? = null,
    ) {
        viewModelScope.launch {
            completeNavigation(
                event = event,
                completionBriefingText = completionBriefingText,
            )
        }
    }

    private suspend fun completeNavigation(
        event: NavigationUiEvent,
        completionBriefingText: String? = null,
    ) {
        if (isEndNavigationInFlight) return
        isEndNavigationInFlight = true
        currentLocationManager.stopLocationUpdates()
        currentHeadingManager.stopHeadingUpdates()

        routeSession
            ?.routeId
            ?.let { routeId ->
                runCatching {
                    routeRepository.endRoute(routeId)
                }.onSuccess { sessionData ->
                    routeSession = routeSession?.withEndedSession(sessionData.sessionId)
                }
            }

        isEndNavigationInFlight = false
        publishNavigationState()
        mutableUiEvent.emit(NavigationUiEvent.StopBriefing)
        if (completionBriefingText != null && uiState.value.tts.isEnabled) {
            mutableUiEvent.emit(NavigationUiEvent.SpeakBriefing(completionBriefingText))
            delay(NAVIGATION_COMPLETION_TTS_NAVIGATION_DELAY_MILLIS)
        }
        mutableUiEvent.emit(event)
    }

    private fun maybeRefreshTransit(
        currentSession: NavigationRouteSession,
        progress: NavigationProgressSnapshot,
        snapshot: LocationSnapshot,
    ) {
        if (isTransitRefreshInFlight) return
        val trigger =
            currentSession.resolveTransitRefreshTrigger(
                progress = progress,
                recordedAtEpochMillis = snapshot.recordedAtEpochMillis,
            ) ?: return

        isTransitRefreshInFlight = true
        viewModelScope.launch {
            runCatching {
                routeRepository.refreshTransit(
                    routeId = trigger.routeId,
                    legSequence = trigger.legSequence,
                )
            }.onSuccess { refreshData ->
                routeSession =
                    routeSession?.withTransitRefresh(
                        legSequence = trigger.legSequence,
                        data = refreshData,
                        requestedAtMillis = snapshot.recordedAtEpochMillis,
                    )
                latestTransitPresentation =
                    routeSession?.resolveTransitPresentation(progress.activeLegIndex)
                publishNavigationState()
            }.onFailure {
                routeSession =
                    routeSession?.copy(
                        lastTransitRefreshAtMillisByLegSequence =
                            currentSession.lastTransitRefreshAtMillisByLegSequence +
                                (trigger.legSequence to snapshot.recordedAtEpochMillis),
                    )
            }
            isTransitRefreshInFlight = false
        }
    }

    private fun maybeRequestReroute(
        currentSession: NavigationRouteSession,
        progress: NavigationProgressSnapshot,
        snapshot: LocationSnapshot,
    ) {
        val isOffRoute = progress.routeMatchState == NavigationRouteMatchState.OffRoute
        deviationState =
            deviationState.next(
                isOffRoute = isOffRoute,
                recordedAtEpochMillis = snapshot.recordedAtEpochMillis,
            )
        if (!deviationState.shouldTriggerReroute(snapshot.recordedAtEpochMillis) || isRerouteInFlight) {
            return
        }

        val routeId = currentSession.routeId ?: return
        val currentCoordinate = progress.rawCoordinate
        deviationState = NavigationDeviationState()
        isRerouteInFlight = true
        if (uiState.value.tts.canRequestBriefing) {
            emitUiEvent(NavigationUiEvent.SpeakBriefing(NAVIGATION_REROUTE_TTS_TEXT))
        }
        viewModelScope.launch {
            runCatching {
                routeRepository.reroute(
                    routeId = routeId,
                    currentPoint = currentCoordinate,
                )
            }.onSuccess { rerouteData ->
                rerouteData.route?.let { reroutedRoute ->
                    applyReroutedRoute(
                        currentSession = currentSession,
                        reroutedRoute = reroutedRoute,
                        currentCoordinate = currentCoordinate,
                    )
                }
                publishNavigationState()
            }
            isRerouteInFlight = false
            processPendingHazardReportReroute()
        }
    }

    private fun handleHazardReportSubmitted(reportId: Long) {
        if (isLowVisionMode) return
        pendingHazardReportRerouteId = reportId
        processPendingHazardReportReroute()
    }

    private fun processPendingHazardReportReroute() {
        if (isLowVisionMode || isRerouteInFlight) return

        val reportId = pendingHazardReportRerouteId ?: return
        val currentSession = routeSession ?: return
        val routeId = currentSession.routeId ?: return
        val currentCoordinate = latestLocationCoordinate ?: return
        val currentProgress = latestProgress ?: currentSession.route.evaluateProgress(currentCoordinate)
        val activeLegSequence = currentProgress?.activeLegIndex?.let(currentSession.route.legs::getOrNull)?.sequence

        if (!shouldAttemptHazardReportReroute(currentSession.route, currentProgress?.activeLegIndex)) {
            pendingHazardReportRerouteId = null
            emitUiEvent(NavigationUiEvent.ShowDuribalCallDialog)
            return
        }

        pendingHazardReportRerouteId = null
        isRerouteInFlight = true
        viewModelScope.launch {
            val rerouteResult =
                runCatching {
                    reportRepository.rerouteAfterHazardReport(
                        reportId = reportId,
                        routeId = routeId,
                        currentPoint = currentCoordinate,
                        activeLegSequence = activeLegSequence,
                    )
                }.getOrNull()

            if (rerouteResult?.rerouted == true && rerouteResult.route != null) {
                applyReroutedRoute(
                    currentSession = currentSession,
                    reroutedRoute = rerouteResult.route,
                    currentCoordinate = currentCoordinate,
                )
                publishNavigationState()
            } else {
                emitUiEvent(NavigationUiEvent.ShowDuribalCallDialog)
            }

            isRerouteInFlight = false
            processPendingHazardReportReroute()
        }
    }

    private fun shouldAttemptHazardReportReroute(
        route: RouteCandidate,
        activeLegIndex: Int?,
    ): Boolean {
        return when (route.transportMode) {
            RouteTransportMode.WALK -> true
            RouteTransportMode.PUBLIC_TRANSIT -> {
                val activeLeg = activeLegIndex?.let(route.legs::getOrNull) ?: return false
                activeLeg.role == RouteLegRole.WALK_TO_TRANSIT ||
                    activeLeg.role == RouteLegRole.WALK_TO_DESTINATION
            }
        }
    }

    private fun applyReroutedRoute(
        currentSession: NavigationRouteSession,
        reroutedRoute: RouteCandidate,
        currentCoordinate: GeoCoordinate,
    ) {
        routeSession = currentSession.withReroutedRoute(reroutedRoute)
        latestRouteMatch = null
        resetAutomaticTtsHistory()
        latestProgress = reroutedRoute.evaluateProgress(currentCoordinate)
        val remainingMetrics =
            latestProgress?.let { progress ->
                reroutedRoute.resolveRemainingMetrics(
                    progress = progress,
                    destination = navigationRequest?.destination?.coordinate ?: currentCoordinate,
                    useLowVisionWalkingPace = isLowVisionMode,
                )
            }
        latestRemainingDistanceMeters =
            remainingMetrics?.distanceMeters ?: reroutedRoute.totalDistanceMeters()
        latestEstimatedMinutes =
            remainingMetrics?.estimatedMinutes ?: reroutedRoute.summary.estimatedTimeMinutes
        latestRemainingMetricsSource =
            remainingMetrics?.source ?: NavigationRemainingMetricsSource.ProjectedRoute
        latestTransitPresentation =
            routeSession?.resolveTransitPresentation(latestProgress?.activeLegIndex ?: 0)
        val reroutedSegmentIndex = latestProgress?.activeSegmentIndex ?: 0
        activeSegmentIndex = reroutedSegmentIndex
        focusedSegmentIndex = reroutedSegmentIndex
        isInspectingSegments = false
        hasPendingActiveChange = false
        lastLowVisionRouteChangeAlertSegmentIndex = null
    }

    companion object {
        fun provideFactory(
            currentLocationManager: CurrentLocationManager,
            currentHeadingManager: CurrentHeadingManager = NoOpCurrentHeadingManager,
            locationPermissionManager: LocationPermissionManager? = null,
            bookmarkRepository: BookmarkRepository,
            routeRepository: RouteRepository,
            reportRepository: ReportRepository,
            isLowVisionMode: Boolean = false,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    NavigationViewModel(
                        currentLocationManager = currentLocationManager,
                        currentHeadingManager = currentHeadingManager,
                        locationPermissionManager = locationPermissionManager,
                        bookmarkRepository = bookmarkRepository,
                        routeRepository = routeRepository,
                        reportRepository = reportRepository,
                        initialLowVisionMode = isLowVisionMode,
                    ) as T
            }
    }
}

private data class NavigationRouteSession(
    val route: RouteCandidate,
    val routeId: String? = route.serverRouteId,
    val sessionId: String? = null,
    val latestEndedSessionId: String? = null,
    val transitRefreshByLegSequence: Map<Int, RouteTransitRefreshData> = emptyMap(),
    val lastTransitRefreshAtMillisByLegSequence: Map<Int, Long> = emptyMap(),
) {
    fun withEndedSession(sessionId: String): NavigationRouteSession =
        copy(latestEndedSessionId = sessionId)

    fun withTransitRefresh(
        legSequence: Int,
        data: RouteTransitRefreshData,
        requestedAtMillis: Long,
    ): NavigationRouteSession =
        copy(
            transitRefreshByLegSequence = transitRefreshByLegSequence + (legSequence to data),
            lastTransitRefreshAtMillisByLegSequence =
                lastTransitRefreshAtMillisByLegSequence + (legSequence to requestedAtMillis),
        )

    fun withReroutedRoute(reroutedRoute: RouteCandidate): NavigationRouteSession =
        copy(
            route = reroutedRoute,
            routeId = reroutedRoute.serverRouteId ?: routeId,
            transitRefreshByLegSequence = emptyMap(),
            lastTransitRefreshAtMillisByLegSequence = emptyMap(),
        )

    fun toAutomaticTtsGuidanceKey(
        segmentIndex: Int,
        segment: RouteSegment,
        guidanceAction: NavigationGuidanceAction,
    ): String =
        listOf(
            sessionId.orEmpty(),
            routeId.orEmpty(),
            route.serverRouteId.orEmpty(),
            segmentIndex.toString(),
            segment.sequence.toString(),
            segment.sourceLegSequence?.toString().orEmpty(),
            segment.sourceStepSequence?.toString().orEmpty(),
            segment.guidanceType?.name.orEmpty(),
            segment.guidanceDirection?.name.orEmpty(),
            guidanceAction.name,
        ).joinToString(separator = "|")

    fun resolveTransitRefreshTrigger(
        progress: NavigationProgressSnapshot,
        recordedAtEpochMillis: Long,
    ): NavigationTransitRefreshTrigger? {
        val routeId = routeId ?: return null
        val activeLeg = route.legs.getOrNull(progress.activeLegIndex) ?: return null
        if (activeLeg.role != RouteLegRole.WALK_TO_TRANSIT) return null

        val transitLeg =
            route.legs
                .drop(progress.activeLegIndex + 1)
                .firstOrNull { leg -> leg.type == RouteLegType.BUS || leg.type == RouteLegType.SUBWAY }
                ?: return null
        val boardingStop = transitLeg.boardingStop ?: return null
        val thresholdMeters =
            when (transitLeg.type) {
                RouteLegType.BUS -> BUS_STOP_REFRESH_DISTANCE_METERS
                RouteLegType.SUBWAY -> SUBWAY_ELEVATOR_REFRESH_DISTANCE_METERS
                RouteLegType.WALK -> return null
            }
        val lastRefreshAt = lastTransitRefreshAtMillisByLegSequence[transitLeg.sequence]
        if (
            lastRefreshAt != null &&
            recordedAtEpochMillis - lastRefreshAt < TRANSIT_REFRESH_COOLDOWN_MILLIS
        ) {
            return null
        }
        val distanceToBoardingStopMeters =
            haversineDistanceMeters(progress.coordinate, boardingStop.coordinate)
        if (distanceToBoardingStopMeters > thresholdMeters) return null

        return NavigationTransitRefreshTrigger(
            routeId = routeId,
            legSequence = transitLeg.sequence,
        )
    }

    fun resolveTransitPresentation(activeLegIndex: Int): NavigationTransitPresentation? {
        val activeLeg = route.legs.getOrNull(activeLegIndex) ?: return null
        val targetLeg =
            when {
                activeLeg.role == RouteLegRole.WALK_TO_TRANSIT ->
                    route.legs
                        .drop(activeLegIndex + 1)
                        .firstOrNull { leg -> leg.type == RouteLegType.BUS || leg.type == RouteLegType.SUBWAY }

                activeLeg.role == RouteLegRole.TRANSIT &&
                    (activeLeg.type == RouteLegType.BUS || activeLeg.type == RouteLegType.SUBWAY) ->
                    activeLeg

                else -> null
            } ?: return null

        val refreshData = transitRefreshByLegSequence[targetLeg.sequence]
        val refreshedArrival =
            refreshData
                ?.transits
                ?.firstOrNull { arrival -> arrival.routeNo == targetLeg.routeNo }
                ?: refreshData?.transits?.firstOrNull()
        val fallbackLaneOption =
            targetLeg.laneOptions.firstOrNull { option -> option.routeNo == targetLeg.routeNo }
                ?: targetLeg.laneOptions.firstOrNull()
        val waitMinutes = refreshedArrival?.remainingMinute ?: fallbackLaneOption?.remainingMinute
        val transitLabel =
            when (targetLeg.type) {
                RouteLegType.BUS -> targetLeg.routeNo?.let { routeNo -> "${routeNo}번 버스" } ?: "버스"
                RouteLegType.SUBWAY -> targetLeg.routeNo ?: "지하철"
                RouteLegType.WALK -> "대중교통"
            }
        val statusLabel =
            when (refreshData?.arrivalStatus) {
                "REALTIME_AVAILABLE" -> "실시간 도착"
                "SCHEDULE_BASED" -> "시간표 기준"
                "NO_CURRENT_ARRIVAL",
                "ARRIVAL_UNKNOWN",
                    -> "도착 정보 없음"

                else -> "도착 정보"
            }
        val supportingText =
            when {
                waitMinutes != null -> "${arrivalBasis(refreshData?.arrivalStatus)} $transitLabel ${waitMinutes}분 후 도착 예정"
                targetLeg.boardingStop != null -> "${targetLeg.boardingStop.name} 승차 정보 없음"
                else -> "승차 정보 없음"
            }

        return NavigationTransitPresentation(
            statusLabel = statusLabel,
            supportingText = supportingText,
            info = targetLeg.toNavigationTransitInfo(arrivalRouteNo = refreshedArrival?.routeNo, arrivalMinutes = waitMinutes),
        )
    }
}

private fun arrivalBasis(arrivalStatus: String?): String =
    when (arrivalStatus) {
        "REALTIME_AVAILABLE" -> "실시간 기준"
        "SCHEDULE_BASED" -> "시간표 기준"
        else -> "도착 정보 기준"
    }

private data class NavigationProgressSnapshot(
    val coordinate: GeoCoordinate,
    val rawCoordinate: GeoCoordinate,
    val activeSegmentIndex: Int,
    val activeLegIndex: Int,
    val routeMatchState: NavigationRouteMatchState = NavigationRouteMatchState.OnRoute,
    val routeMatchConfidence: Double = 1.0,
    val distanceToRouteMeters: Double,
    val distanceAlongPolylineMeters: Double,
    val routePolylineDistanceMeters: Double,
    val distanceAlongRouteMeters: Int,
    val remainingRouteDistanceMeters: Int,
    val remainingDurationSeconds: Int,
)

private data class NavigationRemainingMetrics(
    val distanceMeters: Int,
    val estimatedMinutes: Int,
    val source: NavigationRemainingMetricsSource,
)

private enum class NavigationRemainingMetricsSource {
    ProjectedRoute,
    ActualDirect,
    ActualRouteSearch,
    Unavailable,
}

private data class NavigationDeviationState(
    val consecutiveOffRouteCount: Int = 0,
    val firstOffRouteEpochMillis: Long? = null,
) {
    fun next(
        isOffRoute: Boolean,
        recordedAtEpochMillis: Long,
    ): NavigationDeviationState =
        if (!isOffRoute) {
            NavigationDeviationState()
        } else {
            NavigationDeviationState(
                consecutiveOffRouteCount = consecutiveOffRouteCount + 1,
                firstOffRouteEpochMillis = firstOffRouteEpochMillis ?: recordedAtEpochMillis,
            )
        }

    fun shouldTriggerReroute(recordedAtEpochMillis: Long): Boolean {
        if (consecutiveOffRouteCount >= REROUTE_OFF_ROUTE_CONSECUTIVE_COUNT) {
            return true
        }
        val firstOffRouteAt = firstOffRouteEpochMillis ?: return false
        return recordedAtEpochMillis - firstOffRouteAt >= REROUTE_OFF_ROUTE_DURATION_MILLIS
    }
}

private data class NavigationTransitRefreshTrigger(
    val routeId: String,
    val legSequence: Int,
)

private data class NavigationTransitPresentation(
    val statusLabel: String,
    val supportingText: String,
    val info: NavigationTransitInfoUiState? = null,
)

private fun RouteLeg.toNavigationTransitInfo(
    arrivalRouteNo: String?,
    arrivalMinutes: Int?,
): NavigationTransitInfoUiState? {
    if (type != RouteLegType.BUS && type != RouteLegType.SUBWAY) return null
    val guidanceAction =
        when (type) {
            RouteLegType.BUS -> NavigationGuidanceAction.BUS
            RouteLegType.SUBWAY -> NavigationGuidanceAction.SUBWAY
            RouteLegType.WALK -> return null
        }
    val startName =
        boardingStop?.name?.takeIf(String::isNotBlank)
            ?: if (type == RouteLegType.SUBWAY) "출발역" else "출발 정류장"
    val endName =
        alightingStop?.name?.takeIf(String::isNotBlank)
            ?: if (type == RouteLegType.SUBWAY) "도착역" else "도착 정류장"
    val durationLabel =
        estimatedTimeMinutes?.takeIf { minute -> minute > 0 }?.let { minute -> "${minute}분" }
            ?: durationSeconds?.takeIf { seconds -> seconds > 0 }?.let { seconds -> "${((seconds + 59) / 60).coerceAtLeast(1)}분" }
    val routeNumbers =
        buildList {
            routeNo?.takeIf(String::isNotBlank)?.let(::add)
            addAll(laneOptions.mapNotNull { option -> option.routeNo?.takeIf(String::isNotBlank) })
            arrivalRouteNo?.takeIf(String::isNotBlank)?.let(::add)
        }.distinct()
    val arrivalByRouteNo =
        laneOptions
            .mapNotNull { option ->
                val optionRouteNo = option.routeNo?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val arrivalLabel =
                    option.remainingMinute?.let { minute -> "${minute}분" }
                        ?: option.estimatedTimeMinutes?.let { minute -> "${minute}분" }
                optionRouteNo to arrivalLabel
            }.toMap()
    val options =
        routeNumbers.map { routeNo ->
            RouteTransitOptionLabelUiState(
                typeLabel =
                    when (type) {
                        RouteLegType.SUBWAY -> "지하철"
                        else -> if (isLowFloor == true || laneOptions.any { option -> option.routeNo == routeNo && option.isLowFloor == true }) "저상" else "일반"
                    },
                routeNo = routeNo,
                arrivalLabel =
                    if (routeNo == arrivalRouteNo && arrivalMinutes != null) {
                        "${arrivalMinutes}분"
                    } else {
                        arrivalByRouteNo[routeNo]
                    },
            )
        }.take(NAVIGATION_TRANSIT_OPTION_LABEL_LIMIT)

    return NavigationTransitInfoUiState(
        guidanceAction = guidanceAction,
        startName = startName,
        endName = endName,
        durationLabel = durationLabel,
        optionLabels = options,
    )
}

internal data class RoutePolylineProjection(
    val distanceToPolylineMeters: Double,
    val distanceAlongPolylineMeters: Double,
    val totalPolylineDistanceMeters: Double,
    val projectedCoordinate: GeoCoordinate,
)

internal data class RouteSegmentProjection(
    val distanceToSegmentMeters: Double,
    val distanceAlongSegmentMeters: Double,
    val projectedCoordinate: GeoCoordinate,
)

private data class RouteSegmentDistanceSpan(
    val startDistanceMeters: Double,
    val endDistanceMeters: Double,
)

private fun RouteNavigationRequest.toDestinationBookmarkDataOrNull(): BookmarkData? =
    destination.toBookmarkDataOrNull(
        fallbackPlaceId = destination.toNavigationDestinationPlaceId(),
        fallbackPlaceName = destination.name.orEmpty().ifBlank { "목적지" },
    )

private fun RouteNavigationRequest.toRouteBookmarkDraft(
    routeSession: NavigationRouteSession? = null,
    route: RouteCandidate? = routeSession?.route ?: selectedRoute,
): RouteBookmarkDraft =
    RouteBookmarkDraft(
        routeId = routeSession?.routeId ?: selectionHandoff?.routeId ?: route?.serverRouteId ?: selectedRoute.serverRouteId,
        startLabel = origin.name.orEmpty().ifBlank { "출발지" },
        endLabel = destination.name.orEmpty().ifBlank { "목적지" },
        startPoint = origin.coordinate,
        endPoint = destination.coordinate,
        routeOption = route?.routeOption ?: selectedRoute.routeOption,
        distanceMeters = route?.summary?.distanceMeters?.takeIf { distance -> distance > 0 },
        durationMinutes = route?.summary?.estimatedTimeMinutes?.takeIf { duration -> duration > 0 },
        routeSnapshot = route,
    )

private fun RouteNavigationRequest.toDestinationBookmarkData(): BookmarkData {
    val destinationWaypoint = destination
    val placeId = destinationWaypoint.placeId ?: destinationWaypoint.toNavigationDestinationPlaceId()
    val placeName = destinationWaypoint.name.orEmpty().ifBlank { "목적지" }

    return BookmarkData(
        placeId = placeId,
        placeName = placeName,
        address = destinationWaypoint.address?.takeIf { address -> address.isNotBlank() },
        latitude = destinationWaypoint.coordinate.latitude,
        longitude = destinationWaypoint.coordinate.longitude,
        category = destinationWaypoint.category?.name,
    )
}

private fun RouteNavigationRequest.toRouteBookmarkDraft(
    route: RouteCandidate? = selectedRoute,
): RouteBookmarkDraft =
    RouteBookmarkDraft(
        routeId = route?.serverRouteId,
        startLabel = origin.name.orEmpty().ifBlank { "출발지" },
        endLabel = destination.name.orEmpty().ifBlank { "목적지" },
        startPoint = origin.coordinate,
        endPoint = destination.coordinate,
        routeOption = route?.routeOption ?: selectedRoute.routeOption,
        distanceMeters = route?.summary?.distanceMeters?.takeIf { distance -> distance > 0 },
        durationMinutes = route?.summary?.estimatedTimeMinutes?.takeIf { duration -> duration > 0 },
        routeSnapshot = route,
    )

private fun RouteWaypoint.toNavigationDestinationPlaceId(): String =
    "navigation-destination:${coordinate.latitude},${coordinate.longitude}"

private fun RouteNavigationRequest.withSelectedRoute(route: RouteCandidate): RouteNavigationRequest =
    copy(selectedRoute = route)

private fun RouteCandidate.totalDistanceMeters(): Int =
    summary.distanceMeters
        .takeIf { distanceMeters -> distanceMeters > 0 }
        ?: legs.sumOf { leg -> leg.distanceMeters ?: 0 }.takeIf { distanceMeters -> distanceMeters > 0 }
        ?: segments.sumOf(RouteSegment::distanceMeters).takeIf { distanceMeters -> distanceMeters > 0 }
        ?: navigationPolylinePoints().totalPolylineDistanceMeters().roundToInt()

private fun RouteCandidate.totalDurationSeconds(): Int =
    summary.durationSeconds
        ?.takeIf { durationSeconds -> durationSeconds > 0 }
        ?: legs.sumOf { leg -> leg.durationSeconds ?: 0 }.takeIf { durationSeconds -> durationSeconds > 0 }
        ?: summary.estimatedTimeMinutes.takeIf { estimatedTimeMinutes -> estimatedTimeMinutes > 0 }?.times(60)
        ?: 0

private fun RouteCandidate.evaluateProgress(
    current: GeoCoordinate,
    rawCurrent: GeoCoordinate = current,
    routeMatch: NavigationRouteMatchResult? = null,
): NavigationProgressSnapshot? {
    val routePoints = navigationPolylinePoints()
    val projection = routeMatch?.matchedProjection ?: projectOntoPolylineMeters(current = current, polyline = routePoints) ?: return null
    val rawProjection = routeMatch?.rawProjection ?: projectOntoPolylineMeters(current = rawCurrent, polyline = routePoints) ?: projection
    val progressRatio =
        if (projection.totalPolylineDistanceMeters <= 0.0) {
            0.0
        } else {
            (projection.distanceAlongPolylineMeters / projection.totalPolylineDistanceMeters).coerceIn(0.0, 1.0)
        }
    val totalDistanceMeters = totalDistanceMeters()
    val totalDurationSeconds = totalDurationSeconds()
    val remainingRatio = (1.0 - progressRatio).coerceIn(0.0, 1.0)

    return NavigationProgressSnapshot(
        coordinate = current,
        rawCoordinate = rawCurrent,
        activeSegmentIndex = resolveActiveSegmentIndex(projection),
        activeLegIndex = resolveActiveLegIndex(progressRatio),
        routeMatchState = routeMatch?.state ?: NavigationRouteMatchState.OnRoute,
        routeMatchConfidence = routeMatch?.confidence ?: 1.0,
        distanceToRouteMeters = rawProjection.distanceToPolylineMeters,
        distanceAlongPolylineMeters = projection.distanceAlongPolylineMeters,
        routePolylineDistanceMeters = projection.totalPolylineDistanceMeters,
        distanceAlongRouteMeters = (totalDistanceMeters * progressRatio).roundToInt().coerceAtLeast(0),
        remainingRouteDistanceMeters =
            (totalDistanceMeters * remainingRatio).roundToInt().coerceAtLeast(0),
        remainingDurationSeconds =
            (totalDurationSeconds * remainingRatio).roundToInt().coerceAtLeast(0),
    )
}

private fun RouteCandidate.resolveRemainingMetrics(
    progress: NavigationProgressSnapshot,
    destination: GeoCoordinate,
    useLowVisionWalkingPace: Boolean,
): NavigationRemainingMetrics =
    resolveRemainingMetrics(
        progress = progress,
        destination = destination,
        directDistanceMeters = haversineDistanceMeters(progress.rawCoordinate, destination).roundToInt().coerceAtLeast(0),
        useLowVisionWalkingPace = useLowVisionWalkingPace,
    )

private fun RouteCandidate.resolveRemainingMetrics(
    progress: NavigationProgressSnapshot,
    destination: GeoCoordinate,
    directDistanceMeters: Int,
    useLowVisionWalkingPace: Boolean,
): NavigationRemainingMetrics {
    if (!progress.shouldUseProjectedRemainingMetrics()) {
        return resolveActualRemainingMetrics(
            current = progress.coordinate,
            destination = destination,
            directDistanceMeters = directDistanceMeters,
            useLowVisionWalkingPace = useLowVisionWalkingPace,
        )
    }

    val projectedDistanceMeters =
        (progress.remainingRouteDistanceMeters + progress.distanceToRouteMeters.roundToInt())
            .coerceAtLeast(0)
    val adjustedDistanceMeters = maxOf(projectedDistanceMeters, directDistanceMeters)
    val adjustedDurationSeconds =
        maxOf(
            progress.remainingDurationSeconds,
            estimateDurationSeconds(
                distanceMeters = adjustedDistanceMeters,
                minimumDurationSeconds = progress.remainingDurationSeconds,
            ),
        )

    return NavigationRemainingMetrics(
        distanceMeters = adjustedDistanceMeters,
        estimatedMinutes = adjustedDurationSeconds.toEtaMinutes(),
        source = NavigationRemainingMetricsSource.ProjectedRoute,
    )
}

private fun RouteCandidate.resolveActualRemainingMetrics(
    current: GeoCoordinate,
    destination: GeoCoordinate,
    directDistanceMeters: Int =
        haversineDistanceMeters(current, destination)
            .roundToInt()
            .coerceAtLeast(0),
    useLowVisionWalkingPace: Boolean = false,
): NavigationRemainingMetrics {
    if (directDistanceMeters <= 0) {
        return NavigationRemainingMetrics(
            distanceMeters = 0,
            estimatedMinutes = 0,
            source = NavigationRemainingMetricsSource.ActualDirect,
        )
    }

    return NavigationRemainingMetrics(
        distanceMeters = directDistanceMeters,
        estimatedMinutes =
            estimateDirectDurationSeconds(
                distanceMeters = directDistanceMeters,
                useLowVisionWalkingPace = useLowVisionWalkingPace,
            ).toEtaMinutes(),
        source = NavigationRemainingMetricsSource.ActualDirect,
    )
}

private fun NavigationProgressSnapshot.shouldUseProjectedRemainingMetrics(): Boolean =
    distanceToRouteMeters <= PROJECTED_PROGRESS_MAX_DISTANCE_METERS

private fun RouteCandidate.estimateDurationSeconds(
    distanceMeters: Int,
    minimumDurationSeconds: Int = MIN_NAVIGATION_DURATION_SECONDS,
): Int {
    val totalDistanceMeters = totalDistanceMeters()
    val totalDurationSeconds = totalDurationSeconds()
    val estimatedDurationSeconds =
        if (totalDistanceMeters > 0 && totalDurationSeconds > 0) {
            ((distanceMeters.toDouble() / totalDistanceMeters) * totalDurationSeconds).roundToInt()
        } else {
            ceil(distanceMeters / FALLBACK_NAVIGATION_SPEED_METERS_PER_SECOND).toInt()
    }
    return estimatedDurationSeconds.coerceAtLeast(minimumDurationSeconds)
}

private fun estimateDirectDurationSeconds(
    distanceMeters: Int,
    useLowVisionWalkingPace: Boolean,
): Int {
    val walkingSpeedMetersPerSecond =
        if (useLowVisionWalkingPace) {
            LOW_VISION_FALLBACK_WALKING_SPEED_METERS_PER_SECOND
        } else {
            FALLBACK_NAVIGATION_SPEED_METERS_PER_SECOND
    }
    return ceil(distanceMeters / walkingSpeedMetersPerSecond).toInt().coerceAtLeast(MIN_NAVIGATION_DURATION_SECONDS)
}

private fun RouteSearchData.selectLowVisionActualRoute(preferredOption: RouteOption): RouteCandidate? =
    findRoute(preferredOption) ?: primaryRoute ?: routes.firstOrNull()

private fun RouteCandidate.toActualRouteSearchMetrics(): NavigationRemainingMetrics {
    val distanceMeters = summary.distanceMeters.coerceAtLeast(0)
    val estimatedMinutes =
        summary.durationSeconds
            ?.takeIf { durationSeconds -> durationSeconds > 0 }
            ?.toEtaMinutes()
            ?: summary.estimatedTimeMinutes.coerceAtLeast(0)
    return NavigationRemainingMetrics(
        distanceMeters = distanceMeters,
        estimatedMinutes = estimatedMinutes,
        source = NavigationRemainingMetricsSource.ActualRouteSearch,
    )
}

private fun RouteCandidate.resolveActiveSegmentIndex(projection: RoutePolylineProjection): Int {
    if (segments.isEmpty()) return 0
    val routePoints = navigationPolylinePoints()
    if (routePoints.size < 2) return 0
    val currentDistance = projection.distanceAlongPolylineMeters
    segments.forEachIndexed { segmentIndex, segment ->
        if (!segment.shouldHoldRealtimeGuidanceUntilSegmentEnd()) return@forEachIndexed
        val span = resolveSegmentRouteSpanMeters(segmentIndex = segmentIndex, routePoints = routePoints) ?: return@forEachIndexed
        if (
            currentDistance >= span.startDistanceMeters - NAVIGATION_LIVE_GUIDANCE_TARGET_AHEAD_TOLERANCE_METERS &&
            currentDistance <= span.endDistanceMeters + NAVIGATION_NODE_TRANSITION_PASSED_DISTANCE_METERS
        ) {
            return segmentIndex
        }
    }
    return segments.indices.firstOrNull { segmentIndex ->
        val targetCoordinate = resolveRealtimeStepTargetCoordinate(segmentIndex) ?: return@firstOrNull false
        val targetProjection = projectOntoPolylineMeters(current = targetCoordinate, polyline = routePoints) ?: return@firstOrNull false
        targetProjection.distanceAlongPolylineMeters >= currentDistance - NAVIGATION_LIVE_GUIDANCE_TARGET_AHEAD_TOLERANCE_METERS
    } ?: segments.lastIndex
}

private fun RouteCandidate.shouldAdvancePastGuidanceNode(
    activeSegmentIndex: Int,
    previousCoordinate: GeoCoordinate?,
    currentCoordinate: GeoCoordinate,
    progress: NavigationProgressSnapshot,
): Boolean {
    val routePoints = navigationPolylinePoints()
    if (routePoints.size < 2) return false
    val activeSegment = segments.getOrNull(activeSegmentIndex) ?: return false
    val isSegmentHoldGuidance = activeSegment.shouldHoldRealtimeGuidanceUntilSegmentEnd()
    val guidanceNode =
        if (isSegmentHoldGuidance) {
            resolveSegmentEndCoordinate(activeSegmentIndex)
        } else {
            resolveRealtimeStepTargetCoordinate(activeSegmentIndex)
        } ?: return false
    val guidanceNodeProjection = projectOntoPolylineMeters(current = guidanceNode, polyline = routePoints) ?: return false
    val previousRouteProjection =
        previousCoordinate?.let { coordinate -> projectOntoPolylineMeters(current = coordinate, polyline = routePoints) }
    if (
        previousRouteProjection != null &&
        progress.distanceAlongPolylineMeters < previousRouteProjection.distanceAlongPolylineMeters - NAVIGATION_LIVE_GUIDANCE_TARGET_AHEAD_TOLERANCE_METERS
    ) {
        return false
    }
    val nextSegmentIndex = activeSegmentIndex + 1
    val transitionSegmentPolyline =
        if (isSegmentHoldGuidance) {
            resolveSegmentDisplayPolyline(nextSegmentIndex)
        } else {
            resolveSegmentDisplayPolyline(activeSegmentIndex)
        }.takeIf { polyline -> polyline.size >= 2 }
            ?: listOfNotNull(guidanceNode, resolveSegmentEndCoordinate(nextSegmentIndex))
                .takeIf { polyline -> polyline.size >= 2 }
            ?: return progress.distanceAlongPolylineMeters >=
                guidanceNodeProjection.distanceAlongPolylineMeters + NAVIGATION_NODE_TRANSITION_PASSED_DISTANCE_METERS
    val hasPassedGuidanceNodeOnRoute =
        progress.distanceAlongPolylineMeters >= guidanceNodeProjection.distanceAlongPolylineMeters
    if (!hasPassedGuidanceNodeOnRoute) {
        if (isSegmentHoldGuidance) return false
        val currentSegmentProjection =
            projectOntoPolylineMeters(current = currentCoordinate, polyline = transitionSegmentPolyline) ?: return false
        if (currentSegmentProjection.distanceToPolylineMeters > NAVIGATION_NODE_TRANSITION_TURN_MAX_SIDE_DISTANCE_METERS) {
            return false
        }
        val previousSegmentProjection =
            previousCoordinate
                ?.let { coordinate -> projectOntoPolylineMeters(current = coordinate, polyline = transitionSegmentPolyline) }
                ?: return false
        return isMovingAlongTransitionSegment(
            previousCoordinate = previousCoordinate,
            currentCoordinate = currentCoordinate,
            transitionPolyline = transitionSegmentPolyline,
            previousSegmentProjection = previousSegmentProjection,
            currentSegmentProjection = currentSegmentProjection,
        )
    }
    if (
        isSegmentHoldGuidance &&
        progress.distanceAlongPolylineMeters <
        guidanceNodeProjection.distanceAlongPolylineMeters + NAVIGATION_NODE_TRANSITION_PASSED_DISTANCE_METERS
    ) {
        return false
    }

    val currentProgressCoordinate = progress.coordinate
    val distanceToGuidanceNodeMeters =
        minOf(
            haversineDistanceMeters(currentCoordinate, guidanceNode),
            haversineDistanceMeters(currentProgressCoordinate, guidanceNode),
        )
    if (
        distanceToGuidanceNodeMeters > NAVIGATION_NODE_TRANSITION_RADIUS_METERS &&
        progress.distanceAlongPolylineMeters <
        guidanceNodeProjection.distanceAlongPolylineMeters + NAVIGATION_NODE_TRANSITION_PASSED_DISTANCE_METERS
    ) {
        return false
    }

    val currentSegmentProjection =
        projectOntoPolylineMeters(current = currentProgressCoordinate, polyline = transitionSegmentPolyline) ?: return false
    val transitionEnterDistanceMeters =
        if (isSegmentHoldGuidance) {
            NAVIGATION_NODE_TRANSITION_ENTER_DISTANCE_METERS
        } else {
            NAVIGATION_NODE_TRANSITION_TURN_MAX_SIDE_DISTANCE_METERS
        }
    if (currentSegmentProjection.distanceToPolylineMeters > transitionEnterDistanceMeters) {
        return false
    }
    val requiredAdvanceMeters = resolveNodeTransitionAdvanceMeters(transitionSegmentPolyline)
    if (currentSegmentProjection.distanceAlongPolylineMeters >= requiredAdvanceMeters) return true

    if (!isSegmentHoldGuidance && previousCoordinate != null) {
        val previousSegmentProjection =
            projectOntoPolylineMeters(current = previousCoordinate, polyline = transitionSegmentPolyline)
        if (
            previousSegmentProjection != null &&
            isMovingAlongTransitionSegment(
                previousCoordinate = previousCoordinate,
                currentCoordinate = currentCoordinate,
                transitionPolyline = transitionSegmentPolyline,
                previousSegmentProjection = previousSegmentProjection,
                currentSegmentProjection = currentSegmentProjection,
            )
        ) {
            return true
        }
    }

    val previousSegmentProjection =
        previousCoordinate
            ?.let { coordinate -> projectOntoPolylineMeters(current = coordinate, polyline = transitionSegmentPolyline) }
            ?: return false
    return currentSegmentProjection.distanceAlongPolylineMeters - previousSegmentProjection.distanceAlongPolylineMeters >=
        requiredAdvanceMeters
}

private fun resolveNodeTransitionAdvanceMeters(segmentPolyline: List<GeoCoordinate>): Double {
    val segmentDistanceMeters = segmentPolyline.totalPolylineDistanceMeters()
    val scaledAdvanceMeters = segmentDistanceMeters * NAVIGATION_NODE_TRANSITION_SEGMENT_ADVANCE_RATIO
    return minOf(NAVIGATION_NODE_TRANSITION_DEFAULT_ADVANCE_METERS, scaledAdvanceMeters)
        .coerceAtLeast(NAVIGATION_NODE_TRANSITION_MIN_ADVANCE_METERS)
}

private fun isMovingAlongTransitionSegment(
    previousCoordinate: GeoCoordinate,
    currentCoordinate: GeoCoordinate,
    transitionPolyline: List<GeoCoordinate>,
    previousSegmentProjection: RoutePolylineProjection,
    currentSegmentProjection: RoutePolylineProjection,
): Boolean {
    val movedMeters = haversineDistanceMeters(previousCoordinate, currentCoordinate)
    if (movedMeters < NAVIGATION_NODE_TRANSITION_COURSE_MIN_MOVE_METERS) return false

    val progressDeltaMeters =
        currentSegmentProjection.distanceAlongPolylineMeters - previousSegmentProjection.distanceAlongPolylineMeters
    if (progressDeltaMeters < NAVIGATION_NODE_TRANSITION_MIN_ADVANCE_METERS) return false

    val moveBearingDegrees = bearingDegreesBetween(previousCoordinate, currentCoordinate)
    val segmentBearingDegrees =
        transitionPolyline.bearingAtDistanceMeters(currentSegmentProjection.distanceAlongPolylineMeters) ?: return false
    return angularDifferenceDegrees(moveBearingDegrees, segmentBearingDegrees) <=
        NAVIGATION_NODE_TRANSITION_COURSE_MAX_ANGLE_DEGREES
}

private fun RouteCandidate.resolveProgressCoordinate(rawCurrent: GeoCoordinate): GeoCoordinate {
    val routePoints = navigationPolylinePoints()
    val projection = projectOntoPolylineMeters(current = rawCurrent, polyline = routePoints) ?: return rawCurrent
    return if (projection.distanceToPolylineMeters <= NAVIGATION_ROUTE_PROGRESS_SNAP_DISTANCE_METERS) {
        projection.projectedCoordinate
    } else {
        rawCurrent
    }
}

private fun RouteCandidate.resolveActiveLegIndex(progressRatio: Double): Int =
    resolveActiveIndex(
        progressRatio = progressRatio,
        weights =
            legs.map { leg ->
                leg.polyline.totalDistanceWeight()
                    ?: leg.distanceMeters?.toDouble()?.takeIf { distanceMeters -> distanceMeters > 0 }
                    ?: 1.0
            },
        advanceAtBoundary = false,
    )

private fun resolveActiveIndex(
    progressRatio: Double,
    weights: List<Double>,
    advanceAtBoundary: Boolean,
): Int {
    if (weights.isEmpty()) return 0

    val sanitizedWeights =
        weights.map { weight ->
            if (weight > 0.0) {
                weight
            } else {
                1.0
            }
        }
    val totalWeight = sanitizedWeights.sum().takeIf { total -> total > 0.0 } ?: sanitizedWeights.size.toDouble()
    var cumulativeRatio = 0.0
    sanitizedWeights.forEachIndexed { index, weight ->
        cumulativeRatio += weight / totalWeight
        if (advanceAtBoundary) {
            if (progressRatio < cumulativeRatio || index == sanitizedWeights.lastIndex) return index
        } else {
            if (progressRatio <= cumulativeRatio || index == sanitizedWeights.lastIndex) return index
        }
    }
    return sanitizedWeights.lastIndex
}

internal fun RouteCandidate.navigationPolylinePoints(): List<GeoCoordinate> {
    if (previewPolyline.isRenderable) return previewPolyline.points
    if (geometry.isRenderable) return geometry.points

    return buildList {
        segments.forEach { segment ->
            val segmentPoints = segment.polyline.points
            if (segmentPoints.isEmpty()) return@forEach
            if (isEmpty()) {
                addAll(segmentPoints)
            } else if (last() == segmentPoints.first()) {
                addAll(segmentPoints.drop(1))
            } else {
                addAll(segmentPoints)
            }
        }
    }
}

private fun RouteCandidate.finalRouteEndpoint(): GeoCoordinate? =
    navigationPolylinePoints().lastOrNull()
        ?: segments.lastOrNull()?.polyline?.points?.lastOrNull()
        ?: segments.lastOrNull()?.anchorCoordinate

private fun RouteCandidate.resolveSegmentTravelKind(
    segment: RouteSegment?,
    hasTransitLeg: Boolean,
): NavigationSegmentTravelKind {
    val legType =
        legs.firstOrNull { leg ->
            leg.sequence == segment?.sourceLegSequence
        }?.type

    return when (legType) {
        RouteLegType.BUS,
        RouteLegType.SUBWAY,
            -> NavigationSegmentTravelKind.TRANSIT

        else ->
            if (hasTransitLeg) {
                NavigationSegmentTravelKind.TRANSIT_WALK
            } else {
                NavigationSegmentTravelKind.WALK
            }
    }
}

private fun RouteCandidate.hasTransitLeg(): Boolean =
    legs.any { leg -> leg.type == RouteLegType.BUS || leg.type == RouteLegType.SUBWAY }

private fun RoutePolyline.totalDistanceWeight(): Double? =
    points.totalPolylineDistanceMeters().takeIf { distanceMeters -> distanceMeters > 0.0 }

internal fun List<GeoCoordinate>.totalPolylineDistanceMeters(): Double =
    if (size < 2) {
        0.0
    } else {
        zipWithNext().sumOf { (start, end) -> haversineDistanceMeters(start, end) }
    }

internal fun projectOntoPolylineMeters(
    current: GeoCoordinate,
    polyline: List<GeoCoordinate>,
): RoutePolylineProjection? {
    val totalPolylineDistanceMeters = polyline.totalPolylineDistanceMeters()
    if (polyline.isEmpty()) return null
    if (polyline.size == 1) {
        return RoutePolylineProjection(
            distanceToPolylineMeters = haversineDistanceMeters(current, polyline.single()),
            distanceAlongPolylineMeters = 0.0,
            totalPolylineDistanceMeters = 0.0,
            projectedCoordinate = polyline.single(),
        )
    }

    var cumulativeDistanceMeters = 0.0
    var bestDistanceToPolylineMeters = Double.POSITIVE_INFINITY
    var bestDistanceAlongPolylineMeters = 0.0
    var bestProjectedCoordinate = polyline.first()
    polyline.zipWithNext().forEach { (start, end) ->
        val segmentLengthMeters = haversineDistanceMeters(start, end)
        val projection =
            projectOntoSegmentMeters(
                point = current,
                start = start,
                end = end,
                segmentLengthMeters = segmentLengthMeters,
            )
        val projectedDistanceAlongPolyline = cumulativeDistanceMeters + projection.distanceAlongSegmentMeters
        val isCloser = projection.distanceToSegmentMeters < bestDistanceToPolylineMeters
        val isTieButFurtherAlong =
            !isCloser &&
                kotlin.math.abs(projection.distanceToSegmentMeters - bestDistanceToPolylineMeters) < 0.01 &&
                projectedDistanceAlongPolyline >= bestDistanceAlongPolylineMeters
        if (isCloser || isTieButFurtherAlong) {
            bestDistanceToPolylineMeters = projection.distanceToSegmentMeters
            bestDistanceAlongPolylineMeters = projectedDistanceAlongPolyline
            bestProjectedCoordinate = projection.projectedCoordinate
        }
        cumulativeDistanceMeters += segmentLengthMeters
    }

    return RoutePolylineProjection(
        distanceToPolylineMeters = bestDistanceToPolylineMeters,
        distanceAlongPolylineMeters = bestDistanceAlongPolylineMeters.coerceIn(0.0, totalPolylineDistanceMeters),
        totalPolylineDistanceMeters = totalPolylineDistanceMeters,
        projectedCoordinate = bestProjectedCoordinate,
    )
}

private fun projectOntoSegmentMeters(
    point: GeoCoordinate,
    start: GeoCoordinate,
    end: GeoCoordinate,
    segmentLengthMeters: Double,
): RouteSegmentProjection {
    if (segmentLengthMeters <= 0.0) {
        return RouteSegmentProjection(
            distanceToSegmentMeters = haversineDistanceMeters(point, start),
            distanceAlongSegmentMeters = 0.0,
            projectedCoordinate = start,
        )
    }

    val referenceLatitudeRadians = Math.toRadians((start.latitude + end.latitude + point.latitude) / 3.0)
    fun GeoCoordinate.toLocalPoint(origin: GeoCoordinate): Pair<Double, Double> {
        val deltaLongitudeRadians = Math.toRadians(longitude - origin.longitude)
        val deltaLatitudeRadians = Math.toRadians(latitude - origin.latitude)
        val x = deltaLongitudeRadians * EARTH_RADIUS_METERS * cos(referenceLatitudeRadians)
        val y = deltaLatitudeRadians * EARTH_RADIUS_METERS
        return x to y
    }

    val (segmentEndX, segmentEndY) = end.toLocalPoint(start)
    val (pointX, pointY) = point.toLocalPoint(start)
    val segmentMagnitudeSquared = segmentEndX * segmentEndX + segmentEndY * segmentEndY
    val projectionRatio =
        if (segmentMagnitudeSquared <= 0.0) {
            0.0
        } else {
            ((pointX * segmentEndX + pointY * segmentEndY) / segmentMagnitudeSquared).coerceIn(0.0, 1.0)
        }
    val projectedX = segmentEndX * projectionRatio
    val projectedY = segmentEndY * projectionRatio
    val deltaX = pointX - projectedX
    val deltaY = pointY - projectedY

    return RouteSegmentProjection(
        distanceToSegmentMeters = sqrt(deltaX * deltaX + deltaY * deltaY),
        distanceAlongSegmentMeters = segmentLengthMeters * projectionRatio,
        projectedCoordinate = start.interpolateTo(end, projectionRatio),
    )
}

internal fun calculateRemainingDistanceMeters(
    current: GeoCoordinate,
    polyline: List<GeoCoordinate>,
): Double {
    return RemainingDistanceCalculator(polyline).calculateRemainingDistanceMeters(current)
}

internal class RemainingDistanceCalculator(
    private val polyline: List<GeoCoordinate>,
) {
    private val cumulativeDistanceMeters: List<Double> =
        buildList {
            var total = 0.0
            add(total)
            for (index in 0 until polyline.lastIndex) {
                total += haversineDistanceMeters(polyline[index], polyline[index + 1])
                add(total)
            }
        }

    val isEmpty: Boolean
        get() = polyline.isEmpty()

    fun calculateRemainingDistanceMeters(current: GeoCoordinate): Double {
        if (polyline.isEmpty()) return 0.0

        val nearestIndex =
            polyline.indices.minByOrNull { index ->
                haversineDistanceMeters(current, polyline[index])
            } ?: 0

        val distanceToNearestPoint = haversineDistanceMeters(current, polyline[nearestIndex])
        val routeTailDistance = cumulativeDistanceMeters.last() - cumulativeDistanceMeters[nearestIndex]
        return distanceToNearestPoint + routeTailDistance
    }
}

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val MIN_LOCATION_UPDATE_INTERVAL_MILLIS = 1_000L
private const val TRANSIT_REFRESH_COOLDOWN_MILLIS = 60_000L
private const val TRANSIT_BOARDING_REFRESH_DISTANCE_METERS = 300.0
private const val BUS_STOP_REFRESH_DISTANCE_METERS = TRANSIT_BOARDING_REFRESH_DISTANCE_METERS
private const val SUBWAY_ELEVATOR_REFRESH_DISTANCE_METERS = TRANSIT_BOARDING_REFRESH_DISTANCE_METERS
private const val REROUTE_DEVIATION_DISTANCE_METERS = 40.0
private const val REROUTE_MAX_GPS_ACCURACY_METERS = 25f
private const val REROUTE_OFF_ROUTE_CONSECUTIVE_COUNT = 2
private const val REROUTE_OFF_ROUTE_DURATION_MILLIS = 3_000L
private const val PROJECTED_PROGRESS_MAX_DISTANCE_METERS = 75.0
private const val FALLBACK_NAVIGATION_SPEED_METERS_PER_SECOND = 1.4
private const val LOW_VISION_FALLBACK_WALKING_SPEED_METERS_PER_SECOND = 1.0
private const val LOW_VISION_WALKABLE_DISTANCE_THRESHOLD_METERS = 750
private const val MIN_NAVIGATION_DURATION_SECONDS = 60
private const val LOW_VISION_ACTUAL_METRICS_BUCKET_SCALE = 10_000.0
private const val LOW_VISION_ROUTE_CHANGE_ALERT_DISTANCE_METERS = 50
private const val IMMEDIATE_GUIDANCE_CAMERA_DISTANCE_THRESHOLD_METERS = 500
private const val PENDING_ACTIVE_SEGMENT_LABEL = "Current segment updated"

private fun RouteNavigationRequest.withTransitAlightingGuidanceSegments(): RouteNavigationRequest {
    val normalizedRoute = selectedRoute.withTransitAlightingGuidanceSegments()
    return if (normalizedRoute == selectedRoute) {
        this
    } else {
        copy(selectedRoute = normalizedRoute)
    }
}

private fun RouteCandidate.withTransitAlightingGuidanceSegments(): RouteCandidate {
    val transitLegsMissingAlightingGuidance =
        legs
            .filter { leg -> leg.type == RouteLegType.BUS || leg.type == RouteLegType.SUBWAY }
            .filter { leg -> leg.alightingStop != null }
            .filterNot { leg ->
                segments.any { segment ->
                    segment.sourceLegSequence == leg.sequence &&
                        segment.guidanceType == RouteGuidanceType.ARRIVING_POINT
                }
            }
    if (transitLegsMissingAlightingGuidance.isEmpty()) return this

    val missingLegsBySequence = transitLegsMissingAlightingGuidance.associateBy(RouteLeg::sequence)
    val sortedSegments = segments.sortedBy(RouteSegment::sequence)
    var nextSequence = 1
    val normalizedSegments =
        buildList {
            sortedSegments.forEachIndexed { index, segment ->
                add(segment.copy(sequence = nextSequence++))
                val sourceLegSequence = segment.sourceLegSequence ?: return@forEachIndexed
                val leg = missingLegsBySequence[sourceLegSequence] ?: return@forEachIndexed
                val hasLaterSegmentForLeg =
                    sortedSegments.drop(index + 1).any { candidate ->
                        candidate.sourceLegSequence == sourceLegSequence
                    }
                if (!hasLaterSegmentForLeg) {
                    add(leg.toNavigationAlightingSegment(sequence = nextSequence++))
                }
            }
            missingLegsBySequence.values
                .filterNot { leg -> sortedSegments.any { segment -> segment.sourceLegSequence == leg.sequence } }
                .sortedBy(RouteLeg::sequence)
                .forEach { leg ->
                    add(leg.toNavigationAlightingSegment(sequence = nextSequence++))
                }
        }
    return copy(segments = normalizedSegments)
}

private fun RouteLeg.toNavigationAlightingSegment(sequence: Int): RouteSegment {
    val stop = requireNotNull(alightingStop)
    return RouteSegment(
        sequence = sequence,
        polyline = RoutePolyline(),
        anchorCoordinate = stop.coordinate,
        distanceMeters = 0,
        safetyFlags = RouteSegmentSafetyFlags(),
        riskLevel = RouteRiskLevel.LOW,
        guidanceMessage = "${stop.name} \uD558\uCC28\uC9C0\uC810\uC785\uB2C8\uB2E4.",
        sourceLegSequence = this.sequence,
        guidanceType = RouteGuidanceType.ARRIVING_POINT,
    )
}

internal fun haversineDistanceMeters(a: GeoCoordinate, b: GeoCoordinate): Double {
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val sinHalfLat = sin(dLat / 2)
    val sinHalfLon = sin(dLon / 2)
    val h =
        sinHalfLat.pow(2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sinHalfLon.pow(2)
    return 2 * EARTH_RADIUS_METERS * atan2(sqrt(h), sqrt(1 - h))
}

private fun RouteNavigationRequest.toScreenState(): NavigationScreenState =
    if (selectedRoute.segments.isEmpty()) {
        NavigationScreenState.Empty
    } else {
        NavigationScreenState.Ready
    }

private fun RouteNavigationRequest.toMapPlaceholderDescription(screenState: NavigationScreenState): String {
    val destinationName = destination.name.orEmpty().ifBlank { "목적지" }
    return when (screenState) {
        NavigationScreenState.Loading -> "현재 위치와 경로 안내를 준비하고 있습니다."
        NavigationScreenState.Ready -> "$destinationName 방향 경로 안내를 시작합니다."
        NavigationScreenState.Empty -> "$destinationName 방향 요약 정보만 먼저 표시합니다."
    }
}

private fun RouteNavigationRequest.toMapOverlayUiState(
    currentLocationCoordinate: GeoCoordinate?,
    activeSegmentIndex: Int,
    focusedSegmentIndex: Int,
    mapFocusMode: NavigationMapFocusMode,
    trackingMode: NavigationTrackingMode,
    headingDegrees: Double?,
): NavigationMapOverlayUiState {
    val selectedRoutePolyline = selectedRoute.previewPolyline.points
    val activeSegment =
        if (activeSegmentIndex == NavigationOriginSegmentIndex) {
            null
        } else {
            selectedRoute.segments.getOrNull(activeSegmentIndex)
        }
    val focusedSegment =
        if (focusedSegmentIndex == NavigationOriginSegmentIndex) {
            null
        } else {
            selectedRoute.segments.getOrNull(focusedSegmentIndex)
        }
    val activeSegmentPolyline =
        if (activeSegmentIndex == NavigationOriginSegmentIndex) {
            emptyList()
        } else {
            selectedRoute.resolveSegmentDisplayPolyline(activeSegmentIndex)
        }
    val focusedSegmentPolyline =
        if (focusedSegmentIndex == NavigationOriginSegmentIndex) {
            emptyList()
        } else {
            selectedRoute.resolveSegmentDisplayPolyline(focusedSegmentIndex)
        }
    val hasTransitLeg = selectedRoute.hasTransitLeg()
    val activeFocusCoordinate =
        if (activeSegmentIndex == NavigationOriginSegmentIndex) {
            currentLocationCoordinate ?: origin.coordinate
        } else {
            currentLocationCoordinate
                ?: selectedRoute.resolveSegmentStartCoordinate(activeSegmentIndex)
                ?: selectedRoute.resolveSegmentFocusCoordinate(activeSegmentIndex)
                ?: origin.coordinate
        }
    val inspectedFocusCoordinate =
        if (focusedSegmentIndex == NavigationOriginSegmentIndex) {
            origin.coordinate
        } else {
            selectedRoute.resolveSegmentRepresentativeCoordinate(focusedSegmentIndex)
                ?: activeFocusCoordinate
        }
    val segmentRouteSegments =
        selectedRoute.segments.mapIndexed { index, segment ->
            NavigationMapSegmentUiState(
                sequence = segment.sequence,
                polyline = selectedRoute.resolveSegmentDisplayPolyline(index),
                segmentStartCoordinate = selectedRoute.resolveSegmentStartCoordinate(index),
                segmentEndCoordinate = selectedRoute.resolveSegmentEndCoordinate(index),
                distanceMeters = segment.distanceMeters,
                riskLevel = segment.riskLevel,
                guidanceMessage = segment.guidanceMessage,
                travelKind = selectedRoute.resolveSegmentTravelKind(segment, hasTransitLeg),
                isActive = index == activeSegmentIndex,
                isFocused = index == focusedSegmentIndex,
                isCompleted = index < activeSegmentIndex,
                isRiskUpcoming = index > activeSegmentIndex && segment.riskLevel != RouteRiskLevel.LOW,
            )
        }
    val routeSegments = segmentRouteSegments + selectedRoute.toFallbackWalkingLegMapSegments(segmentRouteSegments, hasTransitLeg)
    val originPoint = origin.toNavigationMapPointUiState(fallbackLabel = "출발지")
    val destinationPoint = destination.toNavigationMapPointUiState(fallbackLabel = "목적지")

    return NavigationMapOverlayUiState(
        isDisplayable = selectedRoute.previewPolyline.isRenderable || routeSegments.any(NavigationMapSegmentUiState::isRenderable),
        currentLocation =
            currentLocationCoordinate?.let { coordinate ->
                NavigationMapPointUiState(
                    label = "현재 위치",
                    coordinate = coordinate,
                )
            },
        origin = originPoint,
        destination = destinationPoint,
        selectedRoutePolyline = selectedRoutePolyline,
        activeSegmentPolyline = activeSegmentPolyline,
        focusedSegmentPolyline = focusedSegmentPolyline,
        activeSegmentTravelKind = selectedRoute.resolveSegmentTravelKind(activeSegment, hasTransitLeg),
        focusedSegmentTravelKind = selectedRoute.resolveSegmentTravelKind(focusedSegment, hasTransitLeg),
        focusCoordinate =
            when (mapFocusMode) {
                NavigationMapFocusMode.ACTIVE -> activeFocusCoordinate
                NavigationMapFocusMode.FOCUSED -> inspectedFocusCoordinate
            },
        routeSegments = routeSegments,
        mapFocusMode = mapFocusMode,
        trackingMode = trackingMode,
        headingDegrees = headingDegrees,
        shouldAnimateCameraTransition = true,
    )
}

private data class NavigationPose(
    val rawLocation: GeoCoordinate,
    val displayLocation: GeoCoordinate,
    val heading: NavigationHeadingSelection,
    val recordedAtEpochMillis: Long,
)

private fun NavigationPose.withHeading(heading: NavigationHeadingSelection): NavigationPose =
    copy(heading = heading)

private data class NavigationHeadingSelection(
    val degrees: Double?,
    val source: NavigationHeadingSource?,
)

private enum class NavigationHeadingSource {
    GPS_BEARING,
    DEVICE_SENSOR,
    ROUTE_FALLBACK,
}

private fun resolveNavigationHeading(
    gpsBearingDegrees: Double?,
    sensorHeadingDegrees: Double?,
    routeFallbackDegrees: Double?,
): NavigationHeadingSelection =
    when {
        sensorHeadingDegrees != null ->
            NavigationHeadingSelection(
                degrees = normalizeHeadingDegrees(sensorHeadingDegrees),
                source = NavigationHeadingSource.DEVICE_SENSOR,
            )
        gpsBearingDegrees != null ->
            NavigationHeadingSelection(
                degrees = normalizeHeadingDegrees(gpsBearingDegrees),
                source = NavigationHeadingSource.GPS_BEARING,
            )
        routeFallbackDegrees != null ->
            NavigationHeadingSelection(
                degrees = normalizeHeadingDegrees(routeFallbackDegrees),
                source = NavigationHeadingSource.ROUTE_FALLBACK,
            )
        else -> NavigationHeadingSelection(degrees = null, source = null)
    }

private fun LocationSnapshot.toUsableNavigationBearingDegrees(): Double? {
    val bearing = bearingDegrees ?: return null
    val speed = speedMetersPerSecond
    if (speed != null && speed < NAVIGATION_GPS_BEARING_MIN_SPEED_METERS_PER_SECOND) return null
    return normalizeHeadingDegrees(bearing.toDouble())
}

private fun LocationSnapshot.isImpossibleNavigationJumpFrom(
    previousCoordinate: GeoCoordinate,
    elapsedMillis: Long,
    route: RouteCandidate,
    activeSegmentIndex: Int,
): Boolean {
    if (elapsedMillis <= 0L) return true
    val currentCoordinate = GeoCoordinate(latitude = latitude, longitude = longitude)
    val jumpDistanceMeters = haversineDistanceMeters(previousCoordinate, currentCoordinate)
    if (jumpDistanceMeters < NAVIGATION_IMPOSSIBLE_JUMP_MIN_DISTANCE_METERS) return false
    val currentRouteDistanceMeters =
        projectOntoPolylineMeters(
            current = currentCoordinate,
            polyline = route.navigationPolylinePoints(),
        )?.distanceToPolylineMeters ?: Double.POSITIVE_INFINITY
    if (currentRouteDistanceMeters <= NAVIGATION_ROUTE_PROGRESS_SNAP_DISTANCE_METERS) return false
    val elapsedSeconds = elapsedMillis / 1_000.0
    val observedSpeedMetersPerSecond = jumpDistanceMeters / elapsedSeconds
    val maxAcceptedSpeed =
        if (route.isTransitNavigationContext(activeSegmentIndex)) {
            NAVIGATION_TRANSIT_MAX_ACCEPTED_SPEED_METERS_PER_SECOND
        } else {
            NAVIGATION_WALKING_MAX_ACCEPTED_SPEED_METERS_PER_SECOND
        }
    return observedSpeedMetersPerSecond > maxAcceptedSpeed
}

private fun LocationSnapshot.resolveSmoothedDisplayCoordinate(
    rawCoordinate: GeoCoordinate,
    previousPose: NavigationPose?,
): GeoCoordinate {
    val previousDisplayCoordinate = previousPose?.displayLocation ?: return rawCoordinate
    val distanceMeters = haversineDistanceMeters(previousDisplayCoordinate, rawCoordinate)
    if (distanceMeters <= 0.0) return rawCoordinate
    val shouldFreezeJitter =
        speedMetersPerSecond != null &&
            speedMetersPerSecond <= NAVIGATION_JITTER_FREEZE_SPEED_METERS_PER_SECOND &&
            distanceMeters <= NAVIGATION_JITTER_FREEZE_DISTANCE_METERS
    if (shouldFreezeJitter) return previousDisplayCoordinate
    val alpha =
        when {
            distanceMeters <= NAVIGATION_SMOOTHING_NEAR_DISTANCE_METERS -> NAVIGATION_SMOOTHING_NEAR_ALPHA
            distanceMeters <= NAVIGATION_SMOOTHING_MID_DISTANCE_METERS -> NAVIGATION_SMOOTHING_MID_ALPHA
            else -> 1.0
        }
    return previousDisplayCoordinate.interpolateTo(rawCoordinate, alpha)
}

private fun RouteCandidate.resolveNavigationRouteFallbackBearingDegrees(current: GeoCoordinate): Double? {
    val routePoints = navigationPolylinePoints()
    if (routePoints.size < 2) return null
    val projection = projectOntoPolylineMeters(current = current, polyline = routePoints) ?: return null
    val lookaheadDistance =
        (projection.distanceAlongPolylineMeters + NAVIGATION_FOLLOW_LOOKAHEAD_METERS)
            .coerceAtMost(projection.totalPolylineDistanceMeters)
    val lookaheadCoordinate = routePoints.coordinateAtDistanceMeters(lookaheadDistance) ?: return null
    return bearingDegreesBetween(projection.projectedCoordinate, lookaheadCoordinate)
}

private fun RouteCandidate.isTransitNavigationContext(activeSegmentIndex: Int): Boolean {
    val activeSegment = segments.getOrNull(activeSegmentIndex)
    return resolveSegmentTravelKind(segment = activeSegment, hasTransitLeg = hasTransitLeg()) == NavigationSegmentTravelKind.TRANSIT
}

private fun NavigationTrackingMode.nextOnCurrentLocationClick(): NavigationTrackingMode =
    when (this) {
        NavigationTrackingMode.IDLE -> NavigationTrackingMode.FOLLOW
        NavigationTrackingMode.FOLLOW -> NavigationTrackingMode.FOLLOW
        NavigationTrackingMode.FOLLOW_WITH_HEADING -> NavigationTrackingMode.FOLLOW
    }

private fun RouteCandidate.toFallbackWalkingLegMapSegments(
    existingSegments: List<NavigationMapSegmentUiState>,
    hasTransitLeg: Boolean,
): List<NavigationMapSegmentUiState> {
    val renderableWalkingLegs =
        legs.filter { leg ->
            leg.type == RouteLegType.WALK && leg.polyline.isRenderable
        }
    if (renderableWalkingLegs.isEmpty()) return emptyList()

    val renderableWalkingSegmentPolylines =
        existingSegments
            .filter { segment ->
                (segment.travelKind == NavigationSegmentTravelKind.WALK ||
                    segment.travelKind == NavigationSegmentTravelKind.TRANSIT_WALK) &&
                    segment.isRenderable
            }
            .map { segment -> segment.polyline }
    val hasRenderableWalkingSegment = renderableWalkingSegmentPolylines.isNotEmpty()
    val fallbackWalkingLegs =
        if (hasTransitLeg) {
            renderableWalkingLegs.filterNot { leg ->
                val legPolyline = leg.polyline.points
                val hasRenderableScopedSegment =
                    segments.any { segment ->
                        segment.sourceLegSequence == leg.sequence && segment.polyline.isRenderable
                    }
                hasRenderableScopedSegment ||
                    renderableWalkingSegmentPolylines.any { polyline -> polyline == legPolyline }
            }
        } else if (hasRenderableWalkingSegment) {
            emptyList()
        } else {
            renderableWalkingLegs
        }

    return fallbackWalkingLegs
        .map { leg ->
            NavigationMapSegmentUiState(
                sequence = leg.sequence,
                polyline = leg.polyline.points,
                segmentStartCoordinate = leg.polyline.points.firstOrNull(),
                segmentEndCoordinate = leg.polyline.points.lastOrNull(),
                distanceMeters = leg.distanceMeters ?: 0,
                riskLevel = RouteRiskLevel.LOW,
                guidanceMessage = leg.instruction,
                travelKind =
                    if (hasTransitLeg) {
                        NavigationSegmentTravelKind.TRANSIT_WALK
                    } else {
                        NavigationSegmentTravelKind.WALK
                    },
                showJunctionMarker = true,
            )
        }
}

internal fun createNavigationSegmentMarkerDebugSummary(
    mapOverlay: NavigationMapOverlayUiState,
): String {
    val activeIndex = mapOverlay.routeSegments.indexOfFirst(NavigationMapSegmentUiState::isActive)
    val focusedIndex = mapOverlay.routeSegments.indexOfFirst(NavigationMapSegmentUiState::isFocused)
    return buildString {
        append("focusMode=")
        append(mapOverlay.mapFocusMode.name)
        append(" count=")
        append(mapOverlay.routeSegments.size)
        append(" active=")
        append(activeIndex)
        append(" focused=")
        append(focusedIndex)
        append(" routePolyline=")
        append(mapOverlay.selectedRoutePolyline.size)
        append(" activePolyline=")
        append(mapOverlay.activeSegmentPolyline.size)
        append(" focusedPolyline=")
        append(mapOverlay.focusedSegmentPolyline.size)
        append(" details=[")
        append(
            mapOverlay.routeSegments.mapIndexed { index, segment ->
                buildString {
                    append("idx=")
                    append(index)
                    append(" seq=")
                    append(segment.sequence)
                    append(" kind=")
                    append(segment.travelKind.name)
                    append(" polyline=")
                    append(segment.polyline.size)
                    append(" first=")
                    append(segment.polyline.firstOrNull().toDebugCoordinate())
                    append(" start=")
                    append(segment.segmentStartCoordinate.toDebugCoordinate())
                    append(" active=")
                    append(segment.isActive)
                    append(" focused=")
                    append(segment.isFocused)
                }
            }.joinToString(separator = "; "),
        )
        append("]")
    }
}

private fun RouteNavigationRequest.toSegmentSyncUiState(
    activeSegmentIndex: Int,
    focusedSegmentIndex: Int,
    isInspectingSegments: Boolean,
    hasPendingActiveChange: Boolean,
    mapFocusMode: NavigationMapFocusMode,
    transitPresentation: NavigationTransitPresentation?,
): NavigationSegmentSyncUiState {
    val originRailItem =
        NavigationSegmentRailItemUiState(
            index = NavigationOriginSegmentIndex,
            sequence = 0,
            instruction = NavigationOriginHeroTitle,
            distanceLabel = "",
            riskLabel = "",
            guidanceAction = NavigationGuidanceAction.START,
            isFocused = focusedSegmentIndex == NavigationOriginSegmentIndex,
        )

    return NavigationSegmentSyncUiState(
        activeSegmentIndex = activeSegmentIndex,
        focusedSegmentIndex = focusedSegmentIndex,
        isInspectingSegments = isInspectingSegments,
        mapFocusMode = mapFocusMode,
        hasPendingActiveChange = hasPendingActiveChange,
        railItems =
            listOf(originRailItem) +
                selectedRoute.segments.mapIndexed { index, segment ->
                val sidePanelDetail = selectedRoute.toNavigationSidePanelStepDetail(segment)
                NavigationSegmentRailItemUiState(
                    index = index,
                    sequence = segment.sequence,
                    instruction = segment.guidanceMessage,
                    distanceLabel = segment.distanceMeters.toNavigationDistanceLabel(),
                    riskLabel = segment.riskLevel.toRiskLabel(),
                    guidanceAction = selectedRoute.toNavigationGuidanceAction(segment),
                    isActive = index == activeSegmentIndex,
                    isFocused = index == focusedSegmentIndex,
                    isCompleted = index < activeSegmentIndex,
                    isRiskUpcoming = index > activeSegmentIndex && segment.riskLevel != RouteRiskLevel.LOW,
                    transitInfo = selectedRoute.resolveFocusedSegmentTransitInfo(segment, transitPresentation),
                    sidePanelTitle = sidePanelDetail.title,
                    sidePanelDescription = sidePanelDetail.description,
                )
            },
    )
}

private data class NavigationSidePanelStepDetail(
    val title: String,
    val description: String?,
)

private fun RouteCandidate.toNavigationSidePanelStepDetail(segment: RouteSegment): NavigationSidePanelStepDetail {
    val kind = toRouteDetailStepKind(segment)
    val sourceLeg = segment.resolveSourceLeg(legs = legs)
    val routeDurationSeconds = summary.durationSeconds ?: summary.estimatedTimeMinutes * 60
    return NavigationSidePanelStepDetail(
        title = segment.navigationSidePanelStepTitle(kind = kind),
        description =
            segment.navigationSidePanelStepDescription(
                kind = kind,
                sourceLeg = sourceLeg,
                routeDurationSeconds = routeDurationSeconds,
            ),
    )
}

private fun RouteSegment.navigationSidePanelStepTitle(kind: RouteDetailStepKind): String =
    when (kind) {
        RouteDetailStepKind.START -> NavigationOriginHeroTitle
        RouteDetailStepKind.ALIGHT -> "\uD558\uCC28"
        RouteDetailStepKind.BUS -> "\uBC84\uC2A4 \uD0D1\uC2B9"
        RouteDetailStepKind.SUBWAY -> "\uC9C0\uD558\uCCA0 \uD0D1\uC2B9"
        RouteDetailStepKind.STRAIGHT -> "${guidanceDisplayDistanceMeters()}m \uC9C1\uC9C4 \uC774\uB3D9"
        RouteDetailStepKind.TURN_LEFT -> "${guidanceDisplayDistanceMeters()}m \uD6C4 \uC88C\uD68C\uC804"
        RouteDetailStepKind.TURN_RIGHT -> "${guidanceDisplayDistanceMeters()}m \uD6C4 \uC6B0\uD68C\uC804"
        RouteDetailStepKind.TACTILE_GUIDE -> "\uC810\uC790\uBE14\uB85D \uB530\uB77C \uC774\uB3D9"
        RouteDetailStepKind.CROSSWALK -> crosswalkSidePanelTitle()
        RouteDetailStepKind.ELEVATOR -> "\uC5D8\uB9AC\uBCA0\uC774\uD130 \uC774\uC6A9"
        RouteDetailStepKind.CONSTRUCTION -> "\uACF5\uC0AC \uAD6C\uAC04 \uC9C4\uC785"
        RouteDetailStepKind.CURB_GAP -> "\uB2E8\uCC28 \uAD6C\uAC04 \uC8FC\uC758"
        RouteDetailStepKind.STAIRS -> "\uACC4\uB2E8 \uAD6C\uAC04 \uC8FC\uC758"
        RouteDetailStepKind.ARRIVAL -> "\uBAA9\uC801\uC9C0 \uB3C4\uCC29"
        RouteDetailStepKind.FALLBACK -> "\uC774\uB3D9 \uACBD\uB85C \uD655\uC778"
    }

private fun RouteSegment.navigationSidePanelStepDescription(
    kind: RouteDetailStepKind,
    sourceLeg: RouteLeg?,
    routeDurationSeconds: Int?,
): String? =
    when (kind) {
        RouteDetailStepKind.ALIGHT ->
            guidanceMessage.takeIf(String::hasVisibleHangul)
                ?: sourceLeg?.alightingStop?.name?.takeIf(String::isNotBlank)?.let { stopName ->
                    "${stopName} \uD558\uCC28\uC9C0\uC810\uC785\uB2C8\uB2E4."
                }
                ?: "\uD558\uCC28\uC9C0\uC810\uC785\uB2C8\uB2E4."

        RouteDetailStepKind.BUS ->
            sourceLeg?.routeNo?.takeIf(String::isNotBlank)?.let { routeNo ->
                "${routeNo}\uBC88 \uBC84\uC2A4\uC5D0 \uD0D1\uC2B9\uD558\uC138\uC694."
            } ?: "\uBC84\uC2A4\uC5D0 \uD0D1\uC2B9\uD558\uC138\uC694."

        RouteDetailStepKind.SUBWAY ->
            sourceLeg?.routeNo?.takeIf(String::isNotBlank)?.let { routeNo ->
                "${routeNo} \uC9C0\uD558\uCCA0\uC5D0 \uD0D1\uC2B9\uD558\uC138\uC694."
            } ?: "\uC9C0\uD558\uCCA0\uC5D0 \uD0D1\uC2B9\uD558\uC138\uC694."

        RouteDetailStepKind.START -> NavigationOriginHeroDescription
        RouteDetailStepKind.ARRIVAL -> "\uC548\uB0B4\uAC00 \uC644\uB8CC\uB418\uC5C8\uC2B5\uB2C8\uB2E4"
        else -> "\uBAA9\uC801\uC9C0\uAE4C\uC9C0 \uC57D ${remainingMinutesToDestination(routeDurationSeconds)}\uBD84"
    }

private fun RouteSegment.guidanceDisplayDistanceMeters(): Int =
    (guidanceDistanceMeters ?: distanceMeters).coerceAtLeast(0)

private fun RouteSegment.remainingMinutesToDestination(routeDurationSeconds: Int?): Int {
    val totalSeconds = routeDurationSeconds ?: return 0
    val elapsedSeconds = durationFromRouteStartSeconds ?: 0
    return ((totalSeconds - elapsedSeconds).coerceAtLeast(0) + 59) / 60
}

private fun RouteSegment.crosswalkSidePanelTitle(): String =
    when {
        RouteGuidanceFeature.AUDIO_SIGNAL in guidanceFeatures || safetyFlags.hasAudioSignal ->
            "\uC74C\uD5A5 \uC2E0\uD638 \uD6A1\uB2E8\uBCF4\uB3C4 \uAC74\uB108\uAE30"
        RouteGuidanceFeature.SIGNAL in guidanceFeatures || safetyFlags.hasSignal ->
            "\uC2E0\uD638 \uD6A1\uB2E8\uBCF4\uB3C4 \uAC74\uB108\uAE30"
        else -> "\uD6A1\uB2E8\uBCF4\uB3C4 \uAC74\uB108\uAE30"
    }

private fun String.hasVisibleHangul(): Boolean = any { character -> character in '\uAC00'..'\uD7A3' }

private fun RouteNavigationRequest.toFocusedSegmentCardUiState(
    focusedSegmentIndex: Int,
    estimatedMinutes: Int?,
    transitPresentation: NavigationTransitPresentation?,
): NavigationFocusedSegmentCardUiState? {
    val totalStepCount = selectedRoute.segments.size.coerceAtLeast(1)
    if (focusedSegmentIndex == NavigationOriginSegmentIndex) {
        val remainingTimeLabel = estimatedMinutes.toDestinationRemainingTimeLabel()
        return NavigationFocusedSegmentCardUiState(
            sequenceLabel = "1 / $totalStepCount",
            instruction = NavigationOriginHeroTitle,
            heroTitle = NavigationOriginHeroTitle,
            heroDescription = remainingTimeLabel,
            distanceLabel = remainingTimeLabel,
            riskLabel = "",
            supportingText = selectedRoute.title.toNavigationRouteTitle(selectedRoute.routeOption),
            guidanceAction = NavigationGuidanceAction.START,
        )
    }

    val focusedSegment = selectedRoute.segments.getOrNull(focusedSegmentIndex) ?: return null
    val heroDetail = selectedRoute.toNavigationHeroDetail(focusedSegment)
    val remainingTimeLabel =
        selectedRoute
            .remainingMinutesFromSegmentIndex(
                segmentIndex = focusedSegmentIndex,
                fallbackEstimatedMinutes = estimatedMinutes,
            ).toDestinationRemainingTimeLabel()

    return NavigationFocusedSegmentCardUiState(
        sequenceLabel = "${focusedSegment.sequence} / $totalStepCount",
        instruction = focusedSegment.guidanceMessage,
        heroTitle = heroDetail.title,
        heroDescription = remainingTimeLabel,
        distanceLabel = remainingTimeLabel,
        riskLabel = focusedSegment.riskLevel.toRiskLabel(),
        supportingText = selectedRoute.title.toNavigationRouteTitle(selectedRoute.routeOption),
        guidanceAction = heroDetail.guidanceAction,
        transitInfo = selectedRoute.resolveFocusedSegmentTransitInfo(focusedSegment, transitPresentation),
    )
}

private fun RouteCandidate.remainingMinutesFromSegmentIndex(
    segmentIndex: Int,
    fallbackEstimatedMinutes: Int?,
): Int? {
    if (segments.isEmpty()) return fallbackEstimatedMinutes
    val boundedIndex = segmentIndex.coerceIn(0, segments.lastIndex)
    val totalSeconds =
        totalDurationSeconds().takeIf { seconds -> seconds > 0 }
            ?: fallbackEstimatedMinutes?.takeIf { minutes -> minutes >= 0 }?.times(60)
            ?: return fallbackEstimatedMinutes
    val elapsedSeconds = segments.buildEffectiveElapsedSecondsByIndex(totalSeconds)[boundedIndex]
    if (elapsedSeconds != null) {
        return (totalSeconds - elapsedSeconds)
            .coerceAtLeast(0)
            .toEtaMinutes()
    }

    val totalSegmentDistance = segments.sumOf { segment -> segment.distanceMeters.coerceAtLeast(0) }
    val routeDistance = totalSegmentDistance.takeIf { distance -> distance > 0 } ?: totalDistanceMeters()
    if (routeDistance <= 0) return fallbackEstimatedMinutes

    val elapsedDistance =
        segments
            .take(boundedIndex)
            .sumOf { segment -> segment.distanceMeters.coerceAtLeast(0) }
            .coerceAtMost(routeDistance)
    val remainingRatio =
        ((routeDistance - elapsedDistance).toDouble() / routeDistance.toDouble())
            .coerceIn(0.0, 1.0)

    return (totalSeconds * remainingRatio)
        .roundToInt()
        .coerceAtLeast(0)
        .toEtaMinutes()
}

private fun List<RouteSegment>.buildEffectiveElapsedSecondsByIndex(totalDurationSeconds: Int): Map<Int, Int> {
    if (totalDurationSeconds <= 0) return emptyMap()
    val totalDistance = sumOf { segment -> segment.distanceMeters.coerceAtLeast(0) }
    var cumulativeDistance = 0
    var previousServerElapsedSeconds: Int? = null

    return mapIndexed { index, segment ->
        val fallbackElapsedSeconds =
            when {
                totalDistance > 0 ->
                    ((totalDurationSeconds.toLong() * cumulativeDistance.toLong()) / totalDistance.toLong()).toInt()
                size > 1 ->
                    ((totalDurationSeconds.toLong() * index.toLong()) / size.toLong()).toInt()
                else -> 0
            }
        val serverElapsedSeconds =
            segment.durationFromRouteStartSeconds
                ?.takeIf { seconds -> seconds in 0..totalDurationSeconds }
        val previousServerElapsed = previousServerElapsedSeconds
        val isServerElapsedProgressing =
            serverElapsedSeconds != null &&
                (
                    previousServerElapsed == null ||
                        serverElapsedSeconds > previousServerElapsed ||
                        segment.distanceMeters <= 0
                )
        val effectiveElapsedSeconds =
            if (isServerElapsedProgressing && serverElapsedSeconds != null) {
                serverElapsedSeconds
            } else {
                fallbackElapsedSeconds
            }
        if (isServerElapsedProgressing) {
            previousServerElapsedSeconds = serverElapsedSeconds
        }
        cumulativeDistance += segment.distanceMeters.coerceAtLeast(0)
        index to effectiveElapsedSeconds
    }.toMap()
}

private fun RouteCandidate.resolveFocusedSegmentTransitInfo(
    focusedSegment: RouteSegment,
    transitPresentation: NavigationTransitPresentation?,
): NavigationTransitInfoUiState? {
    if (focusedSegment.guidanceType == RouteGuidanceType.ARRIVING_POINT) return null
    val sourceLeg = focusedSegment.resolveSourceLeg(legs = legs) ?: return null
    if (sourceLeg.type != RouteLegType.BUS && sourceLeg.type != RouteLegType.SUBWAY) return null

    val activeTransitInfo = transitPresentation?.info
    if (
        activeTransitInfo != null &&
        activeTransitInfo.guidanceAction ==
        when (sourceLeg.type) {
            RouteLegType.BUS -> NavigationGuidanceAction.BUS
            RouteLegType.SUBWAY -> NavigationGuidanceAction.SUBWAY
            RouteLegType.WALK -> NavigationGuidanceAction.STRAIGHT
        }
    ) {
        return activeTransitInfo
    }

    return sourceLeg.toNavigationTransitInfo(
        arrivalRouteNo = null,
        arrivalMinutes = sourceLeg.laneOptions.firstOrNull()?.remainingMinute,
    )
}

private fun RouteWaypoint.toNavigationMapPointUiState(fallbackLabel: String): NavigationMapPointUiState =
    NavigationMapPointUiState(
        label = name.orEmpty().ifBlank { fallbackLabel },
        coordinate = coordinate,
    )

private fun RouteCandidate.resolveSegmentStartCoordinate(segmentIndex: Int): GeoCoordinate? {
    val segment = segments.getOrNull(segmentIndex) ?: return null
    val sourceLeg = segment.resolveSourceLeg(legs = legs)

    segment.polyline
        .takeIf(RoutePolyline::isRenderable)
        ?.points
        ?.firstOrNull()
        ?.let { return it }
    segment.anchorCoordinate?.let { return it }
    segment.resolveSourceLegStartCoordinate(
        segmentIndex = segmentIndex,
        segments = segments,
        sourceLeg = sourceLeg,
    )?.let { return it }
    resolveSparseSegmentBoundaryCoordinate(segmentIndex)?.let { return it }

    val fallbackPolyline = navigationPolylinePoints()
    if (fallbackPolyline.isNotEmpty()) {
        val progressRatio = resolveSegmentStartProgressRatio(segmentIndex = segmentIndex, weights = segmentWeights())
        fallbackPolyline.coordinateAtProgressRatio(progressRatio)?.let { return it }
    }

    return sourceLeg?.polyline?.points?.firstOrNull()
}

private fun RouteCandidate.resolveSegmentFocusCoordinate(segmentIndex: Int): GeoCoordinate? {
    val segment = segments.getOrNull(segmentIndex) ?: return null
    val sourceLeg = segment.resolveSourceLeg(legs = legs)

    segment.polyline
        .takeIf(RoutePolyline::isRenderable)
        ?.points
        ?.toNavigationFocusCoordinate()
        ?.let { return it }
    segment.anchorCoordinate?.let { return it }
    segment.resolveSourceLegFocusCoordinate(
        segmentIndex = segmentIndex,
        segments = segments,
        sourceLeg = sourceLeg,
    )?.let { return it }
    resolveSparseSegmentBoundaryCoordinate(segmentIndex)?.let { return it }

    val fallbackPolyline = navigationPolylinePoints()
    if (fallbackPolyline.isEmpty()) return null

    val progressRatio = resolveSegmentMidProgressRatio(segmentIndex = segmentIndex, weights = segmentWeights())
    return fallbackPolyline.coordinateAtProgressRatio(progressRatio)
}

private fun RouteCandidate.resolveSegmentRepresentativeCoordinate(segmentIndex: Int): GeoCoordinate? {
    val segment = segments.getOrNull(segmentIndex)
    segment
        ?.polyline
        ?.takeIf(RoutePolyline::isRenderable)
        ?.points
        ?.toNavigationFocusCoordinate()
        ?.let { return it }

    return resolveSegmentStartCoordinate(segmentIndex)
        ?: resolveSegmentFocusCoordinate(segmentIndex)
}

private fun RouteCandidate.resolveSegmentEndCoordinate(segmentIndex: Int): GeoCoordinate? {
    val segment = segments.getOrNull(segmentIndex) ?: return null
    val sourceLeg = segment.resolveSourceLeg(legs = legs)

    if (segment.guidanceType == RouteGuidanceType.ARRIVING_POINT) {
        return segment.anchorCoordinate ?: sourceLeg?.alightingStop?.coordinate
    }
    if (sourceLeg?.type == RouteLegType.BUS || sourceLeg?.type == RouteLegType.SUBWAY) {
        sourceLeg.alightingStop?.coordinate?.let { return it }
    }
    segment.polyline
        .takeIf(RoutePolyline::isRenderable)
        ?.points
        ?.lastOrNull()
        ?.let { return it }
    segment.anchorCoordinate?.let { return it }
    if (sourceLeg?.type == RouteLegType.WALK && segment.isFirstSegmentOfSourceLeg(segmentIndex = segmentIndex, segments = segments)) {
        sourceLeg.polyline.points.lastOrNull()?.let { return it }
    }
    return null
}

private fun RouteCandidate.resolveSegmentDisplayPolyline(segmentIndex: Int): List<GeoCoordinate> {
    val segment = segments.getOrNull(segmentIndex) ?: return emptyList()
    if (segment.guidanceType == RouteGuidanceType.ARRIVING_POINT) return emptyList()

    val sourceLeg = segment.resolveSourceLeg(legs = legs)
    if (
        sourceLeg != null &&
        (sourceLeg.type == RouteLegType.BUS || sourceLeg.type == RouteLegType.SUBWAY) &&
        sourceLeg.polyline.isRenderable
    ) {
        return sourceLeg.polyline.points.withResolvedEndCoordinate(sourceLeg.alightingStop?.coordinate)
    }

    return segment.polyline.points
}

private fun RouteCandidate.resolveSegmentRouteSpanMeters(
    segmentIndex: Int,
    routePoints: List<GeoCoordinate>,
): RouteSegmentDistanceSpan? {
    val segmentPolyline = resolveSegmentDisplayPolyline(segmentIndex).takeIf { it.size >= 2 }
    val startCoordinate =
        segmentPolyline?.firstOrNull()
            ?: resolveSegmentStartCoordinate(segmentIndex)
            ?: return null
    val endCoordinate =
        segmentPolyline?.lastOrNull()
            ?: resolveSegmentEndCoordinate(segmentIndex)
            ?: return null
    val startProjection = projectOntoPolylineMeters(current = startCoordinate, polyline = routePoints) ?: return null
    val endProjection = projectOntoPolylineMeters(current = endCoordinate, polyline = routePoints) ?: return null
    val startDistance = minOf(startProjection.distanceAlongPolylineMeters, endProjection.distanceAlongPolylineMeters)
    val endDistance = maxOf(startProjection.distanceAlongPolylineMeters, endProjection.distanceAlongPolylineMeters)
    if (endDistance - startDistance <= 0.5) return null
    return RouteSegmentDistanceSpan(
        startDistanceMeters = startDistance,
        endDistanceMeters = endDistance,
    )
}

private fun RouteSegment.shouldHoldRealtimeGuidanceUntilSegmentEnd(): Boolean =
    when (guidanceType) {
        RouteGuidanceType.CROSSWALK,
        RouteGuidanceType.LOW_SLOPE,
        RouteGuidanceType.MIDDLE_SLOPE,
        RouteGuidanceType.STAIR,
        RouteGuidanceType.NARROW_SIDEWALK,
        RouteGuidanceType.UNPAVED,
        RouteGuidanceType.SUBWAY_ELEVATOR,
            -> true
        else -> false
    }

private fun List<GeoCoordinate>.withResolvedEndCoordinate(endCoordinate: GeoCoordinate?): List<GeoCoordinate> {
    if (endCoordinate == null || isEmpty() || last() == endCoordinate) return this
    return dropLast(1) + endCoordinate
}

private fun RouteCandidate.segmentWeights(): List<Double> =
    segments.map { candidateSegment ->
        candidateSegment.polyline.totalDistanceWeight()
            ?: candidateSegment.distanceMeters.toDouble().takeIf { distanceMeters -> distanceMeters > 0 }
            ?: 1.0
    }

private fun RouteCandidate.distanceToNextSegmentBoundaryMeters(progress: NavigationProgressSnapshot): Int? {
    if (progress.activeSegmentIndex >= segments.lastIndex) return null
    val sanitizedWeights =
        segmentWeights().map { weight ->
            if (weight > 0.0) {
                weight
            } else {
                1.0
            }
        }
    if (sanitizedWeights.isEmpty()) return null

    val totalWeight = sanitizedWeights.sum().takeIf { weight -> weight > 0.0 } ?: return null
    val boundaryRatio =
        sanitizedWeights
            .take(progress.activeSegmentIndex + 1)
            .sum()
            .div(totalWeight)
            .coerceIn(0.0, 1.0)
    val boundaryDistanceMeters = totalDistanceMeters() * boundaryRatio
    return (boundaryDistanceMeters - progress.distanceAlongRouteMeters)
        .roundToInt()
        .coerceAtLeast(0)
}

private fun RouteCandidate.distanceToLiveGuidanceMeters(progress: NavigationProgressSnapshot): Int? {
    val routePoints = navigationPolylinePoints()
    if (routePoints.size < 2) return null
    val currentProjection = projectOntoPolylineMeters(current = progress.coordinate, polyline = routePoints) ?: return null
    val targetProjection =
        resolveLiveGuidanceTargetProjection(
            startSegmentIndex = progress.activeSegmentIndex,
            currentProjection = currentProjection,
            routePoints = routePoints,
        ) ?: return null
    return (targetProjection.distanceAlongPolylineMeters - currentProjection.distanceAlongPolylineMeters)
        .roundToInt()
        .coerceAtLeast(0)
}

private fun RouteCandidate.resolveLiveGuidanceTargetProjection(
    startSegmentIndex: Int,
    currentProjection: RoutePolylineProjection,
    routePoints: List<GeoCoordinate>,
): RoutePolylineProjection? {
    val startIndex = startSegmentIndex.coerceIn(0, segments.lastIndex.coerceAtLeast(0))
    return (startIndex..segments.lastIndex)
        .asSequence()
        .mapNotNull { segmentIndex ->
            val coordinate = resolveRealtimeStepTargetCoordinate(segmentIndex) ?: return@mapNotNull null
            projectOntoPolylineMeters(current = coordinate, polyline = routePoints)
        }
        .firstOrNull { targetProjection ->
            targetProjection.distanceAlongPolylineMeters >
                currentProjection.distanceAlongPolylineMeters + NAVIGATION_LIVE_GUIDANCE_TARGET_AHEAD_TOLERANCE_METERS
        }
        ?: resolveRealtimeStepTargetCoordinate(startSegmentIndex)
            ?.let { coordinate -> projectOntoPolylineMeters(current = coordinate, polyline = routePoints) }
}

private fun RouteCandidate.hasRenderableRealtimeProgress(progress: NavigationProgressSnapshot): Boolean {
    if (progress.activeSegmentIndex !in segments.indices) return false
    val routePoints = navigationPolylinePoints()
    if (routePoints.size < 2) return false
    if (projectOntoPolylineMeters(current = progress.coordinate, polyline = routePoints) == null) return false
    return true
}

private fun RouteCandidate.shouldWaitForInitialRouteStartJoin(
    origin: GeoCoordinate?,
    current: GeoCoordinate,
): Boolean {
    val routeStart = navigationPolylinePoints().firstOrNull() ?: return false
    if (origin == null) return false
    val hasDetachedOrigin =
        haversineDistanceMeters(origin, routeStart) > NAVIGATION_ROUTE_START_JOIN_RADIUS_METERS
    if (!hasDetachedOrigin) return false
    return haversineDistanceMeters(current, routeStart) > NAVIGATION_ROUTE_START_JOIN_RADIUS_METERS
}

private fun RouteCandidate.isNearFinalRouteProgress(progress: NavigationProgressSnapshot): Boolean =
    progress.activeSegmentIndex >= segments.lastIndex.coerceAtLeast(0)

private fun RouteCandidate.resolveLiveGuidanceTargetCoordinate(segmentIndex: Int): GeoCoordinate? {
    resolveRealtimeStepTargetCoordinate(segmentIndex)?.let { return it }
    val segment = segments.getOrNull(segmentIndex) ?: return null
    return when (resolveSegmentTravelKind(segment = segment, hasTransitLeg = hasTransitLeg())) {
        NavigationSegmentTravelKind.TRANSIT -> resolveSegmentEndCoordinate(segmentIndex)
        NavigationSegmentTravelKind.WALK,
        NavigationSegmentTravelKind.TRANSIT_WALK,
            -> resolveSegmentStartCoordinate(segmentIndex)
    }
}

private fun RouteCandidate.resolveRealtimeStepTargetCoordinate(segmentIndex: Int): GeoCoordinate? {
    val segment = segments.getOrNull(segmentIndex) ?: return null
    return when (resolveSegmentTravelKind(segment = segment, hasTransitLeg = hasTransitLeg())) {
        NavigationSegmentTravelKind.TRANSIT ->
            segment.anchorCoordinate
                ?: segment.polyline.points.lastOrNull()
                ?: resolveSparseSegmentBoundaryCoordinate(segmentIndex + 1)
                ?: resolveSegmentEndCoordinate(segmentIndex)
                ?: segment.polyline.points.lastOrNull()
                ?: segment.anchorCoordinate
        NavigationSegmentTravelKind.WALK,
        NavigationSegmentTravelKind.TRANSIT_WALK,
            ->
            segment.anchorCoordinate
                ?: segment.polyline.points.firstOrNull()
                ?: resolveSparseSegmentBoundaryCoordinate(segmentIndex)
                ?: resolveSegmentStartCoordinate(segmentIndex)
                ?: segment.anchorCoordinate
    }
}

private fun RouteSegment.resolveSourceLeg(legs: List<RouteLeg>): RouteLeg? =
    sourceLegSequence?.let { sourceLegSequence ->
        legs.firstOrNull { leg -> leg.sequence == sourceLegSequence }
    }

private fun RouteSegment.isFirstSegmentOfSourceLeg(
    segmentIndex: Int,
    segments: List<RouteSegment>,
): Boolean {
    val sourceLegSequence = sourceLegSequence ?: return false
    return segments.indexOfFirst { candidateSegment -> candidateSegment.sourceLegSequence == sourceLegSequence } == segmentIndex
}

private fun RouteSegment.resolveSourceLegStartCoordinate(
    segmentIndex: Int,
    segments: List<RouteSegment>,
    sourceLeg: RouteLeg?,
): GeoCoordinate? {
    val resolvedSourceLeg = sourceLeg ?: return null
    return if (
        resolvedSourceLeg.type != RouteLegType.WALK ||
        isFirstSegmentOfSourceLeg(segmentIndex = segmentIndex, segments = segments)
    ) {
        resolvedSourceLeg.polyline.points.firstOrNull()
    } else {
        null
    }
}

private fun RouteCandidate.resolveSparseSegmentBoundaryCoordinate(segmentIndex: Int): GeoCoordinate? {
    val fallbackPolyline = navigationPolylinePoints()
    if (fallbackPolyline.size < segments.size + 1) return null
    return fallbackPolyline.getOrNull(segmentIndex)
}

private fun RouteSegment.resolveSourceLegFocusCoordinate(
    segmentIndex: Int,
    segments: List<RouteSegment>,
    sourceLeg: RouteLeg?,
): GeoCoordinate? {
    val resolvedSourceLeg = sourceLeg ?: return null
    return if (
        resolvedSourceLeg.type != RouteLegType.WALK ||
        isFirstSegmentOfSourceLeg(segmentIndex = segmentIndex, segments = segments)
    ) {
        resolvedSourceLeg.toNavigationFocusCoordinate()
    } else {
        null
    }
}

private fun RouteLeg.toNavigationFocusCoordinate(): GeoCoordinate? =
    polyline.points.toNavigationFocusCoordinate()
        ?: listOfNotNull(boardingStop?.coordinate, alightingStop?.coordinate).toNavigationFocusCoordinate()

private fun List<GeoCoordinate>.toNavigationFocusCoordinate(): GeoCoordinate? {
    if (isEmpty()) return null
    if (size == 1) return single()
    return coordinateAtProgressRatio(progressRatio = 0.5)
}

private fun resolveSegmentMidProgressRatio(
    segmentIndex: Int,
    weights: List<Double>,
): Double {
    if (weights.isEmpty()) return 0.5

    val sanitizedWeights =
        weights.map { weight ->
            if (weight > 0.0) {
                weight
            } else {
                1.0
            }
        }
    val safeSegmentIndex = segmentIndex.coerceIn(0, sanitizedWeights.lastIndex)
    val totalWeight = sanitizedWeights.sum().takeIf { total -> total > 0.0 } ?: sanitizedWeights.size.toDouble()
    val accumulatedWeightBefore = sanitizedWeights.take(safeSegmentIndex).sum()
    val targetWeight = sanitizedWeights[safeSegmentIndex]
    return ((accumulatedWeightBefore + (targetWeight / 2.0)) / totalWeight).coerceIn(0.0, 1.0)
}

private fun resolveSegmentStartProgressRatio(
    segmentIndex: Int,
    weights: List<Double>,
): Double {
    if (weights.isEmpty()) return 0.0

    val sanitizedWeights =
        weights.map { weight ->
            if (weight > 0.0) {
                weight
            } else {
                1.0
            }
        }
    val safeSegmentIndex = segmentIndex.coerceIn(0, sanitizedWeights.lastIndex)
    val totalWeight = sanitizedWeights.sum().takeIf { total -> total > 0.0 } ?: sanitizedWeights.size.toDouble()
    val accumulatedWeightBefore = sanitizedWeights.take(safeSegmentIndex).sum()
    return (accumulatedWeightBefore / totalWeight).coerceIn(0.0, 1.0)
}

private fun List<GeoCoordinate>.coordinateAtProgressRatio(progressRatio: Double): GeoCoordinate? {
    if (isEmpty()) return null
    if (size == 1) return single()

    val clampedRatio = progressRatio.coerceIn(0.0, 1.0)
    val totalDistanceMeters = totalPolylineDistanceMeters()
    if (totalDistanceMeters <= 0.0) {
        return first().interpolateTo(last(), 0.5)
    }

    val targetDistanceMeters = totalDistanceMeters * clampedRatio
    var cumulativeDistanceMeters = 0.0
    zipWithNext().forEach { (start, end) ->
        val segmentDistanceMeters = haversineDistanceMeters(start, end)
        val nextCumulativeDistanceMeters = cumulativeDistanceMeters + segmentDistanceMeters
        if (segmentDistanceMeters <= 0.0) {
            cumulativeDistanceMeters = nextCumulativeDistanceMeters
            return@forEach
        }
        if (targetDistanceMeters <= nextCumulativeDistanceMeters) {
            val segmentRatio = ((targetDistanceMeters - cumulativeDistanceMeters) / segmentDistanceMeters).coerceIn(0.0, 1.0)
            return start.interpolateTo(end, segmentRatio)
        }
        cumulativeDistanceMeters = nextCumulativeDistanceMeters
    }

    return last()
}

private fun GeoCoordinate.interpolateTo(
    other: GeoCoordinate,
    progressRatio: Double,
): GeoCoordinate =
    GeoCoordinate(
        latitude = latitude + ((other.latitude - latitude) * progressRatio),
        longitude = longitude + ((other.longitude - longitude) * progressRatio),
    )

private fun List<GeoCoordinate>.coordinateAtDistanceMeters(distanceMeters: Double): GeoCoordinate? {
    if (isEmpty()) return null
    if (size == 1 || distanceMeters <= 0.0) return first()
    var cumulativeDistanceMeters = 0.0
    zipWithNext().forEach { (start, end) ->
        val segmentDistanceMeters = haversineDistanceMeters(start, end)
        val nextCumulativeDistanceMeters = cumulativeDistanceMeters + segmentDistanceMeters
        if (distanceMeters <= nextCumulativeDistanceMeters || end == last()) {
            val segmentRatio =
                if (segmentDistanceMeters <= 0.0) {
                    0.0
                } else {
                    ((distanceMeters - cumulativeDistanceMeters) / segmentDistanceMeters).coerceIn(0.0, 1.0)
                }
            return start.interpolateTo(end, segmentRatio)
        }
        cumulativeDistanceMeters = nextCumulativeDistanceMeters
    }
    return last()
}

private fun bearingDegreesBetween(
    start: GeoCoordinate,
    end: GeoCoordinate,
): Double {
    val startLatitudeRadians = Math.toRadians(start.latitude)
    val endLatitudeRadians = Math.toRadians(end.latitude)
    val deltaLongitudeRadians = Math.toRadians(end.longitude - start.longitude)
    val y = sin(deltaLongitudeRadians) * cos(endLatitudeRadians)
    val x =
        cos(startLatitudeRadians) * sin(endLatitudeRadians) -
            sin(startLatitudeRadians) * cos(endLatitudeRadians) * cos(deltaLongitudeRadians)
    return normalizeHeadingDegrees(Math.toDegrees(atan2(y, x)))
}

private fun angularDifferenceDegrees(
    firstDegrees: Double,
    secondDegrees: Double,
): Double {
    val delta = kotlin.math.abs(normalizeHeadingDegrees(firstDegrees) - normalizeHeadingDegrees(secondDegrees))
    return minOf(delta, 360.0 - delta)
}

private fun List<GeoCoordinate>.bearingAtDistanceMeters(distanceAlongPolylineMeters: Double): Double? {
    if (size < 2) return null
    var cumulativeDistanceMeters = 0.0
    zipWithNext().forEach { (start, end) ->
        val segmentLengthMeters = haversineDistanceMeters(start, end)
        val nextCumulativeDistanceMeters = cumulativeDistanceMeters + segmentLengthMeters
        if (segmentLengthMeters > 0.0 && distanceAlongPolylineMeters <= nextCumulativeDistanceMeters) {
            return bearingDegreesBetween(start, end)
        }
        cumulativeDistanceMeters = nextCumulativeDistanceMeters
    }
    return zipWithNext()
        .lastOrNull { (start, end) -> haversineDistanceMeters(start, end) > 0.0 }
        ?.let { (start, end) -> bearingDegreesBetween(start, end) }
}

private fun RouteNavigationRequest.toStepCardUiState(
    screenState: NavigationScreenState,
    activeSegmentIndex: Int,
    remainingDistanceMeters: Int?,
    estimatedMinutes: Int?,
    remainingMetricsSource: NavigationRemainingMetricsSource,
    transitPresentation: NavigationTransitPresentation?,
    liveGuidanceRawDistanceMeters: Int?,
    liveGuidanceDisplayDistanceMeters: Int?,
    destinationDistanceMeters: Int?,
    destinationSoon: Boolean,
    hasPassedFinalRouteEndpoint: Boolean,
): NavigationStepCardUiState =
    when (screenState) {
        NavigationScreenState.Loading -> NavigationStepCardUiState()
        NavigationScreenState.Ready ->
            toReadyStepCardUiState(
                activeSegmentIndex = activeSegmentIndex,
                remainingDistanceMeters = remainingDistanceMeters,
                estimatedMinutes = estimatedMinutes,
                remainingMetricsSource = remainingMetricsSource,
                transitPresentation = transitPresentation,
                liveGuidanceRawDistanceMeters = liveGuidanceRawDistanceMeters,
                liveGuidanceDisplayDistanceMeters = liveGuidanceDisplayDistanceMeters,
                destinationDistanceMeters = destinationDistanceMeters,
                destinationSoon = destinationSoon,
                hasPassedFinalRouteEndpoint = hasPassedFinalRouteEndpoint,
            )
        NavigationScreenState.Empty -> toEmptyStepCardUiState()
    }

private fun NavigationStepCardUiState.toNavigationBriefingText(): String =
    speechText
        .trim()
        .takeIf(String::isNotEmpty)
        ?: listOf(
            heroTitle.trim(),
            heroDescription.trim(),
        ).filter(String::isNotEmpty)
            .distinct()
            .joinToString(separator = " ")

private fun NavigationFocusedSegmentCardUiState.toSpeechText(): String? =
    listOf(
        heroTitle.trim(),
        heroDescription.trim(),
    ).filter(String::isNotEmpty)
        .distinct()
        .joinToString(separator = " ")
        .trim()
        .takeIf(String::isNotEmpty)

private fun NavigationTtsUiState.toFallbackMessage(): String =
    when {
        !isEnabled -> NAVIGATION_TTS_DISABLED_MESSAGE
        status == NavigationTtsStatus.Unavailable -> NAVIGATION_TTS_UNAVAILABLE_MESSAGE
        status == NavigationTtsStatus.Initializing -> NAVIGATION_TTS_PREPARING_MESSAGE
        else -> ""
    }

private fun RouteNavigationRequest.toReadyStepCardUiState(
    activeSegmentIndex: Int,
    remainingDistanceMeters: Int?,
    estimatedMinutes: Int?,
    remainingMetricsSource: NavigationRemainingMetricsSource,
    transitPresentation: NavigationTransitPresentation?,
    liveGuidanceRawDistanceMeters: Int?,
    liveGuidanceDisplayDistanceMeters: Int?,
    destinationDistanceMeters: Int?,
    destinationSoon: Boolean,
    hasPassedFinalRouteEndpoint: Boolean,
): NavigationStepCardUiState {
    val totalStepCount = selectedRoute.segments.size.coerceAtLeast(1)
    val remainingTimeLabel = estimatedMinutes.toDestinationRemainingTimeLabel()
    val primarySegment =
        selectedRoute.segments.getOrNull(activeSegmentIndex)
            ?: selectedRoute.segments.firstOrNull { segment ->
                segment.guidanceMessage.isNotBlank()
            }
            ?: selectedRoute.segments.firstOrNull()
    val remainingMetricPresentation =
        navigationRemainingMetricPresentation(
            distanceMeters = remainingDistanceMeters ?: selectedRoute.summary.distanceMeters,
            estimatedMinutes = estimatedMinutes ?: selectedRoute.summary.estimatedTimeMinutes,
            source = remainingMetricsSource,
        )

    if (activeSegmentIndex == NavigationOriginSegmentIndex) {
        return NavigationStepCardUiState(
            sectionLabel = "현재 안내",
            statusLabel = selectedRoute.routeOption.toRouteOptionLabel(),
            emphasisLabel = selectedRoute.summary.riskLevel.toRiskLabel(),
            distanceLabel = remainingTimeLabel,
            heroTitle = NavigationOriginHeroTitle,
            heroDescription = NAVIGATION_ROUTE_START_GUIDANCE_TEXT,
            instruction = NAVIGATION_ROUTE_START_GUIDANCE_TEXT,
            supportingText = selectedRoute.title.toNavigationRouteTitle(selectedRoute.routeOption),
            speechText =
                formatNavigationLiveGuidanceSpeechText(
                    action = NavigationGuidanceAction.START,
                    rawDistanceMeters = 0,
                    stage = NavigationLiveGuidanceSpeechStage.INITIAL,
                    fallbackTitle = NavigationOriginHeroTitle,
                ),
            guidanceAction = NavigationGuidanceAction.START,
            transitInfo = null,
            metrics =
                listOf(
                    NavigationStepMetricUiState(
                        label = "남은 거리",
                        value = remainingMetricPresentation.distanceLabel,
                    ),
                    NavigationStepMetricUiState(
                        label = "남은 시간",
                        value = remainingMetricPresentation.etaLabel,
                    ),
                    NavigationStepMetricUiState(
                        label = "진행 단계",
                        value = "1 / $totalStepCount",
                    ),
                ),
        )
    }

    val heroGuidanceAction =
        if (destinationSoon || hasPassedFinalRouteEndpoint) {
            NavigationGuidanceAction.ARRIVAL
        } else {
            primarySegment?.let { segment -> selectedRoute.toNavigationHeroDetail(segment).guidanceAction }
                ?: NavigationGuidanceAction.STRAIGHT
        }
    val fallbackHeroTitle =
        primarySegment?.let { segment -> selectedRoute.toNavigationHeroDetail(segment).title }
            ?: "경로 안내"
    val liveGuidanceRawDistance = liveGuidanceRawDistanceMeters ?: primarySegment?.guidanceDisplayDistanceMeters()
    val liveGuidanceDisplayDistance =
        liveGuidanceDisplayDistanceMeters
            ?: liveGuidanceRawDistance?.let(::toLiveGuidanceDisplayDistanceMeters)
    val arrivalRawDistance =
        destinationDistanceMeters
            ?: remainingDistanceMeters
            ?: liveGuidanceRawDistance
            ?: selectedRoute.summary.distanceMeters
    val heroTitle =
        when {
            destinationSoon ->
                formatNavigationLiveGuidanceCardTextFromRawDistance(
                    action = NavigationGuidanceAction.ARRIVAL,
                    rawDistanceMeters = arrivalRawDistance,
                    fallbackTitle = "목적지",
                )
            hasPassedFinalRouteEndpoint -> "목적지"
            else ->
                liveGuidanceDisplayDistance?.let { displayDistance ->
                    formatNavigationLiveGuidanceCardText(
                        action = heroGuidanceAction,
                        displayDistanceMeters = displayDistance,
                        fallbackTitle = fallbackHeroTitle,
                    )
                }
                    ?: "경로 안내를 준비하고 있습니다"
        }
    val speechText =
        when {
            destinationSoon -> NAVIGATION_DESTINATION_SOON_TTS_TEXT
            hasPassedFinalRouteEndpoint -> "목적지 방향으로 계속 이동하세요."
            else ->
                formatNavigationLiveGuidanceSpeechText(
                    action = heroGuidanceAction,
                    rawDistanceMeters = liveGuidanceRawDistance ?: 0,
                    stage = NavigationLiveGuidanceSpeechStage.INITIAL,
                    fallbackTitle = fallbackHeroTitle,
                )
        }

    return NavigationStepCardUiState(
        sectionLabel = "현재 안내",
        statusLabel = selectedRoute.routeOption.toRouteOptionLabel(),
        emphasisLabel = (primarySegment?.riskLevel ?: selectedRoute.summary.riskLevel).toRiskLabel(),
        distanceLabel = estimatedMinutes.toDestinationRemainingTimeLabel(),
        heroTitle = heroTitle,
        heroDescription =
            estimatedMinutes.toDestinationRemainingTimeLabel(),
        instruction =
            if (destinationSoon || hasPassedFinalRouteEndpoint) {
                "목적지 방향으로 계속 이동해 주세요."
            } else {
                primarySegment?.guidanceMessage
                    ?.trim()
                    ?.takeIf { guidanceMessage -> guidanceMessage.isNotEmpty() }
                    ?: "목적지 방향으로 계속 이동해 주세요."
            },
        supportingText =
            transitPresentation?.supportingText ?: "${destination.name.orEmpty().ifBlank { "목적지" }} 방향으로 " +
                "${selectedRoute.title.toNavigationRouteTitle(selectedRoute.routeOption)} 경로를 따라 이동합니다.",
        speechText = speechText,
        guidanceAction = heroGuidanceAction,
        transitInfo = primarySegment?.let { segment -> selectedRoute.resolveFocusedSegmentTransitInfo(segment, transitPresentation) },
        metrics =
            listOf(
                NavigationStepMetricUiState(
                    label = "남은 거리",
                    value = remainingMetricPresentation.distanceLabel,
                ),
                NavigationStepMetricUiState(
                    label = "남은 시간",
                    value = remainingMetricPresentation.etaLabel,
                ),
                NavigationStepMetricUiState(
                    label = "진행 단계",
                    value = "${activeSegmentIndex + 1} / $totalStepCount",
                ),
            ),
    )
}

private fun Int?.toDestinationRemainingTimeLabel(): String =
    this
        ?.takeIf { minutes -> minutes >= 0 }
        ?.let { minutes -> "목적지까지 약 ${minutes.coerceAtLeast(1)}분" }
        ?: "목적지까지 확인 중"

private fun RouteSegment.isRiskPrioritySegment(): Boolean {
    if (riskLevel != RouteRiskLevel.LOW) return true
    return when (toNavigationGuidanceAction()) {
        NavigationGuidanceAction.CROSSWALK,
        NavigationGuidanceAction.CURB_GAP,
        NavigationGuidanceAction.STAIRS,
        NavigationGuidanceAction.CONSTRUCTION,
        NavigationGuidanceAction.TACTILE_GUIDE,
        NavigationGuidanceAction.ELEVATOR,
        -> true
        else -> false
    }
}

private fun RouteNavigationRequest.toEmptyStepCardUiState(): NavigationStepCardUiState =
    NavigationStepCardUiState(
        sectionLabel = "다음 안내",
        statusLabel = selectedRoute.routeOption.toRouteOptionLabel(),
        emphasisLabel = selectedRoute.summary.riskLevel.toRiskLabel(),
        distanceLabel = selectedRoute.summary.distanceMeters.toNavigationDistanceLabel(),
        heroTitle = "경로 안내",
        heroDescription = "현재 안내 메시지를 준비하지 못했습니다.",
        instruction = "현재 안내 메시지를 준비하지 못했습니다.",
        supportingText =
            "${destination.name.orEmpty().ifBlank { "목적지" }} 방향으로 거리와 예상 시간 요약만 먼저 표시합니다.",
        guidanceAction =
            selectedRoute.segments.firstOrNull()?.let(selectedRoute::toNavigationGuidanceAction)
                ?: NavigationGuidanceAction.STRAIGHT,
        metrics =
            listOf(
                NavigationStepMetricUiState(
                    label = "남은 거리",
                    value = selectedRoute.summary.distanceMeters.toNavigationDistanceLabel(),
                ),
                NavigationStepMetricUiState(
                    label = "남은 시간",
                    value = selectedRoute.summary.estimatedTimeMinutes.toNavigationEtaLabel(),
                ),
                NavigationStepMetricUiState(
                    label = "진행 단계",
                    value = "안내 없음",
                ),
            ),
    )

private fun NavigationScreenState.toExitCtaUiState(): NavigationCtaUiState =
    when (this) {
        NavigationScreenState.Loading ->
            NavigationCtaUiState(
                label = "안내 준비 중",
                supportingText = "경로 정보가 준비되면 종료 버튼이 활성화됩니다.",
                isEnabled = false,
            )
        NavigationScreenState.Ready,
        NavigationScreenState.Empty,
            -> NavigationCtaUiState(
                label = "안내 종료",
                supportingText = "안내를 종료하고 지도로 돌아갑니다.",
                isEnabled = true,
            )
    }

private fun String.toNavigationRouteTitle(routeOption: RouteOption): String =
    trim().ifBlank { routeOption.toRouteOptionLabel() }

private fun RouteOption.toRouteOptionLabel(): String =
    when (this) {
        RouteOption.SAFE -> "안전한 길"
        RouteOption.SHORTEST -> "최단거리"
        RouteOption.RECOMMENDED -> "추천 경로"
        RouteOption.MIN_TRANSFER -> "최소 환승"
        RouteOption.MIN_WALK -> "최소 도보"
    }

private fun RouteRiskLevel.toRiskLabel(): String =
    when (this) {
        RouteRiskLevel.LOW -> "위험도 낮음"
        RouteRiskLevel.MEDIUM -> "위험도 보통"
        RouteRiskLevel.HIGH -> "위험도 높음"
    }

private fun Int.toNavigationDistanceLabel(): String =
    when {
        this <= 0 -> "확인 중"
        this < 1_000 -> "${this}m"
        else -> String.format(Locale.US, "%.1fkm", this / 1_000f)
    }

private fun Int.toNavigationEtaLabel(): String =
    if (this > 0) {
        "${this}분"
    } else {
        "확인 중"
    }

private data class NavigationRemainingMetricPresentation(
    val distanceLabel: String,
    val etaLabel: String,
)

private fun navigationRemainingMetricPresentation(
    distanceMeters: Int,
    estimatedMinutes: Int,
    source: NavigationRemainingMetricsSource,
): NavigationRemainingMetricPresentation {
    if (source == NavigationRemainingMetricsSource.Unavailable) {
        return NavigationRemainingMetricPresentation(
            distanceLabel = "-",
            etaLabel = "-",
        )
    }

    return NavigationRemainingMetricPresentation(
        distanceLabel = distanceMeters.toNavigationDistanceLabel(),
        etaLabel = estimatedMinutes.toNavigationEtaLabel(),
    )
}

private fun Int.toEtaMinutes(): Int =
    if (this <= 0) {
        0
    } else {
        ceil(this / 60.0).toInt()
    }

private data class LowVisionActualMetricsKey(
    val currentLatitudeBucket: Int,
    val currentLongitudeBucket: Int,
    val destinationLatitudeBucket: Int,
    val destinationLongitudeBucket: Int,
) {
    companion object {
        fun from(
            current: GeoCoordinate,
            destination: GeoCoordinate,
        ): LowVisionActualMetricsKey =
            LowVisionActualMetricsKey(
                currentLatitudeBucket = current.latitude.toLowVisionMetricsBucket(),
                currentLongitudeBucket = current.longitude.toLowVisionMetricsBucket(),
                destinationLatitudeBucket = destination.latitude.toLowVisionMetricsBucket(),
                destinationLongitudeBucket = destination.longitude.toLowVisionMetricsBucket(),
            )
    }
}

private fun LowVisionActualMetricsKey.hasSameDestinationAs(other: LowVisionActualMetricsKey): Boolean =
    destinationLatitudeBucket == other.destinationLatitudeBucket &&
        destinationLongitudeBucket == other.destinationLongitudeBucket

private fun Double.toLowVisionMetricsBucket(): Int =
    (this * LOW_VISION_ACTUAL_METRICS_BUCKET_SCALE).roundToInt()

private val LOW_VISION_ACTUAL_WALK_OPTIONS = listOf(RouteOption.SAFE, RouteOption.SHORTEST)
private val LOW_VISION_ACTUAL_TRANSIT_OPTIONS =
    listOf(
        RouteOption.RECOMMENDED,
        RouteOption.MIN_TRANSFER,
        RouteOption.MIN_WALK,
    )
private const val LOW_VISION_ACTUAL_METRICS_MIN_REQUEST_INTERVAL_MILLIS = 5_000L
private const val LOW_VISION_ACTUAL_METRICS_MIN_REQUEST_DISTANCE_METERS = 20.0
private const val LOW_VISION_ACTUAL_METRICS_REUSE_DISTANCE_METERS = 25.0
private const val LOW_VISION_ACTUAL_METRICS_CACHE_MAX_AGE_MILLIS = 10_000L
private const val NAVIGATION_TRANSIT_OPTION_LABEL_LIMIT = 4
private const val NAVIGATION_BOOKMARK_UNAVAILABLE_MESSAGE = "서버에 저장할 수 있는 목적지에서만 북마크를 저장할 수 있습니다."
private const val NAVIGATION_BOOKMARK_SAVE_FAILURE_MESSAGE = "북마크를 저장하지 못했습니다. 다시 시도해 주세요."

private object NoOpRouteRepository : RouteRepository {
    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("NavigationViewModel requires a RouteRepository for route search.")

    override suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("NavigationViewModel requires a RouteRepository for transit route search.")

    override suspend fun selectRoute(
        routeId: String,
        searchId: String,
    ) = error("NavigationViewModel requires a RouteRepository for route selection.")

    override suspend fun refreshTransit(
        routeId: String,
        legSequence: Int,
    ) = error("NavigationViewModel requires a RouteRepository for transit refresh.")

    override suspend fun reroute(
        routeId: String,
        currentPoint: GeoCoordinate,
    ) = error("NavigationViewModel requires a RouteRepository for reroute.")

    override suspend fun endRoute(routeId: String) =
        error("NavigationViewModel requires a RouteRepository for route end.")

    override suspend fun rateRoute(
        sessionId: String,
        score: Int,
    ) = error("NavigationViewModel does not submit ratings.")
}

private object NoOpReportRepository : ReportRepository {
    override fun observeReportHistory() =
        error("NavigationViewModel does not observe report history.")

    override suspend fun getLatestDraft(): ReportDraftData =
        error("NavigationViewModel does not read report drafts.")

    override suspend fun saveDraft(draft: ReportDraftData): ReportDraftData =
        error("NavigationViewModel does not save report drafts.")

    override suspend fun deleteDraft(draftId: String) =
        error("NavigationViewModel does not delete report drafts.")

    override suspend fun saveOutbox(outbox: ReportOutboxData): ReportOutboxData =
        error("NavigationViewModel does not save report outbox.")

    override suspend fun submitOutboxToServer(outboxId: String) =
        error("NavigationViewModel does not submit report outbox.")
}

private fun GeoCoordinate?.toDebugCoordinate(): String =
    this?.let { coordinate ->
        String.format(Locale.US, "%.6f,%.6f", coordinate.latitude, coordinate.longitude)
    } ?: "null"
