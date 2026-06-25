package com.ssafy.e102.eumgil.feature.navigation

import com.ssafy.e102.eumgil.core.location.CurrentHeadingManager
import com.ssafy.e102.eumgil.core.location.CurrentLocationManager
import com.ssafy.e102.eumgil.core.location.HeadingSnapshot
import com.ssafy.e102.eumgil.core.location.LocationSnapshot
import com.ssafy.e102.eumgil.core.location.LocationUpdateProfile
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.RouteCandidate
import com.ssafy.e102.eumgil.core.model.RouteGuidanceDirection
import com.ssafy.e102.eumgil.core.model.RouteGuidanceType
import com.ssafy.e102.eumgil.core.model.RouteLeg
import com.ssafy.e102.eumgil.core.model.RouteLegRole
import com.ssafy.e102.eumgil.core.model.RouteLegType
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
import com.ssafy.e102.eumgil.core.model.RouteTransportMode
import com.ssafy.e102.eumgil.core.model.RouteTransitStop
import com.ssafy.e102.eumgil.core.model.RouteWaypoint
import com.ssafy.e102.eumgil.data.repository.BookmarkData
import com.ssafy.e102.eumgil.data.repository.BookmarkRepository
import com.ssafy.e102.eumgil.data.repository.HazardReportRerouteResult
import com.ssafy.e102.eumgil.data.repository.RouteRatingData
import com.ssafy.e102.eumgil.data.repository.RouteRerouteData
import com.ssafy.e102.eumgil.data.repository.RouteRepository
import com.ssafy.e102.eumgil.data.repository.RouteSessionData
import com.ssafy.e102.eumgil.data.repository.RouteTransitArrivalData
import com.ssafy.e102.eumgil.data.repository.RouteTransitRefreshData
import com.ssafy.e102.eumgil.data.repository.ReportRepository
import com.ssafy.e102.eumgil.feature.map.component.MapViewportPointKind
import com.ssafy.e102.eumgil.feature.map.component.createNavigationViewportOverlayState
import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import com.ssafy.e102.eumgil.feature.route.RouteNavigationRequest
import com.ssafy.e102.eumgil.feature.route.RouteNavigationSelectionHandoff
import com.ssafy.e102.eumgil.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `report click emits guidance report navigation event`() =
        runTest {
            val viewModel = createViewModel()
            val eventDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.first() }

            viewModel.onAction(NavigationUiAction.ReportClicked)
            advanceUntilIdle()

            assertEquals(NavigationUiEvent.NavigateToReport, eventDeferred.await())
        }

    @Test
    fun `submitted hazard report reroutes current navigation immediately`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val reportRepository =
                FakeNavigationReportRepository(
                    rerouteResult = HazardReportRerouteResult(rerouted = true, route = reroutedWalkRoute()),
                )
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    reportRepository = reportRepository,
                )

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()
            locationManager.emitLocation(WALK_MID_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            locationManager.emitLocation(WALK_MID_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()

            viewModel.onAction(NavigationUiAction.HazardReportSubmitted(reportId = 42L))
            advanceUntilIdle()

            assertEquals(
                listOf(HazardRerouteCall(42L, "walk-route-1", WALK_MID_POINT, 1)),
                reportRepository.hazardRerouteCalls,
            )
            assertEquals("rr_rerouted_walk_1", viewModel.currentRouteDetailRequest()?.selectedRoute?.serverRouteId)
        }

    @Test
    fun `submitted hazard report keeps current route and prompts duribal when no alternate route exists`() =
        runTest {
            val reportRepository =
                FakeNavigationReportRepository(
                    rerouteResult = HazardReportRerouteResult(rerouted = false, route = null),
                )
            val locationManager = FakeCurrentLocationManager()
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    reportRepository = reportRepository,
                )
            val eventDeferred =
                async(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.uiEvent.first { it is NavigationUiEvent.ShowDuribalCallDialog }
                }

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()
            locationManager.emitLocation(WALK_MID_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            locationManager.emitLocation(WALK_MID_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()
            viewModel.onAction(NavigationUiAction.HazardReportSubmitted(reportId = 42L))
            advanceUntilIdle()

            assertEquals("walk-route-1", viewModel.currentRouteDetailRequest()?.selectedRoute?.serverRouteId)
            assertEquals(NavigationUiEvent.ShowDuribalCallDialog, eventDeferred.await())
        }

    @Test
    fun `submitted hazard report keeps current route and prompts duribal when reroute request fails`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val reportRepository =
                FakeNavigationReportRepository(
                    rerouteFailure = IllegalStateException("reroute failed"),
                )
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    reportRepository = reportRepository,
                )
            val eventDeferred =
                async(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.uiEvent.first { it is NavigationUiEvent.ShowDuribalCallDialog }
                }

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()
            locationManager.emitLocation(WALK_MID_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            locationManager.emitLocation(WALK_MID_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()

            viewModel.onAction(NavigationUiAction.HazardReportSubmitted(reportId = 42L))
            advanceUntilIdle()

            assertEquals(
                listOf(HazardRerouteCall(42L, "walk-route-1", WALK_MID_POINT, 1)),
                reportRepository.hazardRerouteCalls,
            )
            assertEquals("walk-route-1", viewModel.currentRouteDetailRequest()?.selectedRoute?.serverRouteId)
            assertEquals(NavigationUiEvent.ShowDuribalCallDialog, eventDeferred.await())
        }

    @Test
    fun `submitted hazard report does not reroute without a current location`() =
        runTest {
            val reportRepository =
                FakeNavigationReportRepository(
                    rerouteResult = HazardReportRerouteResult(rerouted = true, route = reroutedWalkRoute()),
                )
            val viewModel = createViewModel(reportRepository = reportRepository)

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()

            viewModel.onAction(NavigationUiAction.HazardReportSubmitted(reportId = 42L))
            advanceUntilIdle()

            assertTrue(reportRepository.hazardRerouteCalls.isEmpty())
            assertEquals("walk-route-1", viewModel.currentRouteDetailRequest()?.selectedRoute?.serverRouteId)
        }

    @Test
    fun `submitted hazard report is queued while route reroute is already in flight`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val routeRepository = FakeRouteRepository(rerouteGate = CompletableDeferred())
            val reportRepository = FakeNavigationReportRepository()
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    routeRepository = routeRepository,
                    reportRepository = reportRepository,
                )

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()

            locationManager.emitLocation(
                OFF_ROUTE_WITHIN_OLD_THRESHOLD_POINT.toLocationSnapshot(recordedAtEpochMillis = 4_000L),
            )
            locationManager.emitLocation(
                OFF_ROUTE_WITHIN_OLD_THRESHOLD_POINT.toLocationSnapshot(recordedAtEpochMillis = 5_500L),
            )
            advanceUntilIdle()
            locationManager.emitLocation(OFF_ROUTE_CANDIDATE_POINT.toLocationSnapshot(recordedAtEpochMillis = 10_000L))
            advanceUntilIdle()
            locationManager.emitLocation(OFF_ROUTE_CANDIDATE_POINT.toLocationSnapshot(recordedAtEpochMillis = 11_500L))
            advanceUntilIdle()

            assertEquals(listOf("walk-route-1" to OFF_ROUTE_CANDIDATE_POINT), routeRepository.rerouteCalls)
            assertTrue(reportRepository.hazardRerouteCalls.isEmpty())

            viewModel.onAction(NavigationUiAction.HazardReportSubmitted(reportId = 42L))
            advanceUntilIdle()

            assertTrue(reportRepository.hazardRerouteCalls.isEmpty())

            routeRepository.completeReroute()
            advanceUntilIdle()

            assertEquals(
                listOf(HazardRerouteCall(42L, "walk-route-1", OFF_ROUTE_CANDIDATE_POINT, 1)),
                reportRepository.hazardRerouteCalls,
            )
        }

    @Test
    fun `submitted hazard report is ignored in low vision navigation mode`() =
        runTest {
            val reportRepository = FakeNavigationReportRepository()
            val viewModel =
                createViewModel(
                    reportRepository = reportRepository,
                    initialLowVisionMode = true,
                )

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()
            viewModel.onAction(NavigationUiAction.HazardReportSubmitted(reportId = 42L))
            advanceUntilIdle()

            assertTrue(reportRepository.hazardRerouteCalls.isEmpty())
        }

    @Test
    fun `submitted hazard report does not call reroute while active leg is transit`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val reportRepository = FakeNavigationReportRepository()
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    reportRepository = reportRepository,
                )
            val eventDeferred =
                async(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.uiEvent.first { it is NavigationUiEvent.ShowDuribalCallDialog }
                }

            viewModel.bindNavigationRequest(testPartialTransitWalkPolylineNavigationRequest())
            advanceUntilIdle()
            locationManager.emitLocation(PARTIAL_TRANSIT_RIDE_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            locationManager.emitLocation(PARTIAL_TRANSIT_RIDE_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()

            viewModel.onAction(NavigationUiAction.HazardReportSubmitted(reportId = 42L))
            advanceUntilIdle()

            assertTrue(reportRepository.hazardRerouteCalls.isEmpty())
            assertEquals(NavigationUiEvent.ShowDuribalCallDialog, eventDeferred.await())
        }

    @Test
    fun `submitted hazard report reroutes public transit route while walking to boarding stop`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val reportRepository =
                FakeNavigationReportRepository(
                    rerouteResult =
                        HazardReportRerouteResult(
                            rerouted = true,
                            route = reroutedPartialTransitRoute(serverRouteId = "pt-rerouted-boarding"),
                        ),
                )
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    reportRepository = reportRepository,
                )

            viewModel.bindNavigationRequest(testPartialTransitWalkPolylineNavigationRequest())
            advanceUntilIdle()
            locationManager.emitLocation(PARTIAL_TRANSIT_WALK_PROGRESS_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            locationManager.emitLocation(PARTIAL_TRANSIT_WALK_PROGRESS_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()

            viewModel.onAction(NavigationUiAction.HazardReportSubmitted(reportId = 42L))
            advanceUntilIdle()

            assertEquals(
                listOf(
                    HazardRerouteCall(42L, "partial-transit-walk-route-1", PARTIAL_TRANSIT_WALK_PROGRESS_POINT, 1),
                ),
                reportRepository.hazardRerouteCalls,
            )
            assertEquals("pt-rerouted-boarding", viewModel.currentRouteDetailRequest()?.selectedRoute?.serverRouteId)
        }

    @Test
    fun `submitted hazard report reroutes public transit route while walking to destination`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val reportRepository =
                FakeNavigationReportRepository(
                    rerouteResult =
                        HazardReportRerouteResult(
                            rerouted = true,
                            route = reroutedPartialTransitRoute(serverRouteId = "pt-rerouted-destination"),
                        ),
                )
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    reportRepository = reportRepository,
                )

            viewModel.bindNavigationRequest(testPartialTransitWalkPolylineNavigationRequest())
            advanceUntilIdle()
            locationManager.emitLocation(PARTIAL_TRANSIT_FINAL_WALK_PROGRESS_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            locationManager.emitLocation(PARTIAL_TRANSIT_FINAL_WALK_PROGRESS_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()

            viewModel.onAction(NavigationUiAction.HazardReportSubmitted(reportId = 42L))
            advanceUntilIdle()

            assertEquals(
                listOf(
                    HazardRerouteCall(
                        42L,
                        "partial-transit-walk-route-1",
                        PARTIAL_TRANSIT_FINAL_WALK_PROGRESS_POINT,
                        3,
                    ),
                ),
                reportRepository.hazardRerouteCalls,
            )
            assertEquals("pt-rerouted-destination", viewModel.currentRouteDetailRequest()?.selectedRoute?.serverRouteId)
        }

    @Test
    fun `location updates rebuild runtime remaining distance and eta from route progress`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()

            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = WALK_MID_POINT.latitude,
                    longitude = WALK_MID_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 1_000L,
                ),
            )
            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = WALK_MID_POINT.latitude,
                    longitude = WALK_MID_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 2_500L,
                ),
            )
            advanceUntilIdle()

            assertEquals(NavigationScreenState.Ready, viewModel.uiState.value.screenState)
            assertEquals(1, viewModel.uiState.value.segmentSync.activeSegmentIndex)
            assertEquals(1, viewModel.uiState.value.segmentSync.focusedSegmentIndex)
            assertFalse(viewModel.uiState.value.segmentSync.isInspectingSegments)
            assertEquals(null, viewModel.uiState.value.focusedSegmentCard)
            assertEquals("450m", viewModel.uiState.value.remainingDistanceLabel)
            assertEquals("8분", viewModel.uiState.value.remainingEtaLabel)
            assertEquals("2 / 2", viewModel.uiState.value.progressLabel)
            assertEquals("목적지까지 약 8분", viewModel.uiState.value.stepCard.heroDescription)
        }

    @Test
    fun `selection handoff remaining metrics seed initial navigation summary`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.bindNavigationRequest(
                testWalkNavigationRequest().copy(
                    selectionHandoff =
                        RouteNavigationSelectionHandoff(
                            searchId = "search-1",
                            routeId = "walk-route-1",
                            sessionId = "session-1",
                            initialRemainingDistanceMeters = 720,
                            initialRemainingDurationSeconds = 540,
                        ),
                ),
            )
            advanceUntilIdle()

            assertEquals("720m", viewModel.uiState.value.remainingDistanceLabel)
            assertEquals("9분", viewModel.uiState.value.remainingEtaLabel)
        }

    @Test
    fun `navigation entry shows live first step card instead of selected origin preview`() =
        runTest {
            val locationManager =
                FakeCurrentLocationManager(
                    refreshSnapshot =
                        LocationSnapshot(
                            latitude = WALK_END_POINT.latitude,
                            longitude = WALK_END_POINT.longitude,
                            accuracyMeters = 5f,
                            recordedAtEpochMillis = 1_000L,
                        ),
                )
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()

            assertEquals(1, locationManager.refreshLatestLocationCallCount)
            assertEquals(NavigationOriginSegmentIndex, viewModel.uiState.value.segmentSync.activeSegmentIndex)
            assertEquals(NavigationOriginSegmentIndex, viewModel.uiState.value.segmentSync.focusedSegmentIndex)
            assertFalse(viewModel.uiState.value.segmentSync.isInspectingSegments)
            assertEquals(NavigationOriginSegmentIndex, viewModel.uiState.value.segmentSync.railItems.firstOrNull()?.index)
            assertEquals(null, viewModel.uiState.value.focusedSegmentCard)
            assertEquals("1 / 2", viewModel.uiState.value.progressLabel)
            assertEquals(expectedSpeechText(viewModel.uiState.value.stepCard), viewModel.uiState.value.tts.briefingText)
            assertEquals("출발", viewModel.uiState.value.stepCard.heroTitle)
            assertEquals("경로 시작 지점까지 이동하세요", viewModel.uiState.value.stepCard.heroDescription)
        }

    @Test
    fun `navigation start keeps live card even when origin projection is near the destination`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.bindNavigationRequest(
                testWalkNavigationRequest().copy(
                    origin =
                        RouteWaypoint(
                            name = "?꾩옱 ?꾩튂",
                            coordinate = WALK_END_POINT,
                        ),
                ),
            )
            advanceUntilIdle()

            assertEquals(NavigationOriginSegmentIndex, viewModel.uiState.value.segmentSync.activeSegmentIndex)
            assertEquals(NavigationOriginSegmentIndex, viewModel.uiState.value.segmentSync.focusedSegmentIndex)
            assertFalse(viewModel.uiState.value.segmentSync.isInspectingSegments)
            assertEquals(null, viewModel.uiState.value.focusedSegmentCard)
            assertEquals("1 / 2", viewModel.uiState.value.progressLabel)
            assertEquals("출발", viewModel.uiState.value.stepCard.heroTitle)
            assertEquals("경로 시작 지점까지 이동하세요", viewModel.uiState.value.stepCard.heroDescription)
        }

    @Test
    fun `navigation entry waits for stable route proximity before live guide card`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = WALK_MID_POINT.latitude,
                    longitude = WALK_MID_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 1_000L,
                ),
            )
            advanceUntilIdle()

            assertEquals(NavigationOriginSegmentIndex, viewModel.uiState.value.segmentSync.activeSegmentIndex)
            assertEquals(NavigationOriginSegmentIndex, viewModel.uiState.value.segmentSync.focusedSegmentIndex)
            assertEquals("1 / 2", viewModel.uiState.value.progressLabel)
            assertEquals("출발", viewModel.uiState.value.stepCard.heroTitle)

            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = WALK_MID_POINT.latitude,
                    longitude = WALK_MID_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 2_500L,
                ),
            )
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.segmentSync.activeSegmentIndex)
            assertEquals(1, viewModel.uiState.value.segmentSync.focusedSegmentIndex)
            assertFalse(viewModel.uiState.value.segmentSync.isInspectingSegments)
            assertEquals(null, viewModel.uiState.value.focusedSegmentCard)
            assertEquals("2 / 2", viewModel.uiState.value.progressLabel)
            assertEquals("우회전", viewModel.uiState.value.stepCard.heroTitle)
            assertEquals("목적지까지 약 8분", viewModel.uiState.value.stepCard.heroDescription)
        }

    @Test
    fun `navigation stays on route detail guide while current location is outside selected route`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = OFF_ROUTE_POINT.latitude,
                    longitude = OFF_ROUTE_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 1_000L,
                ),
            )
            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = OFF_ROUTE_POINT.latitude,
                    longitude = OFF_ROUTE_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 2_500L,
                ),
            )
            advanceUntilIdle()

            assertEquals(NavigationOriginSegmentIndex, viewModel.uiState.value.segmentSync.activeSegmentIndex)
            assertEquals(NavigationOriginSegmentIndex, viewModel.uiState.value.segmentSync.focusedSegmentIndex)
            assertFalse(viewModel.uiState.value.segmentSync.isInspectingSegments)
            assertEquals("1 / 2", viewModel.uiState.value.progressLabel)
            assertEquals("출발", viewModel.uiState.value.stepCard.heroTitle)
            assertEquals("경로 시작 지점까지 이동하세요", viewModel.uiState.value.stepCard.heroDescription)
        }

    @Test
    fun `navigation stays on route detail guide near origin until the route line is reached`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(
                testWalkNavigationRequest().copy(
                    origin =
                        RouteWaypoint(
                            name = "현재 위치",
                            coordinate = OFF_ROUTE_POINT,
                        ),
                ),
            )
            locationManager.emitLocation(OFF_ROUTE_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            locationManager.emitLocation(OFF_ROUTE_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()

            assertEquals(NavigationOriginSegmentIndex, viewModel.uiState.value.segmentSync.activeSegmentIndex)
            assertEquals(NavigationOriginSegmentIndex, viewModel.uiState.value.segmentSync.focusedSegmentIndex)
            assertEquals("출발", viewModel.uiState.value.stepCard.heroTitle)
            assertEquals("경로 시작 지점까지 이동하세요", viewModel.uiState.value.stepCard.heroDescription)
        }

    @Test
    fun `navigation enters realtime guidance from route line even when origin is detached`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)
            val spokenBriefings = mutableListOf<String>()
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.uiEvent.collect { event ->
                        if (event is NavigationUiEvent.SpeakBriefing) {
                            spokenBriefings += event.text
                        }
                    }
                }

            viewModel.bindNavigationRequest(
                testWalkNavigationRequest().copy(
                    origin =
                        RouteWaypoint(
                            name = "현재 위치",
                            coordinate = OFF_ROUTE_POINT,
                        ),
                ),
            )
            viewModel.enableReadyTts()
            locationManager.emitLocation(
                NEAR_ROUTE_BEFORE_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L),
            )
            locationManager.emitLocation(
                NEAR_ROUTE_BEFORE_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L),
            )
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.segmentSync.activeSegmentIndex)
            assertEquals("우회전", viewModel.uiState.value.stepCard.heroTitle)
            assertEquals("목적지까지 약 8분", viewModel.uiState.value.stepCard.heroDescription)
            assertTrue(spokenBriefings.isNotEmpty())
            collector.cancel()
        }

    @Test
    fun `navigation follow does not expose gps bearing after direction marker removal`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            locationManager.emitLocation(
                OFF_ROUTE_POINT.toLocationSnapshot(
                    recordedAtEpochMillis = 1_000L,
                    speedMetersPerSecond = 1.2f,
                    bearingDegrees = 92f,
                ),
            )
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.mapOverlay.headingDegrees)
            val overlayState = createNavigationViewportOverlayState(viewModel.uiState.value.mapOverlay)
            assertFalse(overlayState.fitToProjection)
            assertEquals(null, overlayState.fallbackCamera.bearingDegrees)
            assertEquals(
                MapCoordinate(latitude = OFF_ROUTE_POINT.latitude, longitude = OFF_ROUTE_POINT.longitude),
                overlayState.fallbackCamera.center,
            )
        }

    @Test
    fun `navigation follow ignores device heading after direction marker removal`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val headingManager = FakeCurrentHeadingManager()
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    headingManager = headingManager,
                )

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            locationManager.emitLocation(
                OFF_ROUTE_POINT.toLocationSnapshot(
                    recordedAtEpochMillis = 1_000L,
                    speedMetersPerSecond = 1.2f,
                    bearingDegrees = 92f,
                ),
            )
            advanceUntilIdle()

            headingManager.emitHeading(137.0)
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.mapOverlay.headingDegrees)
            val overlayState = createNavigationViewportOverlayState(viewModel.uiState.value.mapOverlay)
            assertEquals(null, overlayState.fallbackCamera.bearingDegrees)
        }

    @Test
    fun `navigation binding requests dense navigation location profile`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()

            assertEquals(LocationUpdateProfile.NAVIGATION, locationManager.lastStartedProfile)
        }

    @Test
    fun `navigation entered restarts dense location and heading updates`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val headingManager = FakeCurrentHeadingManager()
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    headingManager = headingManager,
                )

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            locationManager.stopLocationUpdates()
            headingManager.stopHeadingUpdates()
            viewModel.onAction(NavigationUiAction.NavigationEntered)
            advanceUntilIdle()

            assertTrue(locationManager.isUpdating)
            assertTrue(headingManager.isUpdating)
            assertEquals(LocationUpdateProfile.NAVIGATION, locationManager.lastStartedProfile)
        }

    @Test
    fun `navigation pose keeps current location without exposing device heading`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val headingManager = FakeCurrentHeadingManager()
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    headingManager = headingManager,
                )

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            headingManager.emitHeading(37.0)
            locationManager.emitLocation(
                OFF_ROUTE_POINT.toLocationSnapshot(
                    recordedAtEpochMillis = 1_000L,
                    speedMetersPerSecond = 0.1f,
                    bearingDegrees = 92f,
                ),
            )
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.mapOverlay.headingDegrees)
            assertEquals(
                OFF_ROUTE_POINT,
                viewModel.uiState.value.mapOverlay.currentLocation?.coordinate,
            )
        }

    @Test
    fun `manual camera keeps current location marker without device heading`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val headingManager = FakeCurrentHeadingManager()
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    headingManager = headingManager,
                )

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            locationManager.emitLocation(OFF_ROUTE_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            viewModel.onAction(NavigationUiAction.MapCameraMovedByUser)
            headingManager.emitHeading(124.0)
            advanceUntilIdle()

            assertEquals(NavigationTrackingMode.IDLE, viewModel.uiState.value.mapOverlay.trackingMode)
            assertEquals(null, viewModel.uiState.value.mapOverlay.headingDegrees)
        }

    @Test
    fun `navigation ignores low quality location updates`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            advanceUntilIdle()
            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = WALK_MID_POINT.latitude,
                    longitude = WALK_MID_POINT.longitude,
                    accuracyMeters = 80f,
                    recordedAtEpochMillis = 2_500L,
                ),
            )
            advanceUntilIdle()

            assertEquals(WALK_START_POINT, viewModel.uiState.value.mapOverlay.currentLocation?.coordinate)
        }

    @Test
    fun `navigation ignores impossible walking gps jumps`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            advanceUntilIdle()
            locationManager.emitLocation(OFF_ROUTE_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()

            assertEquals(WALK_START_POINT, viewModel.uiState.value.mapOverlay.currentLocation?.coordinate)
        }

    @Test
    fun `navigation smooths small display movements without changing raw progress eligibility`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            advanceUntilIdle()
            locationManager.emitLocation(
                WALK_SMALL_DISPLAY_SHIFT_POINT.toLocationSnapshot(
                    recordedAtEpochMillis = 2_500L,
                    speedMetersPerSecond = 1.0f,
                ),
            )
            advanceUntilIdle()

            val displayLocation = viewModel.uiState.value.mapOverlay.currentLocation?.coordinate
            assertTrue(displayLocation != null)
            assertTrue(displayLocation!!.longitude > WALK_START_POINT.longitude)
            assertTrue(displayLocation.longitude < WALK_SMALL_DISPLAY_SHIFT_POINT.longitude)
        }

    @Test
    fun `navigation falls back to route detail guide when current location leaves selected route`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = WALK_MID_POINT.latitude,
                    longitude = WALK_MID_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 1_000L,
                ),
            )
            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = WALK_MID_POINT.latitude,
                    longitude = WALK_MID_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 2_500L,
                ),
            )
            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = OFF_ROUTE_POINT.latitude,
                    longitude = OFF_ROUTE_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 4_000L,
                ),
            )
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.segmentSync.activeSegmentIndex)
            assertEquals(1, viewModel.uiState.value.segmentSync.focusedSegmentIndex)
            assertFalse(viewModel.uiState.value.segmentSync.isInspectingSegments)
            assertEquals("2 / 2", viewModel.uiState.value.progressLabel)
            assertEquals("우회전", viewModel.uiState.value.stepCard.heroTitle)
        }

    @Test
    fun `navigation returns to route start guide after leaving the route near origin`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(
                testWalkNavigationRequest().copy(
                    origin =
                        RouteWaypoint(
                            name = "현재 위치",
                            coordinate = OFF_ROUTE_POINT,
                        ),
                ),
            )
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()

            assertEquals(0, viewModel.uiState.value.segmentSync.activeSegmentIndex)

            locationManager.emitLocation(OFF_ROUTE_POINT.toLocationSnapshot(recordedAtEpochMillis = 4_000L))
            advanceUntilIdle()

            assertEquals(0, viewModel.uiState.value.segmentSync.activeSegmentIndex)
            assertEquals(0, viewModel.uiState.value.segmentSync.focusedSegmentIndex)
            assertEquals("220m 직진 이동", viewModel.uiState.value.stepCard.heroTitle)
        }

    @Test
    fun `navigation uses route projection for progress while raw location remains near the route`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()

            locationManager.emitLocation(NEAR_ROUTE_OFFSET_MID_POINT.toLocationSnapshot(recordedAtEpochMillis = 4_000L))
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.segmentSync.activeSegmentIndex)
            assertEquals(NEAR_ROUTE_OFFSET_MID_POINT, viewModel.uiState.value.mapOverlay.currentLocation?.coordinate)
        }

    @Test
    fun `navigation reroutes only after stable deviation candidate inside the outer corridor`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val routeRepository =
                FakeRouteRepository(
                    rerouteRoute = reroutedWalkRoute(),
                    endSessionId = "ended-session",
                )
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    routeRepository = routeRepository,
                )
            val spokenBriefings = mutableListOf<String>()
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.uiEvent.collect { event ->
                        if (event is NavigationUiEvent.SpeakBriefing) {
                            spokenBriefings += event.text
                        }
                    }
                }

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            viewModel.enableReadyTts()
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()

            locationManager.emitLocation(OFF_ROUTE_WITHIN_OLD_THRESHOLD_POINT.toLocationSnapshot(recordedAtEpochMillis = 4_000L))
            locationManager.emitLocation(OFF_ROUTE_WITHIN_OLD_THRESHOLD_POINT.toLocationSnapshot(recordedAtEpochMillis = 5_500L))
            advanceUntilIdle()

            assertTrue(routeRepository.rerouteCalls.isEmpty())

            locationManager.emitLocation(OFF_ROUTE_CANDIDATE_POINT.toLocationSnapshot(recordedAtEpochMillis = 10_000L))
            advanceUntilIdle()

            assertTrue(routeRepository.rerouteCalls.isEmpty())

            locationManager.emitLocation(OFF_ROUTE_CANDIDATE_POINT.toLocationSnapshot(recordedAtEpochMillis = 11_500L))
            advanceUntilIdle()

            assertEquals(listOf("walk-route-1" to OFF_ROUTE_CANDIDATE_POINT), routeRepository.rerouteCalls)
            assertTrue(spokenBriefings.contains("경로를 벗어났습니다. 경로를 다시 탐색합니다."))
            collector.cancel()
        }

    @Test
    fun `realtime guidance automatically speaks once when activated and ignores distance only updates`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)
            val spokenBriefings = mutableListOf<String>()
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.uiEvent.collect { event ->
                        if (event is NavigationUiEvent.SpeakBriefing) {
                            spokenBriefings += event.text
                        }
                    }
                }

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            viewModel.enableReadyTts()
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()

            assertEquals(1, spokenBriefings.size)

            locationManager.emitLocation(WALK_PRE_TURN_POINT.toLocationSnapshot(recordedAtEpochMillis = 4_000L))
            advanceUntilIdle()

            assertEquals(2, spokenBriefings.size)
            collector.cancel()
        }

    @Test
    fun `first route node arrival speaks guidance even when current origin is off network`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)
            val spokenBriefings = mutableListOf<String>()
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.uiEvent.collect { event ->
                        if (event is NavigationUiEvent.SpeakBriefing) {
                            spokenBriefings += event.text
                        }
                    }
                }

            viewModel.bindNavigationRequest(
                testWalkNavigationRequest().copy(
                    origin =
                        RouteWaypoint(
                            name = "현재 위치",
                            coordinate = OFF_ROUTE_POINT,
                        ),
                ),
            )
            viewModel.enableReadyTts()
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()

            assertEquals(0, viewModel.uiState.value.segmentSync.activeSegmentIndex)
            assertEquals(1, spokenBriefings.size)
            assertEquals(expectedSpeechText(viewModel.uiState.value.stepCard), spokenBriefings.single())
            collector.cancel()
        }

    @Test
    fun `realtime guidance speaks near threshold once and suppresses arrival duplicate`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)
            val spokenBriefings = mutableListOf<String>()
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.uiEvent.collect { event ->
                        if (event is NavigationUiEvent.SpeakBriefing) {
                            spokenBriefings += event.text
                        }
                    }
                }

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            viewModel.enableReadyTts()
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()

            locationManager.emitLocation(WALK_NEAR_TURN_POINT.toLocationSnapshot(recordedAtEpochMillis = 4_000L))
            advanceUntilIdle()

            assertEquals(2, spokenBriefings.size)
            assertEquals("곧 우회전입니다.", spokenBriefings.last())

            locationManager.emitLocation(WALK_VERY_NEAR_TURN_POINT.toLocationSnapshot(recordedAtEpochMillis = 5_500L))
            advanceUntilIdle()

            assertEquals(2, spokenBriefings.size)
            collector.cancel()
        }

    @Test
    fun `short realtime guidance within near threshold speaks only near copy`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)
            val spokenBriefings = mutableListOf<String>()
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.uiEvent.collect { event ->
                        if (event is NavigationUiEvent.SpeakBriefing) {
                            spokenBriefings += event.text
                        }
                    }
                }

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            viewModel.enableReadyTts()
            locationManager.emitLocation(WALK_NEAR_TURN_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            locationManager.emitLocation(WALK_NEAR_TURN_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()

            assertEquals(1, spokenBriefings.size)
            assertEquals("곧 우회전입니다.", spokenBriefings.single())
            collector.cancel()
        }

    @Test
    fun `route detail preview suppresses automatic speech while manual replay still speaks`() =
        runTest {
            val viewModel = createViewModel()
            val spokenBriefings = mutableListOf<String>()
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.uiEvent.collect { event ->
                        if (event is NavigationUiEvent.SpeakBriefing) {
                            spokenBriefings += event.text
                        }
                    }
                }

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            viewModel.enableReadyTts()
            viewModel.onAction(NavigationUiAction.NavigationEntered)
            advanceUntilIdle()

            assertEquals(1, spokenBriefings.size)

            viewModel.onAction(NavigationUiAction.BriefingReplayClicked)
            advanceUntilIdle()

            assertEquals(2, spokenBriefings.size)
            collector.cancel()
        }

    @Test
    fun `side rail inspection does not automatically speak selected preview`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)
            val spokenBriefings = mutableListOf<String>()
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.uiEvent.collect { event ->
                        if (event is NavigationUiEvent.SpeakBriefing) {
                            spokenBriefings += event.text
                        }
                    }
                }

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            viewModel.enableReadyTts()
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()

            assertEquals(1, spokenBriefings.size)

            viewModel.onAction(NavigationUiAction.SegmentTapped(index = 1))
            advanceUntilIdle()

            assertEquals(2, spokenBriefings.size)
            collector.cancel()
        }

    @Test
    fun `side rail replay speaks focused guidance card copy only`() =
        runTest {
            val viewModel = createViewModel()
            val events = mutableListOf<NavigationUiEvent>()
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.uiEvent.collect { event ->
                        events += event
                    }
                }

            viewModel.bindNavigationRequest(testTransitNavigationRequest())
            viewModel.enableReadyTts()
            viewModel.onAction(NavigationUiAction.SegmentTapped(index = 1))
            advanceUntilIdle()

            val speechText = events.filterIsInstance<NavigationUiEvent.SpeakBriefing>().last().text
            assertEquals(
                expectedSpeechText(checkNotNull(viewModel.uiState.value.focusedSegmentCard)),
                speechText,
            )
            collector.cancel()
        }

    @Test
    fun `navigation briefing text does not duplicate segment distance`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()

            assertEquals(expectedSpeechText(viewModel.uiState.value.stepCard), viewModel.uiState.value.tts.briefingText)
        }

    @Test
    fun `walk to transit leg triggers transit refresh near boarding stop`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val routeRepository = FakeRouteRepository()
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    routeRepository = routeRepository,
                )

            viewModel.bindNavigationRequest(testTransitNavigationRequest())
            advanceUntilIdle()

            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = TRANSIT_BOARDING_POINT.latitude,
                    longitude = TRANSIT_BOARDING_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 1_000L,
                ),
            )
            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = TRANSIT_BOARDING_POINT.latitude,
                    longitude = TRANSIT_BOARDING_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 2_500L,
                ),
            )
            advanceUntilIdle()

            assertEquals(listOf("transit-route-1" to 2), routeRepository.transitRefreshCalls)
            assertTrue(viewModel.uiState.value.stepCard.supportingText.contains("실시간 기준 100번 버스 6분 후 도착 예정"))
        }

    @Test
    fun `walk to transit leg refreshes within 300 meters with one minute cooldown`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val routeRepository = FakeRouteRepository()
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    routeRepository = routeRepository,
                )

            viewModel.bindNavigationRequest(testTransitNavigationRequest())
            advanceUntilIdle()

            locationManager.emitLocation(TRANSIT_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            locationManager.emitLocation(TRANSIT_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()

            assertEquals(listOf("transit-route-1" to 2), routeRepository.transitRefreshCalls)

            locationManager.emitLocation(TRANSIT_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 33_000L))
            advanceUntilIdle()

            assertEquals(
                "The same transit leg should not refresh again before one minute has elapsed.",
                listOf("transit-route-1" to 2),
                routeRepository.transitRefreshCalls,
            )

            locationManager.emitLocation(TRANSIT_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 64_000L))
            advanceUntilIdle()

            assertEquals(
                listOf("transit-route-1" to 2, "transit-route-1" to 2),
                routeRepository.transitRefreshCalls,
            )
        }

    @Test
    fun `walk to subway leg presents scheduled subway arrival near elevator`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val routeRepository =
                FakeRouteRepository(
                    transitRefreshData =
                        RouteTransitRefreshData(
                            type = "SUBWAY",
                            arrivalStatus = "SCHEDULE_BASED",
                            transits =
                                listOf(
                                    RouteTransitArrivalData(
                                        routeNo = "부산 1호선",
                                        remainingMinute = 4,
                                        isLowFloor = null,
                                    ),
                                ),
                        ),
                )
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    routeRepository = routeRepository,
                )

            viewModel.bindNavigationRequest(testTransitNavigationRequest(transitType = RouteLegType.SUBWAY))
            advanceUntilIdle()

            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = TRANSIT_BOARDING_POINT.latitude,
                    longitude = TRANSIT_BOARDING_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 1_000L,
                ),
            )
            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = TRANSIT_BOARDING_POINT.latitude,
                    longitude = TRANSIT_BOARDING_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 2_500L,
                ),
            )
            advanceUntilIdle()

            assertEquals(listOf("transit-route-1" to 2), routeRepository.transitRefreshCalls)
            assertTrue(viewModel.uiState.value.stepCard.supportingText.contains("시간표 기준 부산 1호선 4분 후 도착 예정"))
        }

    @Test
    fun `active transit boarding guidance exposes the same transit summary used by the rail card`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(testTransitNavigationRequest())
            advanceUntilIdle()
            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = TRANSIT_BOARDING_POINT.latitude,
                    longitude = TRANSIT_BOARDING_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 1_000L,
                ),
            )
            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = TRANSIT_BOARDING_POINT.latitude,
                    longitude = TRANSIT_BOARDING_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 2_500L,
                ),
            )
            advanceUntilIdle()

            val transitInfo =
                viewModel.uiState.value.stepCard.transitInfo
                    ?: error("Active transit boarding step should expose transit detail info.")
            assertEquals(NavigationGuidanceAction.BUS, transitInfo.guidanceAction)
            assertEquals("Bus Stop", transitInfo.startName)
            assertEquals("Destination Stop", transitInfo.endName)
            assertEquals("15", transitInfo.durationLabel?.filter(Char::isDigit))
        }

    @Test
    fun `tapping transit rail segment shows route detail transit info on the focused card`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.bindNavigationRequest(testTransitNavigationRequest())
            advanceUntilIdle()
            viewModel.onAction(NavigationUiAction.SegmentTapped(index = 1))
            advanceUntilIdle()

            val transitInfo =
                viewModel.uiState.value.focusedSegmentCard?.transitInfo
                    ?: error("Focused transit segment should expose transit detail info.")
            assertEquals(NavigationGuidanceAction.BUS, transitInfo.guidanceAction)
            assertEquals("Bus Stop", transitInfo.startName)
            assertEquals("Destination Stop", transitInfo.endName)
            assertEquals("15", transitInfo.durationLabel?.filter(Char::isDigit))
        }

    @Test
    fun `current route detail request exposes the active navigation request`() =
        runTest {
            val viewModel = createViewModel()
            val request = testWalkNavigationRequest()

            viewModel.bindNavigationRequest(request)
            advanceUntilIdle()

            val detailRequest = viewModel.currentRouteDetailRequest()

            assertEquals(request.selectedRoute.serverRouteId, detailRequest?.selectedRoute?.serverRouteId)
            assertEquals(request.destination.coordinate, detailRequest?.destination?.coordinate)
        }

    @Test
    fun `active navigation focus follows the latest gps point before segment fallbacks`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = WALK_PRE_TURN_POINT.latitude,
                    longitude = WALK_PRE_TURN_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 1_000L,
                ),
            )
            advanceUntilIdle()

            assertEquals(NavigationMapFocusMode.ACTIVE, viewModel.uiState.value.mapOverlay.mapFocusMode)
            assertEquals(WALK_PRE_TURN_POINT, viewModel.uiState.value.mapOverlay.currentLocation?.coordinate)
            assertEquals("현재 위치", viewModel.uiState.value.mapOverlay.currentLocation?.label)
            assertEquals(WALK_PRE_TURN_POINT, viewModel.uiState.value.mapOverlay.focusCoordinate)
        }

    @Test
    fun `navigation seeds current location marker from fresh cached gps before recenter click`() =
        runTest {
            val cachedLocation =
                WALK_PRE_TURN_POINT.toLocationSnapshot(
                    recordedAtEpochMillis = System.currentTimeMillis(),
                    speedMetersPerSecond = 1.2f,
                    bearingDegrees = 90f,
                )
            val locationManager = FakeCurrentLocationManager(initialSnapshot = cachedLocation)
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()

            assertEquals(WALK_PRE_TURN_POINT, viewModel.uiState.value.mapOverlay.currentLocation?.coordinate)
            assertEquals("현재 위치", viewModel.uiState.value.mapOverlay.currentLocation?.label)
            assertEquals(null, viewModel.uiState.value.mapOverlay.headingDegrees)
        }

    @Test
    fun `current location button recenters without forcing another gps refresh when location is ready`() =
        runTest {
            val cachedLocation =
                WALK_PRE_TURN_POINT.toLocationSnapshot(
                    recordedAtEpochMillis = System.currentTimeMillis(),
                    speedMetersPerSecond = 1.2f,
                    bearingDegrees = 90f,
                )
            val locationManager = FakeCurrentLocationManager(initialSnapshot = cachedLocation)
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()
            val refreshCountBeforeClick = locationManager.refreshLatestLocationCallCount

            viewModel.onAction(NavigationUiAction.CurrentLocationClicked)
            advanceUntilIdle()

            assertEquals(refreshCountBeforeClick, locationManager.refreshLatestLocationCallCount)
            assertEquals(1L, viewModel.uiState.value.locationRecenterRequestId)
            assertEquals(WALK_PRE_TURN_POINT, viewModel.uiState.value.mapOverlay.currentLocation?.coordinate)
        }

    @Test
    fun `manual map movement exits follow while keeping current location visible`() =
        runTest {
            val cachedLocation =
                WALK_PRE_TURN_POINT.toLocationSnapshot(
                    recordedAtEpochMillis = System.currentTimeMillis(),
                    speedMetersPerSecond = 1.2f,
                    bearingDegrees = 90f,
                )
            val locationManager = FakeCurrentLocationManager(initialSnapshot = cachedLocation)
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()

            viewModel.onAction(NavigationUiAction.MapCameraMovedByUser)
            advanceUntilIdle()

            assertEquals(WALK_PRE_TURN_POINT, viewModel.uiState.value.mapOverlay.currentLocation?.coordinate)
            assertEquals(NavigationTrackingMode.IDLE, viewModel.uiState.value.mapOverlay.trackingMode)
            assertEquals(null, viewModel.uiState.value.mapOverlay.headingDegrees)
        }

    @Test
    fun `current location button returns from manual camera to north up follow`() =
        runTest {
            val cachedLocation =
                WALK_PRE_TURN_POINT.toLocationSnapshot(
                    recordedAtEpochMillis = System.currentTimeMillis(),
                    speedMetersPerSecond = 1.2f,
                    bearingDegrees = 90f,
                )
            val locationManager = FakeCurrentLocationManager(initialSnapshot = cachedLocation)
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()

            viewModel.onAction(NavigationUiAction.MapCameraMovedByUser)
            advanceUntilIdle()
            viewModel.onAction(NavigationUiAction.CurrentLocationClicked)
            advanceUntilIdle()

            assertEquals(NavigationTrackingMode.FOLLOW, viewModel.uiState.value.mapOverlay.trackingMode)
            assertEquals(null, viewModel.uiState.value.mapOverlay.headingDegrees)
            assertEquals(1L, viewModel.uiState.value.locationRecenterRequestId)

            val overlayState = createNavigationViewportOverlayState(viewModel.uiState.value.mapOverlay)
            assertFalse(overlayState.fitToProjection)
            assertEquals(null, overlayState.fallbackCamera.bearingDegrees)
        }

    @Test
    fun `segment tap switches top card to selected step preview until returning to active guidance`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()

            viewModel.onAction(NavigationUiAction.SegmentTapped(index = 1))
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.segmentSync.isInspectingSegments)
            assertEquals("2 / 2", viewModel.uiState.value.focusedSegmentCard?.sequenceLabel)
            assertEquals("우회전", viewModel.uiState.value.focusedSegmentCard?.heroTitle)
            assertEquals("목적지까지 약 10분", viewModel.uiState.value.focusedSegmentCard?.heroDescription)
            assertEquals("목적지까지 약 10분", viewModel.uiState.value.focusedSegmentCard?.distanceLabel)

            viewModel.onAction(NavigationUiAction.ReturnToActiveSegmentClicked)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.segmentSync.isInspectingSegments)
            assertEquals(null, viewModel.uiState.value.focusedSegmentCard)
            assertEquals("1 / 2", viewModel.uiState.value.progressLabel)
            assertEquals(expectedSpeechText(viewModel.uiState.value.stepCard), viewModel.uiState.value.tts.briefingText)
        }

    @Test
    fun `returning from segment inspection restores north up follow camera`() =
        runTest {
            val cachedLocation =
                WALK_PRE_TURN_POINT.toLocationSnapshot(
                    recordedAtEpochMillis = System.currentTimeMillis(),
                    speedMetersPerSecond = 1.2f,
                    bearingDegrees = 90f,
                )
            val locationManager = FakeCurrentLocationManager(initialSnapshot = cachedLocation)
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()

            viewModel.onAction(NavigationUiAction.MapCameraMovedByUser)
            advanceUntilIdle()
            viewModel.onAction(NavigationUiAction.SegmentTapped(index = 1))
            advanceUntilIdle()
            viewModel.onAction(NavigationUiAction.ReturnToActiveSegmentClicked)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.segmentSync.isInspectingSegments)
            assertEquals(NavigationTrackingMode.FOLLOW, viewModel.uiState.value.mapOverlay.trackingMode)
            val overlayState = createNavigationViewportOverlayState(viewModel.uiState.value.mapOverlay)
            assertFalse(overlayState.fitToProjection)
            assertEquals(WALK_PRE_TURN_POINT.latitude, overlayState.fallbackCamera.center.latitude, 0.0)
            assertEquals(WALK_PRE_TURN_POINT.longitude, overlayState.fallbackCamera.center.longitude, 0.0)
            assertEquals(null, overlayState.fallbackCamera.bearingDegrees)
        }

    @Test
    fun `segment tap focuses map on the tapped segment representative coordinate`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()

            viewModel.onAction(NavigationUiAction.SegmentTapped(index = 1))
            advanceUntilIdle()

            assertEquals(NavigationMapFocusMode.FOCUSED, viewModel.uiState.value.mapOverlay.mapFocusMode)
            assertEquals(
                WALK_SECOND_SEGMENT_FOCUS_POINT,
                viewModel.uiState.value.mapOverlay.focusCoordinate,
            )
        }

    @Test
    fun `segment tap falls back to source leg start coordinate when the segment polyline is missing`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.bindNavigationRequest(testLegPolylineFallbackNavigationRequest())
            advanceUntilIdle()

            viewModel.onAction(NavigationUiAction.SegmentTapped(index = 0))
            advanceUntilIdle()

            assertEquals(NavigationMapFocusMode.FOCUSED, viewModel.uiState.value.mapOverlay.mapFocusMode)
            assertTrue(viewModel.uiState.value.mapOverlay.focusedSegmentPolyline.isEmpty())
            assertEquals(
                LEG_FALLBACK_START_POINT,
                viewModel.uiState.value.mapOverlay.focusCoordinate,
            )
        }

    @Test
    fun `segment tap prefers anchor coordinate over source leg start when walk segment polyline is missing`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.bindNavigationRequest(testPointAnchorNavigationRequest())
            advanceUntilIdle()

            viewModel.onAction(NavigationUiAction.SegmentTapped(index = 0))
            advanceUntilIdle()

            assertEquals(NavigationMapFocusMode.FOCUSED, viewModel.uiState.value.mapOverlay.mapFocusMode)
            assertEquals(
                POINT_ANCHOR_EVENT_POINT,
                viewModel.uiState.value.mapOverlay.routeSegments.first().segmentStartCoordinate,
            )
            assertEquals(
                POINT_ANCHOR_EVENT_POINT,
                viewModel.uiState.value.mapOverlay.focusCoordinate,
            )
        }

    @Test
    fun `segment tap resolves the missing segment focus to its route start coordinate`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.bindNavigationRequest(testSparseSegmentNavigationRequest())
            advanceUntilIdle()

            viewModel.onAction(NavigationUiAction.SegmentTapped(index = 0))
            advanceUntilIdle()

            assertEquals(NavigationMapFocusMode.FOCUSED, viewModel.uiState.value.mapOverlay.mapFocusMode)
            assertTrue(viewModel.uiState.value.mapOverlay.focusedSegmentPolyline.isEmpty())
            assertEquals(
                SPARSE_ROUTE_START_POINT,
                viewModel.uiState.value.mapOverlay.focusCoordinate,
            )
        }

    @Test
    fun `segment tap resolves later sparse segments to each branch start coordinate`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.bindNavigationRequest(testMultiSparseSegmentNavigationRequest())
            advanceUntilIdle()

            viewModel.onAction(NavigationUiAction.SegmentTapped(index = 1))
            advanceUntilIdle()

            assertEquals(NavigationMapFocusMode.FOCUSED, viewModel.uiState.value.mapOverlay.mapFocusMode)
            assertTrue(viewModel.uiState.value.mapOverlay.focusedSegmentPolyline.isEmpty())
            assertEquals(
                SPARSE_ROUTE_BRANCH_POINT_1,
                viewModel.uiState.value.mapOverlay.focusCoordinate,
            )

            viewModel.onAction(NavigationUiAction.SegmentTapped(index = 2))
            advanceUntilIdle()

            assertEquals(
                SPARSE_ROUTE_BRANCH_POINT_2,
                viewModel.uiState.value.mapOverlay.focusCoordinate,
            )
        }

    @Test
    fun `navigation segment marker debug summary reports segment sequence kind and first coordinate`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.bindNavigationRequest(testLegPolylineFallbackNavigationRequest())
            advanceUntilIdle()

            val summary = createNavigationSegmentMarkerDebugSummary(viewModel.uiState.value.mapOverlay)

            assertTrue(summary.contains("focusMode=ACTIVE"))
            assertTrue(summary.contains("count=3"))
            assertTrue(summary.contains("idx=0 seq=1 kind=TRANSIT_WALK polyline=0 first=null"))
            assertTrue(summary.contains("idx=1 seq=2 kind=TRANSIT polyline=2 first=35.180600,129.073500"))
            assertTrue(summary.contains("idx=2 seq=1 kind=TRANSIT_WALK polyline=2 first=35.180200,129.071800"))
        }

    @Test
    fun `navigation map adds walking leg polyline when transit segment data omits it`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.bindNavigationRequest(testLegPolylineFallbackNavigationRequest())
            advanceUntilIdle()

            val walkingPolyline =
                viewModel.uiState.value.mapOverlay.routeSegments.firstOrNull { segment ->
                    segment.travelKind == NavigationSegmentTravelKind.TRANSIT_WALK && segment.polyline.size >= 2
                }

            assertEquals(LEG_FALLBACK_START_POINT, walkingPolyline?.polyline?.firstOrNull())
        }

    @Test
    fun `navigation map restores each omitted transit walking leg even when another walking segment is renderable`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.bindNavigationRequest(testPartialTransitWalkPolylineNavigationRequest())
            advanceUntilIdle()

            val walkingPolylineStarts =
                viewModel.uiState.value.mapOverlay.routeSegments
                    .filter { segment ->
                        segment.travelKind == NavigationSegmentTravelKind.TRANSIT_WALK &&
                            segment.polyline.size >= 2
                    }
                    .mapNotNull { segment -> segment.polyline.firstOrNull() }

            assertTrue(PARTIAL_TRANSIT_WALK_START_POINT in walkingPolylineStarts)
            assertTrue(PARTIAL_TRANSIT_FINAL_WALK_START_POINT in walkingPolylineStarts)
        }

    @Test
    fun `navigation adds transit alighting guidance from the response stop coordinate`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.bindNavigationRequest(testPartialTransitWalkPolylineNavigationRequest())
            advanceUntilIdle()

            val alightingRailItem =
                viewModel.uiState.value.segmentSync.railItems.firstOrNull { item ->
                    item.guidanceAction == NavigationGuidanceAction.ALIGHT
                }
            assertEquals("Central Stop \uD558\uCC28\uC9C0\uC810\uC785\uB2C8\uB2E4.", alightingRailItem?.instruction)
            assertEquals("\uD558\uCC28", alightingRailItem?.sidePanelTitle)
            assertEquals("Central Stop \uD558\uCC28\uC9C0\uC810\uC785\uB2C8\uB2E4.", alightingRailItem?.sidePanelDescription)
            assertEquals(null, alightingRailItem?.transitInfo)

            viewModel.onAction(NavigationUiAction.SegmentTapped(index = alightingRailItem?.index ?: -999))
            advanceUntilIdle()

            assertEquals(NavigationGuidanceAction.ALIGHT, viewModel.uiState.value.focusedSegmentCard?.guidanceAction)
            assertEquals(PARTIAL_TRANSIT_ALIGHTING_POINT, viewModel.uiState.value.mapOverlay.focusCoordinate)
            assertTrue(
                viewModel.uiState.value.mapOverlay.routeSegments.any { segment ->
                    segment.guidanceMessage == "Central Stop \uD558\uCC28\uC9C0\uC810\uC785\uB2C8\uB2E4." &&
                        segment.segmentStartCoordinate == PARTIAL_TRANSIT_ALIGHTING_POINT
                },
            )
        }

    @Test
    fun `navigation side panel uses route detail korean copy instead of raw backend english`() =
        runTest {
            val viewModel = createViewModel()
            val request =
                testPartialTransitWalkPolylineNavigationRequest().let { baseRequest ->
                    val route = baseRequest.selectedRoute
                    baseRequest.copy(
                        selectedRoute =
                            route.copy(
                                segments =
                                    route.segments.map { segment ->
                                        if (segment.sequence == 1) {
                                            segment.copy(
                                                distanceMeters = 105,
                                                guidanceMessage = "Turn left.",
                                                guidanceDirection = RouteGuidanceDirection.TURN_LEFT,
                                                durationFromRouteStartSeconds = 60,
                                            )
                                        } else {
                                            segment
                                        }
                                    },
                            ),
                    )
                }

            viewModel.bindNavigationRequest(request)
            advanceUntilIdle()

            val turnItem = viewModel.uiState.value.segmentSync.railItems.first { item -> item.sequence == 1 }

            assertEquals("105m \uD6C4 \uC88C\uD68C\uC804", turnItem.sidePanelTitle)
            assertEquals("\uBAA9\uC801\uC9C0\uAE4C\uC9C0 \uC57D 17\uBD84", turnItem.sidePanelDescription)
        }

    @Test
    fun `navigation map uses transit leg line ending at alighting stop instead of stale segment line`() =
        runTest {
            val viewModel = createViewModel()
            val staleTransitEnd = GeoCoordinate(latitude = 35.1840, longitude = 129.0790)
            val request =
                testPartialTransitWalkPolylineNavigationRequest().let { baseRequest ->
                    val route = baseRequest.selectedRoute
                    baseRequest.copy(
                        selectedRoute =
                            route.copy(
                                segments =
                                    route.segments.map { segment ->
                                        if (segment.sourceLegSequence == 2) {
                                            segment.copy(
                                                polyline =
                                                    RoutePolyline(
                                                        points =
                                                            listOf(
                                                                PARTIAL_TRANSIT_BOARDING_POINT,
                                                                staleTransitEnd,
                                                            ),
                                                    ),
                                            )
                                        } else {
                                            segment
                                        }
                                    },
                            ),
                    )
                }

            viewModel.bindNavigationRequest(request)
            advanceUntilIdle()

            val transitSegment =
                viewModel.uiState.value.mapOverlay.routeSegments.first { segment ->
                    segment.travelKind == NavigationSegmentTravelKind.TRANSIT &&
                        segment.guidanceMessage == "Ride bus"
                }

            assertEquals(
                listOf(PARTIAL_TRANSIT_BOARDING_POINT, PARTIAL_TRANSIT_ALIGHTING_POINT),
                transitSegment.polyline,
            )
            assertEquals(PARTIAL_TRANSIT_ALIGHTING_POINT, transitSegment.segmentEndCoordinate)
        }

    @Test
    fun `navigation map restores omitted transit walking leg when backend segments are not leg scoped`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.bindNavigationRequest(testUnscopedTransitWalkPolylineNavigationRequest())
            advanceUntilIdle()

            val walkingPolylineStarts =
                viewModel.uiState.value.mapOverlay.routeSegments
                    .filter { segment ->
                        segment.travelKind == NavigationSegmentTravelKind.TRANSIT_WALK &&
                            segment.polyline.size >= 2
                    }
                    .mapNotNull { segment -> segment.polyline.firstOrNull() }

            assertTrue(PARTIAL_TRANSIT_WALK_START_POINT in walkingPolylineStarts)
            assertTrue(PARTIAL_TRANSIT_FINAL_WALK_START_POINT in walkingPolylineStarts)
        }

    @Test
    fun `focused guidance over five hundred meters still animates camera transition`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.bindNavigationRequest(testFarOffRouteNavigationRequest())
            advanceUntilIdle()
            viewModel.onAction(NavigationUiAction.SegmentTapped(index = 1))
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.mapOverlay.shouldAnimateCameraTransition)
        }

    @Test
    fun `navigation overlay creates fallback junction markers for sparse later segments`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.bindNavigationRequest(testMultiSparseSegmentNavigationRequest())
            advanceUntilIdle()

            val overlayState = createNavigationViewportOverlayState(viewModel.uiState.value.mapOverlay)
            val junctionCoordinates =
                overlayState.points
                    .filter { point -> point.kind == MapViewportPointKind.SEGMENT_JUNCTION }
                    .map { it.coordinate }

            assertEquals(
                listOf(
                    MapCoordinate(
                        latitude = SPARSE_ROUTE_BRANCH_POINT_1.latitude,
                        longitude = SPARSE_ROUTE_BRANCH_POINT_1.longitude,
                    ),
                    MapCoordinate(
                        latitude = SPARSE_ROUTE_BRANCH_POINT_2.latitude,
                        longitude = SPARSE_ROUTE_BRANCH_POINT_2.longitude,
                    ),
                ),
                junctionCoordinates,
            )
        }

    @Test
    fun `confirming exit navigation routes to arrival flow`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val routeRepository = FakeRouteRepository(endSessionId = "ended-session")
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    routeRepository = routeRepository,
                )
            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()
            val eventsDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.take(2).toList() }

            viewModel.onAction(NavigationUiAction.ExitNavigationClicked)
            viewModel.onAction(NavigationUiAction.ConfirmExitNavigationClicked)
            advanceUntilIdle()

            assertEquals(listOf("walk-route-1"), routeRepository.endRouteCalls)
            assertEquals("ended-session", viewModel.currentRatingSessionId())
            assertEquals(listOf(NavigationUiEvent.StopBriefing, NavigationUiEvent.NavigateToArrival), eventsDeferred.await())
        }

    @Test
    fun `back click opens exit confirmation before completing navigation`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val routeRepository = FakeRouteRepository(endSessionId = "ended-session")
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    routeRepository = routeRepository,
                )
            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()
            val eventsDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.take(2).toList() }

            viewModel.onAction(NavigationUiAction.BackClicked)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isExitConfirmDialogVisible)
            assertTrue(routeRepository.endRouteCalls.isEmpty())

            viewModel.onAction(NavigationUiAction.ConfirmExitNavigationClicked)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isExitConfirmDialogVisible)
            assertEquals(listOf("walk-route-1"), routeRepository.endRouteCalls)
            assertEquals(
                listOf(NavigationUiEvent.StopBriefing, NavigationUiEvent.NavigateToArrival),
                eventsDeferred.await(),
            )
        }

    @Test
    fun `saving destination bookmark completes navigation into saved route when repository save succeeds`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val bookmarkRepository = FakeBookmarkRepository()
            val routeRepository = FakeRouteRepository(endSessionId = "ended-session")
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    bookmarkRepository = bookmarkRepository,
                    routeRepository = routeRepository,
                )
            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()
            val eventsDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.take(2).toList() }

            viewModel.onAction(NavigationUiAction.SaveBookmarkClicked)
            advanceUntilIdle()

            assertEquals(1, bookmarkRepository.savedBookmarks.size)
            assertEquals(42L, bookmarkRepository.savedBookmarks.single().serverPlaceId)
            assertEquals("KAKAO", bookmarkRepository.savedBookmarks.single().provider)
            assertEquals("kakao-destination-42", bookmarkRepository.savedBookmarks.single().providerPlaceId)
            assertEquals(listOf("walk-route-1"), routeRepository.endRouteCalls)
            assertEquals(
                listOf(NavigationUiEvent.StopBriefing, NavigationUiEvent.NavigateToSavedRoute),
                eventsDeferred.await(),
            )
        }

    @Test
    fun `saving destination bookmark failure keeps navigation active and shows toast`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val bookmarkRepository = FakeBookmarkRepository(failSave = true)
            val routeRepository = FakeRouteRepository(endSessionId = "ended-session")
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    bookmarkRepository = bookmarkRepository,
                    routeRepository = routeRepository,
                )
            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()
            val eventsDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.take(1).toList() }

            viewModel.onAction(NavigationUiAction.SaveBookmarkClicked)
            advanceUntilIdle()

            assertTrue(locationManager.isUpdating)
            assertTrue(bookmarkRepository.savedBookmarks.isEmpty())
            assertTrue(routeRepository.endRouteCalls.isEmpty())
            assertEquals(
                listOf(NavigationUiEvent.ShowToast("북마크를 저장하지 못했습니다. 다시 시도해 주세요.")),
                eventsDeferred.await(),
            )
        }

    @Test
    fun `saving destination bookmark without server metadata shows unavailable toast and keeps navigation active`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val bookmarkRepository = FakeBookmarkRepository()
            val routeRepository = FakeRouteRepository(endSessionId = "ended-session")
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    bookmarkRepository = bookmarkRepository,
                    routeRepository = routeRepository,
                )
            viewModel.bindNavigationRequest(
                testWalkNavigationRequest().copy(
                    destination =
                        RouteWaypoint(
                            name = "목적지",
                            coordinate = WALK_END_POINT,
                        ),
                ),
            )
            advanceUntilIdle()
            val eventsDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.take(1).toList() }

            viewModel.onAction(NavigationUiAction.SaveBookmarkClicked)
            advanceUntilIdle()

            assertTrue(locationManager.isUpdating)
            assertTrue(bookmarkRepository.savedBookmarks.isEmpty())
            assertTrue(routeRepository.endRouteCalls.isEmpty())
            assertEquals(
                listOf(NavigationUiEvent.ShowToast("서버에 저장할 수 있는 목적지에서만 북마크를 저장할 수 있습니다.")),
                eventsDeferred.await(),
            )
        }

    @Test
    fun `accurate repeated off route updates stay on selected route detail guide`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val routeRepository =
                FakeRouteRepository(
                    rerouteRoute = reroutedWalkRoute(),
                    endSessionId = "ended-session",
                )
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    routeRepository = routeRepository,
                )
            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()
            val eventsDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.take(2).toList() }

            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = OFF_ROUTE_POINT.latitude,
                    longitude = OFF_ROUTE_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 1_000L,
                ),
            )
            advanceUntilIdle()
            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = OFF_ROUTE_POINT.latitude,
                    longitude = OFF_ROUTE_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 2_000L,
                ),
            )
            advanceUntilIdle()

            viewModel.onAction(NavigationUiAction.NavigationCompleteClicked)
            advanceUntilIdle()

            assertTrue(routeRepository.rerouteCalls.isEmpty())
            assertEquals(listOf("walk-route-1"), routeRepository.endRouteCalls)
            assertEquals("ended-session", viewModel.currentRatingSessionId())
            assertEquals(
                listOf(NavigationUiEvent.StopBriefing, NavigationUiEvent.NavigateToArrival),
                eventsDeferred.await(),
            )
        }

    @Test
    fun `destination arrival auto completes after stable final approach`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val routeRepository = FakeRouteRepository(endSessionId = "ended-session")
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    routeRepository = routeRepository,
                )
            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()
            val eventsDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.take(3).toList() }

            locationManager.emitLocation(WALK_DESTINATION_NEAR_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            advanceUntilIdle()

            assertTrue(routeRepository.endRouteCalls.isEmpty())
            assertFalse(eventsDeferred.isCompleted)

            locationManager.emitLocation(WALK_DESTINATION_NEAR_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()

            assertEquals(listOf("walk-route-1"), routeRepository.endRouteCalls)
            assertEquals("ended-session", viewModel.currentRatingSessionId())
            assertEquals(
                listOf(
                    NavigationUiEvent.StopBriefing,
                    NavigationUiEvent.SpeakBriefing("목적지에 도착했습니다. 안내를 종료합니다."),
                    NavigationUiEvent.NavigateToArrival,
                ),
                eventsDeferred.await(),
            )
        }

    @Test
    fun `destination arrival uses final route endpoint instead of destination poi coordinate`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val routeRepository = FakeRouteRepository(endSessionId = "ended-session")
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    routeRepository = routeRepository,
                )
            viewModel.bindNavigationRequest(
                testWalkNavigationRequest().copy(
                    destination =
                        RouteWaypoint(
                            name = "목적지",
                            coordinate = WALK_DESTINATION_OUTSIDE_AUTO_ARRIVAL_POINT,
                        ),
                ),
            )
            advanceUntilIdle()
            val eventsDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.take(3).toList() }

            locationManager.emitLocation(WALK_DESTINATION_NEAR_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            advanceUntilIdle()
            locationManager.emitLocation(WALK_DESTINATION_NEAR_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()

            assertEquals(listOf("walk-route-1"), routeRepository.endRouteCalls)
            assertEquals(
                listOf(
                    NavigationUiEvent.StopBriefing,
                    NavigationUiEvent.SpeakBriefing("목적지에 도착했습니다. 안내를 종료합니다."),
                    NavigationUiEvent.NavigateToArrival,
                ),
                eventsDeferred.await(),
            )
        }

    @Test
    fun `destination soon guidance speaks once on final segment near route endpoint`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)
            val spokenBriefings = mutableListOf<String>()
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.uiEvent.collect { event ->
                        if (event is NavigationUiEvent.SpeakBriefing) {
                            spokenBriefings += event.text
                        }
                    }
                }

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            viewModel.enableReadyTts()
            locationManager.emitLocation(
                WALK_DESTINATION_OUTSIDE_AUTO_ARRIVAL_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L),
            )
            advanceUntilIdle()
            locationManager.emitLocation(
                WALK_DESTINATION_OUTSIDE_AUTO_ARRIVAL_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L),
            )
            advanceUntilIdle()
            locationManager.emitLocation(
                WALK_DESTINATION_OUTSIDE_AUTO_ARRIVAL_POINT.toLocationSnapshot(recordedAtEpochMillis = 4_000L),
            )
            advanceUntilIdle()

            assertEquals(listOf("목적지에 곧 도착합니다."), spokenBriefings)
            assertEquals("목적지까지 20m", viewModel.uiState.value.stepCard.heroTitle)
            collector.cancel()
        }

    @Test
    fun `destination arrival candidate resets when gps leaves the arrival radius`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val routeRepository = FakeRouteRepository(endSessionId = "ended-session")
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    routeRepository = routeRepository,
                )
            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()
            val eventsDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.take(2).toList() }

            locationManager.emitLocation(WALK_DESTINATION_NEAR_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            advanceUntilIdle()
            locationManager.emitLocation(WALK_DESTINATION_OUTSIDE_AUTO_ARRIVAL_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_000L))
            advanceUntilIdle()

            assertTrue(routeRepository.endRouteCalls.isEmpty())
            assertFalse(eventsDeferred.isCompleted)

            eventsDeferred.cancel()
        }

    @Test
    fun `guidance advances when user turns through node corridor without touching the exact node`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(turnOffsetNavigationRequest())
            advanceUntilIdle()
            locationManager.emitLocation(TURN_ROUTE_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            advanceUntilIdle()
            locationManager.emitLocation(TURN_ROUTE_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()
            locationManager.emitLocation(TURN_ROUTE_BEFORE_NODE_POINT.toLocationSnapshot(recordedAtEpochMillis = 4_000L))
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.segmentSync.activeSegmentIndex)

            locationManager.emitLocation(TURN_ROUTE_OFFSET_AFTER_NODE_POINT_1.toLocationSnapshot(recordedAtEpochMillis = 5_500L))
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.segmentSync.activeSegmentIndex)

            locationManager.emitLocation(TURN_ROUTE_OFFSET_AFTER_NODE_POINT_2.toLocationSnapshot(recordedAtEpochMillis = 7_000L))
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.segmentSync.activeSegmentIndex)
        }

    @Test
    fun `guidance advances after parallel offset turn inside reroute corridor`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(turnOffsetNavigationRequest())
            advanceUntilIdle()
            locationManager.emitLocation(TURN_ROUTE_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            advanceUntilIdle()
            locationManager.emitLocation(TURN_ROUTE_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()
            locationManager.emitLocation(TURN_ROUTE_BEFORE_NODE_POINT.toLocationSnapshot(recordedAtEpochMillis = 4_000L))
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.segmentSync.activeSegmentIndex)

            locationManager.emitLocation(TURN_ROUTE_PARALLEL_OFFSET_AFTER_NODE_POINT_1.toLocationSnapshot(recordedAtEpochMillis = 5_500L))
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.segmentSync.activeSegmentIndex)

            locationManager.emitLocation(TURN_ROUTE_PARALLEL_OFFSET_AFTER_NODE_POINT_2.toLocationSnapshot(recordedAtEpochMillis = 7_000L))
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.segmentSync.activeSegmentIndex)
        }

    @Test
    fun `crosswalk guidance stays active until user reaches the end of crosswalk segment`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(crosswalkNavigationRequest())
            advanceUntilIdle()
            locationManager.emitLocation(CROSSWALK_ROUTE_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            advanceUntilIdle()
            locationManager.emitLocation(CROSSWALK_ROUTE_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()

            locationManager.emitLocation(CROSSWALK_ROUTE_CROSSWALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 4_000L))
            advanceUntilIdle()
            locationManager.emitLocation(CROSSWALK_ROUTE_CROSSWALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 5_500L))
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.segmentSync.activeSegmentIndex)
            assertEquals(NavigationGuidanceAction.CROSSWALK, viewModel.uiState.value.stepCard.guidanceAction)

            locationManager.emitLocation(CROSSWALK_ROUTE_INSIDE_POINT.toLocationSnapshot(recordedAtEpochMillis = 7_000L))
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.segmentSync.activeSegmentIndex)
            assertEquals(NavigationGuidanceAction.CROSSWALK, viewModel.uiState.value.stepCard.guidanceAction)

            locationManager.emitLocation(CROSSWALK_ROUTE_NEAR_END_POINT.toLocationSnapshot(recordedAtEpochMillis = 8_500L))
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.segmentSync.activeSegmentIndex)

            locationManager.emitLocation(CROSSWALK_ROUTE_AFTER_END_POINT.toLocationSnapshot(recordedAtEpochMillis = 10_000L))
            advanceUntilIdle()
            locationManager.emitLocation(CROSSWALK_ROUTE_AFTER_END_POINT.toLocationSnapshot(recordedAtEpochMillis = 11_500L))
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.segmentSync.activeSegmentIndex)
        }

    @Test
    fun `navigation entry keeps initial briefing pending until tts becomes ready`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()
            val briefingText = expectedSpeechText(viewModel.uiState.value.stepCard)
            val eventDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.take(1).toList() }
            advanceUntilIdle()

            viewModel.onAction(NavigationUiAction.NavigationEntered)
            advanceUntilIdle()

            assertFalse(eventDeferred.isCompleted)

            viewModel.updateTextToSpeechState(
                isEnabled = true,
                canSpeak = true,
                status = NavigationTtsStatus.Ready,
            )
            advanceUntilIdle()

            assertEquals(listOf(NavigationUiEvent.SpeakBriefing(briefingText)), eventDeferred.await())
        }

    @Test
    fun `initial briefing auto play does not repeat for duplicate ready updates`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel = createViewModel(locationManager = locationManager)

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 1_000L))
            locationManager.emitLocation(WALK_START_POINT.toLocationSnapshot(recordedAtEpochMillis = 2_500L))
            advanceUntilIdle()
            val eventDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.take(1).toList() }
            advanceUntilIdle()

            viewModel.onAction(NavigationUiAction.NavigationEntered)
            viewModel.updateTextToSpeechState(
                isEnabled = true,
                canSpeak = true,
                status = NavigationTtsStatus.Ready,
            )
            advanceUntilIdle()
            assertEquals(1, eventDeferred.await().size)

            val duplicateDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.take(1).toList() }
            advanceUntilIdle()

            viewModel.updateTextToSpeechState(
                isEnabled = true,
                canSpeak = true,
                status = NavigationTtsStatus.Ready,
            )
            advanceUntilIdle()

            assertFalse(duplicateDeferred.isCompleted)
            duplicateDeferred.cancel()
        }

    @Test
    fun `voice guidance toggle on preserves pending briefing until tts becomes ready`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            advanceUntilIdle()
            val briefingText = viewModel.uiState.value.tts.briefingText
            val eventDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.take(4).toList() }
            advanceUntilIdle()

            viewModel.onAction(NavigationUiAction.NavigationEntered)
            viewModel.onAction(NavigationUiAction.VoiceGuidanceToggled(enabled = false))
            viewModel.onAction(NavigationUiAction.VoiceGuidanceToggled(enabled = true))
            advanceUntilIdle()

            assertFalse(eventDeferred.isCompleted)

            viewModel.updateTextToSpeechState(
                isEnabled = true,
                canSpeak = true,
                status = NavigationTtsStatus.Ready,
            )
            advanceUntilIdle()

            val events = eventDeferred.await()
            assertEquals(4, events.size)
            assertTrue(events.contains(NavigationUiEvent.SetVoiceGuidanceEnabled(enabled = false)))
            assertTrue(events.contains(NavigationUiEvent.StopBriefing))
            assertTrue(events.contains(NavigationUiEvent.SetVoiceGuidanceEnabled(enabled = true)))
            assertEquals(NavigationUiEvent.SpeakBriefing(briefingText), events.last())
        }

    @Test
    fun `low vision mode plays route change alert before the next segment boundary`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    initialLowVisionMode = true,
                )
            viewModel.bindNavigationRequest(testWalkNavigationRequest())
            viewModel.updateTextToSpeechState(
                isEnabled = true,
                canSpeak = true,
                status = NavigationTtsStatus.Ready,
            )
            advanceUntilIdle()
            val eventDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.first() }

            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = WALK_PRE_TURN_POINT.latitude,
                    longitude = WALK_PRE_TURN_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 1_000L,
                ),
            )
            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = WALK_PRE_TURN_POINT.latitude,
                    longitude = WALK_PRE_TURN_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 2_500L,
                ),
            )
            advanceUntilIdle()

            assertEquals(NavigationUiEvent.PlayRouteChangeAlert, eventDeferred.await())
        }

    @Test
    fun `low vision far off route keeps selected route detail metrics when destination is walkable`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val routeRepository =
                FakeRouteRepository(
                    freshWalkSearchData =
                        lowVisionRemainingSearchData(
                            routeId = "actual-walk-route",
                            routeOption = RouteOption.SAFE,
                            distanceMeters = 710,
                            estimatedTimeMinutes = 13,
                            durationSeconds = 780,
                        ),
                )
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    routeRepository = routeRepository,
                    initialLowVisionMode = true,
                )

            viewModel.bindNavigationRequest(testFarOffRouteNavigationRequest())
            advanceUntilIdle()

            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = NEAR_OFF_ROUTE_POINT.latitude,
                    longitude = NEAR_OFF_ROUTE_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 1_000L,
                ),
            )
            advanceUntilIdle()

            assertEquals("2.2km", viewModel.uiState.value.remainingDistanceLabel)
            assertEquals("22\uBD84", viewModel.uiState.value.remainingEtaLabel)
            assertTrue(routeRepository.freshWalkQueries.isEmpty())
            assertTrue(routeRepository.freshTransitQueries.isEmpty())
        }

    @Test
    fun `low vision far off route keeps selected route detail metrics when destination is beyond walking range`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val routeRepository =
                FakeRouteRepository(
                    freshWalkSearchData =
                        lowVisionRemainingSearchData(
                            routeId = "actual-walk-route",
                            routeOption = RouteOption.SAFE,
                            distanceMeters = 1_200,
                            estimatedTimeMinutes = 21,
                            durationSeconds = 1_260,
                        ),
                    freshTransitSearchData =
                        lowVisionRemainingSearchData(
                            routeId = "actual-transit-route",
                            routeOption = RouteOption.RECOMMENDED,
                            distanceMeters = 3_600,
                            estimatedTimeMinutes = 34,
                            durationSeconds = 2_040,
                        ),
                )
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    routeRepository = routeRepository,
                    initialLowVisionMode = true,
                )

            viewModel.bindNavigationRequest(testFarOffRouteNavigationRequest())
            advanceUntilIdle()

            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = FAR_OFF_ROUTE_POINT.latitude,
                    longitude = FAR_OFF_ROUTE_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 1_000L,
                ),
            )
            advanceUntilIdle()

            assertEquals("2.2km", viewModel.uiState.value.remainingDistanceLabel)
            assertEquals("22\uBD84", viewModel.uiState.value.remainingEtaLabel)
            assertTrue(routeRepository.freshWalkQueries.isEmpty())
            assertTrue(routeRepository.freshTransitQueries.isEmpty())
        }

    @Test
    fun `low vision far off route keeps selected route detail metrics when fresh remaining route search is unavailable`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val routeRepository =
                FakeRouteRepository(
                    failFreshWalkSearch = true,
                    failFreshTransitSearch = true,
                )
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    routeRepository = routeRepository,
                    initialLowVisionMode = true,
                )

            viewModel.bindNavigationRequest(testFarOffRouteNavigationRequest())
            advanceUntilIdle()

            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = FAR_OFF_ROUTE_POINT.latitude,
                    longitude = FAR_OFF_ROUTE_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 1_000L,
                ),
            )
            advanceUntilIdle()

            assertEquals("2.2km", viewModel.uiState.value.remainingDistanceLabel)
            assertEquals("22\uBD84", viewModel.uiState.value.remainingEtaLabel)
        }

    @Test
    fun `low vision far off route suppresses fresh remaining route search for small movement`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val routeRepository =
                FakeRouteRepository(
                    freshWalkSearchData =
                        lowVisionRemainingSearchData(
                            routeId = "actual-walk-route",
                            routeOption = RouteOption.SAFE,
                            distanceMeters = 710,
                            estimatedTimeMinutes = 13,
                            durationSeconds = 780,
                        ),
                )
            val viewModel =
                createViewModel(
                    locationManager = locationManager,
                    routeRepository = routeRepository,
                    initialLowVisionMode = true,
                )

            viewModel.bindNavigationRequest(testFarOffRouteNavigationRequest())
            advanceUntilIdle()

            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = NEAR_OFF_ROUTE_POINT.latitude,
                    longitude = NEAR_OFF_ROUTE_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 1_000L,
                ),
            )
            advanceUntilIdle()

            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = SLIGHTLY_SHIFTED_OFF_ROUTE_POINT.latitude,
                    longitude = SLIGHTLY_SHIFTED_OFF_ROUTE_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 3_000L,
                ),
            )
            advanceUntilIdle()

            assertTrue(routeRepository.freshWalkQueries.isEmpty())
            assertEquals("2.2km", viewModel.uiState.value.remainingDistanceLabel)
            assertEquals("22\uBD84", viewModel.uiState.value.remainingEtaLabel)

            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = FARTHER_SHIFTED_OFF_ROUTE_POINT.latitude,
                    longitude = FARTHER_SHIFTED_OFF_ROUTE_POINT.longitude,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 8_000L,
                ),
            )
            advanceUntilIdle()

            assertTrue(routeRepository.freshWalkQueries.isEmpty())
        }
}

private fun createViewModel(
    locationManager: FakeCurrentLocationManager = FakeCurrentLocationManager(),
    headingManager: CurrentHeadingManager = FakeCurrentHeadingManager(),
    bookmarkRepository: BookmarkRepository = FakeBookmarkRepository(),
    routeRepository: RouteRepository = FakeRouteRepository(),
    reportRepository: ReportRepository = FakeNavigationReportRepository(),
    initialLowVisionMode: Boolean = false,
): NavigationViewModel =
    NavigationViewModel(
        currentLocationManager = locationManager,
        currentHeadingManager = headingManager,
        bookmarkRepository = bookmarkRepository,
        routeRepository = routeRepository,
        reportRepository = reportRepository,
        initialLowVisionMode = initialLowVisionMode,
    )

private fun NavigationViewModel.enableReadyTts() {
    updateTextToSpeechState(
        isEnabled = true,
        canSpeak = true,
        status = NavigationTtsStatus.Ready,
    )
}

private fun expectedSpeechText(stepCard: NavigationStepCardUiState): String =
    stepCard.speechText
        .trim()
        .takeIf(String::isNotEmpty)
        ?: listOf(
            stepCard.heroTitle.trim(),
            stepCard.heroDescription.trim(),
        ).filter(String::isNotEmpty)
            .distinct()
            .joinToString(separator = " ")

private fun expectedSpeechText(focusedCard: NavigationFocusedSegmentCardUiState): String =
    listOf(
        focusedCard.heroTitle.trim(),
        focusedCard.heroDescription.trim(),
    ).filter(String::isNotEmpty)
        .distinct()
        .joinToString(separator = " ")

private fun GeoCoordinate.toLocationSnapshot(
    recordedAtEpochMillis: Long,
    speedMetersPerSecond: Float? = null,
    bearingDegrees: Float? = null,
): LocationSnapshot =
    LocationSnapshot(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = 5f,
        recordedAtEpochMillis = recordedAtEpochMillis,
        speedMetersPerSecond = speedMetersPerSecond,
        bearingDegrees = bearingDegrees,
    )

private class FakeCurrentLocationManager(
    initialSnapshot: LocationSnapshot? = null,
    private val refreshSnapshot: LocationSnapshot? = null,
) : CurrentLocationManager {
    private val mutableLatestLocation = MutableStateFlow<LocationSnapshot?>(initialSnapshot)

    override val latestLocation: StateFlow<LocationSnapshot?> = mutableLatestLocation

    var isUpdating: Boolean = false
        private set

    var refreshLatestLocationCallCount: Int = 0
        private set

    var lastStartedProfile: LocationUpdateProfile? = null
        private set

    override fun refreshLatestLocation() {
        refreshLatestLocationCallCount += 1
        refreshSnapshot?.let { snapshot ->
            mutableLatestLocation.value = snapshot
        }
    }

    override fun startLocationUpdates() {
        isUpdating = true
        lastStartedProfile = LocationUpdateProfile.DEFAULT
    }

    override fun startLocationUpdates(profile: LocationUpdateProfile) {
        isUpdating = true
        lastStartedProfile = profile
    }

    override fun stopLocationUpdates() {
        isUpdating = false
    }

    fun emitLocation(snapshot: LocationSnapshot) {
        mutableLatestLocation.value = snapshot
    }
}

private class FakeCurrentHeadingManager : CurrentHeadingManager {
    private val mutableLatestHeading = MutableStateFlow<HeadingSnapshot?>(null)

    override val latestHeading: StateFlow<HeadingSnapshot?> = mutableLatestHeading

    var isUpdating: Boolean = false
        private set

    override fun startHeadingUpdates() {
        isUpdating = true
    }

    override fun stopHeadingUpdates() {
        isUpdating = false
    }

    fun emitHeading(azimuthDegrees: Double) {
        mutableLatestHeading.value =
            HeadingSnapshot(
                azimuthDegrees = azimuthDegrees,
                recordedAtEpochMillis = 1_000L,
            )
    }
}

private class FakeBookmarkRepository(
    bookmarks: List<BookmarkData> = emptyList(),
    private val failSave: Boolean = false,
) : BookmarkRepository {
    val bookmarks = MutableStateFlow(bookmarks)
    val savedBookmarks = mutableListOf<BookmarkData>()

    override fun observeBookmarks(): Flow<List<BookmarkData>> = bookmarks

    override suspend fun isBookmarked(placeId: String): Boolean =
        bookmarks.value.any { bookmark -> bookmark.placeId == placeId }

    override suspend fun saveBookmark(bookmark: BookmarkData): BookmarkData {
        if (failSave) error("bookmark save failed")
        bookmarks.value = bookmarks.value.filterNot { it.placeId == bookmark.placeId } + bookmark
        savedBookmarks += bookmark
        return bookmark
    }

    override suspend fun deleteBookmark(placeId: String) {
        bookmarks.value = bookmarks.value.filterNot { bookmark -> bookmark.placeId == placeId }
    }
}

private class FakeRouteRepository(
    private val transitRefreshData: RouteTransitRefreshData =
        RouteTransitRefreshData(
            type = "BUS",
            arrivalStatus = "REALTIME_AVAILABLE",
            transits = listOf(RouteTransitArrivalData(routeNo = "100", remainingMinute = 6, isLowFloor = true)),
        ),
    private val rerouteRoute: RouteCandidate? = null,
    private val rerouteGate: CompletableDeferred<Unit>? = null,
    private val endSessionId: String = "ended-session",
    private val freshWalkSearchData: RouteSearchData? = null,
    private val freshTransitSearchData: RouteSearchData? = null,
    private val failFreshWalkSearch: Boolean = false,
    private val failFreshTransitSearch: Boolean = false,
) : RouteRepository {
    val transitRefreshCalls = mutableListOf<Pair<String, Int>>()
    val rerouteCalls = mutableListOf<Pair<String, GeoCoordinate>>()
    val endRouteCalls = mutableListOf<String>()
    val freshWalkQueries = mutableListOf<RouteSearchQuery>()
    val freshTransitQueries = mutableListOf<RouteSearchQuery>()

    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        RouteSearchData(query, RouteSearchResult(query.origin, query.destination), RouteSearchSource.serverApi())

    override suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        RouteSearchData(query, RouteSearchResult(query.origin, query.destination), RouteSearchSource.serverApi())

    override suspend fun getFreshRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        freshWalkQueries += query
        if (failFreshWalkSearch) error("fresh walk search failed")
        return freshWalkSearchData?.copy(query = query) ?: getRouteSearchData(query)
    }

    override suspend fun getFreshTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        freshTransitQueries += query
        if (failFreshTransitSearch) error("fresh transit search failed")
        return freshTransitSearchData?.copy(query = query) ?: getTransitRouteSearchData(query)
    }

    override suspend fun selectRoute(
        routeId: String,
        searchId: String,
    ): RouteSessionData = RouteSessionData(sessionId = "selected-session")

    override suspend fun refreshTransit(
        routeId: String,
        legSequence: Int,
    ): RouteTransitRefreshData {
        transitRefreshCalls += routeId to legSequence
        return transitRefreshData
    }

    override suspend fun reroute(
        routeId: String,
        currentPoint: GeoCoordinate,
    ): RouteRerouteData {
        rerouteCalls += routeId to currentPoint
        rerouteGate?.await()
        return RouteRerouteData(route = rerouteRoute)
    }

    override suspend fun endRoute(routeId: String): RouteSessionData {
        endRouteCalls += routeId
        return RouteSessionData(sessionId = endSessionId)
    }

    override suspend fun rateRoute(
        sessionId: String,
        score: Int,
    ): RouteRatingData = RouteRatingData(ratingId = 1L)

    fun completeReroute() {
        rerouteGate?.complete(Unit)
    }
}

private class FakeNavigationReportRepository(
    private val rerouteResult: HazardReportRerouteResult = HazardReportRerouteResult(rerouted = false, route = null),
    private val rerouteFailure: Throwable? = null,
) : ReportRepository {
    val hazardRerouteCalls = mutableListOf<HazardRerouteCall>()

    override fun observeReportHistory() = kotlinx.coroutines.flow.flowOf(emptyList<com.ssafy.e102.eumgil.data.repository.ReportOutboxData>())

    override suspend fun getLatestDraft() = null

    override suspend fun saveDraft(draft: com.ssafy.e102.eumgil.data.repository.ReportDraftData) = draft

    override suspend fun deleteDraft(draftId: String) = Unit

    override suspend fun saveOutbox(outbox: com.ssafy.e102.eumgil.data.repository.ReportOutboxData) = outbox

    override suspend fun submitOutboxToServer(outboxId: String) = com.ssafy.e102.eumgil.data.repository.ReportSubmitResult.Skipped

    override suspend fun rerouteAfterHazardReport(
        reportId: Long,
        routeId: String,
        currentPoint: GeoCoordinate,
        activeLegSequence: Int?,
    ): HazardReportRerouteResult {
        hazardRerouteCalls += HazardRerouteCall(reportId, routeId, currentPoint, activeLegSequence)
        rerouteFailure?.let { throw it }
        return rerouteResult
    }
}

private data class HazardRerouteCall(
    val reportId: Long,
    val routeId: String,
    val currentPoint: GeoCoordinate,
    val activeLegSequence: Int?,
)

private fun testWalkNavigationRequest(): RouteNavigationRequest =
    RouteNavigationRequest(
        origin =
            RouteWaypoint(
                name = "현재 위치",
                coordinate = WALK_START_POINT,
            ),
        destination =
            RouteWaypoint(
                name = "목적지",
                placeId = "destination-place",
                serverPlaceId = 42L,
                provider = "KAKAO",
                providerPlaceId = "kakao-destination-42",
                providerCategory = "ELEVATOR",
                coordinate = WALK_END_POINT,
            ),
        selectedRoute =
            RouteCandidate(
                serverRouteId = "walk-route-1",
                routeOption = RouteOption.SAFE,
                title = "Safe Route",
                summary =
                    RouteSummary(
                        distanceMeters = 900,
                        estimatedTimeMinutes = 15,
                        riskLevel = RouteRiskLevel.LOW,
                        durationSeconds = 900,
                    ),
                preview =
                    RoutePreviewModel(
                        polyline = RoutePolyline(points = listOf(WALK_START_POINT, WALK_MID_POINT, WALK_END_POINT)),
                        segmentCount = 2,
                        renderableSegmentCount = 2,
                    ),
                legs =
                    listOf(
                        RouteLeg(
                            sequence = 1,
                            role = RouteLegRole.WALK_ONLY,
                            distanceMeters = 900,
                            durationSeconds = 900,
                        ),
                    ),
                segments =
                    listOf(
                        RouteSegment(
                            sequence = 1,
                            polyline = RoutePolyline(points = listOf(WALK_START_POINT, WALK_MID_POINT)),
                            distanceMeters = 300,
                            guidanceMessage = "직진",
                        ),
                        RouteSegment(
                            sequence = 2,
                            polyline = RoutePolyline(points = listOf(WALK_MID_POINT, WALK_END_POINT)),
                            distanceMeters = 600,
                            guidanceMessage = "우회전",
                        ),
                    ),
            ),
        source = RouteSearchSource.serverApi(label = "Navigation test route"),
        selectionHandoff =
            RouteNavigationSelectionHandoff(
                searchId = "search-1",
                routeId = "walk-route-1",
                sessionId = "session-1",
            ),
    )

private fun reroutedWalkRoute(): RouteCandidate =
    testWalkNavigationRequest()
        .selectedRoute
        .copy(
            routeId = "rr_rerouted_walk_1",
            serverRouteId = "rr_rerouted_walk_1",
            title = "Rerouted Route",
        )

private fun turnOffsetNavigationRequest(): RouteNavigationRequest =
    testWalkNavigationRequest()
        .copy(
            origin =
                RouteWaypoint(
                    name = "현재 위치",
                    coordinate = TURN_ROUTE_START_POINT,
                ),
            destination =
                RouteWaypoint(
                    name = "목적지",
                    coordinate = TURN_ROUTE_END_POINT,
                ),
            selectedRoute =
                RouteCandidate(
                    serverRouteId = "turn-offset-route",
                    routeOption = RouteOption.SAFE,
                    title = "Turn Offset Route",
                    summary =
                        RouteSummary(
                            distanceMeters = 300,
                            estimatedTimeMinutes = 5,
                            riskLevel = RouteRiskLevel.LOW,
                            durationSeconds = 300,
                        ),
                    preview =
                        RoutePreviewModel(
                            polyline =
                                RoutePolyline(
                                    points =
                                        listOf(
                                            TURN_ROUTE_START_POINT,
                                            TURN_ROUTE_NODE_POINT,
                                            TURN_ROUTE_AFTER_NODE_POINT,
                                            TURN_ROUTE_END_POINT,
                                        ),
                                ),
                            segmentCount = 3,
                            renderableSegmentCount = 3,
                        ),
                    legs =
                        listOf(
                            RouteLeg(
                                sequence = 1,
                                role = RouteLegRole.WALK_ONLY,
                                distanceMeters = 300,
                                durationSeconds = 300,
                            ),
                        ),
                    segments =
                        listOf(
                            RouteSegment(
                                sequence = 1,
                                polyline = RoutePolyline(points = listOf(TURN_ROUTE_START_POINT, TURN_ROUTE_NODE_POINT)),
                                distanceMeters = 90,
                                guidanceMessage = "직진",
                            ),
                            RouteSegment(
                                sequence = 2,
                                polyline = RoutePolyline(points = listOf(TURN_ROUTE_NODE_POINT, TURN_ROUTE_AFTER_NODE_POINT)),
                                distanceMeters = 100,
                                guidanceMessage = "우회전",
                            ),
                            RouteSegment(
                                sequence = 3,
                                polyline = RoutePolyline(points = listOf(TURN_ROUTE_AFTER_NODE_POINT, TURN_ROUTE_END_POINT)),
                                distanceMeters = 110,
                                guidanceMessage = "직진",
                            ),
                        ),
                ),
            selectionHandoff =
                RouteNavigationSelectionHandoff(
                    searchId = "turn-offset-search",
                    routeId = "turn-offset-route",
                    sessionId = "turn-offset-session",
                ),
        )

private fun crosswalkNavigationRequest(): RouteNavigationRequest =
    testWalkNavigationRequest()
        .copy(
            origin =
                RouteWaypoint(
                    name = "현재 위치",
                    coordinate = CROSSWALK_ROUTE_START_POINT,
                ),
            destination =
                RouteWaypoint(
                    name = "목적지",
                    coordinate = CROSSWALK_ROUTE_END_POINT,
                ),
            selectedRoute =
                RouteCandidate(
                    serverRouteId = "crosswalk-route",
                    routeOption = RouteOption.SAFE,
                    title = "Crosswalk Route",
                    summary =
                        RouteSummary(
                            distanceMeters = 300,
                            estimatedTimeMinutes = 5,
                            riskLevel = RouteRiskLevel.LOW,
                            durationSeconds = 300,
                        ),
                    preview =
                        RoutePreviewModel(
                            polyline =
                                RoutePolyline(
                                    points =
                                        listOf(
                                            CROSSWALK_ROUTE_START_POINT,
                                            CROSSWALK_ROUTE_CROSSWALK_START_POINT,
                                            CROSSWALK_ROUTE_CROSSWALK_END_POINT,
                                            CROSSWALK_ROUTE_END_POINT,
                                        ),
                                ),
                            segmentCount = 3,
                            renderableSegmentCount = 3,
                        ),
                    legs =
                        listOf(
                            RouteLeg(
                                sequence = 1,
                                role = RouteLegRole.WALK_ONLY,
                                distanceMeters = 300,
                                durationSeconds = 300,
                            ),
                        ),
                    segments =
                        listOf(
                            RouteSegment(
                                sequence = 1,
                                polyline = RoutePolyline(points = listOf(CROSSWALK_ROUTE_START_POINT, CROSSWALK_ROUTE_CROSSWALK_START_POINT)),
                                distanceMeters = 90,
                                guidanceMessage = "직진",
                            ),
                            RouteSegment(
                                sequence = 2,
                                polyline = RoutePolyline(points = listOf(CROSSWALK_ROUTE_CROSSWALK_START_POINT, CROSSWALK_ROUTE_CROSSWALK_END_POINT)),
                                distanceMeters = 40,
                                guidanceMessage = "횡단보도",
                                guidanceType = RouteGuidanceType.CROSSWALK,
                            ),
                            RouteSegment(
                                sequence = 3,
                                polyline = RoutePolyline(points = listOf(CROSSWALK_ROUTE_CROSSWALK_END_POINT, CROSSWALK_ROUTE_END_POINT)),
                                distanceMeters = 170,
                                guidanceMessage = "직진",
                            ),
                        ),
                ),
            selectionHandoff =
                RouteNavigationSelectionHandoff(
                    searchId = "crosswalk-search",
                    routeId = "crosswalk-route",
                    sessionId = "crosswalk-session",
                ),
        )

private fun testTransitNavigationRequest(
    transitType: RouteLegType = RouteLegType.BUS,
): RouteNavigationRequest =
    RouteNavigationRequest(
        origin =
            RouteWaypoint(
                name = "현재 위치",
                coordinate = TRANSIT_START_POINT,
            ),
        destination =
            RouteWaypoint(
                name = "목적지",
                placeId = "destination-place",
                coordinate = TRANSIT_END_POINT,
            ),
        selectedRoute =
            RouteCandidate(
                serverRouteId = "transit-route-1",
                routeOption = RouteOption.RECOMMENDED,
                title = "Transit Route",
                summary =
                    RouteSummary(
                        distanceMeters = 900,
                        estimatedTimeMinutes = 20,
                        riskLevel = RouteRiskLevel.MEDIUM,
                        durationSeconds = 1_200,
                    ),
                preview =
                    RoutePreviewModel(
                        polyline =
                            RoutePolyline(
                                points =
                                    listOf(
                                        TRANSIT_START_POINT,
                                        TRANSIT_BOARDING_POINT,
                                        TRANSIT_END_POINT,
                                    ),
                            ),
                        segmentCount = 2,
                        renderableSegmentCount = 2,
                    ),
                legs =
                    listOf(
                        RouteLeg(
                            sequence = 1,
                            role = RouteLegRole.WALK_TO_TRANSIT,
                            distanceMeters = 500,
                            durationSeconds = 300,
                        ),
                        RouteLeg(
                            sequence = 2,
                            type = transitType,
                            role = RouteLegRole.TRANSIT,
                            distanceMeters = 400,
                            durationSeconds = 900,
                            estimatedTimeMinutes = 15,
                            routeNo = if (transitType == RouteLegType.SUBWAY) "부산 1호선" else "100",
                            boardingStop =
                                RouteTransitStop(
                                    name = if (transitType == RouteLegType.SUBWAY) "서면역 엘리베이터" else "Bus Stop",
                                    coordinate = TRANSIT_BOARDING_POINT,
                                ),
                            alightingStop =
                                RouteTransitStop(
                                    name = if (transitType == RouteLegType.SUBWAY) "장산역" else "Destination Stop",
                                    coordinate = TRANSIT_END_POINT,
                                ),
                        ),
                    ),
                segments =
                    listOf(
                        RouteSegment(
                            sequence = 1,
                            polyline = RoutePolyline(points = listOf(TRANSIT_START_POINT, TRANSIT_BOARDING_POINT)),
                            distanceMeters = 500,
                            guidanceMessage = "정류장까지 이동",
                            sourceLegSequence = 1,
                        ),
                        RouteSegment(
                            sequence = 2,
                            polyline = RoutePolyline(points = listOf(TRANSIT_BOARDING_POINT, TRANSIT_END_POINT)),
                            distanceMeters = 400,
                            guidanceMessage =
                                if (transitType == RouteLegType.SUBWAY) {
                                    "부산 1호선 지하철 탑승"
                                } else {
                                    "100번 버스 탑승"
                                },
                            sourceLegSequence = 2,
                        ),
                    ),
            ),
        source = RouteSearchSource.serverApi(label = "Transit test route"),
        selectionHandoff =
            RouteNavigationSelectionHandoff(
                searchId = "search-2",
                routeId = "transit-route-1",
                sessionId = "session-2",
            ),
    )

private fun testSparseSegmentNavigationRequest(): RouteNavigationRequest =
    RouteNavigationRequest(
        origin =
            RouteWaypoint(
                name = "Origin",
                coordinate = SPARSE_ROUTE_START_POINT,
            ),
        destination =
            RouteWaypoint(
                name = "Destination",
                coordinate = SPARSE_ROUTE_END_POINT,
            ),
        selectedRoute =
            RouteCandidate(
                serverRouteId = "sparse-route-1",
                routeOption = RouteOption.RECOMMENDED,
                title = "Sparse Route",
                summary =
                    RouteSummary(
                        distanceMeters = 800,
                        estimatedTimeMinutes = 12,
                        riskLevel = RouteRiskLevel.LOW,
                        durationSeconds = 720,
                    ),
                preview =
                    RoutePreviewModel(
                        polyline =
                            RoutePolyline(
                                points =
                                    listOf(
                                        SPARSE_ROUTE_START_POINT,
                                        SPARSE_ROUTE_BRANCH_POINT_1,
                                        SPARSE_ROUTE_END_POINT,
                                    ),
                            ),
                        segmentCount = 2,
                        renderableSegmentCount = 1,
                    ),
                legs =
                    listOf(
                        RouteLeg(
                            sequence = 1,
                            role = RouteLegRole.WALK_TO_TRANSIT,
                            distanceMeters = 400,
                            durationSeconds = 300,
                        ),
                        RouteLeg(
                            sequence = 2,
                            role = RouteLegRole.TRANSIT,
                            type = RouteLegType.BUS,
                            distanceMeters = 400,
                            durationSeconds = 420,
                        ),
                    ),
                segments =
                    listOf(
                        RouteSegment(
                            sequence = 1,
                            polyline = RoutePolyline(),
                            distanceMeters = 400,
                            guidanceMessage = "Walk to boarding stop",
                            sourceLegSequence = 1,
                        ),
                        RouteSegment(
                            sequence = 2,
                            polyline =
                                RoutePolyline(
                                    points =
                                        listOf(
                                            SPARSE_ROUTE_BRANCH_POINT_1,
                                            SPARSE_ROUTE_END_POINT,
                                        ),
                                ),
                            distanceMeters = 400,
                            guidanceMessage = "Ride the bus",
                            sourceLegSequence = 2,
                        ),
                    ),
            ),
        source = RouteSearchSource.serverApi(label = "Sparse navigation test route"),
        selectionHandoff =
            RouteNavigationSelectionHandoff(
                searchId = "search-3",
                routeId = "sparse-route-1",
                sessionId = "session-3",
            ),
    )

private fun testMultiSparseSegmentNavigationRequest(): RouteNavigationRequest =
    RouteNavigationRequest(
        origin =
            RouteWaypoint(
                name = "Origin",
                coordinate = SPARSE_ROUTE_START_POINT,
            ),
        destination =
            RouteWaypoint(
                name = "Destination",
                coordinate = SPARSE_ROUTE_END_POINT,
            ),
        selectedRoute =
            RouteCandidate(
                serverRouteId = "multi-sparse-route-1",
                routeOption = RouteOption.RECOMMENDED,
                title = "Multi Sparse Route",
                summary =
                    RouteSummary(
                        distanceMeters = 900,
                        estimatedTimeMinutes = 14,
                        riskLevel = RouteRiskLevel.LOW,
                        durationSeconds = 840,
                    ),
                preview =
                    RoutePreviewModel(
                        polyline =
                            RoutePolyline(
                                points =
                                    listOf(
                                        SPARSE_ROUTE_START_POINT,
                                        SPARSE_ROUTE_BRANCH_POINT_1,
                                        SPARSE_ROUTE_BRANCH_POINT_2,
                                        SPARSE_ROUTE_END_POINT,
                                    ),
                            ),
                        segmentCount = 3,
                        renderableSegmentCount = 0,
                    ),
                legs =
                    listOf(
                        RouteLeg(
                            sequence = 1,
                            role = RouteLegRole.WALK_ONLY,
                            distanceMeters = 900,
                            durationSeconds = 840,
                            polyline =
                                RoutePolyline(
                                    points =
                                        listOf(
                                            SPARSE_ROUTE_START_POINT,
                                            SPARSE_ROUTE_BRANCH_POINT_1,
                                        ),
                                ),
                        ),
                    ),
                segments =
                    listOf(
                        RouteSegment(
                            sequence = 1,
                            polyline = RoutePolyline(),
                            distanceMeters = 300,
                            guidanceMessage = "Start walking",
                            sourceLegSequence = 1,
                        ),
                        RouteSegment(
                            sequence = 2,
                            polyline = RoutePolyline(),
                            distanceMeters = 300,
                            guidanceMessage = "Turn at the first branch",
                            sourceLegSequence = 1,
                        ),
                        RouteSegment(
                            sequence = 3,
                            polyline = RoutePolyline(),
                            distanceMeters = 300,
                            guidanceMessage = "Turn at the second branch",
                            sourceLegSequence = 1,
                        ),
                    ),
            ),
        source = RouteSearchSource.serverApi(label = "Multi sparse navigation test route"),
        selectionHandoff =
            RouteNavigationSelectionHandoff(
                searchId = "search-3b",
                routeId = "multi-sparse-route-1",
                sessionId = "session-3b",
            ),
    )

private fun testLegPolylineFallbackNavigationRequest(): RouteNavigationRequest =
    RouteNavigationRequest(
        origin =
            RouteWaypoint(
                name = "Origin",
                coordinate = GeoCoordinate(latitude = 35.1800, longitude = 129.0700),
            ),
        destination =
            RouteWaypoint(
                name = "Destination",
                coordinate = GeoCoordinate(latitude = 35.1810, longitude = 129.0780),
            ),
        selectedRoute =
            RouteCandidate(
                serverRouteId = "leg-fallback-route-1",
                routeOption = RouteOption.RECOMMENDED,
                title = "Leg Fallback Route",
                summary =
                    RouteSummary(
                        distanceMeters = 800,
                        estimatedTimeMinutes = 12,
                        riskLevel = RouteRiskLevel.LOW,
                        durationSeconds = 720,
                    ),
                preview =
                    RoutePreviewModel(
                        polyline =
                            RoutePolyline(
                                points =
                                    listOf(
                                        GeoCoordinate(latitude = 35.1800, longitude = 129.0700),
                                        LEG_FALLBACK_START_POINT,
                                        GeoCoordinate(latitude = 35.1810, longitude = 129.0780),
                                    ),
                            ),
                        segmentCount = 2,
                        renderableSegmentCount = 1,
                    ),
                legs =
                    listOf(
                        RouteLeg(
                            sequence = 1,
                            role = RouteLegRole.WALK_TO_TRANSIT,
                            distanceMeters = 400,
                            durationSeconds = 300,
                            polyline =
                                RoutePolyline(
                                    points =
                                        listOf(
                                            LEG_FALLBACK_START_POINT,
                                            GeoCoordinate(latitude = 35.1806, longitude = 129.0735),
                                        ),
                                ),
                        ),
                        RouteLeg(
                            sequence = 2,
                            role = RouteLegRole.TRANSIT,
                            type = RouteLegType.BUS,
                            distanceMeters = 400,
                            durationSeconds = 420,
                        ),
                    ),
                segments =
                    listOf(
                        RouteSegment(
                            sequence = 1,
                            polyline = RoutePolyline(),
                            distanceMeters = 400,
                            guidanceMessage = "Walk to transit",
                            sourceLegSequence = 1,
                        ),
                        RouteSegment(
                            sequence = 2,
                            polyline =
                                RoutePolyline(
                                    points =
                                        listOf(
                                            GeoCoordinate(latitude = 35.1806, longitude = 129.0735),
                                            GeoCoordinate(latitude = 35.1810, longitude = 129.0780),
                                        ),
                                ),
                            distanceMeters = 400,
                            guidanceMessage = "Ride transit",
                            sourceLegSequence = 2,
                        ),
                    ),
            ),
        source = RouteSearchSource.serverApi(label = "Leg fallback navigation test route"),
        selectionHandoff =
            RouteNavigationSelectionHandoff(
                searchId = "search-4",
                routeId = "leg-fallback-route-1",
                sessionId = "session-4",
            ),
    )

private fun testPartialTransitWalkPolylineNavigationRequest(): RouteNavigationRequest =
    RouteNavigationRequest(
        origin =
            RouteWaypoint(
                name = "Origin",
                coordinate = PARTIAL_TRANSIT_WALK_START_POINT,
            ),
        destination =
            RouteWaypoint(
                name = "Destination",
                coordinate = PARTIAL_TRANSIT_FINAL_WALK_END_POINT,
            ),
        selectedRoute =
            RouteCandidate(
                serverRouteId = "partial-transit-walk-route-1",
                transportMode = RouteTransportMode.PUBLIC_TRANSIT,
                routeOption = RouteOption.RECOMMENDED,
                title = "Partial Transit Walk Route",
                summary =
                    RouteSummary(
                        distanceMeters = 1_200,
                        estimatedTimeMinutes = 18,
                        riskLevel = RouteRiskLevel.LOW,
                        durationSeconds = 1_080,
                    ),
                preview =
                    RoutePreviewModel(
                        polyline =
                            RoutePolyline(
                                points =
                                    listOf(
                                        PARTIAL_TRANSIT_WALK_START_POINT,
                                        PARTIAL_TRANSIT_BOARDING_POINT,
                                        PARTIAL_TRANSIT_ALIGHTING_POINT,
                                        PARTIAL_TRANSIT_FINAL_WALK_START_POINT,
                                        PARTIAL_TRANSIT_FINAL_WALK_END_POINT,
                                    ),
                            ),
                        segmentCount = 3,
                        renderableSegmentCount = 2,
                    ),
                legs =
                    listOf(
                        RouteLeg(
                            sequence = 1,
                            type = RouteLegType.WALK,
                            role = RouteLegRole.WALK_TO_TRANSIT,
                            distanceMeters = 300,
                            polyline =
                                RoutePolyline(
                                    points =
                                        listOf(
                                            PARTIAL_TRANSIT_WALK_START_POINT,
                                            PARTIAL_TRANSIT_BOARDING_POINT,
                                        ),
                                ),
                        ),
                        RouteLeg(
                            sequence = 2,
                            type = RouteLegType.BUS,
                            role = RouteLegRole.TRANSIT,
                            distanceMeters = 700,
                            boardingStop =
                                RouteTransitStop(
                                    name = "Boarding Stop",
                                    coordinate = PARTIAL_TRANSIT_BOARDING_POINT,
                                ),
                            alightingStop =
                                RouteTransitStop(
                                    name = "Central Stop",
                                    coordinate = PARTIAL_TRANSIT_ALIGHTING_POINT,
                                ),
                            polyline =
                                RoutePolyline(
                                    points =
                                        listOf(
                                            PARTIAL_TRANSIT_BOARDING_POINT,
                                            PARTIAL_TRANSIT_ALIGHTING_POINT,
                                        ),
                                ),
                        ),
                        RouteLeg(
                            sequence = 3,
                            type = RouteLegType.WALK,
                            role = RouteLegRole.WALK_TO_DESTINATION,
                            distanceMeters = 200,
                            polyline =
                                RoutePolyline(
                                    points =
                                        listOf(
                                            PARTIAL_TRANSIT_FINAL_WALK_START_POINT,
                                            PARTIAL_TRANSIT_FINAL_WALK_END_POINT,
                                        ),
                                ),
                        ),
                    ),
                segments =
                    listOf(
                        RouteSegment(
                            sequence = 1,
                            polyline = RoutePolyline(),
                            distanceMeters = 300,
                            guidanceMessage = "Walk to bus stop",
                            sourceLegSequence = 1,
                        ),
                        RouteSegment(
                            sequence = 2,
                            polyline =
                                RoutePolyline(
                                    points =
                                        listOf(
                                            PARTIAL_TRANSIT_BOARDING_POINT,
                                            PARTIAL_TRANSIT_ALIGHTING_POINT,
                                        ),
                                ),
                            distanceMeters = 700,
                            guidanceMessage = "Ride bus",
                            sourceLegSequence = 2,
                        ),
                        RouteSegment(
                            sequence = 3,
                            polyline =
                                RoutePolyline(
                                    points =
                                        listOf(
                                            PARTIAL_TRANSIT_FINAL_WALK_START_POINT,
                                            PARTIAL_TRANSIT_FINAL_WALK_END_POINT,
                                        ),
                                ),
                            distanceMeters = 200,
                            guidanceMessage = "Walk to destination",
                            sourceLegSequence = 3,
                        ),
                    ),
            ),
        source = RouteSearchSource.serverApi(label = "Partial transit walk navigation route"),
        selectionHandoff =
            RouteNavigationSelectionHandoff(
                searchId = "search-5",
                routeId = "partial-transit-walk-route-1",
                sessionId = "session-5",
            ),
    )

private fun reroutedPartialTransitRoute(serverRouteId: String): RouteCandidate =
    testPartialTransitWalkPolylineNavigationRequest()
        .selectedRoute
        .copy(serverRouteId = serverRouteId)

private fun testUnscopedTransitWalkPolylineNavigationRequest(): RouteNavigationRequest =
    RouteNavigationRequest(
        origin =
            RouteWaypoint(
                name = "Origin",
                coordinate = PARTIAL_TRANSIT_WALK_START_POINT,
            ),
        destination =
            RouteWaypoint(
                name = "Destination",
                coordinate = PARTIAL_TRANSIT_FINAL_WALK_END_POINT,
            ),
        selectedRoute =
            RouteCandidate(
                serverRouteId = "unscoped-transit-walk-route-1",
                transportMode = RouteTransportMode.PUBLIC_TRANSIT,
                routeOption = RouteOption.RECOMMENDED,
                title = "Unscoped Transit Walk Route",
                summary =
                    RouteSummary(
                        distanceMeters = 1_200,
                        estimatedTimeMinutes = 18,
                        riskLevel = RouteRiskLevel.LOW,
                        durationSeconds = 1_080,
                    ),
                preview =
                    RoutePreviewModel(
                        polyline =
                            RoutePolyline(
                                points =
                                    listOf(
                                        PARTIAL_TRANSIT_WALK_START_POINT,
                                        PARTIAL_TRANSIT_BOARDING_POINT,
                                        PARTIAL_TRANSIT_ALIGHTING_POINT,
                                        PARTIAL_TRANSIT_FINAL_WALK_START_POINT,
                                        PARTIAL_TRANSIT_FINAL_WALK_END_POINT,
                                    ),
                            ),
                        segmentCount = 3,
                        renderableSegmentCount = 2,
                    ),
                legs =
                    listOf(
                        RouteLeg(
                            sequence = 1,
                            type = RouteLegType.WALK,
                            role = RouteLegRole.WALK_TO_TRANSIT,
                            distanceMeters = 300,
                            polyline =
                                RoutePolyline(
                                    points =
                                        listOf(
                                            PARTIAL_TRANSIT_WALK_START_POINT,
                                            PARTIAL_TRANSIT_BOARDING_POINT,
                                        ),
                                ),
                        ),
                        RouteLeg(
                            sequence = 2,
                            type = RouteLegType.BUS,
                            role = RouteLegRole.TRANSIT,
                            distanceMeters = 700,
                            polyline =
                                RoutePolyline(
                                    points =
                                        listOf(
                                            PARTIAL_TRANSIT_BOARDING_POINT,
                                            PARTIAL_TRANSIT_ALIGHTING_POINT,
                                        ),
                                ),
                        ),
                        RouteLeg(
                            sequence = 3,
                            type = RouteLegType.WALK,
                            role = RouteLegRole.WALK_TO_DESTINATION,
                            distanceMeters = 200,
                            polyline =
                                RoutePolyline(
                                    points =
                                        listOf(
                                            PARTIAL_TRANSIT_FINAL_WALK_START_POINT,
                                            PARTIAL_TRANSIT_FINAL_WALK_END_POINT,
                                        ),
                                ),
                        ),
                    ),
                segments =
                    listOf(
                        RouteSegment(
                            sequence = 1,
                            polyline =
                                RoutePolyline(
                                    points =
                                        listOf(
                                            PARTIAL_TRANSIT_WALK_START_POINT,
                                            PARTIAL_TRANSIT_BOARDING_POINT,
                                        ),
                                ),
                            distanceMeters = 300,
                            guidanceMessage = "Walk to bus stop",
                        ),
                        RouteSegment(
                            sequence = 2,
                            polyline =
                                RoutePolyline(
                                    points =
                                        listOf(
                                            PARTIAL_TRANSIT_BOARDING_POINT,
                                            PARTIAL_TRANSIT_ALIGHTING_POINT,
                                        ),
                                ),
                            distanceMeters = 700,
                            guidanceMessage = "Ride bus",
                        ),
                    ),
            ),
        source = RouteSearchSource.serverApi(label = "Unscoped transit walk navigation route"),
        selectionHandoff =
            RouteNavigationSelectionHandoff(
                searchId = "search-6",
                routeId = "unscoped-transit-walk-route-1",
                sessionId = "session-6",
            ),
    )

private fun testPointAnchorNavigationRequest(): RouteNavigationRequest =
    RouteNavigationRequest(
        origin =
            RouteWaypoint(
                name = "Origin",
                coordinate = POINT_ANCHOR_ROUTE_START_POINT,
            ),
        destination =
            RouteWaypoint(
                name = "Destination",
                coordinate = POINT_ANCHOR_ROUTE_END_POINT,
            ),
        selectedRoute =
            RouteCandidate(
                serverRouteId = "point-anchor-route-1",
                routeOption = RouteOption.SAFE,
                title = "Point Anchor Route",
                summary =
                    RouteSummary(
                        distanceMeters = 320,
                        estimatedTimeMinutes = 5,
                        riskLevel = RouteRiskLevel.LOW,
                        durationSeconds = 300,
                    ),
                preview =
                    RoutePreviewModel(
                        polyline =
                            RoutePolyline(
                                points =
                                    listOf(
                                        POINT_ANCHOR_ROUTE_START_POINT,
                                        POINT_ANCHOR_EVENT_POINT,
                                        POINT_ANCHOR_ROUTE_END_POINT,
                                    ),
                            ),
                        segmentCount = 1,
                        renderableSegmentCount = 0,
                    ),
                legs =
                    listOf(
                        RouteLeg(
                            sequence = 1,
                            role = RouteLegRole.WALK_ONLY,
                            distanceMeters = 320,
                            durationSeconds = 300,
                            polyline =
                                RoutePolyline(
                                    points =
                                        listOf(
                                            POINT_ANCHOR_ROUTE_START_POINT,
                                            POINT_ANCHOR_ROUTE_END_POINT,
                                        ),
                                ),
                        ),
                    ),
                segments =
                    listOf(
                        RouteSegment(
                            sequence = 1,
                            polyline = RoutePolyline(),
                            anchorCoordinate = POINT_ANCHOR_EVENT_POINT,
                            distanceMeters = 320,
                            guidanceMessage = "Crosswalk ahead",
                            sourceLegSequence = 1,
                        ),
                    ),
            ),
        source = RouteSearchSource.serverApi(label = "Point anchor navigation test route"),
        selectionHandoff =
            RouteNavigationSelectionHandoff(
                searchId = "search-5",
                routeId = "point-anchor-route-1",
                sessionId = "session-5",
            ),
    )

private fun testFarOffRouteNavigationRequest(): RouteNavigationRequest =
    RouteNavigationRequest(
        origin =
            RouteWaypoint(
                name = "Origin",
                coordinate = FAR_ROUTE_START_POINT,
            ),
        destination =
            RouteWaypoint(
                name = "Destination",
                coordinate = FAR_ROUTE_END_POINT,
            ),
        selectedRoute =
            RouteCandidate(
                serverRouteId = "far-off-route-1",
                routeOption = RouteOption.RECOMMENDED,
                title = "Far Off Route",
                summary =
                    RouteSummary(
                        distanceMeters = 2_200,
                        estimatedTimeMinutes = 22,
                        riskLevel = RouteRiskLevel.LOW,
                        durationSeconds = 1_320,
                    ),
                preview =
                    RoutePreviewModel(
                        polyline =
                            RoutePolyline(
                                points =
                                    listOf(
                                        FAR_ROUTE_START_POINT,
                                        FAR_ROUTE_MID_POINT,
                                        FAR_ROUTE_END_POINT,
                                    ),
                            ),
                        segmentCount = 2,
                        renderableSegmentCount = 2,
                    ),
                legs =
                    listOf(
                        RouteLeg(
                            sequence = 1,
                            role = RouteLegRole.WALK_ONLY,
                            distanceMeters = 2_200,
                            durationSeconds = 1_320,
                        ),
                    ),
                segments =
                    listOf(
                        RouteSegment(
                            sequence = 1,
                            polyline = RoutePolyline(points = listOf(FAR_ROUTE_START_POINT, FAR_ROUTE_MID_POINT)),
                            distanceMeters = 1_100,
                            guidanceMessage = "Head east",
                        ),
                        RouteSegment(
                            sequence = 2,
                            polyline = RoutePolyline(points = listOf(FAR_ROUTE_MID_POINT, FAR_ROUTE_END_POINT)),
                            distanceMeters = 1_100,
                            guidanceMessage = "Keep going",
                        ),
                    ),
            ),
        source = RouteSearchSource.serverApi(label = "Far off navigation test route"),
        selectionHandoff =
            RouteNavigationSelectionHandoff(
                searchId = "search-6",
                routeId = "far-off-route-1",
                sessionId = "session-6",
            ),
    )

private fun lowVisionRemainingSearchData(
    routeId: String,
    routeOption: RouteOption,
    distanceMeters: Int,
    estimatedTimeMinutes: Int,
    durationSeconds: Int,
): RouteSearchData =
    RouteSearchData(
        query =
            RouteSearchQuery(
                origin =
                    RouteWaypoint(
                        name = "Current",
                        coordinate = FAR_OFF_ROUTE_POINT,
                    ),
                destination =
                    RouteWaypoint(
                        name = "Destination",
                        coordinate = FAR_ROUTE_END_POINT,
                    ),
                requestedOptions = listOf(routeOption),
            ),
        result =
            RouteSearchResult(
                origin =
                    RouteWaypoint(
                        name = "Current",
                        coordinate = FAR_OFF_ROUTE_POINT,
                    ),
                destination =
                    RouteWaypoint(
                        name = "Destination",
                        coordinate = FAR_ROUTE_END_POINT,
                    ),
                routes =
                    listOf(
                        RouteCandidate(
                            serverRouteId = routeId,
                            routeOption = routeOption,
                            title = "Actual Remaining Route",
                            summary =
                                RouteSummary(
                                    distanceMeters = distanceMeters,
                                    estimatedTimeMinutes = estimatedTimeMinutes,
                                    riskLevel = RouteRiskLevel.LOW,
                                    durationSeconds = durationSeconds,
                                ),
                            preview = RoutePreviewModel(),
                        ),
                    ),
            ),
        source = RouteSearchSource.serverApi(label = "Low vision remaining route test"),
    )

private val WALK_START_POINT = GeoCoordinate(latitude = 35.1796, longitude = 129.0756)
private val WALK_SMALL_DISPLAY_SHIFT_POINT = GeoCoordinate(latitude = 35.1796, longitude = 129.07565)
private val WALK_PRE_TURN_POINT = GeoCoordinate(latitude = 35.1796, longitude = 129.077905)
private val WALK_NEAR_TURN_POINT = GeoCoordinate(latitude = 35.1796, longitude = 129.07806)
private val WALK_VERY_NEAR_TURN_POINT = GeoCoordinate(latitude = 35.1796, longitude = 129.07808)
private val WALK_MID_POINT = GeoCoordinate(latitude = 35.1796, longitude = 129.0781)
private val WALK_SECOND_SEGMENT_FOCUS_POINT = GeoCoordinate(latitude = 35.1796, longitude = 129.07935)
private val WALK_END_POINT = GeoCoordinate(latitude = 35.1796, longitude = 129.0806)
private val WALK_DESTINATION_NEAR_POINT = GeoCoordinate(latitude = 35.1796, longitude = 129.08052)
private val WALK_DESTINATION_OUTSIDE_AUTO_ARRIVAL_POINT = GeoCoordinate(latitude = 35.1796, longitude = 129.08035)
private val OFF_ROUTE_POINT = GeoCoordinate(latitude = 35.1815, longitude = 129.0756)
private val NEAR_ROUTE_OFFSET_MID_POINT = GeoCoordinate(latitude = 35.17969, longitude = 129.0781)
private val OFF_ROUTE_WITHIN_OLD_THRESHOLD_POINT = GeoCoordinate(latitude = 35.17969, longitude = 129.0781)
private val OFF_ROUTE_CANDIDATE_POINT = GeoCoordinate(latitude = 35.1800, longitude = 129.0781)
private val NEAR_ROUTE_BEFORE_START_POINT =
    GeoCoordinate(latitude = 35.17969, longitude = 129.0781)
private val TURN_ROUTE_START_POINT = GeoCoordinate(latitude = 35.1800, longitude = 129.0700)
private val TURN_ROUTE_NODE_POINT = GeoCoordinate(latitude = 35.1800, longitude = 129.0710)
private val TURN_ROUTE_BEFORE_NODE_POINT = GeoCoordinate(latitude = 35.1800, longitude = 129.0708)
private val TURN_ROUTE_AFTER_NODE_POINT = GeoCoordinate(latitude = 35.1810, longitude = 129.0710)
private val TURN_ROUTE_OFFSET_AFTER_NODE_POINT_1 = GeoCoordinate(latitude = 35.18009, longitude = 129.07095)
private val TURN_ROUTE_OFFSET_AFTER_NODE_POINT_2 = GeoCoordinate(latitude = 35.18010, longitude = 129.07095)
private val TURN_ROUTE_PARALLEL_OFFSET_AFTER_NODE_POINT_1 = GeoCoordinate(latitude = 35.18008, longitude = 129.07080)
private val TURN_ROUTE_PARALLEL_OFFSET_AFTER_NODE_POINT_2 = GeoCoordinate(latitude = 35.18012, longitude = 129.07080)
private val TURN_ROUTE_END_POINT = GeoCoordinate(latitude = 35.1820, longitude = 129.0710)
private val CROSSWALK_ROUTE_START_POINT = GeoCoordinate(latitude = 35.1830, longitude = 129.0700)
private val CROSSWALK_ROUTE_CROSSWALK_START_POINT = GeoCoordinate(latitude = 35.1830, longitude = 129.0710)
private val CROSSWALK_ROUTE_INSIDE_POINT = GeoCoordinate(latitude = 35.1830, longitude = 129.07118)
private val CROSSWALK_ROUTE_NEAR_END_POINT = GeoCoordinate(latitude = 35.1830, longitude = 129.07132)
private val CROSSWALK_ROUTE_CROSSWALK_END_POINT = GeoCoordinate(latitude = 35.1830, longitude = 129.0714)
private val CROSSWALK_ROUTE_AFTER_END_POINT = GeoCoordinate(latitude = 35.1830, longitude = 129.0715)
private val CROSSWALK_ROUTE_END_POINT = GeoCoordinate(latitude = 35.1830, longitude = 129.0730)
private val LEG_FALLBACK_START_POINT = GeoCoordinate(latitude = 35.1802, longitude = 129.0718)
private val PARTIAL_TRANSIT_WALK_START_POINT = GeoCoordinate(latitude = 35.1800, longitude = 129.0700)
private val PARTIAL_TRANSIT_WALK_PROGRESS_POINT = GeoCoordinate(latitude = 35.1802, longitude = 129.0710)
private val PARTIAL_TRANSIT_BOARDING_POINT = GeoCoordinate(latitude = 35.1804, longitude = 129.0720)
private val PARTIAL_TRANSIT_RIDE_POINT = GeoCoordinate(latitude = 35.1808, longitude = 129.0740)
private val PARTIAL_TRANSIT_ALIGHTING_POINT = GeoCoordinate(latitude = 35.1812, longitude = 129.0760)
private val PARTIAL_TRANSIT_FINAL_WALK_START_POINT = GeoCoordinate(latitude = 35.1812, longitude = 129.0760)
private val PARTIAL_TRANSIT_FINAL_WALK_PROGRESS_POINT = GeoCoordinate(latitude = 35.1814, longitude = 129.0770)
private val PARTIAL_TRANSIT_FINAL_WALK_END_POINT = GeoCoordinate(latitude = 35.1816, longitude = 129.0780)
private val POINT_ANCHOR_ROUTE_START_POINT = GeoCoordinate(latitude = 35.1804, longitude = 129.0710)
private val POINT_ANCHOR_EVENT_POINT = GeoCoordinate(latitude = 35.1805, longitude = 129.0722)
private val POINT_ANCHOR_ROUTE_END_POINT = GeoCoordinate(latitude = 35.1808, longitude = 129.0745)
private val SPARSE_ROUTE_START_POINT = GeoCoordinate(latitude = 35.1800, longitude = 129.0700)
private val SPARSE_ROUTE_BRANCH_POINT_1 = GeoCoordinate(latitude = 35.1800, longitude = 129.0740)
private val SPARSE_ROUTE_BRANCH_POINT_2 = GeoCoordinate(latitude = 35.1800, longitude = 129.0760)
private val SPARSE_ROUTE_END_POINT = GeoCoordinate(latitude = 35.1800, longitude = 129.0780)
private val FAR_ROUTE_START_POINT = GeoCoordinate(latitude = 35.1000, longitude = 129.0000)
private val FAR_ROUTE_MID_POINT = GeoCoordinate(latitude = 35.1000, longitude = 129.0100)
private val FAR_ROUTE_END_POINT = GeoCoordinate(latitude = 35.1000, longitude = 129.0200)
private val NEAR_OFF_ROUTE_POINT = GeoCoordinate(latitude = 35.1060, longitude = 129.0200)
private val SLIGHTLY_SHIFTED_OFF_ROUTE_POINT = GeoCoordinate(latitude = 35.10611, longitude = 129.0200)
private val FARTHER_SHIFTED_OFF_ROUTE_POINT = GeoCoordinate(latitude = 35.10636, longitude = 129.0200)
private val FAR_OFF_ROUTE_POINT = GeoCoordinate(latitude = 35.1100, longitude = 129.0200)

private val TRANSIT_START_POINT = GeoCoordinate(latitude = 35.1700, longitude = 129.0600)
private val TRANSIT_BOARDING_POINT = GeoCoordinate(latitude = 35.1700, longitude = 129.0625)
private val TRANSIT_END_POINT = GeoCoordinate(latitude = 35.1700, longitude = 129.0670)
