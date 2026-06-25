package com.ssafy.e102.eumgil.data.remote.datasource

import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import com.ssafy.e102.eumgil.data.remote.HttpJsonResponse
import com.ssafy.e102.eumgil.data.remote.dto.BookmarkAccessibilityFeatureDto
import com.ssafy.e102.eumgil.data.remote.dto.BookmarkListItemDto
import com.ssafy.e102.eumgil.data.remote.dto.BookmarkPageDto
import com.ssafy.e102.eumgil.data.remote.dto.BookmarkPointDto
import com.ssafy.e102.eumgil.data.remote.dto.CreateBookmarkRequestDto
import com.ssafy.e102.eumgil.data.remote.dto.CreateBookmarkResponseDto
import org.json.JSONArray
import org.json.JSONObject

open class BookmarksRemoteDataSource(
    private val httpJsonClient: HttpJsonClient,
) {
    open suspend fun getBookmarks(
        accessToken: String,
        cursor: Long? = null,
        size: Int? = null,
    ): BookmarkPageDto {
        val queryParams =
            buildMap {
                cursor?.let { put("cursor", it.toString()) }
                size?.let { put("size", it.toString()) }
            }

        val response =
            httpJsonClient.getJson(
                path = "/bookmarks",
                queryParams = queryParams,
                headers = bearerHeader(accessToken),
            )
        val responseJson = response.body.toJsonObjectOrNull()
        val dataJson = response.requireDataJson(responseJson)

        return dataJson.toBookmarkPageDto()
    }

    open suspend fun createBookmark(
        accessToken: String,
        placeId: Long,
    ): CreateBookmarkResponseDto =
        createBookmark(
            accessToken = accessToken,
            request = CreateBookmarkRequestDto(placeId = placeId),
        )

    open suspend fun createBookmark(
        accessToken: String,
        request: CreateBookmarkRequestDto,
    ): CreateBookmarkResponseDto {
        val bodyJson = request.toJsonObject()
        val response =
            httpJsonClient.postJson(
                path = "/bookmarks",
                body = bodyJson.toString(),
                headers = bearerHeader(accessToken),
            )
        val responseJson = response.body.toJsonObjectOrNull()
        val dataJson = response.requireDataJson(responseJson)

        return CreateBookmarkResponseDto(
            bookmarkId =
                dataJson.optLongOrNull("bookmarkId")
                    ?: throw bookmarksApiException(response, responseJson),
            bookmarkTargetId =
                dataJson.optNullableString("bookmarkTargetId")
                    ?: throw bookmarksApiException(response, responseJson),
            targetType =
                dataJson.optNullableString("targetType")
                    ?: throw bookmarksApiException(response, responseJson),
            placeId = dataJson.optLongOrNull("placeId"),
        )
    }

    open suspend fun deleteBookmarkByTargetId(
        accessToken: String,
        bookmarkTargetId: String,
    ) {
        val response =
            httpJsonClient.deleteJson(
                path = "/bookmarks/targets/$bookmarkTargetId",
                headers = bearerHeader(accessToken),
            )

        if (response.statusCode !in 200..299) {
            val responseJson = response.body.toJsonObjectOrNull()
            throw bookmarksApiException(response, responseJson)
        }
    }

    open suspend fun deleteBookmark(
        accessToken: String,
        placeId: Long,
    ) {
        val response =
            httpJsonClient.deleteJson(
                path = "/bookmarks/places/$placeId",
                headers = bearerHeader(accessToken),
            )

        if (response.statusCode !in 200..299) {
            val responseJson = response.body.toJsonObjectOrNull()
            throw bookmarksApiException(response, responseJson)
        }
    }

    private fun bearerHeader(accessToken: String): Map<String, String> = mapOf("Authorization" to "Bearer $accessToken")

    private fun JSONObject.toBookmarkPageDto(): BookmarkPageDto {
        val contentJson = optJSONArray("content") ?: JSONArray()
        val items =
            (0 until contentJson.length()).map { index ->
                contentJson.getJSONObject(index).toBookmarkListItemDto()
            }

        return BookmarkPageDto(
            content = items,
            size = optInt("size"),
            nextCursor = optLongOrNull("nextCursor"),
            hasNext = optBoolean("hasNext"),
        )
    }

    private fun JSONObject.toBookmarkListItemDto(): BookmarkListItemDto {
        val pointJson =
            optJSONObject("point")
                ?: throw BookmarksApiException(
                    httpStatusCode = 0,
                    status = "",
                    message = DEFAULT_BOOKMARKS_API_ERROR_MESSAGE,
                )

        return BookmarkListItemDto(
            bookmarkId = optLong("bookmarkId"),
            bookmarkTargetId = optString("bookmarkTargetId"),
            targetType = optString("targetType"),
            placeId = optLongOrNull("placeId"),
            provider = optNullableString("provider"),
            providerPlaceId = optNullableString("providerPlaceId"),
            name = optString("name"),
            category = optNullableString("category"),
            providerCategory = optNullableString("providerCategory"),
            address = optNullableString("address"),
            point =
                BookmarkPointDto(
                    lat = pointJson.optDouble("lat"),
                    lng = pointJson.optDouble("lng"),
                ),
            accessibilityFeatures =
                optJSONArray("accessibilityFeatures")
                    ?.toBookmarkAccessibilityFeatureDtos()
                    .orEmpty(),
        )
    }

    private fun CreateBookmarkRequestDto.toJsonObject(): JSONObject =
        JSONObject().apply {
            placeId?.let { put("placeId", it) }
            putIfNotBlank("provider", provider)
            putIfNotBlank("providerPlaceId", providerPlaceId)
            putIfNotBlank("name", name)
            putIfNotBlank("providerCategory", providerCategory)
            putIfNotBlank("address", address)
            point?.let { point ->
                put(
                    "point",
                    JSONObject()
                        .put("lat", point.lat)
                        .put("lng", point.lng),
                )
            }
        }

    private fun JSONArray.toBookmarkAccessibilityFeatureDtos(): List<BookmarkAccessibilityFeatureDto> =
        List(length()) { index ->
            getJSONObject(index).let { featureJson ->
                BookmarkAccessibilityFeatureDto(
                    featureType = featureJson.optString("featureType"),
                    isAvailable = featureJson.optBoolean("isAvailable"),
                )
            }
        }

    private fun String.toJsonObjectOrNull(): JSONObject? = runCatching { JSONObject(this) }.getOrNull()

    private fun HttpJsonResponse.requireDataJson(responseJson: JSONObject?): JSONObject {
        if (statusCode !in 200..299) {
            throw bookmarksApiException(this, responseJson)
        }

        return responseJson?.optJSONObject("data")
            ?: throw bookmarksApiException(this, responseJson)
    }

    private fun bookmarksApiException(
        response: HttpJsonResponse,
        responseJson: JSONObject?,
    ): BookmarksApiException =
        BookmarksApiException(
            httpStatusCode = response.statusCode,
            status = responseJson?.optString("status").orEmpty(),
            message =
                responseJson?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_BOOKMARKS_API_ERROR_MESSAGE,
        )

    private fun JSONObject.optNullableString(name: String): String? =
        if (isNull(name)) {
            null
        } else {
            optString(name).takeIf { it.isNotBlank() }
        }

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (isNull(name) || has(name).not()) {
            null
        } else {
            optLong(name, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
        }

    private fun JSONObject.putIfNotBlank(
        name: String,
        value: String?,
    ) {
        value?.takeIf { it.isNotBlank() }?.let { put(name, it) }
    }

    private companion object {
        private const val DEFAULT_BOOKMARKS_API_ERROR_MESSAGE = "북마크 서버 요청에 실패했습니다."
    }
}

class BookmarksApiException(
    val httpStatusCode: Int,
    val status: String,
    override val message: String,
) : RuntimeException(message)
