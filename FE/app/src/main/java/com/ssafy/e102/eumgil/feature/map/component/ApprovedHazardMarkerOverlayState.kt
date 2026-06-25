package com.ssafy.e102.eumgil.feature.map.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.data.repository.ApprovedHazardMarker
import com.ssafy.e102.eumgil.data.repository.ApprovedHazardMarkerBounds
import com.ssafy.e102.eumgil.data.repository.ReportRepository
import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

private const val APPROVED_HAZARD_MARKER_CLICK_PREFIX = "hazard-report-"
private const val APPROVED_HAZARD_MARKER_FETCH_DEBOUNCE_MILLIS = 400L
private const val APPROVED_HAZARD_MARKER_MAX_DIAGONAL_METERS = 2_000.0
private const val APPROVED_HAZARD_MARKER_ROUNDING_SCALE = 10_000.0

@Immutable
internal data class MapViewportBounds(
    val swLat: Double,
    val swLng: Double,
    val neLat: Double,
    val neLng: Double,
)

@Composable
internal fun rememberApprovedHazardMarkerOverlayState(
    reportRepository: ReportRepository,
): ApprovedHazardMarkerOverlayState {
    val coroutineScope = rememberCoroutineScope()
    return remember(reportRepository, coroutineScope) {
        ApprovedHazardMarkerOverlayState(
            reportRepository = reportRepository,
            coroutineScope = coroutineScope,
        )
    }
}

@Stable
@OptIn(FlowPreview::class)
internal class ApprovedHazardMarkerOverlayState(
    private val reportRepository: ReportRepository,
    coroutineScope: CoroutineScope,
    private val debounceMillis: Long = APPROVED_HAZARD_MARKER_FETCH_DEBOUNCE_MILLIS,
) {
    private val viewportFlow = MutableSharedFlow<MapViewportBounds?>(extraBufferCapacity = 1)
    private var lastFetchedKey: HazardViewportFetchKey? = null

    var markers: List<ApprovedHazardMarker> by mutableStateOf(emptyList())
        private set

    var selectedMarker: ApprovedHazardMarker? by mutableStateOf(null)
        private set

    init {
        coroutineScope.launch {
            viewportFlow
                .debounce(debounceMillis)
                .collectLatest { bounds ->
                    refreshMarkers(bounds)
                }
        }
    }

    val overlayPoints: List<MapViewportPointOverlay>
        get() =
            markers.map { marker ->
                MapViewportPointOverlay(
                    overlayId = marker.viewportOverlayId,
                    coordinate = marker.coordinate.toMapCoordinate(),
                    kind = MapViewportPointKind.APPROVED_REPORT,
                    tone = MapViewportOverlayTone.ERROR,
                    contentDescription = marker.contentDescription,
                    isSelected = marker.reportId == selectedMarker?.reportId,
                    includeInProjection = false,
                    clickTargetId = marker.clickTargetId,
                    reportTypeApiValue = marker.reportType,
                )
            }

    fun onViewportBoundsChanged(bounds: MapViewportBounds?) {
        viewportFlow.tryEmit(bounds)
    }

    fun onMarkerClick(markerId: String): Boolean {
        val reportId = markerId.toApprovedHazardMarkerReportIdOrNull() ?: return false
        val marker = markers.firstOrNull { it.reportId == reportId } ?: return false
        selectedMarker = marker
        return true
    }

    fun dismissSelection() {
        selectedMarker = null
    }

    private suspend fun refreshMarkers(bounds: MapViewportBounds?) {
        if (bounds == null) return
        if (bounds.diagonalMeters() > APPROVED_HAZARD_MARKER_MAX_DIAGONAL_METERS) {
            markers = emptyList()
            selectedMarker = null
            lastFetchedKey = null
            return
        }

        val nextKey = bounds.toFetchKey()
        if (nextKey == lastFetchedKey) return

        runCatching {
            reportRepository.getApprovedHazardMarkers(bounds.toApprovedHazardMarkerBounds())
        }.onSuccess { loadedMarkers ->
            markers = loadedMarkers
            selectedMarker =
                loadedMarkers.firstOrNull { marker ->
                    marker.reportId == selectedMarker?.reportId
                }
            lastFetchedKey = nextKey
        }
    }
}

private data class HazardViewportFetchKey(
    val swLat: Long,
    val swLng: Long,
    val neLat: Long,
    val neLng: Long,
)

private fun MapViewportBounds.toFetchKey(): HazardViewportFetchKey =
    HazardViewportFetchKey(
        swLat = (swLat * APPROVED_HAZARD_MARKER_ROUNDING_SCALE).roundToLong(),
        swLng = (swLng * APPROVED_HAZARD_MARKER_ROUNDING_SCALE).roundToLong(),
        neLat = (neLat * APPROVED_HAZARD_MARKER_ROUNDING_SCALE).roundToLong(),
        neLng = (neLng * APPROVED_HAZARD_MARKER_ROUNDING_SCALE).roundToLong(),
    )

private fun MapViewportBounds.toApprovedHazardMarkerBounds(): ApprovedHazardMarkerBounds =
    ApprovedHazardMarkerBounds(
        swLat = swLat,
        swLng = swLng,
        neLat = neLat,
        neLng = neLng,
    )

internal fun MapViewportBounds.diagonalMeters(): Double = haversineMeters(swLat, swLng, neLat, neLng)

private fun haversineMeters(
    fromLat: Double,
    fromLng: Double,
    toLat: Double,
    toLng: Double,
): Double {
    val earthRadiusMeters = 6_371_000.0
    val deltaLat = Math.toRadians(toLat - fromLat)
    val deltaLng = Math.toRadians(toLng - fromLng)
    val fromLatRadians = Math.toRadians(fromLat)
    val toLatRadians = Math.toRadians(toLat)
    val haversine =
        sin(deltaLat / 2).let { it * it } +
            sin(deltaLng / 2).let { it * it } * cos(fromLatRadians) * cos(toLatRadians)
    val angularDistance = 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
    return earthRadiusMeters * angularDistance
}

private val ApprovedHazardMarker.viewportOverlayId: String
    get() = "hazard-$reportId"

private val ApprovedHazardMarker.clickTargetId: String
    get() = "$APPROVED_HAZARD_MARKER_CLICK_PREFIX$reportId"

private val ApprovedHazardMarker.contentDescription: String
    get() = String.format(Locale.US, "승인 제보 %d", reportId)

private fun String.toApprovedHazardMarkerReportIdOrNull(): Long? =
    removePrefix(APPROVED_HAZARD_MARKER_CLICK_PREFIX)
        .takeIf { it != this }
        ?.toLongOrNull()

private fun GeoCoordinate.toMapCoordinate(): MapCoordinate =
    MapCoordinate(
        latitude = latitude,
        longitude = longitude,
    )
