package com.ssafy.e102.eumgil.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityTest {
    @Test
    fun `app defaults hardware volume buttons to media stream`() {
        assertEquals(EXPECTED_STREAM_MUSIC, defaultAppVolumeControlStream())
    }

    @Test
    fun `app allows audio playback capture by all capture clients`() {
        assertEquals(EXPECTED_ALLOW_CAPTURE_BY_ALL, defaultAppAudioPlaybackCapturePolicy())
    }

    @Test
    fun `main activity injects stored text size preference into app theme`() {
        val source = File("src/main/java/com/ssafy/e102/eumgil/app/MainActivity.kt").readText()

        assertTrue(source.contains("textSizePreferenceRepository.observeTextSizePreference()"))
        assertTrue(source.contains("collectAsStateWithLifecycle(initialValue = TextSizePreference.DEFAULT)"))
        assertTrue(source.contains("BusanEumgilTheme(textSizePreference = textSizePreference)"))
    }
}

private const val EXPECTED_STREAM_MUSIC = 3
private const val EXPECTED_ALLOW_CAPTURE_BY_ALL = 1
