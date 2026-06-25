package com.ssafy.e102.eumgil.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface TextToSpeechController {
    val state: StateFlow<TextToSpeechState>

    fun setEnabled(enabled: Boolean)

    fun speak(text: String)

    fun stop()

    fun shutdown()
}

data class TextToSpeechState(
    val enabled: Boolean = true,
    val availability: TextToSpeechAvailability = TextToSpeechAvailability.Initializing,
    val isSpeaking: Boolean = false,
    val completedUtteranceCount: Int = 0,
) {
    val canSpeak: Boolean
        get() = enabled && availability == TextToSpeechAvailability.Ready
}

enum class TextToSpeechAvailability {
    Initializing,
    Ready,
    Unavailable,
}

internal class AndroidTextToSpeechController(
    context: Context,
    private val locale: Locale = Locale.KOREAN,
    private val speechRate: Float = DEFAULT_TTS_SPEECH_RATE,
    private val audioConfig: TextToSpeechAudioConfig = defaultTextToSpeechAudioConfig(),
) : TextToSpeechController {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val audioAttributes by lazy { audioConfig.toAudioAttributes() }
    private var engine: TextToSpeech? = null
    private var pendingText: String? = null
    private var isShutdown = false
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    private val mutableState = MutableStateFlow(TextToSpeechState())
    override val state: StateFlow<TextToSpeechState> = mutableState.asStateFlow()

    override fun setEnabled(enabled: Boolean) {
        mutableState.update { it.copy(enabled = enabled) }
        if (!enabled) {
            stop()
        } else {
            ensureEngine()
        }
    }

    override fun speak(text: String) {
        val utterance = text.trim()
        if (utterance.isEmpty() || isShutdown || !state.value.enabled) return

        pendingText = utterance
        ensureEngine()
        if (state.value.canSpeak) {
            speakPendingText()
        }
    }

    override fun stop() {
        pendingText = null
        runCatching { engine?.stop() }
        abandonAudioFocus()
        mutableState.update { it.copy(isSpeaking = false) }
    }

    override fun shutdown() {
        isShutdown = true
        pendingText = null
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
        abandonAudioFocus()
        engine = null
        mutableState.update { it.copy(availability = TextToSpeechAvailability.Unavailable) }
    }

    private fun ensureEngine() {
        if (engine != null || isShutdown) return

        mutableState.update { it.copy(availability = TextToSpeechAvailability.Initializing) }
        engine =
            runCatching {
                TextToSpeech(appContext) { status -> handleInitResult(status) }
            }.getOrElse {
                markUnavailable()
                null
            }
    }

    private fun handleInitResult(status: Int) {
        val initialized = status == TextToSpeech.SUCCESS
        val languageResult =
            runCatching { engine?.setLanguage(locale) }
                .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                ?: TextToSpeech.LANG_NOT_SUPPORTED
        val languageSupported = initialized && languageResult.isSupportedLanguageResult()

        if (!languageSupported) {
            markUnavailable()
            return
        }

        val speechRateConfigured =
            runCatching { engine?.setSpeechRate(speechRate) }
                .getOrDefault(TextToSpeech.ERROR) != TextToSpeech.ERROR

        if (!speechRateConfigured) {
            markUnavailable()
            return
        }

        val audioAttributesConfigured =
            runCatching { engine?.setAudioAttributes(audioAttributes) }
                .getOrDefault(TextToSpeech.ERROR) != TextToSpeech.ERROR

        if (!audioAttributesConfigured) {
            markUnavailable()
            return
        }

        mutableState.update { it.copy(availability = TextToSpeechAvailability.Ready) }
        engine?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    mutableState.update { it.copy(isSpeaking = true) }
                }

                override fun onDone(utteranceId: String?) {
                    abandonAudioFocus()
                    mutableState.update { state ->
                        state.copy(
                            isSpeaking = false,
                            completedUtteranceCount = state.completedUtteranceCount + 1,
                        )
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onSpeechFinishedWithError()
                }

                override fun onError(
                    utteranceId: String?,
                    errorCode: Int,
                ) {
                    onSpeechFinishedWithError()
                }
            },
        )
        speakPendingText()
    }

    private fun speakPendingText() {
        val text = pendingText ?: return
        pendingText = null
        requestAudioFocus()

        val result =
            runCatching {
                engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, NAVIGATION_TTS_UTTERANCE_ID)
            }.getOrDefault(TextToSpeech.ERROR)

        if (result == TextToSpeech.ERROR) {
            markUnavailable()
        } else {
            mutableState.update { it.copy(isSpeaking = true) }
        }
    }

    private fun markUnavailable() {
        pendingText = null
        abandonAudioFocus()
        mutableState.update {
            it.copy(
                availability = TextToSpeechAvailability.Unavailable,
                isSpeaking = false,
            )
        }
    }

    private fun onSpeechFinishedWithError() {
        abandonAudioFocus()
        mutableState.update { state ->
            state.copy(
                isSpeaking = false,
                completedUtteranceCount = state.completedUtteranceCount + 1,
            )
        }
    }

    private fun requestAudioFocus() {
        val manager = audioManager ?: return
        val request =
            audioFocusRequest ?: AudioFocusRequest.Builder(audioConfig.focusGain)
                .setAudioAttributes(audioAttributes)
                .setWillPauseWhenDucked(false)
                .build()
                .also { builtRequest ->
                    audioFocusRequest = builtRequest
                }
        hasAudioFocus = manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        val request = audioFocusRequest ?: return
        if (!hasAudioFocus) return
        manager.abandonAudioFocusRequest(request)
        hasAudioFocus = false
    }
}

object NoOpTextToSpeechController : TextToSpeechController {
    private val mutableState =
        MutableStateFlow(
            TextToSpeechState(
                enabled = false,
                availability = TextToSpeechAvailability.Unavailable,
            ),
        )
    override val state: StateFlow<TextToSpeechState> = mutableState.asStateFlow()

    override fun setEnabled(enabled: Boolean) = Unit

    override fun speak(text: String) = Unit

    override fun stop() = Unit

    override fun shutdown() = Unit
}

internal const val ROUTE_BRIEFING_TTS_SPEECH_RATE: Float = 1.0f

private const val NAVIGATION_TTS_UTTERANCE_ID = "navigation_guidance"
private const val DEFAULT_TTS_SPEECH_RATE: Float = 1.0f

private fun Int.isSupportedLanguageResult(): Boolean =
    this != TextToSpeech.LANG_MISSING_DATA && this != TextToSpeech.LANG_NOT_SUPPORTED

private fun TextToSpeechAudioConfig.toAudioAttributes(): AudioAttributes =
    AudioAttributes.Builder()
        .setUsage(usage)
        .setContentType(contentType)
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setAllowedCapturePolicy(capturePolicy)
            }
        }
        .build()
