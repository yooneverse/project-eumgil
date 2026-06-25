package com.ssafy.e102.eumgil.feature.lowvision

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.e102.eumgil.app.BusanEumgilApp
import com.ssafy.e102.eumgil.core.location.CurrentLocationManager
import com.ssafy.e102.eumgil.core.location.LocationSnapshot
import com.ssafy.e102.eumgil.core.tts.AndroidTextToSpeechController
import com.ssafy.e102.eumgil.core.tts.TextToSpeechAvailability
import com.ssafy.e102.eumgil.feature.navigation.NavigationTtsStatus
import com.ssafy.e102.eumgil.feature.navigation.NavigationRouteChangeAlertPlayer
import com.ssafy.e102.eumgil.feature.navigation.NavigationUiAction
import com.ssafy.e102.eumgil.feature.navigation.NavigationUiEvent
import com.ssafy.e102.eumgil.feature.navigation.NavigationViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal const val LOW_VISION_NAVIGATION_LOCATION_REQUIRED_MESSAGE: String =
    "현재 위치를 확인한 뒤 길 안내를 시작할게요."

@Composable
fun LowVisionNavigationRoute(
    onNavigateToComplete: () -> Unit,
    onNavigateToBookmark: () -> Unit,
    onTabSelected: (LowVisionBottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val appContainer =
        remember(appContext) {
            (appContext as BusanEumgilApp).appContainer
        }
    val activity = remember(context) { context.findComponentActivity() }
    val viewModelFactory =
        remember(
            appContainer.currentLocationManager,
            appContainer.currentHeadingManager,
            appContainer.locationPermissionManager,
            appContainer.bookmarkRepository,
            appContainer.routeRepository,
        ) {
            NavigationViewModel.provideFactory(
                currentLocationManager = appContainer.currentLocationManager,
                currentHeadingManager = appContainer.currentHeadingManager,
                locationPermissionManager = appContainer.locationPermissionManager,
                bookmarkRepository = appContainer.bookmarkRepository,
                routeRepository = appContainer.routeRepository,
                reportRepository = appContainer.reportRepository,
                isLowVisionMode = true,
            )
        }
    val viewModel =
        remember(activity, viewModelFactory) {
            val owner = checkNotNull(activity) { "LowVisionNavigationRoute requires a ComponentActivity host." }
            ViewModelProvider(owner, viewModelFactory)[NavigationViewModel::class.java]
        }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedDestination by
        appContainer.destinationSelectionRepository.selectedDestination.collectAsStateWithLifecycle()
    val currentLocationSnapshot by
        appContainer.currentLocationManager.latestLocation.collectAsStateWithLifecycle()
    var loadErrorMessage by remember { mutableStateOf<String?>(null) }
    val textToSpeechController =
        remember(appContext) {
            AndroidTextToSpeechController(context = appContext)
        }
    val routeChangeAlertPlayer = remember { NavigationRouteChangeAlertPlayer() }
    val textToSpeechState by textToSpeechController.state.collectAsStateWithLifecycle()
    LaunchedEffect(textToSpeechState) {
        viewModel.updateTextToSpeechState(
            isEnabled = textToSpeechState.enabled,
            canSpeak = textToSpeechState.canSpeak,
            status = textToSpeechState.availability.toLowVisionNavigationTtsStatus(),
        )
    }

    LaunchedEffect(textToSpeechController, uiState.tts.isEnabled) {
        textToSpeechController.setEnabled(uiState.tts.isEnabled)
    }

    LaunchedEffect(viewModel, onNavigateToComplete, onNavigateToBookmark) {
        launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.uiEvent.collect { event ->
                when (event) {
                    NavigationUiEvent.NavigateBack -> Unit
                    is NavigationUiEvent.NavigateToRouteDetail -> Unit
                    NavigationUiEvent.NavigateToReport -> Unit
                    NavigationUiEvent.NavigateToMap,
                    NavigationUiEvent.NavigateToArrival,
                        -> onNavigateToComplete()

                    NavigationUiEvent.NavigateToSavedRoute -> onNavigateToBookmark()
                    NavigationUiEvent.ShowDuribalCallDialog -> Unit
                    is NavigationUiEvent.ShowToast ->
                        Toast.makeText(appContext, event.message, Toast.LENGTH_SHORT).show()
                    is NavigationUiEvent.SpeakBriefing -> textToSpeechController.speak(event.text)
                    NavigationUiEvent.PlayRouteChangeAlert -> routeChangeAlertPlayer.play()
                    NavigationUiEvent.StopBriefing -> textToSpeechController.stop()
                    is NavigationUiEvent.SetVoiceGuidanceEnabled ->
                        textToSpeechController.setEnabled(event.enabled)
                }
            }
        }
    }

    LaunchedEffect(selectedDestination) {
        loadErrorMessage = null
        viewModel.setLowVisionMode(enabled = true)
        appContainer.currentLocationManager.startLocationUpdates()
        appContainer.currentHeadingManager.startHeadingUpdates()
        appContainer.currentLocationManager.refreshLatestLocation()
        val origin =
            awaitLowVisionOriginSnapshot(
                currentLocationManager = appContainer.currentLocationManager,
                immediateSnapshot = currentLocationSnapshot,
            ).toLowVisionRouteOriginWaypointOrNull()
        if (origin == null) {
            loadErrorMessage = LOW_VISION_NAVIGATION_LOCATION_REQUIRED_MESSAGE
        } else {
            val request =
                appContainer.routeRepository
                    .buildLowVisionNavigationRequest(
                        destinationSelectionRepository = appContainer.destinationSelectionRepository,
                        origin = origin,
                    )
            if (request == null) {
                loadErrorMessage = LOW_VISION_NAVIGATION_LOAD_ERROR_MESSAGE
            } else {
                viewModel.bindNavigationRequest(request)
                viewModel.onAction(NavigationUiAction.NavigationEntered)
            }
        }
    }

    DisposableEffect(textToSpeechController) {
        onDispose {
            viewModel.setLowVisionMode(enabled = false)
            appContainer.currentLocationManager.stopLocationUpdates()
            appContainer.currentHeadingManager.stopHeadingUpdates()
            textToSpeechController.stop()
            textToSpeechController.shutdown()
            routeChangeAlertPlayer.release()
        }
    }

    LowVisionFontTheme {
        LowVisionNavigationScreen(
            uiState = uiState,
            onAction = viewModel::onAction,
            onTabSelected = onTabSelected,
            modifier = modifier,
            loadErrorMessage = loadErrorMessage,
        )
    }
}

internal fun shouldNavigateLowVisionHome(event: NavigationUiEvent): Boolean =
    event == NavigationUiEvent.NavigateToMap || event == NavigationUiEvent.NavigateToArrival

private fun TextToSpeechAvailability.toLowVisionNavigationTtsStatus(): NavigationTtsStatus =
    when (this) {
        TextToSpeechAvailability.Initializing -> NavigationTtsStatus.Initializing
        TextToSpeechAvailability.Ready -> NavigationTtsStatus.Ready
        TextToSpeechAvailability.Unavailable -> NavigationTtsStatus.Unavailable
    }

private tailrec fun Context.findComponentActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }

internal suspend fun awaitLowVisionOriginSnapshot(
    currentLocationManager: CurrentLocationManager,
    immediateSnapshot: LocationSnapshot?,
): LocationSnapshot? {
    if (immediateSnapshot != null) return immediateSnapshot
    return withTimeoutOrNull(1_500L) {
        currentLocationManager.latestLocation.filterNotNull().first()
    } ?: currentLocationManager.latestLocation.value
}
