package com.ssafy.e102.eumgil.feature.arrival

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.RouteBookmarkDraft
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.core.model.RouteSearchData
import com.ssafy.e102.eumgil.core.model.RouteSearchQuery
import com.ssafy.e102.eumgil.data.remote.datasource.FavoriteRoutesApiException
import com.ssafy.e102.eumgil.data.repository.RouteBookmarkRepository
import com.ssafy.e102.eumgil.data.repository.RouteBookmarkSaveException
import com.ssafy.e102.eumgil.data.repository.RouteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ArrivalViewModel(
    private val routeBookmarkRepository: RouteBookmarkRepository,
    private val routeRepository: RouteRepository = NoOpArrivalRouteRepository,
    private val currentRouteBookmarkDraft: RouteBookmarkDraft? = null,
    private val currentRatingSessionId: String? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ArrivalUiState())
    val uiState: StateFlow<ArrivalUiState> = mutableUiState.asStateFlow()

    private val mutableUiEvent = MutableSharedFlow<ArrivalUiEvent>()
    val uiEvent: SharedFlow<ArrivalUiEvent> = mutableUiEvent.asSharedFlow()

    init {
        val hasRatingSession = !currentRatingSessionId.isNullOrBlank()
        mutableUiState.update { state ->
            state.copy(
                isEvaluationSheetVisible = hasRatingSession && currentRouteBookmarkDraft?.canSaveToServer != true,
                hasRatingSession = hasRatingSession,
                routeSaveDraft = currentRouteBookmarkDraft?.toUiState(),
                isRouteSaveUpdating = currentRouteBookmarkDraft?.canSaveToServer == true,
            )
        }
        currentRouteBookmarkDraft?.let(::syncInitialRouteSaveState)
    }

    fun onAction(action: ArrivalUiAction) {
        when (action) {
            ArrivalUiAction.HomeClicked -> emitUiEvent(ArrivalUiEvent.NavigateToMap)
            ArrivalUiAction.ExploreNewRouteClicked -> emitUiEvent(ArrivalUiEvent.NavigateToSearch)
            is ArrivalUiAction.RatingSelected -> updateSelectedRating(action.rating)
            ArrivalUiAction.SaveRouteClicked -> toggleRouteBookmark()
            ArrivalUiAction.SubmitEvaluationClicked -> submitEvaluation()
            ArrivalUiAction.EvaluationSheetDismissed ->
                mutableUiState.update { state ->
                    state.copy(isEvaluationSheetVisible = false)
                }
        }
    }

    private fun syncInitialRouteSaveState(draft: RouteBookmarkDraft) {
        if (!draft.canSaveToServer) {
            mutableUiState.update { state ->
                state.copy(
                    isEvaluationSheetVisible = state.hasRatingSession,
                    isRouteSaveUpdating = false,
                )
            }
            return
        }
        viewModelScope.launch {
            runCatching {
                val bookmarkId = resolveRouteBookmarkId(draft)
                val isSaved = bookmarkId != null || routeBookmarkRepository.isBookmarked(draft)
                isSaved to bookmarkId
            }.onSuccess { (isSaved, bookmarkId) ->
                mutableUiState.update { state ->
                    state.copy(
                        isEvaluationSheetVisible = state.hasRatingSession && !isSaved,
                        isRouteSaveSelected = isSaved,
                        routeSaveBookmarkId = bookmarkId,
                        isRouteSaveUpdating = false,
                    )
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                mutableUiState.update { state ->
                    state.copy(
                        isEvaluationSheetVisible = state.hasRatingSession,
                        isRouteSaveUpdating = false,
                    )
                }
            }
        }
    }

    private fun toggleRouteBookmark() {
        when {
            uiState.value.isRouteSaveEnabled.not() -> Unit
            uiState.value.isRouteSaveSelected -> deleteRouteBookmark()
            else -> saveRouteBookmark()
        }
    }

    private fun saveRouteBookmark() {
        val draft = currentRouteBookmarkDraft ?: return
        val saveRequest = draft.toSaveRequest()
        if (!draft.canSaveToServer) {
            logArrivalRouteBookmarkTraceWarning(
                "blocked routeId=${draft.routeId.orEmpty()} reason=route_id_missing",
            )
            emitUiEvent(ArrivalUiEvent.ShowToast(ARRIVAL_ROUTE_BOOKMARK_UNAVAILABLE_MESSAGE))
            return
        }
        if (!uiState.value.isRouteSaveEnabled) {
            logArrivalRouteBookmarkTraceWarning(
                "ignored routeId=${saveRequest.routeId.orEmpty()} reason=save_disabled isUpdating=${uiState.value.isRouteSaveUpdating}",
            )
            return
        }

        mutableUiState.update { state ->
            state.copy(isRouteSaveUpdating = true)
        }
        logArrivalRouteBookmarkTraceInfo(
            "requested routeId=${saveRequest.routeId.orEmpty()} routeOption=${saveRequest.routeOption.name}",
        )

        viewModelScope.launch {
            runCatching {
                routeBookmarkRepository.saveRouteBookmark(saveRequest)
            }.onSuccess { bookmark ->
                logArrivalRouteBookmarkTraceInfo(
                    "success routeId=${saveRequest.routeId.orEmpty()} bookmarkId=${bookmark.bookmarkId}",
                )
                mutableUiState.update { state ->
                    state.copy(
                        isRouteSaveSelected = true,
                        routeSaveBookmarkId = bookmark.bookmarkId,
                        isRouteSaveUpdating = false,
                    )
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                mutableUiState.update { state ->
                    state.copy(isRouteSaveUpdating = false)
                }
                logArrivalRouteBookmarkTraceFailure(
                    routeId = saveRequest.routeId,
                    throwable = throwable,
                )
                emitUiEvent(ArrivalUiEvent.ShowToast(ARRIVAL_ROUTE_BOOKMARK_SAVE_FAILURE_MESSAGE))
            }
        }
    }

    private fun deleteRouteBookmark() {
        val draft = currentRouteBookmarkDraft ?: return
        if (!uiState.value.isRouteSaveEnabled) return

        mutableUiState.update { state ->
            state.copy(isRouteSaveUpdating = true)
        }

        viewModelScope.launch {
            val bookmarkId = uiState.value.routeSaveBookmarkId ?: resolveRouteBookmarkId(draft)
            if (bookmarkId.isNullOrBlank()) {
                mutableUiState.update { state ->
                    state.copy(isRouteSaveUpdating = false)
                }
                return@launch
            }

            runCatching {
                routeBookmarkRepository.deleteRouteBookmark(bookmarkId)
            }.onSuccess {
                mutableUiState.update { state ->
                    state.copy(
                        isRouteSaveSelected = false,
                        routeSaveBookmarkId = null,
                        isRouteSaveUpdating = false,
                    )
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                mutableUiState.update { state ->
                    state.copy(isRouteSaveUpdating = false)
                }
            }
        }
    }

    private fun submitEvaluation() {
        val sessionId = currentRatingSessionId?.takeIf(String::isNotBlank) ?: return
        val selectedRating = uiState.value.selectedRating
        if (!uiState.value.isEvaluationSubmitEnabled) return

        mutableUiState.update { state ->
            state.copy(isEvaluationSubmitting = true)
        }

        viewModelScope.launch {
            runCatching {
                routeRepository.rateRoute(
                    sessionId = sessionId,
                    score = selectedRating,
                )
            }.onSuccess {
                mutableUiState.update { state ->
                    state.copy(
                        isEvaluationSubmitting = false,
                        isEvaluationSheetVisible = false,
                    )
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                mutableUiState.update { state ->
                    state.copy(isEvaluationSubmitting = false)
                }
            }
        }
    }

    private fun emitUiEvent(event: ArrivalUiEvent) {
        viewModelScope.launch {
            mutableUiEvent.emit(event)
        }
    }

    private fun updateSelectedRating(rating: Int) {
        val resolvedRating = rating.coerceIn(1, 5)
        mutableUiState.update { state ->
            state.copy(
                selectedRating = resolvedRating,
                selectedRatingLabel = resolvedRating.toArrivalEvaluationLabel(),
            )
        }
    }

    private suspend fun resolveRouteBookmarkId(draft: RouteBookmarkDraft): String? =
        routeBookmarkRepository
            .observeRouteBookmarks()
            .first()
            .firstOrNull { bookmark -> bookmark.matchesDraft(draft) }
            ?.bookmarkId

    companion object {
        fun provideFactory(
            routeBookmarkRepository: RouteBookmarkRepository,
            routeRepository: RouteRepository,
            currentRouteBookmarkDraft: RouteBookmarkDraft?,
            currentRatingSessionId: String?,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ArrivalViewModel(
                        routeBookmarkRepository = routeBookmarkRepository,
                        routeRepository = routeRepository,
                        currentRouteBookmarkDraft = currentRouteBookmarkDraft,
                        currentRatingSessionId = currentRatingSessionId,
                    ) as T
            }
    }
}

private fun com.ssafy.e102.eumgil.core.model.RouteBookmark.matchesDraft(draft: RouteBookmarkDraft): Boolean =
    startPoint == draft.startPoint &&
        endPoint == draft.endPoint &&
        routeOption == draft.routeOption

private fun Int.toArrivalEvaluationLabel(): ArrivalEvaluationLabel =
    when (this) {
        1 -> ArrivalEvaluationLabel.VeryDissatisfied
        2 -> ArrivalEvaluationLabel.Dissatisfied
        3 -> ArrivalEvaluationLabel.Neutral
        4 -> ArrivalEvaluationLabel.Satisfied
        5 -> ArrivalEvaluationLabel.VerySatisfied
        else -> ArrivalEvaluationLabel.Idle
    }

private fun RouteBookmarkDraft.toUiState(): ArrivalRouteSaveDraftUiState =
    ArrivalRouteSaveDraftUiState(
        defaultRouteName = defaultRouteName,
        startLabel = startLabel.ifBlank { "출발지" },
        endLabel = endLabel.ifBlank { "목적지" },
        routeOptionLabel =
            when (routeOption) {
                RouteOption.SAFE -> "안전한 길"
                RouteOption.SHORTEST -> "최단거리"
                RouteOption.RECOMMENDED -> "추천 경로"
                RouteOption.MIN_TRANSFER -> "최소 환승"
                RouteOption.MIN_WALK -> "최소 도보"
            },
        distanceMeters = distanceMeters,
        durationMinutes = durationMinutes,
        canSaveToServer = canSaveToServer,
    )

private const val ARRIVAL_ROUTE_BOOKMARK_UNAVAILABLE_MESSAGE = "안내 종료 경로 ID가 없어 경로 북마크를 저장할 수 없습니다."
private const val ARRIVAL_ROUTE_BOOKMARK_SAVE_FAILURE_MESSAGE = "경로 북마크를 저장하지 못했습니다. 다시 시도해 주세요."

private const val ARRIVAL_ROUTE_BOOKMARK_LOG_TAG = "ArrivalRouteBookmark"

private fun logArrivalRouteBookmarkTraceInfo(message: String) {
    runCatching {
        Log.i(ARRIVAL_ROUTE_BOOKMARK_LOG_TAG, "RouteBookmarkSaveTrace[ArrivalViewModel] $message")
    }
}

private fun logArrivalRouteBookmarkTraceWarning(
    message: String,
    throwable: Throwable? = null,
) {
    runCatching {
        if (throwable == null) {
            Log.w(ARRIVAL_ROUTE_BOOKMARK_LOG_TAG, "RouteBookmarkSaveTrace[ArrivalViewModel] $message")
        } else {
            Log.w(ARRIVAL_ROUTE_BOOKMARK_LOG_TAG, "RouteBookmarkSaveTrace[ArrivalViewModel] $message", throwable)
        }
    }
}

private fun logArrivalRouteBookmarkTraceError(
    message: String,
    throwable: Throwable,
) {
    runCatching {
        Log.e(ARRIVAL_ROUTE_BOOKMARK_LOG_TAG, "RouteBookmarkSaveTrace[ArrivalViewModel] $message", throwable)
    }
}

private fun logArrivalRouteBookmarkTraceFailure(
    routeId: String?,
    throwable: Throwable,
) {
    val routeIdLabel = routeId.orEmpty()
    when (throwable) {
        is FavoriteRoutesApiException ->
            logArrivalRouteBookmarkTraceError(
                message =
                    "failure routeId=$routeIdLabel exception=${throwable.javaClass.simpleName} " +
                        "httpStatus=${throwable.httpStatusCode} status=${throwable.status} " +
                        "message=${throwable.message.orEmpty()}",
                throwable = throwable,
            )

        is RouteBookmarkSaveException ->
            logArrivalRouteBookmarkTraceWarning(
                message =
                    "failure routeId=$routeIdLabel exception=${throwable.javaClass.simpleName} " +
                        "message=${throwable.message.orEmpty()}",
                throwable = throwable,
            )

        else ->
            logArrivalRouteBookmarkTraceError(
                message =
                    "failure routeId=$routeIdLabel exception=${throwable.javaClass.simpleName} " +
                        "message=${throwable.message.orEmpty()}",
                throwable = throwable,
            )
    }
}

private object NoOpArrivalRouteRepository : RouteRepository {
    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("ArrivalViewModel does not load route search data.")

    override suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        error("ArrivalViewModel does not load transit search data.")

    override suspend fun selectRoute(
        routeId: String,
        searchId: String,
    ) = error("ArrivalViewModel does not select routes.")

    override suspend fun refreshTransit(
        routeId: String,
        legSequence: Int,
    ) = error("ArrivalViewModel does not refresh transit.")

    override suspend fun reroute(
        routeId: String,
        currentPoint: GeoCoordinate,
    ) = error("ArrivalViewModel does not reroute.")

    override suspend fun endRoute(routeId: String) =
        error("ArrivalViewModel does not end routes.")

    override suspend fun rateRoute(
        sessionId: String,
        score: Int,
    ) = error("ArrivalViewModel requires a RouteRepository for rating.")
}
