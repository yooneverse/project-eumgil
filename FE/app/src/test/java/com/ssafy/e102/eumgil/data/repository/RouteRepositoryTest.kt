package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.AuthGateState
import com.ssafy.e102.eumgil.core.model.AuthSession
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.core.model.RouteSearchQuery
import com.ssafy.e102.eumgil.core.model.RouteSearchSourceType
import com.ssafy.e102.eumgil.core.model.RouteTransportMode
import com.ssafy.e102.eumgil.core.model.RouteWaypoint
import com.ssafy.e102.eumgil.data.local.datasource.RouteLocalDataSource
import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import com.ssafy.e102.eumgil.data.remote.datasource.AuthRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.RouteApiException
import com.ssafy.e102.eumgil.data.remote.datasource.RouteRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.dto.ReissueResponseDto
import com.ssafy.e102.eumgil.data.route.DefaultRouteGeometryParser
import com.ssafy.e102.eumgil.data.route.RouteGeometryParseResult
import com.ssafy.e102.eumgil.data.route.RouteGeometryParser
import com.ssafy.e102.eumgil.data.route.RouteDto
import com.ssafy.e102.eumgil.data.route.RouteGuidanceEventDto
import com.ssafy.e102.eumgil.data.route.RouteLegDto
import com.ssafy.e102.eumgil.data.route.RoutePointDto
import com.ssafy.e102.eumgil.data.route.RouteRatingRequestDto
import com.ssafy.e102.eumgil.data.route.RouteRatingResponseDto
import com.ssafy.e102.eumgil.data.route.RouteRerouteRequestDto
import com.ssafy.e102.eumgil.data.route.RouteRerouteResponseDto
import com.ssafy.e102.eumgil.data.route.RouteSearchRequestDto
import com.ssafy.e102.eumgil.data.route.RouteSearchResponseDto
import com.ssafy.e102.eumgil.data.route.RouteSelectRequestDto
import com.ssafy.e102.eumgil.data.route.RouteSelectResponseDto
import com.ssafy.e102.eumgil.data.route.RouteSessionResponseDto
import com.ssafy.e102.eumgil.data.route.RouteTransitArrivalDto
import com.ssafy.e102.eumgil.data.route.RouteTransitLaneOptionDto
import com.ssafy.e102.eumgil.data.route.RouteTransitRefreshRequestDto
import com.ssafy.e102.eumgil.data.route.RouteTransitRefreshResponseDto
import com.ssafy.e102.eumgil.data.route.RouteTransitStopDto
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RouteRepositoryTest {
    @Test
    fun `getRouteSearchData defers route mapping to background dispatcher and skips cache update when cancelled early`() =
        runTest {
            val localDataSource = RouteLocalDataSource()
            val query = routeQuery(requestedOptions = listOf(RouteOption.SAFE))
            val mappingDispatcher = StandardTestDispatcher(testScheduler)
            var walkCallCount = 0
            var parseCallCount = 0
            val countingParser =
                object : RouteGeometryParser {
                    private val delegate = DefaultRouteGeometryParser()

                    override fun parse(geometry: String?): RouteGeometryParseResult {
                        parseCallCount += 1
                        return delegate.parse(geometry)
                    }
                }
            val repository =
                DefaultRouteRepository(
                    localDataSource = localDataSource,
                    remoteDataSource =
                        remoteDataSource(
                            searchWalkResponse = {
                                walkCallCount += 1
                                walkSearchResponse()
                            },
                        ),
                    geometryParser = countingParser,
                    routeMappingDispatcher = mappingDispatcher,
                )

            val deferred =
                async(start = CoroutineStart.UNDISPATCHED) {
                    repository.getRouteSearchData(query)
                }

            assertEquals(1, walkCallCount)
            assertEquals(0, parseCallCount)
            assertFalse(deferred.isCompleted)
            assertNull(localDataSource.getCachedSearchData(query))

            deferred.cancel()
            advanceUntilIdle()

            assertTrue(deferred.isCancelled)
            assertEquals(0, parseCallCount)
            assertNull(localDataSource.getCachedSearchData(query))
        }

    @Test
    fun `getRouteSearchData reloads walk search data on every call`() =
        runBlocking {
            val localDataSource = RouteLocalDataSource()
            var walkCallCount = 0
            val repository =
                DefaultRouteRepository(
                    localDataSource = localDataSource,
                    remoteDataSource =
                        remoteDataSource(
                            searchWalkResponse = {
                                walkCallCount += 1
                                walkSearchResponse(
                                    searchId = "rs_walk_server_$walkCallCount",
                                    routeId = "walk_rt_safe_$walkCallCount",
                                )
                            },
                        ),
                )
            val query = routeQuery(requestedOptions = listOf(RouteOption.SAFE))

            val first = repository.getRouteSearchData(query)
            val second = repository.getRouteSearchData(query)

            assertEquals(2, walkCallCount)
            assertEquals(RouteSearchSourceType.SERVER_API, first.source.type)
            assertTrue(!first.source.isFromCache)
            assertTrue(!second.source.isFromCache)
            assertEquals("rs_walk_server_1", first.result.searchId)
            assertEquals("rs_walk_server_2", second.result.searchId)
            assertEquals("walk_rt_safe_1", first.routes.single().routeId)
            assertEquals("walk_rt_safe_2", second.routes.single().routeId)
            assertEquals(RouteTransportMode.WALK, first.routes.single().transportMode)
            assertNull(localDataSource.getCachedSearchData(query))
        }

    @Test
    fun `getFreshRouteSearchData bypasses cached walk search ids before select`() =
        runBlocking {
            val localDataSource = RouteLocalDataSource()
            var walkCallCount = 0
            val repository =
                DefaultRouteRepository(
                    localDataSource = localDataSource,
                    remoteDataSource =
                        remoteDataSource(
                            searchWalkResponse = {
                                walkCallCount += 1
                                walkSearchResponse(
                                    searchId = "rs_walk_server_$walkCallCount",
                                    routeId = "walk_rt_safe_$walkCallCount",
                                )
                            },
                        ),
                )
            val query = routeQuery(requestedOptions = listOf(RouteOption.SAFE))

            val cached = repository.getRouteSearchData(query)
            val fresh = repository.getFreshRouteSearchData(query)

            assertEquals(2, walkCallCount)
            assertEquals("rs_walk_server_1", cached.result.searchId)
            assertEquals("rs_walk_server_2", fresh.result.searchId)
            assertEquals("walk_rt_safe_2", fresh.routes.single().routeId)
            assertNull(localDataSource.getCachedSearchData(query))
        }

    @Test
    fun `getTransitRouteSearchData uses transit surface and reloads on every call`() =
        runBlocking {
            val localDataSource = RouteLocalDataSource()
            var walkCallCount = 0
            var transitCallCount = 0
            val repository =
                DefaultRouteRepository(
                    localDataSource = localDataSource,
                    remoteDataSource =
                        remoteDataSource(
                            searchWalkResponse = {
                                walkCallCount += 1
                                walkSearchResponse()
                            },
                            searchTransitResponse = {
                                transitCallCount += 1
                                transitSearchResponse()
                            },
                        ),
                )
            val query = routeQuery(requestedOptions = listOf(RouteOption.RECOMMENDED, RouteOption.MIN_WALK))

            val first = repository.getTransitRouteSearchData(query)
            val second = repository.getTransitRouteSearchData(query)

            assertEquals(0, walkCallCount)
            assertEquals(2, transitCallCount)
            assertEquals("rs_transit_server_001", first.result.searchId)
            assertEquals(RouteTransportMode.PUBLIC_TRANSIT, first.routes.single().transportMode)
            assertEquals(RouteOption.RECOMMENDED, first.routes.single().routeOption)
            assertEquals("Stop B", first.routes.single().legs[1].alightingStop?.name)
            assertTrue(!first.source.isFromCache)
            assertTrue(!second.source.isFromCache)
            assertEquals(first.result, second.result)
            assertNull(localDataSource.getCachedSearchData(query))
        }

    @Test
    fun `route action methods map remote responses into repository data`() =
        runBlocking {
            val repository =
                DefaultRouteRepository(
                    localDataSource = RouteLocalDataSource(),
                    remoteDataSource =
                        remoteDataSource(
                            selectResponse = { routeId, request ->
                                assertEquals("route-1", routeId)
                                assertEquals("search-1", request.searchId)
                                RouteSelectResponseDto(
                                    sessionId = "session-select-1",
                                    totalDistanceMeter = 950.0,
                                    totalDurationSecond = 960,
                                )
                            },
                            refreshResponse = { routeId, request ->
                                assertEquals("route-1", routeId)
                                assertEquals(2, request.legSequence)
                                RouteTransitRefreshResponseDto(
                                    type = "BUS",
                                    arrivalStatus = "ARRIVING_SOON",
                                    transits =
                                        listOf(
                                            RouteTransitArrivalDto(
                                                routeNo = "100",
                                                remainingMinute = 2,
                                                isLowFloor = true,
                                            ),
                                        ),
                                )
                            },
                            rerouteResponse = { request ->
                                assertEquals("route-1", request.routeId)
                                assertEquals(35.1797, request.currentPoint.lat, 0.0)
                                RouteRerouteResponseDto(
                                    route =
                                        RouteDto(
                                            routeId = "walk_rt_reroute_1",
                                            transportMode = "WALK",
                                            routeOption = "SAFE",
                                            title = "Rerouted Walk",
                                            distanceMeter = 180.0,
                                            estimatedTimeMinute = 3,
                                            geometry =
                                                "LINESTRING(129.075600 35.179600, 129.076000 35.180100)",
                                            legs =
                                                listOf(
                                                    RouteLegDto(
                                                        sequence = 1,
                                                        type = "WALK",
                                                        role = "WALK_ONLY",
                                                        instruction = "Continue straight",
                                                        distanceMeter = 180.0,
                                                        estimatedTimeMinute = 3,
                                                        geometry =
                                                            "LINESTRING(129.075600 35.179600, 129.076000 35.180100)",
                                                    ),
                                                ),
                                        ),
                                )
                            },
                            endResponse = { routeId ->
                                assertEquals("route-1", routeId)
                                RouteSessionResponseDto(sessionId = "session-end-1")
                            },
                            ratingResponse = { request ->
                                assertEquals("session-end-1", request.sessionId)
                                assertEquals(5, request.score)
                                RouteRatingResponseDto(ratingId = 77L)
                            },
                        ),
                )

            val selected = repository.selectRoute(routeId = "route-1", searchId = "search-1")
            val refreshed = repository.refreshTransit(routeId = "route-1", legSequence = 2)
            val rerouted =
                repository.reroute(
                    routeId = "route-1",
                    currentPoint = GeoCoordinate(latitude = 35.1797, longitude = 129.0757),
                )
            val ended = repository.endRoute(routeId = "route-1")
            val rated = repository.rateRoute(sessionId = ended.sessionId, score = 5)

            assertEquals("session-select-1", selected.sessionId)
            assertEquals(950, selected.totalDistanceMeters)
            assertEquals(960, selected.totalDurationSeconds)
            assertEquals("BUS", refreshed.type)
            assertEquals("ARRIVING_SOON", refreshed.arrivalStatus)
            assertEquals("100", refreshed.transits.single().routeNo)
            assertEquals(2, refreshed.transits.single().remainingMinute)
            assertNotNull(rerouted.route)
            assertEquals("walk_rt_reroute_1", rerouted.route?.routeId)
            assertEquals(RouteOption.SAFE, rerouted.route?.routeOption)
            assertEquals("session-end-1", ended.sessionId)
            assertEquals(77L, rated.ratingId)
        }

    @Test
    fun `selectRoute retries with refreshed auth session when remote responds unauthorized`() =
        runBlocking {
            val authSessionRepository =
                TestAuthSessionRepository(
                    initialState =
                        AuthGateState(
                            authSession = AuthSession(accessToken = "expired-token", refreshToken = "refresh-token"),
                            isProfileCompleted = true,
                        ),
                )
            var requestCount = 0
            val repository =
                DefaultRouteRepository(
                    localDataSource = RouteLocalDataSource(),
                    remoteDataSource =
                        remoteDataSource(
                            selectResponse = { _, _ ->
                                requestCount += 1
                                when (requestCount) {
                                    1 ->
                                        throw RouteApiException(
                                            httpStatusCode = 401,
                                            status = "AUTH_401",
                                            message = "Authentication required.",
                                        )

                                    2 -> RouteSelectResponseDto(sessionId = "session-select-1")

                                    else -> error("Unexpected select retry count: $requestCount")
                                }
                            },
                        ),
                    authSessionRepository = authSessionRepository,
                    authRemoteDataSource =
                        object : AuthRemoteDataSource(HttpJsonClient(baseUrl = "https://example.com")) {
                            override suspend fun reissue(refreshToken: String): ReissueResponseDto {
                                assertEquals("refresh-token", refreshToken)
                                return ReissueResponseDto(
                                    accessToken = "refreshed-access-token",
                                    refreshToken = "refreshed-refresh-token",
                                )
                            }
                        },
                )

            val result = repository.selectRoute(routeId = "route-1", searchId = "search-1")

            assertEquals("session-select-1", result.sessionId)
            assertEquals(2, requestCount)
            assertEquals(
                "refreshed-access-token",
                authSessionRepository.getAuthGateState().authSession?.accessToken,
            )
            assertEquals(
                "refreshed-refresh-token",
                authSessionRepository.getAuthGateState().authSession?.refreshToken,
            )
        }

    @Test
    fun `selectRoute clears auth session and surfaces repository auth error when refresh fails`() =
        runBlocking {
            val authSessionRepository =
                TestAuthSessionRepository(
                    initialState =
                        AuthGateState(
                            authSession = AuthSession(accessToken = "expired-token", refreshToken = "refresh-token"),
                            isProfileCompleted = true,
                        ),
                )
            val repository =
                DefaultRouteRepository(
                    localDataSource = RouteLocalDataSource(),
                    remoteDataSource =
                        remoteDataSource(
                            selectResponse = { _, _ ->
                                throw RouteApiException(
                                    httpStatusCode = 401,
                                    status = "AUTH_401",
                                    message = "Authentication required.",
                                )
                            },
                        ),
                    authSessionRepository = authSessionRepository,
                    authRemoteDataSource =
                        object : AuthRemoteDataSource(HttpJsonClient(baseUrl = "https://example.com")) {
                            override suspend fun reissue(refreshToken: String): ReissueResponseDto {
                                throw IllegalStateException("refresh failed")
                            }
                        },
                )

            val failure =
                runCatching {
                    repository.selectRoute(routeId = "route-1", searchId = "search-1")
                }.exceptionOrNull() as? RouteApiException

            requireNotNull(failure)
            assertEquals(401, failure.httpStatusCode)
            assertEquals("ROUTE_AUTHENTICATION_FAILED", failure.status)
            assertNull(authSessionRepository.getAuthGateState().authSession)
        }

    @Test
    fun `selectRoute surfaces forbidden response without clearing auth session`() =
        runBlocking {
            val authSessionRepository =
                TestAuthSessionRepository(
                    initialState =
                        AuthGateState(
                            authSession = AuthSession(accessToken = "access-token", refreshToken = "refresh-token"),
                            isProfileCompleted = true,
                        ),
                )
            val repository =
                DefaultRouteRepository(
                    localDataSource = RouteLocalDataSource(),
                    remoteDataSource =
                        remoteDataSource(
                            selectResponse = { _, _ ->
                                throw RouteApiException(
                                    httpStatusCode = 403,
                                    status = "FR4030",
                                    message = "Forbidden.",
                                )
                            },
                        ),
                    authSessionRepository = authSessionRepository,
                    authRemoteDataSource = AuthRemoteDataSource(HttpJsonClient(baseUrl = "https://example.com")),
                )

            val failure =
                runCatching {
                    repository.selectRoute(routeId = "route-1", searchId = "search-1")
                }.exceptionOrNull() as? RouteApiException

            requireNotNull(failure)
            assertEquals(403, failure.httpStatusCode)
            assertEquals("FR4030", failure.status)
            assertEquals("Forbidden.", failure.message)
            assertEquals("access-token", authSessionRepository.getAuthGateState().authSession?.accessToken)
            assertEquals("refresh-token", authSessionRepository.getAuthGateState().authSession?.refreshToken)
        }
}

private fun remoteDataSource(
    searchWalkResponse: suspend (RouteSearchRequestDto) -> RouteSearchResponseDto = {
        error("searchWalkRoutes was not expected")
    },
    searchTransitResponse: suspend (RouteSearchRequestDto) -> RouteSearchResponseDto = {
        error("searchTransitRoutes was not expected")
    },
    selectResponse: suspend (String, RouteSelectRequestDto) -> RouteSelectResponseDto = { _, _ ->
        error("selectRoute was not expected")
    },
    refreshResponse: suspend (String, RouteTransitRefreshRequestDto) -> RouteTransitRefreshResponseDto = { _, _ ->
        error("refreshTransit was not expected")
    },
    rerouteResponse: suspend (RouteRerouteRequestDto) -> RouteRerouteResponseDto = {
        error("reroute was not expected")
    },
    endResponse: suspend (String) -> RouteSessionResponseDto = {
        error("endRoute was not expected")
    },
    ratingResponse: suspend (RouteRatingRequestDto) -> RouteRatingResponseDto = {
        error("rateRoute was not expected")
    },
): RouteRemoteDataSource =
    object : RouteRemoteDataSource(
        postRequestExecutor = { _, _, _ ->
            error("repository tests override route remote methods directly")
        },
    ) {
        override suspend fun searchWalkRoutes(request: RouteSearchRequestDto): RouteSearchResponseDto =
            searchWalkResponse(request)

        override suspend fun searchTransitRoutes(request: RouteSearchRequestDto): RouteSearchResponseDto =
            searchTransitResponse(request)

        override suspend fun selectRoute(
            routeId: String,
            request: RouteSelectRequestDto,
        ): RouteSelectResponseDto = selectResponse(routeId, request)

        override suspend fun refreshTransit(
            routeId: String,
            request: RouteTransitRefreshRequestDto,
        ): RouteTransitRefreshResponseDto = refreshResponse(routeId, request)

        override suspend fun reroute(request: RouteRerouteRequestDto): RouteRerouteResponseDto =
            rerouteResponse(request)

        override suspend fun endRoute(routeId: String): RouteSessionResponseDto = endResponse(routeId)

        override suspend fun rateRoute(request: RouteRatingRequestDto): RouteRatingResponseDto =
            ratingResponse(request)
    }

private fun routeQuery(requestedOptions: List<RouteOption>): RouteSearchQuery =
    RouteSearchQuery(
        origin =
            RouteWaypoint(
                name = "Origin",
                coordinate = GeoCoordinate(latitude = 35.1796, longitude = 129.0756),
            ),
        destination =
            RouteWaypoint(
                name = "Destination",
                coordinate = GeoCoordinate(latitude = 35.1151, longitude = 129.0414),
            ),
        requestedOptions = requestedOptions,
    )

private fun walkSearchResponse(
    searchId: String = "rs_walk_server_001",
    routeId: String = "walk_rt_safe_001",
): RouteSearchResponseDto =
    RouteSearchResponseDto(
        searchId = searchId,
        routes =
            listOf(
                RouteDto(
                    routeId = routeId,
                    transportMode = "WALK",
                    routeOption = "SAFE",
                    routeOptions = listOf("SAFE"),
                    title = "Accessible Walk",
                    distanceMeter = 120.0,
                    estimatedTimeMinute = 2,
                    geometry = "LINESTRING(129.075600 35.179600, 129.076800 35.180600)",
                    legs =
                        listOf(
                            RouteLegDto(
                                sequence = 1,
                                type = "WALK",
                                role = "WALK_ONLY",
                                instruction = "Walk to destination",
                                distanceMeter = 120.0,
                                estimatedTimeMinute = 2,
                                geometry = "LINESTRING(129.075600 35.179600, 129.076800 35.180600)",
                            ),
                        ),
                ),
            ),
    )

private fun transitSearchResponse(): RouteSearchResponseDto =
    RouteSearchResponseDto(
        searchId = "rs_transit_server_001",
        routes =
            listOf(
                RouteDto(
                    routeId = "pt_rt_001",
                    transportMode = "PUBLIC_TRANSIT",
                    routeOptions = listOf("RECOMMENDED", "MIN_WALK"),
                    title = "Transit Route",
                    distanceMeter = 4200.0,
                    estimatedTimeMinute = 28,
                    transferCount = 1,
                    badges = listOf("ELEVATOR"),
                    legs =
                        listOf(
                            RouteLegDto(
                                sequence = 1,
                                type = "WALK",
                                role = "WALK_TO_TRANSIT",
                                instruction = "Walk to Stop A",
                                distanceMeter = 180.0,
                                estimatedTimeMinute = 3,
                                geometry =
                                    "LINESTRING(129.075600 35.179600, 129.076000 35.179900)",
                                guidanceEvents =
                                    listOf(
                                        RouteGuidanceEventDto(
                                            sequence = 1,
                                            type = "TURN_RIGHT",
                                            distanceFromLegStartMeter = 40.0,
                                            durationFromLegStartSecond = 40,
                                            distanceFromRouteStartMeter = 40.0,
                                            durationFromRouteStartSecond = 40,
                                            geometry = "POINT(129.076000 35.179900)",
                                        ),
                                    ),
                            ),
                            RouteLegDto(
                                sequence = 2,
                                type = "BUS",
                                role = "TRANSIT",
                                instruction = "Take bus 100",
                                estimatedTimeMinute = 11,
                                routeNo = "100",
                                laneOptions =
                                    listOf(
                                        RouteTransitLaneOptionDto(
                                            routeNo = "100",
                                            remainingMinute = 3,
                                            durationSecond = 660,
                                            estimatedTimeMinute = 11,
                                            isLowFloor = true,
                                        ),
                                    ),
                                boardingStop =
                                    RouteTransitStopDto(
                                        name = "Stop A",
                                        lat = 35.1799,
                                        lng = 129.0760,
                                    ),
                                arrivingStop =
                                    RouteTransitStopDto(
                                        name = "Stop B",
                                        lat = 35.1650,
                                        lng = 129.0600,
                                    ),
                            ),
                        ),
                ),
            ),
    )
