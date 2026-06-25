package com.ssafy.e102.eumgil.feature.map.component

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing
import com.ssafy.e102.eumgil.core.model.BrailleBlockType
import com.ssafy.e102.eumgil.core.model.FacilityCategory
import com.ssafy.e102.eumgil.feature.map.MapTapPayload
import com.ssafy.e102.eumgil.feature.map.model.MapCameraSource
import com.ssafy.e102.eumgil.feature.map.model.MapCameraTarget
import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerDisplayState
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerOverlayState
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerUiModel
import com.ssafy.e102.eumgil.feature.map.model.resolvedZoomLevel

@Immutable
internal data class MapViewportUiState(
    val integrationState: MapIntegrationState,
    val cameraTarget: MapCameraTarget,
    val rendererSessionKey: Long = 0L,
    val currentLocation: MapCoordinate?,
    val selectedOriginCoordinate: MapCoordinate? = null,
    val selectedOriginName: String? = null,
    val selectedDestinationCoordinate: MapCoordinate? = null,
    val selectedDestinationName: String? = null,
    val markerOverlayState: MapMarkerOverlayState,
    val overlayState: MapViewportOverlayState = MapViewportOverlayState(),
    val selectedMarkerId: String?,
    val selectedMapPinCoordinate: MapCoordinate?,
    val regionLabel: String,
    val statusLabel: String,
    val title: String,
    val description: String,
    val supportingText: String,
)

@Immutable
sealed interface MapIntegrationState {
    @Immutable
    data object Unbound : MapIntegrationState

    @Immutable
    data class Bound(val providerName: String) : MapIntegrationState
}

@Composable
internal fun MapViewport(
    state: MapViewportUiState,
    onMarkerClick: (String) -> Unit = {},
    onCameraMoveEnd: (MapCoordinate, Int, Boolean, Boolean?) -> Unit = { _, _, _, _ -> },
    onViewportBoundsChanged: (MapViewportBounds?) -> Unit = {},
    onBackgroundClick: () -> Unit = {},
    onMapClick: (MapTapPayload) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.integrationState) {
        when (val integrationState = state.integrationState) {
            MapIntegrationState.Unbound -> {
                Log.w(
                    MAP_VIEWPORT_LOG_TAG,
                    "Map integration unavailable; rendering fallback surface",
                )
            }

            is MapIntegrationState.Bound -> {
                Log.i(
                    MAP_VIEWPORT_LOG_TAG,
                    "Map integration bound provider=${integrationState.providerName}",
                )
            }
        }
    }

    when (val integrationState = state.integrationState) {
        MapIntegrationState.Unbound -> {
            MapFallbackSurface(
                markerOverlayState = state.markerOverlayState,
                overlayState = state.overlayState,
                cameraZoomLevel = state.cameraTarget.resolvedZoomLevel(),
                regionLabel = state.regionLabel,
                statusLabel = state.statusLabel,
                title = state.title,
                description = state.description,
                supportingText = state.supportingText,
                onMarkerClick = onMarkerClick,
                modifier = modifier,
            )
        }

        is MapIntegrationState.Bound -> {
            key(state.rendererSessionKey) {
                MapContainer(
                    integrationState = integrationState,
                    state = state,
                    onMarkerClick = onMarkerClick,
                    onCameraMoveEnd = onCameraMoveEnd,
                    onViewportBoundsChanged = onViewportBoundsChanged,
                    onBackgroundClick = onBackgroundClick,
                    onMapClick = onMapClick,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun MapContainer(
    integrationState: MapIntegrationState.Bound,
    state: MapViewportUiState,
    onMarkerClick: (String) -> Unit,
    onCameraMoveEnd: (MapCoordinate, Int, Boolean, Boolean?) -> Unit,
    onViewportBoundsChanged: (MapViewportBounds?) -> Unit,
    onBackgroundClick: () -> Unit,
    onMapClick: (MapTapPayload) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (integrationState.providerName) {
        KAKAO_MAP_PROVIDER_NAME ->
            KakaoMapViewport(
                state = state,
                onMarkerClick = onMarkerClick,
                onCameraMoveEnd = onCameraMoveEnd,
                onViewportBoundsChanged = onViewportBoundsChanged,
                onBackgroundClick = onBackgroundClick,
                onMapClick = onMapClick,
                modifier = modifier,
            )

        else ->
            MapFallbackSurface(
                markerOverlayState = state.markerOverlayState,
                overlayState = state.overlayState,
                cameraZoomLevel = state.cameraTarget.resolvedZoomLevel(),
                regionLabel = state.regionLabel,
                statusLabel = integrationState.providerName,
                title = state.title,
                description = state.description,
                supportingText = state.supportingText,
                onMarkerClick = onMarkerClick,
                modifier = modifier,
            )
    }
}

@Composable
internal fun MapFallbackSurface(
    markerOverlayState: MapMarkerOverlayState,
    overlayState: MapViewportOverlayState,
    cameraZoomLevel: Int,
    regionLabel: String,
    statusLabel: String,
    title: String,
    description: String,
    supportingText: String,
    onMarkerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val surfaceTint = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val backgroundBrush =
        Brush.verticalGradient(
            colors =
                listOf(
                    MaterialTheme.colorScheme.surfaceVariant,
                    surfaceTint,
                ),
        )

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(brush = backgroundBrush),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val verticalStep = size.width / 5f
            val horizontalStep = size.height / 6f
            val strokeWidth = 1.dp.toPx()

            for (index in 0..5) {
                val x = index * verticalStep
                drawLine(
                    color = outline.copy(alpha = 0.18f),
                    start = Offset(x, 0f),
                    end = Offset(x - (size.height * 0.16f), size.height),
                    strokeWidth = strokeWidth,
                )
            }

            for (index in 0..6) {
                val y = index * horizontalStep
                drawLine(
                    color = outline.copy(alpha = 0.12f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y + (size.width * 0.08f)),
                    strokeWidth = strokeWidth,
                )
            }

            drawCircle(
                color = primary.copy(alpha = 0.10f),
                radius = size.minDimension * 0.20f,
                center = Offset(size.width * 0.72f, size.height * 0.28f),
            )
            drawCircle(
                color = secondary.copy(alpha = 0.12f),
                radius = size.minDimension * 0.13f,
                center = Offset(size.width * 0.22f, size.height * 0.68f),
            )
        }

        MapViewportOverlayBackdrop(
            overlayState = overlayState,
            zoomLevel = cameraZoomLevel,
            modifier = Modifier.fillMaxSize(),
            onPointClick = onMarkerClick,
        )

        markerOverlayStatusMessage(markerOverlayState)?.let { message ->
            MarkerOverlayStatusCard(
                message = message,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(EumSpacing.medium),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .widthIn(max = 340.dp)
                .padding(EumSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
        ) {
            Surface(
                color = primary.copy(alpha = 0.10f),
                shape = RoundedCornerShape(EumRadius.full),
                border = BorderStroke(1.dp, primary.copy(alpha = 0.22f)),
            ) {
                Text(
                    text = regionLabel,
                    modifier =
                        Modifier.padding(
                            horizontal = EumSpacing.small,
                            vertical = EumSpacing.xSmall,
                        ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Surface(
                color = surfaceTint.copy(alpha = 0.98f),
                shape = RoundedCornerShape(EumRadius.large),
                tonalElevation = 2.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, outline.copy(alpha = 0.8f)),
            ) {
                Column(
                    modifier = Modifier.padding(EumSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun MapRendererFallbackOverlay(
    title: String,
    description: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier
                    .widthIn(max = 320.dp)
                    .padding(EumSpacing.large),
            shape = RoundedCornerShape(EumRadius.large),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
        ) {
            Column(
                modifier = Modifier.padding(EumSpacing.large),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (actionLabel != null && onActionClick != null) {
                    Button(onClick = onActionClick) {
                        Text(text = actionLabel)
                    }
                }
            }
        }
    }
}

private const val MAP_VIEWPORT_LOG_TAG = "MapViewport"

@Composable
private fun MapMarkerOverlay(
    cameraTarget: MapCameraTarget,
    markerOverlayState: MapMarkerOverlayState,
    selectedMarkerId: String?,
    onMarkerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val projectionBounds =
        markerProjectionBounds(
            cameraTarget = cameraTarget,
            markers = markerOverlayState.visibleMarkers,
        )
    val visibleMarkers = markerOverlayState.visibleMarkers

    BoxWithConstraints(modifier = modifier) {
        val horizontalPadding = 24.dp
        val verticalPadding = 24.dp
        val markerAreaWidth = (maxWidth - (horizontalPadding * 2)).coerceAtLeast(0.dp)
        val markerAreaHeight = (maxHeight - (verticalPadding * 2)).coerceAtLeast(0.dp)
        val focusPoint = projectionBounds.project(cameraTarget.center)

        MapCameraFocusIndicator(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .offsetWithinMarkerArea(
                        xRatio = focusPoint.xRatio,
                        yRatio = focusPoint.yRatio,
                        areaWidth = markerAreaWidth,
                        areaHeight = markerAreaHeight,
                        horizontalPadding = horizontalPadding,
                        verticalPadding = verticalPadding,
                        elementSize = 18.dp,
                    ),
        )

        visibleMarkers.forEach { marker ->
            val projectedPoint = projectionBounds.project(marker.coordinate)
            val jitter = markerOverlayJitter(marker.markerId)
            val isSelected = marker.markerId == selectedMarkerId
            val markerSize = markerSize(marker = marker, isSelected = isSelected)

            MapMarkerChip(
                marker = marker,
                isSelected = isSelected,
                onClick = { onMarkerClick(marker.markerId) },
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .offsetWithinMarkerArea(
                            xRatio = projectedPoint.xRatio,
                            yRatio = projectedPoint.yRatio,
                            areaWidth = markerAreaWidth,
                            areaHeight = markerAreaHeight,
                            horizontalPadding = horizontalPadding,
                            verticalPadding = verticalPadding,
                            elementSize = markerSize,
                            extraOffsetX = jitter.x,
                            extraOffsetY = jitter.y,
                        ),
            )
        }
    }
}

@Composable
private fun MarkerOverlayStatusCard(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(EumRadius.full),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)),
        shadowElevation = 4.dp,
    ) {
        Text(
            text = message,
            modifier =
                Modifier.padding(
                    horizontal = EumSpacing.small,
                    vertical = EumSpacing.xSmall,
                ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun markerOverlayStatusMessage(markerOverlayState: MapMarkerOverlayState): String? =
    when {
        markerOverlayState.isLoadFailed -> stringResource(id = R.string.map_viewport_marker_status_error)
        markerOverlayState.isEmptyData -> null
        markerOverlayState.isEmptyResult -> stringResource(id = R.string.map_viewport_marker_status_empty_result)
        else -> null
    }

@Composable
private fun MapCameraFocusIndicator(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(18.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(6.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
            ) {}
        }
    }
}

@Composable
private fun MapMarkerChip(
    marker: MapMarkerUiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = markerPalette(marker)
    val isBrailleBlock = marker.categoryType.category == FacilityCategory.BRAILLE_BLOCK
    val markerSize = markerSize(marker = marker, isSelected = isSelected)
    val borderColor =
        if (isSelected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            palette.border
        }

    Surface(
        modifier =
            modifier
                .zIndex(if (isSelected) 2f else 1f)
                .size(markerSize)
                .graphicsLayer {
                    rotationZ = if (isBrailleBlock) 45f else 0f
                }
                .semantics {
                    contentDescription = marker.name
                }
                .clickable(onClick = onClick),
        shape =
            if (isBrailleBlock) {
                RoundedCornerShape(12.dp)
            } else {
                CircleShape
            },
        color = palette.container,
        tonalElevation = if (isSelected) 4.dp else 0.dp,
        shadowElevation = if (isSelected) 10.dp else 6.dp,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = markerLabel(marker),
                modifier =
                    if (isBrailleBlock) {
                        Modifier.graphicsLayer { rotationZ = -45f }
                    } else {
                        Modifier
                    },
                color = palette.content,
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isBrailleBlock) 9.sp else 10.sp,
                    ),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

private data class MarkerPalette(
    val container: Color,
    val content: Color,
    val border: Color,
)

private data class MarkerProjectionBounds(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
) {
    private val latitudeSpan: Double
        get() = (maxLatitude - minLatitude).coerceAtLeast(MIN_LATITUDE_SPAN)

    private val longitudeSpan: Double
        get() = (maxLongitude - minLongitude).coerceAtLeast(MIN_LONGITUDE_SPAN)

    fun project(coordinate: MapCoordinate): MarkerProjectionPoint {
        val longitudeRatio =
            ((coordinate.longitude - minLongitude) / longitudeSpan)
                .toFloat()
                .coerceIn(0.06f, 0.94f)
        val latitudeRatio =
            (1f - ((coordinate.latitude - minLatitude) / latitudeSpan).toFloat())
                .coerceIn(0.08f, 0.92f)

        return MarkerProjectionPoint(
            xRatio = longitudeRatio,
            yRatio = latitudeRatio,
        )
    }
}

private data class MarkerProjectionPoint(
    val xRatio: Float,
    val yRatio: Float,
)

private data class MarkerOffset(
    val x: Dp,
    val y: Dp,
)

private fun markerProjectionBounds(
    cameraTarget: MapCameraTarget,
    markers: List<MapMarkerUiModel>,
): MarkerProjectionBounds {
    if (markers.isEmpty()) {
        val latitudePadding =
            when (cameraTarget.source) {
                MapCameraSource.DEFAULT_BUSAN -> 0.010
                MapCameraSource.CURRENT_LOCATION -> 0.006
                MapCameraSource.SEARCH_RESULT -> 0.004
            }
        val longitudePadding =
            when (cameraTarget.source) {
                MapCameraSource.DEFAULT_BUSAN -> 0.014
                MapCameraSource.CURRENT_LOCATION -> 0.009
                MapCameraSource.SEARCH_RESULT -> 0.007
            }

        return MarkerProjectionBounds(
            minLatitude = cameraTarget.center.latitude - latitudePadding,
            maxLatitude = cameraTarget.center.latitude + latitudePadding,
            minLongitude = cameraTarget.center.longitude - longitudePadding,
            maxLongitude = cameraTarget.center.longitude + longitudePadding,
        )
    }

    val markerMinLatitude = markers.minOf { marker -> marker.coordinate.latitude }
    val markerMaxLatitude = markers.maxOf { marker -> marker.coordinate.latitude }
    val markerMinLongitude = markers.minOf { marker -> marker.coordinate.longitude }
    val markerMaxLongitude = markers.maxOf { marker -> marker.coordinate.longitude }
    val latitudeBounds =
        expandedBounds(
            minValue = markerMinLatitude,
            maxValue = markerMaxLatitude,
            minimumSpan = MIN_LATITUDE_SPAN,
        )
    val longitudeBounds =
        expandedBounds(
            minValue = markerMinLongitude,
            maxValue = markerMaxLongitude,
            minimumSpan = MIN_LONGITUDE_SPAN,
        )

    return MarkerProjectionBounds(
        minLatitude = latitudeBounds.first,
        maxLatitude = latitudeBounds.second,
        minLongitude = longitudeBounds.first,
        maxLongitude = longitudeBounds.second,
    )
}

private fun expandedBounds(
    minValue: Double,
    maxValue: Double,
    minimumSpan: Double,
): Pair<Double, Double> {
    val center = (minValue + maxValue) / 2.0
    val paddedSpan = (maxValue - minValue) * 1.48
    val finalSpan = maxOf(paddedSpan, minimumSpan)
    val halfSpan = finalSpan / 2.0

    return (center - halfSpan) to (center + halfSpan)
}

private fun markerOverlayJitter(markerId: String): MarkerOffset {
    val normalizedHash = markerId.hashCode().toLong().ushr(1).toInt()
    val x =
        when (normalizedHash % 5) {
            0 -> (-8).dp
            1 -> (-4).dp
            2 -> 0.dp
            3 -> 4.dp
            else -> 8.dp
        }
    val y =
        when ((normalizedHash / 5) % 5) {
            0 -> (-6).dp
            1 -> (-3).dp
            2 -> 0.dp
            3 -> 3.dp
            else -> 6.dp
        }

    return MarkerOffset(x = x, y = y)
}

private fun markerPalette(marker: MapMarkerUiModel): MarkerPalette =
    when (marker.categoryType.category) {
        FacilityCategory.TOILET ->
            MarkerPalette(
                container = Color(0xFF00897B),
                content = Color.White,
                border = Color(0xFFBFEDE7),
            )

        FacilityCategory.ELEVATOR ->
            MarkerPalette(
                container = Color(0xFF5E7A2F),
                content = Color.White,
                border = Color(0xFFDDE8C8),
            )

        FacilityCategory.CHARGING_STATION ->
            MarkerPalette(
                container = Color(0xFF9C5F00),
                content = Color.White,
                border = Color(0xFFF1D6AA),
            )

        FacilityCategory.FOOD_CAFE ->
            MarkerPalette(
                container = Color(0xFFD96A39),
                content = Color.White,
                border = Color(0xFFF7D3C3),
            )

        FacilityCategory.TOURIST_SPOT ->
            MarkerPalette(
                container = Color(0xFF1976D2),
                content = Color.White,
                border = Color(0xFFC7E0FF),
            )

        FacilityCategory.ACCOMMODATION ->
            MarkerPalette(
                container = Color(0xFF8D6E63),
                content = Color.White,
                border = Color(0xFFE5D4CD),
            )

        FacilityCategory.HEALTHCARE ->
            MarkerPalette(
                container = Color(0xFFC62828),
                content = Color.White,
                border = Color(0xFFF5C4C4),
            )

        FacilityCategory.WELFARE ->
            MarkerPalette(
                container = Color(0xFF2E7D6B),
                content = Color.White,
                border = Color(0xFFC7E7DE),
            )

        FacilityCategory.PUBLIC_OFFICE ->
            MarkerPalette(
                container = Color(0xFF546E7A),
                content = Color.White,
                border = Color(0xFFD1DADF),
            )

        FacilityCategory.BRAILLE_BLOCK ->
            MarkerPalette(
                container = Color(0xFF7A5A1D),
                content = Color.White,
                border = Color(0xFFF0DEB7),
            )

        FacilityCategory.RESTAURANT ->
            MarkerPalette(
                container = Color(0xFFD96A39),
                content = Color.White,
                border = Color(0xFFF7D3C3),
            )

        FacilityCategory.TOURIST_ATTRACTION ->
            MarkerPalette(
                container = Color(0xFF1976D2),
                content = Color.White,
                border = Color(0xFFC7E0FF),
            )

        FacilityCategory.OTHER ->
            MarkerPalette(
                container = Color(0xFF5B6670),
                content = Color.White,
                border = Color(0xFFD3D7DC),
            )
    }

private fun markerLabel(marker: MapMarkerUiModel): String =
    when (marker.categoryType.category) {
        FacilityCategory.TOILET -> "WC"
        FacilityCategory.ELEVATOR -> "EV"
        FacilityCategory.CHARGING_STATION -> "CH"
        FacilityCategory.FOOD_CAFE -> "FC"
        FacilityCategory.TOURIST_SPOT -> "TS"
        FacilityCategory.ACCOMMODATION -> "ST"
        FacilityCategory.HEALTHCARE -> "HP"
        FacilityCategory.WELFARE -> "WF"
        FacilityCategory.PUBLIC_OFFICE -> "PO"
        FacilityCategory.OTHER -> "OT"
        FacilityCategory.RESTAURANT -> "R"
        FacilityCategory.TOURIST_ATTRACTION -> "A"
        FacilityCategory.BRAILLE_BLOCK ->
            when (marker.categoryType.brailleBlockType) {
                BrailleBlockType.GUIDING_LINE -> "BG"
                BrailleBlockType.WARNING_SURFACE -> "BW"
                BrailleBlockType.CROSSWALK_APPROACH -> "BC"
                null -> "BB"
            }
    }

private fun markerSize(
    marker: MapMarkerUiModel,
    isSelected: Boolean,
): Dp =
    when {
        marker.categoryType.category == FacilityCategory.BRAILLE_BLOCK -> 40.dp
        else -> 44.dp
    }

private fun Modifier.offsetWithinMarkerArea(
    xRatio: Float,
    yRatio: Float,
    areaWidth: Dp,
    areaHeight: Dp,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    elementSize: Dp,
    extraOffsetX: Dp = 0.dp,
    extraOffsetY: Dp = 0.dp,
): Modifier =
    this.offset(
        x = horizontalPadding + (areaWidth * xRatio) - (elementSize / 2) + extraOffsetX,
        y = verticalPadding + (areaHeight * yRatio) - (elementSize / 2) + extraOffsetY,
    )

private const val MIN_LATITUDE_SPAN = 0.0035
private const val MIN_LONGITUDE_SPAN = 0.0045
