package com.ssafy.e102.eumgil.feature.lowvision

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.e102.eumgil.app.BusanEumgilApp
import com.ssafy.e102.eumgil.core.tts.AndroidTextToSpeechController
import com.ssafy.e102.eumgil.core.tts.ROUTE_BRIEFING_TTS_SPEECH_RATE

@Composable
fun LowVisionRouteBriefingRoute(
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
        remember(appContainer) {
            LowVisionRouteBriefingViewModel.provideFactory(
                routeRepository = appContainer.routeRepository,
                destinationSelectionRepository = appContainer.destinationSelectionRepository,
            )
        }
    val viewModel =
        remember(activity, viewModelFactory) {
            val owner = checkNotNull(activity) { "LowVisionRouteBriefingRoute requires a ComponentActivity host." }
            ViewModelProvider(owner, viewModelFactory)[LowVisionRouteBriefingViewModel::class.java]
        }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedDestination by
        appContainer.destinationSelectionRepository.selectedDestination.collectAsStateWithLifecycle()
    val ttsController =
        remember(appContext) {
            AndroidTextToSpeechController(
                context = appContext,
                speechRate = ROUTE_BRIEFING_TTS_SPEECH_RATE,
            )
        }
    val ttsState by ttsController.state.collectAsStateWithLifecycle()
    var playbackActive by rememberSaveable { mutableStateOf(false) }
    var playbackToken by rememberSaveable { mutableIntStateOf(ttsState.completedUtteranceCount) }
    var visibleStepStartIndex by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(viewModel, selectedDestination) {
        appContainer.currentLocationManager.startLocationUpdates()
        appContainer.currentLocationManager.refreshLatestLocation()
        val originSnapshot =
            awaitLowVisionOriginSnapshot(
                currentLocationManager = appContainer.currentLocationManager,
                immediateSnapshot = appContainer.currentLocationManager.latestLocation.value,
            )
        val origin = originSnapshot.toLowVisionRouteOriginWaypointOrNull()
        if (origin == null) {
            viewModel.showLocationRequired()
        } else {
            viewModel.loadBriefing(origin = origin)
        }
    }

    LaunchedEffect(uiState.steps) {
        visibleStepStartIndex = 0
        if (uiState.steps.isEmpty()) {
            playbackActive = false
        }
    }

    LaunchedEffect(ttsState.completedUtteranceCount) {
        if (playbackActive && ttsState.completedUtteranceCount > playbackToken) {
            val nextStartIndex = uiState.steps.nextBriefingWindowStart(visibleStepStartIndex)
            if (nextStartIndex == null) {
                playbackActive = false
            } else {
                visibleStepStartIndex = nextStartIndex
                playbackToken = ttsState.completedUtteranceCount
                ttsController.speak(uiState.steps.briefingSpeechTextFrom(nextStartIndex))
            }
        }
    }

    DisposableEffect(ttsController) {
        onDispose {
            ttsController.stop()
            ttsController.shutdown()
        }
    }

    LowVisionFontTheme {
        LowVisionRouteBriefingScreen(
            uiState = uiState,
            visibleSteps = uiState.steps.visibleBriefingSteps(visibleStepStartIndex),
            isPlaying = playbackActive,
            onPlaybackClick = {
                if (playbackActive) {
                    playbackActive = false
                    ttsController.stop()
                } else {
                    visibleStepStartIndex = 0
                    playbackToken = ttsState.completedUtteranceCount
                    playbackActive = true
                    ttsController.speak(uiState.steps.briefingSpeechTextFrom(0))
                }
            },
            onTabSelected = onTabSelected,
            modifier = modifier,
        )
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }
