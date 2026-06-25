package com.ssafy.e102.eumgil.feature.route

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTopBarPolicyTest {
    @Test
    fun `route top bar keeps back button with centered title`() {
        val policy = routeScreenTopBarPolicy()

        assertTrue(policy.showBackButton)
        assertEquals(FontWeight.SemiBold, policy.titleFontWeight)
    }
}
