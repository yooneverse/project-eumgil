package com.ssafy.e102.eumgil.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceDestinationTest {
    @Test
    fun `search result handoff preserves bookmark metadata needed for server save`() {
        val result =
            SearchResult(
                placeId = "place-1",
                title = "Busan City Hall Elevator",
                subtitle = "123 Jungang-daero, Busan",
                latitude = 35.1796,
                longitude = 129.0756,
                category = PlaceCategory.ELEVATOR,
                serverPlaceId = "101",
                providerPlaceId = "kakao-101",
            )

        val destination = result.toPlaceDestination()

        assertEquals("place-1", destination.placeId)
        assertEquals("Busan City Hall Elevator", destination.name)
        assertEquals("123 Jungang-daero, Busan", destination.address)
        assertEquals(35.1796, destination.latitude, 0.0)
        assertEquals(129.0756, destination.longitude, 0.0)
        assertEquals(PlaceCategory.ELEVATOR, destination.category)
        assertEquals(101L, destination.serverPlaceId)
        assertEquals("KAKAO", destination.provider)
        assertEquals("kakao-101", destination.providerPlaceId)
        assertEquals("ELEVATOR", destination.providerCategory)
    }

    @Test
    fun `facility detail handoff maps facility category into shared destination contract`() {
        val detail =
            FacilityDetailSeed(
                facilityId = "facility-1",
                name = "Accessible Rest Stop",
                address = "45 Haeundae-ro, Busan",
                coordinate = GeoCoordinate(latitude = 35.1632, longitude = 129.1636),
                category = FacilityCategory.TOILET,
            )

        val destination = detail.toPlaceDestination()

        assertEquals("facility-1", destination.placeId)
        assertEquals("Accessible Rest Stop", destination.name)
        assertEquals("45 Haeundae-ro, Busan", destination.address)
        assertEquals(35.1632, destination.latitude, 0.0)
        assertEquals(129.1636, destination.longitude, 0.0)
        assertEquals(PlaceCategory.TOILET, destination.category)
        assertEquals(null, destination.provider)
        assertEquals(null, destination.providerPlaceId)
    }

    @Test
    fun `facility detail handoff preserves new place categories for recent destination consumers`() {
        val detail =
            FacilityDetailSeed(
                facilityId = "2",
                name = "Busan District Office",
                address = "10 Jungang-daero, Busan",
                coordinate = GeoCoordinate(latitude = 35.1798, longitude = 129.0758),
                category = FacilityCategory.PUBLIC_OFFICE,
            )

        val destination = detail.toPlaceDestination()

        assertEquals(PlaceCategory.PUBLIC_OFFICE, destination.category)
        assertEquals(2L, destination.serverPlaceId)
    }

    @Test
    fun `provider only search result with valid coordinates produces route destination handoff`() {
        val result =
            SearchResult(
                placeId = "provider:kakao:987654321",
                title = "Provider Only Cafe",
                subtitle = "2 Gwangbok-ro, Busan",
                latitude = 35.1010,
                longitude = 129.0330,
                category = null,
                serverPlaceId = null,
                providerPlaceId = "987654321",
                accessibilityTagKeys = listOf("step-free-entrance"),
                matched = false,
            )

        val destination = result.toPlaceDestinationOrNull()

        assertEquals("provider:kakao:987654321", destination?.placeId)
        assertEquals("Provider Only Cafe", destination?.name)
        assertEquals("2 Gwangbok-ro, Busan", destination?.address)
        assertEquals(35.1010, destination?.latitude ?: Double.NaN, 0.0)
        assertEquals(129.0330, destination?.longitude ?: Double.NaN, 0.0)
        assertEquals("KAKAO", destination?.provider)
        assertEquals("987654321", destination?.providerPlaceId)
    }
}
