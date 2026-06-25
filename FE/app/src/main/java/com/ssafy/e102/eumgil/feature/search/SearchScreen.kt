package com.ssafy.e102.eumgil.feature.search

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.designsystem.component.feedback.EumCircularLoadingIndicator
import com.ssafy.e102.eumgil.core.designsystem.component.navigation.EumCenteredTopBar
import com.ssafy.e102.eumgil.core.designsystem.theme.BusanEumgilLightColorScheme
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing
import com.ssafy.e102.eumgil.core.model.RecentSearch
import com.ssafy.e102.eumgil.core.model.SearchResult
import com.ssafy.e102.eumgil.core.model.SearchSortOption
import com.ssafy.e102.eumgil.data.repository.RouteEditingTarget
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

enum class SearchScreenDestination {
    Entry,
    Results,
    VoiceInput,
}

enum class SearchTrailingAction {
    VoiceInput,
    ClearQuery,
}

internal fun resolveSearchTrailingAction(query: String): SearchTrailingAction =
    if (query.isEmpty()) {
        SearchTrailingAction.VoiceInput
    } else {
        SearchTrailingAction.ClearQuery
    }

internal fun shouldShowSearchResultSection(resultState: SearchResultUiState): Boolean =
    resultState != SearchResultUiState.EmptyQuery && resultState !is SearchResultUiState.Typing

internal fun shouldAutoRequestNextSearchPage(
    lastVisibleItemIndex: Int,
    totalItemsCount: Int,
    hasNext: Boolean,
    isLoadingNextPage: Boolean,
): Boolean =
    hasNext &&
        isLoadingNextPage.not() &&
        totalItemsCount > 0 &&
        lastVisibleItemIndex >= totalItemsCount - SEARCH_NEXT_PAGE_PREFETCH_ITEM_THRESHOLD

internal fun resolveSearchResultClickAction(
    selectionMode: SearchSelectionMode,
    result: SearchResult,
): SearchUiAction =
    when (selectionMode) {
        SearchSelectionMode.PREVIEW_ON_MAP -> SearchUiAction.SearchResultPreviewClicked(result = result)
        SearchSelectionMode.APPLY_TO_ROUTE -> SearchUiAction.SearchResultClicked(result = result)
    }

internal fun shouldShowRouteEndpointQuickActions(
    selectionMode: SearchSelectionMode,
    editingTarget: RouteEditingTarget,
): Boolean =
    selectionMode == SearchSelectionMode.APPLY_TO_ROUTE &&
        (editingTarget == RouteEditingTarget.ORIGIN || editingTarget == RouteEditingTarget.DESTINATION)

internal data class RouteEndpointQuickActionCopy(
    @StringRes val currentLocationActionRes: Int,
    @StringRes val currentLocationContentDescriptionRes: Int,
    @StringRes val mapPickerActionRes: Int,
    @StringRes val mapPickerContentDescriptionRes: Int,
)

internal fun resolveRouteEndpointQuickActionCopy(editingTarget: RouteEditingTarget): RouteEndpointQuickActionCopy =
    when (editingTarget) {
        RouteEditingTarget.ORIGIN ->
            RouteEndpointQuickActionCopy(
                currentLocationActionRes = R.string.search_screen_current_location_origin_action,
                currentLocationContentDescriptionRes = R.string.search_screen_current_location_origin_a11y,
                mapPickerActionRes = R.string.search_screen_map_picker_origin_action,
                mapPickerContentDescriptionRes = R.string.search_screen_map_picker_origin_a11y,
            )

        RouteEditingTarget.DESTINATION ->
            RouteEndpointQuickActionCopy(
                currentLocationActionRes = R.string.search_screen_current_location_destination_action,
                currentLocationContentDescriptionRes = R.string.search_screen_current_location_destination_a11y,
                mapPickerActionRes = R.string.search_screen_map_picker_destination_action,
                mapPickerContentDescriptionRes = R.string.search_screen_map_picker_destination_a11y,
            )
    }

internal data class SearchCurrentLocationStatusContent(
    @StringRes val messageRes: Int,
    val isError: Boolean = false,
    val showProgress: Boolean = false,
)

internal fun resolveSearchCurrentLocationStatusContent(
    status: SearchCurrentLocationQuickActionStatus,
    editingTarget: RouteEditingTarget,
): SearchCurrentLocationStatusContent? =
    when (status) {
        SearchCurrentLocationQuickActionStatus.Idle -> null
        SearchCurrentLocationQuickActionStatus.Resolving ->
            SearchCurrentLocationStatusContent(
                messageRes = R.string.search_screen_current_location_resolving_status,
                showProgress = true,
            )

        SearchCurrentLocationQuickActionStatus.Applied ->
            SearchCurrentLocationStatusContent(
                messageRes =
                    when (editingTarget) {
                        RouteEditingTarget.ORIGIN -> R.string.search_screen_current_location_origin_applied_status
                        RouteEditingTarget.DESTINATION -> R.string.search_screen_current_location_destination_applied_status
                    },
            )

        SearchCurrentLocationQuickActionStatus.PermissionDenied ->
            SearchCurrentLocationStatusContent(
                messageRes = R.string.search_screen_current_location_permission_denied_status,
                isError = true,
            )

        SearchCurrentLocationQuickActionStatus.LocationUnavailable ->
            SearchCurrentLocationStatusContent(
                messageRes = R.string.search_screen_current_location_unavailable_status,
                isError = true,
            )

        SearchCurrentLocationQuickActionStatus.LocationAccessUnavailable ->
            SearchCurrentLocationStatusContent(
                messageRes = R.string.search_screen_current_location_access_unavailable_status,
                isError = true,
            )
    }

internal data class SearchResultDistanceUiState(
    @StringRes val labelResId: Int,
    val value: Number,
)

internal fun resolveSearchResultDistanceUiState(distanceMeters: Int?): SearchResultDistanceUiState? {
    val normalizedDistanceMeters = distanceMeters?.takeIf { value -> value >= 0 } ?: return null

    return if (normalizedDistanceMeters < METERS_PER_KILOMETER) {
        SearchResultDistanceUiState(
            labelResId = R.string.search_screen_result_distance_meters,
            value = normalizedDistanceMeters,
        )
    } else {
        SearchResultDistanceUiState(
            labelResId = R.string.search_screen_result_distance_kilometers,
            value = normalizedDistanceMeters / METERS_PER_KILOMETER.toDouble(),
        )
    }
}

internal data class SearchCopyUiState(
    @StringRes val entryTitleRes: Int,
    @StringRes val resultsTitleRes: Int,
    @StringRes val entryHeadlineRes: Int,
    @StringRes val queryPlaceholderRes: Int,
    @StringRes val voiceInputTitleRes: Int,
    @StringRes val voiceInputHeadlineRes: Int,
    @StringRes val voiceInputDescriptionRes: Int?,
    @StringRes val voiceInputExamplePhraseRes: Int,
    @StringRes val initialTitleRes: Int,
    @StringRes val initialDescriptionRes: Int,
    @StringRes val resultSummaryRes: Int,
    @StringRes val emptyResultDescriptionRes: Int,
    @StringRes val resultActionLabelRes: Int,
    @StringRes val resultSelectableDescriptionRes: Int,
)

internal fun resolveSearchCopyUiState(editingTarget: RouteEditingTarget): SearchCopyUiState =
    when (editingTarget) {
        RouteEditingTarget.ORIGIN ->
            SearchCopyUiState(
                entryTitleRes = R.string.search_origin_screen_title,
                resultsTitleRes = R.string.search_origin_results_screen_title,
                entryHeadlineRes = R.string.search_origin_screen_entry_headline,
                queryPlaceholderRes = R.string.search_origin_screen_query_placeholder,
                voiceInputTitleRes = R.string.search_origin_voice_input_title,
                voiceInputHeadlineRes = R.string.search_origin_voice_input_headline,
                voiceInputDescriptionRes = R.string.search_origin_voice_input_description,
                voiceInputExamplePhraseRes = R.string.search_origin_voice_input_example_phrase,
                initialTitleRes = R.string.search_origin_screen_initial_title,
                initialDescriptionRes = R.string.search_origin_screen_initial_description,
                resultSummaryRes = R.string.search_origin_screen_result_summary,
                emptyResultDescriptionRes = R.string.search_origin_screen_empty_result_description,
                resultActionLabelRes = R.string.search_origin_screen_result_action_label,
                resultSelectableDescriptionRes = R.string.search_origin_screen_result_selectable,
            )

        RouteEditingTarget.DESTINATION ->
            SearchCopyUiState(
                entryTitleRes = R.string.search_screen_title,
                resultsTitleRes = R.string.search_results_screen_title,
                entryHeadlineRes = R.string.search_screen_entry_headline,
                queryPlaceholderRes = R.string.search_screen_query_placeholder,
                voiceInputTitleRes = R.string.search_voice_input_title,
                voiceInputHeadlineRes = R.string.search_voice_input_headline,
                voiceInputDescriptionRes = null,
                voiceInputExamplePhraseRes = R.string.search_voice_input_example_phrase,
                initialTitleRes = R.string.search_screen_initial_title,
                initialDescriptionRes = R.string.search_screen_initial_description,
                resultSummaryRes = R.string.search_screen_result_summary,
                emptyResultDescriptionRes = R.string.search_screen_empty_result_description,
                resultActionLabelRes = R.string.search_screen_result_action_label,
                resultSelectableDescriptionRes = R.string.search_screen_result_selectable,
            )
    }

internal fun shouldShowDestinationPromoBanner(editingTarget: RouteEditingTarget): Boolean =
    editingTarget == RouteEditingTarget.DESTINATION ||
        editingTarget == RouteEditingTarget.ORIGIN

internal fun resolveVoiceInputBackgroundDestination(resultState: SearchResultUiState): SearchScreenDestination =
    when (resultState) {
        SearchResultUiState.Initial,
        SearchResultUiState.EmptyQuery,
        is SearchResultUiState.Typing,
        -> SearchScreenDestination.Entry

        is SearchResultUiState.Loading,
        is SearchResultUiState.Success,
        is SearchResultUiState.Empty,
        is SearchResultUiState.Error,
        -> SearchScreenDestination.Results
    }

internal fun searchVoiceInputSheetTopCornerRadius(): Dp = EumRadius.scaleL

internal fun searchVoiceInputSheetContainerColor(): Color = BusanEumgilLightColorScheme.surface

internal fun shouldShowSearchVoiceInputTranscriptPreview(
    voiceInputState: SearchVoiceInputUiState,
): Boolean = voiceInputState.transcript.isNotBlank()

internal data class SearchVoiceInputStatusContent(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int? = null,
)

internal fun resolveSearchVoiceInputStatusContent(
    voiceInputState: SearchVoiceInputUiState,
): SearchVoiceInputStatusContent? =
    when {
        voiceInputState.status == SearchVoiceInputStatus.Recognized ->
            SearchVoiceInputStatusContent(
                titleRes = R.string.search_voice_input_status_recognized_title,
                descriptionRes = R.string.search_voice_input_status_recognized_description,
            )

        voiceInputState.status == SearchVoiceInputStatus.Listening ->
            SearchVoiceInputStatusContent(
                titleRes = R.string.search_voice_input_status_listening_title,
            )

        voiceInputState.guidance == SearchVoiceInputGuidance.RetryRequired ->
            SearchVoiceInputStatusContent(
                titleRes = R.string.search_voice_input_status_retry_message,
            )

        else -> null
    }

internal data class DestinationPromoBannerModel(
    @DrawableRes val imageRes: Int,
    @StringRes val contentDescriptionRes: Int,
)

internal fun searchDestinationPromoBannerModel(): DestinationPromoBannerModel =
    DestinationPromoBannerModel(
        imageRes = R.drawable.dest01_accessibility_banner,
        contentDescriptionRes = R.string.search_screen_promo_banner_content_description,
    )

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onAction: (SearchUiAction) -> Unit,
    modifier: Modifier = Modifier,
    destination: SearchScreenDestination = SearchScreenDestination.Entry,
) {
    when (destination) {
        SearchScreenDestination.VoiceInput ->
            SearchVoiceInputScreen(
                uiState = uiState,
                onAction = onAction,
                modifier = modifier,
            )

        SearchScreenDestination.Entry,
        SearchScreenDestination.Results,
        -> SearchPrimaryScreen(
            uiState = uiState,
            onAction = onAction,
            destination = destination,
            modifier = modifier,
        )
    }
}

@Composable
private fun SearchPrimaryScreen(
    uiState: SearchUiState,
    onAction: (SearchUiAction) -> Unit,
    destination: SearchScreenDestination,
    modifier: Modifier = Modifier,
) {
    val copy = resolveSearchCopyUiState(uiState.editingTarget)
    val titleRes =
        when (destination) {
            SearchScreenDestination.Entry -> copy.entryTitleRes
            SearchScreenDestination.Results -> copy.resultsTitleRes
            SearchScreenDestination.VoiceInput -> copy.voiceInputTitleRes
        }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = SearchScreenContentWindowInsets,
        topBar = {
            SearchTopBar(
                titleRes = titleRes,
                onBackClick = { onAction(SearchUiAction.BackClicked) },
            )
        },
    ) { innerPadding ->
        SearchContentBody(
            uiState = uiState,
            copy = copy,
            onAction = onAction,
            destination = destination,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        )
    }
}

@Composable
private fun SearchTopBar(
    @StringRes titleRes: Int,
    onBackClick: () -> Unit,
) {
    EumCenteredTopBar(
        title = stringResource(id = titleRes),
        onBackClick = onBackClick,
        backContentDescription = stringResource(id = R.string.search_screen_back),
    )
}

@Composable
private fun SearchContentBody(
    uiState: SearchUiState,
    copy: SearchCopyUiState,
    onAction: (SearchUiAction) -> Unit,
    destination: SearchScreenDestination,
    modifier: Modifier = Modifier,
) {
    if (destination == SearchScreenDestination.Results) {
        SearchResultsContent(
            uiState = uiState,
            copy = copy,
            onAction = onAction,
            modifier =
                modifier.padding(
                    horizontal = EumSpacing.medium,
                    vertical = EumSpacing.medium,
                ),
        )
        return
    }

    when (destination) {
        SearchScreenDestination.Entry ->
            SearchEntryContent(
                uiState = uiState,
                copy = copy,
                onAction = onAction,
                modifier =
                    modifier
                        .fillMaxSize()
                        .padding(horizontal = EumSpacing.medium, vertical = EumSpacing.medium),
            )

        SearchScreenDestination.Results,
        SearchScreenDestination.VoiceInput -> Unit
    }
}

@Composable
private fun SearchEntryContent(
    uiState: SearchUiState,
    copy: SearchCopyUiState,
    onAction: (SearchUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
    ) {
        Text(
            text = stringResource(id = copy.entryHeadlineRes),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SearchInputField(
            query = uiState.query,
            queryPlaceholderRes = copy.queryPlaceholderRes,
            showEmptyQueryError = uiState.resultState is SearchResultUiState.EmptyQuery,
            onQueryChanged = { onAction(SearchUiAction.QueryChanged(query = it)) },
            onVoiceInputClick = { onAction(SearchUiAction.VoiceInputClicked) },
            onClearQueryClick = { onAction(SearchUiAction.ClearQueryClicked) },
            onSearch = { onAction(SearchUiAction.SearchSubmitted) },
        )
        if (shouldShowRouteEndpointQuickActions(uiState.selectionMode, uiState.editingTarget)) {
            RouteEndpointQuickActionSection(
                editingTarget = uiState.editingTarget,
                currentLocationState = uiState.currentLocationQuickActionState,
                onCurrentLocationClick = { onAction(SearchUiAction.CurrentLocationClicked) },
                onMapPickerClick = { onAction(SearchUiAction.MapPickerClicked) },
            )
        }
        RecentVisitSection(
            recentSearches = uiState.recentSearches,
            onAction = onAction,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        )
        if (shouldShowDestinationPromoBanner(uiState.editingTarget)) {
            DestinationPromoBanner()
        }
    }
}

@Composable
private fun SearchResultsContent(
    uiState: SearchUiState,
    copy: SearchCopyUiState,
    onAction: (SearchUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val successState = uiState.resultState as? SearchResultUiState.Success
    val canLoadNextPage = successState?.let { state -> state.hasNext && state.isLoadingNextPage.not() } == true

    LaunchedEffect(listState, successState?.query, successState?.results?.size, canLoadNextPage) {
        if (!canLoadNextPage) return@LaunchedEffect

        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            shouldAutoRequestNextSearchPage(
                lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1,
                totalItemsCount = layoutInfo.totalItemsCount,
                hasNext = successState?.hasNext == true,
                isLoadingNextPage = successState?.isLoadingNextPage == true,
            )
        }
            .distinctUntilChanged()
            .filter { shouldLoad -> shouldLoad }
            .collect { onAction(SearchUiAction.LoadNextPageClicked) }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
    ) {
        SearchInputField(
            query = uiState.query,
            queryPlaceholderRes = copy.queryPlaceholderRes,
            showEmptyQueryError = uiState.resultState is SearchResultUiState.EmptyQuery,
            onQueryChanged = { onAction(SearchUiAction.QueryChanged(query = it)) },
            onVoiceInputClick = { onAction(SearchUiAction.VoiceInputClicked) },
            onClearQueryClick = { onAction(SearchUiAction.ClearQueryClicked) },
            onSearch = { onAction(SearchUiAction.SearchSubmitted) },
        )
        SearchSortControl(
            selectedSortOption = uiState.sortOption,
            onSortOptionSelected = { sortOption ->
                onAction(SearchUiAction.SortOptionSelected(sortOption = sortOption))
            },
        )

        when (val resultState = uiState.resultState) {
            SearchResultUiState.Initial ->
                SearchResultStateBox {
                    SearchCenteredStateMessage(
                        title = stringResource(id = copy.initialTitleRes),
                        description = stringResource(id = copy.initialDescriptionRes),
                        showIllustration = false,
                    )
                }

            SearchResultUiState.EmptyQuery ->
                SearchResultStateBox {
                    SearchCenteredStateMessage(
                        title = stringResource(id = copy.initialTitleRes),
                        description = stringResource(id = copy.initialDescriptionRes),
                        showIllustration = false,
                    )
                }

            is SearchResultUiState.Typing -> Unit

            is SearchResultUiState.Loading ->
                SearchResultStateBox {
                    SearchCenteredStateMessage(
                        title = stringResource(id = R.string.search_screen_loading_title, resultState.query),
                        description = stringResource(id = R.string.search_screen_loading_description),
                        showIllustration = false,
                        showLoadingIndicator = true,
                    )
                }

            is SearchResultUiState.Success -> {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
                ) {
                    item(key = "result-summary") {
                        Text(
                            text = stringResource(id = copy.resultSummaryRes, resultState.results.size),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    items(
                        items = resultState.results,
                    ) { result ->
                        SearchResultItem(
                            copy = copy,
                            result = result,
                            onClick = {
                                onAction(
                                    resolveSearchResultClickAction(
                                        selectionMode = uiState.selectionMode,
                                        result = result,
                                    ),
                                )
                            },
                        )
                    }
                    if (resultState.isLoadingNextPage) {
                        item(key = "next-page-loading") {
                            SearchNextPageLoadingIndicator()
                        }
                    }
                }
            }

            is SearchResultUiState.Empty ->
                SearchResultStateBox {
                    SearchCenteredStateMessage(
                        title = stringResource(id = R.string.search_screen_empty_result_title),
                        description = null,
                        illustrationRes = R.drawable.search_empty_result_illustration,
                        illustrationSize = SearchEmptyResultIllustrationSize,
                        contentOffsetY = SearchEmptyResultContentOffsetY,
                        titleTopPadding = SearchEmptyResultTitleTopPadding,
                        useEmptyResultTypography = true,
                    )
                }

            is SearchResultUiState.Error ->
                SearchResultStateBox {
                    SearchCenteredStateMessage(
                        title = stringResource(id = R.string.search_screen_error_title),
                        description = stringResource(id = R.string.search_screen_error_description),
                    )
                }
        }
    }
}

@Composable
private fun ColumnScope.SearchResultStateBox(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun SearchInputField(
    query: String,
    @StringRes queryPlaceholderRes: Int,
    showEmptyQueryError: Boolean,
    onQueryChanged: (String) -> Unit,
    onVoiceInputClick: () -> Unit,
    onClearQueryClick: () -> Unit,
    onSearch: () -> Unit,
) {
    val trailingAction = resolveSearchTrailingAction(query = query)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissKeyboardBeforeVoiceInput = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onVoiceInputClick()
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(text = stringResource(id = queryPlaceholderRes)) },
        leadingIcon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_nav_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            when (trailingAction) {
                SearchTrailingAction.VoiceInput ->
                    IconButton(onClick = dismissKeyboardBeforeVoiceInput) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_search_voice_mic),
                            contentDescription = stringResource(id = R.string.search_screen_voice_input),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }

                SearchTrailingAction.ClearQuery ->
                    IconButton(onClick = onClearQueryClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_action_close),
                            contentDescription = stringResource(id = R.string.search_screen_clear_query),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
            }
        },
        singleLine = true,
        isError = showEmptyQueryError,
        shape = RoundedCornerShape(EumRadius.small),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                errorContainerColor = MaterialTheme.colorScheme.surface,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions =
            KeyboardActions(
                onSearch = { onSearch() },
            ),
        supportingText =
            if (showEmptyQueryError) {
                {
                    Text(text = stringResource(id = R.string.search_screen_empty_query_description))
                }
            } else {
                null
            },
    )
}

@Composable
private fun RouteEndpointQuickActionSection(
    editingTarget: RouteEditingTarget,
    currentLocationState: SearchCurrentLocationQuickActionUiState,
    onCurrentLocationClick: () -> Unit,
    onMapPickerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = resolveRouteEndpointQuickActionCopy(editingTarget)
    val statusContent =
        resolveSearchCurrentLocationStatusContent(
            status = currentLocationState.status,
            editingTarget = editingTarget,
        )
    val isResolving = currentLocationState.status == SearchCurrentLocationQuickActionStatus.Resolving

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
        ) {
            RouteEndpointCurrentLocationButton(
                labelRes = copy.currentLocationActionRes,
                contentDescriptionRes = copy.currentLocationContentDescriptionRes,
                enabled = isResolving.not(),
                onClick = onCurrentLocationClick,
                modifier = Modifier.weight(1f),
            )
            RouteEndpointMapPickerButton(
                labelRes = copy.mapPickerActionRes,
                contentDescriptionRes = copy.mapPickerContentDescriptionRes,
                onClick = onMapPickerClick,
                modifier = Modifier.weight(1f),
            )
        }
        if (statusContent != null) {
            RouteEndpointCurrentLocationStatus(content = statusContent)
        }
    }
}

@Composable
private fun RouteEndpointCurrentLocationButton(
    @StringRes labelRes: Int,
    @StringRes contentDescriptionRes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentLocationContentDescription = stringResource(id = contentDescriptionRes)

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = currentLocationContentDescription
                },
        shape = RoundedCornerShape(EumRadius.medium),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_map_current_location),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(id = labelRes),
            modifier = Modifier.padding(start = EumSpacing.xSmall),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun RouteEndpointMapPickerButton(
    @StringRes labelRes: Int,
    @StringRes contentDescriptionRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mapPickerContentDescription = stringResource(id = contentDescriptionRes)

    OutlinedButton(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = mapPickerContentDescription
                },
        shape = RoundedCornerShape(EumRadius.medium),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_map_selected_pin_blue),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(id = labelRes),
            modifier = Modifier.padding(start = EumSpacing.xSmall),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun RouteEndpointCurrentLocationStatus(
    content: SearchCurrentLocationStatusContent,
    modifier: Modifier = Modifier,
) {
    val contentColor =
        when {
            content.isError -> MaterialTheme.colorScheme.error
            content.showProgress -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val containerColor =
        when {
            content.isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EumRadius.medium),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EumSpacing.medium, vertical = EumSpacing.small),
            horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (content.showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_map_current_location),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = contentColor,
                )
            }
            Text(
                text = stringResource(id = content.messageRes),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchVoiceInputScreen(
    uiState: SearchUiState,
    onAction: (SearchUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = resolveSearchCopyUiState(uiState.editingTarget)
    val backgroundDestination = resolveVoiceInputBackgroundDestination(uiState.resultState)
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(modifier = modifier.fillMaxSize()) {
        SearchPrimaryScreen(
            uiState = uiState,
            onAction = onAction,
            destination = backgroundDestination,
            modifier = Modifier.fillMaxSize(),
        )
        SearchVoiceInputBottomSheet(
            uiState = uiState,
            onAction = onAction,
            bottomSheetState = bottomSheetState,
            copy = copy,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchVoiceInputBottomSheet(
    uiState: SearchUiState,
    onAction: (SearchUiAction) -> Unit,
    bottomSheetState: androidx.compose.material3.SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    copy: SearchCopyUiState = resolveSearchCopyUiState(uiState.editingTarget),
) {
    ModalBottomSheet(
        onDismissRequest = { onAction(SearchUiAction.VoiceInputDismissed) },
        sheetState = bottomSheetState,
        dragHandle = null,
        shape =
            RoundedCornerShape(
                topStart = searchVoiceInputSheetTopCornerRadius(),
                topEnd = searchVoiceInputSheetTopCornerRadius(),
                bottomEnd = 0.dp,
                bottomStart = 0.dp,
            ),
        containerColor = searchVoiceInputSheetContainerColor(),
        scrimColor = Color.Black.copy(alpha = 0.38f),
        windowInsets = SearchVoiceInputBottomSheetWindowInsets,
    ) {
        SearchVoiceInputContent(
            uiState = uiState,
            copy = copy,
            onAction = onAction,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(searchVoiceInputSheetContainerColor())
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun SearchVoiceInputContent(
    uiState: SearchUiState,
    copy: SearchCopyUiState,
    onAction: (SearchUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusContent = resolveSearchVoiceInputStatusContent(uiState.voiceInputState)
    val showTranscriptPreview = shouldShowSearchVoiceInputTranscriptPreview(uiState.voiceInputState)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(id = copy.voiceInputTitleRes),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = { onAction(SearchUiAction.VoiceInputDismissed) }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_action_close),
                    contentDescription = stringResource(id = R.string.search_voice_input_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = stringResource(id = copy.voiceInputHeadlineRes),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (copy.voiceInputDescriptionRes != null) {
            Text(
                text = stringResource(id = copy.voiceInputDescriptionRes),
                modifier = Modifier.padding(top = EumSpacing.xSmall),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (showTranscriptPreview) {
            SearchStateCard(
                title = stringResource(id = R.string.search_voice_input_transcript_title),
                description = uiState.voiceInputState.transcript,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = EumSpacing.medium),
                containerColor = MaterialTheme.colorScheme.surface,
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
            )
        } else {
            Surface(
                modifier = Modifier.padding(top = EumSpacing.medium),
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
            ) {
                Text(
                    text = stringResource(id = copy.voiceInputExamplePhraseRes),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .padding(top = 28.dp)
                    .size(132.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            ) {}
            Surface(
                onClick = { onAction(SearchUiAction.VoiceCaptureButtonClicked) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 6.dp,
            ) {
                Box(
                    modifier = Modifier.size(108.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search_voice_mic),
                        contentDescription = stringResource(id = R.string.search_screen_voice_input),
                        modifier = Modifier.size(40.dp),
                        tint = Color.White,
                    )
                }
            }
        }

        if (statusContent != null) {
            Text(
                text = stringResource(id = statusContent.titleRes),
                modifier = Modifier.padding(top = 20.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (statusContent.descriptionRes != null) {
                Text(
                    text = stringResource(id = statusContent.descriptionRes),
                    modifier = Modifier.padding(top = EumSpacing.xSmall),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

    }
}

@Composable
private fun SearchResultSection(
    copy: SearchCopyUiState,
    resultState: SearchResultUiState,
    onAction: (SearchUiAction) -> Unit,
    selectionMode: SearchSelectionMode = SearchSelectionMode.PREVIEW_ON_MAP,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
    ) {
        when (resultState) {
            SearchResultUiState.Initial ->
                SearchCenteredStateMessage(
                    title = stringResource(id = copy.initialTitleRes),
                    description = stringResource(id = copy.initialDescriptionRes),
                    showIllustration = false,
                )

            SearchResultUiState.EmptyQuery ->
                SearchCenteredStateMessage(
                    title = stringResource(id = copy.initialTitleRes),
                    description = stringResource(id = copy.initialDescriptionRes),
                    showIllustration = false,
                )

            is SearchResultUiState.Typing -> Unit

            is SearchResultUiState.Loading ->
                SearchCenteredStateMessage(
                    title = stringResource(id = R.string.search_screen_loading_title, resultState.query),
                    description = stringResource(id = R.string.search_screen_loading_description),
                    showIllustration = false,
                    showLoadingIndicator = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = EumSpacing.small),
                )

            is SearchResultUiState.Success -> {
                Text(
                    text = stringResource(id = copy.resultSummaryRes, resultState.results.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                resultState.results.forEach { result ->
                    SearchResultItem(
                        copy = copy,
                        result = result,
                        onClick = {
                            onAction(
                                resolveSearchResultClickAction(
                                    selectionMode = selectionMode,
                                    result = result,
                                ),
                            )
                        },
                    )
                }
                if (resultState.isLoadingNextPage) {
                    SearchNextPageLoadingIndicator()
                }
            }

            is SearchResultUiState.Empty ->
                SearchCenteredStateMessage(
                    title = stringResource(id = R.string.search_screen_empty_result_title),
                    description = null,
                    illustrationRes = R.drawable.search_empty_result_illustration,
                    illustrationSize = SearchEmptyResultIllustrationSize,
                    contentOffsetY = SearchEmptyResultContentOffsetY,
                    titleTopPadding = SearchEmptyResultTitleTopPadding,
                    useEmptyResultTypography = true,
                )

            is SearchResultUiState.Error ->
                SearchCenteredStateMessage(
                    title = stringResource(id = R.string.search_screen_error_title),
                    description = stringResource(id = R.string.search_screen_error_description),
                )
        }
    }
}

@Composable
private fun SearchSortControl(
    selectedSortOption: SearchSortOption,
    onSortOptionSelected: (SearchSortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EumRadius.full),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
        ) {
            val indicatorWidth = (maxWidth - SearchSortOptionButtonGap) / 2
            val targetIndicatorOffset =
                if (selectedSortOption == SearchSortOption.RELEVANCE) {
                    0.dp
                } else {
                    indicatorWidth + SearchSortOptionButtonGap
                }
            val animatedIndicatorOffset by animateDpAsState(
                targetValue = targetIndicatorOffset,
                animationSpec = tween(SearchSortOptionButtonAnimationMillis),
                label = "SearchSortOptionIndicatorOffset",
            )

            Surface(
                modifier =
                    Modifier
                        .offset(x = animatedIndicatorOffset)
                        .width(indicatorWidth)
                        .heightIn(min = SearchSortOptionButtonHeight),
                shape = RoundedCornerShape(EumRadius.full),
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {}
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SearchSortOptionButtonGap),
            ) {
                SearchSortOptionButton(
                    label = stringResource(id = R.string.search_screen_sort_relevance),
                    selected = selectedSortOption == SearchSortOption.RELEVANCE,
                    onClick = { onSortOptionSelected(SearchSortOption.RELEVANCE) },
                    modifier = Modifier.weight(1f),
                )
                SearchSortOptionButton(
                    label = stringResource(id = R.string.search_screen_sort_distance),
                    selected = selectedSortOption == SearchSortOption.DISTANCE,
                    onClick = { onSortOptionSelected(SearchSortOption.DISTANCE) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SearchSortOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(EumRadius.full))
                .clickable(
                    role = Role.RadioButton,
                    onClick = onClick,
                )
                .semantics {
                    this.selected = selected
                    stateDescription =
                        if (selected) {
                            label + " 선택됨"
                        } else {
                            label + " 선택 안 됨"
                        }
                },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = SearchSortOptionButtonHeight)
                    .padding(horizontal = EumSpacing.medium, vertical = EumSpacing.xxSmall),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun SearchNextPageLoadingIndicator() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun RecentVisitSection(
    recentSearches: List<RecentSearch>,
    onAction: (SearchUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(id = R.string.search_screen_recent_section_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (recentSearches.isNotEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .heightIn(min = 44.dp)
                            .clickable(
                                role = Role.Button,
                                onClick = { onAction(SearchUiAction.RecentSearchClearAllClicked) },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(id = R.string.search_screen_recent_clear_all),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (recentSearches.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(id = R.string.search_screen_recent_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
            ) {
                items(
                    items = recentSearches,
                    key = { recentSearch -> recentSearch.keyword },
                ) { recentSearch ->
                    RecentVisitItem(
                        keyword = recentSearch.keyword,
                        onClick = {
                            onAction(
                                SearchUiAction.RecentSearchClicked(
                                    keyword = recentSearch.keyword,
                                ),
                            )
                        },
                        onDeleteClick = {
                            onAction(
                                SearchUiAction.RecentSearchDeleteClicked(
                                    keyword = recentSearch.keyword,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentVisitItem(
    keyword: String,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
        shape = RoundedCornerShape(EumRadius.small),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = EumSpacing.medium, end = EumSpacing.xSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = keyword,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(vertical = EumSpacing.medium),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = onDeleteClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_action_close),
                    contentDescription =
                        stringResource(
                            id = R.string.search_screen_recent_delete,
                            keyword,
                        ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DestinationPromoBanner(
    modifier: Modifier = Modifier,
) {
    val model = searchDestinationPromoBannerModel()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EumRadius.large),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
        shadowElevation = 2.dp,
    ) {
        Image(
            painter = painterResource(id = model.imageRes),
            contentDescription = stringResource(id = model.contentDescriptionRes),
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
    }
}

@Composable
private fun SearchResultItem(
    copy: SearchCopyUiState,
    result: SearchResult,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val actionLabel = stringResource(id = copy.resultActionLabelRes)
    val stateDescription =
        stringResource(
            id =
                resolveSearchResultStateDescriptionRes(
                    result = result,
                    selectableResId = copy.resultSelectableDescriptionRes,
                ),
        )
    val accessibilityDescription =
        if (result.subtitle.isBlank()) {
            stringResource(
                id = R.string.search_screen_result_a11y_without_address,
                result.title,
            )
        } else {
            stringResource(
                id = R.string.search_screen_result_a11y_with_address,
                result.title,
                result.subtitle,
            )
        }
    val accessibilityTagUiState = resolveSearchResultAccessibilityTagUiState(result.accessibilityTagKeys)
    val trimmedAddress = result.subtitle.trim()
    val distanceUiState = resolveSearchResultDistanceUiState(result.distanceMeters)
    val distanceText =
        distanceUiState?.let { uiState ->
            stringResource(id = uiState.labelResId, uiState.value)
        }
    val accessibilityDescriptionWithDistance =
        when {
            distanceText == null -> accessibilityDescription
            result.subtitle.isBlank() ->
                stringResource(
                    id = R.string.search_screen_result_a11y_without_address_with_distance,
                    result.title,
                    distanceText,
                )
            else ->
                stringResource(
                    id = R.string.search_screen_result_a11y_with_address_and_distance,
                    result.title,
                    result.subtitle,
                    distanceText,
                )
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = actionLabel,
                    onClick = onClick,
                ).semantics(mergeDescendants = true) {
                    contentDescription = accessibilityDescriptionWithDistance
                    this.stateDescription = stateDescription
                },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = EumSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(EumSpacing.medium),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(SearchResultPlaceIconContainerSize)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_map_selected_pin_blue),
                    contentDescription = null,
                    modifier = Modifier.size(SearchResultPlaceIconSize),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
            ) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (trimmedAddress.isNotEmpty()) {
                    Text(
                        text = trimmedAddress,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (distanceText != null) {
                    Text(
                        text = distanceText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (accessibilityTagUiState.hasLabels) {
                    SearchResultAccessibilityTagRow(uiState = accessibilityTagUiState)
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SearchResultAccessibilityTagRow(
    uiState: SearchResultAccessibilityTagUiState,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        uiState.labelResIds.forEach { labelResId ->
            SearchResultAccessibilityTagChip(
                text = stringResource(id = labelResId),
                iconRes = searchResultAccessibilityTagIconRes(labelResId),
            )
        }
    }
}

@Composable
private fun SearchResultAccessibilityTagChip(
    text: String,
    @DrawableRes iconRes: Int? = null,
) {
    val containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
    val contentColor = MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        border =
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = EumSpacing.small, vertical = EumSpacing.xSmall),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconRes?.let { resId ->
                Icon(
                    painter = painterResource(id = resId),
                    contentDescription = null,
                    modifier = Modifier.size(searchResultAccessibilityTagIconSizeDp(resId).dp),
                    tint = contentColor,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
        }
    }
}

@DrawableRes
private fun searchResultAccessibilityTagIconRes(
    @StringRes labelResId: Int,
): Int? =
    when (labelResId) {
        R.string.place_accessibility_label_accessible_toilet -> R.drawable.ic_accessibility_tag_accessible_toilet
        R.string.place_accessibility_label_elevator -> R.drawable.ic_accessibility_tag_elevator
        R.string.place_accessibility_label_accessible_parking -> R.drawable.ic_accessibility_tag_accessible_parking
        R.string.place_accessibility_label_step_free -> R.drawable.ic_accessibility_tag_step_free
        R.string.place_accessibility_label_guidance_facility -> R.drawable.ic_accessibility_tag_guidance_facility
        else -> null
    }

private fun searchResultAccessibilityTagIconSizeDp(
    @DrawableRes iconRes: Int,
): Int =
    when (iconRes) {
        R.drawable.ic_accessibility_tag_accessible_toilet -> 16
        else -> 14
    }

private const val METERS_PER_KILOMETER = 1_000
private const val SEARCH_NEXT_PAGE_PREFETCH_ITEM_THRESHOLD = 3
private val SearchResultPlaceIconContainerSize: Dp = 56.dp
private val SearchResultPlaceIconSize: Dp = 32.dp
private val SearchSortOptionButtonHeight: Dp = 44.dp
private val SearchSortOptionButtonGap: Dp = 4.dp
private val SearchStateIllustrationMinHeight: Dp = 360.dp
private val SearchStateIllustrationSize: Dp = 128.dp
private val SearchEmptyResultIllustrationSize: Dp = 300.dp
private val SearchEmptyResultContentOffsetY: Dp = (-32).dp
private val SearchEmptyResultTitleTopPadding: Dp = 0.dp
private val SearchScreenContentWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0)

private val SearchVoiceInputBottomSheetWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0)
private val SearchStateTitleLineHeight = 34.sp
private val SearchEmptyResultTitleLineHeight = 34.sp
private const val SearchSortOptionButtonAnimationMillis: Int = 220

@Composable
private fun SearchCenteredStateMessage(
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    showIllustration: Boolean = true,
    showLoadingIndicator: Boolean = false,
    @DrawableRes illustrationRes: Int = R.drawable.manual_galmaegi,
    illustrationSize: Dp = SearchStateIllustrationSize,
    contentOffsetY: Dp = 0.dp,
    titleTopPadding: Dp = EumSpacing.medium,
    useEmptyResultTypography: Boolean = false,
) {
    val titleStyle =
        if (useEmptyResultTypography) {
            MaterialTheme.typography.headlineSmall.copy(
                fontSize = 26.sp,
                lineHeight = SearchEmptyResultTitleLineHeight,
                fontWeight = FontWeight.Bold,
            )
        } else {
            MaterialTheme.typography.headlineSmall.copy(lineHeight = SearchStateTitleLineHeight)
        }
    val descriptionTopPadding = if (useEmptyResultTypography) 16.dp else EumSpacing.xSmall
    val descriptionStyle =
        if (useEmptyResultTypography) {
            MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Normal,
            )
        } else {
            MaterialTheme.typography.bodyLarge
        }
    val titleColor =
        if (useEmptyResultTypography) {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Column(
        modifier =
            modifier
                .offset(y = contentOffsetY)
                .fillMaxWidth()
                .heightIn(min = SearchStateIllustrationMinHeight)
                .padding(
                    horizontal = EumSpacing.medium,
                    vertical = EumSpacing.large,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showIllustration) {
            Image(
                painter = painterResource(id = illustrationRes),
                contentDescription = null,
                modifier = Modifier.size(illustrationSize),
                contentScale = ContentScale.Fit,
            )
        }
        if (showLoadingIndicator) {
            EumCircularLoadingIndicator(
                modifier =
                    Modifier
                        .padding(top = EumSpacing.medium),
            )
        }
        Text(
            text = title,
            modifier = Modifier.padding(top = titleTopPadding),
            style = titleStyle,
            color = titleColor,
            textAlign = TextAlign.Center,
        )
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                modifier = Modifier.padding(top = descriptionTopPadding),
                style = descriptionStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
            )
        }
        if (!supportingText.isNullOrBlank()) {
            Text(
                text = supportingText,
                modifier = Modifier.padding(top = EumSpacing.xSmall),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SearchStateCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    containerColor: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
) {
    val resolvedContainerColor =
        if (containerColor == Color.Unspecified) {
            MaterialTheme.colorScheme.surface
        } else {
            containerColor
        }
    val resolvedBorderColor =
        if (borderColor == Color.Unspecified) {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)
        } else {
            borderColor
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EumRadius.large),
        color = resolvedContainerColor,
        border = BorderStroke(1.dp, resolvedBorderColor),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(EumSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!supportingText.isNullOrBlank()) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
