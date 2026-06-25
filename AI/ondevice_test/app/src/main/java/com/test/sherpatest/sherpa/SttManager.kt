package com.test.sherpatest.sherpa

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import java.io.File
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.test.sherpatest.model.SttResult

class SttManager(context: Context) {

    companion object {
        private const val TAG = "SttManager"
        private const val SAMPLE_RATE = 16000

        @Volatile
        private var instance: SttManager? = null

        fun getInstance(context: Context): SttManager {
            return instance ?: synchronized(this) {
                instance ?: SttManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val recognizer: OfflineRecognizer

    init {
        val modelPath = SherpaManager.senseVoiceModelPath(context)
        val tokensPath = SherpaManager.tokensPath(context)
        // 파일 존재 여부 및 크기 확인
        Log.d(TAG, "modelPath=$modelPath exists=${File(modelPath).exists()} size=${File(modelPath).length()}")
        Log.d(TAG, "tokensPath=$tokensPath exists=${File(tokensPath).exists()}")

        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = modelPath,
                    language = "ko",
                    useInverseTextNormalization = true
                ),
                tokens = tokensPath,
                numThreads = 4,
                debug = false
            )
        )

        recognizer = OfflineRecognizer(config = config)
        Log.d(TAG, "SenseVoice model loaded")
    }

    /**
     * 오디오 샘플을 받아 STT 추론 수행 및 시간 측정
     * @param samples FloatArray (16kHz mono)
     * @param recordingStartTimeMs 녹음 시작 시각 (System.currentTimeMillis)
     * @param vadTimeMs VAD 처리에 소요된 시간
     */
    fun recognize(
        samples: FloatArray,
        recordingStartTimeMs: Long,
        vadTimeMs: Long
    ): SttResult {
        val audioLengthMs = (samples.size.toLong() * 1000L) / SAMPLE_RATE

        val sttStart = System.currentTimeMillis()
        val stream = recognizer.createStream()
        stream.acceptWaveform(samples, sampleRate = SAMPLE_RATE)
        recognizer.decode(stream)
        val rawResult = recognizer.getResult(stream).text
        // <|ko|>, <|NEUTRAL|>, <|Speech|> 등 SenseVoice 태그 제거
        val result = rawResult.replace(Regex("<\\|[^|]+\\|>"), "").trim()
        val sttTimeMs = System.currentTimeMillis() - sttStart

        val totalTimeMs = System.currentTimeMillis() - recordingStartTimeMs
        val rtf = if (audioLengthMs > 0) sttTimeMs.toFloat() / audioLengthMs.toFloat() else 0f

        Log.d(TAG, "STT raw='$rawResult' result='$result' sttTime=${sttTimeMs}ms audioLen=${audioLengthMs}ms RTF=${"%.3f".format(rtf)}")

        return SttResult(
            text = result,
            totalTimeMs = totalTimeMs,
            vadTimeMs = vadTimeMs,
            sttTimeMs = sttTimeMs,
            audioLengthMs = audioLengthMs,
            rtf = rtf
        )
    }
}
