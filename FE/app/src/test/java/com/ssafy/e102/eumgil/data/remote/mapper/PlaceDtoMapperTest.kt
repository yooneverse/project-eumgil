package com.ssafy.e102.eumgil.data.remote.mapper

import com.ssafy.e102.eumgil.core.model.MapPlaceDetailType
import com.ssafy.e102.eumgil.core.model.PlaceCategory
import com.ssafy.e102.eumgil.core.model.PlaceFeatureType
import com.ssafy.e102.eumgil.core.model.PlaceMarkerKind
import com.ssafy.e102.eumgil.data.remote.dto.MapPlaceDetailDto
import com.ssafy.e102.eumgil.data.remote.dto.PlaceAccessibilityFeatureDto
import com.ssafy.e102.eumgil.data.remote.dto.PlaceDetailDto
import com.ssafy.e102.eumgil.data.remote.dto.PlacePointDto
import com.ssafy.e102.eumgil.data.remote.dto.PlaceSummaryDto
import com.ssafy.e102.eumgil.data.remote.dto.PlaceTransitArrivalDto
import com.ssafy.e102.eumgil.data.remote.dto.PlacesBrowseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceDtoMapperTest {
    @Test
    fun `toPlaceSummaries keeps the API category and all available accessibility tag keys`() {
        val summaries =
            PlaceDtoMapper.toPlaceSummaries(
                PlacesBrowseDto(
                    places =
                        listOf(
                            PlaceSummaryDto(
                                placeId = 88L,
                                name = "Remote Welfare Center",
                                category = "WELFARE",
                                address = "88 Welfare-ro, Busan",
                                point = PlacePointDto(lat = 35.1801, lng = 129.0722),
                                accessibilityFeatures =
                                    listOf(
                                        PlaceAccessibilityFeatureDto(
                                            featureType = "guidanceFacility",
                                            isAvailable = true,
                                        ),
                                        PlaceAccessibilityFeatureDto(
                                            featureType = "accessibleRoom",
                                            isAvailable = true,
                                        ),
                                        PlaceAccessibilityFeatureDto(
                                            featureType = "accessibleToilet",
                                            isAvailable = true,
                                        ),
                                        PlaceAccessibilityFeatureDto(
                                            featureType = "accessibleParking",
                                            isAvailable = false,
                                        ),
                                    ),
                                isBookmarked = false,
                            ),
                        ),
                ),
            )

        assertEquals(1, summaries.size)
        assertEquals(PlaceCategory.WELFARE, summaries.first().category)
        assertEquals(PlaceMarkerKind.DEFAULT, summaries.first().markerKind)
        assertEquals(
            listOf(
                PlaceFeatureType.GUIDANCE_FACILITY,
                PlaceFeatureType.ACCESSIBLE_ROOM,
                PlaceFeatureType.ACCESSIBLE_TOILET,
                PlaceFeatureType.ACCESSIBLE_PARKING,
            ),
            summaries.first().features.map { feature -> feature.featureType },
        )
        assertEquals(
            listOf("guidance-facility", "accessible-room", "accessible-toilet"),
            summaries.first().accessibilityTags,
        )
    }

    @Test
    fun `toPlaceSummaries keeps explicit marker kind from marker response`() {
        val summaries =
            PlaceDtoMapper.toPlaceSummaries(
                PlacesBrowseDto(
                    places =
                        listOf(
                            PlaceSummaryDto(
                                placeId = 90L,
                                name = "Bus stop",
                                category = "ETC",
                                markerKind = "BUS_STOP",
                                address = "90 Transit-ro, Busan",
                                point = PlacePointDto(lat = 35.1801, lng = 129.0722),
                                accessibilityFeatures = emptyList(),
                                isBookmarked = false,
                            ),
                            PlaceSummaryDto(
                                placeId = 91L,
                                name = "Subway station",
                                category = "ETC",
                                markerKind = "SUBWAY_STATION",
                                address = "91 Transit-ro, Busan",
                                point = PlacePointDto(lat = 35.1802, lng = 129.0723),
                                accessibilityFeatures = emptyList(),
                                isBookmarked = false,
                            ),
                        ),
                ),
            )

        assertEquals(
            listOf(PlaceMarkerKind.BUS_STOP, PlaceMarkerKind.SUBWAY_STATION),
            summaries.map { summary -> summary.markerKind },
        )
    }

    @Test
    fun `toPlaceDetail keeps description when present and normalizes blank optional values`() {
        val detail =
            PlaceDtoMapper.toPlaceDetail(
                PlaceDetailDto(
                    placeId = 101L,
                    name = "Barrier-Free Hotel",
                    category = "ACCOMMODATION",
                    address = "101 Ocean-ro, Busan",
                    point = PlacePointDto(lat = 35.166, lng = 129.072),
                    providerPlaceId = "   ",
                    accessibilityFeatures =
                        listOf(
                            PlaceAccessibilityFeatureDto(
                                featureType = "elevator",
                                isAvailable = true,
                            ),
                        ),
                    isBookmarked = true,
                    phone = "051-555-0101",
                    description = "Wheelchair-friendly lobby and rooms",
                ),
            )

        assertEquals(PlaceCategory.ACCOMMODATION, detail.category)
        assertEquals(listOf(PlaceFeatureType.ELEVATOR), detail.features.map { feature -> feature.featureType })
        assertEquals(listOf("elevator"), detail.accessibilityTags)
        assertEquals("Wheelchair-friendly lobby and rooms", detail.description)
        assertEquals("051-555-0101", detail.phoneNumber)
        assertNull(detail.providerPlaceId)
    }

    @Test
    fun `toMapTappedPlaceDetail preserves external target metadata and nullable category`() {
        val detail =
            PlaceDtoMapper.toMapTappedPlaceDetail(
                MapPlaceDetailDto(
                    bookmarkTargetId = "kakao:poi-123",
                    detailType = "EXTERNAL_POI",
                    placeId = null,
                    provider = "KAKAO",
                    providerPlaceId = "poi-123",
                    name = "Kakao Cafe",
                    category = null,
                    providerCategory = "Cafe",
                    address = "10 Cafe-ro, Busan",
                    point = PlacePointDto(lat = 35.1799, lng = 129.0752),
                    accessibilityFeatures =
                        listOf(
                            PlaceAccessibilityFeatureDto(
                                featureType = "accessibleEntrance",
                                isAvailable = true,
                            ),
                            PlaceAccessibilityFeatureDto(
                                featureType = "accessibleParking",
                                isAvailable = false,
                            ),
                        ),
                    transitArrivals =
                        listOf(
                            PlaceTransitArrivalDto(
                                transitType = "BUS",
                                routeName = "100",
                                direction = null,
                                remainingMinute = 6,
                                isLowFloor = true,
                                source = "REALTIME",
                            ),
                        ),
                    isBookmarked = false,
                    phone = "051-123-4567",
                    description = "External Kakao POI",
                ),
            )

        assertEquals("kakao:poi-123", detail.bookmarkTargetId)
        assertEquals(MapPlaceDetailType.EXTERNAL_POI, detail.detailType)
        assertNull(detail.placeId)
        assertEquals("KAKAO", detail.provider)
        assertEquals("poi-123", detail.providerPlaceId)
        assertEquals("Cafe", detail.providerCategory)
        assertNull(detail.category)
        assertEquals("Kakao Cafe", detail.name)
        assertEquals("051-123-4567", detail.phoneNumber)
        assertEquals(PlaceFeatureType.ACCESSIBLE_ENTRANCE, detail.features.first().featureType)
        assertEquals(listOf("step-free-entrance"), detail.accessibilityTags)
        assertEquals("100", detail.transitArrivals.single().routeName)
        assertEquals(6, detail.transitArrivals.single().remainingMinute)
        assertEquals(true, detail.transitArrivals.single().isLowFloor)
        assertEquals("External Kakao POI", detail.description)
    }
}
