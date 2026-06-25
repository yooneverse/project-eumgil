package com.ssafy.e102.eumgil.feature.arrival

import com.ssafy.e102.eumgil.R

data class ArrivalUiState(
    val isEvaluationSheetVisible: Boolean = false,
    val selectedRating: Int = 0,
    val selectedRatingLabel: ArrivalEvaluationLabel = ArrivalEvaluationLabel.Idle,
    val hasRatingSession: Boolean = false,
    val isEvaluationSubmitting: Boolean = false,
    val routeSaveDraft: ArrivalRouteSaveDraftUiState? = null,
    val isRouteSaveSelected: Boolean = false,
    val routeSaveBookmarkId: String? = null,
    val isRouteSaveUpdating: Boolean = false,
) {
    val isEvaluationSubmitEnabled: Boolean
        get() = hasRatingSession && selectedRating > 0 && !isEvaluationSubmitting

    val hasRouteSaveTarget: Boolean
        get() = routeSaveDraft != null

    val isRouteSaveEnabled: Boolean
        get() = routeSaveDraft?.canSaveToServer == true && !isRouteSaveUpdating
}

data class ArrivalRouteSaveDraftUiState(
    val defaultRouteName: String,
    val startLabel: String,
    val endLabel: String,
    val routeOptionLabel: String,
    val distanceMeters: Int? = null,
    val durationMinutes: Int? = null,
    val canSaveToServer: Boolean,
)

enum class ArrivalEvaluationLabel(val labelResId: Int?) {
    Idle(labelResId = null),
    VeryDissatisfied(labelResId = R.string.arrival_evaluation_rating_very_dissatisfied),
    Dissatisfied(labelResId = R.string.arrival_evaluation_rating_dissatisfied),
    Neutral(labelResId = R.string.arrival_evaluation_rating_neutral),
    Satisfied(labelResId = R.string.arrival_evaluation_rating_satisfied),
    VerySatisfied(labelResId = R.string.arrival_evaluation_rating_very_satisfied),
}

sealed interface ArrivalUiAction {
    data object HomeClicked : ArrivalUiAction

    data object ExploreNewRouteClicked : ArrivalUiAction

    data class RatingSelected(val rating: Int) : ArrivalUiAction

    data object SaveRouteClicked : ArrivalUiAction

    data object SubmitEvaluationClicked : ArrivalUiAction

    data object EvaluationSheetDismissed : ArrivalUiAction
}

sealed interface ArrivalUiEvent {
    data object NavigateToMap : ArrivalUiEvent

    data object NavigateToSearch : ArrivalUiEvent

    data class ShowToast(
        val message: String,
    ) : ArrivalUiEvent
}
