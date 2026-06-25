package com.ssafy.e102.eumgil.feature.map.component

import android.util.Log
import androidx.annotation.DrawableRes
import com.kakao.vectormap.label.TransformMethod
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.BuildConfig
import com.ssafy.e102.eumgil.core.model.FacilityCategory
import com.ssafy.e102.eumgil.core.model.PlaceMarkerKind
import com.ssafy.e102.eumgil.feature.map.model.MapCameraTarget
import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerCategoryType
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerOverlayState
import com.ssafy.e102.eumgil.feature.map.model.resolvedZoomLevel
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal const val KAKAO_MAP_PROVIDER_NAME = "Kakao Map"

internal fun resolveMapIntegrationState(
    hasNativeAppKey: Boolean,
    isInspectionMode: Boolean,
): MapIntegrationState =
    if (hasNativeAppKey && !isInspectionMode) {
        MapIntegrationState.Bound(providerName = KAKAO_MAP_PROVIDER_NAME)
    } else {
        MapIntegrationState.Unbound
    }

internal data class KakaoCameraRenderState(
    val latitude: Double,
    val longitude: Double,
    val zoomLevel: Int,
    val bearingDegrees: Double?,
    val requestId: Long,
)

internal fun createKakaoCameraRenderState(cameraTarget: MapCameraTarget): KakaoCameraRenderState =
    KakaoCameraRenderState(
        latitude = cameraTarget.center.latitude,
        longitude = cameraTarget.center.longitude,
        zoomLevel = cameraTarget.resolvedZoomLevel(),
        bearingDegrees = cameraTarget.bearingDegrees,
        requestId = cameraTarget.requestId,
    )

internal fun kakaoCameraRotationRadians(bearingDegrees: Double?): Double =
    bearingDegrees?.let(Math::toRadians) ?: 0.0

internal fun createKakaoCameraDebugSummary(cameraTarget: MapCameraTarget): String {
    val cameraState = createKakaoCameraRenderState(cameraTarget)
    return buildString {
        append("requestId=")
        append(cameraState.requestId)
        append(" source=")
        append(cameraTarget.source.name)
        append(" lat=")
        append(cameraState.latitude.toLogCoordinate())
        append(" lng=")
        append(cameraState.longitude.toLogCoordinate())
        append(" zoom=")
        append(cameraState.zoomLevel)
        cameraState.bearingDegrees?.let { bearingDegrees ->
            append(" bearing=")
            append(bearingDegrees)
        }
    }
}

internal fun shouldAnimateKakaoCameraTransition(
    previousTarget: MapCameraTarget?,
    nextTarget: MapCameraTarget,
): Boolean {
    if (!nextTarget.shouldAnimateTransition) return false
    if (previousTarget == null) return false
    if (previousTarget.requestId == nextTarget.requestId) return false
    if (previousTarget.source != nextTarget.source) return false
    return previousTarget.center != nextTarget.center ||
        previousTarget.resolvedZoomLevel() != nextTarget.resolvedZoomLevel() ||
        previousTarget.bearingDegrees != nextTarget.bearingDegrees
}

internal fun syncRenderedKakaoCameraTarget(
    previousTarget: MapCameraTarget?,
    latestStateTarget: MapCameraTarget?,
    center: MapCoordinate,
    zoomLevel: Int,
): MapCameraTarget {
    val baseTarget = latestStateTarget ?: previousTarget ?: MapCameraTarget.DefaultBusan
    return baseTarget.copy(
        center = center,
        zoomLevel = zoomLevel,
    )
}

internal fun shouldSkipKakaoCameraSync(
    renderedTarget: MapCameraTarget?,
    requestedTarget: MapCameraTarget,
): Boolean {
    if (requestedTarget.shouldAnimateTransition) return false
    val previous = renderedTarget ?: return false
    return previous.center.isNearCameraTarget(requestedTarget.center) &&
        previous.resolvedZoomLevel() == requestedTarget.resolvedZoomLevel() &&
        previous.bearingDegrees == requestedTarget.bearingDegrees
}

private fun MapCoordinate.isNearCameraTarget(other: MapCoordinate): Boolean =
    kotlin.math.abs(latitude - other.latitude) <= KAKAO_CAMERA_SYNC_COORDINATE_TOLERANCE &&
        kotlin.math.abs(longitude - other.longitude) <= KAKAO_CAMERA_SYNC_COORDINATE_TOLERANCE

internal data class KakaoMapScreenPoint(
    val x: Int,
    val y: Int,
)

internal fun resolveSelectedMapPinViewportVisibility(
    selectedMapPinCoordinate: MapCoordinate?,
    viewportWidth: Int,
    viewportHeight: Int,
    projectScreenPoint: (MapCoordinate) -> KakaoMapScreenPoint?,
): Boolean? {
    val coordinate = selectedMapPinCoordinate ?: return null
    if (viewportWidth <= 0 || viewportHeight <= 0) return null

    val screenPoint = projectScreenPoint(coordinate) ?: return false
    return isKakaoScreenPointInsideViewport(
        screenPoint = screenPoint,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
    )
}

internal fun isKakaoScreenPointInsideViewport(
    screenPoint: KakaoMapScreenPoint,
    viewportWidth: Int,
    viewportHeight: Int,
): Boolean =
    screenPoint.x in 0 until viewportWidth &&
        screenPoint.y in 0 until viewportHeight

internal enum class KakaoProjectedMarkerKind {
    HAZARD,
    CURRENT_LOCATION,
    CURRENT_LOCATION_DIRECTION,
    SELECTED_DESTINATION,
    SELECTED_MAP_PIN,
    ROUTE_ORIGIN,
    ROUTE_DESTINATION,
    ROUTE_SEGMENT_JUNCTION,
}

private const val KAKAO_CAMERA_SYNC_COORDINATE_TOLERANCE = 0.00002

internal enum class KakaoOverlayMarkerKind {
    APPROVED_REPORT,
    ROUTE_SEGMENT_JUNCTION,
    TRANSIT_STOP,
    TRANSIT_TRANSFER,
    ROUTE_DIRECTION_ARROW,
    FOCUS_HALO,
}

internal data class KakaoProjectedMarkerRenderState(
    val markerId: String,
    val coordinate: MapCoordinate,
    val kind: KakaoProjectedMarkerKind,
    @DrawableRes val iconResId: Int,
    val anchorPointX: Float,
    val anchorPointY: Float,
    val sizeDp: Int,
    val zIndex: Float,
    val fillColorArgb: Int? = null,
    val strokeColorArgb: Int? = null,
    val clickTargetId: String? = null,
    val rotationDegrees: Float = 0f,
    val translationDistanceDp: Int = 0,
)

internal data class KakaoOverlayMarkerRenderState(
    val markerId: String,
    val coordinate: MapCoordinate,
    val kind: KakaoOverlayMarkerKind,
    @DrawableRes val iconResId: Int? = null,
    val anchorPointX: Float,
    val anchorPointY: Float,
    val sizeDp: Int,
    val zIndex: Float,
    val fillColorArgb: Int,
    val strokeColorArgb: Int,
    val isSelected: Boolean = false,
    val rotationDegrees: Float = 0f,
    val label: String? = null,
    val secondaryLabel: String? = null,
    val secondaryFillColorArgb: Int? = null,
    val clickTargetId: String? = null,
)

internal data class KakaoOverlayMarkerRenderPartition(
    val pointMarkers: List<KakaoOverlayMarkerRenderState>,
    val approvedReportMarkers: List<KakaoOverlayMarkerRenderState>,
    val directionArrowMarkers: List<KakaoOverlayMarkerRenderState>,
)

internal data class KakaoRouteDirectionArrowDebugState(
    val markerId: String,
    val overlayId: String,
    val segmentStart: MapCoordinate,
    val segmentEnd: MapCoordinate,
    val segmentHeadingDegrees: Float,
    val rotationModel: String,
    val transformMethodName: String,
    val cameraBearingSource: String,
    val cameraBearingRadians: Double,
    val cameraBearingDegrees: Double,
    val finalRotationDegrees: Float,
)

internal data class KakaoOverlayMarkerRenderComputation(
    val markers: List<KakaoOverlayMarkerRenderState>,
    val routeDirectionArrowDebugStates: List<KakaoRouteDirectionArrowDebugState> = emptyList(),
)

internal data class KakaoProjectedMarkerOverlay(
    val markerId: String,
    val kind: KakaoProjectedMarkerKind,
    @DrawableRes val iconResId: Int,
    val screenPoint: KakaoMapScreenPoint,
    val anchorPointX: Float,
    val anchorPointY: Float,
    val sizeDp: Int,
    val zIndex: Float,
    val fillColorArgb: Int? = null,
    val strokeColorArgb: Int? = null,
    val clickTargetId: String? = null,
    val rotationDegrees: Float = 0f,
    val translationDistanceDp: Int = 0,
)

internal data class KakaoProjectedMarkerProjectionResult(
    val overlays: List<KakaoProjectedMarkerOverlay>,
    val shouldRetry: Boolean,
)

internal data class KakaoRouteLineRenderState(
    val routeLineId: String,
    val points: List<MapCoordinate>,
    val lineWidth: Float,
    val lineColor: Int,
    val strokeWidth: Float,
    val strokeColor: Int,
    val zOrder: Int,
)

internal data class KakaoRouteCameraRenderState(
    val points: List<MapCoordinate>,
    val signature: Int,
)

internal data class KakaoMarkerRenderState(
    val markerId: String,
    val latitude: Double,
    val longitude: Double,
    val category: FacilityCategory,
    @DrawableRes val glyphResId: Int,
    val rank: Long,
    val clickTargetId: String?,
    val isSelected: Boolean,
    val sizeDp: Int,
    val anchorPointX: Float,
    val anchorPointY: Float,
)

internal data class KakaoRendererFailure(
    val reasonLabel: String,
    val detailMessage: String,
) {
    val debugSummary: String
        get() = "$reasonLabel: $detailMessage"
}

internal enum class KakaoRendererLoadingPhase {
    INITIALIZING,
    AUTOMATIC_RETRY,
}

internal enum class KakaoMapLifecycleCommand {
    NONE,
    RESUME,
    PAUSE,
}

internal fun resolveKakaoMapLifecycleCommand(
    isLifecycleResumed: Boolean,
    hasMapView: Boolean,
    isAttachedToWindow: Boolean,
    isStarted: Boolean,
    isFinished: Boolean,
    hasResumedLifecycle: Boolean,
): KakaoMapLifecycleCommand =
    when {
        !hasMapView -> KakaoMapLifecycleCommand.NONE
        !isAttachedToWindow -> KakaoMapLifecycleCommand.NONE
        !isStarted -> KakaoMapLifecycleCommand.NONE
        isFinished -> KakaoMapLifecycleCommand.NONE
        // MapView.resume() drives the renderer lifecycle; waiting for surface creation deadlocks startup.
        isLifecycleResumed && hasResumedLifecycle -> KakaoMapLifecycleCommand.NONE
        isLifecycleResumed -> KakaoMapLifecycleCommand.RESUME
        !hasResumedLifecycle -> KakaoMapLifecycleCommand.NONE
        else -> KakaoMapLifecycleCommand.PAUSE
    }

internal fun createKakaoRendererFailure(error: Throwable): KakaoRendererFailure {
    val reasonLabel = error::class.simpleName ?: KAKAO_RENDERER_ERROR_REASON_FALLBACK
    val detailMessage =
        error.message
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: KAKAO_RENDERER_ERROR_DETAIL_FALLBACK
    return KakaoRendererFailure(
        reasonLabel = reasonLabel,
        detailMessage = detailMessage,
    )
}

internal fun createKakaoRendererTimeoutFailure(): KakaoRendererFailure =
    KakaoRendererFailure(
        reasonLabel = KAKAO_RENDERER_TIMEOUT_REASON_LABEL,
        detailMessage = KAKAO_RENDERER_TIMEOUT_DETAIL_FALLBACK,
    )

internal fun shouldAutoRestartKakaoRenderer(
    failure: KakaoRendererFailure,
    attemptedAutomaticRecoveryCount: Int,
): Boolean =
    attemptedAutomaticRecoveryCount < 1 &&
        (
            failure.reasonLabel == KAKAO_RENDERER_TIMEOUT_REASON_LABEL ||
                failure.reasonLabel == KAKAO_RENDERER_DESTROYED_REASON_LABEL
        )

internal fun resolveKakaoRendererLoadingPhase(
    attemptedAutomaticRecoveryCount: Int,
): KakaoRendererLoadingPhase =
    if (attemptedAutomaticRecoveryCount > 0) {
        KakaoRendererLoadingPhase.AUTOMATIC_RETRY
    } else {
        KakaoRendererLoadingPhase.INITIALIZING
    }

internal fun resolveKakaoRendererFailureAfterUnexpectedDestroy(
    existingFailure: KakaoRendererFailure?,
): KakaoRendererFailure = existingFailure ?: createKakaoRendererDestroyedFailure()

internal fun createKakaoRendererDestroyedFailure(): KakaoRendererFailure =
    KakaoRendererFailure(
        reasonLabel = KAKAO_RENDERER_DESTROYED_REASON_LABEL,
        detailMessage = KAKAO_RENDERER_DESTROYED_DETAIL_FALLBACK,
    )

internal fun createKakaoMarkerRenderStates(
    markerOverlayState: MapMarkerOverlayState,
    selectedMarkerId: String?,
): List<KakaoMarkerRenderState> =
    buildList {
        addAll(
            markerOverlayState.visibleMarkers.map { marker ->
                val isSelected = marker.markerId == selectedMarkerId
                KakaoMarkerRenderState(
                    markerId = marker.markerId,
                    latitude = marker.coordinate.latitude,
                    longitude = marker.coordinate.longitude,
                    category = marker.categoryType.category,
                    glyphResId =
                        facilityMarkerGlyphResId(
                            category = marker.categoryType.category,
                            markerKind = marker.markerKind,
                            selectedFilterCategory = marker.selectedFilterCategory,
                        ),
                    rank = if (isSelected) KAKAO_SELECTED_MARKER_RANK else KAKAO_DEFAULT_MARKER_RANK,
                    clickTargetId = marker.markerId,
                    isSelected = isSelected,
                    sizeDp = resolveKakaoFacilityMarkerSizeDp(marker.categoryType.category, isSelected),
                    anchorPointX = KAKAO_FACILITY_MARKER_ANCHOR_POINT_X,
                    anchorPointY = KAKAO_FACILITY_MARKER_ANCHOR_POINT_Y,
                )
            },
        )
    }

internal fun createKakaoProjectedMarkerRenderStates(
    currentLocation: MapCoordinate?,
    selectedOriginCoordinate: MapCoordinate? = null,
    selectedDestinationCoordinate: MapCoordinate?,
    selectedMapPinCoordinate: MapCoordinate?,
    overlayPoints: List<MapViewportPointOverlay> = emptyList(),
    cameraBearingDegrees: Double = 0.0,
): List<KakaoProjectedMarkerRenderState> {
    val projectedMarkers =
        buildList {
        currentLocation?.let { coordinate ->
            add(
                KakaoProjectedMarkerRenderState(
                    markerId = "current-location",
                    coordinate = coordinate,
                    kind = KakaoProjectedMarkerKind.CURRENT_LOCATION,
                    iconResId = R.drawable.ic_map_current_location,
                    anchorPointX = 0.5f,
                    anchorPointY = 0.5f,
                    sizeDp = 28,
                    zIndex = 2f,
                    rotationDegrees = 0f,
                ),
            )
        }
        addAll(
            overlayPoints.mapNotNull { point ->
                point.toProjectedMarkerRenderState(
                    includeCurrentLocation = currentLocation == null,
                )
            },
        )
        if (selectedMapPinCoordinate == null) {
            selectedOriginCoordinate?.let { coordinate ->
                add(
                    KakaoProjectedMarkerRenderState(
                        markerId = "selected-origin",
                        coordinate = coordinate,
                        kind = KakaoProjectedMarkerKind.ROUTE_ORIGIN,
                        iconResId = R.drawable.ic_navigation_rail_origin_pin,
                        anchorPointX = 0.5f,
                        anchorPointY = 1.0f,
                        sizeDp = 34,
                        zIndex = 3f,
                    ),
                )
            }
            selectedDestinationCoordinate?.let { coordinate ->
                add(
                    KakaoProjectedMarkerRenderState(
                        markerId = "selected-destination",
                        coordinate = coordinate,
                        kind = KakaoProjectedMarkerKind.ROUTE_DESTINATION,
                        iconResId = R.drawable.ic_navigation_rail_destination_pin,
                        anchorPointX = 0.5f,
                        anchorPointY = 1.0f,
                        sizeDp = 34,
                        zIndex = 4f,
                    ),
                )
            }
        }
        selectedMapPinCoordinate?.let { coordinate ->
            add(
                KakaoProjectedMarkerRenderState(
                    markerId = "selected-map-pin",
                    coordinate = coordinate,
                    kind = KakaoProjectedMarkerKind.SELECTED_MAP_PIN,
                    iconResId = R.drawable.ic_map_selected_pin_blue,
                    anchorPointX = 0.5f,
                    anchorPointY = 1.0f,
                    sizeDp = 32,
                    zIndex = 4f,
                ),
            )
        }
    }
    logProjectedSegmentMarkerDebugSummary(projectedMarkers)
    return projectedMarkers
}

internal fun createKakaoOverlayMarkerRenderStates(
    overlayPoints: List<MapViewportPointOverlay>,
    polylines: List<MapViewportPolylineOverlay> = emptyList(),
    cameraLatitude: Double = DEFAULT_VIEWPORT_CENTER_LATITUDE,
    zoomLevel: Int = DEFAULT_KAKAO_ROUTE_DIRECTION_ARROW_ZOOM_LEVEL,
    cameraBearingSource: String = KAKAO_CAMERA_BEARING_SOURCE_SYNC_SNAPSHOT,
    cameraBearingRadians: Double = 0.0,
    cameraBearingDegrees: Double = 0.0,
    screenDensity: Float = DEFAULT_KAKAO_ROUTE_DIRECTION_ARROW_SCREEN_DENSITY,
): List<KakaoOverlayMarkerRenderState> =
    createKakaoOverlayMarkerRenderComputation(
        overlayPoints = overlayPoints,
        polylines = polylines,
        cameraLatitude = cameraLatitude,
        zoomLevel = zoomLevel,
        cameraBearingSource = cameraBearingSource,
        cameraBearingRadians = cameraBearingRadians,
        cameraBearingDegrees = cameraBearingDegrees,
        screenDensity = screenDensity,
    ).markers

internal fun createKakaoOverlayMarkerRenderComputation(
    overlayPoints: List<MapViewportPointOverlay>,
    polylines: List<MapViewportPolylineOverlay> = emptyList(),
    cameraLatitude: Double = DEFAULT_VIEWPORT_CENTER_LATITUDE,
    zoomLevel: Int = DEFAULT_KAKAO_ROUTE_DIRECTION_ARROW_ZOOM_LEVEL,
    cameraBearingSource: String = KAKAO_CAMERA_BEARING_SOURCE_SYNC_SNAPSHOT,
    cameraBearingRadians: Double = 0.0,
    cameraBearingDegrees: Double = 0.0,
    screenDensity: Float = DEFAULT_KAKAO_ROUTE_DIRECTION_ARROW_SCREEN_DENSITY,
): KakaoOverlayMarkerRenderComputation {
    val showDetailedRouteOverlay = shouldShowDetailedRouteOverlay(zoomLevel)
    val pointMarkers =
        overlayPoints
            .asSequence()
            .filter { overlayPoint ->
                showDetailedRouteOverlay || !overlayPoint.isDetailedRouteOverlayMarker()
            }.mapNotNull(MapViewportPointOverlay::toOverlayMarkerRenderState)
            .toList()
    val arrowComputations =
        if (showDetailedRouteOverlay) {
            polylines
                .filter(MapViewportPolylineOverlay::showDirectionArrows)
                .flatMap { polyline ->
                    createKakaoRouteDirectionArrowRenderComputations(
                        polyline = polyline,
                        cameraLatitude = cameraLatitude,
                        zoomLevel = zoomLevel,
                        cameraBearingSource = cameraBearingSource,
                        cameraBearingRadians = cameraBearingRadians,
                        cameraBearingDegrees = cameraBearingDegrees,
                        screenDensity = screenDensity,
                    )
                }
        } else {
            emptyList()
        }
    return KakaoOverlayMarkerRenderComputation(
        markers = pointMarkers + arrowComputations.map(KakaoRouteDirectionArrowRenderComputation::marker),
        routeDirectionArrowDebugStates = arrowComputations.map(KakaoRouteDirectionArrowRenderComputation::debugState),
    )
}

private data class KakaoRouteDirectionArrowRenderComputation(
    val marker: KakaoOverlayMarkerRenderState,
    val debugState: KakaoRouteDirectionArrowDebugState,
)

private data class KakaoRouteDirectionArrowRotationSpec(
    val rotationDegrees: Float,
    val rotationModel: String,
)

private fun createKakaoRouteDirectionArrowRenderComputations(
    polyline: MapViewportPolylineOverlay,
    cameraLatitude: Double,
    zoomLevel: Int,
    cameraBearingSource: String,
    cameraBearingRadians: Double,
    cameraBearingDegrees: Double,
    screenDensity: Float,
): List<KakaoRouteDirectionArrowRenderComputation> =
    sampleRouteDirectionArrowPlacements(
        points = polyline.points,
        intervalDistance =
            resolveKakaoRouteDirectionArrowSpacingMeters(
                cameraLatitude = cameraLatitude,
                zoomLevel = zoomLevel,
                screenDensity = screenDensity,
            ),
        edgePaddingDistance =
            resolveKakaoRouteDirectionArrowEdgePaddingMeters(
                cameraLatitude = cameraLatitude,
                zoomLevel = zoomLevel,
                screenDensity = screenDensity,
            ),
        minimumPlacementCount = 1,
        measureDistance = MapCoordinate::distanceMetersTo,
        interpolatePoint = { start, end, fraction -> start.interpolateTo(end = end, fraction = fraction) },
    ).mapIndexed { index, placement ->
        val markerId = "arrow-${polyline.overlayId}-$index"
        val segmentHeadingDegrees = placement.segmentStart.rotationDegreesTo(end = placement.segmentEnd)
        val transformMethod =
            resolveKakaoOverlayMarkerTransformMethod(KakaoOverlayMarkerKind.ROUTE_DIRECTION_ARROW)
                ?: TransformMethod.None
        val rotationSpec =
            resolveKakaoRouteDirectionArrowRotationSpec(
                segmentHeadingDegrees = segmentHeadingDegrees,
                cameraBearingDegrees = cameraBearingDegrees,
                transformMethod = transformMethod,
            )
        KakaoRouteDirectionArrowRenderComputation(
            marker =
                KakaoOverlayMarkerRenderState(
                    markerId = markerId,
                    coordinate = placement.point,
                    kind = KakaoOverlayMarkerKind.ROUTE_DIRECTION_ARROW,
                    anchorPointX = 0.5f,
                    anchorPointY = 0.5f,
                    sizeDp = 14,
                    zIndex = 4.4f,
                    fillColorArgb = 0xFFFFFFFF.toInt(),
                    strokeColorArgb = 0x00FFFFFF,
                    rotationDegrees = rotationSpec.rotationDegrees,
                ),
            debugState =
                KakaoRouteDirectionArrowDebugState(
                    markerId = markerId,
                    overlayId = polyline.overlayId,
                    segmentStart = placement.segmentStart,
                    segmentEnd = placement.segmentEnd,
                    segmentHeadingDegrees = segmentHeadingDegrees,
                    rotationModel = rotationSpec.rotationModel,
                    transformMethodName = transformMethod.name,
                    cameraBearingSource = cameraBearingSource,
                    cameraBearingRadians = cameraBearingRadians,
                    cameraBearingDegrees = cameraBearingDegrees,
                    finalRotationDegrees = rotationSpec.rotationDegrees,
                ),
        )
    }

internal fun createKakaoRouteLineRenderStates(
    polylines: List<MapViewportPolylineOverlay>,
): List<KakaoRouteLineRenderState> =
    polylines
        .filter(MapViewportPolylineOverlay::isRenderable)
        .mapIndexed { index, polyline ->
            val style = polyline.toKakaoRouteLineStyle()
            KakaoRouteLineRenderState(
                routeLineId = polyline.overlayId,
                points = polyline.points,
                lineWidth = style.lineWidth,
                lineColor = style.lineColor,
                strokeWidth = style.strokeWidth,
                strokeColor = style.strokeColor,
                zOrder = KAKAO_ROUTE_LINE_BASE_Z_ORDER + index,
            )
        }

internal fun createKakaoRouteCameraRenderState(
    overlayState: MapViewportOverlayState,
): KakaoRouteCameraRenderState? {
    if (!overlayState.fitToProjection) return null

    val projectionPoints =
        buildList {
            overlayState.polylines
                .filter(MapViewportPolylineOverlay::includeInProjection)
                .flatMapTo(this) { polyline -> polyline.points }
            overlayState.points
                .filter(MapViewportPointOverlay::includeInProjection)
                .mapTo(this) { point -> point.coordinate }
        }.distinct()

    if (projectionPoints.size < 2) return null

    return KakaoRouteCameraRenderState(
        points = projectionPoints,
        signature = projectionPoints.hashCode(),
    )
}

internal fun createKakaoProjectedMarkerOverlays(
    projectedMarkers: List<KakaoProjectedMarkerRenderState>,
    projectScreenPoint: (MapCoordinate) -> KakaoMapScreenPoint?,
): List<KakaoProjectedMarkerOverlay> =
    projectedMarkers.mapNotNull { marker ->
        projectScreenPoint(marker.coordinate)?.let { screenPoint ->
            KakaoProjectedMarkerOverlay(
                markerId = marker.markerId,
                kind = marker.kind,
                iconResId = marker.iconResId,
                screenPoint = screenPoint,
                anchorPointX = marker.anchorPointX,
                anchorPointY = marker.anchorPointY,
                sizeDp = marker.sizeDp,
                zIndex = marker.zIndex,
                fillColorArgb = marker.fillColorArgb,
                strokeColorArgb = marker.strokeColorArgb,
                clickTargetId = marker.clickTargetId,
                rotationDegrees = marker.rotationDegrees,
                translationDistanceDp = marker.translationDistanceDp,
            )
        }
    }

internal fun partitionKakaoOverlayMarkerRenderStates(
    markers: List<KakaoOverlayMarkerRenderState>,
): KakaoOverlayMarkerRenderPartition =
    KakaoOverlayMarkerRenderPartition(
        pointMarkers =
            markers.filter { marker ->
                marker.kind != KakaoOverlayMarkerKind.ROUTE_DIRECTION_ARROW &&
                    marker.kind != KakaoOverlayMarkerKind.APPROVED_REPORT
            },
        approvedReportMarkers =
            markers.filter { marker ->
                marker.kind == KakaoOverlayMarkerKind.APPROVED_REPORT
            },
        directionArrowMarkers =
            markers.filter { marker ->
                marker.kind == KakaoOverlayMarkerKind.ROUTE_DIRECTION_ARROW
            },
    )

internal fun createKakaoProjectedMarkerProjectionResult(
    projectedMarkers: List<KakaoProjectedMarkerRenderState>,
    projectScreenPoint: (MapCoordinate) -> KakaoMapScreenPoint?,
): KakaoProjectedMarkerProjectionResult {
    val overlays = createKakaoProjectedMarkerOverlays(projectedMarkers, projectScreenPoint)
    return KakaoProjectedMarkerProjectionResult(
        overlays = overlays,
        shouldRetry = projectedMarkers.isNotEmpty() && overlays.isEmpty(),
    )
}

internal fun createProjectedSegmentMarkerDebugSummary(
    projectedMarkers: List<KakaoProjectedMarkerRenderState>,
): String {
    val segmentMarkers =
        projectedMarkers.filter { marker ->
            marker.kind == KakaoProjectedMarkerKind.ROUTE_SEGMENT_JUNCTION
        }
    return buildString {
        append("total=")
        append(projectedMarkers.size)
        append(" segmentProjected=")
        append(segmentMarkers.size)
        append(" details=[")
        append(
            segmentMarkers.joinToString(separator = "; ") { marker ->
                buildString {
                    append("id=")
                    append(marker.markerId)
                    append(" coord=")
                    append(marker.coordinate.toDebugCoordinate())
                    append(" sizeDp=")
                    append(marker.sizeDp)
                    append(" z=")
                    append(marker.zIndex)
                    append(" fill=")
                    append(marker.fillColorArgb.toDebugColor())
                    append(" stroke=")
                    append(marker.strokeColorArgb.toDebugColor())
                }
            },
        )
        append("]")
    }
}

internal fun createProjectedSegmentMarkerPipelineDebugSummary(
    projectedMarkers: List<KakaoProjectedMarkerRenderState>,
    projectionResult: KakaoProjectedMarkerProjectionResult,
    isCameraMoveInProgress: Boolean,
    retryScheduled: Boolean,
    retryCount: Int,
): String {
    val segmentProjectedCount =
        projectedMarkers.count { marker ->
            marker.kind == KakaoProjectedMarkerKind.ROUTE_SEGMENT_JUNCTION
        }
    val segmentRendered =
        projectionResult.overlays.filter { overlay ->
            overlay.kind == KakaoProjectedMarkerKind.ROUTE_SEGMENT_JUNCTION
        }
    return buildString {
        append("projected=")
        append(projectedMarkers.size)
        append(" segmentProjected=")
        append(segmentProjectedCount)
        append(" overlays=")
        append(projectionResult.overlays.size)
        append(" segmentRendered=")
        append(segmentRendered.size)
        append(" shouldRetry=")
        append(projectionResult.shouldRetry)
        append(" retryScheduled=")
        append(retryScheduled)
        append(" retryCount=")
        append(retryCount)
        append(" cameraMoving=")
        append(isCameraMoveInProgress)
        append(" details=[")
        append(
            segmentRendered.joinToString(separator = "; ") { overlay ->
                buildString {
                    append("id=")
                    append(overlay.markerId)
                    append(" screen=")
                    append(overlay.screenPoint.x)
                    append(",")
                    append(overlay.screenPoint.y)
                    append(" sizeDp=")
                    append(overlay.sizeDp)
                    append(" z=")
                    append(overlay.zIndex)
                    append(" fill=")
                    append(overlay.fillColorArgb.toDebugColor())
                    append(" stroke=")
                    append(overlay.strokeColorArgb.toDebugColor())
                }
            },
        )
        append("]")
    }
}

internal fun createRenderedSegmentJunctionOverlayDebugSummary(
    overlays: List<KakaoProjectedMarkerOverlay>,
): String {
    val segmentRendered =
        overlays.filter { overlay ->
            overlay.kind == KakaoProjectedMarkerKind.ROUTE_SEGMENT_JUNCTION
        }
    return buildString {
        append("count=")
        append(segmentRendered.size)
        append(" details=[")
        append(
            segmentRendered.joinToString(separator = "; ") { overlay ->
                buildString {
                    append("id=")
                    append(overlay.markerId)
                    append(" screen=")
                    append(overlay.screenPoint.x)
                    append(",")
                    append(overlay.screenPoint.y)
                    append(" sizeDp=")
                    append(overlay.sizeDp)
                    append(" z=")
                    append(overlay.zIndex)
                    append(" fill=")
                    append(overlay.fillColorArgb.toDebugColor())
                    append(" stroke=")
                    append(overlay.strokeColorArgb.toDebugColor())
                }
            },
        )
        append("]")
    }
}

internal fun createProjectedSegmentRenderPathDebugSummary(
    overlays: List<KakaoProjectedMarkerOverlay>,
): String {
    val segmentRendered =
        overlays.filter { overlay ->
            overlay.kind == KakaoProjectedMarkerKind.ROUTE_SEGMENT_JUNCTION
        }
    return buildString {
        append("renderPath=projected")
        append(" overlayCount=")
        append(overlays.size)
        append(" segmentCount=")
        append(segmentRendered.size)
        append(" details=[")
        append(
            segmentRendered.joinToString(separator = "; ") { overlay ->
                buildString {
                    append("id=")
                    append(overlay.markerId)
                    append(" screen=")
                    append(overlay.screenPoint.x)
                    append(",")
                    append(overlay.screenPoint.y)
                    append(" sizeDp=")
                    append(overlay.sizeDp)
                    append(" z=")
                    append(overlay.zIndex)
                    append(" fill=")
                    append(overlay.fillColorArgb.toDebugColor())
                    append(" stroke=")
                    append(overlay.strokeColorArgb.toDebugColor())
                }
            },
        )
        append("]")
    }
}

internal fun createNativeSegmentRenderPathDebugSummary(
    layerId: String,
    markers: List<KakaoOverlayMarkerRenderState>,
): String {
    val segmentMarkers =
        markers.filter { marker ->
            marker.kind == KakaoOverlayMarkerKind.ROUTE_SEGMENT_JUNCTION
        }
    return buildString {
        append("renderPath=native-label")
        append(" layer=")
        append(layerId)
        append(" markerCount=")
        append(markers.size)
        append(" segmentCount=")
        append(segmentMarkers.size)
        append(" details=[")
        append(
            segmentMarkers.joinToString(separator = "; ") { marker ->
                buildString {
                    append("id=")
                    append(marker.markerId)
                    append(" coord=")
                    append(marker.coordinate.toDebugCoordinate())
                    append(" sizeDp=")
                    append(marker.sizeDp)
                    append(" z=")
                    append(marker.zIndex)
                    append(" fill=")
                    append(marker.fillColorArgb.toDebugColor())
                    append(" stroke=")
                    append(marker.strokeColorArgb.toDebugColor())
                }
            },
        )
        append("]")
    }
}

internal fun createKakaoMarkerDebugSummary(
    markerOverlayState: MapMarkerOverlayState,
    renderedMarkers: List<KakaoMarkerRenderState>,
    selectedMarkerId: String?,
): String =
    buildString {
        append("total=")
        append(markerOverlayState.totalMarkerCount)
        append(" visible=")
        append(markerOverlayState.visibleMarkerCount)
        append(" rendered=")
        append(renderedMarkers.size)
        append(" selected=")
        append(selectedMarkerId ?: "none")
    }

private data class KakaoRouteLineStyleSpec(
    val lineWidth: Float,
    val lineColor: Int,
    val strokeWidth: Float,
    val strokeColor: Int,
)

private data class KakaoRouteLinePalette(
    val lineColor: Int,
    val casingColor: Int,
)

private data class KakaoOverlayPointMarkerSpec(
    val kind: KakaoProjectedMarkerKind,
    @DrawableRes val iconResId: Int,
    val sizeDp: Int,
    val anchorPointY: Float,
    val zIndex: Float,
    val fillColorArgb: Int? = null,
    val strokeColorArgb: Int? = null,
)

private fun MapViewportPointOverlay.toProjectedMarkerRenderState(
    includeCurrentLocation: Boolean,
): KakaoProjectedMarkerRenderState? {
    val markerSpec =
        when (kind) {
            MapViewportPointKind.HAZARD ->
                KakaoOverlayPointMarkerSpec(
                    kind = KakaoProjectedMarkerKind.HAZARD,
                    iconResId = R.drawable.ic_status_warning,
                    sizeDp = if (isSelected) 34 else 30,
                    anchorPointY = 1.0f,
                    zIndex = if (isSelected) 5.2f else 4.6f,
                )

            MapViewportPointKind.ORIGIN ->
                KakaoOverlayPointMarkerSpec(
                    kind = KakaoProjectedMarkerKind.ROUTE_ORIGIN,
                    iconResId = R.drawable.ic_navigation_rail_origin_pin,
                    sizeDp = 34,
                    anchorPointY = 1.0f,
                    zIndex = 4f,
                )

            MapViewportPointKind.DESTINATION ->
                KakaoOverlayPointMarkerSpec(
                    kind = KakaoProjectedMarkerKind.ROUTE_DESTINATION,
                    iconResId = R.drawable.ic_navigation_rail_destination_pin,
                    sizeDp = 34,
                    anchorPointY = 1.0f,
                    zIndex = 5f,
                )

            MapViewportPointKind.CURRENT_LOCATION ->
                if (includeCurrentLocation) {
                    KakaoOverlayPointMarkerSpec(
                        kind = KakaoProjectedMarkerKind.CURRENT_LOCATION,
                        iconResId = R.drawable.ic_map_current_location,
                        sizeDp = 28,
                        anchorPointY = 0.5f,
                        zIndex = 6f,
                    )
                } else {
                    null
                }

            MapViewportPointKind.CURRENT_LOCATION_HEADING ->
                null

            MapViewportPointKind.SEGMENT_JUNCTION,
            MapViewportPointKind.TRANSIT_BUS_STOP,
            MapViewportPointKind.TRANSIT_SUBWAY_STATION,
            MapViewportPointKind.TRANSIT_TRANSFER,
            MapViewportPointKind.APPROVED_REPORT,
                -> null

            MapViewportPointKind.FACILITY,
            MapViewportPointKind.CAMERA_FOCUS,
            MapViewportPointKind.FOCUS_HALO,
                -> null
        } ?: return null

    return KakaoProjectedMarkerRenderState(
        markerId = "overlay-$overlayId",
        coordinate = coordinate,
        kind = markerSpec.kind,
        iconResId = markerSpec.iconResId,
        anchorPointX = 0.5f,
        anchorPointY = markerSpec.anchorPointY,
        sizeDp = markerSpec.sizeDp,
        zIndex = markerSpec.zIndex,
        fillColorArgb = markerSpec.fillColorArgb,
        strokeColorArgb = markerSpec.strokeColorArgb,
        clickTargetId = clickTargetId,
        rotationDegrees = 0f,
        translationDistanceDp = 0,
    )
}

internal fun MapViewportPointOverlay.toKakaoProjectedPointMarkerState(): KakaoOverlayMarkerRenderState? =
    toOverlayMarkerRenderState()

private fun MapViewportPointOverlay.toOverlayMarkerRenderState(): KakaoOverlayMarkerRenderState? {
    return when (kind) {
        MapViewportPointKind.APPROVED_REPORT ->
            KakaoOverlayMarkerRenderState(
                markerId = clickTargetId ?: overlayId,
                coordinate = coordinate,
                kind = KakaoOverlayMarkerKind.APPROVED_REPORT,
                anchorPointX = 0.5f,
                anchorPointY = 0.5f,
                sizeDp = 32,
                zIndex = 4.2f,
                fillColorArgb = KAKAO_APPROVED_REPORT_MARKER_FILL,
                strokeColorArgb = KAKAO_APPROVED_REPORT_MARKER_STROKE,
                isSelected = isSelected,
                clickTargetId = clickTargetId,
            )

        MapViewportPointKind.SEGMENT_JUNCTION ->
            KakaoOverlayMarkerRenderState(
                markerId = "overlay-$overlayId",
                coordinate = coordinate,
                kind = KakaoOverlayMarkerKind.ROUTE_SEGMENT_JUNCTION,
                anchorPointX = 0.5f,
                anchorPointY = 0.5f,
                sizeDp = 18,
                zIndex = 3.6f,
                fillColorArgb = 0xFFFFFFFF.toInt(),
                strokeColorArgb = 0xFF8C8C8E.toInt(),
                clickTargetId = clickTargetId,
            )

        MapViewportPointKind.TRANSIT_BUS_STOP ->
            KakaoOverlayMarkerRenderState(
                markerId = "overlay-$overlayId",
                coordinate = coordinate,
                kind = KakaoOverlayMarkerKind.TRANSIT_STOP,
                anchorPointX = 0.5f,
                anchorPointY = 0.5f,
                sizeDp = 30,
                zIndex = 3.8f,
                fillColorArgb = 0xFF304583.toInt(),
                strokeColorArgb = 0xFFFFFFFF.toInt(),
                label = "BUS",
                clickTargetId = clickTargetId,
            )

        MapViewportPointKind.TRANSIT_SUBWAY_STATION -> {
            val routeLabel = transitMarker?.from?.label ?: label
            KakaoOverlayMarkerRenderState(
                markerId = "overlay-$overlayId",
                coordinate = coordinate,
                kind = KakaoOverlayMarkerKind.TRANSIT_STOP,
                anchorPointX = 0.5f,
                anchorPointY = 0.5f,
                sizeDp = 30,
                zIndex = 3.8f,
                fillColorArgb = routeLabel.toKakaoSubwayLineColor(),
                strokeColorArgb = 0xFFFFFFFF.toInt(),
                label = routeLabel.toKakaoSubwayLineShortLabel(),
                clickTargetId = clickTargetId,
            )
        }

        MapViewportPointKind.TRANSIT_TRANSFER -> {
            val marker = transitMarker ?: return null
            val from = marker.from
            val to = marker.to ?: return null
            KakaoOverlayMarkerRenderState(
                markerId = "overlay-$overlayId",
                coordinate = coordinate,
                kind = KakaoOverlayMarkerKind.TRANSIT_TRANSFER,
                anchorPointX = 0.5f,
                anchorPointY = 0.5f,
                sizeDp = 64,
                zIndex = 4.1f,
                fillColorArgb = from.toKakaoTransitColor(),
                strokeColorArgb = 0xFFFFFFFF.toInt(),
                label = from.toKakaoTransitShortLabel(),
                secondaryLabel = to.toKakaoTransitShortLabel(),
                secondaryFillColorArgb = to.toKakaoTransitColor(),
                clickTargetId = clickTargetId,
            )
        }

        MapViewportPointKind.FOCUS_HALO ->
            KakaoOverlayMarkerRenderState(
                markerId = "overlay-$overlayId",
                coordinate = coordinate,
                kind = KakaoOverlayMarkerKind.FOCUS_HALO,
                anchorPointX = 0.5f,
                anchorPointY = 0.5f,
                sizeDp = 26,
                zIndex = 3.5f,
                fillColorArgb = 0x804D8FF9.toInt(),
                strokeColorArgb = 0x004D8FF9,
            )

        else -> null
    }
}

private fun MapViewportPointOverlay.isDetailedRouteOverlayMarker(): Boolean =
    kind == MapViewportPointKind.SEGMENT_JUNCTION

private fun MapViewportTransitMarkerLeg.toKakaoTransitColor(): Int =
    when (kind) {
        MapViewportTransitMarkerKind.BUS -> 0xFF304583.toInt()
        MapViewportTransitMarkerKind.SUBWAY -> label.toKakaoSubwayLineColor()
    }

private fun MapViewportTransitMarkerLeg.toKakaoTransitShortLabel(): String =
    when (kind) {
        MapViewportTransitMarkerKind.BUS -> "BUS"
        MapViewportTransitMarkerKind.SUBWAY -> label.toKakaoSubwayLineShortLabel()
    }

private fun String?.toKakaoSubwayLineColor(): Int =
    when {
        this == null -> 0xFF304583.toInt()
        contains("부산김해", ignoreCase = true) ||
            contains("김해", ignoreCase = true) ||
            contains("BGL", ignoreCase = true) -> 0xFF8200FF.toInt()
        contains("1") -> 0xFFFF7F00.toInt()
        contains("2") -> 0xFF3ED93B.toInt()
        contains("3") -> 0xFFE8AB56.toInt()
        contains("4") -> 0xFF32B1FF.toInt()
        else -> 0xFF304583.toInt()
    }

private fun String?.toKakaoSubwayLineShortLabel(): String =
    when {
        this == null -> "?"
        contains("부산김해", ignoreCase = true) ||
            contains("김해", ignoreCase = true) ||
            contains("BGL", ignoreCase = true) -> "김"
        contains("1") -> "1"
        contains("2") -> "2"
        contains("3") -> "3"
        contains("4") -> "4"
        else -> take(2)
    }

private fun MapCoordinate.distanceMetersTo(other: MapCoordinate): Double {
    val latitudeMeters = (other.latitude - latitude) * KAKAO_METERS_PER_LATITUDE_DEGREE
    val averageLatitudeRadians = Math.toRadians((latitude + other.latitude) / 2.0)
    val longitudeMeters =
        (other.longitude - longitude) *
            KAKAO_METERS_PER_LATITUDE_DEGREE *
            cos(averageLatitudeRadians)
    return sqrt(latitudeMeters * latitudeMeters + longitudeMeters * longitudeMeters)
}

private fun MapCoordinate.interpolateTo(
    end: MapCoordinate,
    fraction: Double,
): MapCoordinate =
    MapCoordinate(
        latitude = latitude + ((end.latitude - latitude) * fraction),
        longitude = longitude + ((end.longitude - longitude) * fraction),
    )

private fun MapCoordinate.rotationDegreesTo(end: MapCoordinate): Float =
    Math.toDegrees(
        atan2(
            -(end.latitude - latitude),
            end.longitude - longitude,
        ),
    ).toFloat()

private fun resolveKakaoRouteDirectionArrowScreenRotationDegrees(
    segmentHeadingDegrees: Float,
    cameraBearingDegrees: Double,
): Float =
    normalizeKakaoRouteDirectionArrowRotationDegrees(
        /*
         * Kakao CameraPosition.rotationAngle is reported in radians by the SDK. Convert it to
         * degrees before subtracting it from the segment's north-up heading so the bitmap's
         * screen-space rotation stays in the same unit system end-to-end.
         */
        segmentHeadingDegrees - cameraBearingDegrees,
    ).toFloat()

private fun resolveKakaoRouteDirectionArrowRotationSpec(
    segmentHeadingDegrees: Float,
    cameraBearingDegrees: Double,
    transformMethod: TransformMethod,
): KakaoRouteDirectionArrowRotationSpec =
    when (transformMethod) {
        TransformMethod.AbsoluteRotation,
        TransformMethod.AbsoluteRotation_Decal,
        TransformMethod.AbsoluteRotation_KeepUpright,
            ->
            KakaoRouteDirectionArrowRotationSpec(
                rotationDegrees = normalizeKakaoRouteDirectionArrowRotationDegrees(segmentHeadingDegrees.toDouble()).toFloat(),
                rotationModel = KAKAO_ROUTE_DIRECTION_ARROW_ROTATION_MODEL_MAP_ABSOLUTE,
            )

        else ->
            KakaoRouteDirectionArrowRotationSpec(
                rotationDegrees =
                    resolveKakaoRouteDirectionArrowScreenRotationDegrees(
                        segmentHeadingDegrees = segmentHeadingDegrees,
                        cameraBearingDegrees = cameraBearingDegrees,
                    ),
                rotationModel = KAKAO_ROUTE_DIRECTION_ARROW_ROTATION_MODEL_SCREEN_RELATIVE,
            )
    }

internal fun createKakaoRouteDirectionArrowDebugSummary(
    debugState: KakaoRouteDirectionArrowDebugState,
): String =
    buildString {
        append("id=")
        append(debugState.markerId)
        append(" overlayId=")
        append(debugState.overlayId)
        append(" segmentHeading=")
        append(String.format(Locale.US, "%.2f", debugState.segmentHeadingDegrees))
        append(" rotationModel=")
        append(debugState.rotationModel)
        append(" transform=")
        append(debugState.transformMethodName)
        append(" cameraSource=")
        append(debugState.cameraBearingSource)
        append(" cameraBearingRad=")
        append(String.format(Locale.US, "%.4f", debugState.cameraBearingRadians))
        append(" cameraBearingDeg=")
        append(String.format(Locale.US, "%.2f", debugState.cameraBearingDegrees))
        append(" finalRotation=")
        append(String.format(Locale.US, "%.2f", debugState.finalRotationDegrees))
        append(" segment=")
        append(debugState.segmentStart.latitude.toLogCoordinate())
        append(",")
        append(debugState.segmentStart.longitude.toLogCoordinate())
        append("->")
        append(debugState.segmentEnd.latitude.toLogCoordinate())
        append(",")
        append(debugState.segmentEnd.longitude.toLogCoordinate())
    }

internal fun resolveKakaoOverlayMarkerTransformMethod(
    kind: KakaoOverlayMarkerKind,
): TransformMethod? =
    when (kind) {
        KakaoOverlayMarkerKind.ROUTE_DIRECTION_ARROW -> KAKAO_ROUTE_DIRECTION_ARROW_TRANSFORM_METHOD
        else -> null
    }

private fun normalizeKakaoRouteDirectionArrowRotationDegrees(
    rotationDegrees: Double,
): Double {
    val normalizedRotation = rotationDegrees % 360.0
    return when {
        normalizedRotation <= -180.0 -> normalizedRotation + 360.0
        normalizedRotation > 180.0 -> normalizedRotation - 360.0
        else -> normalizedRotation
    }
}

private fun resolveKakaoRouteDirectionArrowSpacingMeters(
    cameraLatitude: Double,
    zoomLevel: Int,
    screenDensity: Float,
): Double =
    resolveKakaoMetersPerScreenDp(
        cameraLatitude = cameraLatitude,
        zoomLevel = zoomLevel,
        screenDensity = screenDensity,
        distanceDp = ROUTE_DIRECTION_ARROW_TARGET_SPACING_DP,
    ).coerceAtLeast(KAKAO_ROUTE_DIRECTION_ARROW_MIN_INTERVAL_METERS)

private fun resolveKakaoRouteDirectionArrowEdgePaddingMeters(
    cameraLatitude: Double,
    zoomLevel: Int,
    screenDensity: Float,
): Double =
    resolveKakaoMetersPerScreenDp(
        cameraLatitude = cameraLatitude,
        zoomLevel = zoomLevel,
        screenDensity = screenDensity,
        distanceDp = ROUTE_DIRECTION_ARROW_EDGE_PADDING_DP,
    )

private fun resolveKakaoMetersPerScreenDp(
    cameraLatitude: Double,
    zoomLevel: Int,
    screenDensity: Float,
    distanceDp: Double,
): Double {
    val metersPerPixel =
        (KAKAO_WEB_MERCATOR_METERS_PER_PIXEL_AT_ZOOM_ZERO * cos(Math.toRadians(cameraLatitude))) /
            2.0.pow(zoomLevel.toDouble())
    return distanceDp * screenDensity.coerceAtLeast(1f) * metersPerPixel
}

private const val KAKAO_ROUTE_DIRECTION_ARROW_MIN_INTERVAL_METERS = 12.0
private const val KAKAO_METERS_PER_LATITUDE_DEGREE = 111_320.0
private const val KAKAO_WEB_MERCATOR_METERS_PER_PIXEL_AT_ZOOM_ZERO = 156_543.03392
private const val DEFAULT_KAKAO_ROUTE_DIRECTION_ARROW_ZOOM_LEVEL = 17
private const val DEFAULT_KAKAO_ROUTE_DIRECTION_ARROW_SCREEN_DENSITY = 1f
internal const val KAKAO_CAMERA_BEARING_SOURCE_SYNC_SNAPSHOT = "sync-snapshot"
private const val KAKAO_ROUTE_DIRECTION_ARROW_ROTATION_MODEL_SCREEN_RELATIVE = "screen-relative"
private const val KAKAO_ROUTE_DIRECTION_ARROW_ROTATION_MODEL_MAP_ABSOLUTE = "map-absolute"
private val KAKAO_ROUTE_DIRECTION_ARROW_TRANSFORM_METHOD = TransformMethod.AbsoluteRotation
private val KAKAO_APPROVED_REPORT_MARKER_FILL = 0xFFFFD84D.toInt()
private val KAKAO_APPROVED_REPORT_MARKER_STROKE = 0xFF111827.toInt()

private fun MapViewportPolylineOverlay.toKakaoRouteLineStyle(): KakaoRouteLineStyleSpec {
    val palette = tone.toKakaoRouteLinePalette()
    return when (style) {
        MapViewportPolylineStyle.ROUTE_PREVIEW ->
            KakaoRouteLineStyleSpec(
                lineWidth = 18f,
                lineColor = palette.lineColor,
                strokeWidth = 0f,
                strokeColor = palette.casingColor,
            )

        MapViewportPolylineStyle.ROUTE_CONNECTOR ->
            KakaoRouteLineStyleSpec(
                lineWidth = 18f,
                lineColor = 0xFF64748B.toInt(),
                strokeWidth = 0f,
                strokeColor = 0xFF64748B.toInt(),
            )

        MapViewportPolylineStyle.ROUTE_BASELINE ->
            KakaoRouteLineStyleSpec(
                lineWidth = 18f,
                lineColor = palette.lineColor,
                strokeWidth = 0f,
                strokeColor = palette.casingColor,
            )

        MapViewportPolylineStyle.ACTIVE_SEGMENT ->
            KakaoRouteLineStyleSpec(
                lineWidth = 18f,
                lineColor = palette.lineColor,
                strokeWidth = 0f,
                strokeColor = palette.casingColor,
            )

        MapViewportPolylineStyle.FOCUSED_SEGMENT ->
            KakaoRouteLineStyleSpec(
                lineWidth = 18f,
                lineColor = palette.lineColor,
                strokeWidth = 0f,
                strokeColor = palette.casingColor,
            )
    }
}

private fun MapViewportOverlayTone.toKakaoRouteLinePalette(): KakaoRouteLinePalette =
    when (this) {
        MapViewportOverlayTone.PRIMARY ->
            KakaoRouteLinePalette(
                lineColor = 0xFF006BE0.toInt(),
                casingColor = 0xFF006BE0.toInt(),
            )

        MapViewportOverlayTone.SECONDARY ->
            KakaoRouteLinePalette(
                lineColor = 0xFF14AA82.toInt(),
                casingColor = 0xFF0A7B5E.toInt(),
            )

        MapViewportOverlayTone.TERTIARY ->
            KakaoRouteLinePalette(
                lineColor = 0xFFF9AB4D.toInt(),
                casingColor = 0xFFF9AB4D.toInt(),
            )

        MapViewportOverlayTone.NEUTRAL ->
            KakaoRouteLinePalette(
                lineColor = 0xFFD9D9D9.toInt(),
                casingColor = 0xFFD9D9D9.toInt(),
            )

        MapViewportOverlayTone.NAVY ->
            KakaoRouteLinePalette(
                lineColor = 0xFF005391.toInt(),
                casingColor = 0xFF005391.toInt(),
            )

        MapViewportOverlayTone.NAVIGATION_WALK ->
            KakaoRouteLinePalette(
                lineColor = 0xFF0061FE.toInt(),
                casingColor = 0xFF0061FE.toInt(),
            )

        MapViewportOverlayTone.TRANSIT_WALK ->
            KakaoRouteLinePalette(
                lineColor = 0xFF99B5D1.toInt(),
                casingColor = 0xFF99B5D1.toInt(),
            )

        MapViewportOverlayTone.ERROR ->
            KakaoRouteLinePalette(
                lineColor = 0xFFD94C4C.toInt(),
                casingColor = 0xFF9D2A2A.toInt(),
            )
    }

// Kakao labels render raw drawable bounds, so map markers must use compact icon assets.
@DrawableRes
internal fun facilityMarkerGlyphResId(
    category: FacilityCategory,
    markerKind: PlaceMarkerKind = PlaceMarkerKind.DEFAULT,
    selectedFilterCategory: FacilityCategory? = null,
): Int =
    selectedFilterCategory?.let(::facilityMarkerAccessibilityGlyphResId)
        ?: facilityMarkerKindGlyphResId(markerKind)
        ?: facilityMarkerCategoryGlyphResId(category)

@DrawableRes
private fun facilityMarkerKindGlyphResId(markerKind: PlaceMarkerKind): Int? =
    when (markerKind) {
        PlaceMarkerKind.BUS_STOP -> R.drawable.ic_place_bus
        PlaceMarkerKind.SUBWAY_STATION -> R.drawable.ic_place_subway
        PlaceMarkerKind.DEFAULT -> null
    }

@DrawableRes
private fun facilityMarkerAccessibilityGlyphResId(category: FacilityCategory): Int? =
    when (category) {
        FacilityCategory.TOILET -> R.drawable.ic_accessibility_tag_accessible_toilet
        FacilityCategory.ELEVATOR -> R.drawable.ic_accessibility_tag_elevator
        FacilityCategory.CHARGING_STATION -> R.drawable.ic_accessibility_tag_charging_station
        else -> null
    }

@DrawableRes
private fun facilityMarkerCategoryGlyphResId(category: FacilityCategory): Int =
    when (category) {
        FacilityCategory.TOILET -> R.drawable.ic_place_restroom
        FacilityCategory.ELEVATOR -> R.drawable.ic_lowvision_category_elevator
        FacilityCategory.CHARGING_STATION -> R.drawable.ic_place_charging
        FacilityCategory.FOOD_CAFE -> R.drawable.ic_place_cafe
        FacilityCategory.TOURIST_SPOT -> R.drawable.ic_place_tourist_spot
        FacilityCategory.ACCOMMODATION -> R.drawable.ic_place_accommodation
        FacilityCategory.HEALTHCARE -> R.drawable.ic_place_healthcare
        FacilityCategory.WELFARE -> R.drawable.ic_place_welfare
        FacilityCategory.PUBLIC_OFFICE -> R.drawable.ic_place_public_office
        FacilityCategory.BRAILLE_BLOCK -> R.drawable.ic_route_tactile_blocks
        FacilityCategory.RESTAURANT -> R.drawable.ic_place_restaurant
        FacilityCategory.TOURIST_ATTRACTION -> R.drawable.ic_place_tourist_spot
        FacilityCategory.OTHER -> R.drawable.ic_map_selected_pin_blue
    }

internal fun resolveKakaoFacilityMarkerSizeDp(
    category: FacilityCategory,
    isSelected: Boolean,
): Int =
    when (category) {
        FacilityCategory.BRAILLE_BLOCK -> 30
        else -> 28
    }

private const val KAKAO_DEFAULT_MARKER_RANK = 0L
private const val KAKAO_SELECTED_MARKER_RANK = 10L
private const val KAKAO_FACILITY_MARKER_ANCHOR_POINT_X = 0.5f
private const val KAKAO_FACILITY_MARKER_ANCHOR_POINT_Y = 0.5f
private const val KAKAO_ROUTE_LINE_BASE_Z_ORDER = 0

internal const val KAKAO_RENDERER_ERROR_REASON_FALLBACK = "MapError"
internal const val KAKAO_RENDERER_ERROR_DETAIL_FALLBACK = "Unknown renderer failure"
internal const val KAKAO_RENDERER_DESTROYED_REASON_LABEL = "MapDestroyed"
internal const val KAKAO_RENDERER_DESTROYED_DETAIL_FALLBACK = "Renderer was destroyed before becoming ready"
internal const val KAKAO_RENDERER_TIMEOUT_REASON_LABEL = "MapTimeout"
internal const val KAKAO_RENDERER_TIMEOUT_DETAIL_FALLBACK = "Renderer did not become ready in time"
internal const val KAKAO_ZOOM_CAMERA_ANIMATION_DURATION_MILLIS = 220

private fun Double.toLogCoordinate(): String = String.format(Locale.US, "%.6f", this)

private var lastProjectedSegmentMarkerDebugSummary: String? = null

private fun logProjectedSegmentMarkerDebugSummary(
    projectedMarkers: List<KakaoProjectedMarkerRenderState>,
) {
    if (!BuildConfig.DEBUG) return
    val summary = createProjectedSegmentMarkerDebugSummary(projectedMarkers)
    if (summary == lastProjectedSegmentMarkerDebugSummary) return
    lastProjectedSegmentMarkerDebugSummary = summary
    runCatching {
        Log.d("KakaoMapViewport", "SegmentMarkerTrace[KakaoProjectedMarkers] $summary")
    }
}

private fun MapCoordinate.toDebugCoordinate(): String =
    String.format(Locale.US, "%.6f,%.6f", latitude, longitude)

private fun Int?.toDebugColor(): String =
    this?.let { color ->
        String.format(Locale.US, "0x%08X", color)
    } ?: "null"
