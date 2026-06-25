package com.ssafy.e102.eumgil.core.tts

internal data class TextToSpeechAudioConfig(
    val usage: Int,
    val contentType: Int,
    val capturePolicy: Int,
    val focusGain: Int,
)

internal fun defaultTextToSpeechAudioConfig(): TextToSpeechAudioConfig =
    TextToSpeechAudioConfig(
        usage = USAGE_MEDIA,
        contentType = CONTENT_TYPE_SPEECH,
        capturePolicy = ALLOW_CAPTURE_BY_ALL,
        focusGain = AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
    )

private const val USAGE_MEDIA = 1
private const val CONTENT_TYPE_SPEECH = 1
private const val ALLOW_CAPTURE_BY_ALL = 1
private const val AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK = 3
