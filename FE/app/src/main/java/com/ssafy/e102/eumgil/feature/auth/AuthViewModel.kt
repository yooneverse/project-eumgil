package com.ssafy.e102.eumgil.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ssafy.e102.eumgil.data.repository.AuthLoginRepository
import com.ssafy.e102.eumgil.data.repository.AuthLoginRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authLoginRepository: AuthLoginRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = mutableUiState.asStateFlow()

    private val mutableUiEvent = MutableSharedFlow<AuthUiEvent>()
    val uiEvent: SharedFlow<AuthUiEvent> = mutableUiEvent.asSharedFlow()

    private var loginJob: Job? = null

    fun onAction(action: AuthUiAction) {
        when (action) {
            is AuthUiAction.SocialLoginClicked -> startSocialLogin(action.providerKey)
        }
    }

    private fun startSocialLogin(providerKey: String) {
        val currentState = mutableUiState.value
        if (currentState.isLoading) return
        if (currentState.providers.none { provider -> provider.key == providerKey }) {
            mutableUiState.update { state ->
                state.copy(loginStatus = AuthLoginStatus.Error(DEFAULT_AUTH_LOGIN_ERROR_MESSAGE))
            }
            return
        }

        loginJob?.cancel()
        loginJob =
            viewModelScope.launch {
                mutableUiState.update { state ->
                    state.copy(loginStatus = AuthLoginStatus.Loading(providerKey = providerKey))
                }

                try {
                    authLoginRepository.login(AuthLoginRequest(providerKey = providerKey))
                    mutableUiState.update { state ->
                        state.copy(loginStatus = AuthLoginStatus.Idle)
                    }
                    mutableUiEvent.emit(AuthUiEvent.EvaluateNextGate)
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable

                    mutableUiState.update { state ->
                        state.copy(
                            loginStatus =
                                AuthLoginStatus.Error(
                                    message = throwable.message ?: DEFAULT_AUTH_LOGIN_ERROR_MESSAGE,
                                ),
                        )
                    }
                }
            }
    }

    override fun onCleared() {
        loginJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun provideFactory(authLoginRepository: AuthLoginRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                        return AuthViewModel(authLoginRepository = authLoginRepository) as T
                    }

                    error("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}
