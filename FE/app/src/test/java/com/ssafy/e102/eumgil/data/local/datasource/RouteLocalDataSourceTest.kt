package com.ssafy.e102.eumgil.data.local.datasource

import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.core.model.RouteSearchData
import com.ssafy.e102.eumgil.core.model.RouteSearchQuery
import com.ssafy.e102.eumgil.core.model.RouteSearchResult
import com.ssafy.e102.eumgil.core.model.RouteSearchSource
import com.ssafy.e102.eumgil.core.model.RouteWaypoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteLocalDataSourceTest {
    @Test
    fun `current location origin reuses cached route within nearby radius`() =
        runBlocking {
            val localDataSource = RouteLocalDataSource()
            val cachedQuery =
                routeQuery(
                    originPlaceId = CURRENT_LOCATION_ORIGIN_PLACE_ID,
                    originCoordinate = GeoCoordinate(latitude = 35.1796, longitude = 129.0756),
                )
            val cachedSearchData = routeSearchData(query = cachedQuery)
            val nearbyQuery =
                routeQuery(
                    originPlaceId = CURRENT_LOCATION_ORIGIN_PLACE_ID,
                    originCoordinate = GeoCoordinate(latitude = 35.17966, longitude = 129.0756),
                )

            localDataSource.updateCachedSearchData(query = cachedQuery, searchData = cachedSearchData)

            assertEquals(cachedSearchData, localDataSource.getCachedSearchData(nearbyQuery))
        }

    @Test
    fun `manual origin keeps exact cache matching even when coordinates are nearby`() =
        runBlocking {
            val localDataSource = RouteLocalDataSource()
            val cachedQuery =
                routeQuery(
                    originPlaceId = "manual-origin",
                    originCoordinate = GeoCoordinate(latitude = 35.1796, longitude = 129.0756),
                )
            val cachedSearchData = routeSearchData(query = cachedQuery)
            val nearbyQuery =
                routeQuery(
                    originPlaceId = "manual-origin",
                    originCoordinate = GeoCoordinate(latitude = 35.17966, longitude = 129.0756),
                )

            localDataSource.updateCachedSearchData(query = cachedQuery, searchData = cachedSearchData)

            assertNull(localDataSource.getCachedSearchData(nearbyQuery))
        }

    @Test
    fun `current location origin does not reuse cached route beyond nearby radius`() =
        runBlocking {
            val localDataSource = RouteLocalDataSource()
            val cachedQuery =
                routeQuery(
                    originPlaceId = CURRENT_LOCATION_ORIGIN_PLACE_ID,
                    originCoordinate = GeoCoordinate(latitude = 35.1796, longitude = 129.0756),
                )
            val cachedSearchData = routeSearchData(query = cachedQuery)
            val distantQuery =
                routeQuery(
                    originPlaceId = CURRENT_LOCATION_ORIGIN_PLACE_ID,
                    originCoordinate = GeoCoordinate(latitude = 35.1799, longitude = 129.0756),
                )

            localDataSource.updateCachedSearchData(query = cachedQuery, searchData = cachedSearchData)

            assertNull(localDataSource.getCachedSearchData(distantQuery))
        }
}

private fun routeQuery(
    originPlaceId: String,
    originCoordinate: GeoCoordinate,
    destinationPlaceId: String = "destination-place",
    requestedOptions: List<RouteOption> = listOf(RouteOption.SAFE),
): RouteSearchQuery =
    RouteSearchQuery(
        origin =
            RouteWaypoint(
                name = "Origin",
                placeId = originPlaceId,
                coordinate = originCoordinate,
            ),
        destination =
            RouteWaypoint(
                name = "Destination",
                placeId = destinationPlaceId,
                coordinate = GeoCoordinate(latitude = 35.1151, longitude = 129.0414),
            ),
        requestedOptions = requestedOptions,
    )

private fun routeSearchData(query: RouteSearchQuery): RouteSearchData =
    RouteSearchData(
        query = query,
        result =
            RouteSearchResult(
                origin = query.origin,
                destination = query.destination,
                searchId = "search-id",
            ),
        source = RouteSearchSource.serverApi(label = "Cached route"),
    )

private const val CURRENT_LOCATION_ORIGIN_PLACE_ID = "route-origin-current-location"
