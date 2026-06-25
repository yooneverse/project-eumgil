package com.ssafy.e102.eumgil.data.local.datasource

import com.ssafy.e102.eumgil.core.model.PlaceCategory
import com.ssafy.e102.eumgil.core.model.PlaceDetail
import com.ssafy.e102.eumgil.core.model.PlaceQuery
import com.ssafy.e102.eumgil.core.model.PlaceSummary
import java.util.concurrent.ConcurrentHashMap

class PlacesLocalDataSource(
    private val currentAccountScopeProvider: suspend () -> String? = { null },
) {
    private val placeCacheByQuery = ConcurrentHashMap<String, List<PlaceSummary>>()
    private val placeDetailCacheById = ConcurrentHashMap<String, PlaceDetail>()

    suspend fun getCachedPlaces(query: PlaceQuery): List<PlaceSummary> =
        placeCacheByQuery[scopedQueryKey(query)].orEmpty()

    suspend fun updateCachedPlaces(
        query: PlaceQuery,
        places: List<PlaceSummary>,
    ) {
        placeCacheByQuery[scopedQueryKey(query)] = places
    }

    suspend fun getCachedPlaceDetail(placeId: String): PlaceDetail? = placeDetailCacheById[scopedPlaceDetailKey(placeId)]

    suspend fun updateCachedPlaceDetail(placeDetail: PlaceDetail) {
        placeDetailCacheById[scopedPlaceDetailKey(placeDetail.placeId)] = placeDetail
    }

    suspend fun clearCurrentAccountCache() {
        val scopePrefix = scopePrefix(resolveAccountScopeKey())
        placeCacheByQuery.keys
            .filter { cacheKey -> cacheKey.startsWith(scopePrefix) }
            .forEach(placeCacheByQuery::remove)
        placeDetailCacheById.keys
            .filter { cacheKey -> cacheKey.startsWith(scopePrefix) }
            .forEach(placeDetailCacheById::remove)
    }

    private fun PlaceQuery.cacheKey(): String =
        listOf(
            keyword?.trim().orEmpty(),
            latitude?.toString().orEmpty(),
            longitude?.toString().orEmpty(),
            radiusMeters.toString(),
            categories
                .sortedBy(PlaceCategory::name)
                .joinToString(separator = ","),
            featureTypes
                .sortedBy { featureType -> featureType.name }
                .joinToString(separator = ","),
        ).joinToString(separator = "|")

    private suspend fun scopedQueryKey(query: PlaceQuery): String = scopePrefix(resolveAccountScopeKey()) + query.cacheKey()

    private suspend fun scopedPlaceDetailKey(placeId: String): String = scopePrefix(resolveAccountScopeKey()) + placeId

    private suspend fun resolveAccountScopeKey(): String = currentAccountScopeProvider().orEmpty().ifBlank { DEFAULT_ACCOUNT_SCOPE_KEY }

    private fun scopePrefix(accountScopeKey: String): String = "$accountScopeKey|"

    private companion object {
        private const val DEFAULT_ACCOUNT_SCOPE_KEY = "anonymous"
    }
}
