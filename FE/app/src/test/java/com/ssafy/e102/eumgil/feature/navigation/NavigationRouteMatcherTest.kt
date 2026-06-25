package com.ssafy.e102.eumgil.feature.navigation

import com.ssafy.e102.eumgil.core.location.LocationSnapshot
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.RouteCandidate
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.core.model.RoutePolyline
import com.ssafy.e102.eumgil.core.model.RouteRiskLevel
import com.ssafy.e102.eumgil.core.model.RouteSegment
import com.ssafy.e102.eumgil.core.model.RouteSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRouteMatcherTest {
    private val matcher = NavigationRouteMatcher()

    @Test
    fun `near route point is matched as on-route projection`() {
        val result =
            matcher.match(
                route = straightRoute(),
                snapshot = location(latitude = 35.00050, longitude = 129.00005, accuracyMeters = 8f),
                previousMatch = null,
            )

        requireNotNull(result)
        assertEquals(NavigationRouteMatchState.OnRoute, result.state)
        assertTrue(result.rawProjection.distanceToPolylineMeters < 6.0)
        assertEquals(129.0, result.matchedCoordinate.longitude, 0.000001)
    }

    @Test
    fun `middle distance route point is suspicious but still projected for progress`() {
        val result =
            matcher.match(
                route = straightRoute(),
                snapshot = location(latitude = 35.00050, longitude = 129.00022, accuracyMeters = 8f),
                previousMatch = null,
            )

        requireNotNull(result)
        assertEquals(NavigationRouteMatchState.Suspicious, result.state)
        assertTrue(result.rawProjection.distanceToPolylineMeters in 15.0..40.0)
        assertEquals(129.0, result.matchedCoordinate.longitude, 0.000001)
    }

    @Test
    fun `far route point with stable accuracy is off-route`() {
        val result =
            matcher.match(
                route = straightRoute(),
                snapshot = location(latitude = 35.00050, longitude = 129.00050, accuracyMeters = 8f),
                previousMatch = null,
            )

        requireNotNull(result)
        assertEquals(NavigationRouteMatchState.OffRoute, result.state)
        assertTrue(result.rawProjection.distanceToPolylineMeters >= 40.0)
        assertEquals(129.00050, result.matchedCoordinate.longitude, 0.000001)
    }

    @Test
    fun `backward progress jump is suspicious`() {
        val route = straightRoute()
        val previous =
            matcher.match(
                route = route,
                snapshot = location(latitude = 35.00070, longitude = 129.0, recordedAtEpochMillis = 1_000L),
                previousMatch = null,
            )

        val result =
            matcher.match(
                route = route,
                snapshot = location(latitude = 35.00055, longitude = 129.0, recordedAtEpochMillis = 2_000L),
                previousMatch = previous,
            )

        requireNotNull(result)
        assertEquals(NavigationRouteMatchState.Suspicious, result.state)
    }

    @Test
    fun `impossible forward progress jump is suspicious even when it lands on route`() {
        val route = straightRoute()
        val previous =
            matcher.match(
                route = route,
                snapshot = location(latitude = 35.00010, longitude = 129.0, recordedAtEpochMillis = 1_000L),
                previousMatch = null,
            )

        val result =
            matcher.match(
                route = route,
                snapshot = location(latitude = 35.00090, longitude = 129.0, recordedAtEpochMillis = 2_000L),
                previousMatch = previous,
            )

        requireNotNull(result)
        assertEquals(NavigationRouteMatchState.Suspicious, result.state)
    }

    private fun straightRoute(): RouteCandidate =
        RouteCandidate(
            routeId = "route",
            routeOption = RouteOption.SAFE,
            title = "route",
            summary =
                RouteSummary(
                    distanceMeters = 111,
                    estimatedTimeMinutes = 2,
                    riskLevel = RouteRiskLevel.LOW,
                ),
            geometry = RoutePolyline(listOf(START, END)),
            segments =
                listOf(
                    RouteSegment(
                        sequence = 1,
                        polyline = RoutePolyline(listOf(START, END)),
                        distanceMeters = 111,
                    ),
                ),
        )

    private fun location(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float = 8f,
        recordedAtEpochMillis: Long = 1_000L,
    ): LocationSnapshot =
        LocationSnapshot(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            recordedAtEpochMillis = recordedAtEpochMillis,
            speedMetersPerSecond = 1.0f,
        )

    private companion object {
        val START = GeoCoordinate(latitude = 35.0, longitude = 129.0)
        val END = GeoCoordinate(latitude = 35.001, longitude = 129.0)
    }
}
