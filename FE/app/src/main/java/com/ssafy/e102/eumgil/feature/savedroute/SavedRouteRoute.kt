package com.ssafy.e102.eumgil.feature.savedroute

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.ssafy.e102.eumgil.app.BusanEumgilApp
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.feature.route.RouteNavigationRequest
import kotlinx.coroutines.flow.collect

@Composable
fun SavedRouteRoute(
    onNavigateToMap: () -> Unit,
    onNavigateToNavigation: (RouteNavigationRequest) -> Unit,
    onNavigateToRouteDetail: (RouteNavigationRequest) -> Unit,
    onNavigateToRouteSetting: (RouteOption?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContainer =
        remember(context.applicationContext) {
            (context.applicationContext as BusanEumgilApp).appContainer
        }
    val viewModelFactory =
        remember(appContainer) {
            SavedRouteViewModel.provideFactory(
                authSessionRepository = appContainer.authSessionRepository,
                bookmarkRepository = appContainer.bookmarkRepository,
                routeBookmarkRepository = appContainer.routeBookmarkRepository,
                destinationSelectionRepository = appContainer.destinationSelectionRepository,
                destinationPreviewRepository = appContainer.destinationPreviewRepository,
                searchRepository = appContainer.searchRepository,
                currentLocationManager = appContainer.currentLocationManager,
            )
        }
    val owner = checkNotNull(LocalViewModelStoreOwner.current) { "SavedRouteRoute requires a ViewModelStoreOwner." }
    val viewModel =
        remember(owner, viewModelFactory) {
            ViewModelProvider(owner, viewModelFactory)[SavedRouteViewModel::class.java]
        }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.setLowVisionMode(enabled = false)
    }

    LaunchedEffect(viewModel, onNavigateToMap, onNavigateToNavigation, onNavigateToRouteDetail, onNavigateToRouteSetting) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                SavedRouteUiEvent.NavigateToMap -> onNavigateToMap()
                is SavedRouteUiEvent.NavigateToNavigation -> onNavigateToNavigation(event.request)
                is SavedRouteUiEvent.NavigateToRouteDetail -> onNavigateToRouteDetail(event.request)
                is SavedRouteUiEvent.NavigateToRouteSetting -> onNavigateToRouteSetting(event.initialRouteOption)
                SavedRouteUiEvent.NavigateToRouteBriefing -> Unit
                is SavedRouteUiEvent.ShowSnackbar -> Unit
            }
        }
    }

    SavedRouteScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}
