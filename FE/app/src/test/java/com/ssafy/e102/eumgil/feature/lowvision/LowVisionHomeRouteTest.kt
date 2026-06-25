package com.ssafy.e102.eumgil.feature.lowvision

import com.ssafy.e102.eumgil.core.location.LocationGrantAccuracy
import com.ssafy.e102.eumgil.core.location.LocationPermissionState
import com.ssafy.e102.eumgil.core.location.LocationPermissionUnavailableReason
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LowVisionHomeRouteTest {
    @Test
    fun `low vision home requests location permission before refreshing denied location`() {
        assertTrue(
            shouldRequestLowVisionHomeLocationPermission(LocationPermissionState.Denied),
        )
        assertFalse(
            shouldRequestLowVisionHomeLocationPermission(
                LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE),
            ),
        )
        assertFalse(
            shouldRequestLowVisionHomeLocationPermission(
                LocationPermissionState.Unavailable(
                    LocationPermissionUnavailableReason.LOCATION_SERVICES_DISABLED,
                ),
            ),
        )
    }
}
