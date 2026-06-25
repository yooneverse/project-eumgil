package com.ssafy.e102.eumgil.data.remote.mapper

import com.ssafy.e102.eumgil.data.remote.dto.PlacePointDto
import com.ssafy.e102.eumgil.data.remote.dto.PlacesSearchDto
import com.ssafy.e102.eumgil.data.remote.dto.SearchPlaceDto
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchDtoMapperTest {
    @Test
    fun `toSearchPage falls back to address when external result name is blank`() {
        val page =
            SearchDtoMapper.toSearchPage(
                PlacesSearchDto(
                    places =
                        listOf(
                            SearchPlaceDto(
                                placeId = null,
                                provider = "KAKAO",
                                providerPlaceId = "home-123",
                                name = "   ",
                                category = null,
                                address = "101 Jungang-daero, Busan",
                                distanceMeter = null,
                                point = PlacePointDto(lat = 35.1797, lng = 129.0750),
                                accessibilityFeatures = emptyList(),
                                matched = false,
                            ),
                        ),
                    nextCursor = null,
                    size = 1,
                    totalElements = 1,
                    hasNext = false,
                ),
            )

        val result = page.results.single()
        assertEquals("101 Jungang-daero, Busan", result.title)
        assertEquals("101 Jungang-daero, Busan", result.subtitle)
        assertEquals("provider:kakao:home-123", result.placeId)
    }
}
