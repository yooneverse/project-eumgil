package com.ssafy.e102.eumgil.data.remote.datasource

import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import com.ssafy.e102.eumgil.data.remote.HttpJsonResponse
import com.ssafy.e102.eumgil.data.remote.dto.CreateFavoriteRouteResponseDto
import com.ssafy.e102.eumgil.data.remote.dto.FavoriteRouteDetailDto
import com.ssafy.e102.eumgil.data.remote.dto.FavoriteRouteListItemDto
import com.ssafy.e102.eumgil.data.remote.dto.FavoriteRoutePageDto
import com.ssafy.e102.eumgil.data.remote.dto.FavoriteRoutePointDto
import com.ssafy.e102.eumgil.data.route.toRouteDto
import org.json.JSONArray
import org.json.JSONObject

open class FavoriteRoutesRemoteDataSource(
    private val getRequestExecutor: suspend (String, Map<String, String>, Map<String, String>) -> HttpJsonResponse,
    private val postRequestExecutor: suspend (String, String, Map<String, String>) -> HttpJsonResponse,
    private val patchRequestExecutor: suspend (String, String, Map<String, String>) -> HttpJsonResponse,
    private val deleteRequestExecutor: suspend (String, Map<String, String>) -> HttpJsonResponse,
) {
    constructor(
        httpJsonClient: HttpJsonClient,
    ) : this(
        getRequestExecutor = { path, queryParams, headers ->
            httpJsonClient.getJson(
                path = path,
                queryParams = queryParams,
                headers = headers,
            )
        },
        postRequestExecutor = { path, body, headers ->
            httpJsonClient.postJson(
                path = path,
                body = body,
                headers = headers,
            )
        },
        patchRequestExecutor = { path, body, headers ->
            httpJsonClient.patchJson(
                path = path,
                body = body,
                headers = headers,
            )
        },
        deleteRequestExecutor = { path, headers ->
            httpJsonClient.deleteJson(
                path = path,
                headers = headers,
            )
        },
    )

    open suspend fun getFavoriteRoutes(
        accessToken: String,
        cursor: Long? = null,
        size: Int? = null,
    ): FavoriteRoutePageDto {
        val queryParams =
            buildMap {
                cursor?.let { put("cursor", it.toString()) }
                size?.let { put("size", it.toString()) }
            }

        val response =
            getRequestExecutor(
                "/favorite-routes",
                queryParams,
                bearerHeader(accessToken),
            )
        val responseJson = response.body.toJsonObjectOrNull()
        val dataJson = response.requireDataJson(responseJson)

        return dataJson.toFavoriteRoutePageDto()
    }

    open suspend fun getFavoriteRouteDetail(
        accessToken: String,
        favRouteId: Long,
    ): FavoriteRouteDetailDto {
        val response =
            getRequestExecutor(
                "/favorite-routes/$favRouteId",
                emptyMap(),
                bearerHeader(accessToken),
            )
        val responseJson = response.body.toJsonObjectOrNull()
        val dataJson = response.requireDataJson(responseJson)

        return dataJson.toFavoriteRouteDetailDto()
    }

    open suspend fun createFavoriteRoute(
        accessToken: String,
        routeId: String,
        startLabel: String,
        endLabel: String,
    ): CreateFavoriteRouteResponseDto {
        val requestJson =
            JSONObject()
                .put("routeId", routeId)
                .put("startLabel", startLabel)
                .put("endLabel", endLabel)

        val response =
            postRequestExecutor(
                "/favorite-routes",
                requestJson.toString(),
                bearerHeader(accessToken),
            )
        val responseJson = response.body.toJsonObjectOrNull()
        val dataJson = response.requireDataJson(responseJson)

        return CreateFavoriteRouteResponseDto(
            favRouteId =
                dataJson.optLongOrNull("favRouteId")
                    ?: throw favoriteRoutesApiException(response, responseJson),
        )
    }

    open suspend fun updateFavoriteRoute(
        accessToken: String,
        favRouteId: Long,
        startLabel: String? = null,
        endLabel: String? = null,
    ) {
        val requestJson = JSONObject()
        startLabel?.let { requestJson.put("startLabel", it) }
        endLabel?.let { requestJson.put("endLabel", it) }

        val response =
            patchRequestExecutor(
                "/favorite-routes/$favRouteId",
                requestJson.toString(),
                bearerHeader(accessToken),
            )

        if (response.statusCode !in 200..299) {
            val responseJson = response.body.toJsonObjectOrNull()
            throw favoriteRoutesApiException(response, responseJson)
        }
    }

    open suspend fun deleteFavoriteRoute(
        accessToken: String,
        favRouteId: Long,
    ) {
        val response =
            deleteRequestExecutor(
                "/favorite-routes/$favRouteId",
                bearerHeader(accessToken),
            )

        if (response.statusCode !in 200..299) {
            val responseJson = response.body.toJsonObjectOrNull()
            throw favoriteRoutesApiException(response, responseJson)
        }
    }

    private fun bearerHeader(accessToken: String): Map<String, String> = mapOf("Authorization" to "Bearer $accessToken")

    private fun JSONObject.toFavoriteRoutePageDto(): FavoriteRoutePageDto {
        val contentJson = optJSONArray("content") ?: JSONArray()
        val items =
            (0 until contentJson.length()).map { index ->
                contentJson.getJSONObject(index).toFavoriteRouteListItemDto()
            }

        return FavoriteRoutePageDto(
            content = items,
            size = optInt("size"),
            nextCursor = optLongOrNull("nextCursor"),
            hasNext = optBoolean("hasNext"),
        )
    }

    private fun JSONObject.toFavoriteRouteListItemDto(): FavoriteRouteListItemDto {
        val startPointJson =
            optJSONObject("startPoint")
                ?: throw FavoriteRoutesApiException(
                    httpStatusCode = 0,
                    status = "",
                    message = DEFAULT_FAVORITE_ROUTES_API_ERROR_MESSAGE,
                )
        val endPointJson =
            optJSONObject("endPoint")
                ?: throw FavoriteRoutesApiException(
                    httpStatusCode = 0,
                    status = "",
                    message = DEFAULT_FAVORITE_ROUTES_API_ERROR_MESSAGE,
                )

        return FavoriteRouteListItemDto(
            favRouteId = optLong("favRouteId"),
            routeName = optString("routeName"),
            startLabel = optString("startLabel"),
            endLabel = optString("endLabel"),
            startPoint =
                FavoriteRoutePointDto(
                    lat = startPointJson.optDouble("lat"),
                    lng = startPointJson.optDouble("lng"),
                ),
            endPoint =
                FavoriteRoutePointDto(
                    lat = endPointJson.optDouble("lat"),
                    lng = endPointJson.optDouble("lng"),
                ),
            transportMode = optString("transportMode").takeIf { it.isNotBlank() },
            routeOption = optString("routeOption"),
        )
    }

    private fun JSONObject.toFavoriteRouteDetailDto(): FavoriteRouteDetailDto {
        val startPointJson =
            optJSONObject("startPoint")
                ?: throw FavoriteRoutesApiException(
                    httpStatusCode = 0,
                    status = "",
                    message = DEFAULT_FAVORITE_ROUTES_API_ERROR_MESSAGE,
                )
        val endPointJson =
            optJSONObject("endPoint")
                ?: throw FavoriteRoutesApiException(
                    httpStatusCode = 0,
                    status = "",
                    message = DEFAULT_FAVORITE_ROUTES_API_ERROR_MESSAGE,
                )

        return FavoriteRouteDetailDto(
            favRouteId = optLong("favRouteId"),
            routeName = optString("routeName"),
            startLabel = optString("startLabel"),
            endLabel = optString("endLabel"),
            startPoint =
                FavoriteRoutePointDto(
                    lat = startPointJson.optDouble("lat"),
                    lng = startPointJson.optDouble("lng"),
                ),
            endPoint =
                FavoriteRoutePointDto(
                    lat = endPointJson.optDouble("lat"),
                    lng = endPointJson.optDouble("lng"),
                ),
            transportMode = optString("transportMode").takeIf { it.isNotBlank() },
            routeOption = optString("routeOption"),
            route = optJSONObject("route")?.toRouteDto(),
        )
    }

    private fun String.toJsonObjectOrNull(): JSONObject? = runCatching { JSONObject(this) }.getOrNull()

    private fun HttpJsonResponse.requireDataJson(responseJson: JSONObject?): JSONObject {
        if (statusCode !in 200..299) {
            throw favoriteRoutesApiException(this, responseJson)
        }

        return responseJson?.optJSONObject("data")
            ?: throw favoriteRoutesApiException(this, responseJson)
    }

    private fun favoriteRoutesApiException(
        response: HttpJsonResponse,
        responseJson: JSONObject?,
    ): FavoriteRoutesApiException =
        FavoriteRoutesApiException(
            httpStatusCode = response.statusCode,
            status = responseJson?.optString("status").orEmpty(),
            message =
                responseJson?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_FAVORITE_ROUTES_API_ERROR_MESSAGE,
        )

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (isNull(name)) {
            null
        } else {
            optLong(name, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
        }

    private companion object {
        private const val DEFAULT_FAVORITE_ROUTES_API_ERROR_MESSAGE = "경로 북마크 서버 요청에 실패했습니다."
    }
}

class FavoriteRoutesApiException(
    val httpStatusCode: Int,
    val status: String,
    override val message: String,
) : RuntimeException(message)
