package com.ssafy.e102.eumgil.feature.map

import androidx.activity.ComponentActivity
import com.ssafy.e102.eumgil.core.location.CurrentLocationManager
import com.ssafy.e102.eumgil.core.location.LocationGrantAccuracy
import com.ssafy.e102.eumgil.core.location.LocationPermissionManager
import com.ssafy.e102.eumgil.core.location.LocationPermissionState
import com.ssafy.e102.eumgil.core.location.LocationSnapshot
import com.ssafy.e102.eumgil.core.model.AccessibilityTag
import com.ssafy.e102.eumgil.core.model.AuthGateState
import com.ssafy.e102.eumgil.core.model.AuthSession
import com.ssafy.e102.eumgil.core.model.FacilityBrowseData
import com.ssafy.e102.eumgil.core.model.FacilityCategory
import com.ssafy.e102.eumgil.core.model.FacilityDetailSeed
import com.ssafy.e102.eumgil.core.model.FacilityMarkerSeed
import com.ssafy.e102.eumgil.core.model.FacilitySeed
import com.ssafy.e102.eumgil.core.model.FacilitySeedCatalog
import com.ssafy.e102.eumgil.core.model.FacilitySeedQuery
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.MapPlaceClickType
import com.ssafy.e102.eumgil.core.model.MapPlaceDetailRequest
import com.ssafy.e102.eumgil.core.model.MapPlaceDetailType
import com.ssafy.e102.eumgil.core.model.MapTappedPlaceDetail
import com.ssafy.e102.eumgil.core.model.PlaceCategory
import com.ssafy.e102.eumgil.core.model.PlaceDetail
import com.ssafy.e102.eumgil.core.model.PlaceDestination
import com.ssafy.e102.eumgil.core.model.PlaceFeatureAvailability
import com.ssafy.e102.eumgil.core.model.PlaceFeatureType
import com.ssafy.e102.eumgil.core.model.PlaceQuery
import com.ssafy.e102.eumgil.core.model.PlaceSummary
import com.ssafy.e102.eumgil.core.model.RecentDestination
import com.ssafy.e102.eumgil.core.model.toPlaceDestination
import com.ssafy.e102.eumgil.data.local.datasource.FacilitySeedLocalDataSource
import com.ssafy.e102.eumgil.data.mock.datasource.FacilitySeedMockDataSource
import com.ssafy.e102.eumgil.data.repository.BookmarkData
import com.ssafy.e102.eumgil.data.repository.BookmarkRepository
import com.ssafy.e102.eumgil.data.repository.DefaultFacilitySeedRepository
import com.ssafy.e102.eumgil.data.repository.FacilitySeedRepository
import com.ssafy.e102.eumgil.data.repository.InMemoryDestinationPreviewRepository
import com.ssafy.e102.eumgil.data.repository.InMemoryDestinationSelectionRepository
import com.ssafy.e102.eumgil.data.repository.PlacesRepository
import com.ssafy.e102.eumgil.data.repository.RouteEditingTarget
import com.ssafy.e102.eumgil.data.repository.SearchRepository
import com.ssafy.e102.eumgil.data.repository.TestAuthSessionRepository
import com.ssafy.e102.eumgil.feature.map.component.createKakaoCameraRenderState
import com.ssafy.e102.eumgil.feature.search.SearchSelectionMode
import com.ssafy.e102.eumgil.feature.map.model.MapCameraSource
import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import com.ssafy.e102.eumgil.feature.map.model.MapDefaults
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerDisplayState
import com.ssafy.e102.eumgil.feature.map.model.MapShortcutFilterKey
import com.ssafy.e102.eumgil.feature.map.model.resolvedZoomLevel
import com.ssafy.e102.eumgil.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `destination selection centers camera on selected place`() =
        runTest {
            val permissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied)
            val locationManager = FakeCurrentLocationManager()
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager = permissionManager,
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = destinationSelectionRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )
            val destination = testDestination()

            destinationSelectionRepository.updateSelectedDestination(destination)
            advanceUntilIdle()

            assertEquals(destination, viewModel.uiState.value.selectedDestination)
            assertEquals(MapCameraSource.SEARCH_RESULT, viewModel.uiState.value.cameraTarget.source)
            assertEquals(destination.latitude, viewModel.uiState.value.cameraTarget.center.latitude, 0.0)
            assertEquals(destination.longitude, viewModel.uiState.value.cameraTarget.center.longitude, 0.0)
        }

    @Test
    fun `route endpoint state exposes selected origin and active editing target`() =
        runTest {
            val permissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied)
            val locationManager = FakeCurrentLocationManager()
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager = permissionManager,
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = destinationSelectionRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )
            val origin = testDestination().copy(placeId = "origin-1", name = "부산시청")

            destinationSelectionRepository.setEditingTarget(RouteEditingTarget.ORIGIN)
            destinationSelectionRepository.updateSelectedOrigin(origin)
            advanceUntilIdle()

            assertEquals(RouteEditingTarget.ORIGIN, viewModel.uiState.value.routeEditingTarget)
            assertEquals(origin, viewModel.uiState.value.selectedOrigin)
        }

    @Test
    fun `home search entry opens place preview search mode`() =
        runTest {
            val viewModel =
                MapViewModel(
                    locationPermissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            viewModel.onAction(MapUiAction.SearchEntryClicked)
            advanceUntilIdle()

            val event =
                withTimeoutOrNull(100) {
                    viewModel.uiEvent.first()
                }

            assertEquals(
                MapUiEvent.NavigateToSearch(
                    editingTarget = RouteEditingTarget.DESTINATION,
                    selectionMode = SearchSelectionMode.PREVIEW_ON_MAP,
                ),
                event,
            )
        }

    @Test
    fun `route endpoint status click opens search for requested endpoint`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            viewModel.onAction(MapUiAction.RouteEndpointStatusClicked(RouteEditingTarget.ORIGIN))
            advanceUntilIdle()

            val event =
                withTimeoutOrNull(100) {
                    viewModel.uiEvent.first()
                }

            assertEquals(RouteEditingTarget.ORIGIN, destinationSelectionRepository.editingTarget.value)
            assertEquals(RouteEditingTarget.ORIGIN, viewModel.uiState.value.routeEditingTarget)
            assertEquals(
                MapUiEvent.NavigateToSearch(
                    editingTarget = RouteEditingTarget.ORIGIN,
                    selectionMode = SearchSelectionMode.APPLY_TO_ROUTE,
                ),
                event,
            )
        }

    @Test
    fun `route endpoint map picker enter exposes active editing target and dismiss clears mode`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            viewModel.onAction(MapUiAction.RouteEndpointMapPickerEntered(RouteEditingTarget.ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteEditingTarget.ORIGIN, destinationSelectionRepository.editingTarget.value)
            assertEquals(RouteEditingTarget.ORIGIN, viewModel.uiState.value.routeEndpointMapPickerState?.editingTarget)

            viewModel.onAction(MapUiAction.RouteEndpointMapPickerDismissed)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.routeEndpointMapPickerState)
            assertFalse(viewModel.uiState.value.facilityDetailSheetState.isVisible)
        }

    @Test
    fun `route endpoint map picker camera center selects coordinate destination for active target`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )
            val tappedCoordinate = MapCoordinate(latitude = 35.1775, longitude = 129.0771)

            viewModel.onAction(MapUiAction.RouteEndpointMapPickerEntered(RouteEditingTarget.ORIGIN))
            viewModel.onAction(
                MapUiAction.ViewportCameraChanged(
                    center = tappedCoordinate,
                    zoomLevel = 4,
                    isUserGesture = true,
                    isSelectedMapPinVisibleInViewport = false,
                ),
            )
            advanceUntilIdle()

            val pickerState = requireNotNull(viewModel.uiState.value.routeEndpointMapPickerState)
            assertEquals(tappedCoordinate, pickerState.candidateCoordinate)
            assertEquals(MapPlaceDetailType.EXTERNAL_ADDRESS, pickerState.candidateDetail?.detailType)
            assertNull(viewModel.uiState.value.selectedMapPinCoordinate)
            assertFalse(viewModel.uiState.value.facilityDetailSheetState.isVisible)

            viewModel.onAction(MapUiAction.FacilitySetRouteEndpointClicked(RouteEditingTarget.DESTINATION))
            advanceUntilIdle()

            val event =
                withTimeoutOrNull(100) {
                    viewModel.uiEvent.first()
                }
            val selectedOrigin = destinationSelectionRepository.selectedOrigin.value

            assertEquals(MapUiEvent.NavigateToRouteSetting(locationPermissionPrechecked = true), event)
            assertEquals(tappedCoordinate.latitude, selectedOrigin?.latitude ?: 0.0, 0.0)
            assertEquals(tappedCoordinate.longitude, selectedOrigin?.longitude ?: 0.0, 0.0)
            assertNull(destinationSelectionRepository.selectedDestination.value)
            assertNull(viewModel.uiState.value.routeEndpointMapPickerState)
            assertFalse(viewModel.uiState.value.facilityDetailSheetState.isVisible)
        }

    @Test
    fun `route endpoint map picker resolves stopped camera center as poi before address fallback`() =
        runTest {
            val stoppedCoordinate = MapCoordinate(latitude = 35.1151, longitude = 129.0414)
            val placesRepository =
                FakePlacesRepository(
                    mapTapDetailsByClickType =
                        mapOf(
                            MapPlaceClickType.POI to
                                testMapTappedDetail(
                                    bookmarkTargetId = "kakao:poi-123",
                                    detailType = MapPlaceDetailType.EXTERNAL_POI,
                                    provider = "KAKAO",
                                    providerPlaceId = "poi-123",
                                    name = "Busan Station",
                                    providerCategory = "Subway Station",
                                    address = "206 Jungang-daero, Busan",
                                    latitude = stoppedCoordinate.latitude,
                                    longitude = stoppedCoordinate.longitude,
                                ),
                        ),
                )
            val viewModel =
                MapViewModel(
                    locationPermissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    placesRepository = placesRepository,
                )

            viewModel.onAction(MapUiAction.RouteEndpointMapPickerEntered(RouteEditingTarget.DESTINATION))
            advanceUntilIdle()
            placesRepository.mapTapDetailRequests.clear()

            viewModel.onAction(
                MapUiAction.ViewportCameraChanged(
                    center = stoppedCoordinate,
                    zoomLevel = 4,
                    isUserGesture = true,
                ),
            )
            advanceUntilIdle()

            val request = placesRepository.mapTapDetailRequests.last()
            val pickerState = requireNotNull(viewModel.uiState.value.routeEndpointMapPickerState)

            assertEquals(listOf(MapPlaceClickType.POI), placesRepository.mapTapDetailRequests.map { it.clickType })
            assertEquals(stoppedCoordinate.latitude, request.latitude, 0.0)
            assertEquals(stoppedCoordinate.longitude, request.longitude, 0.0)
            assertEquals("Busan Station", pickerState.candidateDetail?.name)
            assertEquals(MapPlaceDetailType.EXTERNAL_POI, pickerState.candidateDetail?.detailType)
            assertFalse(pickerState.isResolvingCandidate)
        }

    @Test
    fun `route endpoint map picker falls back to address when poi lookup returns empty`() =
        runTest {
            val stoppedCoordinate = MapCoordinate(latitude = 35.1151, longitude = 129.0414)
            val placesRepository =
                FakePlacesRepository(
                    mapTapDetailsByClickType =
                        mapOf(
                            MapPlaceClickType.POI to null,
                            MapPlaceClickType.ADDRESS to
                                testMapTappedDetail(
                                    bookmarkTargetId = "external-address:35.1151,129.0414",
                                    detailType = MapPlaceDetailType.EXTERNAL_ADDRESS,
                                    name = "206 Jungang-daero",
                                    address = "206 Jungang-daero, Busan",
                                    latitude = stoppedCoordinate.latitude,
                                    longitude = stoppedCoordinate.longitude,
                                ),
                        ),
                )
            val viewModel =
                MapViewModel(
                    locationPermissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    placesRepository = placesRepository,
                )

            viewModel.onAction(MapUiAction.RouteEndpointMapPickerEntered(RouteEditingTarget.DESTINATION))
            advanceUntilIdle()
            placesRepository.mapTapDetailRequests.clear()

            viewModel.onAction(
                MapUiAction.ViewportCameraChanged(
                    center = stoppedCoordinate,
                    zoomLevel = 4,
                    isUserGesture = true,
                ),
            )
            advanceUntilIdle()

            val pickerState = requireNotNull(viewModel.uiState.value.routeEndpointMapPickerState)

            assertEquals(
                listOf(MapPlaceClickType.POI, MapPlaceClickType.ADDRESS),
                placesRepository.mapTapDetailRequests.map { it.clickType },
            )
            assertEquals("206 Jungang-daero", pickerState.candidateDetail?.name)
            assertEquals(MapPlaceDetailType.EXTERNAL_ADDRESS, pickerState.candidateDetail?.detailType)
            assertFalse(pickerState.isResolvingCandidate)
        }

    @Test
    fun `search preview centers camera and opens bottom sheet without selecting route destination`() =
        runTest {
            val permissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied)
            val locationManager = FakeCurrentLocationManager()
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager = permissionManager,
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = destinationSelectionRepository,
                    destinationPreviewRepository = destinationPreviewRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )
            val destination =
                PlaceDestination(
                    placeId = "preview-1",
                    name = "Busan Tower",
                    address = "1 Yongdusan-gil, Busan",
                    latitude = 35.1000,
                    longitude = 129.0320,
                    category = PlaceCategory.TOURIST_SPOT,
                )

            destinationPreviewRepository.requestPreview(
                destination = destination,
                accessibilityTagKeys = listOf("elevator"),
            )
            advanceUntilIdle()

            assertNull(destinationSelectionRepository.selectedDestination.value)
            assertNull(destinationPreviewRepository.pendingPreview.value)
            assertEquals(MapCameraSource.SEARCH_RESULT, viewModel.uiState.value.cameraTarget.source)
            assertEquals(destination.latitude, viewModel.uiState.value.cameraTarget.center.latitude, 0.0)
            assertEquals(destination.longitude, viewModel.uiState.value.cameraTarget.center.longitude, 0.0)
            assertEquals(destination.latitude, viewModel.uiState.value.selectedMapPinCoordinate?.latitude ?: 0.0, 0.0)
            assertEquals(destination.longitude, viewModel.uiState.value.selectedMapPinCoordinate?.longitude ?: 0.0, 0.0)
            val sheetState = viewModel.uiState.value.facilityDetailSheetState
            assertTrue(sheetState.isVisible)
            assertEquals(MapFacilityDetailSheetPresentation.EXPANDED, sheetState.presentation)
            assertEquals(destination, sheetState.destinationPreview?.destination)
            assertEquals("Busan Tower", sheetState.mapTapDetail?.name)
            assertEquals(listOf("elevator"), sheetState.mapTapDetail?.accessibilityTags)
        }

    @Test
    fun `search preview hydrates internal place detail phone number when detail api is available`() =
        runTest {
            val permissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied)
            val locationManager = FakeCurrentLocationManager()
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val placesRepository =
                FakePlacesRepository(
                    placeDetailsById =
                        mapOf(
                            "preview-1" to
                                PlaceDetail(
                                    placeId = "preview-1",
                                    name = "Busan Tower",
                                    address = "1 Yongdusan-gil, Busan",
                                    latitude = 35.1000,
                                    longitude = 129.0320,
                                    category = PlaceCategory.TOURIST_SPOT,
                                    phoneNumber = "051-600-1000",
                                ),
                        ),
                )
            val viewModel =
                MapViewModel(
                    locationPermissionManager = permissionManager,
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = destinationSelectionRepository,
                    destinationPreviewRepository = destinationPreviewRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    placesRepository = placesRepository,
                )
            val destination =
                PlaceDestination(
                    placeId = "preview-1",
                    name = "Busan Tower",
                    address = "1 Yongdusan-gil, Busan",
                    latitude = 35.1000,
                    longitude = 129.0320,
                    category = PlaceCategory.TOURIST_SPOT,
                )

            destinationPreviewRepository.requestPreview(
                destination = destination,
                accessibilityTagKeys = listOf("elevator"),
            )
            advanceUntilIdle()

            val sheetState = viewModel.uiState.value.facilityDetailSheetState
            assertEquals("051-600-1000", sheetState.mapTapDetail?.phoneNumber)
            assertEquals(listOf("elevator"), sheetState.mapTapDetail?.accessibilityTags)
        }

    @Test
    fun `search preview reflects existing bookmark state from local repository`() =
        runTest {
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    destinationPreviewRepository = destinationPreviewRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(bookmarkedPlaceIds = mutableSetOf("preview-1")),
                )
            val destination =
                PlaceDestination(
                    placeId = "preview-1",
                    name = "Busan Tower",
                    address = "1 Yongdusan-gil, Busan",
                    latitude = 35.1000,
                    longitude = 129.0320,
                    category = PlaceCategory.TOURIST_SPOT,
                )

            destinationPreviewRepository.requestPreview(destination = destination)
            advanceUntilIdle()

            val sheetState = viewModel.uiState.value.facilityDetailSheetState
            assertTrue(sheetState.isBookmarked)
            assertTrue(requireNotNull(sheetState.mapTapDetail).isBookmarked)
        }

    @Test
    fun `search preview keeps preview camera when map route restarts with current location available`() =
        runTest {
            val initialLocation = testLocationSnapshot(latitude = 35.1796, longitude = 129.0756)
            val permissionManager =
                FakeLocationPermissionManager(
                    initialState = LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE),
                )
            val locationManager = FakeCurrentLocationManager(initialLocation = initialLocation)
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager = permissionManager,
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = destinationSelectionRepository,
                    destinationPreviewRepository = destinationPreviewRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )
            val destination =
                PlaceDestination(
                    placeId = "preview-restart",
                    name = "Busan Tower",
                    address = "1 Yongdusan-gil, Busan",
                    latitude = 35.1000,
                    longitude = 129.0320,
                    category = PlaceCategory.TOURIST_SPOT,
                )

            viewModel.onRouteStarted()
            advanceUntilIdle()
            viewModel.onRouteStopped()

            destinationPreviewRepository.requestPreview(
                destination = destination,
                accessibilityTagKeys = listOf("elevator"),
            )
            advanceUntilIdle()

            viewModel.onRouteStarted()
            advanceUntilIdle()

            assertNull(destinationSelectionRepository.selectedDestination.value)
            assertEquals(MapCameraSource.SEARCH_RESULT, viewModel.uiState.value.cameraTarget.source)
            assertEquals(destination.latitude, viewModel.uiState.value.cameraTarget.center.latitude, 0.0)
            assertEquals(destination.longitude, viewModel.uiState.value.cameraTarget.center.longitude, 0.0)
            assertEquals(destination.latitude, viewModel.uiState.value.selectedMapPinCoordinate?.latitude ?: 0.0, 0.0)
            assertEquals(destination.longitude, viewModel.uiState.value.selectedMapPinCoordinate?.longitude ?: 0.0, 0.0)
            assertTrue(viewModel.uiState.value.facilityDetailSheetState.isVisible)
        }

    @Test
    fun `search preview keeps preview camera when map route restarts without location permission`() =
        runTest {
            val permissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied)
            val locationManager = FakeCurrentLocationManager()
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager = permissionManager,
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = destinationSelectionRepository,
                    destinationPreviewRepository = destinationPreviewRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )
            val destination =
                PlaceDestination(
                    placeId = "preview-no-permission",
                    name = "Busan Tower",
                    address = "1 Yongdusan-gil, Busan",
                    latitude = 35.1000,
                    longitude = 129.0320,
                    category = PlaceCategory.TOURIST_SPOT,
                )

            viewModel.onRouteStarted()
            advanceUntilIdle()
            viewModel.onRouteStopped()

            destinationPreviewRepository.requestPreview(
                destination = destination,
                accessibilityTagKeys = listOf("elevator"),
            )
            advanceUntilIdle()

            viewModel.onRouteStarted()
            advanceUntilIdle()

            assertNull(destinationSelectionRepository.selectedDestination.value)
            assertEquals(MapCameraSource.SEARCH_RESULT, viewModel.uiState.value.cameraTarget.source)
            assertEquals(destination.latitude, viewModel.uiState.value.cameraTarget.center.latitude, 0.0)
            assertEquals(destination.longitude, viewModel.uiState.value.cameraTarget.center.longitude, 0.0)
            assertEquals(destination.latitude, viewModel.uiState.value.selectedMapPinCoordinate?.latitude ?: 0.0, 0.0)
            assertEquals(destination.longitude, viewModel.uiState.value.selectedMapPinCoordinate?.longitude ?: 0.0, 0.0)
            assertTrue(viewModel.uiState.value.facilityDetailSheetState.isVisible)
        }

    @Test
    fun `search preview ignores stale programmatic current location camera callback`() =
        runTest {
            val currentLocation = testLocationSnapshot(latitude = 35.1796, longitude = 129.0756)
            val permissionManager =
                FakeLocationPermissionManager(
                    initialState = LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE),
                )
            val locationManager = FakeCurrentLocationManager(initialLocation = currentLocation)
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager = permissionManager,
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = destinationSelectionRepository,
                    destinationPreviewRepository = destinationPreviewRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )
            val destination =
                PlaceDestination(
                    placeId = "preview-stale-callback",
                    name = "Busan Tower",
                    address = "1 Yongdusan-gil, Busan",
                    latitude = 35.1000,
                    longitude = 129.0320,
                    category = PlaceCategory.TOURIST_SPOT,
                )

            viewModel.onRouteStarted()
            advanceUntilIdle()

            destinationPreviewRepository.requestPreview(destination = destination)
            advanceUntilIdle()

            viewModel.onAction(
                MapUiAction.ViewportCameraChanged(
                    center = MapCoordinate(latitude = currentLocation.latitude, longitude = currentLocation.longitude),
                    zoomLevel = viewModel.uiState.value.cameraTarget.resolvedZoomLevel(),
                    isUserGesture = false,
                ),
            )
            advanceUntilIdle()

            assertEquals(MapCameraSource.SEARCH_RESULT, viewModel.uiState.value.cameraTarget.source)
            assertEquals(destination.latitude, viewModel.uiState.value.cameraTarget.center.latitude, 0.0)
            assertEquals(destination.longitude, viewModel.uiState.value.cameraTarget.center.longitude, 0.0)
        }

    @Test
    fun `search preview keeps map pin state during programmatic offscreen callback`() =
        runTest {
            val permissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied)
            val locationManager = FakeCurrentLocationManager()
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager = permissionManager,
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = destinationSelectionRepository,
                    destinationPreviewRepository = destinationPreviewRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )
            val destination =
                PlaceDestination(
                    placeId = "preview-programmatic-offscreen",
                    name = "Busan Tower",
                    address = "1 Yongdusan-gil, Busan",
                    latitude = 35.1000,
                    longitude = 129.0320,
                    category = PlaceCategory.TOURIST_SPOT,
                )

            destinationPreviewRepository.requestPreview(destination = destination)
            advanceUntilIdle()

            viewModel.onAction(
                MapUiAction.ViewportCameraChanged(
                    center = MapCoordinate(latitude = destination.latitude, longitude = destination.longitude),
                    zoomLevel = viewModel.uiState.value.cameraTarget.resolvedZoomLevel(),
                    isUserGesture = false,
                    isSelectedMapPinVisibleInViewport = false,
                ),
            )
            advanceUntilIdle()

            assertEquals(MapCameraSource.SEARCH_RESULT, viewModel.uiState.value.cameraTarget.source)
            assertEquals(destination.latitude, viewModel.uiState.value.selectedMapPinCoordinate?.latitude ?: 0.0, 0.0)
            assertEquals(destination.longitude, viewModel.uiState.value.selectedMapPinCoordinate?.longitude ?: 0.0, 0.0)
            assertEquals(destination, viewModel.uiState.value.facilityDetailSheetState.destinationPreview?.destination)
            assertTrue(viewModel.uiState.value.facilityDetailSheetState.isVisible)
        }

    @Test
    fun `search preview keeps map pin state and compacts sheet when user moves selected pin offscreen`() =
        runTest {
            val permissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied)
            val locationManager = FakeCurrentLocationManager()
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager = permissionManager,
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = destinationSelectionRepository,
                    destinationPreviewRepository = destinationPreviewRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )
            val destination =
                PlaceDestination(
                    placeId = "preview-offscreen",
                    name = "Busan Tower",
                    address = "1 Yongdusan-gil, Busan",
                    latitude = 35.1000,
                    longitude = 129.0320,
                    category = PlaceCategory.TOURIST_SPOT,
                )

            destinationPreviewRepository.requestPreview(destination = destination)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.facilityDetailSheetState.isVisible)
            assertEquals(destination.latitude, viewModel.uiState.value.selectedMapPinCoordinate?.latitude ?: 0.0, 0.0)

            viewModel.onAction(
                MapUiAction.ViewportCameraChanged(
                    center = MapCoordinate(latitude = 35.1796, longitude = 129.0756),
                    zoomLevel = viewModel.uiState.value.cameraTarget.resolvedZoomLevel(),
                    isUserGesture = true,
                    isSelectedMapPinVisibleInViewport = false,
                ),
            )
            advanceUntilIdle()

            assertEquals(destination.latitude, viewModel.uiState.value.selectedMapPinCoordinate?.latitude ?: 0.0, 0.0)
            assertEquals(destination.longitude, viewModel.uiState.value.selectedMapPinCoordinate?.longitude ?: 0.0, 0.0)
            assertEquals(destination, viewModel.uiState.value.facilityDetailSheetState.destinationPreview?.destination)
            assertTrue(viewModel.uiState.value.facilityDetailSheetState.isVisible)
            assertEquals(
                MapFacilityDetailSheetPresentation.COMPACT,
                viewModel.uiState.value.facilityDetailSheetState.presentation,
            )
        }

    @Test
    fun `background map tap clears selected preview state`() =
        runTest {
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    destinationPreviewRepository = destinationPreviewRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )
            val destination =
                PlaceDestination(
                    placeId = "preview-background-clear",
                    name = "Busan Tower",
                    address = "1 Yongdusan-gil, Busan",
                    latitude = 35.1000,
                    longitude = 129.0320,
                    category = PlaceCategory.TOURIST_SPOT,
                )

            destinationPreviewRepository.requestPreview(destination = destination)
            advanceUntilIdle()

            viewModel.onAction(MapUiAction.BackgroundMapTapped)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.selectedMapPinCoordinate)
            assertFalse(viewModel.uiState.value.facilityDetailSheetState.isVisible)
            assertEquals(
                MapFacilityDetailSheetPresentation.EXPANDED,
                viewModel.uiState.value.facilityDetailSheetState.presentation,
            )
        }

    @Test
    fun `compact facility detail expansion returns sheet to expanded without moving camera`() =
        runTest {
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    destinationPreviewRepository = destinationPreviewRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )
            val destination =
                PlaceDestination(
                    placeId = "preview-compact-expand",
                    name = "Busan Tower",
                    address = "1 Yongdusan-gil, Busan",
                    latitude = 35.1000,
                    longitude = 129.0320,
                    category = PlaceCategory.TOURIST_SPOT,
                )

            destinationPreviewRepository.requestPreview(destination = destination)
            advanceUntilIdle()
            viewModel.onAction(
                MapUiAction.ViewportCameraChanged(
                    center = MapCoordinate(latitude = 35.1796, longitude = 129.0756),
                    zoomLevel = viewModel.uiState.value.cameraTarget.resolvedZoomLevel(),
                    isUserGesture = true,
                    isSelectedMapPinVisibleInViewport = false,
                ),
            )
            advanceUntilIdle()
            val cameraBeforeExpand = viewModel.uiState.value.cameraTarget

            viewModel.onAction(MapUiAction.FacilityDetailExpanded)
            advanceUntilIdle()

            assertEquals(
                MapFacilityDetailSheetPresentation.EXPANDED,
                viewModel.uiState.value.facilityDetailSheetState.presentation,
            )
            assertEquals(cameraBeforeExpand, viewModel.uiState.value.cameraTarget)
            assertEquals(destination.latitude, viewModel.uiState.value.selectedMapPinCoordinate?.latitude ?: 0.0, 0.0)
        }

    @Test
    fun `search preview CTA confirms selection and dismissal does not mutate route state`() =
        runTest {
            val permissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied)
            val locationManager = FakeCurrentLocationManager()
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager = permissionManager,
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = destinationSelectionRepository,
                    destinationPreviewRepository = destinationPreviewRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )
            val destination =
                PlaceDestination(
                    placeId = "preview-2",
                    name = "Busan Station",
                    address = "206 Jungang-daero, Busan",
                    latitude = 35.1151,
                    longitude = 129.0414,
                    category = PlaceCategory.PUBLIC_OFFICE,
                )

            destinationPreviewRepository.requestPreview(destination = destination)
            advanceUntilIdle()
            viewModel.onAction(MapUiAction.FacilityDetailDismissed)
            advanceUntilIdle()

            assertNull(destinationSelectionRepository.selectedDestination.value)
            assertFalse(viewModel.uiState.value.facilityDetailSheetState.isVisible)
            assertNull(viewModel.uiState.value.selectedMapPinCoordinate)

            destinationPreviewRepository.requestPreview(
                destination = destination,
                editingTarget = RouteEditingTarget.DESTINATION,
            )
            advanceUntilIdle()
            viewModel.onAction(MapUiAction.FacilitySetRouteEndpointClicked(RouteEditingTarget.DESTINATION))
            advanceUntilIdle()

            val event =
                withTimeoutOrNull(100) {
                    viewModel.uiEvent.first()
                }

            assertEquals(destination, destinationSelectionRepository.selectedDestination.value)
            assertEquals(MapUiEvent.NavigateToRouteSetting(), event)
            assertFalse(viewModel.uiState.value.facilityDetailSheetState.isVisible)
            assertNull(viewModel.uiState.value.selectedMapPinCoordinate)
        }

    @Test
    fun `search preview origin CTA navigates to route setting without destination`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    destinationPreviewRepository = destinationPreviewRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )
            val origin =
                PlaceDestination(
                    placeId = "origin-1",
                    name = "Origin Place",
                    address = "1 Origin-ro, Busan",
                    latitude = 35.1300,
                    longitude = 129.0500,
                    category = PlaceCategory.OTHER,
                )

            destinationPreviewRepository.requestPreview(
                destination = origin,
                editingTarget = RouteEditingTarget.ORIGIN,
            )
            advanceUntilIdle()
            viewModel.onAction(MapUiAction.FacilitySetRouteEndpointClicked(RouteEditingTarget.ORIGIN))
            advanceUntilIdle()

            val event =
                withTimeoutOrNull(100) {
                    viewModel.uiEvent.first()
                }

            assertEquals(origin, destinationSelectionRepository.selectedOrigin.value)
            assertNull(destinationSelectionRepository.selectedDestination.value)
            assertEquals(MapUiEvent.NavigateToRouteSetting(), event)
        }

    @Test
    fun `search preview destination CTA keeps existing origin`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val origin =
                PlaceDestination(
                    placeId = "origin-keep",
                    name = "Existing Origin",
                    address = "1 Origin-ro, Busan",
                    latitude = 35.1200,
                    longitude = 129.0400,
                    category = PlaceCategory.OTHER,
                )
            val destination =
                PlaceDestination(
                    placeId = "destination-new",
                    name = "New Destination",
                    address = "2 Destination-ro, Busan",
                    latitude = 35.1400,
                    longitude = 129.0600,
                    category = PlaceCategory.PUBLIC_OFFICE,
                )
            destinationSelectionRepository.updateSelectedOrigin(origin)
            val viewModel =
                MapViewModel(
                    locationPermissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    destinationPreviewRepository = destinationPreviewRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            destinationPreviewRepository.requestPreview(destination = destination)
            advanceUntilIdle()
            viewModel.onAction(MapUiAction.FacilitySetRouteEndpointClicked(RouteEditingTarget.DESTINATION))
            advanceUntilIdle()

            val event =
                withTimeoutOrNull(100) {
                    viewModel.uiEvent.first()
                }

            assertEquals(origin, destinationSelectionRepository.selectedOrigin.value)
            assertEquals(destination, destinationSelectionRepository.selectedDestination.value)
            assertEquals(MapUiEvent.NavigateToRouteSetting(), event)
        }

    @Test
    fun `origin CTA navigates to route setting when destination already exists`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val origin =
                PlaceDestination(
                    placeId = "origin-new",
                    name = "New Origin",
                    address = "1 Origin-ro, Busan",
                    latitude = 35.1200,
                    longitude = 129.0400,
                    category = PlaceCategory.OTHER,
                )
            val destination =
                PlaceDestination(
                    placeId = "destination-existing",
                    name = "Existing Destination",
                    address = "2 Destination-ro, Busan",
                    latitude = 35.1400,
                    longitude = 129.0600,
                    category = PlaceCategory.PUBLIC_OFFICE,
                )
            destinationSelectionRepository.updateSelectedDestination(destination)
            val viewModel =
                MapViewModel(
                    locationPermissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    destinationPreviewRepository = destinationPreviewRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            destinationPreviewRepository.requestPreview(
                destination = origin,
                editingTarget = RouteEditingTarget.ORIGIN,
            )
            advanceUntilIdle()
            viewModel.onAction(MapUiAction.FacilitySetRouteEndpointClicked(RouteEditingTarget.ORIGIN))
            advanceUntilIdle()

            val event =
                withTimeoutOrNull(100) {
                    viewModel.uiEvent.first()
                }

            assertEquals(origin, destinationSelectionRepository.selectedOrigin.value)
            assertEquals(destination, destinationSelectionRepository.selectedDestination.value)
            assertEquals(MapUiEvent.NavigateToRouteSetting(), event)
        }

    @Test
    fun `map tapped place destination CTA opens route setting`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val origin =
                PlaceDestination(
                    placeId = "origin-keep-detail",
                    name = "Existing Origin",
                    address = "1 Origin-ro, Busan",
                    latitude = 35.1200,
                    longitude = 129.0400,
                    category = PlaceCategory.OTHER,
                )
            val tappedCoordinate = MapCoordinate(latitude = 35.1151, longitude = 129.0414)
            val mapTapDetail =
                testMapTappedDetail(
                    bookmarkTargetId = "kakao:poi-123",
                    detailType = MapPlaceDetailType.EXTERNAL_POI,
                    provider = "KAKAO",
                    providerPlaceId = "poi-123",
                    name = "Busan Station",
                    category = PlaceCategory.PUBLIC_OFFICE,
                    address = "206 Jungang-daero, Busan",
                    latitude = tappedCoordinate.latitude,
                    longitude = tappedCoordinate.longitude,
                )
            val viewModel =
                MapViewModel(
                    locationPermissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    placesRepository = FakePlacesRepository(mapTapDetail = mapTapDetail),
                )
            destinationSelectionRepository.updateSelectedOrigin(origin)

            viewModel.onAction(
                MapUiAction.MapTapped(
                    MapTapPayload(
                        coordinate = tappedCoordinate,
                        clickType = MapTapClickType.POI,
                        provider = "KAKAO",
                        providerPlaceId = "poi-123",
                        nameHint = "Busan Station",
                    ),
                ),
            )
            advanceUntilIdle()
            viewModel.onAction(MapUiAction.FacilitySetRouteEndpointClicked(RouteEditingTarget.DESTINATION))
            advanceUntilIdle()

            val event =
                withTimeoutOrNull(100) {
                    viewModel.uiEvent.first()
                }

            assertEquals(origin, destinationSelectionRepository.selectedOrigin.value)
            val selectedDestination = destinationSelectionRepository.selectedDestination.value
            assertEquals(mapTapDetail.bookmarkTargetId, selectedDestination?.placeId)
            assertEquals(mapTapDetail.name, selectedDestination?.name)
            assertEquals(mapTapDetail.address, selectedDestination?.address)
            assertEquals(mapTapDetail.latitude, selectedDestination?.latitude ?: 0.0, 0.0)
            assertEquals(mapTapDetail.longitude, selectedDestination?.longitude ?: 0.0, 0.0)
            assertEquals(MapUiEvent.NavigateToRouteSetting(), event)
        }

    @Test
    fun `home reentry resets destination and preview state to current location when available`() =
        runTest {
            val currentLocation = testLocationSnapshot(latitude = 35.1796, longitude = 129.0756)
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val selectedOrigin =
                PlaceDestination(
                    placeId = "origin-home-reset",
                    name = "Busan City Hall",
                    address = "1001 Jungang-daero, Busan",
                    latitude = 35.1798,
                    longitude = 129.0750,
                    category = PlaceCategory.PUBLIC_OFFICE,
                )
            val selectedDestination = testDestination()
            val previewDestination =
                PlaceDestination(
                    placeId = "preview-home-reset",
                    name = "Busan Tower",
                    address = "1 Yongdusan-gil, Busan",
                    latitude = 35.1000,
                    longitude = 129.0320,
                    category = PlaceCategory.TOURIST_SPOT,
                )
            destinationSelectionRepository.updateSelectedOrigin(selectedOrigin)
            destinationSelectionRepository.updateSelectedDestination(selectedDestination)
            destinationSelectionRepository.setEditingTarget(RouteEditingTarget.ORIGIN)
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(
                            initialState = LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE),
                        ),
                    currentLocationManager = FakeCurrentLocationManager(initialLocation = currentLocation),
                    destinationSelectionRepository = destinationSelectionRepository,
                    destinationPreviewRepository = destinationPreviewRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            advanceUntilIdle()
            destinationPreviewRepository.requestPreview(
                destination = previewDestination,
                accessibilityTagKeys = listOf("elevator"),
            )
            advanceUntilIdle()

            viewModel.onHomeReentered()
            advanceUntilIdle()

            assertNull(destinationSelectionRepository.selectedDestination.value)
            assertNull(destinationSelectionRepository.selectedOrigin.value)
            assertEquals(RouteEditingTarget.DESTINATION, destinationSelectionRepository.editingTarget.value)
            assertNull(viewModel.uiState.value.selectedDestination)
            assertNull(viewModel.uiState.value.selectedOrigin)
            assertEquals(RouteEditingTarget.DESTINATION, viewModel.uiState.value.routeEditingTarget)
            assertFalse(viewModel.uiState.value.facilityDetailSheetState.isVisible)
            assertNull(viewModel.uiState.value.facilityDetailSheetState.destinationPreview)
            assertNull(viewModel.uiState.value.selectedMapPinCoordinate)
            assertEquals(0L, viewModel.uiState.value.rendererSessionKey)
            assertEquals(MapCameraSource.CURRENT_LOCATION, viewModel.uiState.value.cameraTarget.source)
            assertEquals(currentLocation.latitude, viewModel.uiState.value.cameraTarget.center.latitude, 0.0)
            assertEquals(currentLocation.longitude, viewModel.uiState.value.cameraTarget.center.longitude, 0.0)
        }

    @Test
    fun `home reentry falls back without restoring selected destination when current location is unavailable`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val selectedDestination = testDestination()
            destinationSelectionRepository.updateSelectedDestination(selectedDestination)
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(
                            initialState = LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE),
                        ),
                    currentLocationManager = FakeCurrentLocationManager(initialLocation = null),
                    destinationSelectionRepository = destinationSelectionRepository,
                    destinationPreviewRepository = destinationPreviewRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            advanceUntilIdle()

            viewModel.onHomeReentered()
            advanceUntilIdle()

            assertNull(destinationSelectionRepository.selectedDestination.value)
            assertNull(destinationSelectionRepository.selectedOrigin.value)
            assertNull(viewModel.uiState.value.selectedDestination)
            assertNull(viewModel.uiState.value.selectedOrigin)
            assertEquals(0L, viewModel.uiState.value.rendererSessionKey)
            assertEquals(MapCameraSource.DEFAULT_BUSAN, viewModel.uiState.value.cameraTarget.source)
            assertEquals(MapDefaults.BUSAN_CENTER.latitude, viewModel.uiState.value.cameraTarget.center.latitude, 0.0)
            assertEquals(MapDefaults.BUSAN_CENTER.longitude, viewModel.uiState.value.cameraTarget.center.longitude, 0.0)
        }

    @Test
    fun `home reentry keeps prior current location camera while refreshed location is pending`() =
        runTest {
            val currentLocation = testLocationSnapshot(latitude = 35.1796, longitude = 129.0756)
            val locationManager = FakeCurrentLocationManager(initialLocation = currentLocation)
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(
                            initialState = LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE),
                        ),
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            viewModel.onRouteStarted()
            advanceUntilIdle()

            assertEquals(MapCameraSource.CURRENT_LOCATION, viewModel.uiState.value.cameraTarget.source)
            val refreshCountBeforeReentry = locationManager.refreshLatestLocationCallCount

            locationManager.updateLocation(null)
            viewModel.onHomeReentered()
            runCurrent()

            assertEquals(refreshCountBeforeReentry + 1, locationManager.refreshLatestLocationCallCount)
            assertEquals(0L, viewModel.uiState.value.rendererSessionKey)
            assertEquals(MapCameraSource.CURRENT_LOCATION, viewModel.uiState.value.cameraTarget.source)
            assertEquals(currentLocation.latitude, viewModel.uiState.value.cameraTarget.center.latitude, 0.0)
            assertEquals(currentLocation.longitude, viewModel.uiState.value.cameraTarget.center.longitude, 0.0)
            assertEquals(
                MapLocationStatus.Unavailable(MapLocationUnavailableReason.CURRENT_LOCATION_UNAVAILABLE),
                viewModel.uiState.value.locationStatus,
            )
        }

    @Test
    fun `map route refreshes current location when updates stop arriving`() =
        runTest {
            var now = 10_000L
            val locationManager =
                FakeCurrentLocationManager(
                    initialLocation =
                        testLocationSnapshot(
                            latitude = 35.1796,
                            longitude = 129.0756,
                            recordedAtEpochMillis = now,
                        ),
                )
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(
                            initialState = LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE),
                        ),
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    nowEpochMillis = { now },
                    enableLocationRecoveryWatchdog = true,
                )

            viewModel.onRouteStarted()
            runCurrent()
            try {
                val refreshCountAfterStart = locationManager.refreshLatestLocationCallCount

                now += 10_000L
                advanceTimeBy(10_000L)
                runCurrent()

                assertEquals(refreshCountAfterStart + 1, locationManager.refreshLatestLocationCallCount)
            } finally {
                viewModel.onRouteStopped()
            }
        }

    @Test
    fun `map route restarts location updates when no fresh update arrives past recovery threshold`() =
        runTest {
            var now = 10_000L
            val locationManager =
                FakeCurrentLocationManager(
                    initialLocation =
                        testLocationSnapshot(
                            latitude = 35.1796,
                            longitude = 129.0756,
                            recordedAtEpochMillis = now,
                        ),
                )
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(
                            initialState = LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE),
                        ),
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    nowEpochMillis = { now },
                    enableLocationRecoveryWatchdog = true,
                )

            viewModel.onRouteStarted()
            runCurrent()
            try {
                val startCountAfterRouteStart = locationManager.startLocationUpdatesCallCount
                val refreshCountAfterRouteStart = locationManager.refreshLatestLocationCallCount

                now += 30_000L
                advanceTimeBy(30_000L)
                runCurrent()

                assertEquals(startCountAfterRouteStart + 1, locationManager.startLocationUpdatesCallCount)
                assertTrue(locationManager.refreshLatestLocationCallCount > refreshCountAfterRouteStart)
            } finally {
                viewModel.onRouteStopped()
            }
        }

    @Test
    fun `home reentry after route stop keeps prior current location camera while refreshed location is pending`() =
        runTest {
            val currentLocation = testLocationSnapshot(latitude = 35.1796, longitude = 129.0756)
            val locationManager = FakeCurrentLocationManager(initialLocation = currentLocation)
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(
                            initialState = LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE),
                        ),
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            viewModel.onRouteStarted()
            advanceUntilIdle()
            assertEquals(MapCameraSource.CURRENT_LOCATION, viewModel.uiState.value.cameraTarget.source)
            val refreshCountBeforeReentry = locationManager.refreshLatestLocationCallCount

            viewModel.onRouteStopped()
            locationManager.updateLocation(null)

            viewModel.onHomeReentered()
            runCurrent()

            assertEquals(refreshCountBeforeReentry + 1, locationManager.refreshLatestLocationCallCount)
            assertEquals(0L, viewModel.uiState.value.rendererSessionKey)
            assertEquals(MapCameraSource.CURRENT_LOCATION, viewModel.uiState.value.cameraTarget.source)
            assertEquals(currentLocation.latitude, viewModel.uiState.value.cameraTarget.center.latitude, 0.0)
            assertEquals(currentLocation.longitude, viewModel.uiState.value.cameraTarget.center.longitude, 0.0)
            assertEquals(
                MapLocationStatus.Loading,
                viewModel.uiState.value.locationStatus,
            )
        }

    @Test
    fun `location updates do not override selected destination until recenter`() =
        runTest {
            val initialLocation = testLocationSnapshot(latitude = 35.1000, longitude = 129.1000)
            val updatedLocation = testLocationSnapshot(latitude = 35.2000, longitude = 129.2000)
            val permissionManager =
                FakeLocationPermissionManager(
                    initialState = LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE),
                )
            val locationManager = FakeCurrentLocationManager(initialLocation = initialLocation)
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager = permissionManager,
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = destinationSelectionRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )
            val destination = testDestination()

            viewModel.onRouteStarted()
            advanceUntilIdle()

            destinationSelectionRepository.updateSelectedDestination(destination)
            advanceUntilIdle()

            locationManager.updateLocation(updatedLocation)
            advanceUntilIdle()

            assertEquals(MapCameraSource.SEARCH_RESULT, viewModel.uiState.value.cameraTarget.source)
            assertEquals(destination.latitude, viewModel.uiState.value.cameraTarget.center.latitude, 0.0)
            assertEquals(destination.longitude, viewModel.uiState.value.cameraTarget.center.longitude, 0.0)

            viewModel.onAction(MapUiAction.LocationActionClicked)
            advanceUntilIdle()

            assertEquals(MapCameraSource.CURRENT_LOCATION, viewModel.uiState.value.cameraTarget.source)
            assertEquals(updatedLocation.latitude, viewModel.uiState.value.cameraTarget.center.latitude, 0.0)
            assertEquals(updatedLocation.longitude, viewModel.uiState.value.cameraTarget.center.longitude, 0.0)
            assertEquals(destination, viewModel.uiState.value.selectedDestination)
        }

    @Test
    fun `reselecting same destination recenters map after current location recenter`() =
        runTest {
            val currentLocation = testLocationSnapshot(latitude = 35.1500, longitude = 129.1500)
            val permissionManager =
                FakeLocationPermissionManager(
                    initialState = LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE),
                )
            val locationManager = FakeCurrentLocationManager(initialLocation = currentLocation)
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager = permissionManager,
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = destinationSelectionRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )
            val destination = testDestination()

            viewModel.onRouteStarted()
            advanceUntilIdle()

            destinationSelectionRepository.updateSelectedDestination(destination)
            advanceUntilIdle()
            viewModel.onAction(MapUiAction.LocationActionClicked)
            advanceUntilIdle()

            destinationSelectionRepository.updateSelectedDestination(destination)
            advanceUntilIdle()

            assertEquals(MapCameraSource.SEARCH_RESULT, viewModel.uiState.value.cameraTarget.source)
            assertEquals(destination.latitude, viewModel.uiState.value.cameraTarget.center.latitude, 0.0)
            assertEquals(destination.longitude, viewModel.uiState.value.cameraTarget.center.longitude, 0.0)
        }

    @Test
    fun `current location button stays visually inactive on initial auto location sync`() =
        runTest {
            val currentLocation = testLocationSnapshot(latitude = 35.1500, longitude = 129.1500)
            val permissionManager =
                FakeLocationPermissionManager(
                    initialState = LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE),
                )
            val locationManager = FakeCurrentLocationManager(initialLocation = currentLocation)
            val viewModel =
                MapViewModel(
                    locationPermissionManager = permissionManager,
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            viewModel.onRouteStarted()
            advanceUntilIdle()

            assertEquals(MapRecenterButtonState.ENABLED, viewModel.uiState.value.recenterButtonState)
            assertFalse(viewModel.uiState.value.isRecenterButtonActive)
        }

    @Test
    fun `stale last known location does not center map on route start`() =
        runTest {
            val staleLocation =
                testLocationSnapshot(
                    latitude = 35.1500,
                    longitude = 129.1500,
                    recordedAtEpochMillis = 1_000L,
                )
            val permissionManager =
                FakeLocationPermissionManager(
                    initialState = LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE),
                )
            val locationManager = FakeCurrentLocationManager(initialLocation = staleLocation)
            val viewModel =
                MapViewModel(
                    locationPermissionManager = permissionManager,
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            viewModel.onRouteStarted()
            runCurrent()

            assertEquals(MapCameraSource.DEFAULT_BUSAN, viewModel.uiState.value.cameraTarget.source)
            assertEquals(MapDefaults.BUSAN_CENTER.latitude, viewModel.uiState.value.cameraTarget.center.latitude, 0.0)
            assertEquals(MapDefaults.BUSAN_CENTER.longitude, viewModel.uiState.value.cameraTarget.center.longitude, 0.0)
            assertEquals(MapRecenterButtonState.RETRY, viewModel.uiState.value.recenterButtonState)
        }

    @Test
    fun `current location button becomes visually active after explicit recenter tap`() =
        runTest {
            val currentLocation = testLocationSnapshot(latitude = 35.1500, longitude = 129.1500)
            val permissionManager =
                FakeLocationPermissionManager(
                    initialState = LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE),
                )
            val locationManager = FakeCurrentLocationManager(initialLocation = currentLocation)
            val viewModel =
                MapViewModel(
                    locationPermissionManager = permissionManager,
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            viewModel.onRouteStarted()
            advanceUntilIdle()
            viewModel.onAction(MapUiAction.LocationActionClicked)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isRecenterButtonActive)
        }

    @Test
    fun `active current location follow increments camera request on location update`() =
        runTest {
            val currentLocation = testLocationSnapshot(latitude = 35.1500, longitude = 129.1500)
            val updatedLocation = testLocationSnapshot(latitude = 35.1508, longitude = 129.1508)
            val permissionManager =
                FakeLocationPermissionManager(
                    initialState = LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE),
                )
            val locationManager = FakeCurrentLocationManager(initialLocation = currentLocation)
            val viewModel =
                MapViewModel(
                    locationPermissionManager = permissionManager,
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            viewModel.onRouteStarted()
            advanceUntilIdle()
            viewModel.onAction(MapUiAction.LocationActionClicked)
            advanceUntilIdle()
            val followedCamera = viewModel.uiState.value.cameraTarget

            locationManager.updateLocation(updatedLocation)
            advanceUntilIdle()

            val updatedCamera = viewModel.uiState.value.cameraTarget
            assertTrue(viewModel.uiState.value.isRecenterButtonActive)
            assertEquals(followedCamera.requestId + 1L, updatedCamera.requestId)
            assertEquals(updatedLocation.latitude, updatedCamera.center.latitude, 0.0)
            assertEquals(updatedLocation.longitude, updatedCamera.center.longitude, 0.0)
        }

    @Test
    fun `programmatic viewport camera change keeps explicit recenter active`() =
        runTest {
            val currentLocation = testLocationSnapshot(latitude = 35.1500, longitude = 129.1500)
            val permissionManager =
                FakeLocationPermissionManager(
                    initialState = LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE),
                )
            val locationManager = FakeCurrentLocationManager(initialLocation = currentLocation)
            val viewModel =
                MapViewModel(
                    locationPermissionManager = permissionManager,
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            viewModel.onRouteStarted()
            advanceUntilIdle()
            viewModel.onAction(MapUiAction.LocationActionClicked)
            advanceUntilIdle()

            viewModel.onAction(
                MapUiAction.ViewportCameraChanged(
                    center = MapCoordinate(latitude = 35.1501, longitude = 129.1501),
                    zoomLevel = 17,
                    isUserGesture = false,
                ),
            )
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isRecenterButtonActive)
        }

    @Test
    fun `user viewport camera change clears explicit recenter active`() =
        runTest {
            val currentLocation = testLocationSnapshot(latitude = 35.1500, longitude = 129.1500)
            val permissionManager =
                FakeLocationPermissionManager(
                    initialState = LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE),
                )
            val locationManager = FakeCurrentLocationManager(initialLocation = currentLocation)
            val viewModel =
                MapViewModel(
                    locationPermissionManager = permissionManager,
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            viewModel.onRouteStarted()
            advanceUntilIdle()
            viewModel.onAction(MapUiAction.LocationActionClicked)
            advanceUntilIdle()

            viewModel.onAction(
                MapUiAction.ViewportCameraChanged(
                    center = MapCoordinate(latitude = 35.1510, longitude = 129.1510),
                    zoomLevel = 16,
                    isUserGesture = true,
                ),
            )
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isRecenterButtonActive)
        }

    @Test
    fun `zoom in action increases camera zoom level and request id`() =
        runTest {
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            advanceUntilIdle()

            val initialCamera = createKakaoCameraRenderState(viewModel.uiState.value.cameraTarget)

            viewModel.onAction(MapUiAction.ZoomInClicked)
            advanceUntilIdle()

            val updatedCamera = createKakaoCameraRenderState(viewModel.uiState.value.cameraTarget)

            assertEquals(initialCamera.zoomLevel + 1, updatedCamera.zoomLevel)
            assertEquals(initialCamera.requestId + 1L, updatedCamera.requestId)
            assertEquals(initialCamera.latitude, updatedCamera.latitude, 0.0)
            assertEquals(initialCamera.longitude, updatedCamera.longitude, 0.0)
        }

    @Test
    fun `zoom action keeps last viewport center after manual camera move`() =
        runTest {
            val currentLocation = testLocationSnapshot(latitude = 35.1500, longitude = 129.1500)
            val permissionManager =
                FakeLocationPermissionManager(
                    initialState = LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE),
                )
            val locationManager = FakeCurrentLocationManager(initialLocation = currentLocation)
            val viewModel =
                MapViewModel(
                    locationPermissionManager = permissionManager,
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )
            val viewportCenter = MapCoordinate(latitude = 35.1705, longitude = 129.0832)

            viewModel.onRouteStarted()
            advanceUntilIdle()

            viewModel.onAction(
                MapUiAction.ViewportCameraChanged(
                    center = viewportCenter,
                    zoomLevel = 14,
                    isUserGesture = true,
                ),
            )
            advanceUntilIdle()

            viewModel.onAction(MapUiAction.ZoomInClicked)
            advanceUntilIdle()

            val updatedCamera = createKakaoCameraRenderState(viewModel.uiState.value.cameraTarget)

            assertEquals(viewportCenter.latitude, updatedCamera.latitude, 0.0)
            assertEquals(viewportCenter.longitude, updatedCamera.longitude, 0.0)
            assertEquals(15, updatedCamera.zoomLevel)
        }

    @Test
    fun `browse state initializes with no active filters and hides markers by default`() =
        runTest {
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            advanceUntilIdle()

            assertEquals(17, viewModel.uiState.value.markerOverlayState.totalMarkerCount)
            assertEquals(0, viewModel.uiState.value.markerOverlayState.visibleMarkerCount)
            assertFalse(viewModel.uiState.value.markerFilterState.selection.isShowingAllCategories)
            assertFalse(viewModel.uiState.value.markerOverlayState.isEmptyResult)
            assertEquals(10, viewModel.uiState.value.markerFilterState.categoryOptions.size)
            assertTrue(viewModel.uiState.value.shortcutFilterState.chips.none { chip -> chip.isSelected })
        }

    @Test
    fun `category filter options prioritize accessibility facilities`() =
        runTest {
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            advanceUntilIdle()

            val categoryOrder =
                viewModel.uiState.value.markerFilterState.categoryOptions
                    .map { option -> option.category }

            assertEquals(
                listOf(
                    FacilityCategory.TOILET,
                    FacilityCategory.ELEVATOR,
                    FacilityCategory.CHARGING_STATION,
                    FacilityCategory.FOOD_CAFE,
                    FacilityCategory.TOURIST_SPOT,
                    FacilityCategory.ACCOMMODATION,
                    FacilityCategory.HEALTHCARE,
                    FacilityCategory.WELFARE,
                    FacilityCategory.PUBLIC_OFFICE,
                    FacilityCategory.BRAILLE_BLOCK,
                ),
                categoryOrder,
            )
        }

    @Test
    fun `toggling category filter from all state shows only selected category markers`() =
        runTest {
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            advanceUntilIdle()

            viewModel.onAction(MapUiAction.MarkerCategoryFilterToggled(FacilityCategory.TOILET))
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.markerOverlayState.visibleMarkerCount)
            assertEquals(
                2,
                viewModel.uiState.value.markerOverlayState.markers.count { marker ->
                    marker.displayState == MapMarkerDisplayState.VISIBLE &&
                        marker.categoryType.category == FacilityCategory.TOILET
                },
            )
            assertTrue(viewModel.uiState.value.markerFilterState.selection.isShowingAllCategories.not())
            assertTrue(
                viewModel.uiState.value.markerFilterState.categoryOptions
                    .first { option -> option.category == FacilityCategory.TOILET }
                    .isSelected,
            )
        }

    @Test
    fun `toggling another category keeps custom multi select state`() =
        runTest {
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            advanceUntilIdle()

            viewModel.onAction(MapUiAction.MarkerCategoryFilterToggled(FacilityCategory.TOILET))
            viewModel.onAction(MapUiAction.MarkerCategoryFilterToggled(FacilityCategory.ELEVATOR))
            advanceUntilIdle()

            assertEquals(4, viewModel.uiState.value.markerOverlayState.visibleMarkerCount)
            assertEquals(
                setOf(FacilityCategory.TOILET, FacilityCategory.ELEVATOR),
                viewModel.uiState.value.markerFilterState.selection.selectedFacilityCategories,
            )
        }

    @Test
    fun `toggling last selected category clears the filter selection`() =
        runTest {
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            advanceUntilIdle()

            viewModel.onAction(MapUiAction.MarkerCategoryFilterToggled(FacilityCategory.TOILET))
            viewModel.onAction(MapUiAction.MarkerCategoryFilterToggled(FacilityCategory.TOILET))
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.markerFilterState.selection.isShowingAllCategories)
            assertTrue(viewModel.uiState.value.markerFilterState.selection.selectedFacilityCategories.isEmpty())
            assertEquals(0, viewModel.uiState.value.markerOverlayState.visibleMarkerCount)
            assertFalse(viewModel.uiState.value.markerOverlayState.isEmptyResult)
        }

    @Test
    fun `marker tap toggles selected marker state`() =
        runTest {
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            advanceUntilIdle()

            val markerId = viewModel.uiState.value.markerOverlayState.markers.first().markerId

            viewModel.onAction(MapUiAction.MarkerTapped(markerId))
            advanceUntilIdle()
            assertEquals(markerId, viewModel.uiState.value.selectedMarkerId)
            assertEquals(markerId, viewModel.uiState.value.facilityDetailSheetState.detail?.facilityId)

            viewModel.onAction(MapUiAction.MarkerTapped(markerId))
            advanceUntilIdle()
            assertEquals(null, viewModel.uiState.value.selectedMarkerId)
            assertEquals(null, viewModel.uiState.value.facilityDetailSheetState.detail)
        }

    @Test
    fun `address map tap clears selected facility without selecting coordinate outside picker`() =
        runTest {
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            advanceUntilIdle()

            val markerId = viewModel.uiState.value.markerOverlayState.markers.first().markerId
            val tappedCoordinate = MapCoordinate(latitude = 35.1775, longitude = 129.0771)

            viewModel.onAction(MapUiAction.MarkerTapped(markerId))
            advanceUntilIdle()
            viewModel.onAction(
                MapUiAction.MapTapped(
                    MapTapPayload(
                        coordinate = tappedCoordinate,
                        clickType = MapTapClickType.POI,
                    ),
                ),
            )
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.selectedMapPinCoordinate)
            assertEquals(null, viewModel.uiState.value.selectedMarkerId)
            assertEquals(null, viewModel.uiState.value.facilityDetailSheetState.detail)
        }

    @Test
    fun `blank address map tap does not request detail and keeps map tap ui hidden`() =
        runTest {
            val tappedCoordinate = MapCoordinate(latitude = 35.1775, longitude = 129.0771)
            val placesRepository =
                FakePlacesRepository(
                    mapTapDetail =
                        testMapTappedDetail(
                            bookmarkTargetId = "external-address:35.1775,129.0771",
                            detailType = MapPlaceDetailType.EXTERNAL_ADDRESS,
                            name = "Selected Address",
                            address = "100 Jungang-daero, Busan",
                            latitude = tappedCoordinate.latitude,
                            longitude = tappedCoordinate.longitude,
                        ),
                )
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    placesRepository = placesRepository,
                )

            advanceUntilIdle()

            viewModel.onAction(
                MapUiAction.MapTapped(
                    MapTapPayload(
                        coordinate = tappedCoordinate,
                    ),
                ),
            )
            advanceUntilIdle()

            assertTrue(placesRepository.mapTapDetailRequests.isEmpty())
            assertNull(viewModel.uiState.value.selectedMapPinCoordinate)
            assertNull(viewModel.uiState.value.facilityDetailSheetState.mapTapDetail)
            assertFalse(viewModel.uiState.value.facilityDetailSheetState.isMapTapDetailLoading)
            assertNull(viewModel.uiState.value.facilityDetailSheetState.mapTapDetailErrorMessage)
            assertFalse(viewModel.uiState.value.facilityDetailSheetState.isVisible)
            assertNull(destinationSelectionRepository.selectedDestination.value)
        }

    @Test
    fun `external poi tap requests provider detail and preserves provider metadata`() =
        runTest {
            val tappedCoordinate = MapCoordinate(latitude = 35.1799, longitude = 129.0752)
            val placesRepository =
                FakePlacesRepository(
                    mapTapDetail =
                        testMapTappedDetail(
                            bookmarkTargetId = "kakao:poi-123",
                            detailType = MapPlaceDetailType.EXTERNAL_POI,
                            provider = "KAKAO",
                            providerPlaceId = "poi-123",
                            name = "Kakao Cafe",
                            providerCategory = "Cafe",
                            address = "10 Cafe-ro, Busan",
                            latitude = tappedCoordinate.latitude,
                            longitude = tappedCoordinate.longitude,
                        ),
                )
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    placesRepository = placesRepository,
                )

            advanceUntilIdle()

            viewModel.onAction(
                MapUiAction.MapTapped(
                    MapTapPayload(
                        coordinate = tappedCoordinate,
                        clickType = MapTapClickType.POI,
                        provider = "KAKAO",
                        providerPlaceId = "poi-123",
                        nameHint = "Cafe Hint",
                    ),
                ),
            )
            advanceUntilIdle()

            val request = placesRepository.mapTapDetailRequests.single()
            assertEquals(MapPlaceClickType.POI, request.clickType)
            assertEquals("KAKAO", request.provider)
            assertEquals("poi-123", request.providerPlaceId)
            assertEquals("Cafe Hint", request.nameHint)
            assertEquals("Cafe Hint", viewModel.uiState.value.facilityDetailSheetState.mapTapNameHint)
            val detail = requireNotNull(viewModel.uiState.value.facilityDetailSheetState.mapTapDetail)
            assertEquals(MapPlaceDetailType.EXTERNAL_POI, detail.detailType)
            assertEquals("kakao:poi-123", detail.bookmarkTargetId)
            assertEquals("KAKAO", detail.provider)
            assertEquals("poi-123", detail.providerPlaceId)
            assertEquals("Cafe", detail.providerCategory)
            assertEquals("Kakao Cafe", detail.name)
        }

    @Test
    fun `external poi tap falls back to tapped coordinate when detail coordinate is invalid`() =
        runTest {
            val tappedCoordinate = MapCoordinate(latitude = 35.1799, longitude = 129.0752)
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val placesRepository =
                FakePlacesRepository(
                    mapTapDetail =
                        testMapTappedDetail(
                            bookmarkTargetId = "kakao:poi-123",
                            detailType = MapPlaceDetailType.EXTERNAL_POI,
                            provider = "KAKAO",
                            providerPlaceId = "poi-123",
                            name = "Kakao Cafe",
                            providerCategory = "Cafe",
                            address = "10 Cafe-ro, Busan",
                            latitude = Double.NaN,
                            longitude = Double.NaN,
                        ),
                )
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    placesRepository = placesRepository,
                )

            advanceUntilIdle()
            viewModel.onAction(
                MapUiAction.MapTapped(
                    MapTapPayload(
                        coordinate = tappedCoordinate,
                        clickType = MapTapClickType.POI,
                        provider = "KAKAO",
                        providerPlaceId = "poi-123",
                        nameHint = "Cafe Hint",
                    ),
                ),
            )
            advanceUntilIdle()

            val detail = requireNotNull(viewModel.uiState.value.facilityDetailSheetState.mapTapDetail)
            assertEquals(tappedCoordinate.latitude, detail.latitude, 0.0)
            assertEquals(tappedCoordinate.longitude, detail.longitude, 0.0)

            viewModel.onAction(MapUiAction.FacilitySetDestinationClicked)
            advanceUntilIdle()

            val event =
                withTimeoutOrNull(100) {
                    viewModel.uiEvent.first()
                }
            val selectedDestination = requireNotNull(destinationSelectionRepository.selectedDestination.value)
            assertEquals(MapUiEvent.NavigateToRouteSetting(), event)
            assertEquals("Kakao Cafe", selectedDestination.name)
            assertEquals(tappedCoordinate.latitude, selectedDestination.latitude, 0.0)
            assertEquals(tappedCoordinate.longitude, selectedDestination.longitude, 0.0)
        }

    @Test
    fun `facility phone click emits dialer event for selected marker detail`() =
        runTest {
            val placesRepository =
                FakePlacesRepository(
                    placeDetailsById =
                        mapOf(
                            "101" to
                                PlaceDetail(
                                    placeId = "101",
                                    name = "Accessible Hotel",
                                    address = "10 Haeundae-ro, Busan",
                                    latitude = 35.1587,
                                    longitude = 129.1604,
                                    category = PlaceCategory.ACCOMMODATION,
                                    phoneNumber = "051-700-1000",
                                ),
                        ),
                )
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    placesRepository = placesRepository,
                )

            advanceUntilIdle()
            viewModel.onAction(MapUiAction.MarkerTapped("101"))
            advanceUntilIdle()

            viewModel.onAction(MapUiAction.FacilityPhoneClicked)
            advanceUntilIdle()

            val event =
                withTimeoutOrNull(100) {
                    viewModel.uiEvent.first()
                }

            assertEquals(MapUiEvent.OpenDialer("051-700-1000"), event)
        }

    @Test
    fun `external poi bookmark posts snapshot and deletes by returned target id`() =
        runTest {
            val tappedCoordinate = MapCoordinate(latitude = 35.1799, longitude = 129.0752)
            val bookmarkRepository =
                FakeBookmarkRepository(
                    saveTransform = { bookmark ->
                        bookmark.copy(
                            placeId = "tgt_fedcba9876543210",
                            bookmarkId = 7L,
                            bookmarkTargetId = "tgt_fedcba9876543210",
                            targetType = "EXTERNAL_POI",
                        )
                    },
                )
            val placesRepository =
                FakePlacesRepository(
                    mapTapDetail =
                        testMapTappedDetail(
                            bookmarkTargetId = "kakao:poi-123",
                            detailType = MapPlaceDetailType.EXTERNAL_POI,
                            provider = "KAKAO",
                            providerPlaceId = "poi-123",
                            name = "Kakao Cafe",
                            providerCategory = "Cafe",
                            address = "10 Cafe-ro, Busan",
                            latitude = tappedCoordinate.latitude,
                            longitude = tappedCoordinate.longitude,
                        ),
                )
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = bookmarkRepository,
                    placesRepository = placesRepository,
                )

            advanceUntilIdle()
            viewModel.onAction(
                MapUiAction.MapTapped(
                    MapTapPayload(
                        coordinate = tappedCoordinate,
                        clickType = MapTapClickType.POI,
                        provider = "KAKAO",
                        providerPlaceId = "poi-123",
                        nameHint = "Cafe Hint",
                    ),
                ),
            )
            advanceUntilIdle()

            viewModel.onAction(MapUiAction.FacilityBookmarkClicked)
            advanceUntilIdle()

            val savedBookmark = bookmarkRepository.savedBookmarks.single()
            assertEquals("KAKAO", savedBookmark.provider)
            assertEquals("poi-123", savedBookmark.providerPlaceId)
            assertEquals("Kakao Cafe", savedBookmark.placeName)
            assertEquals("Cafe", savedBookmark.providerCategory)
            assertEquals("tgt_fedcba9876543210", viewModel.uiState.value.facilityDetailSheetState.mapTapDetail?.bookmarkTargetId)
            assertTrue(viewModel.uiState.value.facilityDetailSheetState.isBookmarked)

            viewModel.onAction(MapUiAction.FacilityBookmarkClicked)
            advanceUntilIdle()

            assertEquals(listOf("tgt_fedcba9876543210"), bookmarkRepository.deletedPlaceIds)
            assertFalse(viewModel.uiState.value.facilityDetailSheetState.isBookmarked)
        }

    @Test
    fun `map tap detail failure clears loading and keeps sheet error state`() =
        runTest {
            val tappedCoordinate = MapCoordinate(latitude = 35.1775, longitude = 129.0771)
            val placesRepository =
                FakePlacesRepository(
                    mapTapDetailFailure = IllegalStateException("detail failed"),
                )
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    placesRepository = placesRepository,
                )

            advanceUntilIdle()

            viewModel.onAction(
                MapUiAction.MapTapped(
                    MapTapPayload(
                        coordinate = tappedCoordinate,
                        clickType = MapTapClickType.POI,
                    ),
                ),
            )
            advanceUntilIdle()

            assertEquals(1, placesRepository.mapTapDetailRequests.size)
            val event =
                withTimeoutOrNull(100) {
                    viewModel.uiEvent.first()
                }
            assertEquals(tappedCoordinate, viewModel.uiState.value.selectedMapPinCoordinate)
            assertEquals(null, viewModel.uiState.value.facilityDetailSheetState.mapTapDetail)
            assertFalse(viewModel.uiState.value.facilityDetailSheetState.isMapTapDetailLoading)
            assertTrue(viewModel.uiState.value.facilityDetailSheetState.mapTapDetailErrorMessage?.isNotBlank() == true)
            assertTrue(viewModel.uiState.value.facilityDetailSheetState.isVisible)
            assertNull(event)
        }

    @Test
    fun `marker tap clears previous map tap detail before loading internal detail`() =
        runTest {
            val tappedCoordinate = MapCoordinate(latitude = 35.1775, longitude = 129.0771)
            val placesRepository =
                FakePlacesRepository(
                    places =
                        listOf(
                            PlaceSummary(
                                placeId = "marker-place-1",
                                name = "Marker Cafe",
                                address = "1 Marker-ro, Busan",
                                latitude = 35.1796,
                                longitude = 129.0756,
                                category = PlaceCategory.FOOD_CAFE,
                            ),
                        ),
                    mapTapDetail =
                        testMapTappedDetail(
                            bookmarkTargetId = "external-address:35.1775,129.0771",
                            detailType = MapPlaceDetailType.EXTERNAL_ADDRESS,
                            name = "Selected Address",
                            latitude = tappedCoordinate.latitude,
                            longitude = tappedCoordinate.longitude,
                        ),
                )
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    placesRepository = placesRepository,
                )

            advanceUntilIdle()

            val markerId = viewModel.uiState.value.markerOverlayState.markers.first().markerId
            viewModel.onAction(
                MapUiAction.MapTapped(
                    MapTapPayload(
                        coordinate = tappedCoordinate,
                        clickType = MapTapClickType.POI,
                    ),
                ),
            )
            advanceUntilIdle()
            assertEquals("Selected Address", viewModel.uiState.value.facilityDetailSheetState.mapTapDetail?.name)

            viewModel.onAction(MapUiAction.MarkerTapped(markerId))
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.selectedMapPinCoordinate)
            assertEquals(null, viewModel.uiState.value.facilityDetailSheetState.mapTapDetail)
            assertEquals(markerId, viewModel.uiState.value.selectedMarkerId)
            assertEquals(markerId, viewModel.uiState.value.facilityDetailSheetState.detail?.facilityId)
        }

    @Test
    fun `marker tap loads existing bookmark state`() =
        runTest {
            val bookmarkRepository = FakeBookmarkRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = bookmarkRepository,
                )

            advanceUntilIdle()

            val markerId = viewModel.uiState.value.markerOverlayState.markers.first().markerId
            bookmarkRepository.bookmarkedPlaceIds += markerId

            viewModel.onAction(MapUiAction.MarkerTapped(markerId))
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.facilityDetailSheetState.isBookmarked)
            assertFalse(viewModel.uiState.value.facilityDetailSheetState.isBookmarkUpdating)
        }

    @Test
    fun `bookmark toggle saves and removes selected facility bookmark`() =
        runTest {
            val bookmarkRepository = FakeBookmarkRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = bookmarkRepository,
                )

            advanceUntilIdle()

            val markerId = viewModel.uiState.value.markerOverlayState.markers.first().markerId
            viewModel.onAction(MapUiAction.MarkerTapped(markerId))
            advanceUntilIdle()

            viewModel.onAction(MapUiAction.FacilityBookmarkClicked)
            advanceUntilIdle()

            assertTrue(markerId in bookmarkRepository.bookmarkedPlaceIds)
            assertTrue(viewModel.uiState.value.facilityDetailSheetState.isBookmarked)

            viewModel.onAction(MapUiAction.FacilityBookmarkClicked)
            advanceUntilIdle()

            assertFalse(markerId in bookmarkRepository.bookmarkedPlaceIds)
            assertFalse(viewModel.uiState.value.facilityDetailSheetState.isBookmarked)
        }

    @Test
    fun `bookmark toggle failure rolls back selected facility state`() =
        runTest {
            val bookmarkRepository = FakeBookmarkRepository(failSave = true)
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = bookmarkRepository,
                )

            advanceUntilIdle()

            val markerId = viewModel.uiState.value.markerOverlayState.markers.first().markerId
            viewModel.onAction(MapUiAction.MarkerTapped(markerId))
            advanceUntilIdle()

            viewModel.onAction(MapUiAction.FacilityBookmarkClicked)
            advanceUntilIdle()

            val sheetState = viewModel.uiState.value.facilityDetailSheetState

            assertFalse(markerId in bookmarkRepository.bookmarkedPlaceIds)
            assertFalse(sheetState.isBookmarked)
            assertFalse(sheetState.isBookmarkUpdating)
            assertEquals("북마크 저장에 실패했습니다. 다시 시도해 주세요.", sheetState.bookmarkErrorMessage)
        }

    @Test
    fun `facility detail dismiss action clears selected marker state`() =
        runTest {
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            advanceUntilIdle()

            val markerId = viewModel.uiState.value.markerOverlayState.markers.first().markerId

            viewModel.onAction(MapUiAction.MarkerTapped(markerId))
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.facilityDetailSheetState.isVisible)

            viewModel.onAction(MapUiAction.FacilityDetailDismissed)
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.selectedMarkerId)
            assertEquals(null, viewModel.uiState.value.facilityDetailSheetState.detail)
        }

    @Test
    fun `voice search click clears existing facility detail and opens voice sheet`() =
        runTest {
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            advanceUntilIdle()

            val markerId = viewModel.uiState.value.markerOverlayState.markers.first().markerId

            viewModel.onAction(MapUiAction.MarkerTapped(markerId))
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.facilityDetailSheetState.isVisible)

            viewModel.onAction(MapUiAction.VoiceSearchClicked)
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.selectedMarkerId)
            assertEquals(null, viewModel.uiState.value.facilityDetailSheetState.detail)
            assertEquals(false, viewModel.uiState.value.facilityDetailSheetState.isVisible)
            assertTrue(viewModel.uiState.value.isVoiceSearchVisible)
        }

    @Test
    fun `voice search dismiss hides the voice sheet state`() =
        runTest {
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            advanceUntilIdle()

            viewModel.onAction(MapUiAction.VoiceSearchClicked)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isVoiceSearchVisible)

            viewModel.onAction(MapUiAction.VoiceSearchDismissed)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isVoiceSearchVisible)
        }

    @Test
    fun `route entry action stores selected facility destination and emits navigation event`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            advanceUntilIdle()

            val markerId = viewModel.uiState.value.markerOverlayState.markers.first().markerId

            viewModel.onAction(MapUiAction.MarkerTapped(markerId))
            advanceUntilIdle()

            val selectedDetail = checkNotNull(viewModel.uiState.value.facilityDetailSheetState.detail)

            viewModel.onAction(MapUiAction.FacilitySetDestinationClicked)
            advanceUntilIdle()

            val event =
                withTimeoutOrNull(100) {
                    viewModel.uiEvent.first()
                }

            assertEquals(selectedDetail.toPlaceDestination(), destinationSelectionRepository.selectedDestination.value)
            assertEquals(null, viewModel.uiState.value.selectedMarkerId)
            assertEquals(null, viewModel.uiState.value.facilityDetailSheetState.detail)
            assertEquals(MapUiEvent.NavigateToRouteSetting(), event)
        }

    @Test
    fun `selected marker is cleared when filter hides it`() =
        runTest {
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            advanceUntilIdle()

            val foodCafeMarkerId =
                viewModel.uiState.value.markerOverlayState.markers
                    .first { marker -> marker.categoryType.category == FacilityCategory.FOOD_CAFE }
                    .markerId

            viewModel.onAction(MapUiAction.MarkerTapped(foodCafeMarkerId))
            viewModel.onAction(MapUiAction.MarkerCategoryFilterToggled(FacilityCategory.TOILET))
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.selectedMarkerId)
            assertEquals(null, viewModel.uiState.value.facilityDetailSheetState.detail)
        }

    @Test
    fun `external destination selection clears facility detail state before recentering`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            advanceUntilIdle()

            val markerId = viewModel.uiState.value.markerOverlayState.markers.first().markerId
            val destination = testDestination()

            viewModel.onAction(MapUiAction.MarkerTapped(markerId))
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.facilityDetailSheetState.isVisible)

            destinationSelectionRepository.updateSelectedDestination(destination)
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.selectedMarkerId)
            assertEquals(null, viewModel.uiState.value.facilityDetailSheetState.detail)
            assertEquals(destination, viewModel.uiState.value.selectedDestination)
            assertEquals(MapCameraSource.SEARCH_RESULT, viewModel.uiState.value.cameraTarget.source)
        }

    @Test
    fun `recent destinations keep up to ten entries for the expandable sheet`() =
        runTest {
            val searchRepository =
                FakeSearchRepository(
                    recentDestinations =
                        listOf(
                            recentDestination(placeId = "place-11", searchedAtMillis = 11_000L),
                            recentDestination(placeId = "place-10", searchedAtMillis = 10_000L),
                            recentDestination(placeId = "place-9", searchedAtMillis = 9_000L),
                            recentDestination(placeId = "place-8", searchedAtMillis = 8_000L),
                            recentDestination(placeId = "place-7", searchedAtMillis = 7_000L),
                            recentDestination(placeId = "place-6", searchedAtMillis = 6_000L),
                            recentDestination(placeId = "place-5", searchedAtMillis = 5_000L),
                            recentDestination(placeId = "place-4", searchedAtMillis = 4_000L),
                            recentDestination(placeId = "place-3", searchedAtMillis = 3_000L),
                            recentDestination(placeId = "place-2", searchedAtMillis = 2_000L),
                            recentDestination(placeId = "place-1", searchedAtMillis = 1_000L),
                        ),
                )
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    searchRepository = searchRepository,
                )

            advanceUntilIdle()

            assertEquals(
                listOf(
                    "place-11",
                    "place-10",
                    "place-9",
                    "place-8",
                    "place-7",
                    "place-6",
                    "place-5",
                    "place-4",
                    "place-3",
                    "place-2",
                ),
                viewModel.uiState.value.recentDestinations.map { recentDestination -> recentDestination.placeId },
            )
        }

    @Test
    fun `recent destinations stay stale until next refresh when save completes after first route start`() =
        runTest {
            val searchRepository = FakeSearchRepository(saveGate = CompletableDeferred())
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val destination = testDestination().copy(placeId = "bookmark-place-1")
            val recentDestination =
                recentDestination(
                    placeId = destination.placeId,
                    searchedAtMillis = 1_000L,
                )
                    .copy(
                        latitude = destination.latitude,
                        longitude = destination.longitude,
                    )
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    searchRepository = searchRepository,
                )

            val saveJob =
                backgroundScope.launch {
                    searchRepository.saveRecentDestination(recentDestination)
                }
            runCurrent()

            viewModel.onRouteStarted()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.recentDestinations.isEmpty())

            searchRepository.allowPendingSave()
            saveJob.join()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.recentDestinations.isEmpty())

            viewModel.onRouteStopped()
            viewModel.onRouteStarted()
            advanceUntilIdle()

            assertEquals(
                listOf("bookmark-place-1"),
                viewModel.uiState.value.recentDestinations.map { item -> item.placeId },
            )
        }

    @Test
    fun `recent destination preview click centers camera and opens detail sheet`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    destinationPreviewRepository = destinationPreviewRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    searchRepository =
                        FakeSearchRepository(
                            recentDestinations =
                                listOf(
                                    recentDestination(placeId = "recent-place-1", searchedAtMillis = 2_000L),
                                ),
                        ),
                )

            advanceUntilIdle()

            viewModel.onAction(MapUiAction.RecentDestinationPreviewClicked(placeId = "recent-place-1"))
            advanceUntilIdle()

            assertNull(destinationSelectionRepository.selectedDestination.value)
            assertNull(destinationPreviewRepository.pendingPreview.value)
            assertEquals(MapCameraSource.SEARCH_RESULT, viewModel.uiState.value.cameraTarget.source)
            assertEquals(35.1796, viewModel.uiState.value.cameraTarget.center.latitude, 0.0)
            assertEquals(129.0756, viewModel.uiState.value.cameraTarget.center.longitude, 0.0)
            assertEquals("recent-place-1", viewModel.uiState.value.facilityDetailSheetState.destinationPreview?.destination?.placeId)
            assertEquals("Recent Destination recent-place-1", viewModel.uiState.value.facilityDetailSheetState.mapTapDetail?.name)
            assertEquals(listOf("accessible-parking"), viewModel.uiState.value.facilityDetailSheetState.mapTapDetail?.accessibilityTags)
        }

    @Test
    fun `recent destination route click stores destination and emits navigation event`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    searchRepository =
                        FakeSearchRepository(
                            recentDestinations =
                                listOf(
                                    recentDestination(placeId = "recent-place-1", searchedAtMillis = 2_000L),
                                ),
                        ),
                )

            advanceUntilIdle()

            viewModel.onAction(MapUiAction.RecentDestinationRouteClicked(placeId = "recent-place-1"))
            advanceUntilIdle()

            val event =
                withTimeoutOrNull(100) {
                    viewModel.uiEvent.first()
                }

            assertEquals("recent-place-1", destinationSelectionRepository.selectedDestination.value?.placeId)
            assertEquals(MapUiEvent.NavigateToRouteSetting(), event)
        }

    @Test
    fun `shortcut filter row exposes contract aligned quick filters`() =
        runTest {
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            advanceUntilIdle()

            assertEquals(
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
                ),
                viewModel.uiState.value.shortcutFilterState.chips.map { chip -> chip.key },
            )
        }

    @Test
    fun `shortcut filter charging station chip narrows visible markers to charging facilities`() =
        runTest {
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            advanceUntilIdle()

            viewModel.onAction(MapUiAction.ShortcutFilterClicked(MapShortcutFilterKey.CHARGING_STATION))
            advanceUntilIdle()

            val visibleMarkers = viewModel.uiState.value.markerOverlayState.visibleMarkers

            assertTrue(visibleMarkers.isNotEmpty())
            assertTrue(visibleMarkers.size < viewModel.uiState.value.markerOverlayState.totalMarkerCount)
            assertTrue(
                visibleMarkers.all { marker ->
                    marker.categoryType.category == FacilityCategory.CHARGING_STATION
                },
            )
            assertTrue(
                viewModel.uiState.value.shortcutFilterState.chips
                    .first { chip -> chip.key == MapShortcutFilterKey.CHARGING_STATION }
                    .isSelected,
            )
        }

    @Test
    fun `shortcut filter chips keep only one selected shortcut category`() =
        runTest {
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = testFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            advanceUntilIdle()

            viewModel.onAction(MapUiAction.ShortcutFilterClicked(MapShortcutFilterKey.CHARGING_STATION))
            viewModel.onAction(MapUiAction.ShortcutFilterClicked(MapShortcutFilterKey.TOILET))
            advanceUntilIdle()

            val visibleMarkers = viewModel.uiState.value.markerOverlayState.visibleMarkers

            assertTrue(visibleMarkers.isNotEmpty())
            assertTrue(
                visibleMarkers.all { marker ->
                    marker.categoryType.category == FacilityCategory.TOILET
                },
            )
            assertEquals(
                setOf(FacilityCategory.TOILET),
                viewModel.uiState.value.markerFilterState.selection.selectedFacilityCategories,
            )
            assertFalse(
                viewModel.uiState.value.shortcutFilterState.chips
                    .first { chip -> chip.key == MapShortcutFilterKey.CHARGING_STATION }
                    .isSelected,
            )
            assertTrue(
                viewModel.uiState.value.shortcutFilterState.chips
                    .first { chip -> chip.key == MapShortcutFilterKey.TOILET }
                    .isSelected,
            )
        }

    @Test
    fun `shortcut filter replaces the previous selected category even when all nearby categories are available`() =
        runTest {
            val placesRepository =
                FakePlacesRepository(
                    places =
                        listOf(
                            PlaceSummary(
                                placeId = "toilet-1",
                                name = "Accessible Toilet",
                                address = "1 Toilet-ro, Busan",
                                latitude = 35.1796,
                                longitude = 129.0756,
                                category = PlaceCategory.TOILET,
                            ),
                            PlaceSummary(
                                placeId = "elevator-1",
                                name = "Station Elevator",
                                address = "2 Elevator-ro, Busan",
                                latitude = 35.1802,
                                longitude = 129.0762,
                                category = PlaceCategory.ELEVATOR,
                            ),
                        ),
                )
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = EmptyFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    placesRepository = placesRepository,
                )

            advanceUntilIdle()

            viewModel.onAction(MapUiAction.ShortcutFilterClicked(MapShortcutFilterKey.TOILET))
            viewModel.onAction(MapUiAction.ShortcutFilterClicked(MapShortcutFilterKey.ELEVATOR))
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.markerFilterState.selection.isShowingAllCategories)
            assertEquals(
                setOf(FacilityCategory.ELEVATOR),
                viewModel.uiState.value.markerFilterState.selection.selectedFacilityCategories,
            )
            assertFalse(
                viewModel.uiState.value.shortcutFilterState.chips
                    .first { chip -> chip.key == MapShortcutFilterKey.TOILET }
                    .isSelected,
            )
            assertTrue(
                viewModel.uiState.value.shortcutFilterState.chips
                    .first { chip -> chip.key == MapShortcutFilterKey.ELEVATOR }
                    .isSelected,
            )
        }

    @Test
    fun `moving the live map shows search here and reloads around the current viewport`() =
        runTest {
            val placesRepository =
                FakePlacesRepository(
                    places =
                        listOf(
                            PlaceSummary(
                                placeId = "toilet-1",
                                name = "Accessible Toilet",
                                address = "1 Toilet-ro, Busan",
                                latitude = 35.1796,
                                longitude = 129.0756,
                                category = PlaceCategory.TOILET,
                            ),
                            PlaceSummary(
                                placeId = "elevator-1",
                                name = "Accessible Elevator",
                                address = "1 Elevator-ro, Busan",
                                latitude = 35.1797,
                                longitude = 129.0757,
                                category = PlaceCategory.ELEVATOR,
                            ),
                        ),
                )
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = EmptyFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    placesRepository = placesRepository,
                )

            advanceUntilIdle()
            viewModel.onAction(MapUiAction.ShortcutFilterClicked(MapShortcutFilterKey.TOILET))
            viewModel.onAction(MapUiAction.ShortcutFilterClicked(MapShortcutFilterKey.ELEVATOR))
            val movedCenter = MapCoordinate(latitude = 35.18, longitude = 129.08)

            viewModel.onAction(
                MapUiAction.ViewportCameraChanged(
                    center = movedCenter,
                    zoomLevel = 15,
                    isUserGesture = true,
                ),
            )

            assertTrue(viewModel.uiState.value.isSearchHereVisible)

            viewModel.onAction(MapUiAction.SearchHereClicked)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isSearchHereVisible)
            assertEquals(movedCenter.latitude, placesRepository.queries.last().latitude ?: Double.NaN, 0.0)
            assertEquals(movedCenter.longitude, placesRepository.queries.last().longitude ?: Double.NaN, 0.0)
            assertEquals(
                setOf(FacilityCategory.ELEVATOR),
                viewModel.uiState.value.markerFilterState.selection.selectedFacilityCategories,
            )
            assertTrue(
                viewModel.uiState.value.shortcutFilterState.chips
                    .first { chip -> chip.key == MapShortcutFilterKey.ELEVATOR }
                    .isSelected,
            )
            assertFalse(
                viewModel.uiState.value.shortcutFilterState.chips
                    .first { chip -> chip.key == MapShortcutFilterKey.TOILET }
                .isSelected,
            )
        }

    @Test
    fun `delayed search here response does not clear filter selected after request started`() =
        runTest {
            val placesRepository = DelayedSearchHerePlacesRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = EmptyFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    placesRepository = placesRepository,
                )

            advanceUntilIdle()
            viewModel.onAction(MapUiAction.ShortcutFilterClicked(MapShortcutFilterKey.TOILET))
            viewModel.onAction(
                MapUiAction.ViewportCameraChanged(
                    center = MapCoordinate(latitude = 35.18, longitude = 129.08),
                    zoomLevel = 15,
                    isUserGesture = true,
                ),
            )
            viewModel.onAction(MapUiAction.SearchHereClicked)
            runCurrent()

            assertTrue(placesRepository.searchHereRequested.isCompleted)

            viewModel.onAction(MapUiAction.ShortcutFilterClicked(MapShortcutFilterKey.ELEVATOR))
            runCurrent()

            assertEquals(
                setOf(FacilityCategory.ELEVATOR),
                viewModel.uiState.value.markerFilterState.selection.selectedFacilityCategories,
            )

            placesRepository.completeSearchHere(
                listOf(
                    PlaceSummary(
                        placeId = "toilet-2",
                        name = "New Accessible Toilet",
                        address = "2 Toilet-ro, Busan",
                        latitude = 35.181,
                        longitude = 129.081,
                        category = PlaceCategory.TOILET,
                    ),
                ),
            )
            advanceUntilIdle()

            assertEquals(
                "The user-selected filter should stay selected even if the delayed response has no matching category.",
                setOf(FacilityCategory.ELEVATOR),
                viewModel.uiState.value.markerFilterState.selection.selectedFacilityCategories,
            )
            assertTrue(viewModel.uiState.value.markerOverlayState.isEmptyResult)
            assertTrue(
                viewModel.uiState.value.shortcutFilterState.chips
                    .first { chip -> chip.key == MapShortcutFilterKey.ELEVATOR }
                    .isSelected,
            )
        }

    @Test
    fun `current location reload after search here keeps selected shortcut filter`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val placesRepository =
                SequentialPlacesRepository(
                    responses =
                        listOf(
                            listOf(
                                PlaceSummary(
                                    placeId = "toilet-1",
                                    name = "Accessible Toilet",
                                    address = "1 Toilet-ro, Busan",
                                    latitude = 35.1796,
                                    longitude = 129.0756,
                                    category = PlaceCategory.TOILET,
                                ),
                                PlaceSummary(
                                    placeId = "elevator-1",
                                    name = "Station Elevator",
                                    address = "2 Elevator-ro, Busan",
                                    latitude = 35.1802,
                                    longitude = 129.0762,
                                    category = PlaceCategory.ELEVATOR,
                                ),
                            ),
                            listOf(
                                PlaceSummary(
                                    placeId = "toilet-2",
                                    name = "Viewport Toilet",
                                    address = "2 Toilet-ro, Busan",
                                    latitude = 35.181,
                                    longitude = 129.081,
                                    category = PlaceCategory.TOILET,
                                ),
                                PlaceSummary(
                                    placeId = "elevator-2",
                                    name = "Viewport Elevator",
                                    address = "2 Elevator-ro, Busan",
                                    latitude = 35.1812,
                                    longitude = 129.0812,
                                    category = PlaceCategory.ELEVATOR,
                                ),
                            ),
                            listOf(
                                PlaceSummary(
                                    placeId = "toilet-3",
                                    name = "Current Location Toilet",
                                    address = "3 Toilet-ro, Busan",
                                    latitude = 35.182,
                                    longitude = 129.082,
                                    category = PlaceCategory.TOILET,
                                ),
                            ),
                        ),
                )
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = EmptyFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    placesRepository = placesRepository,
                )

            advanceUntilIdle()
            viewModel.onAction(MapUiAction.ShortcutFilterClicked(MapShortcutFilterKey.TOILET))
            viewModel.onAction(
                MapUiAction.ViewportCameraChanged(
                    center = MapCoordinate(latitude = 35.18, longitude = 129.08),
                    zoomLevel = 15,
                    isUserGesture = true,
                ),
            )
            viewModel.onAction(MapUiAction.SearchHereClicked)
            advanceUntilIdle()
            viewModel.onAction(MapUiAction.ShortcutFilterClicked(MapShortcutFilterKey.ELEVATOR))
            advanceUntilIdle()

            locationManager.updateLocation(testLocationSnapshot(latitude = 35.182, longitude = 129.082))
            advanceUntilIdle()

            assertEquals(3, placesRepository.queries.size)
            assertEquals(
                setOf(FacilityCategory.ELEVATOR),
                viewModel.uiState.value.markerFilterState.selection.selectedFacilityCategories,
            )
            assertTrue(viewModel.uiState.value.markerOverlayState.isEmptyResult)
            assertTrue(
                viewModel.uiState.value.shortcutFilterState.chips
                    .first { chip -> chip.key == MapShortcutFilterKey.ELEVATOR }
                    .isSelected,
            )
        }

    @Test
    fun `shortcut filter disabled chip tap shows unavailable snackbar without changing selection`() =
        runTest {
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = facilitySeedRepositoryWithOtherCategory(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            advanceUntilIdle()

            viewModel.onAction(MapUiAction.ShortcutFilterClicked(MapShortcutFilterKey.CHARGING_STATION))
            advanceUntilIdle()

            val event =
                withTimeoutOrNull(100) {
                    viewModel.uiEvent.first()
                }

            assertEquals(
                MapUiEvent.ShowSnackbar("근처에 해당 장소가 없어요"),
                event,
            )
            assertFalse(viewModel.uiState.value.markerFilterState.selection.isShowingAllCategories)
            assertEquals(0, viewModel.uiState.value.markerOverlayState.visibleMarkerCount)
            assertFalse(
                viewModel.uiState.value.shortcutFilterState.chips
                    .first { chip -> chip.key == MapShortcutFilterKey.CHARGING_STATION }
                    .isSelected,
            )
        }

    @Test
    fun `category filter options exclude other even when browse data contains other markers`() =
        runTest {
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = facilitySeedRepositoryWithOtherCategory(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            advanceUntilIdle()

            assertFalse(
                viewModel.uiState.value.markerFilterState.categoryOptions.any { option ->
                    option.category == FacilityCategory.OTHER
                },
            )
            assertEquals(2, viewModel.uiState.value.markerOverlayState.totalMarkerCount)
            assertEquals(0, viewModel.uiState.value.markerOverlayState.visibleMarkerCount)
        }

    @Test
    fun `browse load failure exposes error states`() =
        runTest {
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = FailingFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.markerOverlayState.isLoadFailed)
            assertTrue(viewModel.uiState.value.markerFilterState.isLoadFailed)
            assertEquals(0, viewModel.uiState.value.markerOverlayState.totalMarkerCount)
        }

    @Test
    fun `empty browse data exposes empty states`() =
        runTest {
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = EmptyFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                )

            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.markerOverlayState.isEmptyData)
            assertTrue(viewModel.uiState.value.markerFilterState.isEmptyData)
            assertEquals(0, viewModel.uiState.value.markerOverlayState.visibleMarkerCount)
            assertTrue(viewModel.uiState.value.markerFilterState.categoryOptions.isEmpty())
        }

    @Test
    fun `places browse maps feature filter membership without changing facility detail category`() =
        runTest {
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = EmptyFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    placesRepository =
                        FakePlacesRepository(
                            places =
                                listOf(
                                    PlaceSummary(
                                        placeId = "place-cafe-1",
                                        name = "Accessible Cafe",
                                        address = "1 Jungang-daero, Busan",
                                        latitude = 35.1796,
                                        longitude = 129.0756,
                                        category = PlaceCategory.FOOD_CAFE,
                                        features =
                                            listOf(
                                                PlaceFeatureAvailability(
                                                    featureType = PlaceFeatureType.ACCESSIBLE_TOILET,
                                                    isAvailable = true,
                                                ),
                                                PlaceFeatureAvailability(
                                                    featureType = PlaceFeatureType.ACCESSIBLE_PARKING,
                                                    isAvailable = true,
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                )

            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.shortcutFilterState.chips
                    .first { chip -> chip.key == MapShortcutFilterKey.TOILET }
                    .isEnabled,
            )

            viewModel.onAction(MapUiAction.ShortcutFilterClicked(MapShortcutFilterKey.TOILET))
            advanceUntilIdle()

            assertEquals(
                listOf("place-cafe-1"),
                viewModel.uiState.value.markerOverlayState.visibleMarkers.map { marker -> marker.markerId },
            )

            viewModel.onAction(MapUiAction.MarkerTapped("place-cafe-1"))
            advanceUntilIdle()

            assertEquals(
                FacilityCategory.FOOD_CAFE,
                viewModel.uiState.value.facilityDetailSheetState.detail?.category,
            )
        }

    @Test
    fun `marker tap upgrades sheet detail from live place detail and keeps destination handoff`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val placesRepository =
                FakePlacesRepository(
                    places =
                        listOf(
                            PlaceSummary(
                                placeId = "101",
                                name = "Accessible Cafe",
                                address = "1 Jungang-daero, Busan",
                                latitude = 35.1796,
                                longitude = 129.0756,
                                category = PlaceCategory.FOOD_CAFE,
                            ),
                        ),
                    placeDetailsById =
                        mapOf(
                            "101" to
                                PlaceDetail(
                                    placeId = "101",
                                    name = "Accessible Cafe",
                                    address = "1 Jungang-daero, Busan",
                                    latitude = 35.1796,
                                    longitude = 129.0756,
                                    category = PlaceCategory.FOOD_CAFE,
                                    features =
                                        listOf(
                                            PlaceFeatureAvailability(
                                                featureType = PlaceFeatureType.ACCESSIBLE_ENTRANCE,
                                                isAvailable = true,
                                            ),
                                            PlaceFeatureAvailability(
                                                featureType = PlaceFeatureType.ACCESSIBLE_TOILET,
                                                isAvailable = true,
                                            ),
                                        ),
                                    isBookmarked = true,
                                    accessibilityTags = listOf("step-free-entrance", "accessible-toilet"),
                                    providerPlaceId = "kakao-101",
                                    description = "East gate is step-free and the accessible toilet is inside.",
                                ),
                        ),
                )
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    facilitySeedRepository = EmptyFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    placesRepository = placesRepository,
                )

            advanceUntilIdle()

            viewModel.onAction(MapUiAction.MarkerTapped("101"))
            advanceUntilIdle()

            assertEquals(listOf("101"), placesRepository.detailRequests)
            val detail = requireNotNull(viewModel.uiState.value.facilityDetailSheetState.detail)
            assertEquals("101", detail.facilityId)
            assertEquals(FacilityCategory.FOOD_CAFE, detail.category)
            assertTrue(AccessibilityTag.STEP_FREE_ENTRANCE in detail.accessibilityTags)
            assertTrue(AccessibilityTag.ACCESSIBLE_TOILET in detail.accessibilityTags)
            assertEquals(
                "East gate is step-free and the accessible toilet is inside.",
                detail.description,
            )
            assertTrue(viewModel.uiState.value.facilityDetailSheetState.isBookmarked)

            viewModel.onAction(MapUiAction.FacilitySetDestinationClicked)
            advanceUntilIdle()

            val event =
                withTimeoutOrNull(100) {
                    viewModel.uiEvent.first()
                }

            assertEquals("101", destinationSelectionRepository.selectedDestination.value?.placeId)
            assertEquals("Accessible Cafe", destinationSelectionRepository.selectedDestination.value?.name)
            assertEquals(MapUiEvent.NavigateToRouteSetting(), event)
        }

    @Test
    fun `account scope change clears stale map preview state and reloads places`() =
        runTest {
            val authSessionRepository =
                TestAuthSessionRepository(
                    initialState =
                        AuthGateState(
                            authSession =
                                AuthSession(
                                    accessToken = "access-token-a",
                                    refreshToken = "refresh-token-a",
                                    userId = "user-a",
                                ),
                            isProfileCompleted = true,
                        ),
                )
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val placesRepository =
                FakePlacesRepository(
                    places =
                        listOf(
                            PlaceSummary(
                                placeId = "scoped-place-1",
                                name = "Scoped Place",
                                address = "1 Scoped-ro, Busan",
                                latitude = 35.1801,
                                longitude = 129.0722,
                                category = PlaceCategory.WELFARE,
                            ),
                        ),
                )
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    destinationPreviewRepository = destinationPreviewRepository,
                    facilitySeedRepository = EmptyFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    authSessionRepository = authSessionRepository,
                    placesRepository = placesRepository,
                )

            advanceUntilIdle()

            destinationPreviewRepository.requestPreview(destination = testDestination())
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.facilityDetailSheetState.isVisible)
            assertEquals(1, placesRepository.queries.size)

            authSessionRepository.updateAuthSession(
                authSession =
                    AuthSession(
                        accessToken = "access-token-b",
                        refreshToken = "refresh-token-b",
                        userId = "user-b",
                    ),
                isProfileCompleted = true,
            )
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.selectedMarkerId)
            assertNull(viewModel.uiState.value.selectedMapPinCoordinate)
            assertFalse(viewModel.uiState.value.facilityDetailSheetState.isVisible)
            assertNull(viewModel.uiState.value.facilityDetailSheetState.detail)
            assertNull(viewModel.uiState.value.facilityDetailSheetState.mapTapDetail)
            assertNull(viewModel.uiState.value.facilityDetailSheetState.destinationPreview)
            assertEquals(2, placesRepository.queries.size)
        }

    @Test
    fun `live places browse uses the fallback busan camera center before current location is ready`() =
        runTest {
            val placesRepository =
                FakePlacesRepository(
                    places =
                        listOf(
                            PlaceSummary(
                                placeId = "fallback-place-1",
                                name = "Fallback Browse Place",
                                address = "1 Busan-daero, Busan",
                                latitude = 35.1802,
                                longitude = 129.0724,
                                category = PlaceCategory.WELFARE,
                            ),
                        ),
                )
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = EmptyFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    placesRepository = placesRepository,
                )

            advanceUntilIdle()

            val query = placesRepository.queries.single()
            assertEquals(MapDefaults.BUSAN_CENTER.latitude, query.latitude ?: Double.NaN, 0.0)
            assertEquals(MapDefaults.BUSAN_CENTER.longitude, query.longitude ?: Double.NaN, 0.0)
            assertEquals(1_000, query.radiusMeters)
            assertEquals(MapCameraSource.DEFAULT_BUSAN, viewModel.uiState.value.cameraTarget.source)
        }

    @Test
    fun `stale fallback places response does not replace current location browse results`() =
        runTest {
            val currentLocation = testLocationSnapshot(latitude = 35.0893, longitude = 128.8534)
            val locationManager = FakeCurrentLocationManager(initialLocation = null)
            val placesRepository = DelayedFallbackPlacesRepository()
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(
                            initialState = LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE),
                        ),
                    currentLocationManager = locationManager,
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = EmptyFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    placesRepository = placesRepository,
                )

            viewModel.onRouteStarted()
            runCurrent()

            assertTrue(placesRepository.fallbackRequested.isCompleted)

            locationManager.updateLocation(currentLocation)
            advanceUntilIdle()
            viewModel.onAction(MapUiAction.ShortcutFilterClicked(MapShortcutFilterKey.ELEVATOR))

            assertEquals(
                "Current-location browse result should be visible before fallback resolves.",
                listOf("current-elevator"),
                viewModel.uiState.value.markerOverlayState.visibleMarkers.map { marker -> marker.markerId },
            )

            placesRepository.completeFallback()
            advanceUntilIdle()

            assertEquals(
                listOf("current-elevator"),
                viewModel.uiState.value.markerOverlayState.visibleMarkers.map { marker -> marker.markerId },
            )
        }

    @Test
    fun `marker tap keeps preview detail when live detail returns not found`() =
        runTest {
            val placesRepository =
                FakePlacesRepository(
                    places =
                        listOf(
                            PlaceSummary(
                                placeId = "404",
                                name = "Accessible Hotel",
                                address = "10 Haeundae-ro, Busan",
                                latitude = 35.1587,
                                longitude = 129.1604,
                                category = PlaceCategory.ACCOMMODATION,
                                features =
                                    listOf(
                                        PlaceFeatureAvailability(
                                            featureType = PlaceFeatureType.GUIDANCE_FACILITY,
                                            isAvailable = true,
                                        ),
                                        PlaceFeatureAvailability(
                                            featureType = PlaceFeatureType.ACCESSIBLE_ROOM,
                                            isAvailable = true,
                                        ),
                                    ),
                            ),
                        ),
                )
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = EmptyFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    placesRepository = placesRepository,
                )

            advanceUntilIdle()

            viewModel.onAction(MapUiAction.MarkerTapped("404"))
            advanceUntilIdle()

            val detail = requireNotNull(viewModel.uiState.value.facilityDetailSheetState.detail)
            assertEquals("404", detail.facilityId)
            assertEquals(FacilityCategory.ACCOMMODATION, detail.category)
            assertEquals(
                listOf(
                    AccessibilityTag.GUIDANCE_FACILITY,
                    AccessibilityTag.ACCESSIBLE_ROOM,
                ),
                detail.accessibilityTags,
            )
            assertEquals(listOf("404"), placesRepository.detailRequests)
            assertEquals("404", viewModel.uiState.value.selectedMarkerId)
        }

    @Test
    fun `marker tap keeps preview detail when live detail request fails`() =
        runTest {
            val placesRepository =
                FakePlacesRepository(
                    places =
                        listOf(
                            PlaceSummary(
                                placeId = "failed-detail",
                                name = "Accessible Welfare Center",
                                address = "7 Welfare-ro, Busan",
                                latitude = 35.1777,
                                longitude = 129.0711,
                                category = PlaceCategory.WELFARE,
                                features =
                                    listOf(
                                        PlaceFeatureAvailability(
                                            featureType = PlaceFeatureType.ACCESSIBLE_ENTRANCE,
                                            isAvailable = true,
                                        ),
                                        PlaceFeatureAvailability(
                                            featureType = PlaceFeatureType.ACCESSIBLE_PARKING,
                                            isAvailable = true,
                                        ),
                                    ),
                            ),
                        ),
                    detailFailureById =
                        mapOf(
                            "failed-detail" to IllegalStateException("detail fetch failed"),
                        ),
                )
            val viewModel =
                MapViewModel(
                    locationPermissionManager =
                        FakeLocationPermissionManager(initialState = LocationPermissionState.Denied),
                    currentLocationManager = FakeCurrentLocationManager(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    facilitySeedRepository = EmptyFacilitySeedRepository(),
                    bookmarkRepository = FakeBookmarkRepository(),
                    placesRepository = placesRepository,
                )

            advanceUntilIdle()

            viewModel.onAction(MapUiAction.MarkerTapped("failed-detail"))
            advanceUntilIdle()

            val detail = requireNotNull(viewModel.uiState.value.facilityDetailSheetState.detail)
            assertEquals("failed-detail", detail.facilityId)
            assertEquals(FacilityCategory.WELFARE, detail.category)
            assertEquals(
                listOf(
                    AccessibilityTag.STEP_FREE_ENTRANCE,
                    AccessibilityTag.ACCESSIBLE_PARKING,
                ),
                detail.accessibilityTags,
            )
            assertEquals("failed-detail", viewModel.uiState.value.selectedMarkerId)
        }
}

private fun testDestination(): PlaceDestination =
    PlaceDestination(
        placeId = "place-1",
        name = "부산시청",
        address = "부산 연제구 중앙대로 1001",
        latitude = 35.1797,
        longitude = 129.0750,
    )

private fun testLocationSnapshot(
    latitude: Double,
    longitude: Double,
    recordedAtEpochMillis: Long = System.currentTimeMillis(),
): LocationSnapshot =
    LocationSnapshot(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = 5f,
        recordedAtEpochMillis = recordedAtEpochMillis,
    )

private fun testMapTappedDetail(
    bookmarkTargetId: String,
    detailType: MapPlaceDetailType,
    provider: String? = null,
    providerPlaceId: String? = null,
    name: String,
    category: PlaceCategory? = null,
    providerCategory: String? = null,
    address: String = "",
    latitude: Double,
    longitude: Double,
    isBookmarked: Boolean = false,
    accessibilityTags: List<String> = emptyList(),
    phoneNumber: String? = null,
): MapTappedPlaceDetail =
    MapTappedPlaceDetail(
        bookmarkTargetId = bookmarkTargetId,
        detailType = detailType,
        placeId = null,
        provider = provider,
        providerPlaceId = providerPlaceId,
        name = name,
        category = category,
        providerCategory = providerCategory,
        address = address,
        latitude = latitude,
        longitude = longitude,
        isBookmarked = isBookmarked,
        accessibilityTags = accessibilityTags,
        phoneNumber = phoneNumber,
    )

private class FakeLocationPermissionManager(
    initialState: LocationPermissionState,
) : LocationPermissionManager {
    private val mutablePermissionState = MutableStateFlow(initialState)

    override val permissionState: StateFlow<LocationPermissionState> = mutablePermissionState

    override fun refreshPermissionState() = Unit

    override fun requestLocationPermission(activity: ComponentActivity) = Unit
}

private class FakeCurrentLocationManager(
    initialLocation: LocationSnapshot? = null,
) : CurrentLocationManager {
    private val mutableLatestLocation = MutableStateFlow(initialLocation)
    var refreshLatestLocationCallCount: Int = 0
        private set
    var startLocationUpdatesCallCount: Int = 0
        private set
    var stopLocationUpdatesCallCount: Int = 0
        private set

    override val latestLocation: StateFlow<LocationSnapshot?> = mutableLatestLocation

    override fun refreshLatestLocation() {
        refreshLatestLocationCallCount += 1
    }

    override fun startLocationUpdates() {
        startLocationUpdatesCallCount += 1
    }

    override fun stopLocationUpdates() {
        stopLocationUpdatesCallCount += 1
    }

    fun updateLocation(snapshot: LocationSnapshot?) {
        mutableLatestLocation.value = snapshot
    }
}

private class FakeBookmarkRepository(
    val bookmarkedPlaceIds: MutableSet<String> = mutableSetOf(),
    private val failSave: Boolean = false,
    private val failDelete: Boolean = false,
    private val saveTransform: (BookmarkData) -> BookmarkData = { bookmark -> bookmark },
) : BookmarkRepository {
    val savedBookmarks = mutableListOf<BookmarkData>()
    val deletedPlaceIds = mutableListOf<String>()

    override fun observeBookmarks(): Flow<List<BookmarkData>> = flowOf(emptyList())

    override suspend fun isBookmarked(placeId: String): Boolean =
        placeId in bookmarkedPlaceIds

    override suspend fun saveBookmark(bookmark: BookmarkData): BookmarkData {
        if (failSave) error("bookmark save failed")
        val savedBookmark = saveTransform(bookmark)
        savedBookmarks += savedBookmark
        bookmarkedPlaceIds += savedBookmark.placeId
        return savedBookmark
    }

    override suspend fun deleteBookmark(placeId: String) {
        if (failDelete) error("bookmark delete failed")
        deletedPlaceIds += placeId
        bookmarkedPlaceIds -= placeId
    }
}

private class FakeSearchRepository(
    recentDestinations: List<RecentDestination> = emptyList(),
    private val saveGate: CompletableDeferred<Unit>? = null,
) : SearchRepository {
    private val storedRecentDestinations = recentDestinations.toMutableList()

    override suspend fun search(query: com.ssafy.e102.eumgil.core.model.SearchQuery) =
        emptyList<com.ssafy.e102.eumgil.core.model.SearchResult>()

    override suspend fun getRecentSearches() = emptyList<com.ssafy.e102.eumgil.core.model.RecentSearch>()

    override suspend fun saveRecentSearch(keyword: String) = Unit

    override suspend fun getRecentDestinations(): List<RecentDestination> = storedRecentDestinations.toList()

    override suspend fun saveRecentDestination(destination: RecentDestination) {
        saveGate?.await()
        storedRecentDestinations += destination
    }

    fun allowPendingSave() {
        saveGate?.complete(Unit)
    }
}

private class DelayedFallbackPlacesRepository : PlacesRepository {
    val fallbackRequested = CompletableDeferred<Unit>()
    private val fallbackGate = CompletableDeferred<Unit>()

    override suspend fun getPlaces(query: PlaceQuery): List<PlaceSummary> {
        return if (query.isFallbackBusanQuery()) {
            fallbackRequested.complete(Unit)
            fallbackGate.await()
            listOf(
                PlaceSummary(
                    placeId = "fallback-elevator",
                    name = "부산시청 엘리베이터",
                    address = "부산광역시 연제구 중앙대로 1001",
                    latitude = 35.1797,
                    longitude = 129.0750,
                    category = PlaceCategory.ELEVATOR,
                ),
            )
        } else {
            listOf(
                PlaceSummary(
                    placeId = "current-elevator",
                    name = "현재 위치 엘리베이터",
                    address = "현재 위치 주변",
                    latitude = query.latitude ?: 0.0,
                    longitude = query.longitude ?: 0.0,
                    category = PlaceCategory.ELEVATOR,
                ),
            )
        }
    }

    override suspend fun getPlaceDetail(placeId: String): PlaceDetail? = null

    override suspend fun getMapTappedPlaceDetail(request: MapPlaceDetailRequest): MapTappedPlaceDetail? = null

    fun completeFallback() {
        fallbackGate.complete(Unit)
    }

    private fun PlaceQuery.isFallbackBusanQuery(): Boolean =
        latitude == MapDefaults.BUSAN_CENTER.latitude &&
            longitude == MapDefaults.BUSAN_CENTER.longitude
}

private class DelayedSearchHerePlacesRepository : PlacesRepository {
    val queries = mutableListOf<PlaceQuery>()
    val searchHereRequested = CompletableDeferred<Unit>()
    private val searchHereGate = CompletableDeferred<List<PlaceSummary>>()

    override suspend fun getPlaces(query: PlaceQuery): List<PlaceSummary> {
        queries += query
        if (queries.size == 1) {
            return listOf(
                PlaceSummary(
                    placeId = "toilet-1",
                    name = "Accessible Toilet",
                    address = "1 Toilet-ro, Busan",
                    latitude = 35.1796,
                    longitude = 129.0756,
                    category = PlaceCategory.TOILET,
                ),
                PlaceSummary(
                    placeId = "elevator-1",
                    name = "Station Elevator",
                    address = "2 Elevator-ro, Busan",
                    latitude = 35.1802,
                    longitude = 129.0762,
                    category = PlaceCategory.ELEVATOR,
                ),
            )
        }

        searchHereRequested.complete(Unit)
        return searchHereGate.await()
    }

    override suspend fun getPlaceDetail(placeId: String): PlaceDetail? = null

    override suspend fun getMapTappedPlaceDetail(request: MapPlaceDetailRequest): MapTappedPlaceDetail? = null

    fun completeSearchHere(places: List<PlaceSummary>) {
        searchHereGate.complete(places)
    }
}

private class SequentialPlacesRepository(
    private val responses: List<List<PlaceSummary>>,
) : PlacesRepository {
    val queries = mutableListOf<PlaceQuery>()
    private var responseIndex = 0

    override suspend fun getPlaces(query: PlaceQuery): List<PlaceSummary> {
        queries += query
        val resolvedIndex = responseIndex.coerceAtMost(responses.lastIndex)
        responseIndex += 1
        return responses[resolvedIndex]
    }

    override suspend fun getPlaceDetail(placeId: String): PlaceDetail? = null

    override suspend fun getMapTappedPlaceDetail(request: MapPlaceDetailRequest): MapTappedPlaceDetail? = null
}

private class FakePlacesRepository(
    private val places: List<PlaceSummary> = emptyList(),
    private val placeDetailsById: Map<String, PlaceDetail> = emptyMap(),
    private val mapTapDetail: MapTappedPlaceDetail? = null,
    private val mapTapDetailsByClickType: Map<MapPlaceClickType, MapTappedPlaceDetail?> = emptyMap(),
    private val placesFailure: Throwable? = null,
    private val detailFailureById: Map<String, Throwable> = emptyMap(),
    private val mapTapDetailFailure: Throwable? = null,
    private val mapTapDetailFailuresByClickType: Map<MapPlaceClickType, Throwable> = emptyMap(),
) : PlacesRepository {
    val queries = mutableListOf<PlaceQuery>()
    val detailRequests = mutableListOf<String>()
    val mapTapDetailRequests = mutableListOf<MapPlaceDetailRequest>()

    override suspend fun getPlaces(query: PlaceQuery): List<PlaceSummary> {
        queries += query
        placesFailure?.let { throw it }
        return places
    }

    override suspend fun getPlaceDetail(placeId: String): PlaceDetail? {
        detailRequests += placeId
        detailFailureById[placeId]?.let { throw it }
        return placeDetailsById[placeId]
    }

    override suspend fun getMapTappedPlaceDetail(request: MapPlaceDetailRequest): MapTappedPlaceDetail? {
        mapTapDetailRequests += request
        mapTapDetailFailuresByClickType[request.clickType]?.let { throw it }
        if (mapTapDetailsByClickType.containsKey(request.clickType)) {
            return mapTapDetailsByClickType[request.clickType]
        }
        mapTapDetailFailure?.let { throw it }
        return mapTapDetail
    }
}

private fun testFacilitySeedRepository(): FacilitySeedRepository =
    DefaultFacilitySeedRepository(
        localDataSource = FacilitySeedLocalDataSource(),
        mockDataSource = FacilitySeedMockDataSource(),
    )

private fun facilitySeedRepositoryWithOtherCategory(): FacilitySeedRepository =
    object : FacilitySeedRepository {
        private val toiletMarker =
            FacilityMarkerSeed(
                facilityId = "facility-toilet",
                name = "장애인 화장실",
                coordinate = GeoCoordinate(latitude = 35.1, longitude = 129.1),
                category = FacilityCategory.TOILET,
            )
        private val otherMarker =
            FacilityMarkerSeed(
                facilityId = "facility-other",
                name = "기타 편의시설",
                coordinate = GeoCoordinate(latitude = 35.2, longitude = 129.2),
                category = FacilityCategory.OTHER,
            )
        private val details =
            listOf(
                FacilityDetailSeed(
                    facilityId = toiletMarker.facilityId,
                    name = toiletMarker.name,
                    address = "부산광역시 테스트구 1",
                    coordinate = toiletMarker.coordinate,
                    category = toiletMarker.category,
                ),
                FacilityDetailSeed(
                    facilityId = otherMarker.facilityId,
                    name = otherMarker.name,
                    address = "부산광역시 테스트구 2",
                    coordinate = otherMarker.coordinate,
                    category = otherMarker.category,
                ),
            )

        override suspend fun getSeedCatalog(): FacilitySeedCatalog =
            FacilitySeedCatalog(
                facilities =
                    details.map { detail ->
                        FacilitySeed(
                            facilityId = detail.facilityId,
                            name = detail.name,
                            address = detail.address,
                            coordinate = detail.coordinate,
                            category = detail.category,
                        )
                    },
            )

        override suspend fun getFacilityBrowseData(query: FacilitySeedQuery): FacilityBrowseData =
            FacilityBrowseData(
                facilityMarkers = listOf(toiletMarker, otherMarker),
                detailsById = details.associateBy { detail -> detail.facilityId },
                availableCategories = listOf(FacilityCategory.TOILET, FacilityCategory.OTHER),
            )

        override suspend fun getFacilityMarkers(query: FacilitySeedQuery): List<FacilityMarkerSeed> =
            listOf(toiletMarker, otherMarker)

        override suspend fun getFacilityDetail(facilityId: String): FacilityDetailSeed? =
            details.firstOrNull { detail -> detail.facilityId == facilityId }
    }

private class EmptyFacilitySeedRepository : FacilitySeedRepository {
    override suspend fun getSeedCatalog(): FacilitySeedCatalog = FacilitySeedCatalog()

    override suspend fun getFacilityBrowseData(query: FacilitySeedQuery): FacilityBrowseData = FacilityBrowseData()

    override suspend fun getFacilityMarkers(query: FacilitySeedQuery): List<FacilityMarkerSeed> = emptyList()

    override suspend fun getFacilityDetail(facilityId: String): FacilityDetailSeed? = null
}

private class FailingFacilitySeedRepository : FacilitySeedRepository {
    override suspend fun getSeedCatalog(): FacilitySeedCatalog {
        error("browse load failed")
    }

    override suspend fun getFacilityBrowseData(query: FacilitySeedQuery): FacilityBrowseData {
        error("browse load failed")
    }

    override suspend fun getFacilityMarkers(query: FacilitySeedQuery): List<FacilityMarkerSeed> {
        error("browse load failed")
    }

    override suspend fun getFacilityDetail(facilityId: String): FacilityDetailSeed? {
        error("browse load failed")
    }
}

private fun recentDestination(
    placeId: String,
    searchedAtMillis: Long,
): RecentDestination =
    RecentDestination(
        placeId = placeId,
        name = "Recent Destination $placeId",
        address = "Busan Address $placeId",
        latitude = 35.1796,
        longitude = 129.0756,
        category = PlaceCategory.RESTAURANT,
        accessibilityTagKeys = listOf("accessible-parking"),
        searchedAtMillis = searchedAtMillis,
    )
