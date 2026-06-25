package com.ssafy.e102.eumgil.feature.search

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.e102.eumgil.core.stt.AudioRecorder
import com.ssafy.e102.eumgil.core.stt.SherpaManager
import com.ssafy.e102.eumgil.core.stt.SttManager
import com.ssafy.e102.eumgil.core.stt.VadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SearchVoiceInputEvent {
    data class TranscriptReady(
        val recognizedText: String,
        val searchQuery: String?,
    ) : SearchVoiceInputEvent

    data object TranscriptEmpty : SearchVoiceInputEvent

    data class SpeakError(val text: String) : SearchVoiceInputEvent

    data object ReadyToRecord : SearchVoiceInputEvent
}

/**
 * Voice search input pipeline for the standard search flow.
 *
 * This screen should surface the raw STT transcript immediately, then let
 * SearchViewModel handle the delayed preview and query normalization before
 * navigating to results.
 */
class SearchVoiceInputViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "SearchVoiceInputVM"
        private const val SILENCE_FRAMES_FOR_STOP = 20 // before: 30
    }

    private val _uiEvent = Channel<SearchVoiceInputEvent>(Channel.BUFFERED)
    val uiEvent: Flow<SearchVoiceInputEvent> = _uiEvent.receiveAsFlow()

    private val audioRecorder = AudioRecorder(getApplication())
    private var vadManager: VadManager? = null
    private var sttManager: SttManager? = null
    private var listeningJob: Job? = null

    /**
     * Initializes models and asks the route to play the prompt TTS before the
     * actual recording starts.
     */
    fun startListening() {
        if (listeningJob?.isActive == true) return
        listeningJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                SherpaManager.ensureModelsExtracted(context)

                if (!SherpaManager.modelsExist(context)) {
                    Log.e(TAG, "Missing STT model files. Cancelling voice input.")
                    _uiEvent.send(SearchVoiceInputEvent.TranscriptEmpty)
                    return@launch
                }

                if (vadManager == null) vadManager = VadManager(context)
                if (sttManager == null) sttManager = SttManager.getInstance(context)

                _uiEvent.send(SearchVoiceInputEvent.ReadyToRecord)
            } catch (e: Exception) {
                Log.e(TAG, "Voice input initialization failed: ${e.message}", e)
                _uiEvent.send(SearchVoiceInputEvent.TranscriptEmpty)
            }
        }
    }

    /**
     * Starts the VAD + STT pipeline after the prompt TTS finishes.
     */
    fun beginRecording() {
        if (listeningJob?.isActive == true) return
        listeningJob = viewModelScope.launch(Dispatchers.IO) {
            runPipeline()
        }
    }

    /**
     * Stops an in-flight recording. Navigation is handled by the caller.
     */
    fun stopListening() {
        audioRecorder.stop()
        listeningJob?.cancel()
    }

    private suspend fun runPipeline() {
        try {
            var voiceDetectedEver = false
            var silenceFrameCount = 0
            val accumulatedSamples = mutableListOf<Float>()
            var skipStt = false

            Log.d(TAG, "Starting voice capture pipeline")

            audioRecorder.startRecording().collect { floatSamples ->
                vadManager?.acceptWaveform(floatSamples)

                var hadSegment = false
                while (vadManager?.isEmpty() == false) {
                    val segment = vadManager?.front() ?: break
                    vadManager?.popSegment()
                    accumulatedSamples.addAll(segment.samples.toList())
                    hadSegment = true
                }

                val currentlySpeaking = vadManager?.isSpeechDetected() ?: false

                when {
                    hadSegment -> {
                        if (!voiceDetectedEver) Log.d(TAG, "Voice detected")
                        voiceDetectedEver = true
                        silenceFrameCount = 0
                    }
                    currentlySpeaking -> {
                        voiceDetectedEver = true
                        silenceFrameCount = 0
                    }
                    else -> {
                        silenceFrameCount++
                    }
                }

                when {
                    voiceDetectedEver && silenceFrameCount >= SILENCE_FRAMES_FOR_STOP -> {
                        Log.d(TAG, "Silence threshold reached. Finalizing STT.")
                        audioRecorder.stop()
                    }
                    !voiceDetectedEver && silenceFrameCount >= SILENCE_FRAMES_FOR_STOP * 2 -> {
                        Log.d(TAG, "No speech detected. Cancelling voice input.")
                        skipStt = true
                        audioRecorder.stop()
                    }
                }
            }

            vadManager?.flush()
            while (vadManager?.isEmpty() == false) {
                val segment = vadManager?.front() ?: break
                vadManager?.popSegment()
                accumulatedSamples.addAll(segment.samples.toList())
            }

            if (!skipStt && voiceDetectedEver && accumulatedSamples.isNotEmpty()) {
                Log.d(TAG, "Running STT on ${accumulatedSamples.size} samples")
                val text = sttManager?.recognize(accumulatedSamples.toFloatArray()).orEmpty()
                Log.d(TAG, "STT transcript: '$text'")
                if (text.isBlank()) {
                    _uiEvent.send(SearchVoiceInputEvent.TranscriptEmpty)
                } else {
                    publishTranscript(text)
                }
            } else {
                Log.d(TAG, "Voice capture ended without usable speech")
                _uiEvent.send(SearchVoiceInputEvent.TranscriptEmpty)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Voice capture failed: ${e.message}", e)
            withContext(Dispatchers.Main) {
                _uiEvent.send(SearchVoiceInputEvent.TranscriptEmpty)
            }
        }
    }

    /**
     * The raw transcript should render first. SearchViewModel owns the delayed
     * analysis step that resolves the final query before navigation.
     */
    private suspend fun publishTranscript(sttText: String) {
        _uiEvent.send(
            SearchVoiceInputEvent.TranscriptReady(
                recognizedText = sttText,
                searchQuery = null,
            ),
        )
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stop()
        listeningJob?.cancel()
        vadManager?.release()
        Log.d(TAG, "SearchVoiceInputViewModel cleared")
    }
}
