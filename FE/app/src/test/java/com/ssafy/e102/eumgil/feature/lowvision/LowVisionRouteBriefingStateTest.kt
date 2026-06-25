package com.ssafy.e102.eumgil.feature.lowvision

import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.RouteCandidate
import com.ssafy.e102.eumgil.core.model.RouteLeg
import com.ssafy.e102.eumgil.core.model.RouteLegType
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.core.model.RouteRiskLevel
import com.ssafy.e102.eumgil.core.model.RouteSearchQuery
import com.ssafy.e102.eumgil.core.model.RouteSegment
import com.ssafy.e102.eumgil.core.model.RouteStep
import com.ssafy.e102.eumgil.core.model.RouteSummary
import com.ssafy.e102.eumgil.core.model.RouteWaypoint
import com.ssafy.e102.eumgil.data.route.DefaultRouteGeometryParser
import com.ssafy.e102.eumgil.data.route.parseRouteSearchResponseDto
import com.ssafy.e102.eumgil.data.route.toDomain
import org.junit.Assert.assertEquals
import org.junit.Test

class LowVisionRouteBriefingStateTest {
    @Test
    fun `briefing steps are exposed in three step windows`() {
        val steps = (1..7).map(::briefingStep)

        assertEquals(listOf(1, 2, 3), steps.visibleBriefingSteps(0).map { it.sequence })
        assertEquals(listOf(4, 5, 6), steps.visibleBriefingSteps(3).map { it.sequence })
        assertEquals(listOf(7), steps.visibleBriefingSteps(6).map { it.sequence })
    }

    @Test
    fun `briefing step window clamps progress beyond route length to final window`() {
        val steps = (1..7).map(::briefingStep)

        assertEquals(listOf(7), steps.visibleBriefingSteps(99).map { it.sequence })
    }

    @Test
    fun `briefing speech text speaks the current visible step window`() {
        val steps = (1..5).map(::briefingStep)

        assertEquals(
            "경로 브리핑. 4번. 4번 안내. 5번. 5번 안내.",
            steps.briefingSpeechTextFrom(startIndex = 3),
        )
    }

    @Test
    fun `route segment briefing instruction keeps only distance and core action`() {
        assertEquals(
            "100m \uD6C4 \uC9C1\uC9C4",
            RouteSegment(
                sequence = 4,
                distanceMeters = 100,
                guidanceMessage = "100\uBBF8\uD130 \uC9C1\uC9C4 \uD6C4 \uD6A1\uB2E8\uBCF4\uB3C4\uB97C \uAC74\uB108\uC138\uC694",
            ).toCompactBriefingInstruction(),
        )
        assertEquals(
            "40m \uD6C4 \uC6B0\uD68C\uC804",
            RouteSegment(
                sequence = 5,
                distanceMeters = 40,
                guidanceMessage = "\uC624\uB978\uCABD\uC73C\uB85C \uC774\uB3D9\uD558\uC138\uC694",
            ).toCompactBriefingInstruction(),
        )
        assertEquals(
            "\uB3C4\uCC29",
            RouteSegment(
                sequence = 6,
                distanceMeters = 0,
                guidanceMessage = "\uB3C4\uCC29\uC9C0 \uC6B0\uCE21",
            ).toCompactBriefingInstruction(),
        )
    }

    @Test
    fun `route segment detailed briefing preserves concrete guidance sentences`() {
        assertEquals(
            "100\uBBF8\uD130 \uC9C1\uC9C4 \uD6C4 \uD6A1\uB2E8\uBCF4\uB3C4\uB97C \uAC74\uB108\uC138\uC694",
            RouteSegment(
                sequence = 4,
                distanceMeters = 100,
                guidanceMessage = "100\uBBF8\uD130 \uC9C1\uC9C4 \uD6C4 \uD6A1\uB2E8\uBCF4\uB3C4\uB97C \uAC74\uB108\uC138\uC694",
            ).toDetailedBriefingInstruction(),
        )
        assertEquals(
            "40m \uC624\uB978\uCABD\uC73C\uB85C \uC774\uB3D9\uD558\uC138\uC694",
            RouteSegment(
                sequence = 5,
                distanceMeters = 40,
                guidanceMessage = "\uC624\uB978\uCABD\uC73C\uB85C \uC774\uB3D9\uD558\uC138\uC694",
            ).toDetailedBriefingInstruction(),
        )
    }

    @Test
    fun `route briefing falls back to leg step elements when route segments are absent`() {
        val route =
            RouteCandidate(
                routeOption = RouteOption.SAFE,
                title = "Safe route",
                summary =
                    RouteSummary(
                        distanceMeters = 160,
                        estimatedTimeMinutes = 3,
                        riskLevel = RouteRiskLevel.LOW,
                    ),
                segments = emptyList(),
                legs =
                    listOf(
                        RouteLeg(
                            sequence = 1,
                            type = RouteLegType.WALK,
                            steps =
                                listOf(
                                    RouteStep(
                                        sequence = 1,
                                        instruction = "Continue straight",
                                        distanceMeters = 120,
                                    ),
                                    RouteStep(
                                        sequence = 2,
                                        instruction = "Turn right",
                                        distanceMeters = 40,
                                    ),
                                ),
                        ),
                    ),
            )

        val steps = route.toLowVisionRouteBriefingSteps()

        assertEquals(listOf(1, 2), steps.map(LowVisionRouteBriefingStepUiState::sequence))
        assertEquals(listOf("120m Continue straight", "40m Turn right"), steps.map { it.instruction })
    }

    @Test
    fun `route briefing preserves guidance event direction and feature details from api payload`() {
        val route =
            parseRouteSearchResponseDto(
                """
                {
                  "status": "S2000",
                  "data": {
                    "routes": [
                      {
                        "routeId": "walk_rt_guidance_detail",
                        "transportMode": "WALK",
                        "routeOption": "SAFE",
                        "title": "Guidance detail route",
                        "distanceMeter": 180.0,
                        "estimatedTimeMinute": 3,
                        "geometry": "LINESTRING(129.075600 35.179600, 129.075800 35.179700, 129.076000 35.179900)",
                        "legs": [
                          {
                            "sequence": 1,
                            "type": "WALK",
                            "role": "WALK_ONLY",
                            "instruction": "Walk to destination",
                            "distanceMeter": 180.0,
                            "guidanceEvents": [
                              {
                                "sequence": 1,
                                "direction": "TURN_LEFT",
                                "distanceFromLegStartMeter": 40.0,
                                "geometry": "POINT(129.075800 35.179700)"
                              },
                              {
                                "sequence": 2,
                                "type": "CROSSWALK",
                                "features": ["AUDIO_SIGNAL"],
                                "distanceFromLegStartMeter": 120.0,
                                "geometry": "POINT(129.075900 35.179800)"
                              },
                              {
                                "sequence": 3,
                                "type": "DESTINATION",
                                "distanceFromLegStartMeter": 180.0,
                                "geometry": "POINT(129.076000 35.179900)"
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ).toDomain(
                query = testLowVisionRouteSearchQuery(),
                geometryParser = DefaultRouteGeometryParser(),
            ).routes.single()

        val steps = route.toLowVisionRouteBriefingSteps()

        assertEquals(
            listOf(
                "40m Turn left",
                "80m Audio signal crosswalk ahead",
                "60m Arrive at destination",
            ),
            steps.map { it.instruction },
        )
        assertEquals(
            listOf(
                LowVisionRouteBriefingStepIcon.TURN,
                LowVisionRouteBriefingStepIcon.TRANSIT,
                LowVisionRouteBriefingStepIcon.STRAIGHT,
            ),
            steps.map { it.icon },
        )
    }

    private fun briefingStep(sequence: Int): LowVisionRouteBriefingStepUiState =
        LowVisionRouteBriefingStepUiState(
            sequence = sequence,
            instruction = "${sequence}번 안내",
            icon = LowVisionRouteBriefingStepIcon.STRAIGHT,
        )

    private fun testLowVisionRouteSearchQuery(): RouteSearchQuery =
        RouteSearchQuery(
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
                            latitude = 35.1799,
                            longitude = 129.0760,
                        ),
                ),
            requestedOptions = listOf(RouteOption.SAFE),
        )
}
