package com.ssafy.e102.eumgil.core.model

data class PlaceDestination(
    val placeId: String,
    val name: String,
    val address: String? = null,
    val latitude: Double,
    val longitude: Double,
    val category: PlaceCategory? = null,
    val serverPlaceId: Long? = null,
    val provider: String? = null,
    val providerPlaceId: String? = null,
    val providerCategory: String? = null,
)

// Search, facility detail, and saved-place handoff all converge on the same minimal destination payload.
fun SearchResult.toPlaceDestination(): PlaceDestination =
    PlaceDestination(
        placeId = placeId,
        name = title,
        address = subtitle.takeIf { it.isNotBlank() },
        latitude = latitude,
        longitude = longitude,
        category = category,
        serverPlaceId = serverPlaceId?.toLongOrNull(),
        provider = bookmarkProvider(),
        providerPlaceId = bookmarkProviderPlaceId(),
        providerCategory = category?.name,
    )

fun SearchResult.toPlaceDestinationOrNull(): PlaceDestination? =
    if (hasValidCoordinate(latitude = latitude, longitude = longitude)) {
        toPlaceDestination()
    } else {
        null
    }

// 119 fixes the facility-detail handoff contract here so 200/214 can reuse it without branching by source.
fun FacilityDetailSeed.toPlaceDestination(): PlaceDestination =
    PlaceDestination(
        placeId = facilityId,
        name = name,
        address = address.takeIf { it.isNotBlank() },
        latitude = coordinate.latitude,
        longitude = coordinate.longitude,
        category = category.toPlaceCategory(),
        serverPlaceId = facilityId.toLongOrNull(),
    )

fun PlaceDestination.hasValidCoordinate(): Boolean =
    hasValidCoordinate(latitude = latitude, longitude = longitude)

fun PlaceDestination.toRouteWaypointOrNull(): RouteWaypoint? =
    if (hasValidCoordinate()) {
        toRouteWaypoint()
    } else {
        null
    }

private fun hasValidCoordinate(
    latitude: Double,
    longitude: Double,
): Boolean = latitude.isValidLatitude() && longitude.isValidLongitude()

private fun Double.isValidLatitude(): Boolean = isFinite() && this in -90.0..90.0

private fun Double.isValidLongitude(): Boolean = isFinite() && this in -180.0..180.0

private fun FacilityCategory.toPlaceCategory(): PlaceCategory =
    when (this) {
        FacilityCategory.TOILET -> PlaceCategory.TOILET
        FacilityCategory.ELEVATOR -> PlaceCategory.ELEVATOR
        FacilityCategory.CHARGING_STATION -> PlaceCategory.CHARGING_STATION
        FacilityCategory.FOOD_CAFE -> PlaceCategory.FOOD_CAFE
        FacilityCategory.TOURIST_SPOT -> PlaceCategory.TOURIST_SPOT
        FacilityCategory.ACCOMMODATION -> PlaceCategory.ACCOMMODATION
        FacilityCategory.HEALTHCARE -> PlaceCategory.HEALTHCARE
        FacilityCategory.WELFARE -> PlaceCategory.WELFARE
        FacilityCategory.PUBLIC_OFFICE -> PlaceCategory.PUBLIC_OFFICE
        FacilityCategory.BRAILLE_BLOCK -> PlaceCategory.BRAILLE_BLOCK
        FacilityCategory.RESTAURANT -> PlaceCategory.RESTAURANT
        FacilityCategory.TOURIST_ATTRACTION -> PlaceCategory.TOURIST_ATTRACTION
        FacilityCategory.OTHER -> PlaceCategory.OTHER
    }
