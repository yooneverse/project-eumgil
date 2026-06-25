package com.ssafy.e102.eumgil.feature.textsize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ssafy.e102.eumgil.core.model.TextSizePreference
import com.ssafy.e102.eumgil.data.repository.TextSizePreferenceRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TextSizeSettingViewModel(
    private val textSizePreferenceRepository: TextSizePreferenceRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(TextSizeSettingUiState())
    val uiState: StateFlow<TextSizeSettingUiState> = mutableUiState.asStateFlow()

    private val uiEventChannel = Channel<TextSizeSettingUiEvent>(capacity = Channel.BUFFERED)
    val uiEvent = uiEventChannel.receiveAsFlow()

    init {
        observeTextSizePreference()
    }

    fun onAction(action: TextSizeSettingUiAction) {
        when (action) {
            TextSizeSettingUiAction.BackClicked -> {
                viewModelScope.launch {
                    uiEventChannel.send(TextSizeSettingUiEvent.NavigateBack)
                }
            }
            is TextSizeSettingUiAction.PreferenceSelected -> selectPreference(action.preference)
        }
    }

    private fun observeTextSizePreference() {
        viewModelScope.launch {
            textSizePreferenceRepository.observeTextSizePreference().collectLatest { preference ->
                mutableUiState.update { state ->
                    state.copy(selectedPreference = preference)
                }
            }
        }
    }

    private fun selectPreference(preference: TextSizePreference) {
        if (preference == mutableUiState.value.selectedPreference) return

        viewModelScope.launch {
            textSizePreferenceRepository.saveTextSizePreference(preference)
        }
    }

    companion object {
        fun provideFactory(
            textSizePreferenceRepository: TextSizePreferenceRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(TextSizeSettingViewModel::class.java)) {
                        return TextSizeSettingViewModel(
                            textSizePreferenceRepository = textSizePreferenceRepository,
                        ) as T
                    }

                    error("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}
