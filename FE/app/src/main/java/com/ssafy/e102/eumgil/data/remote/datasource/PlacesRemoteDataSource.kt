package com.ssafy.e102.eumgil.data.remote.datasource

import android.util.Log
import com.ssafy.e102.eumgil.core.model.MapPlaceClickType
import com.ssafy.e102.eumgil.core.model.MapPlaceDetailRequest
import com.ssafy.e102.eumgil.core.model.MapTappedPlaceDetail
import com.ssafy.e102.eumgil.core.model.PlaceQuery
import com.ssafy.e102.eumgil.core.model.PlaceDetail
import com.ssafy.e102.eumgil.core.model.PlaceSummary
import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import com.ssafy.e102.eumgil.data.remote.HttpJsonResponse
import com.ssafy.e102.eumgil.data.remote.mapper.PlaceDtoMapper
import com.ssafy.e102.eumgil.data.remote.mapper.PlaceDtoMapper.toFeatureTypeApiValueOrNull
import com.ssafy.e102.eumgil.data.remote.mapper.PlaceDtoMapper.toServerCategoryApiValueOrNull
import com.ssafy.e102.eumgil.data.remote.mapper.PlaceDtoMapper.toApiValue
import org.json.JSONObject
import java.util.Locale

open class PlacesRemoteDataSource private constructor(
    private val getRequestExecutor: suspend (String, Map<String, String>, Map<String, String>) -> HttpJsonResponse,
    private val postRequestExecutor: suspend (String, String, Map<String, String>) -> HttpJsonResponse,
    private val accessTokenProvider: suspend () -> String?,
    private val baseUrlLabel: String,
) {
    constructor(
        baseUrl: String,
        accessTokenProvider: suspend () -> String? = { null },
    ) : this(
        getRequestExecutor = { path, queryParams, headers ->
            HttpJsonClient(baseUrl = baseUrl).getJson(
                path = path,
                queryParams = queryParams,
                headers = headers,
            )
        },
        postRequestExecutor = { path, body, headers ->
            HttpJsonClient(baseUrl = baseUrl).postJson(
                path = path,
                body = body,
                headers = headers,
            )
        },
        accessTokenProvider = accessTokenProvider,
        baseUrlLabel = baseUrl,
    )

    internal constructor(
        requestExecutor: suspend (String, Map<String, String>, Map<String, String>) -> HttpJsonResponse,
        accessTokenProvider: suspend () -> String? = { null },
        postRequestExecutor: suspend (String, String, Map<String, String>) -> HttpJsonResponse = { _, _, _ ->
            error("POST request executor is not configured.")
        },
    ) : this(
        getRequestExecutor = requestExecutor,
        postRequestExecutor = postRequestExecutor,
        accessTokenProvider = accessTokenProvider,
        baseUrlLabel = "<test>",
    )

    open suspend fun getPlaces(query: PlaceQuery): List<PlaceSummary> {
        val latitude = requireNotNull(query.latitude) { "places browse requires latitude" }
        val longitude = requireNotNull(query.longitude) { "places browse requires longitude" }
        val queryParams =
            query.toQueryParams(
                latitude = latitude,
                longitude = longitude,
            )
        safeLogInfo(
            PLACES_REMOTE_LOG_TAG,
            "GET /places baseUrl=$baseUrlLabel lat=${latitude.toLogCoordinate()} lng=${longitude.toLogCoordinate()} radius=${query.radiusMeters} categories=${query.categories.toLogList()} featureTypes=${query.featureTypes.toLogList()}",
        )
        val response =
            getRequestExecutor(
                "/places",
                queryParams,
                bearerHeader(),
            )
        val responseJson = response.body.toJsonObjectOrNull()
        val placesBrowseDto = response.requirePlacesBrowseDto(responseJson)
        val places = PlaceDtoMapper.toPlaceSummaries(placesBrowseDto)
        safeLogInfo(
            PLACES_REMOTE_LOG_TAG,
            "GET /places success status=${response.statusCode} count=${places.size}",
        )
        if (places.isEmpty()) {
            safeLogWarn(
                PLACES_REMOTE_LOG_TAG,
                "GET /places returned empty result set lat=${latitude.toLogCoordinate()} lng=${longitude.toLogCoordinate()} radius=${query.radiusMeters}",
            )
        }
        return places
    }

    open suspend fun getPlaceDetail(placeId: String): PlaceDetail? {
        val response =
            getRequestExecutor(
                "/places/$placeId",
                emptyMap(),
                bearerHeader(),
            )
        if (response.statusCode == 404) {
            return null
        }

        val responseJson = response.body.toJsonObjectOrNull()
        val placeDetailDto = response.requirePlaceDetailDto(responseJson)
        return PlaceDtoMapper.toPlaceDetail(placeDetailDto)
    }

    open suspend fun getMapTappedPlaceDetail(request: MapPlaceDetailRequest): MapTappedPlaceDetail? {
        safeLogInfo(
            PLACES_REMOTE_LOG_TAG,
            "POST /places/detail baseUrl=$baseUrlLabel lat=${request.latitude.toLogCoordinate()} lng=${request.longitude.toLogCoordinate()} clickType=${request.clickType.name} provider=${request.provider.orEmpty()} providerPlaceId=${request.providerPlaceId.orEmpty()}",
        )
        val response =
            postRequestExecutor(
                "/places/detail",
                request.toRequestBody(),
                bearerHeader(),
            )
        if (response.statusCode == 404) {
            return null
        }

        val responseJson = response.body.toJsonObjectOrNull()
        val detailDto = response.requireMapPlaceDetailDto(responseJson)
        return PlaceDtoMapper.toMapTappedPlaceDetail(detailDto)
    }

    private suspend fun bearerHeader(): Map<String, String> =
        accessTokenProvider()
            ?.takeIf { accessToken -> accessToken.isNotBlank() }
            ?.let { accessToken -> mapOf("Authorization" to "Bearer $accessToken") }
            .orEmpty()

    private fun PlaceQuery.toQueryParams(
        latitude: Double,
        longitude: Double,
    ): Map<String, String> =
        buildMap {
            put("lat", latitude.toString())
            put("lng", longitude.toString())
            put("radius", radiusMeters.toString())
            val serverCategories =
                categories
                    .mapNotNull { category -> category.toServerCategoryApiValueOrNull() }
                    .distinct()
            val categoryFeatureTypes =
                categories
                    .mapNotNull { category -> category.toFeatureTypeApiValueOrNull() }
                    .distinct()
            if (serverCategories.isNotEmpty()) {
                put(
                    "category",
                    serverCategories
                        .sorted()
                        .joinToString(","),
                )
            }
            val serverFeatureTypes =
                (
                    featureTypes.map { featureType -> featureType.toApiValue() } +
                        categoryFeatureTypes
                ).distinct()
            if (serverFeatureTypes.isNotEmpty()) {
                put(
                    "featureType",
                    serverFeatureTypes
                        .sorted()
                        .joinToString(","),
                )
            }
        }

    private fun MapPlaceDetailRequest.toRequestBody(): String =
        JSONObject()
            .put("lat", latitude)
            .put("lng", longitude)
            .put("clickType", clickType.toApiValue())
            .putOptional("provider", provider)
            .putOptional("providerPlaceId", providerPlaceId)
            .putOptional("nameHint", nameHint)
            .toString()

    private fun MapPlaceClickType.toApiValue(): String = name

    private fun HttpJsonResponse.requirePlacesBrowseDto(responseJson: JSONObject?) =
        if (statusCode in 200..299) {
            runCatching {
                PlaceDtoMapper.parsePlacesBrowseDto(body)
            }.getOrElse { error ->
                throw placesApiException(response = this, responseJson = responseJson, fallback = error.message)
            }
        } else {
            throw placesApiException(response = this, responseJson = responseJson)
        }

    private fun HttpJsonResponse.requirePlaceDetailDto(responseJson: JSONObject?) =
        if (statusCode in 200..299) {
            runCatching {
                PlaceDtoMapper.parsePlaceDetailDto(body)
            }.getOrElse { error ->
                throw placesApiException(response = this, responseJson = responseJson, fallback = error.message)
            }
        } else {
            throw placesApiException(response = this, responseJson = responseJson)
        }

    private fun HttpJsonResponse.requireMapPlaceDetailDto(responseJson: JSONObject?) =
        if (statusCode in 200..299) {
            runCatching {
                PlaceDtoMapper.parseMapPlaceDetailDto(body)
            }.getOrElse { error ->
                throw placesApiException(response = this, responseJson = responseJson, fallback = error.message)
            }
        } else {
            throw placesApiException(response = this, responseJson = responseJson)
        }

    private fun placesApiException(
        response: HttpJsonResponse,
        responseJson: JSONObject?,
        fallback: String? = null,
    ): PlacesApiException =
        PlacesApiException(
            httpStatusCode = response.statusCode,
            status = responseJson?.optString("status").orEmpty(),
            message =
                responseJson?.optString("message")
                    ?.takeIf { message -> message.isNotBlank() }
                    ?: fallback
                    ?: DEFAULT_PLACES_API_ERROR_MESSAGE,
        )

    private fun String.toJsonObjectOrNull(): JSONObject? = runCatching { JSONObject(this) }.getOrNull()

    private fun JSONObject.putOptional(
        name: String,
        value: String?,
    ): JSONObject =
        apply {
            value
                ?.takeIf { it.isNotBlank() }
                ?.let { put(name, it) }
        }

    private companion object {
        private const val PLACES_REMOTE_LOG_TAG = "PlacesRemoteDataSource"
        private const val DEFAULT_PLACES_API_ERROR_MESSAGE = "장소 조회 서버 요청에 실패했습니다."
    }
}

private fun Double.toLogCoordinate(): String = String.format(Locale.US, "%.6f", this)

private fun Collection<Enum<*>>.toLogList(): String =
    if (isEmpty()) {
        "ALL"
    } else {
        joinToString(",") { value -> value.name }
    }

private fun safeLogInfo(
    tag: String,
    message: String,
) {
    runCatching { Log.i(tag, message) }
}

private fun safeLogWarn(
    tag: String,
    message: String,
) {
    runCatching { Log.w(tag, message) }
}

class PlacesApiException(
    val httpStatusCode: Int,
    val status: String,
    override val message: String,
) : RuntimeException(message)
