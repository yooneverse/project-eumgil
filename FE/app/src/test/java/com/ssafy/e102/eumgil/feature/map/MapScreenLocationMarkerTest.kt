package com.ssafy.e102.eumgil.feature.map

import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapScreenLocationMarkerTest {
    @Test
    fun `ready location status exposes current location marker regardless of camera source`() {
        val currentLocation = MapCoordinate(latitude = 35.1796, longitude = 129.0756)

        assertEquals(
            currentLocation,
            resolveCurrentLocationMarker(
                MapLocationStatus.Ready(
                    location = currentLocation,
                    accuracyMeters = 8f,
                ),
            ),
        )
    }

    @Test
    fun `non ready location statuses do not expose current location marker`() {
        assertNull(resolveCurrentLocationMarker(MapLocationStatus.PermissionDenied))
        assertNull(resolveCurrentLocationMarker(MapLocationStatus.Loading))
        assertNull(
            resolveCurrentLocationMarker(
                MapLocationStatus.Unavailable(
                    MapLocationUnavailableReason.CURRENT_LOCATION_UNAVAILABLE,
                ),
            ),
        )
    }
}
