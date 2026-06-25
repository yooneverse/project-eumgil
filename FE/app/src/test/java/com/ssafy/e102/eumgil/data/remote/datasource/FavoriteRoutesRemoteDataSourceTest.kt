package com.ssafy.e102.eumgil.data.remote.datasource

import com.ssafy.e102.eumgil.data.remote.HttpJsonResponse
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FavoriteRoutesRemoteDataSourceTest {
    @Test
    fun `createFavoriteRoute posts latest BE body with route id and labels only`() =
        runBlocking {
            var capturedPath: String? = null
            var capturedBody: String? = null
            var capturedHeaders: Map<String, String> = emptyMap()
            val dataSource =
                FavoriteRoutesRemoteDataSource(
                    getRequestExecutor = { _, _, _ -> error("GET is not used") },
                    postRequestExecutor = { path, body, headers ->
                        capturedPath = path
                        capturedBody = body
                        capturedHeaders = headers
                        HttpJsonResponse(
                            statusCode = 201,
                            body =
                                """
                                {
                                  "status": "S2010",
                                  "data": {
                                    "favRouteId": 42
                                  },
                                  "message": "created"
                                }
                                """.trimIndent(),
                        )
                    },
                    patchRequestExecutor = { _, _, _ -> error("PATCH is not used") },
                    deleteRequestExecutor = { _, _ -> error("DELETE is not used") },
                )

            val response =
                dataSource.createFavoriteRoute(
                    accessToken = "access-token",
                    routeId = "walk_rt_safe_001",
                    startLabel = "Busan City Hall",
                    endLabel = "Haeundae Beach",
                )

            val bodyJson = JSONObject(capturedBody.orEmpty())
            assertEquals("/favorite-routes", capturedPath)
            assertEquals("Bearer access-token", capturedHeaders["Authorization"])
            assertEquals("walk_rt_safe_001", bodyJson.getString("routeId"))
            assertEquals("Busan City Hall", bodyJson.getString("startLabel"))
            assertEquals("Haeundae Beach", bodyJson.getString("endLabel"))
            assertFalse(bodyJson.has("startPoint"))
            assertFalse(bodyJson.has("endPoint"))
            assertFalse(bodyJson.has("routeOption"))
            assertEquals(42L, response.favRouteId)
        }

    @Test
    fun `updateFavoriteRoute patches labels only`() =
        runBlocking {
            var capturedBody: String? = null
            val dataSource =
                FavoriteRoutesRemoteDataSource(
                    getRequestExecutor = { _, _, _ -> error("GET is not used") },
                    postRequestExecutor = { _, _, _ -> error("POST is not used") },
                    patchRequestExecutor = { _, body, _ ->
                        capturedBody = body
                        HttpJsonResponse(
                            statusCode = 200,
                            body = """{"status":"S2000","data":null,"message":"ok"}""",
                        )
                    },
                    deleteRequestExecutor = { _, _ -> error("DELETE is not used") },
                )

            dataSource.updateFavoriteRoute(
                accessToken = "access-token",
                favRouteId = 42L,
                startLabel = "Start",
                endLabel = "End",
            )

            val bodyJson = JSONObject(capturedBody.orEmpty())
            assertEquals("Start", bodyJson.getString("startLabel"))
            assertEquals("End", bodyJson.getString("endLabel"))
            assertFalse(bodyJson.has("startPoint"))
            assertFalse(bodyJson.has("endPoint"))
            assertFalse(bodyJson.has("routeOption"))
            assertFalse(bodyJson.has("routeId"))
        }
}
