package com.ssafy.e102.eumgil.data.mock.fixture

import com.ssafy.e102.eumgil.core.model.PlaceCategory
import com.ssafy.e102.eumgil.core.model.PlaceDetail
import com.ssafy.e102.eumgil.core.model.PlaceQuery
import com.ssafy.e102.eumgil.core.model.PlaceSummary
import com.ssafy.e102.eumgil.core.model.SearchQuery
import com.ssafy.e102.eumgil.core.model.SearchResult

object MockPlaceFixtures {
    private val placeDetails =
        listOf(
            PlaceDetail(
                placeId = "mock-place-1",
                name = "Busan City Hall Elevator",
                address = "123 Jungang-daero, Busan",
                latitude = 35.1796,
                longitude = 129.0756,
                category = PlaceCategory.ELEVATOR,
                accessibilityTags = listOf("elevator", "wide-entry"),
                description = "Indoor elevator access for the main hall.",
            ),
            PlaceDetail(
                placeId = "mock-place-2",
                name = "해운대역 공공화장실",
                address = "해운대구",
                latitude = 35.1632,
                longitude = 129.1636,
                category = PlaceCategory.TOILET,
                accessibilityTags = listOf("accessible-toilet", "rest-area"),
                description = "Public rest stop with accessible toilet access.",
            ),
            PlaceDetail(
                placeId = "mock-place-3",
                name = "Braille Block Crossing",
                address = "9 Gwangbok-ro, Busan",
                latitude = 35.0984,
                longitude = 129.0365,
                category = PlaceCategory.BRAILLE_BLOCK,
                accessibilityTags = listOf("braille-block", "crosswalk"),
                description = "Braille block route segment prepared for demo mode.",
            ),
            PlaceDetail(
                placeId = "mock-place-4",
                name = "해운대해수욕장 화장실",
                address = "해운대구",
                latitude = 35.1587,
                longitude = 129.1604,
                category = PlaceCategory.TOILET,
                accessibilityTags = listOf("accessible-toilet"),
                description = "Category result fixture for low-vision toilet search.",
            ),
            PlaceDetail(
                placeId = "mock-place-5",
                name = "부산역 음식점",
                address = "동구",
                latitude = 35.1151,
                longitude = 129.0415,
                category = PlaceCategory.RESTAURANT,
                accessibilityTags = listOf("wide-entry"),
                description = "Category result fixture for low-vision restaurant search.",
            ),
            PlaceDetail(
                placeId = "mock-place-6",
                name = "센텀 접근 가능 음식점",
                address = "해운대구",
                latitude = 35.1699,
                longitude = 129.1302,
                category = PlaceCategory.RESTAURANT,
                accessibilityTags = listOf("wide-entry"),
                description = "Category result fixture for low-vision restaurant search.",
            ),
            PlaceDetail(
                placeId = "mock-place-7",
                name = "남포 접근 가능 숙박시설",
                address = "중구",
                latitude = 35.0979,
                longitude = 129.0344,
                category = PlaceCategory.OTHER,
                accessibilityTags = listOf("wide-entry", "elevator"),
                description = "Category result fixture for low-vision lodging search.",
            ),
            PlaceDetail(
                placeId = "mock-place-8",
                name = "해운대 병원",
                address = "해운대구",
                latitude = 35.1663,
                longitude = 129.1631,
                category = PlaceCategory.OTHER,
                accessibilityTags = listOf("wide-entry"),
                description = "Category result fixture for low-vision hospital search.",
            ),
        )

    fun getPlaces(query: PlaceQuery): List<PlaceSummary> {
        val normalizedKeyword = query.keyword?.trim()?.lowercase().orEmpty()

        return placeDetails
            .asSequence()
            .filter { detail ->
                normalizedKeyword.isBlank() ||
                    detail.name.lowercase().contains(normalizedKeyword) ||
                    detail.address.lowercase().contains(normalizedKeyword)
            }.filter { detail ->
                query.categories.isEmpty() || detail.category in query.categories
            }.map { detail -> detail.toSummary() }
            .toList()
    }

    fun getPlaceDetail(placeId: String): PlaceDetail? =
        placeDetails.firstOrNull { detail -> detail.placeId == placeId }

    fun search(query: SearchQuery): List<SearchResult> =
        getPlaces(PlaceQuery(keyword = query.normalizedKeyword))
            .take(query.limit)
            .map { place ->
                SearchResult(
                    placeId = place.placeId,
                    title = place.name,
                    subtitle = place.address,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    category = place.category,
                    accessibilityTagKeys = place.accessibilityTags,
                )
            }

    private fun PlaceDetail.toSummary(): PlaceSummary =
        PlaceSummary(
            placeId = placeId,
            name = name,
            address = address,
            latitude = latitude,
            longitude = longitude,
            category = category,
            accessibilityTags = accessibilityTags,
        )
}
