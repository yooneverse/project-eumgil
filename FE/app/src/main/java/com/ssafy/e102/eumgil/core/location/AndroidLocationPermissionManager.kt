package com.ssafy.e102.eumgil.core.location

import android.content.Context
import android.location.LocationManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidLocationPermissionManager(
    context: Context,
) : LocationPermissionManager {
    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val mutablePermissionState = MutableStateFlow(resolvePermissionState())
    private var requestLauncher: ActivityResultLauncher<Array<String>>? = null
    private var launcherOwnerIdentity: Int? = null

    override val permissionState: StateFlow<LocationPermissionState> =
        mutablePermissionState.asStateFlow()

    override fun refreshPermissionState() {
        mutablePermissionState.value = resolvePermissionState()
    }

    override fun requestLocationPermission(activity: ComponentActivity) {
        refreshPermissionState()
        if (permissionState.value !is LocationPermissionState.Denied) return

        val launcher = resolveRequestLauncher(activity)
        launcher.launch(locationPermissions)
    }

    private fun resolvePermissionState(): LocationPermissionState {
        if (!appContext.hasLocationFeature()) {
            return LocationPermissionState.Unavailable(
                reason = LocationPermissionUnavailableReason.NO_LOCATION_FEATURE,
            )
        }

        val accuracy = appContext.resolveLocationGrantAccuracy() ?: return LocationPermissionState.Denied
        val usableProviders = locationManager.usableProviders(accuracy)

        if (usableProviders.isEmpty()) {
            return LocationPermissionState.Unavailable(
                reason = LocationPermissionUnavailableReason.LOCATION_SERVICES_DISABLED,
            )
        }

        return LocationPermissionState.Granted(accuracy = accuracy)
    }

    private fun resolveRequestLauncher(activity: ComponentActivity): ActivityResultLauncher<Array<String>> {
        val activityIdentity = System.identityHashCode(activity)
        val currentLauncher = requestLauncher
        if (currentLauncher != null && launcherOwnerIdentity == activityIdentity) {
            return currentLauncher
        }

        clearRequestLauncher()

        val key = "location_permission_request_$activityIdentity"
        launcherOwnerIdentity = activityIdentity
        requestLauncher =
            activity.activityResultRegistry.register(
                key,
                ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                refreshPermissionState()
            }

        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    if (launcherOwnerIdentity == activityIdentity) {
                        clearRequestLauncher()
                    }
                    owner.lifecycle.removeObserver(this)
                }
            },
        )

        return checkNotNull(requestLauncher)
    }

    private fun clearRequestLauncher() {
        requestLauncher?.unregister()
        requestLauncher = null
        launcherOwnerIdentity = null
    }
}
