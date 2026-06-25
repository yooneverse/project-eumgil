package com.ssafy.e102.eumgil.app.navigation

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavHostContentWindowInsetsTest {
    private val density = Density(density = 1f)

    @Test
    fun `app nav host scaffold does not add its own system bar inset`() {
        assertEquals(0, AppNavHostContentWindowInsets.getLeft(density, LayoutDirection.Ltr))
        assertEquals(0, AppNavHostContentWindowInsets.getTop(density))
        assertEquals(0, AppNavHostContentWindowInsets.getRight(density, LayoutDirection.Ltr))
        assertEquals(0, AppNavHostContentWindowInsets.getBottom(density))
    }
}
