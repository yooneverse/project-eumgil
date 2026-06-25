package com.ssafy.e102.eumgil.data.remote.datasource

import com.ssafy.e102.eumgil.core.model.PlaceCategory
import com.ssafy.e102.eumgil.core.model.SearchQuery
import com.ssafy.e102.eumgil.core.model.SearchSortOption
import com.ssafy.e102.eumgil.core.model.SearchVoiceIntent
import com.ssafy.e102.eumgil.core.model.SearchVoiceMode
import com.ssafy.e102.eumgil.data.remote.HttpJsonResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRemoteDataSourceTest {
    @Test
    fun `search maps matched and unmatched provider results safely`() =
        runBlocking {
            var capturedPath: String? = null
            var capturedQueryParams: Map<String, String> = emptyMap()
            var capturedHeaders: Map<String, String> = emptyMap()
            val dataSource =
                SearchRemoteDataSource(
                    getRequestExecutor = { path, queryParams, headers ->
                        capturedPath = path
                        capturedQueryParams = queryParams
                        capturedHeaders = headers
                        HttpJsonResponse(
                            statusCode = 200,
                            body =
                                """
                                {
                                  "status": "S2000",
                                  "data": {
                                    "places": [
                                      {
                                        "placeId": 10,
                                        "provider": "KAKAO",
                                        "providerPlaceId": "123456789",
                                        "name": "Busan Tower",
                                        "category": "TOURIST_SPOT",
                                        "address": "1 Yongdusan-gil, Busan",
                                        "distanceMeter": 350,
                                        "point": {
                                          "lat": 35.1000,
                                          "lng": 129.0320
                                        },
                                        "accessibilityFeatures": [
                                          {
                                            "featureType": "accessibleEntrance",
                                            "isAvailable": true
                                          }
                                        ],
                                        "matched": true
                                      },
                                      {
                                        "placeId": null,
                                        "provider": "KAKAO",
                                        "providerPlaceId": "987654321",
                                        "name": "Provider Only Cafe",
                                        "category": null,
                                        "address": "2 Gwangbok-ro, Busan",
                                        "distanceMeter": 120,
                                        "point": {
                                          "lat": 35.1010,
                                          "lng": 129.0330
                                        },
                                        "accessibilityFeatures": [
                                          {
                                            "featureType": "accessibleEntrance",
                                            "isAvailable": true
                                          }
                                        ],
                                        "matched": false
                                      }
                                    ],
                                    "nextCursor": null,
                                    "size": 2,
                                    "totalElements": 2,
                                    "hasNext": false
                                  },
                                  "message": "ok"
                                }
                                """.trimIndent(),
                        )
                    },
                    postRequestExecutor = { _, _, _ ->
                        error("voice analyze should not be called from search()")
                    },
                    accessTokenProvider = { "access-token" },
                )

            val results =
                dataSource.search(
                    SearchQuery(
                        keyword = "Busan",
                        limit = 2,
                    ),
                )

            assertEquals("/places/search", capturedPath)
            assertEquals("Busan", capturedQueryParams["keyword"])
            assertEquals("2", capturedQueryParams["size"])
            assertEquals("relevance", capturedQueryParams["sort"])
            assertEquals("Bearer access-token", capturedHeaders["Authorization"])

            assertEquals(2, results.size)

            val matchedResult = results[0]
            assertEquals("10", matchedResult.placeId)
            assertEquals("10", matchedResult.serverPlaceId)
            assertEquals("10", matchedResult.displayPlaceId)
            assertEquals(PlaceCategory.TOURIST_SPOT, matchedResult.category)
            assertEquals(listOf("step-free-entrance"), matchedResult.accessibilityTagKeys)
            assertEquals(350, matchedResult.distanceMeters)
            assertTrue(matchedResult.matched)

            val unmatchedResult = results[1]
            assertEquals("provider:kakao:987654321", unmatchedResult.placeId)
            assertNull(unmatchedResult.serverPlaceId)
            assertEquals("987654321", unmatchedResult.displayPlaceId)
            assertNull(unmatchedResult.category)
            assertEquals(emptyList<String>(), unmatchedResult.accessibilityTagKeys)
            assertEquals(120, unmatchedResult.distanceMeters)
            assertTrue(unmatchedResult.matched.not())
        }

    @Test
    fun `analyzeVoiceSearch extracts place keyword for mobility mode`() =
        runBlocking {
            var capturedBody: String? = null
            val dataSource =
                SearchRemoteDataSource(
                    getRequestExecutor = { _, _, _ ->
                        error("search() should not be called from analyzeVoiceSearch()")
                    },
                    postRequestExecutor = { _, body, _ ->
                        capturedBody = body
                        HttpJsonResponse(
                            statusCode = 200,
                            body =
                                """
                                {
                                  "status": "S2000",
                                  "data": {
                                    "intent": "PLACE_SEARCH",
                                    "placeName": "Busan Station",
                                    "confirmed": null,
                                    "confirmationMessage": null
                                  },
                                  "message": "ok"
                                }
                                """.trimIndent(),
                        )
                    },
                )

            val analysis =
                dataSource.analyzeVoiceSearch(
                    text = "부산역",
                    mode = SearchVoiceMode.MOBILITY_IMPAIRED,
                )

            assertTrue(capturedBody.orEmpty().contains("\"text\":\"부산역\""))
            assertTrue(capturedBody.orEmpty().contains("\"mode\":\"MOBILITY_IMPAIRED\""))
            assertEquals(SearchVoiceIntent.PLACE_SEARCH, analysis.intent)
            assertEquals("Busan Station", analysis.placeName)
            assertNull(analysis.confirmationMessage)
        }

    @Test
    fun `search uses backend compatible default size when limit is omitted`() =
        runBlocking {
            var capturedQueryParams: Map<String, String> = emptyMap()
            val dataSource =
                SearchRemoteDataSource(
                    getRequestExecutor = { _, queryParams, _ ->
                        capturedQueryParams = queryParams
                        HttpJsonResponse(
                            statusCode = 200,
                            body =
                                """
                                {
                                  "status": "S2000",
                                  "data": {
                                    "places": [],
                                    "nextCursor": null,
                                    "size": 0,
                                    "totalElements": 0,
                                    "hasNext": false
                                  },
                                  "message": "ok"
                                }
                                """.trimIndent(),
                        )
                    },
                    postRequestExecutor = { _, _, _ ->
                        error("voice analyze should not be called from search()")
                    },
                )

            dataSource.search(SearchQuery(keyword = "Busan Station"))

            assertEquals(SearchQuery.DEFAULT_LIMIT.toString(), capturedQueryParams["size"])
            assertEquals("15", capturedQueryParams["size"])
        }

    @Test
    fun `search passes distance sort option to backend`() =
        runBlocking {
            var capturedQueryParams: Map<String, String> = emptyMap()
            val dataSource =
                SearchRemoteDataSource(
                    getRequestExecutor = { _, queryParams, _ ->
                        capturedQueryParams = queryParams
                        HttpJsonResponse(
                            statusCode = 200,
                            body =
                                """
                                {
                                  "status": "S2000",
                                  "data": {
                                    "places": [],
                                    "nextCursor": null,
                                    "size": 0,
                                    "totalElements": 0,
                                    "hasNext": false
                                  },
                                  "message": "ok"
                                }
                                """.trimIndent(),
                        )
                    },
                    postRequestExecutor = { _, _, _ ->
                        error("voice analyze should not be called from search()")
                    },
                )

            dataSource.search(
                SearchQuery(
                    keyword = "Busan Station",
                    sortOption = SearchSortOption.DISTANCE,
                ),
            )

            assertEquals("distance", capturedQueryParams["sort"])
        }
}
