package com.ssafy.e102.eumgil.feature.report

data class ReportHistoryUiState(
    val screenState: ReportHistoryScreenState = ReportHistoryScreenState.LOADING,
    val reports: List<ReportHistoryUiModel> = emptyList(),
    val selectedDetail: ReportHistoryDetailUiModel? = null,
    val detailLoadingHistoryId: String? = null,
    val errorMessage: String? = null,
)

data class ReportHistoryUiModel(
    val outboxId: String,
    val title: String,
    val address: String,
    val submittedAtText: String,
    val photoUri: String?,
    val sourceLabel: String,
    val statusLabel: String,
    val isApproved: Boolean,
    val updatedAtMillis: Long,
)

data class ReportHistoryDetailUiModel(
    val historyId: String,
    val title: String,
    val description: String,
    val locationText: String,
    val submittedAtText: String,
    val imageCountText: String,
    val sourceLabel: String,
    val receiptNumberText: String,
    val statusLabel: String,
    val isApproved: Boolean,
)

enum class ReportHistoryScreenState {
    LOADING,
    CONTENT,
    EMPTY,
    ERROR,
}

sealed interface ReportHistoryUiAction {
    data object BackClicked : ReportHistoryUiAction

    data object ReportCtaClicked : ReportHistoryUiAction

    data object RetryClicked : ReportHistoryUiAction

    data object DetailBackClicked : ReportHistoryUiAction

    data class ReportClicked(
        val outboxId: String,
    ) : ReportHistoryUiAction
}

sealed interface ReportHistoryUiEvent {
    data object NavigateBack : ReportHistoryUiEvent

    data object NavigateToReport : ReportHistoryUiEvent

    data class ShowSnackbar(
        val message: String,
    ) : ReportHistoryUiEvent
}
