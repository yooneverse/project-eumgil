package com.ssafy.e102.eumgil.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ssafy.e102.eumgil.data.repository.ReportHistoryData
import com.ssafy.e102.eumgil.data.repository.ReportHistoryDetailData
import com.ssafy.e102.eumgil.data.repository.ReportHistorySource
import com.ssafy.e102.eumgil.data.repository.ReportProcessingStatus
import com.ssafy.e102.eumgil.data.repository.ReportRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReportHistoryViewModel(
    private val reportRepository: ReportRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ReportHistoryUiState())
    val uiState: StateFlow<ReportHistoryUiState> = mutableUiState.asStateFlow()

    private val mutableUiEvent = MutableSharedFlow<ReportHistoryUiEvent>()
    val uiEvent: SharedFlow<ReportHistoryUiEvent> = mutableUiEvent.asSharedFlow()

    private var observeHistoryJob: Job? = null
    private var loadDetailJob: Job? = null

    init {
        observeReportHistory()
    }

    fun onAction(action: ReportHistoryUiAction) {
        when (action) {
            ReportHistoryUiAction.BackClicked ->
                emitUiEvent(ReportHistoryUiEvent.NavigateBack)
            is ReportHistoryUiAction.ReportClicked ->
                loadReportDetail(action.outboxId)
            ReportHistoryUiAction.ReportCtaClicked ->
                emitUiEvent(ReportHistoryUiEvent.NavigateToReport)
            ReportHistoryUiAction.RetryClicked ->
                observeReportHistory()
            ReportHistoryUiAction.DetailBackClicked ->
                mutableUiState.update { state ->
                    state.copy(
                        selectedDetail = null,
                        detailLoadingHistoryId = null,
                    )
                }
        }
    }

    private fun observeReportHistory() {
        observeHistoryJob?.cancel()
        mutableUiState.update { state ->
            state.copy(
                screenState = ReportHistoryScreenState.LOADING,
                selectedDetail = null,
                detailLoadingHistoryId = null,
                errorMessage = null,
            )
        }
        observeHistoryJob =
            viewModelScope.launch {
                reportRepository.observeReportHistoryEntries()
                    .catch {
                        mutableUiState.update { state ->
                            state.copy(
                                screenState = ReportHistoryScreenState.ERROR,
                                reports = emptyList(),
                                selectedDetail = null,
                                detailLoadingHistoryId = null,
                                errorMessage = REPORT_HISTORY_LOAD_FAILURE_MESSAGE,
                            )
                        }
                    }
                    .collectLatest { historyItems ->
                        val reports =
                            historyItems
                                .sortedByDescending(ReportHistoryData::updatedAtMillis)
                                .map(ReportHistoryData::toReportHistoryUiModel)
                        mutableUiState.update { state ->
                            state.copy(
                                screenState =
                                    if (reports.isEmpty()) {
                                        ReportHistoryScreenState.EMPTY
                                    } else {
                                        ReportHistoryScreenState.CONTENT
                                    },
                                reports = reports,
                                selectedDetail =
                                    state.selectedDetail?.takeIf { detail ->
                                        reports.any { report -> report.outboxId == detail.historyId }
                                    },
                                errorMessage = null,
                            )
                        }
                    }
            }
    }

    private fun loadReportDetail(historyId: String) {
        loadDetailJob?.cancel()
        mutableUiState.update { state ->
            state.copy(
                detailLoadingHistoryId = historyId,
                selectedDetail = null,
            )
        }
        loadDetailJob =
            viewModelScope.launch {
                runCatching { reportRepository.getReportHistoryDetail(historyId) }
                    .fold(
                        onSuccess = { detail ->
                            if (detail == null) {
                                mutableUiState.update { state ->
                                    state.copy(detailLoadingHistoryId = null)
                                }
                                emitUiEvent(ReportHistoryUiEvent.ShowSnackbar(REPORT_DETAIL_LOAD_FAILURE_MESSAGE))
                            } else {
                                mutableUiState.update { state ->
                                    state.copy(
                                        selectedDetail = detail.toReportHistoryDetailUiModel(),
                                        detailLoadingHistoryId = null,
                                    )
                                }
                            }
                        },
                        onFailure = {
                            mutableUiState.update { state ->
                                state.copy(detailLoadingHistoryId = null)
                            }
                            emitUiEvent(ReportHistoryUiEvent.ShowSnackbar(REPORT_DETAIL_LOAD_FAILURE_MESSAGE))
                        },
                    )
            }
    }

    private fun emitUiEvent(event: ReportHistoryUiEvent) {
        viewModelScope.launch {
            mutableUiEvent.emit(event)
        }
    }

    companion object {
        private const val REPORT_HISTORY_LOAD_FAILURE_MESSAGE = "제보 내역을 불러오지 못했습니다."
        private const val REPORT_DETAIL_LOAD_FAILURE_MESSAGE = "제보 상세를 불러오지 못했습니다."

        fun provideFactory(reportRepository: ReportRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ReportHistoryViewModel::class.java)) {
                        return ReportHistoryViewModel(reportRepository = reportRepository) as T
                    }

                    error("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}

private fun ReportHistoryData.toReportHistoryUiModel(): ReportHistoryUiModel =
    ReportHistoryUiModel(
        outboxId = historyId,
        title = reportCategory.toReportHistoryTitle(),
        address = address?.takeIf { it.isNotBlank() } ?: toCoordinateText(),
        submittedAtText = updatedAtMillis.formatSubmittedAt(),
        photoUri = photoUri?.takeIf { it.isNotBlank() } ?: imageUrl?.takeIf { it.isNotBlank() },
        sourceLabel = source.toSourceLabel(),
        statusLabel = processingStatus.toStatusLabel(),
        isApproved = processingStatus == ReportProcessingStatus.APPROVED,
        updatedAtMillis = updatedAtMillis,
    )

private fun ReportHistoryDetailData.toReportHistoryDetailUiModel(): ReportHistoryDetailUiModel =
    ReportHistoryDetailUiModel(
        historyId = historyId,
        title = reportCategory.toReportHistoryTitle(),
        description = description?.takeIf { it.isNotBlank() } ?: "상세 설명이 없습니다.",
        locationText = address?.takeIf { it.isNotBlank() } ?: toCoordinateText(),
        submittedAtText = createdAtMillis.formatSubmittedAt(),
        imageCountText =
            if (imageRefs.isEmpty()) {
                "첨부 사진 없음"
            } else {
                "첨부 사진 ${imageRefs.size}장"
            },
        sourceLabel = source.toSourceLabel(),
        receiptNumberText = serverReportId?.let { "RP-$it" } ?: historyId,
        statusLabel = processingStatus.toStatusLabel(),
        isApproved = processingStatus == ReportProcessingStatus.APPROVED,
    )

private fun ReportHistoryData.toCoordinateText(): String =
    "위치 ${latitude.formatCoordinate()}, ${longitude.formatCoordinate()}"

private fun ReportHistoryDetailData.toCoordinateText(): String =
    "위치 ${latitude.formatCoordinate()}, ${longitude.formatCoordinate()}"

private fun Double.formatCoordinate(): String = String.format(Locale.US, "%.6f", this)

private fun ReportHistorySource.toSourceLabel(): String =
    when (this) {
        ReportHistorySource.Server -> "서버 이력"
        ReportHistorySource.LocalOutbox -> "로컬 저장"
    }

private fun ReportProcessingStatus?.toStatusLabel(): String =
    when (this) {
        ReportProcessingStatus.APPROVED -> "반영 완료"
        ReportProcessingStatus.REJECTED -> "처리 종료"
        ReportProcessingStatus.PENDING, null -> "접수됨"
    }

private fun String.toReportHistoryTitle(): String =
    when (this) {
        "STAIRS_STEP" -> "계단·단차 있음"
        "BRAILLE_BLOCK" -> "점자블록 문제"
        "SIDEWALK_MISSING" -> "인도 없음"
        "RAMP" -> "경사로 문제"
        "SIDEWALK_WIDTH" -> "인도폭 문제"
        "OTHER_OBSTACLE" -> "기타 장애물"
        "STAIRS" -> "계단·단차 있음"
        "TACTILE_BLOCK", "GUIDANCE_BLOCK" -> "점자블록 문제"
        "SLOPE" -> "경사로 문제"
        "CONSTRUCTION", "ELEVATOR", "FACILITY_DAMAGE" -> "기타 장애물"
        else -> "제보 내역"
    }

private fun Long.formatSubmittedAt(): String =
    SimpleDateFormat("yyyy.MM.dd", Locale.KOREAN).format(Date(this))
