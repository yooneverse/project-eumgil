package com.ssafy.e102.eumgil.feature.map

import com.ssafy.e102.eumgil.core.model.FacilityBrowseData
import com.ssafy.e102.eumgil.core.model.FacilityCategory
import com.ssafy.e102.eumgil.core.model.FacilityMarkerSeed
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.feature.map.model.MapFilterSelectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapBrowseStateFactoryTest {
    @Test
    fun `default selection hides markers without treating the state as an empty result`() {
        val browseData =
            FacilityBrowseData(
                facilityMarkers =
                    listOf(
                        FacilityMarkerSeed(
                            facilityId = "food-cafe-1",
                            name = "Accessible Cafe",
                            coordinate = GeoCoordinate(latitude = 35.1796, longitude = 129.0756),
                            category = FacilityCategory.FOOD_CAFE,
                        ),
                    ),
                availableCategories = listOf(FacilityCategory.FOOD_CAFE),
            )

        val overlayState =
            MapBrowseStateFactory.createMarkerOverlayState(
                browseData = browseData,
                selection = MapBrowseStateFactory.resetSelection(),
            )

        assertEquals(1, overlayState.totalMarkerCount)
        assertEquals(0, overlayState.visibleMarkerCount)
        assertFalse(overlayState.hasActiveFilterSelection)
        assertFalse(overlayState.isEmptyResult)
    }

    @Test
    fun `toggleCategory keeps explicit single category selection when it is the only available category`() {
        val browseData =
            FacilityBrowseData(
                facilityMarkers =
                    listOf(
                        FacilityMarkerSeed(
                            facilityId = "food-cafe-1",
                            name = "Accessible Cafe",
                            coordinate = GeoCoordinate(latitude = 35.1796, longitude = 129.0756),
                            category = FacilityCategory.FOOD_CAFE,
                        ),
                    ),
                availableCategories = listOf(FacilityCategory.FOOD_CAFE),
            )

        val selection =
            MapBrowseStateFactory.toggleCategory(
                selection = MapFilterSelectionState(),
                browseData = browseData,
                category = FacilityCategory.FOOD_CAFE,
            )

        assertFalse(selection.isShowingAllCategories)
        assertEquals(setOf(FacilityCategory.FOOD_CAFE), selection.selectedFacilityCategories)
    }

    @Test
    fun `toggleCategory clears the selection when the last explicit category is toggled off`() {
        val browseData =
            FacilityBrowseData(
                facilityMarkers =
                    listOf(
                        FacilityMarkerSeed(
                            facilityId = "food-cafe-1",
                            name = "Accessible Cafe",
                            coordinate = GeoCoordinate(latitude = 35.1796, longitude = 129.0756),
                            category = FacilityCategory.FOOD_CAFE,
                        ),
                    ),
                availableCategories = listOf(FacilityCategory.FOOD_CAFE),
            )
        val selected =
            MapFilterSelectionState(
                isShowingAllCategories = false,
                selectedFacilityCategories = setOf(FacilityCategory.FOOD_CAFE),
            )

        val selection =
            MapBrowseStateFactory.toggleCategory(
                selection = selected,
                browseData = browseData,
                category = FacilityCategory.FOOD_CAFE,
            )

        assertFalse(selection.isShowingAllCategories)
        assertTrue(selection.selectedFacilityCategories.isEmpty())
    }

    @Test
    fun `toggleCategory keeps explicit multi select when all available categories are selected`() {
        val browseData =
            FacilityBrowseData(
                facilityMarkers =
                    listOf(
                        FacilityMarkerSeed(
                            facilityId = "toilet-1",
                            name = "Accessible Toilet",
                            coordinate = GeoCoordinate(latitude = 35.1796, longitude = 129.0756),
                            category = FacilityCategory.TOILET,
                        ),
                        FacilityMarkerSeed(
                            facilityId = "elevator-1",
                            name = "Station Elevator",
                            coordinate = GeoCoordinate(latitude = 35.1801, longitude = 129.0761),
                            category = FacilityCategory.ELEVATOR,
                        ),
                    ),
                availableCategories = listOf(FacilityCategory.TOILET, FacilityCategory.ELEVATOR),
            )
        val toiletSelected =
            MapBrowseStateFactory.toggleCategory(
                selection = MapFilterSelectionState(),
                browseData = browseData,
                category = FacilityCategory.TOILET,
            )

        val selection =
            MapBrowseStateFactory.toggleCategory(
                selection = toiletSelected,
                browseData = browseData,
                category = FacilityCategory.ELEVATOR,
            )

        assertFalse(selection.isShowingAllCategories)
        assertEquals(
            setOf(FacilityCategory.TOILET, FacilityCategory.ELEVATOR),
            selection.selectedFacilityCategories,
        )
    }

    @Test
    fun `visible marker exposes selected accessibility filter category for marker icon policy`() {
        val browseData =
            FacilityBrowseData(
                facilityMarkers =
                    listOf(
                        FacilityMarkerSeed(
                            facilityId = "other-accessible-toilet",
                            name = "Accessible building",
                            coordinate = GeoCoordinate(latitude = 35.1796, longitude = 129.0756),
                            category = FacilityCategory.OTHER,
                            filterCategories = setOf(FacilityCategory.OTHER, FacilityCategory.TOILET),
                        ),
                    ),
                availableCategories = listOf(FacilityCategory.OTHER, FacilityCategory.TOILET),
            )

        val overlayState =
            MapBrowseStateFactory.createMarkerOverlayState(
                browseData = browseData,
                selection =
                    MapFilterSelectionState(
                        selectedFacilityCategories = setOf(FacilityCategory.TOILET),
                    ),
            )

        assertEquals(FacilityCategory.TOILET, overlayState.visibleMarkers.single().selectedFilterCategory)
    }
}
