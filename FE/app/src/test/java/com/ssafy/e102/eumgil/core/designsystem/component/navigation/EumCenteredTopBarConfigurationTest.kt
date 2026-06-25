package com.ssafy.e102.eumgil.core.designsystem.component.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class EumCenteredTopBarConfigurationTest {
    @Test
    fun `centered top bar uses flat layout without separator`() {
        val spec = centeredTopBarLayoutSpec()

        assertEquals(8, spec.horizontalPaddingDp)
        assertEquals(56, spec.minHeightDp)
        assertEquals(0, spec.shadowElevationDp)
        assertEquals(0, spec.tonalElevationDp)
    }
}
