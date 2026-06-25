package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.AuthSession
import com.ssafy.e102.eumgil.data.remote.datasource.AuthRemoteDataSource
import kotlin.coroutines.cancellation.CancellationException

sealed interface AuthenticatedRequestResult<out T> {
    data class Success<T>(
        val value: T,
    ) : AuthenticatedRequestResult<T>

    data object MissingSession : AuthenticatedRequestResult<Nothing>

    data object AuthenticationFailed : AuthenticatedRequestResult<Nothing>
}

class AuthenticatedRequestRunner(
    private val authSessionRepository: AuthSessionRepository,
    private val authRemoteDataSource: AuthRemoteDataSource,
) {
    suspend fun <T> run(
        execute: suspend (AuthSession) -> T,
        isAuthenticationFailure: (Throwable) -> Boolean,
    ): AuthenticatedRequestResult<T> {
        val authGateState = authSessionRepository.getAuthGateState()
        val authSession = authGateState.authSession ?: return AuthenticatedRequestResult.MissingSession

        return try {
            AuthenticatedRequestResult.Success(execute(authSession))
        } catch (throwable: Throwable) {
            throwable.throwIfCancellation()
            if (!isAuthenticationFailure(throwable)) {
                throw throwable
            }

            val refreshToken = authSession.refreshToken
            if (refreshToken.isNullOrBlank()) {
                authSessionRepository.clearAuthSession()
                return AuthenticatedRequestResult.AuthenticationFailed
            }

            val refreshedSession =
                try {
                    val reissueResponse = authRemoteDataSource.reissue(refreshToken = refreshToken)
                    authSession.copy(
                        accessToken = reissueResponse.accessToken,
                        refreshToken = reissueResponse.refreshToken,
                    )
                } catch (reissueThrowable: Throwable) {
                    reissueThrowable.throwIfCancellation()
                    authSessionRepository.clearAuthSession()
                    return AuthenticatedRequestResult.AuthenticationFailed
                }

            authSessionRepository.saveAuthSession(
                authSession = refreshedSession,
                isProfileCompleted = authGateState.isProfileCompleted,
            )

            try {
                AuthenticatedRequestResult.Success(execute(refreshedSession))
            } catch (retryThrowable: Throwable) {
                retryThrowable.throwIfCancellation()
                if (isAuthenticationFailure(retryThrowable)) {
                    authSessionRepository.clearAuthSession()
                    AuthenticatedRequestResult.AuthenticationFailed
                } else {
                    throw retryThrowable
                }
            }
        }
    }
}

private fun Throwable.throwIfCancellation() {
    if (this is CancellationException) {
        throw this
    }
}
