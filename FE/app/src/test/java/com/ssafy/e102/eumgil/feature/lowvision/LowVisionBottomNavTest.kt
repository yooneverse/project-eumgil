package com.ssafy.e102.eumgil.feature.lowvision

import androidx.compose.ui.unit.dp
import com.ssafy.e102.eumgil.feature.lowvision.component.LowVisionBottomNavDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

class LowVisionBottomNavTest {
    @Test
    fun `bottom nav divides the full width into four large tap areas`() {
        assertEquals(4, LowVisionBottomNavDefaults.itemCount)
        assertEquals(1f, LowVisionBottomNavDefaults.itemWeight)
        assertEquals(80.dp, LowVisionBottomNavDefaults.height)
    }

    @Test
    fun `bottom nav keeps tab height separate from the system safe zone`() {
        assertEquals(80.dp, LowVisionBottomNavDefaults.height)
        assertEquals(true, LowVisionBottomNavDefaults.reservesNavigationBarSafeZone)
    }
}
