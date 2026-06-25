package com.ssafy.e102.eumgil.feature.map.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.appcompat.content.res.AppCompatResources
import com.ssafy.e102.eumgil.R
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.GestureType
import com.kakao.vectormap.camera.CameraPosition
import com.kakao.vectormap.camera.CameraAnimation
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.CompetitionType
import com.kakao.vectormap.label.LabelLayer
import com.kakao.vectormap.label.LabelLayerOptions
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.label.LabelManager
import com.kakao.vectormap.label.OrderingType
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.ssafy.e102.eumgil.core.model.FacilityCategory
import com.ssafy.e102.eumgil.feature.map.MapTapClickType
import com.ssafy.e102.eumgil.feature.map.MapTapPayload
import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import com.ssafy.e102.eumgil.feature.map.model.resolvedZoomLevel
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.cos

@Composable
internal fun KakaoMapViewport(
    state: MapViewportUiState,
    onMarkerClick: (String) -> Unit,
    onCameraMoveEnd: (MapCoordinate, Int, Boolean, Boolean?) -> Unit,
    onViewportBoundsChanged: (MapViewportBounds?) -> Unit,
    onBackgroundClick: () -> Unit,
    onMapClick: (MapTapPayload) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var reloadGeneration by remember { mutableIntStateOf(0) }
    var isRendererRestarting by remember { mutableStateOf(false) }
    var attemptedAutomaticRecoveryCount by remember { mutableIntStateOf(0) }
    val loadingPhase = resolveKakaoRendererLoadingPhase(attemptedAutomaticRecoveryCount)

    LaunchedEffect(reloadGeneration, isRendererRestarting) {
        if (!isRendererRestarting) return@LaunchedEffect
        delay(KAKAO_RENDERER_RESTART_DELAY_MILLIS)
        isRendererRestarting = false
    }

    if (isRendererRestarting) {
        MapRendererFallbackOverlay(
            title = stringResource(id = R.string.map_viewport_title_renderer_retrying),
            description = stringResource(id = R.string.map_viewport_description_renderer_retrying),
            isLoading = true,
            modifier = modifier,
        )
        return
    }

    key(reloadGeneration) {
        val controller = remember(reloadGeneration) { KakaoMapViewportController() }
        val rendererFailure = controller.rendererFailure
        var lastProjectedRenderPathDebugSummary by remember(reloadGeneration) { mutableStateOf<String?>(null) }
        var hasLoadingGracePeriodElapsed by remember(reloadGeneration) { mutableStateOf(false) }
        val isRendererReady = controller.rendererStatus == KakaoRendererStatus.Ready
        val isRendererError = rendererFailure != null
        val isAutomaticRetryLoading =
            !isRendererError && loadingPhase == KakaoRendererLoadingPhase.AUTOMATIC_RETRY

        LaunchedEffect(controller, rendererFailure, attemptedAutomaticRecoveryCount) {
            val failure = rendererFailure ?: return@LaunchedEffect
            if (
                !shouldAutoRestartKakaoRenderer(
                    failure = failure,
                    attemptedAutomaticRecoveryCount = attemptedAutomaticRecoveryCount,
                )
            ) {
                return@LaunchedEffect
            }

            controller.finish()
            attemptedAutomaticRecoveryCount += 1
            isRendererRestarting = true
            reloadGeneration += 1
        }

        LaunchedEffect(controller, controller.rendererStatus) {
            if (controller.rendererStatus != KakaoRendererStatus.Initializing) return@LaunchedEffect
            delay(KAKAO_RENDERER_READY_TIMEOUT_MILLIS)
            controller.markRendererTimedOut()
        }

        LaunchedEffect(reloadGeneration, controller.rendererStatus, rendererFailure, loadingPhase) {
            if (isRendererReady || isRendererError || isAutomaticRetryLoading) {
                hasLoadingGracePeriodElapsed = false
                return@LaunchedEffect
            }

            hasLoadingGracePeriodElapsed = false
            delay(KAKAO_RENDERER_LOADING_OVERLAY_DELAY_MILLIS)
            hasLoadingGracePeriodElapsed = true
        }

        DisposableEffect(lifecycleOwner, controller) {
            val observer =
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> controller.setLifecycleResumed(true)
                        Lifecycle.Event.ON_PAUSE -> controller.setLifecycleResumed(false)
                        Lifecycle.Event.ON_DESTROY -> controller.finish()
                        else -> Unit
                    }
                }
            lifecycleOwner.lifecycle.addObserver(observer)
            controller.setLifecycleResumed(
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
            )
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                controller.finish()
            }
        }

        val shouldShowRendererFallbackOverlay =
            shouldShowKakaoRendererFallbackOverlay(
                isRendererReady = isRendererReady,
                isRendererError = isRendererError,
                isAutomaticRetryLoading = isAutomaticRetryLoading,
                hasLoadingGracePeriodElapsed = hasLoadingGracePeriodElapsed,
            )

        Box(
            modifier =
                modifier
                    .clipToBounds(),
        ) {
            AndroidView(
                factory = { context ->
                    controller.bind(
                        context = context,
                        initialState = state,
                        onMarkerClick = onMarkerClick,
                        onCameraMoveEnd = onCameraMoveEnd,
                        onViewportBoundsChanged = onViewportBoundsChanged,
                        onBackgroundClick = onBackgroundClick,
                        onMapClick = onMapClick,
                    )
                },
                modifier = Modifier.fillMaxSize(),
                update = {
                    controller.render(
                        state = state,
                        onMarkerClick = onMarkerClick,
                        onCameraMoveEnd = onCameraMoveEnd,
                        onViewportBoundsChanged = onViewportBoundsChanged,
                        onBackgroundClick = onBackgroundClick,
                        onMapClick = onMapClick,
                    )
                },
            )

            if (isRendererReady) {
                SideEffect {
                    val composeSummary =
                        createProjectedSegmentRenderPathDebugSummary(controller.projectedMarkerOverlays)
                    if (composeSummary != lastProjectedRenderPathDebugSummary) {
                        lastProjectedRenderPathDebugSummary = composeSummary
                        Log.d(KAKAO_MAP_LOG_TAG, "SegmentMarkerTrace[RenderPath] $composeSummary")
                    }
                }
                controller.projectedMarkerOverlays.forEach { overlay ->
                    MapProjectedMarkerOverlay(
                        overlay = overlay,
                        contentDescription =
                            overlay.resolveContentDescription(
                                selectedDestinationName = state.selectedDestinationName,
                            ),
                        onMarkerClick = onMarkerClick,
                    )
                }
            }

            if (shouldShowRendererFallbackOverlay) {
                MapRendererFallbackOverlay(
                    title =
                        if (isRendererError) {
                            stringResource(id = R.string.map_viewport_title_renderer_error)
                        } else if (isAutomaticRetryLoading) {
                            stringResource(id = R.string.map_viewport_title_renderer_retrying)
                        } else {
                            stringResource(id = R.string.map_viewport_title_renderer_loading)
                        },
                    description =
                        if (isRendererError) {
                            stringResource(id = R.string.map_viewport_description_renderer_error)
                        } else if (isAutomaticRetryLoading) {
                            stringResource(id = R.string.map_viewport_description_renderer_retrying)
                        } else {
                            stringResource(id = R.string.map_viewport_description_renderer_loading)
                        },
                    actionLabel =
                        if (isRendererError) {
                            stringResource(id = R.string.map_viewport_retry)
                        } else {
                            null
                        },
                    onActionClick =
                        if (isRendererError) {
                            {
                                controller.finish()
                                attemptedAutomaticRecoveryCount = 0
                                isRendererRestarting = true
                                reloadGeneration += 1
                            }
                        } else {
                            null
                        },
                    isLoading = !isRendererError,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

internal fun createViewportBounds(
    cornerCoordinates: List<MapCoordinate>,
): MapViewportBounds? {
    if (cornerCoordinates.isEmpty()) return null

    return MapViewportBounds(
        swLat = cornerCoordinates.minOf(MapCoordinate::latitude),
        swLng = cornerCoordinates.minOf(MapCoordinate::longitude),
        neLat = cornerCoordinates.maxOf(MapCoordinate::latitude),
        neLng = cornerCoordinates.maxOf(MapCoordinate::longitude),
    )
}

private class KakaoMapViewportController {
    var rendererStatus by mutableStateOf(KakaoRendererStatus.Initializing)
        private set
    var rendererFailure by mutableStateOf<KakaoRendererFailure?>(null)
        private set
    var projectedMarkerOverlays by mutableStateOf<List<KakaoProjectedMarkerOverlay>>(emptyList())
        private set

    private var mapView: MapView? = null
    private var kakaoMap: KakaoMap? = null
    private var latestState: MapViewportUiState? = null
    private var markerClickHandler: ((String) -> Unit)? = null
    private var cameraMoveEndHandler: ((MapCoordinate, Int, Boolean, Boolean?) -> Unit)? = null
    private var viewportBoundsChangedHandler: ((MapViewportBounds?) -> Unit)? = null
    private var backgroundClickHandler: (() -> Unit)? = null
    private var mapClickHandler: ((MapTapPayload) -> Unit)? = null
    private var facilityMarkerStyleCache: KakaoFacilityMarkerStyleCache? = null
    private var overlayMarkerStyleCache: KakaoOverlayMarkerStyleCache? = null
    private var lastRenderedCameraRequestId: Long? = null
    private var lastRenderedCameraTarget: com.ssafy.e102.eumgil.feature.map.model.MapCameraTarget? = null
    private var lastRenderedRouteCameraSignature: Int? = null
    private var lastRenderedMarkers: List<KakaoMarkerRenderState> = emptyList()
    private var lastRenderedPointOverlayMarkers: List<KakaoOverlayMarkerRenderState> = emptyList()
    private var lastRenderedApprovedReportMarkers: List<KakaoOverlayMarkerRenderState> = emptyList()
    private var lastRenderedArrowOverlayMarkers: List<KakaoOverlayMarkerRenderState> = emptyList()
    private var lastRenderedRouteLines: List<KakaoRouteLineRenderState> = emptyList()
    private var consecutiveEmptyRouteLineFrameCount: Int = 0
    private var lastDispatchedMapTapCoordinate: MapCoordinate? = null
    private var lastDispatchedMapTapUptimeMillis: Long = 0L
    private var lastDispatchedMarkerTapId: String? = null
    private var lastDispatchedMarkerTapUptimeMillis: Long = 0L
    private var lastSuppressedTerrainTapCoordinate: MapCoordinate? = null
    private var lastSuppressedTerrainTapUptimeMillis: Long = 0L
    private var isCameraMoveInProgress = false
    private var projectedMarkerTrackingRunnable: Runnable? = null
    private var projectedMarkerRetryRunnable: Runnable? = null
    private var projectedMarkerRetryCount: Int = 0
    private var lastProjectedMarkerPipelineDebugSummary: String? = null
    private var lastNativeSegmentRenderPathDebugSummary: String? = null
    private var lastRouteDirectionArrowDebugSummary: String? = null
    private var lastRouteDirectionArrowLayerSyncSummary: String? = null
    private var lastCameraBearingComparisonSummary: String? = null
    private var latestListenerCameraPosition: KakaoResolvedCameraPosition? = null
    private var latestCameraPositionRequestSequence: Long = 0L
    private var isStarted = false
    private var isFinished = false
    private var isLifecycleResumed = false
    private var hasMapLifecycleResumed = false
    private var lifecycleDispatchRetryCount = 0
    private val attachStateListener =
        object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                (view as? MapView)?.let(::startMap)
                syncLifecycleToMapView(reason = "view-attached")
            }

            override fun onViewDetachedFromWindow(view: View) = Unit
        }

    private data class KakaoResolvedCameraPosition(
        val latitude: Double,
        val longitude: Double,
        val zoomLevel: Int,
        val source: String,
        val bearingRadians: Double,
        val bearingDegrees: Double,
    )

    fun bind(
        context: Context,
        initialState: MapViewportUiState,
        onMarkerClick: (String) -> Unit,
        onCameraMoveEnd: (MapCoordinate, Int, Boolean, Boolean?) -> Unit,
        onViewportBoundsChanged: (MapViewportBounds?) -> Unit,
        onBackgroundClick: () -> Unit,
        onMapClick: (MapTapPayload) -> Unit,
    ): MapView {
        latestState = initialState
        markerClickHandler = onMarkerClick
        cameraMoveEndHandler = onCameraMoveEnd
        viewportBoundsChangedHandler = onViewportBoundsChanged
        backgroundClickHandler = onBackgroundClick
        mapClickHandler = onMapClick

        return mapView ?: MapView(context).also { createdMapView ->
            Log.i(KAKAO_MAP_LOG_TAG, "Creating Kakao MapView instance")
            createdMapView.addOnAttachStateChangeListener(attachStateListener)
            mapView = createdMapView
            facilityMarkerStyleCache = KakaoFacilityMarkerStyleCache(context = context)
            overlayMarkerStyleCache = KakaoOverlayMarkerStyleCache(context = context)
            if (createdMapView.isAttachedToWindow) {
                startMap(createdMapView)
            }
            syncLifecycleToMapView(reason = "bind")
        }
    }

    fun render(
        state: MapViewportUiState,
        onMarkerClick: (String) -> Unit,
        onCameraMoveEnd: (MapCoordinate, Int, Boolean, Boolean?) -> Unit,
        onViewportBoundsChanged: (MapViewportBounds?) -> Unit,
        onBackgroundClick: () -> Unit,
        onMapClick: (MapTapPayload) -> Unit,
    ) {
        latestState = state
        markerClickHandler = onMarkerClick
        cameraMoveEndHandler = onCameraMoveEnd
        viewportBoundsChangedHandler = onViewportBoundsChanged
        backgroundClickHandler = onBackgroundClick
        mapClickHandler = onMapClick
        renderIntoMapIfReady()
    }

    fun setLifecycleResumed(isResumed: Boolean) {
        isLifecycleResumed = isResumed
        syncLifecycleToMapView(
            reason =
                if (isResumed) {
                    "lifecycle-resume"
                } else {
                    "lifecycle-pause"
                },
        )
    }

    fun finish() {
        if (isFinished) return
        Log.i(KAKAO_MAP_LOG_TAG, "Finishing Kakao map renderer controller")
        isFinished = true
        isStarted = false
        kakaoMap = null
        hasMapLifecycleResumed = false
        lifecycleDispatchRetryCount = 0
        mapView?.removeOnAttachStateChangeListener(attachStateListener)
        stopProjectedMarkerTracking()
        cancelProjectedMarkerRetry(resetCount = true)
        mapView?.finish()
        mapView = null
        rendererStatus = KakaoRendererStatus.Initializing
        rendererFailure = null
        projectedMarkerOverlays = emptyList()
        viewportBoundsChangedHandler = null
        backgroundClickHandler = null
        facilityMarkerStyleCache?.clear()
        facilityMarkerStyleCache = null
        overlayMarkerStyleCache?.clear()
        overlayMarkerStyleCache = null
        lastRenderedCameraRequestId = null
        lastRenderedCameraTarget = null
        lastRenderedRouteCameraSignature = null
        lastRenderedMarkers = emptyList()
        lastRenderedPointOverlayMarkers = emptyList()
        lastRenderedApprovedReportMarkers = emptyList()
        lastRenderedArrowOverlayMarkers = emptyList()
        lastRenderedRouteLines = emptyList()
        lastNativeSegmentRenderPathDebugSummary = null
        lastRouteDirectionArrowDebugSummary = null
        lastRouteDirectionArrowLayerSyncSummary = null
        lastCameraBearingComparisonSummary = null
        latestListenerCameraPosition = null
        latestCameraPositionRequestSequence = 0L
    }

    fun markRendererTimedOut() {
        if (isFinished || rendererStatus != KakaoRendererStatus.Initializing || kakaoMap != null) return

        val failure = createKakaoRendererTimeoutFailure()
        rendererFailure = failure
        rendererStatus = KakaoRendererStatus.Error
        hasMapLifecycleResumed = false
        Log.e(
            KAKAO_MAP_LOG_TAG,
            "Kakao map renderer timed out before ready: ${failure.debugSummary}",
        )
    }

    private fun startMap(createdMapView: MapView) {
        if (isStarted || isFinished) return

        isStarted = true
        Log.i(KAKAO_MAP_LOG_TAG, "Starting Kakao map renderer")
        createdMapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() {
                    if (isFinished) {
                        Log.i(KAKAO_MAP_LOG_TAG, "Ignoring Kakao map destroy callback after controller finish")
                        return
                    }

                    val destroyFailure =
                        resolveKakaoRendererFailureAfterUnexpectedDestroy(
                            existingFailure = rendererFailure,
                        )
                    rendererFailure = destroyFailure
                    rendererStatus = KakaoRendererStatus.Error
                    kakaoMap = null
                    stopProjectedMarkerTracking()
                    cancelProjectedMarkerRetry(resetCount = true)
                    projectedMarkerOverlays = emptyList()
                    hasMapLifecycleResumed = false
                    lifecycleDispatchRetryCount = 0
                    lastRenderedCameraRequestId = null
                    lastRenderedCameraTarget = null
                    lastRenderedMarkers = emptyList()
                    lastRenderedPointOverlayMarkers = emptyList()
                    lastRenderedApprovedReportMarkers = emptyList()
                    lastRenderedArrowOverlayMarkers = emptyList()
                    invalidateRenderedRouteLines()
                    lastNativeSegmentRenderPathDebugSummary = null
                    lastRouteDirectionArrowDebugSummary = null
                    lastRouteDirectionArrowLayerSyncSummary = null
                    lastCameraBearingComparisonSummary = null
                    latestListenerCameraPosition = null
                    latestCameraPositionRequestSequence = 0L
                    Log.w(
                        KAKAO_MAP_LOG_TAG,
                        "Kakao map renderer destroyed before interactive recovery: ${destroyFailure.debugSummary}",
                    )
                }

                override fun onMapError(error: Exception) {
                    val failure = createKakaoRendererFailure(error)
                    rendererFailure = failure
                    rendererStatus = KakaoRendererStatus.Error
                    hasMapLifecycleResumed = false
                    Log.e(
                        KAKAO_MAP_LOG_TAG,
                        "Kakao map renderer failed before ready: ${failure.debugSummary}",
                        error,
                    )
                }

                override fun onMapResumed() {
                    hasMapLifecycleResumed = true
                    lifecycleDispatchRetryCount = 0
                    invalidateRenderedRouteLines()
                    renderIntoMapIfReady()
                    Log.d(KAKAO_MAP_LOG_TAG, "Kakao map lifecycle resumed")
                }

                override fun onMapPaused() {
                    hasMapLifecycleResumed = false
                    lifecycleDispatchRetryCount = 0
                    Log.d(KAKAO_MAP_LOG_TAG, "Kakao map lifecycle paused")
                }
            },
            object : KakaoMapReadyCallback() {
                override fun onMapReady(readyMap: KakaoMap) {
                    kakaoMap = readyMap
                    rendererFailure = null
                    rendererStatus = KakaoRendererStatus.Ready
                    val cameraTarget =
                        latestState?.cameraTarget
                            ?: com.ssafy.e102.eumgil.feature.map.model.MapCameraTarget.DefaultBusan
                    Log.i(
                        KAKAO_MAP_LOG_TAG,
                        "Kakao map ready ${createKakaoCameraDebugSummary(cameraTarget)}",
                    )
                    readyMap.setPoiClickable(true)
                    readyMap.setOnLabelClickListener { _, _, label ->
                        ((label.getTag() as? String) ?: label.labelId)?.let { markerId ->
                            dispatchMarkerTap(
                                markerId = markerId,
                                position = label.position,
                            )
                            true
                        } ?: false
                    }
                    readyMap.setOnPoiClickListener { _, position, layerId, poiId ->
                        if (layerId == KAKAO_MARKER_LAYER_ID && poiId.isNotBlank()) {
                            dispatchMarkerTap(
                                markerId = poiId,
                                position = position,
                            )
                        } else if (layerId == KAKAO_APPROVED_REPORT_MARKER_LAYER_ID && poiId.isNotBlank()) {
                            dispatchMarkerTap(
                                markerId = poiId,
                                position = position,
                            )
                        }
                    }
                    readyMap.setOnTerrainClickListener { _, position, _ ->
                        dispatchBackgroundMapTap(
                            source = "terrain",
                            position = position,
                        )
                    }
                    readyMap.setOnMapClickListener { _, position, _, poi ->
                        if (poi?.isPoi == true && poi.layerId.isClickableMarkerLayer() && poi.poiId.isNotBlank()) {
                            dispatchMarkerTap(
                                markerId = poi.poiId,
                                position = position,
                            )
                        } else if (poi?.isPoi == true && poi.poiId.isNotBlank()) {
                            dispatchExternalPoiTap(
                                position = position,
                                providerPlaceId = poi.poiId,
                                nameHint = poi.name,
                            )
                        } else if (poi == null) {
                            dispatchBackgroundMapTap(
                                source = "map",
                                position = position,
                            )
                        }
                    }
                    readyMap.setOnCameraMoveStartListener { _, _ ->
                        latestState?.let { state ->
                            requestLatestCameraPositionAndSyncMarkers(
                                readyMap = readyMap,
                                state = state,
                                reason = "camera-move-start",
                            )
                        }
                        startProjectedMarkerTracking()
                    }
                    readyMap.setOnCameraMoveEndListener { _, cameraPosition, gestureType ->
                        val moveEndCameraPosition =
                            cameraPosition.toResolvedCameraPosition(
                                source = KAKAO_CAMERA_BEARING_SOURCE_MOVE_END,
                            )
                        latestListenerCameraPosition = moveEndCameraPosition
                        val movedCenter =
                            MapCoordinate(
                                latitude = cameraPosition.position.latitude,
                                longitude = cameraPosition.position.longitude,
                            )
                        val selectedMapPinVisibleInViewport =
                            resolveSelectedMapPinViewportVisibility(
                                selectedMapPinCoordinate = latestState?.selectedMapPinCoordinate,
                                viewportWidth = mapView?.width ?: 0,
                                viewportHeight = mapView?.height ?: 0,
                            ) { coordinate ->
                                readyMap
                                    .toScreenPoint(
                                        LatLng.from(
                                            coordinate.latitude,
                                            coordinate.longitude,
                                        ),
                                    )?.let { point ->
                                        KakaoMapScreenPoint(x = point.x, y = point.y)
                                    }
                            }
                        lastRenderedCameraTarget =
                            syncRenderedKakaoCameraTarget(
                                previousTarget = lastRenderedCameraTarget,
                                latestStateTarget = latestState?.cameraTarget,
                                center = movedCenter,
                                zoomLevel = cameraPosition.zoomLevel,
                            )
                        cameraMoveEndHandler?.invoke(
                            movedCenter,
                            cameraPosition.zoomLevel,
                            gestureType.isUserDrivenCameraMove(),
                            selectedMapPinVisibleInViewport,
                        )
                        dispatchViewportBounds(readyMap)
                        latestState?.let { state ->
                            syncMarkers(
                                readyMap = readyMap,
                                state = state,
                                preferredCameraPosition = moveEndCameraPosition,
                                synchronousCameraPosition = moveEndCameraPosition,
                                reason = "camera-move-end",
                            )
                        }
                        stopProjectedMarkerTracking()
                        updateProjectedMarkerOverlays(readyMap = readyMap, state = latestState)
                    }
                    renderIntoMapIfReady()
                    syncLifecycleToMapView(reason = "map-ready")
                }

                override fun getPosition(): LatLng {
                    val cameraTarget =
                        latestState?.cameraTarget
                            ?: com.ssafy.e102.eumgil.feature.map.model.MapCameraTarget.DefaultBusan
                    return LatLng.from(
                        cameraTarget.center.latitude,
                        cameraTarget.center.longitude,
                    )
                }

                override fun getZoomLevel(): Int {
                    val cameraTarget =
                        latestState?.cameraTarget
                            ?: com.ssafy.e102.eumgil.feature.map.model.MapCameraTarget.DefaultBusan
                    return createKakaoCameraRenderState(cameraTarget).zoomLevel
                }
            },
        )
    }

    private fun renderIntoMapIfReady() {
        val readyMap = kakaoMap ?: return
        val state = latestState ?: return

        syncCamera(readyMap = readyMap, state = state)
        syncRouteLines(readyMap = readyMap, state = state)
        syncMarkers(readyMap = readyMap, state = state)
        updateProjectedMarkerOverlays(readyMap = readyMap, state = state)
        dispatchViewportBounds(readyMap)
    }

    private fun dispatchViewportBounds(readyMap: KakaoMap) {
        viewportBoundsChangedHandler?.invoke(readyMap.toViewportBounds())
    }

    private fun KakaoMap.toViewportBounds(): MapViewportBounds? {
        val viewportRect = viewport
        if (viewportRect.width() <= 0 || viewportRect.height() <= 0) return null

        val viewportCornerCoordinates =
            listOf(
                viewportRect.left to viewportRect.bottom,
                viewportRect.left to viewportRect.top,
                viewportRect.right to viewportRect.bottom,
                viewportRect.right to viewportRect.top,
            ).map { (x, y) ->
                val latLng = fromScreenPoint(x, y) ?: return null
                MapCoordinate(
                    latitude = latLng.latitude,
                    longitude = latLng.longitude,
                )
            }

        return createViewportBounds(viewportCornerCoordinates)
    }

    private fun syncLifecycleToMapView(reason: String) {
        val boundMapView = mapView ?: return
        val lifecycleCommand =
            resolveKakaoMapLifecycleCommand(
                isLifecycleResumed = isLifecycleResumed,
                hasMapView = true,
                isAttachedToWindow = boundMapView.isAttachedToWindow,
                isStarted = isStarted,
                isFinished = isFinished,
                hasResumedLifecycle = hasMapLifecycleResumed,
            )
        if (lifecycleCommand == KakaoMapLifecycleCommand.NONE) {
            Log.d(
                KAKAO_MAP_LOG_TAG,
                "Skipping map lifecycle sync reason=$reason started=$isStarted finished=$isFinished resumed=$isLifecycleResumed attached=${boundMapView.isAttachedToWindow} surface=${boundMapView.surfaceView != null} ready=${kakaoMap != null} mapResumed=$hasMapLifecycleResumed",
            )
            return
        }

        boundMapView.post {
            if (mapView !== boundMapView) return@post
            val replayCommand =
                resolveKakaoMapLifecycleCommand(
                    isLifecycleResumed = isLifecycleResumed,
                    hasMapView = true,
                    isAttachedToWindow = boundMapView.isAttachedToWindow,
                    isStarted = isStarted,
                    isFinished = isFinished,
                    hasResumedLifecycle = hasMapLifecycleResumed,
                )
            when (replayCommand) {
                KakaoMapLifecycleCommand.RESUME -> {
                    Log.d(KAKAO_MAP_LOG_TAG, "Dispatching map resume reason=$reason")
                    runCatching { boundMapView.resume() }
                        .onSuccess {
                            lifecycleDispatchRetryCount = 0
                        }
                        .onFailure { error ->
                            if (scheduleLifecycleRetry(boundMapView, reason, "resume", error)) return@onFailure
                            val failure = createKakaoRendererFailure(error)
                            rendererFailure = failure
                            rendererStatus = KakaoRendererStatus.Error
                            Log.e(
                                KAKAO_MAP_LOG_TAG,
                                "Kakao map resume failed reason=$reason ${failure.debugSummary}",
                                error,
                            )
                        }
                }

                KakaoMapLifecycleCommand.PAUSE -> {
                    Log.d(KAKAO_MAP_LOG_TAG, "Dispatching map pause reason=$reason")
                    runCatching { boundMapView.pause() }
                        .onSuccess {
                            lifecycleDispatchRetryCount = 0
                        }
                        .onFailure { error ->
                            if (scheduleLifecycleRetry(boundMapView, reason, "pause", error)) return@onFailure
                            Log.w(KAKAO_MAP_LOG_TAG, "Kakao map pause failed reason=$reason", error)
                        }
                }

                KakaoMapLifecycleCommand.NONE -> Unit
            }
        }
    }

    private fun scheduleLifecycleRetry(
        boundMapView: MapView,
        reason: String,
        action: String,
        error: Throwable? = null,
    ): Boolean {
        if (lifecycleDispatchRetryCount >= MAX_LIFECYCLE_DISPATCH_RETRIES) {
            return false
        }
        lifecycleDispatchRetryCount += 1
        if (error != null) {
            Log.w(
                KAKAO_MAP_LOG_TAG,
                "Retrying map $action dispatch reason=$reason attempt=$lifecycleDispatchRetryCount",
                error,
            )
        } else {
            Log.d(
                KAKAO_MAP_LOG_TAG,
                "Waiting for map $action reason=$reason attempt=$lifecycleDispatchRetryCount attached=${boundMapView.isAttachedToWindow} surface=${boundMapView.surfaceView != null}",
            )
        }
        boundMapView.postDelayed(
            {
                if (mapView !== boundMapView) return@postDelayed
                syncLifecycleToMapView(
                    reason = "$reason-retry$lifecycleDispatchRetryCount",
                )
            },
            LIFECYCLE_DISPATCH_RETRY_DELAY_MILLIS,
        )
        return true
    }

    private fun syncCamera(
        readyMap: KakaoMap,
        state: MapViewportUiState,
    ) {
        val currentTarget = state.cameraTarget
        val cameraState = createKakaoCameraRenderState(currentTarget)
        val routeCameraState = createKakaoRouteCameraRenderState(state.overlayState)
        if (routeCameraState != null) {
            if (
                lastRenderedCameraRequestId == cameraState.requestId &&
                lastRenderedRouteCameraSignature == routeCameraState.signature
            ) {
                return
            }
            val routePoints =
                routeCameraState.points
                    .map { point -> LatLng.from(point.latitude, point.longitude) }
                    .toTypedArray()
            readyMap.moveCamera(
                CameraUpdateFactory.fitMapPoints(routePoints, KAKAO_ROUTE_CAMERA_PADDING),
                CameraAnimation.from(KAKAO_ZOOM_CAMERA_ANIMATION_DURATION_MILLIS),
            )
            lastRenderedCameraRequestId = cameraState.requestId
            lastRenderedCameraTarget = currentTarget
            lastRenderedRouteCameraSignature = routeCameraState.signature
            Log.d(
                KAKAO_MAP_LOG_TAG,
                "Route camera fitted points=${routePoints.size} requestId=${cameraState.requestId}",
            )
            return
        }
        lastRenderedRouteCameraSignature = null
        if (
            lastRenderedCameraRequestId == cameraState.requestId ||
            shouldSkipKakaoCameraSync(
                renderedTarget = lastRenderedCameraTarget,
                requestedTarget = currentTarget,
            )
        ) {
            lastRenderedCameraRequestId = cameraState.requestId
            lastRenderedCameraTarget = currentTarget
            return
        }
        val cameraUpdate =
            if (cameraState.bearingDegrees != null) {
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.from(
                        cameraState.latitude,
                        cameraState.longitude,
                        cameraState.zoomLevel,
                        0.0,
                        kakaoCameraRotationRadians(cameraState.bearingDegrees),
                        0.0,
                    ),
                )
            } else {
                CameraUpdateFactory.newCenterPosition(
                    LatLng.from(cameraState.latitude, cameraState.longitude),
                    cameraState.zoomLevel,
                )
            }
        if (shouldAnimateKakaoCameraTransition(previousTarget = lastRenderedCameraTarget, nextTarget = currentTarget)) {
            readyMap.moveCamera(
                cameraUpdate,
                CameraAnimation.from(KAKAO_ZOOM_CAMERA_ANIMATION_DURATION_MILLIS),
            )
        } else {
            readyMap.moveCamera(cameraUpdate)
        }
        lastRenderedCameraRequestId = cameraState.requestId
        lastRenderedCameraTarget = currentTarget
        Log.d(
            KAKAO_MAP_LOG_TAG,
            "Camera synced ${createKakaoCameraDebugSummary(currentTarget)}",
        )
    }

    private fun syncRouteLines(
        readyMap: KakaoMap,
        state: MapViewportUiState,
    ) {
        val routeLineStates = createKakaoRouteLineRenderStates(state.overlayState.polylines)
        if (routeLineStates.isEmpty() && lastRenderedRouteLines.isNotEmpty() && consecutiveEmptyRouteLineFrameCount == 0) {
            consecutiveEmptyRouteLineFrameCount += 1
            Log.d(
                KAKAO_MAP_LOG_TAG,
                "Preserving previous route lines for one empty frame previousCount=${lastRenderedRouteLines.size}",
            )
            return
        }
        consecutiveEmptyRouteLineFrameCount =
            if (routeLineStates.isEmpty()) {
                consecutiveEmptyRouteLineFrameCount + 1
            } else {
                0
            }
        if (lastRenderedRouteLines == routeLineStates) return

        val routeLineManager = readyMap.routeLineManager ?: return
        val routeLineLayer =
            routeLineManager.getLayer(KAKAO_ROUTE_LINE_LAYER_ID)
                ?: routeLineManager.addLayer(
                    KAKAO_ROUTE_LINE_LAYER_ID,
                    KAKAO_ROUTE_LINE_LAYER_Z_ORDER,
                )
                ?: return
        routeLineLayer.removeAll()
        if (routeLineStates.isNotEmpty()) {
            routeLineStates.forEach { routeLine ->
                val points =
                    routeLine.points.map { point ->
                        LatLng.from(point.latitude, point.longitude)
                    }
                val style =
                    RouteLineStyle.from(
                        routeLine.lineWidth,
                        routeLine.lineColor,
                        routeLine.strokeWidth,
                        routeLine.strokeColor,
                    )
                val segment = RouteLineSegment.from(points, style)
                routeLineLayer.addRouteLine(
                    RouteLineOptions
                        .from(routeLine.routeLineId, segment)
                        .setZOrder(routeLine.zOrder),
                )
            }
        }
        lastRenderedRouteLines = routeLineStates
        Log.d(
            KAKAO_MAP_LOG_TAG,
            "Route lines synced count=${routeLineStates.size}",
        )
    }

    private fun invalidateRenderedRouteLines() {
        lastRenderedRouteLines = emptyList()
        consecutiveEmptyRouteLineFrameCount = 0
    }

    private fun syncMarkers(
        readyMap: KakaoMap,
        state: MapViewportUiState,
        preferredCameraPosition: KakaoResolvedCameraPosition? = null,
        synchronousCameraPosition: KakaoResolvedCameraPosition? = null,
        reason: String = "render",
    ) {
        // Overlay-only screens do not own a feature-level camera state, so arrow spacing/rotation
        // must follow the Kakao renderer's actual camera values instead of a copied FE snapshot.
        val syncSnapshotCameraPosition =
            synchronousCameraPosition
                ?: readyMap
                    .getCameraPosition()
                    ?.toResolvedCameraPosition(source = KAKAO_CAMERA_BEARING_SOURCE_SYNC_SNAPSHOT)
        val effectiveCameraPosition =
            resolveEffectiveCameraPosition(
                state = state,
                syncSnapshotCameraPosition = syncSnapshotCameraPosition,
                preferredCameraPosition = preferredCameraPosition,
            )
        val listenerCameraPosition = preferredCameraPosition ?: latestListenerCameraPosition
        val markerRenderStates =
            createKakaoMarkerRenderStates(
                markerOverlayState = state.markerOverlayState,
                selectedMarkerId = state.selectedMarkerId,
            )
        val overlayMarkerRenderComputation =
            createKakaoOverlayMarkerRenderComputation(
                overlayPoints = state.overlayState.points,
                polylines = state.overlayState.polylines,
                cameraLatitude = effectiveCameraPosition.latitude,
                zoomLevel = effectiveCameraPosition.zoomLevel,
                cameraBearingSource = effectiveCameraPosition.source,
                cameraBearingRadians = effectiveCameraPosition.bearingRadians,
                cameraBearingDegrees = effectiveCameraPosition.bearingDegrees,
                screenDensity = mapView?.resources?.displayMetrics?.density ?: 1f,
            )
        val overlayMarkerRenderStates = overlayMarkerRenderComputation.markers
        val overlayMarkerRenderPartition = partitionKakaoOverlayMarkerRenderStates(overlayMarkerRenderStates)
        val pointOverlayMarkerRenderStates = overlayMarkerRenderPartition.pointMarkers
        val approvedReportMarkerRenderStates = overlayMarkerRenderPartition.approvedReportMarkers
        val arrowOverlayMarkerRenderStates = overlayMarkerRenderPartition.directionArrowMarkers
        logCameraBearingComparison(
            reason = reason,
            syncSnapshotCameraPosition = syncSnapshotCameraPosition,
            listenerCameraPosition = listenerCameraPosition,
            effectiveCameraPosition = effectiveCameraPosition,
        )
        if (
            lastRenderedMarkers == markerRenderStates &&
            lastRenderedPointOverlayMarkers == pointOverlayMarkerRenderStates &&
            lastRenderedApprovedReportMarkers == approvedReportMarkerRenderStates &&
            lastRenderedArrowOverlayMarkers == arrowOverlayMarkerRenderStates
        ) {
            return
        }

        val labelManager = readyMap.labelManager ?: return
        val markerStyleCache = facilityMarkerStyleCache ?: return
        val overlayStyleCache = overlayMarkerStyleCache ?: return
        if (lastRenderedMarkers != markerRenderStates) {
            if (!syncFacilityMarkerLayer(labelManager, markerStyleCache, markerRenderStates)) return
            lastRenderedMarkers = markerRenderStates
        }
        if (lastRenderedPointOverlayMarkers != pointOverlayMarkerRenderStates) {
            val isOverlayMarkerLayerClickable = pointOverlayMarkerRenderStates.any { marker -> marker.clickTargetId != null }
            if (
                !syncOverlayMarkerLayer(
                    labelManager = labelManager,
                    overlayStyleCache = overlayStyleCache,
                    layerId = KAKAO_OVERLAY_MARKER_LAYER_ID,
                    zOrder = KAKAO_OVERLAY_MARKER_LAYER_Z_ORDER,
                    isLayerClickable = isOverlayMarkerLayerClickable,
                    markers = pointOverlayMarkerRenderStates,
                )
            ) {
                return
            }
            lastRenderedPointOverlayMarkers = pointOverlayMarkerRenderStates
            val nativeRenderPathSummary =
                createNativeSegmentRenderPathDebugSummary(
                    layerId = KAKAO_OVERLAY_MARKER_LAYER_ID,
                    markers = pointOverlayMarkerRenderStates,
                )
            if (nativeRenderPathSummary != lastNativeSegmentRenderPathDebugSummary) {
                lastNativeSegmentRenderPathDebugSummary = nativeRenderPathSummary
                Log.d(KAKAO_MAP_LOG_TAG, "SegmentMarkerTrace[RenderPath] $nativeRenderPathSummary")
            }
        }
        if (lastRenderedApprovedReportMarkers != approvedReportMarkerRenderStates) {
            val isApprovedReportMarkerLayerClickable =
                approvedReportMarkerRenderStates.any { marker -> marker.clickTargetId != null }
            if (
                !syncOverlayMarkerLayer(
                    labelManager = labelManager,
                    overlayStyleCache = overlayStyleCache,
                    layerId = KAKAO_APPROVED_REPORT_MARKER_LAYER_ID,
                    zOrder = KAKAO_APPROVED_REPORT_MARKER_LAYER_Z_ORDER,
                    isLayerClickable = isApprovedReportMarkerLayerClickable,
                    markers = approvedReportMarkerRenderStates,
                )
            ) {
                return
            }
            lastRenderedApprovedReportMarkers = approvedReportMarkerRenderStates
        }
        if (lastRenderedArrowOverlayMarkers != arrowOverlayMarkerRenderStates) {
            val routeDirectionArrowLayerSyncResult =
                syncRouteDirectionArrowLayer(
                    labelManager = labelManager,
                    overlayStyleCache = overlayStyleCache,
                    markers = arrowOverlayMarkerRenderStates,
                    previousMarkers = lastRenderedArrowOverlayMarkers,
                ) ?: return
            lastRenderedArrowOverlayMarkers = arrowOverlayMarkerRenderStates
            val routeDirectionArrowLayerSyncSummary =
                createKakaoRouteDirectionArrowLayerSyncSummary(
                    syncResult = routeDirectionArrowLayerSyncResult,
                    markerCount = arrowOverlayMarkerRenderStates.size,
                    isCameraMoveInProgress = isCameraMoveInProgress,
                )
            if (routeDirectionArrowLayerSyncSummary != lastRouteDirectionArrowLayerSyncSummary) {
                lastRouteDirectionArrowLayerSyncSummary = routeDirectionArrowLayerSyncSummary
                Log.d(KAKAO_MAP_LOG_TAG, "RouteArrowLayerTrace $routeDirectionArrowLayerSyncSummary")
            }
        }
        val routeDirectionArrowDebugSummary =
            overlayMarkerRenderComputation.routeDirectionArrowDebugStates
                .takeIf { debugStates -> debugStates.isNotEmpty() }
                ?.joinToString(separator = " | ", transform = ::createKakaoRouteDirectionArrowDebugSummary)
        if (routeDirectionArrowDebugSummary != lastRouteDirectionArrowDebugSummary) {
            lastRouteDirectionArrowDebugSummary = routeDirectionArrowDebugSummary
            Log.d(
                KAKAO_MAP_LOG_TAG,
                if (routeDirectionArrowDebugSummary == null) {
                    "RouteArrowTrace none"
                } else {
                    "RouteArrowTrace count=${overlayMarkerRenderComputation.routeDirectionArrowDebugStates.size} $routeDirectionArrowDebugSummary"
                },
            )
        }
        Log.d(
            KAKAO_MAP_LOG_TAG,
            buildString {
                append("Markers synced ")
                append(
                    createKakaoMarkerDebugSummary(
                        markerOverlayState = state.markerOverlayState,
                        renderedMarkers = markerRenderStates,
                        selectedMarkerId = state.selectedMarkerId,
                    ),
                )
                state.selectedMapPinCoordinate?.let { coordinate ->
                    append(" pin=")
                    append(coordinate.latitude.toLogCoordinate())
                    append(",")
                    append(coordinate.longitude.toLogCoordinate())
                }
                append(" overlayPoints=")
                append(pointOverlayMarkerRenderStates.size)
                append(" approvedReports=")
                append(approvedReportMarkerRenderStates.size)
                append(" overlayArrows=")
                append(arrowOverlayMarkerRenderStates.size)
                append(" bearingSource=")
                append(effectiveCameraPosition.source)
                append(" bearingRad=")
                append(String.format(Locale.US, "%.4f", effectiveCameraPosition.bearingRadians))
                append(" bearingDeg=")
                append(String.format(Locale.US, "%.2f", effectiveCameraPosition.bearingDegrees))
            },
        )
    }

    private fun resolveEffectiveCameraPosition(
        state: MapViewportUiState,
        syncSnapshotCameraPosition: KakaoResolvedCameraPosition?,
        preferredCameraPosition: KakaoResolvedCameraPosition?,
    ): KakaoResolvedCameraPosition {
        val cachedListenerCameraPosition = latestListenerCameraPosition
        return when {
            preferredCameraPosition != null -> preferredCameraPosition
            syncSnapshotCameraPosition != null -> syncSnapshotCameraPosition
            cachedListenerCameraPosition != null -> cachedListenerCameraPosition
            else -> state.cameraTarget.toFallbackResolvedCameraPosition()
        }
    }

    private fun requestLatestCameraPositionAndSyncMarkers(
        readyMap: KakaoMap,
        state: MapViewportUiState,
        reason: String,
    ) {
        val syncSnapshotCameraPosition =
            readyMap
                .getCameraPosition()
                ?.toResolvedCameraPosition(source = KAKAO_CAMERA_BEARING_SOURCE_SYNC_SNAPSHOT)
        val requestSequence = ++latestCameraPositionRequestSequence
        readyMap.getCameraPosition { latestCameraPosition ->
            if (isFinished || kakaoMap !== readyMap) return@getCameraPosition
            if (requestSequence != latestCameraPositionRequestSequence && isCameraMoveInProgress) {
                return@getCameraPosition
            }
            val resolvedCameraPosition =
                latestCameraPosition.toResolvedCameraPosition(
                    source = KAKAO_CAMERA_BEARING_SOURCE_CAMERA_POSITION_LISTENER,
                )
            latestListenerCameraPosition = resolvedCameraPosition
            syncMarkers(
                readyMap = readyMap,
                state = latestState ?: state,
                preferredCameraPosition = resolvedCameraPosition,
                synchronousCameraPosition = syncSnapshotCameraPosition,
                reason = reason,
            )
        }
    }

    private fun logCameraBearingComparison(
        reason: String,
        syncSnapshotCameraPosition: KakaoResolvedCameraPosition?,
        listenerCameraPosition: KakaoResolvedCameraPosition?,
        effectiveCameraPosition: KakaoResolvedCameraPosition,
    ) {
        if (syncSnapshotCameraPosition == null && listenerCameraPosition == null) return

        val summary =
            buildString {
                append("reason=")
                append(reason)
                append(" cameraMoving=")
                append(isCameraMoveInProgress)
                append(" effectiveSource=")
                append(effectiveCameraPosition.source)
                append(" effectiveDeg=")
                append(String.format(Locale.US, "%.2f", effectiveCameraPosition.bearingDegrees))
                append(" syncDeg=")
                append(syncSnapshotCameraPosition?.bearingDegrees?.let { String.format(Locale.US, "%.2f", it) } ?: "null")
                append(" syncRad=")
                append(syncSnapshotCameraPosition?.bearingRadians?.let { String.format(Locale.US, "%.4f", it) } ?: "null")
                append(" listenerSource=")
                append(listenerCameraPosition?.source ?: "null")
                append(" listenerDeg=")
                append(listenerCameraPosition?.bearingDegrees?.let { String.format(Locale.US, "%.2f", it) } ?: "null")
                append(" listenerRad=")
                append(listenerCameraPosition?.bearingRadians?.let { String.format(Locale.US, "%.4f", it) } ?: "null")
                append(" staleDeltaDeg=")
                append(
                    if (syncSnapshotCameraPosition != null && listenerCameraPosition != null) {
                        String.format(
                            Locale.US,
                            "%.2f",
                            listenerCameraPosition.bearingDegrees - syncSnapshotCameraPosition.bearingDegrees,
                        )
                    } else {
                        "null"
                    },
                )
            }
        if (summary == lastCameraBearingComparisonSummary) return
        lastCameraBearingComparisonSummary = summary
        Log.d(KAKAO_MAP_LOG_TAG, "CameraBearingTrace $summary")
    }

    private fun syncFacilityMarkerLayer(
        labelManager: LabelManager,
        markerStyleCache: KakaoFacilityMarkerStyleCache,
        markerRenderStates: List<KakaoMarkerRenderState>,
    ): Boolean {
        val existingLayer = labelManager.getLayer(KAKAO_MARKER_LAYER_ID)
        if (markerRenderStates.isEmpty()) {
            existingLayer?.let { layer -> labelManager.remove(layer) }
            return true
        }

        val layer = existingLayer ?: labelManager.getOrCreateLabelLayer(KAKAO_MARKER_LAYER_ID, true, KAKAO_MARKER_LAYER_Z_ORDER) ?: return false
        layer.setClickable(true)
        layer.removeAll()
        markerRenderStates
            .sortedWith(compareByDescending<KakaoMarkerRenderState> { it.rank }.thenBy { it.markerId })
            .forEach { marker ->
                val labelStyles = markerStyleCache.stylesFor(labelManager, marker)
                val label =
                    layer.addLabel(
                        LabelOptions
                            .from(
                                marker.markerId,
                                LatLng.from(marker.latitude, marker.longitude),
                            ).setStyles(labelStyles)
                            .setRank(marker.rank)
                            .setClickable(marker.clickTargetId != null)
                            .setTag(marker.clickTargetId ?: marker.markerId),
                    ) ?: return@forEach
                marker.clickTargetId?.let(label::setTag)
                label.setClickable(marker.clickTargetId != null)
            }
        return true
    }

    private fun syncOverlayMarkerLayer(
        labelManager: LabelManager,
        overlayStyleCache: KakaoOverlayMarkerStyleCache,
        layerId: String,
        zOrder: Int,
        isLayerClickable: Boolean,
        markers: List<KakaoOverlayMarkerRenderState>,
    ): Boolean {
        val existingLayer = labelManager.getLayer(layerId)
        if (markers.isEmpty()) {
            existingLayer?.let { layer -> labelManager.remove(layer) }
            return true
        }

        val layer = existingLayer ?: labelManager.getOrCreateLabelLayer(layerId, isLayerClickable, zOrder) ?: return false
        layer.removeAll()
        layer.setClickable(isLayerClickable)
        markers
            .sortedBy(KakaoOverlayMarkerRenderState::markerId)
            .forEach { marker ->
                val labelStyles = overlayStyleCache.stylesFor(labelManager, marker)
                val labelOptions =
                    LabelOptions
                        .from(
                            marker.markerId,
                            LatLng.from(
                                marker.coordinate.latitude,
                                marker.coordinate.longitude,
                            ),
                        ).setStyles(labelStyles)
                        .setRank(KAKAO_OVERLAY_MARKER_RANK)
                        .setClickable(marker.clickTargetId != null)
                        .setTag(marker.clickTargetId ?: marker.markerId)
                resolveKakaoOverlayMarkerTransformMethod(marker.kind)?.let(labelOptions::setTransform)
                val label =
                    layer.addLabel(
                        labelOptions,
                    ) ?: return@forEach
                marker.clickTargetId?.let(label::setTag)
                label.setClickable(marker.clickTargetId != null)
            }
        return true
    }

    private fun syncRouteDirectionArrowLayer(
        labelManager: LabelManager,
        overlayStyleCache: KakaoOverlayMarkerStyleCache,
        markers: List<KakaoOverlayMarkerRenderState>,
        previousMarkers: List<KakaoOverlayMarkerRenderState>,
    ): KakaoRouteDirectionArrowLayerSyncResult? {
        val existingLayer = labelManager.getLayer(KAKAO_DIRECTION_ARROW_LAYER_ID)
        if (markers.isEmpty()) {
            val removedCount = previousMarkers.size
            existingLayer?.let(labelManager::remove)
            return KakaoRouteDirectionArrowLayerSyncResult(removed = removedCount)
        }

        val layer =
            existingLayer
                ?: labelManager.getOrCreateLabelLayer(
                    KAKAO_DIRECTION_ARROW_LAYER_ID,
                    false,
                    KAKAO_DIRECTION_ARROW_LAYER_Z_ORDER,
                )
                ?: return null
        layer.setClickable(false)

        val previousMarkersById = previousMarkers.associateBy(KakaoOverlayMarkerRenderState::markerId)
        val nextMarkerIds = markers.mapTo(mutableSetOf(), KakaoOverlayMarkerRenderState::markerId)
        var addedCount = 0
        var movedCount = 0
        var styleChangedCount = 0
        var removedCount = 0
        var unchangedCount = 0

        previousMarkersById
            .keys
            .filterNot(nextMarkerIds::contains)
            .forEach { markerId ->
                layer.getLabel(markerId)?.let { label ->
                    layer.remove(label)
                    removedCount += 1
                }
            }

        markers
            .sortedBy(KakaoOverlayMarkerRenderState::markerId)
            .forEach { marker ->
                val previousMarker = previousMarkersById[marker.markerId]
                val existingLabel = layer.getLabel(marker.markerId)
                val labelStyles = overlayStyleCache.stylesFor(labelManager, marker)
                val hasEquivalentBitmap = previousMarker?.hasEquivalentArrowBitmap(marker) == true
                if (existingLabel == null) {
                    val labelOptions =
                        LabelOptions
                            .from(
                                marker.markerId,
                                LatLng.from(
                                    marker.coordinate.latitude,
                                    marker.coordinate.longitude,
                                ),
                            ).setStyles(labelStyles)
                            .setRank(KAKAO_OVERLAY_MARKER_RANK)
                            .setClickable(false)
                            .setTag(marker.markerId)
                    resolveKakaoOverlayMarkerTransformMethod(marker.kind)?.let(labelOptions::setTransform)
                    layer.addLabel(labelOptions) ?: return@forEach
                    addedCount += 1
                    return@forEach
                }

                if (previousMarker?.coordinate != marker.coordinate) {
                    existingLabel.moveTo(
                        LatLng.from(
                            marker.coordinate.latitude,
                            marker.coordinate.longitude,
                        ),
                    )
                    movedCount += 1
                }
                if (!hasEquivalentBitmap) {
                    existingLabel.changeStyles(labelStyles)
                    styleChangedCount += 1
                }
                existingLabel.setTag(marker.markerId)
                if (previousMarker?.coordinate == marker.coordinate && hasEquivalentBitmap) {
                    unchangedCount += 1
                }
            }

        return KakaoRouteDirectionArrowLayerSyncResult(
            added = addedCount,
            moved = movedCount,
            styleChanged = styleChangedCount,
            removed = removedCount,
            unchanged = unchangedCount,
        )
    }

    private fun dispatchMapTap(
        source: String,
        position: LatLng,
        clickType: MapTapClickType,
        providerPlaceId: String? = null,
        nameHint: String? = null,
    ) {
        val coordinate =
            MapCoordinate(
                latitude = position.latitude,
                longitude = position.longitude,
            )
        val now = SystemClock.elapsedRealtime()
        val suppressedByMarkerTap =
            source == "terrain" &&
                isSuppressedByRecentMarkerTap(coordinate = coordinate, now = now)
        val previousCoordinate = lastDispatchedMapTapCoordinate
        val isDuplicate =
            previousCoordinate != null &&
                now - lastDispatchedMapTapUptimeMillis <= KAKAO_MAP_TAP_DEDUP_WINDOW_MILLIS &&
                abs(previousCoordinate.latitude - coordinate.latitude) <= KAKAO_MAP_TAP_DEDUP_COORDINATE_EPSILON &&
                abs(previousCoordinate.longitude - coordinate.longitude) <= KAKAO_MAP_TAP_DEDUP_COORDINATE_EPSILON
        if (suppressedByMarkerTap) return
        if (isDuplicate) return
        lastDispatchedMapTapCoordinate = coordinate
        lastDispatchedMapTapUptimeMillis = now
        mapClickHandler?.invoke(
            MapTapPayload(
                coordinate = coordinate,
                clickType = clickType,
                provider = if (clickType == MapTapClickType.POI) KAKAO_PROVIDER_NAME else null,
                providerPlaceId = providerPlaceId,
                nameHint = nameHint?.takeIf { it.isNotBlank() },
            ),
        )
    }

    private fun dispatchBackgroundMapTap(
        source: String,
        position: LatLng,
    ) {
        val coordinate =
            MapCoordinate(
                latitude = position.latitude,
                longitude = position.longitude,
            )
        val now = SystemClock.elapsedRealtime()
        if (isSuppressedByRecentMarkerTap(coordinate = coordinate, now = now)) return

        Log.d(
            KAKAO_MAP_LOG_TAG,
            "Dispatching background single tap source=$source lat=${position.latitude.toLogCoordinate()} lng=${position.longitude.toLogCoordinate()}",
        )
        backgroundClickHandler?.invoke()
    }

    private fun dispatchExternalPoiTap(
        position: LatLng,
        providerPlaceId: String,
        nameHint: String?,
    ) {
        dispatchMapTap(
            source = "poi",
            position = position,
            clickType = MapTapClickType.POI,
            providerPlaceId = providerPlaceId,
            nameHint = nameHint,
        )
    }

    private fun dispatchMarkerTap(
        markerId: String,
        position: LatLng,
    ) {
        val now = SystemClock.elapsedRealtime()
        val isDuplicate =
            lastDispatchedMarkerTapId == markerId &&
                now - lastDispatchedMarkerTapUptimeMillis <= KAKAO_MARKER_TAP_DEDUP_WINDOW_MILLIS
        if (isDuplicate) return

        lastDispatchedMarkerTapId = markerId
        lastDispatchedMarkerTapUptimeMillis = now
        lastSuppressedTerrainTapCoordinate =
            MapCoordinate(
                latitude = position.latitude,
                longitude = position.longitude,
            )
        lastSuppressedTerrainTapUptimeMillis = now
        markerClickHandler?.invoke(markerId)
    }

    private fun isSuppressedByRecentMarkerTap(
        coordinate: MapCoordinate,
        now: Long,
    ): Boolean {
        val previousCoordinate = lastSuppressedTerrainTapCoordinate ?: return false
        return now - lastSuppressedTerrainTapUptimeMillis <= KAKAO_MARKER_TAP_DEDUP_WINDOW_MILLIS &&
            abs(previousCoordinate.latitude - coordinate.latitude) <= KAKAO_MAP_TAP_DEDUP_COORDINATE_EPSILON &&
            abs(previousCoordinate.longitude - coordinate.longitude) <= KAKAO_MAP_TAP_DEDUP_COORDINATE_EPSILON
    }

    private fun updateProjectedMarkerOverlays(
        readyMap: KakaoMap,
        state: MapViewportUiState?,
    ) {
        val cameraBearingDegrees =
            readyMap
                .getCameraPosition()
                ?.toResolvedCameraPosition(source = KAKAO_CAMERA_BEARING_SOURCE_SYNC_SNAPSHOT)
                ?.bearingDegrees
                ?: 0.0
        val projectedMarkers =
            createKakaoProjectedMarkerRenderStates(
                currentLocation = state?.currentLocation,
                selectedOriginCoordinate = state?.selectedOriginCoordinate,
                selectedDestinationCoordinate = state?.selectedDestinationCoordinate,
                selectedMapPinCoordinate = state?.selectedMapPinCoordinate,
                overlayPoints = state?.overlayState?.points.orEmpty(),
                cameraBearingDegrees = cameraBearingDegrees,
            )
        val projectionResult =
            createKakaoProjectedMarkerProjectionResult(projectedMarkers) { coordinate ->
                readyMap
                    .toScreenPoint(
                        LatLng.from(
                            coordinate.latitude,
                            coordinate.longitude,
                        ),
                    )?.let { point ->
                        KakaoMapScreenPoint(x = point.x, y = point.y)
                    }
            }
        projectedMarkerOverlays = projectionResult.overlays
        val retryScheduled =
            projectionResult.shouldRetry &&
                !isCameraMoveInProgress &&
                projectedMarkerTrackingRunnable == null &&
                projectedMarkerRetryRunnable == null &&
                projectedMarkerRetryCount < KAKAO_PROJECTED_MARKER_MAX_RETRY_FRAMES
        logProjectedMarkerPipelineDebugSummary(
            projectedMarkers = projectedMarkers,
            projectionResult = projectionResult,
            retryScheduled = retryScheduled,
        )

        if (projectionResult.shouldRetry && !isCameraMoveInProgress) {
            scheduleProjectedMarkerRetry(readyMap)
        } else if (!projectionResult.shouldRetry) {
            cancelProjectedMarkerRetry(resetCount = true)
        }
    }

    private fun startProjectedMarkerTracking() {
        val boundMapView = mapView ?: return
        val readyMap = kakaoMap ?: return
        isCameraMoveInProgress = true
        if (projectedMarkerTrackingRunnable != null) return

        val trackingRunnable =
            object : Runnable {
                override fun run() {
                    if (!isCameraMoveInProgress || isFinished || mapView !== boundMapView || kakaoMap !== readyMap) {
                        projectedMarkerTrackingRunnable = null
                        return
                    }
                    latestState?.let { state ->
                        syncMarkers(
                            readyMap = readyMap,
                            state = state,
                            reason = "camera-move-tracking",
                        )
                    }
                    updateProjectedMarkerOverlays(readyMap = readyMap, state = latestState)
                    boundMapView.postOnAnimation(this)
                }
            }
        projectedMarkerTrackingRunnable = trackingRunnable
        boundMapView.postOnAnimation(trackingRunnable)
    }

    private fun stopProjectedMarkerTracking() {
        isCameraMoveInProgress = false
        latestCameraPositionRequestSequence = 0L
        val boundMapView = mapView ?: run {
            projectedMarkerTrackingRunnable = null
            return
        }
        projectedMarkerTrackingRunnable?.let(boundMapView::removeCallbacks)
        projectedMarkerTrackingRunnable = null
    }

    private fun scheduleProjectedMarkerRetry(readyMap: KakaoMap) {
        val boundMapView = mapView ?: return
        if (projectedMarkerTrackingRunnable != null || projectedMarkerRetryRunnable != null) return
        if (projectedMarkerRetryCount >= KAKAO_PROJECTED_MARKER_MAX_RETRY_FRAMES) return

        projectedMarkerRetryCount += 1
        val retryRunnable =
            Runnable {
                if (mapView !== boundMapView || kakaoMap !== readyMap || isFinished) {
                    projectedMarkerRetryRunnable = null
                    return@Runnable
                }
                projectedMarkerRetryRunnable = null
                updateProjectedMarkerOverlays(readyMap = readyMap, state = latestState)
            }
        projectedMarkerRetryRunnable = retryRunnable
        boundMapView.postOnAnimation(retryRunnable)
    }

    private fun cancelProjectedMarkerRetry(resetCount: Boolean) {
        val boundMapView = mapView
        projectedMarkerRetryRunnable?.let { retryRunnable ->
            boundMapView?.removeCallbacks(retryRunnable)
        }
        projectedMarkerRetryRunnable = null
        if (resetCount) {
            projectedMarkerRetryCount = 0
        }
    }

    private fun logProjectedMarkerPipelineDebugSummary(
        projectedMarkers: List<KakaoProjectedMarkerRenderState>,
        projectionResult: KakaoProjectedMarkerProjectionResult,
        retryScheduled: Boolean,
    ) {
        val summary =
            createProjectedSegmentMarkerPipelineDebugSummary(
                projectedMarkers = projectedMarkers,
                projectionResult = projectionResult,
                isCameraMoveInProgress = isCameraMoveInProgress,
                retryScheduled = retryScheduled,
                retryCount = projectedMarkerRetryCount,
            )
        if (summary == lastProjectedMarkerPipelineDebugSummary) return
        lastProjectedMarkerPipelineDebugSummary = summary
        Log.d(KAKAO_MAP_LOG_TAG, "SegmentMarkerTrace[Projection] $summary")
    }

    private fun CameraPosition.toResolvedCameraPosition(
        source: String,
    ): KakaoResolvedCameraPosition =
        KakaoResolvedCameraPosition(
            latitude = position.latitude,
            longitude = position.longitude,
            zoomLevel = zoomLevel,
            source = source,
            bearingRadians = rotationAngle,
            bearingDegrees = Math.toDegrees(rotationAngle),
        )

    private fun com.ssafy.e102.eumgil.feature.map.model.MapCameraTarget.toFallbackResolvedCameraPosition(): KakaoResolvedCameraPosition =
        KakaoResolvedCameraPosition(
            latitude = center.latitude,
            longitude = center.longitude,
            zoomLevel = resolvedZoomLevel(),
            source = KAKAO_CAMERA_BEARING_SOURCE_STATE_FALLBACK,
            bearingRadians = 0.0,
            bearingDegrees = 0.0,
        )
}

private enum class KakaoRendererStatus {
    Initializing,
    Ready,
    Error,
}

internal fun shouldShowKakaoRendererFallbackOverlay(
    isRendererReady: Boolean,
    isRendererError: Boolean,
    isAutomaticRetryLoading: Boolean,
    hasLoadingGracePeriodElapsed: Boolean,
): Boolean {
    if (isRendererReady) return false
    return isRendererError || isAutomaticRetryLoading || hasLoadingGracePeriodElapsed
}

private data class KakaoRouteDirectionArrowLayerSyncResult(
    val added: Int = 0,
    val moved: Int = 0,
    val styleChanged: Int = 0,
    val removed: Int = 0,
    val unchanged: Int = 0,
)

private const val KAKAO_MARKER_LAYER_ID = "eumgil-map-markers"
private const val KAKAO_APPROVED_REPORT_MARKER_LAYER_ID = "eumgil-approved-report-markers"
private const val KAKAO_OVERLAY_MARKER_LAYER_ID = "eumgil-overlay-markers"
private const val KAKAO_DIRECTION_ARROW_LAYER_ID = "eumgil-overlay-arrows"
private const val KAKAO_ROUTE_LINE_LAYER_ID = "eumgil-route-lines"
private const val KAKAO_PROVIDER_NAME = "KAKAO"
private const val KAKAO_MAP_LOG_TAG = "KakaoMapViewport"
private const val KAKAO_CAMERA_BEARING_SOURCE_MOVE_END = "move-end"
private const val KAKAO_CAMERA_BEARING_SOURCE_CAMERA_POSITION_LISTENER = "camera-position-listener"
private const val KAKAO_CAMERA_BEARING_SOURCE_STATE_FALLBACK = "state-fallback"
private const val MAX_LIFECYCLE_DISPATCH_RETRIES = 30
private const val LIFECYCLE_DISPATCH_RETRY_DELAY_MILLIS = 50L
private const val KAKAO_RENDERER_RESTART_DELAY_MILLIS = 220L
private const val KAKAO_RENDERER_LOADING_OVERLAY_DELAY_MILLIS = 300L
private const val KAKAO_RENDERER_READY_TIMEOUT_MILLIS = 4_000L
private const val KAKAO_MAP_TAP_DEDUP_WINDOW_MILLIS = 250L
private const val KAKAO_MARKER_TAP_DEDUP_WINDOW_MILLIS = 250L
private const val KAKAO_MAP_TAP_DEDUP_COORDINATE_EPSILON = 0.000001
private const val KAKAO_MARKER_LAYER_Z_ORDER = 1000
private const val KAKAO_APPROVED_REPORT_MARKER_LAYER_Z_ORDER = 1005
private const val KAKAO_OVERLAY_MARKER_LAYER_Z_ORDER = 950
private const val KAKAO_DIRECTION_ARROW_LAYER_Z_ORDER = 940
private const val KAKAO_ROUTE_LINE_LAYER_Z_ORDER = 900
private const val KAKAO_OVERLAY_MARKER_RANK = 0L
private const val KAKAO_ROUTE_CAMERA_PADDING = 84
private const val KAKAO_PROJECTED_MARKER_MAX_RETRY_FRAMES = 6
private const val APPROVED_REPORT_MARKER_FILL = -10163 // 0xFFFFD84D
private const val APPROVED_REPORT_MARKER_STROKE = -2051310 // 0xFFE0B312
private const val APPROVED_REPORT_MARKER_SELECTED_RING = -1 // 0xFFFFFFFF
private const val APPROVED_REPORT_MARKER_SYMBOL = -15658713 // 0xFF111827

private fun String?.isClickableMarkerLayer(): Boolean =
    this == KAKAO_MARKER_LAYER_ID || this == KAKAO_APPROVED_REPORT_MARKER_LAYER_ID

private fun LabelManager.getOrCreateLabelLayer(
    layerId: String,
    isClickable: Boolean,
    zOrder: Int,
): LabelLayer? =
    getLayer(layerId)
        ?: addLayer(
            LabelLayerOptions
                .from(layerId)
                .setCompetitionType(CompetitionType.None)
                .setOrderingType(OrderingType.Rank)
                .setClickable(isClickable)
                .setZOrder(zOrder),
        )

private fun GestureType.isUserDrivenCameraMove(): Boolean = this != GestureType.Unknown

private fun Double.toLogCoordinate(): String = String.format(Locale.US, "%.6f", this)

private fun KakaoOverlayMarkerRenderState.hasEquivalentArrowBitmap(
    other: KakaoOverlayMarkerRenderState,
): Boolean =
    kind == other.kind &&
        sizeDp == other.sizeDp &&
        fillColorArgb == other.fillColorArgb &&
        strokeColorArgb == other.strokeColorArgb &&
        rotationDegrees.roundToInt() == other.rotationDegrees.roundToInt() &&
        label == other.label &&
        secondaryLabel == other.secondaryLabel &&
        secondaryFillColorArgb == other.secondaryFillColorArgb

private fun createKakaoRouteDirectionArrowLayerSyncSummary(
    syncResult: KakaoRouteDirectionArrowLayerSyncResult,
    markerCount: Int,
    isCameraMoveInProgress: Boolean,
): String =
    buildString {
        append("count=")
        append(markerCount)
        append(" added=")
        append(syncResult.added)
        append(" moved=")
        append(syncResult.moved)
        append(" styleChanged=")
        append(syncResult.styleChanged)
        append(" removed=")
        append(syncResult.removed)
        append(" unchanged=")
        append(syncResult.unchanged)
        append(" cameraMoving=")
        append(isCameraMoveInProgress)
    }

@Composable
private fun MapProjectedMarkerOverlay(
    overlay: KakaoProjectedMarkerOverlay,
    contentDescription: String?,
    onMarkerClick: (String) -> Unit,
) {
    val density = LocalDensity.current
    val markerSize = overlay.sizeDp.dp
    val markerWidthPx = with(density) { markerSize.roundToPx() }
    val markerHeightPx = markerWidthPx
    val clickableModifier =
        overlay.clickTargetId?.let { clickTargetId ->
            Modifier.clickable { onMarkerClick(clickTargetId) }
        } ?: Modifier
    val markerModifier =
        Modifier
            .zIndex(overlay.zIndex)
            .offset {
                IntOffset(
                    x = overlay.screenPoint.x - (markerWidthPx * overlay.anchorPointX).toInt(),
                    y = overlay.screenPoint.y - (markerHeightPx * overlay.anchorPointY).toInt(),
                )
            }
            .size(markerSize)
            .then(clickableModifier)
    val translationDistancePx = with(density) { overlay.translationDistanceDp.dp.toPx() }
    val rotationRadians = Math.toRadians(overlay.rotationDegrees.toDouble())

    if (overlay.kind == KakaoProjectedMarkerKind.ROUTE_SEGMENT_JUNCTION) {
        val fillColor = Color(overlay.fillColorArgb ?: 0xFF2A7BFF.toInt())
        val strokeColor = Color(overlay.strokeColorArgb ?: 0xFF0F4FC6.toInt())
        Box(
            modifier = markerModifier,
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
            ) {}
            Surface(
                modifier = Modifier.size((overlay.sizeDp * 0.58f).dp),
                shape = CircleShape,
                color = fillColor,
                border = BorderStroke(1.dp, strokeColor),
            ) {}
        }
        return
    }

    Image(
        painter = painterResource(id = overlay.iconResId),
        contentDescription = contentDescription,
        modifier =
            markerModifier.graphicsLayer {
                rotationZ = overlay.rotationDegrees
                translationX = (sin(rotationRadians) * translationDistancePx).toFloat()
                translationY = (-cos(rotationRadians) * translationDistancePx).toFloat()
            },
    )
}

@Composable
private fun KakaoProjectedMarkerOverlay.resolveContentDescription(
    selectedDestinationName: String?,
): String? =
    when (kind) {
        KakaoProjectedMarkerKind.HAZARD ->
            stringResource(id = R.string.approved_hazard_marker_sheet_title)

        KakaoProjectedMarkerKind.CURRENT_LOCATION ->
            stringResource(id = R.string.navigation_map_marker_current)

        KakaoProjectedMarkerKind.CURRENT_LOCATION_DIRECTION ->
            null

        KakaoProjectedMarkerKind.SELECTED_DESTINATION ->
            selectedDestinationName
                ?: stringResource(id = R.string.map_viewport_description_selected)

        KakaoProjectedMarkerKind.SELECTED_MAP_PIN ->
            stringResource(id = R.string.map_viewport_description_selected)

        KakaoProjectedMarkerKind.ROUTE_ORIGIN ->
            stringResource(id = R.string.navigation_map_marker_origin)

        KakaoProjectedMarkerKind.ROUTE_DESTINATION ->
            stringResource(id = R.string.navigation_map_marker_destination)

        KakaoProjectedMarkerKind.ROUTE_SEGMENT_JUNCTION -> null
    }

private class KakaoOverlayMarkerStyleCache(
    private val context: Context,
) {
    private val densityBucket = resolveDensityBucket(context.resources.displayMetrics.densityDpi)
    private val bitmapCache = mutableMapOf<KakaoOverlayMarkerBitmapCacheKey, Bitmap>()
    private val stylesCache = mutableMapOf<KakaoOverlayMarkerBitmapCacheKey, LabelStyles>()

    fun stylesFor(
        labelManager: LabelManager,
        marker: KakaoOverlayMarkerRenderState,
    ): LabelStyles {
        val key =
            KakaoOverlayMarkerBitmapCacheKey(
                kind = marker.kind,
                iconResId = marker.iconResId,
                fillColorArgb = marker.fillColorArgb,
                strokeColorArgb = marker.strokeColorArgb,
                isSelected = marker.isSelected,
                rotationDegrees = marker.rotationDegrees.roundToInt(),
                label = marker.label,
                secondaryLabel = marker.secondaryLabel,
                secondaryFillColorArgb = marker.secondaryFillColorArgb,
                densityBucket = densityBucket,
            )
        return stylesCache.getOrPut(key) {
            val styles =
                LabelStyles.from(
                    key.styleId,
                    LabelStyle
                        .from(bitmapFor(marker, key))
                        .setApplyDpScale(false)
                        .setAnchorPoint(marker.anchorPointX, marker.anchorPointY),
                )
            labelManager.addLabelStyles(styles) ?: styles
        }
    }

    fun clear() {
        stylesCache.clear()
        bitmapCache.clear()
    }

    private fun bitmapFor(
        marker: KakaoOverlayMarkerRenderState,
        key: KakaoOverlayMarkerBitmapCacheKey,
    ): Bitmap =
        bitmapCache.getOrPut(key) {
            when (marker.kind) {
                KakaoOverlayMarkerKind.APPROVED_REPORT -> createApprovedReportWarningMarkerBitmap(marker)
                KakaoOverlayMarkerKind.ROUTE_SEGMENT_JUNCTION -> createSegmentJunctionBitmap(marker)
                KakaoOverlayMarkerKind.TRANSIT_STOP -> createTransitStopBitmap(marker)
                KakaoOverlayMarkerKind.TRANSIT_TRANSFER -> createTransitTransferBitmap(marker)
                KakaoOverlayMarkerKind.ROUTE_DIRECTION_ARROW -> createDirectionArrowBitmap(marker)
                KakaoOverlayMarkerKind.FOCUS_HALO -> createFocusHaloBitmap(marker)
            }
        }

    private fun createApprovedReportWarningMarkerBitmap(
        marker: KakaoOverlayMarkerRenderState,
    ): Bitmap {
        val sizePx = dpToPx(marker.sizeDp.toFloat())
        val bitmapSizePx = sizePx.roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(bitmapSizePx, bitmapSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        AppCompatResources
            .getDrawable(context, R.drawable.ic_approved_hazard_warning)
            ?.mutate()
            ?.let { drawable ->
                DrawableCompat.setTintList(drawable, null)
                drawable.setBounds(0, 0, bitmapSizePx, bitmapSizePx)
                drawable.draw(canvas)
            }
        return bitmap
    }

    private fun createFocusHaloBitmap(
        marker: KakaoOverlayMarkerRenderState,
    ): Bitmap {
        val sizePx = dpToPx(marker.sizeDp.toFloat())
        val bitmapSizePx = sizePx.roundToInt().coerceAtLeast(1)
        val center = sizePx / 2f
        val bitmap = Bitmap.createBitmap(bitmapSizePx, bitmapSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fillPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = marker.fillColorArgb
            }

        canvas.drawCircle(center, center, center, fillPaint)
        return bitmap
    }

    private fun createDirectionArrowBitmap(
        marker: KakaoOverlayMarkerRenderState,
    ): Bitmap {
        val sizePx = dpToPx(marker.sizeDp.toFloat())
        val bitmapSizePx = sizePx.roundToInt().coerceAtLeast(1)
        val center = sizePx / 2f
        val bitmap = Bitmap.createBitmap(bitmapSizePx, bitmapSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = marker.fillColorArgb
            }
        val path =
            AndroidPath().apply {
                moveTo(sizePx * 0.82f, center)
                lineTo(sizePx * 0.24f, sizePx * 0.22f)
                lineTo(sizePx * 0.24f, sizePx * 0.78f)
                close()
            }

        canvas.save()
        canvas.rotate(marker.rotationDegrees, center, center)
        canvas.drawPath(path, paint)
        canvas.restore()
        return bitmap
    }

    private fun createSegmentJunctionBitmap(
        marker: KakaoOverlayMarkerRenderState,
    ): Bitmap {
        val sizePx = dpToPx(marker.sizeDp.toFloat())
        val bitmapSizePx = sizePx.roundToInt().coerceAtLeast(1)
        val outerRadius = sizePx / 2f
        val center = outerRadius
        val strokeWidth = dpToPx(3f).coerceAtLeast(1f)
        val bitmap = Bitmap.createBitmap(bitmapSizePx, bitmapSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val outerPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = marker.fillColorArgb
            }
        val strokePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                color = marker.strokeColorArgb
            }

        canvas.drawCircle(center, center, outerRadius - strokeWidth / 2f, outerPaint)
        canvas.drawCircle(center, center, outerRadius - strokeWidth / 2f, strokePaint)
        return bitmap
    }

    private fun createTransitStopBitmap(
        marker: KakaoOverlayMarkerRenderState,
    ): Bitmap {
        val sizePx = dpToPx(marker.sizeDp.toFloat())
        val bitmapSizePx = sizePx.roundToInt().coerceAtLeast(1)
        val cornerRadius = dpToPx(7f)
        val strokeWidth = dpToPx(2f).coerceAtLeast(1f)
        val bitmap = Bitmap.createBitmap(bitmapSizePx, bitmapSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = RectF(strokeWidth / 2f, strokeWidth / 2f, sizePx - strokeWidth / 2f, sizePx - strokeWidth / 2f)
        val fillPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = marker.fillColorArgb
            }
        val strokePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                color = marker.strokeColorArgb
            }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)
        drawCenteredMarkerText(canvas, marker.label.orEmpty(), sizePx, if (marker.label == "BUS") 8.5f else 11f)
        return bitmap
    }

    private fun createTransitTransferBitmap(
        marker: KakaoOverlayMarkerRenderState,
    ): Bitmap {
        val heightPx = dpToPx(30f)
        val widthPx = dpToPx(marker.sizeDp.toFloat())
        val bitmap = Bitmap.createBitmap(widthPx.roundToInt().coerceAtLeast(1), heightPx.roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val strokeWidth = dpToPx(1.5f).coerceAtLeast(1f)
        val cardRect = RectF(strokeWidth / 2f, strokeWidth / 2f, widthPx - strokeWidth / 2f, heightPx - strokeWidth / 2f)
        val cardPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = 0xFFFFFFFF.toInt()
            }
        val strokePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                color = 0xFFE5E7EB.toInt()
            }
        canvas.drawRoundRect(cardRect, heightPx / 2f, heightPx / 2f, cardPaint)
        canvas.drawRoundRect(cardRect, heightPx / 2f, heightPx / 2f, strokePaint)
        val iconSize = dpToPx(22f)
        drawTransitTransferIcon(canvas, marker.label.orEmpty(), marker.fillColorArgb, dpToPx(5f), (heightPx - iconSize) / 2f, iconSize)
        drawTransferArrow(canvas, widthPx / 2f, heightPx / 2f)
        drawTransitTransferIcon(
            canvas = canvas,
            label = marker.secondaryLabel.orEmpty(),
            color = marker.secondaryFillColorArgb ?: marker.fillColorArgb,
            left = widthPx - dpToPx(5f) - iconSize,
            top = (heightPx - iconSize) / 2f,
            size = iconSize,
        )
        return bitmap
    }

    private fun drawTransitTransferIcon(
        canvas: Canvas,
        label: String,
        color: Int,
        left: Float,
        top: Float,
        size: Float,
    ) {
        val rect = RectF(left, top, left + size, top + size)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                this.color = color
            }
        canvas.drawRoundRect(rect, dpToPx(5f), dpToPx(5f), paint)
        drawCenteredMarkerText(canvas, label, size, if (label == "BUS") 8f else 10f, offsetX = left, offsetY = top)
    }

    private fun drawTransferArrow(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
    ) {
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = 0xFF111827.toInt()
            }
        val path =
            AndroidPath().apply {
                moveTo(centerX + dpToPx(4f), centerY)
                lineTo(centerX - dpToPx(3f), centerY - dpToPx(5f))
                lineTo(centerX - dpToPx(3f), centerY + dpToPx(5f))
                close()
            }
        canvas.drawPath(path, paint)
    }

    private fun drawCenteredMarkerText(
        canvas: Canvas,
        label: String,
        sizePx: Float,
        textSizeDp: Float,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
    ) {
        if (label.isBlank()) return
        val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                textAlign = Paint.Align.CENTER
                textSize = dpToPx(textSizeDp)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        val x = offsetX + sizePx / 2f
        val y = offsetY + sizePx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, x, y, textPaint)
    }

    private fun dpToPx(dp: Float): Float = dp * context.resources.displayMetrics.density
}

private class KakaoFacilityMarkerStyleCache(
    private val context: Context,
) {
    private val densityBucket = resolveDensityBucket(context.resources.displayMetrics.densityDpi)
    private val bitmapCache = mutableMapOf<KakaoFacilityMarkerBitmapCacheKey, Bitmap>()
    private val stylesCache = mutableMapOf<KakaoFacilityMarkerBitmapCacheKey, LabelStyles>()

    fun stylesFor(
        labelManager: LabelManager,
        marker: KakaoMarkerRenderState,
    ): LabelStyles {
        val key =
            KakaoFacilityMarkerBitmapCacheKey(
                category = marker.category,
                glyphResId = marker.glyphResId,
                isSelected = marker.isSelected,
                densityBucket = densityBucket,
            )
        return stylesCache.getOrPut(key) {
            val styles =
                LabelStyles.from(
                    key.styleId,
                    LabelStyle
                        .from(bitmapFor(marker, key))
                        .setApplyDpScale(false)
                        .setAnchorPoint(marker.anchorPointX, marker.anchorPointY),
                )
            labelManager.addLabelStyles(styles) ?: styles
        }
    }

    fun clear() {
        stylesCache.clear()
        bitmapCache.clear()
    }

    private fun bitmapFor(
        marker: KakaoMarkerRenderState,
        key: KakaoFacilityMarkerBitmapCacheKey,
    ): Bitmap =
        bitmapCache.getOrPut(key) {
            createFacilityMarkerBitmap(
                category = marker.category,
                glyphResId = marker.glyphResId,
                isSelected = marker.isSelected,
                sizeDp = marker.sizeDp,
            )
        }

    private fun createFacilityMarkerBitmap(
        category: FacilityCategory,
        glyphResId: Int,
        isSelected: Boolean,
        sizeDp: Int,
    ): Bitmap {
        val sizePx = dpToPx(sizeDp.toFloat())
        val borderWidthPx = dpToPx(if (isSelected) 2f else 1f).coerceAtLeast(1f)
        val glyphSizePx = dpToPx(resolveFacilityMarkerGlyphSizeDp(category, glyphResId).toFloat())
        val bitmapSizePx = sizePx.roundToInt()
        val glyphSizeIntPx = glyphSizePx.roundToInt()
        val outerRect = RectF(0f, 0f, sizePx, sizePx)
        val innerRect = RectF(borderWidthPx, borderWidthPx, sizePx - borderWidthPx, sizePx - borderWidthPx)
        val palette = facilityMarkerPalette(category)
        val bitmap = Bitmap.createBitmap(bitmapSizePx, bitmapSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val outerPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = if (isSelected) FACILITY_MARKER_SELECTED_RING_COLOR else palette.borderColor
            }
        val innerPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = palette.containerColor
            }

        if (category == FacilityCategory.BRAILLE_BLOCK) {
            val radiusPx = dpToPx(FACILITY_MARKER_BRAILLE_CORNER_RADIUS_DP)
            canvas.save()
            canvas.rotate(45f, sizePx / 2f, sizePx / 2f)
            canvas.drawRoundRect(outerRect, radiusPx, radiusPx, outerPaint)
            canvas.drawRoundRect(innerRect, radiusPx, radiusPx, innerPaint)
            canvas.restore()
        } else {
            val outerRadius = sizePx / 2f
            canvas.drawCircle(outerRadius, outerRadius, outerRadius, outerPaint)
            canvas.drawCircle(outerRadius, outerRadius, outerRadius - borderWidthPx, innerPaint)
        }

        val glyphDrawable =
            AppCompatResources
                .getDrawable(context, glyphResId)
                ?.mutate()
                ?: return bitmap
        DrawableCompat.setTint(glyphDrawable, palette.contentColor)
        val glyphLeft = ((sizePx - glyphSizePx) / 2f).toInt()
        val glyphTop = ((sizePx - glyphSizePx) / 2f).toInt()
        glyphDrawable.bounds =
            Rect(
                glyphLeft,
                glyphTop,
                glyphLeft + glyphSizeIntPx,
                glyphTop + glyphSizeIntPx,
            )
        glyphDrawable.draw(canvas)
        return bitmap
    }

    private fun dpToPx(dp: Float): Float = dp * context.resources.displayMetrics.density
}

private fun facilityMarkerPalette(category: FacilityCategory): KakaoFacilityMarkerPalette =
    when (category) {
        FacilityCategory.TOILET ->
            KakaoFacilityMarkerPalette(
                containerColor = 0xFF00897B.toInt(),
                borderColor = 0xFFBFEDE7.toInt(),
                contentColor = 0xFFFFFFFF.toInt(),
            )

        FacilityCategory.ELEVATOR ->
            KakaoFacilityMarkerPalette(
                containerColor = 0xFF5E7A2F.toInt(),
                borderColor = 0xFFDDE8C8.toInt(),
                contentColor = 0xFFFFFFFF.toInt(),
            )

        FacilityCategory.CHARGING_STATION ->
            KakaoFacilityMarkerPalette(
                containerColor = 0xFF9C5F00.toInt(),
                borderColor = 0xFFF1D6AA.toInt(),
                contentColor = 0xFFFFFFFF.toInt(),
            )

        FacilityCategory.FOOD_CAFE,
        FacilityCategory.RESTAURANT,
        ->
            KakaoFacilityMarkerPalette(
                containerColor = 0xFFD96A39.toInt(),
                borderColor = 0xFFF7D3C3.toInt(),
                contentColor = 0xFFFFFFFF.toInt(),
            )

        FacilityCategory.TOURIST_SPOT,
        FacilityCategory.TOURIST_ATTRACTION,
        ->
            KakaoFacilityMarkerPalette(
                containerColor = 0xFF1976D2.toInt(),
                borderColor = 0xFFC7E0FF.toInt(),
                contentColor = 0xFFFFFFFF.toInt(),
            )

        FacilityCategory.ACCOMMODATION ->
            KakaoFacilityMarkerPalette(
                containerColor = 0xFF8D6E63.toInt(),
                borderColor = 0xFFE5D4CD.toInt(),
                contentColor = 0xFFFFFFFF.toInt(),
            )

        FacilityCategory.HEALTHCARE ->
            KakaoFacilityMarkerPalette(
                containerColor = 0xFFC62828.toInt(),
                borderColor = 0xFFF5C4C4.toInt(),
                contentColor = 0xFFFFFFFF.toInt(),
            )

        FacilityCategory.WELFARE ->
            KakaoFacilityMarkerPalette(
                containerColor = 0xFF2E7D6B.toInt(),
                borderColor = 0xFFC7E7DE.toInt(),
                contentColor = 0xFFFFFFFF.toInt(),
            )

        FacilityCategory.PUBLIC_OFFICE ->
            KakaoFacilityMarkerPalette(
                containerColor = 0xFF546E7A.toInt(),
                borderColor = 0xFFD1DADF.toInt(),
                contentColor = 0xFFFFFFFF.toInt(),
            )

        FacilityCategory.BRAILLE_BLOCK ->
            KakaoFacilityMarkerPalette(
                containerColor = 0xFF7A5A1D.toInt(),
                borderColor = 0xFFF0DEB7.toInt(),
                contentColor = 0xFFFFFFFF.toInt(),
            )

        FacilityCategory.OTHER ->
            KakaoFacilityMarkerPalette(
                containerColor = 0xFF2563EB.toInt(),
                borderColor = 0xFFDBEAFE.toInt(),
                contentColor = 0xFFFFFFFF.toInt(),
            )
    }

private fun resolveFacilityMarkerGlyphSizeDp(
    category: FacilityCategory,
    glyphResId: Int,
): Int =
    when (glyphResId) {
        R.drawable.ic_accessibility_tag_accessible_toilet -> 18
        R.drawable.ic_accessibility_tag_elevator,
        R.drawable.ic_accessibility_tag_charging_station,
        R.drawable.ic_map_selected_pin_blue,
        -> 16

        else ->
            when (category) {
                FacilityCategory.ELEVATOR -> 16
                FacilityCategory.BRAILLE_BLOCK -> 15
                else -> 14
            }
    }

private fun resolveDensityBucket(densityDpi: Int): Int =
    when {
        densityDpi >= DisplayMetrics.DENSITY_XXXHIGH -> DisplayMetrics.DENSITY_XXXHIGH
        densityDpi >= DisplayMetrics.DENSITY_XXHIGH -> DisplayMetrics.DENSITY_XXHIGH
        densityDpi >= DisplayMetrics.DENSITY_XHIGH -> DisplayMetrics.DENSITY_XHIGH
        densityDpi >= DisplayMetrics.DENSITY_HIGH -> DisplayMetrics.DENSITY_HIGH
        else -> DisplayMetrics.DENSITY_MEDIUM
    }

private data class KakaoFacilityMarkerBitmapCacheKey(
    val category: FacilityCategory,
    val glyphResId: Int,
    val isSelected: Boolean,
    val densityBucket: Int,
) {
    val styleId: String
        get() =
            "facility-${category.name.lowercase(Locale.US)}-$glyphResId-${if (isSelected) "selected" else "normal"}-$densityBucket"
}

private data class KakaoOverlayMarkerBitmapCacheKey(
    val kind: KakaoOverlayMarkerKind,
    val iconResId: Int?,
    val fillColorArgb: Int,
    val strokeColorArgb: Int,
    val isSelected: Boolean,
    val rotationDegrees: Int,
    val label: String?,
    val secondaryLabel: String?,
    val secondaryFillColorArgb: Int?,
    val densityBucket: Int,
) {
    val styleId: String
        get() =
            "overlay-${kind.name.lowercase(Locale.US)}-${iconResId ?: 0}-$fillColorArgb-$strokeColorArgb-${if (isSelected) 1 else 0}-$rotationDegrees-${label.orEmpty()}-${secondaryLabel.orEmpty()}-${secondaryFillColorArgb ?: 0}-$densityBucket"
}

private data class KakaoFacilityMarkerPalette(
    val containerColor: Int,
    val borderColor: Int,
    val contentColor: Int,
)

private const val FACILITY_MARKER_SELECTED_RING_COLOR = -0x1
private const val FACILITY_MARKER_BRAILLE_CORNER_RADIUS_DP = 10f
