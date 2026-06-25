package com.example.llmtest.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.*

class TTSManager(context: Context, private val onInitListener: (Boolean) -> Unit) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    companion object {
        private const val TAG = "TTSManager"
    }

    init {
        Log.d(TAG, "Initializing TTS...")

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.KOREAN)
                isInitialized = result != TextToSpeech.LANG_MISSING_DATA
                    && result != TextToSpeech.LANG_NOT_SUPPORTED

                if (isInitialized) {
                    Log.d(TAG, "TTS initialized successfully")
                } else {
                    Log.e(TAG, "Korean language not supported")
                }

                onInitListener(isInitialized)
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
                onInitListener(false)
            }
        }
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized, cannot speak")
            return
        }

        Log.d(TAG, "Speaking: $text")

        if (onComplete != null) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "TTS started")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS completed")
                    onComplete()
                }

                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS error")
                    onComplete()
                }
            })
        }

        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        Log.d(TAG, "Stopping TTS")
        tts?.stop()
    }

    fun shutdown() {
        Log.d(TAG, "Shutting down TTS")
        tts?.shutdown()
        tts = null
    }
}
