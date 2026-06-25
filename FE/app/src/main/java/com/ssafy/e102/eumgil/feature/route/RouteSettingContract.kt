package com.ssafy.e102.eumgil.feature.route

import androidx.annotation.StringRes
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.LowFloorBusReservation
import com.ssafy.e102.eumgil.core.model.PlaceCategory
import com.ssafy.e102.eumgil.core.model.RouteCandidate
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.core.model.RouteRiskLevel
import com.ssafy.e102.eumgil.core.model.RouteSearchSource
import com.ssafy.e102.eumgil.core.model.RouteWaypoint
import com.ssafy.e102.eumgil.data.repository.RouteEditingTarget
import com.ssafy.e102.eumgil.feature.search.SearchSelectionMode

data class RouteSettingUiState(
    val isLoading: Boolean = true,
    val loadErrorMessage: String? = null,
    val loadNoticeMessage: String? = null,
    val loadDebugMessage: String? = null,
    val originState: RouteOriginState = RouteOriginState.CURRENT_LOCATION_LOADING,
    val originStatus: RouteOriginStatusUiState? = null,
    val origin: RouteLocationUiState = RouteLocationUiState(),
    val destination: RouteLocationUiState = RouteLocationUiState(),
    val destinationHandoffState: RouteDestinationHandoffState = RouteDestinationHandoffState.EMPTY,
    val destinationFallbackMessage: String? = null,
    val isUsingFallbackDestination: Boolean = true,
    val selectedTravelMode: RouteTravelMode = RouteTravelMode.WALK,
    val pendingTravelMode: RouteTravelMode? = null,
    val selectedOption: RouteOption = RouteOption.SAFE,
    val optionCards: List<RouteOptionCardUiState> = emptyList(),
    val selectedRoute: RouteSelectedRouteUiState? = null,
    val routePreviewMap: RoutePreviewMapUiState = RoutePreviewMapUiState(),
    val currentLocationCoordinate: GeoCoordinate? = null,
    val sourceLabel: String? = null,
    val cta: RouteSettingCtaUiState = RouteSettingCtaUiState(),
    val ctaAcknowledged: Boolean = false,
    val isRouteRefreshing: Boolean = false,
    val showsDuribalCallAction: Boolean = false,
    val unsupportedArea: RouteUnsupportedAreaUiState? = null,
) {
    val isStartEnabled: Boolean
        get() = cta.isEnabled
}

data class RouteUnsupportedAreaUiState(
    val editingTarget: RouteEditingTarget,
)

data class RouteLocationUiState(
    val placeId: String? = null,
    val name: String = "",
    val supportingText: String? = null,
    val coordinate: GeoCoordinate? = null,
    val category: PlaceCategory? = null,
    val metadataLabel: String? = null,
)

data class RouteOriginStatusUiState(
    val label: String,
    val tone: RouteOriginStatusTone = RouteOriginStatusTone.NEUTRAL,
)

data class RoutePreviewMapUiState(
    val status: RoutePreviewMapStatus = RoutePreviewMapStatus.LOADING,
    val routeOption: RouteOption? = null,
    val originCoordinate: GeoCoordinate? = null,
    val destinationCoordinate: GeoCoordinate? = null,
    val polyline: List<GeoCoordinate> = emptyList(),
    val fallbackMessage: String? = null,
) {
    val isDisplayable: Boolean
        get() =
            status == RoutePreviewMapStatus.READY &&
                originCoordinate != null &&
                destinationCoordinate != null &&
                polyline.size >= 2
}

data class RouteOptionCardUiState(
    val routeOption: RouteOption,
    val travelMode: RouteTravelMode = RouteTravelMode.WALK,
    val title: String,
    val description: String,
    val distanceMeters: Int,
    val estimatedTimeMinutes: Int,
    val riskLevel: RouteRiskLevel,
    val summaryLabel: String,
    val selectionLabel: String,
    val highlightLabel: String? = null,
    val metrics: List<RouteOptionCardMetricUiState> = emptyList(),
    val badges: List<RouteOptionBadge> = emptyList(),
    val segmentBars: List<RouteOptionSegmentBarUiState> = emptyList(),
    val transitStopLabel: String? = null,
    val transitOptionLabels: List<RouteTransitOptionLabelUiState> = emptyList(),
    val isSelected: Boolean = false,
)

data class RouteOptionSegmentBarUiState(
    val kind: RouteOptionSegmentKind,
    val label: String,
    val weight: Float,
    val routeLabel: String? = null,
)

enum class RouteOptionSegmentKind {
    WALK,
    BUS,
    SUBWAY,
}

data class RouteTransitOptionLabelUiState(
    val typeLabel: String,
    val routeNo: String,
    val arrivalLabel: String? = null,
)

data class RouteSelectedRouteUiState(
    val routeOption: RouteOption,
    val destination: RouteLocationUiState,
    val optionTitle: String,
    val title: String,
    val distanceMeters: Int,
    val estimatedTimeMinutes: Int,
    val riskLevel: RouteRiskLevel,
    val guidanceMessage: String,
    val summaryLabel: String,
    val estimatedTimeLabel: String,
    val distanceLabel: String,
    val riskLabel: String,
    val renderableSegmentLabel: String,
    val summaryMetrics: List<RouteSummaryMetricUiState> = emptyList(),
    val previewPoints: List<GeoCoordinate> = emptyList(),
    val segmentCount: Int = 0,
    val renderableSegmentCount: Int = 0,
    val fallbackSegmentCount: Int = 0,
    val previewFallbackNotice: String? = null,
    val badges: List<RouteOptionBadge> = emptyList(),
    val detailAccessibilityChips: List<RouteDetailChipUiState> = emptyList(),
    val detailHighlights: List<RouteDetailHighlightUiState> = emptyList(),
    val detailSteps: List<RouteDetailStepUiState> = emptyList(),
    val detailPolylines: List<RouteDetailPolylineUiState> = emptyList(),
    val detailFallbackMessage: String? = null,
    val lowFloorReservations: List<LowFloorBusReservation> = emptyList(),
)

data class RouteDetailPolylineUiState(
    val points: List<GeoCoordinate>,
    val kind: RouteDetailPolylineKind = RouteDetailPolylineKind.WALK,
)

enum class RouteDetailPolylineKind {
    WALK,
    TRANSIT,
}

data class RouteSummaryMetricUiState(
    val label: String,
    val value: String,
)

data class RouteDetailChipUiState(
    val label: String,
    val kind: RouteDetailChipKind = RouteDetailChipKind.PENDING,
    val tone: RouteDetailTone = RouteDetailTone.INFO,
)

data class RouteDetailHighlightUiState(
    val title: String,
    val description: String,
    val badgeLabel: String,
    val tone: RouteDetailTone,
)

data class RouteDetailStepUiState(
    val indexLabel: String,
    val title: String,
    val description: String,
    val metaLabel: String? = null,
    val badgeLabel: String? = null,
    val badgeTone: RouteDetailTone? = null,
    val kind: RouteDetailStepKind = RouteDetailStepKind.STRAIGHT,
    val tone: RouteDetailTone = RouteDetailTone.NEUTRAL,
    val coordinate: GeoCoordinate? = null,
    val transitLabel: String? = null,
    val transitStartName: String? = null,
    val transitEndName: String? = null,
    val transitDurationLabel: String? = null,
    val transitOptionLabels: List<RouteTransitOptionLabelUiState> = emptyList(),
)

internal fun LowFloorBusReservation.stableReservationKey(): String =
    listOf(stopName, arsNo, routeNo, vehicleNo)
        .joinToString(separator = "|")

data class RouteSettingCtaUiState(
    val label: String = "안내 시작",
    val supportingText: String = "경로 요약을 불러오는 동안 CTA를 잠시 비활성화합니다.",
    val isEnabled: Boolean = false,
)

data class RouteOptionCardMetricUiState(
    val label: String,
    val value: String,
)

enum class RouteDestinationHandoffState {
    DIRECT,
    EMPTY,
    INVALID_COORDINATE,
}

enum class RouteOriginState {
    MANUAL_SELECTION,
    CURRENT_LOCATION_LOADING,
    CURRENT_LOCATION_RESOLVED,
    CURRENT_LOCATION_UNAVAILABLE,
}

enum class RouteOriginStatusTone {
    NEUTRAL,
    INFO,
    WARNING,
}

enum class RoutePreviewMapStatus {
    LOADING,
    READY,
    NO_DESTINATION,
    INVALID_DESTINATION,
    NO_ROUTE,
    POLYLINE_UNAVAILABLE,
    ERROR,
}

enum class RouteTravelMode {
    WALK,
    TRANSIT,
}

enum class RouteDetailTone {
    NEUTRAL,
    INFO,
    WARNING,
}

enum class RouteDetailChipKind {
    STEP_FREE,
    ELEVATOR,
    AUDIO_SIGNAL,
    BRAILLE_BLOCK,
    CONSTRUCTION,
    SIGNAL_CROSSWALK,
    UNSIGNALIZED_CROSSWALK,
    CURB_GAP,
    STAIRS,
    PENDING,
}

enum class RouteDetailStepKind {
    START,
    ALIGHT,
    BUS,
    SUBWAY,
    STRAIGHT,
    TURN_LEFT,
    TURN_RIGHT,
    TACTILE_GUIDE,
    CROSSWALK,
    ELEVATOR,
    CONSTRUCTION,
    CURB_GAP,
    STAIRS,
    ARRIVAL,
    FALLBACK,
}

enum class RouteOptionBadge {
    SAFE_PRIORITY,
    STEP_FREE,
    AUDIO_SIGNAL,
    BRAILLE_BLOCK,
    SIGNAL_CROSSWALK,
    CURB_GAP,
    UNSIGNALIZED_CROSSWALK,
    LOW_SLOPE,
    MIDDLE_SLOPE,
    STAIR,
    CROSSWALK,
    ELEVATOR,
    NARROW_SIDEWALK,
    UNPAVED,
}

sealed interface RouteSettingUiAction {
    data object BackClicked : RouteSettingUiAction

    data object CloseClicked : RouteSettingUiAction

    data class WaypointClicked(
        val editingTarget: RouteEditingTarget,
    ) : RouteSettingUiAction

    data class TravelModeSelected(
        val mode: RouteTravelMode,
    ) : RouteSettingUiAction

    data class RouteOptionSelected(
        val routeOption: RouteOption,
    ) : RouteSettingUiAction

    data class RouteOptionDetailClicked(
        val routeOption: RouteOption,
    ) : RouteSettingUiAction

    data object WaypointsSwapClicked : RouteSettingUiAction

    data object StartNavigationClicked : RouteSettingUiAction

    data object RouteRefreshClicked : RouteSettingUiAction

    data object CurrentLocationClicked : RouteSettingUiAction
}

sealed interface RouteSettingUiEvent {
    data object NavigateBack : RouteSettingUiEvent

    data object NavigateToMap : RouteSettingUiEvent

    data object RequestLocationPermission : RouteSettingUiEvent

    data class NavigateToSearch(
        val editingTarget: RouteEditingTarget,
        val selectionMode: SearchSelectionMode = SearchSelectionMode.APPLY_TO_ROUTE,
    ) : RouteSettingUiEvent

    data class NavigateToRouteDetail(
        val routeOption: RouteOption,
    ) : RouteSettingUiEvent

    data class StartNavigationRequested(
        val request: RouteNavigationRequest,
    ) : RouteSettingUiEvent

    data class ShowSnackbar(
        @StringRes val messageResId: Int,
    ) : RouteSettingUiEvent
}

data class RouteNavigationRequest(
    val origin: RouteWaypoint,
    val destination: RouteWaypoint,
    val selectedRoute: RouteCandidate,
    val source: RouteSearchSource,
    val selectionHandoff: RouteNavigationSelectionHandoff? = null,
)

data class RouteNavigationSelectionHandoff(
    val searchId: String,
    val routeId: String,
    val sessionId: String,
    val initialRemainingDistanceMeters: Int? = null,
    val initialRemainingDurationSeconds: Int? = null,
)
