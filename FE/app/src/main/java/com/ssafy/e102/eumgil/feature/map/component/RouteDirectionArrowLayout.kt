package com.ssafy.e102.eumgil.feature.map.component

internal data class RouteDirectionArrowPlacement<T>(
    val point: T,
    val segmentStart: T,
    val segmentEnd: T,
    val distanceFromStart: Double,
)

internal const val ROUTE_DIRECTION_ARROW_TARGET_SPACING_DP = 28.0
internal const val ROUTE_DIRECTION_ARROW_EDGE_PADDING_DP = 8.0
internal const val ROUTE_DIRECTION_ARROW_LENGTH_DP = 10
internal const val ROUTE_DIRECTION_ARROW_HALF_WIDTH_DP = 5
internal const val ROUTE_DETAIL_OVERLAY_MIN_ZOOM_LEVEL = 16

internal fun shouldShowDetailedRouteOverlay(zoomLevel: Int): Boolean = zoomLevel >= ROUTE_DETAIL_OVERLAY_MIN_ZOOM_LEVEL

internal fun <T> sampleRouteDirectionArrowPlacements(
    points: List<T>,
    intervalDistance: Double,
    edgePaddingDistance: Double,
    minimumVisibleDistance: Double = 0.0,
    minimumPlacementCount: Int = 0,
    measureDistance: (T, T) -> Double,
    interpolatePoint: (T, T, Double) -> T,
): List<RouteDirectionArrowPlacement<T>> {
    if (points.size < 2) return emptyList()
    if (intervalDistance <= 0.0) return emptyList()

    val segments = mutableListOf<RouteDirectionArrowSegment<T>>()
    var totalDistance = 0.0
    points.zipWithNext().forEach { (start, end) ->
        val segmentDistance = measureDistance(start, end)
        if (segmentDistance <= ROUTE_DIRECTION_ARROW_EPSILON) return@forEach
        segments +=
            RouteDirectionArrowSegment(
                start = start,
                end = end,
                startDistance = totalDistance,
                length = segmentDistance,
            )
        totalDistance += segmentDistance
    }

    if (segments.isEmpty()) return emptyList()
    if (totalDistance + ROUTE_DIRECTION_ARROW_EPSILON < minimumVisibleDistance) return emptyList()

    val requestedMinimumPlacementCount = minimumPlacementCount.coerceAtLeast(0)
    val effectiveEdgePaddingDistance =
        if (requestedMinimumPlacementCount > 0) {
            edgePaddingDistance.coerceAtMost(totalDistance / 2.0)
        } else {
            edgePaddingDistance
        }
    val availableDistance = (totalDistance - (effectiveEdgePaddingDistance * 2.0)).coerceAtLeast(0.0)
    val effectiveIntervalDistance =
        if (requestedMinimumPlacementCount > 0 && availableDistance > ROUTE_DIRECTION_ARROW_EPSILON) {
            minOf(intervalDistance, availableDistance / requestedMinimumPlacementCount.toDouble())
        } else {
            intervalDistance
        }

    val minimumRequiredDistance =
        if (requestedMinimumPlacementCount > 0) {
            minimumVisibleDistance
        } else {
            maxOf(
                minimumVisibleDistance,
                intervalDistance + (edgePaddingDistance * 2.0),
            )
        }
    if (totalDistance + ROUTE_DIRECTION_ARROW_EPSILON < minimumRequiredDistance) {
        return emptyList()
    }
    if (requestedMinimumPlacementCount == 1 && availableDistance <= ROUTE_DIRECTION_ARROW_EPSILON) {
        return listOf(
            interpolateRouteDirectionArrowPlacementAtDistance(
                targetDistance = totalDistance / 2.0,
                segments = segments,
                interpolatePoint = interpolatePoint,
            ),
        )
    }

    val firstArrowDistance = effectiveEdgePaddingDistance + (effectiveIntervalDistance / 2.0)
    val lastArrowDistance = totalDistance - effectiveEdgePaddingDistance - (effectiveIntervalDistance / 2.0)
    if (firstArrowDistance > lastArrowDistance + ROUTE_DIRECTION_ARROW_EPSILON) {
        return emptyList()
    }

    val placements = mutableListOf<RouteDirectionArrowPlacement<T>>()
    var segmentIndex = 0
    var targetDistance = firstArrowDistance

    while (targetDistance <= lastArrowDistance + ROUTE_DIRECTION_ARROW_EPSILON) {
        while (
            segmentIndex < segments.lastIndex &&
            targetDistance > segments[segmentIndex].endDistance + ROUTE_DIRECTION_ARROW_EPSILON
        ) {
            segmentIndex += 1
        }

        placements +=
            interpolateRouteDirectionArrowPlacementAtDistance(
                targetDistance = targetDistance,
                segments = segments,
                startSegmentIndex = segmentIndex,
                interpolatePoint = interpolatePoint,
            )
        targetDistance += effectiveIntervalDistance
    }

    return placements
}

private data class RouteDirectionArrowSegment<T>(
    val start: T,
    val end: T,
    val startDistance: Double,
    val length: Double,
) {
    val endDistance: Double
        get() = startDistance + length
}

private fun <T> interpolateRouteDirectionArrowPlacementAtDistance(
    targetDistance: Double,
    segments: List<RouteDirectionArrowSegment<T>>,
    startSegmentIndex: Int = 0,
    interpolatePoint: (T, T, Double) -> T,
): RouteDirectionArrowPlacement<T> {
    var segmentIndex = startSegmentIndex.coerceIn(0, segments.lastIndex)
    while (
        segmentIndex < segments.lastIndex &&
        targetDistance > segments[segmentIndex].endDistance + ROUTE_DIRECTION_ARROW_EPSILON
    ) {
        segmentIndex += 1
    }
    val segment = segments[segmentIndex]
    val distanceIntoSegment = (targetDistance - segment.startDistance).coerceIn(0.0, segment.length)
    val fraction =
        if (segment.length <= ROUTE_DIRECTION_ARROW_EPSILON) {
            0.0
        } else {
            distanceIntoSegment / segment.length
        }
    return RouteDirectionArrowPlacement(
        point = interpolatePoint(segment.start, segment.end, fraction),
        segmentStart = segment.start,
        segmentEnd = segment.end,
        distanceFromStart = targetDistance,
    )
}

private const val ROUTE_DIRECTION_ARROW_EPSILON = 0.0001
