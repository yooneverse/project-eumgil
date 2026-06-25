package com.ssafy.e102.eumgil.app.navigation

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ssafy.e102.eumgil.BuildConfig
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.app.BusanEumgilApp
import com.ssafy.e102.eumgil.core.config.AppEnvironment
import com.ssafy.e102.eumgil.core.designsystem.component.navigation.EumTopLevelTabBar
import com.ssafy.e102.eumgil.core.model.AuthGateState
import com.ssafy.e102.eumgil.core.model.InitSettings
import com.ssafy.e102.eumgil.core.permission.MICROPHONE_PERMISSION
import com.ssafy.e102.eumgil.core.permission.MicrophonePermissionState
import com.ssafy.e102.eumgil.core.permission.resolveMicrophonePermissionState
import com.ssafy.e102.eumgil.data.repository.provideProfileUserTypeUpdateRepository
import com.ssafy.e102.eumgil.feature.map.MapKwsEvent
import com.ssafy.e102.eumgil.feature.map.MapKwsViewModel
import com.ssafy.e102.eumgil.feature.onboarding.PrimaryUserType
import com.ssafy.e102.eumgil.feature.navigation.NavigationUiAction
import com.ssafy.e102.eumgil.feature.voiceassistant.CloseOverlay
import com.ssafy.e102.eumgil.feature.voiceassistant.DispatchAction
import com.ssafy.e102.eumgil.feature.voiceassistant.UiAction as VoiceAssistantUiAction
import com.ssafy.e102.eumgil.feature.voiceassistant.VoiceAssistantAction
import com.ssafy.e102.eumgil.feature.voiceassistant.VoiceAssistantContext
import com.ssafy.e102.eumgil.feature.voiceassistant.VoiceAssistantOverlay
import com.ssafy.e102.eumgil.feature.voiceassistant.VoiceAssistantUserType
import com.ssafy.e102.eumgil.feature.voiceassistant.VoiceAssistantViewModel

internal val AppNavHostContentWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AppNavHost(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val appContainer = remember(context) { (context as BusanEumgilApp).appContainer }
    val settingsRepository = remember(appContainer) { appContainer.settingsRepository }
    val authSessionRepository = remember(appContainer) { appContainer.authSessionRepository }
    val authSignupRepository = remember(appContainer) { appContainer.authSignupRepository }
    val profileUserTypeUpdateRepository =
        remember(authSessionRepository, settingsRepository) {
            provideProfileUserTypeUpdateRepository(
                baseUrl = AppEnvironment.baseUrl,
                authSessionRepository = authSessionRepository,
                settingsRepository = settingsRepository,
                isMockMode = AppEnvironment.isMockMode,
            )
        }
    var appStartDestination by remember { mutableStateOf<AppStartDestination?>(null) }
    var bootstrappedAuthGateState by remember { mutableStateOf<AuthGateState?>(null) }
    var bootstrappedInitSettings by remember { mutableStateOf<InitSettings?>(null) }

    LaunchedEffect(authSessionRepository, settingsRepository) {
        val authGateState = authSessionRepository.getAuthGateState()
        val savedSettings = settingsRepository.getInitSettings()
        bootstrappedAuthGateState = authGateState
        bootstrappedInitSettings = savedSettings
        appStartDestination =
            resolveAppStartDestination(
                authGateState = authGateState,
                initSettings = savedSettings,
                forceLowVisionTermsGuide = BuildConfig.FORCE_LOW_VISION_TERMS_GUIDE,
            )
    }

    if (appStartDestination == null || bootstrappedAuthGateState == null || bootstrappedInitSettings == null) {
        AppEntryLoadingScreen(modifier = modifier)
        return
    }

    val startDestination = appStartDestination ?: return
    val initialAuthGateState = bootstrappedAuthGateState ?: return
    val initialInitSettings = bootstrappedInitSettings ?: return
    val navController = rememberNavController()
    val voiceAssistantViewModelFactory =
        remember(appContainer) {
            VoiceAssistantViewModel.provideFactory(
                voiceAnalyzeRepository = appContainer.voiceAnalyzeRepository,
            )
        }
    val voiceAssistantViewModel: VoiceAssistantViewModel = viewModel(factory = voiceAssistantViewModelFactory)
    val navigationViewModel = rememberNavigationGuidanceViewModel()
    val voiceAssistantUiState by voiceAssistantViewModel.uiState.collectAsStateWithLifecycle()
    var voiceAssistantVisible by remember { mutableStateOf(false) }
    var voiceAssistantSourceContext by remember { mutableStateOf(VoiceAssistantContext()) }
    val authGateState by
        remember(authSessionRepository) {
            authSessionRepository.observeAuthGateState()
        }.collectAsStateWithLifecycle(initialValue = initialAuthGateState)
    val initSettings by
        remember(settingsRepository) {
            settingsRepository.observeInitSettings()
        }.collectAsStateWithLifecycle(initialValue = initialInitSettings)
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val currentTopLevelRoute = currentRoute.toCurrentTopLevelRoute()
    val isMapFacilityDetailVisible by
        currentBackStackEntry
            ?.savedStateHandle
            ?.getStateFlow(MAP_FACILITY_DETAIL_VISIBLE_KEY, false)
            ?.collectAsStateWithLifecycle()
            ?: remember { mutableStateOf(false) }
    val isMapVoiceSearchVisible by
        currentBackStackEntry
            ?.savedStateHandle
            ?.getStateFlow(MAP_VOICE_SEARCH_VISIBLE_KEY, false)
            ?.collectAsStateWithLifecycle()
            ?: remember { mutableStateOf(false) }
    val selectedPrimaryUserType = initSettings.selectedPrimaryUserType
    val mobilityKwsViewModel: MapKwsViewModel? =
        if (selectedPrimaryUserType == PrimaryUserType.MOBILITY_IMPAIRED.routeValue) {
            viewModel()
        } else {
            null
        }
    val currentVoiceAssistantSourceContext =
        VoiceAssistantContext(
            currentRoute = currentRoute,
            currentTopLevelRoute = currentTopLevelRoute,
            userType = selectedPrimaryUserType.toVoiceAssistantUserType(),
        )

    fun showVoiceAssistant(sourceContext: VoiceAssistantContext) {
        if (shouldRequestMapFacilityDetailDismissOnGlobalVoiceAssistantOpen(currentRoute)) {
            navController.currentBackStackEntry?.savedStateHandle?.requestMapFacilityDetailDismiss()
        }
        mobilityKwsViewModel?.enableAutoResume()
        voiceAssistantSourceContext = sourceContext
        voiceAssistantViewModel.onAction(VoiceAssistantUiAction.ContextChanged(sourceContext))
        voiceAssistantViewModel.onAction(VoiceAssistantUiAction.AssistantClicked)
        voiceAssistantVisible = true
    }

    val voiceAssistantPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                showVoiceAssistant(voiceAssistantSourceContext)
            }
        }

    val openVoiceAssistant: (VoiceAssistantContext) -> Unit = { sourceContext ->
        voiceAssistantSourceContext = sourceContext
        when (context.resolveMicrophonePermissionState()) {
            MicrophonePermissionState.GRANTED -> showVoiceAssistant(sourceContext)
            MicrophonePermissionState.DENIED -> voiceAssistantPermissionLauncher.launch(MICROPHONE_PERMISSION)
            MicrophonePermissionState.UNAVAILABLE -> Unit
        }
    }

    LaunchedEffect(voiceAssistantViewModel, navController) {
        voiceAssistantViewModel.uiEvent.collect { event ->
            when (event) {
                CloseOverlay -> voiceAssistantVisible = false
                is DispatchAction -> {
                    voiceAssistantVisible = false
                    when (val action = event.action) {
                        is VoiceAssistantAction.StopNavigation ->
                            navigationViewModel.onAction(NavigationUiAction.ConfirmExitNavigationClicked)
                        is VoiceAssistantAction.OpenReport -> {
                            navController.navigateByVoiceAssistantAction(action)
                            // savedStateHandle 패턴 — navigateToTopLevel 이후 진입점 데이터 전달
                            runCatching {
                                val reportEntry = navController.getBackStackEntry(ReportRoute.Report.route)
                                reportEntry.savedStateHandle[REPORT_VOICE_TYPE_KEY] = action.reportType
                                reportEntry.savedStateHandle[REPORT_VOICE_DESC_KEY] = action.description
                            }
                        }
                        else -> navController.navigateByVoiceAssistantAction(action)
                    }
                }
                else -> Unit
            }
        }
    }

    val showTopLevelBar =
        currentTopLevelRoute != null &&
            !isMapFacilityDetailVisible &&
            !isMapVoiceSearchVisible

    LaunchedEffect(navController, authGateState, currentRoute) {
        if (authGateState.hasSession || authGateState.hasPendingSignup || currentRoute == null) return@LaunchedEffect
        if (currentRoute == AuthRoute.Login.route) return@LaunchedEffect

        navController.navigate(AuthRoute.Login.route) {
            launchSingleTop = true
            popUpTo(navController.graph.findStartDestination().id) {
                inclusive = true
            }
        }
    }

    LaunchedEffect(isMapVoiceSearchVisible, mobilityKwsViewModel) {
        if (isMapVoiceSearchVisible) {
            mobilityKwsViewModel?.enableAutoResume()
        }
    }

    if (selectedPrimaryUserType == PrimaryUserType.MOBILITY_IMPAIRED.routeValue) {
        mobilityKwsViewModel?.let { kwsViewModel ->
            MobilityKwsEffect(
                kwsViewModel = kwsViewModel,
                navController = navController,
                shouldPauseForMapVoiceInput = isMapVoiceSearchVisible,
                voiceAssistantVisible = voiceAssistantVisible,
                onOpenVoiceAssistant = { openVoiceAssistant(currentVoiceAssistantSourceContext) },
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = AppNavHostContentWindowInsets,
            bottomBar = {
                if (showTopLevelBar) {
                    EumTopLevelTabBar(
                        destinations = TopLevelDestination.entries,
                        currentRoute = currentTopLevelRoute,
                        onDestinationSelected = { destination ->
                            if (
                                shouldNavigateToTopLevelMapForHomeEntry(
                                    currentRoute = currentRoute,
                                    destination = destination,
                                )
                            ) {
                                Log.i(
                                    APP_NAV_HOST_LOG_TAG,
                                    "Map home tab selected from aliased route=$currentRoute; forcing home reentry reset",
                                )
                                navController.navigateToTopLevelMapForHomeEntry()
                            } else {
                                navController.navigateToTopLevel(destination)
                            }
                        },
                    )
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = startDestination.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = { appEnterTransition() },
                exitTransition = { appExitTransition() },
                popEnterTransition = { appEnterTransition() },
                popExitTransition = { appExitTransition() },
            ) {
                authNavGraph(
                    navController = navController,
                    authSessionRepository = authSessionRepository,
                    settingsRepository = settingsRepository,
                )
                onboardingNavGraph(
                    navController = navController,
                    settingsRepository = settingsRepository,
                    authSignupRepository = authSignupRepository,
                    profileUserTypeUpdateRepository = profileUserTypeUpdateRepository,
                )
                lowVisionNavGraph(navController = navController)
                mainNavGraph(
                    navController = navController,
                    onOpenVoiceAssistant = { editingTarget ->
                        openVoiceAssistant(currentVoiceAssistantSourceContext.copy(editingTarget = editingTarget))
                    },
                )
            }
        }

        VoiceAssistantOverlay(
            uiState = voiceAssistantUiState,
            onAction = { action ->
                if (action == VoiceAssistantUiAction.Dismissed) {
                    voiceAssistantVisible = false
                }
                voiceAssistantViewModel.onAction(action)
            },
            visible = voiceAssistantVisible,
        )
    }
}

internal fun String?.toCurrentTopLevelRoute(): String? =
    when {
        this == TopLevelRoute.Map.route -> TopLevelRoute.Map.route
        this == TopLevelRoute.SavedRoute.route -> TopLevelRoute.SavedRoute.route
        this == ReportRoute.Report.route -> ReportRoute.Report.route
        this == ReportRoute.Guidance.route -> null
        this == TopLevelRoute.MyPage.route -> TopLevelRoute.MyPage.route
        this?.startsWith("${TopLevelRoute.MyPage.route}/") == true -> TopLevelRoute.MyPage.route
        this == SearchRoute.Entry.route -> TopLevelRoute.Map.route
        this?.startsWith("search/") == true -> TopLevelRoute.Map.route
        this == NavigationRoute.Guidance.route -> null
        this == ArrivalRoute.Entry.route -> null
        this?.startsWith("route_setting") == true -> null
        else -> null
    }

internal fun shouldNavigateToTopLevelMapForHomeEntry(
    currentRoute: String?,
    destination: TopLevelDestination,
): Boolean =
    destination == TopLevelDestination.Map &&
        currentRoute != TopLevelRoute.Map.route &&
        currentRoute != TopLevelRoute.SavedRoute.route &&
        currentRoute.toCurrentTopLevelRoute() != null

internal fun shouldRequestMapFacilityDetailDismissOnGlobalVoiceAssistantOpen(currentRoute: String?): Boolean =
    currentRoute == TopLevelRoute.Map.route

internal sealed interface VoiceAssistantNavigationRequest {
    data class TopLevel(
        val destination: TopLevelDestination,
    ) : VoiceAssistantNavigationRequest

    data object MapHomeEntry : VoiceAssistantNavigationRequest

    data class Route(
        val route: String,
    ) : VoiceAssistantNavigationRequest
}

internal fun VoiceAssistantAction.toNavigationRequest(): VoiceAssistantNavigationRequest? =
    when (this) {
        is VoiceAssistantAction.OpenReport ->
            VoiceAssistantNavigationRequest.TopLevel(TopLevelDestination.Report)

        is VoiceAssistantAction.OpenSavedRoutes ->
            VoiceAssistantNavigationRequest.TopLevel(TopLevelDestination.SavedRoute)

        is VoiceAssistantAction.OpenMyPage ->
            VoiceAssistantNavigationRequest.TopLevel(TopLevelDestination.MyPage)

        is VoiceAssistantAction.OpenMap ->
            VoiceAssistantNavigationRequest.MapHomeEntry

        is VoiceAssistantAction.SearchPlace ->
            VoiceAssistantNavigationRequest.Route(SearchRoute.Results.createRoute(query, editingTarget))

        is VoiceAssistantAction.CategorySearch ->
            // TODO: 이동약자용 카테고리 결과 화면 route 확인 후 연결. 현재 검색 결과 화면으로 임시 처리.
            VoiceAssistantNavigationRequest.Route(SearchRoute.Results.createRoute(category))

        is VoiceAssistantAction.Navigate ->
            // TODO: 현재 GPS 위치 기반 경로 안내 구현. departure가 null이면 현재 GPS 사용.
            // destination으로 GET /places/search → 좌표 → 경로 안내 화면으로 연결 필요.
            // 현재 검색 결과 화면으로 임시 처리.
            VoiceAssistantNavigationRequest.Route(SearchRoute.Results.createRoute(destination))

        is VoiceAssistantAction.ShowBookmarks ->
            VoiceAssistantNavigationRequest.TopLevel(TopLevelDestination.SavedRoute)

        is VoiceAssistantAction.Logout ->
            VoiceAssistantNavigationRequest.Route(AuthRoute.Login.route)

        is VoiceAssistantAction.Ask,
        is VoiceAssistantAction.ResumeNavigationGuidance,
        is VoiceAssistantAction.StopNavigation,
        is VoiceAssistantAction.UnknownCommand,
        -> null
    }

internal fun shouldUseInstantAppDestinationTransitions(): Boolean = true

private fun appEnterTransition(): EnterTransition = EnterTransition.None

private fun appExitTransition(): ExitTransition = ExitTransition.None

private const val APP_NAV_HOST_LOG_TAG = "AppNavHost"
internal const val MAP_FACILITY_DETAIL_VISIBLE_KEY: String = "mapFacilityDetailVisible"

internal fun isLowVisionRoute(currentRoute: String?): Boolean =
    currentRoute?.startsWith("low_vision/") == true

internal fun shouldPauseMapKws(
    currentRoute: String?,
    shouldPauseForMapVoiceInput: Boolean,
    voiceAssistantVisible: Boolean,
): Boolean =
    voiceAssistantVisible ||
        currentRoute == SearchRoute.VoiceInput.route ||
        isLowVisionRoute(currentRoute) ||
        shouldPauseForMapVoiceInput

internal fun shouldAutoResumeMobilityKws(
    autoResumeEnabled: Boolean,
    currentRoute: String?,
    shouldPauseForMapVoiceInput: Boolean,
    voiceAssistantVisible: Boolean,
): Boolean =
    autoResumeEnabled &&
        !shouldPauseMapKws(
            currentRoute = currentRoute,
            shouldPauseForMapVoiceInput = shouldPauseForMapVoiceInput,
            voiceAssistantVisible = voiceAssistantVisible,
        )

@Composable
private fun MobilityKwsEffect(
    kwsViewModel: MapKwsViewModel,
    navController: NavController,
    shouldPauseForMapVoiceInput: Boolean,
    voiceAssistantVisible: Boolean,
    onOpenVoiceAssistant: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnOpenVoiceAssistant by rememberUpdatedState(onOpenVoiceAssistant)
    val currentShouldPauseForMapVoiceInput by rememberUpdatedState(shouldPauseForMapVoiceInput)
    val currentVoiceAssistantVisible by rememberUpdatedState(voiceAssistantVisible)

    DisposableEffect(lifecycleOwner, kwsViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME ->
                    if (
                        shouldAutoResumeMobilityKws(
                            autoResumeEnabled = kwsViewModel.isAutoResumeEnabled(),
                            currentRoute = navController.currentBackStackEntry?.destination?.route,
                            shouldPauseForMapVoiceInput = currentShouldPauseForMapVoiceInput,
                            voiceAssistantVisible = currentVoiceAssistantVisible,
                        )
                    ) {
                        kwsViewModel.resumeSpotting()
                    } else {
                        kwsViewModel.pauseSpotting()
                    }

                Lifecycle.Event.ON_PAUSE -> kwsViewModel.pauseSpotting()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(navController, kwsViewModel, shouldPauseForMapVoiceInput, voiceAssistantVisible) {
        if (
            shouldAutoResumeMobilityKws(
                autoResumeEnabled = kwsViewModel.isAutoResumeEnabled(),
                currentRoute = navController.currentBackStackEntry?.destination?.route,
                shouldPauseForMapVoiceInput = shouldPauseForMapVoiceInput,
                voiceAssistantVisible = voiceAssistantVisible,
            )
        ) {
            kwsViewModel.resumeSpotting()
        } else {
            kwsViewModel.pauseSpotting()
        }
    }

    LaunchedEffect(navController, shouldPauseForMapVoiceInput, voiceAssistantVisible) {
        navController.currentBackStackEntryFlow.collect { entry ->
            if (
                shouldAutoResumeMobilityKws(
                    autoResumeEnabled = kwsViewModel.isAutoResumeEnabled(),
                    currentRoute = entry.destination.route,
                    shouldPauseForMapVoiceInput = shouldPauseForMapVoiceInput,
                    voiceAssistantVisible = voiceAssistantVisible,
                )
            ) {
                kwsViewModel.resumeSpotting()
            } else {
                kwsViewModel.pauseSpotting()
            }
        }
    }

    LaunchedEffect(kwsViewModel) {
        kwsViewModel.uiEvent.collect { event ->
            when (event) {
                MapKwsEvent.OpenVoiceAssistant -> currentOnOpenVoiceAssistant()
            }
        }
    }
}

private fun String?.toVoiceAssistantUserType(): VoiceAssistantUserType? =
    when (this) {
        PrimaryUserType.MOBILITY_IMPAIRED.routeValue -> VoiceAssistantUserType.MOBILITY_IMPAIRED
        null -> null
        else -> VoiceAssistantUserType.GENERAL
    }

private fun NavController.navigateByVoiceAssistantAction(action: VoiceAssistantAction) {
    when (val request = action.toNavigationRequest()) {
        is VoiceAssistantNavigationRequest.TopLevel -> navigateToTopLevel(request.destination)
        VoiceAssistantNavigationRequest.MapHomeEntry -> navigateToTopLevelMapForHomeEntry()
        is VoiceAssistantNavigationRequest.Route -> navigate(request.route)
        null ->
            Log.i(
                APP_NAV_HOST_LOG_TAG,
                "VoiceAssistant action has no AppNavHost navigation mapping: ${action.javaClass.simpleName}",
            )
    }
}

@Composable
private fun AppEntryLoadingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val splashImage =
        remember(context) {
            loadSplashIllustrationBitmap(context.applicationContext)
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        if (splashImage != null) {
            Image(
                bitmap = splashImage,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(id = R.string.app_entry_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun loadSplashIllustrationBitmap(context: Context): ImageBitmap? =
    runCatching {
        BitmapFactory
            .decodeResource(context.resources, R.drawable.splash_illustration)
            ?.asImageBitmap()
    }.getOrNull()
