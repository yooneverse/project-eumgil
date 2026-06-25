package com.ssafy.e102.eumgil.feature.voiceassistant

import com.ssafy.e102.eumgil.data.repository.RouteEditingTarget

data class UiState(
    val context: VoiceAssistantContext = VoiceAssistantContext(),
    val status: VoiceAssistantStatus = VoiceAssistantStatus.Idle,
    val transcript: String = "",
    val pendingConfirmationAction: VoiceAssistantAction? = null,
    val lastResolvedAction: VoiceAssistantAction? = null,
    val errorMessage: String? = null,
)

data class VoiceAssistantContext(
    val currentRoute: String? = null,
    val currentTopLevelRoute: String? = null,
    val userType: VoiceAssistantUserType? = null,
    val editingTarget: RouteEditingTarget = RouteEditingTarget.DESTINATION,
)

enum class VoiceAssistantUserType {
    GENERAL,
    MOBILITY_IMPAIRED,
}

enum class VoiceAssistantStatus {
    Idle,
    Listening,
    Processing,
    AwaitingConfirmation,
    Error,
}

sealed interface UiAction {
    data object AssistantClicked : UiAction

    data object Dismissed : UiAction

    data class ContextChanged(
        val context: VoiceAssistantContext,
    ) : UiAction

    data class TranscriptChanged(
        val transcript: String,
    ) : UiAction

    data class ActionResolved(
        val action: VoiceAssistantAction,
    ) : UiAction

    data object ConfirmationAccepted : UiAction

    data object ConfirmationRejected : UiAction
}

sealed interface UiEvent {
    data object StartListening : UiEvent

    data object StopListening : UiEvent

    data class RequestConfirmation(
        val action: VoiceAssistantAction,
    ) : UiEvent

    data class ExecuteAction(
        val action: VoiceAssistantAction,
    ) : UiEvent

    data class ShowMessage(
        val message: String,
    ) : UiEvent
}

sealed interface VoiceAssistantAction {
    val requiresConfirmation: Boolean

    data class SearchPlace(
        val query: String,
        val editingTarget: RouteEditingTarget = RouteEditingTarget.DESTINATION,
        override val requiresConfirmation: Boolean = false,
    ) : VoiceAssistantAction

    data class OpenReport(
        val reportType: String? = null,
        val description: String? = null,
        override val requiresConfirmation: Boolean = false,
    ) : VoiceAssistantAction

    data class OpenSavedRoutes(
        override val requiresConfirmation: Boolean = false,
    ) : VoiceAssistantAction

    data class OpenMyPage(
        override val requiresConfirmation: Boolean = false,
    ) : VoiceAssistantAction

    data class OpenMap(
        override val requiresConfirmation: Boolean = false,
    ) : VoiceAssistantAction

    data class StopNavigation(
        override val requiresConfirmation: Boolean = true,
    ) : VoiceAssistantAction

    data class ResumeNavigationGuidance(
        override val requiresConfirmation: Boolean = false,
    ) : VoiceAssistantAction

    data class UnknownCommand(
        val rawCommand: String? = null,
        override val requiresConfirmation: Boolean = false,
    ) : VoiceAssistantAction

    data class CategorySearch(
        val category: String,
        override val requiresConfirmation: Boolean = false,
    ) : VoiceAssistantAction

    data class Navigate(
        val departure: String?,
        val destination: String,
        override val requiresConfirmation: Boolean = false,
    ) : VoiceAssistantAction

    data class ShowBookmarks(
        override val requiresConfirmation: Boolean = false,
    ) : VoiceAssistantAction

    data class Logout(
        override val requiresConfirmation: Boolean = false,
    ) : VoiceAssistantAction

    data class Ask(
        val message: String,
        override val requiresConfirmation: Boolean = false,
    ) : VoiceAssistantAction
}
