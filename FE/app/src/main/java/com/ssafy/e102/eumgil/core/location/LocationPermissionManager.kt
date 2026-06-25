package com.ssafy.e102.eumgil.core.location

import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.StateFlow

interface LocationPermissionManager {
    val permissionState: StateFlow<LocationPermissionState>

    fun refreshPermissionState()

    fun requestLocationPermission(activity: ComponentActivity)
}
