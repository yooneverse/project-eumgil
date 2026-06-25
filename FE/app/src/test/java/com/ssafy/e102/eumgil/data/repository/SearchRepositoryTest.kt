package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.location.AddressSearchCandidate
import com.ssafy.e102.eumgil.core.location.AddressSearchResolver
import com.ssafy.e102.eumgil.core.model.AuthGateState
import com.ssafy.e102.eumgil.core.model.AuthSession
import com.ssafy.e102.eumgil.core.model.PlaceCategory
import com.ssafy.e102.eumgil.core.model.RecentDestination
import com.ssafy.e102.eumgil.core.model.RecentSearch
import com.ssafy.e102.eumgil.core.model.SearchPage
import com.ssafy.e102.eumgil.core.model.SearchQuery
import com.ssafy.e102.eumgil.core.model.SearchResult
import com.ssafy.e102.eumgil.core.model.SearchVoiceAnalysis
import com.ssafy.e102.eumgil.core.model.SearchVoiceIntent
import com.ssafy.e102.eumgil.core.model.SearchVoiceMode
import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import com.ssafy.e102.eumgil.data.local.datasource.SearchLocalDataSource
import com.ssafy.e102.eumgil.data.mock.datasource.SearchMockDataSource
import com.ssafy.e102.eumgil.data.remote.HttpJsonResponse
import com.ssafy.e102.eumgil.data.remote.datasource.AuthRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.SearchApiException
import com.ssafy.e102.eumgil.data.remote.datasource.SearchRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.dto.ReissueResponseDto
import com.ssafy.e102.eumgil.data.repository.policy.RepositoryDomain
import com.ssafy.e102.eumgil.data.repository.policy.RepositoryReadPlan
import com.ssafy.e102.eumgil.data.repository.policy.RepositorySource
import com.ssafy.e102.eumgil.data.repository.policy.RepositorySourcePolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchRepositoryTest {
    @Test
    fun `search falls back to geocoded address candidates when road address search returns empty`() =
        runBlocking {
            val query = SearchQuery(keyword = "부산진구 시민공원로 73", limit = 2)
            val repository =
                DefaultSearchRepository(
                    remoteDataSource =
                        SearchRemoteDataSource(
                            getRequestExecutor = { _, _, _ ->
                                HttpJsonResponse(
                                    statusCode = 200,
                                    body =
                                        """
                                        {
                                          "status": "S2000",
                                          "data": {
                                            "places": [],
                                            "nextCursor": null,
                                            "size": 0,
                                            "totalElements": 0,
                                            "hasNext": false
                                          },
                                          "message": "ok"
                                        }
                                        """.trimIndent(),
                                )
                            },
                            postRequestExecutor = { _, _, _ -> error("voice analyze should not run from search()") },
                        ),
                    localDataSource = SearchLocalDataSource(),
                    mockDataSource = SearchMockDataSource(),
                    sourcePolicy = SearchTestRepositorySourcePolicy(RepositoryReadPlan.remoteLocalMock()),
                    addressSearchResolver =
                        FakeAddressSearchResolver(
                            listOf(
                                AddressSearchCandidate(
                                    title = "시민공원로 73",
                                    address = "부산광역시 부산진구 시민공원로 73",
                                    latitude = 35.1686,
                                    longitude = 129.0576,
                                ),
                            ),
                        ),
                )

            val results = repository.search(query)
            val result = results.single()

            assertEquals("시민공원로 73", result.title)
            assertEquals("부산광역시 부산진구 시민공원로 73", result.subtitle)
            assertEquals("ANDROID_GEOCODER", result.provider)
            assertEquals(false, result.matched)
        }

    @Test
    fun `search returns remote provider results and caches them for the same query`() =
        runBlocking {
            val query = SearchQuery(keyword = "Busan Tower", limit = 2)
            val localDataSource = SearchLocalDataSource()
            val repository =
                DefaultSearchRepository(
                    remoteDataSource =
                        SearchRemoteDataSource(
                            getRequestExecutor = { _, _, _ ->
                                HttpJsonResponse(
                                    statusCode = 200,
                                    body =
                                        """
                                        {
                                          "status": "S2000",
                                          "data": {
                                            "places": [
                                              {
                                                "placeId": 10,
                                                "provider": "KAKAO",
                                                "providerPlaceId": "123456789",
                                                "name": "Busan Tower",
                                                "category": "TOURIST_SPOT",
                                                "address": "1 Yongdusan-gil, Busan",
                                                "distanceMeter": 350,
                                                "point": {
                                                  "lat": 35.1000,
                                                  "lng": 129.0320
                                                },
                                                "accessibilityFeatures": [],
                                                "matched": true
                                              },
                                              {
                                                "placeId": null,
                                                "provider": "KAKAO",
                                                "providerPlaceId": "987654321",
                                                "name": "Provider Only Cafe",
                                                "category": null,
                                                "address": "2 Gwangbok-ro, Busan",
                                                "distanceMeter": 120,
                                                "point": {
                                                  "lat": 35.1010,
                                                  "lng": 129.0330
                                                },
                                                "accessibilityFeatures": [],
                                                "matched": false
                                              }
                                            ],
                                            "nextCursor": null,
                                            "size": 2,
                                            "totalElements": 2,
                                            "hasNext": false
                                          },
                                          "message": "ok"
                                        }
                                        """.trimIndent(),
                                )
                            },
                            postRequestExecutor = { _, _, _ -> error("voice analyze should not run from search()") },
                            accessTokenProvider = { "access-token" },
                        ),
                    localDataSource = localDataSource,
                    mockDataSource = SearchMockDataSource(),
                    sourcePolicy = SearchTestRepositorySourcePolicy(RepositoryReadPlan.remoteLocalMock()),
                )

            val results = repository.search(query)

            assertEquals(listOf("10", "provider:kakao:987654321"), results.map(SearchResult::placeId))
            assertEquals(listOf("10", null), results.map(SearchResult::serverPlaceId))
            assertEquals(results, localDataSource.getCachedResults(query))
        }

    @Test
    fun `search returns mock data when policy forces mock`() =
        runBlocking {
            val query = SearchQuery(keyword = "Braille")
            val repository =
                DefaultSearchRepository(
                    remoteDataSource = SearchRemoteDataSource(baseUrl = "https://example.com"),
                    localDataSource = SearchLocalDataSource(),
                    mockDataSource = SearchMockDataSource(),
                    sourcePolicy = SearchTestRepositorySourcePolicy(RepositoryReadPlan.mockOnly()),
                )

            val results = repository.search(query)

            assertEquals("mock-place-3", results.first().placeId)
        }

    @Test
    fun `search returns cached data when live policy falls back from remote to local`() =
        runBlocking {
            val query = SearchQuery(keyword = "custom")
            val cachedResult =
                SearchResult(
                    placeId = "cached-search-1",
                    title = "Cached Search Result",
                    subtitle = "1 Cached-ro, Busan",
                    latitude = 35.1796,
                    longitude = 129.0756,
                )
            val localDataSource =
                SearchLocalDataSource().apply {
                    updateCachedResults(query = query, results = listOf(cachedResult))
                }
            val repository =
                DefaultSearchRepository(
                    remoteDataSource = SearchRemoteDataSource(baseUrl = "https://example.com"),
                    localDataSource = localDataSource,
                    mockDataSource = SearchMockDataSource(),
                    sourcePolicy =
                        SearchTestRepositorySourcePolicy(RepositoryReadPlan.remoteLocalMock()),
                )

            val results = repository.search(query)

            assertEquals(listOf(cachedResult), results)
        }

    @Test
    fun `search throws remote failure when live policy has no cached fallback`() =
        runBlocking {
            val query = SearchQuery(keyword = "custom")
            val repository =
                DefaultSearchRepository(
                    remoteDataSource =
                        object : SearchRemoteDataSource(
                            getRequestExecutor = { _, _, _ -> error("unused") },
                            postRequestExecutor = { _, _, _ -> error("unused") },
                        ) {
                            override suspend fun searchPage(query: SearchQuery): SearchPage {
                                throw IllegalStateException("remote search failed")
                            }
                        },
                    localDataSource = SearchLocalDataSource(),
                    mockDataSource = SearchMockDataSource(),
                    sourcePolicy =
                        SearchTestRepositorySourcePolicy(
                            RepositoryReadPlan(
                                sources = listOf(RepositorySource.REMOTE, RepositorySource.LOCAL),
                            ),
                        ),
                )

            val failure = runCatching { repository.search(query) }.exceptionOrNull()

            assertEquals("remote search failed", failure?.message)
        }

    @Test
    fun `search retries with refreshed auth session when remote responds unauthorized`() =
        runBlocking {
            val query = SearchQuery(keyword = "Busan Tower", limit = 2)
            val localDataSource = SearchLocalDataSource()
            val authSessionRepository =
                TestAuthSessionRepository(
                    initialState =
                        AuthGateState(
                            authSession = AuthSession(accessToken = "expired-token", refreshToken = "refresh-token"),
                            isProfileCompleted = true,
                        ),
                )
            val remoteResults =
                listOf(
                    SearchResult(
                        placeId = "search-1",
                        title = "Busan Tower",
                        subtitle = "1 Yongdusan-gil, Busan",
                        latitude = 35.1000,
                        longitude = 129.0320,
                    ),
                )
            var requestCount = 0
            val repository =
                DefaultSearchRepository(
                    remoteDataSource =
                        object : SearchRemoteDataSource(
                            getRequestExecutor = { _, _, _ -> error("unused") },
                            postRequestExecutor = { _, _, _ -> error("unused") },
                        ) {
                            override suspend fun searchPage(query: SearchQuery): SearchPage {
                                requestCount += 1
                                return when (requestCount) {
                                    1 ->
                                        throw SearchApiException(
                                            httpStatusCode = 401,
                                            status = "AUTH_401",
                                            message = "인증이 필요합니다.",
                                        )

                                    2 -> {
                                        assertEquals(
                                            "refreshed-access-token",
                                            authSessionRepository.getAuthGateState().authSession?.accessToken,
                                        )
                                        SearchPage(results = remoteResults)
                                    }

                                    else -> error("Unexpected search retry count: $requestCount")
                                }
                            }
                        },
                    localDataSource = localDataSource,
                    mockDataSource = SearchMockDataSource(),
                    sourcePolicy =
                        SearchTestRepositorySourcePolicy(
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

            val results = repository.search(query)

            assertEquals(remoteResults, results)
            assertEquals(2, requestCount)
            assertEquals(remoteResults, localDataSource.getCachedResults(query))
            assertEquals(
                "refreshed-refresh-token",
                authSessionRepository.getAuthGateState().authSession?.refreshToken,
            )
        }

    @Test
    fun `analyzeVoiceSearch retries with refreshed auth session when remote responds unauthorized`() =
        runBlocking {
            val authSessionRepository =
                TestAuthSessionRepository(
                    initialState =
                        AuthGateState(
                            authSession = AuthSession(accessToken = "expired-token", refreshToken = "refresh-token"),
                            isProfileCompleted = true,
                        ),
                )
            val remoteAnalysis =
                SearchVoiceAnalysis(
                    intent = SearchVoiceIntent.PLACE_SEARCH,
                    placeName = "Busan Station",
                    confirmed = true,
                )
            var requestCount = 0
            val repository =
                DefaultSearchRepository(
                    remoteDataSource =
                        object : SearchRemoteDataSource(
                            getRequestExecutor = { _, _, _ -> error("unused") },
                            postRequestExecutor = { _, _, _ -> error("unused") },
                        ) {
                            override suspend fun analyzeVoiceSearch(
                                text: String,
                                mode: SearchVoiceMode,
                            ): SearchVoiceAnalysis {
                                requestCount += 1
                                return when (requestCount) {
                                    1 ->
                                        throw SearchApiException(
                                            httpStatusCode = 401,
                                            status = "AUTH_401",
                                            message = "인증이 필요합니다.",
                                        )

                                    2 -> {
                                        assertEquals(
                                            "refreshed-access-token",
                                            authSessionRepository.getAuthGateState().authSession?.accessToken,
                                        )
                                        remoteAnalysis
                                    }

                                    else -> error("Unexpected analyze retry count: $requestCount")
                                }
                            }
                        },
                    localDataSource = SearchLocalDataSource(),
                    mockDataSource = SearchMockDataSource(),
                    sourcePolicy = SearchTestRepositorySourcePolicy(RepositoryReadPlan.remoteLocalMock()),
                    authSessionRepository = authSessionRepository,
                    authRemoteDataSource =
                        object : AuthRemoteDataSource(HttpJsonClient(baseUrl = "https://example.com")) {
                            override suspend fun reissue(refreshToken: String): ReissueResponseDto =
                                ReissueResponseDto(
                                    accessToken = "refreshed-access-token",
                                    refreshToken = "refreshed-refresh-token",
                                )
                        },
                )

            val analysis = repository.analyzeVoiceSearch(text = "Busan Station", mode = SearchVoiceMode.LOW_VISION)

            assertEquals(remoteAnalysis, analysis)
            assertEquals(2, requestCount)
        }

    @Test
    fun `search falls back to local cache and clears auth session when token refresh fails`() =
        runBlocking {
            val query = SearchQuery(keyword = "custom")
            val cachedResult =
                SearchResult(
                    placeId = "cached-search-1",
                    title = "Cached Search Result",
                    subtitle = "1 Cached-ro, Busan",
                    latitude = 35.1796,
                    longitude = 129.0756,
                )
            val localDataSource =
                SearchLocalDataSource().apply {
                    updateCachedResults(query = query, results = listOf(cachedResult))
                }
            val authSessionRepository =
                TestAuthSessionRepository(
                    initialState =
                        AuthGateState(
                            authSession = AuthSession(accessToken = "expired-token", refreshToken = "refresh-token"),
                            isProfileCompleted = true,
                        ),
                )
            val repository =
                DefaultSearchRepository(
                    remoteDataSource =
                        object : SearchRemoteDataSource(
                            getRequestExecutor = { _, _, _ -> error("unused") },
                            postRequestExecutor = { _, _, _ -> error("unused") },
                        ) {
                            override suspend fun searchPage(query: SearchQuery): SearchPage {
                                throw SearchApiException(
                                    httpStatusCode = 401,
                                    status = "AUTH_401",
                                    message = "인증이 필요합니다.",
                                )
                            }
                        },
                    localDataSource = localDataSource,
                    mockDataSource = SearchMockDataSource(),
                    sourcePolicy =
                        SearchTestRepositorySourcePolicy(
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

            val results = repository.search(query)

            assertEquals(listOf(cachedResult), results)
            assertNull(authSessionRepository.getAuthGateState().authSession)
        }

    @Test
    fun `search throws auth failure when token refresh fails and no cached fallback exists`() =
        runBlocking {
            val authSessionRepository =
                TestAuthSessionRepository(
                    initialState =
                        AuthGateState(
                            authSession = AuthSession(accessToken = "expired-token", refreshToken = "refresh-token"),
                            isProfileCompleted = true,
                        ),
                )
            val repository =
                DefaultSearchRepository(
                    remoteDataSource =
                        object : SearchRemoteDataSource(
                            getRequestExecutor = { _, _, _ -> error("unused") },
                            postRequestExecutor = { _, _, _ -> error("unused") },
                        ) {
                            override suspend fun searchPage(query: SearchQuery): SearchPage {
                                throw SearchApiException(
                                    httpStatusCode = 401,
                                    status = "AUTH_401",
                                    message = "인증이 필요합니다.",
                                )
                            }
                        },
                    localDataSource = SearchLocalDataSource(),
                    mockDataSource = SearchMockDataSource(),
                    sourcePolicy =
                        SearchTestRepositorySourcePolicy(
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

            val failure = runCatching { repository.search(SearchQuery(keyword = "custom")) }.exceptionOrNull() as? SearchApiException

            requireNotNull(failure)
            assertEquals(401, failure.httpStatusCode)
            assertEquals("SEARCH_AUTHENTICATION_FAILED", failure.status)
            assertEquals("인증이 필요합니다.", failure.message)
            assertNull(authSessionRepository.getAuthGateState().authSession)
        }

    @Test
    fun `recent destinations keep latest order and dedupe by place`() =
        runBlocking {
            val repository =
                DefaultSearchRepository(
                    remoteDataSource = SearchRemoteDataSource(baseUrl = "https://example.com"),
                    localDataSource = SearchLocalDataSource(),
                    mockDataSource = SearchMockDataSource(),
                    sourcePolicy = SearchTestRepositorySourcePolicy(RepositoryReadPlan.localOnly()),
                )

            repository.saveRecentDestination(
                RecentDestination(
                    placeId = "place-1",
                    name = "Busan City Hall",
                    address = "1 Jungang-daero, Busan",
                    latitude = 35.1796,
                    longitude = 129.0756,
                    category = PlaceCategory.TOURIST_ATTRACTION,
                    searchedAtMillis = 1_000L,
                ),
            )
            repository.saveRecentDestination(
                RecentDestination(
                    placeId = "place-2",
                    name = "Busan Station",
                    address = "2 Jungang-daero, Busan",
                    latitude = 35.1152,
                    longitude = 129.0416,
                    category = PlaceCategory.ELEVATOR,
                    searchedAtMillis = 2_000L,
                ),
            )
            repository.saveRecentDestination(
                RecentDestination(
                    placeId = "place-1",
                    name = "Busan City Hall",
                    address = "1 Jungang-daero, Busan",
                    latitude = 35.1796,
                    longitude = 129.0756,
                    category = PlaceCategory.TOURIST_ATTRACTION,
                    searchedAtMillis = 3_000L,
                ),
            )

            val results = repository.getRecentDestinations()

            assertEquals(listOf("place-1", "place-2"), results.map { recentDestination -> recentDestination.placeId })
        }

    @Test
    fun `recent searches delete selected keyword with normalized match`() =
        runBlocking {
            val repository =
                DefaultSearchRepository(
                    remoteDataSource = SearchRemoteDataSource(baseUrl = "https://example.com"),
                    localDataSource = SearchLocalDataSource(),
                    mockDataSource = SearchMockDataSource(),
                    sourcePolicy = SearchTestRepositorySourcePolicy(RepositoryReadPlan.localOnly()),
                )

            repository.saveRecentSearch("Busan City Hall")
            repository.saveRecentSearch("Busan Station")
            repository.deleteRecentSearch("  busan city hall ")

            val results = repository.getRecentSearches()

            assertEquals(
                listOf(RecentSearch(keyword = "Busan Station", searchedAtMillis = results.single().searchedAtMillis)),
                results,
            )
        }

    @Test
    fun `recent searches clear all removes every saved keyword`() =
        runBlocking {
            val repository =
                DefaultSearchRepository(
                    remoteDataSource = SearchRemoteDataSource(baseUrl = "https://example.com"),
                    localDataSource = SearchLocalDataSource(),
                    mockDataSource = SearchMockDataSource(),
                    sourcePolicy = SearchTestRepositorySourcePolicy(RepositoryReadPlan.localOnly()),
                )

            repository.saveRecentSearch("Busan City Hall")
            repository.saveRecentSearch("Busan Station")
            repository.clearRecentSearches()

            val results = repository.getRecentSearches()

            assertEquals(emptyList<RecentSearch>(), results)
        }
}

private class SearchTestRepositorySourcePolicy(
    private val plan: RepositoryReadPlan,
) : RepositorySourcePolicy {
    override suspend fun readPlan(domain: RepositoryDomain): RepositoryReadPlan = plan
}

private class FakeAddressSearchResolver(
    private val candidates: List<AddressSearchCandidate>,
) : AddressSearchResolver {
    override suspend fun resolve(query: String, limit: Int): List<AddressSearchCandidate> = candidates.take(limit)
}
