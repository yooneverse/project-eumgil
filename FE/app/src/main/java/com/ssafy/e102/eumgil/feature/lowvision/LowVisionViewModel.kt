package com.ssafy.e102.eumgil.feature.lowvision

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.e102.eumgil.core.permission.hasGrantedMicrophonePermission
import com.ssafy.e102.eumgil.core.stt.KeywordSpottingManager
import com.ssafy.e102.eumgil.core.stt.SherpaManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface LowVisionEvent {
    data object NavigateToVoiceInput : LowVisionEvent
}

class LowVisionViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "LowVisionVM"
    }

    private val _uiEvent = Channel<LowVisionEvent>(Channel.BUFFERED)
    val uiEvent: Flow<LowVisionEvent> = _uiEvent.receiveAsFlow()

    private var kwsManager: KeywordSpottingManager? = null
    private var kwsJob: Job? = null
    private var autoResumeEnabled: Boolean = false

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                initializeKeywordSpotting(getApplication())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize KWS", e)
            }
        }
    }

    fun enableAutoResume() {
        autoResumeEnabled = true
    }

    fun isAutoResumeEnabled(): Boolean = autoResumeEnabled

    fun resumeSpotting() {
        if (!autoResumeEnabled) {
            Log.d(TAG, "KWS auto resume is disabled until the user opens voice input")
            return
        }
        if (kwsJob?.isActive == true) return

        val context = getApplication<Application>()
        if (!context.hasGrantedMicrophonePermission()) {
            Log.w(TAG, "Skipping KWS resume because RECORD_AUDIO is not granted")
            return
        }

        if (kwsManager == null) {
            Log.d(TAG, "KWS manager is missing, reinitializing before restart")
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    initializeKeywordSpotting(context)
                    startSpotting()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reinitialize KWS", e)
                }
            }
            return
        }

        Log.d(TAG, "Restarting KWS spotting")
        startSpotting()
    }

    private fun startSpotting() {
        kwsJob = viewModelScope.launch(Dispatchers.IO) {
            kwsManager?.startSpotting()?.collect {
                Log.d(TAG, "Wake word detected, opening low-vision voice input")
                kwsManager?.stop()
                _uiEvent.send(LowVisionEvent.NavigateToVoiceInput)
            }
        }
    }

    private suspend fun initializeKeywordSpotting(context: Application) {
        SherpaManager.ensureKwsModelsExtracted(context)

        if (!context.hasGrantedMicrophonePermission()) {
            Log.w(TAG, "Skipping KWS initialization because RECORD_AUDIO is not granted")
            return
        }

        if (!SherpaManager.kwsModelsExist(context)) {
            Log.e(TAG, "Skipping KWS initialization because no wake-word models are available")
            return
        }

        kwsManager = KeywordSpottingManager(context)
        Log.d(TAG, "KWS initialized and waiting for an explicit resume request")
    }

    override fun onCleared() {
        super.onCleared()
        kwsJob?.cancel()
        kwsManager?.release()
        Log.d(TAG, "Released KWS resources")
    }
}
