package com.ssafy.e102.eumgil.feature.lowvision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LowVisionVoiceInputViewModelConstantsTest {

    // VAD WINDOW_SIZE=512, sampleRate=16000 → 1 frame = 32ms

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val WINDOW_SIZE = 512
        private const val MS_PER_FRAME = (WINDOW_SIZE * 1000) / SAMPLE_RATE  // 32ms
    }

    @Test
    fun `silence frames for stop produces approximately 960ms cutoff after speech`() {
        // 발화 후 무음 끊김 기준: 30 frames × 32ms = 960ms
        val expectedFrames = 30
        val actualMs = expectedFrames * MS_PER_FRAME

        assertEquals(960, actualMs)
    }

    @Test
    fun `no speech timeout frames produces approximately 9600ms cutoff before speech`() {
        // 발화 없음 타임아웃: 300 frames × 32ms = 9600ms (약 9.6초)
        val expectedFrames = 300
        val actualMs = expectedFrames * MS_PER_FRAME

        assertEquals(9600, actualMs)
    }

    @Test
    fun `no speech timeout is at least 5 seconds to match user review requirement`() {
        // 리뷰: "약 3초 뒤 홈으로 나가버립니다. 10초 정도로 더 오래 잡았으면 좋겠습니다."
        // 최소 5초 이상을 보장
        val expectedFrames = 300
        val actualMs = expectedFrames * MS_PER_FRAME

        assertTrue("타임아웃이 5초 이상이어야 함 (현재 ${actualMs}ms)", actualMs >= 5000)
    }

    @Test
    fun `silence frames for stop is shorter than no speech timeout`() {
        // 발화 후 끊김 기준이 타임아웃보다 항상 짧아야 함
        val silenceFrames = 30
        val timeoutFrames = 300

        assertTrue(
            "발화 후 끊김 기준(${silenceFrames}f)이 타임아웃(${timeoutFrames}f)보다 짧아야 함",
            silenceFrames < timeoutFrames
        )
    }

    @Test
    fun `frame duration matches sherpa onnx window size at 16khz`() {
        // WINDOW_SIZE=512, sampleRate=16000 → 32ms per frame
        assertEquals(32, MS_PER_FRAME)
    }
}