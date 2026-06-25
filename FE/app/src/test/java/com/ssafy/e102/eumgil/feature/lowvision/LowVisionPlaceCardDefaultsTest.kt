package com.ssafy.e102.eumgil.feature.lowvision

import com.ssafy.e102.eumgil.R
import org.junit.Assert.assertEquals
import org.junit.Test

class LowVisionPlaceCardDefaultsTest {
    @Test
    fun `place action icons use filled bookmark and route navigation assets`() {
        assertEquals(R.drawable.ic_nav_bookmark_selected, LowVisionPlaceCardDefaults.saveIconRes)
        assertEquals(R.drawable.ic_route_start_navigation, LowVisionPlaceCardDefaults.routeIconRes)
    }

    @Test
    fun `place actions put navigation before bookmark actions`() {
        assertEquals(
            listOf(LowVisionPlaceCardAction.Navigate, LowVisionPlaceCardAction.Bookmark),
            LowVisionPlaceCardDefaults.actionOrder,
        )
    }

    @Test
    fun `brief address keeps the most specific address segments`() {
        assertEquals(
            "중구 중앙대로 206",
            lowVisionBriefAddress("부산광역시 중구 중앙대로 206"),
        )
    }

    @Test
    fun `detail address includes full address and gps text`() {
        assertEquals(
            "상세 주소: 부산광역시 중구 중앙대로 206\nGPS 위치: 위도 35.11510, 경도 129.04150",
            lowVisionDetailAddress(
                address = "부산광역시 중구 중앙대로 206",
                latitude = 35.1151,
                longitude = 129.0415,
            ),
        )
    }

    @Test
    fun `detail address falls back to gps when address is missing`() {
        assertEquals(
            "상세 주소: GPS 기반 위치\nGPS 위치: 위도 35.11510, 경도 129.04150",
            lowVisionDetailAddress(
                address = null,
                latitude = 35.1151,
                longitude = 129.0415,
            ),
        )
    }

    @Test
    fun `place info a11y label combines place name and brief address`() {
        assertEquals(
            "부산역 엘리베이터. 중구 중앙대로 206. 탭하면 상세 주소를 음성으로 안내합니다.",
            lowVisionPlaceInfoA11yLabel(
                name = "부산역 엘리베이터",
                address = "부산광역시 중구 중앙대로 206",
            ),
        )
    }

    @Test
    fun `place info speech text includes place name and full gps detail`() {
        assertEquals(
            "부산역 엘리베이터. 상세 주소: 부산광역시 중구 중앙대로 206\nGPS 위치: 위도 35.11510, 경도 129.04150",
            lowVisionPlaceInfoSpeechText(
                name = "부산역 엘리베이터",
                address = "부산광역시 중구 중앙대로 206",
                latitude = 35.1151,
                longitude = 129.0415,
            ),
        )
    }
}
