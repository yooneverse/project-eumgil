package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.AuthGateState
import com.ssafy.e102.eumgil.core.model.AuthSession
import com.ssafy.e102.eumgil.core.model.PlaceCategory
import com.ssafy.e102.eumgil.core.model.PlaceDetail
import com.ssafy.e102.eumgil.core.model.PlaceFeatureAvailability
import com.ssafy.e102.eumgil.core.model.PlaceFeatureType
import com.ssafy.e102.eumgil.core.model.PlaceQuery
import com.ssafy.e102.eumgil.core.model.PlaceSummary
import com.ssafy.e102.eumgil.data.local.datasource.PlacesLocalDataSource
import com.ssafy.e102.eumgil.data.mock.datasource.PlacesMockDataSource
import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import com.ssafy.e102.eumgil.data.remote.datasource.AuthRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.PlacesApiException
import com.ssafy.e102.eumgil.data.remote.datasource.PlacesRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.dto.ReissueResponseDto
import com.ssafy.e102.eumgil.data.repository.policy.RepositoryDomain
import com.ssafy.e102.eumgil.data.repository.policy.RepositoryReadPlan
import com.ssafy.e102.eumgil.data.repository.policy.RepositorySource
import com.ssafy.e102.eumgil.data.repository.policy.RepositorySourcePolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class PlacesRepositoryTest {
    @Test
    fun `getPlaces returns mock data when policy forces mock`() =
        runBlocking {
            val query = PlaceQuery(keyword = "elevator")
            val repository =
                DefaultPlacesRepository(
                    remoteDataSource = PlacesRemoteDataSource(baseUrl = "https://example.com"),
                    localDataSource = PlacesLocalDataSource(),
                    mockDataSource = PlacesMockDataSource(),
                    sourcePolicy = PlacesTestRepositorySourcePolicy(RepositoryReadPlan.mockOnly()),
                )

            val places = repository.getPlaces(query)

            assertEquals("mock-place-1", places.first().placeId)
        }

    @Test
    fun `getPlaces returns cached data when live policy falls back from remote to local`() =
        runBlocking {
            val query = PlaceQuery(keyword = "custom")
            val cachedPlace =
                PlaceSummary(
                    placeId = "cached-place-1",
                    name = "Cached Live Place",
                    address = "1 Cached-ro, Busan",
                    latitude = 35.1796,
                    longitude = 129.0756,
                    category = PlaceCategory.OTHER,
                )
            val localDataSource =
                PlacesLocalDataSource().apply {
                    updateCachedPlaces(query = query, places = listOf(cachedPlace))
                }
            val repository =
                DefaultPlacesRepository(
                    remoteDataSource = PlacesRemoteDataSource(baseUrl = "https://example.com"),
                    localDataSource = localDataSource,
                    mockDataSource = PlacesMockDataSource(),
                    sourcePolicy =
                        PlacesTestRepositorySourcePolicy(RepositoryReadPlan.remoteLocalMock()),
                )

            val places = repository.getPlaces(query)

            assertEquals(listOf(cachedPlace), places)
        }

    @Test
    fun `getPlaces throws remote failure when live policy has no cached fallback`() =
        runBlocking {
            val query = PlaceQuery(keyword = "custom")
            val repository =
                DefaultPlacesRepository(
                    remoteDataSource =
                        object : PlacesRemoteDataSource(
                            requestExecutor = { _, _, _ -> error("unused") },
                        ) {
                            override suspend fun getPlaces(query: PlaceQuery): List<PlaceSummary> {
                                throw IllegalStateException("remote places failed")
                            }
                        },
                    localDataSource = PlacesLocalDataSource(),
                    mockDataSource = PlacesMockDataSource(),
                    sourcePolicy =
                        PlacesTestRepositorySourcePolicy(
                            RepositoryReadPlan(
                                sources = listOf(RepositorySource.REMOTE, RepositorySource.LOCAL),
                            ),
                        ),
                )

            val failure = runCatching { repository.getPlaces(query) }.exceptionOrNull()

            assertEquals("remote places failed", failure?.message)
        }

    @Test
    fun `getPlaces returns remote data and caches it when remote succeeds`() =
        runBlocking {
            val query =
                PlaceQuery(
                    latitude = 35.1796,
                    longitude = 129.0756,
                    radiusMeters = 1200,
                )
            val localDataSource = PlacesLocalDataSource()
            val remotePlaces =
                listOf(
                    PlaceSummary(
                        placeId = "88",
                        name = "Remote Welfare Center",
                        address = "88 Welfare-ro, Busan",
                        latitude = 35.1801,
                        longitude = 129.0722,
                        category = PlaceCategory.WELFARE,
                        features =
                            listOf(
                                PlaceFeatureAvailability(
                                    featureType = PlaceFeatureType.ELEVATOR,
                                    isAvailable = true,
                                ),
                            ),
                    ),
                )
            val repository =
                DefaultPlacesRepository(
                    remoteDataSource =
                        object : PlacesRemoteDataSource(
                            requestExecutor = { _, _, _ -> error("unused") },
                        ) {
                            override suspend fun getPlaces(query: PlaceQuery): List<PlaceSummary> = remotePlaces
                        },
                    localDataSource = localDataSource,
                    mockDataSource = PlacesMockDataSource(),
                    sourcePolicy =
                        PlacesTestRepositorySourcePolicy(RepositoryReadPlan.remoteLocalMock()),
                )

            val places = repository.getPlaces(query)

            assertEquals(remotePlaces, places)
            assertEquals(places, localDataSource.getCachedPlaces(query))
        }

    @Test
    fun `getPlaces does not reuse cached places across account scopes`() =
        runBlocking {
            val query = PlaceQuery(keyword = "scoped")
            var currentAccountScopeKey = "account-a"
            var shouldFailRemote = false
            val localDataSource =
                PlacesLocalDataSource(
                    currentAccountScopeProvider = { currentAccountScopeKey },
                )
            val remotePlaces =
                listOf(
                    PlaceSummary(
                        placeId = "scope-place-1",
                        name = "Scoped Place",
                        address = "1 Scope-ro, Busan",
                        latitude = 35.1796,
                        longitude = 129.0756,
                        category = PlaceCategory.WELFARE,
                    ),
                )
            val repository =
                DefaultPlacesRepository(
                    remoteDataSource =
                        object : PlacesRemoteDataSource(
                            requestExecutor = { _, _, _ -> error("unused") },
                        ) {
                            override suspend fun getPlaces(query: PlaceQuery): List<PlaceSummary> {
                                if (shouldFailRemote) {
                                    throw IllegalStateException("remote places failed")
                                }
                                return remotePlaces
                            }
                        },
                    localDataSource = localDataSource,
                    mockDataSource = PlacesMockDataSource(),
                    sourcePolicy =
                        PlacesTestRepositorySourcePolicy(
                            RepositoryReadPlan(
                                sources = listOf(RepositorySource.REMOTE, RepositorySource.LOCAL),
                            ),
                        ),
                )

            assertEquals(remotePlaces, repository.getPlaces(query))
            assertEquals(remotePlaces, localDataSource.getCachedPlaces(query))

            currentAccountScopeKey = "account-b"
            shouldFailRemote = true

            val failure = runCatching { repository.getPlaces(query) }.exceptionOrNull()

            assertEquals("remote places failed", failure?.message)
            assertEquals(emptyList<PlaceSummary>(), localDataSource.getCachedPlaces(query))
        }

    @Test
    fun `getPlaces retries with refreshed auth session when remote responds unauthorized`() =
        runBlocking {
            val query =
                PlaceQuery(
                    latitude = 35.1796,
                    longitude = 129.0756,
                    radiusMeters = 1200,
                )
            val localDataSource = PlacesLocalDataSource()
            val authSessionRepository =
                FakeAuthSessionRepository(
                    initialState =
                        AuthGateState(
                            authSession = AuthSession(accessToken = "expired-token", refreshToken = "refresh-token"),
                            isProfileCompleted = true,
                        ),
                )
            val remotePlaces =
                listOf(
                    PlaceSummary(
                        placeId = "88",
                        name = "Remote Welfare Center",
                        address = "88 Welfare-ro, Busan",
                        latitude = 35.1801,
                        longitude = 129.0722,
                        category = PlaceCategory.WELFARE,
                    ),
                )
            var requestCount = 0
            val repository =
                DefaultPlacesRepository(
                    remoteDataSource =
                        object : PlacesRemoteDataSource(
                            requestExecutor = { _, _, _ -> error("unused") },
                        ) {
                            override suspend fun getPlaces(query: PlaceQuery): List<PlaceSummary> {
                                requestCount += 1
                                return when (requestCount) {
                                    1 ->
                                        throw PlacesApiException(
                                            httpStatusCode = 401,
                                            status = "AUTH_401",
                                            message = "인증이 필요합니다.",
                                        )

                                    2 -> {
                                        assertEquals(
                                            "refreshed-access-token",
                                            authSessionRepository.getAuthGateState().authSession?.accessToken,
                                        )
                                        remotePlaces
                                    }

                                    else -> error("Unexpected getPlaces retry count: $requestCount")
                                }
                            }
                        },
                    localDataSource = localDataSource,
                    mockDataSource = PlacesMockDataSource(),
                    sourcePolicy =
                        PlacesTestRepositorySourcePolicy(
                            RepositoryReadPlan(
                                sources = listOf(RepositorySource.REMOTE, RepositorySource.LOCAL),
                            ),
                        ),
                    authSessionRepository = authSessionRepository,
                    authRemoteDataSource =
                        object : AuthRemoteDataSource(HttpJsonClient(baseUrl = "https://example.com")) {
                            override suspend fun reissue(refreshToken: String): ReissueResponseDto {
                                assertEquals("refresh-token", refreshToken)
                                return ReissueResponseDto(
                                    accessToken = "refreshed-access-token",
                                    refreshToken = "refreshed-refresh-token",
                                )
                            }
                        },
                )

            val places = repository.getPlaces(query)

            assertEquals(remotePlaces, places)
            assertEquals(2, requestCount)
            assertEquals(remotePlaces, localDataSource.getCachedPlaces(query))
            assertEquals(
                "refreshed-refresh-token",
                authSessionRepository.getAuthGateState().authSession?.refreshToken,
            )
        }

    @Test
    fun `getPlaces clears auth session when token refresh fails`() =
        runBlocking {
            val authSessionRepository =
                FakeAuthSessionRepository(
                    initialState =
                        AuthGateState(
                            authSession = AuthSession(accessToken = "expired-token", refreshToken = "refresh-token"),
                            isProfileCompleted = true,
                        ),
                )
            val repository =
                DefaultPlacesRepository(
                    remoteDataSource =
                        object : PlacesRemoteDataSource(
                            requestExecutor = { _, _, _ -> error("unused") },
                        ) {
                            override suspend fun getPlaces(query: PlaceQuery): List<PlaceSummary> {
                                throw PlacesApiException(
                                    httpStatusCode = 401,
                                    status = "AUTH_401",
                                    message = "인증이 필요합니다.",
                                )
                            }
                        },
                    localDataSource = PlacesLocalDataSource(),
                    mockDataSource = PlacesMockDataSource(),
                    sourcePolicy =
                        PlacesTestRepositorySourcePolicy(
                            RepositoryReadPlan(
                                sources = listOf(RepositorySource.REMOTE, RepositorySource.LOCAL),
                            ),
                        ),
                    authSessionRepository = authSessionRepository,
                    authRemoteDataSource =
                        object : AuthRemoteDataSource(HttpJsonClient(baseUrl = "https://example.com")) {
                            override suspend fun reissue(refreshToken: String): ReissueResponseDto {
                                throw IllegalStateException("refresh failed")
                            }
                        },
                )

            val failure =
                runCatching {
                    repository.getPlaces(
                        PlaceQuery(
                            latitude = 35.1796,
                            longitude = 129.0756,
                        ),
                    )
                }.exceptionOrNull() as? PlacesApiException

            requireNotNull(failure)
            assertEquals(401, failure.httpStatusCode)
            assertEquals("PLACE_AUTHENTICATION_FAILED", failure.status)
            assertEquals("인증이 필요합니다.", failure.message)
            assertNull(authSessionRepository.getAuthGateState().authSession)
        }

    @Test
    fun `getPlaces surfaces forbidden response without clearing auth session`() =
        runBlocking {
            val authSessionRepository =
                FakeAuthSessionRepository(
                    initialState =
                        AuthGateState(
                            authSession = AuthSession(accessToken = "access-token", refreshToken = "refresh-token"),
                            isProfileCompleted = true,
                        ),
                )
            val repository =
                DefaultPlacesRepository(
                    remoteDataSource =
                        object : PlacesRemoteDataSource(
                            requestExecutor = { _, _, _ -> error("unused") },
                        ) {
                            override suspend fun getPlaces(query: PlaceQuery): List<PlaceSummary> {
                                throw PlacesApiException(
                                    httpStatusCode = 403,
                                    status = "A4030",
                                    message = "Forbidden.",
                                )
                            }
                        },
                    localDataSource = PlacesLocalDataSource(),
                    mockDataSource = PlacesMockDataSource(),
                    sourcePolicy =
                        PlacesTestRepositorySourcePolicy(
                            RepositoryReadPlan(
                                sources = listOf(RepositorySource.REMOTE, RepositorySource.LOCAL),
                            ),
                        ),
                    authSessionRepository = authSessionRepository,
                    authRemoteDataSource = AuthRemoteDataSource(HttpJsonClient(baseUrl = "https://example.com")),
                )

            val failure =
                runCatching {
                    repository.getPlaces(
                        PlaceQuery(
                            latitude = 35.1796,
                            longitude = 129.0756,
                        ),
                    )
                }.exceptionOrNull() as? PlacesApiException

            requireNotNull(failure)
            assertEquals(403, failure.httpStatusCode)
            assertEquals("A4030", failure.status)
            assertEquals("Forbidden.", failure.message)
            assertEquals("access-token", authSessionRepository.getAuthGateState().authSession?.accessToken)
            assertEquals("refresh-token", authSessionRepository.getAuthGateState().authSession?.refreshToken)
        }

    @Test
    fun `getPlaceDetail returns remote detail and caches it when remote succeeds`() =
        runBlocking {
            val localDataSource = PlacesLocalDataSource()
            val remoteDetail =
                PlaceDetail(
                    placeId = "88",
                    name = "Remote Welfare Center",
                    address = "88 Welfare-ro, Busan",
                    latitude = 35.1801,
                    longitude = 129.0722,
                    category = PlaceCategory.WELFARE,
                    features =
                        listOf(
                            PlaceFeatureAvailability(
                                featureType = PlaceFeatureType.ELEVATOR,
                                isAvailable = true,
                            ),
                        ),
                    isBookmarked = true,
                    accessibilityTags = listOf("elevator"),
                    providerPlaceId = "kakao-88",
                    description = null,
                )
            val repository =
                DefaultPlacesRepository(
                    remoteDataSource =
                        object : PlacesRemoteDataSource(
                            requestExecutor = { _, _, _ -> error("unused") },
                        ) {
                            override suspend fun getPlaceDetail(placeId: String): PlaceDetail? = remoteDetail
                        },
                    localDataSource = localDataSource,
                    mockDataSource = PlacesMockDataSource(),
                    sourcePolicy =
                        PlacesTestRepositorySourcePolicy(RepositoryReadPlan.remoteLocalMock()),
                )

            val detail = repository.getPlaceDetail("88")

            assertEquals(remoteDetail, detail)
            assertEquals(detail, localDataSource.getCachedPlaceDetail("88"))
        }

    @Test
    fun `getPlaceDetail does not reuse cached detail across account scopes`() =
        runBlocking {
            var currentAccountScopeKey = "account-a"
            var shouldFailRemote = false
            val localDataSource =
                PlacesLocalDataSource(
                    currentAccountScopeProvider = { currentAccountScopeKey },
                )
            val remoteDetail =
                PlaceDetail(
                    placeId = "88",
                    name = "Scoped Detail",
                    address = "88 Scope-ro, Busan",
                    latitude = 35.1801,
                    longitude = 129.0722,
                    category = PlaceCategory.WELFARE,
                    accessibilityTags = listOf("elevator"),
                )
            val repository =
                DefaultPlacesRepository(
                    remoteDataSource =
                        object : PlacesRemoteDataSource(
                            requestExecutor = { _, _, _ -> error("unused") },
                        ) {
                            override suspend fun getPlaceDetail(placeId: String): PlaceDetail? {
                                if (shouldFailRemote) {
                                    throw IllegalStateException("remote place detail failed")
                                }
                                return remoteDetail
                            }
                        },
                    localDataSource = localDataSource,
                    mockDataSource = PlacesMockDataSource(),
                    sourcePolicy =
                        PlacesTestRepositorySourcePolicy(
                            RepositoryReadPlan(
                                sources = listOf(RepositorySource.REMOTE, RepositorySource.LOCAL),
                            ),
                        ),
                )

            assertEquals(remoteDetail, repository.getPlaceDetail("88"))
            assertEquals(remoteDetail, localDataSource.getCachedPlaceDetail("88"))

            currentAccountScopeKey = "account-b"
            shouldFailRemote = true

            val failure = runCatching { repository.getPlaceDetail("88") }.exceptionOrNull()

            assertEquals("remote place detail failed", failure?.message)
            assertNull(localDataSource.getCachedPlaceDetail("88"))
        }

    @Test
    fun `getPlaceDetail returns null when remote detail responds with 404`() =
        runBlocking {
            val localDataSource = PlacesLocalDataSource()
            val repository =
                DefaultPlacesRepository(
                    remoteDataSource =
                        object : PlacesRemoteDataSource(
                            requestExecutor = { _, _, _ -> error("unused") },
                        ) {
                            override suspend fun getPlaceDetail(placeId: String): PlaceDetail? = null
                        },
                    localDataSource = localDataSource,
                    mockDataSource = PlacesMockDataSource(),
                    sourcePolicy =
                        PlacesTestRepositorySourcePolicy(RepositoryReadPlan.remoteLocalMock()),
                )

            val detail = repository.getPlaceDetail("404")

            assertEquals(null, detail)
            assertEquals(null, localDataSource.getCachedPlaceDetail("404"))
        }

    @Test
    fun `getPlaceDetail returns cached detail when live policy falls back from remote to local`() =
        runBlocking {
            val cachedDetail =
                PlaceDetail(
                    placeId = "cached-place-1",
                    name = "Cached Place Detail",
                    address = "1 Cached-ro, Busan",
                    latitude = 35.1796,
                    longitude = 129.0756,
                    category = PlaceCategory.PUBLIC_OFFICE,
                    accessibilityTags = listOf("elevator"),
                )
            val localDataSource =
                PlacesLocalDataSource().apply {
                    updateCachedPlaceDetail(cachedDetail)
                }
            val repository =
                DefaultPlacesRepository(
                    remoteDataSource =
                        object : PlacesRemoteDataSource(
                            requestExecutor = { _, _, _ -> error("unused") },
                        ) {
                            override suspend fun getPlaceDetail(placeId: String): PlaceDetail? {
                                throw IllegalStateException("remote place detail failed")
                            }
                        },
                    localDataSource = localDataSource,
                    mockDataSource = PlacesMockDataSource(),
                    sourcePolicy = PlacesTestRepositorySourcePolicy(RepositoryReadPlan.remoteLocalMock()),
                )

            val detail = repository.getPlaceDetail("cached-place-1")

            assertEquals(cachedDetail, detail)
        }

    @Test
    fun `getPlaceDetail throws remote failure when live policy has no cached fallback`() =
        runBlocking {
            val repository =
                DefaultPlacesRepository(
                    remoteDataSource =
                        object : PlacesRemoteDataSource(
                            requestExecutor = { _, _, _ -> error("unused") },
                        ) {
                            override suspend fun getPlaceDetail(placeId: String): PlaceDetail? {
                                throw IllegalStateException("remote place detail failed")
                            }
                        },
                    localDataSource = PlacesLocalDataSource(),
                    mockDataSource = PlacesMockDataSource(),
                    sourcePolicy =
                        PlacesTestRepositorySourcePolicy(
                            RepositoryReadPlan(
                                sources = listOf(RepositorySource.REMOTE, RepositorySource.LOCAL),
                            ),
                        ),
                )

            val failure = runCatching { repository.getPlaceDetail("missing-place") }.exceptionOrNull()

            assertEquals("remote place detail failed", failure?.message)
        }
}

private class PlacesTestRepositorySourcePolicy(
    private val plan: RepositoryReadPlan,
) : RepositorySourcePolicy {
    override suspend fun readPlan(domain: RepositoryDomain): RepositoryReadPlan = plan
}

private class FakeAuthSessionRepository(
    initialState: AuthGateState,
) : AuthSessionRepository {
    private var authGateState: AuthGateState = initialState

    override fun observeAuthGateState(): Flow<AuthGateState> = flowOf(authGateState)

    override suspend fun getAuthGateState(): AuthGateState = authGateState

    override suspend fun saveAuthSession(
        authSession: AuthSession,
        isProfileCompleted: Boolean,
    ) {
        authGateState =
            authGateState.copy(
                authSession = authSession,
                isProfileCompleted = isProfileCompleted,
                signupToken = null,
            )
    }

    override suspend fun saveSignupToken(signupToken: String) {
        authGateState =
            authGateState.copy(
                authSession = null,
                isProfileCompleted = false,
                signupToken = signupToken,
            )
    }

    override suspend fun clearSignupToken() {
        authGateState = authGateState.copy(signupToken = null)
    }

    override suspend fun markProfileCompleted() {
        authGateState = authGateState.copy(isProfileCompleted = true)
    }

    override suspend fun clearAuthSession() {
        authGateState = authGateState.copy(authSession = null, isProfileCompleted = false)
    }
}
