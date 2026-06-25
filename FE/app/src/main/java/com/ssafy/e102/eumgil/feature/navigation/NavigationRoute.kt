package com.ssafy.e102.eumgil.feature.navigation

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.e102.eumgil.app.BusanEumgilApp
import com.ssafy.e102.eumgil.core.designsystem.component.dialog.EumDuribalCallConfirmDialog
import com.ssafy.e102.eumgil.core.designsystem.component.dialog.EumDuribalCallConfirmDismissStyle
import com.ssafy.e102.eumgil.core.external.createDuribalDialIntent
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.core.tts.AndroidTextToSpeechController
import com.ssafy.e102.eumgil.core.tts.TextToSpeechAvailability
import com.ssafy.e102.eumgil.data.repository.ReportRepository
import com.ssafy.e102.eumgil.feature.lowvision.LowVisionNavigationScreen
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun NavigationRoute(
    onNavigateBack: () -> Unit,
    onNavigateToRouteDetail: (RouteOption) -> Unit = {},
    onNavigateToReport: () -> Unit = {},
    onNavigateToMap: () -> Unit,
    onNavigateToSavedRoute: () -> Unit,
    onNavigateToArrival: () -> Unit,
    submittedHazardReportId: Long? = null,
    onSubmittedHazardReportConsumed: () -> Unit = {},
    useLowVisionUi: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val activity = remember(context) { context.findComponentActivity() }
    val textToSpeechController =
        remember(appContext) {
            AndroidTextToSpeechController(context = appContext)
        }
    val routeChangeAlertPlayer = remember { NavigationRouteChangeAlertPlayer() }
    val currentLocationManager = remember(appContext) {
        (appContext as BusanEumgilApp).appContainer.currentLocationManager
    }
    val currentHeadingManager = remember(appContext) {
        (appContext as BusanEumgilApp).appContainer.currentHeadingManager
    }
    val locationPermissionManager = remember(appContext) {
        (appContext as BusanEumgilApp).appContainer.locationPermissionManager
    }
    val bookmarkRepository = remember(appContext) {
        (appContext as BusanEumgilApp).appContainer.bookmarkRepository
    }
    val routeRepository = remember(appContext) {
        (appContext as BusanEumgilApp).appContainer.routeRepository
    }
    val reportRepository = remember(appContext) {
        (appContext as BusanEumgilApp).appContainer.reportRepository
    }
    val viewModelFactory =
        remember(currentLocationManager, currentHeadingManager, locationPermissionManager, bookmarkRepository, routeRepository, reportRepository, useLowVisionUi) {
            NavigationViewModel.provideFactory(
                currentLocationManager = currentLocationManager,
                currentHeadingManager = currentHeadingManager,
                locationPermissionManager = locationPermissionManager,
                bookmarkRepository = bookmarkRepository,
                routeRepository = routeRepository,
                reportRepository = reportRepository,
                isLowVisionMode = useLowVisionUi,
            )
        }
    val viewModel =
        remember(activity, viewModelFactory) {
            val owner = checkNotNull(activity) { "NavigationRoute requires a ComponentActivity host." }
            ViewModelProvider(owner, viewModelFactory)[NavigationViewModel::class.java]
        }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val textToSpeechState by textToSpeechController.state.collectAsStateWithLifecycle()
    var isDuribalConfirmDialogVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(textToSpeechState) {
        viewModel.updateTextToSpeechState(
            isEnabled = textToSpeechState.enabled,
            canSpeak = textToSpeechState.canSpeak,
            status = textToSpeechState.availability.toNavigationTtsStatus(),
        )
    }

    LaunchedEffect(textToSpeechController, uiState.tts.isEnabled) {
        textToSpeechController.setEnabled(uiState.tts.isEnabled)
    }

    LaunchedEffect(submittedHazardReportId, viewModel, onSubmittedHazardReportConsumed) {
        val reportId = submittedHazardReportId ?: return@LaunchedEffect
        viewModel.onAction(NavigationUiAction.HazardReportSubmitted(reportId))
        onSubmittedHazardReportConsumed()
    }

    BackHandler(
        enabled = !useLowVisionUi && !uiState.isExitConfirmDialogVisible,
    ) {
        viewModel.onAction(NavigationUiAction.BackClicked)
    }

    LaunchedEffect(
        viewModel,
        useLowVisionUi,
        onNavigateBack,
        onNavigateToRouteDetail,
        onNavigateToReport,
        onNavigateToMap,
        onNavigateToSavedRoute,
        onNavigateToArrival,
    ) {
        viewModel.setLowVisionMode(useLowVisionUi)
        launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.uiEvent.collect { event ->
                when (event) {
                    NavigationUiEvent.NavigateBack -> onNavigateBack()
                    is NavigationUiEvent.NavigateToRouteDetail -> onNavigateToRouteDetail(event.routeOption)
                    NavigationUiEvent.NavigateToReport -> onNavigateToReport()
                    NavigationUiEvent.NavigateToMap -> onNavigateToMap()
                    NavigationUiEvent.NavigateToSavedRoute -> onNavigateToSavedRoute()
                    NavigationUiEvent.NavigateToArrival -> onNavigateToArrival()
                    NavigationUiEvent.ShowDuribalCallDialog -> {
                        if (!useLowVisionUi) {
                            isDuribalConfirmDialogVisible = true
                        }
                    }
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
        viewModel.onAction(NavigationUiAction.NavigationEntered)
    }

    DisposableEffect(textToSpeechController) {
        onDispose {
            textToSpeechController.stop()
            textToSpeechController.shutdown()
            routeChangeAlertPlayer.release()
        }
    }

    if (useLowVisionUi) {
        LowVisionNavigationScreen(
            uiState = uiState,
            onAction = viewModel::onAction,
            modifier = modifier,
        )
    } else {
        NavigationScreen(
            uiState = uiState,
            reportRepository = reportRepository,
            onAction = viewModel::onAction,
            modifier = modifier,
        )
    }

    if (!useLowVisionUi && isDuribalConfirmDialogVisible) {
        EumDuribalCallConfirmDialog(
            onDismiss = { isDuribalConfirmDialogVisible = false },
            onConfirm = {
                isDuribalConfirmDialogVisible = false
                context.startActivity(createDuribalDialIntent())
            },
            dismissStyle = EumDuribalCallConfirmDismissStyle.TextButton,
        )
    }
}

private fun TextToSpeechAvailability.toNavigationTtsStatus(): NavigationTtsStatus =
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
