package com.ssafy.e102.eumgil.feature.search

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.app.BusanEumgilApp
import com.ssafy.e102.eumgil.core.tts.AndroidTextToSpeechController
import com.ssafy.e102.eumgil.data.repository.RouteEditingTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

@Composable
fun SearchEntryRoute(
    onNavigateBack: () -> Unit,
    onNavigateToResults: (String, RouteEditingTarget, SearchSelectionMode) -> Unit,
    onNavigateToVoiceInput: () -> Unit,
    onNavigateToRouteSetting: (Boolean) -> Unit,
    onNavigateToMapPreview: () -> Unit,
    onNavigateToRouteEndpointMapPicker: (RouteEditingTarget) -> Unit,
    onNavigateToRouteBriefing: () -> Unit,
    initialEditingTarget: RouteEditingTarget = RouteEditingTarget.DESTINATION,
    initialSelectionMode: SearchSelectionMode = SearchSelectionMode.PREVIEW_ON_MAP,
    preserveEntryStateOnReentry: Boolean = false,
    modifier: Modifier = Modifier,
) {
    SearchRouteContent(
        destination = SearchScreenDestination.Entry,
        initialQuery = null,
        initialEditingTarget = initialEditingTarget,
        initialSelectionMode = initialSelectionMode,
        preserveEntryStateOnReentry = preserveEntryStateOnReentry,
        onNavigateBack = onNavigateBack,
        onNavigateToResults = onNavigateToResults,
        onNavigateToVoiceInput = onNavigateToVoiceInput,
        onNavigateToRouteSetting = onNavigateToRouteSetting,
        onNavigateToMapPreview = onNavigateToMapPreview,
        onNavigateToRouteEndpointMapPicker = onNavigateToRouteEndpointMapPicker,
        onNavigateToRouteBriefing = onNavigateToRouteBriefing,
        modifier = modifier,
    )
}

@Composable
fun SearchResultsRoute(
    initialQuery: String,
    onNavigateBack: () -> Unit,
    onNavigateToResults: (String, RouteEditingTarget, SearchSelectionMode) -> Unit,
    onNavigateToVoiceInput: () -> Unit,
    onNavigateToRouteSetting: (Boolean) -> Unit,
    onNavigateToMapPreview: () -> Unit,
    onNavigateToRouteEndpointMapPicker: (RouteEditingTarget) -> Unit,
    onNavigateToRouteBriefing: () -> Unit,
    initialEditingTarget: RouteEditingTarget = RouteEditingTarget.DESTINATION,
    initialSelectionMode: SearchSelectionMode = SearchSelectionMode.PREVIEW_ON_MAP,
    modifier: Modifier = Modifier,
) {
    SearchRouteContent(
        destination = SearchScreenDestination.Results,
        initialQuery = initialQuery,
        initialEditingTarget = initialEditingTarget,
        initialSelectionMode = initialSelectionMode,
        onNavigateBack = onNavigateBack,
        onNavigateToResults = onNavigateToResults,
        onNavigateToVoiceInput = onNavigateToVoiceInput,
        onNavigateToRouteSetting = onNavigateToRouteSetting,
        onNavigateToMapPreview = onNavigateToMapPreview,
        onNavigateToRouteEndpointMapPicker = onNavigateToRouteEndpointMapPicker,
        onNavigateToRouteBriefing = onNavigateToRouteBriefing,
        modifier = modifier,
    )
}

@Composable
fun SearchVoiceInputRoute(
    onNavigateBack: () -> Unit,
    onNavigateToResults: (String, RouteEditingTarget, SearchSelectionMode) -> Unit,
    initialEditingTarget: RouteEditingTarget = RouteEditingTarget.DESTINATION,
    initialSelectionMode: SearchSelectionMode = SearchSelectionMode.PREVIEW_ON_MAP,
    modifier: Modifier = Modifier,
) {
    SearchVoiceInputExperience(
        initialEditingTarget = initialEditingTarget,
        initialSelectionMode = initialSelectionMode,
        onNavigateBack = onNavigateBack,
        onNavigateToResults = onNavigateToResults,
    ) { uiState, onAction ->
        SearchScreen(
            destination = SearchScreenDestination.VoiceInput,
            uiState = uiState,
            onAction = onAction,
            modifier = modifier,
        )
    }
}

@Composable
private fun SearchRouteContent(
    destination: SearchScreenDestination,
    initialQuery: String?,
    initialEditingTarget: RouteEditingTarget,
    initialSelectionMode: SearchSelectionMode,
    preserveEntryStateOnReentry: Boolean = false,
    onNavigateBack: () -> Unit,
    onNavigateToResults: (String, RouteEditingTarget, SearchSelectionMode) -> Unit,
    onNavigateToVoiceInput: () -> Unit,
    onNavigateToRouteSetting: (Boolean) -> Unit,
    onNavigateToMapPreview: () -> Unit,
    onNavigateToRouteEndpointMapPicker: (RouteEditingTarget) -> Unit,
    onNavigateToRouteBriefing: () -> Unit = {},
    onStartVoiceCapture: () -> Unit = {},
    onStopVoiceCapture: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberSearchViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appContainer =
        remember(context.applicationContext) {
            (context.applicationContext as BusanEumgilApp).appContainer
        }
    val activity = remember(context) { context.findComponentActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.onAction(SearchUiAction.RefreshLocationPermission)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(viewModel, initialEditingTarget, initialSelectionMode) {
        viewModel.onAction(
            SearchUiAction.EditingTargetConfigured(
                editingTarget = initialEditingTarget,
                selectionMode = initialSelectionMode,
            ),
        )
    }

    LaunchedEffect(viewModel, destination, preserveEntryStateOnReentry) {
        if (destination == SearchScreenDestination.Entry) {
            viewModel.onAction(
                SearchUiAction.EntryRouteEntered(
                    preserveState = preserveEntryStateOnReentry,
                ),
            )
        }
    }

    LaunchedEffect(viewModel, initialQuery, initialSelectionMode) {
        if (initialQuery != null) {
            viewModel.onAction(
                SearchUiAction.ResultsRouteEntered(
                    query = initialQuery,
                    editingTarget = initialEditingTarget,
                    selectionMode = initialSelectionMode,
                ),
            )
        }
    }

    LaunchedEffect(
        viewModel,
        onNavigateBack,
        onNavigateToResults,
        onNavigateToVoiceInput,
        onNavigateToRouteSetting,
        onNavigateToMapPreview,
        onNavigateToRouteEndpointMapPicker,
        onNavigateToRouteBriefing,
        onStartVoiceCapture,
        onStopVoiceCapture,
        appContainer,
        activity,
    ) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                SearchUiEvent.NavigateBack -> onNavigateBack()
                SearchUiEvent.NavigateToVoiceInput -> onNavigateToVoiceInput()
                is SearchUiEvent.NavigateToResults ->
                    onNavigateToResults(event.query, event.editingTarget, event.selectionMode)
                SearchUiEvent.StartVoiceCapture -> onStartVoiceCapture()
                SearchUiEvent.StopVoiceCapture -> onStopVoiceCapture()
                is SearchUiEvent.NavigateToRouteSetting ->
                    onNavigateToRouteSetting(event.locationPermissionPrechecked)
                SearchUiEvent.NavigateToMapPreview -> onNavigateToMapPreview()
                is SearchUiEvent.NavigateToRouteEndpointMapPicker ->
                    onNavigateToRouteEndpointMapPicker(event.editingTarget)
                SearchUiEvent.NavigateToRouteBriefing -> onNavigateToRouteBriefing()
                SearchUiEvent.NavigateToLowVisionBookmark -> Unit
                SearchUiEvent.RequestLocationPermission ->
                    activity?.let(appContainer.locationPermissionManager::requestLocationPermission)
            }
        }
    }

    SearchScreen(
        destination = destination,
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

@Composable
internal fun SearchVoiceInputExperience(
    initialEditingTarget: RouteEditingTarget = RouteEditingTarget.DESTINATION,
    initialSelectionMode: SearchSelectionMode = SearchSelectionMode.PREVIEW_ON_MAP,
    onNavigateBack: () -> Unit,
    onNavigateToResults: (String, RouteEditingTarget, SearchSelectionMode) -> Unit,
    content: @Composable (SearchUiState, (SearchUiAction) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val searchViewModel = rememberSearchViewModel()
    val sttViewModel: SearchVoiceInputViewModel = viewModel()
    val uiState by searchViewModel.uiState.collectAsStateWithLifecycle()
    val ttsController =
        remember(context.applicationContext) {
            AndroidTextToSpeechController(context = context.applicationContext)
        }
    val ttsState by ttsController.state.collectAsStateWithLifecycle()
    val voiceInputPrompt = stringResource(R.string.voice_input_prompt)
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)
    val currentOnNavigateToResults by rememberUpdatedState(onNavigateToResults)
    val currentCompletedUtteranceCount by rememberUpdatedState(ttsState.completedUtteranceCount)
    val currentVoiceInputPrompt by rememberUpdatedState(voiceInputPrompt)

    LaunchedEffect(searchViewModel, initialEditingTarget, initialSelectionMode) {
        searchViewModel.onAction(
            SearchUiAction.EditingTargetConfigured(
                editingTarget = initialEditingTarget,
                selectionMode = initialSelectionMode,
            ),
        )
    }

    LaunchedEffect(searchViewModel, sttViewModel) {
        searchViewModel.onAction(SearchUiAction.VoiceRouteEntered)
        sttViewModel.startListening()
    }

    val lastCompletedCount = remember { mutableIntStateOf(-1) }

    LaunchedEffect(ttsState.completedUtteranceCount) {
        if (
            lastCompletedCount.intValue >= 0 &&
            ttsState.completedUtteranceCount > lastCompletedCount.intValue
        ) {
            delay(300)
            sttViewModel.beginRecording()
        }
        lastCompletedCount.intValue = ttsState.completedUtteranceCount
    }

    LaunchedEffect(searchViewModel, sttViewModel, ttsController) {
        sttViewModel.uiEvent.collect { event ->
            when (event) {
                is SearchVoiceInputEvent.TranscriptReady ->
                    searchViewModel.onAction(
                        SearchUiAction.VoiceTranscriptReceived(
                            transcript = event.recognizedText,
                            searchQuery = event.searchQuery,
                        ),
                    )

                SearchVoiceInputEvent.TranscriptEmpty ->
                    searchViewModel.onAction(SearchUiAction.VoiceCaptureEmpty)

                is SearchVoiceInputEvent.SpeakError -> ttsController.speak(event.text)
                SearchVoiceInputEvent.ReadyToRecord -> {
                    lastCompletedCount.intValue = currentCompletedUtteranceCount
                    ttsController.speak(currentVoiceInputPrompt)
                }
            }
        }
    }

    LaunchedEffect(searchViewModel, sttViewModel) {
        searchViewModel.uiEvent.collect { event ->
            when (event) {
                SearchUiEvent.NavigateBack -> {
                    sttViewModel.stopListening()
                    currentOnNavigateBack()
                }

                is SearchUiEvent.NavigateToResults -> {
                    sttViewModel.stopListening()
                    currentOnNavigateToResults(event.query, event.editingTarget, event.selectionMode)
                }

                SearchUiEvent.StartVoiceCapture -> sttViewModel.startListening()
                SearchUiEvent.StopVoiceCapture -> sttViewModel.stopListening()
                is SearchUiEvent.NavigateToRouteSetting -> Unit
                SearchUiEvent.NavigateToMapPreview -> Unit
                is SearchUiEvent.NavigateToRouteEndpointMapPicker -> Unit
                SearchUiEvent.NavigateToRouteBriefing -> Unit
                SearchUiEvent.NavigateToLowVisionBookmark -> Unit
                SearchUiEvent.NavigateToVoiceInput -> Unit
                SearchUiEvent.RequestLocationPermission -> Unit
            }
        }
    }

    DisposableEffect(sttViewModel, ttsController) {
        onDispose {
            sttViewModel.stopListening()
            ttsController.stop()
            ttsController.shutdown()
        }
    }

    content(uiState, searchViewModel::onAction)
}

@Composable
private fun rememberSearchViewModel(): SearchViewModel {
    val context = LocalContext.current
    val appContainer =
        remember(context.applicationContext) {
            (context.applicationContext as BusanEumgilApp).appContainer
        }
    val activity = remember(context) { context.findComponentActivity() }
    val viewModelFactory =
        remember(appContainer) {
            SearchViewModel.provideFactory(
                searchRepository = appContainer.searchRepository,
                bookmarkRepository = appContainer.bookmarkRepository,
                destinationSelectionRepository = appContainer.destinationSelectionRepository,
                destinationPreviewRepository = appContainer.destinationPreviewRepository,
                placesRepository = appContainer.placesRepository,
                currentLocationManager = appContainer.currentLocationManager,
                locationPermissionManager = appContainer.locationPermissionManager,
                currentLocationAddressResolver = appContainer.currentLocationAddressResolver,
            )
        }

    return remember(activity, viewModelFactory) {
        val owner = checkNotNull(activity) { "SearchRoute requires a ComponentActivity host." }
        ViewModelProvider(owner, viewModelFactory)[SearchViewModel::class.java]
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }
