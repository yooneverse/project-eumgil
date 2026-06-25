package com.ssafy.e102.eumgil.feature.voiceassistant

import com.ssafy.e102.eumgil.core.model.VoiceAnalyzeHistoryItem
import com.ssafy.e102.eumgil.core.model.VoiceAnalyzeIntent
import com.ssafy.e102.eumgil.core.model.VoiceAnalyzeMode
import com.ssafy.e102.eumgil.core.model.VoiceAnalyzeResult
import com.ssafy.e102.eumgil.data.repository.VoiceAnalyzeRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * AiVoiceAssistantInterpreter 단위 테스트
 *
 * 테스트 대상:
 * - 14개 intent → VoiceAssistantAction 매핑
 * - conversationHistory 누적 (user/assistant 순서)
 * - UNKNOWN 수신 시 history clear
 * - currentRoute가 repository로 전달되는지
 */
class AiVoiceAssistantInterpreterTest {

    // ── Mock Repository ───────────────────────────────────────────────────────

    /**
     * 테스트마다 반환할 결과를 주입할 수 있는 Fake Repository.
     * 호출 인자를 캡처해서 검증에도 활용.
     */
    private class FakeVoiceAnalyzeRepository(
        private var nextResult: VoiceAnalyzeResult,
    ) : VoiceAnalyzeRepository {

        var lastText: String? = null
        var lastMode: VoiceAnalyzeMode? = null
        var lastHistory: List<VoiceAnalyzeHistoryItem> = emptyList()
        var lastCurrentRoute: String? = null
        var callCount: Int = 0

        fun setNextResult(result: VoiceAnalyzeResult) {
            nextResult = result
        }

        override suspend fun analyze(
            text: String,
            mode: VoiceAnalyzeMode,
            history: List<VoiceAnalyzeHistoryItem>,
            currentRoute: String?,
        ): VoiceAnalyzeResult {
            lastText = text
            lastMode = mode
            lastHistory = history
            lastCurrentRoute = currentRoute
            callCount++
            return nextResult
        }
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private fun makeResult(
        intent: VoiceAnalyzeIntent,
        placeName: String? = null,
        category: String? = null,
        departure: String? = null,
        destination: String? = null,
        reportType: String? = null,
        description: String? = null,
        confirmed: Boolean? = null,
        confirmationMessage: String? = null,
    ) = VoiceAnalyzeResult(
        intent = intent,
        placeName = placeName,
        category = category,
        bookmarkAction = null,
        departure = departure,
        destination = destination,
        reportType = reportType,
        description = description,
        confirmed = confirmed,
        confirmationMessage = confirmationMessage,
    )

    private lateinit var fakeRepo: FakeVoiceAnalyzeRepository
    private lateinit var interpreter: AiVoiceAssistantInterpreter

    @Before
    fun setUp() {
        fakeRepo = FakeVoiceAnalyzeRepository(
            nextResult = makeResult(VoiceAnalyzeIntent.UNKNOWN),
        )
        interpreter = AiVoiceAssistantInterpreter(fakeRepo)
    }

    // ── Intent 매핑 테스트 ────────────────────────────────────────────────────

    @Test
    fun `PLACE_SEARCH intent maps to SearchPlace action`() = runTest {
        fakeRepo.setNextResult(makeResult(VoiceAnalyzeIntent.PLACE_SEARCH, placeName = "부산역"))

        val action = interpreter.interpret("부산역 찾아줘", VoiceAssistantContext())

        assertEquals(VoiceAssistantAction.SearchPlace(query = "부산역"), action)
    }

    @Test
    fun `CATEGORY_SEARCH intent maps to CategorySearch action`() = runTest {
        fakeRepo.setNextResult(makeResult(VoiceAnalyzeIntent.CATEGORY_SEARCH, category = "음식점"))

        val action = interpreter.interpret("음식점 찾아줘", VoiceAssistantContext())

        assertEquals(VoiceAssistantAction.CategorySearch(category = "음식점"), action)
    }

    @Test
    fun `NAVIGATE intent maps to Navigate action with departure and destination`() = runTest {
        fakeRepo.setNextResult(
            makeResult(VoiceAnalyzeIntent.NAVIGATE, departure = "서면역", destination = "부산역"),
        )

        val action = interpreter.interpret("서면역에서 부산역 가줘", VoiceAssistantContext())

        assertEquals(VoiceAssistantAction.Navigate(departure = "서면역", destination = "부산역"), action)
    }

    @Test
    fun `NAVIGATE intent with null departure maps to Navigate with null departure`() = runTest {
        fakeRepo.setNextResult(
            makeResult(VoiceAnalyzeIntent.NAVIGATE, departure = null, destination = "부산역"),
        )

        val action = interpreter.interpret("부산역 가줘", VoiceAssistantContext())

        assertEquals(VoiceAssistantAction.Navigate(departure = null, destination = "부산역"), action)
    }

    @Test
    fun `SHOW_BOOKMARKS intent maps to ShowBookmarks action`() = runTest {
        fakeRepo.setNextResult(makeResult(VoiceAnalyzeIntent.SHOW_BOOKMARKS))

        val action = interpreter.interpret("북마크 보여줘", VoiceAssistantContext())

        assertEquals(VoiceAssistantAction.ShowBookmarks(), action)
    }

    @Test
    fun `SHOW_FAVORITE_ROUTES intent maps to OpenSavedRoutes action`() = runTest {
        fakeRepo.setNextResult(makeResult(VoiceAnalyzeIntent.SHOW_FAVORITE_ROUTES))

        val action = interpreter.interpret("저장 경로 보여줘", VoiceAssistantContext())

        assertEquals(VoiceAssistantAction.OpenSavedRoutes(), action)
    }

    @Test
    fun `LOGOUT intent maps to Logout action`() = runTest {
        fakeRepo.setNextResult(makeResult(VoiceAnalyzeIntent.LOGOUT))

        val action = interpreter.interpret("로그아웃", VoiceAssistantContext())

        assertEquals(VoiceAssistantAction.Logout(), action)
    }

    @Test
    fun `REPORT intent maps to OpenReport action`() = runTest {
        fakeRepo.setNextResult(makeResult(VoiceAnalyzeIntent.REPORT, reportType = "STAIRS_STEP"))

        val action = interpreter.interpret("제보할게요", VoiceAssistantContext())

        assertEquals(VoiceAssistantAction.OpenReport(reportType = "STAIRS_STEP"), action)
    }

    @Test
    fun `NAVIGATION_END intent maps to StopNavigation action`() = runTest {
        fakeRepo.setNextResult(makeResult(VoiceAnalyzeIntent.NAVIGATION_END))

        val action = interpreter.interpret("안내 종료해줘", VoiceAssistantContext())

        assertEquals(VoiceAssistantAction.StopNavigation(), action)
    }

    @Test
    fun `OPEN_MY_PAGE intent maps to OpenMyPage action`() = runTest {
        fakeRepo.setNextResult(makeResult(VoiceAnalyzeIntent.OPEN_MY_PAGE))

        val action = interpreter.interpret("마이페이지 열어줘", VoiceAssistantContext())

        assertEquals(VoiceAssistantAction.OpenMyPage(), action)
    }

    @Test
    fun `OPEN_MAP intent maps to OpenMap action`() = runTest {
        fakeRepo.setNextResult(makeResult(VoiceAnalyzeIntent.OPEN_MAP))

        val action = interpreter.interpret("지도 홈으로", VoiceAssistantContext())

        assertEquals(VoiceAssistantAction.OpenMap(), action)
    }

    @Test
    fun `ASK intent maps to Ask action with confirmationMessage`() = runTest {
        fakeRepo.setNextResult(
            makeResult(VoiceAnalyzeIntent.ASK, confirmationMessage = "어떤 장소를 찾으시나요?"),
        )

        val action = interpreter.interpret("찾아줘", VoiceAssistantContext())

        assertEquals(VoiceAssistantAction.Ask(message = "어떤 장소를 찾으시나요?"), action)
    }

    @Test
    fun `ASK intent with null confirmationMessage maps to Ask with empty message`() = runTest {
        fakeRepo.setNextResult(makeResult(VoiceAnalyzeIntent.ASK, confirmationMessage = null))

        val action = interpreter.interpret("찾아줘", VoiceAssistantContext())

        assertEquals(VoiceAssistantAction.Ask(message = ""), action)
    }

    @Test
    fun `UNKNOWN intent maps to UnknownCommand action`() = runTest {
        fakeRepo.setNextResult(makeResult(VoiceAnalyzeIntent.UNKNOWN))

        val action = interpreter.interpret("오늘 날씨 어때", VoiceAssistantContext())

        assertTrue(action is VoiceAssistantAction.UnknownCommand)
    }

    // ── History 누적 테스트 ───────────────────────────────────────────────────

    @Test
    fun `first call passes empty history to repository`() = runTest {
        fakeRepo.setNextResult(makeResult(VoiceAnalyzeIntent.PLACE_SEARCH, placeName = "부산역"))

        interpreter.interpret("부산역 찾아줘", VoiceAssistantContext())

        // 첫 호출 시 history는 빈 리스트
        assertEquals(0, fakeRepo.lastHistory.size)
    }

    @Test
    fun `second call passes two history items (user + assistant)`() = runTest {
        fakeRepo.setNextResult(makeResult(VoiceAnalyzeIntent.PLACE_SEARCH, placeName = "부산역"))
        interpreter.interpret("부산역 찾아줘", VoiceAssistantContext())

        fakeRepo.setNextResult(makeResult(VoiceAnalyzeIntent.PLACE_SEARCH, placeName = "서면역"))
        interpreter.interpret("서면역 찾아줘", VoiceAssistantContext())

        // 두 번째 호출 시 history = [user(1턴), assistant(1턴)]
        assertEquals(2, fakeRepo.lastHistory.size)
        assertEquals("user", fakeRepo.lastHistory[0].role)
        assertEquals("assistant", fakeRepo.lastHistory[1].role)
    }

    @Test
    fun `UNKNOWN clears history so next call passes empty history`() = runTest {
        // 1턴 정상 처리
        fakeRepo.setNextResult(makeResult(VoiceAnalyzeIntent.PLACE_SEARCH, placeName = "부산역"))
        interpreter.interpret("부산역 찾아줘", VoiceAssistantContext())

        // 2턴 UNKNOWN → history clear
        fakeRepo.setNextResult(makeResult(VoiceAnalyzeIntent.UNKNOWN))
        interpreter.interpret("오늘 날씨 어때", VoiceAssistantContext())

        // 3턴 — history가 비어 있어야 함
        fakeRepo.setNextResult(makeResult(VoiceAnalyzeIntent.PLACE_SEARCH, placeName = "해운대"))
        interpreter.interpret("해운대 찾아줘", VoiceAssistantContext())

        assertEquals(0, fakeRepo.lastHistory.size)
    }

    // ── currentRoute 전달 테스트 ──────────────────────────────────────────────

    @Test
    fun `currentRoute from context is passed to repository`() = runTest {
        fakeRepo.setNextResult(makeResult(VoiceAnalyzeIntent.PLACE_SEARCH, placeName = "부산역"))
        val context = VoiceAssistantContext(currentRoute = "navigation/guidance")

        interpreter.interpret("부산역 찾아줘", context)

        assertEquals("navigation/guidance", fakeRepo.lastCurrentRoute)
    }

    @Test
    fun `null currentRoute is passed as null to repository`() = runTest {
        fakeRepo.setNextResult(makeResult(VoiceAnalyzeIntent.PLACE_SEARCH, placeName = "부산역"))
        val context = VoiceAssistantContext(currentRoute = null)

        interpreter.interpret("부산역 찾아줘", context)

        assertEquals(null, fakeRepo.lastCurrentRoute)
    }

    // ── mode 검증 ─────────────────────────────────────────────────────────────

    @Test
    fun `repository is always called with MOBILITY_IMPAIRED mode`() = runTest {
        fakeRepo.setNextResult(makeResult(VoiceAnalyzeIntent.PLACE_SEARCH, placeName = "부산역"))

        interpreter.interpret("부산역 찾아줘", VoiceAssistantContext())

        assertEquals(VoiceAnalyzeMode.MOBILITY_IMPAIRED, fakeRepo.lastMode)
    }
}
