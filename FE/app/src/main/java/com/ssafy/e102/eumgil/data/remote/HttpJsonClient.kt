package com.ssafy.e102.eumgil.data.remote

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DEFAULT_HTTP_TIMEOUT_MILLIS = 10_000

data class HttpJsonResponse(
    val statusCode: Int,
    val body: String,
)

data class HttpJsonTimeoutConfig(
    val connectTimeoutMillis: Int = DEFAULT_HTTP_TIMEOUT_MILLIS,
    val readTimeoutMillis: Int = DEFAULT_HTTP_TIMEOUT_MILLIS,
)

class HttpJsonClient(
    private val baseUrl: String,
    private val timeoutConfig: HttpJsonTimeoutConfig = HttpJsonTimeoutConfig(),
) {
    constructor(
        baseUrl: String,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
    ) : this(
        baseUrl = baseUrl,
        timeoutConfig =
            HttpJsonTimeoutConfig(
                connectTimeoutMillis = connectTimeoutMillis,
                readTimeoutMillis = readTimeoutMillis,
            ),
    )

    suspend fun getJson(
        path: String,
        queryParams: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): HttpJsonResponse =
        withContext(Dispatchers.IO) {
            val connection = openConnection(path, queryParams)
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }

            connection.toHttpJsonResponse()
        }

    suspend fun postJson(
        path: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpJsonResponse =
        withContext(Dispatchers.IO) {
            val connection = openConnection(path)
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }

            connection.outputStream.use { outputStream ->
                outputStream.write(body.toByteArray(Charsets.UTF_8))
            }

            connection.toHttpJsonResponse()
        }

    suspend fun patchJson(
        path: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpJsonResponse =
        withContext(Dispatchers.IO) {
            val connection = openConnection(path)
            connection.requestMethod = "PATCH"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }

            connection.outputStream.use { outputStream ->
                outputStream.write(body.toByteArray(Charsets.UTF_8))
            }

            connection.toHttpJsonResponse()
        }

    suspend fun deleteJson(
        path: String,
        queryParams: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): HttpJsonResponse =
        withContext(Dispatchers.IO) {
            val connection = openConnection(path, queryParams)
            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }

            connection.toHttpJsonResponse()
        }

    private fun openConnection(
        path: String,
        queryParams: Map<String, String> = emptyMap(),
    ): HttpURLConnection =
        URL(buildUrl(path, queryParams)).openConnection().let { connection ->
            (connection as HttpURLConnection).apply {
                connectTimeout = timeoutConfig.connectTimeoutMillis
                readTimeout = timeoutConfig.readTimeoutMillis
            }
        }

    private fun buildUrl(
        path: String,
        queryParams: Map<String, String>,
    ): String {
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        val normalizedPath = path.trimStart('/')
        val urlWithoutQuery = "$normalizedBaseUrl/$normalizedPath"

        if (queryParams.isEmpty()) return urlWithoutQuery

        val query =
            queryParams.entries.joinToString("&") { (key, value) ->
                "${urlEncode(key)}=${urlEncode(value)}"
            }
        return "$urlWithoutQuery?$query"
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun HttpURLConnection.toHttpJsonResponse(): HttpJsonResponse {
        val statusCode = responseCode
        val responseBody =
            runCatching {
                val stream = if (statusCode in 200..299) inputStream else errorStream
                stream?.readUtf8().orEmpty()
            }.getOrDefault("")

        disconnect()
        return HttpJsonResponse(statusCode = statusCode, body = responseBody)
    }

    private fun InputStream.readUtf8(): String =
        BufferedReader(InputStreamReader(this, Charsets.UTF_8)).use { reader ->
            buildString {
                while (true) {
                    val line = reader.readLine() ?: break
                    append(line)
                }
            }
        }

}
