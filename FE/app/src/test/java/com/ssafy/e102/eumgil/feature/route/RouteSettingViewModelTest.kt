package com.ssafy.e102.eumgil.feature.route

import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.location.CurrentLocationManager
import com.ssafy.e102.eumgil.core.location.LocationSnapshot
import com.ssafy.e102.eumgil.core.model.PlaceCategory
import com.ssafy.e102.eumgil.core.model.PlaceDestination
import com.ssafy.e102.eumgil.core.model.RecentDestination
import com.ssafy.e102.eumgil.core.model.RouteCandidate
import com.ssafy.e102.eumgil.core.model.RouteLeg
import com.ssafy.e102.eumgil.core.model.RouteLegRole
import com.ssafy.e102.eumgil.core.model.RouteLegType
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.core.model.RoutePolyline
import com.ssafy.e102.eumgil.core.model.RouteRiskLevel
import com.ssafy.e102.eumgil.core.model.RouteSearchData
import com.ssafy.e102.eumgil.core.model.RouteSearchQuery
import com.ssafy.e102.eumgil.core.model.RouteSearchResult
import com.ssafy.e102.eumgil.core.model.RouteSearchSource
import com.ssafy.e102.eumgil.core.model.RouteSegment
import com.ssafy.e102.eumgil.core.model.RouteSegmentSafetyFlags
import com.ssafy.e102.eumgil.core.model.RouteSummary
import com.ssafy.e102.eumgil.core.model.RoutePreviewModel
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.RecentSearch
import com.ssafy.e102.eumgil.core.model.RouteTransitStop
import com.ssafy.e102.eumgil.core.model.RouteTransportMode
import com.ssafy.e102.eumgil.core.model.RouteWaypoint
import com.ssafy.e102.eumgil.core.model.SearchQuery
import com.ssafy.e102.eumgil.core.model.SearchResult
import com.ssafy.e102.eumgil.data.local.datasource.RouteLocalDataSource
import com.ssafy.e102.eumgil.data.mock.fixture.MockRouteFixtures
import com.ssafy.e102.eumgil.data.remote.datasource.RouteApiException
import com.ssafy.e102.eumgil.data.remote.datasource.RouteFailureKind
import com.ssafy.e102.eumgil.data.remote.datasource.RouteRemoteDataSource
import com.ssafy.e102.eumgil.data.repository.DefaultRouteRepository
import com.ssafy.e102.eumgil.data.repository.InMemoryDestinationSelectionRepository
import com.ssafy.e102.eumgil.data.repository.RouteEditingTarget
import com.ssafy.e102.eumgil.data.repository.RouteRatingData
import com.ssafy.e102.eumgil.data.repository.RouteRepository
import com.ssafy.e102.eumgil.data.repository.RouteRerouteData
import com.ssafy.e102.eumgil.data.repository.RouteSessionData
import com.ssafy.e102.eumgil.data.repository.RouteTransitRefreshData
import com.ssafy.e102.eumgil.data.repository.SearchRepository
import com.ssafy.e102.eumgil.data.route.RouteSearchRequestDto
import com.ssafy.e102.eumgil.data.route.RouteSearchResponseDto
import com.ssafy.e102.eumgil.feature.search.SearchSelectionMode
import com.ssafy.e102.eumgil.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RouteSettingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `init loads SAFE route by default with preview map state`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = testRouteRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertFalse(uiState.isLoading)
            assertEquals(RouteTravelMode.WALK, uiState.selectedTravelMode)
            assertEquals(RouteOption.SAFE, uiState.selectedOption)
            assertEquals("place-1", uiState.destination.placeId)
            assertEquals(PlaceCategory.RESTAURANT, uiState.destination.category)
            assertEquals("ID place-1 | Category RESTAURANT", uiState.destination.metadataLabel)
            assertEquals("현재 위치 확인 중", uiState.origin.name)
            assertEquals(RouteOriginState.CURRENT_LOCATION_LOADING, uiState.originState)
            assertEquals("카페 온도", uiState.destination.name)
            assertFalse(uiState.isUsingFallbackDestination)
            assertEquals(listOf(RouteOption.SAFE, RouteOption.SHORTEST), uiState.optionCards.map(RouteOptionCardUiState::routeOption))
            val safeCard = uiState.optionCards.first()
            val shortestCard = uiState.optionCards.last()
            assertTrue(safeCard.isSelected)
            assertEquals("안전한 길", safeCard.title)
            assertEquals("보행 안전 요소를 우선으로 반영한 추천 경로입니다.", safeCard.description)
            assertEquals("현재 선택됨", safeCard.selectionLabel)
            assertEquals("추천", safeCard.highlightLabel)
            assertEquals(
                listOf("위험도", "예상 시간"),
                safeCard.metrics.map(RouteOptionCardMetricUiState::label),
            )
            assertEquals(
                listOf("낮음", "16분"),
                safeCard.metrics.map(RouteOptionCardMetricUiState::value),
            )
            assertEquals("최단 경로", shortestCard.title)
            assertEquals("이동 거리를 줄이는 기준으로 빠른 경로를 비교합니다.", shortestCard.description)
            assertEquals("탭하여 선택", shortestCard.selectionLabel)
            assertEquals(
                listOf("예상 시간", "예상 거리"),
                shortestCard.metrics.map(RouteOptionCardMetricUiState::label),
            )
            assertEquals(RouteOption.SAFE, uiState.selectedRoute?.routeOption)
            assertEquals("안전한 길", uiState.selectedRoute?.optionTitle)
            assertEquals("Safe Route", uiState.selectedRoute?.title)
            assertEquals(720, uiState.selectedRoute?.distanceMeters)
            assertEquals(16, uiState.selectedRoute?.estimatedTimeMinutes)
            assertEquals(RouteRiskLevel.LOW, uiState.selectedRoute?.riskLevel)
            assertEquals("16분", uiState.selectedRoute?.estimatedTimeLabel)
            assertEquals("720 m", uiState.selectedRoute?.distanceLabel)
            assertEquals("위험도 낮음", uiState.selectedRoute?.riskLabel)
            assertEquals("6/6", uiState.selectedRoute?.renderableSegmentLabel)
            assertEquals(
                listOf("예상 시간", "예상 거리", "위험도", "표시 구간"),
                uiState.selectedRoute?.summaryMetrics?.map(RouteSummaryMetricUiState::label),
            )
            assertEquals(uiState.destination, uiState.selectedRoute?.destination)
            assertTrue(uiState.selectedRoute?.previewPoints?.size ?: 0 >= 2)
            assertEquals(null, uiState.selectedRoute?.previewFallbackNotice)
            assertEquals(RoutePreviewMapStatus.READY, uiState.routePreviewMap.status)
            assertTrue(uiState.cta.isEnabled)
            assertEquals(null, uiState.routePreviewMap.fallbackMessage)
            assertEquals(RouteOption.SAFE, uiState.routePreviewMap.routeOption)
            assertEquals(uiState.origin.coordinate, uiState.routePreviewMap.originCoordinate)
            assertEquals(uiState.destination.coordinate, uiState.routePreviewMap.destinationCoordinate)
            assertEquals(uiState.selectedRoute?.previewPoints, uiState.routePreviewMap.polyline)
            assertEquals(null, uiState.routePreviewMap.fallbackMessage)
            assertTrue(uiState.routePreviewMap.isDisplayable)
            assertTrue(uiState.cta.isEnabled)
            assertEquals("안내 시작", uiState.cta.label)
            assertEquals("선택한 경로로 길 안내를 시작할 수 있습니다.", uiState.cta.supportingText)
            assertTrue(uiState.isStartEnabled)
            assertEquals(
                listOf("엘리베이터 있음", "공사 구간 주의", "신호등 횡단보도"),
                uiState.selectedRoute?.detailAccessibilityChips?.map(RouteDetailChipUiState::label),
            )
            assertEquals(
                listOf("연석 단차 주의"),
                uiState.selectedRoute?.detailHighlights?.map(RouteDetailHighlightUiState::title),
            )
            assertEquals(
                listOf(
                    "출발",
                    "150m 직진 이동",
                    "엘리베이터 이용",
                    "공사 구간 진입",
                    "신호 횡단보도 건너기",
                    "단차 구간 주의",
                    "450m 직진 이동",
                    "목적지 도착",
                ),
                uiState.selectedRoute?.detailSteps?.map(RouteDetailStepUiState::title),
            )
            assertEquals(
                listOf(
                    RouteDetailStepKind.START,
                    RouteDetailStepKind.STRAIGHT,
                    RouteDetailStepKind.ELEVATOR,
                    RouteDetailStepKind.CONSTRUCTION,
                    RouteDetailStepKind.CROSSWALK,
                    RouteDetailStepKind.CURB_GAP,
                    RouteDetailStepKind.STRAIGHT,
                    RouteDetailStepKind.ARRIVAL,
                ),
                uiState.selectedRoute?.detailSteps?.map(RouteDetailStepUiState::kind),
            )
            val detailChipKinds = uiState.selectedRoute?.detailAccessibilityChips?.map(RouteDetailChipUiState::kind).orEmpty()
            assertTrue(
                detailChipKinds.containsAll(
                    listOf(
                        RouteDetailChipKind.ELEVATOR,
                        RouteDetailChipKind.CONSTRUCTION,
                        RouteDetailChipKind.SIGNAL_CROSSWALK,
                    ),
                ),
            )
            assertTrue(uiState.selectedRoute?.detailHighlights.orEmpty().all { highlight -> highlight.tone == RouteDetailTone.WARNING })
            assertEquals(
                listOf("150 m", "90 m", "62 m", "128 m", "100 m", "450 m"),
                uiState.selectedRoute?.detailSteps?.drop(1)?.dropLast(1)?.mapNotNull(RouteDetailStepUiState::metaLabel),
            )
            assertEquals(null, uiState.selectedRoute?.detailFallbackMessage)
        }

    @Test
    fun `empty destination keeps destination placeholder and hides route summary`() =
        runTest {
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = testRouteRepository(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                )

            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertTrue(uiState.isUsingFallbackDestination)
            assertEquals(RouteDestinationHandoffState.EMPTY, uiState.destinationHandoffState)
            assertEquals("목적지를 선택하면 경로를 보여드릴게요.", uiState.destinationFallbackMessage)
            assertEquals(null, uiState.destination.metadataLabel)
            assertEquals("도착지를 선택해주세요", uiState.destination.name)
            assertEquals("검색 또는 지도에서 도착지를 설정할 수 있어요.", uiState.destination.supportingText)
            assertEquals(null, uiState.selectedRoute)
            assertTrue(uiState.optionCards.isEmpty())
            assertEquals(RoutePreviewMapStatus.NO_DESTINATION, uiState.routePreviewMap.status)
            assertEquals("목적지를 선택하면 경로 미리보기를 보여드려요.", uiState.routePreviewMap.fallbackMessage)
            assertFalse(uiState.routePreviewMap.isDisplayable)
            assertFalse(uiState.isStartEnabled)
            assertEquals("검색 또는 지도에서 목적지를 선택하면 안내 시작을 활성화합니다.", uiState.cta.supportingText)
        }

    @Test
    fun `start action without destination emits destination required snackbar instead of navigation`() =
        runTest {
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = testRouteRepository(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                )

            advanceUntilIdle()
            val uiEvent = async { viewModel.uiEvent.first() }
            runCurrent()

            viewModel.onAction(RouteSettingUiAction.StartNavigationClicked)
            advanceUntilIdle()

            val event = uiEvent.await()
            assertTrue(event is RouteSettingUiEvent.ShowSnackbar)
            assertEquals(
                R.string.route_setting_start_destination_required_snackbar,
                (event as RouteSettingUiEvent.ShowSnackbar).messageResId,
            )
            assertFalse(viewModel.uiState.value.ctaAcknowledged)
            assertFalse(viewModel.uiState.value.isStartEnabled)
        }

    @Test
    fun `destination outside Gangseo shows unsupported area state without requesting route`() =
        runTest {
            val routeRepository = CountingRouteRepository()
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(
                        testDestination().copy(
                            address = "부산 부산진구 중앙대로 1001",
                        ),
                    )
                }
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = routeRepository,
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertFalse(uiState.isLoading)
            assertEquals(0, routeRepository.callCount)
            assertEquals(RouteEditingTarget.DESTINATION, uiState.unsupportedArea?.editingTarget)
            assertEquals(RoutePreviewMapStatus.ERROR, uiState.routePreviewMap.status)
            assertEquals(null, uiState.selectedRoute)
            assertFalse(uiState.showsDuribalCallAction)
            assertFalse(uiState.isStartEnabled)
        }

    @Test
    fun `selected destination update replaces fallback destination metadata after init`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = testRouteRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isUsingFallbackDestination)
            assertEquals(null, viewModel.uiState.value.destination.metadataLabel)

            destinationSelectionRepository.updateSelectedDestination(testDestination())
            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertFalse(uiState.isUsingFallbackDestination)
            assertEquals(RouteDestinationHandoffState.DIRECT, uiState.destinationHandoffState)
            assertEquals(null, uiState.destinationFallbackMessage)
            assertEquals("place-1", uiState.destination.placeId)
            assertEquals(PlaceCategory.RESTAURANT, uiState.destination.category)
            assertEquals("ID place-1 | Category RESTAURANT", uiState.destination.metadataLabel)
            assertEquals("카페 온도", uiState.destination.name)
            assertEquals(uiState.destination, uiState.selectedRoute?.destination)
        }

    @Test
    fun `invalid destination coordinates fall back to default destination and disable start action`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(invalidDestination())
                }
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = testRouteRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertTrue(uiState.isUsingFallbackDestination)
            assertEquals(RouteDestinationHandoffState.INVALID_COORDINATE, uiState.destinationHandoffState)
            assertEquals("목적지 정보를 다시 확인한 뒤 경로를 보여드릴게요.", uiState.destinationFallbackMessage)
            assertEquals("부산역", uiState.destination.name)
            assertEquals(uiState.destination, uiState.selectedRoute?.destination)
            assertEquals(RoutePreviewMapStatus.INVALID_DESTINATION, uiState.routePreviewMap.status)
            assertEquals("목적지 좌표를 확인할 수 없어 지도 미리보기를 표시하지 않습니다.", uiState.routePreviewMap.fallbackMessage)
            assertFalse(uiState.routePreviewMap.isDisplayable)
            assertFalse(uiState.isStartEnabled)
            assertEquals("목적지 좌표를 다시 확인하면 안내 시작을 활성화합니다.", uiState.cta.supportingText)
        }

    @Test
    fun `partial route data exposes fallback summary labels and address placeholder`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(
                        PlaceDestination(
                            placeId = "place-2",
                            name = "주소 없는 목적지",
                            address = null,
                            latitude = 35.1799,
                            longitude = 129.0762,
                        ),
                    )
                }
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = partialRouteRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()

            val uiState = viewModel.uiState.value
            val selectedRoute = requireNotNull(uiState.selectedRoute)

            assertEquals("주소 정보 없음", uiState.destination.supportingText)
            assertEquals("확인 중", selectedRoute.estimatedTimeLabel)
            assertEquals("확인 중", selectedRoute.distanceLabel)
            assertEquals("위험도 보통", selectedRoute.riskLabel)
            assertEquals("0/2", selectedRoute.renderableSegmentLabel)
            assertEquals("선택한 경로를 따라 이동합니다.", selectedRoute.guidanceMessage)
            assertEquals(uiState.destination, selectedRoute.destination)
            assertEquals(
                "일부 구간은 지도에 표시할 수 없어 요약 정보만 보여드려요.",
                selectedRoute.previewFallbackNotice,
            )
            assertEquals(listOf("상세 정보 확인 중"), selectedRoute.detailAccessibilityChips.map(RouteDetailChipUiState::label))
            assertEquals(listOf(RouteDetailChipKind.PENDING), selectedRoute.detailAccessibilityChips.map(RouteDetailChipUiState::kind))
            assertTrue(selectedRoute.detailHighlights.isEmpty())
            assertEquals(
                listOf("출발", "세부 경로 확인 중", "목적지 도착"),
                selectedRoute.detailSteps.map(RouteDetailStepUiState::title),
            )
            assertEquals(
                listOf(RouteDetailStepKind.START, RouteDetailStepKind.FALLBACK, RouteDetailStepKind.ARRIVAL),
                selectedRoute.detailSteps.map(RouteDetailStepUiState::kind),
            )
            assertEquals(
                "세부 이동 정보는 준비 중입니다. 요약 정보와 주의 구간을 먼저 확인하세요.",
                selectedRoute.detailFallbackMessage,
            )
            assertEquals(RoutePreviewMapStatus.POLYLINE_UNAVAILABLE, uiState.routePreviewMap.status)
            assertEquals(RouteOption.SAFE, uiState.routePreviewMap.routeOption)
            assertEquals(uiState.origin.coordinate, uiState.routePreviewMap.originCoordinate)
            assertEquals(uiState.destination.coordinate, uiState.routePreviewMap.destinationCoordinate)
            assertTrue(uiState.routePreviewMap.polyline.isEmpty())
            assertEquals("선택한 경로를 지도에 표시할 수 없어 요약 정보만 보여드려요.", uiState.routePreviewMap.fallbackMessage)
            assertFalse(uiState.routePreviewMap.isDisplayable)
        }

    @Test
    fun `clearing selected destination returns route setting to fallback shell`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = testRouteRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            destinationSelectionRepository.clearSelectedDestination()
            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertTrue(uiState.isUsingFallbackDestination)
            assertEquals(RouteDestinationHandoffState.EMPTY, uiState.destinationHandoffState)
            assertEquals("도착지를 선택해주세요", uiState.destination.name)
            assertEquals(RouteOption.SAFE, uiState.selectedOption)
            assertEquals(null, uiState.selectedRoute)
            assertFalse(uiState.isStartEnabled)
        }

    @Test
    fun `empty route result keeps preview map no route fallback and disables start action`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = emptyRouteRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertTrue(uiState.optionCards.isEmpty())
            assertEquals(null, uiState.selectedRoute)
            assertEquals(RouteDestinationHandoffState.DIRECT, uiState.destinationHandoffState)
            assertEquals(RoutePreviewMapStatus.NO_ROUTE, uiState.routePreviewMap.status)
            assertEquals(uiState.origin.coordinate, uiState.routePreviewMap.originCoordinate)
            assertEquals(uiState.destination.coordinate, uiState.routePreviewMap.destinationCoordinate)
            assertEquals("선택된 경로가 없습니다.", uiState.routePreviewMap.fallbackMessage)
            assertFalse(uiState.routePreviewMap.isDisplayable)
            assertFalse(uiState.cta.isEnabled)
            assertEquals("표시할 경로가 준비되면 시작 CTA를 활성화합니다.", uiState.cta.supportingText)
            assertFalse(uiState.isStartEnabled)
        }

    @Test
    fun `route load failure exposes disabled CTA and error supporting text`() =
        runTest {
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = failingRouteRepository(),
                    destinationSelectionRepository =
                        InMemoryDestinationSelectionRepository().apply {
                            updateSelectedDestination(testDestination())
                        },
                )

            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertFalse(uiState.cta.isEnabled)
            assertEquals("경로 정보를 다시 불러오면 시작 CTA를 활성화할 수 있습니다.", uiState.cta.supportingText)
            assertEquals("전체 경로를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.", uiState.loadErrorMessage)
            assertEquals(RoutePreviewMapStatus.ERROR, uiState.routePreviewMap.status)
            assertEquals("전체 경로를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.", uiState.routePreviewMap.fallbackMessage)
            assertFalse(uiState.routePreviewMap.isDisplayable)
        }

    @Test
    fun `first real location after fallback walk failure forces route reload`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val currentLocationManager = ViewModelTestCurrentLocationManager()
            val routeRepository = FallbackFailureRecoveryRouteRepository()
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = routeRepository,
                    destinationSelectionRepository = destinationSelectionRepository,
                    currentLocationManager = currentLocationManager,
                )

            advanceUntilIdle()
            val initialWalkSearchCount = routeRepository.walkSearchCount
            assertTrue(initialWalkSearchCount >= 1)
            assertEquals(DEFAULT_TEST_ORIGIN_COORDINATE, routeRepository.lastWalkQuery?.origin?.coordinate)
            assertTrue(viewModel.uiState.value.loadErrorMessage?.isNotBlank() == true)

            viewModel.startLocationUpdates()
            advanceUntilIdle()
            val fallbackReloadCount = routeRepository.walkSearchCount
            assertEquals(DEFAULT_TEST_ORIGIN_COORDINATE, routeRepository.lastWalkQuery?.origin?.coordinate)

            val actualLocation =
                LocationSnapshot(
                    latitude = 35.1802,
                    longitude = 129.0764,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 1_716_000_000_000L,
                )
            currentLocationManager.updateLocation(actualLocation)
            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertTrue(routeRepository.walkSearchCount > fallbackReloadCount)
            assertEquals(
                GeoCoordinate(
                    latitude = actualLocation.latitude,
                    longitude = actualLocation.longitude,
                ),
                routeRepository.lastWalkQuery?.origin?.coordinate,
            )
            assertEquals(RouteOriginState.CURRENT_LOCATION_RESOLVED, uiState.originState)
            assertEquals(
                GeoCoordinate(
                    latitude = actualLocation.latitude,
                    longitude = actualLocation.longitude,
                ),
                uiState.origin.coordinate,
            )
            assertEquals(null, uiState.loadErrorMessage)
            assertEquals(RoutePreviewMapStatus.READY, uiState.routePreviewMap.status)
            assertTrue(uiState.routePreviewMap.isDisplayable)
        }

    @Test
    fun `route timeout exposes timeout specific failure copy`() =
        runTest {
            val viewModel =
                RouteSettingViewModel(
                    routeRepository =
                        routeApiFailingRepository(
                            routeApiException(
                                failureKind = RouteFailureKind.CLIENT_TIMEOUT,
                                status = "ROUTE_CLIENT_TIMEOUT",
                                message = "temporary timeout",
                            ),
                        ),
                    destinationSelectionRepository =
                        InMemoryDestinationSelectionRepository().apply {
                            updateSelectedDestination(testDestination())
                        },
                )

            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertFalse(uiState.cta.isEnabled)
            assertEquals("경로 응답이 늦어지고 있어요. 잠시 후 다시 시도해 주세요.", uiState.loadErrorMessage)
            assertEquals(RoutePreviewMapStatus.ERROR, uiState.routePreviewMap.status)
            assertEquals("경로 응답이 늦어지고 있어요. 잠시 후 다시 시도해 주세요.", uiState.routePreviewMap.fallbackMessage)
            assertEquals(null, uiState.loadNoticeMessage)
            assertFalse(uiState.routePreviewMap.isDisplayable)
        }

    @Test
    fun `route network failure exposes connectivity specific failure copy`() =
        runTest {
            val viewModel =
                RouteSettingViewModel(
                    routeRepository =
                        routeApiFailingRepository(
                            routeApiException(
                                failureKind = RouteFailureKind.UNKNOWN_HOST,
                                status = "ROUTE_UNKNOWN_HOST",
                                message = "network unavailable",
                            ),
                        ),
                    destinationSelectionRepository =
                        InMemoryDestinationSelectionRepository().apply {
                            updateSelectedDestination(testDestination())
                        },
                )

            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertFalse(uiState.cta.isEnabled)
            assertEquals("네트워크 연결 상태를 확인한 뒤 다시 시도해 주세요.", uiState.loadErrorMessage)
            assertEquals(RoutePreviewMapStatus.ERROR, uiState.routePreviewMap.status)
            assertEquals("네트워크 연결 상태를 확인한 뒤 다시 시도해 주세요.", uiState.routePreviewMap.fallbackMessage)
            assertEquals(null, uiState.loadNoticeMessage)
            assertFalse(uiState.routePreviewMap.isDisplayable)
        }

    @Test
    fun `route no path failure exposes no route specific failure copy`() =
        runTest {
            val manualOrigin =
                PlaceDestination(
                    placeId = "manual-origin",
                    name = "강서 출발지",
                    address = "부산 강서구 녹산산단335로 7",
                    latitude = 35.1796,
                    longitude = 129.0756,
                    category = PlaceCategory.OTHER,
                )
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedOrigin(manualOrigin)
                    updateSelectedDestination(testDestination())
                }
            val viewModel =
                RouteSettingViewModel(
                    routeRepository =
                        routeApiFailingRepository(
                            routeApiException(
                                failureKind = RouteFailureKind.HTTP_RESPONSE,
                                status = "RT4040",
                                message = "no route",
                                httpStatusCode = 404,
                            ),
                        ),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertFalse(uiState.cta.isEnabled)
            assertEquals(RouteTravelMode.TRANSIT, uiState.selectedTravelMode)
            assertEquals(RouteOption.RECOMMENDED, uiState.selectedOption)
            assertTrue(uiState.showsDuribalCallAction)
            assertEquals("탐색 가능한 경로가 없어요. 출발지나 도착지를 다시 선택해 주세요.", uiState.loadErrorMessage)
            assertEquals(RoutePreviewMapStatus.NO_ROUTE, uiState.routePreviewMap.status)
            assertEquals("탐색 가능한 경로가 없어요. 출발지나 도착지를 다시 선택해 주세요.", uiState.routePreviewMap.fallbackMessage)
            assertEquals(null, uiState.loadNoticeMessage)
            assertFalse(uiState.routePreviewMap.isDisplayable)
        }

    @Test
    fun `route no path failure exposes no route state without extra delay`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val viewModel =
                RouteSettingViewModel(
                    routeRepository =
                        routeApiFailingRepository(
                            routeApiException(
                                failureKind = RouteFailureKind.HTTP_RESPONSE,
                                status = "RT4040",
                                message = "no route",
                                httpStatusCode = 404,
                            ),
                        ),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

            val uiState = viewModel.uiState.value
            assertFalse(uiState.isLoading)
            assertEquals("탐색 가능한 경로가 없어요. 출발지나 도착지를 다시 선택해 주세요.", uiState.loadErrorMessage)
            assertEquals(RoutePreviewMapStatus.NO_ROUTE, uiState.routePreviewMap.status)
        }

    @Test
    fun `walk no path failure retries fresh route before exposing no route state`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = WalkNoRouteThenFreshSuccessRepository()
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = routeRepository,
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()

            val uiState = viewModel.uiState.value
            assertEquals(1, routeRepository.walkSearchCount)
            assertEquals(1, routeRepository.freshWalkSearchCount)
            assertEquals(RouteTravelMode.WALK, uiState.selectedTravelMode)
            assertEquals(RoutePreviewMapStatus.READY, uiState.routePreviewMap.status)
            assertEquals("Safe Route", uiState.selectedRoute?.title)
            assertEquals(null, uiState.loadErrorMessage)
        }

    @Test
    fun `route same endpoint failure exposes same endpoint specific failure copy`() =
        runTest {
            val viewModel =
                RouteSettingViewModel(
                    routeRepository =
                        routeApiFailingRepository(
                            routeApiException(
                                failureKind = RouteFailureKind.HTTP_RESPONSE,
                                status = "RT4004",
                                message = "same endpoint",
                                httpStatusCode = 400,
                            ),
                        ),
                    destinationSelectionRepository =
                        InMemoryDestinationSelectionRepository().apply {
                            updateSelectedDestination(testDestination())
                        },
                )

            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertFalse(uiState.cta.isEnabled)
            assertEquals("출발지와 도착지를 다르게 선택해 주세요.", uiState.loadErrorMessage)
            assertEquals(RoutePreviewMapStatus.ERROR, uiState.routePreviewMap.status)
            assertEquals("출발지와 도착지를 다르게 선택해 주세요.", uiState.routePreviewMap.fallbackMessage)
            assertEquals(null, uiState.loadNoticeMessage)
            assertFalse(uiState.routePreviewMap.isDisplayable)
        }

    @Test
    fun `same origin and destination are blocked before route search request`() =
        runTest {
            val samePlace = testDestination()
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedOrigin(samePlace)
                    updateSelectedDestination(samePlace)
                }
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = failIfCalledRouteRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertEquals("출발지와 도착지를 다르게 선택해 주세요.", uiState.loadErrorMessage)
            assertEquals(RoutePreviewMapStatus.ERROR, uiState.routePreviewMap.status)
            assertEquals("출발지와 도착지를 다르게 선택해 주세요.", uiState.routePreviewMap.fallbackMessage)
            assertFalse(uiState.routePreviewMap.isDisplayable)
        }

    @Test
    fun `route option selection swaps summary and preview map to shortest route`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = testRouteRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            viewModel.onAction(RouteSettingUiAction.RouteOptionSelected(RouteOption.SHORTEST))
            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertEquals(RouteOption.SHORTEST, uiState.selectedOption)
            assertEquals(RouteOption.SHORTEST, uiState.selectedRoute?.routeOption)
            assertEquals(RouteOption.SHORTEST, uiState.routePreviewMap.routeOption)
            assertEquals(RoutePreviewMapStatus.READY, uiState.routePreviewMap.status)
            assertEquals(uiState.selectedRoute?.previewPoints, uiState.routePreviewMap.polyline)
            assertEquals(uiState.origin.coordinate, uiState.routePreviewMap.originCoordinate)
            assertEquals(uiState.destination.coordinate, uiState.routePreviewMap.destinationCoordinate)
            assertTrue(uiState.routePreviewMap.isDisplayable)
            assertEquals("최단 경로", uiState.selectedRoute?.optionTitle)
            assertEquals("Shortest Route", uiState.selectedRoute?.title)
            assertEquals(RouteRiskLevel.MEDIUM, uiState.selectedRoute?.riskLevel)
            assertEquals(uiState.destination, uiState.selectedRoute?.destination)
            assertEquals(
                listOf("신호등 횡단보도"),
                uiState.selectedRoute?.detailAccessibilityChips?.map(RouteDetailChipUiState::label),
            )
            assertEquals(
                listOf("연석 단차 주의"),
                uiState.selectedRoute?.detailHighlights?.map(RouteDetailHighlightUiState::title),
            )
            assertEquals(
                listOf(RouteDetailStepKind.CROSSWALK, RouteDetailStepKind.CURB_GAP),
                uiState.selectedRoute?.detailSteps?.drop(1)?.dropLast(1)?.map(RouteDetailStepUiState::kind),
            )
            assertEquals(
                listOf(RouteDetailTone.WARNING, RouteDetailTone.WARNING),
                uiState.selectedRoute?.detailSteps?.drop(1)?.dropLast(1)?.map(RouteDetailStepUiState::tone),
            )
            val detailChips = uiState.selectedRoute?.detailAccessibilityChips.orEmpty()
            val detailSteps = uiState.selectedRoute?.detailSteps?.drop(1)?.dropLast(1).orEmpty()
            assertTrue(detailChips.any { chip -> chip.kind == RouteDetailChipKind.SIGNAL_CROSSWALK })
            assertTrue(detailSteps.any { step -> step.kind == RouteDetailStepKind.CROSSWALK })
            assertTrue(detailSteps.all { step -> step.tone == RouteDetailTone.WARNING })
            assertTrue(uiState.optionCards.single { card -> card.routeOption == RouteOption.SHORTEST }.isSelected)
            assertEquals(
                "현재 선택됨",
                uiState.optionCards.single { card -> card.routeOption == RouteOption.SHORTEST }.selectionLabel,
            )
            assertEquals(
                "탭하여 선택",
                uiState.optionCards.single { card -> card.routeOption == RouteOption.SAFE }.selectionLabel,
            )
        }

    @Test
    fun `detail steps classify straight left crosswalk and right guidance separately`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = directionalRouteRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()

            val detailSteps = viewModel.uiState.value.selectedRoute?.detailSteps.orEmpty()

            assertEquals(
                listOf("출발", "120m 직진 이동", "80m 후 좌회전", "음향 신호 횡단보도 건너기", "150m 후 우회전", "목적지 도착"),
                detailSteps.map(RouteDetailStepUiState::title),
            )
            assertEquals(
                listOf(
                    "현재 위치에서 선택한 경로 안내를 시작합니다.",
                    "목적지까지 약 8분",
                    "목적지까지 약 6분",
                    "목적지까지 약 5분",
                    "목적지까지 약 3분",
                    "안내가 완료되었습니다",
                ),
                detailSteps.map(RouteDetailStepUiState::description),
            )
            assertEquals(
                listOf(
                    RouteDetailStepKind.START,
                    RouteDetailStepKind.STRAIGHT,
                    RouteDetailStepKind.TURN_LEFT,
                    RouteDetailStepKind.CROSSWALK,
                    RouteDetailStepKind.TURN_RIGHT,
                    RouteDetailStepKind.ARRIVAL,
                ),
                detailSteps.map(RouteDetailStepUiState::kind),
            )
            assertEquals(
                "목적지까지 약 5분",
                detailSteps[3].description,
            )
            assertEquals("음향 신호", detailSteps[3].badgeLabel)
            assertEquals(RouteDetailTone.INFO, detailSteps[3].badgeTone)
        }

    @Test
    fun `detail steps hide pending meta label when crosswalk distance is missing`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = crosswalkMissingDistanceRouteRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()

            val crosswalkStep =
                viewModel.uiState.value.selectedRoute
                    ?.detailSteps
                    ?.firstOrNull { step -> step.kind == RouteDetailStepKind.CROSSWALK }

            assertEquals("횡단보도 건너기", crosswalkStep?.title)
            assertEquals(null, crosswalkStep?.metaLabel)
        }

    @Test
    fun `waypoint swap action swaps displayed endpoints and preview direction`() =
        runTest {
            val manualOrigin =
                PlaceDestination(
                    placeId = "manual-origin",
                    name = "강서 출발지",
                    address = "부산 강서구 녹산산단335로 7",
                    latitude = 35.1796,
                    longitude = 129.0756,
                    category = PlaceCategory.OTHER,
                )
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedOrigin(manualOrigin)
                    updateSelectedDestination(testDestination())
                }
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = testRouteRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            val initialState = viewModel.uiState.value
            val initialOrigin = initialState.origin
            val initialDestination = initialState.destination
            val initialPreviewPoints = initialState.routePreviewMap.polyline

            viewModel.onAction(RouteSettingUiAction.WaypointsSwapClicked)
            advanceUntilIdle()

            val swappedState = viewModel.uiState.value

            assertEquals(initialDestination, swappedState.origin)
            assertEquals(initialOrigin.placeId, swappedState.destination.placeId)
            assertEquals(initialOrigin.name, swappedState.destination.name)
            assertEquals(initialOrigin.coordinate, swappedState.destination.coordinate)
            assertEquals(initialDestination.coordinate, swappedState.routePreviewMap.originCoordinate)
            assertEquals(initialOrigin.coordinate, swappedState.routePreviewMap.destinationCoordinate)
            assertEquals(swappedState.routePreviewMap.originCoordinate, swappedState.routePreviewMap.polyline.firstOrNull())
            assertEquals(swappedState.routePreviewMap.destinationCoordinate, swappedState.routePreviewMap.polyline.lastOrNull())
            assertEquals(swappedState.destination, swappedState.selectedRoute?.destination)
            assertEquals(initialDestination.coordinate, swappedState.routePreviewMap.polyline.first())
            assertEquals(initialOrigin.coordinate, swappedState.routePreviewMap.polyline.last())
            assertEquals(swappedState.destination.placeId, swappedState.selectedRoute?.destination?.placeId)
            assertEquals(swappedState.destination.name, swappedState.selectedRoute?.destination?.name)
            assertEquals(swappedState.destination.coordinate, swappedState.selectedRoute?.destination?.coordinate)
            assertTrue(swappedState.isStartEnabled)

            viewModel.onAction(RouteSettingUiAction.WaypointsSwapClicked)
            advanceUntilIdle()

            val restoredState = viewModel.uiState.value

            assertEquals(initialOrigin.placeId, restoredState.origin.placeId)
            assertEquals(initialOrigin.name, restoredState.origin.name)
            assertEquals(initialOrigin.coordinate, restoredState.origin.coordinate)
            assertEquals(initialDestination, restoredState.destination)
            assertEquals(restoredState.routePreviewMap.originCoordinate, restoredState.routePreviewMap.polyline.firstOrNull())
            assertEquals(restoredState.routePreviewMap.destinationCoordinate, restoredState.routePreviewMap.polyline.lastOrNull())
            assertEquals(initialOrigin.coordinate, restoredState.routePreviewMap.polyline.first())
            assertEquals(initialDestination.coordinate, restoredState.routePreviewMap.polyline.last())
        }

    @Test
    fun `waypoint search opens search in apply to route mode`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = testRouteRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            val uiEvent = async { viewModel.uiEvent.first() }
            runCurrent()

            viewModel.onAction(RouteSettingUiAction.WaypointClicked(RouteEditingTarget.ORIGIN))
            advanceUntilIdle()

            val event = uiEvent.await()
            assertTrue(event is RouteSettingUiEvent.NavigateToSearch)
            val navigateToSearch = event as RouteSettingUiEvent.NavigateToSearch
            assertEquals(RouteEditingTarget.ORIGIN, navigateToSearch.editingTarget)
            assertEquals(SearchSelectionMode.APPLY_TO_ROUTE, navigateToSearch.selectionMode)
            assertEquals(RouteEditingTarget.ORIGIN, destinationSelectionRepository.editingTarget.value)
        }

    @Test
    fun `route detail action selects target option and emits detail navigation event`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = testRouteRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            val uiEvent = async { viewModel.uiEvent.first() }
            runCurrent()

            viewModel.onAction(RouteSettingUiAction.RouteOptionDetailClicked(RouteOption.SHORTEST))
            advanceUntilIdle()

            val uiState = viewModel.uiState.value
            assertEquals(RouteOption.SHORTEST, uiState.selectedOption)
            assertEquals(RouteOption.SHORTEST, uiState.selectedRoute?.routeOption)
            val event = uiEvent.await()
            assertTrue(event is RouteSettingUiEvent.NavigateToRouteDetail)
            assertEquals(RouteOption.SHORTEST, (event as RouteSettingUiEvent.NavigateToRouteDetail).routeOption)
        }

    @Test
    fun `same destination reselection reloads route shell and resets option to SAFE`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = CountingRouteRepository()
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = routeRepository,
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            viewModel.onAction(RouteSettingUiAction.RouteOptionSelected(RouteOption.SHORTEST))
            advanceUntilIdle()

            destinationSelectionRepository.updateSelectedDestination(testDestination())
            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertEquals(2, routeRepository.callCount)
            assertEquals(RouteOption.SAFE, uiState.selectedOption)
            assertEquals(RouteOption.SAFE, uiState.selectedRoute?.routeOption)
            assertEquals("Safe Route #2", uiState.selectedRoute?.title)
            assertEquals(uiState.destination, uiState.selectedRoute?.destination)
        }

    @Test
    fun `init defaults to transit when SAFE walk distance exceeds 750m`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = TransitModeRecordingRouteRepository(walkSafeDistanceMeters = 820)
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = routeRepository,
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertEquals(RouteTravelMode.TRANSIT, uiState.selectedTravelMode)
            assertEquals(RouteOption.RECOMMENDED, uiState.selectedOption)
            assertEquals(
                listOf(RouteOption.RECOMMENDED, RouteOption.MIN_TRANSFER, RouteOption.MIN_WALK),
                uiState.optionCards.map(RouteOptionCardUiState::routeOption),
            )
            assertEquals("Transit Recommended", uiState.selectedRoute?.title)
            assertEquals(1, routeRepository.walkSearchCount)
            assertEquals(1, routeRepository.transitSearchCount)
        }

    @Test
    fun `initial long walk keeps transit selected while default transit finishes`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = DelayedTransitRouteRepository(walkSafeDistanceMeters = 820)
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = routeRepository,
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()

            val interimState = viewModel.uiState.value

            assertTrue(interimState.isLoading)
            assertEquals(RouteTravelMode.TRANSIT, interimState.selectedTravelMode)
            assertEquals(RouteTravelMode.TRANSIT, interimState.pendingTravelMode)
            assertEquals(RouteOption.RECOMMENDED, interimState.selectedOption)
            assertTrue(interimState.optionCards.isEmpty())
            assertEquals(null, interimState.selectedRoute)
            assertEquals(RoutePreviewMapStatus.LOADING, interimState.routePreviewMap.status)
            assertFalse(interimState.routePreviewMap.isDisplayable)
            assertTrue(interimState.loadNoticeMessage?.isNotBlank() == true)
            assertEquals(1, routeRepository.walkSearchCount)
            assertEquals(1, routeRepository.transitSearchCount)

            routeRepository.completeTransitSuccess()
            advanceUntilIdle()

            val finalState = viewModel.uiState.value

            assertEquals(RouteTravelMode.TRANSIT, finalState.selectedTravelMode)
            assertEquals(RouteOption.RECOMMENDED, finalState.selectedOption)
            assertEquals("Transit Recommended", finalState.selectedRoute?.title)
            assertEquals(null, finalState.loadNoticeMessage)
        }

    @Test
    fun `transit timeout after successful long walk keeps walk result visible`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = TransitTimeoutRouteRepository(walkSafeDistanceMeters = 820)
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = routeRepository,
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertEquals(RouteTravelMode.WALK, uiState.selectedTravelMode)
            assertEquals(RouteOption.SAFE, uiState.selectedOption)
            assertEquals("Safe Route", uiState.selectedRoute?.title)
            assertEquals(null, uiState.loadErrorMessage)
            assertTrue(uiState.loadNoticeMessage?.isNotBlank() == true)
            assertEquals(RoutePreviewMapStatus.READY, uiState.routePreviewMap.status)
            assertTrue(uiState.routePreviewMap.isDisplayable)
            assertEquals(1, routeRepository.walkSearchCount)
            assertEquals(1, routeRepository.transitSearchCount)
            return@runTest

            assertEquals(RouteTravelMode.TRANSIT, uiState.selectedTravelMode)
            assertEquals(RouteOption.RECOMMENDED, uiState.selectedOption)
            assertTrue(uiState.optionCards.isEmpty())
            assertEquals(null, uiState.selectedRoute)
            assertEquals("경로 응답이 늦어지고 있어요. 잠시 후 다시 시도해 주세요.", uiState.loadErrorMessage)
            assertEquals(null, uiState.loadNoticeMessage)
            assertEquals(RoutePreviewMapStatus.ERROR, uiState.routePreviewMap.status)
            assertEquals("경로 응답이 늦어지고 있어요. 잠시 후 다시 시도해 주세요.", uiState.routePreviewMap.fallbackMessage)
            assertFalse(uiState.routePreviewMap.isDisplayable)
            assertEquals(1, routeRepository.walkSearchCount)
            assertEquals(1, routeRepository.transitSearchCount)
        }

    @Test
    fun `manual transit selection keeps transit state when transit search times out`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = TransitTimeoutRouteRepository(walkSafeDistanceMeters = 720)
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = routeRepository,
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            assertEquals(RouteTravelMode.WALK, viewModel.uiState.value.selectedTravelMode)

            viewModel.onAction(RouteSettingUiAction.TravelModeSelected(RouteTravelMode.TRANSIT))
            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertEquals(RouteTravelMode.TRANSIT, uiState.selectedTravelMode)
            assertEquals(RouteOption.RECOMMENDED, uiState.selectedOption)
            assertTrue(uiState.optionCards.isEmpty())
            assertEquals(null, uiState.selectedRoute)
            assertEquals("경로 응답이 늦어지고 있어요. 잠시 후 다시 시도해 주세요.", uiState.loadErrorMessage)
            assertEquals(null, uiState.loadNoticeMessage)
            assertEquals(RoutePreviewMapStatus.ERROR, uiState.routePreviewMap.status)
            assertEquals("경로 응답이 늦어지고 있어요. 잠시 후 다시 시도해 주세요.", uiState.routePreviewMap.fallbackMessage)
            assertEquals(1, routeRepository.walkSearchCount)
            assertEquals(1, routeRepository.transitSearchCount)
        }

    @Test
    fun `manual travel mode change reloads walk surface when returning to walk`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = TransitModeRecordingRouteRepository(walkSafeDistanceMeters = 720)
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = routeRepository,
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            assertEquals(RouteTravelMode.WALK, viewModel.uiState.value.selectedTravelMode)

            viewModel.onAction(RouteSettingUiAction.TravelModeSelected(RouteTravelMode.TRANSIT))
            advanceUntilIdle()

            val transitState = viewModel.uiState.value
            assertEquals(RouteTravelMode.TRANSIT, transitState.selectedTravelMode)
            assertEquals(RouteOption.RECOMMENDED, transitState.selectedOption)
            assertEquals(
                listOf(RouteOption.RECOMMENDED, RouteOption.MIN_TRANSFER, RouteOption.MIN_WALK),
                transitState.optionCards.map(RouteOptionCardUiState::routeOption),
            )
            assertEquals(1, routeRepository.walkSearchCount)
            assertEquals(1, routeRepository.transitSearchCount)

            viewModel.onAction(RouteSettingUiAction.TravelModeSelected(RouteTravelMode.WALK))
            advanceUntilIdle()

            assertEquals(RouteTravelMode.WALK, viewModel.uiState.value.selectedTravelMode)
            assertEquals(RouteOption.SAFE, viewModel.uiState.value.selectedOption)
            assertEquals("Safe Route", viewModel.uiState.value.selectedRoute?.title)
            assertEquals(2, routeRepository.walkSearchCount)
            assertEquals(1, routeRepository.transitSearchCount)
        }

    @Test
    fun `manual walk refresh reloads selected walk options with a fresh search`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = TransitModeRecordingRouteRepository(walkSafeDistanceMeters = 720)
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = routeRepository,
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()

            assertEquals(1, routeRepository.walkSearchCount)

            viewModel.onAction(RouteSettingUiAction.RouteRefreshClicked)
            advanceUntilIdle()

            assertEquals(RouteTravelMode.WALK, viewModel.uiState.value.selectedTravelMode)
            assertEquals(1, routeRepository.freshWalkSearchCount)
            assertFalse(viewModel.uiState.value.isRouteRefreshing)
            assertTrue(viewModel.uiState.value.isStartEnabled)
        }

    @Test
    fun `manual transit refresh reloads selected transit options with a fresh search`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = TransitModeRecordingRouteRepository(walkSafeDistanceMeters = 720)
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = routeRepository,
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            viewModel.onAction(RouteSettingUiAction.TravelModeSelected(RouteTravelMode.TRANSIT))
            advanceUntilIdle()

            assertEquals(1, routeRepository.transitSearchCount)
            assertEquals("transit-search-1", routeRepository.lastTransitSearchId)

            viewModel.onAction(RouteSettingUiAction.RouteRefreshClicked)
            advanceUntilIdle()

            assertEquals(RouteTravelMode.TRANSIT, viewModel.uiState.value.selectedTravelMode)
            assertEquals(1, routeRepository.transitSearchCount)
            assertEquals(1, routeRepository.freshTransitSearchCount)
            assertEquals("transit-fresh-search-1", routeRepository.lastTransitSearchId)
            assertFalse(viewModel.uiState.value.isRouteRefreshing)
            assertTrue(viewModel.uiState.value.isStartEnabled)
        }

    @Test
    fun `transit detail steps distinguish bus and subway segments`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = TransitModeRecordingRouteRepository(walkSafeDistanceMeters = 720)
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = routeRepository,
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()

            viewModel.onAction(RouteSettingUiAction.TravelModeSelected(RouteTravelMode.TRANSIT))
            advanceUntilIdle()

            val detailSteps = viewModel.uiState.value.selectedRoute?.detailSteps.orEmpty()

            assertEquals(
                listOf(
                    RouteDetailStepKind.START,
                    RouteDetailStepKind.STRAIGHT,
                    RouteDetailStepKind.BUS,
                    RouteDetailStepKind.SUBWAY,
                    RouteDetailStepKind.STRAIGHT,
                    RouteDetailStepKind.ARRIVAL,
                ),
                detailSteps.map(RouteDetailStepUiState::kind),
            )
            assertEquals("버스 탑승", detailSteps[2].title)
            assertEquals("시청 정류장에서 1001번 버스를 타고 이동하세요.", detailSteps[2].description)
            assertEquals("지하철 탑승", detailSteps[3].title)
            assertEquals("시청역에서 2호선 지하철을 타고 이동하세요.", detailSteps[3].description)
        }

    @Test
    fun `transit selected route keeps walk and transit detail polylines separated`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = TransitModeRecordingRouteRepository(walkSafeDistanceMeters = 720)
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = routeRepository,
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            viewModel.onAction(RouteSettingUiAction.TravelModeSelected(RouteTravelMode.TRANSIT))
            advanceUntilIdle()

            val detailPolylines = viewModel.uiState.value.selectedRoute?.detailPolylines.orEmpty()

            assertEquals(
                listOf(
                    RouteDetailPolylineKind.WALK,
                    RouteDetailPolylineKind.TRANSIT,
                    RouteDetailPolylineKind.TRANSIT,
                    RouteDetailPolylineKind.WALK,
                ),
                detailPolylines.map(RouteDetailPolylineUiState::kind),
            )
            assertTrue(detailPolylines.all { polyline -> polyline.points.size >= 2 })
        }

    @Test
    fun `transit selected route restores walk leg polylines when segment geometry omits them`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository =
                TransitModeRecordingRouteRepository(
                    walkSafeDistanceMeters = 720,
                    omitFirstTransitWalkSegmentPolyline = true,
                )
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = routeRepository,
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            viewModel.onAction(RouteSettingUiAction.TravelModeSelected(RouteTravelMode.TRANSIT))
            advanceUntilIdle()

            val detailPolylines = viewModel.uiState.value.selectedRoute?.detailPolylines.orEmpty()

            assertEquals(
                listOf(
                    RouteDetailPolylineKind.WALK,
                    RouteDetailPolylineKind.TRANSIT,
                    RouteDetailPolylineKind.TRANSIT,
                    RouteDetailPolylineKind.WALK,
                ),
                detailPolylines.map(RouteDetailPolylineUiState::kind),
            )
            assertEquals(GeoCoordinate(35.1796, 129.0756), detailPolylines.firstOrNull()?.points?.firstOrNull())
            assertTrue(detailPolylines.all { polyline -> polyline.points.size >= 2 })
        }

    @Test
    fun `manual transit selection exposes remote success debug info`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = TransitModeRecordingRouteRepository(walkSafeDistanceMeters = 720)
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = routeRepository,
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            viewModel.onAction(RouteSettingUiAction.TravelModeSelected(RouteTravelMode.TRANSIT))
            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertEquals(null, uiState.loadErrorMessage)
            assertTrue(uiState.loadDebugMessage?.contains("mode=TRANSIT") == true)
            assertTrue(uiState.loadDebugMessage?.contains("path=/routes/search/transit") == true)
            assertTrue(uiState.loadDebugMessage?.contains("result=success") == true)
            assertTrue(uiState.loadDebugMessage?.contains("source=SERVER_API") == true)
            assertTrue(uiState.loadDebugMessage?.contains("fromCache=false") == true)
        }

    @Test
    fun `manual transit selection exposes auth gate debug info when session is missing`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = MissingSessionTransitRouteRepository(walkSafeDistanceMeters = 720)
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = routeRepository,
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            viewModel.onAction(RouteSettingUiAction.TravelModeSelected(RouteTravelMode.TRANSIT))
            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertEquals(RouteTravelMode.TRANSIT, uiState.selectedTravelMode)
            assertEquals("로그인이 필요해요. 다시 로그인한 뒤 시도해 주세요.", uiState.loadErrorMessage)
            assertTrue(uiState.loadDebugMessage?.contains("mode=TRANSIT") == true)
            assertTrue(uiState.loadDebugMessage?.contains("path=/routes/search/transit") == true)
            assertTrue(uiState.loadDebugMessage?.contains("result=failure") == true)
            assertTrue(uiState.loadDebugMessage?.contains("layer=AUTH_GATE") == true)
            assertTrue(uiState.loadDebugMessage?.contains("httpStatus=401") == true)
            assertTrue(uiState.loadDebugMessage?.contains("status=ROUTE_AUTH_MISSING_SESSION") == true)
        }

    @Test
    fun `start action selects route and emits handoff payload with search and session ids`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = TransitModeRecordingRouteRepository(walkSafeDistanceMeters = 820)
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = routeRepository,
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            val uiEvent = async { viewModel.uiEvent.first() }
            runCurrent()

            viewModel.onAction(RouteSettingUiAction.StartNavigationClicked)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.ctaAcknowledged)
            assertFalse(viewModel.uiState.value.cta.isEnabled)
            assertEquals("길 안내를 시작하는 중입니다.", viewModel.uiState.value.cta.supportingText)
            val event = uiEvent.await()
            assertTrue(event is RouteSettingUiEvent.StartNavigationRequested)
            val request = (event as RouteSettingUiEvent.StartNavigationRequested).request
            val selectionHandoff = requireNotNull(request.selectionHandoff)
            assertEquals(RouteOption.RECOMMENDED, request.selectedRoute.routeOption)
            assertEquals("pt_rt_recommended_001", routeRepository.lastSelectedRouteId)
            assertEquals("transit-search-1", routeRepository.lastSelectedSearchId)
            assertEquals("transit-search-1", selectionHandoff.searchId)
            assertEquals("pt_rt_recommended_001", selectionHandoff.routeId)
            assertEquals("session-pt_rt_recommended_001", selectionHandoff.sessionId)
            assertEquals(2500, selectionHandoff.initialRemainingDistanceMeters)
            assertEquals(900, selectionHandoff.initialRemainingDurationSeconds)
        }

    @Test
    fun `start action refreshes expired search once and retries select with fresh search id`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = ExpiredSelectRecoveryRouteRepository()
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = routeRepository,
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            val uiEvent = async { viewModel.uiEvent.first() }
            runCurrent()

            viewModel.onAction(RouteSettingUiAction.StartNavigationClicked)
            advanceUntilIdle()

            val event = uiEvent.await()
            assertTrue(event is RouteSettingUiEvent.StartNavigationRequested)
            val request = (event as RouteSettingUiEvent.StartNavigationRequested).request
            val selectionHandoff = requireNotNull(request.selectionHandoff)
            assertEquals(1, routeRepository.freshWalkSearchCount)
            assertEquals(
                listOf(
                    "initial_SAFE" to "walk-search-initial",
                    "fresh_SAFE" to "walk-search-fresh-1",
                ),
                routeRepository.selectRequests,
            )
            assertEquals("walk-search-fresh-1", selectionHandoff.searchId)
            assertEquals("fresh_SAFE", selectionHandoff.routeId)
            assertEquals("session-fresh_SAFE", selectionHandoff.sessionId)
            assertTrue(viewModel.uiState.value.ctaAcknowledged)
        }

    @Test
    fun `start action resets route flow when fresh select also returns expired search`() =
        runTest {
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(testDestination())
                }
            val routeRepository = ExpiredSelectRecoveryRouteRepository(expireFreshSelect = true)
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = routeRepository,
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            val uiEvent = async { viewModel.uiEvent.first() }
            runCurrent()

            viewModel.onAction(RouteSettingUiAction.StartNavigationClicked)
            advanceUntilIdle()

            assertEquals(RouteSettingUiEvent.NavigateToMap, uiEvent.await())
            assertEquals(1, routeRepository.freshWalkSearchCount)
            assertEquals(
                listOf(
                    "initial_SAFE" to "walk-search-initial",
                    "fresh_SAFE" to "walk-search-fresh-1",
                ),
                routeRepository.selectRequests,
            )
            assertEquals(null, destinationSelectionRepository.selectedDestination.value)
            assertEquals(null, viewModel.uiState.value.selectedRoute)
            assertFalse(viewModel.uiState.value.ctaAcknowledged)
        }

    @Test
    fun `start action clears manual origin for the next fresh route search without changing current handoff`() =
        runTest {
            val manualOrigin =
                PlaceDestination(
                    placeId = "manual-origin",
                    name = "Manual Origin",
                    address = "부산 강서구 녹산산단335로 7",
                    latitude = 35.1796,
                    longitude = 129.0756,
                    category = PlaceCategory.OTHER,
                )
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedOrigin(manualOrigin)
                    updateSelectedDestination(testDestination())
                }
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = testRouteRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            val uiEvent = async { viewModel.uiEvent.first() }
            runCurrent()

            viewModel.onAction(RouteSettingUiAction.StartNavigationClicked)
            advanceUntilIdle()

            val event = uiEvent.await()
            assertTrue(event is RouteSettingUiEvent.StartNavigationRequested)
            val request = (event as RouteSettingUiEvent.StartNavigationRequested).request
            assertEquals(manualOrigin.latitude, request.origin.coordinate.latitude, 0.0)
            assertEquals(manualOrigin.longitude, request.origin.coordinate.longitude, 0.0)
            assertEquals(null, destinationSelectionRepository.selectedOrigin.value)
            assertTrue(viewModel.uiState.value.ctaAcknowledged)
        }

    @Test
    fun `start action saves direct destination as recent before emitting navigation handoff`() =
        runTest {
            val destination = testDestination()
            val destinationSelectionRepository =
                InMemoryDestinationSelectionRepository().apply {
                    updateSelectedDestination(destination)
                }
            val saveGate = CompletableDeferred<Unit>()
            val searchRepository = FakeSearchRepository(saveGate = saveGate)
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = testRouteRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    searchRepository = searchRepository,
                )

            advanceUntilIdle()
            val uiEvent = async { viewModel.uiEvent.first() }
            runCurrent()

            viewModel.onAction(RouteSettingUiAction.StartNavigationClicked)
            runCurrent()

            assertEquals(1, searchRepository.pendingSaveCount)
            assertTrue(searchRepository.savedRecentDestinations.isEmpty())
            assertFalse(uiEvent.isCompleted)

            saveGate.complete(Unit)
            advanceUntilIdle()

            val savedRecentDestination = searchRepository.savedRecentDestinations.single()
            assertEquals(destination.placeId, savedRecentDestination.placeId)
            assertEquals(destination.name, savedRecentDestination.name)
            assertEquals(destination.address, savedRecentDestination.address)
            assertEquals(destination.latitude, savedRecentDestination.latitude, 0.0)
            assertEquals(destination.longitude, savedRecentDestination.longitude, 0.0)
            assertEquals(destination.category, savedRecentDestination.category)
            assertTrue(savedRecentDestination.searchedAtMillis > 0L)
            assertTrue(uiEvent.await() is RouteSettingUiEvent.StartNavigationRequested)
        }

    @Test
    fun `binding a route detail request hydrates detail state and reuses the same navigation request on start`() =
        runTest {
            val request = testDetailRouteNavigationRequest()
            val viewModel =
                RouteSettingViewModel(
                    routeRepository = failIfCalledRouteRepository(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                )

            advanceUntilIdle()
            val uiEvent = async { viewModel.uiEvent.first() }

            viewModel.bindRouteDetailRequest(request)
            advanceUntilIdle()

            val uiState = viewModel.uiState.value
            assertFalse(uiState.isLoading)
            assertEquals(RouteOption.SHORTEST, uiState.selectedOption)
            assertEquals(RouteOption.SHORTEST, uiState.selectedRoute?.routeOption)
            assertEquals("Stored detail route", uiState.selectedRoute?.title)
            assertEquals(RoutePreviewMapStatus.READY, uiState.routePreviewMap.status)
            assertEquals(request.selectedRoute.preview.polyline.points, uiState.routePreviewMap.polyline)

            viewModel.onAction(RouteSettingUiAction.StartNavigationClicked)
            advanceUntilIdle()

            val event = uiEvent.await()
            assertTrue(event is RouteSettingUiEvent.StartNavigationRequested)
            val startedRequest = (event as RouteSettingUiEvent.StartNavigationRequested).request
            assertEquals(request.selectedRoute.serverRouteId, startedRequest.selectedRoute.serverRouteId)
            assertEquals(request.selectionHandoff?.sessionId, startedRequest.selectionHandoff?.sessionId)
        }
}

private fun testDetailRouteNavigationRequest(): RouteNavigationRequest =
    RouteNavigationRequest(
        origin =
            RouteWaypoint(
                name = "Detail Origin",
                coordinate = GeoCoordinate(35.1796, 129.0756),
            ),
        destination =
            RouteWaypoint(
                name = "Detail Destination",
                coordinate = GeoCoordinate(35.1808, 129.0822),
            ),
        selectedRoute =
            RouteCandidate(
                serverRouteId = "detail-route-1",
                routeOption = RouteOption.SHORTEST,
                title = "Stored detail route",
                summary =
                    RouteSummary(
                        distanceMeters = 540,
                        estimatedTimeMinutes = 11,
                        riskLevel = RouteRiskLevel.MEDIUM,
                        durationSeconds = 660,
                    ),
                preview =
                    RoutePreviewModel(
                        polyline =
                            RoutePolyline(
                                points =
                                    listOf(
                                        GeoCoordinate(35.1796, 129.0756),
                                        GeoCoordinate(35.1802, 129.0790),
                                        GeoCoordinate(35.1808, 129.0822),
                                    ),
                            ),
                        segmentCount = 2,
                        renderableSegmentCount = 2,
                    ),
                segments =
                    listOf(
                        RouteSegment(
                            sequence = 1,
                            polyline =
                                RoutePolyline(
                                    points =
                                        listOf(
                                            GeoCoordinate(35.1796, 129.0756),
                                            GeoCoordinate(35.1802, 129.0790),
                                        ),
                                ),
                            distanceMeters = 260,
                            guidanceMessage = "Walk straight",
                        ),
                        RouteSegment(
                            sequence = 2,
                            polyline =
                                RoutePolyline(
                                    points =
                                        listOf(
                                            GeoCoordinate(35.1802, 129.0790),
                                            GeoCoordinate(35.1808, 129.0822),
                                        ),
                                ),
                            distanceMeters = 280,
                            guidanceMessage = "Arrive at destination",
                        ),
                    ),
            ),
        source = RouteSearchSource.serverApi(label = "Saved route detail"),
        selectionHandoff =
            RouteNavigationSelectionHandoff(
                searchId = "detail-search-1",
                routeId = "detail-route-1",
                sessionId = "detail-session-1",
                initialRemainingDistanceMeters = 540,
                initialRemainingDurationSeconds = 660,
            ),
    )

private fun testRouteRepository(): RouteRepository {
    val delegate =
        DefaultRouteRepository(
            localDataSource = RouteLocalDataSource(),
            remoteDataSource =
                testRouteRemoteDataSource { request ->
                    MockRouteFixtures.searchRoutes(request)
                },
            routeMappingDispatcher = Dispatchers.Unconfined,
        )
    return object : BaseTestRouteRepository() {
        override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
            delegate.getRouteSearchData(query).withSafeWalkDistance(distanceMeters = 720)

        override suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData =
            buildTransitSearchData(
                query = query,
                searchId = "transit-search-1",
            )

        override suspend fun selectRoute(
            routeId: String,
            searchId: String,
        ): RouteSessionData =
            RouteSessionData(
                sessionId = "session-$routeId",
                totalDistanceMeters = 2500,
                totalDurationSeconds = 900,
            )
    }
}

private fun testRouteRemoteDataSource(
    responseProvider: suspend (RouteSearchRequestDto) -> RouteSearchResponseDto,
): RouteRemoteDataSource =
    object : RouteRemoteDataSource(
        postRequestExecutor = { _, _, _ ->
            error("viewmodel tests override searchWalkRoutes directly")
        },
    ) {
        override suspend fun searchWalkRoutes(request: RouteSearchRequestDto): RouteSearchResponseDto =
            responseProvider(request)
    }

private fun testDestination(): PlaceDestination =
    PlaceDestination(
        placeId = "place-1",
        name = "카페 온도",
        address = "부산 강서구 녹산산단321로 24-8",
        latitude = 35.1797,
        longitude = 129.0750,
        category = PlaceCategory.RESTAURANT,
    )

private fun invalidDestination(): PlaceDestination =
    PlaceDestination(
        placeId = "place-invalid",
        name = "좌표 누락 목적지",
        address = "알 수 없는 위치",
        latitude = Double.NaN,
        longitude = 129.0750,
        category = PlaceCategory.OTHER,
    )

private class FakeSearchRepository(
    private val saveGate: CompletableDeferred<Unit>? = null,
) : SearchRepository {
    val savedRecentDestinations = mutableListOf<RecentDestination>()
    var pendingSaveCount: Int = 0
        private set

    override suspend fun search(query: SearchQuery): List<SearchResult> = emptyList()

    override suspend fun getRecentSearches(): List<RecentSearch> = emptyList()

    override suspend fun saveRecentSearch(keyword: String) = Unit

    override suspend fun getRecentDestinations(): List<RecentDestination> = savedRecentDestinations

    override suspend fun saveRecentDestination(destination: RecentDestination) {
        pendingSaveCount += 1
        saveGate?.await()
        savedRecentDestinations += destination
    }
}

private val DEFAULT_TEST_ORIGIN_COORDINATE = GeoCoordinate(latitude = 35.1796, longitude = 129.0756)

private abstract class BaseTestRouteRepository : RouteRepository {
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

private class ViewModelTestCurrentLocationManager : CurrentLocationManager {
    private val mutableLatestLocation = MutableStateFlow<LocationSnapshot?>(null)

    override val latestLocation = mutableLatestLocation.asStateFlow()

    override fun refreshLatestLocation() = Unit

    override fun startLocationUpdates() = Unit

    override fun stopLocationUpdates() = Unit

    fun updateLocation(snapshot: LocationSnapshot?) {
        mutableLatestLocation.value = snapshot
    }
}

private class FallbackFailureRecoveryRouteRepository : BaseTestRouteRepository() {
    var walkSearchCount: Int = 0
        private set
    var lastWalkQuery: RouteSearchQuery? = null
        private set

    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        walkSearchCount += 1
        lastWalkQuery = query
        return if (query.origin.coordinate == DEFAULT_TEST_ORIGIN_COORDINATE) {
            throw routeApiException(
                failureKind = RouteFailureKind.HTTP_RESPONSE,
                status = "RT4040",
                message = "no walk route for fallback origin",
                httpStatusCode = 404,
            )
        } else {
            buildWalkSearchData(
                query = query,
                searchId = "walk-search-$walkSearchCount",
                safeDistanceMeters = 720,
            )
        }
    }
}

private class TransitModeRecordingRouteRepository(
    private val walkSafeDistanceMeters: Int,
    private val omitFirstTransitWalkSegmentPolyline: Boolean = false,
) : BaseTestRouteRepository() {
    var walkSearchCount: Int = 0
        private set
    var freshWalkSearchCount: Int = 0
        private set
    var transitSearchCount: Int = 0
        private set
    var freshTransitSearchCount: Int = 0
        private set
    var lastTransitSearchId: String? = null
        private set
    var lastSelectedRouteId: String? = null
        private set
    var lastSelectedSearchId: String? = null
        private set

    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        walkSearchCount += 1
        return buildWalkSearchData(
            query = query,
            searchId = "walk-search-$walkSearchCount",
            safeDistanceMeters = walkSafeDistanceMeters,
        )
    }

    override suspend fun getFreshRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        freshWalkSearchCount += 1
        return buildWalkSearchData(
            query = query,
            searchId = "walk-fresh-search-$freshWalkSearchCount",
            safeDistanceMeters = walkSafeDistanceMeters,
        )
    }

    override suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        transitSearchCount += 1
        lastTransitSearchId = "transit-search-$transitSearchCount"
        val searchData =
            buildTransitSearchData(
            query = query,
            searchId = checkNotNull(lastTransitSearchId),
        )
        if (!omitFirstTransitWalkSegmentPolyline) return searchData
        return searchData.copy(
            result =
                searchData.result.copy(
                    routes =
                        searchData.result.routes.map { route ->
                            route.copy(
                                segments =
                                    route.segments.map { segment ->
                                        if (segment.sourceLegSequence == 1) {
                                            segment.copy(polyline = RoutePolyline())
                                        } else {
                                            segment
                                        }
                                    },
                            )
                        },
                ),
        )
    }

    override suspend fun getFreshTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        freshTransitSearchCount += 1
        lastTransitSearchId = "transit-fresh-search-$freshTransitSearchCount"
        return buildTransitSearchData(
            query = query,
            searchId = checkNotNull(lastTransitSearchId),
        )
    }

    override suspend fun selectRoute(
        routeId: String,
        searchId: String,
    ): RouteSessionData {
        lastSelectedRouteId = routeId
        lastSelectedSearchId = searchId
        return RouteSessionData(
            sessionId = "session-$routeId",
            totalDistanceMeters = 2500,
            totalDurationSeconds = 900,
        )
    }
}

private class WalkNoRouteThenFreshSuccessRepository : BaseTestRouteRepository() {
    var walkSearchCount: Int = 0
        private set
    var freshWalkSearchCount: Int = 0
        private set

    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        walkSearchCount += 1
        throw routeApiException(
            failureKind = RouteFailureKind.HTTP_RESPONSE,
            status = "RT4040",
            message = "no walk route",
            httpStatusCode = 404,
        )
    }

    override suspend fun getFreshRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        freshWalkSearchCount += 1
        return buildWalkSearchData(
            query = query,
            searchId = "fresh-walk-search-$freshWalkSearchCount",
            safeDistanceMeters = 720,
        )
    }
}

private class ExpiredSelectRecoveryRouteRepository(
    private val expireFreshSelect: Boolean = false,
) : BaseTestRouteRepository() {
    var walkSearchCount: Int = 0
        private set
    var freshWalkSearchCount: Int = 0
        private set
    val selectRequests = mutableListOf<Pair<String, String>>()

    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        walkSearchCount += 1
        return buildWalkSearchData(
            query = query,
            searchId = "walk-search-initial",
            safeDistanceMeters = 720,
        ).withServerRouteIdPrefix("initial")
    }

    override suspend fun getFreshRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        freshWalkSearchCount += 1
        return buildWalkSearchData(
            query = query,
            searchId = "walk-search-fresh-$freshWalkSearchCount",
            safeDistanceMeters = 720,
        ).withServerRouteIdPrefix("fresh")
    }

    override suspend fun selectRoute(
        routeId: String,
        searchId: String,
    ): RouteSessionData {
        selectRequests += routeId to searchId
        if (selectRequests.size == 1 || expireFreshSelect) {
            throw routeApiException(
                failureKind = RouteFailureKind.HTTP_RESPONSE,
                status = "RT4041",
                message = "검색 결과가 만료되었습니다.",
                httpStatusCode = 404,
            )
        }
        return RouteSessionData(
            sessionId = "session-$routeId",
            totalDistanceMeters = 2500,
            totalDurationSeconds = 900,
        )
    }
}

private class DelayedTransitRouteRepository(
    private val walkSafeDistanceMeters: Int,
) : BaseTestRouteRepository() {
    var walkSearchCount: Int = 0
        private set
    var transitSearchCount: Int = 0
        private set

    private val transitResult = CompletableDeferred<Result<RouteSearchData>>()
    private var latestTransitQuery: RouteSearchQuery? = null

    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        walkSearchCount += 1
        return buildWalkSearchData(
            query = query,
            searchId = "walk-search-$walkSearchCount",
            safeDistanceMeters = walkSafeDistanceMeters,
        )
    }

    override suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        transitSearchCount += 1
        latestTransitQuery = query
        return transitResult.await().getOrThrow()
    }

    fun completeTransitSuccess() {
        val query = checkNotNull(latestTransitQuery) { "Transit query was not requested." }
        transitResult.complete(
            Result.success(
                buildTransitSearchData(
                    query = query,
                    searchId = "transit-search-$transitSearchCount",
                ),
            ),
        )
    }
}

private class TransitTimeoutRouteRepository(
    private val walkSafeDistanceMeters: Int,
) : BaseTestRouteRepository() {
    var walkSearchCount: Int = 0
        private set
    var transitSearchCount: Int = 0
        private set

    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        walkSearchCount += 1
        return buildWalkSearchData(
            query = query,
            searchId = "walk-search-$walkSearchCount",
            safeDistanceMeters = walkSafeDistanceMeters,
        )
    }

    override suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        transitSearchCount += 1
        throw routeApiException(
            failureKind = RouteFailureKind.CLIENT_TIMEOUT,
            status = "ROUTE_CLIENT_TIMEOUT",
            message = "temporary timeout",
        )
    }
}

private class MissingSessionTransitRouteRepository(
    private val walkSafeDistanceMeters: Int,
) : BaseTestRouteRepository() {
    var walkSearchCount: Int = 0
        private set
    var transitSearchCount: Int = 0
        private set

    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        walkSearchCount += 1
        return buildWalkSearchData(
            query = query,
            searchId = "walk-search-$walkSearchCount",
            safeDistanceMeters = walkSafeDistanceMeters,
        )
    }

    override suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        transitSearchCount += 1
        throw routeApiException(
            failureKind = RouteFailureKind.HTTP_RESPONSE,
            status = "ROUTE_AUTH_MISSING_SESSION",
            message = "인증이 필요합니다.",
            httpStatusCode = 401,
        )
    }
}

private fun routeApiException(
    failureKind: RouteFailureKind,
    status: String,
    message: String,
    httpStatusCode: Int = 0,
): RouteApiException =
    RouteApiException(
        httpStatusCode = httpStatusCode,
        status = status,
        message = message,
        failureKind = failureKind,
    )

private fun RouteSearchData.withSafeWalkDistance(distanceMeters: Int): RouteSearchData =
    copy(
        result =
            result.copy(
                routes =
                    routes.map { route ->
                        if (route.routeOption == RouteOption.SAFE) {
                            route.copy(summary = route.summary.copy(distanceMeters = distanceMeters))
                        } else {
                            route
                        }
                    },
            ),
    )

private fun RouteSearchData.withServerRouteIdPrefix(prefix: String): RouteSearchData =
    copy(
        result =
            result.copy(
                routes =
                    routes.map { route ->
                        val routeId = "${prefix}_${route.routeOption.name}"
                        route.copy(
                            routeId = routeId,
                            serverRouteId = routeId,
                        )
                    },
            ),
    )

private fun buildWalkSearchData(
    query: RouteSearchQuery,
    searchId: String,
    safeDistanceMeters: Int,
): RouteSearchData =
    RouteSearchData(
        query = query,
        result =
            RouteSearchResult(
                origin = query.origin,
                destination = query.destination,
                searchId = searchId,
                routes =
                    listOf(
                        buildRouteCandidate(
                            routeOption = RouteOption.SAFE,
                            title = "Safe Route",
                            routeId = "walk_rt_safe_001",
                            transportMode = RouteTransportMode.WALK,
                            distanceMeters = safeDistanceMeters,
                            estimatedTimeMinutes = 16,
                            riskLevel = RouteRiskLevel.LOW,
                        ),
                        buildRouteCandidate(
                            routeOption = RouteOption.SHORTEST,
                            title = "Shortest Route",
                            routeId = "walk_rt_shortest_001",
                            transportMode = RouteTransportMode.WALK,
                            distanceMeters = 640,
                            estimatedTimeMinutes = 14,
                            riskLevel = RouteRiskLevel.MEDIUM,
                        ),
                    ),
            ),
        source = RouteSearchSource.serverApi(label = "Walk route payload"),
    )

private fun buildTransitSearchData(
    query: RouteSearchQuery,
    searchId: String,
): RouteSearchData =
    RouteSearchData(
        query = query,
        result =
            RouteSearchResult(
                origin = query.origin,
                destination = query.destination,
                searchId = searchId,
                routes =
                    listOf(
                        buildRouteCandidate(
                            routeOption = RouteOption.RECOMMENDED,
                            title = "Transit Recommended",
                            routeId = "pt_rt_recommended_001",
                            transportMode = RouteTransportMode.PUBLIC_TRANSIT,
                            distanceMeters = 4200,
                            estimatedTimeMinutes = 28,
                            riskLevel = RouteRiskLevel.LOW,
                        ),
                        buildRouteCandidate(
                            routeOption = RouteOption.MIN_TRANSFER,
                            title = "Transit Min Transfer",
                            routeId = "pt_rt_min_transfer_001",
                            transportMode = RouteTransportMode.PUBLIC_TRANSIT,
                            distanceMeters = 4380,
                            estimatedTimeMinutes = 30,
                            riskLevel = RouteRiskLevel.MEDIUM,
                        ),
                        buildRouteCandidate(
                            routeOption = RouteOption.MIN_WALK,
                            title = "Transit Min Walk",
                            routeId = "pt_rt_min_walk_001",
                            transportMode = RouteTransportMode.PUBLIC_TRANSIT,
                            distanceMeters = 4520,
                            estimatedTimeMinutes = 31,
                            riskLevel = RouteRiskLevel.LOW,
                        ),
                    ),
            ),
        source = RouteSearchSource.serverApi(label = "Transit route payload"),
    )

private fun buildRouteCandidate(
    routeOption: RouteOption,
    title: String,
    routeId: String,
    transportMode: RouteTransportMode,
    distanceMeters: Int,
    estimatedTimeMinutes: Int,
    riskLevel: RouteRiskLevel,
): RouteCandidate {
    if (transportMode == RouteTransportMode.PUBLIC_TRANSIT) {
        return buildTransitRouteCandidate(
            routeOption = routeOption,
            title = title,
            routeId = routeId,
            distanceMeters = distanceMeters,
            estimatedTimeMinutes = estimatedTimeMinutes,
            riskLevel = riskLevel,
        )
    }

    val previewPoints =
        listOf(
            GeoCoordinate(35.1796, 129.0756),
            GeoCoordinate(35.1768, 129.0714),
            GeoCoordinate(35.1734, 129.0641),
        )
    return RouteCandidate(
        routeId = routeId,
        serverRouteId = routeId,
        transportMode = transportMode,
        routeOption = routeOption,
        title = title,
        summary =
            RouteSummary(
                distanceMeters = distanceMeters,
                estimatedTimeMinutes = estimatedTimeMinutes,
                riskLevel = riskLevel,
            ),
        preview =
            RoutePreviewModel(
                polyline = RoutePolyline(points = previewPoints),
                segmentCount = 2,
                renderableSegmentCount = 2,
                fallbackSegmentCount = 0,
            ),
        segments =
            listOf(
                RouteSegment(
                    sequence = 1,
                    polyline = RoutePolyline(points = previewPoints.take(2)),
                    distanceMeters = distanceMeters / 2,
                    guidanceMessage = "Start on the selected route.",
                ),
                RouteSegment(
                    sequence = 2,
                    polyline = RoutePolyline(points = previewPoints.drop(1)),
                    distanceMeters = distanceMeters - (distanceMeters / 2),
                    guidanceMessage = "Continue to the destination.",
                ),
            ),
    )
}

private fun buildTransitRouteCandidate(
    routeOption: RouteOption,
    title: String,
    routeId: String,
    distanceMeters: Int,
    estimatedTimeMinutes: Int,
    riskLevel: RouteRiskLevel,
): RouteCandidate {
    val previewPoints =
        listOf(
            GeoCoordinate(35.1796, 129.0756),
            GeoCoordinate(35.1788, 129.0738),
            GeoCoordinate(35.1776, 129.0708),
            GeoCoordinate(35.1748, 129.0658),
            GeoCoordinate(35.1726, 129.0614),
        )
    val busBoardingStop =
        RouteTransitStop(
            name = "시청 정류장",
            coordinate = previewPoints[1],
        )
    val busAlightingStop =
        RouteTransitStop(
            name = "서면 정류장",
            coordinate = previewPoints[2],
        )
    val subwayBoardingStop =
        RouteTransitStop(
            name = "시청역",
            coordinate = previewPoints[2],
        )
    val subwayAlightingStop =
        RouteTransitStop(
            name = "전포역",
            coordinate = previewPoints[3],
        )

    return RouteCandidate(
        routeId = routeId,
        serverRouteId = routeId,
        transportMode = RouteTransportMode.PUBLIC_TRANSIT,
        routeOption = routeOption,
        title = title,
        summary =
            RouteSummary(
                distanceMeters = distanceMeters,
                estimatedTimeMinutes = estimatedTimeMinutes,
                riskLevel = riskLevel,
            ),
        preview =
            RoutePreviewModel(
                polyline = RoutePolyline(points = previewPoints),
                segmentCount = 4,
                renderableSegmentCount = 4,
                fallbackSegmentCount = 0,
            ),
        legs =
            listOf(
                RouteLeg(
                    sequence = 1,
                    type = RouteLegType.WALK,
                    role = RouteLegRole.WALK_TO_TRANSIT,
                    polyline = RoutePolyline(points = previewPoints.take(2)),
                ),
                RouteLeg(
                    sequence = 2,
                    type = RouteLegType.BUS,
                    role = RouteLegRole.TRANSIT,
                    routeNo = "1001",
                    boardingStop = busBoardingStop,
                    alightingStop = busAlightingStop,
                    polyline = RoutePolyline(points = previewPoints.slice(1..2)),
                ),
                RouteLeg(
                    sequence = 3,
                    type = RouteLegType.SUBWAY,
                    role = RouteLegRole.TRANSIT,
                    routeNo = "2호선",
                    boardingStop = subwayBoardingStop,
                    alightingStop = subwayAlightingStop,
                    polyline = RoutePolyline(points = previewPoints.slice(2..3)),
                ),
                RouteLeg(
                    sequence = 4,
                    type = RouteLegType.WALK,
                    role = RouteLegRole.WALK_TO_DESTINATION,
                    polyline = RoutePolyline(points = previewPoints.drop(3)),
                ),
            ),
        segments =
            listOf(
                RouteSegment(
                    sequence = 1,
                    polyline = RoutePolyline(points = previewPoints.take(2)),
                    distanceMeters = 180,
                    guidanceMessage = "정류장 방향으로 직진하세요.",
                    sourceLegSequence = 1,
                ),
                RouteSegment(
                    sequence = 2,
                    polyline = RoutePolyline(points = previewPoints.slice(1..2)),
                    distanceMeters = 1_400,
                    guidanceMessage = "Next transit segment",
                    sourceLegSequence = 2,
                ),
                RouteSegment(
                    sequence = 3,
                    polyline = RoutePolyline(points = previewPoints.slice(2..3)),
                    distanceMeters = 2_200,
                    guidanceMessage = "Next transit segment",
                    sourceLegSequence = 3,
                ),
                RouteSegment(
                    sequence = 4,
                    polyline = RoutePolyline(points = previewPoints.drop(3)),
                    distanceMeters = 120,
                    guidanceMessage = "목적지 방향으로 직진하세요.",
                    sourceLegSequence = 4,
                ),
            ),
    )
}

private fun partialRouteRepository(): RouteRepository =
    object : BaseTestRouteRepository() {
        override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
            RouteSearchData(
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
                                            distanceMeters = 0,
                                            estimatedTimeMinutes = 0,
                                            riskLevel = RouteRiskLevel.MEDIUM,
                                        ),
                                    preview =
                                        RoutePreviewModel(
                                            polyline = RoutePolyline(),
                                            segmentCount = 2,
                                            renderableSegmentCount = 0,
                                            fallbackSegmentCount = 2,
                                        ),
                                    segments =
                                        listOf(
                                            RouteSegment(
                                                sequence = 1,
                                                guidanceMessage = "",
                                            ),
                                            RouteSegment(
                                                sequence = 2,
                                                guidanceMessage = " ",
                                            ),
                                        ),
                                ),
                            ),
                    ),
                source =
                    RouteSearchSource.serverApi(
                        label = "Partial route payload",
                    ),
            )
    }

private fun emptyRouteRepository(): RouteRepository =
    object : BaseTestRouteRepository() {
        override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
            RouteSearchData(
                query = query,
                result =
                    RouteSearchResult(
                        origin = RouteWaypoint(name = "현재 위치", coordinate = GeoCoordinate(35.1796, 129.0756)),
                        destination = RouteWaypoint(name = "부산역", coordinate = GeoCoordinate(35.1151, 129.0414)),
                        routes = emptyList(),
                    ),
                source =
                    RouteSearchSource.serverApi(
                        label = "Empty route payload",
                    ),
            )
    }

private fun failingRouteRepository(): RouteRepository =
    object : BaseTestRouteRepository() {
        override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
            error("route load failed")
    }

private fun routeApiFailingRepository(
    failure: RouteApiException,
): RouteRepository =
    object : BaseTestRouteRepository() {
        override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
            throw failure
    }

private fun failIfCalledRouteRepository(): RouteRepository =
    object : BaseTestRouteRepository() {
        override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
            error("Route search should have been blocked before repository access")
    }

private fun directionalRouteRepository(): RouteRepository =
    object : BaseTestRouteRepository() {
        override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
            RouteSearchData(
                query = query,
                result =
                    RouteSearchResult(
                        origin = query.origin,
                        destination = query.destination,
                        routes =
                            listOf(
                                RouteCandidate(
                                    routeOption = RouteOption.SAFE,
                                    title = "Directional Route",
                                    summary =
                                        RouteSummary(
                                            distanceMeters = 410,
                                            estimatedTimeMinutes = 8,
                                            riskLevel = RouteRiskLevel.LOW,
                                        ),
                                    preview =
                                        RoutePreviewModel(
                                            polyline =
                                                RoutePolyline(
                                                    points =
                                                        listOf(
                                                            query.origin.coordinate,
                                                            GeoCoordinate(35.17965, 129.07555),
                                                            GeoCoordinate(35.17982, 129.07572),
                                                            query.destination.coordinate,
                                                        ),
                                                ),
                                            segmentCount = 4,
                                            renderableSegmentCount = 4,
                                            fallbackSegmentCount = 0,
                                        ),
                                    segments =
                                        listOf(
                                            RouteSegment(
                                                sequence = 1,
                                                distanceMeters = 120,
                                                durationFromRouteStartSeconds = 0,
                                                guidanceMessage = "직진 120m 구간입니다.",
                                            ),
                                            RouteSegment(
                                                sequence = 2,
                                                distanceMeters = 80,
                                                durationFromRouteStartSeconds = 0,
                                                guidanceMessage = "좌회전 후 80m 이동하세요.",
                                            ),
                                            RouteSegment(
                                                sequence = 3,
                                                distanceMeters = 60,
                                                durationFromRouteStartSeconds = 0,
                                                safetyFlags =
                                                    RouteSegmentSafetyFlags(
                                                        hasCrosswalk = true,
                                                        hasSignal = true,
                                                        hasAudioSignal = true,
                                                    ),
                                                guidanceMessage = "횡단보도로 이동하세요.",
                                            ),
                                            RouteSegment(
                                                sequence = 4,
                                                distanceMeters = 150,
                                                durationFromRouteStartSeconds = 0,
                                                guidanceMessage = "우회전 후 목적지 방향으로 이동하세요.",
                                            ),
                                        ),
                                ),
                            ),
                    ),
                source =
                    RouteSearchSource.serverApi(
                        label = "Directional route payload",
                    ),
            )
    }

private fun crosswalkMissingDistanceRouteRepository(): RouteRepository =
    object : BaseTestRouteRepository() {
        override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
            RouteSearchData(
                query = query,
                result =
                    RouteSearchResult(
                        origin = query.origin,
                        destination = query.destination,
                        routes =
                            listOf(
                                RouteCandidate(
                                    routeOption = RouteOption.SAFE,
                                    title = "Crosswalk Pending Route",
                                    summary =
                                        RouteSummary(
                                            distanceMeters = 80,
                                            estimatedTimeMinutes = 2,
                                            riskLevel = RouteRiskLevel.LOW,
                                        ),
                                    preview =
                                        RoutePreviewModel(
                                            polyline =
                                                RoutePolyline(
                                                    points =
                                                        listOf(
                                                            query.origin.coordinate,
                                                            GeoCoordinate(35.17965, 129.07555),
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
                                                distanceMeters = 0,
                                                safetyFlags =
                                                    RouteSegmentSafetyFlags(
                                                        hasCrosswalk = true,
                                                    ),
                                                guidanceMessage = "",
                                            ),
                                        ),
                                ),
                            ),
                    ),
                source =
                    RouteSearchSource.serverApi(
                        label = "Crosswalk pending route payload",
                    ),
            )
    }

private class CountingRouteRepository : BaseTestRouteRepository() {
    var callCount: Int = 0
        private set

    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        callCount += 1
        val countLabel = callCount
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
                                title = "Safe Route #$countLabel",
                                summary =
                                    RouteSummary(
                                        distanceMeters = 700 + countLabel,
                                        estimatedTimeMinutes = 15 + countLabel,
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
                                            guidanceMessage = "Safe guidance #$countLabel",
                                        ),
                                    ),
                            ),
                            RouteCandidate(
                                routeOption = RouteOption.SHORTEST,
                                title = "Shortest Route #$countLabel",
                                summary =
                                    RouteSummary(
                                        distanceMeters = 620 + countLabel,
                                        estimatedTimeMinutes = 13 + countLabel,
                                        riskLevel = RouteRiskLevel.MEDIUM,
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
                                            guidanceMessage = "Shortest guidance #$countLabel",
                                        ),
                                    ),
                            ),
                        ),
                ),
            source =
                RouteSearchSource.serverApi(
                    label = "Counting route payload #$countLabel",
                ),
        )
    }
}
