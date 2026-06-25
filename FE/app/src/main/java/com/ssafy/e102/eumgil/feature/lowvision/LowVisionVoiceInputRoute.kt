package com.ssafy.e102.eumgil.feature.lowvision

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.tts.AndroidTextToSpeechController

/**
 * Route wrapper for [LowVisionVoiceInputScreen].
 *
 * [LowVisionVoiceInputViewModel]을 생성·연결하고, VAD+STT 파이프라인 결과를
 * NavGraph 콜백([onRecordingCompleted] / [onCancelRecording])으로 위임한다.
 */
@Composable
fun LowVisionVoiceInputRoute(
    onCancelRecording: () -> Unit,
    onRecordingCompleted: (String) -> Unit,
    onTabSelected: (LowVisionBottomTab) -> Unit,
    onCategorySearchCompleted: (String) -> Unit = {},
    onBookmarkAddCompleted: (String) -> Unit = {},
    onBookmarkDeleteCompleted: (String) -> Unit = {},
    onNavigateCompleted: (String, String) -> Unit = { _, _ -> },
    onShowBookmarksCompleted: () -> Unit = {},
    onShowFavoriteRoutesCompleted: () -> Unit = {},
    onLogoutCompleted: () -> Unit = {},
    onNavigationEndCompleted: () -> Unit = {},
    currentRoute: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: LowVisionVoiceInputViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ttsController = remember(context.applicationContext) {
        AndroidTextToSpeechController(context = context.applicationContext)
    }
    val ttsState by ttsController.state.collectAsStateWithLifecycle()
    val voiceInputPrompt = stringResource(R.string.voice_input_prompt)

    val playBeep: () -> Unit = remember {
        {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                Handler(Looper.getMainLooper()).postDelayed({ toneGen.release() }, 300)
            } catch (e: Exception) {
                // 비프음 실패해도 녹음 계속 진행
            }
        }
    }

    // currentRoute 변경 시 ViewModel에 전달
    LaunchedEffect(currentRoute) {
        viewModel.updateCurrentRoute(currentRoute)
    }

    // -1: 아직 speak()를 한 번도 호출하지 않은 상태.
    // completedUtteranceCount >= 0 조건을 함께 쓰면 앱 진입 시 spurious 트리거 방지.
    val lastCompletedCount = remember { mutableIntStateOf(-1) }

    // confirmationMessage TTS
    LaunchedEffect(uiState.confirmationMessage, uiState.ttsNonce) {
        val message = uiState.confirmationMessage
        if (!message.isNullOrBlank()) {
            ttsController.speak(message)
        }
    }

    // TTS 완료 감지 → beginRecording().
    // lastCompletedCount >= 0 이어야 실제로 speak()를 호출한 이후임을 보장한다.
    LaunchedEffect(ttsState.completedUtteranceCount) {
        if (lastCompletedCount.intValue >= 0 &&
            ttsState.completedUtteranceCount > lastCompletedCount.intValue
        ) {
            delay(200)    // TTS 잔향 대기
            playBeep()    // 비프음 (녹음 시작 신호)
            delay(300)    // AEC 안정화 대기
            viewModel.beginRecording()
        }
        lastCompletedCount.intValue = ttsState.completedUtteranceCount
    }

    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is LowVisionVoiceInputEvent.RecordingCompleted -> onRecordingCompleted(event.query)
                LowVisionVoiceInputEvent.RecordingCancelled -> onCancelRecording()
                LowVisionVoiceInputEvent.ReadyToRecord -> {
                    // AndroidTextToSpeechController가 내부적으로 pendingText를 처리하므로
                    // 엔진 초기화 전에 호출해도 초기화 완료 후 자동 재생됨
                    lastCompletedCount.intValue = ttsState.completedUtteranceCount
                    ttsController.speak(voiceInputPrompt)
                }
                is LowVisionVoiceInputEvent.CategorySearchCompleted ->
                    onCategorySearchCompleted(event.category)
                is LowVisionVoiceInputEvent.BookmarkAddCompleted ->
                    onBookmarkAddCompleted(event.placeName)
                is LowVisionVoiceInputEvent.BookmarkDeleteCompleted ->
                    onBookmarkDeleteCompleted(event.placeName)
                is LowVisionVoiceInputEvent.NavigateCompleted ->
                    onNavigateCompleted(event.departure, event.destination)
                LowVisionVoiceInputEvent.ShowBookmarksCompleted -> onShowBookmarksCompleted()
                LowVisionVoiceInputEvent.ShowFavoriteRoutesCompleted -> onShowFavoriteRoutesCompleted()
                LowVisionVoiceInputEvent.LogoutCompleted -> onLogoutCompleted()
                LowVisionVoiceInputEvent.NavigationEndCompleted -> onNavigationEndCompleted()
            }
        }
    }

    DisposableEffect(ttsController) {
        onDispose {
            ttsController.stop()
            ttsController.shutdown()
        }
    }

    LowVisionFontTheme {
        LowVisionVoiceInputScreen(
            onCancelRecording = viewModel::cancelRecording,
            modifier = modifier,
        )
    }
}
