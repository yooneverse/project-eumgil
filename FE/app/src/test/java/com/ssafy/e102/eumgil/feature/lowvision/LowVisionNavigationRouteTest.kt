package com.ssafy.e102.eumgil.feature.lowvision

import com.ssafy.e102.eumgil.core.model.PlaceDestination
import com.ssafy.e102.eumgil.core.model.RoutePolyline
import com.ssafy.e102.eumgil.core.model.RoutePreviewModel
import com.ssafy.e102.eumgil.core.model.RouteWaypoint
import com.ssafy.e102.eumgil.core.model.RouteCandidate
import com.ssafy.e102.eumgil.core.model.RouteSearchData
import com.ssafy.e102.eumgil.core.model.RouteSearchQuery
import com.ssafy.e102.eumgil.core.model.RouteSearchResult
import com.ssafy.e102.eumgil.core.model.RouteSearchSource
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.core.model.RouteRiskLevel
import com.ssafy.e102.eumgil.core.model.RouteSegment
import com.ssafy.e102.eumgil.core.model.RouteSummary
import com.ssafy.e102.eumgil.data.repository.InMemoryDestinationSelectionRepository
import com.ssafy.e102.eumgil.data.repository.RouteRatingData
import com.ssafy.e102.eumgil.data.repository.RouteRepository
import com.ssafy.e102.eumgil.data.repository.RouteRerouteData
import com.ssafy.e102.eumgil.data.repository.RouteSessionData
import com.ssafy.e102.eumgil.data.repository.RouteTransitRefreshData
import com.ssafy.e102.eumgil.data.remote.datasource.RouteApiException
import com.ssafy.e102.eumgil.feature.navigation.NavigationUiEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.io.File
import org.junit.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

class LowVisionNavigationRouteTest {
    @Test
    fun `low vision navigation treats exit events as home navigation`() {
        assertEquals(true, shouldNavigateLowVisionHome(NavigationUiEvent.NavigateToMap))
        assertEquals(true, shouldNavigateLowVisionHome(NavigationUiEvent.NavigateToArrival))
    }

    @Test
    fun `low vision navigation ignores non exit navigation events for home`() {
        assertEquals(false, shouldNavigateLowVisionHome(NavigationUiEvent.NavigateBack))
        assertEquals(false, shouldNavigateLowVisionHome(NavigationUiEvent.NavigateToSavedRoute))
        assertEquals(false, shouldNavigateLowVisionHome(NavigationUiEvent.NavigateToRouteDetail(RouteOption.SAFE)))
        assertEquals(false, shouldNavigateLowVisionHome(NavigationUiEvent.ShowToast("Saved")))
    }

    @Test
    fun `low vision navigation route does not start guidance with the default origin when current location is missing`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/lowvision/LowVisionNavigationRoute.kt")
                .readText()

        assertTrue(source.contains("toLowVisionRouteOriginWaypointOrNull()"))
        assertFalse(source.contains(").toLowVisionRouteOriginWaypoint()"))
        assertTrue(source.contains("LOW_VISION_NAVIGATION_LOCATION_REQUIRED_MESSAGE"))
    }

    @Test
    fun `low vision navigation request falls back to local route when route api fails`() =
        runBlocking {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            destinationSelectionRepository.updateSelectedDestination(
                PlaceDestination(
                    placeId = "real-place-id",
                    name = "Real Place",
                    address = "Busan",
                    latitude = 35.2,
                    longitude = 129.2,
                ),
            )

            val request =
                ThrowingRouteRepository()
                    .buildLowVisionNavigationRequest(destinationSelectionRepository)

            assertEquals("low-vision-fallback-route", request?.selectedRoute?.routeId)
            assertEquals(null, request?.selectionHandoff)
            assertTrue(request?.selectedRoute?.segments?.isNotEmpty() == true)
        }

    @Test
    fun `low vision fallback route measures distance from selected destination coordinates`() =
        runBlocking {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            destinationSelectionRepository.updateSelectedDestination(
                PlaceDestination(
                    placeId = "provider:kakao:unknown-place",
                    name = "Unknown Provider Place",
                    address = "Outside internal list",
                    latitude = 35.006,
                    longitude = 129.0,
                ),
            )
            val origin =
                RouteWaypoint(
                    name = "Current location",
                    address = "Current location",
                    coordinate =
                        com.ssafy.e102.eumgil.core.model.GeoCoordinate(
                            latitude = 35.0,
                            longitude = 129.0,
                        ),
                )

            val request =
                ThrowingRouteRepository()
                    .buildLowVisionNavigationRequest(
                        destinationSelectionRepository = destinationSelectionRepository,
                        origin = origin,
                    )

            assertEquals(35.006, request?.destination?.coordinate?.latitude ?: 0.0, 0.0)
            assertEquals(129.0, request?.destination?.coordinate?.longitude ?: 0.0, 0.0)
            val distanceMeters = request?.selectedRoute?.summary?.distanceMeters ?: 0
            assertTrue("Fallback distance should come from origin and destination coordinates.", distanceMeters in 650..700)
            assertTrue(request?.selectedRoute?.summary?.estimatedTimeMinutes ?: 0 > 0)
        }

    @Test
    fun `low vision navigation request fetches a fresh route search before selecting`() =
        runBlocking {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            destinationSelectionRepository.updateSelectedDestination(
                PlaceDestination(
                    placeId = "real-place-id",
                    name = "Real Place",
                    address = "Busan",
                    latitude = 35.2,
                    longitude = 129.2,
                ),
            )
            val routeRepository = FreshRouteSearchTrackingRepository()

            val request = routeRepository.buildLowVisionNavigationRequest(destinationSelectionRepository)

            assertEquals("fresh-search", request?.selectionHandoff?.searchId)
            assertEquals("fresh-route", request?.selectionHandoff?.routeId)
            assertEquals(950, request?.selectionHandoff?.initialRemainingDistanceMeters)
            assertEquals(960, request?.selectionHandoff?.initialRemainingDurationSeconds)
            assertTrue(routeRepository.freshWalkSearchCalled)
        }

    @Test
    fun `low vision navigation request can select route id when server route id is missing`() =
        runBlocking {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            destinationSelectionRepository.updateSelectedDestination(
                PlaceDestination(
                    placeId = "real-place-id",
                    name = "Real Place",
                    address = "Busan",
                    latitude = 35.2,
                    longitude = 129.2,
                ),
            )
            val routeRepository = RouteIdOnlyRouteSearchRepository()

            val request = routeRepository.buildLowVisionNavigationRequest(destinationSelectionRepository)

            assertEquals("fresh-route", request?.selectionHandoff?.routeId)
            assertEquals("session-1", request?.selectionHandoff?.sessionId)
        }

    @Test
    fun `low vision navigation still returns route request when route select fails`() =
        runBlocking {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            destinationSelectionRepository.updateSelectedDestination(
                PlaceDestination(
                    placeId = "real-place-id",
                    name = "Real Place",
                    address = "Busan",
                    latitude = 35.2,
                    longitude = 129.2,
                ),
            )
            val routeRepository = SelectFailureFallbackRouteRepository()

            val request = routeRepository.buildLowVisionNavigationRequest(destinationSelectionRepository)

            assertEquals("fresh-route", request?.selectedRoute?.serverRouteId)
            assertEquals("fresh-search", routeRepository.lastSearchId)
            assertNull(request?.selectionHandoff)
        }

    @Test
    fun `low vision navigation request returns null when route select search expired`() =
        runBlocking {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            destinationSelectionRepository.updateSelectedDestination(
                PlaceDestination(
                    placeId = "real-place-id",
                    name = "Real Place",
                    address = "Busan",
                    latitude = 35.2,
                    longitude = 129.2,
                ),
            )
            val routeRepository = ExpiredSelectRouteRepository()

            val request = routeRepository.buildLowVisionNavigationRequest(destinationSelectionRepository)

            assertNull(request)
            assertEquals("fresh-search", routeRepository.lastSearchId)
        }

    @Test
    fun `low vision navigation still returns route request when search id is missing`() =
        runBlocking {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            destinationSelectionRepository.updateSelectedDestination(
                PlaceDestination(
                    placeId = "real-place-id",
                    name = "Real Place",
                    address = "Busan",
                    latitude = 35.2,
                    longitude = 129.2,
                ),
            )
            val routeRepository = MissingSearchIdRouteRepository()

            val request = routeRepository.buildLowVisionNavigationRequest(destinationSelectionRepository)

            assertEquals("fresh-route", request?.selectedRoute?.serverRouteId)
            assertNull(request?.selectionHandoff)
        }

    @Test
    fun `low vision navigation repairs incomplete route metrics without synthesizing route segments`() =
        runBlocking {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            destinationSelectionRepository.updateSelectedDestination(
                PlaceDestination(
                    placeId = "real-place-id",
                    name = "Real Place",
                    address = "Busan",
                    latitude = 35.2,
                    longitude = 129.2,
                ),
            )
            val routeRepository = IncompleteFreshRouteRepository()

            val request = routeRepository.buildLowVisionNavigationRequest(destinationSelectionRepository)

            assertEquals("incomplete-route", request?.selectedRoute?.serverRouteId)
            assertTrue(request?.selectedRoute?.summary?.distanceMeters ?: 0 > 0)
            assertTrue(request?.selectedRoute?.summary?.estimatedTimeMinutes ?: 0 > 0)
            assertTrue(request?.selectedRoute?.summary?.durationSeconds ?: 0 > 0)
            assertTrue(request?.selectedRoute?.segments?.isEmpty() == true)
            assertTrue(request?.selectedRoute?.previewPolyline?.isRenderable == true)
            assertTrue(request?.selectionHandoff?.initialRemainingDistanceMeters ?: 0 > 0)
            assertTrue(request?.selectionHandoff?.initialRemainingDurationSeconds ?: 0 > 0)
        }

    @Test
    fun `low vision navigation keeps missing route segment elements empty`() =
        runBlocking {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            destinationSelectionRepository.updateSelectedDestination(
                PlaceDestination(
                    placeId = "real-place-id",
                    name = "Real Place",
                    address = "Busan",
                    latitude = 35.2,
                    longitude = 129.2,
                ),
            )
            val routeRepository = IncompleteFreshRouteRepository()

            val request = routeRepository.buildLowVisionNavigationRequest(destinationSelectionRepository)
            assertTrue(request?.selectedRoute?.segments?.isEmpty() == true)
        }

    @Test
    fun `low vision navigation falls back to walk route when transit search fails`() =
        runBlocking {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            destinationSelectionRepository.updateSelectedDestination(
                PlaceDestination(
                    placeId = "far-place-id",
                    name = "Far Place",
                    address = "Busan",
                    latitude = 35.22,
                    longitude = 129.09,
                ),
            )
            val routeRepository = TransitFailureFallbackRouteRepository()

            val request = routeRepository.buildLowVisionNavigationRequest(destinationSelectionRepository)

            assertEquals("walk-route", request?.selectionHandoff?.routeId)
            assertEquals("fresh-walk-search", request?.selectionHandoff?.searchId)
            assertTrue(routeRepository.freshTransitSearchCalled)
        }

    @Test
    fun `low vision navigation request uses current location origin when provided`() =
        runBlocking {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            destinationSelectionRepository.updateSelectedDestination(
                PlaceDestination(
                    placeId = "near-place-id",
                    name = "Near Place",
                    address = "Busan",
                    latitude = 35.164,
                    longitude = 129.164,
                ),
            )
            val currentOrigin =
                RouteWaypoint(
                    name = "Current location",
                    address = "Current address",
                    coordinate = com.ssafy.e102.eumgil.core.model.GeoCoordinate(
                        latitude = 35.163,
                        longitude = 129.163,
                    ),
                )
            val routeRepository = OriginTrackingRouteRepository()

            routeRepository.buildLowVisionNavigationRequest(
                destinationSelectionRepository = destinationSelectionRepository,
                origin = currentOrigin,
            )

            assertEquals(currentOrigin.coordinate, routeRepository.lastWalkQuery?.origin?.coordinate)
        }

    @Test
    fun `low vision navigation request does not build a route when destination is missing`() =
        runBlocking {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val currentOrigin =
                RouteWaypoint(
                    name = "Current location",
                    address = "Current address",
                    coordinate = com.ssafy.e102.eumgil.core.model.GeoCoordinate(
                        latitude = 35.163,
                        longitude = 129.163,
                    ),
                )
            val routeRepository = OriginTrackingRouteRepository()

            val request =
                routeRepository.buildLowVisionNavigationRequest(
                    destinationSelectionRepository = destinationSelectionRepository,
                    origin = currentOrigin,
                )

            assertNull(request)
            assertNull(routeRepository.lastWalkQuery)
        }

    @Test
    fun `low vision navigation falls back to default origin when current origin route search fails`() =
        runBlocking {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            destinationSelectionRepository.updateSelectedDestination(
                PlaceDestination(
                    placeId = "near-place-id",
                    name = "Near Place",
                    address = "Busan",
                    latitude = 35.164,
                    longitude = 129.164,
                ),
            )
            val currentOrigin =
                RouteWaypoint(
                    name = "Current location",
                    address = "Current address",
                    coordinate = com.ssafy.e102.eumgil.core.model.GeoCoordinate(
                        latitude = 37.5665,
                        longitude = 126.9780,
                    ),
                )
            val routeRepository = FallbackOriginRouteRepository(currentOrigin = currentOrigin)

            val request =
                routeRepository.buildLowVisionNavigationRequest(
                    destinationSelectionRepository = destinationSelectionRepository,
                    origin = currentOrigin,
                )

            assertEquals(2, routeRepository.walkQueries.size)
            assertEquals(currentOrigin.coordinate, routeRepository.walkQueries.first().origin.coordinate)
            assertTrue(routeRepository.walkQueries.last().origin.coordinate != currentOrigin.coordinate)
            assertEquals("fallback-search", request?.selectionHandoff?.searchId)
        }

    @Test
    fun `low vision navigation request preserves coroutine cancellation`() =
        runBlocking {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            destinationSelectionRepository.updateSelectedDestination(
                PlaceDestination(
                    placeId = "real-place-id",
                    name = "Real Place",
                    address = "Busan",
                    latitude = 35.2,
                    longitude = 129.2,
                ),
            )

            try {
                CancellationRouteRepository()
                    .buildLowVisionNavigationRequest(destinationSelectionRepository)
                fail("CancellationException should be rethrown")
            } catch (_: CancellationException) {
                // Expected cancellation should not be converted into a route load failure.
            }
        }
}

private class ThrowingRouteRepository : RouteRepository {
    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        throw IllegalStateException("route api failed")

    override suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        throw IllegalStateException("transit route api failed")

    override suspend fun selectRoute(
        routeId: String,
        searchId: String,
    ): RouteSessionData = throw IllegalStateException("select route failed")

    override suspend fun refreshTransit(
        routeId: String,
        legSequence: Int,
    ): RouteTransitRefreshData = throw IllegalStateException("refresh failed")

    override suspend fun reroute(
        routeId: String,
        currentPoint: com.ssafy.e102.eumgil.core.model.GeoCoordinate,
    ): RouteRerouteData = throw IllegalStateException("reroute failed")

    override suspend fun endRoute(routeId: String): RouteSessionData =
        throw IllegalStateException("end route failed")

    override suspend fun rateRoute(
        sessionId: String,
        score: Int,
    ): RouteRatingData = throw IllegalStateException("rating failed")
}

private class FreshRouteSearchTrackingRepository : RouteRepository {
    var freshWalkSearchCalled: Boolean = false

    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("cached route search should not be used for low vision navigation start")

    override suspend fun getFreshRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        freshWalkSearchCalled = true
        return lowVisionRouteSearchData(
            query = query,
            searchId = "fresh-search",
            routeId = "fresh-route",
            distanceMeters = 120.0,
        )
    }

    override suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("transit route search was not expected")

    override suspend fun selectRoute(
        routeId: String,
        searchId: String,
    ): RouteSessionData {
        assertEquals("fresh-route", routeId)
        assertEquals("fresh-search", searchId)
        return RouteSessionData(
            sessionId = "session-1",
            totalDistanceMeters = 950,
            totalDurationSeconds = 960,
        )
    }

    override suspend fun refreshTransit(
        routeId: String,
        legSequence: Int,
    ): RouteTransitRefreshData = throw IllegalStateException("refresh failed")

    override suspend fun reroute(
        routeId: String,
        currentPoint: com.ssafy.e102.eumgil.core.model.GeoCoordinate,
    ): RouteRerouteData = throw IllegalStateException("reroute failed")

    override suspend fun endRoute(routeId: String): RouteSessionData =
        throw IllegalStateException("end route failed")

    override suspend fun rateRoute(
        sessionId: String,
        score: Int,
    ): RouteRatingData = throw IllegalStateException("rating failed")
}

private class RouteIdOnlyRouteSearchRepository : RouteRepository {
    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("cached route search should not be used for low vision navigation start")

    override suspend fun getFreshRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        lowVisionRouteSearchData(
            query = query,
            searchId = "fresh-search",
            routeId = "fresh-route",
            serverRouteId = null,
            distanceMeters = 120.0,
        )

    override suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("transit route search was not expected")

    override suspend fun selectRoute(
        routeId: String,
        searchId: String,
    ): RouteSessionData {
        assertEquals("fresh-route", routeId)
        assertEquals("fresh-search", searchId)
        return RouteSessionData(sessionId = "session-1")
    }

    override suspend fun refreshTransit(
        routeId: String,
        legSequence: Int,
    ): RouteTransitRefreshData = throw IllegalStateException("refresh failed")

    override suspend fun reroute(
        routeId: String,
        currentPoint: com.ssafy.e102.eumgil.core.model.GeoCoordinate,
    ): RouteRerouteData = throw IllegalStateException("reroute failed")

    override suspend fun endRoute(routeId: String): RouteSessionData =
        throw IllegalStateException("end route failed")

    override suspend fun rateRoute(
        sessionId: String,
        score: Int,
    ): RouteRatingData = throw IllegalStateException("rating failed")
}

private class SelectFailureFallbackRouteRepository : RouteRepository {
    var lastSearchId: String? = null

    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("cached route search should not be used for low vision navigation start")

    override suspend fun getFreshRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        lowVisionRouteSearchData(
            query = query,
            searchId = "fresh-search",
            routeId = "fresh-route",
            distanceMeters = 120.0,
        )

    override suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("transit route search was not expected")

    override suspend fun selectRoute(
        routeId: String,
        searchId: String,
    ): RouteSessionData {
        lastSearchId = searchId
        throw IllegalStateException("select route failed")
    }

    override suspend fun refreshTransit(
        routeId: String,
        legSequence: Int,
    ): RouteTransitRefreshData = throw IllegalStateException("refresh failed")

    override suspend fun reroute(
        routeId: String,
        currentPoint: com.ssafy.e102.eumgil.core.model.GeoCoordinate,
    ): RouteRerouteData = throw IllegalStateException("reroute failed")

    override suspend fun endRoute(routeId: String): RouteSessionData =
        throw IllegalStateException("end route failed")

    override suspend fun rateRoute(
        sessionId: String,
        score: Int,
    ): RouteRatingData = throw IllegalStateException("rating failed")
}

private class ExpiredSelectRouteRepository : RouteRepository {
    var lastSearchId: String? = null

    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("cached route search should not be used for low vision navigation start")

    override suspend fun getFreshRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        lowVisionRouteSearchData(
            query = query,
            searchId = "fresh-search",
            routeId = "fresh-route",
            distanceMeters = 120.0,
        )

    override suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("transit route search was not expected")

    override suspend fun selectRoute(
        routeId: String,
        searchId: String,
    ): RouteSessionData {
        lastSearchId = searchId
        throw RouteApiException(
            httpStatusCode = 404,
            status = "RT4041",
            message = "route search expired",
        )
    }

    override suspend fun refreshTransit(
        routeId: String,
        legSequence: Int,
    ): RouteTransitRefreshData = throw IllegalStateException("refresh failed")

    override suspend fun reroute(
        routeId: String,
        currentPoint: com.ssafy.e102.eumgil.core.model.GeoCoordinate,
    ): RouteRerouteData = throw IllegalStateException("reroute failed")

    override suspend fun endRoute(routeId: String): RouteSessionData =
        throw IllegalStateException("end route failed")

    override suspend fun rateRoute(
        sessionId: String,
        score: Int,
    ): RouteRatingData = throw IllegalStateException("rating failed")
}

private class MissingSearchIdRouteRepository : RouteRepository {
    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("cached route search should not be used for low vision navigation start")

    override suspend fun getFreshRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        lowVisionRouteSearchData(
            query = query,
            searchId = "",
            routeId = "fresh-route",
            distanceMeters = 120.0,
        )

    override suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("transit route search was not expected")

    override suspend fun selectRoute(
        routeId: String,
        searchId: String,
    ): RouteSessionData = throw IllegalStateException("select route should not be called")

    override suspend fun refreshTransit(
        routeId: String,
        legSequence: Int,
    ): RouteTransitRefreshData = throw IllegalStateException("refresh failed")

    override suspend fun reroute(
        routeId: String,
        currentPoint: com.ssafy.e102.eumgil.core.model.GeoCoordinate,
    ): RouteRerouteData = throw IllegalStateException("reroute failed")

    override suspend fun endRoute(routeId: String): RouteSessionData =
        throw IllegalStateException("end route failed")

    override suspend fun rateRoute(
        sessionId: String,
        score: Int,
    ): RouteRatingData = throw IllegalStateException("rating failed")
}

private class IncompleteFreshRouteRepository : RouteRepository {
    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("cached route search should not be used for low vision navigation start")

    override suspend fun getFreshRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        val polyline =
            RoutePolyline(
                points =
                    listOf(
                        query.origin.coordinate,
                        query.origin.coordinate.interpolateTo(query.destination.coordinate, fraction = 0.5),
                        query.destination.coordinate,
                    ),
            )
        return RouteSearchData(
            query = query,
            result =
                RouteSearchResult(
                    origin = query.origin,
                    destination = query.destination,
                    searchId = "incomplete-search",
                    routes =
                        listOf(
                            RouteCandidate(
                                routeId = "incomplete-route",
                                serverRouteId = "incomplete-route",
                                routeOption = RouteOption.SAFE,
                                title = "Incomplete route",
                                summary =
                                    RouteSummary(
                                        distanceMeters = 0,
                                        estimatedTimeMinutes = 0,
                                        riskLevel = RouteRiskLevel.LOW,
                                        durationSeconds = 0,
                                    ),
                                geometry = polyline,
                                preview = RoutePreviewModel(polyline = polyline),
                                segments = emptyList(),
                            ),
                        ),
                ),
            source = RouteSearchSource.serverApi(),
        )
    }

    override suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("transit route search was not expected")

    override suspend fun selectRoute(
        routeId: String,
        searchId: String,
    ): RouteSessionData {
        assertEquals("incomplete-route", routeId)
        assertEquals("incomplete-search", searchId)
        return RouteSessionData(sessionId = "session-incomplete")
    }

    override suspend fun refreshTransit(
        routeId: String,
        legSequence: Int,
    ): RouteTransitRefreshData = throw IllegalStateException("refresh failed")

    override suspend fun reroute(
        routeId: String,
        currentPoint: com.ssafy.e102.eumgil.core.model.GeoCoordinate,
    ): RouteRerouteData = throw IllegalStateException("reroute failed")

    override suspend fun endRoute(routeId: String): RouteSessionData =
        throw IllegalStateException("end route failed")

    override suspend fun rateRoute(
        sessionId: String,
        score: Int,
    ): RouteRatingData = throw IllegalStateException("rating failed")
}

private class TransitFailureFallbackRouteRepository : RouteRepository {
    var freshTransitSearchCalled: Boolean = false

    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("cached route search should not be used for low vision navigation start")

    override suspend fun getFreshRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        lowVisionRouteSearchData(
            query = query,
            searchId = "fresh-walk-search",
            routeId = "walk-route",
            distanceMeters = 2_400.0,
        )

    override suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("cached transit route search should not be used")

    override suspend fun getFreshTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        freshTransitSearchCalled = true
        throw IllegalStateException("transit route api failed")
    }

    override suspend fun selectRoute(
        routeId: String,
        searchId: String,
    ): RouteSessionData {
        assertEquals("walk-route", routeId)
        assertEquals("fresh-walk-search", searchId)
        return RouteSessionData(sessionId = "session-walk")
    }

    override suspend fun refreshTransit(
        routeId: String,
        legSequence: Int,
    ): RouteTransitRefreshData = throw IllegalStateException("refresh failed")

    override suspend fun reroute(
        routeId: String,
        currentPoint: com.ssafy.e102.eumgil.core.model.GeoCoordinate,
    ): RouteRerouteData = throw IllegalStateException("reroute failed")

    override suspend fun endRoute(routeId: String): RouteSessionData =
        throw IllegalStateException("end route failed")

    override suspend fun rateRoute(
        sessionId: String,
        score: Int,
    ): RouteRatingData = throw IllegalStateException("rating failed")
}

private class OriginTrackingRouteRepository : RouteRepository {
    var lastWalkQuery: RouteSearchQuery? = null

    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("cached route search should not be used for low vision navigation start")

    override suspend fun getFreshRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        lastWalkQuery = query
        return lowVisionRouteSearchData(
            query = query,
            searchId = "origin-search",
            routeId = "origin-route",
            distanceMeters = 120.0,
        )
    }

    override suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("transit route search was not expected")

    override suspend fun selectRoute(
        routeId: String,
        searchId: String,
    ): RouteSessionData = RouteSessionData(sessionId = "session-origin")

    override suspend fun refreshTransit(
        routeId: String,
        legSequence: Int,
    ): RouteTransitRefreshData = throw IllegalStateException("refresh failed")

    override suspend fun reroute(
        routeId: String,
        currentPoint: com.ssafy.e102.eumgil.core.model.GeoCoordinate,
    ): RouteRerouteData = throw IllegalStateException("reroute failed")

    override suspend fun endRoute(routeId: String): RouteSessionData =
        throw IllegalStateException("end route failed")

    override suspend fun rateRoute(
        sessionId: String,
        score: Int,
    ): RouteRatingData = throw IllegalStateException("rating failed")
}

private class FallbackOriginRouteRepository(
    private val currentOrigin: RouteWaypoint,
) : RouteRepository {
    val walkQueries = mutableListOf<RouteSearchQuery>()

    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("cached route search should not be used for low vision navigation start")

    override suspend fun getFreshRouteSearchData(query: RouteSearchQuery): RouteSearchData {
        walkQueries += query
        if (query.origin.coordinate == currentOrigin.coordinate) {
            throw RouteApiException(
                httpStatusCode = 400,
                status = "OUT_OF_SERVICE_AREA",
                message = "out of service area",
            )
        }
        return lowVisionRouteSearchData(
            query = query,
            searchId = "fallback-search",
            routeId = "fallback-route",
            distanceMeters = 120.0,
        )
    }

    override suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("transit route search was not expected")

    override suspend fun selectRoute(
        routeId: String,
        searchId: String,
    ): RouteSessionData = RouteSessionData(sessionId = "session-fallback")

    override suspend fun refreshTransit(
        routeId: String,
        legSequence: Int,
    ): RouteTransitRefreshData = throw IllegalStateException("refresh failed")

    override suspend fun reroute(
        routeId: String,
        currentPoint: com.ssafy.e102.eumgil.core.model.GeoCoordinate,
    ): RouteRerouteData = throw IllegalStateException("reroute failed")

    override suspend fun endRoute(routeId: String): RouteSessionData =
        throw IllegalStateException("end route failed")

    override suspend fun rateRoute(
        sessionId: String,
        score: Int,
    ): RouteRatingData = throw IllegalStateException("rating failed")
}

private class CancellationRouteRepository : RouteRepository {
    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("cached route search should not be used for low vision navigation start")

    override suspend fun getFreshRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        throw CancellationException("origin changed")

    override suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("transit route search was not expected")

    override suspend fun selectRoute(
        routeId: String,
        searchId: String,
    ): RouteSessionData = throw IllegalStateException("select route failed")

    override suspend fun refreshTransit(
        routeId: String,
        legSequence: Int,
    ): RouteTransitRefreshData = throw IllegalStateException("refresh failed")

    override suspend fun reroute(
        routeId: String,
        currentPoint: com.ssafy.e102.eumgil.core.model.GeoCoordinate,
    ): RouteRerouteData = throw IllegalStateException("reroute failed")

    override suspend fun endRoute(routeId: String): RouteSessionData =
        throw IllegalStateException("end route failed")

    override suspend fun rateRoute(
        sessionId: String,
        score: Int,
    ): RouteRatingData = throw IllegalStateException("rating failed")
}

private fun com.ssafy.e102.eumgil.core.model.GeoCoordinate.interpolateTo(
    destination: com.ssafy.e102.eumgil.core.model.GeoCoordinate,
    fraction: Double,
): com.ssafy.e102.eumgil.core.model.GeoCoordinate =
    com.ssafy.e102.eumgil.core.model.GeoCoordinate(
        latitude = latitude + (destination.latitude - latitude) * fraction,
        longitude = longitude + (destination.longitude - longitude) * fraction,
    )

private fun lowVisionRouteSearchData(
    query: RouteSearchQuery,
    searchId: String,
    routeId: String,
    serverRouteId: String? = routeId,
    distanceMeters: Double,
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
                        RouteCandidate(
                            routeId = routeId,
                            serverRouteId = serverRouteId,
                            routeOption = RouteOption.SAFE,
                            title = "Fresh route",
                            summary =
                                RouteSummary(
                                    distanceMeters = distanceMeters.toInt(),
                                    estimatedTimeMinutes = 2,
                                    riskLevel = RouteRiskLevel.LOW,
                                ),
                        ),
                    ),
            ),
        source = RouteSearchSource.serverApi(),
    )
