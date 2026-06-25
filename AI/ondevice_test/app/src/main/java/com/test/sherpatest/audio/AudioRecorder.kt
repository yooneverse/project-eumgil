package com.test.sherpatest.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiser
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserConfig
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserGtcrnModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

enum class NoiseCancelMode {
    NOISE_SUPPRESSOR_ONLY,
    GTCRN_ONLY
}

class AudioRecorder {

    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 16000
        private const val WINDOW_SIZE = 512  // VadManager.WINDOW_SIZE와 동일
        private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var gainControl: AutomaticGainControl? = null
    private var speechEnhancer: OfflineSpeechDenoiser? = null

    @Volatile
    private var isRecording = false

    fun initGtcrn(context: Context) {
        if (speechEnhancer != null) return
        val modelFile = File(context.filesDir, "models/enhancement/gtcrn_simple.onnx")
        if (!modelFile.exists()) {
            modelFile.parentFile?.mkdirs()
            context.assets.open("models/enhancement/gtcrn_simple.onnx")
                .use { input -> modelFile.outputStream().use { input.copyTo(it) } }
        }
        val config = OfflineSpeechDenoiserConfig(
            model = OfflineSpeechDenoiserModelConfig(
                gtcrn = OfflineSpeechDenoiserGtcrnModelConfig(model = modelFile.absolutePath)
            )
        )
        speechEnhancer = OfflineSpeechDenoiser(config = config)
        Log.d(TAG, "GTCRN 초기화 완료")
    }

    @SuppressLint("MissingPermission")
    fun startRecording(mode: NoiseCancelMode = NoiseCancelMode.NOISE_SUPPRESSOR_ONLY): Flow<FloatArray> = flow {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = minBuf.coerceAtLeast(WINDOW_SIZE * 2)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize
        )

        val sessionId = audioRecord!!.audioSessionId

        if (mode == NoiseCancelMode.NOISE_SUPPRESSOR_ONLY) {
            if (NoiseSuppressor.isAvailable())
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.also {
                    it.enabled = true
                    Log.d(TAG, "NoiseSuppressor 활성화")
                }
            if (AcousticEchoCanceler.isAvailable())
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.also {
                    it.enabled = true
                    Log.d(TAG, "AcousticEchoCanceler 활성화")
                }
            if (AutomaticGainControl.isAvailable())
                gainControl = AutomaticGainControl.create(sessionId)?.also {
                    it.enabled = true
                    Log.d(TAG, "AutomaticGainControl 활성화")
                }
        }

        audioRecord!!.startRecording()
        isRecording = true
        Log.d(TAG, "녹음 시작 - 모드: $mode")

        val shortBuffer = ShortArray(WINDOW_SIZE)
        try {
            while (isRecording) {
                val read = audioRecord!!.read(shortBuffer, 0, shortBuffer.size)
                if (read > 0) {
                    var samples = FloatArray(read) { shortBuffer[it] / 32768f }
                    if (mode == NoiseCancelMode.GTCRN_ONLY) {
                        samples = applyGtcrn(samples)
                    }
                    emit(samples)
                }
            }
        } finally {
            noiseSuppressor?.release(); noiseSuppressor = null
            echoCanceler?.release(); echoCanceler = null
            gainControl?.release(); gainControl = null
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.d(TAG, "녹음 중지")
        }
    }.flowOn(Dispatchers.IO)

    fun stop() {
        isRecording = false
    }

    fun isRecording() = isRecording

    private fun applyBandpassFilter(samples: FloatArray): FloatArray {
        // FIR 밴드패스 필터 계수 (300Hz ~ 3400Hz, 16kHz 샘플레이트)
        val coefficients = floatArrayOf(
            -0.0006f, -0.0008f, -0.0004f,  0.0008f,  0.0026f,
             0.0042f,  0.0044f,  0.0022f, -0.0022f, -0.0071f,
            -0.0104f, -0.0096f, -0.0030f,  0.0083f,  0.0200f,
             0.0263f,  0.0215f,  0.0029f, -0.0252f, -0.0535f,
            -0.0718f, -0.0659f, -0.0240f,  0.0497f,  0.1408f,
             0.2310f,  0.2839f,  0.2839f,  0.2310f,  0.1408f,
             0.0497f, -0.0240f, -0.0659f, -0.0718f, -0.0535f,
            -0.0252f,  0.0029f,  0.0215f,  0.0263f,  0.0200f,
             0.0083f, -0.0030f, -0.0096f, -0.0104f, -0.0071f,
            -0.0022f,  0.0022f,  0.0044f,  0.0042f,  0.0026f,
             0.0008f, -0.0004f, -0.0008f, -0.0006f
        )
        val result = FloatArray(samples.size)
        val halfLen = coefficients.size / 2
        for (i in samples.indices) {
            var sum = 0f
            for (j in coefficients.indices) {
                val idx = i - j + halfLen
                if (idx in samples.indices) sum += coefficients[j] * samples[idx]
            }
            result[i] = sum
        }
        return result
    }

    private fun applyGtcrn(samples: FloatArray): FloatArray {
        val enhancer = speechEnhancer ?: return samples
        val result = enhancer.run(samples, sampleRate = 16000)
        return result.samples
    }
}
