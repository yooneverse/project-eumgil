package com.example.llmtest.stt

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.SpeechSegment
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

internal class VadManager(context: Context) {

    companion object {
        private const val TAG = "VadManager"
        const val WINDOW_SIZE = 512
    }

    private var vad: Vad? = null

    init {
        val vadPath = SherpaManager.vadModelPath(context)
        Log.d(TAG, "Loading VAD model: $vadPath")

        val config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = vadPath,
                threshold = 0.5f,
                minSilenceDuration = 1.5f,
                minSpeechDuration = 0.25f,
                windowSize = WINDOW_SIZE,
                maxSpeechDuration = 10.0f,
            ),
            sampleRate = 16000,
            numThreads = 1,
            debug = false,
        )

        vad = Vad(config = config)
        Log.d(TAG, "VAD model loaded")
    }

    fun acceptWaveform(samples: FloatArray) {
        vad?.acceptWaveform(samples)
    }

    fun isSpeechDetected(): Boolean = vad?.isSpeechDetected() ?: false

    fun isEmpty(): Boolean = vad?.empty() ?: true

    fun front(): SpeechSegment? {
        val v = vad ?: return null
        return if (!v.empty()) v.front() else null
    }

    fun popSegment() {
        vad?.pop()
    }

    fun flush() {
        vad?.flush()
    }

    fun reset() {
        vad?.reset()
    }

    fun release() {
        vad = null
        Log.d(TAG, "VAD released")
    }
}
