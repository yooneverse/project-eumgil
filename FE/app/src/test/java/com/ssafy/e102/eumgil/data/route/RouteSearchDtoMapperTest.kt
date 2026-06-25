package com.ssafy.e102.eumgil.data.route

import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.RouteBadge
import com.ssafy.e102.eumgil.core.model.RouteGuidanceDirection
import com.ssafy.e102.eumgil.core.model.RouteGuidanceFeature
import com.ssafy.e102.eumgil.core.model.RouteGuidanceType
import com.ssafy.e102.eumgil.core.model.RouteLegRole
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.core.model.RouteTransportMode
import com.ssafy.e102.eumgil.core.model.RouteWaypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSearchDtoMapperTest {
    private val geometryParser: RouteGeometryParser = DefaultRouteGeometryParser()

    @Test
    fun `parseRouteSearchResponseDto reads transit payload fields from data envelope`() {
        val response =
            parseRouteSearchResponseDto(
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
                                "isLowFloor": true,
                                "lowFloorReservation": {
                                  "stopName": "Stop A",
                                  "arsNo": "070001",
                                  "routeNo": "100",
                                  "vehicleNo": "1618",
                                  "remainingMinute": 3,
                                  "remainingStopCount": 2
                                }
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

        assertEquals("rs_transit_server_001", response.searchId)
        val route = response.routes.single()
        assertEquals(listOf("RECOMMENDED", "MIN_WALK"), route.routeOptions)
        assertEquals(1, route.transferCount)
        assertEquals("TURN_RIGHT", route.legs.first().guidanceEvents.single().type)
        assertEquals("100", route.legs[1].laneOptions.single().routeNo)
        assertEquals("Stop B", route.legs[1].arrivingStop?.name)
        val reservation = route.legs[1].laneOptions.single().lowFloorReservation
        assertEquals("Stop A", reservation?.stopName)
        assertEquals("070001", reservation?.arsNo)
        assertEquals("1618", reservation?.vehicleNo)
        assertEquals(2, reservation?.remainingStopCount)
    }

    @Test
    fun `toDomain maps guidance events route options and transit to walk role`() {
        val result =
            RouteSearchResponseDto(
                searchId = "rs_transit_guidance_001",
                routes =
                    listOf(
                        RouteDto(
                            routeId = "pt_rt_guidance_001",
                            transportMode = "PUBLIC_TRANSIT",
                            routeOptions = listOf("RECOMMENDED", "MIN_WALK"),
                            title = "Transit Route",
                            distanceMeter = 4200.0,
                            estimatedTimeMinute = 28,
                            transferCount = 1,
                            badges = listOf("ELEVATOR"),
                            geometry =
                                "LINESTRING(129.075600 35.179600, 129.076000 35.179900, 129.076500 35.180200)",
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
                                                    type = "TURN_LEFT",
                                                    distanceFromLegStartMeter = 40.0,
                                                    geometry = "POINT(129.075800 35.179700)",
                                                ),
                                                RouteGuidanceEventDto(
                                                    sequence = 2,
                                                    type = "BUS_STOP",
                                                    distanceFromLegStartMeter = 180.0,
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
                                    RouteLegDto(
                                        sequence = 3,
                                        type = "WALK",
                                        role = "TRANSIT_TO_WALK",
                                        instruction = "Walk to destination",
                                        distanceMeter = 220.0,
                                        estimatedTimeMinute = 4,
                                        geometry =
                                            "LINESTRING(129.076000 35.179900, 129.076500 35.180200)",
                                        guidanceEvents =
                                            listOf(
                                                RouteGuidanceEventDto(
                                                    sequence = 1,
                                                    type = "CROSSWALK_AUDIO",
                                                    distanceFromLegStartMeter = 70.0,
                                                    geometry = "POINT(129.076200 35.180000)",
                                                ),
                                                RouteGuidanceEventDto(
                                                    sequence = 2,
                                                    type = "DESTINATION",
                                                    distanceFromLegStartMeter = 220.0,
                                                    geometry = "POINT(129.076500 35.180200)",
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            ).toDomain(
                query = testRouteSearchQuery(routeOptions = listOf(RouteOption.RECOMMENDED)),
                geometryParser = geometryParser,
            )

        assertEquals("rs_transit_guidance_001", result.searchId)
        val route = result.routes.single()
        assertEquals("pt_rt_guidance_001", route.routeId)
        assertEquals(RouteTransportMode.PUBLIC_TRANSIT, route.transportMode)
        assertEquals(RouteOption.RECOMMENDED, route.routeOption)
        assertEquals(3, route.legs.size)
        assertEquals(RouteLegRole.WALK_TO_DESTINATION, route.legs.last().role)
        assertEquals("Stop B", route.legs[1].alightingStop?.name)
        assertEquals("Turn left.", route.legs.first().steps.first().instruction)
        assertEquals(
            GeoCoordinate(latitude = 35.1797, longitude = 129.0758),
            route.legs.first().steps.first().anchorCoordinate,
        )
        assertFalse(route.legs.first().steps.first().hasRenderablePolyline)
        assertEquals("Arrive at destination.", route.legs.last().steps.last().instruction)
        assertEquals(
            GeoCoordinate(latitude = 35.1797, longitude = 129.0758),
            route.segments.first().anchorCoordinate,
        )
        assertFalse(route.segments.first().hasRenderablePolyline)
        val alightingSegment =
            route.segments.firstOrNull { segment ->
                segment.guidanceType == RouteGuidanceType.ARRIVING_POINT
            }
        assertEquals("Stop B \uD558\uCC28\uC9C0\uC810\uC785\uB2C8\uB2E4.", alightingSegment?.guidanceMessage)
        assertEquals(GeoCoordinate(latitude = 35.1650, longitude = 129.0600), alightingSegment?.anchorCoordinate)
        assertEquals(route.legs[1].sequence, alightingSegment?.sourceLegSequence)
        assertTrue(route.segments.any { segment -> segment.guidanceMessage == "Audio signal crosswalk ahead." })
        assertTrue(route.segments.any { segment -> segment.guidanceMessage == "Arrive at destination." })
    }

    @Test
    fun `guidance event metadata and backend badges are preserved for detail guidance`() {
        val result =
            RouteSearchResponseDto(
                routes =
                    listOf(
                        RouteDto(
                            routeId = "walk-meta",
                            transportMode = "WALK",
                            routeOption = "SAFE",
                            title = "Accessible Walk",
                            distanceMeter = 300.0,
                            estimatedTimeMinute = 10,
                            badges = listOf("LOW_SLOPE", "CROSSWALK", "STAIR"),
                            legs =
                                listOf(
                                    RouteLegDto(
                                        sequence = 1,
                                        type = "WALK",
                                        role = "WALK_ONLY",
                                        instruction = "Walk to destination",
                                        distanceMeter = 300.0,
                                        estimatedTimeMinute = 10,
                                        guidanceEvents =
                                            listOf(
                                                RouteGuidanceEventDto(
                                                    sequence = 1,
                                                    type = null,
                                                    direction = "TURN_LEFT",
                                                    distanceFromLegStartMeter = 40.0,
                                                    durationFromRouteStartSecond = 80,
                                                ),
                                                RouteGuidanceEventDto(
                                                    sequence = 2,
                                                    type = "CROSSWALK",
                                                    direction = "STRAIGHT",
                                                    features = listOf("SIGNAL"),
                                                    distanceFromLegStartMeter = 100.0,
                                                    durationFromRouteStartSecond = 160,
                                                ),
                                                RouteGuidanceEventDto(
                                                    sequence = 3,
                                                    type = "STRAIGHT",
                                                    direction = "STRAIGHT",
                                                    distanceFromLegStartMeter = 120.0,
                                                    durationFromRouteStartSecond = 220,
                                                ),
                                                RouteGuidanceEventDto(
                                                    sequence = 4,
                                                    type = "DESTINATION",
                                                    distanceFromLegStartMeter = 300.0,
                                                    durationFromRouteStartSecond = 600,
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            ).toDomain(
                query = testRouteSearchQuery(routeOptions = listOf(RouteOption.SAFE)),
                geometryParser = geometryParser,
            )

        val route = result.routes.single()
        val segments = route.segments

        assertEquals(listOf(RouteBadge.LOW_SLOPE, RouteBadge.CROSSWALK, RouteBadge.STAIR), route.badges)
        assertEquals(RouteGuidanceDirection.TURN_LEFT, segments[0].guidanceDirection)
        assertEquals(40, segments[0].guidanceDistanceMeters)
        assertEquals(RouteGuidanceType.CROSSWALK, segments[1].guidanceType)
        assertEquals(listOf(RouteGuidanceFeature.SIGNAL), segments[1].guidanceFeatures)
        assertEquals(100, segments[1].guidanceDistanceMeters)
        assertEquals(RouteGuidanceType.STRAIGHT, segments[2].guidanceType)
        assertEquals(180, segments[2].guidanceDistanceMeters)
        assertEquals(RouteGuidanceType.DESTINATION, segments[3].guidanceType)
        assertEquals(0, segments[3].guidanceDistanceMeters)
        assertEquals(600, segments[3].durationFromRouteStartSeconds)
    }

    @Test
    fun `turn guidance distance uses previous event gap instead of leg cumulative distance`() {
        val result =
            RouteSearchResponseDto(
                routes =
                    listOf(
                        RouteDto(
                            routeId = "walk-cumulative-turn",
                            transportMode = "WALK",
                            routeOption = "SAFE",
                            title = "Cumulative Turn Route",
                            distanceMeter = 1_200.0,
                            estimatedTimeMinute = 20,
                            legs =
                                listOf(
                                    RouteLegDto(
                                        sequence = 1,
                                        type = "WALK",
                                        role = "WALK_ONLY",
                                        distanceMeter = 1_200.0,
                                        guidanceEvents =
                                            listOf(
                                                RouteGuidanceEventDto(
                                                    sequence = 1,
                                                    type = "STRAIGHT",
                                                    direction = "STRAIGHT",
                                                    distanceFromLegStartMeter = 920.0,
                                                    durationFromRouteStartSecond = 600,
                                                ),
                                                RouteGuidanceEventDto(
                                                    sequence = 2,
                                                    type = null,
                                                    direction = "TURN_LEFT",
                                                    distanceFromLegStartMeter = 1_000.0,
                                                    durationFromRouteStartSecond = 660,
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            ).toDomain(
                query = testRouteSearchQuery(routeOptions = listOf(RouteOption.SAFE)),
                geometryParser = geometryParser,
            )

        val turnSegment = result.routes.single().segments[1]

        assertEquals(RouteGuidanceDirection.TURN_LEFT, turnSegment.guidanceDirection)
        assertEquals(80, turnSegment.distanceMeters)
        assertEquals(80, turnSegment.guidanceDistanceMeters)
    }

    @Test
    fun `parse helper functions read session refresh reroute and rating envelopes`() {
        val selected =
            parseRouteSelectResponseDto(
                """
                {
                  "status": "S2000",
                  "data": {
                    "sessionId": "session-select-1",
                    "totalDistanceMeter": 950.0,
                    "totalDurationSecond": 960
                  }
                }
                """.trimIndent(),
            )
        val session =
            parseRouteSessionResponseDto(
                """
                {
                  "status": "S2000",
                  "data": {
                    "sessionId": "session-1"
                  }
                }
                """.trimIndent(),
            )
        val refresh =
            parseRouteTransitRefreshResponseDto(
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
                """.trimIndent(),
            )
        val reroute =
            parseRouteRerouteResponseDto(
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
                """.trimIndent(),
            )
        val rating =
            parseRouteRatingResponseDto(
                """
                {
                  "status": "S2000",
                  "data": {
                    "ratingId": 77
                  }
                }
                """.trimIndent(),
            )

        assertEquals("session-select-1", selected.sessionId)
        assertEquals(950.0, selected.totalDistanceMeter ?: -1.0, 0.0)
        assertEquals(960, selected.totalDurationSecond)
        assertEquals("session-1", session.sessionId)
        assertEquals("BUS", refresh.type)
        assertEquals("ARRIVING_SOON", refresh.arrivalStatus)
        assertEquals("100", refresh.transits.single().routeNo)
        assertEquals("walk_rt_reroute_1", reroute.route?.routeId)
        assertEquals(77L, rating.ratingId)
    }

    @Test
    fun `guidance event crosswalk variants map to compatibility segment safety flags`() {
        val result =
            RouteSearchResponseDto(
                routes =
                    listOf(
                        RouteDto(
                            routeOption = "SAFE",
                            title = "Accessible Walk",
                            distanceMeter = 90.0,
                            estimatedTimeMinute = 2,
                            legs =
                                listOf(
                                    RouteLegDto(
                                        sequence = 1,
                                        type = "WALK",
                                        role = "WALK_ONLY",
                                        instruction = "Walk to destination",
                                        distanceMeter = 90.0,
                                        guidanceEvents =
                                            listOf(
                                                RouteGuidanceEventDto(
                                                    sequence = 1,
                                                    type = "CROSSWALK",
                                                    distanceFromLegStartMeter = 30.0,
                                                    geometry = "POINT(129.076000 35.179900)",
                                                ),
                                                RouteGuidanceEventDto(
                                                    sequence = 2,
                                                    type = "CROSSWALK_SIGNAL",
                                                    distanceFromLegStartMeter = 60.0,
                                                    geometry = "POINT(129.077000 35.180500)",
                                                ),
                                                RouteGuidanceEventDto(
                                                    sequence = 3,
                                                    type = "CROSSWALK_AUDIO",
                                                    distanceFromLegStartMeter = 90.0,
                                                    geometry = "POINT(129.078000 35.181000)",
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            ).toDomain(
                query = testRouteSearchQuery(routeOptions = listOf(RouteOption.SAFE)),
                geometryParser = geometryParser,
            )

        val flags = result.routes.single().segments.map { segment -> segment.safetyFlags }
        assertEquals(3, flags.size)
        assertTrue(flags[0].hasCrosswalk)
        assertFalse(flags[0].hasSignal)
        assertFalse(flags[0].hasAudioSignal)
        assertTrue(flags[1].hasCrosswalk)
        assertTrue(flags[1].hasSignal)
        assertFalse(flags[1].hasAudioSignal)
        assertTrue(flags[2].hasCrosswalk)
        assertTrue(flags[2].hasSignal)
        assertTrue(flags[2].hasAudioSignal)
    }

    @Test
    fun `toDomain reuses route geometry parse result for legacy segment fallback`() {
        val routeGeometry =
            "LINESTRING(129.075600 35.179600, 129.076000 35.179900, 129.076500 35.180200)"
        val segmentGeometry =
            "LINESTRING(129.075600 35.179600, 129.076000 35.179900)"
        val parseCounts = linkedMapOf<String?, Int>()
        val countingParser =
            object : RouteGeometryParser {
                private val delegate = DefaultRouteGeometryParser()

                override fun parse(geometry: String?): RouteGeometryParseResult {
                    parseCounts[geometry] = (parseCounts[geometry] ?: 0) + 1
                    return delegate.parse(geometry)
                }
            }

        val result =
            RouteSearchResponseDto(
                routes =
                    listOf(
                        RouteDto(
                            routeId = "walk_rt_safe_legacy",
                            transportMode = "WALK",
                            routeOption = "SAFE",
                            title = "Legacy Walk",
                            distanceMeter = 180.0,
                            estimatedTimeMinute = 3,
                            geometry = routeGeometry,
                            segments =
                                listOf(
                                    RouteSegmentDto(
                                        sequence = 1,
                                        geometry = segmentGeometry,
                                        distanceMeter = 180,
                                        guidanceMessage = "Continue straight",
                                    ),
                                ),
                        ),
                    ),
            ).toDomain(
                query = testRouteSearchQuery(routeOptions = listOf(RouteOption.SAFE)),
                geometryParser = countingParser,
            )

        assertEquals(1, parseCounts[routeGeometry])
        assertEquals(1, parseCounts[segmentGeometry])
        assertTrue(result.routes.single().geometry.isRenderable)
        assertTrue(result.routes.single().legs.single().polyline.isRenderable)
    }
}

private fun testRouteSearchQuery(routeOptions: List<RouteOption>): com.ssafy.e102.eumgil.core.model.RouteSearchQuery =
    com.ssafy.e102.eumgil.core.model.RouteSearchQuery(
        origin =
            RouteWaypoint(
                name = "Origin",
                coordinate =
                    GeoCoordinate(
                        latitude = 35.1796,
                        longitude = 129.0756,
                    ),
            ),
        destination =
            RouteWaypoint(
                name = "Destination",
                coordinate =
                    GeoCoordinate(
                        latitude = 35.1151,
                        longitude = 129.0414,
                    ),
            ),
        requestedOptions = routeOptions,
    )
