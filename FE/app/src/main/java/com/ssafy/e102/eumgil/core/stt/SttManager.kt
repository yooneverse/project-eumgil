package com.ssafy.e102.eumgil.core.stt

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig

/**
 * SenseVoice Small int8 오프라인 STT 추론기.
 *
 * 싱글턴으로 모델을 한 번만 로드하며, [recognize]는 16kHz mono FloatArray를 받아
 * 인식 텍스트를 반환한다. SenseVoice 태그(<|ko|> 등)는 자동으로 제거된다.
 */
internal class SttManager private constructor(context: Context) {

    companion object {
        private const val TAG = "SttManager"
        private const val SAMPLE_RATE = 16000

        @Volatile
        private var instance: SttManager? = null

        fun getInstance(context: Context): SttManager =
            instance ?: synchronized(this) {
                instance ?: SttManager(context.applicationContext).also { instance = it }
            }
    }

    private val recognizer: OfflineRecognizer

    init {
        val modelPath = SherpaManager.senseVoiceModelPath(context)
        val tokensPath = SherpaManager.tokensPath(context)
        Log.d(TAG, "Loading SenseVoice: $modelPath")

        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = modelPath,
                    language = "ko",
                    useInverseTextNormalization = true,
                ),
                tokens = tokensPath,
                numThreads = 4,
                debug = false,
            ),
        )

        recognizer = OfflineRecognizer(config = config)
        Log.d(TAG, "SenseVoice loaded")
    }

    /**
     * 16kHz mono PCM FloatArray를 받아 한국어 STT 결과 텍스트를 반환한다.
     * SenseVoice 언어/감정 태그(<|ko|>, <|NEUTRAL|> 등)는 제거된다.
     */
    fun recognize(samples: FloatArray): String {
        val stream = recognizer.createStream()
        stream.acceptWaveform(samples, sampleRate = SAMPLE_RATE)
        recognizer.decode(stream)
        val raw = recognizer.getResult(stream).text
        val result = raw.replace(Regex("<\\|[^|]+\\|>"), "").trim()
        Log.d(TAG, "STT raw='$raw' result='$result'")
        return result
    }
}
