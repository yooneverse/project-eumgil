package com.ssafy.e102.eumgil.feature.map.component

import androidx.compose.runtime.Immutable
import android.util.Log
import com.ssafy.e102.eumgil.BuildConfig
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.feature.map.model.ApprovedReportMarkerData
import com.ssafy.e102.eumgil.feature.map.model.MapCameraSource
import com.ssafy.e102.eumgil.feature.map.model.MapCameraTarget
import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerCategoryType
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerOverlayState
import com.ssafy.e102.eumgil.feature.map.model.approvedReportClickTargetId
import com.ssafy.e102.eumgil.feature.navigation.NavigationMapFocusMode
import com.ssafy.e102.eumgil.feature.navigation.NavigationMapOverlayUiState
import com.ssafy.e102.eumgil.feature.navigation.NavigationMapPointUiState
import com.ssafy.e102.eumgil.feature.navigation.NavigationMapSegmentUiState
import com.ssafy.e102.eumgil.feature.navigation.NavigationSegmentTravelKind
import com.ssafy.e102.eumgil.feature.navigation.NavigationTrackingMode
import com.ssafy.e102.eumgil.feature.navigation.navigationSegmentMarkerId
import com.ssafy.e102.eumgil.feature.route.RoutePreviewMapStatus
import com.ssafy.e102.eumgil.feature.route.RoutePreviewMapUiState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

@Immutable
internal data class MapViewportOverlayState(
    val fallbackCamera: MapViewportFallbackCamera = defaultMapViewportFallbackCamera(),
    val points: List<MapViewportPointOverlay> = emptyList(),
    val polylines: List<MapViewportPolylineOverlay> = emptyList(),
    val shouldAnimateCameraTransition: Boolean = true,
    val fitToProjection: Boolean = true,
)

@Immutable
internal data class MapViewportFallbackCamera(
    val center: MapCoordinate,
    val latitudeSpan: Double,
    val longitudeSpan: Double,
    val bearingDegrees: Double? = null,
)

@Immutable
internal data class MapViewportPointOverlay(
    val overlayId: String,
    val coordinate: MapCoordinate,
    val kind: MapViewportPointKind,
    val tone: MapViewportOverlayTone? = null,
    val categoryType: MapMarkerCategoryType? = null,
    val label: String? = null,
    val contentDescription: String? = null,
    val headingDegrees: Double? = null,
    val isSelected: Boolean = false,
    val includeInProjection: Boolean = true,
    val clickTargetId: String? = null,
    val transitMarker: MapViewportTransitMarker? = null,
    val reportTypeApiValue: String? = null,
)

internal enum class MapViewportPointKind {
    FACILITY,
    HAZARD,
    APPROVED_REPORT,
    ORIGIN,
    DESTINATION,
    CURRENT_LOCATION,
    CURRENT_LOCATION_HEADING,
    SEGMENT_JUNCTION,
    TRANSIT_BUS_STOP,
    TRANSIT_SUBWAY_STATION,
    TRANSIT_TRANSFER,
    CAMERA_FOCUS,
    FOCUS_HALO,
}

@Immutable
internal data class MapViewportTransitMarker(
    val from: MapViewportTransitMarkerLeg,
    val to: MapViewportTransitMarkerLeg? = null,
)

@Immutable
internal data class MapViewportTransitMarkerLeg(
    val kind: MapViewportTransitMarkerKind,
    val label: String? = null,
)

internal enum class MapViewportTransitMarkerKind {
    BUS,
    SUBWAY,
}

@Immutable
internal data class MapViewportPolylineOverlay(
    val overlayId: String,
    val points: List<MapCoordinate>,
    val style: MapViewportPolylineStyle,
    val tone: MapViewportOverlayTone,
    val includeInProjection: Boolean = true,
    val showDirectionArrows: Boolean = true,
) {
    val isRenderable: Boolean
        get() = points.size >= 2
}

internal enum class MapViewportPolylineStyle {
    ROUTE_PREVIEW,
    ROUTE_CONNECTOR,
    ROUTE_BASELINE,
    ACTIVE_SEGMENT,
    FOCUSED_SEGMENT,
}

internal enum class MapViewportOverlayTone {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    NEUTRAL,
    NAVY,
    NAVIGATION_WALK,
    TRANSIT_WALK,
    ERROR,
}

@Immutable
internal data class MapViewportSegmentMarkerPalette(
    val fillColorArgb: Int,
    val strokeColorArgb: Int,
)

internal fun createMapMarkerViewportOverlayState(
    cameraTarget: MapCameraTarget,
    markerOverlayState: MapMarkerOverlayState,
    selectedMarkerId: String?,
    currentLocation: MapCoordinate?,
    currentLocationLabel: String?,
    approvedReportMarkers: List<ApprovedReportMarkerData> = emptyList(),
): MapViewportOverlayState =
    MapViewportOverlayState(
        fallbackCamera = cameraTarget.toViewportFallbackCamera(),
        points =
            buildList {
                add(
                    MapViewportPointOverlay(
                        overlayId = "camera-focus",
                        coordinate = cameraTarget.center,
                        kind = MapViewportPointKind.CAMERA_FOCUS,
                        includeInProjection = false,
                    ),
                )
                currentLocation?.let { coordinate ->
                    add(
                        MapViewportPointOverlay(
                            overlayId = "current-location",
                            coordinate = coordinate,
                            kind = MapViewportPointKind.CURRENT_LOCATION,
                            label = currentLocationLabel,
                            contentDescription = currentLocationLabel,
                            includeInProjection = false,
                        ),
                    )
                }
                addAll(
                    markerOverlayState.visibleMarkers.map { marker ->
                        MapViewportPointOverlay(
                            overlayId = marker.markerId,
                            coordinate = marker.coordinate,
                            kind = MapViewportPointKind.FACILITY,
                            categoryType = marker.categoryType,
                            contentDescription = marker.name,
                            isSelected = marker.markerId == selectedMarkerId,
                            includeInProjection = false,
                            clickTargetId = marker.markerId,
                        )
                    },
                )
                addAll(
                    approvedReportMarkers.map { report ->
                        val clickTargetId = approvedReportClickTargetId(report.reportId)
                        MapViewportPointOverlay(
                            overlayId = clickTargetId,
                            coordinate = report.coordinate.toMapCoordinate(),
                            kind = MapViewportPointKind.APPROVED_REPORT,
                            label = report.reportTypeLabel,
                            contentDescription = "주의 제보: ${report.reportTypeLabel}",
                            includeInProjection = false,
                            clickTargetId = clickTargetId,
                            reportTypeApiValue = report.reportTypeApiValue,
                        )
                    },
                )
            },
    )

internal fun createRoutePreviewViewportOverlayState(
    previewMap: RoutePreviewMapUiState,
    routeTone: MapViewportOverlayTone = previewMap.routeOption.toViewportOverlayTone(),
    routePolylineOverlays: List<MapViewportPolylineOverlay> = emptyList(),
    guidanceMarkers: List<MapViewportPointOverlay> = emptyList(),
    originIsCurrentLocation: Boolean = false,
    focusSelectedGuidanceMarker: Boolean = false,
    showDetailedRouteOverlay: Boolean = true,
): MapViewportOverlayState {
    val visibleGuidanceMarkers =
        guidanceMarkers
            .let { markers ->
                if (showDetailedRouteOverlay) {
                    markers
                } else {
                    markers.filter(MapViewportPointOverlay::isSelected)
                }
            }
            .deduplicateRouteGuidanceMarkers()

    return MapViewportOverlayState(
        points =
            buildList {
                previewMap.originCoordinate?.toOverlayPoint(
                    overlayId = "route-origin",
                    kind =
                        if (originIsCurrentLocation) {
                            MapViewportPointKind.CURRENT_LOCATION
                        } else {
                            MapViewportPointKind.ORIGIN
                        },
                    label = "출발",
                )?.copy(
                    contentDescription =
                        if (originIsCurrentLocation) {
                            "현재 위치 출발"
                        } else {
                            null
                        },
                    includeInProjection = !focusSelectedGuidanceMarker,
                )?.let(::add)
                previewMap.destinationCoordinate?.toOverlayPoint(
                    overlayId = "route-destination",
                    kind = MapViewportPointKind.DESTINATION,
                    label = "도착",
                )?.copy(includeInProjection = !focusSelectedGuidanceMarker)?.let(::add)
                addAll(
                    visibleGuidanceMarkers.map { marker ->
                        if (focusSelectedGuidanceMarker) {
                            marker.copy(includeInProjection = marker.isSelected)
                        } else {
                            marker
                        }
                    },
                )
                if (focusSelectedGuidanceMarker) {
                    visibleGuidanceMarkers
                        .firstOrNull(MapViewportPointOverlay::isSelected)
                        ?.let { selectedMarker ->
                            add(
                                MapViewportPointOverlay(
                                    overlayId = "route-guidance-focus",
                                    coordinate = selectedMarker.coordinate,
                                    kind = MapViewportPointKind.FOCUS_HALO,
                                    includeInProjection = true,
                                ),
                            )
                        }
                }
            },
        polylines =
            routePolylineOverlays.ifEmpty {
                createConnectorPolylineOverlays(
                    overlayIdPrefix = "route-origin-connector",
                    start = previewMap.originCoordinate,
                    end = previewMap.polyline.firstOrNull(),
                    includeInProjection = true,
                ) +
                    MapViewportPolylineOverlay(
                        overlayId = "route-preview",
                        points = previewMap.polyline.map(GeoCoordinate::toMapCoordinate),
                        style = MapViewportPolylineStyle.ROUTE_PREVIEW,
                        tone = routeTone,
                        includeInProjection = !focusSelectedGuidanceMarker,
                        showDirectionArrows = showDetailedRouteOverlay,
                    )
            }.filter(MapViewportPolylineOverlay::isRenderable),
    )
}

private fun createConnectorPolylineOverlays(
    overlayIdPrefix: String,
    start: GeoCoordinate?,
    end: GeoCoordinate?,
    includeInProjection: Boolean,
): List<MapViewportPolylineOverlay> {
    val connectorStart = start ?: return emptyList()
    val connectorEnd = end ?: return emptyList()
    val distanceMeters = haversineDistanceMeters(connectorStart, connectorEnd)
    if (distanceMeters <= ROUTE_CONNECTOR_MIN_DISTANCE_METERS) return emptyList()

    return listOf(
        MapViewportPolylineOverlay(
            overlayId = overlayIdPrefix,
            points =
                listOf(
                    connectorStart.toMapCoordinate(),
                    connectorEnd.toMapCoordinate(),
                ),
            style = MapViewportPolylineStyle.ROUTE_CONNECTOR,
            tone = MapViewportOverlayTone.NEUTRAL,
            includeInProjection = includeInProjection,
            showDirectionArrows = false,
        ),
    )
}

private fun List<MapViewportPointOverlay>.deduplicateRouteGuidanceMarkers(): List<MapViewportPointOverlay> =
    groupBy { marker -> marker.coordinate.deduplicationKey() }
        .values
        .mapNotNull { markers ->
            markers.maxWithOrNull(
                compareBy<MapViewportPointOverlay> { marker -> marker.routeGuidanceMarkerPriority() }
                    .thenBy { marker -> marker.overlayId },
            )
        }

private fun MapCoordinate.deduplicationKey(): Pair<Long, Long> =
    Pair(
        (latitude * COORDINATE_DEDUPLICATION_SCALE).roundToLong(),
        (longitude * COORDINATE_DEDUPLICATION_SCALE).roundToLong(),
    )

private fun MapViewportPointOverlay.routeGuidanceMarkerPriority(): Int =
    when {
        isSelected && transitMarker != null -> 60
        isSelected -> 50
        transitMarker != null -> 40
        kind == MapViewportPointKind.TRANSIT_BUS_STOP || kind == MapViewportPointKind.TRANSIT_SUBWAY_STATION -> 35
        kind == MapViewportPointKind.TRANSIT_TRANSFER -> 30
        kind == MapViewportPointKind.SEGMENT_JUNCTION -> 10
        else -> 20
    }

internal fun createNavigationViewportOverlayState(
    mapOverlay: NavigationMapOverlayUiState,
): MapViewportOverlayState {
    val useFocusedProjection = mapOverlay.mapFocusMode == NavigationMapFocusMode.FOCUSED
    val routePreviewStartOverlayState = mapOverlay.createRoutePreviewStartOverlayStateOrNull()
    val useRoutePreviewStartOverlay = routePreviewStartOverlayState != null
    val useActiveCurrentCamera =
        mapOverlay.mapFocusMode == NavigationMapFocusMode.ACTIVE &&
            mapOverlay.currentLocation != null
    val useActiveFocusFallbackProjection =
        mapOverlay.mapFocusMode == NavigationMapFocusMode.ACTIVE &&
            mapOverlay.currentLocation == null &&
            mapOverlay.focusCoordinate != null
    val currentLocationOverlapsOrigin =
        mapOverlay.origin.isNearCurrentLocation(mapOverlay.currentLocation)
    val currentLocationOverlapsDestination =
        mapOverlay.destination.isNearCurrentLocation(mapOverlay.currentLocation)
    val shouldShowCurrentLocation = mapOverlay.currentLocation != null
    val selectedRoutePoints = mapOverlay.selectedRoutePolyline.map(GeoCoordinate::toMapCoordinate)
    val activeSegmentPoints =
        mapOverlay.activeSegmentPolyline.map(GeoCoordinate::toMapCoordinate)
            .ifEmpty {
                mapOverlay.routeSegments
                    .firstOrNull(NavigationMapSegmentUiState::isActive)
                    ?.polyline
                    ?.map(GeoCoordinate::toMapCoordinate)
                    .orEmpty()
            }
    val focusedSegmentPoints =
        mapOverlay.focusedSegmentPolyline.map(GeoCoordinate::toMapCoordinate)
            .ifEmpty {
                mapOverlay.routeSegments
                    .firstOrNull(NavigationMapSegmentUiState::isFocused)
                    ?.polyline
                    ?.map(GeoCoordinate::toMapCoordinate)
                    .orEmpty()
            }
    val preferredArrowOverlayId =
        resolveNavigationArrowOverlayId(
            selectedRoutePoints = selectedRoutePoints,
            activeSegmentPoints = activeSegmentPoints,
            focusedSegmentPoints = focusedSegmentPoints,
        )
    val preferredDetailedArrowPoints =
        when (preferredArrowOverlayId) {
            "navigation-focused" -> focusedSegmentPoints
            "navigation-active" -> activeSegmentPoints
            else -> emptyList()
        }
    val shouldUseRoutePreviewOnly =
        useRoutePreviewStartOverlay &&
            activeSegmentPoints.isEmpty() &&
            focusedSegmentPoints.isEmpty() &&
            mapOverlay.routeSegments.none(NavigationMapSegmentUiState::isActive)
    val detailedBaselinePolylines =
        if (shouldUseRoutePreviewOnly) {
            emptyList()
        } else {
            mapOverlay.routeSegments.toBaselinePolylineOverlays(
                includeInProjection = false,
                preferredArrowPoints = preferredDetailedArrowPoints,
            )
        }
    val focusedFallbackCoordinate =
        when {
            mapOverlay.focusCoordinate != null -> mapOverlay.focusCoordinate.toMapCoordinate()
            focusedSegmentPoints.isNotEmpty() -> focusedSegmentPoints.first()
            else -> null
        }
    val routePreviewStartPolylines =
        if (shouldUseRoutePreviewOnly) {
            routePreviewStartOverlayState
                ?.polylines
                .orEmpty()
                .filterNot { polyline -> polyline.style == MapViewportPolylineStyle.ROUTE_CONNECTOR }
                .map { polyline -> polyline.copy(includeInProjection = !useActiveCurrentCamera) }
        } else {
            emptyList()
        }
    val overlayState =
        MapViewportOverlayState(
            fallbackCamera =
                when {
                    useActiveCurrentCamera -> {
                        val currentCoordinate = mapOverlay.currentLocation!!.coordinate.toMapCoordinate()
                        currentCoordinate.toCurrentLocationFallbackCamera(
                            bearingDegrees = null,
                        )
                    }
                    useFocusedProjection && focusedFallbackCoordinate != null ->
                        focusedFallbackCoordinate.toFocusedFallbackCamera()
                    else -> defaultMapViewportFallbackCamera()
                },
            shouldAnimateCameraTransition = mapOverlay.shouldAnimateCameraTransition,
            fitToProjection = !useActiveCurrentCamera,
            points =
                buildList {
                    if (shouldShowCurrentLocation) {
                        mapOverlay.currentLocation?.let { point ->
                            add(
                                point.coordinate.toOverlayPoint(
                                    overlayId = "navigation-current",
                                    kind = MapViewportPointKind.CURRENT_LOCATION,
                                    label = "C",
                                    headingDegrees = null,
                                    includeInProjection = useRoutePreviewStartOverlay && !useActiveCurrentCamera,
                                ),
                            )
                        }
                    }
                    if (!currentLocationOverlapsOrigin) {
                        mapOverlay.origin?.let { point ->
                            add(
                                point.coordinate.toOverlayPoint(
                                    overlayId = "navigation-origin",
                                    kind = MapViewportPointKind.ORIGIN,
                                    label = "O",
                                    clickTargetId = mapOverlay.routeSegments.firstOrNull()?.let { navigationSegmentMarkerId(0) },
                                    includeInProjection =
                                        (useRoutePreviewStartOverlay && !useActiveCurrentCamera) ||
                                            (!useActiveCurrentCamera && currentLocationOverlapsOrigin),
                                ),
                            )
                        }
                    }
                    mapOverlay.destination?.let { point ->
                        add(
                            point.coordinate.toOverlayPoint(
                                overlayId = "navigation-destination",
                                kind = MapViewportPointKind.DESTINATION,
                                label = "D",
                                includeInProjection =
                                    (useRoutePreviewStartOverlay && !useActiveCurrentCamera) ||
                                        (!useActiveCurrentCamera && currentLocationOverlapsDestination),
                            ),
                        )
                    }
                    addAll(
                        mapOverlay.routeSegments.toSegmentMarkerOverlays(
                            currentLocation =
                                when {
                                    useActiveCurrentCamera -> mapOverlay.currentLocation?.coordinate
                                    useRoutePreviewStartOverlay -> mapOverlay.origin?.coordinate ?: mapOverlay.currentLocation?.coordinate
                                    else -> null
                                },
                            hideCompletedBeforeActiveIndex =
                                if (useActiveCurrentCamera) {
                                    mapOverlay.routeSegments.indexOfFirst(NavigationMapSegmentUiState::isActive)
                                } else {
                                    null
                                },
                        ),
                    )
                    (mapOverlay.focusCoordinate?.toMapCoordinate() ?: focusedFallbackCoordinate)?.let { coordinate ->
                        add(
                            coordinate.toGeoCoordinate().toOverlayPoint(
                                overlayId = "navigation-focus",
                                kind = MapViewportPointKind.FOCUS_HALO,
                                includeInProjection = useFocusedProjection || useActiveFocusFallbackProjection,
                            ),
                        )
                    }
                },
            polylines =
                buildList<MapViewportPolylineOverlay> {
                    addAll(routePreviewStartPolylines)
                    addAll(detailedBaselinePolylines)
                    if (!shouldUseRoutePreviewOnly && this.none { overlay -> overlay.style == MapViewportPolylineStyle.ROUTE_BASELINE }) {
                        add(
                            MapViewportPolylineOverlay(
                                overlayId = "navigation-route",
                                points = selectedRoutePoints,
                                style = MapViewportPolylineStyle.ROUTE_BASELINE,
                                tone = MapViewportOverlayTone.PRIMARY,
                                includeInProjection = false,
                                showDirectionArrows = preferredArrowOverlayId == "navigation-route",
                            ),
                        )
                    }
                    if (!shouldUseRoutePreviewOnly && detailedBaselinePolylines.isEmpty() && mapOverlay.activeSegmentPolyline != mapOverlay.focusedSegmentPolyline) {
                        add(
                            MapViewportPolylineOverlay(
                                overlayId = "navigation-active",
                                points = activeSegmentPoints,
                                style = MapViewportPolylineStyle.ACTIVE_SEGMENT,
                                tone = mapOverlay.activeSegmentTravelKind.toActiveOverlayTone(),
                                includeInProjection = false,
                                showDirectionArrows = preferredArrowOverlayId == "navigation-active",
                            ),
                        )
                    }
                    if (!shouldUseRoutePreviewOnly && detailedBaselinePolylines.isEmpty()) {
                        add(
                            MapViewportPolylineOverlay(
                                overlayId = "navigation-focused",
                                points = focusedSegmentPoints,
                                style = MapViewportPolylineStyle.FOCUSED_SEGMENT,
                                tone = mapOverlay.focusedSegmentTravelKind.toFocusedOverlayTone(),
                                includeInProjection = false,
                                showDirectionArrows = preferredArrowOverlayId == "navigation-focused",
                            ),
                        )
                    }
                }.filter(MapViewportPolylineOverlay::isRenderable),
        )
    logSegmentJunctionOverlayDebugSummary(mapOverlay, overlayState)
    return overlayState
}

private fun NavigationMapOverlayUiState.createRoutePreviewStartOverlayStateOrNull(): MapViewportOverlayState? {
    if (!shouldUseRoutePreviewStartOverlay()) return null
    val connectorStartCoordinate = currentLocation?.coordinate ?: origin?.coordinate
    val previewMap =
        RoutePreviewMapUiState(
            status = RoutePreviewMapStatus.READY,
            originCoordinate = connectorStartCoordinate,
            destinationCoordinate = destination?.coordinate,
            polyline = selectedRoutePolyline,
        )
    return createRoutePreviewViewportOverlayState(previewMap = previewMap)
}

private fun NavigationMapOverlayUiState.shouldUseRoutePreviewStartOverlay(): Boolean =
    mapFocusMode == NavigationMapFocusMode.ACTIVE &&
        origin != null &&
        destination != null &&
        selectedRoutePolyline.size >= 2

private fun defaultMapViewportFallbackCamera(): MapViewportFallbackCamera =
    MapViewportFallbackCamera(
        center = MapCoordinate(latitude = DEFAULT_VIEWPORT_CENTER_LATITUDE, longitude = DEFAULT_VIEWPORT_CENTER_LONGITUDE),
        latitudeSpan = MIN_VIEWPORT_LATITUDE_SPAN,
        longitudeSpan = MIN_VIEWPORT_LONGITUDE_SPAN,
    )

private fun MapCoordinate.toCurrentLocationFallbackCamera(bearingDegrees: Double?): MapViewportFallbackCamera =
    MapViewportFallbackCamera(
        center = this,
        latitudeSpan = 0.006,
        longitudeSpan = 0.009,
        bearingDegrees = bearingDegrees,
    )

private fun MapCoordinate.toFocusedFallbackCamera(): MapViewportFallbackCamera =
    MapViewportFallbackCamera(
        center = this,
        latitudeSpan = 0.0035,
        longitudeSpan = 0.0045,
    )

private fun MapCameraTarget.toViewportFallbackCamera(): MapViewportFallbackCamera =
    MapViewportFallbackCamera(
        center = center,
        latitudeSpan =
            when (source) {
                MapCameraSource.DEFAULT_BUSAN -> 0.010
                MapCameraSource.CURRENT_LOCATION -> 0.006
                MapCameraSource.SEARCH_RESULT -> 0.004
            },
        longitudeSpan =
            when (source) {
                MapCameraSource.DEFAULT_BUSAN -> 0.014
                MapCameraSource.CURRENT_LOCATION -> 0.009
                MapCameraSource.SEARCH_RESULT -> 0.007
            },
    )

private fun GeoCoordinate.toOverlayPoint(
    overlayId: String,
    kind: MapViewportPointKind,
    label: String? = null,
    clickTargetId: String? = null,
    headingDegrees: Double? = null,
    includeInProjection: Boolean = true,
): MapViewportPointOverlay =
    MapViewportPointOverlay(
        overlayId = overlayId,
        coordinate = toMapCoordinate(),
        kind = kind,
        label = label,
        contentDescription = label,
        headingDegrees = headingDegrees,
        includeInProjection = includeInProjection,
        clickTargetId = clickTargetId,
    )

private fun GeoCoordinate.toMapCoordinate(): MapCoordinate =
    MapCoordinate(
        latitude = latitude,
        longitude = longitude,
    )

private fun MapCoordinate.toGeoCoordinate(): GeoCoordinate =
    GeoCoordinate(
        latitude = latitude,
        longitude = longitude,
    )

private fun List<MapCoordinate>.resolveLookaheadBearingDegrees(current: MapCoordinate): Double? {
    if (size < 2) return null
    val projection = projectOntoMapPolylineMeters(current = current, polyline = this) ?: return null
    val lookaheadDistanceMeters =
        (projection.distanceAlongPolylineMeters + NAVIGATION_FOLLOW_LOOKAHEAD_METERS)
            .coerceAtMost(projection.totalPolylineDistanceMeters)
    val lookaheadCoordinate = coordinateAtDistanceMeters(lookaheadDistanceMeters) ?: return null
    if (haversineDistanceMeters(current, lookaheadCoordinate) <= 0.5) return null
    return current.bearingDegreesTo(lookaheadCoordinate)
}

private data class MapPolylineProjection(
    val distanceAlongPolylineMeters: Double,
    val totalPolylineDistanceMeters: Double,
)

private fun projectOntoMapPolylineMeters(
    current: MapCoordinate,
    polyline: List<MapCoordinate>,
): MapPolylineProjection? {
    if (polyline.size < 2) return null
    val totalDistanceMeters = polyline.totalDistanceMeters()
    if (totalDistanceMeters <= 0.0) return null

    var cumulativeDistanceMeters = 0.0
    var bestDistanceToPolylineMeters = Double.POSITIVE_INFINITY
    var bestDistanceAlongPolylineMeters = 0.0
    polyline.zipWithNext().forEach { (start, end) ->
        val segmentLengthMeters = haversineDistanceMeters(start, end)
        val projectionRatio = projectRatioOnSegment(point = current, start = start, end = end)
        val projected = start.interpolateTo(end, projectionRatio)
        val distanceToSegmentMeters = haversineDistanceMeters(current, projected)
        if (distanceToSegmentMeters < bestDistanceToPolylineMeters) {
            bestDistanceToPolylineMeters = distanceToSegmentMeters
            bestDistanceAlongPolylineMeters = cumulativeDistanceMeters + segmentLengthMeters * projectionRatio
        }
        cumulativeDistanceMeters += segmentLengthMeters
    }

    return MapPolylineProjection(
        distanceAlongPolylineMeters = bestDistanceAlongPolylineMeters.coerceIn(0.0, totalDistanceMeters),
        totalPolylineDistanceMeters = totalDistanceMeters,
    )
}

private fun List<MapCoordinate>.coordinateAtDistanceMeters(distanceMeters: Double): MapCoordinate? {
    if (isEmpty()) return null
    if (size == 1) return single()
    var cumulativeDistanceMeters = 0.0
    zipWithNext().forEach { (start, end) ->
        val segmentDistanceMeters = haversineDistanceMeters(start, end)
        val nextCumulativeDistanceMeters = cumulativeDistanceMeters + segmentDistanceMeters
        if (distanceMeters <= nextCumulativeDistanceMeters && segmentDistanceMeters > 0.0) {
            val ratio = ((distanceMeters - cumulativeDistanceMeters) / segmentDistanceMeters).coerceIn(0.0, 1.0)
            return start.interpolateTo(end, ratio)
        }
        cumulativeDistanceMeters = nextCumulativeDistanceMeters
    }
    return last()
}

private fun projectRatioOnSegment(
    point: MapCoordinate,
    start: MapCoordinate,
    end: MapCoordinate,
): Double {
    val referenceLatitudeRadians = Math.toRadians((start.latitude + end.latitude + point.latitude) / 3.0)
    fun MapCoordinate.toLocalPoint(origin: MapCoordinate): Pair<Double, Double> {
        val deltaLongitudeRadians = Math.toRadians(longitude - origin.longitude)
        val deltaLatitudeRadians = Math.toRadians(latitude - origin.latitude)
        val x = deltaLongitudeRadians * NAVIGATION_MARKER_EARTH_RADIUS_METERS * cos(referenceLatitudeRadians)
        val y = deltaLatitudeRadians * NAVIGATION_MARKER_EARTH_RADIUS_METERS
        return x to y
    }

    val (segmentEndX, segmentEndY) = end.toLocalPoint(start)
    val (pointX, pointY) = point.toLocalPoint(start)
    val segmentMagnitudeSquared = segmentEndX * segmentEndX + segmentEndY * segmentEndY
    if (segmentMagnitudeSquared <= 0.0) return 0.0
    return ((pointX * segmentEndX + pointY * segmentEndY) / segmentMagnitudeSquared).coerceIn(0.0, 1.0)
}

private fun List<MapCoordinate>.totalDistanceMeters(): Double =
    zipWithNext().sumOf { (start, end) -> haversineDistanceMeters(start, end) }

private fun MapCoordinate.interpolateTo(
    other: MapCoordinate,
    progressRatio: Double,
): MapCoordinate =
    MapCoordinate(
        latitude = latitude + ((other.latitude - latitude) * progressRatio),
        longitude = longitude + ((other.longitude - longitude) * progressRatio),
    )

private fun MapCoordinate.bearingDegreesTo(other: MapCoordinate): Double {
    val startLatitude = Math.toRadians(latitude)
    val endLatitude = Math.toRadians(other.latitude)
    val deltaLongitude = Math.toRadians(other.longitude - longitude)
    val y = sin(deltaLongitude) * cos(endLatitude)
    val x =
        cos(startLatitude) * sin(endLatitude) -
            sin(startLatitude) * cos(endLatitude) * cos(deltaLongitude)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

private fun NavigationMapPointUiState?.isNearCurrentLocation(currentLocation: NavigationMapPointUiState?): Boolean =
    this != null &&
        currentLocation != null &&
        haversineDistanceMeters(coordinate, currentLocation.coordinate) <= NAVIGATION_MARKER_CURRENT_LOCATION_HIDE_RADIUS_METERS

private fun List<NavigationMapSegmentUiState>.toSegmentMarkerOverlays(
    currentLocation: GeoCoordinate? = null,
    hideCompletedBeforeActiveIndex: Int? = null,
): List<MapViewportPointOverlay> =
    mapIndexedNotNull { index, segment ->
        if (hideCompletedBeforeActiveIndex != null && hideCompletedBeforeActiveIndex >= 0 && index < hideCompletedBeforeActiveIndex) {
            return@mapIndexedNotNull null
        }
        if (!segment.showJunctionMarker) return@mapIndexedNotNull null
        val coordinate =
            when (segment.travelKind) {
                NavigationSegmentTravelKind.TRANSIT -> segment.segmentEndCoordinate ?: segment.polyline.lastOrNull() ?: segment.segmentStartCoordinate ?: segment.polyline.firstOrNull()
                NavigationSegmentTravelKind.WALK,
                NavigationSegmentTravelKind.TRANSIT_WALK,
                    -> segment.segmentStartCoordinate ?: segment.polyline.firstOrNull()
            } ?: return@mapIndexedNotNull null
        if (
            currentLocation != null &&
            haversineDistanceMeters(currentLocation, coordinate) <= NAVIGATION_MARKER_CURRENT_LOCATION_HIDE_RADIUS_METERS
        ) {
            return@mapIndexedNotNull null
        }
        MapViewportPointOverlay(
            overlayId = "navigation-junction-$index",
            coordinate = coordinate.toMapCoordinate(),
            kind = MapViewportPointKind.SEGMENT_JUNCTION,
            tone = segment.travelKind.toSegmentMarkerTone(),
            contentDescription =
                segment.guidanceMessage
                    .trim()
                    .ifBlank { "Segment ${segment.sequence}" },
            clickTargetId = navigationSegmentMarkerId(index),
            includeInProjection = false,
        )
    }.distinctBy { point -> point.coordinate.deduplicationKey() }

private fun haversineDistanceMeters(a: GeoCoordinate, b: GeoCoordinate): Double {
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val sinHalfLat = sin(dLat / 2)
    val sinHalfLon = sin(dLon / 2)
    val h =
        sinHalfLat.pow(2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sinHalfLon.pow(2)
    return 2 * NAVIGATION_MARKER_EARTH_RADIUS_METERS * atan2(sqrt(h), sqrt(1 - h))
}

private fun haversineDistanceMeters(a: MapCoordinate, b: MapCoordinate): Double {
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val sinHalfLat = sin(dLat / 2)
    val sinHalfLon = sin(dLon / 2)
    val h =
        sinHalfLat.pow(2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sinHalfLon.pow(2)
    return 2 * NAVIGATION_MARKER_EARTH_RADIUS_METERS * atan2(sqrt(h), sqrt(1 - h))
}

internal fun createSegmentJunctionOverlayDebugSummary(
    mapOverlay: NavigationMapOverlayUiState,
    overlayState: MapViewportOverlayState,
): String {
    val junctionPoints =
        overlayState.points.filter { point ->
            point.kind == MapViewportPointKind.SEGMENT_JUNCTION
        }
    val projectionPoints =
        overlayState.points
            .filter(MapViewportPointOverlay::includeInProjection)
            .joinToString(separator = ", ") { point ->
                "${point.overlayId}:${point.kind.name}"
            }
    val projectionPolylines =
        overlayState.polylines
            .filter(MapViewportPolylineOverlay::includeInProjection)
            .joinToString(separator = ", ") { polyline ->
                "${polyline.overlayId}:${polyline.style.name}"
            }
    return buildString {
        append("focusMode=")
        append(mapOverlay.mapFocusMode.name)
        append(" points=")
        append(overlayState.points.size)
        append(" polylines=")
        append(overlayState.polylines.size)
        append(" junctions=")
        append(junctionPoints.size)
        append(" details=[")
        append(
            junctionPoints.joinToString(separator = "; ") { point ->
                buildString {
                    append("id=")
                    append(point.overlayId)
                    append(" coord=")
                    append(point.coordinate.toDebugCoordinate())
                    append(" tone=")
                    append(point.tone?.name ?: "null")
                    append(" includeInProjection=")
                    append(point.includeInProjection)
                }
            },
        )
        append("] projectionPoints=[")
        append(projectionPoints)
        append("] projectionPolylines=[")
        append(projectionPolylines)
        append("]")
    }
}

private fun List<NavigationMapSegmentUiState>.toBaselinePolylineOverlays(
    includeInProjection: Boolean,
    preferredArrowPoints: List<MapCoordinate> = emptyList(),
): List<MapViewportPolylineOverlay> =
    map { segment ->
        val points = segment.polyline.map(GeoCoordinate::toMapCoordinate)
        MapViewportPolylineOverlay(
            overlayId = "navigation-route-segment-${segment.sequence}",
            points = points,
            style = MapViewportPolylineStyle.ROUTE_BASELINE,
            tone = segment.travelKind.toBaselineOverlayTone(),
            includeInProjection = includeInProjection,
            showDirectionArrows = preferredArrowPoints.isNotEmpty() && points == preferredArrowPoints,
        )
    }.filter(MapViewportPolylineOverlay::isRenderable)

private fun resolveNavigationArrowOverlayId(
    selectedRoutePoints: List<MapCoordinate>,
    activeSegmentPoints: List<MapCoordinate>,
    focusedSegmentPoints: List<MapCoordinate>,
): String? =
    when {
        focusedSegmentPoints.size >= 2 -> "navigation-focused"
        activeSegmentPoints.size >= 2 -> "navigation-active"
        selectedRoutePoints.size >= 2 -> "navigation-route"
        else -> null
    }

private fun NavigationSegmentTravelKind.toBaselineOverlayTone(): MapViewportOverlayTone =
    when (this) {
        NavigationSegmentTravelKind.WALK -> MapViewportOverlayTone.NAVIGATION_WALK
        NavigationSegmentTravelKind.TRANSIT_WALK -> MapViewportOverlayTone.TRANSIT_WALK
        NavigationSegmentTravelKind.TRANSIT -> MapViewportOverlayTone.NAVY
    }

private fun NavigationSegmentTravelKind.toSegmentMarkerTone(): MapViewportOverlayTone =
    when (this) {
        NavigationSegmentTravelKind.WALK -> MapViewportOverlayTone.NEUTRAL
        NavigationSegmentTravelKind.TRANSIT_WALK -> MapViewportOverlayTone.NEUTRAL
        NavigationSegmentTravelKind.TRANSIT -> MapViewportOverlayTone.NAVY
    }

private fun NavigationSegmentTravelKind.toActiveOverlayTone(): MapViewportOverlayTone =
    when (this) {
        NavigationSegmentTravelKind.WALK -> MapViewportOverlayTone.NAVIGATION_WALK
        NavigationSegmentTravelKind.TRANSIT_WALK -> MapViewportOverlayTone.TRANSIT_WALK
        NavigationSegmentTravelKind.TRANSIT -> MapViewportOverlayTone.NAVY
    }

private fun NavigationSegmentTravelKind.toFocusedOverlayTone(): MapViewportOverlayTone =
    when (this) {
        NavigationSegmentTravelKind.WALK -> MapViewportOverlayTone.NAVIGATION_WALK
        NavigationSegmentTravelKind.TRANSIT_WALK -> MapViewportOverlayTone.TRANSIT_WALK
        NavigationSegmentTravelKind.TRANSIT -> MapViewportOverlayTone.NAVY
    }

private fun RouteOption?.toViewportOverlayTone(): MapViewportOverlayTone =
    when (this) {
        RouteOption.SHORTEST,
        RouteOption.MIN_WALK,
            -> MapViewportOverlayTone.TERTIARY

        RouteOption.MIN_TRANSFER -> MapViewportOverlayTone.SECONDARY
        RouteOption.SAFE,
        RouteOption.RECOMMENDED,
        null,
            -> MapViewportOverlayTone.PRIMARY
    }

internal fun MapViewportOverlayTone.toSegmentMarkerPalette(): MapViewportSegmentMarkerPalette =
    when (this) {
        MapViewportOverlayTone.PRIMARY ->
            MapViewportSegmentMarkerPalette(
                fillColorArgb = 0xFF006BE0.toInt(),
                strokeColorArgb = 0xFF0054B0.toInt(),
            )

        MapViewportOverlayTone.SECONDARY ->
            MapViewportSegmentMarkerPalette(
                fillColorArgb = 0xFF14AA82.toInt(),
                strokeColorArgb = 0xFF0A7B5E.toInt(),
            )

        MapViewportOverlayTone.TERTIARY ->
            MapViewportSegmentMarkerPalette(
                fillColorArgb = 0xFFF9AB4D.toInt(),
                strokeColorArgb = 0xFFD8841F.toInt(),
            )

        MapViewportOverlayTone.NEUTRAL ->
            MapViewportSegmentMarkerPalette(
                fillColorArgb = 0xFFD9D9D9.toInt(),
                strokeColorArgb = 0xFF8C8C8E.toInt(),
            )

        MapViewportOverlayTone.NAVY ->
            MapViewportSegmentMarkerPalette(
                fillColorArgb = 0xFF304583.toInt(),
                strokeColorArgb = 0xFF1E2C5A.toInt(),
            )

        MapViewportOverlayTone.NAVIGATION_WALK ->
            MapViewportSegmentMarkerPalette(
                fillColorArgb = 0xFF0061FE.toInt(),
                strokeColorArgb = 0xFF004CC8.toInt(),
            )

        MapViewportOverlayTone.TRANSIT_WALK ->
            MapViewportSegmentMarkerPalette(
                fillColorArgb = 0xFF0061FE.toInt(),
                strokeColorArgb = 0xFF004CC8.toInt(),
            )

        MapViewportOverlayTone.ERROR ->
            MapViewportSegmentMarkerPalette(
                fillColorArgb = 0xFFD94C4C.toInt(),
                strokeColorArgb = 0xFF9D2A2A.toInt(),
            )
    }

internal const val DEFAULT_VIEWPORT_CENTER_LATITUDE = 35.1796
internal const val DEFAULT_VIEWPORT_CENTER_LONGITUDE = 129.0756
internal const val MIN_VIEWPORT_LATITUDE_SPAN = 0.0035
internal const val MIN_VIEWPORT_LONGITUDE_SPAN = 0.0045
private const val COORDINATE_DEDUPLICATION_SCALE = 1_000_000.0
private const val NAVIGATION_MARKER_CURRENT_LOCATION_HIDE_RADIUS_METERS = 8.0
private const val NAVIGATION_MARKER_EARTH_RADIUS_METERS = 6_371_000.0
private const val NAVIGATION_FOLLOW_LOOKAHEAD_METERS = 12.0
private const val ROUTE_CONNECTOR_MIN_DISTANCE_METERS = 2.0

private var lastSegmentJunctionOverlayDebugSummary: String? = null

private fun logSegmentJunctionOverlayDebugSummary(
    mapOverlay: NavigationMapOverlayUiState,
    overlayState: MapViewportOverlayState,
) {
    if (!BuildConfig.DEBUG) return
    val summary = createSegmentJunctionOverlayDebugSummary(mapOverlay, overlayState)
    if (summary == lastSegmentJunctionOverlayDebugSummary) return
    lastSegmentJunctionOverlayDebugSummary = summary
    runCatching {
        Log.d("MapViewportOverlay", "SegmentMarkerTrace[MapViewportOverlay] $summary")
    }
}

private fun MapCoordinate.toDebugCoordinate(): String =
    String.format(java.util.Locale.US, "%.6f,%.6f", latitude, longitude)
