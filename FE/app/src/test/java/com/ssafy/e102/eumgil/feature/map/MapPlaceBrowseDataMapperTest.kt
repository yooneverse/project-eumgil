package com.ssafy.e102.eumgil.feature.map

import com.ssafy.e102.eumgil.core.model.AccessibilityTag
import com.ssafy.e102.eumgil.core.model.FacilityCategory
import com.ssafy.e102.eumgil.core.model.PlaceCategory
import com.ssafy.e102.eumgil.core.model.PlaceDetail
import com.ssafy.e102.eumgil.core.model.PlaceFeatureAvailability
import com.ssafy.e102.eumgil.core.model.PlaceFeatureType
import com.ssafy.e102.eumgil.core.model.PlaceMarkerKind
import com.ssafy.e102.eumgil.core.model.PlaceSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class MapPlaceBrowseDataMapperTest {
    @Test
    fun `toBrowseData keeps the original place category as the marker category`() {
        val browseData =
            MapPlaceBrowseDataMapper.toBrowseData(
                listOf(
                    PlaceSummary(
                        placeId = "place-cafe-1",
                        name = "Accessible Cafe",
                        address = "1 Jungang-daero, Busan",
                        latitude = 35.1796,
                        longitude = 129.0756,
                        category = PlaceCategory.FOOD_CAFE,
                        markerKind = PlaceMarkerKind.BUS_STOP,
                        features =
                            listOf(
                                PlaceFeatureAvailability(
                                    featureType = PlaceFeatureType.ACCESSIBLE_TOILET,
                                    isAvailable = true,
                                ),
                                PlaceFeatureAvailability(
                                    featureType = PlaceFeatureType.CHARGING_STATION,
                                    isAvailable = true,
                                ),
                                PlaceFeatureAvailability(
                                    featureType = PlaceFeatureType.ACCESSIBLE_PARKING,
                                    isAvailable = true,
                                ),
                            ),
                    ),
                ),
            )

        val marker = requireNotNull(browseData.facilityMarkers.single())

        assertEquals(FacilityCategory.FOOD_CAFE, marker.category)
        assertEquals(PlaceMarkerKind.BUS_STOP, marker.markerKind)
        assertEquals(
            setOf(
                FacilityCategory.FOOD_CAFE,
                FacilityCategory.TOILET,
                FacilityCategory.CHARGING_STATION,
            ),
            marker.filterCategories,
        )
        assertEquals(
            listOf(
                AccessibilityTag.ACCESSIBLE_PARKING,
                AccessibilityTag.CHARGING_STATION,
                AccessibilityTag.ACCESSIBLE_TOILET,
            ),
            marker.accessibilityTags,
        )
    }

    @Test
    fun `toFacilityDetailSeed keeps non shortcut accessibility features as detail tags in guide order`() {
        val detail =
            MapPlaceBrowseDataMapper.toFacilityDetailSeed(
                PlaceDetail(
                    placeId = "place-hotel-1",
                    name = "Accessible Hotel",
                    address = "10 Haeundae-ro, Busan",
                    latitude = 35.1587,
                    longitude = 129.1604,
                    category = PlaceCategory.ACCOMMODATION,
                    phoneNumber = "051-700-1000",
                    features =
                        listOf(
                            PlaceFeatureAvailability(
                                featureType = PlaceFeatureType.GUIDANCE_FACILITY,
                                isAvailable = true,
                            ),
                            PlaceFeatureAvailability(
                                featureType = PlaceFeatureType.ACCESSIBLE_ROOM,
                                isAvailable = true,
                            ),
                            PlaceFeatureAvailability(
                                featureType = PlaceFeatureType.ACCESSIBLE_PARKING,
                                isAvailable = true,
                            ),
                            PlaceFeatureAvailability(
                                featureType = PlaceFeatureType.ACCESSIBLE_ENTRANCE,
                                isAvailable = true,
                            ),
                        ),
                    description = "Front desk can confirm the accessible room on arrival.",
                ),
            )

        assertEquals(FacilityCategory.ACCOMMODATION, detail.category)
        assertEquals(
            listOf(
                AccessibilityTag.STEP_FREE_ENTRANCE,
                AccessibilityTag.ACCESSIBLE_PARKING,
                AccessibilityTag.GUIDANCE_FACILITY,
                AccessibilityTag.ACCESSIBLE_ROOM,
            ),
            detail.accessibilityTags,
        )
        assertEquals("051-700-1000", detail.phoneNumber)
        assertEquals("Front desk can confirm the accessible room on arrival.", detail.description)
    }

    @Test
    fun `toFacilityDetailSeed restores charging tag from raw accessibility keys`() {
        val detail =
            MapPlaceBrowseDataMapper.toFacilityDetailSeed(
                PlaceDetail(
                    placeId = "place-office-1",
                    name = "Charging Enabled Office",
                    address = "7 Center Plaza, Busan",
                    latitude = 35.1663,
                    longitude = 129.1631,
                    category = PlaceCategory.PUBLIC_OFFICE,
                    accessibilityTags = listOf("charging-station"),
                ),
            )

        assertEquals(FacilityCategory.PUBLIC_OFFICE, detail.category)
        assertEquals(listOf(AccessibilityTag.CHARGING_STATION), detail.accessibilityTags)
    }
}
