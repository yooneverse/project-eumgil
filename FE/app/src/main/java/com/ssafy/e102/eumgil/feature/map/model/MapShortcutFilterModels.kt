package com.ssafy.e102.eumgil.feature.map.model

enum class MapShortcutFilterKey {
    TOILET,
    ELEVATOR,
    CHARGING_STATION,
    FOOD_CAFE,
    TOURIST_SPOT,
    ACCOMMODATION,
    HEALTHCARE,
    WELFARE,
    PUBLIC_OFFICE,
}

data class MapShortcutFilterChipState(
    val key: MapShortcutFilterKey,
    val isSelected: Boolean = false,
    val isEnabled: Boolean = true,
)

data class MapShortcutFilterRowState(
    val chips: List<MapShortcutFilterChipState> = emptyList(),
)
