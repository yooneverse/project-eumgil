package com.ssafy.e102.eumgil.core.designsystem.component.place

import com.ssafy.e102.eumgil.R
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceListCardDefaultsTest {
    @Test
    fun `place list card actions put navigation before bookmark actions`() {
        assertEquals(
            listOf(PlaceListCardAction.Navigate, PlaceListCardAction.Bookmark),
            PlaceListCardDefaults.actionOrder,
        )
    }

    @Test
    fun `place list card uses filled bookmark asset`() {
        assertEquals(R.drawable.ic_nav_bookmark_selected, PlaceListCardDefaults.bookmarkIconRes)
        assertEquals(R.drawable.ic_nav_route, PlaceListCardDefaults.routeIconRes)
    }
}
