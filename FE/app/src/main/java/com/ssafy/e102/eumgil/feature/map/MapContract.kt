package com.ssafy.e102.eumgil.feature.map

import com.ssafy.e102.eumgil.core.model.FacilityCategory
import com.ssafy.e102.eumgil.core.model.FacilityDetailSeed
import com.ssafy.e102.eumgil.core.model.MapTappedPlaceDetail
import com.ssafy.e102.eumgil.core.model.PlaceDestination
import com.ssafy.e102.eumgil.core.model.RecentDestination
import com.ssafy.e102.eumgil.data.repository.DestinationPreviewRequest
import com.ssafy.e102.eumgil.data.repository.RouteEditingTarget
import com.ssafy.e102.eumgil.feature.map.model.MapCameraTarget
import com.ssafy.e102.eumgil.feature.map.model.ApprovedReportMarkerUiState
import com.ssafy.e102.eumgil.feature.map.model.ApprovedReportSheetState
import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerFilterUiState
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerOverlayState
import com.ssafy.e102.eumgil.feature.map.model.MapShortcutFilterKey
import com.ssafy.e102.eumgil.feature.map.model.MapShortcutFilterRowState
import com.ssafy.e102.eumgil.feature.search.SearchSelectionMode

data class MapUiState(
    val cameraTarget: MapCameraTarget = MapCameraTarget.DefaultBusan,
    val rendererSessionKey: Long = 0L,
    val selectedOrigin: PlaceDestination? = null,
    val selectedDestination: PlaceDestination? = null,
    val routeEditingTarget: RouteEditingTarget = RouteEditingTarget.DESTINATION,
    val selectedMarkerId: String? = null,
    val selectedMapPinCoordinate: MapCoordinate? = null,
    val locationStatus: MapLocationStatus = MapLocationStatus.PermissionDenied,
    val recenterButtonState: MapRecenterButtonState = MapRecenterButtonState.REQUEST_PERMISSION,
    val isRecenterButtonActive: Boolean = false,
    val markerOverlayState: MapMarkerOverlayState = MapMarkerOverlayState(),
    val markerFilterState: MapMarkerFilterUiState = MapMarkerFilterUiState(),
    val shortcutFilterState: MapShortcutFilterRowState = MapShortcutFilterRowState(),
    val isSearchHereVisible: Boolean = false,
    val recentDestinations: List<RecentDestination> = emptyList(),
    val facilityDetailSheetState: MapFacilityDetailSheetState = MapFacilityDetailSheetState(),
    val approvedReportMarkerState: ApprovedReportMarkerUiState = ApprovedReportMarkerUiState(),
    val approvedReportSheetState: ApprovedReportSheetState = ApprovedReportSheetState(),
    val routeEndpointMapPickerState: RouteEndpointMapPickerState? = null,
    val isVoiceSearchVisible: Boolean = false,
)

data class RouteEndpointMapPickerState(
    val editingTarget: RouteEditingTarget,
    val candidateCoordinate: MapCoordinate? = null,
    val candidateDetail: MapTappedPlaceDetail? = null,
    val isResolvingCandidate: Boolean = false,
    val candidateErrorMessage: String? = null,
)

data class MapFacilityDetailSheetState(
    val detail: FacilityDetailSeed? = null,
    val mapTapDetail: MapTappedPlaceDetail? = null,
    val mapTapNameHint: String? = null,
    val destinationPreview: DestinationPreviewRequest? = null,
    val presentation: MapFacilityDetailSheetPresentation = MapFacilityDetailSheetPresentation.EXPANDED,
    val isMapTapDetailLoading: Boolean = false,
    val mapTapDetailErrorMessage: String? = null,
    val isBookmarked: Boolean = false,
    val isBookmarkUpdating: Boolean = false,
    val bookmarkErrorMessage: String? = null,
) {
    val isVisible: Boolean
        get() =
            detail != null ||
                mapTapDetail != null ||
                destinationPreview != null ||
                isMapTapDetailLoading ||
                mapTapDetailErrorMessage != null
}

enum class MapFacilityDetailSheetPresentation {
    EXPANDED,
    COMPACT,
}

data class MapTapPayload(
    val coordinate: MapCoordinate,
    val clickType: MapTapClickType = MapTapClickType.ADDRESS,
    val provider: String? = null,
    val providerPlaceId: String? = null,
    val nameHint: String? = null,
)

enum class MapTapClickType {
    POI,
    ADDRESS,
}

sealed interface MapUiAction {
    data object SearchEntryClicked : MapUiAction

    data object VoiceSearchClicked : MapUiAction

    data object VoiceSearchDismissed : MapUiAction

    data object SearchHereClicked : MapUiAction

    data object LocationActionClicked : MapUiAction

    data object ZoomInClicked : MapUiAction

    data object ZoomOutClicked : MapUiAction

    data object FacilityDetailDismissed : MapUiAction

    data object FacilityDetailExpanded : MapUiAction

    data object BackgroundMapTapped : MapUiAction

    data class ApprovedReportMarkerTapped(
        val reportId: Long,
    ) : MapUiAction

    data object ApprovedReportSheetDismissed : MapUiAction

    data class RouteEndpointMapPickerEntered(
        val editingTarget: RouteEditingTarget,
    ) : MapUiAction

    data object RouteEndpointMapPickerDismissed : MapUiAction

    data object FacilitySetDestinationClicked : MapUiAction

    data class FacilitySetRouteEndpointClicked(
        val editingTarget: RouteEditingTarget,
    ) : MapUiAction

    data class RouteEndpointStatusClicked(
        val editingTarget: RouteEditingTarget,
    ) : MapUiAction

    data class ShortcutFilterClicked(
        val key: MapShortcutFilterKey,
    ) : MapUiAction

    data class RecentDestinationPreviewClicked(
        val placeId: String,
    ) : MapUiAction

    data class RecentDestinationRouteClicked(
        val placeId: String,
    ) : MapUiAction

    data object FacilityBookmarkClicked : MapUiAction

    data object FacilityPhoneClicked : MapUiAction

    data class MarkerTapped(
        val markerId: String,
    ) : MapUiAction

    data class MapTapped(
        val payload: MapTapPayload,
    ) : MapUiAction

    data class ViewportCameraChanged(
        val center: MapCoordinate,
        val zoomLevel: Int,
        val isUserGesture: Boolean = false,
        val isSelectedMapPinVisibleInViewport: Boolean? = null,
    ) : MapUiAction

    data class MarkerCategoryFilterToggled(
        val category: FacilityCategory,
    ) : MapUiAction

    data object MarkerCategoryFilterReset : MapUiAction
}

sealed interface MapUiEvent {
    data class NavigateToSearch(
        val editingTarget: RouteEditingTarget,
        val selectionMode: SearchSelectionMode = SearchSelectionMode.PREVIEW_ON_MAP,
    ) : MapUiEvent

    data class NavigateToRouteSetting(
        val locationPermissionPrechecked: Boolean = false,
    ) : MapUiEvent

    data object RequestLocationPermission : MapUiEvent

    data class ShowSnackbar(
        val message: String,
    ) : MapUiEvent

    data class OpenDialer(
        val phoneNumber: String,
    ) : MapUiEvent
}

sealed interface MapLocationStatus {
    data object PermissionDenied : MapLocationStatus

    data object Loading : MapLocationStatus

    data class Ready(
        val location: MapCoordinate,
        val accuracyMeters: Float?,
    ) : MapLocationStatus

    data class Unavailable(
        val reason: MapLocationUnavailableReason,
    ) : MapLocationStatus
}

enum class MapLocationUnavailableReason {
    CURRENT_LOCATION_UNAVAILABLE,
    LOCATION_SERVICES_DISABLED,
    NO_LOCATION_FEATURE,
}

enum class MapRecenterButtonState {
    REQUEST_PERMISSION,
    LOADING,
    RETRY,
    DISABLED,
    ENABLED,
}
