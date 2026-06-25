package com.ssafy.e102.eumgil.feature.lowvision

import com.ssafy.e102.eumgil.core.location.LocationSnapshot
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.RouteCandidate
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.core.model.RoutePolyline
import com.ssafy.e102.eumgil.core.model.RoutePreviewModel
import com.ssafy.e102.eumgil.core.model.RouteRiskLevel
import com.ssafy.e102.eumgil.core.model.RouteSearchData
import com.ssafy.e102.eumgil.core.model.RouteSearchQuery
import com.ssafy.e102.eumgil.core.model.RouteSearchResult
import com.ssafy.e102.eumgil.core.model.RouteSearchSource
import com.ssafy.e102.eumgil.core.model.RouteSegment
import com.ssafy.e102.eumgil.core.model.RouteSummary
import com.ssafy.e102.eumgil.core.model.RouteWaypoint
import com.ssafy.e102.eumgil.core.model.toRouteWaypointOrNull
import com.ssafy.e102.eumgil.data.repository.DestinationSelectionRepository
import com.ssafy.e102.eumgil.data.repository.RouteRepository
import com.ssafy.e102.eumgil.data.remote.datasource.RouteApiException
import com.ssafy.e102.eumgil.feature.route.RouteNavigationRequest
import com.ssafy.e102.eumgil.feature.route.RouteNavigationSelectionHandoff
import kotlinx.coroutines.CancellationException
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal data class LowVisionNavigationPlan(
    val searchData: RouteSearchData,
    val selectedRoute: RouteCandidate,
)

internal suspend fun RouteRepository.buildLowVisionNavigationPlan(
    destinationSelectionRepository: DestinationSelectionRepository,
    origin: RouteWaypoint = LOW_VISION_DEFAULT_ORIGIN,
): LowVisionNavigationPlan? {
    val destination = destinationSelectionRepository.selectedDestination.value?.toRouteWaypointOrNull() ?: return null
    val walkQuery =
        RouteSearchQuery(
            origin = origin,
            destination = destination,
            requestedOptions = LOW_VISION_WALK_OPTIONS,
        )
    val walkSearchData =
        runCatching {
            getFreshLowVisionWalkRouteSearchData(query = walkQuery)
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            return fallbackLowVisionNavigationPlan(query = walkQuery)
        }
    val resolvedOrigin = walkSearchData.query.origin
    val selectedWalkRoute =
        (walkSearchData.findRoute(RouteOption.SAFE)
            ?: walkSearchData.primaryRoute
            ?: walkSearchData.routes.firstOrNull()
            ?: return fallbackLowVisionNavigationPlan(query = walkSearchData.query))
            .withLowVisionNavigationDefaults(query = walkSearchData.query)
    if (selectedWalkRoute.summary.distanceMeters <= LOW_VISION_TRANSIT_THRESHOLD_METERS) {
        return LowVisionNavigationPlan(
            searchData = walkSearchData,
            selectedRoute = selectedWalkRoute,
        )
    }

    val transitSearchData =
        runCatching {
            getFreshTransitRouteSearchData(
                RouteSearchQuery(
                    origin = resolvedOrigin,
                    destination = destination,
                    requestedOptions = LOW_VISION_TRANSIT_OPTIONS,
                ),
            )
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            return LowVisionNavigationPlan(
                searchData = walkSearchData,
                selectedRoute = selectedWalkRoute,
            )
        }
    val selectedTransitRoute =
        (transitSearchData.findRoute(RouteOption.RECOMMENDED)
            ?: transitSearchData.primaryRoute
            ?: transitSearchData.routes.firstOrNull()
            ?: return LowVisionNavigationPlan(
                searchData = walkSearchData,
                selectedRoute = selectedWalkRoute,
            ))
            .withLowVisionNavigationDefaults(query = transitSearchData.query)

    return LowVisionNavigationPlan(
        searchData = transitSearchData,
        selectedRoute = selectedTransitRoute,
    )
}

internal suspend fun RouteRepository.buildLowVisionNavigationRequest(
    destinationSelectionRepository: DestinationSelectionRepository,
    origin: RouteWaypoint = LOW_VISION_DEFAULT_ORIGIN,
): RouteNavigationRequest? {
    return try {
        val plan =
            buildLowVisionNavigationPlan(
                destinationSelectionRepository = destinationSelectionRepository,
                origin = origin,
            ) ?: return null
        val searchId = plan.searchData.searchId?.takeIf(String::isNotBlank)
        val routeId =
            plan.selectedRoute.serverRouteId?.takeIf(String::isNotBlank)
                ?: plan.selectedRoute.routeId.takeIf(String::isNotBlank)
        val selectionHandoff =
            if (searchId != null && routeId != null) {
                selectLowVisionRouteOrNull(
                    routeId = routeId,
                    searchId = searchId,
                    selectedRoute = plan.selectedRoute,
                )
            } else {
                null
            }
        RouteNavigationRequest(
            origin = plan.searchData.result.origin,
            destination = plan.searchData.result.destination,
            selectedRoute = plan.selectedRoute,
            source = plan.searchData.source,
            selectionHandoff = selectionHandoff,
        )
    } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        null
    }
}

private suspend fun RouteRepository.selectLowVisionRouteOrNull(
    routeId: String,
    searchId: String,
    selectedRoute: RouteCandidate,
): RouteNavigationSelectionHandoff? =
    try {
        val sessionData =
            selectRoute(
                routeId = routeId,
                searchId = searchId,
            )
        RouteNavigationSelectionHandoff(
            searchId = searchId,
            routeId = routeId,
            sessionId = sessionData.sessionId,
            initialRemainingDistanceMeters =
                sessionData.totalDistanceMeters ?: selectedRoute.summary.distanceMeters,
            initialRemainingDurationSeconds =
                sessionData.totalDurationSeconds
                    ?: selectedRoute.summary.durationSeconds
                    ?: selectedRoute.summary.estimatedTimeMinutes * SECONDS_PER_MINUTE,
        )
    } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        if (throwable is RouteApiException && throwable.status == ROUTE_STATUS_SEARCH_EXPIRED) throw throwable
        null
    }

private suspend fun RouteRepository.getFreshLowVisionWalkRouteSearchData(query: RouteSearchQuery): RouteSearchData =
    try {
        getFreshRouteSearchData(query)
    } catch (throwable: Throwable) {
        if (throwable is CancellationException ||
            throwable !is RouteApiException ||
            query.origin.isLowVisionDefaultOrigin()
        ) {
            throw throwable
        }
        getFreshRouteSearchData(query.copy(origin = LOW_VISION_DEFAULT_ORIGIN))
    }

private fun fallbackLowVisionNavigationPlan(query: RouteSearchQuery): LowVisionNavigationPlan {
    val fallbackRoute = query.toLowVisionFallbackRoute()
    return LowVisionNavigationPlan(
        searchData =
            RouteSearchData(
                query = query,
                result =
                    RouteSearchResult(
                        origin = query.origin,
                        destination = query.destination,
                        routes = listOf(fallbackRoute),
                    ),
                source = RouteSearchSource.serverApi(label = LOW_VISION_FALLBACK_SOURCE_LABEL),
            ),
        selectedRoute = fallbackRoute,
    )
}

private fun RouteCandidate.withLowVisionNavigationDefaults(query: RouteSearchQuery): RouteCandidate {
    val resolvedPolyline = lowVisionRoutePolyline(query)
    val resolvedDistanceMeters = lowVisionDistanceMeters(resolvedPolyline)
    val resolvedDurationSeconds = lowVisionDurationSeconds(resolvedDistanceMeters)
    val resolvedEstimatedMinutes =
        summary.estimatedTimeMinutes
            .takeIf { estimatedMinutes -> estimatedMinutes > 0 }
            ?: ceil(resolvedDurationSeconds / SECONDS_PER_MINUTE.toDouble()).toInt().coerceAtLeast(1)
    val resolvedSegments = segments
    val renderableSegmentCount = resolvedSegments.count(RouteSegment::hasRenderablePolyline)

    return copy(
        summary =
            summary.copy(
                distanceMeters = resolvedDistanceMeters,
                estimatedTimeMinutes = resolvedEstimatedMinutes,
                durationSeconds = resolvedDurationSeconds,
            ),
        geometry = resolvedPolyline,
        preview =
            preview.copy(
                polyline = resolvedPolyline,
                segmentCount = resolvedSegments.size,
                renderableSegmentCount = renderableSegmentCount,
                fallbackSegmentCount = (resolvedSegments.size - renderableSegmentCount).coerceAtLeast(0),
            ),
        segments = resolvedSegments,
    )
}

private fun RouteCandidate.lowVisionRoutePolyline(query: RouteSearchQuery): RoutePolyline =
    when {
        preview.polyline.isRenderable -> preview.polyline
        geometry.isRenderable -> geometry
        segments.any(RouteSegment::hasRenderablePolyline) -> segments.toLowVisionRoutePolyline()
        legs.any { leg -> leg.polyline.isRenderable } ->
            RoutePolyline(points = legs.flatMapPolylinePoints { leg -> leg.polyline.points })
        else -> RoutePolyline(points = query.origin.coordinate.toFallbackPath(query.destination.coordinate))
    }

private fun RouteCandidate.lowVisionDistanceMeters(polyline: RoutePolyline): Int =
    summary.distanceMeters
        .takeIf { distanceMeters -> distanceMeters > 0 }
        ?: segments.sumOf(RouteSegment::distanceMeters).takeIf { distanceMeters -> distanceMeters > 0 }
        ?: legs
            .sumOf { leg ->
                leg.distanceMeters ?: leg.steps.sumOf { step -> step.distanceMeters }
            }.takeIf { distanceMeters -> distanceMeters > 0 }
        ?: polyline.points.totalLowVisionPolylineDistanceMeters().roundToInt().takeIf { distanceMeters -> distanceMeters > 0 }
        ?: LOW_VISION_FALLBACK_DISTANCE_METERS

private fun RouteCandidate.lowVisionDurationSeconds(distanceMeters: Int): Int =
    summary.durationSeconds
        ?.takeIf { durationSeconds -> durationSeconds > 0 }
        ?: legs.sumOf { leg -> leg.durationSeconds ?: 0 }.takeIf { durationSeconds -> durationSeconds > 0 }
        ?: summary.estimatedTimeMinutes
            .takeIf { estimatedMinutes -> estimatedMinutes > 0 }
            ?.times(SECONDS_PER_MINUTE)
        ?: ceil(distanceMeters / LOW_VISION_WALKING_SPEED_METERS_PER_SECOND).toInt().coerceAtLeast(SECONDS_PER_MINUTE)

private fun List<RouteSegment>.toLowVisionRoutePolyline(): RoutePolyline =
    RoutePolyline(points = flatMapPolylinePoints { segment -> segment.polyline.points })

private fun <T> List<T>.flatMapPolylinePoints(pointsOf: (T) -> List<GeoCoordinate>): List<GeoCoordinate> =
    buildList {
        this@flatMapPolylinePoints.forEach { item ->
            val points = pointsOf(item)
            if (points.isEmpty()) return@forEach
            if (isEmpty()) {
                addAll(points)
            } else if (last() == points.first()) {
                addAll(points.drop(1))
            } else {
                addAll(points)
            }
        }
    }

private fun List<GeoCoordinate>.totalLowVisionPolylineDistanceMeters(): Double =
    if (size < 2) {
        0.0
    } else {
        zipWithNext().sumOf { (start, end) -> haversineLowVisionDistanceMeters(start, end) }
    }

private fun haversineLowVisionDistanceMeters(
    start: GeoCoordinate,
    end: GeoCoordinate,
): Double {
    val dLat = Math.toRadians(end.latitude - start.latitude)
    val dLon = Math.toRadians(end.longitude - start.longitude)
    val sinHalfLat = sin(dLat / 2)
    val sinHalfLon = sin(dLon / 2)
    val h =
        sinHalfLat.pow(2) +
            cos(Math.toRadians(start.latitude)) * cos(Math.toRadians(end.latitude)) * sinHalfLon.pow(2)
    return 2 * LOW_VISION_EARTH_RADIUS_METERS * atan2(sqrt(h), sqrt(1 - h))
}

private fun RouteSearchQuery.toLowVisionFallbackRoute(): RouteCandidate {
    val path = origin.coordinate.toFallbackPath(destination.coordinate)
    val routePolyline = RoutePolyline(points = path)
    val fallbackDistanceMeters =
        routePolyline.points
            .totalLowVisionPolylineDistanceMeters()
            .roundToInt()
            .takeIf { distanceMeters -> distanceMeters > 0 }
            ?: LOW_VISION_FALLBACK_DISTANCE_METERS
    val fallbackDurationSeconds = lowVisionFallbackDurationSeconds(distanceMeters = fallbackDistanceMeters)
    val segmentDistance = fallbackDistanceMeters / LOW_VISION_FALLBACK_SEGMENT_COUNT
    val segments =
        listOf(
            fallbackSegment(
                sequence = 1,
                points = listOf(path[0], path[1]),
                distanceMeters = segmentDistance,
                guidanceMessage = "Continue straight from the start.",
            ),
            fallbackSegment(
                sequence = 2,
                points = listOf(path[1], path[2]),
                distanceMeters = segmentDistance,
                guidanceMessage = "Continue carefully and check nearby crossings.",
            ),
            fallbackSegment(
                sequence = 3,
                points = listOf(path[2], path[3]),
                distanceMeters =
                    fallbackDistanceMeters -
                        segmentDistance * (LOW_VISION_FALLBACK_SEGMENT_COUNT - 1),
                guidanceMessage = "Continue toward the destination.",
            ),
        )
    return RouteCandidate(
        routeId = LOW_VISION_FALLBACK_ROUTE_ID,
        routeOption = RouteOption.SAFE,
        title = LOW_VISION_FALLBACK_ROUTE_TITLE,
        summary =
            RouteSummary(
                distanceMeters = fallbackDistanceMeters,
                estimatedTimeMinutes = ceil(fallbackDurationSeconds / SECONDS_PER_MINUTE.toDouble()).toInt(),
                riskLevel = RouteRiskLevel.LOW,
                durationSeconds = fallbackDurationSeconds,
            ),
        geometry = routePolyline,
        preview =
            RoutePreviewModel(
                polyline = routePolyline,
                segmentCount = segments.size,
                renderableSegmentCount = segments.count(RouteSegment::hasRenderablePolyline),
            ),
        segments = segments,
    )
}

private fun lowVisionFallbackDurationSeconds(distanceMeters: Int): Int =
    ceil(distanceMeters / LOW_VISION_WALKING_SPEED_METERS_PER_SECOND)
        .toInt()
        .coerceAtLeast(SECONDS_PER_MINUTE)

private fun fallbackSegment(
    sequence: Int,
    points: List<GeoCoordinate>,
    distanceMeters: Int,
    guidanceMessage: String,
): RouteSegment =
    RouteSegment(
        sequence = sequence,
        polyline = RoutePolyline(points = points),
        distanceMeters = distanceMeters,
        riskLevel = RouteRiskLevel.LOW,
        guidanceMessage = guidanceMessage,
    )

private fun GeoCoordinate.toFallbackPath(destination: GeoCoordinate): List<GeoCoordinate> =
    listOf(
        this,
        interpolateTo(destination, fraction = 0.33),
        interpolateTo(destination, fraction = 0.66),
        destination,
    )

private fun GeoCoordinate.interpolateTo(
    destination: GeoCoordinate,
    fraction: Double,
): GeoCoordinate =
    GeoCoordinate(
        latitude = latitude + (destination.latitude - latitude) * fraction,
        longitude = longitude + (destination.longitude - longitude) * fraction,
    )

private fun RouteWaypoint.isLowVisionDefaultOrigin(): Boolean =
    coordinate == LOW_VISION_DEFAULT_ORIGIN.coordinate

internal fun LocationSnapshot?.toLowVisionRouteOriginWaypointOrNull(): RouteWaypoint? =
    this?.let { snapshot ->
        RouteWaypoint(
            name = "\uD604\uC7AC \uC704\uCE58",
            address = "\uD604\uC7AC \uC704\uCE58",
            coordinate = GeoCoordinate(latitude = snapshot.latitude, longitude = snapshot.longitude),
        )
    }

internal fun LocationSnapshot?.toLowVisionRouteOriginWaypoint(): RouteWaypoint =
    toLowVisionRouteOriginWaypointOrNull() ?: LOW_VISION_DEFAULT_ORIGIN

private val LOW_VISION_DEFAULT_ORIGIN =
    RouteWaypoint(
        name = "현재 위치",
        address = "기본 출발지",
        coordinate = GeoCoordinate(latitude = 35.1796, longitude = 129.0756),
    )

private const val LOW_VISION_TRANSIT_THRESHOLD_METERS = 750
private const val LOW_VISION_FALLBACK_DISTANCE_METERS = 600
private const val LOW_VISION_FALLBACK_DURATION_SECONDS = 600
private const val LOW_VISION_FALLBACK_SEGMENT_COUNT = 3
private const val LOW_VISION_WALKING_SPEED_METERS_PER_SECOND = 1.0
private const val LOW_VISION_EARTH_RADIUS_METERS = 6_371_000.0
private const val LOW_VISION_FALLBACK_ROUTE_ID = "low-vision-fallback-route"
private const val LOW_VISION_FALLBACK_ROUTE_TITLE = "Low vision route"
private const val LOW_VISION_FALLBACK_SOURCE_LABEL = "Low vision fallback route"
private const val SECONDS_PER_MINUTE = 60
private const val ROUTE_STATUS_SEARCH_EXPIRED = "RT4041"
private val LOW_VISION_WALK_OPTIONS = listOf(RouteOption.SAFE, RouteOption.SHORTEST)
private val LOW_VISION_TRANSIT_OPTIONS =
    listOf(
        RouteOption.RECOMMENDED,
        RouteOption.MIN_TRANSFER,
        RouteOption.MIN_WALK,
    )
