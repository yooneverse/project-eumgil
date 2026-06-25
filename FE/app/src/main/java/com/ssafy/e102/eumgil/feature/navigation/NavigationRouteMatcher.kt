package com.ssafy.e102.eumgil.feature.navigation

import com.ssafy.e102.eumgil.core.location.LocationSnapshot
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.RouteCandidate
import kotlin.math.abs

internal enum class NavigationRouteMatchState {
    OnRoute,
    Suspicious,
    OffRoute,
}

internal data class NavigationRouteMatchResult(
    val rawCoordinate: GeoCoordinate,
    val matchedCoordinate: GeoCoordinate,
    val rawProjection: RoutePolylineProjection,
    val matchedProjection: RoutePolylineProjection,
    val state: NavigationRouteMatchState,
    val confidence: Double,
    val recordedAtEpochMillis: Long,
)

internal class NavigationRouteMatcher(
    private val onRouteDistanceMeters: Double = 15.0,
    private val offRouteDistanceMeters: Double = 40.0,
    private val offRouteMaxAccuracyMeters: Float = 25f,
    private val suspiciousProjectionMaxDistanceMeters: Double = 75.0,
    private val progressBacktrackToleranceMeters: Double = 5.0,
    private val impossibleProgressSpeedMetersPerSecond: Double = 8.0,
    private val walkingSpeedCeilingMetersPerSecond: Float = 3.0f,
) {
    fun match(
        route: RouteCandidate,
        snapshot: LocationSnapshot,
        previousMatch: NavigationRouteMatchResult?,
    ): NavigationRouteMatchResult? {
        val rawCoordinate = GeoCoordinate(latitude = snapshot.latitude, longitude = snapshot.longitude)
        val routePoints = route.navigationPolylinePoints()
        val rawProjection = projectOntoPolylineMeters(current = rawCoordinate, polyline = routePoints) ?: return null
        val state =
            resolveState(
                snapshot = snapshot,
                rawProjection = rawProjection,
                previousMatch = previousMatch,
            )
        val matchedCoordinate =
            when (state) {
                NavigationRouteMatchState.OnRoute -> rawProjection.projectedCoordinate
                NavigationRouteMatchState.Suspicious ->
                    resolveSuspiciousMatchedCoordinate(
                        rawProjection = rawProjection,
                        previousMatch = previousMatch,
                    )
                NavigationRouteMatchState.OffRoute -> rawCoordinate
            }
        val matchedProjection =
            if (matchedCoordinate == rawProjection.projectedCoordinate) {
                rawProjection
            } else {
                projectOntoPolylineMeters(current = matchedCoordinate, polyline = routePoints) ?: rawProjection
            }

        return NavigationRouteMatchResult(
            rawCoordinate = rawCoordinate,
            matchedCoordinate = matchedCoordinate,
            rawProjection = rawProjection,
            matchedProjection = matchedProjection,
            state = state,
            confidence = resolveConfidence(snapshot = snapshot, projection = rawProjection, state = state),
            recordedAtEpochMillis = snapshot.recordedAtEpochMillis,
        )
    }

    private fun resolveState(
        snapshot: LocationSnapshot,
        rawProjection: RoutePolylineProjection,
        previousMatch: NavigationRouteMatchResult?,
    ): NavigationRouteMatchState {
        val distanceToRouteMeters = rawProjection.distanceToPolylineMeters
        val accuracyMeters = snapshot.accuracyMeters
        if (
            accuracyMeters != null &&
            accuracyMeters <= offRouteMaxAccuracyMeters &&
            distanceToRouteMeters >= offRouteDistanceMeters
        ) {
            return NavigationRouteMatchState.OffRoute
        }
        if (distanceToRouteMeters > onRouteDistanceMeters) {
            return NavigationRouteMatchState.Suspicious
        }
        if (hasUnreliableProgressJump(snapshot = snapshot, rawProjection = rawProjection, previousMatch = previousMatch)) {
            return NavigationRouteMatchState.Suspicious
        }
        return NavigationRouteMatchState.OnRoute
    }

    private fun hasUnreliableProgressJump(
        snapshot: LocationSnapshot,
        rawProjection: RoutePolylineProjection,
        previousMatch: NavigationRouteMatchResult?,
    ): Boolean {
        previousMatch ?: return false
        val elapsedSeconds =
            (snapshot.recordedAtEpochMillis - previousMatch.recordedAtEpochMillis).div(1_000.0)
        if (elapsedSeconds <= 0.0) return true

        val progressDeltaMeters =
            rawProjection.distanceAlongPolylineMeters - previousMatch.matchedProjection.distanceAlongPolylineMeters
        if (progressDeltaMeters < -progressBacktrackToleranceMeters) return true

        val observedProgressSpeed = abs(progressDeltaMeters) / elapsedSeconds
        if (observedProgressSpeed <= impossibleProgressSpeedMetersPerSecond) return false

        val reportedSpeed = snapshot.speedMetersPerSecond
        return rawProjection.distanceToPolylineMeters > onRouteDistanceMeters ||
            reportedSpeed == null ||
            reportedSpeed <= walkingSpeedCeilingMetersPerSecond
    }

    private fun resolveSuspiciousMatchedCoordinate(
        rawProjection: RoutePolylineProjection,
        previousMatch: NavigationRouteMatchResult?,
    ): GeoCoordinate =
        if (
            previousMatch != null &&
            rawProjection.distanceToPolylineMeters > suspiciousProjectionMaxDistanceMeters
        ) {
            previousMatch.matchedCoordinate
        } else {
            rawProjection.projectedCoordinate
        }

    private fun resolveConfidence(
        snapshot: LocationSnapshot,
        projection: RoutePolylineProjection,
        state: NavigationRouteMatchState,
    ): Double {
        val distanceScore =
            (1.0 - (projection.distanceToPolylineMeters / offRouteDistanceMeters))
                .coerceIn(0.0, 1.0)
        val accuracyScore =
            snapshot.accuracyMeters
                ?.let { accuracy -> (1.0 - (accuracy / 50.0)).coerceIn(0.0, 1.0) }
                ?: 0.7
        val stateMultiplier =
            when (state) {
                NavigationRouteMatchState.OnRoute -> 1.0
                NavigationRouteMatchState.Suspicious -> 0.55
                NavigationRouteMatchState.OffRoute -> 0.0
            }
        return (distanceScore * 0.7 + accuracyScore * 0.3) * stateMultiplier
    }
}
