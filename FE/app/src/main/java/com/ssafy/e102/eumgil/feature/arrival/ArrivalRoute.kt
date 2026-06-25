package com.ssafy.e102.eumgil.feature.arrival

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ssafy.e102.eumgil.app.BusanEumgilApp
import com.ssafy.e102.eumgil.feature.navigation.NavigationViewModel

@Composable
fun ArrivalRoute(
    onNavigateToMap: () -> Unit,
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContainer =
        remember(context.applicationContext) {
            (context.applicationContext as BusanEumgilApp).appContainer
        }
    val navigationViewModel = rememberNavigationGuidanceViewModel()
    val viewModelFactory =
        remember(appContainer, navigationViewModel) {
            ArrivalViewModel.provideFactory(
                routeBookmarkRepository = appContainer.routeBookmarkRepository,
                routeRepository = appContainer.routeRepository,
                currentRouteBookmarkDraft = navigationViewModel.currentRouteBookmarkDraft(),
                currentRatingSessionId = navigationViewModel.currentRatingSessionId(),
            )
        }
    val viewModel: ArrivalViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler {
        viewModel.onAction(ArrivalUiAction.HomeClicked)
    }

    LaunchedEffect(viewModel, onNavigateToMap, onNavigateToSearch) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                ArrivalUiEvent.NavigateToMap -> onNavigateToMap()
                ArrivalUiEvent.NavigateToSearch -> onNavigateToSearch()
                is ArrivalUiEvent.ShowToast ->
                    Toast.makeText(context.applicationContext, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    ArrivalScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

@Composable
private fun rememberNavigationGuidanceViewModel(): NavigationViewModel {
    val context = LocalContext.current
    val appContainer =
        remember(context.applicationContext) {
            (context.applicationContext as BusanEumgilApp).appContainer
        }
    val activity = remember(context) { context.findComponentActivity() }
    val navigationViewModelFactory =
        remember(appContainer) {
            NavigationViewModel.provideFactory(
                currentLocationManager = appContainer.currentLocationManager,
                currentHeadingManager = appContainer.currentHeadingManager,
                bookmarkRepository = appContainer.bookmarkRepository,
                routeRepository = appContainer.routeRepository,
                reportRepository = appContainer.reportRepository,
            )
        }

    return remember(activity, navigationViewModelFactory) {
        val owner = checkNotNull(activity) { "ArrivalRoute requires a ComponentActivity host." }
        ViewModelProvider(owner, navigationViewModelFactory)[NavigationViewModel::class.java]
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }
