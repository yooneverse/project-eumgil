package com.ssafy.e102.eumgil.feature.route

import com.ssafy.e102.eumgil.core.location.CurrentLocationManager
import com.ssafy.e102.eumgil.core.location.LocationGrantAccuracy
import com.ssafy.e102.eumgil.core.location.LocationPermissionManager
import com.ssafy.e102.eumgil.core.location.LocationPermissionState
import com.ssafy.e102.eumgil.core.location.LocationSnapshot
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.MapPlaceClickType
import com.ssafy.e102.eumgil.core.model.MapPlaceDetailRequest
import com.ssafy.e102.eumgil.core.model.MapPlaceDetailType
import com.ssafy.e102.eumgil.core.model.MapTappedPlaceDetail
import com.ssafy.e102.eumgil.core.model.PlaceCategory
import com.ssafy.e102.eumgil.core.model.PlaceDetail
import com.ssafy.e102.eumgil.core.model.PlaceDestination
import com.ssafy.e102.eumgil.core.model.PlaceQuery
import com.ssafy.e102.eumgil.core.model.PlaceSummary
import com.ssafy.e102.eumgil.core.model.RouteCandidate
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.core.model.RoutePolyline
import com.ssafy.e102.eumgil.core.model.RoutePreviewModel
import com.ssafy.e102.eumgil.core.model.RouteRiskLevel
import com.ssafy.e102.eumgil.core.model.RouteSearchData
import com.ssafy.e102.eumgil.core.model.RouteSearchQuery
import com.ssafy.e102.eumgil.core.model.RouteSearchResult
import com.ssafy.e102.eumgil.core.model.RouteSearchSource
import com.ssafy.e102.eumgil.core.model.RouteSegment
import com.ssafy.e102.eumgil.core.model.RouteSummary
import com.ssafy.e102.eumgil.data.repository.InMemoryDestinationSelectionRepository
import com.ssafy.e102.eumgil.data.repository.PlacesRepository
import com.ssafy.e102.eumgil.data.repository.RouteRatingData
import com.ssafy.e102.eumgil.data.repository.RouteRepository
import com.ssafy.e102.eumgil.data.repository.RouteRerouteData
import com.ssafy.e102.eumgil.data.repository.RouteSessionData
import com.ssafy.e102.eumgil.data.repository.RouteTransitRefreshData
import com.ssafy.e102.eumgil.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RouteSettingOriginSelectionTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `route setting starts and stops active location updates while screen is visible`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val locationManager = FakeCurrentLocationManager()
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = RecordingRouteRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    currentLocationManager = locationManager,
                )

            advanceUntilIdle()
            viewModel.startLocationUpdates()
            viewModel.stopLocationUpdates()

            assertEquals(1, locationManager.refreshCallCount)
            assertEquals(1, locationManager.startCallCount)
            assertEquals(1, locationManager.stopCallCount)
        }

    @Test
    fun `current location button refreshes route setting gps tracking`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val locationManager = FakeCurrentLocationManager()
            val permissionManager =
                FakeLocationPermissionManager(
                    initialState = LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE),
                )
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = RecordingRouteRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    currentLocationManager = locationManager,
                    locationPermissionManager = permissionManager,
                )

            advanceUntilIdle()
            val permissionRefreshBefore = permissionManager.refreshCallCount
            val startBefore = locationManager.startCallCount
            val refreshBefore = locationManager.refreshCallCount
            viewModel.onAction(RouteSettingUiAction.CurrentLocationClicked)

            assertEquals(permissionRefreshBefore + 1, permissionManager.refreshCallCount)
            assertEquals(startBefore + 1, locationManager.startCallCount)
            assertEquals(refreshBefore + 1, locationManager.refreshCallCount)
        }

    @Test
    fun `missing gps location shows direct selection guidance instead of demo origin copy`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val locationManager = FakeCurrentLocationManager()
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = RecordingRouteRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    currentLocationManager = locationManager,
                )

            advanceUntilIdle()
            viewModel.startLocationUpdates()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.origin.name.isBlank())
            assertFalse(viewModel.uiState.value.origin.supportingText.isNullOrBlank())
            assertEquals(RouteOriginState.CURRENT_LOCATION_UNAVAILABLE, viewModel.uiState.value.originState)
            assertFalse(viewModel.uiState.value.originStatus?.label.isNullOrBlank())
            assertNull(viewModel.uiState.value.origin.metadataLabel)
        }

    @Test
    fun `route setting requests location permission when automatic origin has no permission`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val locationManager = FakeCurrentLocationManager()
            val permissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied)
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = RecordingRouteRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    currentLocationManager = locationManager,
                    locationPermissionManager = permissionManager,
            )

            advanceUntilIdle()
            val event = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvent.first() }
            viewModel.startLocationUpdates()

            assertEquals(RouteSettingUiEvent.RequestLocationPermission, event.await())
            assertEquals(1, permissionManager.refreshCallCount)
            assertEquals(0, locationManager.startCallCount)
        }

    @Test
    fun `prechecked route setting skips duplicate permission prompt when automatic origin has no permission`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val locationManager = FakeCurrentLocationManager()
            val permissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied)
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = RecordingRouteRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    currentLocationManager = locationManager,
                    locationPermissionManager = permissionManager,
            )

            advanceUntilIdle()
            val event = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvent.first() }
            viewModel.startLocationUpdates(requestLocationPermissionIfNeeded = false)
            runCurrent()

            assertFalse(event.isCompleted)
            event.cancel()
            assertEquals(1, permissionManager.refreshCallCount)
            assertEquals(0, locationManager.startCallCount)
            assertEquals(1, locationManager.stopCallCount)
        }

    @Test
    fun `granting location permission after auto request starts current location tracking`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = RecordingRouteRepository()
            val locationManager = FakeCurrentLocationManager()
            val permissionManager = FakeLocationPermissionManager(initialState = LocationPermissionState.Denied)
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = routeRepository,
                    destinationSelectionRepository = destinationSelectionRepository,
                    currentLocationManager = locationManager,
                    locationPermissionManager = permissionManager,
            )

            advanceUntilIdle()
            val event = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvent.first() }
            viewModel.startLocationUpdates()
            assertEquals(RouteSettingUiEvent.RequestLocationPermission, event.await())

            permissionManager.updateState(LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE))
            locationManager.updateLocation(
                testLocationSnapshot(
                    latitude = 35.1701,
                    longitude = 129.0712,
                ),
            )
            advanceUntilIdle()

            assertEquals(1, locationManager.startCallCount)
            assertEquals(RouteOriginState.CURRENT_LOCATION_RESOLVED, viewModel.uiState.value.originState)
            assertTrue(routeRepository.callCount >= 2)
            assertEquals(35.1701, routeRepository.queries.last().origin.coordinate.latitude, 0.0)
            assertEquals(129.0712, routeRepository.queries.last().origin.coordinate.longitude, 0.0)
        }

    @Test
    fun `gps location initializes origin when no manual origin exists`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = RecordingRouteRepository()
            val currentLocation =
                testLocationSnapshot(
                    latitude = 35.1701,
                    longitude = 129.0712,
                )
            val placesRepository =
                FakePlacesRepository(
                    mapTapDetail =
                        testCurrentLocationDetail(
                            name = "Busan Citizens Park",
                            address = "100 Simingongwon-ro, Busan",
                            latitude = currentLocation.latitude,
                            longitude = currentLocation.longitude,
                        ),
                )
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = routeRepository,
                    destinationSelectionRepository = destinationSelectionRepository,
                    currentLocationManager = FakeCurrentLocationManager(initialLocation = currentLocation),
                    placesRepository = placesRepository,
                )

            advanceUntilIdle()
            val originCoordinate = requireNotNull(viewModel.uiState.value.origin.coordinate)
            val detailRequest = placesRepository.mapTapDetailRequests.single()

            assertEquals(currentLocation.latitude, detailRequest.latitude, 0.0)
            assertEquals(currentLocation.longitude, detailRequest.longitude, 0.0)
            assertEquals(MapPlaceClickType.ADDRESS, detailRequest.clickType)
            assertEquals("Busan Citizens Park", viewModel.uiState.value.origin.name)
            assertEquals("100 Simingongwon-ro, Busan", viewModel.uiState.value.origin.supportingText)
            assertEquals(RouteOriginState.CURRENT_LOCATION_RESOLVED, viewModel.uiState.value.originState)
            assertFalse(viewModel.uiState.value.originStatus?.label.isNullOrBlank())
            assertEquals("Busan Citizens Park", routeRepository.queries.single().origin.name)
            assertEquals("100 Simingongwon-ro, Busan", routeRepository.queries.single().origin.address)
            assertEquals(currentLocation.latitude, originCoordinate.latitude, 0.0)
            assertEquals(currentLocation.longitude, originCoordinate.longitude, 0.0)
            assertEquals(currentLocation.latitude, routeRepository.queries.single().origin.coordinate.latitude, 0.0)
            assertEquals(currentLocation.longitude, routeRepository.queries.single().origin.coordinate.longitude, 0.0)
        }

    @Test
    fun `manual origin selection is not overwritten by later gps updates`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = RecordingRouteRepository()
            val locationManager =
                FakeCurrentLocationManager(
                    initialLocation =
                        testLocationSnapshot(
                            latitude = 35.1701,
                            longitude = 129.0712,
                        ),
                )
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = routeRepository,
                    destinationSelectionRepository = destinationSelectionRepository,
                    currentLocationManager = locationManager,
                )

            advanceUntilIdle()
            destinationSelectionRepository.updateSelectedOrigin(testOrigin())
            advanceUntilIdle()
            locationManager.updateLocation(
                testLocationSnapshot(
                    latitude = 35.1999,
                    longitude = 129.0999,
                ),
            )
            advanceUntilIdle()

            assertTrue(routeRepository.callCount >= 2)
            assertEquals("origin-1", viewModel.uiState.value.origin.placeId)
            assertEquals(testOrigin().latitude, routeRepository.queries.last().origin.coordinate.latitude, 0.0)
            assertEquals(testOrigin().longitude, routeRepository.queries.last().origin.coordinate.longitude, 0.0)
        }

    @Test
    fun `manual origin selection hides direct selection status label`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = RecordingRouteRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    currentLocationManager = FakeCurrentLocationManager(),
                )

            advanceUntilIdle()
            destinationSelectionRepository.updateSelectedOrigin(testOrigin())
            advanceUntilIdle()

            assertEquals(RouteOriginState.MANUAL_SELECTION, viewModel.uiState.value.originState)
            assertEquals("Seomyeon Station", viewModel.uiState.value.origin.name)
            assertEquals("Seojeon-ro, Busan", viewModel.uiState.value.origin.supportingText)
            assertNull(viewModel.uiState.value.originStatus)
        }

    @Test
    fun `small gps jitter does not trigger a redundant route reload`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = RecordingRouteRepository()
            val locationManager =
                FakeCurrentLocationManager(
                    initialLocation =
                        testLocationSnapshot(
                            latitude = 35.1701,
                            longitude = 129.0712,
                        ),
                )
            RouteSettingViewModel(
                routeRepository = routeRepository,
                destinationSelectionRepository = destinationSelectionRepository,
                currentLocationManager = locationManager,
            )

            advanceUntilIdle()
            locationManager.updateLocation(
                testLocationSnapshot(
                    latitude = 35.17014,
                    longitude = 129.07124,
                ),
            )
            advanceUntilIdle()

            assertEquals(1, routeRepository.callCount)
            assertEquals(35.1701, routeRepository.queries.single().origin.coordinate.latitude, 0.0)
            assertEquals(129.0712, routeRepository.queries.single().origin.coordinate.longitude, 0.0)
        }

    @Test
    fun `changing origin and destination requeries route with updated endpoints`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = RecordingRouteRepository()
            RouteSettingViewModel(
                routeRepository = routeRepository,
                destinationSelectionRepository = destinationSelectionRepository,
                currentLocationManager =
                    FakeCurrentLocationManager(
                        initialLocation =
                            testLocationSnapshot(
                                latitude = 35.1701,
                                longitude = 129.0712,
                            ),
                    ),
            )

            advanceUntilIdle()
            destinationSelectionRepository.updateSelectedOrigin(testOrigin())
            advanceUntilIdle()
            destinationSelectionRepository.updateSelectedDestination(testUpdatedDestination())
            advanceUntilIdle()

            assertTrue(routeRepository.callCount >= 3)
            assertEquals(testOrigin().latitude, routeRepository.queries.last().origin.coordinate.latitude, 0.0)
            assertEquals(testOrigin().longitude, routeRepository.queries.last().origin.coordinate.longitude, 0.0)
            assertEquals(
                testUpdatedDestination().latitude,
                routeRepository.queries.last().destination.coordinate.latitude,
                0.0,
            )
            assertEquals(
                testUpdatedDestination().longitude,
                routeRepository.queries.last().destination.coordinate.longitude,
                0.0,
            )
        }

    @Test
    fun `same destination reselection requeries route through explicit selection request`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = RecordingRouteRepository()
            RouteSettingViewModel(
                routeRepository = routeRepository,
                destinationSelectionRepository = destinationSelectionRepository,
                currentLocationManager =
                    FakeCurrentLocationManager(
                        initialLocation =
                            testLocationSnapshot(
                                latitude = 35.1701,
                                longitude = 129.0712,
                            ),
                    ),
            )

            advanceUntilIdle()
            destinationSelectionRepository.updateSelectedDestination(testDestination())
            advanceUntilIdle()

            assertEquals(2, routeRepository.callCount)
            assertEquals(testDestination().latitude, routeRepository.queries.last().destination.coordinate.latitude, 0.0)
            assertEquals(testDestination().longitude, routeRepository.queries.last().destination.coordinate.longitude, 0.0)
        }

    @Test
    fun `swap action swaps effective endpoints and requeries route`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedOrigin(testOrigin())
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = RecordingRouteRepository()
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = routeRepository,
                    destinationSelectionRepository = destinationSelectionRepository,
                    currentLocationManager = FakeCurrentLocationManager(),
                )

            advanceUntilIdle()
            viewModel.onAction(RouteSettingUiAction.WaypointsSwapClicked)
            advanceUntilIdle()

            assertTrue(routeRepository.callCount >= 2)
            assertEquals("destination-1", viewModel.uiState.value.origin.placeId)
            assertEquals("origin-1", viewModel.uiState.value.destination.placeId)
            assertEquals(testDestination().latitude, routeRepository.queries.last().origin.coordinate.latitude, 0.0)
            assertEquals(testDestination().longitude, routeRepository.queries.last().origin.coordinate.longitude, 0.0)
            assertEquals(testOrigin().latitude, routeRepository.queries.last().destination.coordinate.latitude, 0.0)
            assertEquals(testOrigin().longitude, routeRepository.queries.last().destination.coordinate.longitude, 0.0)
        }

    @Test
    fun `selection and location changes while loading collapse into one follow-up search`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = SlowRecordingRouteRepository()
            val locationManager =
                FakeCurrentLocationManager(
                    initialLocation =
                        testLocationSnapshot(
                            latitude = 35.1701,
                            longitude = 129.0712,
                        ),
                )
            RouteSettingViewModel(
                routeRepository = routeRepository,
                destinationSelectionRepository = destinationSelectionRepository,
                currentLocationManager = locationManager,
            )

            runCurrent()
            assertEquals(1, routeRepository.callCount)

            destinationSelectionRepository.updateSelectedDestination(testUpdatedDestination())
            locationManager.updateLocation(
                testLocationSnapshot(
                    latitude = 35.1716,
                    longitude = 129.0728,
                ),
            )
            advanceUntilIdle()

            assertEquals(2, routeRepository.callCount)
            assertEquals(testUpdatedDestination().latitude, routeRepository.queries.last().destination.coordinate.latitude, 0.0)
            assertEquals(testUpdatedDestination().longitude, routeRepository.queries.last().destination.coordinate.longitude, 0.0)
            assertEquals(35.1716, routeRepository.queries.last().origin.coordinate.latitude, 0.0)
            assertEquals(129.0728, routeRepository.queries.last().origin.coordinate.longitude, 0.0)
        }
}

private fun testOrigin(): PlaceDestination =
    PlaceDestination(
        placeId = "origin-1",
        name = "Seomyeon Station",
        address = "Seojeon-ro, Busan",
        latitude = 35.1578,
        longitude = 129.0592,
        category = PlaceCategory.OTHER,
    )

private fun testDestination(): PlaceDestination =
    PlaceDestination(
        placeId = "destination-1",
        name = "Busan City Hall",
        address = "1001 Jungang-daero, Busan",
        latitude = 35.1797,
        longitude = 129.0750,
        category = PlaceCategory.TOURIST_ATTRACTION,
    )

private fun testUpdatedDestination(): PlaceDestination =
    PlaceDestination(
        placeId = "destination-2",
        name = "Busan Station",
        address = "206 Jungang-daero, Busan",
        latitude = 35.1151,
        longitude = 129.0414,
        category = PlaceCategory.OTHER,
    )

private fun testLocationSnapshot(
    latitude: Double,
    longitude: Double,
): LocationSnapshot =
    LocationSnapshot(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = 5f,
        recordedAtEpochMillis = 1_000L,
    )

private fun testCurrentLocationDetail(
    name: String,
    address: String,
    latitude: Double,
    longitude: Double,
): MapTappedPlaceDetail =
    MapTappedPlaceDetail(
        bookmarkTargetId = "current-location:$latitude,$longitude",
        detailType = MapPlaceDetailType.EXTERNAL_ADDRESS,
        placeId = null,
        provider = null,
        providerPlaceId = null,
        name = name,
        category = PlaceCategory.OTHER,
        providerCategory = null,
        address = address,
        latitude = latitude,
        longitude = longitude,
    )

private class FakeCurrentLocationManager(
    initialLocation: LocationSnapshot? = null,
) : CurrentLocationManager {
    private val mutableLatestLocation = MutableStateFlow(initialLocation)
    var refreshCallCount: Int = 0
        private set
    var startCallCount: Int = 0
        private set
    var stopCallCount: Int = 0
        private set

    override val latestLocation: StateFlow<LocationSnapshot?> = mutableLatestLocation

    override fun refreshLatestLocation() {
        refreshCallCount += 1
    }

    override fun startLocationUpdates() {
        startCallCount += 1
    }

    override fun stopLocationUpdates() {
        stopCallCount += 1
    }

    fun updateLocation(snapshot: LocationSnapshot?) {
        mutableLatestLocation.value = snapshot
    }
}

private class FakeLocationPermissionManager(
    initialState: LocationPermissionState,
) : LocationPermissionManager {
    private val mutablePermissionState = MutableStateFlow(initialState)
    var refreshCallCount: Int = 0
        private set

    override val permissionState: StateFlow<LocationPermissionState> = mutablePermissionState

    override fun refreshPermissionState() {
        refreshCallCount += 1
    }

    override fun requestLocationPermission(activity: androidx.activity.ComponentActivity) = Unit

    fun updateState(state: LocationPermissionState) {
        mutablePermissionState.value = state
    }
}

private class FakePlacesRepository(
    private val places: List<PlaceSummary> = emptyList(),
    private val placeDetailsById: Map<String, PlaceDetail> = emptyMap(),
    private val mapTapDetail: MapTappedPlaceDetail? = null,
) : PlacesRepository {
    val queries = mutableListOf<PlaceQuery>()
    val detailRequests = mutableListOf<String>()
    val mapTapDetailRequests = mutableListOf<MapPlaceDetailRequest>()

    override suspend fun getPlaces(query: PlaceQuery): List<PlaceSummary> {
        queries += query
        return places
    }

    override suspend fun getPlaceDetail(placeId: String): PlaceDetail? {
        detailRequests += placeId
        return placeDetailsById[placeId]
    }

    override suspend fun getMapTappedPlaceDetail(request: MapPlaceDetailRequest): MapTappedPlaceDetail? {
        mapTapDetailRequests += request
        return mapTapDetail
    }
}

private open class RecordingRouteRepository : RouteRepository {
    val queries = mutableListOf<RouteSearchQuery>()
    val callCount: Int
        get() = queries.size

    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        queries += query
        return RouteSearchData(
            query = query,
            result =
                RouteSearchResult(
                    origin = query.origin,
                    destination = query.destination,
                    routes =
                        listOf(
                            RouteCandidate(
                                routeOption = RouteOption.SAFE,
                                title = "Safe Route",
                                summary =
                                    RouteSummary(
                                        distanceMeters = 700,
                                        estimatedTimeMinutes = 15,
                                        riskLevel = RouteRiskLevel.LOW,
                                    ),
                                preview =
                                    RoutePreviewModel(
                                        polyline =
                                            RoutePolyline(
                                                points =
                                                    listOf(
                                                        query.origin.coordinate,
                                                        query.destination.coordinate,
                                                    ),
                                            ),
                                        segmentCount = 1,
                                        renderableSegmentCount = 1,
                                        fallbackSegmentCount = 0,
                                    ),
                                segments =
                                    listOf(
                                        RouteSegment(
                                            sequence = 1,
                                            distanceMeters = 900,
                                            guidanceMessage = "Head to destination",
                                        ),
                                    ),
                            ),
                        ),
                ),
            source =
                RouteSearchSource.serverApi(
                    label = "Recording route payload",
                ),
        )
    }

    override suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("getTransitRouteSearchData was not expected")

    override suspend fun selectRoute(
        routeId: String,
        searchId: String,
    ): RouteSessionData =
        error("selectRoute was not expected")

    override suspend fun refreshTransit(
        routeId: String,
        legSequence: Int,
    ): RouteTransitRefreshData = RouteTransitRefreshData(type = "BUS", arrivalStatus = "UNKNOWN")

    override suspend fun reroute(
        routeId: String,
        currentPoint: GeoCoordinate,
    ): RouteRerouteData = RouteRerouteData()

    override suspend fun endRoute(routeId: String): RouteSessionData = RouteSessionData(sessionId = "session-$routeId")

    override suspend fun rateRoute(
        sessionId: String,
        score: Int,
    ): RouteRatingData = RouteRatingData(ratingId = 0L)
}

private class SlowRecordingRouteRepository : RouteRepository {
    val queries = mutableListOf<RouteSearchQuery>()
    val callCount: Int
        get() = queries.size

    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        queries += query
        delay(1_000)
        return RecordingRouteRepository().getRouteSearchData(query)
    }

    override suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("getTransitRouteSearchData was not expected")

    override suspend fun selectRoute(
        routeId: String,
        searchId: String,
    ): RouteSessionData =
        error("selectRoute was not expected")

    override suspend fun refreshTransit(
        routeId: String,
        legSequence: Int,
    ): RouteTransitRefreshData = RouteTransitRefreshData(type = "BUS", arrivalStatus = "UNKNOWN")

    override suspend fun reroute(
        routeId: String,
        currentPoint: GeoCoordinate,
    ): RouteRerouteData = RouteRerouteData()

    override suspend fun endRoute(routeId: String): RouteSessionData = RouteSessionData(sessionId = "session-$routeId")

    override suspend fun rateRoute(
        sessionId: String,
        score: Int,
    ): RouteRatingData = RouteRatingData(ratingId = 0L)
}
