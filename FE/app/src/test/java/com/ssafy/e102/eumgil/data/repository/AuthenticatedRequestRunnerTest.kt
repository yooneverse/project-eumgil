package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.AuthGateState
import com.ssafy.e102.eumgil.core.model.AuthSession
import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import com.ssafy.e102.eumgil.data.remote.datasource.AuthRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.dto.ReissueResponseDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AuthenticatedRequestRunnerTest {
    @Test
    fun `successful request returns without reissue`() =
        runTest {
            val authSessionRepository =
                RecordingRunnerAuthSessionRepository(
                    authGateState =
                        AuthGateState(
                            authSession =
                                AuthSession(
                                    accessToken = "access-token",
                                    refreshToken = "refresh-token",
                                ),
                            isProfileCompleted = true,
                        ),
                )
            val authRemoteDataSource = FakeRunnerAuthRemoteDataSource()
            val runner =
                AuthenticatedRequestRunner(
                    authSessionRepository = authSessionRepository,
                    authRemoteDataSource = authRemoteDataSource,
                )
            var attempts = 0

            val result =
                runner.run(
                    execute = { session: AuthSession ->
                        attempts += 1
                        "token:${session.accessToken}"
                    },
                    isAuthenticationFailure = Throwable::isRunnerAuthenticationFailure,
                )

            assertEquals(1, attempts)
            assertEquals(0, authRemoteDataSource.reissueCallCount)
            assertEquals(
                AuthenticatedRequestResult.Success("token:access-token"),
                result,
            )
        }

    @Test
    fun `authentication failure retries once with rotated tokens and saves refreshed session`() =
        runTest {
            val authSessionRepository =
                RecordingRunnerAuthSessionRepository(
                    authGateState =
                        AuthGateState(
                            authSession =
                                AuthSession(
                                    accessToken = "expired-access-token",
                                    refreshToken = "refresh-token",
                                ),
                            isProfileCompleted = true,
                        ),
                )
            val authRemoteDataSource =
                FakeRunnerAuthRemoteDataSource(
                    reissueResponse =
                        ReissueResponseDto(
                            accessToken = "new-access-token",
                            refreshToken = "new-refresh-token",
                        ),
                )
            val runner =
                AuthenticatedRequestRunner(
                    authSessionRepository = authSessionRepository,
                    authRemoteDataSource = authRemoteDataSource,
                )
            var attempts = 0

            val result =
                runner.run(
                    execute = { session: AuthSession ->
                        attempts += 1
                        when (attempts) {
                            1 -> throw RunnerTestApiException(httpStatusCode = 401)
                            2 -> "token:${session.accessToken}"
                            else -> error("Unexpected attempt count: $attempts")
                        }
                    },
                    isAuthenticationFailure = Throwable::isRunnerAuthenticationFailure,
                )

            assertEquals(2, attempts)
            assertEquals(1, authRemoteDataSource.reissueCallCount)
            assertEquals("refresh-token", authRemoteDataSource.latestRefreshToken)
            assertEquals("new-access-token", authSessionRepository.savedAuthSession?.accessToken)
            assertEquals("new-refresh-token", authSessionRepository.savedAuthSession?.refreshToken)
            assertEquals(true, authSessionRepository.savedIsProfileCompleted)
            assertEquals(
                AuthenticatedRequestResult.Success("token:new-access-token"),
                result,
            )
        }

    @Test
    fun `unauthorized failure without refresh token clears session and returns auth failure`() =
        runTest {
            val authSessionRepository =
                RecordingRunnerAuthSessionRepository(
                    authGateState =
                        AuthGateState(
                            authSession = AuthSession(accessToken = "expired-access-token"),
                            isProfileCompleted = true,
                        ),
                )
            val runner =
                AuthenticatedRequestRunner(
                    authSessionRepository = authSessionRepository,
                    authRemoteDataSource = FakeRunnerAuthRemoteDataSource(),
                )

            val result =
                runner.run(
                    execute = { throw RunnerTestApiException(httpStatusCode = 401) },
                    isAuthenticationFailure = Throwable::isRunnerAuthenticationFailure,
                )

            assertEquals(1, authSessionRepository.clearAuthSessionCallCount)
            assertSame(AuthenticatedRequestResult.AuthenticationFailed, result)
        }

    @Test
    fun `forbidden failure is rethrown without reissue or session clear`() =
        runTest {
            val authSessionRepository =
                RecordingRunnerAuthSessionRepository(
                    authGateState =
                        AuthGateState(
                            authSession =
                                AuthSession(
                                    accessToken = "access-token",
                                    refreshToken = "refresh-token",
                                ),
                            isProfileCompleted = true,
                        ),
                )
            val authRemoteDataSource = FakeRunnerAuthRemoteDataSource()
            val runner =
                AuthenticatedRequestRunner(
                    authSessionRepository = authSessionRepository,
                    authRemoteDataSource = authRemoteDataSource,
                )

            val failure =
                runCatching {
                    runner.run(
                        execute = { throw RunnerTestApiException(httpStatusCode = 403) },
                        isAuthenticationFailure = Throwable::isRunnerAuthenticationFailure,
                    )
                }.exceptionOrNull() as? RunnerTestApiException

            requireNotNull(failure)
            assertEquals(403, failure.httpStatusCode)
            assertEquals(0, authRemoteDataSource.reissueCallCount)
            assertEquals(0, authSessionRepository.clearAuthSessionCallCount)
        }

    @Test
    fun `reissue failure clears session and returns auth failure`() =
        runTest {
            val authSessionRepository =
                RecordingRunnerAuthSessionRepository(
                    authGateState =
                        AuthGateState(
                            authSession =
                                AuthSession(
                                    accessToken = "expired-access-token",
                                    refreshToken = "refresh-token",
                                ),
                            isProfileCompleted = false,
                        ),
                )
            val runner =
                AuthenticatedRequestRunner(
                    authSessionRepository = authSessionRepository,
                    authRemoteDataSource =
                        FakeRunnerAuthRemoteDataSource(
                            reissueThrowable = IllegalStateException("reissue failed"),
                        ),
                )

            val result =
                runner.run(
                    execute = { throw RunnerTestApiException(httpStatusCode = 401) },
                    isAuthenticationFailure = Throwable::isRunnerAuthenticationFailure,
                )

            assertEquals(1, authSessionRepository.clearAuthSessionCallCount)
            assertSame(AuthenticatedRequestResult.AuthenticationFailed, result)
        }

    @Test
    fun `retried request stops after one retry and clears session on repeated auth failure`() =
        runTest {
            val authSessionRepository =
                RecordingRunnerAuthSessionRepository(
                    authGateState =
                        AuthGateState(
                            authSession =
                                AuthSession(
                                    accessToken = "expired-access-token",
                                    refreshToken = "refresh-token",
                                ),
                            isProfileCompleted = true,
                        ),
                )
            val authRemoteDataSource =
                FakeRunnerAuthRemoteDataSource(
                    reissueResponse =
                        ReissueResponseDto(
                            accessToken = "new-access-token",
                            refreshToken = "new-refresh-token",
                        ),
                )
            val runner =
                AuthenticatedRequestRunner(
                    authSessionRepository = authSessionRepository,
                    authRemoteDataSource = authRemoteDataSource,
                )
            var attempts = 0

            val result =
                runner.run(
                    execute = {
                        attempts += 1
                        throw RunnerTestApiException(httpStatusCode = 401)
                    },
                    isAuthenticationFailure = Throwable::isRunnerAuthenticationFailure,
                )

            assertEquals(2, attempts)
            assertEquals(1, authRemoteDataSource.reissueCallCount)
            assertEquals(1, authSessionRepository.clearAuthSessionCallCount)
            assertSame(AuthenticatedRequestResult.AuthenticationFailed, result)
        }
}

private class RecordingRunnerAuthSessionRepository(
    private val authGateState: AuthGateState,
) : AuthSessionRepository {
    var savedAuthSession: AuthSession? = null
        private set
    var savedIsProfileCompleted: Boolean? = null
        private set
    var clearAuthSessionCallCount: Int = 0
        private set

    override fun observeAuthGateState(): Flow<AuthGateState> = emptyFlow()

    override suspend fun getAuthGateState(): AuthGateState = authGateState

    override suspend fun saveAuthSession(
        authSession: AuthSession,
        isProfileCompleted: Boolean,
    ) {
        savedAuthSession = authSession
        savedIsProfileCompleted = isProfileCompleted
    }

    override suspend fun saveSignupToken(signupToken: String) = Unit

    override suspend fun clearSignupToken() = Unit

    override suspend fun markProfileCompleted() = Unit

    override suspend fun clearAuthSession() {
        clearAuthSessionCallCount += 1
    }
}

private class FakeRunnerAuthRemoteDataSource(
    private val reissueResponse: ReissueResponseDto? = null,
    private val reissueThrowable: Throwable? = null,
) : AuthRemoteDataSource(httpJsonClient = HttpJsonClient(baseUrl = "https://example.com")) {
    var latestRefreshToken: String? = null
        private set
    var reissueCallCount: Int = 0
        private set

    override suspend fun reissue(refreshToken: String): ReissueResponseDto {
        reissueCallCount += 1
        latestRefreshToken = refreshToken
        reissueThrowable?.let { throw it }
        return checkNotNull(reissueResponse)
    }
}

private class RunnerTestApiException(
    val httpStatusCode: Int,
) : RuntimeException("HTTP $httpStatusCode")

private fun Throwable.isRunnerAuthenticationFailure(): Boolean =
    this is RunnerTestApiException && httpStatusCode == 401
