package com.ssafy.e102.eumgil.feature.lowvision

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.e102.eumgil.app.BusanEumgilApp
import com.ssafy.e102.eumgil.core.model.VoiceAnalyzeHistoryItem
import com.ssafy.e102.eumgil.core.model.VoiceAnalyzeIntent
import com.ssafy.e102.eumgil.core.model.VoiceAnalyzeMode
import com.ssafy.e102.eumgil.core.model.VoiceAnalyzeResult
import com.ssafy.e102.eumgil.core.model.toJsonString
import com.ssafy.e102.eumgil.core.stt.AudioRecorder
import com.ssafy.e102.eumgil.core.stt.SherpaManager
import com.ssafy.e102.eumgil.core.stt.SttManager
import com.ssafy.e102.eumgil.core.stt.VadManager
import com.ssafy.e102.eumgil.data.remote.datasource.VoiceAnalyzeApiException
import com.ssafy.e102.eumgil.data.repository.VoiceAnalyzeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LowVisionVoiceInputEvent {
    /** VAD + STT 파이프라인 완료: [query]를 검색어로 결과 화면으로 이동. */
    data class RecordingCompleted(val query: String) : LowVisionVoiceInputEvent

    /** 사용자 취소 또는 오류: 홈 화면으로 복귀. */
    data object RecordingCancelled : LowVisionVoiceInputEvent

    /** TTS "말씀해 주세요" 재생 요청 — Route가 TTS 완료 후 [beginRecording]을 호출한다. */
    data object ReadyToRecord : LowVisionVoiceInputEvent

    data class CategorySearchCompleted(val category: String) : LowVisionVoiceInputEvent
    data class BookmarkAddCompleted(val placeName: String) : LowVisionVoiceInputEvent
    data class BookmarkDeleteCompleted(val placeName: String) : LowVisionVoiceInputEvent
    data class NavigateCompleted(val departure: String, val destination: String) : LowVisionVoiceInputEvent
    data object ShowBookmarksCompleted : LowVisionVoiceInputEvent
    data object ShowFavoriteRoutesCompleted : LowVisionVoiceInputEvent
    data object LogoutCompleted : LowVisionVoiceInputEvent
    data object NavigationEndCompleted : LowVisionVoiceInputEvent
}

/**
 * 시각지원 음성 입력 화면 ViewModel.
 *
 * 화면 진입 즉시 모델 초기화 → VAD 기반 녹음 → SenseVoice STT 파이프라인을
 * 자동으로 실행한다.
 *
 * 이벤트:
 *  - [LowVisionVoiceInputEvent.RecordingCompleted]: STT 결과 텍스트와 함께 발행
 *  - [LowVisionVoiceInputEvent.RecordingCancelled]: 사용자 취소 / 발화 없음 / 오류
 */
class LowVisionVoiceInputViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "LowVisionVoiceInputVM"
        private const val SILENCE_FRAMES_FOR_STOP = 30    // 발화 후 무음 끊김 기준 (30 × 32ms = 960ms)
        private const val NO_SPEECH_TIMEOUT_FRAMES = 300  // 발화 없음 타임아웃 (300 × 32ms = 9.6초)
        private const val MAX_ANALYZE_RETRY_COUNT = 2
        private const val HTTP_UNAUTHORIZED = 401
        private const val ROLE_USER = "user"
        private const val ROLE_ASSISTANT = "assistant"
        private const val RETRY_MESSAGE = "다시 시도해주세요"
    }

    private val voiceAnalyzeRepository: VoiceAnalyzeRepository by lazy {
        (getApplication<Application>() as BusanEumgilApp).appContainer.voiceAnalyzeRepository
    }

    private val _uiState = MutableStateFlow(LowVisionVoiceInputUiState())
    val uiState: StateFlow<LowVisionVoiceInputUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<LowVisionVoiceInputEvent>(Channel.BUFFERED)
    val uiEvent: Flow<LowVisionVoiceInputEvent> = _uiEvent.receiveAsFlow()

    private val audioRecorder = AudioRecorder(getApplication())
    private var vadManager: VadManager? = null
    private var sttManager: SttManager? = null
    private var recordingJob: Job? = null

    /** 멀티턴 대화 히스토리 (user/assistant 교번 구조). */
    private val conversationHistory = mutableListOf<VoiceAnalyzeHistoryItem>()

    /** 현재 화면 route — AI 서버 context 전달용. Route에서 업데이트된다. */
    private var currentRoute: String? = null
    private var analyzeRetryCount = 0

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                SherpaManager.ensureModelsExtracted(context)

                if (!SherpaManager.modelsExist(context)) {
                    Log.e(TAG, "모델 파일 없음 — 음성 입력 취소")
                    _uiEvent.send(LowVisionVoiceInputEvent.RecordingCancelled)
                    return@launch
                }

                vadManager = VadManager(context)
                sttManager = SttManager.getInstance(context)

                startRecording()
            } catch (e: Exception) {
                Log.e(TAG, "초기화 실패: ${e.message}", e)
                _uiEvent.send(LowVisionVoiceInputEvent.RecordingCancelled)
            }
        }
    }

    /** 화면 탭 → 녹음 즉시 취소하고 홈으로 복귀. */
    fun cancelRecording() {
        audioRecorder.stop()
        recordingJob?.cancel()
        viewModelScope.launch {
            _uiEvent.send(LowVisionVoiceInputEvent.RecordingCancelled)
        }
    }

    /**
     * 현재 화면 route를 업데이트한다.
     * LowVisionVoiceInputRoute에서 navController의 currentRoute 변경 시 호출된다.
     */
    fun updateCurrentRoute(route: String?) {
        currentRoute = route
    }

    /**
     * TTS "말씀해 주세요" 재생을 Route에 요청한다.
     * Route가 TTS 완료를 감지하면 [beginRecording]을 호출한다.
     */
    private suspend fun startRecording() {
        _uiEvent.send(LowVisionVoiceInputEvent.ReadyToRecord)
    }

    /**
     * 실제 VAD+STT 파이프라인을 시작한다.
     * Route에서 TTS 완료 후 호출한다.
     */
    fun beginRecording() {
        if (recordingJob?.isActive == true) return
        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                var voiceDetectedEver = false
                var silenceFrameCount = 0
                val accumulatedSamples = mutableListOf<Float>()
                var skipStt = false

                Log.d(TAG, "=== 녹음 시작 ===")

                audioRecorder.startRecording().collect { floatSamples ->
                    vadManager?.acceptWaveform(floatSamples)

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
                            if (!voiceDetectedEver) Log.d(TAG, ">>> 발화 감지 시작")
                            voiceDetectedEver = true
                            silenceFrameCount = 0
                        }
                        currentlySpeaking -> {
                            voiceDetectedEver = true
                            silenceFrameCount = 0
                        }
                        else -> {
                            silenceFrameCount++
                        }
                    }

                    when {
                        voiceDetectedEver && silenceFrameCount >= SILENCE_FRAMES_FOR_STOP -> {
                            Log.d(TAG, "=== 무음 지속 → STT 준비 ===")
                            audioRecorder.stop()
                        }
                        !voiceDetectedEver && silenceFrameCount >= NO_SPEECH_TIMEOUT_FRAMES -> {
                            Log.d(TAG, "=== 발화 없음 타임아웃 → 취소 ===")
                            skipStt = true
                            audioRecorder.stop()
                        }
                    }
                }

                // flush 후 잔여 세그먼트 수집
                vadManager?.flush()
                while (vadManager?.isEmpty() == false) {
                    val segment = vadManager?.front() ?: break
                    vadManager?.popSegment()
                    accumulatedSamples.addAll(segment.samples.toList())
                }

                if (!skipStt && voiceDetectedEver && accumulatedSamples.isNotEmpty()) {
                    Log.d(TAG, "=== STT 추론 시작 (${accumulatedSamples.size} samples) ===")
                    val text = sttManager?.recognize(accumulatedSamples.toFloatArray()).orEmpty()
                    Log.d(TAG, "=== STT 완료: '$text' ===")
                    if (text.isBlank()) {
                        _uiEvent.send(LowVisionVoiceInputEvent.RecordingCancelled)
                    } else {
                        handleSttResult(text)
                    }
                } else {
                    Log.d(TAG, "발화 없음 또는 취소 — 홈으로 복귀")
                    _uiEvent.send(LowVisionVoiceInputEvent.RecordingCancelled)
                }
            } catch (e: Exception) {
                Log.e(TAG, "녹음 오류: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _uiEvent.send(LowVisionVoiceInputEvent.RecordingCancelled)
                }
            }
        }
    }

    /**
     * STT 결과를 AI 분석 API에 전달하고 멀티턴 대화를 진행한다.
     *
     * - confirmed == null && confirmationMessage != null → TTS 확인 요청, 다음 발화 대기
     * - confirmed == true → [LowVisionVoiceInputEvent.RecordingCompleted] 발행
     * - confirmed == false / intent == UNKNOWN → 히스토리 초기화 후 재녹음
     */
    private suspend fun handleSttResult(sttText: String) {
        // 현재 발화 추가 전 snapshot 저장 — AI 서버에서 text와 history 중복 방지
        val historySnapshot = conversationHistory.toList()

        // 사용자 발화를 히스토리에 추가
        conversationHistory.add(VoiceAnalyzeHistoryItem(role = ROLE_USER, content = sttText))

        try {
            Log.d(TAG, "=== 음성 분석 요청 (history=${historySnapshot.size}턴): '$sttText' ===")
            val result = voiceAnalyzeRepository.analyze(
                text = sttText,
                mode = VoiceAnalyzeMode.LOW_VISION,
                history = historySnapshot,
                currentRoute = currentRoute,
            )
            Log.d(TAG, "=== 음성 분석 완료: intent=${result.intent}, confirmed=${result.confirmed}, placeName=${result.placeName} ===")

            // 어시스턴트 응답을 JSON 직렬화하여 히스토리에 추가
            conversationHistory.add(
                VoiceAnalyzeHistoryItem(
                    role = ROLE_ASSISTANT,
                    content = result.toJsonString(),
                ),
            )

            when {
                result.intent == VoiceAnalyzeIntent.ASK -> {
                    analyzeRetryCount = 0
                    Log.d(TAG, "=== ASK → TTS 출력 후 재녹음 ===")
                    _uiState.value = _uiState.value.copy(
                        confirmationMessage = result.confirmationMessage,
                        ttsNonce = _uiState.value.ttsNonce + 1,
                    )
                }

                result.confirmed == false -> {
                    Log.d(TAG, "=== 부정 응답 → 히스토리 초기화 후 재녹음 ===")
                    conversationHistory.clear()
                    requestRetryRecording(result.confirmationMessage)
                }

                result.confirmed == null && result.intent != VoiceAnalyzeIntent.UNKNOWN -> {
                    analyzeRetryCount = 0
                    Log.d(TAG, "=== 확인 요청 단계: '${result.confirmationMessage}' ===")
                    _uiState.value = _uiState.value.copy(
                        confirmationMessage = result.confirmationMessage,
                        ttsNonce = _uiState.value.ttsNonce + 1,
                    )
                }

                result.confirmed == true -> {
                    analyzeRetryCount = 0
                    Log.d(TAG, "=== 확인 완료: intent=${result.intent} ===")
                    when (result.intent) {
                        VoiceAnalyzeIntent.PLACE_SEARCH ->
                            _uiEvent.send(LowVisionVoiceInputEvent.RecordingCompleted(result.placeName.orEmpty()))
                        VoiceAnalyzeIntent.CATEGORY_SEARCH ->
                            _uiEvent.send(LowVisionVoiceInputEvent.CategorySearchCompleted(result.category.orEmpty()))
                        VoiceAnalyzeIntent.BOOKMARK_ADD ->
                            _uiEvent.send(LowVisionVoiceInputEvent.BookmarkAddCompleted(result.placeName.orEmpty()))
                        VoiceAnalyzeIntent.BOOKMARK_DELETE ->
                            _uiEvent.send(LowVisionVoiceInputEvent.BookmarkDeleteCompleted(result.placeName.orEmpty()))
                        VoiceAnalyzeIntent.NAVIGATE ->
                            _uiEvent.send(LowVisionVoiceInputEvent.NavigateCompleted(
                                departure = result.departure.orEmpty(),
                                destination = result.destination.orEmpty()
                            ))
                        VoiceAnalyzeIntent.SHOW_BOOKMARKS ->
                            _uiEvent.send(LowVisionVoiceInputEvent.ShowBookmarksCompleted)
                        VoiceAnalyzeIntent.SHOW_FAVORITE_ROUTES ->
                            _uiEvent.send(LowVisionVoiceInputEvent.ShowFavoriteRoutesCompleted)
                        VoiceAnalyzeIntent.LOGOUT ->
                            _uiEvent.send(LowVisionVoiceInputEvent.LogoutCompleted)
                        VoiceAnalyzeIntent.NAVIGATION_END ->
                            _uiEvent.send(LowVisionVoiceInputEvent.NavigationEndCompleted)
                        else -> {
                            Log.d(TAG, "=== confirmed=true 미처리 intent → 히스토리 초기화 후 재녹음 ===")
                            conversationHistory.clear()
                            requestRetryRecording(result.confirmationMessage)
                        }
                    }
                }

                else -> {
                    Log.d(TAG, "=== 예외 케이스(UNKNOWN 포함) → 히스토리 초기화 후 재녹음 ===")
                    conversationHistory.clear()
                    requestRetryRecording(result.confirmationMessage)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "음성 분석 실패 — 재녹음: ${e.message}", e)
            if (e is VoiceAnalyzeApiException && e.httpStatusCode == HTTP_UNAUTHORIZED) {
                conversationHistory.clear()
                _uiState.value = _uiState.value.copy(confirmationMessage = null)
                _uiEvent.send(LowVisionVoiceInputEvent.RecordingCancelled)
                return
            }
            // 분석 실패 시 히스토리 초기화 후 재녹음
            conversationHistory.clear()
            withContext(Dispatchers.Main) { requestRetryRecording() }
        }
    }

    private suspend fun requestRetryRecording(message: String? = null) {
        analyzeRetryCount += 1
        if (analyzeRetryCount > MAX_ANALYZE_RETRY_COUNT) {
            Log.d(TAG, "=== 음성 분석 재시도 한도 초과 → 홈으로 복귀 ===")
            analyzeRetryCount = 0
            _uiState.value = _uiState.value.copy(confirmationMessage = null)
            _uiEvent.send(LowVisionVoiceInputEvent.RecordingCancelled)
            return
        }

        _uiState.value = _uiState.value.copy(
            confirmationMessage = message.takeUnless { it.isNullOrBlank() } ?: RETRY_MESSAGE,
            ttsNonce = _uiState.value.ttsNonce + 1,
        )
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stop()
        recordingJob?.cancel()
        vadManager?.release()
    }
}
