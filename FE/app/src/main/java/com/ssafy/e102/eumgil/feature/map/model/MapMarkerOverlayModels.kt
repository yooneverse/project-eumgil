package com.ssafy.e102.eumgil.feature.map.model

import com.ssafy.e102.eumgil.core.model.AccessibilityTag
import com.ssafy.e102.eumgil.core.model.BrailleBlockType
import com.ssafy.e102.eumgil.core.model.FacilityCategory
import com.ssafy.e102.eumgil.core.model.PlaceMarkerKind

enum class MapMarkerLoadStatus {
    LOADING,
    READY,
    ERROR,
}

data class MapMarkerCategoryType(
    val category: FacilityCategory,
    val brailleBlockType: BrailleBlockType? = null,
) {
    init {
        require(category == FacilityCategory.BRAILLE_BLOCK || brailleBlockType == null) {
            "Braille block type can only be assigned to BRAILLE_BLOCK markers."
        }
    }
}

enum class MapMarkerDisplayState {
    VISIBLE,
    HIDDEN_BY_FILTER,
}

data class MapMarkerUiModel(
    val markerId: String,
    val name: String,
    val coordinate: MapCoordinate,
    val categoryType: MapMarkerCategoryType,
    val markerKind: PlaceMarkerKind = PlaceMarkerKind.DEFAULT,
    val selectedFilterCategory: FacilityCategory? = null,
    val accessibilityTags: List<AccessibilityTag> = emptyList(),
    val displayState: MapMarkerDisplayState = MapMarkerDisplayState.VISIBLE,
)

data class MapMarkerOverlayState(
    val loadStatus: MapMarkerLoadStatus = MapMarkerLoadStatus.LOADING,
    val markers: List<MapMarkerUiModel> = emptyList(),
    val visibleMarkerCount: Int = 0,
    val totalMarkerCount: Int = 0,
    val hasActiveFilterSelection: Boolean = false,
) {
    val isLoading: Boolean
        get() = loadStatus == MapMarkerLoadStatus.LOADING

    val isLoadFailed: Boolean
        get() = loadStatus == MapMarkerLoadStatus.ERROR

    val isReady: Boolean
        get() = loadStatus == MapMarkerLoadStatus.READY

    val hasMarkers: Boolean
        get() = totalMarkerCount > 0

    val hasVisibleMarkers: Boolean
        get() = visibleMarkerCount > 0

    val isEmptyData: Boolean
        get() = isReady && totalMarkerCount == 0

    val isEmptyResult: Boolean
        get() = isReady && hasActiveFilterSelection && totalMarkerCount > 0 && visibleMarkerCount == 0

    val hiddenMarkerCount: Int
        get() = (totalMarkerCount - visibleMarkerCount).coerceAtLeast(0)

    val visibleMarkers: List<MapMarkerUiModel>
        get() = markers.filter { marker -> marker.displayState == MapMarkerDisplayState.VISIBLE }
}

data class MapFilterSelectionState(
    val isShowingAllCategories: Boolean = false,
    val selectedFacilityCategories: Set<FacilityCategory> = emptySet(),
    val selectedBrailleBlockTypes: Set<BrailleBlockType> = emptySet(),
) {
    val hasCustomSelection: Boolean
        get() = selectedFacilityCategories.isNotEmpty()

    val selectedCategoryCount: Int
        get() = selectedFacilityCategories.size

    fun isCategorySelected(category: FacilityCategory): Boolean =
        category in selectedFacilityCategories

    fun isBrailleBlockTypeSelected(brailleBlockType: BrailleBlockType): Boolean =
        FacilityCategory.BRAILLE_BLOCK in selectedFacilityCategories &&
            (selectedBrailleBlockTypes.isEmpty() || brailleBlockType in selectedBrailleBlockTypes)
}

data class MapCategoryFilterOption(
    val category: FacilityCategory,
    val totalMarkerCount: Int,
    val visibleMarkerCount: Int,
    val isSelected: Boolean,
)

data class MapBrailleBlockFilterOption(
    val brailleBlockType: BrailleBlockType,
    val totalMarkerCount: Int,
    val visibleMarkerCount: Int,
    val isSelected: Boolean,
)

data class MapMarkerFilterUiState(
    val loadStatus: MapMarkerLoadStatus = MapMarkerLoadStatus.LOADING,
    val selection: MapFilterSelectionState = MapFilterSelectionState(),
    val categoryOptions: List<MapCategoryFilterOption> = emptyList(),
    val brailleBlockTypeOptions: List<MapBrailleBlockFilterOption> = emptyList(),
    val visibleMarkerCount: Int = 0,
    val totalMarkerCount: Int = 0,
) {
    val isLoading: Boolean
        get() = loadStatus == MapMarkerLoadStatus.LOADING

    val isLoadFailed: Boolean
        get() = loadStatus == MapMarkerLoadStatus.ERROR

    val isReady: Boolean
        get() = loadStatus == MapMarkerLoadStatus.READY

    val hasCustomSelection: Boolean
        get() = selection.hasCustomSelection

    val isEmptyData: Boolean
        get() = isReady && totalMarkerCount == 0

    val isEmptyResult: Boolean
        get() = isReady && hasCustomSelection && totalMarkerCount > 0 && visibleMarkerCount == 0
}
