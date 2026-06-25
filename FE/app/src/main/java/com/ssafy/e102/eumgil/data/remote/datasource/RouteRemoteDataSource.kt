package com.ssafy.e102.eumgil.data.remote.datasource

import android.util.Log
import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import com.ssafy.e102.eumgil.data.remote.HttpJsonResponse
import com.ssafy.e102.eumgil.data.remote.HttpJsonTimeoutConfig
import com.ssafy.e102.eumgil.data.route.RoutePointDto
import com.ssafy.e102.eumgil.data.route.RouteRatingRequestDto
import com.ssafy.e102.eumgil.data.route.RouteRatingResponseDto
import com.ssafy.e102.eumgil.data.route.RouteRerouteRequestDto
import com.ssafy.e102.eumgil.data.route.RouteRerouteResponseDto
import com.ssafy.e102.eumgil.data.route.RouteSearchRequestDto
import com.ssafy.e102.eumgil.data.route.RouteSearchResponseDto
import com.ssafy.e102.eumgil.data.route.RouteSelectRequestDto
import com.ssafy.e102.eumgil.data.route.RouteSelectResponseDto
import com.ssafy.e102.eumgil.data.route.RouteSessionResponseDto
import com.ssafy.e102.eumgil.data.route.RouteTransitRefreshRequestDto
import com.ssafy.e102.eumgil.data.route.RouteTransitRefreshResponseDto
import com.ssafy.e102.eumgil.data.route.parseRouteRatingResponseDto
import com.ssafy.e102.eumgil.data.route.parseRouteRerouteResponseDto
import com.ssafy.e102.eumgil.data.route.parseRouteSearchResponseDto
import com.ssafy.e102.eumgil.data.route.parseRouteSelectResponseDto
import com.ssafy.e102.eumgil.data.route.parseRouteSessionResponseDto
import com.ssafy.e102.eumgil.data.route.parseRouteTransitRefreshResponseDto
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.json.JSONObject

open class RouteRemoteDataSource internal constructor(
    private val postRequestExecutor: suspend (String, String, Map<String, String>) -> HttpJsonResponse,
    private val accessTokenProvider: suspend () -> String? = { null },
) {
    constructor(
        baseUrl: String,
        accessTokenProvider: suspend () -> String? = { null },
        timeoutConfig: HttpJsonTimeoutConfig = HttpJsonTimeoutConfig(),
    ) : this(
        postRequestExecutor = createPostRequestExecutor(baseUrl = baseUrl, timeoutConfig = timeoutConfig),
        accessTokenProvider = accessTokenProvider,
    )

    internal constructor(
        baseUrl: String,
        accessTokenProvider: suspend () -> String? = { null },
        timeoutConfig: HttpJsonTimeoutConfig = HttpJsonTimeoutConfig(),
        postRequestExecutorFactory: (
            String,
            HttpJsonTimeoutConfig,
        ) -> suspend (String, String, Map<String, String>) -> HttpJsonResponse,
    ) : this(
        postRequestExecutor = postRequestExecutorFactory(baseUrl, timeoutConfig),
        accessTokenProvider = accessTokenProvider,
    )

    open suspend fun searchWalkRoutes(request: RouteSearchRequestDto): RouteSearchResponseDto =
        postRouteRequest(
            path = "/routes/search/walk",
            body = createRouteSearchBody(request),
            responseParser = ::parseRouteSearchResponseDto,
        )

    open suspend fun searchTransitRoutes(request: RouteSearchRequestDto): RouteSearchResponseDto =
        postRouteRequest(
            path = "/routes/search/transit",
            body = createRouteSearchBody(request),
            responseParser = ::parseRouteSearchResponseDto,
        )

    open suspend fun selectRoute(
        routeId: String,
        request: RouteSelectRequestDto,
    ): RouteSelectResponseDto =
        postRouteRequest(
            path = "/routes/$routeId/select",
            body = createSelectRouteBody(request),
            responseParser = ::parseRouteSelectResponseDto,
        )

    open suspend fun refreshTransit(
        routeId: String,
        request: RouteTransitRefreshRequestDto,
    ): RouteTransitRefreshResponseDto =
        postRouteRequest(
            path = "/routes/$routeId/transit-refresh",
            body = createTransitRefreshBody(request),
            responseParser = ::parseRouteTransitRefreshResponseDto,
        )

    open suspend fun reroute(request: RouteRerouteRequestDto): RouteRerouteResponseDto =
        postRouteRequest(
            path = "/routes/reroute",
            body = createRerouteBody(request),
            responseParser = ::parseRouteRerouteResponseDto,
        )

    open suspend fun endRoute(routeId: String): RouteSessionResponseDto =
        postRouteRequest(
            path = "/routes/$routeId/end",
            body = "",
            responseParser = ::parseRouteSessionResponseDto,
        )

    open suspend fun rateRoute(request: RouteRatingRequestDto): RouteRatingResponseDto =
        postRouteRequest(
            path = "/route-ratings",
            body = createRouteRatingBody(request),
            responseParser = ::parseRouteRatingResponseDto,
        )

    private suspend fun <T> postRouteRequest(
        path: String,
        body: String,
        responseParser: (String) -> T,
    ): T {
        val startedAtNanos = System.nanoTime()
        return try {
            val response = postRequestExecutor(path, body, bearerHeader())
            val responseJson = response.body.toJsonObjectOrNull()
            val result =
                response.requireRouteResponse(
                    responseJson = responseJson,
                    responseParser = responseParser,
                )
            logRouteSuccess(
                path = path,
                durationMillis = startedAtNanos.elapsedMillis(),
                httpStatusCode = response.statusCode,
            )
            result
        } catch (error: RouteApiException) {
            logRouteFailure(
                path = path,
                durationMillis = startedAtNanos.elapsedMillis(),
                error = error,
            )
            throw error
        } catch (error: IOException) {
            val normalizedError = normalizeNetworkFailure(error)
            logRouteFailure(
                path = path,
                durationMillis = startedAtNanos.elapsedMillis(),
                error = normalizedError,
            )
            throw normalizedError
        }
    }

    private suspend fun bearerHeader(): Map<String, String> =
        accessTokenProvider()
            ?.takeIf { accessToken -> accessToken.isNotBlank() }
            ?.let { accessToken -> mapOf("Authorization" to "Bearer $accessToken") }
            .orEmpty()

    private fun createRouteSearchBody(request: RouteSearchRequestDto): String =
        JSONObject()
            .put("startPoint", request.startPoint.toJsonObject())
            .put("endPoint", request.endPoint.toJsonObject())
            .toString()

    private fun createSelectRouteBody(request: RouteSelectRequestDto): String =
        JSONObject()
            .put("searchId", request.searchId)
            .toString()

    private fun createTransitRefreshBody(request: RouteTransitRefreshRequestDto): String =
        JSONObject()
            .put("legSequence", request.legSequence)
            .toString()

    private fun createRerouteBody(request: RouteRerouteRequestDto): String =
        JSONObject()
            .put("routeId", request.routeId)
            .put("currentPoint", request.currentPoint.toJsonObject())
            .toString()

    private fun createRouteRatingBody(request: RouteRatingRequestDto): String =
        JSONObject()
            .put("sessionId", request.sessionId)
            .put("score", request.score)
            .toString()

    private fun RoutePointDto.toJsonObject(): JSONObject =
        JSONObject()
            .put("lat", lat)
            .put("lng", lng)

    private fun <T> HttpJsonResponse.requireRouteResponse(
        responseJson: JSONObject?,
        responseParser: (String) -> T,
    ): T =
        if (statusCode in 200..299) {
            runCatching {
                responseParser(body)
            }.getOrElse { error ->
                throw routeApiException(
                    response = this,
                    responseJson = responseJson,
                    fallback = error.message,
                    failureKind = RouteFailureKind.RESPONSE_PARSING,
                )
            }
        } else {
            throw routeApiException(
                response = this,
                responseJson = responseJson,
                failureKind = RouteFailureKind.HTTP_RESPONSE,
            )
        }

    private fun routeApiException(
        response: HttpJsonResponse,
        responseJson: JSONObject?,
        fallback: String? = null,
        failureKind: RouteFailureKind,
    ): RouteApiException =
        RouteApiException(
            httpStatusCode = response.statusCode,
            status = responseJson?.optString("status").orEmpty(),
            message =
                responseJson?.optString("message")
                    ?.takeIf(String::isNotBlank)
                    ?: fallback
                    ?: DEFAULT_ROUTE_API_ERROR_MESSAGE,
            failureKind = failureKind,
        )

    private fun normalizeNetworkFailure(error: IOException): RouteApiException =
        when (error) {
            is SocketTimeoutException ->
                RouteApiException(
                    httpStatusCode = NO_HTTP_STATUS_CODE,
                    status = ROUTE_STATUS_CLIENT_TIMEOUT,
                    message = ROUTE_CLIENT_TIMEOUT_MESSAGE,
                    failureKind = RouteFailureKind.CLIENT_TIMEOUT,
                )

            is UnknownHostException ->
                RouteApiException(
                    httpStatusCode = NO_HTTP_STATUS_CODE,
                    status = ROUTE_STATUS_UNKNOWN_HOST,
                    message = ROUTE_NETWORK_UNAVAILABLE_MESSAGE,
                    failureKind = RouteFailureKind.UNKNOWN_HOST,
                )

            is ConnectException ->
                RouteApiException(
                    httpStatusCode = NO_HTTP_STATUS_CODE,
                    status = ROUTE_STATUS_CONNECTION_FAILED,
                    message = ROUTE_NETWORK_UNAVAILABLE_MESSAGE,
                    failureKind = RouteFailureKind.CONNECTION_FAILURE,
                )

            else ->
                RouteApiException(
                    httpStatusCode = NO_HTTP_STATUS_CODE,
                    status = ROUTE_STATUS_NETWORK_IO_ERROR,
                    message = ROUTE_NETWORK_IO_ERROR_MESSAGE,
                    failureKind = RouteFailureKind.NETWORK_IO,
                )
        }

    private fun logRouteSuccess(
        path: String,
        durationMillis: Long,
        httpStatusCode: Int,
    ) {
        safeLogInfo(
            ROUTE_REMOTE_LOG_TAG,
            "routeRequest path=$path result=success durationMs=$durationMillis httpStatus=$httpStatusCode",
        )
    }

    private fun logRouteFailure(
        path: String,
        durationMillis: Long,
        error: RouteApiException,
    ) {
        safeLogWarn(
            ROUTE_REMOTE_LOG_TAG,
            "routeRequest path=$path result=failure durationMs=$durationMillis layer=remote failureKind=${error.failureKind.name} httpStatus=${error.httpStatusCode} status=${error.status} message=\"${error.message.logFieldValue()}\"",
        )
    }

    private fun String.toJsonObjectOrNull(): JSONObject? = runCatching { JSONObject(this) }.getOrNull()

    private companion object {
        private const val DEFAULT_ROUTE_API_ERROR_MESSAGE = "경로를 불러오지 못했습니다."
        private const val ROUTE_REMOTE_LOG_TAG = "RouteRemoteDataSource"
        private const val NO_HTTP_STATUS_CODE = 0
        private const val ROUTE_STATUS_CLIENT_TIMEOUT = "ROUTE_CLIENT_TIMEOUT"
        private const val ROUTE_STATUS_CONNECTION_FAILED = "ROUTE_CONNECTION_FAILED"
        private const val ROUTE_STATUS_UNKNOWN_HOST = "ROUTE_UNKNOWN_HOST"
        private const val ROUTE_STATUS_NETWORK_IO_ERROR = "ROUTE_NETWORK_IO_ERROR"
        private const val ROUTE_CLIENT_TIMEOUT_MESSAGE = "경로 응답이 지연되고 있습니다. 잠시 후 다시 시도해주세요."
        private const val ROUTE_NETWORK_UNAVAILABLE_MESSAGE = "경로 서버에 연결할 수 없습니다. 네트워크 상태를 확인한 뒤 다시 시도해주세요."
        private const val ROUTE_NETWORK_IO_ERROR_MESSAGE = "경로를 불러오지 못했습니다. 잠시 후 다시 시도해주세요."

        private fun createPostRequestExecutor(
            baseUrl: String,
            timeoutConfig: HttpJsonTimeoutConfig,
        ): suspend (String, String, Map<String, String>) -> HttpJsonResponse =
            { path: String, body: String, headers: Map<String, String> ->
                HttpJsonClient(
                    baseUrl = baseUrl,
                    timeoutConfig = timeoutConfig,
                ).postJson(
                    path = path,
                    body = body,
                    headers = headers,
                )
            }
    }
}

enum class RouteFailureKind {
    HTTP_RESPONSE,
    RESPONSE_PARSING,
    CLIENT_TIMEOUT,
    CONNECTION_FAILURE,
    UNKNOWN_HOST,
    NETWORK_IO,
}

class RouteApiException(
    val httpStatusCode: Int,
    val status: String,
    override val message: String,
    val failureKind: RouteFailureKind = RouteFailureKind.HTTP_RESPONSE,
) : RuntimeException(message)

private fun Long.elapsedMillis(): Long = (System.nanoTime() - this) / 1_000_000

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

private fun String.logFieldValue(): String =
    replace('"', '\'').replace('\n', ' ').replace('\r', ' ')
