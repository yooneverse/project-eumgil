package com.ssafy.e102.eumgil.core.stt

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * sherpa-onnx KeywordSpotter를 사용해 웨이크워드("HEY LINK")를 감지하는 매니저.
 *
 * 내부에서 [AudioRecorder]를 소유하며, [startSpotting] Flow가 수집되는 동안
 * 마이크를 점유하고 웨이크워드 감지 시 `true`를 emit한다.
 *
 * 사용 예:
 * ```kotlin
 * val manager = KeywordSpottingManager(context)
 * manager.startSpotting().collect { /* 웨이크워드 감지됨 */ }
 * // 종료 시
 * manager.release()
 * ```
 */
internal class KeywordSpottingManager(context: Context) {

    companion object {
        private const val TAG = "KeywordSpottingManager"
        private const val SAMPLE_RATE = 16000
    }

    private val appContext = context.applicationContext
    private val audioRecorder = AudioRecorder(appContext)
    private val keywordSpotter: KeywordSpotter

    init {
        val config = KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = SherpaManager.kwsEncoderPath(appContext),
                    decoder = SherpaManager.kwsDecoderPath(appContext),
                    joiner = SherpaManager.kwsJoinerPath(appContext),
                ),
                tokens = SherpaManager.kwsTokensPath(appContext),
                numThreads = 2,
            ),
            maxActivePaths = 4,
            keywordsFile = SherpaManager.kwsKeywordsPath(appContext),
            keywordsScore = 1.0f,
            keywordsThreshold = 0.1f,
            numTrailingBlanks = 1,
        )
        keywordSpotter = KeywordSpotter(config = config)
        Log.d(TAG, "KeywordSpotter 초기화 완료")
    }

    /**
     * 마이크 스트림을 청취하며 웨이크워드 감지 시 `true`를 emit하는 Flow.
     *
     * Flow가 cancel되거나 AudioRecorder.stop()이 호출될 때까지 실행된다.
     */
    fun startSpotting(): Flow<Boolean> = flow {
        val stream = keywordSpotter.createStream("")
        Log.d(TAG, "=== 웨이크워드 청취 시작 ===")
        try {
            audioRecorder.startRecording().collect { samples ->
                stream.acceptWaveform(samples, SAMPLE_RATE)
                while (keywordSpotter.isReady(stream)) {
                    keywordSpotter.decode(stream)
                    val result = keywordSpotter.getResult(stream)
                    if (result.keyword.isNotEmpty()) {
                        Log.d(TAG, "웨이크워드 감지: '${result.keyword}'")
                        keywordSpotter.reset(stream)
                        emit(true)
                    }
                }
            }
        } finally {
            stream.release()
            Log.d(TAG, "=== 웨이크워드 청취 종료 ===")
        }
    }

    /** 녹음을 중단한다. 실행 중인 [startSpotting] Flow가 자연 종료된다. */
    fun stop() {
        audioRecorder.stop()
    }

    /** 녹음 중단 + KeywordSpotter 네이티브 리소스 해제. */
    fun release() {
        audioRecorder.stop()
        keywordSpotter.release()
        Log.d(TAG, "KeywordSpottingManager released")
    }
}
