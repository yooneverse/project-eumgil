package com.ssafy.e102.eumgil.data.remote.datasource

import com.ssafy.e102.eumgil.core.model.MapPlaceClickType
import com.ssafy.e102.eumgil.core.model.MapPlaceDetailRequest
import com.ssafy.e102.eumgil.core.model.MapPlaceDetailType
import com.ssafy.e102.eumgil.core.model.PlaceCategory
import com.ssafy.e102.eumgil.core.model.PlaceFeatureAvailability
import com.ssafy.e102.eumgil.core.model.PlaceFeatureType
import com.ssafy.e102.eumgil.core.model.PlaceQuery
import com.ssafy.e102.eumgil.data.remote.HttpJsonResponse
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesRemoteDataSourceTest {
    @Test
    fun `getPlaces maps browse response and sends expected query filters`() =
        runBlocking {
            var capturedPath: String? = null
            var capturedQueryParams: Map<String, String> = emptyMap()
            var capturedHeaders: Map<String, String> = emptyMap()
            val dataSource =
                PlacesRemoteDataSource(
                    requestExecutor = { path, queryParams, headers ->
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
                                        "placeId": 101,
                                        "name": "Accessible Cafe",
                                        "category": "FOOD_CAFE",
                                        "address": "1 Jungang-daero, Busan",
                                        "point": {
                                          "lat": 35.1796,
                                          "lng": 129.0756
                                        },
                                        "accessibilityFeatures": [
                                          {
                                            "featureType": "accessibleToilet",
                                            "isAvailable": true
                                          },
                                          {
                                            "featureType": "accessibleParking",
                                            "isAvailable": false
                                          }
                                        ],
                                        "isBookmarked": true
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

            val places =
                dataSource.getPlaces(
                    PlaceQuery(
                        latitude = 35.1796,
                        longitude = 129.0756,
                        radiusMeters = 1500,
                        categories = setOf(PlaceCategory.FOOD_CAFE),
                        featureTypes =
                            setOf(
                                PlaceFeatureType.ACCESSIBLE_TOILET,
                                PlaceFeatureType.ELEVATOR,
                            ),
                    ),
                )

            assertEquals("/places", capturedPath)
            assertEquals("35.1796", capturedQueryParams["lat"])
            assertEquals("129.0756", capturedQueryParams["lng"])
            assertEquals("1500", capturedQueryParams["radius"])
            assertEquals("FOOD_CAFE", capturedQueryParams["category"])
            assertEquals("accessibleToilet,elevator", capturedQueryParams["featureType"])
            assertEquals("Bearer access-token", capturedHeaders["Authorization"])

            assertEquals(1, places.size)
            assertEquals("101", places.first().placeId)
            assertEquals(PlaceCategory.FOOD_CAFE, places.first().category)
            assertTrue(
                places.first().features.any { feature ->
                    feature.featureType == PlaceFeatureType.ACCESSIBLE_TOILET && feature.isAvailable
                },
            )
            assertTrue(places.first().isBookmarked)
        }

    @Test
    fun `getPlaces keeps ui-only filters out of category query and maps them to featureType`() =
        runBlocking {
            var capturedQueryParams: Map<String, String> = emptyMap()
            val dataSource =
                PlacesRemoteDataSource(
                    requestExecutor = { _, queryParams, _ ->
                        capturedQueryParams = queryParams
                        HttpJsonResponse(
                            statusCode = 200,
                            body =
                                """
                                {
                                  "status": "S2000",
                                  "data": {
                                    "places": []
                                  },
                                  "message": "ok"
                                }
                                """.trimIndent(),
                        )
                    },
                )

            dataSource.getPlaces(
                PlaceQuery(
                    latitude = 35.1796,
                    longitude = 129.0756,
                    categories =
                        setOf(
                            PlaceCategory.TOILET,
                            PlaceCategory.ELEVATOR,
                            PlaceCategory.CHARGING_STATION,
                            PlaceCategory.BRAILLE_BLOCK,
                            PlaceCategory.FOOD_CAFE,
                        ),
                ),
            )

            assertEquals("FOOD_CAFE", capturedQueryParams["category"])
            assertEquals("accessibleToilet,chargingStation,elevator", capturedQueryParams["featureType"])
        }

    @Test
    fun `getPlaceDetail maps detail response and sends expected auth header`() =
        runBlocking {
            var capturedPath: String? = null
            var capturedQueryParams: Map<String, String> = emptyMap()
            var capturedHeaders: Map<String, String> = emptyMap()
            val dataSource =
                PlacesRemoteDataSource(
                    requestExecutor = { path, queryParams, headers ->
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
                                    "placeId": 101,
                                    "name": "Accessible Cafe",
                                    "category": "FOOD_CAFE",
                                    "address": "1 Jungang-daero, Busan",
                                    "point": {
                                      "lat": 35.1796,
                                      "lng": 129.0756
                                    },
                                    "providerPlaceId": "kakao-101",
                                    "accessibilityFeatures": [
                                      {
                                        "featureType": "accessibleEntrance",
                                        "isAvailable": true
                                      },
                                      {
                                        "featureType": "accessibleToilet",
                                        "isAvailable": true
                                      }
                                    ],
                                    "isBookmarked": true
                                  },
                                  "message": "ok"
                                }
                                """.trimIndent(),
                        )
                    },
                    accessTokenProvider = { "access-token" },
                )

            val detail = dataSource.getPlaceDetail("101")

            assertEquals("/places/101", capturedPath)
            assertTrue(capturedQueryParams.isEmpty())
            assertEquals("Bearer access-token", capturedHeaders["Authorization"])
            assertEquals("101", detail?.placeId)
            assertEquals(PlaceCategory.FOOD_CAFE, detail?.category)
            assertEquals("kakao-101", detail?.providerPlaceId)
            assertEquals(
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
                detail?.features,
            )
            assertEquals(listOf("step-free-entrance", "accessible-toilet"), detail?.accessibilityTags)
            assertTrue(detail?.isBookmarked == true)
            assertNull(detail?.description)
        }

    @Test
    fun `getMapTappedPlaceDetail posts provider payload and maps external detail response`() =
        runBlocking {
            var capturedPath: String? = null
            var capturedBody: JSONObject? = null
            var capturedHeaders: Map<String, String> = emptyMap()
            val dataSource =
                PlacesRemoteDataSource(
                    requestExecutor = { _, _, _ ->
                        error("GET should not be used for map tap detail.")
                    },
                    postRequestExecutor = { path, body, headers ->
                        capturedPath = path
                        capturedBody = JSONObject(body)
                        capturedHeaders = headers
                        HttpJsonResponse(
                            statusCode = 200,
                            body =
                                """
                                {
                                  "status": "S2000",
                                  "data": {
                                    "bookmarkTargetId": "kakao:poi-123",
                                    "detailType": "EXTERNAL_POI",
                                    "placeId": null,
                                    "provider": "KAKAO",
                                    "providerPlaceId": "poi-123",
                                    "name": "Kakao Cafe",
                                    "category": null,
                                    "providerCategory": "Cafe",
                                    "address": "10 Cafe-ro, Busan",
                                    "point": {
                                      "lat": 35.1799,
                                      "lng": 129.0752
                                    },
                                    "accessibilityFeatures": [
                                      {
                                        "featureType": "accessibleEntrance",
                                        "isAvailable": true
                                      }
                                    ],
                                    "transitArrivals": [
                                      {
                                        "transitType": "BUS",
                                        "routeName": "100",
                                        "direction": null,
                                        "remainingMinute": 6,
                                        "isLowFloor": true,
                                        "source": "REALTIME"
                                      }
                                    ],
                                    "isBookmarked": false,
                                    "description": "External Kakao POI"
                                  },
                                  "message": "ok"
                                }
                                """.trimIndent(),
                        )
                    },
                    accessTokenProvider = { "access-token" },
                )

            val detail =
                dataSource.getMapTappedPlaceDetail(
                    MapPlaceDetailRequest(
                        latitude = 35.1799,
                        longitude = 129.0752,
                        clickType = MapPlaceClickType.POI,
                        provider = "KAKAO",
                        providerPlaceId = "poi-123",
                        nameHint = "Cafe Hint",
                    ),
                )

            assertEquals("/places/detail", capturedPath)
            assertEquals(35.1799, capturedBody?.getDouble("lat") ?: Double.NaN, 0.0)
            assertEquals(129.0752, capturedBody?.getDouble("lng") ?: Double.NaN, 0.0)
            assertEquals("POI", capturedBody?.getString("clickType"))
            assertEquals("KAKAO", capturedBody?.getString("provider"))
            assertEquals("poi-123", capturedBody?.getString("providerPlaceId"))
            assertEquals("Cafe Hint", capturedBody?.getString("nameHint"))
            assertEquals("Bearer access-token", capturedHeaders["Authorization"])

            assertEquals("kakao:poi-123", detail?.bookmarkTargetId)
            assertEquals(MapPlaceDetailType.EXTERNAL_POI, detail?.detailType)
            assertEquals("KAKAO", detail?.provider)
            assertEquals("poi-123", detail?.providerPlaceId)
            assertEquals("Cafe", detail?.providerCategory)
            assertEquals("Kakao Cafe", detail?.name)
            assertEquals("10 Cafe-ro, Busan", detail?.address)
            assertEquals(listOf("step-free-entrance"), detail?.accessibilityTags)
            assertEquals("100", detail?.transitArrivals?.single()?.routeName)
            assertEquals(6, detail?.transitArrivals?.single()?.remainingMinute)
            assertEquals("External Kakao POI", detail?.description)
        }
}
