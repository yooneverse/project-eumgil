package com.ssafy.e102.eumgil.feature.lowvision

import com.ssafy.e102.eumgil.core.location.LocationSnapshot
import com.ssafy.e102.eumgil.core.location.isFreshCurrentLocation
import com.ssafy.e102.eumgil.core.model.PlaceCategory
import com.ssafy.e102.eumgil.core.model.PlaceQuery
import com.ssafy.e102.eumgil.core.model.PlaceSummary
import com.ssafy.e102.eumgil.core.model.RecentDestination
import com.ssafy.e102.eumgil.core.model.RecentSearch
import com.ssafy.e102.eumgil.core.model.SearchPage
import com.ssafy.e102.eumgil.core.model.SearchQuery
import com.ssafy.e102.eumgil.core.model.SearchResult
import com.ssafy.e102.eumgil.core.model.SearchSortOption
import com.ssafy.e102.eumgil.core.model.SearchVoiceAnalysis
import com.ssafy.e102.eumgil.core.model.SearchVoiceMode
import com.ssafy.e102.eumgil.data.repository.PlacesRepository
import com.ssafy.e102.eumgil.data.repository.SearchRepository
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal class LowVisionSearchRepository(
    private val delegate: SearchRepository,
    private val placesRepository: PlacesRepository? = null,
    private val currentLocationProvider: () -> LocationSnapshot? = { null },
) : SearchRepository {
    override suspend fun search(query: SearchQuery): List<SearchResult> = searchPage(query).results

    override suspend fun searchPage(query: SearchQuery): SearchPage {
        val categoryFilters = query.normalizedKeyword.toLowVisionCategoryFilters()
        val queryAnchor = query.toCategorySearchAnchorOrNull()
        val knownAnchor = currentLocationProvider().toCategorySearchAnchorOrNull(requireFresh = categoryFilters == null)
        val anchor = queryAnchor ?: knownAnchor

        if (categoryFilters != null && query.cursor.isNullOrBlank() && placesRepository != null) {
            if (anchor == null) {
                throw LowVisionCurrentLocationRequiredException()
            }

            val places =
                placesRepository.getPlaces(
                    PlaceQuery(
                        latitude = anchor.latitude,
                        longitude = anchor.longitude,
                        radiusMeters = LOW_VISION_CATEGORY_SEARCH_RADIUS_METERS,
                        categories = categoryFilters,
                    ),
                )
            return SearchPage(
                results =
                    places
                        .map(PlaceSummary::toSearchResult)
                        .sortedByAnchor(anchor),
            )
        }

        val page = delegate.searchPage(query)
        return if (query.sortOption == SearchSortOption.DISTANCE) {
            page.sortedByAnchor(anchor)
        } else {
            page
        }
    }

    override suspend fun analyzeVoiceSearch(
        text: String,
        mode: SearchVoiceMode,
    ): SearchVoiceAnalysis = delegate.analyzeVoiceSearch(text = text, mode = mode)

    override suspend fun getRecentSearches(): List<RecentSearch> = delegate.getRecentSearches()

    override suspend fun saveRecentSearch(keyword: String) {
        // Low-vision flows do not surface recent-search UI, so category/voice queries should not pollute shared history.
        Unit
    }

    override suspend fun getRecentDestinations(): List<RecentDestination> = delegate.getRecentDestinations()

    override suspend fun saveRecentDestination(destination: RecentDestination) {
        delegate.saveRecentDestination(destination)
    }
}

private data class CategorySearchAnchor(
    val latitude: Double,
    val longitude: Double,
)

private fun SearchQuery.toCategorySearchAnchorOrNull(): CategorySearchAnchor? {
    val latitude = this.latitude ?: return null
    val longitude = this.longitude ?: return null
    return CategorySearchAnchor(latitude = latitude, longitude = longitude)
}

private fun LocationSnapshot?.toCategorySearchAnchorOrNull(requireFresh: Boolean): CategorySearchAnchor? {
    val snapshot = this ?: return null
    if (requireFresh && !snapshot.isFreshCurrentLocation()) return null
    return CategorySearchAnchor(latitude = snapshot.latitude, longitude = snapshot.longitude)
}

private fun String.toLowVisionCategoryFilters(): Set<PlaceCategory>? =
    when (trim()) {
        "\uC74C\uC2DD\uC810" ->
            setOf(PlaceCategory.FOOD_CAFE, PlaceCategory.RESTAURANT)
        "\uAD00\uAD11\uC9C0" ->
            setOf(PlaceCategory.TOURIST_SPOT, PlaceCategory.TOURIST_ATTRACTION)
        "\uC219\uBC15\uC2DC\uC124" ->
            setOf(PlaceCategory.ACCOMMODATION)
        "\uBCD1\uC6D0" ->
            setOf(PlaceCategory.HEALTHCARE)
        "\uBCF5\uC9C0\uAD00" ->
            setOf(PlaceCategory.WELFARE)
        "\uAD00\uACF5\uC11C" ->
            setOf(PlaceCategory.PUBLIC_OFFICE)
        else -> null
    }

private fun PlaceSummary.toSearchResult(): SearchResult =
    SearchResult(
        placeId = placeId,
        title = name,
        subtitle = address,
        latitude = latitude,
        longitude = longitude,
        category = category,
        serverPlaceId = placeId,
        accessibilityTagKeys = accessibilityTags,
    )

internal const val LOW_VISION_CURRENT_LOCATION_REQUIRED_MESSAGE: String =
    "\uD604\uC7AC \uC704\uCE58\uB97C \uD655\uC778\uD560 \uC218 \uC5C6\uC5B4\uC694. \uC704\uCE58 \uAD8C\uD55C\uACFC \uC704\uCE58 \uC11C\uBE44\uC2A4\uB97C \uD655\uC778\uD55C \uB4A4 \uB2E4\uC2DC \uC2DC\uB3C4\uD574 \uC8FC\uC138\uC694."

internal class LowVisionCurrentLocationRequiredException :
    IllegalStateException(LOW_VISION_CURRENT_LOCATION_REQUIRED_MESSAGE)

private const val LOW_VISION_CATEGORY_SEARCH_RADIUS_METERS = 3_000

private fun SearchPage.sortedByAnchor(anchor: CategorySearchAnchor?): SearchPage =
    copy(results = results.sortedByAnchor(anchor))

private fun List<SearchResult>.sortedByAnchor(anchor: CategorySearchAnchor?): List<SearchResult> {
    val current = anchor ?: return this
    return sortedBy { result ->
        haversineDistanceMeters(
            startLatitude = current.latitude,
            startLongitude = current.longitude,
            endLatitude = result.latitude,
            endLongitude = result.longitude,
        )
    }
}

private fun haversineDistanceMeters(
    startLatitude: Double,
    startLongitude: Double,
    endLatitude: Double,
    endLongitude: Double,
): Double {
    val earthRadiusMeters = 6_371_000.0
    val dLat = Math.toRadians(endLatitude - startLatitude)
    val dLng = Math.toRadians(endLongitude - startLongitude)
    val startLatitudeRadians = Math.toRadians(startLatitude)
    val endLatitudeRadians = Math.toRadians(endLatitude)
    val haversine =
        sin(dLat / 2).pow(2) +
            cos(startLatitudeRadians) * cos(endLatitudeRadians) * sin(dLng / 2).pow(2)
    return 2 * earthRadiusMeters * atan2(sqrt(haversine), sqrt(1 - haversine))
}
