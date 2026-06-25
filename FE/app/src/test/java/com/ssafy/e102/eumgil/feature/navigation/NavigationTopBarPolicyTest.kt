package com.ssafy.e102.eumgil.feature.navigation

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTopBarPolicyTest {
    @Test
    fun `navigation top bar removes back button and bookmark action`() {
        val policy = navigationTopBarPolicy()

        assertFalse(policy.showBackButton)
        assertFalse(policy.showBookmarkAction)
        assertEquals(FontWeight.SemiBold, policy.titleFontWeight)
    }
}
