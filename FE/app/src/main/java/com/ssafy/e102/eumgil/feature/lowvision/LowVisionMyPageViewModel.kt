package com.ssafy.e102.eumgil.feature.lowvision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ssafy.e102.eumgil.data.repository.AuthLogoutRepository
import com.ssafy.e102.eumgil.data.repository.AuthLogoutResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LowVisionMyPageUiState(
    val isLogoutLoading: Boolean = false,
)

sealed interface LowVisionMyPageUiEvent {
    data object NavigateToLogin : LowVisionMyPageUiEvent

    data class ShowSnackbar(
        val message: String,
    ) : LowVisionMyPageUiEvent
}

class LowVisionMyPageViewModel(
    private val authLogoutRepository: AuthLogoutRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(LowVisionMyPageUiState())
    val uiState: StateFlow<LowVisionMyPageUiState> = mutableUiState.asStateFlow()

    private val mutableUiEvent = MutableSharedFlow<LowVisionMyPageUiEvent>()
    val uiEvent: SharedFlow<LowVisionMyPageUiEvent> = mutableUiEvent.asSharedFlow()

    private var logoutJob: Job? = null

    fun onLogoutClick() {
        if (mutableUiState.value.isLogoutLoading) return

        mutableUiState.update { state -> state.copy(isLogoutLoading = true) }
        logoutJob?.cancel()
        logoutJob =
            viewModelScope.launch {
                when (val result = authLogoutRepository.logout()) {
                    is AuthLogoutResult.Success -> finishLogout()
                    AuthLogoutResult.MissingSession,
                    AuthLogoutResult.AuthenticationFailed
                    -> finishLogout()
                    is AuthLogoutResult.Failure -> {
                        mutableUiState.update { state -> state.copy(isLogoutLoading = false) }
                        mutableUiEvent.emit(LowVisionMyPageUiEvent.ShowSnackbar(message = result.message))
                    }
                }
            }
    }

    private suspend fun finishLogout() {
        mutableUiState.update { state -> state.copy(isLogoutLoading = false) }
        mutableUiEvent.emit(LowVisionMyPageUiEvent.NavigateToLogin)
    }

    override fun onCleared() {
        logoutJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun provideFactory(authLogoutRepository: AuthLogoutRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(LowVisionMyPageViewModel::class.java)) {
                        return LowVisionMyPageViewModel(authLogoutRepository = authLogoutRepository) as T
                    }

                    error("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}
