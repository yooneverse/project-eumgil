package com.ssafy.e102.eumgil.core.model

import com.ssafy.e102.eumgil.core.location.ANDROID_GEOCODER_PROVIDER

data class SearchQuery(
    val keyword: String,
    val limit: Int = DEFAULT_LIMIT,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Int? = null,
    val cursor: String? = null,
    val sortOption: SearchSortOption = SearchSortOption.RELEVANCE,
) {
    val normalizedKeyword: String
        get() = keyword.trim()

    companion object {
        const val DEFAULT_LIMIT: Int = 15
    }
}

enum class SearchSortOption(
    val apiValue: String,
) {
    RELEVANCE(apiValue = "relevance"),
    DISTANCE(apiValue = "distance"),
}

data class SearchResult(
    val placeId: String,
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val category: PlaceCategory? = null,
    val serverPlaceId: String? = placeId,
    val provider: String? = null,
    val providerPlaceId: String? = null,
    val accessibilityTagKeys: List<String> = emptyList(),
    val matched: Boolean = true,
    val distanceMeters: Int? = null,
) {
    val displayPlaceId: String
        get() = serverPlaceId ?: providerPlaceId ?: placeId

    val isVerifiedPlace: Boolean
        get() = matched && !serverPlaceId.isNullOrBlank()
}

data class SearchPage(
    val results: List<SearchResult>,
    val nextCursor: String? = null,
    val hasNext: Boolean = false,
    val size: Int = results.size,
    val totalElements: Long? = null,
)

enum class SearchVoiceMode {
    MOBILITY_IMPAIRED,
    LOW_VISION,
}

enum class SearchVoiceIntent {
    PLACE_SEARCH,
    UNKNOWN,
}

data class SearchVoiceAnalysis(
    val intent: SearchVoiceIntent,
    val placeName: String? = null,
    val confirmed: Boolean? = null,
    val confirmationMessage: String? = null,
)

data class RecentSearch(
    val keyword: String,
    val searchedAtMillis: Long = System.currentTimeMillis(),
)

data class RecentDestination(
    val placeId: String,
    val name: String,
    val address: String? = null,
    val latitude: Double,
    val longitude: Double,
    val category: PlaceCategory? = null,
    val accessibilityTagKeys: List<String> = emptyList(),
    val searchedAtMillis: Long = System.currentTimeMillis(),
)

fun RecentDestination.toPlaceDestination(): PlaceDestination =
    PlaceDestination(
        placeId = placeId,
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
        category = category,
    )

fun PlaceDestination.toRecentDestination(): RecentDestination =
    RecentDestination(
        placeId = placeId,
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
        category = category,
    )

fun SearchResult.bookmarkProvider(): String? =
    when {
        isAddressSearchFallback() -> "KAKAO"
        !provider.isNullOrBlank() -> provider
        !providerPlaceId.isNullOrBlank() -> "KAKAO"
        else -> null
    }

fun SearchResult.bookmarkProviderPlaceId(): String? =
    providerPlaceId?.takeIf { !isAddressSearchFallback() && it.isNotBlank() }

fun SearchResult.isAddressSearchFallback(): Boolean =
    provider?.equals(ANDROID_GEOCODER_PROVIDER, ignoreCase = true) == true
