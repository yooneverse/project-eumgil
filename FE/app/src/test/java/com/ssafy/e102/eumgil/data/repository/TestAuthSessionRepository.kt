package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.AuthGateState
import com.ssafy.e102.eumgil.core.model.AuthSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class TestAuthSessionRepository(
    initialState: AuthGateState,
) : AuthSessionRepository {
    private val authGateState = MutableStateFlow(initialState)

    override fun observeAuthGateState(): Flow<AuthGateState> = authGateState.asStateFlow()

    override suspend fun getAuthGateState(): AuthGateState = authGateState.value

    override suspend fun saveAuthSession(
        authSession: AuthSession,
        isProfileCompleted: Boolean,
    ) {
        authGateState.value =
            authGateState.value.copy(
                authSession = authSession,
                isProfileCompleted = isProfileCompleted,
                signupToken = null,
            )
    }

    override suspend fun saveSignupToken(signupToken: String) {
        authGateState.value =
            authGateState.value.copy(
                authSession = null,
                isProfileCompleted = false,
                signupToken = signupToken,
            )
    }

    override suspend fun clearSignupToken() {
        authGateState.value = authGateState.value.copy(signupToken = null)
    }

    override suspend fun markProfileCompleted() {
        authGateState.value = authGateState.value.copy(isProfileCompleted = true)
    }

    override suspend fun clearAuthSession() {
        authGateState.value = authGateState.value.copy(authSession = null, isProfileCompleted = false)
    }

    suspend fun updateAuthSession(
        authSession: AuthSession?,
        isProfileCompleted: Boolean = authGateState.value.isProfileCompleted,
    ) {
        authGateState.value =
            authGateState.value.copy(
                authSession = authSession,
                isProfileCompleted = isProfileCompleted,
                signupToken = null,
            )
    }
}
