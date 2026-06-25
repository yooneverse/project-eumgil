package com.ssafy.e102.eumgil.feature.auth

data class ProfileSetupUiState(
    val screenState: ProfileSetupScreenState = ProfileSetupScreenState.Editing,
    val draft: ProfileSetupDraft = ProfileSetupDraft(),
    val submitState: ProfileSetupSubmitState = ProfileSetupSubmitState.Idle,
    val completionBoundary: ProfileSetupCompletionBoundary = ProfileSetupCompletionBoundary.LOCAL_MOCK,
    val pendingBackendFields: Set<ProfileSetupPendingBackendField> =
        ProfileSetupPendingBackendField.entries.toSet(),
) {
    val canSubmitLocally: Boolean
        get() = screenState == ProfileSetupScreenState.Editing &&
            draft.isLocallyComplete &&
            submitState !is ProfileSetupSubmitState.Submitting
}

data class ProfileSetupDraft(
    val nickname: String = "",
    val userTypeSelection: ProfileSetupUserTypeSelection? = null,
) {
    val isLocallyComplete: Boolean
        get() = nickname.trim().isNotEmpty() && userTypeSelection != null
}

enum class ProfileSetupUserTypeSelection {
    WHEELCHAIR_USER,
    WALKING_ASSISTANCE,
}

enum class ProfileSetupCompletionBoundary {
    LOCAL_MOCK,
}

enum class ProfileSetupPendingBackendField {
    USER_TYPE_API_MAPPING,
    DISABILITY_GRADE,
    PHONE_NUMBER,
}

sealed interface ProfileSetupScreenState {
    data object InitialLoading : ProfileSetupScreenState

    data object Editing : ProfileSetupScreenState

    data object Submitting : ProfileSetupScreenState

    data object Completed : ProfileSetupScreenState

    data class Failure(
        val reason: ProfileSetupFailureReason,
    ) : ProfileSetupScreenState
}

sealed interface ProfileSetupSubmitState {
    data object Idle : ProfileSetupSubmitState

    data object Submitting : ProfileSetupSubmitState

    data object Completed : ProfileSetupSubmitState

    data class Failed(
        val reason: ProfileSetupFailureReason,
    ) : ProfileSetupSubmitState
}

sealed interface ProfileSetupUiAction {
    data object BackClicked : ProfileSetupUiAction

    data class NicknameChanged(
        val nickname: String,
    ) : ProfileSetupUiAction

    data class UserTypeSelected(
        val userTypeSelection: ProfileSetupUserTypeSelection,
    ) : ProfileSetupUiAction

    data object SubmitClicked : ProfileSetupUiAction

    data object RetrySubmitClicked : ProfileSetupUiAction
}

sealed interface ProfileSetupUiEvent {
    data object NavigateToNextRequiredGate : ProfileSetupUiEvent

    data object ScrollToFirstError : ProfileSetupUiEvent

    data class AnnounceForAccessibility(
        val message: String,
    ) : ProfileSetupUiEvent

    data class ShowSnackbar(
        val message: String,
    ) : ProfileSetupUiEvent
}

sealed interface ProfileSetupFailureReason {
    data object InvalidInput : ProfileSetupFailureReason

    data object LocalSaveFailed : ProfileSetupFailureReason

    data object NetworkUnavailable : ProfileSetupFailureReason

    data object Unknown : ProfileSetupFailureReason
}
