package com.ssafy.e102.eumgil.feature.lowvision

/**
 * 시각지원(저시력/시각장애) 모드의 풀스크린 셸 화면들이 공유하는 하단 네비 탭 정의.
 *
 * 출처: Figma file MREqSzkmwhRcXnFS3lzW17
 *   - node 371:157 (홈 nav.bottom-nav)
 *   - node 371:311 (입력중 nav.nav)
 */
enum class LowVisionBottomTab {
    HOME,
    BOOKMARK,
    CATEGORY,
    MY_PAGE,
}

/**
 * 시각지원 모드 메인 홈 화면 UI 상태.
 *
 * Figma node 371:105 ("home"). 음성 입력 카드와 현재 위치 카드, 그리고 입력 대기
 * 상태 정보 박스를 표시한다. 카드 탭 동작은 호출자에게 콜백으로 위임한다.
 */
data class LowVisionHomeUiState(
    val isWaitingForVoice: Boolean = true,
    val selectedTab: LowVisionBottomTab = LowVisionBottomTab.HOME,
)

/**
 * 시각지원 모드 음성 입력(녹음) 화면 UI 상태.
 *
 * Figma node 371:300 ("div.phone"). 마이크 원 + "입력중" 라벨로 음성 입력 진행을
 * 보여준다. 실제 녹음 시작/종료는 호출자(ViewModel/Route)가 결정.
 */
data class LowVisionVoiceInputUiState(
    val isRecording: Boolean = true,
    val selectedTab: LowVisionBottomTab = LowVisionBottomTab.HOME,
    /** AI 확인 요청 메시지 (TTS 재생용). null이면 대기 없음. */
    val confirmationMessage: String? = null,
    /** 동일 confirmationMessage 반복 재생을 위한 카운터. */
    val ttsNonce: Int = 0,
)
