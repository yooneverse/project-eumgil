package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.PlaceDetail
import com.ssafy.e102.eumgil.core.model.MapPlaceDetailRequest
import com.ssafy.e102.eumgil.core.model.MapTappedPlaceDetail
import com.ssafy.e102.eumgil.core.model.PlaceQuery
import com.ssafy.e102.eumgil.core.model.PlaceSummary
import com.ssafy.e102.eumgil.data.local.datasource.PlacesLocalDataSource
import com.ssafy.e102.eumgil.data.mock.datasource.PlacesMockDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.AuthRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.PlacesApiException
import com.ssafy.e102.eumgil.data.remote.datasource.PlacesRemoteDataSource
import com.ssafy.e102.eumgil.data.repository.policy.RepositoryDomain
import com.ssafy.e102.eumgil.data.repository.policy.RepositorySource
import com.ssafy.e102.eumgil.data.repository.policy.RepositorySourcePolicy

interface PlacesRepository {
    suspend fun getPlaces(query: PlaceQuery): List<PlaceSummary>

    suspend fun getPlaceDetail(placeId: String): PlaceDetail?

    suspend fun getMapTappedPlaceDetail(request: MapPlaceDetailRequest): MapTappedPlaceDetail? = null
}

class DefaultPlacesRepository(
    private val remoteDataSource: PlacesRemoteDataSource,
    private val localDataSource: PlacesLocalDataSource,
    private val mockDataSource: PlacesMockDataSource,
    private val sourcePolicy: RepositorySourcePolicy,
    authSessionRepository: AuthSessionRepository? = null,
    authRemoteDataSource: AuthRemoteDataSource? = null,
) : PlacesRepository {
    private val authenticatedRequestRunner =
        if (authSessionRepository != null && authRemoteDataSource != null) {
            AuthenticatedRequestRunner(
                authSessionRepository = authSessionRepository,
                authRemoteDataSource = authRemoteDataSource,
            )
        } else {
            null
        }

    override suspend fun getPlaces(query: PlaceQuery): List<PlaceSummary> {
        val readPlan = sourcePolicy.readPlan(RepositoryDomain.PLACES)
        val lastSource = readPlan.sources.last()
        var remoteFailure: Throwable? = null

        for (source in readPlan.sources) {
            when (source) {
                RepositorySource.REMOTE -> {
                    val remoteResult = runCatching { runAuthenticatedRemoteRequest { remoteDataSource.getPlaces(query) } }
                    if (remoteResult.isSuccess) {
                        val places = remoteResult.getOrDefault(emptyList())
                        localDataSource.updateCachedPlaces(query = query, places = places)
                        return places
                    }
                    remoteFailure = remoteResult.exceptionOrNull()
                }

                RepositorySource.LOCAL -> {
                    val cachedPlaces = localDataSource.getCachedPlaces(query)
                    if (cachedPlaces.isNotEmpty()) {
                        return cachedPlaces
                    }
                    if (source == lastSource && remoteFailure == null) {
                        return cachedPlaces
                    }
                }

                RepositorySource.MOCK -> return mockDataSource.getPlaces(query)
            }
        }

        throw remoteFailure ?: IllegalStateException("No place data source matched the current policy.")
    }

    override suspend fun getPlaceDetail(placeId: String): PlaceDetail? {
        val readPlan = sourcePolicy.readPlan(RepositoryDomain.PLACES)
        val lastSource = readPlan.sources.last()
        var remoteFailure: Throwable? = null

        for (source in readPlan.sources) {
            when (source) {
                RepositorySource.REMOTE -> {
                    val remoteResult =
                        runCatching {
                            runAuthenticatedRemoteRequest { remoteDataSource.getPlaceDetail(placeId) }
                        }
                    if (remoteResult.isSuccess) {
                        val placeDetail = remoteResult.getOrNull()
                        if (placeDetail != null) {
                            localDataSource.updateCachedPlaceDetail(placeDetail)
                        }
                        return placeDetail
                    }
                    remoteFailure = remoteResult.exceptionOrNull()
                }

                RepositorySource.LOCAL -> {
                    val cachedPlaceDetail = localDataSource.getCachedPlaceDetail(placeId)
                    if (cachedPlaceDetail != null) {
                        return cachedPlaceDetail
                    }
                    if (source == lastSource && remoteFailure == null) {
                        return cachedPlaceDetail
                    }
                }

                RepositorySource.MOCK -> return mockDataSource.getPlaceDetail(placeId)
            }
        }

        throw remoteFailure ?: IllegalStateException("No place detail source matched the current policy.")
    }

    override suspend fun getMapTappedPlaceDetail(request: MapPlaceDetailRequest): MapTappedPlaceDetail? {
        val readPlan = sourcePolicy.readPlan(RepositoryDomain.PLACES)
        var remoteFailure: Throwable? = null

        for (source in readPlan.sources) {
            when (source) {
                RepositorySource.REMOTE -> {
                    val remoteResult =
                        runCatching {
                            runAuthenticatedRemoteRequest { remoteDataSource.getMapTappedPlaceDetail(request) }
                        }
                    if (remoteResult.isSuccess) {
                        return remoteResult.getOrNull()
                    }
                    remoteFailure = remoteResult.exceptionOrNull()
                }

                RepositorySource.LOCAL -> Unit
                RepositorySource.MOCK -> return null
            }
        }

        throw remoteFailure ?: IllegalStateException("No map-tap place detail source matched the current policy.")
    }

    private suspend fun <T> runAuthenticatedRemoteRequest(execute: suspend () -> T): T {
        val runner = authenticatedRequestRunner ?: return execute()

        return when (
            val result =
                runner.run(
                    execute = { execute() },
                    isAuthenticationFailure = ::isAuthenticationFailure,
                )
        ) {
            AuthenticatedRequestResult.MissingSession ->
                throw PlacesApiException(
                    httpStatusCode = HTTP_UNAUTHORIZED,
                    status = PLACE_STATUS_MISSING_SESSION,
                    message = AUTH_REQUIRED_MESSAGE,
                )

            AuthenticatedRequestResult.AuthenticationFailed ->
                throw PlacesApiException(
                    httpStatusCode = HTTP_UNAUTHORIZED,
                    status = PLACE_STATUS_AUTHENTICATION_FAILED,
                    message = AUTH_REQUIRED_MESSAGE,
                )

            is AuthenticatedRequestResult.Success -> result.value
        }
    }

    private fun isAuthenticationFailure(throwable: Throwable): Boolean =
        throwable is PlacesApiException &&
            throwable.httpStatusCode == HTTP_UNAUTHORIZED

    private companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val PLACE_STATUS_MISSING_SESSION = "PLACE_AUTH_MISSING_SESSION"
        private const val PLACE_STATUS_AUTHENTICATION_FAILED = "PLACE_AUTHENTICATION_FAILED"
        private const val AUTH_REQUIRED_MESSAGE = "인증이 필요합니다."
    }
}
