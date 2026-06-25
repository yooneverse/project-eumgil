package com.ssafy.e102.eumgil.data.remote.mapper

import com.ssafy.e102.eumgil.core.model.PlaceCategory
import com.ssafy.e102.eumgil.core.model.PlaceFeatureAvailability
import com.ssafy.e102.eumgil.core.model.PlaceFeatureType
import com.ssafy.e102.eumgil.core.model.PlaceMarkerKind
import com.ssafy.e102.eumgil.data.remote.dto.PlaceAccessibilityFeatureDto

internal object PlaceApiFieldMapper {
    fun toPlaceCategory(value: String): PlaceCategory = toPlaceCategoryOrNull(value) ?: PlaceCategory.OTHER

    fun toPlaceMarkerKind(value: String?): PlaceMarkerKind =
        when (value?.trim()?.uppercase()) {
            "BUS_STOP" -> PlaceMarkerKind.BUS_STOP
            "SUBWAY_STATION" -> PlaceMarkerKind.SUBWAY_STATION
            else -> PlaceMarkerKind.DEFAULT
        }

    fun toPlaceCategoryOrNull(value: String?): PlaceCategory? =
        when (value?.trim()?.uppercase()) {
            null,
            "",
            -> null
            "TOILET" -> PlaceCategory.TOILET
            "ELEVATOR" -> PlaceCategory.ELEVATOR
            "CHARGING_STATION" -> PlaceCategory.CHARGING_STATION
            "FOOD_CAFE" -> PlaceCategory.FOOD_CAFE
            "TOURIST_SPOT" -> PlaceCategory.TOURIST_SPOT
            "ACCOMMODATION" -> PlaceCategory.ACCOMMODATION
            "HEALTHCARE" -> PlaceCategory.HEALTHCARE
            "WELFARE" -> PlaceCategory.WELFARE
            "PUBLIC_OFFICE" -> PlaceCategory.PUBLIC_OFFICE
            "BRAILLE_BLOCK" -> PlaceCategory.BRAILLE_BLOCK
            "RESTAURANT" -> PlaceCategory.RESTAURANT
            "TOURIST_ATTRACTION" -> PlaceCategory.TOURIST_ATTRACTION
            "ETC",
            "OTHER",
            -> PlaceCategory.OTHER
            else -> PlaceCategory.OTHER
        }

    fun toPlaceFeatureAvailabilities(features: Iterable<PlaceAccessibilityFeatureDto>): List<PlaceFeatureAvailability> =
        features.mapNotNull { feature ->
            val featureType = toPlaceFeatureTypeOrNull(feature.featureType) ?: return@mapNotNull null
            PlaceFeatureAvailability(
                featureType = featureType,
                isAvailable = feature.isAvailable,
            )
        }

    fun toAccessibilityTagKeys(features: Iterable<PlaceFeatureAvailability>): List<String> =
        features.mapNotNull { feature ->
            if (!feature.isAvailable) {
                null
            } else {
                when (feature.featureType) {
                    PlaceFeatureType.ACCESSIBLE_ENTRANCE -> "step-free-entrance"
                    PlaceFeatureType.ELEVATOR -> "elevator"
                    PlaceFeatureType.ACCESSIBLE_TOILET -> "accessible-toilet"
                    PlaceFeatureType.ACCESSIBLE_PARKING -> "accessible-parking"
                    PlaceFeatureType.CHARGING_STATION -> "charging-station"
                    PlaceFeatureType.ACCESSIBLE_ROOM -> "accessible-room"
                    PlaceFeatureType.GUIDANCE_FACILITY -> "guidance-facility"
                }
            }
        }.distinct()

    private fun toPlaceFeatureTypeOrNull(value: String): PlaceFeatureType? =
        when (value.trim()) {
            "accessibleEntrance" -> PlaceFeatureType.ACCESSIBLE_ENTRANCE
            "elevator" -> PlaceFeatureType.ELEVATOR
            "accessibleToilet" -> PlaceFeatureType.ACCESSIBLE_TOILET
            "accessibleParking" -> PlaceFeatureType.ACCESSIBLE_PARKING
            "chargingStation" -> PlaceFeatureType.CHARGING_STATION
            "accessibleRoom" -> PlaceFeatureType.ACCESSIBLE_ROOM
            "guidanceFacility" -> PlaceFeatureType.GUIDANCE_FACILITY
            else -> null
        }
}
