package com.ssafy.e102.eumgil.core.model

enum class PlaceCategory {
    TOILET,
    ELEVATOR,
    CHARGING_STATION,
    FOOD_CAFE,
    TOURIST_SPOT,
    ACCOMMODATION,
    HEALTHCARE,
    WELFARE,
    PUBLIC_OFFICE,
    BRAILLE_BLOCK,
    RESTAURANT,
    TOURIST_ATTRACTION,
    OTHER,
}

enum class PlaceMarkerKind {
    DEFAULT,
    BUS_STOP,
    SUBWAY_STATION,
}

enum class PlaceFeatureType {
    ACCESSIBLE_ENTRANCE,
    ELEVATOR,
    ACCESSIBLE_TOILET,
    ACCESSIBLE_PARKING,
    CHARGING_STATION,
    ACCESSIBLE_ROOM,
    GUIDANCE_FACILITY,
}

data class PlaceFeatureAvailability(
    val featureType: PlaceFeatureType,
    val isAvailable: Boolean,
)

data class PlaceQuery(
    val keyword: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Int = DEFAULT_PLACE_BROWSE_RADIUS_METERS,
    val categories: Set<PlaceCategory> = emptySet(),
    val featureTypes: Set<PlaceFeatureType> = emptySet(),
)

data class PlaceSummary(
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val category: PlaceCategory,
    val markerKind: PlaceMarkerKind = PlaceMarkerKind.DEFAULT,
    val features: List<PlaceFeatureAvailability> = emptyList(),
    val isBookmarked: Boolean = false,
    val accessibilityTags: List<String> = emptyList(),
)

data class PlaceDetail(
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val category: PlaceCategory,
    val features: List<PlaceFeatureAvailability> = emptyList(),
    val isBookmarked: Boolean = false,
    val accessibilityTags: List<String> = emptyList(),
    val providerPlaceId: String? = null,
    val phoneNumber: String? = null,
    val description: String? = null,
)

enum class MapPlaceClickType {
    POI,
    ADDRESS,
}

enum class MapPlaceDetailType {
    INTERNAL_PLACE,
    EXTERNAL_POI,
    EXTERNAL_ADDRESS,
}

data class MapPlaceDetailRequest(
    val latitude: Double,
    val longitude: Double,
    val clickType: MapPlaceClickType,
    val provider: String? = null,
    val providerPlaceId: String? = null,
    val nameHint: String? = null,
)

data class PlaceTransitArrival(
    val transitType: String,
    val routeName: String,
    val direction: String? = null,
    val remainingMinute: Int? = null,
    val isLowFloor: Boolean? = null,
    val source: String? = null,
)

data class MapTappedPlaceDetail(
    val bookmarkTargetId: String,
    val detailType: MapPlaceDetailType,
    val placeId: String?,
    val provider: String?,
    val providerPlaceId: String?,
    val name: String,
    val category: PlaceCategory?,
    val providerCategory: String?,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val features: List<PlaceFeatureAvailability> = emptyList(),
    val isBookmarked: Boolean = false,
    val accessibilityTags: List<String> = emptyList(),
    val phoneNumber: String? = null,
    val transitArrivals: List<PlaceTransitArrival> = emptyList(),
    val description: String? = null,
)

private const val DEFAULT_PLACE_BROWSE_RADIUS_METERS = 1_000
