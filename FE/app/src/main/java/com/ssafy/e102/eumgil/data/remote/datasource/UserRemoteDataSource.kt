package com.ssafy.e102.eumgil.data.remote.datasource

import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import com.ssafy.e102.eumgil.data.remote.HttpJsonResponse
import com.ssafy.e102.eumgil.data.remote.dto.UserMeResponseDto
import org.json.JSONObject

open class UserRemoteDataSource(
    private val httpJsonClient: HttpJsonClient,
) {
    open suspend fun getMe(accessToken: String): UserMeResponseDto {
        val response =
            httpJsonClient.getJson(
                path = "/users/me",
                headers =
                    mapOf(
                        AUTHORIZATION_HEADER_NAME to "$BEARER_PREFIX $accessToken",
                    ),
            )
        val responseJson = response.body.toJsonObjectOrNull()
        val dataJson = response.requireDataJson(responseJson)

        return UserMeResponseDto(
            userId = dataJson.optNullableString("userId"),
            socialProvider = dataJson.optNullableString("socialProvider"),
            selectedPrimaryUserType = dataJson.optNullableString("selectedPrimaryUserType"),
            selectedMobilitySubtype = dataJson.optNullableString("selectedMobilitySubtype"),
        )
    }

    open suspend fun withdraw(accessToken: String): String {
        val response =
            httpJsonClient.deleteJson(
                path = "/users/me",
                headers =
                    mapOf(
                        AUTHORIZATION_HEADER_NAME to "$BEARER_PREFIX $accessToken",
                    ),
            )
        val responseJson = response.body.toJsonObjectOrNull()
        response.throwIfNotSuccessful(
            responseJson = responseJson,
            defaultErrorMessage = DEFAULT_USER_WITHDRAW_ERROR_MESSAGE,
        )

        return responseJson?.optString("message")
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_USER_WITHDRAW_SUCCESS_MESSAGE
    }

    private fun String.toJsonObjectOrNull(): JSONObject? =
        runCatching { JSONObject(this) }.getOrNull()

    private fun HttpJsonResponse.requireDataJson(responseJson: JSONObject?): JSONObject {
        throwIfNotSuccessful(
            responseJson = responseJson,
            defaultErrorMessage = DEFAULT_USER_PROFILE_ERROR_MESSAGE,
        )

        return responseJson?.optJSONObject("data")
            ?: throw UserApiException(
                httpStatusCode = statusCode,
                status = responseJson?.optString("status").orEmpty(),
                message = DEFAULT_USER_PROFILE_ERROR_MESSAGE,
            )
    }

    private fun HttpJsonResponse.throwIfNotSuccessful(
        responseJson: JSONObject?,
        defaultErrorMessage: String,
    ) {
        if (statusCode !in 200..299) {
            throw UserApiException(
                httpStatusCode = statusCode,
                status = responseJson?.optString("status").orEmpty(),
                message =
                    responseJson?.optString("message")
                        ?.takeIf { it.isNotBlank() }
                        ?: defaultErrorMessage,
            )
        }
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
        private const val DEFAULT_USER_PROFILE_ERROR_MESSAGE = "User profile request failed."
        private const val DEFAULT_USER_WITHDRAW_ERROR_MESSAGE = "회원탈퇴 처리에 실패했습니다. 다시 시도해주세요."
        private const val DEFAULT_USER_WITHDRAW_SUCCESS_MESSAGE = "회원탈퇴가 완료되었습니다."
    }
}

class UserApiException(
    val httpStatusCode: Int,
    val status: String,
    override val message: String,
) : RuntimeException(message)
