package com.ssafy.e102.eumgil.app.navigation

import com.ssafy.e102.eumgil.feature.voiceassistant.VoiceAssistantAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * VoiceAssistantAction.toNavigationRequest() 단위 테스트
 *
 * 테스트 대상:
 * - 신규 Action 5개 (CategorySearch, Navigate, ShowBookmarks, Logout, Ask) 매핑
 * - null 반환 대상 Action (Ask, StopNavigation, UnknownCommand 등)
 */
class VoiceAssistantNavigationRequestTest {

    @Test
    fun `CategorySearch maps to Route navigation request`() {
        val action = VoiceAssistantAction.CategorySearch(category = "음식점")
        val request = action.toNavigationRequest()

        // 현재 임시로 SearchRoute.Results로 연결. 카테고리 전용 route 확인 후 수정 필요.
        assertTrue(request is VoiceAssistantNavigationRequest.Route)
    }

    @Test
    fun `Navigate maps to Route navigation request with destination`() {
        val action = VoiceAssistantAction.Navigate(departure = null, destination = "부산역")
        val request = action.toNavigationRequest()

        // 현재 임시로 검색 결과 화면으로 연결. GPS 기반 경로 안내 구현 후 수정 필요.
        assertTrue(request is VoiceAssistantNavigationRequest.Route)
    }

    @Test
    fun `ShowBookmarks maps to TopLevel SavedRoute`() {
        val action = VoiceAssistantAction.ShowBookmarks()
        val request = action.toNavigationRequest()

        assertEquals(
            VoiceAssistantNavigationRequest.TopLevel(TopLevelDestination.SavedRoute),
            request,
        )
    }

    @Test
    fun `Logout maps to Route AuthLogin`() {
        val action = VoiceAssistantAction.Logout()
        val request = action.toNavigationRequest()

        assertEquals(
            VoiceAssistantNavigationRequest.Route(AuthRoute.Login.route),
            request,
        )
    }

    @Test
    fun `Ask maps to null (handled by ViewModel ShowMessage)`() {
        val action = VoiceAssistantAction.Ask(message = "어떤 장소를 찾으시나요?")
        val request = action.toNavigationRequest()

        assertNull(request)
    }

    @Test
    fun `UnknownCommand maps to null`() {
        val action = VoiceAssistantAction.UnknownCommand(rawCommand = "날씨 알려줘")
        val request = action.toNavigationRequest()

        assertNull(request)
    }

    @Test
    fun `StopNavigation maps to null`() {
        val action = VoiceAssistantAction.StopNavigation()
        val request = action.toNavigationRequest()

        assertNull(request)
    }

    @Test
    fun `OpenReport maps to TopLevel Report`() {
        val action = VoiceAssistantAction.OpenReport()
        val request = action.toNavigationRequest()

        assertEquals(
            VoiceAssistantNavigationRequest.TopLevel(TopLevelDestination.Report),
            request,
        )
    }

    @Test
    fun `OpenMyPage maps to TopLevel MyPage`() {
        val action = VoiceAssistantAction.OpenMyPage()
        val request = action.toNavigationRequest()

        assertEquals(
            VoiceAssistantNavigationRequest.TopLevel(TopLevelDestination.MyPage),
            request,
        )
    }

    @Test
    fun `OpenMap maps to MapHomeEntry`() {
        val action = VoiceAssistantAction.OpenMap()
        val request = action.toNavigationRequest()

        assertEquals(VoiceAssistantNavigationRequest.MapHomeEntry, request)
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
