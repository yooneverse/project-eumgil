package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.location.ANDROID_GEOCODER_PROVIDER
import com.ssafy.e102.eumgil.core.location.AddressSearchCandidate
import com.ssafy.e102.eumgil.core.location.AddressSearchResolver
import com.ssafy.e102.eumgil.core.location.NoOpAddressSearchResolver
import com.ssafy.e102.eumgil.core.model.RecentDestination
import com.ssafy.e102.eumgil.core.model.RecentSearch
import com.ssafy.e102.eumgil.core.model.SearchPage
import com.ssafy.e102.eumgil.core.model.SearchQuery
import com.ssafy.e102.eumgil.core.model.SearchResult
import com.ssafy.e102.eumgil.core.model.SearchVoiceAnalysis
import com.ssafy.e102.eumgil.core.model.SearchVoiceIntent
import com.ssafy.e102.eumgil.core.model.SearchVoiceMode
import com.ssafy.e102.eumgil.data.local.datasource.SearchLocalDataSource
import com.ssafy.e102.eumgil.data.mock.datasource.SearchMockDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.AuthRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.SearchApiException
import com.ssafy.e102.eumgil.data.remote.datasource.SearchRemoteDataSource
import com.ssafy.e102.eumgil.data.repository.policy.RepositoryDomain
import com.ssafy.e102.eumgil.data.repository.policy.RepositorySource
import com.ssafy.e102.eumgil.data.repository.policy.RepositorySourcePolicy
import java.util.Locale

interface SearchRepository {
    suspend fun search(query: SearchQuery): List<SearchResult>

    suspend fun searchPage(query: SearchQuery): SearchPage =
        SearchPage(results = search(query))

    suspend fun analyzeVoiceSearch(
        text: String,
        mode: SearchVoiceMode = SearchVoiceMode.MOBILITY_IMPAIRED,
    ): SearchVoiceAnalysis {
        val normalizedText = text.trim()
        return if (normalizedText.isEmpty()) {
            SearchVoiceAnalysis(intent = SearchVoiceIntent.UNKNOWN)
        } else {
            SearchVoiceAnalysis(
                intent = SearchVoiceIntent.PLACE_SEARCH,
                placeName = normalizedText,
            )
        }
    }

    suspend fun getRecentSearches(): List<RecentSearch>

    suspend fun saveRecentSearch(keyword: String)

    suspend fun deleteRecentSearch(keyword: String) = Unit

    suspend fun clearRecentSearches() = Unit

    suspend fun getRecentDestinations(): List<RecentDestination>

    suspend fun saveRecentDestination(destination: RecentDestination)
}

class DefaultSearchRepository(
    private val remoteDataSource: SearchRemoteDataSource,
    private val localDataSource: SearchLocalDataSource,
    private val mockDataSource: SearchMockDataSource,
    private val sourcePolicy: RepositorySourcePolicy,
    authSessionRepository: AuthSessionRepository? = null,
    authRemoteDataSource: AuthRemoteDataSource? = null,
    private val addressSearchResolver: AddressSearchResolver = NoOpAddressSearchResolver,
) : SearchRepository {
    private val authenticatedRequestRunner =
        if (authSessionRepository != null && authRemoteDataSource != null) {
            AuthenticatedRequestRunner(
                authSessionRepository = authSessionRepository,
                authRemoteDataSource = authRemoteDataSource,
            )
        } else {
            null
        }

    override suspend fun search(query: SearchQuery): List<SearchResult> = searchPage(query).results

    override suspend fun searchPage(query: SearchQuery): SearchPage {
        val readPlan = sourcePolicy.readPlan(RepositoryDomain.SEARCH)
        val lastSource = readPlan.sources.last()
        var remoteFailure: Throwable? = null

        for (source in readPlan.sources) {
            when (source) {
                RepositorySource.REMOTE -> {
                    val remoteResult = runCatching { runAuthenticatedRemoteRequest { remoteDataSource.searchPage(query) } }
                    if (remoteResult.isSuccess) {
                        val searchPage =
                            resolveAddressFallbackPage(
                                query = query,
                                remotePage = remoteResult.getOrDefault(SearchPage(results = emptyList())),
                            )
                        localDataSource.updateCachedResults(query = query, results = searchPage.results)
                        return searchPage
                    }
                    remoteFailure = remoteResult.exceptionOrNull()
                }

                RepositorySource.LOCAL -> {
                    val cachedResults = localDataSource.getCachedResults(query)
                    if (cachedResults.isNotEmpty()) {
                        return SearchPage(results = cachedResults)
                    }
                    if (source == lastSource && remoteFailure == null) {
                        return SearchPage(results = cachedResults)
                    }
                }

                RepositorySource.MOCK -> return SearchPage(results = mockDataSource.search(query))
            }
        }

        throw remoteFailure ?: IllegalStateException("No search data source matched the current policy.")
    }

    override suspend fun analyzeVoiceSearch(
        text: String,
        mode: SearchVoiceMode,
    ): SearchVoiceAnalysis {
        val normalizedText = text.trim()
        if (normalizedText.isEmpty()) {
            return super<SearchRepository>.analyzeVoiceSearch(text = normalizedText, mode = mode)
        }

        val readPlan = sourcePolicy.readPlan(RepositoryDomain.SEARCH)
        return if (RepositorySource.REMOTE in readPlan.sources) {
            runAuthenticatedRemoteRequest {
                remoteDataSource.analyzeVoiceSearch(
                    text = normalizedText,
                    mode = mode,
                )
            }
        } else {
            super<SearchRepository>.analyzeVoiceSearch(text = normalizedText, mode = mode)
        }
    }

    override suspend fun getRecentSearches(): List<RecentSearch> = localDataSource.getRecentSearches()

    override suspend fun saveRecentSearch(keyword: String) {
        localDataSource.saveRecentSearch(keyword)
    }

    override suspend fun deleteRecentSearch(keyword: String) {
        localDataSource.deleteRecentSearch(keyword)
    }

    override suspend fun clearRecentSearches() {
        localDataSource.clearRecentSearches()
    }

    override suspend fun getRecentDestinations(): List<RecentDestination> = localDataSource.getRecentDestinations()

    override suspend fun saveRecentDestination(destination: RecentDestination) {
        localDataSource.saveRecentDestination(destination)
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
                throw SearchApiException(
                    httpStatusCode = HTTP_UNAUTHORIZED,
                    status = SEARCH_STATUS_MISSING_SESSION,
                    message = AUTH_REQUIRED_MESSAGE,
                )

            AuthenticatedRequestResult.AuthenticationFailed ->
                throw SearchApiException(
                    httpStatusCode = HTTP_UNAUTHORIZED,
                    status = SEARCH_STATUS_AUTHENTICATION_FAILED,
                    message = AUTH_REQUIRED_MESSAGE,
                )

            is AuthenticatedRequestResult.Success -> result.value
        }
    }

    private fun isAuthenticationFailure(throwable: Throwable): Boolean =
        throwable is SearchApiException &&
            throwable.httpStatusCode == HTTP_UNAUTHORIZED

    private suspend fun resolveAddressFallbackPage(
        query: SearchQuery,
        remotePage: SearchPage,
    ): SearchPage {
        if (!shouldUseAddressFallback(query = query, remotePage = remotePage)) {
            return remotePage
        }

        val fallbackResults =
            runCatching {
                addressSearchResolver.resolve(
                    query = query.normalizedKeyword,
                    limit = query.limit,
                )
            }.getOrDefault(emptyList())
                .map(AddressSearchCandidate::toSearchResult)

        return if (fallbackResults.isEmpty()) {
            remotePage
        } else {
            SearchPage(
                results = fallbackResults,
                nextCursor = null,
                hasNext = false,
                size = fallbackResults.size,
                totalElements = fallbackResults.size.toLong(),
            )
        }
    }

    private fun shouldUseAddressFallback(
        query: SearchQuery,
        remotePage: SearchPage,
    ): Boolean =
        query.cursor.isNullOrBlank() &&
            remotePage.results.isEmpty() &&
            !remotePage.hasNext &&
            query.normalizedKeyword.looksLikeAddressQuery()

    private companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val SEARCH_STATUS_MISSING_SESSION = "SEARCH_AUTH_MISSING_SESSION"
        private const val SEARCH_STATUS_AUTHENTICATION_FAILED = "SEARCH_AUTHENTICATION_FAILED"
        private const val AUTH_REQUIRED_MESSAGE = "인증이 필요합니다."
    }
}

private fun String.looksLikeAddressQuery(): Boolean {
    val normalized = trim()
    if (normalized.isEmpty()) return false

    val hasRoadAddressToken =
        ROAD_ADDRESS_QUERY_TOKENS.any { token ->
            normalized.contains(token, ignoreCase = true)
        }
    val hasParcelAddressToken =
        PARCEL_ADDRESS_QUERY_TOKENS.any { token ->
            normalized.contains(token, ignoreCase = true)
        }
    val hasNumber = normalized.any(Char::isDigit)

    return hasRoadAddressToken || (hasParcelAddressToken && hasNumber)
}

private fun AddressSearchCandidate.toSearchResult(): SearchResult {
    val providerPlaceId = String.format(Locale.US, "%.6f,%.6f", latitude, longitude)

    return SearchResult(
        placeId = "provider:${ANDROID_GEOCODER_PROVIDER.lowercase(Locale.US)}:$providerPlaceId",
        title = title,
        subtitle = address,
        latitude = latitude,
        longitude = longitude,
        serverPlaceId = null,
        provider = ANDROID_GEOCODER_PROVIDER,
        providerPlaceId = providerPlaceId,
        matched = false,
    )
}

private val ROAD_ADDRESS_QUERY_TOKENS =
    listOf(
        "대로",
        "로",
        "길",
        "번길",
        "road",
        "street",
        "avenue",
        "boulevard",
        "-ro",
        "-gil",
    )

private val PARCEL_ADDRESS_QUERY_TOKENS =
    listOf(
        "동",
        "읍",
        "면",
        "리",
        "번지",
        "호",
    )
