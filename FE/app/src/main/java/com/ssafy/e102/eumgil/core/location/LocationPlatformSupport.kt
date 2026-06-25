package com.ssafy.e102.eumgil.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

internal val locationPermissions =
    arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

internal fun Context.resolveLocationGrantAccuracy(): LocationGrantAccuracy? =
    when {
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) -> LocationGrantAccuracy.PRECISE
        hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationGrantAccuracy.APPROXIMATE
        else -> null
    }

internal fun Context.hasLocationFeature(): Boolean =
    packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION)

internal fun LocationManager.usableProviders(accuracy: LocationGrantAccuracy): List<String> {
    val candidateProviders =
        when (accuracy) {
            LocationGrantAccuracy.PRECISE ->
                listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                )

            LocationGrantAccuracy.APPROXIMATE -> listOf(LocationManager.NETWORK_PROVIDER)
        }

    return candidateProviders.filter(::isProviderEnabledSafely)
}

internal fun Location.toSnapshot(): LocationSnapshot =
    LocationSnapshot(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy.takeIf { hasAccuracy() },
        recordedAtEpochMillis = time,
        speedMetersPerSecond = speed.takeIf { hasSpeed() },
        bearingDegrees = bearing.takeIf { hasBearing() },
    )

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun LocationManager.isProviderEnabledSafely(provider: String): Boolean =
    runCatching { isProviderEnabled(provider) }.getOrDefault(false)
