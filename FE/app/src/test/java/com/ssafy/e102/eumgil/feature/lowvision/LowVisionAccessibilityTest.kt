package com.ssafy.e102.eumgil.feature.lowvision

import org.junit.Assert.assertEquals
import org.junit.Test

class LowVisionAccessibilityTest {
    @Test
    fun `button talkback label appends button once`() {
        assertEquals("모드 변경 버튼", lowVisionButtonA11yLabel("모드 변경"))
    }

    @Test
    fun `button talkback label can keep explicit double tap guidance`() {
        assertEquals(
            "음성 입력 버튼. 두 번 탭하면 시작합니다.",
            lowVisionButtonA11yLabel("음성 입력", "두 번 탭하면 시작합니다."),
        )
    }

    @Test
    fun `preparing button talkback label keeps unavailable state after button role`() {
        assertEquals(
            "현재 위치 버튼. 준비 중인 기능입니다.",
            lowVisionPreparingButtonA11yLabel("현재 위치"),
        )
    }
}
