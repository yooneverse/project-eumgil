package com.ssafy.e102.eumgil.feature.savedroute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ssafy.e102.eumgil.core.location.CurrentLocationManager
import com.ssafy.e102.eumgil.core.location.LocationSnapshot
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.MapPlaceDetailType
import com.ssafy.e102.eumgil.core.model.RouteBookmark
import com.ssafy.e102.eumgil.core.model.RouteBookmarkDetail
import com.ssafy.e102.eumgil.core.model.RouteSearchSource
import com.ssafy.e102.eumgil.core.model.RouteWaypoint
import com.ssafy.e102.eumgil.core.model.hasValidCoordinate
import com.ssafy.e102.eumgil.core.model.PlaceCategory
import com.ssafy.e102.eumgil.core.model.PlaceDestination
import com.ssafy.e102.eumgil.data.repository.AuthSessionRepository
import com.ssafy.e102.eumgil.data.repository.BookmarkData
import com.ssafy.e102.eumgil.data.repository.BookmarkRepository
import com.ssafy.e102.eumgil.data.repository.DestinationPreviewRepository
import com.ssafy.e102.eumgil.data.repository.DestinationSelectionRepository
import com.ssafy.e102.eumgil.data.repository.NoOpDestinationPreviewRepository
import com.ssafy.e102.eumgil.data.repository.RouteBookmarkRepository
import com.ssafy.e102.eumgil.data.repository.RouteEditingTarget
import com.ssafy.e102.eumgil.data.repository.SearchRepository
import com.ssafy.e102.eumgil.data.repository.observeAccountScopeKey
import com.ssafy.e102.eumgil.feature.route.RouteNavigationRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class SavedRouteViewModel(
    private val authSessionRepository: AuthSessionRepository? = null,
    private val bookmarkRepository: BookmarkRepository,
    private val routeBookmarkRepository: RouteBookmarkRepository,
    private val destinationSelectionRepository: DestinationSelectionRepository,
    private val destinationPreviewRepository: DestinationPreviewRepository = NoOpDestinationPreviewRepository,
    private val searchRepository: SearchRepository? = null,
    private val currentLocationManager: CurrentLocationManager? = null,
    initialLowVisionMode: Boolean = false,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SavedRouteUiState())
    val uiState: StateFlow<SavedRouteUiState> = mutableUiState.asStateFlow()

    private val mutableUiEvent = MutableSharedFlow<SavedRouteUiEvent>()
    val uiEvent: SharedFlow<SavedRouteUiEvent> = mutableUiEvent.asSharedFlow()

    private var latestPlaces: List<SavedPlaceUiModel> = emptyList()
    private var latestPlaceBookmarks: List<BookmarkData> = emptyList()
    private var latestRoutes: List<SavedRouteBookmarkUiModel> = emptyList()
    private var latestLocationCoordinate: GeoCoordinate? = null
    private var isLowVisionMode: Boolean = initialLowVisionMode
    private var lastPublishedLowVisionPlaceLocationCoordinate: GeoCoordinate? = null
    private var lastPublishedLowVisionPlaceIds: List<String> = emptyList()
    private var observePlacesJob: Job? = null
    private var observeRoutesJob: Job? = null

    init {
        observeCurrentLocation()
        if (authSessionRepository == null) {
            observePlaceBookmarks()
            observeRouteBookmarks()
        } else {
            observeAccountScope()
        }
    }

    fun setLowVisionMode(enabled: Boolean) {
        if (isLowVisionMode == enabled) return
        isLowVisionMode = enabled
        if (enabled) {
            currentLocationManager?.startLocationUpdates()
            currentLocationManager?.refreshLatestLocation()
        } else {
            currentLocationManager?.stopLocationUpdates()
            lastPublishedLowVisionPlaceLocationCoordinate = null
            lastPublishedLowVisionPlaceIds = emptyList()
        }
        if (
            latestPlaceBookmarks.isNotEmpty() ||
            mutableUiState.value.placeContent.screenState != SavedBookmarkContentState.LOADING
        ) {
            publishPlaceBookmarks(clearErrorMessage = false)
        }
    }

    fun onAction(action: SavedRouteUiAction) {
        when (action) {
            is SavedRouteUiAction.TabSelected -> {
                mutableUiState.update { state ->
                    state.copy(selectedTab = action.tab)
                }
            }
            SavedRouteUiAction.EditClicked -> enterEditMode()
            SavedRouteUiAction.EditDoneClicked -> exitEditMode()
            SavedRouteUiAction.DeleteSelectedClicked -> applyPendingRemovals()
            SavedRouteUiAction.ExploreMapClicked -> emitUiEvent(SavedRouteUiEvent.NavigateToMap)
            SavedRouteUiAction.RouteSettingClicked -> emitUiEvent(SavedRouteUiEvent.NavigateToRouteSetting())
            SavedRouteUiAction.RetryClicked -> retryCurrentTab()
            is SavedRouteUiAction.SortOrderSelected -> selectSortOrder(action.sortOrder)
            is SavedRouteUiAction.PlaceClicked -> previewPlace(action.placeId)
            is SavedRouteUiAction.PlaceRouteGuideClicked ->
                handoffPlace(
                    placeId = action.placeId,
                    event = SavedRouteUiEvent.NavigateToRouteSetting(),
                )
            is SavedRouteUiAction.PlaceBriefingClicked ->
                handoffPlace(
                    placeId = action.placeId,
                    event = SavedRouteUiEvent.NavigateToRouteBriefing,
                )
            is SavedRouteUiAction.PlaceDeleteClicked -> togglePlaceRemoval(action.placeId)
            is SavedRouteUiAction.PlaceRemoveClicked -> removePlaceBookmark(action.placeId)
            is SavedRouteUiAction.RouteClicked -> openRouteBookmarkDetail(action.bookmarkId)
            is SavedRouteUiAction.RouteGuideClicked -> handoffRouteBookmark(action.bookmarkId)
            is SavedRouteUiAction.RouteDeleteClicked -> toggleRouteRemoval(action.bookmarkId)
            is SavedRouteUiAction.RouteRemoveClicked -> removeRouteBookmark(action.bookmarkId)
        }
    }

    private fun enterEditMode() {
        mutableUiState.update { state ->
            if (state.isApplyingEditChanges) {
                state
            } else {
                state.copy(isEditMode = true)
            }
        }
    }

    private fun exitEditMode() {
        mutableUiState.update { state ->
            if (state.isApplyingEditChanges) {
                state
            } else {
                state.copy(
                    isEditMode = false,
                    pendingPlaceRemovalIds = emptySet(),
                    pendingRouteRemovalIds = emptySet(),
                )
            }
        }
    }

    private fun togglePlaceRemoval(placeId: String) {
        mutableUiState.update { state ->
            if (!state.isEditMode || state.isApplyingEditChanges) {
                state
            } else {
                state.copy(
                    pendingPlaceRemovalIds = state.pendingPlaceRemovalIds.toggled(placeId),
                    placeContent = state.placeContent.copy(errorMessage = null),
                )
            }
        }
    }

    private fun toggleRouteRemoval(bookmarkId: String) {
        mutableUiState.update { state ->
            if (!state.isEditMode || state.isApplyingEditChanges) {
                state
            } else {
                state.copy(
                    pendingRouteRemovalIds = state.pendingRouteRemovalIds.toggled(bookmarkId),
                    routeContent = state.routeContent.copy(errorMessage = null),
                )
            }
        }
    }

    private fun applyPendingRemovals() {
        val currentState = uiState.value
        if (!currentState.isEditMode || currentState.isApplyingEditChanges) return

        val pendingPlaceRemovalIds = currentState.pendingPlaceRemovalIds
        val pendingRouteRemovalIds = currentState.pendingRouteRemovalIds
        if (pendingPlaceRemovalIds.isEmpty() && pendingRouteRemovalIds.isEmpty()) {
            mutableUiState.update { state ->
                state.copy(isEditMode = false)
            }
            return
        }

        mutableUiState.update { state ->
            state.copy(isApplyingEditChanges = true)
        }

        viewModelScope.launch {
            val failedPlaceRemovalIds =
                pendingPlaceRemovalIds.filterTo(mutableSetOf()) { placeId ->
                    runCatching { bookmarkRepository.deleteBookmark(placeId) }.isFailure
                }
            val failedRouteRemovalIds =
                pendingRouteRemovalIds.filterTo(mutableSetOf()) { bookmarkId ->
                    runCatching { routeBookmarkRepository.deleteRouteBookmark(bookmarkId) }.isFailure
                }

            mutableUiState.update { state ->
                state.copy(
                    isEditMode = failedPlaceRemovalIds.isNotEmpty() || failedRouteRemovalIds.isNotEmpty(),
                    isApplyingEditChanges = false,
                    pendingPlaceRemovalIds = failedPlaceRemovalIds,
                    pendingRouteRemovalIds = failedRouteRemovalIds,
                    placeContent =
                        state.placeContent.copy(
                            errorMessage =
                                when {
                                    failedPlaceRemovalIds.isNotEmpty() -> PLACE_BOOKMARK_REMOVE_FAILURE_MESSAGE
                                    pendingPlaceRemovalIds.isNotEmpty() -> null
                                    else -> state.placeContent.errorMessage
                                },
                        ),
                    routeContent =
                        state.routeContent.copy(
                            errorMessage =
                                when {
                                    failedRouteRemovalIds.isNotEmpty() -> ROUTE_BOOKMARK_REMOVE_FAILURE_MESSAGE
                                    pendingRouteRemovalIds.isNotEmpty() -> null
                                    else -> state.routeContent.errorMessage
                                },
                        ),
                )
            }

            when {
                failedPlaceRemovalIds.isEmpty() && failedRouteRemovalIds.isEmpty() ->
                    emitUiEvent(SavedRouteUiEvent.ShowSnackbar(BOOKMARK_REMOVE_SUCCESS_MESSAGE))
                else ->
                    emitUiEvent(SavedRouteUiEvent.ShowSnackbar(BOOKMARK_REMOVE_FAILURE_MESSAGE))
            }
        }
    }

    private fun retryCurrentTab() {
        when (uiState.value.selectedTab) {
            SavedBookmarkTab.PLACE -> observePlaceBookmarks()
            SavedBookmarkTab.ROUTE -> observeRouteBookmarks()
        }
    }

    private fun selectSortOrder(sortOrder: SavedBookmarkSortOrder) {
        mutableUiState.update { state ->
            when (state.selectedTab) {
                SavedBookmarkTab.PLACE ->
                    state.copy(
                        placeSortOrder = sortOrder,
                        placeContent = state.placeContent.copy(places = sortedPlaceBookmarks(sortOrder)),
                    )
                SavedBookmarkTab.ROUTE ->
                    state.copy(
                        routeSortOrder = sortOrder,
                        routeContent = state.routeContent.copy(routes = sortedRouteBookmarks(sortOrder)),
                    )
            }
        }
    }

    private fun observeAccountScope() {
        viewModelScope.launch {
            authSessionRepository
                ?.observeAccountScopeKey()
                ?.collectLatest {
                    latestPlaces = emptyList()
                    latestRoutes = emptyList()
                    mutableUiState.update { state ->
                        SavedRouteUiState(selectedTab = state.selectedTab)
                    }
                    observePlaceBookmarks()
                    observeRouteBookmarks()
                }
        }
    }

    private fun observePlaceBookmarks() {
        observePlacesJob?.cancel()
        mutableUiState.update { state ->
            state.copy(
                placeContent =
                    state.placeContent.copy(
                        screenState = SavedBookmarkContentState.LOADING,
                        errorMessage = null,
                    ),
            )
        }
        if (isLowVisionMode) {
            currentLocationManager?.startLocationUpdates()
            currentLocationManager?.refreshLatestLocation()
        }
        observePlacesJob =
            viewModelScope.launch {
                bookmarkRepository.observeBookmarks()
                    .catch {
                        mutableUiState.update { state ->
                            state.copy(
                                placeContent =
                                    state.placeContent.copy(
                                        screenState =
                                            if (latestPlaces.isEmpty()) {
                                                SavedBookmarkContentState.ERROR
                                            } else {
                                                SavedBookmarkContentState.CONTENT
                                            },
                                        places = latestPlaces,
                                        errorMessage = PLACE_BOOKMARK_LOAD_FAILURE_MESSAGE,
                                    ),
                            )
                        }
                    }
                    .collectLatest { bookmarks ->
                        latestPlaceBookmarks = bookmarks
                        publishPlaceBookmarks(clearErrorMessage = true)
                    }
            }
    }

    private fun observeRouteBookmarks() {
        observeRoutesJob?.cancel()
        mutableUiState.update { state ->
            state.copy(
                routeContent =
                    state.routeContent.copy(
                        screenState = SavedBookmarkContentState.LOADING,
                        errorMessage = null,
                    ),
            )
        }
        observeRoutesJob =
            viewModelScope.launch {
                routeBookmarkRepository.observeRouteBookmarks()
                    .catch {
                        mutableUiState.update { state ->
                            state.copy(
                                routeContent =
                                    state.routeContent.copy(
                                        screenState =
                                            if (latestRoutes.isEmpty()) {
                                                SavedBookmarkContentState.ERROR
                                            } else {
                                                SavedBookmarkContentState.CONTENT
                                            },
                                        routes = sortedRouteBookmarks(mutableUiState.value.routeSortOrder),
                                        errorMessage = ROUTE_BOOKMARK_LOAD_FAILURE_MESSAGE,
                                    ),
                            )
                        }
                    }
                    .collectLatest { routeBookmarks ->
                        latestRoutes = routeBookmarks.map(RouteBookmark::toSavedRouteBookmarkUiModel)
                        mutableUiState.update { state ->
                            state.copy(
                                routeContent =
                                    state.routeContent.copy(
                                        screenState =
                                            if (latestRoutes.isEmpty()) {
                                                SavedBookmarkContentState.EMPTY
                                            } else {
                                                SavedBookmarkContentState.CONTENT
                                            },
                                        routes = sortedRouteBookmarks(state.routeSortOrder),
                                        errorMessage = null,
                                    ),
                            )
                        }
                    }
            }
    }

    private fun removePlaceBookmark(placeId: String) {
        viewModelScope.launch {
            runCatching {
                bookmarkRepository.deleteBookmark(placeId)
            }.onSuccess {
                emitUiEvent(SavedRouteUiEvent.ShowSnackbar(PLACE_BOOKMARK_REMOVE_SUCCESS_MESSAGE))
            }.onFailure {
                mutableUiState.update { state ->
                    state.copy(
                        placeContent =
                            state.placeContent.copy(
                                errorMessage = PLACE_BOOKMARK_REMOVE_FAILURE_MESSAGE,
                            ),
                    )
                }
                emitUiEvent(SavedRouteUiEvent.ShowSnackbar(PLACE_BOOKMARK_REMOVE_FAILURE_MESSAGE))
            }
        }
    }

    private fun removeRouteBookmark(bookmarkId: String) {
        viewModelScope.launch {
            runCatching {
                routeBookmarkRepository.deleteRouteBookmark(bookmarkId)
            }.onSuccess {
                emitUiEvent(SavedRouteUiEvent.ShowSnackbar(ROUTE_BOOKMARK_REMOVE_SUCCESS_MESSAGE))
            }.onFailure {
                mutableUiState.update { state ->
                    state.copy(
                        routeContent =
                            state.routeContent.copy(
                                errorMessage = ROUTE_BOOKMARK_REMOVE_FAILURE_MESSAGE,
                            ),
                    )
                }
                emitUiEvent(SavedRouteUiEvent.ShowSnackbar(ROUTE_BOOKMARK_REMOVE_FAILURE_MESSAGE))
            }
        }
    }

    private fun handoffPlace(
        placeId: String,
        event: SavedRouteUiEvent,
    ) {
        val place = latestPlaces.firstOrNull { savedPlace -> savedPlace.placeId == placeId } ?: return
        val destination = place.toPlaceDestination()
        if (!destination.hasValidCoordinate()) {
            mutableUiState.update { state ->
                state.copy(
                    placeContent =
                        state.placeContent.copy(
                            errorMessage = INVALID_PLACE_COORDINATE_MESSAGE,
                        ),
                )
            }
            emitUiEvent(SavedRouteUiEvent.ShowSnackbar(INVALID_PLACE_COORDINATE_MESSAGE))
            return
        }

        destinationSelectionRepository.setEditingTarget(RouteEditingTarget.DESTINATION)
        if (event == SavedRouteUiEvent.NavigateToMap) {
            destinationPreviewRepository.requestPreview(
                destination = destination,
                editingTarget = RouteEditingTarget.DESTINATION,
            )
        } else {
            destinationSelectionRepository.updateSelectedDestination(destination)
        }
        viewModelScope.launch {
            mutableUiEvent.emit(event)
        }
    }

    private fun previewPlace(placeId: String) {
        val place = latestPlaces.firstOrNull { savedPlace -> savedPlace.placeId == placeId } ?: return
        val destination = place.toPlaceDestination()
        if (!destination.hasValidCoordinate()) {
            mutableUiState.update { state ->
                state.copy(
                    placeContent =
                        state.placeContent.copy(
                            errorMessage = INVALID_PLACE_COORDINATE_MESSAGE,
                        ),
                )
            }
            emitUiEvent(SavedRouteUiEvent.ShowSnackbar(INVALID_PLACE_COORDINATE_MESSAGE))
            return
        }

        destinationPreviewRepository.requestPreview(
            destination = destination,
            editingTarget = RouteEditingTarget.DESTINATION,
            detailType = MapPlaceDetailType.INTERNAL_PLACE,
        )
        emitUiEvent(SavedRouteUiEvent.NavigateToMap)
    }

    private fun handoffRouteBookmark(bookmarkId: String) {
        val routeBookmark =
            latestRoutes.firstOrNull { savedRouteBookmark -> savedRouteBookmark.bookmarkId == bookmarkId } ?: return
        viewModelScope.launch {
            val directNavigationRequest = fetchRouteBookmarkNavigationRequest(bookmarkId)

            if (directNavigationRequest != null) {
                mutableUiEvent.emit(SavedRouteUiEvent.NavigateToNavigation(directNavigationRequest))
                return@launch
            }

            fallbackRouteBookmarkHandoff(routeBookmark)
        }
    }

    private fun openRouteBookmarkDetail(bookmarkId: String) {
        val routeBookmark =
            latestRoutes.firstOrNull { savedRouteBookmark -> savedRouteBookmark.bookmarkId == bookmarkId } ?: return
        viewModelScope.launch {
            val detailRequest = fetchRouteBookmarkNavigationRequest(bookmarkId)

            if (detailRequest != null) {
                mutableUiEvent.emit(SavedRouteUiEvent.NavigateToRouteDetail(detailRequest))
                return@launch
            }

            fallbackRouteBookmarkHandoff(routeBookmark)
        }
    }

    private suspend fun fetchRouteBookmarkNavigationRequest(bookmarkId: String): RouteNavigationRequest? =
        runCatching {
            routeBookmarkRepository.getRouteBookmarkDetail(bookmarkId)?.toRouteNavigationRequestOrNull()
        }.getOrNull()

    private suspend fun fallbackRouteBookmarkHandoff(routeBookmark: SavedRouteBookmarkUiModel) {
        val origin = routeBookmark.toOriginPlaceDestination()
        val destination = routeBookmark.toDestinationPlaceDestination()
        if (!origin.hasValidCoordinate() || !destination.hasValidCoordinate()) {
            mutableUiState.update { state ->
                state.copy(
                    routeContent =
                        state.routeContent.copy(
                            errorMessage = INVALID_ROUTE_COORDINATE_MESSAGE,
                        ),
                )
            }
            mutableUiEvent.emit(SavedRouteUiEvent.ShowSnackbar(INVALID_ROUTE_COORDINATE_MESSAGE))
            return
        }

        destinationSelectionRepository.setEditingTarget(RouteEditingTarget.DESTINATION)
        destinationSelectionRepository.swapSelections(origin = origin, destination = destination)
        mutableUiEvent.emit(
            SavedRouteUiEvent.NavigateToRouteSetting(
                initialRouteOption = routeBookmark.routeOption,
            ),
        )
    }

    private fun emitUiEvent(event: SavedRouteUiEvent) {
        viewModelScope.launch {
            mutableUiEvent.emit(event)
        }
    }

    private fun observeCurrentLocation() {
        val manager = currentLocationManager ?: return
        viewModelScope.launch {
            manager.latestLocation.collectLatest { snapshot ->
                val nextLocationCoordinate = snapshot?.toGeoCoordinate()
                latestLocationCoordinate = nextLocationCoordinate
                if (
                    isLowVisionMode &&
                    latestPlaceBookmarks.isNotEmpty() &&
                    shouldRefreshLowVisionPlaceBookmarks(nextLocationCoordinate)
                ) {
                    publishPlaceBookmarks(clearErrorMessage = false)
                }
            }
        }
    }

    private fun publishPlaceBookmarks(clearErrorMessage: Boolean) {
        latestPlaces = sortedPlaceBookmarks(mutableUiState.value.placeSortOrder)
        if (isLowVisionMode) {
            lastPublishedLowVisionPlaceLocationCoordinate = latestLocationCoordinate
            lastPublishedLowVisionPlaceIds = latestPlaces.map(SavedPlaceUiModel::placeId)
        }
        mutableUiState.update { state ->
            val shouldClearErrorMessage =
                clearErrorMessage &&
                    !state.isEditMode &&
                    !state.isApplyingEditChanges
            state.copy(
                placeContent =
                    state.placeContent.copy(
                        screenState =
                            if (latestPlaces.isEmpty()) {
                                SavedBookmarkContentState.EMPTY
                            } else {
                                SavedBookmarkContentState.CONTENT
                            },
                        places = latestPlaces,
                        errorMessage = if (shouldClearErrorMessage) null else state.placeContent.errorMessage,
                    ),
            )
        }
    }

    private fun sortedPlaceBookmarks(sortOrder: SavedBookmarkSortOrder): List<SavedPlaceUiModel> {
        val placeModels =
            latestPlaceBookmarks.toSavedPlaceUiModels(
                currentLocation = latestLocationCoordinate,
            )
        return when (sortOrder) {
            SavedBookmarkSortOrder.NEAREST ->
                if (latestLocationCoordinate == null) {
                    placeModels
                } else {
                    placeModels.sortedWith(
                        compareBy<SavedPlaceUiModel> { place -> place.distanceMeters ?: Int.MAX_VALUE }
                            .thenBy { place -> place.name },
                    )
                }
            SavedBookmarkSortOrder.RECENT -> placeModels
        }
    }

    private fun sortedRouteBookmarks(sortOrder: SavedBookmarkSortOrder): List<SavedRouteBookmarkUiModel> =
        when (sortOrder) {
            SavedBookmarkSortOrder.NEAREST ->
                latestRoutes.sortedWith(
                    compareBy<SavedRouteBookmarkUiModel> { route -> route.distanceMeters ?: Int.MAX_VALUE }
                        .thenBy { route -> route.routeName },
                )
            SavedBookmarkSortOrder.RECENT -> latestRoutes
        }

    private fun shouldRefreshLowVisionPlaceBookmarks(nextLocationCoordinate: GeoCoordinate?): Boolean {
        val currentCoordinate = nextLocationCoordinate ?: return false
        val nextPlaceIds =
            latestPlaceBookmarks.toSavedPlaceUiModels(
                currentLocation = currentCoordinate,
            ).sortedWith(
                compareBy<SavedPlaceUiModel> { place -> place.distanceMeters ?: Int.MAX_VALUE }
                    .thenBy { place -> place.name },
            ).map(SavedPlaceUiModel::placeId)
        if (nextPlaceIds != lastPublishedLowVisionPlaceIds) {
            val lastPublishedCoordinate = lastPublishedLowVisionPlaceLocationCoordinate ?: return true
            return haversineDistanceMeters(lastPublishedCoordinate, currentCoordinate) >= LOW_VISION_PLACE_REORDER_MIN_DISTANCE_METERS
        }
        return false
    }

    companion object {
        private const val BOOKMARK_REMOVE_SUCCESS_MESSAGE = "선택한 북마크를 삭제했습니다."
        private const val BOOKMARK_REMOVE_FAILURE_MESSAGE = "일부 북마크를 삭제하지 못했습니다. 다시 시도해 주세요."
        private const val PLACE_BOOKMARK_LOAD_FAILURE_MESSAGE = "북마크한 장소를 불러오지 못했습니다."
        private const val ROUTE_BOOKMARK_LOAD_FAILURE_MESSAGE = "북마크한 경로를 불러오지 못했습니다."
        private const val PLACE_BOOKMARK_REMOVE_SUCCESS_MESSAGE = "북마크한 장소를 삭제했습니다."
        private const val PLACE_BOOKMARK_REMOVE_FAILURE_MESSAGE = "북마크한 장소를 삭제하지 못했습니다. 다시 시도해 주세요."
        private const val ROUTE_BOOKMARK_REMOVE_SUCCESS_MESSAGE = "북마크한 경로를 삭제했습니다."
        private const val ROUTE_BOOKMARK_REMOVE_FAILURE_MESSAGE = "북마크한 경로를 삭제하지 못했습니다. 다시 시도해 주세요."
        private const val INVALID_PLACE_COORDINATE_MESSAGE = "북마크한 장소의 좌표가 올바르지 않습니다."
        private const val INVALID_ROUTE_COORDINATE_MESSAGE = "저장한 경로의 좌표가 올바르지 않습니다."

        fun provideFactory(
            authSessionRepository: AuthSessionRepository? = null,
            bookmarkRepository: BookmarkRepository,
            routeBookmarkRepository: RouteBookmarkRepository,
            destinationSelectionRepository: DestinationSelectionRepository,
            destinationPreviewRepository: DestinationPreviewRepository = NoOpDestinationPreviewRepository,
            searchRepository: SearchRepository? = null,
            currentLocationManager: CurrentLocationManager? = null,
            isLowVisionMode: Boolean = false,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SavedRouteViewModel::class.java)) {
                        return SavedRouteViewModel(
                            authSessionRepository = authSessionRepository,
                            bookmarkRepository = bookmarkRepository,
                            routeBookmarkRepository = routeBookmarkRepository,
                            destinationSelectionRepository = destinationSelectionRepository,
                            destinationPreviewRepository = destinationPreviewRepository,
                            searchRepository = searchRepository,
                            currentLocationManager = currentLocationManager,
                            initialLowVisionMode = isLowVisionMode,
                        ) as T
                    }

                    error("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}

private const val LOW_VISION_PLACE_REORDER_MIN_DISTANCE_METERS = 20.0

private fun BookmarkData.toSavedPlaceUiModel(): SavedPlaceUiModel =
    SavedPlaceUiModel(
        placeId = placeId,
        name = placeName,
        address = address,
        category = category,
        latitude = latitude,
        longitude = longitude,
        serverPlaceId = serverPlaceId,
        provider = provider,
        providerPlaceId = providerPlaceId,
        providerCategory = providerCategory,
    )

private fun BookmarkData.toSavedPlaceUiModel(distanceMeters: Int?): SavedPlaceUiModel =
    toSavedPlaceUiModel().copy(distanceMeters = distanceMeters)

private fun RouteBookmark.toSavedRouteBookmarkUiModel(): SavedRouteBookmarkUiModel =
    SavedRouteBookmarkUiModel(
        bookmarkId = bookmarkId,
        routeName = routeName,
        startLabel = startLabel,
        endLabel = endLabel,
        startPoint = startPoint,
        endPoint = endPoint,
        routeOption = routeOption,
        transportMode = transportMode,
        routeOptionLabel = routeOptionLabel,
        distanceMeters = distanceMeters,
        durationMinutes = durationMinutes,
    )

private fun SavedPlaceUiModel.toPlaceDestination(): PlaceDestination =
    PlaceDestination(
        placeId = placeId,
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
        category = category.toPlaceCategoryOrNull(),
        serverPlaceId = serverPlaceId,
        provider = provider,
        providerPlaceId = providerPlaceId,
        providerCategory = providerCategory,
    )

private fun SavedRouteBookmarkUiModel.toOriginPlaceDestination(): PlaceDestination =
    PlaceDestination(
        placeId = "route-bookmark-origin:$bookmarkId",
        name = startLabel,
        latitude = startPoint.latitude,
        longitude = startPoint.longitude,
    )

private fun SavedRouteBookmarkUiModel.toDestinationPlaceDestination(): PlaceDestination =
    PlaceDestination(
        placeId = "route-bookmark:$bookmarkId",
        name = endLabel,
        latitude = endPoint.latitude,
        longitude = endPoint.longitude,
    )

private fun RouteBookmarkDetail.toRouteNavigationRequestOrNull(): RouteNavigationRequest? {
    val selectedRoute = route ?: return null
    if (!startPoint.isValidRouteCoordinate() || !endPoint.isValidRouteCoordinate()) return null

    return RouteNavigationRequest(
        origin =
            RouteWaypoint(
                name = startLabel,
                coordinate = startPoint,
            ),
        destination =
            RouteWaypoint(
                name = endLabel,
                coordinate = endPoint,
            ),
        selectedRoute = selectedRoute,
        source = RouteSearchSource.serverApi(label = routeName.ifBlank { "저장된 경로" }),
    )
}

private fun GeoCoordinate.isValidRouteCoordinate(): Boolean =
    latitude.isFinite() &&
        longitude.isFinite() &&
        latitude in -90.0..90.0 &&
        longitude in -180.0..180.0

private fun String?.toPlaceCategoryOrNull(): PlaceCategory? =
    this?.let { value ->
        runCatching { PlaceCategory.valueOf(value) }.getOrNull()
    }

private fun List<BookmarkData>.toSavedPlaceUiModels(currentLocation: GeoCoordinate?): List<SavedPlaceUiModel> {
    if (currentLocation == null) {
        return map(BookmarkData::toSavedPlaceUiModel)
    }

    return map { bookmark ->
        bookmark.toSavedPlaceUiModel(
            distanceMeters = haversineDistanceMeters(currentLocation, bookmark.toGeoCoordinate()).toInt(),
        )
    }
}

private fun Set<String>.toggled(value: String): Set<String> =
    if (value in this) {
        this - value
    } else {
        this + value
    }

private fun LocationSnapshot.toGeoCoordinate(): GeoCoordinate =
    GeoCoordinate(
        latitude = latitude,
        longitude = longitude,
    )

private fun BookmarkData.toGeoCoordinate(): GeoCoordinate =
    GeoCoordinate(
        latitude = latitude,
        longitude = longitude,
    )

private fun haversineDistanceMeters(
    start: GeoCoordinate,
    end: GeoCoordinate,
): Double {
    val earthRadiusMeters = 6_371_000.0
    val dLat = Math.toRadians(end.latitude - start.latitude)
    val dLng = Math.toRadians(end.longitude - start.longitude)
    val startLatitudeRadians = Math.toRadians(start.latitude)
    val endLatitudeRadians = Math.toRadians(end.latitude)
    val haversine =
        sin(dLat / 2).pow(2) +
            cos(startLatitudeRadians) * cos(endLatitudeRadians) * sin(dLng / 2).pow(2)
    return 2 * earthRadiusMeters * atan2(sqrt(haversine), sqrt(1 - haversine))
}
