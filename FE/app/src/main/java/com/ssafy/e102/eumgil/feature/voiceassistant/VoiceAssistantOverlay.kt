package com.ssafy.e102.eumgil.feature.voiceassistant

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.feature.search.SearchVoiceInputEvent
import com.ssafy.e102.eumgil.feature.search.SearchVoiceInputViewModel
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing
import com.ssafy.e102.eumgil.core.designsystem.theme.EumStatusDanger
import com.ssafy.e102.eumgil.core.designsystem.theme.EumStatusWarning
import com.ssafy.e102.eumgil.core.tts.AndroidTextToSpeechController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class VoiceAssistantOverlayVisualState {
    Idle,
    Listening,
    Processing,
    ResultReady,
    ConfirmationRequired,
    Error,
}

private data class VoiceAssistantOverlayStateCopy(
    @StringRes val badgeRes: Int,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
)

internal fun resolveVoiceAssistantOverlayVisualState(uiState: UiState): VoiceAssistantOverlayVisualState =
    when {
        uiState.status == VoiceAssistantStatus.Error || uiState.errorMessage.isNullOrBlank().not() ->
            VoiceAssistantOverlayVisualState.Error

        uiState.status == VoiceAssistantStatus.AwaitingConfirmation ->
            VoiceAssistantOverlayVisualState.ConfirmationRequired

        uiState.status == VoiceAssistantStatus.Listening ->
            VoiceAssistantOverlayVisualState.Listening

        uiState.status == VoiceAssistantStatus.Processing ->
            VoiceAssistantOverlayVisualState.Processing

        uiState.lastResolvedAction != null ->
            VoiceAssistantOverlayVisualState.ResultReady

        else -> VoiceAssistantOverlayVisualState.Idle
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAssistantOverlay(
    uiState: UiState,
    onAction: (UiAction) -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier,
    bottomSheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    var shouldRender by remember { mutableStateOf(visible) }
    val coroutineScope = rememberCoroutineScope()
    val currentOnAction by rememberUpdatedState(onAction)

    fun requestDismiss() {
        coroutineScope.launch {
            if (bottomSheetState.isVisible) {
                bottomSheetState.hide()
            }
            shouldRender = false
            currentOnAction(UiAction.Dismissed)
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            shouldRender = true
        } else if (shouldRender) {
            if (bottomSheetState.isVisible) {
                bottomSheetState.hide()
            }
            shouldRender = false
        }
    }

    LaunchedEffect(shouldRender, visible) {
        if (shouldRender && visible && !bottomSheetState.isVisible) {
            bottomSheetState.show()
        }
    }

    val context = LocalContext.current
    val sttViewModel: SearchVoiceInputViewModel = viewModel()
    val ttsController = remember(context.applicationContext) {
        AndroidTextToSpeechController(context = context.applicationContext)
    }
    val ttsState by ttsController.state.collectAsStateWithLifecycle()
    val voiceInputPrompt = stringResource(R.string.voice_input_prompt)
    val lastCompletedCount = remember { mutableIntStateOf(-1) }
    val playBeep: () -> Unit = remember {
        {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                Handler(Looper.getMainLooper()).postDelayed({ toneGen.release() }, 300)
            } catch (e: Exception) {
                // 비프음 실패해도 계속 진행
            }
        }
    }

    // Listening 상태 진입 시 STT 모델 초기화 시작, 그 외엔 녹음 중단
    LaunchedEffect(uiState.status) {
        if (uiState.status == VoiceAssistantStatus.Listening) {
            sttViewModel.startListening()
        } else {
            sttViewModel.stopListening()
        }
    }

    // STT 이벤트 수신: ReadyToRecord → TTS, TranscriptReady → VM 전달
    LaunchedEffect(sttViewModel, ttsController) {
        sttViewModel.uiEvent.collect { event ->
            when (event) {
                SearchVoiceInputEvent.ReadyToRecord -> {
                    lastCompletedCount.intValue = ttsState.completedUtteranceCount
                    ttsController.speak(voiceInputPrompt)
                }
                is SearchVoiceInputEvent.TranscriptReady ->
                    currentOnAction(UiAction.TranscriptChanged(event.recognizedText))
                SearchVoiceInputEvent.TranscriptEmpty ->
                    currentOnAction(UiAction.Dismissed)
                is SearchVoiceInputEvent.SpeakError ->
                    ttsController.speak(event.text)
            }
        }
    }

    // TTS 완료 → 비프음 → STT 녹음 시작
    LaunchedEffect(ttsState.completedUtteranceCount) {
        if (
            lastCompletedCount.intValue >= 0 &&
            ttsState.completedUtteranceCount > lastCompletedCount.intValue
        ) {
            delay(200)
            playBeep()
            delay(300)
            sttViewModel.beginRecording()
        }
        lastCompletedCount.intValue = ttsState.completedUtteranceCount
    }

    DisposableEffect(sttViewModel, ttsController) {
        onDispose {
            sttViewModel.stopListening()
            ttsController.stop()
            ttsController.shutdown()
        }
    }

    if (!shouldRender) return

    val visualState = resolveVoiceAssistantOverlayVisualState(uiState)

    ModalBottomSheet(
        onDismissRequest = { requestDismiss() },
        sheetState = bottomSheetState,
        dragHandle = null,
        shape =
            RoundedCornerShape(
                topStart = EumRadius.scaleL,
                topEnd = EumRadius.scaleL,
                bottomEnd = 0.dp,
                bottomStart = 0.dp,
            ),
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.38f),
        windowInsets = VoiceAssistantOverlayWindowInsets,
    ) {
        VoiceAssistantOverlayContent(
            visualState = visualState,
            onAction = { action ->
                if (action == UiAction.Dismissed) {
                    requestDismiss()
                } else {
                    onAction(action)
                }
            },
            modifier =
                modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = EumSpacing.large, vertical = EumSpacing.small),
        )
    }
}

@Composable
private fun VoiceAssistantOverlayContent(
    visualState: VoiceAssistantOverlayVisualState,
    onAction: (UiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stateCopy = voiceAssistantOverlayStateCopy(visualState)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
    ) {
        VoiceAssistantOverlayHeader(
            onDismiss = { onAction(UiAction.Dismissed) },
        )
        VoiceAssistantOverlayStatusHero(
            stateCopy = stateCopy,
            visualState = visualState,
        )
        VoiceAssistantOverlayActions(
            visualState = visualState,
            onAction = onAction,
        )
    }
}

@Composable
private fun VoiceAssistantOverlayHeader(
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EumSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(id = R.string.voice_assistant_overlay_title),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onDismiss) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_close),
                contentDescription = stringResource(id = R.string.voice_assistant_overlay_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VoiceAssistantOverlayStatusHero(
    stateCopy: VoiceAssistantOverlayStateCopy,
    visualState: VoiceAssistantOverlayVisualState,
) {
    val badge = stringResource(id = stateCopy.badgeRes)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    stateDescription = badge
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
    ) {
        Surface(
            shape = CircleShape,
            color = voiceAssistantOverlayAccentContainerColor(visualState),
        ) {
            Box(
                modifier = Modifier.size(VoiceAssistantOverlayStatusIconContainerSize),
                contentAlignment = Alignment.Center,
            ) {
                if (visualState == VoiceAssistantOverlayVisualState.Processing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(VoiceAssistantOverlayProgressSize),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        painter = painterResource(id = stateCopy.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(VoiceAssistantOverlayStatusIconSize),
                        tint = voiceAssistantOverlayAccentColor(visualState),
                    )
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(EumRadius.full),
            color = voiceAssistantOverlayAccentContainerColor(visualState),
            border = BorderStroke(1.dp, voiceAssistantOverlayAccentColor(visualState).copy(alpha = 0.18f)),
        ) {
            Text(
                text = badge,
                modifier = Modifier.padding(horizontal = EumSpacing.medium, vertical = EumSpacing.xSmall),
                style = MaterialTheme.typography.labelLarge,
                color = voiceAssistantOverlayAccentColor(visualState),
            )
        }
        Text(
            text = stringResource(id = stateCopy.titleRes),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun VoiceAssistantOverlayActions(
    visualState: VoiceAssistantOverlayVisualState,
    onAction: (UiAction) -> Unit,
) {
    when (visualState) {
        VoiceAssistantOverlayVisualState.ConfirmationRequired -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
            ) {
                OutlinedButton(
                    onClick = { onAction(UiAction.ConfirmationRejected) },
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = VoiceAssistantOverlayButtonMinHeight),
                ) {
                    Text(text = stringResource(id = R.string.voice_assistant_overlay_action_cancel))
                }
                Button(
                    onClick = { onAction(UiAction.ConfirmationAccepted) },
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = VoiceAssistantOverlayButtonMinHeight),
                ) {
                    Text(text = stringResource(id = R.string.voice_assistant_overlay_action_confirm))
                }
            }
        }

        VoiceAssistantOverlayVisualState.Error -> {
            Button(
                onClick = { onAction(UiAction.AssistantClicked) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = VoiceAssistantOverlayButtonMinHeight),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text(text = stringResource(id = R.string.voice_assistant_overlay_action_retry))
            }
        }

        VoiceAssistantOverlayVisualState.Processing -> {
            Button(
                onClick = {},
                enabled = false,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = VoiceAssistantOverlayButtonMinHeight),
            ) {
                Text(text = stringResource(id = R.string.voice_assistant_overlay_action_processing))
            }
        }

        VoiceAssistantOverlayVisualState.Listening -> {
            OutlinedButton(
                onClick = { onAction(UiAction.Dismissed) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = VoiceAssistantOverlayButtonMinHeight),
            ) {
                Text(text = stringResource(id = R.string.voice_assistant_overlay_action_listening_cancel))
            }
        }

        VoiceAssistantOverlayVisualState.Idle,
        VoiceAssistantOverlayVisualState.ResultReady,
        -> {
            Button(
                onClick = { onAction(UiAction.AssistantClicked) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = VoiceAssistantOverlayButtonMinHeight),
            ) {
                Text(
                    text =
                        stringResource(
                            id =
                                if (visualState == VoiceAssistantOverlayVisualState.ResultReady) {
                                    R.string.voice_assistant_overlay_action_retry
                                } else {
                                    R.string.voice_assistant_overlay_action_start
                                },
                        ),
                )
            }
        }
    }
}

private fun voiceAssistantOverlayStateCopy(
    visualState: VoiceAssistantOverlayVisualState,
): VoiceAssistantOverlayStateCopy =
    when (visualState) {
        VoiceAssistantOverlayVisualState.Idle ->
            VoiceAssistantOverlayStateCopy(
                badgeRes = R.string.voice_assistant_overlay_status_idle_badge,
                titleRes = R.string.voice_assistant_overlay_status_idle_title,
                iconRes = R.drawable.ic_voice_mic,
            )

        VoiceAssistantOverlayVisualState.Listening ->
            VoiceAssistantOverlayStateCopy(
                badgeRes = R.string.voice_assistant_overlay_status_listening_badge,
                titleRes = R.string.voice_assistant_overlay_status_listening_title,
                iconRes = R.drawable.ic_voice_mic,
            )

        VoiceAssistantOverlayVisualState.Processing ->
            VoiceAssistantOverlayStateCopy(
                badgeRes = R.string.voice_assistant_overlay_status_processing_badge,
                titleRes = R.string.voice_assistant_overlay_status_processing_title,
                iconRes = R.drawable.ic_status_processing,
            )

        VoiceAssistantOverlayVisualState.ResultReady ->
            VoiceAssistantOverlayStateCopy(
                badgeRes = R.string.voice_assistant_overlay_status_result_ready_badge,
                titleRes = R.string.voice_assistant_overlay_status_result_ready_title,
                iconRes = R.drawable.ic_status_check,
            )

        VoiceAssistantOverlayVisualState.ConfirmationRequired ->
            VoiceAssistantOverlayStateCopy(
                badgeRes = R.string.voice_assistant_overlay_status_confirmation_required_badge,
                titleRes = R.string.voice_assistant_overlay_status_confirmation_required_title,
                iconRes = R.drawable.ic_status_warning,
            )

        VoiceAssistantOverlayVisualState.Error ->
            VoiceAssistantOverlayStateCopy(
                badgeRes = R.string.voice_assistant_overlay_status_error_badge,
                titleRes = R.string.voice_assistant_overlay_status_error_title,
                iconRes = R.drawable.ic_status_danger,
            )
    }

@Composable
private fun voiceAssistantOverlayAccentColor(
    visualState: VoiceAssistantOverlayVisualState,
): Color =
    when (visualState) {
        VoiceAssistantOverlayVisualState.ConfirmationRequired -> EumStatusWarning
        VoiceAssistantOverlayVisualState.Error -> EumStatusDanger
        else -> MaterialTheme.colorScheme.primary
    }

@Composable
private fun voiceAssistantOverlayAccentContainerColor(
    visualState: VoiceAssistantOverlayVisualState,
): Color =
    when (visualState) {
        VoiceAssistantOverlayVisualState.ConfirmationRequired -> EumStatusWarning.copy(alpha = 0.14f)
        VoiceAssistantOverlayVisualState.Error -> EumStatusDanger.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.54f)
    }

private val VoiceAssistantOverlayWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0)
private val VoiceAssistantOverlayButtonMinHeight = 48.dp
private val VoiceAssistantOverlayStatusIconContainerSize = 88.dp
private val VoiceAssistantOverlayStatusIconSize = 36.dp
private val VoiceAssistantOverlayProgressSize = 40.dp
