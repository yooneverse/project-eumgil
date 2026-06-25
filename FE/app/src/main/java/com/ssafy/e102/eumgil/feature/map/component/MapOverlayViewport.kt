package com.ssafy.e102.eumgil.feature.map.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ssafy.e102.eumgil.BuildConfig
import com.ssafy.e102.eumgil.feature.map.model.KAKAO_MAP_MAX_ZOOM_LEVEL
import com.ssafy.e102.eumgil.feature.map.model.KAKAO_MAP_MIN_ZOOM_LEVEL
import com.ssafy.e102.eumgil.feature.map.model.MapCameraSource
import com.ssafy.e102.eumgil.feature.map.model.MapCameraTarget
import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerLoadStatus
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerOverlayState
import com.ssafy.e102.eumgil.feature.map.model.defaultZoomLevel
import com.ssafy.e102.eumgil.feature.map.model.resolvedZoomLevel
import kotlin.math.abs
import kotlin.math.max

@Composable
internal fun MapOverlayViewport(
    overlayState: MapViewportOverlayState,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onMarkerClick: (String) -> Unit = {},
    onViewportBoundsChanged: (MapViewportBounds?) -> Unit = {},
    onUserCameraGesture: () -> Unit = {},
    controlState: MapOverlayViewportControlState? = null,
) {
    val describedModifier =
        if (contentDescription != null) {
            modifier.semantics { this.contentDescription = contentDescription }
        } else {
            modifier
        }
    val integrationState =
        resolveMapIntegrationState(
            hasNativeAppKey = BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank(),
            isInspectionMode = LocalInspectionMode.current,
        )
    val baseCameraTarget = overlayState.toMapCameraTarget()
    SideEffect {
        controlState?.updateBaseCameraTarget(baseCameraTarget)
    }
    val cameraTarget = controlState?.cameraTargetFor(baseCameraTarget) ?: baseCameraTarget
    val renderedOverlayState =
        if (controlState?.shouldFitProjection == false) {
            overlayState.copy(fitToProjection = false)
        } else {
            overlayState
        }

    when (integrationState) {
        is MapIntegrationState.Bound ->
            KakaoMapViewport(
                state =
                    MapViewportUiState(
                        integrationState = integrationState,
                        cameraTarget = cameraTarget,
                        currentLocation = null,
                        selectedDestinationCoordinate = null,
                        selectedDestinationName = null,
                        markerOverlayState = MapMarkerOverlayState(loadStatus = MapMarkerLoadStatus.READY),
                        overlayState = renderedOverlayState,
                        selectedMarkerId = null,
                        selectedMapPinCoordinate = null,
                        regionLabel = "",
                        statusLabel = "",
                        title = "",
                        description = "",
                        supportingText = "",
                    ),
                onMarkerClick = onMarkerClick,
                onCameraMoveEnd = { center, zoomLevel, isUserGesture, _ ->
                    val effectiveUserGesture =
                        controlState?.resolveUserGesture(
                            center = center,
                            zoomLevel = zoomLevel,
                            reportedUserGesture = isUserGesture,
                        ) ?: isUserGesture
                    controlState?.onCameraMoveEnd(
                        center = center,
                        zoomLevel = zoomLevel,
                        isUserGesture = effectiveUserGesture,
                    )
                    if (effectiveUserGesture) {
                        onUserCameraGesture()
                    }
                },
                onViewportBoundsChanged = onViewportBoundsChanged,
                onBackgroundClick = {},
                onMapClick = {},
                modifier = describedModifier,
            )

        MapIntegrationState.Unbound -> {
            SideEffect {
                onViewportBoundsChanged(null)
            }
            MapViewportOverlayBackdrop(
                overlayState = renderedOverlayState,
                zoomLevel = cameraTarget.resolvedZoomLevel(),
                modifier = describedModifier,
                onPointClick = onMarkerClick,
            )
        }
    }
}

@Composable
internal fun rememberMapOverlayViewportControlState(): MapOverlayViewportControlState =
    remember { MapOverlayViewportControlState() }

@Stable
internal class MapOverlayViewportControlState {
    private var baseCameraTarget: MapCameraTarget? by mutableStateOf(null)
    private var manualCameraTarget: MapCameraTarget? by mutableStateOf(null)
    private var latestObservedCamera: MapOverlayObservedCamera? by mutableStateOf(null)
    private var recenterCameraTarget: MapCameraTarget? by mutableStateOf(null)
    private var nextRequestId by mutableLongStateOf(MAP_OVERLAY_CONTROL_REQUEST_ID_START)

    val shouldFitProjection: Boolean
        get() = manualCameraTarget == null && recenterCameraTarget == null

    internal fun updateBaseCameraTarget(target: MapCameraTarget) {
        val previous = baseCameraTarget
        baseCameraTarget = target
        if (previous != null && previous.requestId != target.requestId) {
            recenterCameraTarget = null
            if (manualCameraTarget == null) {
                latestObservedCamera = null
            }
        }
    }

    internal fun cameraTargetFor(baseTarget: MapCameraTarget): MapCameraTarget =
        manualCameraTarget ?: recenterCameraTarget ?: baseTarget

    internal fun resolveUserGesture(
        center: MapCoordinate,
        zoomLevel: Int,
        reportedUserGesture: Boolean,
    ): Boolean =
        shouldTreatViewportCameraMoveAsUserGesture(
            requestedTarget = baseCameraTarget?.let(::cameraTargetFor),
            center = center,
            zoomLevel = zoomLevel,
            reportedUserGesture = reportedUserGesture,
        )

    internal fun onCameraMoveEnd(
        center: MapCoordinate,
        zoomLevel: Int,
        isUserGesture: Boolean,
    ) {
        val requestedTarget = baseCameraTarget?.let(::cameraTargetFor)
        if (
            !isUserGesture &&
            (manualCameraTarget != null || recenterCameraTarget != null) &&
            requestedTarget != null &&
            !requestedTarget.isAlignedWithCameraCallback(center = center, zoomLevel = zoomLevel)
        ) {
            return
        }
        latestObservedCamera =
            MapOverlayObservedCamera(
                center = center,
                zoomLevel = zoomLevel.coerceIn(KAKAO_MAP_MIN_ZOOM_LEVEL, KAKAO_MAP_MAX_ZOOM_LEVEL),
            )
        if (isUserGesture) {
            manualCameraTarget =
                (baseCameraTarget ?: MapCameraTarget.DefaultBusan).copy(
                    center = center,
                    zoomLevel = zoomLevel.coerceIn(KAKAO_MAP_MIN_ZOOM_LEVEL, KAKAO_MAP_MAX_ZOOM_LEVEL),
                    requestId = nextControlRequestId(),
                    shouldAnimateTransition = false,
                )
            recenterCameraTarget = null
        }
    }

    fun zoomIn() {
        zoomBy(1)
    }

    fun zoomOut() {
        zoomBy(-1)
    }

    fun recenter() {
        val baseTarget = baseCameraTarget ?: return
        manualCameraTarget = null
        latestObservedCamera = null
        recenterCameraTarget =
            baseTarget.copy(
                requestId = nextControlRequestId(),
                shouldAnimateTransition = true,
            )
    }

    fun clearManualCamera() {
        manualCameraTarget = null
        latestObservedCamera = null
    }

    fun recenterToCurrentLocation(currentLocation: MapCoordinate) {
        val baseTarget = baseCameraTarget ?: MapCameraTarget.DefaultBusan
        val observedCamera = latestObservedCamera
        val currentTarget = manualCameraTarget ?: recenterCameraTarget ?: baseTarget
        manualCameraTarget = null
        latestObservedCamera =
            MapOverlayObservedCamera(
                center = currentLocation,
                zoomLevel =
                    observedCamera?.zoomLevel
                        ?: currentTarget.zoomLevel
                        ?: MapCameraSource.CURRENT_LOCATION.defaultZoomLevel(),
            )
        recenterCameraTarget =
            baseTarget.copy(
                center = currentLocation,
                source = MapCameraSource.CURRENT_LOCATION,
                zoomLevel = latestObservedCamera?.zoomLevel ?: MapCameraSource.CURRENT_LOCATION.defaultZoomLevel(),
                bearingDegrees = baseTarget.bearingDegrees,
                requestId = nextControlRequestId(),
                shouldAnimateTransition = true,
            )
    }

    private fun zoomBy(delta: Int) {
        val baseTarget = baseCameraTarget ?: MapCameraTarget.DefaultBusan
        val currentTarget = manualCameraTarget ?: recenterCameraTarget ?: baseTarget
        val observedCamera = latestObservedCamera
        val currentZoomLevel =
            currentTarget.zoomLevel
                ?: observedCamera?.zoomLevel
                ?: currentTarget.resolvedZoomLevel()
        val nextZoomLevel =
            (currentZoomLevel + delta)
                .coerceIn(KAKAO_MAP_MIN_ZOOM_LEVEL, KAKAO_MAP_MAX_ZOOM_LEVEL)
        if (nextZoomLevel == currentZoomLevel) return
        val nextCenter = observedCamera?.center ?: currentTarget.center
        manualCameraTarget =
            currentTarget.copy(
                center = nextCenter,
                zoomLevel = nextZoomLevel,
                requestId = nextControlRequestId(),
                shouldAnimateTransition = true,
            )
        latestObservedCamera =
            MapOverlayObservedCamera(
                center = nextCenter,
                zoomLevel = nextZoomLevel,
            )
        recenterCameraTarget = null
    }

    private fun nextControlRequestId(): Long {
        nextRequestId += 1
        return nextRequestId
    }
}

private data class MapOverlayObservedCamera(
    val center: MapCoordinate,
    val zoomLevel: Int,
)

internal fun shouldTreatViewportCameraMoveAsUserGesture(
    requestedTarget: MapCameraTarget?,
    center: MapCoordinate,
    zoomLevel: Int,
    reportedUserGesture: Boolean,
): Boolean {
    if (!reportedUserGesture) return false
    if (requestedTarget == null) return true
    val isAlignedWithRequestedTarget =
        requestedTarget.center.isApproximatelySameCoordinate(center) &&
            requestedTarget.resolvedZoomLevel() == zoomLevel
    return !isAlignedWithRequestedTarget
}

private fun MapViewportOverlayState.toMapCameraTarget(): MapCameraTarget {
    if (!fitToProjection) {
        return MapCameraTarget(
            center = fallbackCamera.center,
            source = MapCameraSource.CURRENT_LOCATION,
            requestId = hashCode().toLong(),
            zoomLevel = fallbackCamera.toApproximateZoomLevel(),
            bearingDegrees = fallbackCamera.bearingDegrees,
            shouldAnimateTransition = shouldAnimateCameraTransition,
        )
    }

    val points = projectionCoordinates()
    if (points.isEmpty()) {
        return MapCameraTarget(
            center = fallbackCamera.center,
            source = MapCameraSource.SEARCH_RESULT,
            requestId = hashCode().toLong(),
            zoomLevel = fallbackCamera.toApproximateZoomLevel(),
            bearingDegrees = fallbackCamera.bearingDegrees,
            shouldAnimateTransition = shouldAnimateCameraTransition,
        )
    }

    val minLatitude = points.minOf(MapCoordinate::latitude)
    val maxLatitude = points.maxOf(MapCoordinate::latitude)
    val minLongitude = points.minOf(MapCoordinate::longitude)
    val maxLongitude = points.maxOf(MapCoordinate::longitude)
    val latitudeSpan = (maxLatitude - minLatitude).coerceAtLeast(MIN_VIEWPORT_LATITUDE_SPAN)
    val longitudeSpan = (maxLongitude - minLongitude).coerceAtLeast(MIN_VIEWPORT_LONGITUDE_SPAN)

    return MapCameraTarget(
        center =
            MapCoordinate(
                latitude = (minLatitude + maxLatitude) / 2.0,
                longitude = (minLongitude + maxLongitude) / 2.0,
            ),
        source = MapCameraSource.SEARCH_RESULT,
        requestId = hashCode().toLong(),
        zoomLevel = approximateZoomLevel(latitudeSpan = latitudeSpan, longitudeSpan = longitudeSpan),
        shouldAnimateTransition = shouldAnimateCameraTransition,
    )
}

private fun MapViewportOverlayState.projectionCoordinates(): List<MapCoordinate> =
    buildList {
        polylines
            .filter(MapViewportPolylineOverlay::includeInProjection)
            .flatMapTo(this) { polyline -> polyline.points }
        points
            .filter(MapViewportPointOverlay::includeInProjection)
            .mapTo(this) { point -> point.coordinate }
    }.dedupeConsecutive()

private fun List<MapCoordinate>.dedupeConsecutive(): List<MapCoordinate> =
    filterIndexed { index, coordinate -> index == 0 || coordinate != this[index - 1] }

private fun MapViewportFallbackCamera.toApproximateZoomLevel(): Int =
    approximateZoomLevel(
        latitudeSpan = latitudeSpan,
        longitudeSpan = longitudeSpan,
    )

private fun approximateZoomLevel(
    latitudeSpan: Double,
    longitudeSpan: Double,
): Int =
    when (max(latitudeSpan, longitudeSpan)) {
        in 0.0..0.0045 -> 18
        in 0.0045..0.0090 -> 17
        in 0.0090..0.0180 -> 16
        in 0.0180..0.0360 -> 15
        in 0.0360..0.0720 -> 14
        in 0.0720..0.1440 -> 13
        else -> 12
    }

private const val MAP_OVERLAY_CONTROL_REQUEST_ID_START = 1_000_000L
private const val MAP_OVERLAY_CAMERA_CALLBACK_COORDINATE_TOLERANCE = 0.00005

private fun MapCoordinate.isApproximatelySameCoordinate(
    other: MapCoordinate,
    tolerance: Double = MAP_OVERLAY_CAMERA_CALLBACK_COORDINATE_TOLERANCE,
): Boolean = abs(latitude - other.latitude) <= tolerance && abs(longitude - other.longitude) <= tolerance

private fun MapCameraTarget.isAlignedWithCameraCallback(
    center: MapCoordinate,
    zoomLevel: Int,
): Boolean =
    this.center.isApproximatelySameCoordinate(center) &&
        resolvedZoomLevel() == zoomLevel
