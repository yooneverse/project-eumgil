package com.test.sherpatest

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.test.sherpatest.audio.AudioRecorder
import com.test.sherpatest.audio.NoiseCancelMode
import com.test.sherpatest.metrics.MetricsExporter
import com.test.sherpatest.metrics.WerCalculator
import com.test.sherpatest.model.MeasurementRecord
import com.test.sherpatest.model.SttResult
import com.test.sherpatest.sherpa.SherpaManager
import com.test.sherpatest.sherpa.SttManager
import com.test.sherpatest.sherpa.VadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val isRecording: Boolean = false,
    val isInitializing: Boolean = false,
    val modelsReady: Boolean = false,
    val lastResult: SttResult? = null,
    val lastWer: Float? = null,
    val referenceText: String = "",
    val history: List<MeasurementRecord> = emptyList(),
    val errorMessage: String? = null,
    val exportMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
        private const val MAX_HISTORY = 20
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _noiseCancelMode = MutableStateFlow(NoiseCancelMode.NOISE_SUPPRESSOR_ONLY)
    val noiseCancelMode: StateFlow<NoiseCancelMode> = _noiseCancelMode

    private val audioRecorder = AudioRecorder()
    private var vadManager: VadManager? = null
    private var sttManager: SttManager? = null

    private var recordingJob: Job? = null
    private var recordingStartTimeMs = 0L
    private var nextId = 1

    init {
        initializeModels()
    }

    private fun initializeModels() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isInitializing = true) }
            try {
                val context = getApplication<Application>()
                SherpaManager.ensureModelsExtracted(context)

                if (!SherpaManager.modelsExist(context)) {
                    _uiState.update {
                        it.copy(
                            isInitializing = false,
                            modelsReady = false,
                            errorMessage = "모델 파일이 없습니다. assets/models/ 에 배치하세요."
                        )
                    }
                    return@launch
                }

                vadManager = VadManager(context)
                sttManager = SttManager.getInstance(context)

                _uiState.update {
                    it.copy(
                        isInitializing = false,
                        modelsReady = true,
                        errorMessage = null
                    )
                }
                Log.d(TAG, "Models initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Init failed: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isInitializing = false,
                        modelsReady = false,
                        errorMessage = "초기화 실패: ${e.message}"
                    )
                }
            }
        }
    }

    fun setReferenceText(text: String) {
        _uiState.update { it.copy(referenceText = text) }
    }

    fun setNoiseCancelMode(mode: NoiseCancelMode) {
        if (_uiState.value.isRecording) return
        _noiseCancelMode.value = mode
        Log.d(TAG, "노이즈 캔슬링 모드 변경: $mode")
        if (mode == NoiseCancelMode.GTCRN_ONLY) {
            viewModelScope.launch(Dispatchers.IO) {
                audioRecorder.initGtcrn(getApplication())
            }
        }
    }

    fun startRecording() {
        if (_uiState.value.isRecording) return
        if (!_uiState.value.modelsReady) return

        _uiState.update { it.copy(isRecording = true, errorMessage = null, exportMessage = null) }
        recordingStartTimeMs = System.currentTimeMillis()
        vadManager?.reset()

        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val SILENCE_FRAMES_FOR_STOP = 30
                var voiceDetectedEver = false
                var silenceFrameCount = 0
                val accumulatedSamples = mutableListOf<Float>()
                var totalVadTimeMs = 0L
                var frameCount = 0
                var skipStt = false

                Log.d(TAG, "=== 녹음 시작 - 마이크 오디오 수신 대기 중 ===")

                audioRecorder.startRecording(mode = _noiseCancelMode.value).collect { floatSamples ->
                    frameCount++

                    if (frameCount % 10 == 0) {
                        val rms = floatSamples.map { it * it }.average().let { Math.sqrt(it) }
                        Log.d(TAG, "[오디오 수신 중] frame=$frameCount rms=${"%.4f".format(rms)}")
                    }

                    val vadStart = System.currentTimeMillis()
                    vadManager?.acceptWaveform(floatSamples)
                    totalVadTimeMs += System.currentTimeMillis() - vadStart

                    var hadSegment = false
                    while (vadManager?.isEmpty() == false) {
                        val segment = vadManager?.front() ?: break
                        vadManager?.popSegment()
                        accumulatedSamples.addAll(segment.samples.toList())
                        hadSegment = true
                    }

                    val currentlySpeaking = vadManager?.isSpeechDetected() ?: false

                    when {
                        hadSegment -> {
                            if (!voiceDetectedEver) Log.d(TAG, ">>> 발화 감지 시작! 음성 누적 중...")
                            voiceDetectedEver = true
                            silenceFrameCount = 0
                            Log.d(TAG, "[VAD] 세그먼트 완성 - 누적 샘플: ${accumulatedSamples.size}")
                        }
                        currentlySpeaking -> {
                            if (!voiceDetectedEver) Log.d(TAG, ">>> 발화 감지 시작! 음성 누적 중...")
                            voiceDetectedEver = true
                            silenceFrameCount = 0
                        }
                        else -> {
                            silenceFrameCount++
                            if (voiceDetectedEver && silenceFrameCount % 5 == 0) {
                                Log.d(TAG, "[VAD] 무음 감지 중 - $silenceFrameCount / $SILENCE_FRAMES_FOR_STOP 프레임")
                            }
                        }
                    }

                    if (hadSegment && !currentlySpeaking) {
                        Log.d(TAG, "=== 세그먼트 완성 + 발화 종료 → STT 준비 ===")
                        audioRecorder.stop()
                    } else if (voiceDetectedEver && silenceFrameCount >= SILENCE_FRAMES_FOR_STOP) {
                        Log.d(TAG, "=== 무음 ${SILENCE_FRAMES_FOR_STOP * 32}ms 지속 → STT 준비 ===")
                        audioRecorder.stop()
                    } else if (!voiceDetectedEver && silenceFrameCount >= SILENCE_FRAMES_FOR_STOP * 2) {
                        Log.d(TAG, "=== 발화 없음 타임아웃 → 녹음 종료 (STT 스킵) ===")
                        skipStt = true
                        audioRecorder.stop()
                    }
                }

                Log.d(TAG, "--- flush 시작 ---")
                vadManager?.flush()
                while (vadManager?.isEmpty() == false) {
                    val segment = vadManager?.front() ?: break
                    vadManager?.popSegment()
                    accumulatedSamples.addAll(segment.samples.toList())
                }

                if (!skipStt && voiceDetectedEver && accumulatedSamples.isNotEmpty()) {
                    val audioSec = accumulatedSamples.size / 16000f
                    Log.d(TAG, "=== STT 추론 시작 - 누적 샘플: ${accumulatedSamples.size} (${String.format("%.2f", audioSec)}초) ===")
                    processSegment(accumulatedSamples.toFloatArray(), totalVadTimeMs)
                    Log.d(TAG, "=== STT 추론 완료 ===")
                } else {
                    Log.d(TAG, "발화 없음 - STT 호출 스킵")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Recording error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(errorMessage = e.message) }
                }
            } finally {
                _uiState.update { it.copy(isRecording = false) }
            }
        }
    }

    private suspend fun processSegment(samples: FloatArray, vadTimeMs: Long) {
        val stt = sttManager ?: return
        try {
            val result = stt.recognize(samples, recordingStartTimeMs, vadTimeMs)
            val wer = if (_uiState.value.referenceText.isNotBlank())
                WerCalculator.calculate(_uiState.value.referenceText, result.text)
            else null

            withContext(Dispatchers.Main) {
                val record = MeasurementRecord(
                    id = nextId++,
                    referenceText = _uiState.value.referenceText,
                    sttText = result.text,
                    totalTimeMs = result.totalTimeMs,
                    vadTimeMs = result.vadTimeMs,
                    sttTimeMs = result.sttTimeMs,
                    audioLengthMs = result.audioLengthMs,
                    rtf = result.rtf,
                    wer = wer ?: 0f
                )
                _uiState.update { state ->
                    val newHistory = (listOf(record) + state.history).take(MAX_HISTORY)
                    state.copy(
                        lastResult = result,
                        lastWer = wer,
                        history = newHistory
                    )
                }
                recordingStartTimeMs = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Log.e(TAG, "STT error: ${e.message}", e)
        }
    }

    fun stopRecording() {
        audioRecorder.stop()
        _uiState.update { it.copy(isRecording = false) }
        recordingJob?.cancel()
        recordingJob = null
    }

    fun exportCsv() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val path = MetricsExporter.exportCsv(
                    getApplication(),
                    _uiState.value.history.reversed()
                )
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(exportMessage = "저장 완료: $path") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(exportMessage = "저장 실패: ${e.message}") }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopRecording()
        vadManager?.release()
    }
}
