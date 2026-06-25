package com.ssafy.e102.eumgil.feature.navigation

import android.media.AudioManager
import android.media.ToneGenerator

internal class NavigationRouteChangeAlertPlayer {
    private var toneGenerator: ToneGenerator? = null

    fun play() {
        val player =
            toneGenerator
                ?: runCatching {
                    ToneGenerator(AudioManager.STREAM_NOTIFICATION, ROUTE_CHANGE_ALERT_VOLUME_PERCENT)
                }.getOrNull()
                    ?.also { createdPlayer -> toneGenerator = createdPlayer }
                ?: return
        player.startTone(ToneGenerator.TONE_PROP_BEEP, ROUTE_CHANGE_ALERT_DURATION_MILLIS)
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}

private const val ROUTE_CHANGE_ALERT_VOLUME_PERCENT = 80
private const val ROUTE_CHANGE_ALERT_DURATION_MILLIS = 180
