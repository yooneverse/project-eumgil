package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.AuthGateState
import com.ssafy.e102.eumgil.core.model.AuthSession
import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import com.ssafy.e102.eumgil.data.remote.datasource.AuthApiException
import com.ssafy.e102.eumgil.data.remote.datasource.AuthRemoteDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthLogoutRepositoryTest {
    @Test
    fun `logout success calls remote api and clears auth session`() =
        runTest {
            val authSessionRepository =
                RecordingLogoutAuthSessionRepository(
                    authGateState =
                        AuthGateState(
                            authSession =
                                AuthSession(
                                    accessToken = "access-token",
                                    refreshToken = "refresh-token",
                                    userId = "existing-user-id",
                                ),
                            isProfileCompleted = true,
                        ),
                )
            val remoteDataSource =
                FakeLogoutAuthRemoteDataSource(
                    logoutMessage = "로그아웃되었습니다.",
                )
            val localCacheCleaner = RecordingAccountScopedLocalCacheCleaner()
            val repository =
                ServerAuthLogoutRepository(
                    authRemoteDataSource = remoteDataSource,
                    authSessionRepository = authSessionRepository,
                    localCacheCleaner = localCacheCleaner,
                )

            val result = repository.logout()

            assertEquals("access-token", remoteDataSource.latestLogoutAccessToken)
            assertTrue(authSessionRepository.clearAuthSessionCalled)
            assertTrue(localCacheCleaner.clearCalled)
            assertEquals(AuthLogoutResult.Success(message = "로그아웃되었습니다."), result)
        }

    @Test
    fun `logout failure keeps auth session and returns failure`() =
        runTest {
            val authSessionRepository =
                RecordingLogoutAuthSessionRepository(
                    authGateState =
                        AuthGateState(
                            authSession =
                                AuthSession(
                                    accessToken = "access-token",
                                    refreshToken = "refresh-token",
                                    userId = "existing-user-id",
                                ),
                            isProfileCompleted = true,
                        ),
                )
            val remoteDataSource =
                FakeLogoutAuthRemoteDataSource(
                    exception =
                        AuthApiException(
                            httpStatusCode = 500,
                            status = "A5000",
                            message = "로그아웃 처리에 실패했습니다.",
                        ),
                )
            val repository =
                ServerAuthLogoutRepository(
                    authRemoteDataSource = remoteDataSource,
                    authSessionRepository = authSessionRepository,
                    localCacheCleaner = RecordingAccountScopedLocalCacheCleaner(),
                )

            val result = repository.logout()

            assertEquals(AuthLogoutResult.Failure(message = "로그아웃 처리에 실패했습니다."), result)
            assertFalse(authSessionRepository.clearAuthSessionCalled)
        }

    @Test
    fun `logout authentication failure clears auth session and returns authentication failed`() =
        runTest {
            val authSessionRepository =
                RecordingLogoutAuthSessionRepository(
                    authGateState =
                        AuthGateState(
                            authSession =
                                AuthSession(
                                    accessToken = "expired-access-token",
                                    refreshToken = "refresh-token",
                                    userId = "existing-user-id",
                                ),
                            isProfileCompleted = true,
                        ),
                )
            val remoteDataSource =
                FakeLogoutAuthRemoteDataSource(
                    exception =
                        AuthApiException(
                            httpStatusCode = 401,
                            status = "A4010",
                            message = "인증이 필요합니다.",
                        ),
                )
            val localCacheCleaner = RecordingAccountScopedLocalCacheCleaner()
            val repository =
                ServerAuthLogoutRepository(
                    authRemoteDataSource = remoteDataSource,
                    authSessionRepository = authSessionRepository,
                    localCacheCleaner = localCacheCleaner,
                )

            val result = repository.logout()

            assertEquals(AuthLogoutResult.AuthenticationFailed, result)
            assertTrue(authSessionRepository.clearAuthSessionCalled)
            assertTrue(localCacheCleaner.clearCalled)
        }

    @Test
    fun `logout missing session skips remote api and returns missing session`() =
        runTest {
            val authSessionRepository =
                RecordingLogoutAuthSessionRepository(
                    authGateState = AuthGateState(authSession = null, isProfileCompleted = false),
                )
            val remoteDataSource = FakeLogoutAuthRemoteDataSource(logoutMessage = "unused")
            val repository =
                ServerAuthLogoutRepository(
                    authRemoteDataSource = remoteDataSource,
                    authSessionRepository = authSessionRepository,
                    localCacheCleaner = RecordingAccountScopedLocalCacheCleaner(),
                )

            val result = repository.logout()

            assertEquals(AuthLogoutResult.MissingSession, result)
            assertEquals(null, remoteDataSource.latestLogoutAccessToken)
            assertFalse(authSessionRepository.clearAuthSessionCalled)
        }
}

private class FakeLogoutAuthRemoteDataSource(
    private val logoutMessage: String? = null,
    private val exception: Throwable? = null,
) : AuthRemoteDataSource(httpJsonClient = HttpJsonClient(baseUrl = "https://example.com")) {
    var latestLogoutAccessToken: String? = null
        private set

    override suspend fun logout(accessToken: String): String {
        latestLogoutAccessToken = accessToken
        exception?.let { throw it }
        return checkNotNull(logoutMessage)
    }
}

private class RecordingLogoutAuthSessionRepository(
    private val authGateState: AuthGateState,
) : AuthSessionRepository {
    var clearAuthSessionCalled: Boolean = false
        private set

    override fun observeAuthGateState(): Flow<AuthGateState> = emptyFlow()

    override suspend fun getAuthGateState(): AuthGateState = authGateState

    override suspend fun saveAuthSession(
        authSession: AuthSession,
        isProfileCompleted: Boolean,
    ) = Unit

    override suspend fun saveSignupToken(signupToken: String) = Unit

    override suspend fun clearSignupToken() = Unit

    override suspend fun markProfileCompleted() = Unit

    override suspend fun clearAuthSession() {
        clearAuthSessionCalled = true
    }
}

private class RecordingAccountScopedLocalCacheCleaner : AccountScopedLocalCacheCleaner {
    var clearCalled: Boolean = false
        private set

    override suspend fun clearCurrentAccountCache() {
        clearCalled = true
    }
}
