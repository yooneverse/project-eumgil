package com.ssafy.e102.eumgil.data.remote.dto

data class PlacesSearchDto(
    val places: List<SearchPlaceDto>,
    val nextCursor: String?,
    val size: Int,
    val totalElements: Long,
    val hasNext: Boolean,
)

data class SearchPlaceDto(
    val placeId: Long?,
    val provider: String,
    val providerPlaceId: String?,
    val name: String,
    val category: String?,
    val address: String?,
    val distanceMeter: Int?,
    val point: PlacePointDto,
    val accessibilityFeatures: List<PlaceAccessibilityFeatureDto>,
    val matched: Boolean,
)

data class VoiceSearchAnalysisDto(
    val intent: String,
    val placeName: String?,
    val confirmed: Boolean?,
    val confirmationMessage: String?,
)
