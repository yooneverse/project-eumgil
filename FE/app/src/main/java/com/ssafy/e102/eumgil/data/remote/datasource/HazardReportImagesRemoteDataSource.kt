package com.ssafy.e102.eumgil.data.remote.datasource

import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import com.ssafy.e102.eumgil.data.remote.HttpJsonResponse
import com.ssafy.e102.eumgil.data.remote.dto.PresignedUploadBatchRequestDto
import com.ssafy.e102.eumgil.data.remote.dto.PresignedUploadBatchResponseDto
import com.ssafy.e102.eumgil.data.remote.dto.PresignedUploadRequestDto
import com.ssafy.e102.eumgil.data.remote.dto.PresignedUploadResponseDto
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 제보 이미지 업로드 흐름의 BE/S3 호출을 캡슐화한다 (Task 5.5).
 *
 * 흐름:
 * 1. `requestPresignedUpload` — 서비스 BE에 PUT presigned URL을 요청.
 * 2. `uploadBinary` — 받은 presigned URL로 S3/MinIO에 직접 PUT.
 *
 * 1번은 서비스 base URL 기반이라 `HttpJsonClient`로 처리하고,
 * 2번은 외부 S3 absolute URL이라 별도로 `HttpURLConnection`을 직접 사용한다.
 */
open class HazardReportImagesRemoteDataSource(
    private val httpJsonClient: HttpJsonClient,
) {
    open suspend fun requestPresignedUpload(
        accessToken: String,
        request: PresignedUploadRequestDto,
    ): PresignedUploadResponseDto {
        val requestJson =
            JSONObject()
                .put("fileName", request.fileName)
                .put("contentType", request.contentType)
                .put("contentLength", request.contentLength)

        val response =
            httpJsonClient.postJson(
                path = PRESIGNED_UPLOAD_PATH,
                body = requestJson.toString(),
                headers = mapOf("Authorization" to "Bearer $accessToken"),
            )

        if (response.statusCode !in 200..299) throw response.toApiException()
        val data =
            JSONObject(response.body).optJSONObject("data")
                ?: throw response.toApiException()

        return PresignedUploadResponseDto(
            uploadUrl = data.requireString("uploadUrl"),
            objectKey = data.requireString("objectKey"),
            expiresAt = data.requireString("expiresAt"),
        )
    }

    open suspend fun requestPresignedUploadBatch(
        accessToken: String,
        request: PresignedUploadBatchRequestDto,
    ): PresignedUploadBatchResponseDto {
        val requestJson =
            JSONObject().put(
                "files",
                org.json.JSONArray().apply {
                    request.files.forEach { file ->
                        put(
                            JSONObject()
                                .put("fileName", file.fileName)
                                .put("contentType", file.contentType)
                                .put("contentLength", file.contentLength),
                        )
                    }
                },
            )

        val response =
            httpJsonClient.postJson(
                path = PRESIGNED_UPLOAD_BATCH_PATH,
                body = requestJson.toString(),
                headers = mapOf("Authorization" to "Bearer $accessToken"),
            )

        if (response.statusCode !in 200..299) throw response.toApiException()
        val data = JSONObject(response.body).optJSONObject("data") ?: throw response.toApiException()
        val uploadsJson = data.optJSONArray("uploads") ?: throw response.toApiException()
        return PresignedUploadBatchResponseDto(
            uploads =
                List(uploadsJson.length()) { index ->
                    val upload = uploadsJson.getJSONObject(index)
                    PresignedUploadResponseDto(
                        uploadUrl = upload.requireString("uploadUrl"),
                        objectKey = upload.requireString("objectKey"),
                        expiresAt = upload.requireString("expiresAt"),
                    )
                },
        )
    }

    /**
     * presigned PUT URL로 이미지 binary를 직접 업로드한다.
     *
     * BE 명세: `Content-Type`은 presigned 발급 요청의 `contentType`과 동일해야 한다.
     * 다른 HTTP 헤더(Authorization 등)는 보내지 않는다 — presigned URL 자체에 권한이 서명됨.
     *
     * @return 2xx면 true. 그 외엔 false (호출자가 재시도 정책 결정).
     */
    open suspend fun uploadBinary(
        uploadUrl: String,
        contentType: String,
        body: ByteArray,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val connection =
                (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    doOutput = true
                    setRequestProperty("Content-Type", contentType)
                    setFixedLengthStreamingMode(body.size)
                    connectTimeout = UPLOAD_CONNECT_TIMEOUT_MS
                    readTimeout = UPLOAD_READ_TIMEOUT_MS
                }
            runCatching {
                connection.outputStream.use { it.write(body) }
                val statusCode = connection.responseCode
                statusCode in 200..299
            }.also { connection.disconnect() }.getOrDefault(false)
        }

    private fun JSONObject.requireString(name: String): String =
        optString(name).takeIf { it.isNotBlank() }
            ?: throw HazardReportsApiException(
                httpStatusCode = 0,
                status = "",
                message = "이미지 업로드 응답 형식이 올바르지 않습니다.",
            )

    private fun HttpJsonResponse.toApiException(): HazardReportsApiException {
        val json = runCatching { JSONObject(body) }.getOrNull()
        return HazardReportsApiException(
            httpStatusCode = statusCode,
            status = json?.optString("status").orEmpty(),
            message =
                json?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: "제보 이미지 업로드 URL 발급에 실패했습니다.",
        )
    }

    private companion object {
        private const val PRESIGNED_UPLOAD_PATH = "/hazard-reports/images/presigned-upload"
        private const val PRESIGNED_UPLOAD_BATCH_PATH = "/hazard-reports/images/presigned-upload/batch"
        // S3는 binary 업로드라 일반 JSON 호출보다 timeout을 넉넉히 둔다.
        private const val UPLOAD_CONNECT_TIMEOUT_MS = 10_000
        private const val UPLOAD_READ_TIMEOUT_MS = 30_000
    }
}
