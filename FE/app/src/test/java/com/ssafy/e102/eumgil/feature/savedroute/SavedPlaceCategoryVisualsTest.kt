package com.ssafy.e102.eumgil.feature.savedroute

import com.ssafy.e102.eumgil.R
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedPlaceCategoryVisualsTest {
    @Test
    fun `saved place category icons follow shared place icon mapping`() {
        assertEquals(R.drawable.ic_user_wheelchair_compact, savedPlaceCategoryIconRes("TOILET"))
        assertEquals(R.drawable.ic_place_elevator, savedPlaceCategoryIconRes("ELEVATOR"))
        assertEquals(R.drawable.ic_place_charging_station, savedPlaceCategoryIconRes("CHARGING_STATION"))
        assertEquals(R.drawable.ic_place_food_cafe, savedPlaceCategoryIconRes("FOOD_CAFE"))
        assertEquals(R.drawable.ic_place_tourist_spot, savedPlaceCategoryIconRes("TOURIST_SPOT"))
        assertEquals(R.drawable.ic_place_accommodation, savedPlaceCategoryIconRes("ACCOMMODATION"))
        assertEquals(R.drawable.ic_place_healthcare, savedPlaceCategoryIconRes("HEALTHCARE"))
        assertEquals(R.drawable.ic_place_welfare, savedPlaceCategoryIconRes("WELFARE"))
        assertEquals(R.drawable.ic_place_public_office, savedPlaceCategoryIconRes("PUBLIC_OFFICE"))
        assertEquals(R.drawable.ic_route_tactile_blocks, savedPlaceCategoryIconRes("BRAILLE_BLOCK"))
        assertEquals(R.drawable.ic_place_restaurant, savedPlaceCategoryIconRes("RESTAURANT"))
        assertEquals(R.drawable.ic_place_tourist_spot, savedPlaceCategoryIconRes("TOURIST_ATTRACTION"))
        assertEquals(R.drawable.ic_map_selected_pin_blue, savedPlaceCategoryIconRes("OTHER"))
        assertEquals(R.drawable.ic_map_selected_pin_blue, savedPlaceCategoryIconRes(null))
        assertEquals(R.drawable.ic_map_selected_pin_blue, savedPlaceCategoryIconRes("UNEXPECTED"))
    }
}
