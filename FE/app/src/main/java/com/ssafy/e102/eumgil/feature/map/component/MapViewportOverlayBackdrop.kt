package com.ssafy.e102.eumgil.feature.map.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerCategoryType
import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import com.ssafy.e102.eumgil.core.model.BrailleBlockType
import com.ssafy.e102.eumgil.core.model.FacilityCategory
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
internal fun MapViewportOverlayBackdrop(
    overlayState: MapViewportOverlayState,
    zoomLevel: Int = ROUTE_DETAIL_OVERLAY_MIN_ZOOM_LEVEL,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 24.dp,
    verticalPadding: Dp = 24.dp,
    contentDescription: String? = null,
    onPointClick: (String) -> Unit = {},
) {
    val showDetailedRouteOverlay = shouldShowDetailedRouteOverlay(zoomLevel)
    val backgroundBrush =
        Brush.verticalGradient(
            colors =
                listOf(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
        )
    val semanticsModifier =
        if (contentDescription.isNullOrBlank()) {
            Modifier
        } else {
            Modifier.semantics { this.contentDescription = contentDescription }
        }

    BoxWithConstraints(
        modifier =
            modifier
                .background(backgroundBrush)
                .then(semanticsModifier),
    ) {
        val projectionBounds = viewportProjectionBounds(overlayState)
        val markerAreaWidth = (maxWidth - (horizontalPadding * 2)).coerceAtLeast(0.dp)
        val markerAreaHeight = (maxHeight - (verticalPadding * 2)).coerceAtLeast(0.dp)
        val palette =
            ViewportOverlayPalette(
                primary = Color(0xFF006BE0),
                secondary = MaterialTheme.colorScheme.secondary,
                tertiary = Color(0xFFF9AB4D),
                neutral = Color(0xFFD9D9D9),
                navy = Color(0xFF005391),
                navigationWalk = Color(0xFF0061FE),
                transitWalk = Color(0xFF99B5D1),
                error = MaterialTheme.colorScheme.error,
                outline = MaterialTheme.colorScheme.outline,
            )

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawViewportGrid(outline = palette.outline)
            overlayState.polylines.forEach { polyline ->
                drawViewportPolyline(
                    overlay = polyline,
                    showDetailedRouteOverlay = showDetailedRouteOverlay,
                    bounds = projectionBounds,
                    canvasSize = size,
                    palette = palette,
                )
            }
            overlayState.points
                .filter { point -> showDetailedRouteOverlay || !point.isDetailedRouteOverlayMarker() }
                .forEach { point ->
                drawViewportPointHalo(
                    overlay = point,
                    bounds = projectionBounds,
                    canvasSize = size,
                    palette = palette,
                )
            }
        }

        overlayState.points
            .filter { point -> showDetailedRouteOverlay || !point.isDetailedRouteOverlayMarker() }
            .forEach { point ->
            val markerSpec = point.toViewportPointMarkerSpec() ?: return@forEach
            val projectedPoint = projectionBounds.project(point.coordinate)

            ViewportPointMarker(
                point = point,
                spec = markerSpec,
                onPointClick = onPointClick,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .offsetWithinViewport(
                            point = projectedPoint,
                            areaWidth = markerAreaWidth,
                            areaHeight = markerAreaHeight,
                            horizontalPadding = horizontalPadding,
                            verticalPadding = verticalPadding,
                            elementSize = markerSpec.size,
                        ),
            )
        }
    }
}

private fun DrawScope.drawViewportGrid(outline: Color) {
    val verticalStep = size.width / 5f
    val horizontalStep = size.height / 6f
    val strokeWidth = 1.dp.toPx()

    for (index in 0..5) {
        val x = index * verticalStep
        drawLine(
            color = outline.copy(alpha = 0.16f),
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
}

private fun DrawScope.drawViewportPolyline(
    overlay: MapViewportPolylineOverlay,
    showDetailedRouteOverlay: Boolean,
    bounds: ViewportProjectionBounds,
    canvasSize: Size,
    palette: ViewportOverlayPalette,
) {
    if (!overlay.isRenderable) return

    val path = overlay.points.toViewportPath(bounds = bounds, canvasSize = canvasSize)
    val toneColor = overlay.tone.toColor(palette)
    val casingColor = overlay.tone.toCasingColor(palette)

    when (overlay.style) {
        MapViewportPolylineStyle.ROUTE_PREVIEW -> {
            drawPath(
                path = path,
                color = casingColor.copy(alpha = 0.9f),
                style =
                    Stroke(
                        width = 18.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
            )
            drawPath(
                path = path,
                color = toneColor,
                style =
                    Stroke(
                        width = 18.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
            )
        }

        MapViewportPolylineStyle.ROUTE_CONNECTOR -> {
            drawPath(
                path = path,
                color = Color(0xFF64748B),
                style =
                    Stroke(
                        width = 18.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
            )
        }

        MapViewportPolylineStyle.ROUTE_BASELINE -> {
            drawPath(
                path = path,
                color = casingColor.copy(alpha = 0.82f),
                style =
                    Stroke(
                        width = 18.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
            )
            drawPath(
                path = path,
                color = toneColor.copy(alpha = 0.9f),
                style =
                    Stroke(
                        width = 18.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
            )
        }

        MapViewportPolylineStyle.ACTIVE_SEGMENT -> {
            drawPath(
                path = path,
                color = casingColor.copy(alpha = 0.88f),
                style =
                    Stroke(
                        width = 18.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
            )
            drawPath(
                path = path,
                color = toneColor,
                style =
                    Stroke(
                        width = 18.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
            )
        }

        MapViewportPolylineStyle.FOCUSED_SEGMENT -> {
            drawPath(
                path = path,
                color = casingColor.copy(alpha = 0.92f),
                style =
                    Stroke(
                        width = 18.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
            )
            drawPath(
                path = path,
                color = toneColor,
                style =
                    Stroke(
                        width = 18.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
            )
        }
    }
    if (showDetailedRouteOverlay && overlay.showDirectionArrows) {
        drawViewportPolylineDirectionArrows(
            overlay = overlay,
            bounds = bounds,
            canvasSize = canvasSize,
        )
    }
}

private fun DrawScope.drawViewportPolylineDirectionArrows(
    overlay: MapViewportPolylineOverlay,
    bounds: ViewportProjectionBounds,
    canvasSize: Size,
) {
    val projectedPoints =
        overlay.points.map { coordinate ->
            bounds.project(coordinate).toOffset(canvasSize)
        }
    val intervalPx = ROUTE_DIRECTION_ARROW_TARGET_SPACING_DP.dp.toPx()
    val edgePaddingPx = ROUTE_DIRECTION_ARROW_EDGE_PADDING_DP.dp.toPx()
    val arrowLengthPx = ROUTE_DIRECTION_ARROW_LENGTH_DP.dp.toPx()
    val arrowHalfWidthPx = ROUTE_DIRECTION_ARROW_HALF_WIDTH_DP.dp.toPx()

    sampleRouteDirectionArrowPlacements(
        points = projectedPoints,
        intervalDistance = intervalPx.toDouble(),
        edgePaddingDistance = edgePaddingPx.toDouble(),
        minimumPlacementCount = 1,
        measureDistance = { start, end ->
            val deltaX = end.x - start.x
            val deltaY = end.y - start.y
            sqrt((deltaX * deltaX) + (deltaY * deltaY)).toDouble()
        },
        interpolatePoint = { start, end, fraction ->
            Offset(
                x = start.x + ((end.x - start.x) * fraction.toFloat()),
                y = start.y + ((end.y - start.y) * fraction.toFloat()),
            )
        },
    ).forEach { placement ->
        // The fallback surface keeps a fixed north-up projection, so the projected segment angle
        // already matches the final on-screen arrow direction without an extra bearing correction.
        val deltaX = placement.segmentEnd.x - placement.segmentStart.x
        val deltaY = placement.segmentEnd.y - placement.segmentStart.y
        val angle = atan2(deltaY, deltaX)
        val unitX = cos(angle)
        val unitY = sin(angle)
        val normalX = -unitY
        val normalY = unitX
        val tip = placement.point
        val base =
            Offset(
                x = tip.x - (unitX * arrowLengthPx),
                y = tip.y - (unitY * arrowLengthPx),
            )
        val arrowPath =
            Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(
                    base.x + (normalX * arrowHalfWidthPx),
                    base.y + (normalY * arrowHalfWidthPx),
                )
                lineTo(
                    base.x - (normalX * arrowHalfWidthPx),
                    base.y - (normalY * arrowHalfWidthPx),
                )
                close()
            }
        drawPath(
            path = arrowPath,
            color = Color.White.copy(alpha = 0.92f),
        )
    }
}

private fun DrawScope.drawViewportPointHalo(
    overlay: MapViewportPointOverlay,
    bounds: ViewportProjectionBounds,
    canvasSize: Size,
    palette: ViewportOverlayPalette,
) {
    val projectedPoint = bounds.project(overlay.coordinate).toCanvasOffset(canvasSize)

    when (overlay.kind) {
        MapViewportPointKind.HAZARD ->
            drawCircle(
                color = palette.error.copy(alpha = 0.16f),
                radius = 18.dp.toPx(),
                center = projectedPoint,
            )

        MapViewportPointKind.ORIGIN ->
            drawCircle(
                color = palette.secondary.copy(alpha = 0.18f),
                radius = 18.dp.toPx(),
                center = projectedPoint,
            )

        MapViewportPointKind.DESTINATION ->
            drawCircle(
                color = palette.error.copy(alpha = 0.16f),
                radius = 20.dp.toPx(),
                center = projectedPoint,
            )

        MapViewportPointKind.CURRENT_LOCATION ->
            drawCircle(
                color = palette.primary.copy(alpha = 0.16f),
                radius = 16.dp.toPx(),
                center = projectedPoint,
            )

        MapViewportPointKind.CURRENT_LOCATION_HEADING -> Unit

        MapViewportPointKind.SEGMENT_JUNCTION,
        MapViewportPointKind.TRANSIT_BUS_STOP,
        MapViewportPointKind.TRANSIT_SUBWAY_STATION,
        MapViewportPointKind.TRANSIT_TRANSFER,
        MapViewportPointKind.APPROVED_REPORT,
            -> Unit

        MapViewportPointKind.FOCUS_HALO ->
            drawCircle(
                color = FocusedGuidanceMarkerHaloColor,
                radius = FocusedGuidanceMarkerHaloRadius.toPx(),
                center = projectedPoint,
            )

        MapViewportPointKind.FACILITY,
        MapViewportPointKind.CAMERA_FOCUS,
            -> Unit
    }
}

private data class ViewportOverlayPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val neutral: Color,
    val navy: Color,
    val navigationWalk: Color,
    val transitWalk: Color,
    val error: Color,
    val outline: Color,
)

private fun List<MapCoordinate>.toViewportPath(
    bounds: ViewportProjectionBounds,
    canvasSize: Size,
): Path =
    Path().also { path ->
        forEachIndexed { index, coordinate ->
            val offset = bounds.project(coordinate).toCanvasOffset(canvasSize)
            if (index == 0) {
                path.moveTo(offset.x, offset.y)
            } else {
                path.lineTo(offset.x, offset.y)
            }
        }
    }

private fun ViewportProjectionPoint.toCanvasOffset(size: Size): Offset =
    Offset(
        x = size.width * xRatio,
        y = size.height * yRatio,
    )

private fun Modifier.offsetWithinViewport(
    point: ViewportProjectionPoint,
    areaWidth: Dp,
    areaHeight: Dp,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    elementSize: Dp,
): Modifier =
    offset(
        x = horizontalPadding + (areaWidth * point.xRatio) - (elementSize / 2),
        y = verticalPadding + (areaHeight * point.yRatio) - (elementSize / 2),
    )

private fun viewportProjectionBounds(overlayState: MapViewportOverlayState): ViewportProjectionBounds {
    val coordinates =
        buildList<MapCoordinate> {
            overlayState.points
                .filter(MapViewportPointOverlay::includeInProjection)
                .mapTo(this) { point -> point.coordinate }
            overlayState.polylines
                .filter(MapViewportPolylineOverlay::includeInProjection)
                .flatMapTo(this) { polyline -> polyline.points }
        }

    if (coordinates.isEmpty()) {
        return ViewportProjectionBounds(
            minLatitude = overlayState.fallbackCamera.center.latitude - (overlayState.fallbackCamera.latitudeSpan / 2.0),
            maxLatitude = overlayState.fallbackCamera.center.latitude + (overlayState.fallbackCamera.latitudeSpan / 2.0),
            minLongitude = overlayState.fallbackCamera.center.longitude - (overlayState.fallbackCamera.longitudeSpan / 2.0),
            maxLongitude = overlayState.fallbackCamera.center.longitude + (overlayState.fallbackCamera.longitudeSpan / 2.0),
        )
    }

    val latitudeBounds =
        expandedViewportBounds(
            minValue = coordinates.minOf { coordinate -> coordinate.latitude },
            maxValue = coordinates.maxOf { coordinate -> coordinate.latitude },
            minimumSpan = overlayState.fallbackCamera.latitudeSpan.coerceAtLeast(MIN_VIEWPORT_LATITUDE_SPAN),
        )
    val longitudeBounds =
        expandedViewportBounds(
            minValue = coordinates.minOf { coordinate -> coordinate.longitude },
            maxValue = coordinates.maxOf { coordinate -> coordinate.longitude },
            minimumSpan = overlayState.fallbackCamera.longitudeSpan.coerceAtLeast(MIN_VIEWPORT_LONGITUDE_SPAN),
        )

    return ViewportProjectionBounds(
        minLatitude = latitudeBounds.first,
        maxLatitude = latitudeBounds.second,
        minLongitude = longitudeBounds.first,
        maxLongitude = longitudeBounds.second,
    )
}

private fun expandedViewportBounds(
    minValue: Double,
    maxValue: Double,
    minimumSpan: Double,
): Pair<Double, Double> {
    val center = (minValue + maxValue) / 2.0
    val paddedSpan = (maxValue - minValue) * 1.46
    val finalSpan = maxOf(paddedSpan, minimumSpan)
    val halfSpan = finalSpan / 2.0

    return (center - halfSpan) to (center + halfSpan)
}

private data class ViewportProjectionBounds(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
) {
    private val latitudeSpan: Double
        get() = (maxLatitude - minLatitude).coerceAtLeast(MIN_VIEWPORT_LATITUDE_SPAN)

    private val longitudeSpan: Double
        get() = (maxLongitude - minLongitude).coerceAtLeast(MIN_VIEWPORT_LONGITUDE_SPAN)

    fun project(coordinate: MapCoordinate): ViewportProjectionPoint {
        val longitudeRatio =
            ((coordinate.longitude - minLongitude) / longitudeSpan)
                .toFloat()
                .coerceIn(0.08f, 0.92f)
        val latitudeRatio =
            (1f - ((coordinate.latitude - minLatitude) / latitudeSpan).toFloat())
                .coerceIn(0.1f, 0.9f)

        return ViewportProjectionPoint(
            xRatio = longitudeRatio,
            yRatio = latitudeRatio,
        )
    }
}

private data class ViewportProjectionPoint(
    val xRatio: Float,
    val yRatio: Float,
)

private fun ViewportProjectionPoint.toOffset(canvasSize: Size): Offset =
    Offset(
        x = xRatio * canvasSize.width,
        y = yRatio * canvasSize.height,
    )

@Composable
private fun ViewportPointMarker(
    point: MapViewportPointOverlay,
    spec: ViewportPointMarkerSpec,
    onPointClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clickableModifier =
        if (point.clickTargetId == null) {
            Modifier
        } else {
            Modifier.clickable { onPointClick(point.clickTargetId) }
        }
    val semanticsLabel = point.contentDescription ?: spec.label

    if (spec.shape == ViewportPointMarkerShape.TRIANGLE_WARNING) {
        Box(
            modifier =
                modifier
                    .zIndex(if (point.isSelected) 2f else 1f)
                    .size(spec.size)
                    .semantics {
                        contentDescription = semanticsLabel.orEmpty()
                    }
                    .then(clickableModifier),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = spec.borderWidth.toPx()
                val halfStroke = strokeWidth / 2f
                val padding = 2.dp.toPx()
                val path =
                    Path().apply {
                        moveTo(size.width / 2f, padding + halfStroke)
                        lineTo(size.width - padding - halfStroke, size.height - padding - halfStroke)
                        lineTo(padding + halfStroke, size.height - padding - halfStroke)
                        close()
                    }
                drawPath(path = path, color = spec.containerColor)
                drawPath(
                    path = path,
                    color = spec.borderColor,
                    style =
                        Stroke(
                            width = strokeWidth,
                            join = StrokeJoin.Round,
                        ),
                )
            }
            Text(
                text = spec.label.orEmpty(),
                color = spec.contentColor,
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = spec.fontSize,
                    ),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        return
    }

    Surface(
        modifier =
            modifier
                .zIndex(if (point.isSelected) 2f else 1f)
                .size(spec.size)
                .graphicsLayer {
                    rotationZ = if (spec.isRotated) 45f else 0f
                }
                .semantics {
                    contentDescription = semanticsLabel.orEmpty()
                }
                .then(clickableModifier),
        shape =
            if (spec.isDiamond || spec.shape == ViewportPointMarkerShape.DIAMOND) {
                RoundedCornerShape(12.dp)
            } else {
                CircleShape
            },
        color = spec.containerColor,
        tonalElevation = if (point.isSelected) 4.dp else 0.dp,
        shadowElevation = if (point.isSelected) 10.dp else 6.dp,
        border =
            BorderStroke(if (point.isSelected) 2.dp else spec.borderWidth, spec.borderColor),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (point.kind == MapViewportPointKind.CAMERA_FOCUS) {
                Surface(
                    modifier = Modifier.size(6.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                ) {}
            } else {
                if (spec.iconResId != null) {
                    Image(
                        painter = painterResource(id = spec.iconResId),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(spec.contentColor),
                        modifier =
                            Modifier
                                .size(spec.iconSize)
                                .then(
                                    if (spec.isRotated) {
                                        Modifier.graphicsLayer { rotationZ = -45f }
                                    } else {
                                        Modifier
                                    },
                                ),
                    )
                } else {
                    Text(
                        text = spec.label.orEmpty(),
                        modifier =
                            if (spec.isRotated) {
                                Modifier.graphicsLayer { rotationZ = -45f }
                            } else {
                                Modifier
                            },
                        color = spec.contentColor,
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = spec.fontSize,
                            ),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun MapViewportOverlayTone.toColor(palette: ViewportOverlayPalette): Color =
    when (this) {
        MapViewportOverlayTone.PRIMARY -> palette.primary
        MapViewportOverlayTone.SECONDARY -> palette.secondary
        MapViewportOverlayTone.TERTIARY -> palette.tertiary
        MapViewportOverlayTone.NEUTRAL -> palette.neutral
        MapViewportOverlayTone.NAVY -> palette.navy
        MapViewportOverlayTone.NAVIGATION_WALK -> palette.navigationWalk
        MapViewportOverlayTone.TRANSIT_WALK -> palette.transitWalk
        MapViewportOverlayTone.ERROR -> palette.error
    }

private fun MapViewportOverlayTone.toCasingColor(palette: ViewportOverlayPalette): Color =
    when (this) {
        MapViewportOverlayTone.PRIMARY -> palette.primary.copy(red = 0.06f, green = 0.30f, blue = 0.78f)
        MapViewportOverlayTone.SECONDARY -> palette.secondary.copy(red = 0.04f, green = 0.47f, blue = 0.36f)
        MapViewportOverlayTone.TERTIARY -> palette.tertiary.copy(red = 0.72f, green = 0.36f, blue = 0.09f)
        MapViewportOverlayTone.NEUTRAL -> Color(0xFF6B7280)
        MapViewportOverlayTone.NAVY -> palette.navy
        MapViewportOverlayTone.NAVIGATION_WALK -> palette.navigationWalk
        MapViewportOverlayTone.TRANSIT_WALK -> palette.transitWalk
        MapViewportOverlayTone.ERROR -> palette.error.copy(red = 0.62f, green = 0.16f, blue = 0.16f)
    }

private fun MapViewportTransitMarkerLeg.toFallbackTransitShortLabel(): String =
    when (kind) {
        MapViewportTransitMarkerKind.BUS -> "BUS"
        MapViewportTransitMarkerKind.SUBWAY -> label.toFallbackSubwayLineShortLabel()
    }

private fun String?.toFallbackSubwayLineColor(): Color =
    when {
        this == null -> Color(0xFF304583)
        contains("부산김해", ignoreCase = true) ||
            contains("김해", ignoreCase = true) ||
            contains("BGL", ignoreCase = true) -> Color(0xFF8200FF)
        contains("1") -> Color(0xFFFF7F00)
        contains("2") -> Color(0xFF3ED93B)
        contains("3") -> Color(0xFFE8AB56)
        contains("4") -> Color(0xFF32B1FF)
        else -> Color(0xFF304583)
    }

private fun String?.toFallbackSubwayLineShortLabel(): String =
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

@Composable
private fun MapViewportPointOverlay.toViewportPointMarkerSpec(): ViewportPointMarkerSpec? =
    when (kind) {
        MapViewportPointKind.FACILITY -> categoryType?.toFacilityMarkerSpec(isSelected)
        MapViewportPointKind.HAZARD ->
            ViewportPointMarkerSpec(
                label = "!",
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                borderColor = MaterialTheme.colorScheme.surface,
                size = if (isSelected) 42.dp else 38.dp,
                fontSize = 16.sp,
            )
        MapViewportPointKind.ORIGIN ->
            ViewportPointMarkerSpec(
                label = label ?: "출발",
                containerColor = Color(0xFF4D8FF9),
                contentColor = MaterialTheme.colorScheme.onPrimary,
                borderColor = MaterialTheme.colorScheme.surface,
                size = 38.dp,
                fontSize = 11.sp,
            )

        MapViewportPointKind.DESTINATION ->
            ViewportPointMarkerSpec(
                label = label ?: "도착",
                containerColor = Color(0xFFF94D4D),
                contentColor = MaterialTheme.colorScheme.onPrimary,
                borderColor = MaterialTheme.colorScheme.surface,
                size = 38.dp,
                fontSize = 11.sp,
            )

        MapViewportPointKind.CURRENT_LOCATION ->
            ViewportPointMarkerSpec(
                label = label ?: "C",
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                borderColor = MaterialTheme.colorScheme.surface,
                size = 38.dp,
                fontSize = 11.sp,
            )

        MapViewportPointKind.CURRENT_LOCATION_HEADING ->
            ViewportPointMarkerSpec(
                label = "▲",
                containerColor = Color(0xFFFF5A4F),
                contentColor = Color.White,
                borderColor = Color.White,
                size = 20.dp,
                fontSize = 11.sp,
            )

        MapViewportPointKind.SEGMENT_JUNCTION ->
            ViewportPointMarkerSpec(
                label = null,
                containerColor = Color.White,
                contentColor = Color.Transparent,
                borderColor = GuidanceJunctionMarkerStrokeColor,
                size = 20.dp,
                isDiamond = false,
                borderWidth = GuidanceJunctionMarkerStrokeWidth,
                fontSize = 1.sp,
            )

        MapViewportPointKind.TRANSIT_BUS_STOP ->
            ViewportPointMarkerSpec(
                label = "BUS",
                containerColor = Color(0xFF304583),
                contentColor = Color.White,
                borderColor = Color.White,
                size = 30.dp,
                fontSize = 8.sp,
            )

        MapViewportPointKind.TRANSIT_SUBWAY_STATION -> {
            val routeLabel = transitMarker?.from?.label ?: label
            ViewportPointMarkerSpec(
                label = routeLabel.toFallbackSubwayLineShortLabel(),
                containerColor = routeLabel.toFallbackSubwayLineColor(),
                contentColor = Color.White,
                borderColor = Color.White,
                size = 30.dp,
                fontSize = 11.sp,
            )
        }

        MapViewportPointKind.TRANSIT_TRANSFER -> {
            val from = transitMarker?.from
            val to = transitMarker?.to
            ViewportPointMarkerSpec(
                label = listOfNotNull(from?.toFallbackTransitShortLabel(), to?.toFallbackTransitShortLabel()).joinToString("›"),
                containerColor = Color.White,
                contentColor = Color(0xFF111827),
                borderColor = Color(0xFFE5E7EB),
                size = 46.dp,
                fontSize = 9.sp,
            )
        }

        MapViewportPointKind.APPROVED_REPORT ->
            ViewportPointMarkerSpec(
                label = "!",
                containerColor = Color(0xFFFFD84D),
                contentColor = Color(0xFF111827),
                borderColor = Color(0xFF111827),
                size = 40.dp,
                shape = ViewportPointMarkerShape.TRIANGLE_WARNING,
                fontSize = 18.sp,
                borderWidth = 2.dp,
            )

        MapViewportPointKind.CAMERA_FOCUS ->
            ViewportPointMarkerSpec(
                label = null,
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                contentColor = MaterialTheme.colorScheme.primary,
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                size = 18.dp,
                fontSize = 10.sp,
            )

        MapViewportPointKind.FOCUS_HALO -> null
    }

private enum class ViewportPointMarkerShape {
    CIRCLE,
    DIAMOND,
    TRIANGLE_WARNING,
}

private fun MapViewportPointOverlay.isDetailedRouteOverlayMarker(): Boolean =
    kind == MapViewportPointKind.SEGMENT_JUNCTION

@Composable
private fun MapMarkerCategoryType.toFacilityMarkerSpec(isSelected: Boolean): ViewportPointMarkerSpec {
    val palette = toFacilityPalette()
    val isBrailleBlock = category == FacilityCategory.BRAILLE_BLOCK

    return ViewportPointMarkerSpec(
        label = toFacilityLabel(),
        containerColor = palette.container,
        contentColor = palette.content,
        borderColor =
            if (isSelected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                palette.border
            },
        size =
            when {
                isBrailleBlock -> 40.dp
                else -> 44.dp
            },
        isDiamond = isBrailleBlock,
        isRotated = isBrailleBlock,
        fontSize = if (isBrailleBlock) 9.sp else 10.sp,
    )
}

private data class ViewportPointMarkerSpec(
    val label: String?,
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color,
    val size: Dp,
    val isDiamond: Boolean = false,
    val isRotated: Boolean = false,
    val shape: ViewportPointMarkerShape = ViewportPointMarkerShape.CIRCLE,
    val iconResId: Int? = null,
    val iconSize: Dp = 0.dp,
    val fontSize: androidx.compose.ui.unit.TextUnit,
    val borderWidth: Dp = 1.dp,
)

private data class FacilityMarkerPalette(
    val container: Color,
    val content: Color,
    val border: Color,
)

private val GuidanceJunctionMarkerStrokeWidth = 2.dp
private val GuidanceJunctionMarkerStrokeColor = Color(0xFF9CA3AF)

private fun MapMarkerCategoryType.toFacilityPalette(): FacilityMarkerPalette =
    when (category) {
        FacilityCategory.TOILET ->
            FacilityMarkerPalette(
                container = Color(0xFF00897B),
                content = Color.White,
                border = Color(0xFFBFEDE7),
            )

        FacilityCategory.ELEVATOR ->
            FacilityMarkerPalette(
                container = Color(0xFF5E7A2F),
                content = Color.White,
                border = Color(0xFFDDE8C8),
            )

        FacilityCategory.CHARGING_STATION ->
            FacilityMarkerPalette(
                container = Color(0xFF9C5F00),
                content = Color.White,
                border = Color(0xFFF1D6AA),
            )

        FacilityCategory.FOOD_CAFE ->
            FacilityMarkerPalette(
                container = Color(0xFFD96A39),
                content = Color.White,
                border = Color(0xFFF7D3C3),
            )

        FacilityCategory.TOURIST_SPOT ->
            FacilityMarkerPalette(
                container = Color(0xFF1976D2),
                content = Color.White,
                border = Color(0xFFC7E0FF),
            )

        FacilityCategory.ACCOMMODATION ->
            FacilityMarkerPalette(
                container = Color(0xFF8D6E63),
                content = Color.White,
                border = Color(0xFFE5D4CD),
            )

        FacilityCategory.HEALTHCARE ->
            FacilityMarkerPalette(
                container = Color(0xFFC62828),
                content = Color.White,
                border = Color(0xFFF5C4C4),
            )

        FacilityCategory.WELFARE ->
            FacilityMarkerPalette(
                container = Color(0xFF2E7D6B),
                content = Color.White,
                border = Color(0xFFC7E7DE),
            )

        FacilityCategory.PUBLIC_OFFICE ->
            FacilityMarkerPalette(
                container = Color(0xFF546E7A),
                content = Color.White,
                border = Color(0xFFD1DADF),
            )

        FacilityCategory.BRAILLE_BLOCK ->
            FacilityMarkerPalette(
                container = Color(0xFF7A5A1D),
                content = Color.White,
                border = Color(0xFFF0DEB7),
            )

        FacilityCategory.RESTAURANT ->
            FacilityMarkerPalette(
                container = Color(0xFFD96A39),
                content = Color.White,
                border = Color(0xFFF7D3C3),
            )

        FacilityCategory.TOURIST_ATTRACTION ->
            FacilityMarkerPalette(
                container = Color(0xFF1976D2),
                content = Color.White,
                border = Color(0xFFC7E0FF),
            )

        FacilityCategory.OTHER ->
            FacilityMarkerPalette(
                container = Color(0xFF5B6670),
                content = Color.White,
                border = Color(0xFFD3D7DC),
            )
    }

private fun MapMarkerCategoryType.toFacilityLabel(): String =
    when (category) {
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
            when (brailleBlockType) {
                BrailleBlockType.GUIDING_LINE -> "BG"
                BrailleBlockType.WARNING_SURFACE -> "BW"
                BrailleBlockType.CROSSWALK_APPROACH -> "BC"
                null -> "BB"
            }
    }

private const val RouteDirectionArrowInsetDp = 28
private const val RouteDirectionArrowIntervalDp = 20
private const val RouteDirectionArrowMinSegmentDp = 44
private const val RouteDirectionArrowLengthDp = 10
private const val RouteDirectionArrowHalfWidthDp = 5
private val FocusedGuidanceMarkerHaloColor = Color(0x804D8FF9)
private val FocusedGuidanceMarkerHaloRadius = 13.dp
