package com.ssafy.e102.eumgil.feature.map

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.e102.eumgil.app.BusanEumgilApp
import com.ssafy.e102.eumgil.core.external.createDialIntent
import com.ssafy.e102.eumgil.core.permission.MICROPHONE_PERMISSION
import com.ssafy.e102.eumgil.core.permission.MicrophonePermissionState
import com.ssafy.e102.eumgil.core.permission.resolveMicrophonePermissionState
import com.ssafy.e102.eumgil.data.repository.RouteEditingTarget
import com.ssafy.e102.eumgil.feature.search.SearchSelectionMode
import com.ssafy.e102.eumgil.feature.search.SearchVoiceInputBottomSheet
import com.ssafy.e102.eumgil.feature.search.SearchVoiceInputExperience
import kotlinx.coroutines.flow.collect

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MapRoute(
    viewModelStoreOwner: ViewModelStoreOwner,
    onNavigateToSavedRoutes: () -> Unit,
    onNavigateToMyPage: () -> Unit,
    onNavigateToRouteSetting: (Boolean) -> Unit = {},
    onNavigateToSearch: (RouteEditingTarget, SearchSelectionMode) -> Unit = { _, _ -> },
    onNavigateToSearchResults: (String, RouteEditingTarget) -> Unit = { _, _ -> },
    routeEndpointMapPickerTarget: RouteEditingTarget? = null,
    onRouteEndpointMapPickerTargetConsumed: () -> Unit = {},
    shouldResetForHomeEntry: Boolean = false,
    onHomeReentryResetConsumed: () -> Unit = {},
    onFacilityDetailVisibilityChanged: (Boolean) -> Unit = {},
    facilityDetailDismissRequestId: Long = 0L,
    onFacilityDetailDismissRequestConsumed: (Long) -> Boolean = { false },
    onVoiceSearchVisibilityChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContainer =
        remember(context.applicationContext) {
            (context.applicationContext as BusanEumgilApp).appContainer
        }
    val activity = remember(context) { context.findComponentActivity() }
    val viewModelFactory =
        remember(appContainer) {
            MapViewModel.provideFactory(
                locationPermissionManager = appContainer.locationPermissionManager,
                currentLocationManager = appContainer.currentLocationManager,
                destinationSelectionRepository = appContainer.destinationSelectionRepository,
                destinationPreviewRepository = appContainer.destinationPreviewRepository,
                facilitySeedRepository = appContainer.facilitySeedRepository,
                bookmarkRepository = appContainer.bookmarkRepository,
                authSessionRepository = appContainer.authSessionRepository,
                searchRepository = appContainer.searchRepository,
                placesRepository = appContainer.placesRepository,
                approvedReportMapRepository = appContainer.approvedReportMapRepository,
            )
        }
    val viewModel =
        remember(viewModelStoreOwner, viewModelFactory) {
            ViewModelProvider(viewModelStoreOwner, viewModelFactory)[MapViewModel::class.java]
        }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val consumeHomeReentryReset by rememberUpdatedState(onHomeReentryResetConsumed)
    val micPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                viewModel.onAction(MapUiAction.VoiceSearchClicked)
            }
        }
    val openVoiceSearch =
        remember(context, micPermissionLauncher, viewModel) {
            {
                when (context.resolveMicrophonePermissionState()) {
                    MicrophonePermissionState.GRANTED ->
                        viewModel.onAction(MapUiAction.VoiceSearchClicked)

                    MicrophonePermissionState.DENIED ->
                        micPermissionLauncher.launch(MICROPHONE_PERMISSION)

                    MicrophonePermissionState.UNAVAILABLE -> Unit
                }
            }
        }

    LaunchedEffect(viewModel, shouldResetForHomeEntry) {
        if (!shouldResetForHomeEntry) return@LaunchedEffect

        viewModel.onHomeReentered()
        consumeHomeReentryReset()
    }

    LaunchedEffect(viewModel, routeEndpointMapPickerTarget) {
        val editingTarget = routeEndpointMapPickerTarget ?: return@LaunchedEffect

        viewModel.onAction(MapUiAction.RouteEndpointMapPickerEntered(editingTarget))
        onRouteEndpointMapPickerTargetConsumed()
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> viewModel.onRouteStarted()
                    Lifecycle.Event.ON_STOP -> viewModel.onRouteStopped()
                    else -> Unit
                }
            }

        lifecycle.addObserver(observer)
        if (shouldStartMapRouteImmediately(lifecycle.currentState)) {
            viewModel.onRouteStarted()
        }

        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.onRouteStopped()
        }
    }

    LaunchedEffect(
        viewModel,
        activity,
        appContainer,
        context,
        onNavigateToRouteSetting,
        onNavigateToSearch,
        onNavigateToSearchResults,
        snackbarHostState,
    ) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is MapUiEvent.NavigateToRouteSetting ->
                    onNavigateToRouteSetting(event.locationPermissionPrechecked)
                is MapUiEvent.NavigateToSearch -> onNavigateToSearch(event.editingTarget, event.selectionMode)
                is MapUiEvent.OpenDialer -> context.startActivity(createDialIntent(event.phoneNumber))
                MapUiEvent.RequestLocationPermission ->
                    activity?.let(appContainer.locationPermissionManager::requestLocationPermission)
                is MapUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    BackHandler(enabled = uiState.isVoiceSearchVisible) {
        viewModel.onAction(MapUiAction.VoiceSearchDismissed)
    }

    BackHandler(enabled = uiState.routeEndpointMapPickerState != null && uiState.isVoiceSearchVisible.not()) {
        viewModel.onAction(MapUiAction.RouteEndpointMapPickerDismissed)
    }

    BackHandler(
        enabled =
            uiState.approvedReportSheetState.isVisible &&
                uiState.routeEndpointMapPickerState == null &&
                uiState.isVoiceSearchVisible.not(),
    ) {
        viewModel.onAction(MapUiAction.ApprovedReportSheetDismissed)
    }

    BackHandler(
        enabled =
            uiState.facilityDetailSheetState.isVisible &&
                uiState.approvedReportSheetState.isVisible.not() &&
                uiState.routeEndpointMapPickerState == null &&
                uiState.isVoiceSearchVisible.not(),
    ) {
        viewModel.onAction(MapUiAction.FacilityDetailDismissed)
    }

    BackHandler(
        enabled =
            uiState.facilityDetailSheetState.isVisible.not() &&
                uiState.approvedReportSheetState.isVisible.not() &&
                uiState.routeEndpointMapPickerState == null &&
                uiState.isVoiceSearchVisible.not(),
    ) {
        if (activity?.moveTaskToBack(true) == false) {
            activity.finish()
        }
    }

    LaunchedEffect(
        uiState.facilityDetailSheetState.isVisible,
        uiState.routeEndpointMapPickerState,
        uiState.isVoiceSearchVisible,
        onFacilityDetailVisibilityChanged,
    ) {
        onFacilityDetailVisibilityChanged(
            uiState.facilityDetailSheetState.isVisible ||
                uiState.routeEndpointMapPickerState != null,
        )
    }

    LaunchedEffect(uiState.isVoiceSearchVisible, onVoiceSearchVisibilityChanged) {
        onVoiceSearchVisibilityChanged(uiState.isVoiceSearchVisible)
    }

    LaunchedEffect(
        facilityDetailDismissRequestId,
        onFacilityDetailDismissRequestConsumed,
        viewModel,
    ) {
        if (facilityDetailDismissRequestId <= 0L) return@LaunchedEffect
        if (!onFacilityDetailDismissRequestConsumed(facilityDetailDismissRequestId)) return@LaunchedEffect

        viewModel.onAction(MapUiAction.FacilityDetailDismissed)
        viewModel.onAction(MapUiAction.VoiceSearchDismissed)
    }

    DisposableEffect(onFacilityDetailVisibilityChanged, onVoiceSearchVisibilityChanged) {
        onDispose {
            onFacilityDetailVisibilityChanged(false)
            onVoiceSearchVisibilityChanged(false)
        }
    }

    MapScreen(
        uiState = uiState,
        reportRepository = appContainer.reportRepository,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
        onVoiceSearchClick = openVoiceSearch,
        onNavigateToSavedRoutes = onNavigateToSavedRoutes,
        onNavigateToMyPage = onNavigateToMyPage,
        modifier = modifier,
    )

    if (uiState.isVoiceSearchVisible) {
        SearchVoiceInputExperience(
            initialEditingTarget = RouteEditingTarget.DESTINATION,
            onNavigateBack = {
                viewModel.onAction(MapUiAction.VoiceSearchDismissed)
            },
            onNavigateToResults = { query, editingTarget, _ ->
                viewModel.onAction(MapUiAction.VoiceSearchDismissed)
                onNavigateToSearchResults(query, editingTarget)
            },
        ) { searchUiState, onSearchAction ->
            SearchVoiceInputBottomSheet(
                uiState = searchUiState,
                onAction = onSearchAction,
            )
        }
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }

internal fun shouldStartMapRouteImmediately(currentState: Lifecycle.State): Boolean =
    currentState.isAtLeast(Lifecycle.State.STARTED)
