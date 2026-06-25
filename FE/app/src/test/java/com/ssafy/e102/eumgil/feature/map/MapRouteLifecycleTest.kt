package com.ssafy.e102.eumgil.feature.map

import androidx.lifecycle.Lifecycle
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapRouteLifecycleTest {
    @Test
    fun `map route starts immediately when lifecycle is already resumed`() {
        assertTrue(shouldStartMapRouteImmediately(Lifecycle.State.RESUMED))
        assertTrue(shouldStartMapRouteImmediately(Lifecycle.State.STARTED))
    }

    @Test
    fun `map route waits for on start when lifecycle is not started yet`() {
        assertFalse(shouldStartMapRouteImmediately(Lifecycle.State.CREATED))
        assertFalse(shouldStartMapRouteImmediately(Lifecycle.State.INITIALIZED))
    }

    @Test
    fun `map route consumes global overlay dismiss request by closing detail and map voice search`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/MapRoute.kt")
                .readText()
        val dismissRequestEffect =
            source
                .substringAfter("facilityDetailDismissRequestId")
                .substringBefore("MapScreen(")

        assertTrue(
            "MapRoute should consume the savedStateHandle request id once.",
            dismissRequestEffect.contains("onFacilityDetailDismissRequestConsumed(facilityDetailDismissRequestId)"),
        )
        assertTrue(
            "MapRoute should dismiss the facility detail sheet when the global overlay opens.",
            dismissRequestEffect.contains("MapUiAction.FacilityDetailDismissed"),
        )
        assertTrue(
            "MapRoute should also close the legacy map voiceSearch sheet so it cannot remain behind the global overlay.",
            dismissRequestEffect.contains("MapUiAction.VoiceSearchDismissed"),
        )
    }
}
