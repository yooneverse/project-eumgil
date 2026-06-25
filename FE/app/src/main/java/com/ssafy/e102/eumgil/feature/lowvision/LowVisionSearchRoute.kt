package com.ssafy.e102.eumgil.feature.lowvision

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.e102.eumgil.app.BusanEumgilApp
import com.ssafy.e102.eumgil.core.model.SearchSortOption
import com.ssafy.e102.eumgil.data.repository.RouteEditingTarget
import com.ssafy.e102.eumgil.feature.search.SearchUiAction
import com.ssafy.e102.eumgil.feature.search.SearchUiEvent
import com.ssafy.e102.eumgil.feature.search.SearchViewModel
import kotlinx.coroutines.flow.collect

@Composable
fun LowVisionSearchRoute(
    initialQuery: String,
    onNavigateBack: () -> Unit,
    onNavigateToRouteSetting: () -> Unit,
    onNavigateToRouteBriefing: () -> Unit,
    onNavigateToBookmark: () -> Unit,
    modifier: Modifier = Modifier,
    categoryLabel: String? = null,
) {
    val context = LocalContext.current
    val appContainer =
        remember(context.applicationContext) {
            (context.applicationContext as BusanEumgilApp).appContainer
        }
    val lowVisionSearchRepository =
        remember(appContainer.searchRepository, appContainer.placesRepository, appContainer.currentLocationManager) {
            LowVisionSearchRepository(
                delegate = appContainer.searchRepository,
                placesRepository = appContainer.placesRepository,
                currentLocationProvider = { appContainer.currentLocationManager.latestLocation.value },
            )
        }
    val activity = remember(context) { context.findComponentActivity() }
    val viewModelFactory =
        remember(appContainer, lowVisionSearchRepository) {
            SearchViewModel.provideFactory(
                searchRepository = lowVisionSearchRepository,
                bookmarkRepository = appContainer.bookmarkRepository,
                destinationSelectionRepository = appContainer.destinationSelectionRepository,
                destinationPreviewRepository = appContainer.destinationPreviewRepository,
                placesRepository = appContainer.placesRepository,
                currentLocationManager = appContainer.currentLocationManager,
            )
        }
    val viewModel =
        remember(activity, viewModelFactory) {
            val owner = checkNotNull(activity) { "LowVisionSearchRoute requires a ComponentActivity host." }
            ViewModelProvider(owner, viewModelFactory).get(LOW_VISION_SEARCH_VIEW_MODEL_KEY, SearchViewModel::class.java)
        }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(appContainer.currentLocationManager) {
        appContainer.currentLocationManager.startLocationUpdates()
        appContainer.currentLocationManager.refreshLatestLocation()
    }

    LaunchedEffect(viewModel, initialQuery, categoryLabel) {
        viewModel.onAction(SearchUiAction.EditingTargetConfigured(editingTarget = RouteEditingTarget.DESTINATION))
        if (!categoryLabel.isNullOrBlank()) {
            viewModel.onAction(SearchUiAction.SortOptionSelected(sortOption = SearchSortOption.DISTANCE))
        }
        viewModel.onAction(SearchUiAction.ResultsRouteEntered(query = initialQuery))
    }

    LaunchedEffect(viewModel, onNavigateBack, onNavigateToRouteSetting) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                SearchUiEvent.NavigateBack -> onNavigateBack()
                is SearchUiEvent.NavigateToResults -> Unit
                is SearchUiEvent.NavigateToRouteSetting -> onNavigateToRouteSetting()
                SearchUiEvent.NavigateToRouteBriefing -> onNavigateToRouteBriefing()
                SearchUiEvent.NavigateToMapPreview -> Unit
                is SearchUiEvent.NavigateToRouteEndpointMapPicker -> Unit
                SearchUiEvent.NavigateToLowVisionBookmark -> onNavigateToBookmark()
                SearchUiEvent.NavigateToVoiceInput -> Unit
                SearchUiEvent.StartVoiceCapture -> Unit
                SearchUiEvent.StopVoiceCapture -> Unit
                SearchUiEvent.RequestLocationPermission -> Unit
            }
        }
    }

    LowVisionFontTheme {
        LowVisionSearchScreen(
            uiState = uiState,
            onAction = viewModel::onAction,
            modifier = modifier,
            categoryLabel = categoryLabel,
        )
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }

private const val LOW_VISION_SEARCH_VIEW_MODEL_KEY: String = "low-vision-search-view-model"
