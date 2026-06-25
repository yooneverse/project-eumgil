package com.ssafy.e102.eumgil.feature.savedroute

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ssafy.e102.eumgil.R

@DrawableRes
internal fun savedPlaceCategoryIconRes(category: String?): Int =
    when (category) {
        "TOILET" -> R.drawable.ic_user_wheelchair_compact
        "ELEVATOR" -> R.drawable.ic_place_elevator
        "CHARGING_STATION" -> R.drawable.ic_place_charging_station
        "FOOD_CAFE" -> R.drawable.ic_place_food_cafe
        "TOURIST_SPOT" -> R.drawable.ic_place_tourist_spot
        "ACCOMMODATION" -> R.drawable.ic_place_accommodation
        "HEALTHCARE" -> R.drawable.ic_place_healthcare
        "WELFARE" -> R.drawable.ic_place_welfare
        "PUBLIC_OFFICE" -> R.drawable.ic_place_public_office
        "BRAILLE_BLOCK" -> R.drawable.ic_route_tactile_blocks
        "RESTAURANT" -> R.drawable.ic_place_restaurant
        "TOURIST_ATTRACTION" -> R.drawable.ic_place_tourist_spot
        "OTHER", null -> R.drawable.ic_map_selected_pin_blue
        else -> R.drawable.ic_map_selected_pin_blue
    }

@Composable
internal fun savedPlaceCategoryLabel(category: String?): String =
    when (category) {
        "FOOD_CAFE" -> "식당·카페"
        "TOURIST_SPOT" -> "무장애 관광지"
        "ACCOMMODATION" -> "숙박"
        "HEALTHCARE" -> "병원"
        "WELFARE" -> "복지관"
        "PUBLIC_OFFICE" -> "관공서"
        "RESTAURANT" -> stringResource(id = R.string.map_filter_category_restaurant)
        "TOURIST_ATTRACTION" -> stringResource(id = R.string.map_filter_category_tourist_attraction)
        "TOILET" -> stringResource(id = R.string.map_filter_category_toilet)
        "ELEVATOR" -> stringResource(id = R.string.map_filter_category_elevator)
        "CHARGING_STATION" -> stringResource(id = R.string.map_filter_category_charging_station)
        "BRAILLE_BLOCK" -> stringResource(id = R.string.map_filter_category_braille_block)
        else -> stringResource(id = R.string.map_filter_category_other)
    }
