package com.ssafy.e102.eumgil.core.location

enum class LocationGrantAccuracy {
    APPROXIMATE,
    PRECISE,
}

sealed interface LocationPermissionState {
    data class Granted(val accuracy: LocationGrantAccuracy) : LocationPermissionState

    data object Denied : LocationPermissionState

    data class Unavailable(val reason: LocationPermissionUnavailableReason) : LocationPermissionState
}

enum class LocationPermissionUnavailableReason {
    LOCATION_SERVICES_DISABLED,
    NO_LOCATION_FEATURE,
}

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val recordedAtEpochMillis: Long,
    val speedMetersPerSecond: Float? = null,
    val bearingDegrees: Float? = null,
)

fun LocationSnapshot.isFreshCurrentLocation(
    nowEpochMillis: Long = System.currentTimeMillis(),
    maxAgeMillis: Long = MAX_CURRENT_LOCATION_AGE_MILLIS,
): Boolean {
    if (recordedAtEpochMillis <= 0L) return false

    return recordedAtEpochMillis >= nowEpochMillis - maxAgeMillis
}

const val MAX_CURRENT_LOCATION_AGE_MILLIS = 2 * 60 * 1_000L
