package com.ssafy.e102.eumgil.data.remote.datasource

import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import com.ssafy.e102.eumgil.data.remote.HttpJsonResponse
import com.ssafy.e102.eumgil.data.remote.dto.UserTypeResponseDto
import org.json.JSONObject

open class UserTypeRemoteDataSource(
    private val httpJsonClient: HttpJsonClient,
) {
    open suspend fun updateUserType(
        accessToken: String,
        selectedPrimaryUserType: String,
        selectedMobilitySubtype: String?,
    ): UserTypeResponseDto {
        val requestJson =
            JSONObject()
                .put("selectedPrimaryUserType", selectedPrimaryUserType)
                .put(
                    "selectedMobilitySubtype",
                    selectedMobilitySubtype ?: JSONObject.NULL,
                )

        val response =
            httpJsonClient.patchJson(
                path = "/users/me/user-type",
                body = requestJson.toString(),
                headers =
                    mapOf(
                        AUTHORIZATION_HEADER_NAME to "$BEARER_PREFIX $accessToken",
                    ),
            )
        val responseJson = response.body.toJsonObjectOrNull()
        val dataJson = response.requireDataJson(responseJson)

        return UserTypeResponseDto(
            userId = dataJson.optNullableString("userId"),
            selectedPrimaryUserType =
                dataJson.optString("selectedPrimaryUserType")
                    .takeIf { it.isNotBlank() }
                    ?: throw UserTypeUpdateApiException(
                        httpStatusCode = response.statusCode,
                        status = responseJson?.optString("status").orEmpty(),
                        message = DEFAULT_USER_TYPE_UPDATE_ERROR_MESSAGE,
                    ),
            selectedMobilitySubtype = dataJson.optNullableString("selectedMobilitySubtype"),
        )
    }

    private fun String.toJsonObjectOrNull(): JSONObject? =
        runCatching { JSONObject(this) }.getOrNull()

    private fun HttpJsonResponse.requireDataJson(responseJson: JSONObject?): JSONObject {
        if (statusCode !in 200..299) {
            throw UserTypeUpdateApiException(
                httpStatusCode = statusCode,
                status = responseJson?.optString("status").orEmpty(),
                message =
                    responseJson?.optString("message")
                        ?.takeIf { it.isNotBlank() }
                        ?: DEFAULT_USER_TYPE_UPDATE_ERROR_MESSAGE,
            )
        }

        return responseJson?.optJSONObject("data")
            ?: throw UserTypeUpdateApiException(
                httpStatusCode = statusCode,
                status = responseJson?.optString("status").orEmpty(),
                message = DEFAULT_USER_TYPE_UPDATE_ERROR_MESSAGE,
            )
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (isNull(name)) {
            null
        } else {
            optString(name).takeIf { it.isNotBlank() }
        }

    private companion object {
        private const val AUTHORIZATION_HEADER_NAME = "Authorization"
        private const val BEARER_PREFIX = "Bearer"
        private const val DEFAULT_USER_TYPE_UPDATE_ERROR_MESSAGE = "사용자 유형 변경 요청에 실패했습니다."
    }
}

class UserTypeUpdateApiException(
    val httpStatusCode: Int,
    val status: String,
    override val message: String,
) : RuntimeException(message)
