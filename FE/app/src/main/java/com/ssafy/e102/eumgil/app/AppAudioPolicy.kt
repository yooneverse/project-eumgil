package com.ssafy.e102.eumgil.app

import android.content.Context
import android.media.AudioManager
import android.os.Build

internal fun defaultAppVolumeControlStream(): Int = STREAM_MUSIC

internal fun defaultAppAudioPlaybackCapturePolicy(): Int = ALLOW_CAPTURE_BY_ALL

internal fun configureAppAudioPlaybackCapturePolicy(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

    val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return
    audioManager.setAllowedCapturePolicy(defaultAppAudioPlaybackCapturePolicy())
}

private const val STREAM_MUSIC = 3
private const val ALLOW_CAPTURE_BY_ALL = 1
