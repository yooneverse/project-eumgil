package com.ssafy.e102.eumgil.core.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

internal const val MICROPHONE_PERMISSION: String = Manifest.permission.RECORD_AUDIO

internal enum class MicrophonePermissionState {
    GRANTED,
    DENIED,
    UNAVAILABLE,
}

internal fun Context.resolveMicrophonePermissionState(): MicrophonePermissionState =
    when {
        !packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE) ->
            MicrophonePermissionState.UNAVAILABLE
        ContextCompat.checkSelfPermission(this, MICROPHONE_PERMISSION) == PackageManager.PERMISSION_GRANTED ->
            MicrophonePermissionState.GRANTED
        else -> MicrophonePermissionState.DENIED
    }

internal fun Context.hasGrantedMicrophonePermission(): Boolean =
    resolveMicrophonePermissionState() == MicrophonePermissionState.GRANTED
