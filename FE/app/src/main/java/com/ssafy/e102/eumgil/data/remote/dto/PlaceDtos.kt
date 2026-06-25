package com.ssafy.e102.eumgil.data.remote.dto

data class PlacesBrowseDto(
    val places: List<PlaceSummaryDto>,
)

data class PlaceSummaryDto(
    val placeId: Long,
    val name: String,
    val category: String,
    val markerKind: String = "DEFAULT",
    val address: String?,
    val point: PlacePointDto,
    val accessibilityFeatures: List<PlaceAccessibilityFeatureDto>,
    val isBookmarked: Boolean,
)

data class PlaceDetailDto(
    val placeId: Long,
    val name: String,
    val category: String,
    val address: String?,
    val point: PlacePointDto,
    val providerPlaceId: String?,
    val accessibilityFeatures: List<PlaceAccessibilityFeatureDto>,
    val isBookmarked: Boolean,
    val phone: String? = null,
    val description: String?,
)

data class MapPlaceDetailDto(
    val bookmarkTargetId: String,
    val detailType: String,
    val placeId: Long?,
    val provider: String?,
    val providerPlaceId: String?,
    val name: String,
    val category: String?,
    val providerCategory: String?,
    val address: String?,
    val point: PlacePointDto,
    val accessibilityFeatures: List<PlaceAccessibilityFeatureDto>,
    val transitArrivals: List<PlaceTransitArrivalDto>,
    val isBookmarked: Boolean,
    val phone: String? = null,
    val description: String?,
)

data class PlacePointDto(
    val lat: Double,
    val lng: Double,
)

data class PlaceAccessibilityFeatureDto(
    val featureType: String,
    val isAvailable: Boolean,
)

data class PlaceTransitArrivalDto(
    val transitType: String,
    val routeName: String,
    val direction: String?,
    val remainingMinute: Int?,
    val isLowFloor: Boolean?,
    val source: String?,
)
