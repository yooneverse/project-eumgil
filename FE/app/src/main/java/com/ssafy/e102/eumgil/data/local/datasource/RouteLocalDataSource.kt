package com.ssafy.e102.eumgil.data.local.datasource

import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.RouteSearchData
import com.ssafy.e102.eumgil.core.model.RouteSearchQuery
import com.ssafy.e102.eumgil.core.model.RouteWaypoint
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class RouteLocalDataSource {
    private val searchDataByQuery = ConcurrentHashMap<String, RouteSearchData>()

    suspend fun getCachedSearchData(query: RouteSearchQuery): RouteSearchData? =
        searchDataByQuery[query.cacheKey()]
            ?: findNearbyCurrentLocationCache(query)

    suspend fun updateCachedSearchData(
        query: RouteSearchQuery,
        searchData: RouteSearchData,
    ) {
        searchDataByQuery[query.cacheKey()] = searchData
    }

    private fun RouteSearchQuery.cacheKey(): String =
        listOf(
            origin.cacheKey(),
            destination.cacheKey(),
            requestedOptions.joinToString(separator = ",") { routeOption -> routeOption.name },
        ).joinToString(separator = "|")

    private fun findNearbyCurrentLocationCache(query: RouteSearchQuery): RouteSearchData? {
        if (!query.origin.isCurrentLocationOrigin()) {
            return null
        }

        // Keep manual waypoints exact, but absorb small GPS jitter for synthetic current-location origins.
        return searchDataByQuery.values
            .asSequence()
            .filter { cachedSearchData ->
                cachedSearchData.query.origin.isCurrentLocationOrigin() &&
                    cachedSearchData.query.destination.cacheKey() == query.destination.cacheKey() &&
                    cachedSearchData.query.requestedOptions == query.requestedOptions
            }.map { cachedSearchData ->
                cachedSearchData to haversineDistanceMeters(
                    a = cachedSearchData.query.origin.coordinate,
                    b = query.origin.coordinate,
                )
            }.filter { (_, distanceMeters) ->
                distanceMeters <= CURRENT_LOCATION_CACHE_REUSE_RADIUS_METERS
            }.minByOrNull { (_, distanceMeters) -> distanceMeters }
            ?.first
    }

    private fun RouteWaypoint.cacheKey(): String =
        listOf(
            placeId.normalized(),
            name.normalized(),
            address.normalized(),
            coordinate.latitude.toCacheCoordinate(),
            coordinate.longitude.toCacheCoordinate(),
        ).joinToString(separator = "~")

    private fun String?.normalized(): String = this?.trim()?.lowercase().orEmpty()

    private fun Double.toCacheCoordinate(): String = String.format(Locale.US, "%.6f", this)

    private fun RouteWaypoint.isCurrentLocationOrigin(): Boolean =
        placeId.normalized() == CURRENT_LOCATION_ORIGIN_PLACE_ID

    private fun haversineDistanceMeters(
        a: GeoCoordinate,
        b: GeoCoordinate,
    ): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val startLatitudeRadians = Math.toRadians(a.latitude)
        val endLatitudeRadians = Math.toRadians(b.latitude)
        val haversine =
            Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(startLatitudeRadians) *
                Math.cos(endLatitudeRadians) *
                Math.sin(dLon / 2) *
                Math.sin(dLon / 2)
        val arc = 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine))
        return EARTH_RADIUS_METERS * arc
    }

    private companion object {
        const val CURRENT_LOCATION_ORIGIN_PLACE_ID = "route-origin-current-location"
        const val CURRENT_LOCATION_CACHE_REUSE_RADIUS_METERS = 10.0
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
