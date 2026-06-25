package com.ssafy.e102.eumgil.feature.lowvision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ssafy.e102.eumgil.data.repository.AccountWithdrawalRepository
import com.ssafy.e102.eumgil.data.repository.AccountWithdrawalResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LowVisionAppInfoUiState(
    val accountActionState: LowVisionAppInfoAccountActionState = LowVisionAppInfoAccountActionState.Idle,
) {
    val isWithdrawLoading: Boolean
        get() = accountActionState == LowVisionAppInfoAccountActionState.Loading
}

sealed interface LowVisionAppInfoUiEvent {
    data object NavigateToLogin : LowVisionAppInfoUiEvent

    data class ShowSnackbar(
        val message: String,
    ) : LowVisionAppInfoUiEvent
}

sealed interface LowVisionAppInfoAccountActionState {
    data object Idle : LowVisionAppInfoAccountActionState

    data object Loading : LowVisionAppInfoAccountActionState

    data object Success : LowVisionAppInfoAccountActionState

    data class Failure(
        val message: String,
    ) : LowVisionAppInfoAccountActionState
}

class LowVisionAppInfoViewModel(
    private val accountWithdrawalRepository: AccountWithdrawalRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(LowVisionAppInfoUiState())
    val uiState: StateFlow<LowVisionAppInfoUiState> = mutableUiState.asStateFlow()

    private val mutableUiEvent = MutableSharedFlow<LowVisionAppInfoUiEvent>()
    val uiEvent: SharedFlow<LowVisionAppInfoUiEvent> = mutableUiEvent.asSharedFlow()

    private var withdrawJob: Job? = null

    fun onWithdrawClick() {
        if (mutableUiState.value.isWithdrawLoading) return

        withdrawJob?.cancel()
        withdrawJob =
            viewModelScope.launch {
                mutableUiState.update { state ->
                    state.copy(accountActionState = LowVisionAppInfoAccountActionState.Loading)
                }

                when (val result = accountWithdrawalRepository.withdraw()) {
                    is AccountWithdrawalResult.Success -> {
                        mutableUiState.update { state ->
                            state.copy(accountActionState = LowVisionAppInfoAccountActionState.Success)
                        }
                        mutableUiEvent.emit(LowVisionAppInfoUiEvent.NavigateToLogin)
                    }
                    AccountWithdrawalResult.MissingSession,
                    AccountWithdrawalResult.AuthenticationFailed,
                    -> {
                        mutableUiState.update { state ->
                            state.copy(accountActionState = LowVisionAppInfoAccountActionState.Idle)
                        }
                        mutableUiEvent.emit(LowVisionAppInfoUiEvent.NavigateToLogin)
                    }
                    is AccountWithdrawalResult.Failure -> {
                        mutableUiState.update { state ->
                            state.copy(
                                accountActionState = LowVisionAppInfoAccountActionState.Failure(result.message),
                            )
                        }
                        mutableUiEvent.emit(LowVisionAppInfoUiEvent.ShowSnackbar(message = result.message))
                        mutableUiState.update { state ->
                            state.copy(accountActionState = LowVisionAppInfoAccountActionState.Idle)
                        }
                    }
                }
            }
    }

    override fun onCleared() {
        withdrawJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun provideFactory(accountWithdrawalRepository: AccountWithdrawalRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(LowVisionAppInfoViewModel::class.java)) {
                        return LowVisionAppInfoViewModel(
                            accountWithdrawalRepository = accountWithdrawalRepository,
                        ) as T
                    }

                    error("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}
