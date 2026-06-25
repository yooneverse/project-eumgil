package com.ssafy.e102.eumgil.feature.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ssafy.e102.eumgil.core.location.CurrentLocationManager
import com.ssafy.e102.eumgil.core.location.LocationPermissionManager
import com.ssafy.e102.eumgil.core.location.LocationPermissionState
import com.ssafy.e102.eumgil.core.location.LocationPermissionUnavailableReason as PermissionUnavailableReason
import com.ssafy.e102.eumgil.core.location.LocationSnapshot
import com.ssafy.e102.eumgil.core.location.isFreshCurrentLocation
import com.ssafy.e102.eumgil.core.model.FacilityBrowseData
import com.ssafy.e102.eumgil.core.model.FacilityCategory
import com.ssafy.e102.eumgil.core.model.FacilityDetailSeed
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.MapPlaceClickType
import com.ssafy.e102.eumgil.core.model.MapPlaceDetailType
import com.ssafy.e102.eumgil.core.model.MapPlaceDetailRequest
import com.ssafy.e102.eumgil.core.model.MapTappedPlaceDetail
import com.ssafy.e102.eumgil.core.model.PlaceDetail
import com.ssafy.e102.eumgil.core.model.PlaceDestination
import com.ssafy.e102.eumgil.core.model.RecentDestination
import com.ssafy.e102.eumgil.core.model.toPlaceDestination
import com.ssafy.e102.eumgil.data.repository.ApprovedReportMapQuery
import com.ssafy.e102.eumgil.data.repository.ApprovedReportMapRepository
import com.ssafy.e102.eumgil.data.repository.AuthSessionRepository
import com.ssafy.e102.eumgil.data.repository.BookmarkData
import com.ssafy.e102.eumgil.data.repository.BookmarkRepository
import com.ssafy.e102.eumgil.data.repository.DestinationPreviewRepository
import com.ssafy.e102.eumgil.data.repository.DestinationPreviewRequest
import com.ssafy.e102.eumgil.data.repository.DestinationSelectionRepository
import com.ssafy.e102.eumgil.data.repository.EmptyApprovedReportMapRepository
import com.ssafy.e102.eumgil.data.repository.FacilitySeedRepository
import com.ssafy.e102.eumgil.data.repository.NoOpDestinationPreviewRepository
import com.ssafy.e102.eumgil.data.repository.PlacesRepository
import com.ssafy.e102.eumgil.data.repository.RouteSelectionRequestReason
import com.ssafy.e102.eumgil.data.repository.RouteEditingTarget
import com.ssafy.e102.eumgil.data.repository.SearchRepository
import com.ssafy.e102.eumgil.data.repository.observeAccountScopeKey
import com.ssafy.e102.eumgil.data.repository.toBookmarkData
import com.ssafy.e102.eumgil.feature.map.model.ApprovedReportSheetState
import com.ssafy.e102.eumgil.feature.map.model.KAKAO_MAP_MAX_ZOOM_LEVEL
import com.ssafy.e102.eumgil.feature.map.model.KAKAO_MAP_MIN_ZOOM_LEVEL
import com.ssafy.e102.eumgil.feature.map.model.MapCameraSource
import com.ssafy.e102.eumgil.feature.map.model.MapCameraTarget
import com.ssafy.e102.eumgil.feature.map.model.MapDefaults
import com.ssafy.e102.eumgil.feature.map.model.MapFilterSelectionState
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerDisplayState
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerFilterUiState
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerOverlayState
import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import com.ssafy.e102.eumgil.feature.map.model.MapShortcutFilterChipState
import com.ssafy.e102.eumgil.feature.map.model.MapShortcutFilterKey
import com.ssafy.e102.eumgil.feature.map.model.MapShortcutFilterRowState
import com.ssafy.e102.eumgil.feature.map.model.defaultZoomLevel
import com.ssafy.e102.eumgil.feature.map.model.resolvedZoomLevel
import com.ssafy.e102.eumgil.feature.map.model.shouldShowApprovedReportMarkers
import com.ssafy.e102.eumgil.feature.map.model.toApprovedReportMarkerDataOrNull
import com.ssafy.e102.eumgil.feature.map.model.toApprovedReportSheetState
import com.ssafy.e102.eumgil.feature.map.model.toMapCoordinate
import com.ssafy.e102.eumgil.feature.search.SearchSelectionMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

class MapViewModel(
    private val locationPermissionManager: LocationPermissionManager,
    private val currentLocationManager: CurrentLocationManager,
    private val destinationSelectionRepository: DestinationSelectionRepository,
    private val destinationPreviewRepository: DestinationPreviewRepository = NoOpDestinationPreviewRepository,
    private val facilitySeedRepository: FacilitySeedRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val authSessionRepository: AuthSessionRepository? = null,
    private val searchRepository: SearchRepository = NoOpSearchRepository,
    private val placesRepository: PlacesRepository? = null,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val enableLocationRecoveryWatchdog: Boolean = false,
    private val approvedReportMapRepository: ApprovedReportMapRepository = EmptyApprovedReportMapRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = mutableUiState.asStateFlow()

    private val mutableUiEvent = Channel<MapUiEvent>(capacity = Channel.BUFFERED)
    val uiEvent: Flow<MapUiEvent> = mutableUiEvent.receiveAsFlow()

    private var latestPermissionState: LocationPermissionState = locationPermissionManager.permissionState.value
    private var latestLocation: LocationSnapshot? = currentLocationManager.latestLocation.value.toFreshCurrentLocationOrNull()
    private var selectedOrigin: PlaceDestination? = destinationSelectionRepository.selectedOrigin.value
    private var selectedDestination: PlaceDestination? = destinationSelectionRepository.selectedDestination.value
    private var routeEditingTarget: RouteEditingTarget = destinationSelectionRepository.editingTarget.value
    private var selectedMarkerId: String? = null
    private var selectedMapPinCoordinate: MapCoordinate? = null
    private var selectedFacilityDetail: FacilityDetailSeed? = null
    private var selectedMapTapDetail: MapTappedPlaceDetail? = null
    private var selectedMapTapNameHint: String? = null
    private var selectedDestinationPreview: DestinationPreviewRequest? = null
    private var facilityDetailSheetPresentation: MapFacilityDetailSheetPresentation =
        MapFacilityDetailSheetPresentation.EXPANDED
    private var isMapTapDetailLoading = false
    private var mapTapDetailErrorMessage: String? = null
    private var mapTapDetailRequestId: Long = 0L
    private var mapTapDetailLookupJob: Job? = null
    private var selectedFacilityBookmarkState = SelectedFacilityBookmarkState()
    private var facilityBrowseData: FacilityBrowseData? = null
    private var markerFilterSelectionState: MapFilterSelectionState = MapFilterSelectionState()
    private var recentDestinations: List<RecentDestination> = emptyList()
    private var routeEndpointMapPickerState: RouteEndpointMapPickerState? = null
    private var isRouteStarted = false
    private var locationLookupState: LocationLookupState = LocationLookupState.Idle
    private var locationLookupTimeoutJob: Job? = null
    private var locationRecoveryJob: Job? = null
    private var lastFreshLocationObservedAtEpochMillis: Long? = latestLocation?.let { nowEpochMillis() }
    private var locationRecoveryRestartAttemptCount = 0
    private var isRecenterButtonActive = false
    private var lastPlacesBrowseAnchorSource: PlacesBrowseAnchorSource? = null
    private var placesBrowseRequestSequence: Long = 0L
    private var lastMarkerOverlayLogSnapshot: String? = null
    private var lastApprovedReportQuery: ApprovedReportMapQuery? = null
    private var approvedReportRequestSequence: Long = 0L

    init {
        mutableUiState.update { state ->
            state.copy(
                selectedOrigin = selectedOrigin,
                selectedDestination = selectedDestination,
                routeEditingTarget = routeEditingTarget,
            )
        }
        selectedDestination?.let { destination ->
            syncCameraToSelectedDestination(
                destination = destination,
                incrementRequestId = true,
            )
        }
        observeSelectedDestination()
        observeSelectionRequests()
        observeDestinationPreviewRequests()
        observeAccountScope()
        observePermissionState()
        observeLocationUpdates()
        loadMarkerBrowseState()
        refreshRecentDestinations()
        renderUiState()
    }

    fun onRouteStarted() {
        if (isRouteStarted) {
            locationPermissionManager.refreshPermissionState()
            return
        }

        isRouteStarted = true
        isRecenterButtonActive = false
        locationPermissionManager.refreshPermissionState()
        latestPermissionState = locationPermissionManager.permissionState.value

        when (latestPermissionState) {
            is LocationPermissionState.Granted -> startLocationTracking(forceLookupRestart = false)
            LocationPermissionState.Denied -> applyFallbackCameraTarget()
            is LocationPermissionState.Unavailable -> applyFallbackCameraTarget()
        }

        refreshRecentDestinations()
        renderUiState()
        loadApprovedReportsForViewport(force = true)
    }

    fun onRouteStopped() {
        if (!isRouteStarted) return

        isRouteStarted = false
        isRecenterButtonActive = false
        routeEndpointMapPickerState = null
        stopLocationLookup()
        stopLocationRecoveryWatchdog()
        currentLocationManager.stopLocationUpdates()
    }

    fun onHomeReentered() {
        isRecenterButtonActive = false
        locationPermissionManager.refreshPermissionState()
        latestPermissionState = locationPermissionManager.permissionState.value
        if (latestPermissionState is LocationPermissionState.Granted) {
            currentLocationManager.startLocationUpdates()
            currentLocationManager.refreshLatestLocation()
        }
        latestLocation = currentLocationManager.latestLocation.value.toFreshCurrentLocationOrNull()

        routeEndpointMapPickerState = null
        val hadFacilitySelection = clearSelectedFacilitySelection()
        val hadSelectedOrigin = selectedOrigin != null
        val hadSelectedDestination = selectedDestination != null
        selectedOrigin = null
        selectedDestination = null
        routeEditingTarget = RouteEditingTarget.DESTINATION
        mutableUiState.update { state ->
            state.copy(
                selectedOrigin = null,
                selectedDestination = null,
                routeEditingTarget = RouteEditingTarget.DESTINATION,
            )
        }
        if (hadSelectedOrigin) {
            destinationSelectionRepository.clearSelectedOriginSilently()
        }
        destinationSelectionRepository.setEditingTarget(RouteEditingTarget.DESTINATION)
        if (hadSelectedDestination) {
            destinationSelectionRepository.clearSelectedDestination()
        }
        destinationPreviewRepository.clearPreview()
        if (hadFacilitySelection) {
            renderSelectedFacilityState()
        }

        val currentLocation = latestLocation
        if (currentLocation != null && latestPermissionState is LocationPermissionState.Granted) {
            stopLocationLookup()
            syncCameraToCurrentLocation(
                snapshot = currentLocation,
                incrementRequestId = true,
            )
        } else {
            if (latestPermissionState is LocationPermissionState.Granted) {
                if (isRouteStarted) {
                    locationLookupState = LocationLookupState.TimedOut
                } else {
                    startLocationLookup(forceRestart = true)
                }
            } else {
                stopLocationLookup()
            }
            applyFallbackCameraTarget()
        }
        renderUiState()
    }

    fun onAction(action: MapUiAction) {
        when (action) {
            is MapUiAction.ApprovedReportMarkerTapped -> handleApprovedReportMarkerTapped(action.reportId)
            MapUiAction.ApprovedReportSheetDismissed -> dismissApprovedReportSheet()
            MapUiAction.BackgroundMapTapped -> handleBackgroundMapTapped()
            MapUiAction.FacilityBookmarkClicked -> toggleSelectedFacilityBookmark()
            MapUiAction.FacilityDetailDismissed -> dismissFacilityDetailSheet()
            MapUiAction.FacilityDetailExpanded -> expandFacilityDetailSheet()
            MapUiAction.FacilityPhoneClicked -> handleFacilityPhoneClicked()
            is MapUiAction.RouteEndpointMapPickerEntered -> enterRouteEndpointMapPicker(action.editingTarget)
            MapUiAction.RouteEndpointMapPickerDismissed -> dismissRouteEndpointMapPicker()
            MapUiAction.VoiceSearchClicked -> openVoiceSearch()
            MapUiAction.VoiceSearchDismissed -> dismissVoiceSearch()
            MapUiAction.FacilitySetDestinationClicked ->
                handleFacilitySetRouteEndpointClicked(RouteEditingTarget.DESTINATION)
            is MapUiAction.FacilitySetRouteEndpointClicked ->
                handleFacilitySetRouteEndpointClicked(action.editingTarget)
            MapUiAction.LocationActionClicked -> handleLocationAction()
            MapUiAction.ZoomInClicked -> handleZoomAction(delta = 1)
            MapUiAction.ZoomOutClicked -> handleZoomAction(delta = -1)
            is MapUiAction.MarkerTapped -> handleMarkerTapped(action.markerId)
            is MapUiAction.MapTapped -> handleMapTapped(action.payload)
            is MapUiAction.ViewportCameraChanged ->
                handleViewportCameraChanged(
                    center = action.center,
                    zoomLevel = action.zoomLevel,
                    isUserGesture = action.isUserGesture,
                    isSelectedMapPinVisibleInViewport = action.isSelectedMapPinVisibleInViewport,
                )
            MapUiAction.MarkerCategoryFilterReset -> resetMarkerCategoryFilter()
            is MapUiAction.MarkerCategoryFilterToggled -> toggleMarkerCategoryFilter(action.category)
            is MapUiAction.ShortcutFilterClicked -> handleShortcutFilterClicked(action.key)
            is MapUiAction.RecentDestinationPreviewClicked -> handleRecentDestinationPreviewClicked(action.placeId)
            is MapUiAction.RecentDestinationRouteClicked -> handleRecentDestinationRouteClicked(action.placeId)
            MapUiAction.SearchHereClicked -> handleSearchHereClicked()
            MapUiAction.SearchEntryClicked ->
                emitUiEvent(
                    MapUiEvent.NavigateToSearch(
                        editingTarget = RouteEditingTarget.DESTINATION,
                        selectionMode = SearchSelectionMode.PREVIEW_ON_MAP,
                    ),
                )
            is MapUiAction.RouteEndpointStatusClicked -> handleRouteEndpointStatusClicked(action.editingTarget)
        }
    }

    override fun onCleared() {
        stopLocationLookup()
        stopLocationRecoveryWatchdog()
        mapTapDetailLookupJob?.cancel()
        currentLocationManager.stopLocationUpdates()
        mutableUiEvent.close()
        super.onCleared()
    }

    private fun loadMarkerBrowseState() {
        val placesRepository = placesRepository
        if (placesRepository != null) {
            safeLogInfo(
                MAP_VIEW_MODEL_LOG_TAG,
                "Loading live places browse state source=${currentPlacesBrowseAnchorSource().name}",
            )
            loadLivePlaceBrowseState(
                anchorSource = currentPlacesBrowseAnchorSource(),
            )
            return
        }

        safeLogInfo(MAP_VIEW_MODEL_LOG_TAG, "Loading facility seed browse state")
        viewModelScope.launch {
            runCatching {
                facilitySeedRepository.getFacilityBrowseData()
            }.onSuccess { browseData ->
                facilityBrowseData = browseData
                safeLogInfo(
                    MAP_VIEW_MODEL_LOG_TAG,
                    "Seed browse success markers=${browseData.facilityMarkers.size} categories=${browseData.availableCategories.size}",
                )
                markerFilterSelectionState = MapBrowseStateFactory.resetSelection()
                renderMarkerBrowseState()
            }.onFailure {
                safeLogError(MAP_VIEW_MODEL_LOG_TAG, "Seed browse load failed", it)
                facilityBrowseData = null
                updateSelectedFacility(markerId = null)
                markerFilterSelectionState = MapBrowseStateFactory.resetSelection()
                mutableUiState.update { state ->
                    state.copy(
                        selectedMarkerId = null,
                        facilityDetailSheetState = currentFacilityDetailSheetState(),
                        markerOverlayState = MapBrowseStateFactory.createErrorMarkerOverlayState(),
                        markerFilterState = MapBrowseStateFactory.createErrorFilterUiState(),
                        shortcutFilterState = createShortcutFilterState(),
                    )
                }
            }
        }
    }

    private fun loadLivePlaceBrowseState(
        anchorSource: PlacesBrowseAnchorSource,
        force: Boolean = false,
        anchor: MapCoordinate = currentPlacesBrowseAnchor(),
        preserveSelection: Boolean = false,
    ) {
        val placesRepository = placesRepository ?: return
        if (!force && lastPlacesBrowseAnchorSource == anchorSource && facilityBrowseData != null) {
            safeLogDebug(
                MAP_VIEW_MODEL_LOG_TAG,
                "Skipping live places browse reload source=${anchorSource.name} reason=cached",
            )
            return
        }

        val requestId = ++placesBrowseRequestSequence

        safeLogInfo(
            MAP_VIEW_MODEL_LOG_TAG,
            "Requesting places browse requestId=$requestId source=${anchorSource.name} force=$force lat=${anchor.latitude.toLogCoordinate()} lng=${anchor.longitude.toLogCoordinate()} radius=$MAP_BROWSE_RADIUS_METERS",
        )
        viewModelScope.launch {
            runCatching {
                placesRepository.getPlaces(
                    com.ssafy.e102.eumgil.core.model.PlaceQuery(
                        latitude = anchor.latitude,
                        longitude = anchor.longitude,
                        radiusMeters = MAP_BROWSE_RADIUS_METERS,
                    ),
                )
            }.onSuccess { places ->
                if (requestId != placesBrowseRequestSequence) {
                    safeLogInfo(
                        MAP_VIEW_MODEL_LOG_TAG,
                        "Ignoring stale places browse response requestId=$requestId latestRequestId=$placesBrowseRequestSequence source=${anchorSource.name}",
                    )
                    return@onSuccess
                }
                val browseData = MapPlaceBrowseDataMapper.toBrowseData(places)
                facilityBrowseData = browseData
                lastPlacesBrowseAnchorSource = anchorSource
                safeLogInfo(
                    MAP_VIEW_MODEL_LOG_TAG,
                    "Places browse success source=${anchorSource.name} places=${places.size} markers=${browseData.facilityMarkers.size} categories=${browseData.availableCategories.size}",
                )
                if (places.isEmpty()) {
                    safeLogWarn(
                        MAP_VIEW_MODEL_LOG_TAG,
                        "Places browse returned no data source=${anchorSource.name} lat=${anchor.latitude.toLogCoordinate()} lng=${anchor.longitude.toLogCoordinate()} radius=$MAP_BROWSE_RADIUS_METERS",
                    )
                }
                markerFilterSelectionState =
                    if (preserveSelection) {
                        MapBrowseStateFactory.normalizeSelection(
                            selection = markerFilterSelectionState,
                            browseData = browseData,
                            preserveUnavailableCategories = true,
                        )
                    } else {
                        MapBrowseStateFactory.resetSelection()
                    }
                renderMarkerBrowseState()
                mutableUiState.update { state ->
                    state.copy(
                        isSearchHereVisible = false,
                    )
                }
            }.onFailure {
                if (requestId != placesBrowseRequestSequence) {
                    safeLogInfo(
                        MAP_VIEW_MODEL_LOG_TAG,
                        "Ignoring stale places browse failure requestId=$requestId latestRequestId=$placesBrowseRequestSequence source=${anchorSource.name}",
                    )
                    return@onFailure
                }
                safeLogError(
                    MAP_VIEW_MODEL_LOG_TAG,
                    "Places browse failed source=${anchorSource.name} lat=${anchor.latitude.toLogCoordinate()} lng=${anchor.longitude.toLogCoordinate()} radius=$MAP_BROWSE_RADIUS_METERS",
                    it,
                )
                facilityBrowseData = null
                lastPlacesBrowseAnchorSource = null
                updateSelectedFacility(markerId = null)
                markerFilterSelectionState = MapBrowseStateFactory.resetSelection()
                mutableUiState.update { state ->
                    state.copy(
                        selectedMarkerId = null,
                        facilityDetailSheetState = currentFacilityDetailSheetState(),
                        markerOverlayState = MapBrowseStateFactory.createErrorMarkerOverlayState(),
                        markerFilterState = MapBrowseStateFactory.createErrorFilterUiState(),
                        shortcutFilterState = createShortcutFilterState(),
                        isSearchHereVisible = false,
                    )
                }
            }
        }
    }

    private fun handleMarkerTapped(markerId: String) {
        dismissApprovedReportSheet()
        if (selectedMarkerId == markerId) {
            updateSelectedFacility(markerId = null)
            renderSelectedFacilityState()
            return
        }

        val previewDetail = facilityBrowseData?.detailFor(markerId)
        val placesRepository = placesRepository
        if (placesRepository != null) {
            if (previewDetail != null) {
                updateSelectedFacility(
                    markerId = markerId,
                    detail = previewDetail,
                    loadBookmarkState = false,
                )
            } else {
                clearMapTapSelectionState(clearPin = true)
                selectedMarkerId = markerId
                selectedFacilityDetail = null
                selectedFacilityBookmarkState = SelectedFacilityBookmarkState()
            }
            renderSelectedFacilityState()
            fetchSelectedFacilityDetail(
                markerId = markerId,
                fallbackDetail = previewDetail,
            )
            return
        }

        updateSelectedFacility(markerId = markerId)
        renderSelectedFacilityState()
    }

    private fun handleApprovedReportMarkerTapped(reportId: Long) {
        val state = mutableUiState.value
        if (state.routeEndpointMapPickerState != null || state.isVoiceSearchVisible) return

        clearSelectedFacilitySelection()

        val selectedReportId = state.approvedReportMarkerState.selectedReportId
        val nextSelectedReportId = reportId.takeIf { selectedReportId != reportId }
        val nextSheetState =
            toApprovedReportSheetState(
                reports = state.approvedReportMarkerState.visibleReports,
                selectedReportId = nextSelectedReportId,
            )

        mutableUiState.update { current ->
            current.copy(
                selectedMarkerId = null,
                selectedMapPinCoordinate = null,
                facilityDetailSheetState = MapFacilityDetailSheetState(),
                approvedReportMarkerState =
                    current.approvedReportMarkerState.copy(selectedReportId = nextSelectedReportId),
                approvedReportSheetState = nextSheetState,
            )
        }
    }

    private fun dismissApprovedReportSheet() {
        mutableUiState.update { state ->
            state.copy(
                approvedReportMarkerState =
                    state.approvedReportMarkerState.copy(selectedReportId = null),
                approvedReportSheetState = ApprovedReportSheetState(),
            )
        }
    }

    private fun handleMapTapped(payload: MapTapPayload) {
        val coordinate = payload.coordinate
        safeLogInfo(
            MAP_VIEW_MODEL_LOG_TAG,
            "Map tapped lat=${coordinate.latitude.toLogCoordinate()} lng=${coordinate.longitude.toLogCoordinate()} clickType=${payload.clickType.name} provider=${payload.provider.orEmpty()} providerPlaceId=${payload.providerPlaceId.orEmpty()}",
        )
        if (routeEndpointMapPickerState != null) return
        if (payload.clickType == MapTapClickType.ADDRESS) {
            clearSelectedFacilitySelection()
            renderSelectedFacilityState()
            return
        }
        val placesRepository = placesRepository
        if (placesRepository == null) {
            mapTapDetailRequestId += 1L
            clearSelectedFacilitySelection()
            renderSelectedFacilityState()
            return
        }
        mapTapDetailRequestId += 1L
        val requestId = mapTapDetailRequestId
        selectedMapPinCoordinate = coordinate
        clearSelectedFacilitySelection(clearMapTapSelection = false)
        facilityDetailSheetPresentation = MapFacilityDetailSheetPresentation.EXPANDED
        selectedMapTapDetail = null
        selectedMapTapNameHint = payload.nameHint?.takeIf { it.isNotBlank() }
        mapTapDetailErrorMessage = null
        isMapTapDetailLoading = true
        renderSelectedFacilityState()

        mapTapDetailLookupJob?.cancel()
        mapTapDetailLookupJob =
            viewModelScope.launch {
                runCatching {
                    placesRepository.getMapTappedPlaceDetail(payload.toMapPlaceDetailRequest())
                }.onSuccess { detail ->
                    if (requestId != mapTapDetailRequestId) return@onSuccess
                    val normalizedDetail = detail?.withFallbackCoordinate(coordinate)
                    isMapTapDetailLoading = false
                    selectedMapTapDetail = normalizedDetail
                    selectedFacilityBookmarkState =
                        normalizedDetail?.let { mapTapDetail ->
                            SelectedFacilityBookmarkState(
                                facilityId = mapTapDetail.bookmarkCacheKey(),
                                isBookmarked = mapTapDetail.isBookmarked,
                            )
                        } ?: SelectedFacilityBookmarkState()
                    mapTapDetailErrorMessage =
                        if (normalizedDetail == null) {
                            MAP_TAP_DETAIL_EMPTY_MESSAGE
                        } else {
                            null
                        }
                    renderSelectedFacilityState()
                }.onFailure {
                    if (requestId != mapTapDetailRequestId) return@onFailure
                    isMapTapDetailLoading = false
                    selectedMapTapDetail = null
                    mapTapDetailErrorMessage = MAP_TAP_DETAIL_LOAD_FAILURE_MESSAGE
                    renderSelectedFacilityState()
                }
            }
    }

    private fun updateRouteEndpointMapPickerCandidate(coordinate: MapCoordinate) {
        val currentPickerState = routeEndpointMapPickerState ?: return
        val previousCoordinate = currentPickerState.candidateCoordinate
        val isSameCoordinate = previousCoordinate?.isApproximatelySameCoordinate(coordinate) == true
        if (
            isSameCoordinate &&
            (currentPickerState.candidateDetail != null || currentPickerState.isResolvingCandidate)
        ) {
            return
        }

        mapTapDetailRequestId += 1L
        val requestId = mapTapDetailRequestId
        val addressPayload = MapTapPayload(coordinate = coordinate, clickType = MapTapClickType.ADDRESS)
        val poiPayload = MapTapPayload(coordinate = coordinate, clickType = MapTapClickType.POI)
        val fallbackDetail = addressPayload.toRouteEndpointPickerMapTapDetail()

        routeEndpointMapPickerState =
            currentPickerState.copy(
                candidateCoordinate = coordinate,
                candidateDetail = fallbackDetail,
                isResolvingCandidate = placesRepository != null,
                candidateErrorMessage = null,
            )
        renderUiState()

        val placesRepository = placesRepository ?: return

        mapTapDetailLookupJob?.cancel()
        mapTapDetailLookupJob =
            viewModelScope.launch {
                val poiResult =
                    runCatching {
                        placesRepository.getMapTappedPlaceDetail(poiPayload.toMapPlaceDetailRequest())
                    }
                if (requestId != mapTapDetailRequestId) return@launch

                val poiDetail = poiResult.getOrNull()?.withFallbackCoordinate(coordinate)
                if (poiDetail != null) {
                    routeEndpointMapPickerState =
                        routeEndpointMapPickerState?.copy(
                            candidateCoordinate = coordinate,
                            candidateDetail = poiDetail,
                            isResolvingCandidate = false,
                            candidateErrorMessage = null,
                        )
                    renderUiState()
                    return@launch
                }

                runCatching {
                    placesRepository.getMapTappedPlaceDetail(addressPayload.toMapPlaceDetailRequest())
                }.onSuccess { detail ->
                    if (requestId != mapTapDetailRequestId) return@onSuccess
                    val normalizedDetail = detail?.withFallbackCoordinate(coordinate) ?: fallbackDetail
                    routeEndpointMapPickerState =
                        routeEndpointMapPickerState?.copy(
                            candidateCoordinate = coordinate,
                            candidateDetail = normalizedDetail,
                            isResolvingCandidate = false,
                            candidateErrorMessage = null,
                        )
                    renderUiState()
                }.onFailure {
                    if (requestId != mapTapDetailRequestId) return@onFailure
                    routeEndpointMapPickerState =
                        routeEndpointMapPickerState?.copy(
                            candidateCoordinate = coordinate,
                            candidateDetail = fallbackDetail,
                            isResolvingCandidate = false,
                            candidateErrorMessage = MAP_TAP_DETAIL_LOAD_FAILURE_MESSAGE,
                        )
                    renderUiState()
                }
            }
    }

    private fun fetchSelectedFacilityDetail(
        markerId: String,
        fallbackDetail: FacilityDetailSeed?,
    ) {
        val placesRepository = placesRepository ?: return
        viewModelScope.launch {
            runCatching { placesRepository.getPlaceDetail(markerId) }
                .onSuccess { placeDetail ->
                    if (selectedMarkerId != markerId) return@onSuccess
                    if (placeDetail == null) {
                        fallbackDetail?.let(::loadSelectedFacilityBookmarkState)
                        return@onSuccess
                    }

                    updateSelectedFacility(
                        markerId = markerId,
                        detail = MapPlaceBrowseDataMapper.toFacilityDetailSeed(placeDetail),
                        loadBookmarkState = false,
                    )
                    selectedFacilityBookmarkState =
                        SelectedFacilityBookmarkState(
                            facilityId = markerId,
                            isBookmarked = placeDetail.isBookmarked,
                        )
                    renderSelectedFacilityState()
                }.onFailure {
                    if (selectedMarkerId != markerId) return@onFailure
                    fallbackDetail?.let(::loadSelectedFacilityBookmarkState)
                }
        }
    }

    private fun dismissFacilityDetailSheet() {
        if (routeEndpointMapPickerState != null) {
            dismissRouteEndpointMapPicker()
            return
        }
        if (!clearSelectedFacilitySelection()) return
        renderSelectedFacilityState()
    }

    private fun expandFacilityDetailSheet() {
        if (facilityDetailSheetPresentation == MapFacilityDetailSheetPresentation.EXPANDED) return
        if (!currentFacilityDetailSheetState().isVisible) return

        facilityDetailSheetPresentation = MapFacilityDetailSheetPresentation.EXPANDED
        renderSelectedFacilityState()
    }

    private fun handleBackgroundMapTapped() {
        if (routeEndpointMapPickerState != null) return
        if (!clearSelectedFacilitySelection()) return

        renderSelectedFacilityState()
    }

    private fun enterRouteEndpointMapPicker(editingTarget: RouteEditingTarget) {
        destinationSelectionRepository.setEditingTarget(editingTarget)
        routeEditingTarget = editingTarget
        routeEndpointMapPickerState = RouteEndpointMapPickerState(editingTarget = editingTarget)
        isRecenterButtonActive = false
        if (clearSelectedFacilitySelection()) {
            renderSelectedFacilityState()
        }
        renderUiState()
        updateRouteEndpointMapPickerCandidate(mutableUiState.value.cameraTarget.center)
    }

    private fun dismissRouteEndpointMapPicker() {
        if (routeEndpointMapPickerState == null) return

        routeEndpointMapPickerState = null
        mapTapDetailLookupJob?.cancel()
        mapTapDetailLookupJob = null
        mapTapDetailRequestId += 1L
        clearSelectedFacilitySelection()
        renderSelectedFacilityState()
        renderUiState()
    }

    private fun openVoiceSearch() {
        if (mutableUiState.value.isVoiceSearchVisible) return

        if (clearSelectedFacilitySelection()) {
            renderSelectedFacilityState()
        }

        mutableUiState.update { state ->
            state.copy(isVoiceSearchVisible = true)
        }
    }

    private fun dismissVoiceSearch() {
        if (!mutableUiState.value.isVoiceSearchVisible) return

        mutableUiState.update { state ->
            state.copy(isVoiceSearchVisible = false)
        }
    }

    private fun handleFacilitySetRouteEndpointClicked(editingTarget: RouteEditingTarget) {
        val pickerState = routeEndpointMapPickerState
        val shouldBypassRouteSettingPermissionGate = pickerState != null
        val resolvedEditingTarget = pickerState?.editingTarget ?: editingTarget
        val pickerDestination = pickerState?.candidateDetail?.toPlaceDestinationOrNull()
        if (pickerDestination != null) {
            routeEndpointMapPickerState = null
            mapTapDetailLookupJob?.cancel()
            mapTapDetailLookupJob = null
            clearSelectedFacilitySelection()
            renderSelectedFacilityState()
            destinationSelectionRepository.setEditingTarget(resolvedEditingTarget)
            destinationSelectionRepository.updateSelectionForEditingTarget(pickerDestination)
            renderUiState()
            navigateToRouteSettingIfRouteEndpointsReady(
                locationPermissionPrechecked = shouldBypassRouteSettingPermissionGate,
            )
            return
        }

        val preview = selectedDestinationPreview
        if (preview != null) {
            clearSelectedFacilitySelection()
            routeEndpointMapPickerState = null
            renderSelectedFacilityState()
            destinationSelectionRepository.setEditingTarget(resolvedEditingTarget)
            destinationSelectionRepository.updateSelectionForEditingTarget(preview.destination)
            renderUiState()
            navigateToRouteSettingIfRouteEndpointsReady(
                locationPermissionPrechecked = shouldBypassRouteSettingPermissionGate,
            )
            return
        }

        val destination =
            selectedMapTapDetail?.toPlaceDestinationOrNull()
                ?: selectedFacilityDetail?.toPlaceDestination()
                ?: return
        clearSelectedFacilitySelection()
        routeEndpointMapPickerState = null
        renderSelectedFacilityState()
        destinationSelectionRepository.setEditingTarget(resolvedEditingTarget)
        destinationSelectionRepository.updateSelectionForEditingTarget(destination)
        renderUiState()
        navigateToRouteSettingIfRouteEndpointsReady(
            locationPermissionPrechecked = shouldBypassRouteSettingPermissionGate,
        )
    }

    private fun navigateToRouteSettingIfRouteEndpointsReady(locationPermissionPrechecked: Boolean = false) {
        emitUiEvent(
            MapUiEvent.NavigateToRouteSetting(
                locationPermissionPrechecked = locationPermissionPrechecked,
            ),
        )
    }

    private fun handleRouteEndpointStatusClicked(editingTarget: RouteEditingTarget) {
        destinationSelectionRepository.setEditingTarget(editingTarget)
        routeEditingTarget = editingTarget
        mutableUiState.update { state ->
            state.copy(routeEditingTarget = editingTarget)
        }
        emitUiEvent(
            MapUiEvent.NavigateToSearch(
                editingTarget = editingTarget,
                selectionMode = SearchSelectionMode.APPLY_TO_ROUTE,
            ),
        )
    }

    private fun handleRecentDestinationRouteClicked(placeId: String) {
        val destination =
            recentDestinations
                .firstOrNull { recentDestination -> recentDestination.placeId == placeId }
                ?.toPlaceDestination()
                ?: return

        if (clearSelectedFacilitySelection()) {
            renderSelectedFacilityState()
        }
        destinationSelectionRepository.updateSelectedDestination(destination)
        emitUiEvent(MapUiEvent.NavigateToRouteSetting(locationPermissionPrechecked = false))
    }

    private fun handleRecentDestinationPreviewClicked(placeId: String) {
        val recentDestination =
            recentDestinations.firstOrNull { destination -> destination.placeId == placeId } ?: return

        destinationPreviewRepository.requestPreview(
            destination = recentDestination.toPlaceDestination(),
            editingTarget = routeEditingTarget,
            accessibilityTagKeys = recentDestination.accessibilityTagKeys,
        )
    }

    private fun resetMarkerCategoryFilter() {
        if (facilityBrowseData == null) return
        markerFilterSelectionState = MapBrowseStateFactory.resetSelection()
        renderMarkerBrowseState()
    }

    private fun toggleMarkerCategoryFilter(category: FacilityCategory) {
        val browseData = facilityBrowseData ?: return
        markerFilterSelectionState =
            MapBrowseStateFactory.toggleCategory(
                selection = markerFilterSelectionState,
                browseData = browseData,
                category = category,
            )
        renderMarkerBrowseState()
    }

    private fun handleSearchHereClicked() {
        if (placesRepository == null) return

        val anchor = mutableUiState.value.cameraTarget.center
        mutableUiState.update { state ->
            state.copy(isSearchHereVisible = false)
        }
        loadLivePlaceBrowseState(
            anchorSource = PlacesBrowseAnchorSource.VIEWPORT,
            force = true,
            anchor = anchor,
            preserveSelection = true,
        )
    }

    private fun handleShortcutFilterClicked(key: MapShortcutFilterKey) {
        val browseData = facilityBrowseData ?: return

        val category = key.toFacilityCategory(browseData.availableCategories.toSet())
        if (category == null && key.isSelected(markerFilterSelectionState)) {
            markerFilterSelectionState = MapBrowseStateFactory.resetSelection()
            renderMarkerBrowseState()
            return
        }
        if (category == null) {
            emitUiEvent(MapUiEvent.ShowSnackbar(SHORTCUT_FILTER_UNAVAILABLE_MESSAGE))
            return
        }
        markerFilterSelectionState =
            MapBrowseStateFactory.selectSingleCategory(
                selection = markerFilterSelectionState,
                browseData = browseData,
                category = category,
            )
        renderMarkerBrowseState()
    }

    private fun renderMarkerBrowseState() {
        val browseData = facilityBrowseData ?: return
        val availableCategories = browseData.availableCategories.toSet()
        val baseOverlayState =
            MapBrowseStateFactory.createMarkerOverlayState(
                browseData = browseData,
                selection = markerFilterSelectionState,
            )
        val overlayState = baseOverlayState
        val filterState =
            MapBrowseStateFactory.createFilterUiState(
                browseData = browseData,
                selection = markerFilterSelectionState,
                overlayState = overlayState,
            )
        val overlayLogSnapshot =
            buildString {
                append("total=")
                append(overlayState.totalMarkerCount)
                append(" visible=")
                append(overlayState.visibleMarkerCount)
                append(" emptyData=")
                append(overlayState.isEmptyData)
                append(" emptyResult=")
                append(overlayState.isEmptyResult)
                append(" shortcut=")
                append(
                    SHORTCUT_FILTER_ORDER
                        .filter { key -> key.isSelected(selection = filterState.selection, availableCategories = availableCategories) }
                        .joinToString(",") { key -> key.name }
                        .ifEmpty {
                            if (filterState.selection.isShowingAllCategories) {
                                "ALL"
                            } else {
                                "NONE"
                            }
                        },
                )
                append(" categories=")
                append(filterState.selection.selectedFacilityCategories.joinToString(",") { category -> category.name }.ifEmpty {
                    if (filterState.selection.isShowingAllCategories) {
                        "ALL"
                    } else {
                        "NONE"
                    }
                })
            }
        if (lastMarkerOverlayLogSnapshot != overlayLogSnapshot) {
            lastMarkerOverlayLogSnapshot = overlayLogSnapshot
            safeLogInfo(MAP_VIEW_MODEL_LOG_TAG, "Marker overlay state $overlayLogSnapshot")
        }

        val nextSelectedMarkerId =
            selectedMarkerId?.takeIf { markerId ->
                overlayState.markers.any { marker ->
                    marker.markerId == markerId
                        && marker.displayState == MapMarkerDisplayState.VISIBLE
                }
            }
        val nextSelectedFacilityDetail = nextSelectedMarkerId?.let(browseData::detailFor)
        updateSelectedFacility(
            markerId = nextSelectedMarkerId,
            detail = nextSelectedFacilityDetail,
        )
        markerFilterSelectionState = filterState.selection

        mutableUiState.update { state ->
            state.copy(
                selectedMarkerId = selectedMarkerId,
                facilityDetailSheetState = currentFacilityDetailSheetState(),
                markerOverlayState = overlayState,
                markerFilterState = filterState,
                shortcutFilterState = createShortcutFilterState(browseData),
            )
        }
    }

    private fun observePermissionState() {
        viewModelScope.launch {
            locationPermissionManager.permissionState.collectLatest { permissionState ->
                latestPermissionState = permissionState

                when (permissionState) {
                    is LocationPermissionState.Granted -> handleGrantedPermission()
                    LocationPermissionState.Denied -> handlePermissionBlocked()
                    is LocationPermissionState.Unavailable -> handlePermissionBlocked()
                }

                renderUiState()
            }
        }
    }

    private fun observeSelectedDestination() {
        viewModelScope.launch {
            destinationSelectionRepository.selectedDestination.collectLatest { destination ->
                val previousDestination = selectedDestination
                selectedDestination = destination
                if (destination != null) {
                    isRecenterButtonActive = false
                }

                mutableUiState.update { state ->
                    state.copy(selectedDestination = destination)
                }

                if (
                    destination == null &&
                    previousDestination != null &&
                    mutableUiState.value.cameraTarget.source == MapCameraSource.SEARCH_RESULT
                ) {
                    applyFallbackCameraTarget()
                    renderUiState()
                }
            }
        }
    }

    private fun observeAccountScope() {
        val authSessionRepository = authSessionRepository ?: return

        viewModelScope.launch {
            var isInitialEmission = true
            authSessionRepository.observeAccountScopeKey().collectLatest {
                if (isInitialEmission) {
                    isInitialEmission = false
                    return@collectLatest
                }

                handleAccountScopeChanged()
            }
        }
    }

    private fun handleAccountScopeChanged() {
        selectedDestination = null
        facilityBrowseData = null
        markerFilterSelectionState = MapBrowseStateFactory.resetSelection()
        recentDestinations = emptyList()
        lastPlacesBrowseAnchorSource = null
        lastMarkerOverlayLogSnapshot = null
        isRecenterButtonActive = false
        routeEndpointMapPickerState = null

        clearSelectedFacilitySelection()
        mutableUiState.update { state ->
            state.copy(
                selectedDestination = null,
                recentDestinations = emptyList(),
                markerOverlayState = MapMarkerOverlayState(),
                markerFilterState = MapMarkerFilterUiState(),
                shortcutFilterState = createShortcutFilterState(browseData = null),
            )
        }
        renderSelectedFacilityState()
        applyFallbackCameraTarget()
        loadMarkerBrowseState()
        refreshRecentDestinations()
        renderUiState()
    }

    private fun observeSelectionRequests() {
        viewModelScope.launch {
            destinationSelectionRepository.selectionRequests.collectLatest { request ->
                selectedOrigin = request.state.selectedOrigin
                routeEditingTarget = request.state.editingTarget
                mutableUiState.update { state ->
                    state.copy(
                        selectedOrigin = selectedOrigin,
                        routeEditingTarget = routeEditingTarget,
                    )
                }
                if (
                    request.reason != RouteSelectionRequestReason.DESTINATION_UPDATED &&
                    request.reason != RouteSelectionRequestReason.DESTINATION_CLEARED &&
                    request.reason != RouteSelectionRequestReason.SWAPPED
                ) {
                    return@collectLatest
                }
                // Any destination handoff should close stale facility detail state before the map recenters.
                if (clearSelectedFacilitySelection()) {
                    renderSelectedFacilityState()
                }
                val destination = request.state.selectedDestination
                isRecenterButtonActive = false
                if (destination == null) {
                    applyFallbackCameraTarget()
                } else {
                    syncCameraToSelectedDestination(
                        destination = destination,
                        incrementRequestId = true,
                    )
                }
                renderUiState()
            }
        }
    }

    private fun observeDestinationPreviewRequests() {
        viewModelScope.launch {
            destinationPreviewRepository.pendingPreview.collectLatest { previewRequest ->
                if (previewRequest == null) return@collectLatest

                handleDestinationPreviewRequested(previewRequest)
                destinationPreviewRepository.consumePreview(previewRequest.requestId)
            }
        }
    }

    private fun handleDestinationPreviewRequested(previewRequest: DestinationPreviewRequest) {
        val destination = previewRequest.destination
        val coordinate =
            MapCoordinate(
                latitude = destination.latitude,
                longitude = destination.longitude,
            )

        mapTapDetailRequestId += 1L
        val requestId = mapTapDetailRequestId
        mapTapDetailLookupJob?.cancel()
        mapTapDetailLookupJob = null
        selectedMapPinCoordinate = coordinate
        selectedDestinationPreview = previewRequest
        selectedMapTapDetail = previewRequest.toMapTappedPlaceDetail()
        selectedMapTapNameHint = destination.name
        facilityDetailSheetPresentation = MapFacilityDetailSheetPresentation.EXPANDED
        isMapTapDetailLoading = false
        mapTapDetailErrorMessage = null
        selectedMarkerId = null
        selectedFacilityDetail = null
        selectedFacilityBookmarkState =
            SelectedFacilityBookmarkState(
                facilityId = selectedMapTapDetail?.bookmarkCacheKey(),
                isBookmarked = selectedMapTapDetail?.isBookmarked ?: false,
            )
        isRecenterButtonActive = false
        syncCameraToDestinationPreview(
            coordinate = coordinate,
            incrementRequestId = true,
        )
        renderSelectedFacilityState()
        renderUiState()
        loadDestinationPreviewBookmarkState(
            previewRequest = previewRequest,
            requestId = requestId,
        )
        hydrateDestinationPreviewDetail(
            previewRequest = previewRequest,
            requestId = requestId,
        )
    }

    private fun loadDestinationPreviewBookmarkState(
        previewRequest: DestinationPreviewRequest,
        requestId: Long,
    ) {
        val initialBookmarkKey = selectedMapTapDetail?.bookmarkCacheKey() ?: return

        viewModelScope.launch {
            runCatching { bookmarkRepository.isBookmarked(initialBookmarkKey) }
                .onSuccess { isBookmarked ->
                    if (requestId != mapTapDetailRequestId) return@onSuccess
                    if (selectedDestinationPreview?.requestId != previewRequest.requestId) return@onSuccess
                    val currentDetail = selectedMapTapDetail ?: return@onSuccess
                    if (currentDetail.bookmarkCacheKey() != initialBookmarkKey) return@onSuccess

                    val nextBookmarked =
                        isBookmarked ||
                            currentDetail.isBookmarked ||
                            selectedFacilityBookmarkState.isBookmarked
                    selectedMapTapDetail = currentDetail.copy(isBookmarked = nextBookmarked)
                    selectedFacilityBookmarkState =
                        selectedFacilityBookmarkState.copy(
                            facilityId = initialBookmarkKey,
                            isBookmarked = nextBookmarked,
                            errorMessage = null,
                        )
                    renderSelectedFacilityState()
                }
        }
    }

    private fun hydrateDestinationPreviewDetail(
        previewRequest: DestinationPreviewRequest,
        requestId: Long,
    ) {
        if (previewRequest.detailType != MapPlaceDetailType.INTERNAL_PLACE) return

        val placeId = previewRequest.destination.placeId.takeIf(String::isNotBlank) ?: return
        val placesRepository = placesRepository ?: return

        viewModelScope.launch {
            runCatching { placesRepository.getPlaceDetail(placeId) }
                .onSuccess { placeDetail ->
                    if (requestId != mapTapDetailRequestId) return@onSuccess
                    if (selectedDestinationPreview?.requestId != previewRequest.requestId) return@onSuccess
                    if (placeDetail == null) return@onSuccess

                    val nextBookmarked =
                        placeDetail.isBookmarked ||
                            selectedMapTapDetail?.isBookmarked == true ||
                            selectedFacilityBookmarkState.isBookmarked
                    selectedMapTapDetail =
                        selectedMapTapDetail
                            ?.mergeInternalPreviewDetail(placeDetail)
                            ?.copy(isBookmarked = nextBookmarked)
                    selectedFacilityBookmarkState =
                        selectedFacilityBookmarkState.copy(
                            facilityId = selectedMapTapDetail?.bookmarkCacheKey(),
                            isBookmarked = nextBookmarked,
                        )
                    renderSelectedFacilityState()
                }
                .onFailure {
                    if (requestId != mapTapDetailRequestId) return@onFailure
                    if (selectedDestinationPreview?.requestId != previewRequest.requestId) return@onFailure
                }
        }
    }

    private fun observeLocationUpdates() {
        viewModelScope.launch {
            currentLocationManager.latestLocation.collectLatest { snapshot ->
                val hadLocation = latestLocation != null
                latestLocation = snapshot.toFreshCurrentLocationOrNull()
                val freshLocation = latestLocation

                if (freshLocation == null) {
                    isRecenterButtonActive = false
                    if (hadLocation && isRouteStarted) {
                        locationLookupState = LocationLookupState.TimedOut
                    } else if (latestPermissionState is LocationPermissionState.Granted && isRouteStarted) {
                        startLocationLookup(forceRestart = false)
                    }
                    applyFallbackCameraTarget()
                } else {
                    lastFreshLocationObservedAtEpochMillis = nowEpochMillis()
                    locationRecoveryRestartAttemptCount = 0
                    stopLocationLookup()
                    if (placesRepository != null && lastPlacesBrowseAnchorSource != PlacesBrowseAnchorSource.CURRENT_LOCATION) {
                        loadLivePlaceBrowseState(
                            anchorSource = PlacesBrowseAnchorSource.CURRENT_LOCATION,
                            force = true,
                            preserveSelection = true,
                        )
                    }
                    if (shouldSyncCameraToCurrentLocation()) {
                        val shouldIncrementRequestId =
                            !hadLocation ||
                                mutableUiState.value.cameraTarget.source != MapCameraSource.CURRENT_LOCATION ||
                                isRecenterButtonActive

                        syncCameraToCurrentLocation(
                            snapshot = freshLocation,
                            incrementRequestId = shouldIncrementRequestId,
                        )
                    }
                }

                renderUiState()
            }
        }
    }

    private fun handleGrantedPermission() {
        if (!isRouteStarted) return
        startLocationTracking(forceLookupRestart = false)
    }

    private fun handlePermissionBlocked() {
        isRecenterButtonActive = false
        stopLocationLookup()
        stopLocationRecoveryWatchdog()
        currentLocationManager.stopLocationUpdates()
        applyFallbackCameraTarget()
    }

    private fun handleLocationAction() {
        when (mutableUiState.value.recenterButtonState) {
            MapRecenterButtonState.REQUEST_PERMISSION ->
                emitUiEvent(MapUiEvent.RequestLocationPermission)

            MapRecenterButtonState.LOADING -> Unit
            MapRecenterButtonState.DISABLED -> Unit

            MapRecenterButtonState.RETRY -> retryLocationResolution()

            MapRecenterButtonState.ENABLED -> {
                val snapshot = latestLocation ?: return retryLocationResolution()
                isRecenterButtonActive = true
                syncCameraToCurrentLocation(
                    snapshot = snapshot,
                    incrementRequestId = true,
                )
                renderUiState()
            }
        }
    }

    private fun retryLocationResolution() {
        locationPermissionManager.refreshPermissionState()
        latestPermissionState = locationPermissionManager.permissionState.value

        when (latestPermissionState) {
            is LocationPermissionState.Granted -> startLocationTracking(forceLookupRestart = true)
            LocationPermissionState.Denied -> {
                isRecenterButtonActive = false
                emitUiEvent(MapUiEvent.RequestLocationPermission)
            }
            is LocationPermissionState.Unavailable -> {
                isRecenterButtonActive = false
                applyFallbackCameraTarget()
                renderUiState()
            }
        }
    }

    private fun handleZoomAction(delta: Int) {
        var nextZoomLevelForVisibility: Int? = null
        mutableUiState.update { state ->
            val currentZoomLevel = state.cameraTarget.resolvedZoomLevel()
            val nextZoomLevel =
                (currentZoomLevel + delta)
                    .coerceIn(KAKAO_MAP_MIN_ZOOM_LEVEL, KAKAO_MAP_MAX_ZOOM_LEVEL)
            if (nextZoomLevel == currentZoomLevel) {
                state
            } else {
                nextZoomLevelForVisibility = nextZoomLevel
                state.copy(
                    cameraTarget =
                        state.cameraTarget.copy(
                            requestId = state.cameraTarget.requestId + 1L,
                            zoomLevel = nextZoomLevel,
                        ),
                )
            }
        }
        nextZoomLevelForVisibility?.let(::updateApprovedReportVisibilityForZoom)
    }

    private fun handleViewportCameraChanged(
        center: MapCoordinate,
        zoomLevel: Int,
        isUserGesture: Boolean,
        isSelectedMapPinVisibleInViewport: Boolean?,
    ) {
        var ignoredStaleProgrammaticCallback = false
        var shouldLoadApprovedReportsForViewport = false
        val isRouteEndpointPickerActive = routeEndpointMapPickerState != null
        mutableUiState.update { state ->
            val currentTarget = state.cameraTarget
            val isAlignedWithRequestedCenter = currentTarget.center.isApproximatelySameCoordinate(center)

            // Ignore stale programmatic move-end callbacks that arrive after a newer camera target won.
            if (!isUserGesture && !isAlignedWithRequestedCenter) {
                ignoredStaleProgrammaticCallback = true
                return@update state
            }
            val hasCameraChanged =
                !isAlignedWithRequestedCenter ||
                    currentTarget.resolvedZoomLevel() != zoomLevel

            if (!hasCameraChanged) {
                state
            } else {
                shouldLoadApprovedReportsForViewport = isUserGesture && !isAlignedWithRequestedCenter
                if (isUserGesture) {
                    isRecenterButtonActive = false
                }
                state.copy(
                    cameraTarget =
                        currentTarget.copy(
                            center = if (isAlignedWithRequestedCenter) currentTarget.center else center,
                            zoomLevel = zoomLevel,
                        ),
                    isRecenterButtonActive = if (isUserGesture) false else state.isRecenterButtonActive,
                    isSearchHereVisible =
                        when {
                            isRouteEndpointPickerActive -> false
                            isUserGesture && placesRepository != null -> true
                            !isUserGesture -> false
                            else -> state.isSearchHereVisible
                        },
                )
            }
        }
        if (ignoredStaleProgrammaticCallback) return
        updateApprovedReportVisibilityForZoom(zoomLevel)
        if (shouldLoadApprovedReportsForViewport) {
            loadApprovedReportsForViewport()
        }

        if (routeEndpointMapPickerState != null) {
            updateRouteEndpointMapPickerCandidate(center)
            return
        }

        if (isUserGesture && isSelectedMapPinVisibleInViewport == false && compactOffscreenSelectedMapPinSheet()) {
            renderSelectedFacilityState()
        }
    }

    private fun startLocationTracking(forceLookupRestart: Boolean) {
        if (!isRouteStarted) return

        startLocationRecoveryWatchdog()
        currentLocationManager.startLocationUpdates()
        currentLocationManager.refreshLatestLocation()
        val latestRawLocation = currentLocationManager.latestLocation.value
        latestLocation = latestRawLocation.toFreshCurrentLocationOrNull()

        val snapshot = latestLocation
        if (snapshot == null) {
            if (latestRawLocation == null) {
                startLocationLookup(forceRestart = forceLookupRestart)
            } else {
                locationLookupState = LocationLookupState.TimedOut
            }
            applyFallbackCameraTarget()
            return
        }

        stopLocationLookup()
        if (shouldSyncCameraToCurrentLocation()) {
            syncCameraToCurrentLocation(
                snapshot = snapshot,
                incrementRequestId = mutableUiState.value.cameraTarget.source != MapCameraSource.CURRENT_LOCATION,
            )
        }
    }

    private fun startLocationRecoveryWatchdog() {
        if (!enableLocationRecoveryWatchdog) return
        if (locationRecoveryJob != null) return

        locationRecoveryJob =
            viewModelScope.launch {
                while (true) {
                    delay(LOCATION_RECOVERY_REFRESH_INTERVAL_MILLIS)
                    if (!isRouteStarted || latestPermissionState !is LocationPermissionState.Granted) continue

                    currentLocationManager.refreshLatestLocation()

                    val lastObservedAt = lastFreshLocationObservedAtEpochMillis ?: 0L
                    val missingDurationMillis = nowEpochMillis() - lastObservedAt
                    val restartThresholdMillis =
                        (
                            LOCATION_RECOVERY_RESTART_BASE_INTERVAL_MILLIS *
                                (locationRecoveryRestartAttemptCount + 1)
                        ).coerceAtMost(LOCATION_RECOVERY_RESTART_MAX_INTERVAL_MILLIS)

                    if (missingDurationMillis >= restartThresholdMillis) {
                        locationRecoveryRestartAttemptCount += 1
                        currentLocationManager.startLocationUpdates()
                        currentLocationManager.refreshLatestLocation()
                    }
                }
            }
    }

    private fun stopLocationRecoveryWatchdog() {
        locationRecoveryJob?.cancel()
        locationRecoveryJob = null
        locationRecoveryRestartAttemptCount = 0
    }

    private fun loadApprovedReportsForViewport(force: Boolean = false) {
        val cameraTarget = mutableUiState.value.cameraTarget
        val query =
            ApprovedReportMapQuery(
                center = cameraTarget.center.toGeoCoordinate(),
                radiusMeters = approvedReportQueryRadiusMeters(cameraTarget.resolvedZoomLevel()),
            )
        if (!force && query == lastApprovedReportQuery) return

        lastApprovedReportQuery = query
        val requestId = ++approvedReportRequestSequence

        viewModelScope.launch {
            val reports =
                runCatching {
                    approvedReportMapRepository.getApprovedReports(query)
                }.getOrDefault(emptyList())
                    .mapNotNull { entry -> entry.toApprovedReportMarkerDataOrNull() }

            if (requestId != approvedReportRequestSequence) return@launch

            mutableUiState.update { state ->
                val visibleReports =
                    if (shouldShowApprovedReportMarkers(state.cameraTarget.resolvedZoomLevel())) {
                        reports
                    } else {
                        emptyList()
                    }
                val selectedReportId =
                    state.approvedReportMarkerState.selectedReportId
                        ?.takeIf { reportId -> visibleReports.any { report -> report.reportId == reportId } }

                state.copy(
                    approvedReportMarkerState =
                        state.approvedReportMarkerState.copy(
                            reports = reports,
                            visibleReports = visibleReports,
                            selectedReportId = selectedReportId,
                        ),
                    approvedReportSheetState =
                        toApprovedReportSheetState(
                            reports = visibleReports,
                            selectedReportId = selectedReportId,
                        ),
                )
            }
        }
    }

    private fun updateApprovedReportVisibilityForZoom(zoomLevel: Int) {
        mutableUiState.update { state ->
            val visibleReports =
                if (shouldShowApprovedReportMarkers(zoomLevel)) {
                    state.approvedReportMarkerState.reports
                } else {
                    emptyList()
                }
            val selectedReportId =
                state.approvedReportMarkerState.selectedReportId
                    ?.takeIf { reportId -> visibleReports.any { report -> report.reportId == reportId } }

            state.copy(
                approvedReportMarkerState =
                    state.approvedReportMarkerState.copy(
                        visibleReports = visibleReports,
                        selectedReportId = selectedReportId,
                    ),
                approvedReportSheetState =
                    toApprovedReportSheetState(
                        reports = visibleReports,
                        selectedReportId = selectedReportId,
                    ),
            )
        }
    }

    private fun startLocationLookup(forceRestart: Boolean) {
        if (!isRouteStarted || latestPermissionState !is LocationPermissionState.Granted || latestLocation != null) {
            return
        }
        if (forceRestart && mutableUiState.value.cameraTarget.source == MapCameraSource.CURRENT_LOCATION) {
            locationLookupState = LocationLookupState.TimedOut
            return
        }
        if (!forceRestart && locationLookupState != LocationLookupState.Idle) return

        locationLookupTimeoutJob?.cancel()
        locationLookupState = LocationLookupState.Searching
        locationLookupTimeoutJob =
            viewModelScope.launch {
                delay(LOCATION_LOOKUP_TIMEOUT_MILLIS)
                if (
                    isRouteStarted &&
                    latestPermissionState is LocationPermissionState.Granted &&
                    latestLocation == null
                ) {
                    locationLookupState = LocationLookupState.TimedOut
                    renderUiState()
                }
            }
    }

    private fun stopLocationLookup() {
        locationLookupTimeoutJob?.cancel()
        locationLookupTimeoutJob = null
        locationLookupState = LocationLookupState.Idle
    }

    private fun shouldSyncCameraToCurrentLocation(): Boolean =
        when {
            mutableUiState.value.cameraTarget.source == MapCameraSource.CURRENT_LOCATION -> true
            selectedDestination != null -> false
            selectedDestinationPreview != null -> false
            else -> true
        }

    private fun syncCameraToCurrentLocation(
        snapshot: LocationSnapshot,
        incrementRequestId: Boolean,
    ) {
        val coordinate = snapshot.toMapCoordinate()

        mutableUiState.update { state ->
            val shouldIncrement =
                incrementRequestId || state.cameraTarget.source != MapCameraSource.CURRENT_LOCATION
            val nextRequestId =
                if (shouldIncrement) {
                    state.cameraTarget.requestId + 1L
                } else {
                    state.cameraTarget.requestId
                }
            val nextZoomLevel =
                if (shouldIncrement) {
                    MapCameraSource.CURRENT_LOCATION.defaultZoomLevel()
                } else {
                    state.cameraTarget.zoomLevel
                }

            state.copy(
                cameraTarget =
                    MapCameraTarget(
                        center = coordinate,
                        source = MapCameraSource.CURRENT_LOCATION,
                        requestId = nextRequestId,
                        zoomLevel = nextZoomLevel,
                    ),
                isSearchHereVisible = false,
            )
        }
    }

    private fun syncCameraToSelectedDestination(
        destination: PlaceDestination,
        incrementRequestId: Boolean,
    ) {
        val coordinate =
            com.ssafy.e102.eumgil.feature.map.model.MapCoordinate(
                latitude = destination.latitude,
                longitude = destination.longitude,
            )

        mutableUiState.update { state ->
            val shouldIncrement =
                incrementRequestId || state.cameraTarget.source != MapCameraSource.SEARCH_RESULT
            val nextRequestId =
                if (shouldIncrement) {
                    state.cameraTarget.requestId + 1L
                } else {
                    state.cameraTarget.requestId
                }
            val nextZoomLevel =
                if (shouldIncrement) {
                    MapCameraSource.SEARCH_RESULT.defaultZoomLevel()
                } else {
                    state.cameraTarget.zoomLevel
                }

            state.copy(
                cameraTarget =
                    MapCameraTarget(
                        center = coordinate,
                        source = MapCameraSource.SEARCH_RESULT,
                        requestId = nextRequestId,
                        zoomLevel = nextZoomLevel,
                    ),
                selectedDestination = destination,
                isSearchHereVisible = false,
            )
        }
    }

    private fun syncCameraToDestinationPreview(
        coordinate: MapCoordinate,
        incrementRequestId: Boolean,
    ) {
        mutableUiState.update { state ->
            val shouldIncrement =
                incrementRequestId ||
                    state.cameraTarget.source != MapCameraSource.SEARCH_RESULT ||
                    state.cameraTarget.center != coordinate
            val nextRequestId =
                if (shouldIncrement) {
                    state.cameraTarget.requestId + 1L
                } else {
                    state.cameraTarget.requestId
                }
            val nextZoomLevel =
                if (shouldIncrement) {
                    MapCameraSource.SEARCH_RESULT.defaultZoomLevel()
                } else {
                    state.cameraTarget.zoomLevel
                }

            state.copy(
                cameraTarget =
                    MapCameraTarget(
                        center = coordinate,
                        source = MapCameraSource.SEARCH_RESULT,
                        requestId = nextRequestId,
                        zoomLevel = nextZoomLevel,
                    ),
                isSearchHereVisible = false,
            )
        }
    }

    private fun applyFallbackCameraTarget() {
        selectedDestinationPreview?.let { preview ->
            syncCameraToDestinationPreview(
                coordinate =
                    MapCoordinate(
                        latitude = preview.destination.latitude,
                        longitude = preview.destination.longitude,
                    ),
                incrementRequestId = mutableUiState.value.cameraTarget.source != MapCameraSource.SEARCH_RESULT,
            )
            return
        }

        selectedDestination?.let { destination ->
            syncCameraToSelectedDestination(
                destination = destination,
                incrementRequestId = mutableUiState.value.cameraTarget.source != MapCameraSource.SEARCH_RESULT,
            )
            return
        }

        val snapshot = latestLocation
        if (snapshot != null && latestPermissionState is LocationPermissionState.Granted) {
            syncCameraToCurrentLocation(
                snapshot = snapshot,
                incrementRequestId = mutableUiState.value.cameraTarget.source != MapCameraSource.CURRENT_LOCATION,
            )
            return
        }

        if (shouldKeepCurrentLocationCameraWhileLocationRefreshes()) {
            return
        }

        applyDefaultCameraTarget()
    }

    private fun shouldKeepCurrentLocationCameraWhileLocationRefreshes(): Boolean =
        latestPermissionState is LocationPermissionState.Granted &&
            latestLocation == null &&
            mutableUiState.value.cameraTarget.source == MapCameraSource.CURRENT_LOCATION

    private fun applyDefaultCameraTarget() {
        mutableUiState.update { state ->
            if (
                state.cameraTarget.source == MapCameraSource.DEFAULT_BUSAN &&
                state.cameraTarget.center == MapDefaults.BUSAN_CENTER
            ) {
                state
            } else {
                state.copy(
                    cameraTarget =
                        MapCameraTarget(
                            center = MapDefaults.BUSAN_CENTER,
                            source = MapCameraSource.DEFAULT_BUSAN,
                            requestId = state.cameraTarget.requestId + 1L,
                            zoomLevel = MapCameraSource.DEFAULT_BUSAN.defaultZoomLevel(),
                        ),
                    isSearchHereVisible = false,
                )
            }
        }
    }

    private fun renderUiState() {
        val locationStatus =
            when (val permissionState = latestPermissionState) {
                is LocationPermissionState.Granted ->
                    latestLocation?.let { snapshot ->
                        MapLocationStatus.Ready(
                            location = snapshot.toMapCoordinate(),
                            accuracyMeters = snapshot.accuracyMeters,
                        )
                    } ?: if (locationLookupState == LocationLookupState.TimedOut) {
                        MapLocationStatus.Unavailable(
                            reason = MapLocationUnavailableReason.CURRENT_LOCATION_UNAVAILABLE,
                        )
                    } else {
                        MapLocationStatus.Loading
                    }

                LocationPermissionState.Denied -> MapLocationStatus.PermissionDenied

                is LocationPermissionState.Unavailable -> {
                    val reason =
                        when (permissionState.reason) {
                            PermissionUnavailableReason.LOCATION_SERVICES_DISABLED ->
                                MapLocationUnavailableReason.LOCATION_SERVICES_DISABLED

                            PermissionUnavailableReason.NO_LOCATION_FEATURE ->
                                MapLocationUnavailableReason.NO_LOCATION_FEATURE
                        }

                    MapLocationStatus.Unavailable(reason = reason)
                }
            }

        val recenterButtonState =
            when (locationStatus) {
                MapLocationStatus.PermissionDenied -> MapRecenterButtonState.REQUEST_PERMISSION
                MapLocationStatus.Loading -> MapRecenterButtonState.LOADING
                is MapLocationStatus.Ready -> MapRecenterButtonState.ENABLED
                is MapLocationStatus.Unavailable ->
                    when (locationStatus.reason) {
                        MapLocationUnavailableReason.NO_LOCATION_FEATURE -> MapRecenterButtonState.DISABLED
                        MapLocationUnavailableReason.CURRENT_LOCATION_UNAVAILABLE -> MapRecenterButtonState.RETRY
                        MapLocationUnavailableReason.LOCATION_SERVICES_DISABLED -> MapRecenterButtonState.RETRY
                    }
            }

        if (recenterButtonState != MapRecenterButtonState.ENABLED) {
            isRecenterButtonActive = false
        }

        mutableUiState.update { state ->
            state.copy(
                selectedOrigin = selectedOrigin,
                selectedDestination = selectedDestination,
                routeEditingTarget = routeEditingTarget,
                locationStatus = locationStatus,
                recenterButtonState = recenterButtonState,
                isRecenterButtonActive = isRecenterButtonActive,
                routeEndpointMapPickerState = routeEndpointMapPickerState,
            )
        }
    }

    private fun renderSelectedFacilityState() {
        mutableUiState.update { state ->
            state.copy(
                selectedMarkerId = selectedMarkerId,
                selectedMapPinCoordinate = selectedMapPinCoordinate,
                facilityDetailSheetState = currentFacilityDetailSheetState(),
            )
        }
    }

    private fun clearSelectedFacilitySelection(clearMapTapSelection: Boolean = true): Boolean {
        val hadSelection =
            selectedMarkerId != null ||
                selectedFacilityDetail != null ||
                selectedDestinationPreview != null ||
                (
                    clearMapTapSelection &&
                        (
                            selectedMapPinCoordinate != null ||
                                selectedMapTapDetail != null ||
                                isMapTapDetailLoading ||
                                mapTapDetailErrorMessage != null
                        )
                )
        if (!hadSelection) return false

        selectedMarkerId = null
        selectedFacilityDetail = null
        selectedFacilityBookmarkState = SelectedFacilityBookmarkState()
        selectedDestinationPreview = null
        facilityDetailSheetPresentation = MapFacilityDetailSheetPresentation.EXPANDED
        if (clearMapTapSelection) {
            clearMapTapSelectionState(clearPin = true)
        }
        return true
    }

    private fun compactOffscreenSelectedMapPinSheet(): Boolean {
        val hasSelectedMapPinState =
            selectedMapPinCoordinate != null ||
                selectedDestinationPreview != null ||
                selectedMapTapDetail != null ||
                isMapTapDetailLoading ||
                mapTapDetailErrorMessage != null
        if (!hasSelectedMapPinState) return false
        if (facilityDetailSheetPresentation == MapFacilityDetailSheetPresentation.COMPACT) return false

        facilityDetailSheetPresentation = MapFacilityDetailSheetPresentation.COMPACT
        return true
    }

    private fun clearMapTapSelectionState(clearPin: Boolean) {
        mapTapDetailLookupJob?.cancel()
        mapTapDetailLookupJob = null
        mapTapDetailRequestId += 1L
        if (clearPin) {
            selectedMapPinCoordinate = null
        }
        selectedMapTapDetail = null
        selectedMapTapNameHint = null
        selectedDestinationPreview = null
        facilityDetailSheetPresentation = MapFacilityDetailSheetPresentation.EXPANDED
        isMapTapDetailLoading = false
        mapTapDetailErrorMessage = null
    }

    private fun updateSelectedFacility(
        markerId: String?,
        detail: FacilityDetailSeed? = markerId?.let { facilityBrowseData?.detailFor(it) },
        loadBookmarkState: Boolean = true,
    ) {
        if (markerId != null && detail == null) {
            selectedMarkerId = null
            selectedFacilityDetail = null
            selectedFacilityBookmarkState = SelectedFacilityBookmarkState()
            return
        }

        val previousFacilityId = selectedFacilityDetail?.facilityId
        if (markerId != null) {
            clearMapTapSelectionState(clearPin = true)
            facilityDetailSheetPresentation = MapFacilityDetailSheetPresentation.EXPANDED
        }
        selectedMarkerId = markerId
        selectedFacilityDetail = detail
        if (detail == null) {
            selectedFacilityBookmarkState = SelectedFacilityBookmarkState()
        } else if (previousFacilityId != detail.facilityId) {
            selectedFacilityBookmarkState =
                if (loadBookmarkState) {
                    SelectedFacilityBookmarkState(
                        facilityId = detail.facilityId,
                        isUpdating = true,
                    )
                } else {
                    SelectedFacilityBookmarkState(
                        facilityId = detail.facilityId,
                    )
                }
            if (loadBookmarkState) {
                loadSelectedFacilityBookmarkState(detail)
            }
        }
    }

    private fun currentFacilityDetailSheetState(): MapFacilityDetailSheetState =
        MapFacilityDetailSheetState(
            detail = selectedFacilityDetail,
            mapTapDetail = selectedMapTapDetail,
            mapTapNameHint = selectedMapTapNameHint,
            destinationPreview = selectedDestinationPreview,
            presentation = facilityDetailSheetPresentation,
            isMapTapDetailLoading = isMapTapDetailLoading,
            mapTapDetailErrorMessage = mapTapDetailErrorMessage,
            isBookmarked = selectedFacilityBookmarkState.isBookmarked,
            isBookmarkUpdating = selectedFacilityBookmarkState.isUpdating,
            bookmarkErrorMessage = selectedFacilityBookmarkState.errorMessage,
        )

    private fun loadSelectedFacilityBookmarkState(detail: FacilityDetailSeed) {
        viewModelScope.launch {
            runCatching { bookmarkRepository.isBookmarked(detail.facilityId) }
                .onSuccess { isBookmarked ->
                    if (selectedFacilityDetail?.facilityId != detail.facilityId) return@onSuccess
                    selectedFacilityBookmarkState =
                        SelectedFacilityBookmarkState(
                            facilityId = detail.facilityId,
                            isBookmarked = isBookmarked,
                        )
                    renderSelectedFacilityState()
                }
                .onFailure {
                    if (selectedFacilityDetail?.facilityId != detail.facilityId) return@onFailure
                    selectedFacilityBookmarkState =
                        SelectedFacilityBookmarkState(
                            facilityId = detail.facilityId,
                            errorMessage = BOOKMARK_LOAD_ERROR_MESSAGE,
                        )
                    renderSelectedFacilityState()
                }
        }
    }

    private fun toggleSelectedFacilityBookmark() {
        selectedFacilityDetail?.let { detail ->
            toggleFacilityBookmark(detail)
            return
        }
        selectedMapTapDetail?.let { detail ->
            toggleMapTapBookmark(detail)
        }
    }

    private fun handleFacilityPhoneClicked() {
        val phoneNumber =
            selectedMapTapDetail?.phoneNumber?.takeIf(String::isNotBlank)
                ?: selectedFacilityDetail?.phoneNumber?.takeIf(String::isNotBlank)
                ?: return
        emitUiEvent(MapUiEvent.OpenDialer(phoneNumber))
    }

    private fun toggleFacilityBookmark(detail: FacilityDetailSeed) {
        val currentBookmarkState = selectedFacilityBookmarkState
        if (currentBookmarkState.isUpdating) return

        val nextBookmarked = !currentBookmarkState.isBookmarked
        selectedFacilityBookmarkState =
            currentBookmarkState.copy(
                isBookmarked = nextBookmarked,
                isUpdating = true,
                errorMessage = null,
            )
        renderSelectedFacilityState()

        viewModelScope.launch {
            runCatching {
                if (nextBookmarked) {
                    bookmarkRepository.saveBookmark(detail.toBookmarkData())
                } else {
                    bookmarkRepository.deleteBookmark(detail.facilityId)
                }
            }.onSuccess {
                if (selectedFacilityDetail?.facilityId != detail.facilityId) return@onSuccess
                selectedFacilityBookmarkState =
                    selectedFacilityBookmarkState.copy(
                        isBookmarked = nextBookmarked,
                        isUpdating = false,
                        errorMessage = null,
                    )
                renderSelectedFacilityState()
                if (!nextBookmarked) {
                    emitUiEvent(MapUiEvent.ShowSnackbar(BOOKMARK_DELETE_SUCCESS_MESSAGE))
                }
            }
            .onFailure {
                if (selectedFacilityDetail?.facilityId != detail.facilityId) return@onFailure
                selectedFacilityBookmarkState =
                    currentBookmarkState.copy(
                        isUpdating = false,
                        errorMessage = BOOKMARK_SAVE_FAILURE_MESSAGE,
                    )
                renderSelectedFacilityState()
                emitUiEvent(MapUiEvent.ShowSnackbar(BOOKMARK_SAVE_FAILURE_MESSAGE))
            }
        }
    }

    private fun toggleMapTapBookmark(detail: MapTappedPlaceDetail) {
        val currentBookmarkState = selectedFacilityBookmarkState
        if (currentBookmarkState.isUpdating) return

        val nextBookmarked = !currentBookmarkState.isBookmarked
        selectedFacilityBookmarkState =
            currentBookmarkState.copy(
                isBookmarked = nextBookmarked,
                isUpdating = true,
                errorMessage = null,
            )
        selectedMapTapDetail = selectedMapTapDetail?.copy(isBookmarked = nextBookmarked)
        renderSelectedFacilityState()

        viewModelScope.launch {
            runCatching {
                if (nextBookmarked) {
                    bookmarkRepository.saveBookmark(detail.toBookmarkData())
                } else {
                    bookmarkRepository.deleteBookmark(currentBookmarkState.facilityId ?: detail.bookmarkCacheKey())
                    null
                }
            }.onSuccess { savedBookmark ->
                if (selectedMapTapDetail?.matchesBookmarkTarget(detail) != true) return@onSuccess
                val updatedDetail =
                    if (nextBookmarked && savedBookmark != null) {
                        selectedMapTapDetail?.withSavedBookmark(savedBookmark)
                    } else {
                        selectedMapTapDetail?.copy(isBookmarked = false)
                    }
                selectedMapTapDetail = updatedDetail
                selectedFacilityBookmarkState =
                    selectedFacilityBookmarkState.copy(
                        facilityId = updatedDetail?.bookmarkCacheKey() ?: currentBookmarkState.facilityId,
                        isBookmarked = nextBookmarked,
                        isUpdating = false,
                        errorMessage = null,
                    )
                renderSelectedFacilityState()
                if (!nextBookmarked) {
                    emitUiEvent(MapUiEvent.ShowSnackbar(BOOKMARK_DELETE_SUCCESS_MESSAGE))
                }
            }.onFailure {
                if (selectedMapTapDetail?.matchesBookmarkTarget(detail) != true) return@onFailure
                selectedMapTapDetail = selectedMapTapDetail?.copy(isBookmarked = currentBookmarkState.isBookmarked)
                selectedFacilityBookmarkState =
                    currentBookmarkState.copy(
                        isUpdating = false,
                        errorMessage = BOOKMARK_SAVE_FAILURE_MESSAGE,
                    )
                renderSelectedFacilityState()
                emitUiEvent(MapUiEvent.ShowSnackbar(BOOKMARK_SAVE_FAILURE_MESSAGE))
            }
        }
    }

    private fun refreshRecentDestinations() {
        viewModelScope.launch {
            recentDestinations =
                runCatching {
                    searchRepository.getRecentDestinations()
                }.getOrDefault(emptyList())
            mutableUiState.update { state ->
                state.copy(
                    recentDestinations = recentDestinations.take(MAX_MAP_HOME_RECENT_DESTINATIONS),
                )
            }
        }
    }

    private fun createShortcutFilterState(
        browseData: FacilityBrowseData? = facilityBrowseData,
    ): MapShortcutFilterRowState {
        val availableCategories = browseData?.availableCategories?.toSet().orEmpty()
        val selection = markerFilterSelectionState

        return MapShortcutFilterRowState(
            chips =
                SHORTCUT_FILTER_ORDER.map { key ->
                    MapShortcutFilterChipState(
                        key = key,
                        isSelected = key.isSelected(selection = selection, availableCategories = availableCategories),
                        isEnabled = key.toFacilityCategory(availableCategories) != null || key.isSelected(selection),
                    )
                },
        )
    }

    private fun emitUiEvent(event: MapUiEvent) {
        mutableUiEvent.trySend(event)
    }

    private fun currentPlacesBrowseAnchor(): com.ssafy.e102.eumgil.feature.map.model.MapCoordinate =
        latestLocation?.toMapCoordinate() ?: mutableUiState.value.cameraTarget.center

    private fun currentPlacesBrowseAnchorSource(): PlacesBrowseAnchorSource =
        if (latestLocation != null) {
            PlacesBrowseAnchorSource.CURRENT_LOCATION
        } else {
            PlacesBrowseAnchorSource.FALLBACK
        }

    private fun LocationSnapshot?.toFreshCurrentLocationOrNull(): LocationSnapshot? =
        this?.takeIf { snapshot -> snapshot.isFreshCurrentLocation(nowEpochMillis = nowEpochMillis()) }

    private enum class LocationLookupState {
        Idle,
        Searching,
        TimedOut,
    }

    private enum class PlacesBrowseAnchorSource {
        FALLBACK,
        CURRENT_LOCATION,
        VIEWPORT,
    }

    companion object {
        private const val MAP_VIEW_MODEL_LOG_TAG = "MapViewModel"
        private val SHORTCUT_FILTER_ORDER =
            listOf(
                MapShortcutFilterKey.TOILET,
                MapShortcutFilterKey.ELEVATOR,
                MapShortcutFilterKey.CHARGING_STATION,
                MapShortcutFilterKey.FOOD_CAFE,
                MapShortcutFilterKey.TOURIST_SPOT,
                MapShortcutFilterKey.ACCOMMODATION,
                MapShortcutFilterKey.HEALTHCARE,
                MapShortcutFilterKey.WELFARE,
                MapShortcutFilterKey.PUBLIC_OFFICE,
            )
        private const val LOCATION_LOOKUP_TIMEOUT_MILLIS = 5_000L
        private const val LOCATION_RECOVERY_REFRESH_INTERVAL_MILLIS = 10_000L
        private const val LOCATION_RECOVERY_RESTART_BASE_INTERVAL_MILLIS = 30_000L
        private const val LOCATION_RECOVERY_RESTART_MAX_INTERVAL_MILLIS = 120_000L
        private const val MAP_BROWSE_RADIUS_METERS = 1_000
        private const val MAX_MAP_HOME_RECENT_DESTINATIONS = 10
        private const val BOOKMARK_LOAD_ERROR_MESSAGE = "북마크 상태를 확인하지 못했습니다."
        private const val BOOKMARK_DELETE_SUCCESS_MESSAGE = "북마크를 해제했습니다."
        private const val BOOKMARK_SAVE_FAILURE_MESSAGE = "북마크 저장에 실패했습니다. 다시 시도해 주세요."
        private const val SHORTCUT_FILTER_UNAVAILABLE_MESSAGE = "근처에 해당 장소가 없어요"

        private const val MAP_TAP_DETAIL_LOAD_FAILURE_MESSAGE = "선택한 위치의 상세 정보를 불러오지 못했습니다."
        private const val MAP_TAP_DETAIL_EMPTY_MESSAGE = "선택한 위치의 상세 정보가 없습니다."

        fun provideFactory(
            locationPermissionManager: LocationPermissionManager,
            currentLocationManager: CurrentLocationManager,
            destinationSelectionRepository: DestinationSelectionRepository,
            destinationPreviewRepository: DestinationPreviewRepository,
            facilitySeedRepository: FacilitySeedRepository,
            bookmarkRepository: BookmarkRepository,
            authSessionRepository: AuthSessionRepository? = null,
            searchRepository: SearchRepository,
            placesRepository: PlacesRepository? = null,
            approvedReportMapRepository: ApprovedReportMapRepository = EmptyApprovedReportMapRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
                        return MapViewModel(
                            locationPermissionManager = locationPermissionManager,
                            currentLocationManager = currentLocationManager,
                            destinationSelectionRepository = destinationSelectionRepository,
                            destinationPreviewRepository = destinationPreviewRepository,
                            facilitySeedRepository = facilitySeedRepository,
                            bookmarkRepository = bookmarkRepository,
                            authSessionRepository = authSessionRepository,
                            searchRepository = searchRepository,
                            placesRepository = placesRepository,
                            enableLocationRecoveryWatchdog = true,
                            approvedReportMapRepository = approvedReportMapRepository,
                        ) as T
                    }

                    error("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}

private fun Double.toLogCoordinate(): String = String.format(Locale.US, "%.6f", this)

private fun MapCoordinate.toGeoCoordinate(): GeoCoordinate =
    GeoCoordinate(latitude = latitude, longitude = longitude)

private fun approvedReportQueryRadiusMeters(zoomLevel: Int): Int =
    when {
        zoomLevel <= 12 -> 12_000
        zoomLevel <= 15 -> 5_000
        else -> 2_000
    }

private fun safeLogDebug(
    tag: String,
    message: String,
) {
    runCatching { Log.d(tag, message) }
}

private fun safeLogInfo(
    tag: String,
    message: String,
) {
    runCatching { Log.i(tag, message) }
}

private fun safeLogWarn(
    tag: String,
    message: String,
) {
    runCatching { Log.w(tag, message) }
}

private fun safeLogError(
    tag: String,
    message: String,
    throwable: Throwable,
) {
    runCatching { Log.e(tag, message, throwable) }
}

private object NoOpSearchRepository : SearchRepository {
    override suspend fun search(query: com.ssafy.e102.eumgil.core.model.SearchQuery) = emptyList<com.ssafy.e102.eumgil.core.model.SearchResult>()

    override suspend fun getRecentSearches() = emptyList<com.ssafy.e102.eumgil.core.model.RecentSearch>()

    override suspend fun saveRecentSearch(keyword: String) = Unit

    override suspend fun getRecentDestinations(): List<RecentDestination> = emptyList()

    override suspend fun saveRecentDestination(destination: RecentDestination) = Unit
}

private fun MapTapPayload.toMapPlaceDetailRequest(): MapPlaceDetailRequest =
    MapPlaceDetailRequest(
        latitude = coordinate.latitude,
        longitude = coordinate.longitude,
        clickType =
            when (clickType) {
                MapTapClickType.POI -> MapPlaceClickType.POI
                MapTapClickType.ADDRESS -> MapPlaceClickType.ADDRESS
            },
        provider = provider,
        providerPlaceId = providerPlaceId,
        nameHint = nameHint,
    )

private fun MapTapPayload.toRouteEndpointPickerMapTapDetail(): MapTappedPlaceDetail {
    val providerValue = provider?.takeIf { it.isNotBlank() }
    val providerPlaceIdValue = providerPlaceId?.takeIf { it.isNotBlank() }
    val fallbackName = nameHint?.takeIf { it.isNotBlank() } ?: ROUTE_ENDPOINT_PICKER_DEFAULT_PLACE_NAME
    val latitudeText = coordinate.latitude.toLogCoordinate()
    val longitudeText = coordinate.longitude.toLogCoordinate()
    val coordinateAddress = "$latitudeText, $longitudeText"
    val fallbackTargetId =
        providerPlaceIdValue
            ?.let { id -> "provider:${providerValue.orEmpty().lowercase(Locale.US)}:$id" }
            ?: "map:$latitudeText,$longitudeText"

    return MapTappedPlaceDetail(
        bookmarkTargetId = fallbackTargetId,
        detailType =
            when (clickType) {
                MapTapClickType.POI -> MapPlaceDetailType.EXTERNAL_POI
                MapTapClickType.ADDRESS -> MapPlaceDetailType.EXTERNAL_ADDRESS
            },
        placeId = null,
        provider = providerValue,
        providerPlaceId = providerPlaceIdValue,
        name = fallbackName,
        category = null,
        providerCategory = null,
        address = "$ROUTE_ENDPOINT_PICKER_COORDINATE_LABEL $coordinateAddress",
        latitude = coordinate.latitude,
        longitude = coordinate.longitude,
    )
}

private fun DestinationPreviewRequest.toMapTappedPlaceDetail(): MapTappedPlaceDetail =
    MapTappedPlaceDetail(
        bookmarkTargetId =
            bookmarkTargetId
                ?.takeIf { it.isNotBlank() }
                ?: destination.placeId.takeIf { detailType == MapPlaceDetailType.INTERNAL_PLACE }
                ?: "",
        detailType = detailType,
        placeId = destination.placeId.takeIf { detailType == MapPlaceDetailType.INTERNAL_PLACE },
        provider = provider,
        providerPlaceId = providerPlaceId,
        name = destination.name,
        category = destination.category,
        providerCategory = destination.providerCategory,
        address = destination.address.orEmpty(),
        latitude = destination.latitude,
        longitude = destination.longitude,
        accessibilityTags = accessibilityTagKeys,
    )

private fun MapTappedPlaceDetail.mergeInternalPreviewDetail(detail: PlaceDetail): MapTappedPlaceDetail =
    copy(
        bookmarkTargetId = bookmarkTargetId.ifBlank { detail.placeId },
        placeId = detail.placeId,
        name = detail.name,
        category = detail.category,
        address = detail.address,
        features = detail.features,
        isBookmarked = detail.isBookmarked,
        accessibilityTags =
            detail.accessibilityTags.takeIf { detailAccessibilityTags -> detailAccessibilityTags.isNotEmpty() }
                ?: accessibilityTags,
        phoneNumber = detail.phoneNumber ?: phoneNumber,
        description = detail.description ?: description,
    )

private fun MapTappedPlaceDetail.toBookmarkData(): BookmarkData {
    val serverPlaceId = placeId?.toLongOrNull()
    val localPlaceId =
        serverPlaceId?.toString()
            ?: bookmarkTargetId.takeIf { it.isNotBlank() }
            ?: externalBookmarkFallbackKey()

    return BookmarkData(
        placeId = localPlaceId,
        placeName = name,
        address = address.takeIf { it.isNotBlank() },
        latitude = latitude,
        longitude = longitude,
        category = category?.name,
        bookmarkTargetId = bookmarkTargetId.takeIf { it.isNotBlank() },
        targetType = detailType.name,
        serverPlaceId = serverPlaceId,
        provider =
            provider?.takeIf { it.isNotBlank() }
                ?: DEFAULT_EXTERNAL_BOOKMARK_PROVIDER.takeIf { detailType != MapPlaceDetailType.INTERNAL_PLACE },
        providerPlaceId = providerPlaceId?.takeIf { it.isNotBlank() },
        providerCategory = providerCategory?.takeIf { it.isNotBlank() },
    )
}

private fun MapTappedPlaceDetail.withSavedBookmark(bookmark: BookmarkData): MapTappedPlaceDetail =
    copy(
        bookmarkTargetId = bookmark.bookmarkTargetId ?: bookmarkTargetId,
        placeId = bookmark.serverPlaceId?.toString() ?: placeId,
        isBookmarked = true,
    )

private fun MapTappedPlaceDetail.bookmarkCacheKey(): String =
    placeId?.takeIf { it.isNotBlank() }
        ?: bookmarkTargetId.takeIf { it.isNotBlank() }
        ?: externalBookmarkFallbackKey()

private fun MapTappedPlaceDetail.externalBookmarkFallbackKey(): String =
    providerPlaceId
        ?.takeIf { it.isNotBlank() }
        ?.let { providerPlaceId -> "provider:${provider.orEmpty().lowercase(Locale.US)}:$providerPlaceId" }
        ?: "map:${latitude.toLogCoordinate()},${longitude.toLogCoordinate()}"

private fun MapTappedPlaceDetail.matchesBookmarkTarget(other: MapTappedPlaceDetail): Boolean =
    (bookmarkTargetId.isNotBlank() && bookmarkTargetId == other.bookmarkTargetId) ||
        (!placeId.isNullOrBlank() && placeId == other.placeId) ||
        (!providerPlaceId.isNullOrBlank() && providerPlaceId == other.providerPlaceId) ||
        externalBookmarkFallbackKey() == other.externalBookmarkFallbackKey()

private fun MapTappedPlaceDetail.withFallbackCoordinate(
    fallbackCoordinate: MapCoordinate,
): MapTappedPlaceDetail =
    if (latitude.isValidLatitude() && longitude.isValidLongitude()) {
        this
    } else {
        copy(
            latitude = fallbackCoordinate.latitude,
            longitude = fallbackCoordinate.longitude,
        )
    }

private fun MapTappedPlaceDetail.toPlaceDestinationOrNull(): PlaceDestination? {
    if (!latitude.isValidLatitude() || !longitude.isValidLongitude()) return null

    return PlaceDestination(
        placeId = bookmarkCacheKey(),
        name = name,
        address = address.takeIf { it.isNotBlank() },
        latitude = latitude,
        longitude = longitude,
        category = category,
        serverPlaceId = placeId?.toLongOrNull(),
        provider = provider?.takeIf { it.isNotBlank() },
        providerPlaceId = providerPlaceId?.takeIf { it.isNotBlank() },
        providerCategory = providerCategory?.takeIf { it.isNotBlank() },
    )
}

private fun Double.isValidLatitude(): Boolean = isFinite() && this in -90.0..90.0

private fun Double.isValidLongitude(): Boolean = isFinite() && this in -180.0..180.0

private fun MapCoordinate.isApproximatelySameCoordinate(
    other: MapCoordinate,
    tolerance: Double = CAMERA_CALLBACK_COORDINATE_TOLERANCE,
): Boolean = abs(latitude - other.latitude) <= tolerance && abs(longitude - other.longitude) <= tolerance

private data class SelectedFacilityBookmarkState(
    val facilityId: String? = null,
    val isBookmarked: Boolean = false,
    val isUpdating: Boolean = false,
    val errorMessage: String? = null,
)

private fun MapShortcutFilterKey.toFacilityCategory(availableCategories: Set<FacilityCategory>): FacilityCategory? =
    when (this) {
        MapShortcutFilterKey.TOILET -> FacilityCategory.TOILET.takeIf(availableCategories::contains)
        MapShortcutFilterKey.ELEVATOR -> FacilityCategory.ELEVATOR.takeIf(availableCategories::contains)
        MapShortcutFilterKey.CHARGING_STATION -> FacilityCategory.CHARGING_STATION.takeIf(availableCategories::contains)
        MapShortcutFilterKey.FOOD_CAFE ->
            when {
                FacilityCategory.FOOD_CAFE in availableCategories -> FacilityCategory.FOOD_CAFE
                FacilityCategory.RESTAURANT in availableCategories -> FacilityCategory.RESTAURANT
                else -> null
            }

        MapShortcutFilterKey.TOURIST_SPOT ->
            when {
                FacilityCategory.TOURIST_SPOT in availableCategories -> FacilityCategory.TOURIST_SPOT
                FacilityCategory.TOURIST_ATTRACTION in availableCategories -> FacilityCategory.TOURIST_ATTRACTION
                else -> null
            }

        MapShortcutFilterKey.ACCOMMODATION -> FacilityCategory.ACCOMMODATION.takeIf(availableCategories::contains)
        MapShortcutFilterKey.HEALTHCARE -> FacilityCategory.HEALTHCARE.takeIf(availableCategories::contains)
        MapShortcutFilterKey.WELFARE -> FacilityCategory.WELFARE.takeIf(availableCategories::contains)
        MapShortcutFilterKey.PUBLIC_OFFICE -> FacilityCategory.PUBLIC_OFFICE.takeIf(availableCategories::contains)
    }

private fun MapShortcutFilterKey.isSelected(
    selection: MapFilterSelectionState,
    availableCategories: Set<FacilityCategory>,
): Boolean {
    if (selection.isShowingAllCategories) return false
    val category = toFacilityCategory(availableCategories)
    return if (category != null) {
        category in selection.selectedFacilityCategories
    } else {
        isSelected(selection)
    }
}

private fun MapShortcutFilterKey.isSelected(selection: MapFilterSelectionState): Boolean {
    if (selection.isShowingAllCategories) return false
    return targetFacilityCategories().any { category -> category in selection.selectedFacilityCategories }
}

private fun MapShortcutFilterKey.targetFacilityCategories(): Set<FacilityCategory> =
    when (this) {
        MapShortcutFilterKey.TOILET -> setOf(FacilityCategory.TOILET)
        MapShortcutFilterKey.ELEVATOR -> setOf(FacilityCategory.ELEVATOR)
        MapShortcutFilterKey.CHARGING_STATION -> setOf(FacilityCategory.CHARGING_STATION)
        MapShortcutFilterKey.FOOD_CAFE -> setOf(FacilityCategory.FOOD_CAFE, FacilityCategory.RESTAURANT)
        MapShortcutFilterKey.TOURIST_SPOT -> setOf(FacilityCategory.TOURIST_SPOT, FacilityCategory.TOURIST_ATTRACTION)
        MapShortcutFilterKey.ACCOMMODATION -> setOf(FacilityCategory.ACCOMMODATION)
        MapShortcutFilterKey.HEALTHCARE -> setOf(FacilityCategory.HEALTHCARE)
        MapShortcutFilterKey.WELFARE -> setOf(FacilityCategory.WELFARE)
        MapShortcutFilterKey.PUBLIC_OFFICE -> setOf(FacilityCategory.PUBLIC_OFFICE)
    }

private const val ROUTE_ENDPOINT_PICKER_DEFAULT_PLACE_NAME = "선택한 위치"
private const val ROUTE_ENDPOINT_PICKER_COORDINATE_LABEL = "좌표"
private const val CAMERA_CALLBACK_COORDINATE_TOLERANCE = 0.00001
private const val DEFAULT_EXTERNAL_BOOKMARK_PROVIDER = "KAKAO"
