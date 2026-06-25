package com.ssafy.e102.eumgil.feature.search

import androidx.activity.ComponentActivity
import com.ssafy.e102.eumgil.core.location.CurrentLocationAddressResolver
import com.ssafy.e102.eumgil.core.location.CurrentLocationManager
import com.ssafy.e102.eumgil.core.location.LocationGrantAccuracy
import com.ssafy.e102.eumgil.core.location.LocationPermissionManager
import com.ssafy.e102.eumgil.core.location.LocationPermissionState
import com.ssafy.e102.eumgil.core.location.LocationSnapshot
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.PlaceCategory
import com.ssafy.e102.eumgil.core.model.RecentDestination
import com.ssafy.e102.eumgil.core.model.RecentSearch
import com.ssafy.e102.eumgil.core.model.SearchQuery
import com.ssafy.e102.eumgil.core.model.SearchResult
import com.ssafy.e102.eumgil.core.model.toPlaceDestination
import com.ssafy.e102.eumgil.data.repository.BookmarkData
import com.ssafy.e102.eumgil.data.repository.BookmarkRepository
import com.ssafy.e102.eumgil.data.repository.InMemoryDestinationPreviewRepository
import com.ssafy.e102.eumgil.data.repository.InMemoryDestinationSelectionRepository
import com.ssafy.e102.eumgil.data.repository.RouteEditingTarget
import com.ssafy.e102.eumgil.data.repository.RouteSelectionRequestReason
import com.ssafy.e102.eumgil.data.repository.SearchRepository
import com.ssafy.e102.eumgil.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelEditingTargetTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `editing target configuration updates ui state`() =
        runTest {
            val viewModel =
                SearchViewModel(
                    searchRepository = EditingTargetFakeSearchRepository(),
                    bookmarkRepository = EditingTargetFakeBookmarkRepository(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                )

            advanceUntilIdle()
            viewModel.onAction(SearchUiAction.EditingTargetConfigured(RouteEditingTarget.ORIGIN))

            assertEquals(RouteEditingTarget.ORIGIN, viewModel.uiState.value.editingTarget)
        }

    @Test
    fun `selection mode configuration updates ui state`() =
        runTest {
            val viewModel =
                SearchViewModel(
                    searchRepository = EditingTargetFakeSearchRepository(),
                    bookmarkRepository = EditingTargetFakeBookmarkRepository(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                )

            advanceUntilIdle()
            viewModel.onAction(
                SearchUiAction.EditingTargetConfigured(
                    editingTarget = RouteEditingTarget.DESTINATION,
                    selectionMode = SearchSelectionMode.APPLY_TO_ROUTE,
                ),
            )

            assertEquals(SearchSelectionMode.APPLY_TO_ROUTE, viewModel.uiState.value.selectionMode)
        }

    @Test
    fun `map picker click in apply to route mode opens picker for active editing target`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val viewModel =
                SearchViewModel(
                    searchRepository = EditingTargetFakeSearchRepository(),
                    bookmarkRepository = EditingTargetFakeBookmarkRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            val uiEvent =
                backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
                    withTimeoutOrNull(100) { viewModel.uiEvent.first() }
                }

            viewModel.onAction(
                SearchUiAction.EditingTargetConfigured(
                    editingTarget = RouteEditingTarget.ORIGIN,
                    selectionMode = SearchSelectionMode.APPLY_TO_ROUTE,
                ),
            )
            viewModel.onAction(SearchUiAction.MapPickerClicked)
            advanceUntilIdle()

            assertEquals(RouteEditingTarget.ORIGIN, destinationSelectionRepository.editingTarget.value)
            assertEquals(
                SearchUiEvent.NavigateToRouteEndpointMapPicker(RouteEditingTarget.ORIGIN),
                uiEvent.await(),
            )
        }

    @Test
    fun `current location origin action clears selected origin explicitly and navigates with prechecked permission`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedOrigin(testSearchResult().toPlaceDestination())
                }
            val locationManager =
                EditingTargetFakeCurrentLocationManager(
                    initialLocation = testLocationSnapshot(latitude = 35.1797, longitude = 129.0750),
                )
            val viewModel =
                SearchViewModel(
                    searchRepository = EditingTargetFakeSearchRepository(),
                    bookmarkRepository = EditingTargetFakeBookmarkRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    currentLocationManager = locationManager,
                    locationPermissionManager = EditingTargetFakeLocationPermissionManager(),
                )

            advanceUntilIdle()
            val selectionRequest =
                backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
                    destinationSelectionRepository.selectionRequests.first()
                }
            val uiEvent = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvent.first() }

            viewModel.onAction(
                SearchUiAction.EditingTargetConfigured(
                    editingTarget = RouteEditingTarget.ORIGIN,
                    selectionMode = SearchSelectionMode.APPLY_TO_ROUTE,
                ),
            )
            viewModel.onAction(SearchUiAction.CurrentLocationClicked)
            advanceUntilIdle()

            assertEquals(null, destinationSelectionRepository.selectedOrigin.value)
            assertEquals(RouteSelectionRequestReason.ORIGIN_CLEARED, selectionRequest.await().reason)
            assertEquals(
                SearchCurrentLocationQuickActionStatus.Applied,
                viewModel.uiState.value.currentLocationQuickActionState.status,
            )
            assertEquals(
                SearchUiEvent.NavigateToRouteSetting(locationPermissionPrechecked = true),
                uiEvent.await(),
            )
        }

    @Test
    fun `current location destination action stores resolved address place destination`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val currentLocation = testLocationSnapshot(latitude = 35.1000, longitude = 129.0320)
            val viewModel =
                SearchViewModel(
                    searchRepository = EditingTargetFakeSearchRepository(),
                    bookmarkRepository = EditingTargetFakeBookmarkRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    currentLocationManager =
                        EditingTargetFakeCurrentLocationManager(
                            initialLocation = currentLocation,
                        ),
                    locationPermissionManager = EditingTargetFakeLocationPermissionManager(),
                    currentLocationAddressResolver =
                        EditingTargetFakeCurrentLocationAddressResolver(
                            address = "부산광역시 중구 중앙대로 100",
                        ),
                )

            advanceUntilIdle()
            val uiEvent = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvent.first() }

            viewModel.onAction(
                SearchUiAction.EditingTargetConfigured(
                    editingTarget = RouteEditingTarget.DESTINATION,
                    selectionMode = SearchSelectionMode.APPLY_TO_ROUTE,
                ),
            )
            viewModel.onAction(SearchUiAction.CurrentLocationClicked)
            advanceUntilIdle()

            val selectedDestination = destinationSelectionRepository.selectedDestination.value
            assertEquals("current-location", selectedDestination?.placeId)
            assertEquals("부산광역시 중구 중앙대로 100", selectedDestination?.name)
            assertEquals("부산광역시 중구 중앙대로 100", selectedDestination?.address)
            assertEquals(currentLocation.latitude, selectedDestination?.latitude)
            assertEquals(currentLocation.longitude, selectedDestination?.longitude)
            assertEquals(
                SearchCurrentLocationQuickActionStatus.Applied,
                viewModel.uiState.value.currentLocationQuickActionState.status,
            )
            assertEquals(
                SearchUiEvent.NavigateToRouteSetting(locationPermissionPrechecked = true),
                uiEvent.await(),
            )
        }

    @Test
    fun `current location action with denied permission leaves visible permission status and requests permission`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val viewModel =
                SearchViewModel(
                    searchRepository = EditingTargetFakeSearchRepository(),
                    bookmarkRepository = EditingTargetFakeBookmarkRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    currentLocationManager = EditingTargetFakeCurrentLocationManager(),
                    locationPermissionManager =
                        EditingTargetFakeLocationPermissionManager(
                            initialState = LocationPermissionState.Denied,
                        ),
                )

            advanceUntilIdle()
            val uiEvent = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvent.first() }

            viewModel.onAction(
                SearchUiAction.EditingTargetConfigured(
                    editingTarget = RouteEditingTarget.DESTINATION,
                    selectionMode = SearchSelectionMode.APPLY_TO_ROUTE,
                ),
            )
            viewModel.onAction(SearchUiAction.CurrentLocationClicked)
            advanceUntilIdle()

            assertEquals(null, destinationSelectionRepository.selectedDestination.value)
            assertEquals(
                SearchCurrentLocationQuickActionStatus.PermissionDenied,
                viewModel.uiState.value.currentLocationQuickActionState.status,
            )
            assertEquals(SearchUiEvent.RequestLocationPermission, uiEvent.await())
        }

    @Test
    fun `current location action without available fix leaves visible unavailable status`() =
        runTest {
            val viewModel =
                SearchViewModel(
                    searchRepository = EditingTargetFakeSearchRepository(),
                    bookmarkRepository = EditingTargetFakeBookmarkRepository(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    currentLocationManager = EditingTargetFakeCurrentLocationManager(),
                    locationPermissionManager = EditingTargetFakeLocationPermissionManager(),
                )

            advanceUntilIdle()
            viewModel.onAction(
                SearchUiAction.EditingTargetConfigured(
                    editingTarget = RouteEditingTarget.DESTINATION,
                    selectionMode = SearchSelectionMode.APPLY_TO_ROUTE,
                ),
            )
            viewModel.onAction(SearchUiAction.CurrentLocationClicked)
            advanceTimeBy(1_501L)
            advanceUntilIdle()

            assertEquals(
                SearchCurrentLocationQuickActionStatus.LocationUnavailable,
                viewModel.uiState.value.currentLocationQuickActionState.status,
            )
        }

    @Test
    fun `search submit keeps destination editing target in results navigation`() =
        runTest {
            val viewModel =
                SearchViewModel(
                    searchRepository = EditingTargetFakeSearchRepository(),
                    bookmarkRepository = EditingTargetFakeBookmarkRepository(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                )

            advanceUntilIdle()
            val uiEvent = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvent.first() }

            viewModel.onAction(SearchUiAction.QueryChanged(query = "city hall"))
            viewModel.onAction(SearchUiAction.SearchSubmitted)
            advanceUntilIdle()

            assertEquals(
                SearchUiEvent.NavigateToResults(
                    query = "city hall",
                    editingTarget = RouteEditingTarget.DESTINATION,
                ),
                uiEvent.await(),
            )
        }

    @Test
    fun `search submit keeps apply to route selection mode in results navigation`() =
        runTest {
            val viewModel =
                SearchViewModel(
                    searchRepository = EditingTargetFakeSearchRepository(),
                    bookmarkRepository = EditingTargetFakeBookmarkRepository(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                )

            advanceUntilIdle()
            val uiEvent = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvent.first() }

            viewModel.onAction(
                SearchUiAction.EditingTargetConfigured(
                    editingTarget = RouteEditingTarget.ORIGIN,
                    selectionMode = SearchSelectionMode.APPLY_TO_ROUTE,
                ),
            )
            viewModel.onAction(SearchUiAction.QueryChanged(query = "city hall"))
            viewModel.onAction(SearchUiAction.SearchSubmitted)
            advanceUntilIdle()

            assertEquals(
                SearchUiEvent.NavigateToResults(
                    query = "city hall",
                    editingTarget = RouteEditingTarget.ORIGIN,
                    selectionMode = SearchSelectionMode.APPLY_TO_ROUTE,
                ),
                uiEvent.await(),
            )
        }

    @Test
    fun `results route entry resets stale origin apply mode back to home destination preview mode`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    setEditingTarget(RouteEditingTarget.ORIGIN)
                }
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val result = testSearchResult()
            val viewModel =
                SearchViewModel(
                    searchRepository = EditingTargetFakeSearchRepository(),
                    bookmarkRepository = EditingTargetFakeBookmarkRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    destinationPreviewRepository = destinationPreviewRepository,
                )

            advanceUntilIdle()
            viewModel.onAction(
                SearchUiAction.EditingTargetConfigured(
                    editingTarget = RouteEditingTarget.ORIGIN,
                    selectionMode = SearchSelectionMode.APPLY_TO_ROUTE,
                ),
            )
            viewModel.onAction(
                SearchUiAction.ResultsRouteEntered(
                    query = "city hall",
                    editingTarget = RouteEditingTarget.DESTINATION,
                    selectionMode = SearchSelectionMode.PREVIEW_ON_MAP,
                ),
            )
            advanceUntilIdle()

            assertEquals(RouteEditingTarget.DESTINATION, viewModel.uiState.value.editingTarget)
            assertEquals(SearchSelectionMode.PREVIEW_ON_MAP, viewModel.uiState.value.selectionMode)
            assertEquals(RouteEditingTarget.DESTINATION, destinationSelectionRepository.editingTarget.value)

            val uiEvent = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvent.first() }
            viewModel.onAction(SearchUiAction.SearchResultPreviewClicked(result = result))
            advanceUntilIdle()

            assertEquals(RouteEditingTarget.DESTINATION, destinationPreviewRepository.pendingPreview.value?.editingTarget)
            assertEquals(null, destinationPreviewRepository.pendingPreview.value?.routeEndpointTarget)
            assertEquals(SearchUiEvent.NavigateToMapPreview, uiEvent.await())
        }

    @Test
    fun `apply to route search result click stores selected origin and prechecks route setting permission`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val viewModel =
                SearchViewModel(
                    searchRepository = EditingTargetFakeSearchRepository(),
                    bookmarkRepository = EditingTargetFakeBookmarkRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                )
            val result = testSearchResult()

            advanceUntilIdle()
            val uiEvent = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvent.first() }

            viewModel.onAction(
                SearchUiAction.EditingTargetConfigured(
                    editingTarget = RouteEditingTarget.ORIGIN,
                    selectionMode = SearchSelectionMode.APPLY_TO_ROUTE,
                ),
            )
            viewModel.onAction(SearchUiAction.SearchResultClicked(result = result))
            advanceUntilIdle()

            assertEquals(result.toPlaceDestination(), destinationSelectionRepository.selectedOrigin.value)
            assertEquals(null, destinationSelectionRepository.selectedDestination.value)
            assertEquals(
                SearchUiEvent.NavigateToRouteSetting(locationPermissionPrechecked = true),
                uiEvent.await(),
            )
        }

    @Test
    fun `search result preview click previews selected origin candidate when editing target is origin`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    setEditingTarget(RouteEditingTarget.ORIGIN)
                }
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val result = testSearchResult()
            val viewModel =
                SearchViewModel(
                    searchRepository = EditingTargetFakeSearchRepository(),
                    bookmarkRepository = EditingTargetFakeBookmarkRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    destinationPreviewRepository = destinationPreviewRepository,
                )

            advanceUntilIdle()
            val uiEvent = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvent.first() }

            viewModel.onAction(SearchUiAction.SearchResultPreviewClicked(result = result))
            advanceUntilIdle()

            assertEquals(null, destinationSelectionRepository.selectedOrigin.value)
            assertEquals(null, destinationSelectionRepository.selectedDestination.value)
            assertEquals(result.toPlaceDestination(), destinationPreviewRepository.pendingPreview.value?.destination)
            assertEquals(RouteEditingTarget.ORIGIN, destinationPreviewRepository.pendingPreview.value?.editingTarget)
            assertEquals(SearchUiEvent.NavigateToMapPreview, uiEvent.await())
        }

    @Test
    fun `search result preview click previews selected destination candidate when editing target is destination`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    setEditingTarget(RouteEditingTarget.DESTINATION)
                }
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val result = testSearchResult()
            val viewModel =
                SearchViewModel(
                    searchRepository = EditingTargetFakeSearchRepository(),
                    bookmarkRepository = EditingTargetFakeBookmarkRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    destinationPreviewRepository = destinationPreviewRepository,
                )

            advanceUntilIdle()
            val uiEvent = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvent.first() }

            viewModel.onAction(SearchUiAction.SearchResultPreviewClicked(result = result))
            advanceUntilIdle()

            assertEquals(null, destinationSelectionRepository.selectedOrigin.value)
            assertEquals(null, destinationSelectionRepository.selectedDestination.value)
            assertEquals(result.toPlaceDestination(), destinationPreviewRepository.pendingPreview.value?.destination)
            assertEquals(RouteEditingTarget.DESTINATION, destinationPreviewRepository.pendingPreview.value?.editingTarget)
            assertEquals(SearchUiEvent.NavigateToMapPreview, uiEvent.await())
        }

    @Test
    fun `provider only search result can preview origin when coordinates are valid`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    setEditingTarget(RouteEditingTarget.ORIGIN)
                }
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val result =
                SearchResult(
                    placeId = "provider:kakao:987654321",
                    serverPlaceId = null,
                    providerPlaceId = "987654321",
                    title = "Provider Only Cafe",
                    subtitle = "2 Gwangbok-ro, Busan",
                    latitude = 35.1010,
                    longitude = 129.0330,
                    matched = false,
                )
            val viewModel =
                SearchViewModel(
                    searchRepository = EditingTargetFakeSearchRepository(),
                    bookmarkRepository = EditingTargetFakeBookmarkRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    destinationPreviewRepository = destinationPreviewRepository,
            )

            advanceUntilIdle()
            val uiEvent = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvent.first() }

            viewModel.onAction(SearchUiAction.SearchResultPreviewClicked(result = result))
            advanceUntilIdle()

            assertEquals(null, destinationSelectionRepository.selectedOrigin.value)
            assertEquals(null, destinationSelectionRepository.selectedDestination.value)
            assertEquals("provider:kakao:987654321", destinationPreviewRepository.pendingPreview.value?.destination?.placeId)
            assertEquals(RouteEditingTarget.ORIGIN, destinationPreviewRepository.pendingPreview.value?.editingTarget)
            assertEquals(SearchUiEvent.NavigateToMapPreview, uiEvent.await())
        }
}

private fun testSearchResult(): SearchResult =
    SearchResult(
        placeId = "place-1",
        title = "Busan City Hall",
        subtitle = "123 Jungang-daero, Busan",
        latitude = 35.1797,
        longitude = 129.0750,
        category = PlaceCategory.TOURIST_ATTRACTION,
    )

private fun testLocationSnapshot(
    latitude: Double,
    longitude: Double,
): LocationSnapshot =
    LocationSnapshot(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = 12f,
        recordedAtEpochMillis = System.currentTimeMillis(),
    )

private class EditingTargetFakeCurrentLocationManager(
    initialLocation: LocationSnapshot? = null,
) : CurrentLocationManager {
    private val mutableLatestLocation = MutableStateFlow(initialLocation)

    override val latestLocation: StateFlow<LocationSnapshot?> = mutableLatestLocation.asStateFlow()

    override fun refreshLatestLocation() = Unit

    override fun startLocationUpdates() = Unit

    override fun stopLocationUpdates() = Unit
}

private class EditingTargetFakeLocationPermissionManager(
    initialState: LocationPermissionState =
        LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE),
) : LocationPermissionManager {
    private val mutablePermissionState = MutableStateFlow(initialState)

    override val permissionState: StateFlow<LocationPermissionState> = mutablePermissionState.asStateFlow()

    override fun refreshPermissionState() = Unit

    override fun requestLocationPermission(activity: ComponentActivity) = Unit
}

private class EditingTargetFakeCurrentLocationAddressResolver(
    private val address: String?,
) : CurrentLocationAddressResolver {
    override suspend fun resolveAddress(coordinate: GeoCoordinate): String? = address
}

private class EditingTargetFakeSearchRepository : SearchRepository {
    override suspend fun search(query: SearchQuery): List<SearchResult> = emptyList()

    override suspend fun getRecentSearches(): List<RecentSearch> = emptyList()

    override suspend fun saveRecentSearch(keyword: String) = Unit

    override suspend fun getRecentDestinations(): List<RecentDestination> = emptyList()

    override suspend fun saveRecentDestination(destination: RecentDestination) = Unit
}

private class EditingTargetFakeBookmarkRepository : BookmarkRepository {
    private val bookmarks = MutableStateFlow<List<BookmarkData>>(emptyList())

    override fun observeBookmarks(): Flow<List<BookmarkData>> = bookmarks

    override suspend fun isBookmarked(placeId: String): Boolean = false

    override suspend fun saveBookmark(bookmark: BookmarkData): BookmarkData = bookmark

    override suspend fun deleteBookmark(placeId: String) = Unit
}
