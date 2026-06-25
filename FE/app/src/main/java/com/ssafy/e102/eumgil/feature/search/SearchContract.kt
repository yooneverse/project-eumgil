package com.ssafy.e102.eumgil.feature.search

import com.ssafy.e102.eumgil.core.model.RecentSearch
import com.ssafy.e102.eumgil.core.model.SearchResult
import com.ssafy.e102.eumgil.core.model.SearchSortOption
import com.ssafy.e102.eumgil.data.repository.RouteEditingTarget

enum class SearchSelectionMode {
    PREVIEW_ON_MAP,
    APPLY_TO_ROUTE,
}

data class SearchUiState(
    val query: String = "",
    val hasEditedQuery: Boolean = false,
    val editingTarget: RouteEditingTarget = RouteEditingTarget.DESTINATION,
    val selectionMode: SearchSelectionMode = SearchSelectionMode.PREVIEW_ON_MAP,
    val recentSearches: List<RecentSearch> = emptyList(),
    val sortOption: SearchSortOption = SearchSortOption.RELEVANCE,
    val resultState: SearchResultUiState = SearchResultUiState.Initial,
    val voiceInputState: SearchVoiceInputUiState = SearchVoiceInputUiState(),
    val currentLocationQuickActionState: SearchCurrentLocationQuickActionUiState =
        SearchCurrentLocationQuickActionUiState(),
)

data class SearchCurrentLocationQuickActionUiState(
    val status: SearchCurrentLocationQuickActionStatus = SearchCurrentLocationQuickActionStatus.Idle,
)

enum class SearchCurrentLocationQuickActionStatus {
    Idle,
    Resolving,
    Applied,
    PermissionDenied,
    LocationUnavailable,
    LocationAccessUnavailable,
}

data class SearchVoiceInputUiState(
    val isActive: Boolean = false,
    val transcript: String = "",
    val status: SearchVoiceInputStatus = SearchVoiceInputStatus.Idle,
    val guidance: SearchVoiceInputGuidance = SearchVoiceInputGuidance.None,
)

enum class SearchVoiceInputStatus {
    Idle,
    Listening,
    Recognized,
}

enum class SearchVoiceInputGuidance {
    None,
    RetryRequired,
}

sealed interface SearchUiAction {
    data object BackClicked : SearchUiAction

    data class EditingTargetConfigured(
        val editingTarget: RouteEditingTarget,
        val selectionMode: SearchSelectionMode = SearchSelectionMode.PREVIEW_ON_MAP,
    ) : SearchUiAction

    data class EntryRouteEntered(
        val preserveState: Boolean,
    ) : SearchUiAction

    data object VoiceInputClicked : SearchUiAction

    data object CurrentLocationClicked : SearchUiAction

    data object MapPickerClicked : SearchUiAction

    data object RefreshLocationPermission : SearchUiAction

    data object VoiceRouteEntered : SearchUiAction

    data object VoiceCaptureButtonClicked : SearchUiAction

    data object VoiceCaptureEmpty : SearchUiAction

    data object VoiceInputDismissed : SearchUiAction

    data class VoiceTranscriptReceived(
        val transcript: String,
        val searchQuery: String? = null,
    ) : SearchUiAction

    data class ResultsRouteEntered(
        val query: String,
        val editingTarget: RouteEditingTarget = RouteEditingTarget.DESTINATION,
        val selectionMode: SearchSelectionMode = SearchSelectionMode.PREVIEW_ON_MAP,
    ) : SearchUiAction

    data class QueryChanged(
        val query: String,
    ) : SearchUiAction

    data object ClearQueryClicked : SearchUiAction

    data object SearchSubmitted : SearchUiAction

    data class SortOptionSelected(
        val sortOption: SearchSortOption,
    ) : SearchUiAction

    data class RecentSearchClicked(
        val keyword: String,
    ) : SearchUiAction

    data class RecentSearchDeleteClicked(
        val keyword: String,
    ) : SearchUiAction

    data object RecentSearchClearAllClicked : SearchUiAction

    data class SearchResultClicked(
        val result: SearchResult,
    ) : SearchUiAction

    data class SearchResultPreviewClicked(
        val result: SearchResult,
    ) : SearchUiAction

    data class SearchResultBriefingClicked(
        val result: SearchResult,
    ) : SearchUiAction

    data class BookmarkToggleClicked(
        val result: SearchResult,
    ) : SearchUiAction

    data class LowVisionBookmarkSaveClicked(
        val result: SearchResult,
    ) : SearchUiAction

    data object LoadNextPageClicked : SearchUiAction
}

sealed interface SearchUiEvent {
    data object NavigateBack : SearchUiEvent

    data object NavigateToVoiceInput : SearchUiEvent

    data class NavigateToResults(
        val query: String,
        val editingTarget: RouteEditingTarget,
        val selectionMode: SearchSelectionMode = SearchSelectionMode.PREVIEW_ON_MAP,
    ) : SearchUiEvent

    data object StartVoiceCapture : SearchUiEvent

    data object StopVoiceCapture : SearchUiEvent

    data object RequestLocationPermission : SearchUiEvent

    data class NavigateToRouteSetting(
        val locationPermissionPrechecked: Boolean = false,
    ) : SearchUiEvent

    data object NavigateToMapPreview : SearchUiEvent

    data class NavigateToRouteEndpointMapPicker(
        val editingTarget: RouteEditingTarget,
    ) : SearchUiEvent

    data object NavigateToRouteBriefing : SearchUiEvent

    data object NavigateToLowVisionBookmark : SearchUiEvent
}

sealed interface SearchResultUiState {
    data object Initial : SearchResultUiState

    data object EmptyQuery : SearchResultUiState

    data class Typing(
        val query: String,
    ) : SearchResultUiState

    data class Loading(
        val query: String,
    ) : SearchResultUiState

    data class Success(
        val query: String,
        val results: List<SearchResult>,
        val nextCursor: String? = null,
        val hasNext: Boolean = false,
        val isLoadingNextPage: Boolean = false,
    ) : SearchResultUiState

    data class Empty(
        val query: String,
    ) : SearchResultUiState

    data class Error(
        val query: String,
        val message: String? = null,
    ) : SearchResultUiState
}
