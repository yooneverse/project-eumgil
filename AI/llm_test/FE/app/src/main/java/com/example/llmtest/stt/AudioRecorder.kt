package com.example.llmtest.stt

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

internal class AudioRecorder(private val context: Context? = null) {

    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 16000
        private const val WINDOW_SIZE = 512
        private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    @Volatile
    private var isRecording = false

    @SuppressLint("MissingPermission")
    fun startRecording(): Flow<FloatArray> = flow {
        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val focusRequest = audioManager?.let {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .build()
        }

        if (audioManager != null && focusRequest != null) {
            val result = audioManager.requestAudioFocus(focusRequest)
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w(TAG, "오디오 포커스 요청 실패 (result=$result) — 녹음 계속 진행")
            } else {
                Log.d(TAG, "오디오 포커스 획득")
            }
        }

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = minBuf.coerceAtLeast(WINDOW_SIZE * 2)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize,
        )

        val sessionId = audioRecord.audioSessionId

        val noiseSuppressor: NoiseSuppressor? =
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(sessionId)?.also { it.enabled = true }
            } else null

        val echoCanceler: AcousticEchoCanceler? =
            if (AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(sessionId)?.also { it.enabled = true }
            } else null

        val gainControl: AutomaticGainControl? =
            if (AutomaticGainControl.isAvailable()) {
                AutomaticGainControl.create(sessionId)?.also { it.enabled = true }
            } else null

        audioRecord.startRecording()
        isRecording = true
        Log.d(TAG, "녹음 시작")

        val shortBuffer = ShortArray(WINDOW_SIZE)
        try {
            while (isRecording) {
                val read = audioRecord.read(shortBuffer, 0, shortBuffer.size)
                if (read > 0) {
                    emit(FloatArray(read) { shortBuffer[it] / 32768f })
                }
            }
        } finally {
            noiseSuppressor?.release()
            echoCanceler?.release()
            gainControl?.release()
            audioRecord.stop()
            audioRecord.release()
            if (audioManager != null && focusRequest != null) {
                audioManager.abandonAudioFocusRequest(focusRequest)
            }
            Log.d(TAG, "녹음 중지")
        }
    }.flowOn(Dispatchers.IO)

    fun stop() {
        isRecording = false
    }

    fun isRecording(): Boolean = isRecording
}
