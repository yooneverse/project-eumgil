package com.ssafy.e102.eumgil.feature.voiceassistant

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceAssistantOverlayTest {
    @Test
    fun `overlay visual states cover the requested assistant sheet states`() {
        assertEquals(
            listOf(
                VoiceAssistantOverlayVisualState.Idle,
                VoiceAssistantOverlayVisualState.Listening,
                VoiceAssistantOverlayVisualState.Processing,
                VoiceAssistantOverlayVisualState.ResultReady,
                VoiceAssistantOverlayVisualState.ConfirmationRequired,
                VoiceAssistantOverlayVisualState.Error,
            ),
            VoiceAssistantOverlayVisualState.entries.toList(),
        )
    }

    @Test
    fun `awaiting confirmation maps to confirmation required visual state`() {
        val uiState =
            UiState(
                status = VoiceAssistantStatus.AwaitingConfirmation,
                pendingConfirmationAction = VoiceAssistantAction.StopNavigation(),
            )

        assertEquals(
            VoiceAssistantOverlayVisualState.ConfirmationRequired,
            resolveVoiceAssistantOverlayVisualState(uiState),
        )
    }

    @Test
    fun `idle with a resolved action maps to result ready visual state`() {
        val uiState =
            UiState(
                status = VoiceAssistantStatus.Idle,
                lastResolvedAction = VoiceAssistantAction.OpenReport(),
            )

        assertEquals(
            VoiceAssistantOverlayVisualState.ResultReady,
            resolveVoiceAssistantOverlayVisualState(uiState),
        )
    }

    @Test
    fun `error message takes precedence over stale resolved action`() {
        val uiState =
            UiState(
                status = VoiceAssistantStatus.Idle,
                lastResolvedAction = VoiceAssistantAction.OpenReport(),
                errorMessage = "assistant error",
            )

        assertEquals(
            VoiceAssistantOverlayVisualState.Error,
            resolveVoiceAssistantOverlayVisualState(uiState),
        )
    }
}
