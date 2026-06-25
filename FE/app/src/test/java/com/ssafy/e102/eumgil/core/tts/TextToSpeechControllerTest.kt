package com.ssafy.e102.eumgil.core.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TextToSpeechControllerTest {
    @Test
    fun `route briefing uses normal speed speech rate`() {
        assertEquals(1.0f, ROUTE_BRIEFING_TTS_SPEECH_RATE)
    }

    @Test
    fun `no op tts controller stays unavailable and ignores commands`() {
        val controller = NoOpTextToSpeechController

        controller.setEnabled(enabled = true)
        controller.speak("route briefing")
        controller.stop()
        controller.shutdown()

        assertFalse(controller.state.value.enabled)
        assertEquals(TextToSpeechAvailability.Unavailable, controller.state.value.availability)
        assertFalse(controller.state.value.canSpeak)
    }

    @Test
    fun `navigation tts uses media speech attributes for playback capture`() {
        val config = defaultTextToSpeechAudioConfig()

        assertEquals(EXPECTED_MEDIA_USAGE, config.usage)
        assertEquals(EXPECTED_SPEECH_CONTENT_TYPE, config.contentType)
        assertEquals(EXPECTED_ALLOW_CAPTURE_BY_ALL, config.capturePolicy)
    }

    @Test
    fun `navigation tts requests transient may duck audio focus`() {
        val config = defaultTextToSpeechAudioConfig()

        assertEquals(EXPECTED_TRANSIENT_MAY_DUCK_FOCUS_GAIN, config.focusGain)
    }
}

private const val EXPECTED_MEDIA_USAGE = 1
private const val EXPECTED_SPEECH_CONTENT_TYPE = 1
private const val EXPECTED_ALLOW_CAPTURE_BY_ALL = 1
private const val EXPECTED_TRANSIENT_MAY_DUCK_FOCUS_GAIN = 3
