package com.ssafy.e102.eumgil.feature.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun ProfileSetupRoute(
    onProfileSetupCompleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var uiState by remember { mutableStateOf(ProfileSetupUiState()) }

    ProfileSetupScreen(
        uiState = uiState,
        onAction = { action ->
            when (action) {
                ProfileSetupUiAction.BackClicked -> Unit
                is ProfileSetupUiAction.NicknameChanged -> {
                    uiState = uiState.copy(
                        draft = uiState.draft.copy(nickname = action.nickname),
                    )
                }
                ProfileSetupUiAction.RetrySubmitClicked,
                ProfileSetupUiAction.SubmitClicked,
                -> {
                    if (uiState.canSubmitLocally) {
                        uiState = uiState.copy(
                            screenState = ProfileSetupScreenState.Completed,
                            submitState = ProfileSetupSubmitState.Completed,
                        )
                        onProfileSetupCompleted()
                    }
                }
                is ProfileSetupUiAction.UserTypeSelected -> {
                    uiState = uiState.copy(
                        draft = uiState.draft.copy(userTypeSelection = action.userTypeSelection),
                    )
                }
            }
        },
        modifier = modifier,
    )
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun ProfileSetupScreen(
    uiState: ProfileSetupUiState,
    onAction: (ProfileSetupUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize())
}
