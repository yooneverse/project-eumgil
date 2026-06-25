package com.ssafy.e102.eumgil.feature.savedroute

import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.feature.route.RouteNavigationRequest

data class SavedRouteUiState(
    val selectedTab: SavedBookmarkTab = SavedBookmarkTab.PLACE,
    val placeContent: SavedPlaceContentUiState = SavedPlaceContentUiState(),
    val routeContent: SavedRouteBookmarkContentUiState = SavedRouteBookmarkContentUiState(),
    val isEditMode: Boolean = false,
    val isApplyingEditChanges: Boolean = false,
    val pendingPlaceRemovalIds: Set<String> = emptySet(),
    val pendingRouteRemovalIds: Set<String> = emptySet(),
    val placeSortOrder: SavedBookmarkSortOrder = SavedBookmarkSortOrder.NEAREST,
    val routeSortOrder: SavedBookmarkSortOrder = SavedBookmarkSortOrder.RECENT,
)

data class SavedPlaceContentUiState(
    val screenState: SavedBookmarkContentState = SavedBookmarkContentState.LOADING,
    val places: List<SavedPlaceUiModel> = emptyList(),
    val errorMessage: String? = null,
)

data class SavedRouteBookmarkContentUiState(
    val screenState: SavedBookmarkContentState = SavedBookmarkContentState.LOADING,
    val routes: List<SavedRouteBookmarkUiModel> = emptyList(),
    val errorMessage: String? = null,
)

data class SavedPlaceUiModel(
    val placeId: String,
    val name: String,
    val address: String?,
    val category: String?,
    val latitude: Double,
    val longitude: Double,
    val serverPlaceId: Long? = null,
    val provider: String? = null,
    val providerPlaceId: String? = null,
    val providerCategory: String? = null,
    val distanceMeters: Int? = null,
)

data class SavedRouteBookmarkUiModel(
    val bookmarkId: String,
    val routeName: String,
    val startLabel: String,
    val endLabel: String,
    val startPoint: GeoCoordinate,
    val endPoint: GeoCoordinate,
    val routeOption: RouteOption,
    val transportMode: String? = null,
    val routeOptionLabel: String? = null,
    val distanceMeters: Int? = null,
    val durationMinutes: Int? = null,
)

enum class SavedBookmarkTab {
    PLACE,
    ROUTE,
}

enum class SavedBookmarkContentState {
    LOADING,
    CONTENT,
    EMPTY,
    ERROR,
}

enum class SavedBookmarkSortOrder {
    NEAREST,
    RECENT,
}

sealed interface SavedRouteUiAction {
    data class TabSelected(
        val tab: SavedBookmarkTab,
    ) : SavedRouteUiAction

    data object EditClicked : SavedRouteUiAction

    data object EditDoneClicked : SavedRouteUiAction

    data object DeleteSelectedClicked : SavedRouteUiAction

    data object ExploreMapClicked : SavedRouteUiAction

    data object RouteSettingClicked : SavedRouteUiAction

    data object RetryClicked : SavedRouteUiAction

    data class SortOrderSelected(
        val sortOrder: SavedBookmarkSortOrder,
    ) : SavedRouteUiAction

    data class PlaceClicked(
        val placeId: String,
    ) : SavedRouteUiAction

    data class PlaceRouteGuideClicked(
        val placeId: String,
    ) : SavedRouteUiAction

    data class PlaceBriefingClicked(
        val placeId: String,
    ) : SavedRouteUiAction

    data class PlaceDeleteClicked(
        val placeId: String,
    ) : SavedRouteUiAction

    data class PlaceRemoveClicked(
        val placeId: String,
    ) : SavedRouteUiAction

    data class RouteGuideClicked(
        val bookmarkId: String,
    ) : SavedRouteUiAction

    data class RouteClicked(
        val bookmarkId: String,
    ) : SavedRouteUiAction

    data class RouteDeleteClicked(
        val bookmarkId: String,
    ) : SavedRouteUiAction

    data class RouteRemoveClicked(
        val bookmarkId: String,
    ) : SavedRouteUiAction
}

sealed interface SavedRouteUiEvent {
    data object NavigateToMap : SavedRouteUiEvent

    data class NavigateToNavigation(
        val request: RouteNavigationRequest,
    ) : SavedRouteUiEvent

    data class NavigateToRouteDetail(
        val request: RouteNavigationRequest,
    ) : SavedRouteUiEvent

    data class NavigateToRouteSetting(
        val initialRouteOption: RouteOption? = null,
    ) : SavedRouteUiEvent

    data object NavigateToRouteBriefing : SavedRouteUiEvent

    data class ShowSnackbar(
        val message: String,
    ) : SavedRouteUiEvent
}
