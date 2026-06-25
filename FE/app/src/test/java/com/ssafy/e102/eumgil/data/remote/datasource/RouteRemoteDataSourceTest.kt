package com.ssafy.e102.eumgil.data.remote.datasource

import com.ssafy.e102.eumgil.data.remote.HttpJsonTimeoutConfig
import com.ssafy.e102.eumgil.data.remote.HttpJsonResponse
import com.ssafy.e102.eumgil.data.route.RoutePointDto
import com.ssafy.e102.eumgil.data.route.RouteRatingRequestDto
import com.ssafy.e102.eumgil.data.route.RouteRerouteRequestDto
import com.ssafy.e102.eumgil.data.route.RouteSearchRequestDto
import com.ssafy.e102.eumgil.data.route.RouteSelectRequestDto
import com.ssafy.e102.eumgil.data.route.RouteTransitRefreshRequestDto
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RouteRemoteDataSourceTest {
    @Test
    fun `searchWalkRoutes posts walk request with bearer token and parses data envelope`() =
        runBlocking {
            var capturedPath: String? = null
            var capturedBody: String? = null
            var capturedHeaders: Map<String, String> = emptyMap()
            val dataSource =
                RouteRemoteDataSource(
                    postRequestExecutor = { path, body, headers ->
                        capturedPath = path
                        capturedBody = body
                        capturedHeaders = headers
                        HttpJsonResponse(
                            statusCode = 200,
                            body =
                                """
                                {
                                  "status": "S2000",
                                  "data": {
                                    "searchId": "rs_walk_server_001",
                                    "routes": [
                                      {
                                        "routeId": "walk_rt_safe_001",
                                        "transportMode": "WALK",
                                        "routeOption": "SAFE",
                                        "routeOptions": ["SAFE"],
                                        "title": "Accessible Walk",
                                        "distanceMeter": 120.0,
                                        "durationSecond": 150,
                                        "estimatedTimeMinute": 2,
                                        "badges": ["LOW_SLOPE"],
                                        "geometry": "LINESTRING(129.075600 35.179600, 129.076800 35.180600)",
                                        "legs": [
                                          {
                                            "sequence": 1,
                                            "type": "WALK",
                                            "role": "WALK_ONLY",
                                            "instruction": "Walk to destination",
                                            "distanceMeter": 120.0,
                                            "durationSecond": 150,
                                            "estimatedTimeMinute": 2,
                                            "geometry": "LINESTRING(129.075600 35.179600, 129.076800 35.180600)",
                                            "steps": [
                                              {
                                                "sequence": 1,
                                                "instruction": "Go straight",
                                                "distanceMeter": 80.0,
                                                "durationSecond": 90,
                                                "geometry": "LINESTRING(129.075600 35.179600, 129.076000 35.179900)",
                                                "alert": {
                                                  "type": "CROSSWALK",
                                                  "distanceMeter": 15.0
                                                }
                                              }
                                            ]
                                          }
                                        ]
                                      }
                                    ]
                                  },
                                  "message": "ok"
                                }
                                """.trimIndent(),
                        )
                    },
                    accessTokenProvider = { "access-token" },
                )

            val response =
                dataSource.searchWalkRoutes(
                    RouteSearchRequestDto(
                        startPoint = RoutePointDto(lat = 35.1796, lng = 129.0756),
                        endPoint = RoutePointDto(lat = 35.1151, lng = 129.0414),
                        routeOptions = listOf("SAFE", "SHORTEST"),
                    ),
                )

            assertEquals("/routes/search/walk", capturedPath)
            assertEquals("Bearer access-token", capturedHeaders["Authorization"])
            assertTrue(capturedBody.orEmpty().contains("\"lat\":35.1796"))
            assertTrue(capturedBody.orEmpty().contains("\"lng\":129.0414"))
            assertFalse(capturedBody.orEmpty().contains("routeOptions"))
            assertEquals("rs_walk_server_001", response.searchId)
            assertEquals(1, response.routes.size)
            assertEquals("walk_rt_safe_001", response.routes.single().routeId)
            assertEquals(listOf("SAFE"), response.routes.single().routeOptions)
            assertEquals(150, response.routes.single().legs.single().durationSecond)
            assertEquals(90, response.routes.single().legs.single().steps.single().durationSecond)
            assertEquals("CROSSWALK", response.routes.single().legs.single().steps.single().alert?.type)
        }

    @Test
    fun `searchTransitRoutes posts transit request and parses transit contract fields`() =
        runBlocking {
            var capturedPath: String? = null
            var capturedBody: String? = null
            val dataSource =
                RouteRemoteDataSource(
                    postRequestExecutor = { path, body, _ ->
                        capturedPath = path
                        capturedBody = body
                        HttpJsonResponse(
                            statusCode = 200,
                            body =
                                """
                                {
                                  "status": "S2000",
                                  "data": {
                                    "searchId": "rs_transit_server_001",
                                    "routes": [
                                      {
                                        "routeId": "pt_rt_001",
                                        "transportMode": "PUBLIC_TRANSIT",
                                        "routeOption": "RECOMMENDED",
                                        "routeOptions": ["RECOMMENDED", "MIN_WALK"],
                                        "title": "Transit Route",
                                        "distanceMeter": 4200.0,
                                        "estimatedTimeMinute": 28,
                                        "transferCount": 1,
                                        "legs": [
                                          {
                                            "sequence": 1,
                                            "type": "WALK",
                                            "role": "WALK_TO_TRANSIT",
                                            "instruction": "Walk to Stop A",
                                            "geometry": "LINESTRING(129.075600 35.179600, 129.076000 35.179900)",
                                            "guidanceEvents": [
                                              {
                                                "sequence": 1,
                                                "type": "TURN_RIGHT",
                                                "distanceFromLegStartMeter": 40.0,
                                                "durationFromLegStartSecond": 40,
                                                "distanceFromRouteStartMeter": 40.0,
                                                "durationFromRouteStartSecond": 40,
                                                "geometry": "POINT(129.076000 35.179900)"
                                              }
                                            ]
                                          },
                                          {
                                            "sequence": 2,
                                            "type": "BUS",
                                            "role": "TRANSIT",
                                            "instruction": "Take bus 100",
                                            "routeNo": "100",
                                            "laneOptions": [
                                              {
                                                "routeNo": "100",
                                                "remainingMinute": 3,
                                                "durationSecond": 660,
                                                "estimatedTimeMinute": 11,
                                                "isLowFloor": true
                                              }
                                            ],
                                            "boardingStop": {
                                              "name": "Stop A",
                                              "lat": 35.1799,
                                              "lng": 129.0760
                                            },
                                            "arrivingStop": {
                                              "name": "Stop B",
                                              "lat": 35.1650,
                                              "lng": 129.0600
                                            }
                                          }
                                        ]
                                      }
                                    ]
                                  }
                                }
                                """.trimIndent(),
                        )
                    },
                )

            val response =
                dataSource.searchTransitRoutes(
                    RouteSearchRequestDto(
                        startPoint = RoutePointDto(lat = 35.1796, lng = 129.0756),
                        endPoint = RoutePointDto(lat = 35.1151, lng = 129.0414),
                        routeOptions = listOf("RECOMMENDED", "MIN_WALK"),
                    ),
                )

            assertEquals("/routes/search/transit", capturedPath)
            assertTrue(capturedBody.orEmpty().contains("\"startPoint\""))
            assertFalse(capturedBody.orEmpty().contains("routeOptions"))
            val route = response.routes.single()
            assertEquals(listOf("RECOMMENDED", "MIN_WALK"), route.routeOptions)
            assertEquals(1, route.legs.first().guidanceEvents.size)
            assertEquals("TURN_RIGHT", route.legs.first().guidanceEvents.single().type)
            assertEquals(1, route.legs[1].laneOptions.size)
            assertEquals("100", route.legs[1].laneOptions.single().routeNo)
            assertEquals("Stop B", route.legs[1].arrivingStop?.name)
        }

    @Test
    fun `route action methods post correct bodies and parse thin responses`() =
        runBlocking {
            val calls = mutableListOf<RecordedCall>()
            val dataSource =
                RouteRemoteDataSource(
                    postRequestExecutor = { path, body, headers ->
                        calls += RecordedCall(path = path, body = body, headers = headers)
                        HttpJsonResponse(
                            statusCode = 200,
                            body =
                                when (path) {
                                    "/routes/route-1/select" ->
                                        """
                                        {
                                          "status": "S2000",
                                          "data": {
                                            "sessionId": "session-select-1",
                                            "totalDistanceMeter": 950.0,
                                            "totalDurationSecond": 960
                                          }
                                        }
                                        """.trimIndent()

                                    "/routes/route-1/transit-refresh" ->
                                        """
                                        {
                                          "status": "S2000",
                                          "data": {
                                            "type": "BUS",
                                            "arrivalStatus": "ARRIVING_SOON",
                                            "transits": [
                                              {
                                                "routeNo": "100",
                                                "remainingMinute": 2,
                                                "isLowFloor": true
                                              }
                                            ]
                                          }
                                        }
                                        """.trimIndent()

                                    "/routes/reroute" ->
                                        """
                                        {
                                          "status": "S2000",
                                          "data": {
                                            "route": {
                                              "routeId": "walk_rt_reroute_1",
                                              "transportMode": "WALK",
                                              "routeOption": "SAFE",
                                              "title": "Rerouted Walk",
                                              "distanceMeter": 180.0,
                                              "estimatedTimeMinute": 3,
                                              "geometry": "LINESTRING(129.075600 35.179600, 129.076000 35.180100)",
                                              "legs": [
                                                {
                                                  "sequence": 1,
                                                  "type": "WALK",
                                                  "role": "WALK_ONLY",
                                                  "instruction": "Continue straight",
                                                  "distanceMeter": 180.0,
                                                  "estimatedTimeMinute": 3,
                                                  "geometry": "LINESTRING(129.075600 35.179600, 129.076000 35.180100)"
                                                }
                                              ]
                                            }
                                          }
                                        }
                                        """.trimIndent()

                                    "/routes/route-1/end" ->
                                        """
                                        {
                                          "status": "S2000",
                                          "data": {
                                            "sessionId": "session-end-1"
                                          }
                                        }
                                        """.trimIndent()

                                    "/route-ratings" ->
                                        """
                                        {
                                          "status": "S2000",
                                          "data": {
                                            "ratingId": 77
                                          }
                                        }
                                        """.trimIndent()

                                    else -> error("Unexpected path: $path")
                                },
                        )
                    },
                    accessTokenProvider = { "access-token" },
                )

            val selectResponse = dataSource.selectRoute("route-1", RouteSelectRequestDto(searchId = "search-1"))
            val refreshResponse = dataSource.refreshTransit("route-1", RouteTransitRefreshRequestDto(legSequence = 2))
            val rerouteResponse =
                dataSource.reroute(
                    RouteRerouteRequestDto(
                        routeId = "route-1",
                        currentPoint = RoutePointDto(lat = 35.1797, lng = 129.0757),
                    ),
                )
            val endResponse = dataSource.endRoute("route-1")
            val ratingResponse = dataSource.rateRoute(RouteRatingRequestDto(sessionId = "session-1", score = 5))

            assertEquals(5, calls.size)
            assertEquals("/routes/route-1/select", calls[0].path)
            assertTrue(calls[0].body.contains("\"searchId\":\"search-1\""))
            assertEquals("Bearer access-token", calls[0].headers["Authorization"])

            assertEquals("/routes/route-1/transit-refresh", calls[1].path)
            assertTrue(calls[1].body.contains("\"legSequence\":2"))

            assertEquals("/routes/reroute", calls[2].path)
            assertTrue(calls[2].body.contains("\"routeId\":\"route-1\""))
            assertTrue(calls[2].body.contains("\"lat\":35.1797"))

            assertEquals("/routes/route-1/end", calls[3].path)
            assertEquals("", calls[3].body)

            assertEquals("/route-ratings", calls[4].path)
            assertTrue(calls[4].body.contains("\"sessionId\":\"session-1\""))
            assertTrue(calls[4].body.contains("\"score\":5"))

            assertEquals("session-select-1", selectResponse.sessionId)
            assertEquals(950.0, selectResponse.totalDistanceMeter ?: -1.0, 0.0)
            assertEquals(960, selectResponse.totalDurationSecond)
            assertEquals("BUS", refreshResponse.type)
            assertEquals("ARRIVING_SOON", refreshResponse.arrivalStatus)
            assertEquals("100", refreshResponse.transits.single().routeNo)
            assertNotNull(rerouteResponse.route)
            assertEquals("walk_rt_reroute_1", rerouteResponse.route?.routeId)
            assertEquals("session-end-1", endResponse.sessionId)
            assertEquals(77L, ratingResponse.ratingId)
        }

    @Test
    fun `searchWalkRoutes surfaces route api errors without fallback`() =
        runBlocking {
            val dataSource =
                RouteRemoteDataSource(
                    postRequestExecutor = { _, _, _ ->
                        HttpJsonResponse(
                            statusCode = 404,
                            body =
                                """
                                {
                                  "status": "RT4040",
                                  "data": null,
                                  "message": "No route is available."
                                }
                                """.trimIndent(),
                        )
                    },
                )

            try {
                dataSource.searchWalkRoutes(
                    RouteSearchRequestDto(
                        startPoint = RoutePointDto(lat = 35.1796, lng = 129.0756),
                        endPoint = RoutePointDto(lat = 35.1151, lng = 129.0414),
                        routeOptions = listOf("SAFE"),
                    ),
                )
                fail("RouteApiException was expected")
            } catch (error: RouteApiException) {
                assertEquals(404, error.httpStatusCode)
                assertEquals("RT4040", error.status)
                assertEquals("No route is available.", error.message)
            }
        }

    @Test
    fun `searchWalkRoutes normalizes client timeout into route api exception`() =
        runBlocking {
            val dataSource =
                RouteRemoteDataSource(
                    postRequestExecutor = { _, _, _ ->
                        throw SocketTimeoutException("Read timed out")
                    },
                )

            val failure = runCatching { dataSource.searchWalkRoutes(routeSearchRequest()) }.exceptionOrNull()

            requireNotNull(failure)
            assertTrue(failure is RouteApiException)
            failure as RouteApiException
            assertEquals(0, failure.httpStatusCode)
            assertEquals("ROUTE_CLIENT_TIMEOUT", failure.status)
            assertEquals(RouteFailureKind.CLIENT_TIMEOUT, failure.failureKind)
        }

    @Test
    fun `searchWalkRoutes normalizes connection failure into route api exception`() =
        runBlocking {
            val dataSource =
                RouteRemoteDataSource(
                    postRequestExecutor = { _, _, _ ->
                        throw ConnectException("Connection refused")
                    },
                )

            val failure = runCatching { dataSource.searchWalkRoutes(routeSearchRequest()) }.exceptionOrNull()

            requireNotNull(failure)
            assertTrue(failure is RouteApiException)
            failure as RouteApiException
            assertEquals(0, failure.httpStatusCode)
            assertEquals("ROUTE_CONNECTION_FAILED", failure.status)
            assertEquals(RouteFailureKind.CONNECTION_FAILURE, failure.failureKind)
        }

    @Test
    fun `searchWalkRoutes normalizes unknown host into route api exception`() =
        runBlocking {
            val dataSource =
                RouteRemoteDataSource(
                    postRequestExecutor = { _, _, _ ->
                        throw UnknownHostException("route.example.invalid")
                    },
                )

            val failure = runCatching { dataSource.searchWalkRoutes(routeSearchRequest()) }.exceptionOrNull()

            requireNotNull(failure)
            assertTrue(failure is RouteApiException)
            failure as RouteApiException
            assertEquals(0, failure.httpStatusCode)
            assertEquals("ROUTE_UNKNOWN_HOST", failure.status)
            assertEquals(RouteFailureKind.UNKNOWN_HOST, failure.failureKind)
        }

    @Test
    fun `searchWalkRoutes normalizes generic io exception into route api exception`() =
        runBlocking {
            val dataSource =
                RouteRemoteDataSource(
                    postRequestExecutor = { _, _, _ ->
                        throw IOException("socket closed")
                    },
                )

            val failure = runCatching { dataSource.searchWalkRoutes(routeSearchRequest()) }.exceptionOrNull()

            requireNotNull(failure)
            assertTrue(failure is RouteApiException)
            failure as RouteApiException
            assertEquals(0, failure.httpStatusCode)
            assertEquals("ROUTE_NETWORK_IO_ERROR", failure.status)
            assertEquals(RouteFailureKind.NETWORK_IO, failure.failureKind)
        }

    @Test
    fun `searchWalkRoutes preserves server 504 route status and message`() =
        runBlocking {
            val dataSource =
                RouteRemoteDataSource(
                    postRequestExecutor = { _, _, _ ->
                        HttpJsonResponse(
                            statusCode = 504,
                            body =
                                """
                                {
                                  "status": "EX5040",
                                  "message": "외부 경로 정보 응답이 지연되고 있습니다."
                                }
                                """.trimIndent(),
                        )
                    },
                )

            val failure = runCatching { dataSource.searchWalkRoutes(routeSearchRequest()) }.exceptionOrNull()

            requireNotNull(failure)
            assertTrue(failure is RouteApiException)
            failure as RouteApiException
            assertEquals(504, failure.httpStatusCode)
            assertEquals("EX5040", failure.status)
            assertEquals("외부 경로 정보 응답이 지연되고 있습니다.", failure.message)
            assertEquals(RouteFailureKind.HTTP_RESPONSE, failure.failureKind)
        }

    @Test
    fun `route constructor passes explicit timeout config to executor factory`() =
        runBlocking {
            var capturedBaseUrl: String? = null
            var capturedTimeoutConfig: HttpJsonTimeoutConfig? = null
            val timeoutConfig =
                HttpJsonTimeoutConfig(
                    connectTimeoutMillis = 4_000,
                    readTimeoutMillis = 7_000,
                )
            val dataSource =
                RouteRemoteDataSource(
                    baseUrl = "https://route.example.com",
                    timeoutConfig = timeoutConfig,
                    postRequestExecutorFactory = { baseUrl: String, configuredTimeouts: HttpJsonTimeoutConfig ->
                        capturedBaseUrl = baseUrl
                        capturedTimeoutConfig = configuredTimeouts
                        { _: String, _: String, _: Map<String, String> ->
                            HttpJsonResponse(
                                statusCode = 200,
                                body =
                                    """
                                    {
                                      "status": "S2000",
                                      "data": {
                                        "searchId": "rs_timeout_config_001",
                                        "routes": []
                                      }
                                    }
                                    """.trimIndent(),
                            )
                        }
                    },
                )

            val response = dataSource.searchWalkRoutes(routeSearchRequest())

            assertEquals("https://route.example.com", capturedBaseUrl)
            assertEquals(timeoutConfig, requireNotNull(capturedTimeoutConfig))
            assertEquals("rs_timeout_config_001", response.searchId)
        }

    private data class RecordedCall(
        val path: String,
        val body: String,
        val headers: Map<String, String>,
    )
}

private fun routeSearchRequest(): RouteSearchRequestDto =
    RouteSearchRequestDto(
        startPoint = RoutePointDto(lat = 35.1796, lng = 129.0756),
        endPoint = RoutePointDto(lat = 35.1151, lng = 129.0414),
        routeOptions = listOf("SAFE"),
    )
