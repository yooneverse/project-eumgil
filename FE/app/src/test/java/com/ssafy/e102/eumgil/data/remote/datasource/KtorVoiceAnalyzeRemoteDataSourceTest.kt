package com.ssafy.e102.eumgil.data.remote.datasource

import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import com.ssafy.e102.eumgil.data.remote.HttpJsonResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class KtorVoiceAnalyzeRemoteDataSourceTest {
    @Test
    fun `analyze sends bearer token when access token is available`() = runTest {
        var capturedHeaders = emptyMap<String, String>()
        val dataSource =
            KtorVoiceAnalyzeRemoteDataSource(
                httpJsonClient = HttpJsonClient(baseUrl = "https://example.com"),
                accessTokenProvider = { "access-token" },
                postRequestExecutor = { _, _, headers ->
                    capturedHeaders = headers
                    HttpJsonResponse(
                        statusCode = 200,
                        body =
                            """
                            {
                              "status": "S2000",
                              "data": {
                                "intent": "PLACE_SEARCH",
                                "placeName": "부산역",
                                "category": null,
                                "bookmarkAction": null,
                                "departure": null,
                                "destination": null,
                                "reportType": null,
                                "description": null,
                                "confirmed": true,
                                "confirmationMessage": null
                              },
                              "message": "정상 처리되었습니다."
                            }
                            """.trimIndent(),
                    )
                },
            )

        val result = dataSource.analyze(
            text = "부산역",
            mode = "LOW_VISION",
            history = emptyList(),
            currentRoute = null,
        )

        assertEquals("Bearer access-token", capturedHeaders["Authorization"])
        assertEquals("PLACE_SEARCH", result.intent)
        assertEquals("부산역", result.placeName)
    }
}
