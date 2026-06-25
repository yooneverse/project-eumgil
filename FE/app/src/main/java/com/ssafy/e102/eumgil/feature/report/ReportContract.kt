package com.ssafy.e102.eumgil.feature.report

import com.ssafy.e102.eumgil.data.repository.ReportProcessingCounts

object ReportFormLimits {
    const val DESCRIPTION_MAX_LENGTH = 300
    const val ADDRESS_MAX_LENGTH = 120
    const val PHOTO_MAX_BYTES = 10L * 1024L * 1024L
    const val PHOTO_MAX_COUNT = 5
}

data class ReportUiState(
    val screenState: ReportScreenState = ReportScreenState.Editing,
    val currentStep: ReportStep = ReportStep.Home,
    val entryPoint: ReportEntryPoint = ReportEntryPoint.TopLevel,
    val reportType: ReportTypeInput = ReportTypeInput(),
    val location: ReportLocationInput = ReportLocationInput(),
    val photo: ReportPhotoInput = ReportPhotoInput(),
    val description: ReportDescriptionInput = ReportDescriptionInput(),
    val outboxState: ReportOutboxState = ReportOutboxState.NotSaved,
    val submitState: ReportSubmitState = ReportSubmitState.Idle,
    val processingCounts: ReportProcessingCounts = ReportProcessingCounts(),
    val recentReports: List<ReportRecentUiModel> = emptyList(),
    val submittedAtMillis: Long? = null,
    // Task 4.1 — 단말 네트워크 연결성. 오프라인이면 서버 제출 자체를 막아 무의미한 retry를 피한다.
    // 기본값 true: 정보가 없을 때는 사용자가 시도할 수 있게 두는 게 더 자연스럽다.
    val isOnline: Boolean = true,
) {
    val isLocationStepConfirmable: Boolean
        get() = location.value != null && location.error == null

    val isSubmitEnabled: Boolean
        get() = screenState == ReportScreenState.Editing &&
            submitState !is ReportSubmitState.Submitting &&
            reportType.value != null &&
            location.value != null &&
            reportType.error == null &&
            location.error == null &&
            photo.error == null &&
            description.error == null &&
            // Task 4.1 — 오프라인일 때는 사용자가 버튼을 눌러도 결국 실패하므로 미리 disabled 처리.
            isOnline
}

enum class ReportStep {
    Home,
    TypeSelection,
    LocationConfirm,
    DetailInput,
    Complete,
}

enum class ReportEntryPoint {
    TopLevel,
    NavigationGuidance,
    VoiceAssistant,
}

data class ReportRecentUiModel(
    val historyId: String,
    val title: String,
    val address: String,
    val submittedAtText: String,
    val statusLabel: String,
    val isApproved: Boolean,
)

data class ReportTypeInput(
    val value: ReportType? = null,
    val isTouched: Boolean = false,
    val isDirty: Boolean = false,
    val error: ReportTypeError? = null,
)

data class ReportLocationInput(
    val value: ReportLocation? = null,
    val addressText: String = "",
    val source: ReportLocationSource = ReportLocationSource.None,
    val isTouched: Boolean = false,
    val isDirty: Boolean = false,
    val isResolvingCurrentLocation: Boolean = false,
    val error: ReportLocationError? = null,
)

data class ReportPhotoInput(
    val values: List<ReportPhoto> = emptyList(),
    val isTouched: Boolean = false,
    val isDirty: Boolean = false,
    val error: ReportPhotoError? = null,
) {
    val count: Int get() = values.size
    val canAddMore: Boolean get() = values.size < ReportFormLimits.PHOTO_MAX_COUNT
    val firstOrNull: ReportPhoto? get() = values.firstOrNull()
}

data class ReportDescriptionInput(
    val value: String = "",
    val isTouched: Boolean = false,
    val isDirty: Boolean = false,
    val error: ReportDescriptionError? = null,
) {
    val trimmedValue: String
        get() = value.trim()

    val remainingLength: Int
        get() = ReportFormLimits.DESCRIPTION_MAX_LENGTH - value.length
}

data class ReportLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
)

data class ReportPhoto(
    val localUri: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
)

// apiValue codes match server `ReportType` enum defined in 제보 API 명세 (2026-04-29).
enum class ReportType(
    val apiValue: String,
) {
    STAIRS_STEP("STAIRS_STEP"),
    BRAILLE_BLOCK("BRAILLE_BLOCK"),
    SIDEWALK_MISSING("SIDEWALK_MISSING"),
    RAMP("RAMP"),
    SIDEWALK_WIDTH("SIDEWALK_WIDTH"),
    OTHER_OBSTACLE("OTHER_OBSTACLE"),
}

enum class ReportLocationSource {
    None,
    CurrentLocation,
    MapPin,
    AddressText,
}

sealed interface ReportScreenState {
    data object InitialLoading : ReportScreenState

    data object Editing : ReportScreenState

    data object Submitting : ReportScreenState

    data object Completed : ReportScreenState

    data class Failure(
        val reason: ReportFailureReason,
    ) : ReportScreenState
}

sealed interface ReportOutboxState {
    data object NotSaved : ReportOutboxState

    data object Saving : ReportOutboxState

    data class Saved(
        val outboxId: String,
    ) : ReportOutboxState

    data class Failed(
        val reason: ReportFailureReason,
    ) : ReportOutboxState
}

sealed interface ReportSubmitState {
    data object Idle : ReportSubmitState

    data object Submitting : ReportSubmitState

    data class Success(
        val reportId: Long? = null,
    ) : ReportSubmitState

    data class Failed(
        val reason: ReportFailureReason,
    ) : ReportSubmitState
}

sealed interface ReportUiAction {
    data class RouteEntered(
        val entryPoint: ReportEntryPoint,
        val startNew: Boolean = false,
    ) : ReportUiAction

    data object BackClicked : ReportUiAction

    data class ReportTypeSelected(
        val type: ReportType,
    ) : ReportUiAction

    data object ReportTypeBlurred : ReportUiAction

    data object CurrentLocationResetClicked : ReportUiAction

    /**
     * 권한 다이얼로그 dismiss 후 Activity ON_RESUME에서 dispatch된다.
     * Route가 lifecycle observer를 통해 한 번씩 보내며, ViewModel은 pending 중인 현재 위치
     * 요청이 있으면 새 권한 state로 흐름을 재개·종료한다.
     */
    data object RefreshLocationPermission : ReportUiAction

    data class LocationSelected(
        val location: ReportLocation,
        val source: ReportLocationSource,
    ) : ReportUiAction

    data class AddressTextChanged(
        val address: String,
    ) : ReportUiAction

    data object LocationBlurred : ReportUiAction

    data object PhotoAddClicked : ReportUiAction

    data class PhotoSelected(
        val photo: ReportPhoto,
    ) : ReportUiAction

    data class PhotoRemovedAt(
        val index: Int,
    ) : ReportUiAction

    data object PhotoBlurred : ReportUiAction

    data class DescriptionChanged(
        val description: String,
    ) : ReportUiAction

    data object DescriptionBlurred : ReportUiAction

    data object SubmitClicked : ReportUiAction

    data object RetrySubmitClicked : ReportUiAction

    data object NextStepClicked : ReportUiAction

    data object ReportHistoryClicked : ReportUiAction

    data class RecentReportClicked(
        val historyId: String,
    ) : ReportUiAction

    data object StartNewReportClicked : ReportUiAction

    data object BackToMapClicked : ReportUiAction

    data object TabReentered : ReportUiAction
}

sealed interface ReportUiEvent {
    data object NavigateBack : ReportUiEvent

    data class ReturnToNavigationWithSubmittedReport(
        val reportId: Long,
    ) : ReportUiEvent

    data object RequestLocationPermission : ReportUiEvent

    data object OpenPhotoPicker : ReportUiEvent

    data object ScrollToFirstError : ReportUiEvent

    data class AnnounceForAccessibility(
        val message: String,
    ) : ReportUiEvent

    data class NavigateToReportHistory(
        val historyId: String? = null,
    ) : ReportUiEvent

    data object NavigateToMap : ReportUiEvent
}

sealed interface ReportFailureReason {
    data object InvalidInput : ReportFailureReason

    data object LocationPermissionDenied : ReportFailureReason

    data object CurrentLocationUnavailable : ReportFailureReason

    data object NetworkUnavailable : ReportFailureReason

    data object LocalSaveFailed : ReportFailureReason

    data object ServerSubmitFailed : ReportFailureReason

    data object Unauthorized : ReportFailureReason

    data object Unknown : ReportFailureReason
}

enum class ReportTypeError {
    Required,
}

enum class ReportLocationError {
    Required,
    InvalidCoordinate,
    AddressTooLong,
    PermissionDenied,
    CurrentLocationUnavailable,
}

enum class ReportPhotoError {
    UnsupportedFormat,
    TooLarge,
    Unreadable,
    TooMany,
}

enum class ReportDescriptionError {
    TooLong,
}
