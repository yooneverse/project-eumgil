package com.ssafy.e102.eumgil.core.location

import kotlinx.coroutines.flow.StateFlow

interface CurrentLocationManager {
    val latestLocation: StateFlow<LocationSnapshot?>

    fun refreshLatestLocation()

    fun startLocationUpdates()

    fun startLocationUpdates(profile: LocationUpdateProfile) {
        startLocationUpdates()
    }

    fun stopLocationUpdates()
}

enum class LocationUpdateProfile {
    DEFAULT,
    NAVIGATION,
}
