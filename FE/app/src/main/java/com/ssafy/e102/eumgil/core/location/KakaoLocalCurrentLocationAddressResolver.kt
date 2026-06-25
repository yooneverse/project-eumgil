package com.ssafy.e102.eumgil.core.location

import android.util.Log
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import org.json.JSONObject

private const val KAKAO_LOCAL_RGC_LOG_TAG = "KakaoLocalRGC"

/**
 * 카카오 로컬 REST API(/v2/local/geo/coord2address.json) 기반 reverse geocoder.
 *
 * Android `Geocoder`는 POI 매칭 반경이 넓어 큰 건물 부근의 좌표가 모두 같은 이름으로
 * 묶이는 경향이 있는 반면, 카카오 로컬 API는 도로명 주소를 우선 반환해 한국 지역의
 * 좌표 → 주소 변환 정밀도가 높다.
 *
 * Authorization 헤더에는 `KakaoAK <앱 키>` 형식을 사용한다. KakaoMap SDK가 사용하는
 * Native App Key와 REST API Key는 카카오 디벨로퍼스에서 별도로 발급되지만, 동일
 * 애플리케이션의 키라면 권한 설정에 따라 REST 호출에도 사용 가능하다.
 */
class KakaoLocalCurrentLocationAddressResolver(
    private val apiKey: String,
) : CurrentLocationAddressResolver {
    private val httpClient = HttpJsonClient(baseUrl = KAKAO_LOCAL_API_BASE_URL)

    override suspend fun resolveAddress(coordinate: GeoCoordinate): String? {
        if (apiKey.isBlank()) {
            Log.w(KAKAO_LOCAL_RGC_LOG_TAG, "apiKey is blank — skipping Kakao Local RGC")
            return null
        }

        val response =
            runCatching {
                httpClient.getJson(
                    path = "/v2/local/geo/coord2address.json",
                    queryParams =
                        mapOf(
                            // 카카오 로컬 API는 x=경도, y=위도 순서.
                            "x" to coordinate.longitude.toString(),
                            "y" to coordinate.latitude.toString(),
                            "input_coord" to "WGS84",
                        ),
                    headers = mapOf("Authorization" to "KakaoAK $apiKey"),
                )
            }.onFailure { error ->
                Log.w(KAKAO_LOCAL_RGC_LOG_TAG, "Kakao Local RGC request failed", error)
            }.getOrNull() ?: return null

        if (response.statusCode !in 200..299) {
            Log.w(
                KAKAO_LOCAL_RGC_LOG_TAG,
                "Kakao Local RGC non-2xx: status=${response.statusCode} body=${response.body.take(200)}",
            )
            return null
        }
        val resolved = parseFirstAddress(response.body)
        Log.d(
            KAKAO_LOCAL_RGC_LOG_TAG,
            "Kakao Local RGC ok lat=${coordinate.latitude} lng=${coordinate.longitude} -> $resolved",
        )
        return resolved
    }

    /**
     * 카카오 응답 구조:
     * ```
     * { "documents": [ {
     *     "road_address": { "address_name": "..." },
     *     "address":      { "address_name": "..." }
     *   } ] }
     * ```
     * 도로명 주소(road_address)가 있으면 우선 사용하고, 없으면 지번 주소(address)로 fallback.
     */
    private fun parseFirstAddress(body: String): String? =
        runCatching {
            val documents = JSONObject(body).optJSONArray("documents") ?: return@runCatching null
            if (documents.length() == 0) return@runCatching null
            val first = documents.getJSONObject(0)
            val roadAddress =
                first
                    .optJSONObject("road_address")
                    ?.optString("address_name")
                    ?.takeIf { it.isNotBlank() && it != "null" }
            if (roadAddress != null) return@runCatching roadAddress
            first
                .optJSONObject("address")
                ?.optString("address_name")
                ?.takeIf { it.isNotBlank() && it != "null" }
        }.getOrNull()

    private companion object {
        private const val KAKAO_LOCAL_API_BASE_URL = "https://dapi.kakao.com"
    }
}
