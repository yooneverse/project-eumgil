package com.ssafy.e102.eumgil.feature.lowvision

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.e102.eumgil.app.BusanEumgilApp
import com.ssafy.e102.eumgil.core.location.AndroidCurrentLocationAddressResolver
import com.ssafy.e102.eumgil.core.location.LocationPermissionState

/**
 * Route wrapper for [LowVisionHomeScreen].
 *
 * 화면 자체는 stateless에 가깝고 네비 선택만 saveable로 들고 있는다.
 * 실제 화면 전환(음성 입력 카드 → 음성 입력 화면 등)은 [onVoiceInputClick]·
 * [onCurrentLocationClick]·[onTabSelected] 콜백을 NavGraph가 받아서 처리한다.
 */
@Composable
fun LowVisionHomeRoute(
    onVoiceInputClick: () -> Unit,
    onCurrentLocationClick: () -> Unit,
    onTabSelected: (LowVisionBottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val activity = remember(context) { context.findComponentActivity() }
    val appContainer =
        remember(appContext) {
            (appContext as BusanEumgilApp).appContainer
        }
    val currentLocationManager =
        remember(appContainer) { appContainer.currentLocationManager }
    val locationPermissionManager =
        remember(appContainer) { appContainer.locationPermissionManager }
    val currentLocation by currentLocationManager.latestLocation.collectAsStateWithLifecycle()
    val locationPermissionState by locationPermissionManager.permissionState.collectAsStateWithLifecycle()
    val currentLocationAddressResolver =
        remember(appContext) { AndroidCurrentLocationAddressResolver(context = appContext) }
    val currentLocationAddress =
        rememberLowVisionCurrentLocationAddress(
            coordinate = currentLocation.toLowVisionCurrentLocationCoordinate(),
            addressResolver = currentLocationAddressResolver,
        )

    DisposableEffect(currentLocationManager, locationPermissionManager) {
        locationPermissionManager.refreshPermissionState()
        currentLocationManager.startLocationUpdates()
        currentLocationManager.refreshLatestLocation()
        onDispose {
            currentLocationManager.stopLocationUpdates()
        }
    }

    LaunchedEffect(locationPermissionState, currentLocationManager) {
        if (locationPermissionState is LocationPermissionState.Granted) {
            currentLocationManager.startLocationUpdates()
            currentLocationManager.refreshLatestLocation()
        }
    }

    LowVisionFontTheme {
        LowVisionHomeScreen(
            uiState = LowVisionHomeUiState(selectedTab = LowVisionBottomTab.HOME),
            onVoiceInputClick = onVoiceInputClick,
            onCurrentLocationClick = {
                locationPermissionManager.refreshPermissionState()
                val currentPermissionState = locationPermissionManager.permissionState.value
                if (shouldRequestLowVisionHomeLocationPermission(currentPermissionState)) {
                    activity?.let(locationPermissionManager::requestLocationPermission)
                } else {
                    currentLocationManager.startLocationUpdates()
                    currentLocationManager.refreshLatestLocation()
                    onCurrentLocationClick()
                }
            },
            onTabSelected = onTabSelected,
            modifier = modifier,
            currentLocationDisplay =
                lowVisionCurrentLocationDisplay(
                    snapshot = currentLocation,
                    address = currentLocationAddress,
                ),
        )
    }
}

internal fun shouldRequestLowVisionHomeLocationPermission(
    permissionState: LocationPermissionState,
): Boolean =
    permissionState is LocationPermissionState.Denied

private tailrec fun Context.findComponentActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }
