package com.ssafy.e102.eumgil.feature.map.component

import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class KakaoMapViewportBoundsTest {
    @Test
    fun `createViewportBounds uses all four corners`() {
        val bounds =
            createViewportBounds(
                listOf(
                    MapCoordinate(latitude = 35.1000, longitude = 129.1000),
                    MapCoordinate(latitude = 35.1060, longitude = 129.1040),
                    MapCoordinate(latitude = 35.0940, longitude = 129.1060),
                    MapCoordinate(latitude = 35.1000, longitude = 129.1100),
                ),
            )

        assertNotNull(bounds)
        assertEquals(35.0940, bounds?.swLat ?: 0.0, 0.0)
        assertEquals(129.1000, bounds?.swLng ?: 0.0, 0.0)
        assertEquals(35.1060, bounds?.neLat ?: 0.0, 0.0)
        assertEquals(129.1100, bounds?.neLng ?: 0.0, 0.0)
    }
}
