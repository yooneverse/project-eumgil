package com.ssafy.e102.eumgil.data.repository

import android.util.Log
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.RouteCandidate
import com.ssafy.e102.eumgil.core.model.RouteSearchData
import com.ssafy.e102.eumgil.core.model.RouteSearchQuery
import com.ssafy.e102.eumgil.core.model.RouteSearchResult
import com.ssafy.e102.eumgil.core.model.RouteSearchSource
import com.ssafy.e102.eumgil.data.local.datasource.RouteLocalDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.AuthRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.RouteApiException
import com.ssafy.e102.eumgil.data.remote.datasource.RouteRemoteDataSource
import com.ssafy.e102.eumgil.data.route.DefaultRouteGeometryParser
import com.ssafy.e102.eumgil.data.route.RouteGeometryParser
import com.ssafy.e102.eumgil.data.route.RoutePointDto
import com.ssafy.e102.eumgil.data.route.RouteRatingRequestDto
import com.ssafy.e102.eumgil.data.route.RouteRatingResponseDto
import com.ssafy.e102.eumgil.data.route.RouteRerouteRequestDto
import com.ssafy.e102.eumgil.data.route.RouteRerouteResponseDto
import com.ssafy.e102.eumgil.data.route.RouteSearchRequestDto
import com.ssafy.e102.eumgil.data.route.RouteSearchResponseDto
import com.ssafy.e102.eumgil.data.route.RouteSelectRequestDto
import com.ssafy.e102.eumgil.data.route.RouteSelectResponseDto
import com.ssafy.e102.eumgil.data.route.RouteSessionResponseDto
import com.ssafy.e102.eumgil.data.route.RouteTransitArrivalDto
import com.ssafy.e102.eumgil.data.route.RouteTransitRefreshRequestDto
import com.ssafy.e102.eumgil.data.route.RouteTransitRefreshResponseDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ssafy.e102.eumgil.data.route.toDomain
import com.ssafy.e102.eumgil.data.route.toRequestDto
import com.ssafy.e102.eumgil.data.route.toRouteCandidate
import kotlin.math.roundToInt

interface RouteRepository {
    // Primary read-model entry point for 199 route setting and 200/201/202 handoff consumers.
    suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData

    suspend fun getFreshRouteSearchData(query: RouteSearchQuery): RouteSearchData = getRouteSearchData(query)

    suspend fun searchRoutes(query: RouteSearchQuery): RouteSearchResult = getRouteSearchData(query).result

    suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData

    suspend fun getFreshTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        getTransitRouteSearchData(query)

    suspend fun searchTransitRoutes(query: RouteSearchQuery): RouteSearchResult = getTransitRouteSearchData(query).result

    suspend fun selectRoute(
        routeId: String,
        searchId: String,
    ): RouteSessionData

    suspend fun refreshTransit(
        routeId: String,
        legSequence: Int,
    ): RouteTransitRefreshData

    suspend fun reroute(
        routeId: String,
        currentPoint: GeoCoordinate,
    ): RouteRerouteData

    suspend fun endRoute(routeId: String): RouteSessionData

    suspend fun rateRoute(
        sessionId: String,
        score: Int,
    ): RouteRatingData
}

class DefaultRouteRepository(
    private val localDataSource: RouteLocalDataSource,
    private val remoteDataSource: RouteRemoteDataSource,
    private val geometryParser: RouteGeometryParser = DefaultRouteGeometryParser(),
    authSessionRepository: AuthSessionRepository? = null,
    authRemoteDataSource: AuthRemoteDataSource? = null,
    private val routeMappingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : RouteRepository {
    private val authenticatedRequestRunner =
        if (authSessionRepository != null && authRemoteDataSource != null) {
            AuthenticatedRequestRunner(
                authSessionRepository = authSessionRepository,
                authRemoteDataSource = authRemoteDataSource,
            )
        } else {
            null
        }

    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        getSearchData(
            query = query,
            requestPath = ROUTE_SEARCH_WALK_PATH,
        ) { request ->
            remoteDataSource.searchWalkRoutes(request)
        }

    override suspend fun getFreshRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        getSearchData(
            query = query,
            requestPath = ROUTE_SEARCH_WALK_PATH,
        ) { request ->
            remoteDataSource.searchWalkRoutes(request)
        }

    override suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        getSearchData(
            query = query,
            requestPath = ROUTE_SEARCH_TRANSIT_PATH,
        ) { request ->
            remoteDataSource.searchTransitRoutes(request)
        }

    override suspend fun getFreshTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        getSearchData(
            query = query,
            requestPath = ROUTE_SEARCH_TRANSIT_PATH,
        ) { request ->
            remoteDataSource.searchTransitRoutes(request)
        }

    override suspend fun selectRoute(
        routeId: String,
        searchId: String,
    ): RouteSessionData =
        runAuthenticatedRemoteRequest(requestPath = "/routes/$routeId/select") {
            remoteDataSource.selectRoute(
                routeId = routeId,
                request = RouteSelectRequestDto(searchId = searchId),
            )
        }.toRepositoryData()

    override suspend fun refreshTransit(
        routeId: String,
        legSequence: Int,
    ): RouteTransitRefreshData =
        runAuthenticatedRemoteRequest(requestPath = "/routes/$routeId/transit-refresh") {
            remoteDataSource.refreshTransit(
                routeId = routeId,
                request = RouteTransitRefreshRequestDto(legSequence = legSequence),
            )
        }.toRepositoryData()

    override suspend fun reroute(
        routeId: String,
        currentPoint: GeoCoordinate,
    ): RouteRerouteData {
        val response =
            runAuthenticatedRemoteRequest(requestPath = ROUTE_REROUTE_PATH) {
                remoteDataSource.reroute(
                    RouteRerouteRequestDto(
                        routeId = routeId,
                        currentPoint = currentPoint.toPointDto(),
                    ),
                )
            }
        return withContext(routeMappingDispatcher) {
            response.toRepositoryData(geometryParser = geometryParser)
        }
    }

    override suspend fun endRoute(routeId: String): RouteSessionData =
        runAuthenticatedRemoteRequest(requestPath = "/routes/$routeId/end") {
            remoteDataSource.endRoute(routeId = routeId)
        }.toRepositoryData()

    override suspend fun rateRoute(
        sessionId: String,
        score: Int,
    ): RouteRatingData =
        runAuthenticatedRemoteRequest(requestPath = ROUTE_RATING_PATH) {
            remoteDataSource.rateRoute(
                RouteRatingRequestDto(
                    sessionId = sessionId,
                    score = score,
                ),
            )
        }.toRepositoryData()

    private suspend fun getSearchData(
        query: RouteSearchQuery,
        requestPath: String,
        remoteSearch: suspend (RouteSearchRequestDto) -> RouteSearchResponseDto,
    ): RouteSearchData {
        val response = runAuthenticatedRemoteRequest(requestPath = requestPath) { remoteSearch(query.toRequestDto()) }
        return withContext(routeMappingDispatcher) {
            RouteSearchData(
                query = query,
                result = response.toDomain(query = query, geometryParser = geometryParser),
                source = RouteSearchSource.serverApi(),
            )
        }
    }

    private suspend fun <T> runAuthenticatedRemoteRequest(
        requestPath: String,
        execute: suspend () -> T,
    ): T {
        val runner = authenticatedRequestRunner ?: return execute()

        return when (
            val result =
                runner.run(
                    execute = { execute() },
                    isAuthenticationFailure = ::isAuthenticationFailure,
                )
        ) {
            AuthenticatedRequestResult.MissingSession -> {
                logAuthGateFailure(
                    requestPath = requestPath,
                    status = ROUTE_STATUS_MISSING_SESSION,
                )
                throw RouteApiException(
                    httpStatusCode = HTTP_UNAUTHORIZED,
                    status = ROUTE_STATUS_MISSING_SESSION,
                    message = AUTH_REQUIRED_MESSAGE,
                )
            }

            AuthenticatedRequestResult.AuthenticationFailed -> {
                logAuthGateFailure(
                    requestPath = requestPath,
                    status = ROUTE_STATUS_AUTHENTICATION_FAILED,
                )
                throw RouteApiException(
                    httpStatusCode = HTTP_UNAUTHORIZED,
                    status = ROUTE_STATUS_AUTHENTICATION_FAILED,
                    message = AUTH_REQUIRED_MESSAGE,
                )
            }

            is AuthenticatedRequestResult.Success -> result.value
        }
    }

    private fun logAuthGateFailure(
        requestPath: String,
        status: String,
    ) {
        safeLogInfo(
            ROUTE_REPOSITORY_LOG_TAG,
            "routeRequest path=$requestPath result=failure layer=auth_gate httpStatus=$HTTP_UNAUTHORIZED status=$status",
        )
    }

    private fun isAuthenticationFailure(throwable: Throwable): Boolean =
        throwable is RouteApiException &&
            throwable.httpStatusCode == HTTP_UNAUTHORIZED

    private companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val ROUTE_STATUS_MISSING_SESSION = "ROUTE_AUTH_MISSING_SESSION"
        private const val ROUTE_STATUS_AUTHENTICATION_FAILED = "ROUTE_AUTHENTICATION_FAILED"
        private const val ROUTE_REPOSITORY_LOG_TAG = "RouteRepository"
        private const val ROUTE_SEARCH_WALK_PATH = "/routes/search/walk"
        private const val ROUTE_SEARCH_TRANSIT_PATH = "/routes/search/transit"
        private const val ROUTE_REROUTE_PATH = "/routes/reroute"
        private const val ROUTE_RATING_PATH = "/route-ratings"
        private const val AUTH_REQUIRED_MESSAGE = "인증이 필요합니다."
    }
}

private fun safeLogInfo(
    tag: String,
    message: String,
) {
    runCatching { Log.i(tag, message) }
}

data class RouteSessionData(
    val sessionId: String,
    val totalDistanceMeters: Int? = null,
    val totalDurationSeconds: Int? = null,
)

data class RouteTransitArrivalData(
    val routeNo: String? = null,
    val remainingMinute: Int? = null,
    val isLowFloor: Boolean? = null,
)

data class RouteTransitRefreshData(
    val type: String,
    val arrivalStatus: String,
    val transits: List<RouteTransitArrivalData> = emptyList(),
)

data class RouteRerouteData(
    val route: RouteCandidate? = null,
)

data class RouteRatingData(
    val ratingId: Long,
)

private fun RouteSelectResponseDto.toRepositoryData(): RouteSessionData =
    RouteSessionData(
        sessionId = sessionId,
        totalDistanceMeters = totalDistanceMeter.toRoundedMeters(),
        totalDurationSeconds = totalDurationSecond?.takeIf { durationSeconds -> durationSeconds >= 0 },
    )

private fun RouteSessionResponseDto.toRepositoryData(): RouteSessionData =
    RouteSessionData(
        sessionId = sessionId,
    )

private fun RouteTransitRefreshResponseDto.toRepositoryData(): RouteTransitRefreshData =
    RouteTransitRefreshData(
        type = type,
        arrivalStatus = arrivalStatus,
        transits = transits.map(RouteTransitArrivalDto::toRepositoryData),
    )

private fun RouteTransitArrivalDto.toRepositoryData(): RouteTransitArrivalData =
    RouteTransitArrivalData(
        routeNo = routeNo?.trim()?.takeIf(String::isNotEmpty),
        remainingMinute = remainingMinute,
        isLowFloor = isLowFloor,
    )

private fun RouteRerouteResponseDto.toRepositoryData(geometryParser: RouteGeometryParser): RouteRerouteData =
    RouteRerouteData(
        route = toRouteCandidate(geometryParser),
    )

private fun RouteRatingResponseDto.toRepositoryData(): RouteRatingData =
    RouteRatingData(ratingId = ratingId)

private fun Double?.toRoundedMeters(): Int? =
    this
        ?.takeIf { value -> value >= 0.0 }
        ?.roundToInt()

private fun GeoCoordinate.toPointDto(): RoutePointDto =
    RoutePointDto(
        lat = latitude,
        lng = longitude,
    )
