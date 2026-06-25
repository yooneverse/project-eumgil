package com.ssafy.e102.eumgil.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ssafy.e102.eumgil.core.location.CurrentLocationAddressResolver
import com.ssafy.e102.eumgil.core.location.CurrentLocationManager
import com.ssafy.e102.eumgil.core.location.LocationPermissionManager
import com.ssafy.e102.eumgil.core.location.NoOpCurrentLocationAddressResolver
import com.ssafy.e102.eumgil.core.network.AlwaysOnlineNetworkMonitor
import com.ssafy.e102.eumgil.core.network.NetworkMonitor
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.location.LocationPermissionState
import com.ssafy.e102.eumgil.core.location.LocationSnapshot
import com.ssafy.e102.eumgil.core.location.isFreshCurrentLocation
import com.ssafy.e102.eumgil.data.repository.ReportHistoryData
import com.ssafy.e102.eumgil.data.repository.ReportOutboxData
import com.ssafy.e102.eumgil.data.repository.ReportOutboxPhotoData
import com.ssafy.e102.eumgil.data.repository.ReportProcessingCounts
import com.ssafy.e102.eumgil.data.repository.ReportProcessingStatus
import com.ssafy.e102.eumgil.data.repository.ReportRepository
import com.ssafy.e102.eumgil.data.repository.ReportSubmitFailureReason
import com.ssafy.e102.eumgil.data.repository.ReportSubmitResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ReportViewModel(
    private val reportRepository: ReportRepository,
    private val currentLocationManager: CurrentLocationManager,
    private val locationPermissionManager: LocationPermissionManager,
    private val addressResolver: CurrentLocationAddressResolver = NoOpCurrentLocationAddressResolver,
    // Task 4.1 — 단말 네트워크 가용성 관찰자. 기본값은 항상 online으로 보고하는 no-op (테스트 더블 호환).
    private val networkMonitor: NetworkMonitor = AlwaysOnlineNetworkMonitor,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ReportUiState(isOnline = networkMonitor.isCurrentlyOnline))
    val uiState: StateFlow<ReportUiState> = mutableUiState.asStateFlow()

    private val mutableUiEvent = MutableSharedFlow<ReportUiEvent>()
    val uiEvent: SharedFlow<ReportUiEvent> = mutableUiEvent.asSharedFlow()

    // ─── 현재 위치 one-shot resolution 상태 ───────────────────────────────
    // 권한 요청 대기 중인지. true인 동안 RefreshLocationPermission 액션으로 흐름 재개·종료.
    private var pendingCurrentLocationRequest = false
    // 진행 중인 위치 fetch job (timeout 포함). 화면 이탈/취소 시 정리 대상.
    private var currentLocationJob: Job? = null
    // 권한 다이얼로그가 응답 없이 너무 오래 대기 시 fallback timeout.
    private var permissionPendingTimeoutJob: Job? = null

    init {
        observeNetworkAvailability()
        observeReportOverview()
    }

    /**
     * Task 4.1 — 단말 네트워크 가용성을 ViewModel 수명동안 구독해 `isOnline`을 갱신한다.
     * 사용자가 비행기 모드를 토글해도 제출 버튼 상태가 즉시 반영된다.
     */
    private fun observeNetworkAvailability() {
        viewModelScope.launch {
            networkMonitor.observeOnlineState().collect { online ->
                mutableUiState.update { state -> state.copy(isOnline = online) }
            }
        }
    }

    private fun observeReportOverview() {
        viewModelScope.launch {
            reportRepository.observeReportHistoryEntries().collect { reports ->
                mutableUiState.update { state ->
                    state.copy(
                        processingCounts = reports.toProcessingCounts(),
                        recentReports =
                            reports
                                .sortedByDescending(ReportHistoryData::updatedAtMillis)
                                .take(REPORT_HOME_RECENT_LIMIT)
                                .map(ReportHistoryData::toReportRecentUiModel),
                    )
                }
            }
        }
    }

    fun onAction(action: ReportUiAction) {
        when (action) {
            is ReportUiAction.RouteEntered -> handleRouteEntered(action.entryPoint, action.startNew)
            ReportUiAction.BackClicked -> handleBackClicked()

            is ReportUiAction.ReportTypeSelected -> selectReportType(action.type)
            ReportUiAction.ReportTypeBlurred -> touchReportType()
            ReportUiAction.CurrentLocationResetClicked -> requestCurrentLocation()
            ReportUiAction.RefreshLocationPermission -> handleRefreshLocationPermission()
            is ReportUiAction.LocationSelected -> selectLocation(action.location, action.source)
            is ReportUiAction.AddressTextChanged -> updateAddressText(action.address)
            ReportUiAction.LocationBlurred -> touchLocation()
            ReportUiAction.PhotoAddClicked -> handlePhotoAddClicked()
            is ReportUiAction.PhotoSelected -> selectPhoto(action.photo)
            is ReportUiAction.PhotoRemovedAt -> removePhotoAt(action.index)
            ReportUiAction.PhotoBlurred -> touchPhoto()
            is ReportUiAction.DescriptionChanged -> updateDescription(action.description)
            ReportUiAction.DescriptionBlurred -> touchDescription()
            ReportUiAction.NextStepClicked -> advanceStep()
            ReportUiAction.ReportHistoryClicked -> {
                if (mutableUiState.value.screenState is ReportScreenState.Completed) {
                    resetForm()
                }
                emitUiEvent(ReportUiEvent.NavigateToReportHistory())
            }
            is ReportUiAction.RecentReportClicked -> {
                if (mutableUiState.value.screenState is ReportScreenState.Completed) {
                    resetForm()
                }
                emitUiEvent(ReportUiEvent.NavigateToReportHistory(action.historyId))
            }
            ReportUiAction.StartNewReportClicked -> {
                resetForm(
                    currentStep = ReportStep.TypeSelection,
                )
            }
            ReportUiAction.BackToMapClicked -> {
                if (mutableUiState.value.screenState is ReportScreenState.Completed) {
                    resetForm()
                }
                emitUiEvent(ReportUiEvent.NavigateToMap)
            }
            ReportUiAction.TabReentered -> handleTabReentered()
            ReportUiAction.SubmitClicked,
            ReportUiAction.RetrySubmitClicked -> submitReport()
        }
    }

    private fun handleTabReentered() {
        resetForm()
    }

    private fun handleRouteEntered(
        entryPoint: ReportEntryPoint,
        startNew: Boolean,
    ) {
        resetForm(
            entryPoint = entryPoint,
            currentStep = if (startNew) ReportStep.TypeSelection else ReportStep.Home,
        )
    }

    private fun handleBackClicked() {
        val state = mutableUiState.value
        val previousStep = state.currentStep.previousOrNull(state.entryPoint)
        if (previousStep == null) {
            emitUiEvent(ReportUiEvent.NavigateBack)
        } else {
            mutableUiState.update { state -> state.copy(currentStep = previousStep) }
        }
    }

    private fun advanceStep() {
        val state = mutableUiState.value
        val nextStep =
            when (state.currentStep) {
                ReportStep.Home -> ReportStep.TypeSelection
                ReportStep.TypeSelection ->
                    if (state.reportType.value != null) ReportStep.LocationConfirm else null
                ReportStep.LocationConfirm ->
                    if (state.isLocationStepConfirmable) ReportStep.DetailInput else null
                ReportStep.DetailInput, ReportStep.Complete -> null
            }
        if (nextStep != null) {
            mutableUiState.update { it.copy(currentStep = nextStep) }
        }
    }

    private fun selectReportType(type: ReportType) {
        val state = mutableUiState.value

        // 같은 type을 다시 누른 경우는 idempotent. TypeSelection 단계라면 다음 단계로만 진행한다.
        if (state.reportType.value == type) {
            if (state.currentStep == ReportStep.Home || state.currentStep == ReportStep.TypeSelection) {
                applyReportType(type)
            }
            return
        }

        applyReportType(type)
    }

    private fun applyReportType(type: ReportType) {
        mutableUiState.update { state ->
            val nextStep =
                when (state.currentStep) {
                    ReportStep.Home -> ReportStep.LocationConfirm
                    ReportStep.TypeSelection -> ReportStep.TypeSelection
                    else -> state.currentStep
                }
            state.copy(
                screenState = ReportScreenState.Editing,
                currentStep = nextStep,
                reportType = state.reportType.withValue(type),
                outboxState = ReportOutboxState.NotSaved,
                submitState = ReportSubmitState.Idle,
            )
        }
    }

    private fun touchReportType() {
        mutableUiState.update { state ->
            state.copy(
                reportType = state.reportType.validated(touched = true),
            )
        }
    }

    // ─── 현재 위치 one-shot resolution ─────────────────────────────────────
    //
    // 흐름:
    //   1) "현재 위치로 설정" 버튼 → requestCurrentLocation()
    //   2) 권한 state 확인
    //      - Granted → 즉시 fetchAndApplyCurrentLocation() (last known + 필요시 active fix)
    //      - Denied  → RequestLocationPermission emit, pending 플래그 + lifecycle 타임아웃
    //      - Unavailable → 즉시 에러 + Snackbar 안내
    //   3) (Denied 경로) 사용자가 다이얼로그 응답 → Activity ON_RESUME → Route가
    //      RefreshLocationPermission dispatch → handleRefreshLocationPermission()이 새 state로 분기
    //   4) 권한이 Granted로 바뀌면 fetch로 진입. 여전히 Denied/Unavailable이면 적절한 에러 종료.
    //
    // 정리:
    //   - fetch는 active provider를 임시로 켜고 첫 fresh snapshot 받으면 즉시 stop (Map의
    //     continuous tracking과 간섭 최소화).
    //   - onCleared / 새 요청 시작 시 이전 job 취소.

    private fun requestCurrentLocation() {
        // 같은 요청 중복 클릭 방어: 이미 resolving 중이면 무시.
        if (mutableUiState.value.location.isResolvingCurrentLocation) return

        setResolvingCurrentLocation(true, clearError = true)

        locationPermissionManager.refreshPermissionState()
        when (locationPermissionManager.permissionState.value) {
            is LocationPermissionState.Granted -> startCurrentLocationFetch()
            LocationPermissionState.Denied -> {
                pendingCurrentLocationRequest = true
                startPermissionPendingTimeout()
                emitUiEvent(ReportUiEvent.RequestLocationPermission)
            }
            is LocationPermissionState.Unavailable -> {
                finishCurrentLocationWithUnavailable()
            }
        }
    }

    private fun handleRefreshLocationPermission() {
        // ON_RESUME에서 한 번씩 들어옴. pending 중인 요청만 처리.
        if (!pendingCurrentLocationRequest) return

        locationPermissionManager.refreshPermissionState()
        when (locationPermissionManager.permissionState.value) {
            is LocationPermissionState.Granted -> {
                pendingCurrentLocationRequest = false
                cancelPermissionPendingTimeout()
                startCurrentLocationFetch()
            }
            LocationPermissionState.Denied -> {
                // 사용자가 거부했거나 다이얼로그를 닫음. one-shot 흐름 종료.
                pendingCurrentLocationRequest = false
                cancelPermissionPendingTimeout()
                finishCurrentLocationWithError(ReportLocationError.PermissionDenied)
            }
            is LocationPermissionState.Unavailable -> {
                pendingCurrentLocationRequest = false
                cancelPermissionPendingTimeout()
                finishCurrentLocationWithUnavailable()
            }
        }
    }

    private fun startCurrentLocationFetch() {
        currentLocationJob?.cancel()
        currentLocationJob =
            viewModelScope.launch {
                val snapshot = fetchFreshCurrentLocation()
                if (snapshot != null) {
                    applyFetchedLocation(snapshot)
                } else {
                    finishCurrentLocationWithError(ReportLocationError.CurrentLocationUnavailable)
                }
            }
    }

    private suspend fun fetchFreshCurrentLocation(): LocationSnapshot? {
        // 1) Last known이 fresh면 즉시 사용 (active provider 호출 없이).
        currentLocationManager.refreshLatestLocation()
        currentLocationManager.latestLocation.value
            ?.takeIf { it.isFreshCurrentLocation() }
            ?.let { return it }

        // 2) Active provider 임시 시작 → 첫 fresh snapshot 또는 timeout.
        currentLocationManager.startLocationUpdates()
        return try {
            withTimeoutOrNull(CURRENT_LOCATION_FETCH_TIMEOUT_MS) {
                currentLocationManager.latestLocation
                    .filterNotNull()
                    .filter { it.isFreshCurrentLocation() }
                    .first()
            }
        } finally {
            currentLocationManager.stopLocationUpdates()
        }
    }

    private fun applyFetchedLocation(snapshot: LocationSnapshot) {
        val location =
            ReportLocation(
                latitude = snapshot.latitude,
                longitude = snapshot.longitude,
                address = null, // Task 2.3 reverse geocoding에서 채움. 그 전까지 좌표만.
            )
        selectLocation(location = location, source = ReportLocationSource.CurrentLocation)
        // selectLocation이 isResolvingCurrentLocation을 직접 false로 두지 않으니 명시적으로 클리어.
        setResolvingCurrentLocation(false)
    }

    private fun finishCurrentLocationWithError(error: ReportLocationError) {
        currentLocationJob?.cancel()
        currentLocationJob = null
        mutableUiState.update { state ->
            state.copy(
                location =
                    state.location.copy(
                        isResolvingCurrentLocation = false,
                        error = error,
                    ),
            )
        }
    }

    private fun finishCurrentLocationWithUnavailable() {
        finishCurrentLocationWithError(ReportLocationError.CurrentLocationUnavailable)
    }

    private fun setResolvingCurrentLocation(
        resolving: Boolean,
        clearError: Boolean = false,
    ) {
        mutableUiState.update { state ->
            state.copy(
                location =
                    state.location.copy(
                        isResolvingCurrentLocation = resolving,
                        error = if (clearError) null else state.location.error,
                    ),
            )
        }
    }

    private fun startPermissionPendingTimeout() {
        cancelPermissionPendingTimeout()
        permissionPendingTimeoutJob =
            viewModelScope.launch {
                kotlinx.coroutines.delay(PERMISSION_PENDING_TIMEOUT_MS)
                if (pendingCurrentLocationRequest) {
                    pendingCurrentLocationRequest = false
                    finishCurrentLocationWithError(ReportLocationError.PermissionDenied)
                }
            }
    }

    private fun cancelPermissionPendingTimeout() {
        permissionPendingTimeoutJob?.cancel()
        permissionPendingTimeoutJob = null
    }

    private fun selectLocation(
        location: ReportLocation,
        source: ReportLocationSource,
    ) {
        mutableUiState.update { state ->
            state.copy(
                screenState = ReportScreenState.Editing,
                location = state.location.withValue(location, source),
                outboxState = ReportOutboxState.NotSaved,
                submitState = ReportSubmitState.Idle,
            )
        }
        // Task 2.3 — 좌표만 들어온 경우 자동 reverse geocoding. 외부에서 address까지 채워 들어온
        // 경우(예: 향후 검색 결과 선택)는 그대로 존중.
        if (location.address.isNullOrBlank()) {
            triggerReverseGeocode(location)
        }
    }

    // ─── Reverse geocoding (Task 2.3) ─────────────────────────────────────
    // 지도 드래그·핀치 등으로 onCameraMoveEnd가 연달아 fire될 때 매번 API를 때리지 않도록
    // 짧은 debounce를 두고, 새 위치가 들어오면 이전 job을 cancel한다.
    private var reverseGeocodeJob: Job? = null

    private fun triggerReverseGeocode(location: ReportLocation) {
        reverseGeocodeJob?.cancel()
        reverseGeocodeJob =
            viewModelScope.launch {
                delay(REVERSE_GEOCODE_DEBOUNCE_MS)
                val resolved =
                    runCatching {
                        addressResolver.resolveAddress(
                            GeoCoordinate(
                                latitude = location.latitude,
                                longitude = location.longitude,
                            ),
                        )
                    }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@launch

                mutableUiState.update { state ->
                    val currentLocation = state.location.value ?: return@update state
                    // 결과 도착 사이에 사용자가 새 좌표를 선택한 경우 stale 결과는 무시한다.
                    if (currentLocation.latitude != location.latitude ||
                        currentLocation.longitude != location.longitude
                    ) {
                        return@update state
                    }
                    // location.address만 갱신해 "선택된 위치" 카드에 표시한다.
                    // addressText(OutlinedTextField)는 사용자 직접 입력 전용이라 건드리지 않는다.
                    state.copy(
                        location =
                            state.location.copy(
                                value = currentLocation.copy(address = resolved),
                            ),
                    )
                }
            }
    }

    private fun updateAddressText(address: String) {
        mutableUiState.update { state ->
            val currentLocation = state.location.value
            val updatedLocation =
                currentLocation?.copy(address = address.trim().ifEmpty { null })

            state.copy(
                screenState = ReportScreenState.Editing,
                location = state.location.withAddress(address, updatedLocation),
                outboxState = ReportOutboxState.NotSaved,
                submitState = ReportSubmitState.Idle,
            )
        }
    }

    private fun touchLocation() {
        mutableUiState.update { state ->
            state.copy(location = state.location.validated(touched = true))
        }
    }

    /**
     * "+" 사진 추가 버튼 클릭 처리.
     *
     * Task 3.2 (S14P31E102-682)부터는 mock URI를 생성하지 않고 Route에 picker 열기 요청을 emit한다.
     * Route가 시스템 Photo Picker를 띄우고, 사용자 선택 결과는 `PhotoSelected` 액션으로 다시 들어온다.
     * 첨부 cap 초과 시에는 picker 호출 자체를 건너뛴다.
     */
    private fun handlePhotoAddClicked() {
        val currentCount = mutableUiState.value.photo.values.size
        if (currentCount >= ReportFormLimits.PHOTO_MAX_COUNT) {
            return
        }
        emitUiEvent(ReportUiEvent.OpenPhotoPicker)
    }

    private fun selectPhoto(photo: ReportPhoto) {
        mutableUiState.update { state ->
            state.copy(
                screenState = ReportScreenState.Editing,
                photo = state.photo.withAdded(photo),
                outboxState = ReportOutboxState.NotSaved,
                submitState = ReportSubmitState.Idle,
            )
        }
    }

    private fun removePhotoAt(index: Int) {
        mutableUiState.update { state ->
            state.copy(
                screenState = ReportScreenState.Editing,
                photo = state.photo.withRemovedAt(index),
                outboxState = ReportOutboxState.NotSaved,
                submitState = ReportSubmitState.Idle,
            )
        }
    }

    private fun touchPhoto() {
        mutableUiState.update { state ->
            state.copy(photo = state.photo.validated(touched = true))
        }
    }

    private fun updateDescription(description: String) {
        mutableUiState.update { state ->
            state.copy(
                screenState = ReportScreenState.Editing,
                description = state.description.withValue(description),
                outboxState = ReportOutboxState.NotSaved,
                submitState = ReportSubmitState.Idle,
            )
        }
    }

    private fun touchDescription() {
        mutableUiState.update { state ->
            state.copy(description = state.description.validated(touched = true))
        }
    }

    private fun submitReport() {
        val validatedState = mutableUiState.value.validatedForSubmit()
        if (!validatedState.isSubmitEnabled) {
            markSubmitValidationFailed(validatedState)
            return
        }

        mutableUiState.value =
            validatedState.copy(
                screenState = ReportScreenState.Submitting,
                submitState = ReportSubmitState.Submitting,
                outboxState =
                    when (val current = validatedState.outboxState) {
                        is ReportOutboxState.Saved -> current
                        else -> ReportOutboxState.Saving
                    },
            )

        viewModelScope.launch {
            val savedOutbox =
                when (val current = validatedState.outboxState) {
                    is ReportOutboxState.Saved -> {
                        validatedState.toOutboxData().copy(outboxId = current.outboxId)
                    }
                    else -> {
                        runCatching { reportRepository.saveOutbox(validatedState.toOutboxData()) }
                            .getOrElse {
                                handleLocalSaveFailure(validatedState)
                                return@launch
                            }
                    }
                }

            val submitResult =
                runCatching { reportRepository.submitOutboxToServer(savedOutbox.outboxId) }
                    .getOrElse {
                        ReportSubmitResult.Failure(
                            outboxId = savedOutbox.outboxId,
                            reason = ReportSubmitFailureReason.Unknown,
                        )
                    }

            when (submitResult) {
                is ReportSubmitResult.Success ->
                    handleServerSubmitSuccess(
                        validatedState = validatedState,
                        outboxId = savedOutbox.outboxId,
                        serverReportId = submitResult.serverReportId,
                    )
                is ReportSubmitResult.Skipped ->
                    handleServerSubmitSkipped(
                        validatedState = validatedState,
                        outboxId = savedOutbox.outboxId,
                    )
                is ReportSubmitResult.Failure ->
                    handleServerSubmitFailure(
                        validatedState = validatedState,
                        outboxId = savedOutbox.outboxId,
                        reason = submitResult.reason,
                    )
            }
        }
    }

    private fun handleLocalSaveFailure(validatedState: ReportUiState) {
        mutableUiState.value =
            validatedState.copy(
                screenState =
                    ReportScreenState.Failure(reason = ReportFailureReason.LocalSaveFailed),
                submitState =
                    ReportSubmitState.Failed(reason = ReportFailureReason.LocalSaveFailed),
                outboxState =
                    ReportOutboxState.Failed(reason = ReportFailureReason.LocalSaveFailed),
            )
    }

    private fun handleServerSubmitSuccess(
        validatedState: ReportUiState,
        outboxId: String,
        serverReportId: Long,
    ) {
        mutableUiState.value =
            validatedState.copy(
                screenState = ReportScreenState.Completed,
                currentStep = ReportStep.Complete,
                outboxState = ReportOutboxState.Saved(outboxId = outboxId),
                submitState = ReportSubmitState.Success(reportId = serverReportId),
                submittedAtMillis = System.currentTimeMillis(),
            )
        if (validatedState.entryPoint == ReportEntryPoint.NavigationGuidance) {
            emitUiEvent(ReportUiEvent.ReturnToNavigationWithSubmittedReport(serverReportId))
        }
        emitUiEvent(ReportUiEvent.AnnounceForAccessibility("제보가 서버에 등록되었습니다."))
    }

    private fun handleServerSubmitSkipped(
        validatedState: ReportUiState,
        outboxId: String,
    ) {
        mutableUiState.value =
            validatedState.copy(
                screenState = ReportScreenState.Completed,
                currentStep = ReportStep.Complete,
                outboxState = ReportOutboxState.Saved(outboxId = outboxId),
                submitState = ReportSubmitState.Success(reportId = null),
                submittedAtMillis = System.currentTimeMillis(),
            )
        emitUiEvent(ReportUiEvent.AnnounceForAccessibility("제보가 로컬 outbox에 저장되었습니다."))
    }

    private fun handleServerSubmitFailure(
        validatedState: ReportUiState,
        outboxId: String,
        reason: ReportSubmitFailureReason,
    ) {
        val mappedReason = reason.toFailureReason()
        mutableUiState.value =
            validatedState.copy(
                screenState = ReportScreenState.Failure(reason = mappedReason),
                outboxState = ReportOutboxState.Saved(outboxId = outboxId),
                submitState = ReportSubmitState.Failed(reason = mappedReason),
            )
    }

    private fun markSubmitValidationFailed(validatedState: ReportUiState) {
        mutableUiState.value =
            validatedState.copy(
                screenState = ReportScreenState.Editing,
                submitState = ReportSubmitState.Idle,
            )
        emitUiEvent(ReportUiEvent.ScrollToFirstError)
    }

    private fun resetForm(
        entryPoint: ReportEntryPoint = mutableUiState.value.entryPoint,
        currentStep: ReportStep = ReportStep.Home,
    ) {
        val currentState = mutableUiState.value
        pendingCurrentLocationRequest = false
        currentLocationJob?.cancel()
        currentLocationJob = null
        cancelPermissionPendingTimeout()
        reverseGeocodeJob?.cancel()
        reverseGeocodeJob = null
        mutableUiState.value =
            ReportUiState(
                currentStep = currentStep,
                entryPoint = entryPoint,
                processingCounts = currentState.processingCounts,
                recentReports = currentState.recentReports,
                isOnline = currentState.isOnline,
            )
    }

    private fun emitUiEvent(event: ReportUiEvent) {
        viewModelScope.launch {
            mutableUiEvent.emit(event)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 화면 이탈 시 진행 중인 위치 fetch, 타임아웃, active provider 모두 정리.
        currentLocationJob?.cancel()
        currentLocationJob = null
        cancelPermissionPendingTimeout()
        pendingCurrentLocationRequest = false
        // Map 등 다른 도메인이 다시 startLocationUpdates를 호출하면 재개됨. 일시 stop OK.
        currentLocationManager.stopLocationUpdates()
    }

    companion object {
        // Active provider로 첫 fresh fix를 받는 최대 대기 시간. 실내·신호 약함 케이스에서
        // 사용자를 무한히 기다리지 않게 막는다. 10초는 Map 화면 동작 감각과 일관.
        private const val CURRENT_LOCATION_FETCH_TIMEOUT_MS = 10_000L
        // 권한 다이얼로그가 응답 없이 머무르는 비정상 케이스 fallback. ON_RESUME이 들어오지
        // 않는 환경에서도 일정 시간 후 흐름을 종료한다.
        private const val PERMISSION_PENDING_TIMEOUT_MS = 20_000L
        // 지도 드래그·핀치 후 onCameraMoveEnd가 짧은 간격으로 여러 번 fire될 때 API 과호출을
        // 방지하기 위한 debounce. 사용자가 카메라를 멈춘 직후의 좌표만 lookup하도록 한다.
        private const val REVERSE_GEOCODE_DEBOUNCE_MS = 300L
        private const val REPORT_HOME_RECENT_LIMIT = 3

        fun provideFactory(
            reportRepository: ReportRepository,
            currentLocationManager: CurrentLocationManager,
            locationPermissionManager: LocationPermissionManager,
            addressResolver: CurrentLocationAddressResolver = NoOpCurrentLocationAddressResolver,
            networkMonitor: NetworkMonitor = AlwaysOnlineNetworkMonitor,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ReportViewModel::class.java)) {
                        return ReportViewModel(
                            reportRepository = reportRepository,
                            currentLocationManager = currentLocationManager,
                            locationPermissionManager = locationPermissionManager,
                            addressResolver = addressResolver,
                            networkMonitor = networkMonitor,
                        ) as T
                    }

                    error("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}

private fun ReportStep.previousOrNull(entryPoint: ReportEntryPoint): ReportStep? =
    when (this) {
        ReportStep.Home -> null
        ReportStep.TypeSelection ->
            if (entryPoint == ReportEntryPoint.NavigationGuidance) {
                null
            } else {
                ReportStep.Home
            }
        ReportStep.LocationConfirm -> ReportStep.TypeSelection
        ReportStep.DetailInput -> ReportStep.LocationConfirm
        ReportStep.Complete -> null
    }

private fun ReportTypeInput.withValue(type: ReportType): ReportTypeInput =
    copy(
        value = type,
        isTouched = true,
        isDirty = true,
        error = validateReportType(type),
    )

private fun ReportUiState.toOutboxData(): ReportOutboxData {
    val now = System.currentTimeMillis()
    val reportTypeValue = requireNotNull(reportType.value)
    val locationValue = requireNotNull(location.value)
    val firstPhoto = photo.firstOrNull

    return ReportOutboxData(
        outboxId = "",
        reportCategory = reportTypeValue.apiValue,
        description = description.trimmedValue,
        // 옵션 4와 동일하게 두 필드 분리. 서버 submit DTO에는 둘 다 안 가고 로컬 outbox에만 보존.
        address = locationValue.address,
        addressDetail = location.addressText.trim().ifEmpty { null },
        latitude = locationValue.latitude,
        longitude = locationValue.longitude,
        photoUri = firstPhoto?.localUri,
        photoMimeType = firstPhoto?.mimeType,
        photoSizeBytes = firstPhoto?.sizeBytes,
        // Task 5.5 — 첨부 사진 전체를 outbox에 보존. 제출 시점에 presigned 업로드 대상으로 사용.
        photos = photo.values.map { it.toOutboxPhotoData() },
        createdAtMillis = now,
        updatedAtMillis = now,
    )
}

private fun ReportPhoto.toOutboxPhotoData(): ReportOutboxPhotoData =
    ReportOutboxPhotoData(
        localUri = localUri,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
    )

private fun ReportTypeInput.validated(touched: Boolean = isTouched): ReportTypeInput =
    copy(
        isTouched = touched,
        error = validateReportType(value),
    )

private fun ReportLocationInput.withValue(
    location: ReportLocation,
    source: ReportLocationSource,
): ReportLocationInput =
    copy(
        value = location,
        // addressText는 사용자가 OutlinedTextField에 직접 입력하는 "추가 메모"용 필드라
        // location.address(자동 RGC 결과)로 덮어쓰지 않고 이전 값을 그대로 보존한다.
        // ReportLocationBottomCard는 이미 location.address 우선 표시이므로 자동 주소는 그쪽으로 노출됨.
        source = source,
        isTouched = true,
        isDirty = true,
        isResolvingCurrentLocation = false,
        error = validateLocation(location, addressText),
    )

private fun ReportLocationInput.withAddress(
    address: String,
    updatedLocation: ReportLocation?,
): ReportLocationInput {
    val nextSource =
        if (updatedLocation == null) {
            source
        } else {
            ReportLocationSource.AddressText
        }
    val shouldValidate =
        isTouched ||
            error != null ||
            address.length > ReportFormLimits.ADDRESS_MAX_LENGTH

    return copy(
        value = updatedLocation,
        addressText = address,
        source = nextSource,
        isDirty = true,
        error = if (shouldValidate) validateLocation(updatedLocation, address) else null,
    )
}

private fun ReportLocationInput.validated(touched: Boolean = isTouched): ReportLocationInput =
    copy(
        isTouched = touched,
        error = validateLocation(value, addressText),
    )

private fun ReportPhotoInput.withAdded(photo: ReportPhoto): ReportPhotoInput {
    // 같은 사진(localUri 기준)을 두 번 첨부하면 무시한다.
    // Photo Picker가 같은 사진에 대해 동일 content URI를 돌려주므로 문자열 비교로 충분.
    if (values.any { it.localUri == photo.localUri }) return this
    val nextValues =
        if (values.size >= ReportFormLimits.PHOTO_MAX_COUNT) {
            values
        } else {
            values + photo
        }
    return copy(
        values = nextValues,
        isTouched = true,
        isDirty = true,
        error = validatePhotos(nextValues),
    )
}

private fun ReportPhotoInput.withRemovedAt(index: Int): ReportPhotoInput {
    val nextValues =
        if (index in values.indices) {
            values.toMutableList().also { it.removeAt(index) }
        } else {
            values
        }
    return copy(
        values = nextValues,
        isTouched = true,
        isDirty = true,
        error = validatePhotos(nextValues),
    )
}

private fun ReportPhotoInput.validated(touched: Boolean = isTouched): ReportPhotoInput =
    copy(
        isTouched = touched,
        error = validatePhotos(values),
    )

private fun ReportDescriptionInput.withValue(description: String): ReportDescriptionInput =
    description
        .take(ReportFormLimits.DESCRIPTION_MAX_LENGTH)
        .let { limitedDescription ->
            copy(
                value = limitedDescription,
                isDirty = true,
                error = validateDescription(limitedDescription),
            )
        }

private fun List<ReportHistoryData>.toProcessingCounts(): ReportProcessingCounts =
    ReportProcessingCounts(
        pending = count { it.processingStatus == ReportProcessingStatus.PENDING || it.processingStatus == null },
        approved = count { it.processingStatus == ReportProcessingStatus.APPROVED },
    )

private fun ReportHistoryData.toReportRecentUiModel(): ReportRecentUiModel =
    ReportRecentUiModel(
        historyId = historyId,
        title = reportCategory.toReportTitle(),
        address = address?.takeIf { it.isNotBlank() } ?: "주소 정보 없음",
        submittedAtText = updatedAtMillis.formatReportHomeDate(),
        statusLabel =
            when (processingStatus) {
                ReportProcessingStatus.APPROVED -> "반영 완료"
                ReportProcessingStatus.REJECTED -> "처리 종료"
                ReportProcessingStatus.PENDING, null -> "접수됨"
            },
        isApproved = processingStatus == ReportProcessingStatus.APPROVED,
    )

private fun String.toReportTitle(): String =
    when (this) {
        "STAIRS_STEP", "STAIRS" -> "계단·단차 있음"
        "BRAILLE_BLOCK", "TACTILE_BLOCK", "GUIDANCE_BLOCK" -> "점자블록 문제"
        "SIDEWALK_MISSING" -> "인도 없음"
        "RAMP", "SLOPE" -> "경사로 문제"
        "SIDEWALK_WIDTH" -> "인도폭 문제"
        "OTHER_OBSTACLE", "CONSTRUCTION", "ELEVATOR", "FACILITY_DAMAGE" -> "기타 장애물"
        else -> "제보 내역"
    }

private fun Long.formatReportHomeDate(): String =
    SimpleDateFormat("yyyy.MM.dd", Locale.KOREAN).format(Date(this))

private fun ReportDescriptionInput.validated(
    touched: Boolean = isTouched,
): ReportDescriptionInput =
    copy(
        isTouched = touched,
        error = validateDescription(value),
    )

private fun ReportUiState.validatedForSubmit(): ReportUiState =
    copy(
        screenState = ReportScreenState.Editing,
        reportType = reportType.validated(touched = true),
        location = location.validated(touched = true),
        photo = photo.validated(touched = true),
        description = description.validated(touched = true),
        submitState = ReportSubmitState.Idle,
    )

private fun validateReportType(type: ReportType?): ReportTypeError? =
    if (type == null) ReportTypeError.Required else null

private fun validateLocation(
    location: ReportLocation?,
    addressText: String,
): ReportLocationError? {
    if (location == null) return ReportLocationError.Required
    if (!location.hasValidCoordinate()) return ReportLocationError.InvalidCoordinate
    if (addressText.length > ReportFormLimits.ADDRESS_MAX_LENGTH) {
        return ReportLocationError.AddressTooLong
    }

    return null
}

private fun ReportLocation.hasValidCoordinate(): Boolean =
    latitude in -90.0..90.0 && longitude in -180.0..180.0

private fun validatePhotos(photos: List<ReportPhoto>): ReportPhotoError? {
    if (photos.isEmpty()) return null
    if (photos.size > ReportFormLimits.PHOTO_MAX_COUNT) return ReportPhotoError.TooMany
    photos.forEach { photo ->
        if (photo.localUri.isBlank()) return ReportPhotoError.Unreadable
        if (photo.mimeType != null && !photo.mimeType.startsWith("image/")) {
            return ReportPhotoError.UnsupportedFormat
        }
        if (photo.sizeBytes != null && photo.sizeBytes > ReportFormLimits.PHOTO_MAX_BYTES) {
            return ReportPhotoError.TooLarge
        }
    }
    return null
}

private fun validateDescription(description: String): ReportDescriptionError? =
    if (description.length > ReportFormLimits.DESCRIPTION_MAX_LENGTH) {
        ReportDescriptionError.TooLong
    } else {
        null
    }

private fun ReportSubmitFailureReason.toFailureReason(): ReportFailureReason =
    when (this) {
        ReportSubmitFailureReason.Unauthorized -> ReportFailureReason.Unauthorized
        ReportSubmitFailureReason.InvalidInput -> ReportFailureReason.InvalidInput
        ReportSubmitFailureReason.Network -> ReportFailureReason.NetworkUnavailable
        ReportSubmitFailureReason.Unknown -> ReportFailureReason.ServerSubmitFailed
    }
