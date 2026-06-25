package com.ssafy.e102.eumgil.app.navigation

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ssafy.e102.eumgil.app.BusanEumgilApp
import com.ssafy.e102.eumgil.core.location.LocationPermissionState
import com.ssafy.e102.eumgil.core.location.locationPermissions
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.core.permission.MICROPHONE_PERMISSION
import com.ssafy.e102.eumgil.core.permission.MicrophonePermissionState
import com.ssafy.e102.eumgil.core.permission.resolveMicrophonePermissionState
import com.ssafy.e102.eumgil.data.repository.RouteEditingTarget
import com.ssafy.e102.eumgil.feature.arrival.ArrivalRoute as ArrivalScreenRoute
import com.ssafy.e102.eumgil.feature.map.MapRoute
import com.ssafy.e102.eumgil.feature.mypage.MyPageRoute
import com.ssafy.e102.eumgil.feature.navigation.NavigationRoute as NavigationScreenRoute
import com.ssafy.e102.eumgil.feature.navigation.NavigationViewModel as NavigationGuidanceViewModel
import com.ssafy.e102.eumgil.feature.onboarding.PrimaryUserType
import com.ssafy.e102.eumgil.feature.report.ReportEntryPoint
import com.ssafy.e102.eumgil.feature.report.ReportHistoryRoute
import com.ssafy.e102.eumgil.feature.report.ReportRoute as ReportScreenRoute
import com.ssafy.e102.eumgil.feature.report.ReportType
import com.ssafy.e102.eumgil.feature.route.RouteDetailEntryRoute
import com.ssafy.e102.eumgil.feature.route.RouteSettingEntryRoute
import com.ssafy.e102.eumgil.feature.savedroute.SavedRouteRoute
import com.ssafy.e102.eumgil.feature.search.SearchEntryRoute
import com.ssafy.e102.eumgil.feature.search.SearchResultsRoute
import com.ssafy.e102.eumgil.feature.search.SearchSelectionMode
import com.ssafy.e102.eumgil.feature.search.SearchVoiceInputRoute
import com.ssafy.e102.eumgil.feature.textsize.TextSizeSettingRoute
import com.ssafy.e102.eumgil.feature.tutorial.MobilityTutorialRoute
import com.ssafy.e102.eumgil.feature.tutorial.TutorialEntryPoint
import kotlinx.coroutines.flow.map

private const val REPORT_START_NEW_REQUEST_KEY = "report_start_new_request"
internal const val REPORT_VOICE_TYPE_KEY = "report_voice_type"
internal const val REPORT_VOICE_DESC_KEY = "report_voice_desc"

fun NavGraphBuilder.mainNavGraph(
    navController: NavHostController,
    onOpenVoiceAssistant: (RouteEditingTarget) -> Unit,
) {
    composable(route = TopLevelRoute.Map.route) { backStackEntry ->
        val shouldResetForHomeEntry by
            backStackEntry.savedStateHandle
                .getStateFlow(MAP_HOME_REENTRY_RESET_KEY, false)
                .collectAsStateWithLifecycle()
        val facilityDetailDismissRequestId by
            backStackEntry.savedStateHandle
                .getStateFlow(
                    MAP_FACILITY_DETAIL_DISMISS_REQUEST_ID_KEY,
                    MAP_FACILITY_DETAIL_DISMISS_REQUEST_INITIAL_ID,
                )
                .collectAsStateWithLifecycle()
        val routeEndpointMapPickerTargetName by
            backStackEntry.savedStateHandle
                .getStateFlow<String?>(MAP_ROUTE_ENDPOINT_PICKER_TARGET_KEY, null)
                .collectAsStateWithLifecycle()
        val routeEndpointMapPickerTarget = routeEndpointMapPickerTargetName.toRouteEditingTargetOrNull()
        MapRoute(
            viewModelStoreOwner = backStackEntry,
            onNavigateToSavedRoutes = {
                navController.navigateToTopLevel(TopLevelDestination.SavedRoute)
            },
            onNavigateToMyPage = {
                navController.navigateToTopLevel(TopLevelDestination.MyPage)
            },
            onNavigateToRouteSetting = { locationPermissionPrechecked ->
                navController.navigateToRouteSettingAfterSearch(locationPermissionPrechecked)
            },
            onNavigateToSearch = { editingTarget, selectionMode ->
                navController.navigate(SearchRoute.Entry.createRoute(editingTarget, selectionMode))
            },
            onNavigateToSearchResults = { query, editingTarget ->
                navController.navigate(
                    SearchRoute.Results.createRoute(query, editingTarget, SearchSelectionMode.PREVIEW_ON_MAP),
                ) {
                    launchSingleTop = true
                }
            },
            routeEndpointMapPickerTarget = routeEndpointMapPickerTarget,
            onRouteEndpointMapPickerTargetConsumed = {
                backStackEntry.savedStateHandle.consumeRouteEndpointMapPickerTarget()
            },
            shouldResetForHomeEntry = shouldResetForHomeEntry,
            onHomeReentryResetConsumed = {
                backStackEntry.savedStateHandle.consumeMapHomeReentryReset()
            },
            onFacilityDetailVisibilityChanged = { isVisible ->
                backStackEntry.savedStateHandle[MAP_FACILITY_DETAIL_VISIBLE_KEY] = isVisible
            },
            facilityDetailDismissRequestId = facilityDetailDismissRequestId,
            onFacilityDetailDismissRequestConsumed = { requestId ->
                backStackEntry.savedStateHandle.consumeMapFacilityDetailDismissRequest(requestId)
            },
            onVoiceSearchVisibilityChanged = { isVisible ->
                backStackEntry.savedStateHandle[MAP_VOICE_SEARCH_VISIBLE_KEY] = isVisible
            },
        )
    }

    composable(route = TopLevelRoute.SavedRoute.route) {
        val navigationViewModel = rememberNavigationGuidanceViewModel()
        SavedRouteRoute(
            onNavigateToMap = {
                navController.navigateToTopLevel(TopLevelDestination.Map)
            },
            onNavigateToNavigation = { request ->
                navigationViewModel.bindNavigationRequest(request)
                navController.navigate(NavigationRoute.Guidance.route)
            },
            onNavigateToRouteDetail = { request ->
                navigationViewModel.bindNavigationRequest(request)
                navController.navigate(
                    RouteSettingRoute.Detail.createRoute(
                        routeOption = request.selectedRoute.routeOption,
                        fromNavigation = true,
                    ),
                )
            },
            onNavigateToRouteSetting = { routeOption ->
                navController.navigateToRouteSettingPermissionGate(initialRouteOption = routeOption)
            },
        )
    }

    composable(route = TopLevelRoute.MyPage.route) {
        MyPageRoute(
            onNavigateToUserTypePrimary = {
                navController.navigate(OnboardingRoute.ProfileUserTypePrimary.route)
            },
            onNavigateToLogin = {
                navController.navigate(AuthRoute.Login.route) {
                    launchSingleTop = true
                    popUpTo(navController.graph.findStartDestination().id) {
                        inclusive = true
                    }
                }
            },
            onNavigateToGuide = {
                navController.navigate(resolveMyPageGuideRoute())
            },
            onNavigateToTextSizeSetting = {
                navController.navigate(MyPageChildRoute.TextSize.route)
            },
        )
    }

    composable(route = MyPageChildRoute.TextSize.route) {
        TextSizeSettingRoute(
            onNavigateBack = {
                navController.popBackStack()
            },
        )
    }

    composable(
        route = SearchRoute.Entry.route,
        arguments =
            listOf(
                navArgument(SearchRoute.Entry.ARG_EDITING_TARGET) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(SearchRoute.Entry.ARG_SELECTION_MODE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { backStackEntry ->
        val initialEditingTarget =
            backStackEntry.arguments
                ?.getString(SearchRoute.Entry.ARG_EDITING_TARGET)
                .toRouteEditingTargetOrDefault()
        val initialSelectionMode =
            backStackEntry.arguments
                ?.getString(SearchRoute.Entry.ARG_SELECTION_MODE)
                .toSearchSelectionModeOrDefault()
        val preserveEntryStateOnReentry =
            backStackEntry.savedStateHandle.get<Boolean>(SEARCH_PRESERVE_ENTRY_STATE_KEY) == true
        backStackEntry.savedStateHandle.set(SEARCH_PRESERVE_ENTRY_STATE_KEY, false)
        SearchEntryRoute(
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToResults = { query, editingTarget, selectionMode ->
                navController.navigate(SearchRoute.Results.createRoute(query, editingTarget, selectionMode))
            },
            onNavigateToVoiceInput = {
                onOpenVoiceAssistant(initialEditingTarget)
            },
            onNavigateToRouteSetting = { locationPermissionPrechecked ->
                navController.navigateToRouteSettingAfterSearch(locationPermissionPrechecked) {
                    popUpTo(TopLevelRoute.Map.route) {
                        inclusive = false
                    }
                }
            },
            onNavigateToMapPreview = {
                val didReturnToMap = navController.popBackStack(
                    route = TopLevelRoute.Map.route,
                    inclusive = false,
                )
                if (!didReturnToMap) {
                    navController.navigateToTopLevel(TopLevelDestination.Map)
                }
            },
            onNavigateToRouteEndpointMapPicker = { editingTarget ->
                navController.navigateToRouteEndpointMapPicker(editingTarget)
            },
            onNavigateToRouteBriefing = {
                navController.navigate(resolveSearchResultBriefingRoute()) {
                    popUpTo(SearchRoute.Entry.route) {
                        inclusive = true
                    }
                }
            },
            initialEditingTarget = initialEditingTarget,
            initialSelectionMode = initialSelectionMode,
            preserveEntryStateOnReentry = preserveEntryStateOnReentry,
        )
    }

    composable(
        route = SearchRoute.Results.route,
        arguments =
            listOf(
                navArgument(SearchRoute.Results.ARG_QUERY) {
                    type = NavType.StringType
                },
                navArgument(SearchRoute.Results.ARG_EDITING_TARGET) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(SearchRoute.Results.ARG_SELECTION_MODE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { backStackEntry ->
        val initialEditingTarget =
            backStackEntry.arguments
                ?.getString(SearchRoute.Results.ARG_EDITING_TARGET)
                .toRouteEditingTargetOrDefault()
        val initialSelectionMode =
            backStackEntry.arguments
                ?.getString(SearchRoute.Results.ARG_SELECTION_MODE)
                .toSearchSelectionModeOrDefault()
        SearchResultsRoute(
            initialQuery = backStackEntry.arguments?.getString(SearchRoute.Results.ARG_QUERY).orEmpty(),
            onNavigateBack = {
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(SEARCH_PRESERVE_ENTRY_STATE_KEY, true)
                navController.popBackStack()
            },
            onNavigateToResults = { query, editingTarget, selectionMode ->
                navController.navigate(SearchRoute.Results.createRoute(query, editingTarget, selectionMode)) {
                    launchSingleTop = true
                }
            },
            onNavigateToVoiceInput = {
                onOpenVoiceAssistant(initialEditingTarget)
            },
            onNavigateToRouteSetting = { locationPermissionPrechecked ->
                navController.navigateToRouteSettingAfterSearch(locationPermissionPrechecked) {
                    popUpTo(TopLevelRoute.Map.route) {
                        inclusive = false
                    }
                }
            },
            onNavigateToMapPreview = {
                val didReturnToMap = navController.popBackStack(
                    route = TopLevelRoute.Map.route,
                    inclusive = false,
                )
                if (!didReturnToMap) {
                    navController.navigateToTopLevel(TopLevelDestination.Map)
                }
            },
            onNavigateToRouteEndpointMapPicker = { editingTarget ->
                navController.navigateToRouteEndpointMapPicker(editingTarget)
            },
            onNavigateToRouteBriefing = {
                navController.navigate(resolveSearchResultBriefingRoute()) {
                    popUpTo(SearchRoute.Entry.route) {
                        inclusive = true
                    }
                }
            },
            initialEditingTarget = initialEditingTarget,
            initialSelectionMode = initialSelectionMode,
        )
    }

    composable(
        route = SearchRoute.VoiceInput.route,
        arguments =
            listOf(
                navArgument(SearchRoute.VoiceInput.ARG_EDITING_TARGET) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(SearchRoute.VoiceInput.ARG_SELECTION_MODE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { backStackEntry ->
        val initialEditingTarget =
            backStackEntry.arguments
                ?.getString(SearchRoute.VoiceInput.ARG_EDITING_TARGET)
                .toRouteEditingTargetOrDefault()
        val initialSelectionMode =
            backStackEntry.arguments
                ?.getString(SearchRoute.VoiceInput.ARG_SELECTION_MODE)
                .toSearchSelectionModeOrDefault()
        val context = LocalContext.current
        val micPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (!isGranted) navController.popBackStack()
        }
        LaunchedEffect(Unit) {
            when (context.resolveMicrophonePermissionState()) {
                MicrophonePermissionState.GRANTED -> Unit
                MicrophonePermissionState.DENIED -> micPermissionLauncher.launch(MICROPHONE_PERMISSION)
                MicrophonePermissionState.UNAVAILABLE -> navController.popBackStack()
            }
        }
        SearchVoiceInputRoute(
            onNavigateBack = {
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(SEARCH_PRESERVE_ENTRY_STATE_KEY, true)
                navController.popBackStack()
            },
            onNavigateToResults = { query, editingTarget, selectionMode ->
                navController.navigate(SearchRoute.Results.createRoute(query, editingTarget, selectionMode)) {
                    launchSingleTop = true
                    popUpTo(SearchRoute.Entry.route) {
                        inclusive = false
                    }
                }
            },
            initialEditingTarget = initialEditingTarget,
            initialSelectionMode = initialSelectionMode,
        )
    }

    composable(
        route = RouteSettingRoute.PermissionGate.route,
        arguments =
            listOf(
                navArgument(RouteSettingRoute.PermissionGate.ARG_AUTO_START_NAVIGATION) {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument(RouteSettingRoute.PermissionGate.ARG_INITIAL_ROUTE_OPTION) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { backStackEntry ->
        val context = LocalContext.current
        val appContainer =
            remember(context.applicationContext) {
                (context.applicationContext as BusanEumgilApp).appContainer
            }
        val locationPermissionManager = remember(appContainer) { appContainer.locationPermissionManager }
        val autoStartNavigation =
            backStackEntry.arguments?.getBoolean(RouteSettingRoute.PermissionGate.ARG_AUTO_START_NAVIGATION) ?: false
        val initialRouteOption =
            backStackEntry.arguments
                ?.getString(RouteSettingRoute.PermissionGate.ARG_INITIAL_ROUTE_OPTION)
                ?.let(RouteOption::fromValue)
        val permissionLauncher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                locationPermissionManager.refreshPermissionState()
                navController.navigateToRouteSettingFromPermissionGate(
                    gateDestinationId = backStackEntry.destination.id,
                    autoStartNavigation = autoStartNavigation,
                    initialRouteOption = initialRouteOption,
                )
            }

        LaunchedEffect(
            locationPermissionManager,
            autoStartNavigation,
            initialRouteOption,
        ) {
            locationPermissionManager.refreshPermissionState()
            when (locationPermissionManager.permissionState.value) {
                is LocationPermissionState.Granted,
                is LocationPermissionState.Unavailable,
                    ->
                    navController.navigateToRouteSettingFromPermissionGate(
                        gateDestinationId = backStackEntry.destination.id,
                        autoStartNavigation = autoStartNavigation,
                        initialRouteOption = initialRouteOption,
                    )

                LocationPermissionState.Denied -> permissionLauncher.launch(locationPermissions)
            }
        }
    }

    composable(
        route = RouteSettingRoute.Setting.route,
        arguments =
            listOf(
                navArgument(RouteSettingRoute.Setting.ARG_AUTO_START_NAVIGATION) {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument(RouteSettingRoute.Setting.ARG_INITIAL_ROUTE_OPTION) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(RouteSettingRoute.Setting.ARG_LOCATION_PERMISSION_PRECHECKED) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
    ) { backStackEntry ->
        val autoStartNavigation =
            backStackEntry.arguments?.getBoolean(RouteSettingRoute.Setting.ARG_AUTO_START_NAVIGATION) ?: false
        val initialRouteOption =
            backStackEntry.arguments
                ?.getString(RouteSettingRoute.Setting.ARG_INITIAL_ROUTE_OPTION)
                ?.let(RouteOption::fromValue)
        val locationPermissionPrechecked =
            backStackEntry.arguments
                ?.getBoolean(RouteSettingRoute.Setting.ARG_LOCATION_PERMISSION_PRECHECKED)
                ?: false
        val navigationViewModel = rememberNavigationGuidanceViewModel()

        RouteSettingEntryRoute(
            autoStartNavigation = autoStartNavigation,
            initialRouteOption = initialRouteOption,
            requestLocationPermissionIfNeeded = !locationPermissionPrechecked,
            onNavigateBack = {
                navController.navigateToTopLevelMapForHomeEntry()
            },
            onNavigateToMap = {
                navController.navigateToTopLevelMapForHomeEntry()
            },
            onNavigateToSearch = { editingTarget, selectionMode ->
                navController.navigate(SearchRoute.Entry.createRoute(editingTarget, selectionMode)) {
                    launchSingleTop = true
                }
            },
            onNavigateToRouteDetail = { routeOption ->
                navController.navigate(RouteSettingRoute.Detail.createRoute(routeOption))
            },
            onStartNavigation = { request ->
                navigationViewModel.bindNavigationRequest(request)
                navController.navigate(NavigationRoute.Guidance.route) {
                    if (autoStartNavigation) {
                        popUpTo(RouteSettingRoute.Setting.route) {
                            inclusive = true
                        }
                    }
                }
            },
        )
    }

    composable(
        route = RouteSettingRoute.Detail.route,
        arguments =
            listOf(
                navArgument(RouteSettingRoute.Detail.ARG_ROUTE_OPTION) {
                    type = NavType.StringType
                },
                navArgument(RouteSettingRoute.Detail.ARG_FROM_NAVIGATION) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
    ) { backStackEntry ->
        val routeOption =
            backStackEntry.arguments
                ?.getString(RouteSettingRoute.Detail.ARG_ROUTE_OPTION)
                ?.toRouteOptionOrDefault()
                ?: RouteOption.SAFE
        val fromNavigation =
            backStackEntry.arguments?.getBoolean(RouteSettingRoute.Detail.ARG_FROM_NAVIGATION) ?: false
        val navigationViewModel = rememberNavigationGuidanceViewModel()

        RouteDetailEntryRoute(
            routeOption = routeOption,
            hydrateFromNavigation = fromNavigation,
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToMap = {
                navController.navigateToTopLevelMapForHomeEntry()
            },
            onStartNavigation = { request ->
                navigationViewModel.bindNavigationRequest(request)
                navController.navigate(NavigationRoute.Guidance.route) {
                    popUpTo(RouteSettingRoute.Detail.createRoute(routeOption, fromNavigation = fromNavigation)) {
                        inclusive = true
                    }
                }
            },
        )
    }

    composable(route = ReportRoute.Report.route) { backStackEntry ->
        val startNewRequest by
            backStackEntry.savedStateHandle
                .getStateFlow(REPORT_START_NEW_REQUEST_KEY, false)
                .collectAsStateWithLifecycle()
        val voiceReportTypeRaw by
            backStackEntry.savedStateHandle
                .getStateFlow<String?>(REPORT_VOICE_TYPE_KEY, null)
                .collectAsStateWithLifecycle()
        val voiceReportType = remember(voiceReportTypeRaw) {
            voiceReportTypeRaw?.let { runCatching { ReportType.valueOf(it) }.getOrNull() }
        }
        val voiceDescription by
            backStackEntry.savedStateHandle
                .getStateFlow<String?>(REPORT_VOICE_DESC_KEY, null)
                .collectAsStateWithLifecycle()
        ReportScreenRoute(
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToReportHistory = { historyId ->
                navController.navigate(ReportRoute.History.createRoute(historyId))
            },
            onNavigateToMap = {
                navController.navigateToTopLevelMapForHomeEntry()
            },
            startNewRequest = startNewRequest,
            onStartNewRequestConsumed = {
                backStackEntry.savedStateHandle[REPORT_START_NEW_REQUEST_KEY] = false
            },
            entryPoint = ReportEntryPoint.TopLevel,
            initialReportType = voiceReportType,
            onInitialReportTypeConsumed = {
                backStackEntry.savedStateHandle[REPORT_VOICE_TYPE_KEY] = null
            },
            initialDescription = voiceDescription,
            onInitialDescriptionConsumed = {
                backStackEntry.savedStateHandle[REPORT_VOICE_DESC_KEY] = null
            },
        )
    }

    composable(route = ReportRoute.Guidance.route) {
        ReportScreenRoute(
            onNavigateBack = {
                navController.navigateBackToNavigationGuidance()
            },
            onNavigateToReportHistory = { historyId ->
                navController.navigate(ReportRoute.History.createRoute(historyId))
            },
            onNavigateToMap = {
                navController.navigateBackToNavigationGuidance()
            },
            onReturnToNavigationWithSubmittedReport = { reportId ->
                navController
                    .getBackStackEntry(NavigationRoute.Guidance.route)
                    .savedStateHandle
                    .setNavigationHazardReportSubmittedReportId(reportId)
                navController.navigateBackToNavigationGuidance()
            },
            entryPoint = ReportEntryPoint.NavigationGuidance,
            startNewRequest = true,
        )
    }

    composable(
        route = ReportRoute.History.route,
        arguments =
            listOf(
                navArgument(ReportRoute.History.ARG_HISTORY_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { backStackEntry ->
        ReportHistoryRoute(
            initialHistoryId = backStackEntry.arguments?.getString(ReportRoute.History.ARG_HISTORY_ID),
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToReport = {
                navController.navigateToReportStartNew()
            },
        )
    }

    composable(route = TutorialRoute.Guide.route) {
        MobilityTutorialRoute(
            entryPoint = TutorialEntryPoint.GUIDE,
            onCompleted = {
                val didPopToAppInfo =
                    navController.popBackStack(
                        route = resolveTutorialGuideCompletedRoute(),
                        inclusive = false,
                    )
                if (!didPopToAppInfo) {
                    navController.navigate(resolveTutorialGuideCompletedRoute()) {
                        launchSingleTop = true
                    }
                }
            },
        )
    }

    composable(route = ArrivalRoute.Entry.route) {
        val selectedPrimaryUserType = rememberSelectedPrimaryUserType()
        ArrivalScreenRoute(
            onNavigateToMap = {
                navController.navigateToArrivalHome(selectedPrimaryUserType)
            },
            onNavigateToSearch = {
                navController.navigate(SearchRoute.Entry.createRoute()) {
                    launchSingleTop = true
                    popUpTo(ArrivalRoute.Entry.route) {
                        inclusive = true
                    }
                }
            },
        )
    }

    composable(route = NavigationRoute.Guidance.route) { backStackEntry ->
        val selectedPrimaryUserType = rememberSelectedPrimaryUserType()
        val submittedHazardReportId by
            backStackEntry.savedStateHandle
                .getStateFlow<Long?>(NAVIGATION_HAZARD_REPORT_SUBMITTED_REPORT_ID_KEY, null)
                .collectAsStateWithLifecycle()
        val useLowVisionUi = shouldUseLowVisionNavigationUi(selectedPrimaryUserType)

        NavigationScreenRoute(
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToRouteDetail = { routeOption ->
                navController.navigate(RouteSettingRoute.Detail.createRoute(routeOption, fromNavigation = true))
            },
            onNavigateToReport = {
                navController.navigate(ReportRoute.Guidance.route) {
                    launchSingleTop = true
                }
            },
            onNavigateToMap = {
                navController.navigateToTopLevelMapForHomeEntry()
            },
            onNavigateToSavedRoute = {
                if (useLowVisionUi) {
                    navController.navigate(resolveNavigationSavedRoute(selectedPrimaryUserType)) {
                        launchSingleTop = true
                        popUpTo(NavigationRoute.Guidance.route) {
                            inclusive = true
                        }
                    }
                } else {
                    navController.navigateToTopLevel(TopLevelDestination.SavedRoute)
                }
            },
            onNavigateToArrival = {
                navController.navigate(resolveNavigationCompletionRoute(selectedPrimaryUserType)) {
                    launchSingleTop = true
                    popUpTo(NavigationRoute.Guidance.route) {
                        inclusive = true
                    }
                }
            },
            submittedHazardReportId = submittedHazardReportId,
            onSubmittedHazardReportConsumed = {
                backStackEntry.savedStateHandle.consumeNavigationHazardReportSubmittedReportId()
            },
            useLowVisionUi = useLowVisionUi,
        )
    }
}

@Composable
private fun rememberSelectedPrimaryUserType(): String? {
    val context = LocalContext.current
    val settingsRepository =
        remember(context) {
            (context.applicationContext as BusanEumgilApp).appContainer.settingsRepository
        }
    val selectedPrimaryUserType by
        remember(settingsRepository) {
            settingsRepository
                .observeInitSettings()
                .map { initSettings -> initSettings.selectedPrimaryUserType }
        }.collectAsStateWithLifecycle(initialValue = null)
    return selectedPrimaryUserType
}

internal fun resolveNavigationSavedRoute(selectedPrimaryUserType: String?): String =
    if (shouldUseLowVisionNavigationUi(selectedPrimaryUserType)) {
        LowVisionRoute.Bookmark.route
    } else {
        TopLevelRoute.SavedRoute.route
    }

internal fun resolveArrivalHomeRoute(selectedPrimaryUserType: String?): String =
    if (shouldUseLowVisionNavigationUi(selectedPrimaryUserType)) {
        LowVisionRoute.Home.route
    } else {
        TopLevelRoute.Map.route
    }

internal fun resolveSearchResultBriefingRoute(): String = LowVisionRoute.RouteBriefing.route

internal fun resolveMyPageGuideRoute(): String = TutorialRoute.Guide.route

internal fun shouldUseLowVisionNavigationUi(selectedPrimaryUserType: String?): Boolean =
    selectedPrimaryUserType == PrimaryUserType.LOW_VISION.routeValue

private const val SEARCH_PRESERVE_ENTRY_STATE_KEY: String = "searchPreserveEntryState"
private const val MAP_HOME_REENTRY_RESET_KEY: String = "mapHomeReentryReset"
private const val MAP_FACILITY_DETAIL_DISMISS_REQUEST_ID_KEY: String = "mapFacilityDetailDismissRequestId"
private const val MAP_FACILITY_DETAIL_DISMISS_CONSUMED_ID_KEY: String = "mapFacilityDetailDismissConsumedId"
private const val NAVIGATION_HAZARD_REPORT_SUBMITTED_REPORT_ID_KEY: String = "navigationHazardReportSubmittedReportId"
internal const val MAP_FACILITY_DETAIL_DISMISS_REQUEST_INITIAL_ID: Long = 0L
private const val MAP_ROUTE_ENDPOINT_PICKER_TARGET_KEY: String = "mapRouteEndpointPickerTarget"
internal const val MAP_VOICE_SEARCH_VISIBLE_KEY: String = "mapVoiceSearchVisible"

internal data class TopLevelNavigationPolicy(
    val launchSingleTop: Boolean,
    val restoreState: Boolean,
    val saveState: Boolean,
)

internal val DefaultTopLevelNavigationPolicy: TopLevelNavigationPolicy =
    TopLevelNavigationPolicy(
        launchSingleTop = true,
        restoreState = true,
        saveState = true,
    )

fun NavController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route.route) {
        launchSingleTop = DefaultTopLevelNavigationPolicy.launchSingleTop
        restoreState = DefaultTopLevelNavigationPolicy.restoreState
        popUpTo(graph.findStartDestination().id) {
            saveState = DefaultTopLevelNavigationPolicy.saveState
        }
    }
}

private fun NavController.navigateToReportStartNew() {
    val didPopToReport =
        popBackStack(
            route = ReportRoute.Report.route,
            inclusive = false,
        )
    if (!didPopToReport) {
        navigate(ReportRoute.Report.route) {
            launchSingleTop = true
        }
    }
    getBackStackEntry(ReportRoute.Report.route).savedStateHandle[REPORT_START_NEW_REQUEST_KEY] = true
}

private fun NavController.navigateBackToNavigationGuidance() {
    val didReturnToGuidance =
        popBackStack(
            route = NavigationRoute.Guidance.route,
            inclusive = false,
        )
    if (!didReturnToGuidance) {
        navigate(NavigationRoute.Guidance.route) {
            launchSingleTop = true
        }
    }
}

internal fun NavController.navigateToTopLevelMapForHomeEntry() {
    val didPopToMap =
        popBackStack(
            route = TopLevelRoute.Map.route,
            inclusive = false,
        )
    if (!didPopToMap) {
        navigateToTopLevel(TopLevelDestination.Map)
    }
    getBackStackEntry(TopLevelRoute.Map.route).savedStateHandle.requestMapHomeReentryReset()
}

private fun NavController.navigateToRouteEndpointMapPicker(editingTarget: RouteEditingTarget) {
    val didReturnToMap =
        popBackStack(
            route = TopLevelRoute.Map.route,
            inclusive = false,
        )
    if (!didReturnToMap) {
        navigateToTopLevel(TopLevelDestination.Map)
    }
    getBackStackEntry(TopLevelRoute.Map.route).savedStateHandle.requestRouteEndpointMapPicker(editingTarget)
}

private fun NavController.navigateToRouteSettingPermissionGate(
    autoStartNavigation: Boolean = false,
    initialRouteOption: RouteOption? = null,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    navigate(
        RouteSettingRoute.PermissionGate.createRoute(
            autoStartNavigation = autoStartNavigation,
            initialRouteOption = initialRouteOption,
        ),
        builder,
    )
}

private fun NavController.navigateToRouteSettingAfterSearch(
    locationPermissionPrechecked: Boolean,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    if (locationPermissionPrechecked) {
        navigate(
            RouteSettingRoute.Setting.createRoute(locationPermissionPrechecked = true),
            builder,
        )
        return
    }

    navigateToRouteSettingPermissionGate(builder = builder)
}

private fun NavController.navigateToRouteSettingFromPermissionGate(
    gateDestinationId: Int,
    autoStartNavigation: Boolean,
    initialRouteOption: RouteOption?,
) {
    navigate(
        RouteSettingRoute.Setting.createRoute(
            autoStartNavigation = autoStartNavigation,
            initialRouteOption = initialRouteOption,
            locationPermissionPrechecked = true,
        ),
    ) {
        launchSingleTop = true
        popUpTo(gateDestinationId) {
            inclusive = true
        }
    }
}

internal fun NavHostController.navigateToArrivalHome(selectedPrimaryUserType: String?) {
    if (!shouldUseLowVisionNavigationUi(selectedPrimaryUserType)) {
        navigateToTopLevelMapForHomeEntry()
        return
    }

    val didPopToLowVisionHome =
        popBackStack(
            route = LowVisionRoute.Home.route,
            inclusive = false,
        )
    if (!didPopToLowVisionHome) {
        navigate(resolveArrivalHomeRoute(selectedPrimaryUserType)) {
            launchSingleTop = true
            popUpTo(ArrivalRoute.Entry.route) {
                inclusive = true
            }
        }
    }
}

internal fun SavedStateHandle.requestMapHomeReentryReset() {
    set(MAP_HOME_REENTRY_RESET_KEY, true)
}

internal fun SavedStateHandle.requestRouteEndpointMapPicker(editingTarget: RouteEditingTarget) {
    set(MAP_ROUTE_ENDPOINT_PICKER_TARGET_KEY, editingTarget.name)
}

internal fun SavedStateHandle.consumeRouteEndpointMapPickerTarget() {
    set<String?>(MAP_ROUTE_ENDPOINT_PICKER_TARGET_KEY, null)
}

internal fun SavedStateHandle.consumeMapHomeReentryReset(): Boolean {
    val shouldReset = get<Boolean>(MAP_HOME_REENTRY_RESET_KEY) == true
    if (shouldReset) {
        set(MAP_HOME_REENTRY_RESET_KEY, false)
    }
    return shouldReset
}

internal fun SavedStateHandle.requestMapFacilityDetailDismiss(): Long {
    val nextRequestId =
        (get<Long>(MAP_FACILITY_DETAIL_DISMISS_REQUEST_ID_KEY)
            ?: MAP_FACILITY_DETAIL_DISMISS_REQUEST_INITIAL_ID) + 1L
    set(MAP_FACILITY_DETAIL_DISMISS_REQUEST_ID_KEY, nextRequestId)
    return nextRequestId
}

internal fun SavedStateHandle.consumeMapFacilityDetailDismissRequest(requestId: Long): Boolean {
    if (requestId <= MAP_FACILITY_DETAIL_DISMISS_REQUEST_INITIAL_ID) return false

    val currentRequestId =
        get<Long>(MAP_FACILITY_DETAIL_DISMISS_REQUEST_ID_KEY)
            ?: MAP_FACILITY_DETAIL_DISMISS_REQUEST_INITIAL_ID
    if (currentRequestId != requestId) return false

    val consumedRequestId =
        get<Long>(MAP_FACILITY_DETAIL_DISMISS_CONSUMED_ID_KEY)
            ?: MAP_FACILITY_DETAIL_DISMISS_REQUEST_INITIAL_ID
    if (requestId <= consumedRequestId) return false

    set(MAP_FACILITY_DETAIL_DISMISS_CONSUMED_ID_KEY, requestId)
    return true
}

internal fun SavedStateHandle.setNavigationHazardReportSubmittedReportId(reportId: Long) {
    set(NAVIGATION_HAZARD_REPORT_SUBMITTED_REPORT_ID_KEY, reportId)
}

internal fun SavedStateHandle.consumeNavigationHazardReportSubmittedReportId(): Long? {
    val reportId = get<Long>(NAVIGATION_HAZARD_REPORT_SUBMITTED_REPORT_ID_KEY)
    if (reportId != null) {
        set<Long?>(NAVIGATION_HAZARD_REPORT_SUBMITTED_REPORT_ID_KEY, null)
    }
    return reportId
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }

private fun String.toRouteOptionOrDefault(): RouteOption =
    runCatching { RouteOption.valueOf(this) }.getOrDefault(RouteOption.SAFE)

private fun String?.toRouteEditingTargetOrDefault(): RouteEditingTarget =
    this
        ?.let { value -> runCatching { RouteEditingTarget.valueOf(value) }.getOrNull() }
        ?: RouteEditingTarget.DESTINATION

private fun String?.toRouteEditingTargetOrNull(): RouteEditingTarget? =
    this
        ?.let { value -> runCatching { RouteEditingTarget.valueOf(value) }.getOrNull() }

private fun String?.toSearchSelectionModeOrDefault(): SearchSelectionMode =
    this
        ?.let { value -> runCatching { SearchSelectionMode.valueOf(value) }.getOrNull() }
        ?: SearchSelectionMode.PREVIEW_ON_MAP

@androidx.compose.runtime.Composable
internal fun rememberNavigationGuidanceViewModel(): NavigationGuidanceViewModel {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    val currentLocationManager = remember(context) {
        (context.applicationContext as BusanEumgilApp).appContainer.currentLocationManager
    }
    val currentHeadingManager = remember(context) {
        (context.applicationContext as BusanEumgilApp).appContainer.currentHeadingManager
    }
    val locationPermissionManager = remember(context) {
        (context.applicationContext as BusanEumgilApp).appContainer.locationPermissionManager
    }
    val bookmarkRepository = remember(context) {
        (context.applicationContext as BusanEumgilApp).appContainer.bookmarkRepository
    }
    val routeRepository = remember(context) {
        (context.applicationContext as BusanEumgilApp).appContainer.routeRepository
    }
    val reportRepository = remember(context) {
        (context.applicationContext as BusanEumgilApp).appContainer.reportRepository
    }
    val navigationViewModelFactory =
        remember(
            currentLocationManager,
            currentHeadingManager,
            locationPermissionManager,
            bookmarkRepository,
            routeRepository,
            reportRepository,
        ) {
            NavigationGuidanceViewModel.provideFactory(
                currentLocationManager = currentLocationManager,
                currentHeadingManager = currentHeadingManager,
                locationPermissionManager = locationPermissionManager,
                bookmarkRepository = bookmarkRepository,
                routeRepository = routeRepository,
                reportRepository = reportRepository,
            )
        }

    return remember(activity, navigationViewModelFactory) {
        val owner = checkNotNull(activity) { "RouteSettingRoute requires a ComponentActivity host." }
        ViewModelProvider(owner, navigationViewModelFactory)[NavigationGuidanceViewModel::class.java]
    }
}
