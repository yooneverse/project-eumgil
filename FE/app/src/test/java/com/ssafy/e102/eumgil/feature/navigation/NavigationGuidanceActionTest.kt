package com.ssafy.e102.eumgil.feature.navigation

import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.RouteCandidate
import com.ssafy.e102.eumgil.core.model.RouteLeg
import com.ssafy.e102.eumgil.core.model.RouteLegRole
import com.ssafy.e102.eumgil.core.model.RouteLegType
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.core.model.RoutePolyline
import com.ssafy.e102.eumgil.core.model.RouteSegment
import com.ssafy.e102.eumgil.core.model.RouteSegmentSafetyFlags
import com.ssafy.e102.eumgil.core.model.RouteSummary
import com.ssafy.e102.eumgil.core.model.RouteTransitStop
import com.ssafy.e102.eumgil.core.model.RouteTransportMode
import com.ssafy.e102.eumgil.feature.route.RouteDetailStepKind
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationGuidanceActionTest {
    @Test
    fun `guidance action reuses route detail step classification`() {
        assertEquals(
            NavigationGuidanceAction.CROSSWALK,
            RouteSegment(
                sequence = 1,
                guidanceMessage = "신호를 확인한 뒤 횡단보도를 건너세요.",
                safetyFlags = RouteSegmentSafetyFlags(hasCrosswalk = true),
            ).toNavigationGuidanceAction(),
        )
        assertEquals(
            NavigationGuidanceAction.TURN_LEFT,
            RouteSegment(
                sequence = 2,
                guidanceMessage = "120m 앞에서 좌회전하세요.",
            ).toNavigationGuidanceAction(),
        )
        assertEquals(
            NavigationGuidanceAction.TURN_RIGHT,
            RouteSegment(
                sequence = 3,
                guidanceMessage = "350m 앞에서 오른쪽 방향으로 이동하세요.",
            ).toNavigationGuidanceAction(),
        )
        assertEquals(
            NavigationGuidanceAction.ELEVATOR,
            RouteSegment(
                sequence = 4,
                guidanceMessage = "엘리베이터를 타고 이동하세요.",
            ).toNavigationGuidanceAction(),
        )
        assertEquals(
            NavigationGuidanceAction.STRAIGHT,
            RouteSegment(
                sequence = 5,
                guidanceMessage = "목적지까지 계속 이동하세요.",
            ).toNavigationGuidanceAction(),
        )
    }

    @Test
    fun `guidance action keeps route detail safety types instead of folding them into straight`() {
        assertEquals(
            "ELEVATOR",
            RouteSegment(
                sequence = 1,
                guidanceMessage = "Take the elevator to continue.",
            ).toNavigationGuidanceAction().name,
        )
        assertEquals(
            "TACTILE_GUIDE",
            RouteSegment(
                sequence = 2,
                safetyFlags = RouteSegmentSafetyFlags(hasBrailleBlock = true),
            ).toNavigationGuidanceAction().name,
        )
        assertEquals(
            "CONSTRUCTION",
            RouteSegment(
                sequence = 3,
                guidanceMessage = "Construction ahead on this segment.",
            ).toNavigationGuidanceAction().name,
        )
        assertEquals(
            "CURB_GAP",
            RouteSegment(
                sequence = 4,
                safetyFlags = RouteSegmentSafetyFlags(hasCurbGap = true),
            ).toNavigationGuidanceAction().name,
        )
        assertEquals(
            "STAIRS",
            RouteSegment(
                sequence = 5,
                safetyFlags = RouteSegmentSafetyFlags(hasStairs = true),
            ).toNavigationGuidanceAction().name,
        )
        assertEquals("FALLBACK", RouteDetailStepKind.FALLBACK.toNavigationGuidanceAction().name)
    }

    @Test
    fun `arrival guidance action uses destination pin instead of straight direction`() {
        assertEquals(NavigationGuidanceAction.ARRIVAL, RouteDetailStepKind.ARRIVAL.toNavigationGuidanceAction())
        assertEquals(R.drawable.ic_navigation_rail_destination_pin, NavigationGuidanceAction.ARRIVAL.iconRes())
    }

    @Test
    fun `alighting guidance action uses a dedicated transit exit icon`() {
        assertEquals(NavigationGuidanceAction.ALIGHT, RouteDetailStepKind.ALIGHT.toNavigationGuidanceAction())
        assertEquals(R.drawable.ic_route_alight, NavigationGuidanceAction.ALIGHT.iconRes())
    }

    @Test
    fun `guidance action icons reuse existing route detail assets for safety types`() {
        assertEquals(R.drawable.ic_route_elevator, NavigationGuidanceAction.valueOf("ELEVATOR").iconRes())
        assertEquals(
            R.drawable.ic_route_tactile_blocks,
            NavigationGuidanceAction.valueOf("TACTILE_GUIDE").iconRes(),
        )
        assertEquals(
            R.drawable.ic_route_construction,
            NavigationGuidanceAction.valueOf("CONSTRUCTION").iconRes(),
        )
        assertEquals(R.drawable.ic_status_warning, NavigationGuidanceAction.valueOf("CURB_GAP").iconRes())
        assertEquals(R.drawable.ic_route_stairs, NavigationGuidanceAction.valueOf("STAIRS").iconRes())
        assertEquals(R.drawable.ic_status_help_circle, NavigationGuidanceAction.valueOf("FALLBACK").iconRes())
    }

    @Test
    fun `hero detail mirrors route detail style copy for english turn guidance`() {
        val detail =
            RouteSegment(
                sequence = 1,
                distanceMeters = 120,
                guidanceMessage = "turn right after the crosswalk",
            ).toNavigationHeroDetail()

        assertEquals("우회전", detail.title)
        assertEquals("120 m 정도 이동 후 오른쪽 방향으로 이동하세요.", detail.description)
        assertEquals(NavigationGuidanceAction.TURN_RIGHT, detail.guidanceAction)
    }

    @Test
    fun `hero detail keeps generated crosswalk copy instead of raw segment text`() {
        val detail =
            RouteSegment(
                sequence = 2,
                distanceMeters = 80,
                guidanceMessage = "횡단보도를 건너 엘리베이터 방향으로 이동하세요.",
                safetyFlags = RouteSegmentSafetyFlags(hasCrosswalk = true),
            ).toNavigationHeroDetail()

        assertEquals("횡단보도 건너기", detail.title)
        assertEquals("주변 차량이 멈췄는지 확인한 뒤 횡단보도를 조심해서 건너세요.", detail.description)
        assertEquals(NavigationGuidanceAction.CROSSWALK, detail.guidanceAction)
    }

    @Test
    fun `transit route uses bus and subway actions from source legs`() {
        val route = transitRouteCandidateForNavigationTest()
        val busSegment = route.segments[0]
        val subwaySegment = route.segments[1]

        assertEquals(NavigationGuidanceAction.BUS, route.toNavigationGuidanceAction(busSegment))
        assertEquals(NavigationGuidanceAction.SUBWAY, route.toNavigationGuidanceAction(subwaySegment))

        val busDetail = route.toNavigationHeroDetail(busSegment)
        assertEquals("버스 탑승", busDetail.title)
        assertEquals("시청 정류장에서 1001번 버스를 타고 이동하세요.", busDetail.description)
        assertEquals(NavigationGuidanceAction.BUS, busDetail.guidanceAction)

        val subwayDetail = route.toNavigationHeroDetail(subwaySegment)
        assertEquals("지하철 탑승", subwayDetail.title)
        assertEquals("시청역에서 2호선 지하철을 타고 이동하세요.", subwayDetail.description)
        assertEquals(NavigationGuidanceAction.SUBWAY, subwayDetail.guidanceAction)
    }
}

private fun transitRouteCandidateForNavigationTest(): RouteCandidate {
    val previewPoints =
        listOf(
            GeoCoordinate(35.1796, 129.0756),
            GeoCoordinate(35.1788, 129.0738),
            GeoCoordinate(35.1776, 129.0708),
        )

    return RouteCandidate(
        routeOption = RouteOption.RECOMMENDED,
        title = "Transit Test Route",
        transportMode = RouteTransportMode.PUBLIC_TRANSIT,
        summary =
            RouteSummary(
                distanceMeters = 3_200,
                estimatedTimeMinutes = 24,
                riskLevel = com.ssafy.e102.eumgil.core.model.RouteRiskLevel.LOW,
            ),
        legs =
            listOf(
                RouteLeg(
                    sequence = 1,
                    type = RouteLegType.BUS,
                    role = RouteLegRole.TRANSIT,
                    routeNo = "1001",
                    boardingStop = RouteTransitStop(name = "시청 정류장", coordinate = previewPoints[0]),
                    polyline = RoutePolyline(points = previewPoints.take(2)),
                ),
                RouteLeg(
                    sequence = 2,
                    type = RouteLegType.SUBWAY,
                    role = RouteLegRole.TRANSIT,
                    routeNo = "2호선",
                    boardingStop = RouteTransitStop(name = "시청역", coordinate = previewPoints[1]),
                    polyline = RoutePolyline(points = previewPoints.drop(1)),
                ),
            ),
        segments =
            listOf(
                RouteSegment(
                    sequence = 1,
                    distanceMeters = 1_200,
                    guidanceMessage = "Next transit segment",
                    sourceLegSequence = 1,
                ),
                RouteSegment(
                    sequence = 2,
                    distanceMeters = 2_000,
                    guidanceMessage = "Next transit segment",
                    sourceLegSequence = 2,
                ),
            ),
    )
}
