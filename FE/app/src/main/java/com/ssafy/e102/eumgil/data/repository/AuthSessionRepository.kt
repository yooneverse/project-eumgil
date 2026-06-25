package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.AuthGateState
import com.ssafy.e102.eumgil.core.model.AuthSession
import com.ssafy.e102.eumgil.data.local.datasource.AuthSessionLocalDataSource
import kotlinx.coroutines.flow.Flow

interface AuthSessionRepository {
    fun observeAuthGateState(): Flow<AuthGateState>

    suspend fun getAuthGateState(): AuthGateState

    suspend fun saveAuthSession(
        authSession: AuthSession,
        isProfileCompleted: Boolean,
    )

    suspend fun saveSignupToken(signupToken: String)

    suspend fun clearSignupToken()

    suspend fun markProfileCompleted()

    suspend fun clearAuthSession()
}

class DefaultAuthSessionRepository(
    private val authSessionLocalDataSource: AuthSessionLocalDataSource,
) : AuthSessionRepository {
    override fun observeAuthGateState(): Flow<AuthGateState> =
        authSessionLocalDataSource.observeAuthGateState()

    override suspend fun getAuthGateState(): AuthGateState =
        authSessionLocalDataSource.getAuthGateState()

    override suspend fun saveAuthSession(
        authSession: AuthSession,
        isProfileCompleted: Boolean,
    ) {
        authSessionLocalDataSource.saveAuthSession(
            authSession = authSession,
            isProfileCompleted = isProfileCompleted,
        )
    }

    override suspend fun saveSignupToken(signupToken: String) {
        authSessionLocalDataSource.saveSignupToken(signupToken = signupToken)
    }

    override suspend fun clearSignupToken() {
        authSessionLocalDataSource.clearSignupToken()
    }

    override suspend fun markProfileCompleted() {
        authSessionLocalDataSource.markProfileCompleted()
    }

    override suspend fun clearAuthSession() {
        authSessionLocalDataSource.clearAuthSession()
    }
}
