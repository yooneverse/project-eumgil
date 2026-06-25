package com.ssafy.e102.eumgil.data.remote.datasource

import com.ssafy.e102.eumgil.core.model.SearchQuery
import com.ssafy.e102.eumgil.core.model.SearchPage
import com.ssafy.e102.eumgil.core.model.SearchResult
import com.ssafy.e102.eumgil.core.model.SearchVoiceAnalysis
import com.ssafy.e102.eumgil.core.model.SearchVoiceMode
import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import com.ssafy.e102.eumgil.data.remote.HttpJsonResponse
import com.ssafy.e102.eumgil.data.remote.mapper.SearchDtoMapper
import org.json.JSONObject

open class SearchRemoteDataSource internal constructor(
    private val getRequestExecutor: suspend (String, Map<String, String>, Map<String, String>) -> HttpJsonResponse,
    private val postRequestExecutor: suspend (String, String, Map<String, String>) -> HttpJsonResponse,
    private val accessTokenProvider: suspend () -> String? = { null },
) {
    constructor(
        baseUrl: String,
        accessTokenProvider: suspend () -> String? = { null },
    ) : this(
        getRequestExecutor = { path: String, queryParams: Map<String, String>, headers: Map<String, String> ->
            HttpJsonClient(baseUrl = baseUrl).getJson(
                path = path,
                queryParams = queryParams,
                headers = headers,
            )
        },
        postRequestExecutor = { path: String, body: String, headers: Map<String, String> ->
            HttpJsonClient(baseUrl = baseUrl).postJson(
                path = path,
                body = body,
                headers = headers,
            )
        },
        accessTokenProvider = accessTokenProvider,
    )

    open suspend fun search(query: SearchQuery): List<SearchResult> {
        return searchPage(query).results
    }

    open suspend fun searchPage(query: SearchQuery): SearchPage {
        val response =
            getRequestExecutor(
                "/places/search",
                query.toQueryParams(),
                bearerHeader(),
            )
        val responseJson = response.body.toJsonObjectOrNull()
        val searchDto = response.requirePlacesSearchDto(responseJson)
        return SearchDtoMapper.toSearchPage(searchDto)
    }

    open suspend fun analyzeVoiceSearch(
        text: String,
        mode: SearchVoiceMode,
    ): SearchVoiceAnalysis {
        val response =
            postRequestExecutor(
                "/voice/analyze",
                createVoiceAnalyzeBody(
                    text = text,
                    mode = mode,
                ),
                bearerHeader(),
            )
        val responseJson = response.body.toJsonObjectOrNull()
        val analysisDto = response.requireVoiceSearchAnalysisDto(responseJson)
        return SearchDtoMapper.toSearchVoiceAnalysis(analysisDto)
    }

    private suspend fun bearerHeader(): Map<String, String> =
        accessTokenProvider()
            ?.takeIf { accessToken -> accessToken.isNotBlank() }
            ?.let { accessToken -> mapOf("Authorization" to "Bearer $accessToken") }
            .orEmpty()

    private fun SearchQuery.toQueryParams(): Map<String, String> =
        buildMap {
            put("keyword", normalizedKeyword)
            put("size", limit.toString())
            put("sort", sortOption.apiValue)
            latitude?.let { latitude -> put("lat", latitude.toString()) }
            longitude?.let { longitude -> put("lng", longitude.toString()) }
            radiusMeters?.let { radiusMeters -> put("radius", radiusMeters.toString()) }
            cursor?.trim()?.takeIf(String::isNotEmpty)?.let { cursor -> put("cursor", cursor) }
        }

    private fun createVoiceAnalyzeBody(
        text: String,
        mode: SearchVoiceMode,
    ): String =
        JSONObject()
            .put("text", text)
            .put("mode", mode.name)
            .toString()

    private fun HttpJsonResponse.requirePlacesSearchDto(responseJson: JSONObject?) =
        if (statusCode in 200..299) {
            runCatching {
                SearchDtoMapper.parsePlacesSearchDto(body)
            }.getOrElse { error ->
                throw searchApiException(response = this, responseJson = responseJson, fallback = error.message)
            }
        } else {
            throw searchApiException(response = this, responseJson = responseJson)
        }

    private fun HttpJsonResponse.requireVoiceSearchAnalysisDto(responseJson: JSONObject?) =
        if (statusCode in 200..299) {
            runCatching {
                SearchDtoMapper.parseVoiceSearchAnalysisDto(body)
            }.getOrElse { error ->
                throw searchApiException(response = this, responseJson = responseJson, fallback = error.message)
            }
        } else {
            throw searchApiException(response = this, responseJson = responseJson)
        }

    private fun searchApiException(
        response: HttpJsonResponse,
        responseJson: JSONObject?,
        fallback: String? = null,
    ): SearchApiException =
        SearchApiException(
            httpStatusCode = response.statusCode,
            status = responseJson?.optString("status").orEmpty(),
            message =
                responseJson?.optString("message")
                    ?.takeIf { message -> message.isNotBlank() }
                    ?: fallback
                    ?: DEFAULT_SEARCH_API_ERROR_MESSAGE,
        )

    private fun String.toJsonObjectOrNull(): JSONObject? = runCatching { JSONObject(this) }.getOrNull()

    private companion object {
        private const val DEFAULT_SEARCH_API_ERROR_MESSAGE = "검색 결과를 불러오지 못했습니다."
    }
}

class SearchApiException(
    val httpStatusCode: Int,
    val status: String,
    override val message: String,
) : RuntimeException(message)
