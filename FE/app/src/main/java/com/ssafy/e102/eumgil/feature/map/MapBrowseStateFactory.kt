package com.ssafy.e102.eumgil.feature.map

import com.ssafy.e102.eumgil.core.model.FacilityBrowseData
import com.ssafy.e102.eumgil.core.model.FacilityCategory
import com.ssafy.e102.eumgil.core.model.FacilityMarkerSeed
import com.ssafy.e102.eumgil.feature.map.model.MapBrailleBlockFilterOption
import com.ssafy.e102.eumgil.feature.map.model.MapCategoryFilterOption
import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import com.ssafy.e102.eumgil.feature.map.model.MapFilterSelectionState
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerCategoryType
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerDisplayState
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerFilterUiState
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerLoadStatus
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerOverlayState
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerUiModel

internal object MapBrowseStateFactory {
    fun createMarkerOverlayState(
        browseData: FacilityBrowseData,
        selection: MapFilterSelectionState,
    ): MapMarkerOverlayState {
        val normalizedSelection =
            normalizeSelection(
                selection = selection,
                browseData = browseData,
                preserveUnavailableCategories = true,
            )
        val markers =
            browseData.allMarkers.map { marker ->
                val matchesSelection = marker.matches(normalizedSelection)
                marker.toMapMarkerUiModel(
                    displayState =
                        if (matchesSelection) {
                            MapMarkerDisplayState.VISIBLE
                        } else {
                            MapMarkerDisplayState.HIDDEN_BY_FILTER
                        },
                    selectedFilterCategory =
                        if (matchesSelection) {
                            marker.selectedFilterCategory(normalizedSelection)
                        } else {
                            null
                        },
                )
            }

        return MapMarkerOverlayState(
            loadStatus = MapMarkerLoadStatus.READY,
            markers = markers,
            visibleMarkerCount = markers.count { marker -> marker.displayState == MapMarkerDisplayState.VISIBLE },
            totalMarkerCount = markers.size,
            hasActiveFilterSelection = normalizedSelection.selectedFacilityCategories.isNotEmpty(),
        )
    }

    fun createFilterUiState(
        browseData: FacilityBrowseData,
        selection: MapFilterSelectionState,
        overlayState: MapMarkerOverlayState,
    ): MapMarkerFilterUiState {
        val normalizedSelection =
            normalizeSelection(
                selection = selection,
                browseData = browseData,
                preserveUnavailableCategories = true,
            )
        val allMarkers = browseData.allMarkers
        val totalMarkerCountByCategory =
            browseData.availableCategories.associateWith { category ->
                allMarkers.count { marker -> category in marker.filterCategories }
            }
        val visibleMarkerCountByCategory =
            browseData.availableCategories.associateWith { category ->
                allMarkers.count { marker ->
                    category in marker.filterCategories && marker.matches(normalizedSelection)
                }
            }
        val totalBrailleBlockCountByType =
            browseData.brailleBlockMarkers
                .mapNotNull { marker -> marker.brailleBlockType }
                .groupingBy { brailleBlockType -> brailleBlockType }
                .eachCount()
        val visibleBrailleBlockCountByType =
            browseData.brailleBlockMarkers
                .filter { marker -> marker.matches(normalizedSelection) }
                .mapNotNull { marker -> marker.brailleBlockType }
                .groupingBy { brailleBlockType -> brailleBlockType }
                .eachCount()

        return MapMarkerFilterUiState(
            loadStatus = MapMarkerLoadStatus.READY,
            selection = normalizedSelection,
            categoryOptions =
                browseData.availableCategories
                    .filter(::shouldExposeCategoryOption)
                    .sortedWith(compareBy(::categoryFilterPriority, FacilityCategory::ordinal))
                    .map { category ->
                        MapCategoryFilterOption(
                            category = category,
                            totalMarkerCount = totalMarkerCountByCategory[category] ?: 0,
                            visibleMarkerCount = visibleMarkerCountByCategory[category] ?: 0,
                            isSelected = normalizedSelection.isCategorySelected(category),
                        )
                    },
            brailleBlockTypeOptions =
                browseData.availableBrailleBlockTypes.map { brailleBlockType ->
                    MapBrailleBlockFilterOption(
                        brailleBlockType = brailleBlockType,
                        totalMarkerCount = totalBrailleBlockCountByType[brailleBlockType] ?: 0,
                        visibleMarkerCount = visibleBrailleBlockCountByType[brailleBlockType] ?: 0,
                        isSelected =
                            normalizedSelection.isBrailleBlockTypeSelected(brailleBlockType),
                    )
                },
            visibleMarkerCount = overlayState.visibleMarkerCount,
            totalMarkerCount = overlayState.totalMarkerCount,
        )
    }

    fun createErrorMarkerOverlayState(): MapMarkerOverlayState =
        MapMarkerOverlayState(
            loadStatus = MapMarkerLoadStatus.ERROR,
        )

    fun createErrorFilterUiState(): MapMarkerFilterUiState =
        MapMarkerFilterUiState(
            loadStatus = MapMarkerLoadStatus.ERROR,
        )

    fun toggleCategory(
        selection: MapFilterSelectionState,
        browseData: FacilityBrowseData,
        category: FacilityCategory,
    ): MapFilterSelectionState {
        val availableCategories = browseData.availableCategories.toSet()
        if (category !in availableCategories) return normalizeSelection(selection = selection, browseData = browseData)

        val nextSelection =
            if (selection.isShowingAllCategories) {
                MapFilterSelectionState(
                    isShowingAllCategories = false,
                    selectedFacilityCategories = setOf(category),
                    selectedBrailleBlockTypes = emptySet(),
                )
            } else {
                if (category in selection.selectedFacilityCategories) {
                    val updatedCategories = selection.selectedFacilityCategories - category
                    if (updatedCategories.isEmpty()) {
                        resetSelection()
                    } else {
                        val selectedBrailleBlockTypes =
                            if (FacilityCategory.BRAILLE_BLOCK in updatedCategories) {
                                selection.selectedBrailleBlockTypes
                            } else {
                                emptySet()
                            }

                        MapFilterSelectionState(
                            isShowingAllCategories = false,
                            selectedFacilityCategories = updatedCategories,
                            selectedBrailleBlockTypes = selectedBrailleBlockTypes,
                        )
                    }
                } else {
                    MapFilterSelectionState(
                        isShowingAllCategories = false,
                        selectedFacilityCategories = selection.selectedFacilityCategories + category,
                        selectedBrailleBlockTypes = selection.selectedBrailleBlockTypes,
                    )
                }
            }

        return normalizeSelection(selection = nextSelection, browseData = browseData)
    }

    fun selectSingleCategory(
        selection: MapFilterSelectionState,
        browseData: FacilityBrowseData,
        category: FacilityCategory,
    ): MapFilterSelectionState {
        val availableCategories = browseData.availableCategories.toSet()
        if (category !in availableCategories) return normalizeSelection(selection = selection, browseData = browseData)

        val isAlreadyOnlySelected =
            !selection.isShowingAllCategories &&
                selection.selectedFacilityCategories == setOf(category)

        val nextSelection =
            if (isAlreadyOnlySelected) {
                resetSelection()
            } else {
                MapFilterSelectionState(
                    isShowingAllCategories = false,
                    selectedFacilityCategories = setOf(category),
                    selectedBrailleBlockTypes = emptySet(),
                )
            }

        return normalizeSelection(selection = nextSelection, browseData = browseData)
    }

    fun resetSelection(): MapFilterSelectionState = MapFilterSelectionState()

    private fun showAllSelection(): MapFilterSelectionState =
        MapFilterSelectionState(
            isShowingAllCategories = true,
        )

    fun normalizeSelection(
        selection: MapFilterSelectionState,
        browseData: FacilityBrowseData,
        preserveUnavailableCategories: Boolean = false,
    ): MapFilterSelectionState {
        if (selection.isShowingAllCategories) {
            return showAllSelection()
        }

        val availableCategories = browseData.availableCategories.toSet()
        val availableBrailleBlockTypes = browseData.availableBrailleBlockTypes.toSet()
        val normalizedCategories =
            if (preserveUnavailableCategories) {
                selection.selectedFacilityCategories
            } else {
                selection.selectedFacilityCategories intersect availableCategories
            }
        if (normalizedCategories.isEmpty()) {
            return resetSelection()
        }

        val normalizedBrailleBlockTypes =
            if (FacilityCategory.BRAILLE_BLOCK in normalizedCategories) {
                selection.selectedBrailleBlockTypes intersect availableBrailleBlockTypes
            } else {
                emptySet()
            }

        return MapFilterSelectionState(
            isShowingAllCategories = false,
            selectedFacilityCategories = normalizedCategories,
            selectedBrailleBlockTypes = normalizedBrailleBlockTypes,
        )
    }

    private fun FacilityMarkerSeed.matches(selection: MapFilterSelectionState): Boolean {
        if (selection.isShowingAllCategories) return true
        if (filterCategories.intersect(selection.selectedFacilityCategories).isEmpty()) return false
        if (category != FacilityCategory.BRAILLE_BLOCK) return true
        return selection.selectedBrailleBlockTypes.isEmpty() || brailleBlockType in selection.selectedBrailleBlockTypes
    }

    private fun FacilityMarkerSeed.toMapMarkerUiModel(
        displayState: MapMarkerDisplayState,
        selectedFilterCategory: FacilityCategory?,
    ): MapMarkerUiModel =
        MapMarkerUiModel(
            markerId = facilityId,
            name = name,
            coordinate =
                MapCoordinate(
                    latitude = coordinate.latitude,
                    longitude = coordinate.longitude,
                ),
            categoryType =
                MapMarkerCategoryType(
                    category = category,
                    brailleBlockType = brailleBlockType,
                ),
            markerKind = markerKind,
            selectedFilterCategory = selectedFilterCategory,
            accessibilityTags = accessibilityTags,
            displayState = displayState,
        )

    private fun FacilityMarkerSeed.selectedFilterCategory(selection: MapFilterSelectionState): FacilityCategory? {
        if (selection.isShowingAllCategories || selection.selectedFacilityCategories.isEmpty()) return null
        return FEATURE_MARKER_ICON_PRIORITY.firstOrNull { category ->
            category in selection.selectedFacilityCategories && category in filterCategories
        }
    }

    private fun categoryFilterPriority(category: FacilityCategory): Int =
        when (category) {
            FacilityCategory.TOILET -> 0
            FacilityCategory.ELEVATOR -> 1
            FacilityCategory.CHARGING_STATION -> 2
            FacilityCategory.FOOD_CAFE -> 3
            FacilityCategory.TOURIST_SPOT -> 4
            FacilityCategory.ACCOMMODATION -> 5
            FacilityCategory.HEALTHCARE -> 6
            FacilityCategory.WELFARE -> 7
            FacilityCategory.PUBLIC_OFFICE -> 8
            FacilityCategory.BRAILLE_BLOCK -> 9
            FacilityCategory.RESTAURANT -> 10
            FacilityCategory.TOURIST_ATTRACTION -> 11
            FacilityCategory.OTHER -> 12
        }

    private fun shouldExposeCategoryOption(category: FacilityCategory): Boolean =
        category != FacilityCategory.OTHER &&
            category != FacilityCategory.RESTAURANT &&
            category != FacilityCategory.TOURIST_ATTRACTION

    private val FEATURE_MARKER_ICON_PRIORITY =
        listOf(
            FacilityCategory.TOILET,
            FacilityCategory.ELEVATOR,
            FacilityCategory.CHARGING_STATION,
        )
}
