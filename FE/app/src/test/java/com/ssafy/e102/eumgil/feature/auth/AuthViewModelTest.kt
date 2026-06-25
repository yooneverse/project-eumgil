package com.ssafy.e102.eumgil.feature.auth

import com.ssafy.e102.eumgil.data.repository.AuthLoginRepository
import com.ssafy.e102.eumgil.data.repository.AuthLoginRequest
import com.ssafy.e102.eumgil.testing.MainDispatcherRule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun `social login click exposes provider loading state and emits next gate event on success`() =
        runTest {
            val repository = ControllableAuthLoginRepository()
            val viewModel = AuthViewModel(authLoginRepository = repository)
            val event = async { viewModel.uiEvent.first() }

            viewModel.onAction(AuthUiAction.SocialLoginClicked(AuthLoginProviderUiKeys.GOOGLE))
            runCurrent()

            assertEquals(1, repository.loginCallCount)
            assertEquals(AuthLoginProviderUiKeys.GOOGLE, repository.latestRequest?.providerKey)
            assertEquals(
                AuthLoginStatus.Loading(providerKey = AuthLoginProviderUiKeys.GOOGLE),
                viewModel.uiState.value.loginStatus,
            )

            repository.complete()
            runCurrent()

            assertEquals(AuthLoginStatus.Idle, viewModel.uiState.value.loginStatus)
            assertEquals(AuthUiEvent.EvaluateNextGate, event.await())
        }

    @Test
    fun `social login click ignores duplicate clicks while loading`() =
        runTest {
            val repository = ControllableAuthLoginRepository()
            val viewModel = AuthViewModel(authLoginRepository = repository)

            viewModel.onAction(AuthUiAction.SocialLoginClicked(AuthLoginProviderUiKeys.GOOGLE))
            runCurrent()
            viewModel.onAction(AuthUiAction.SocialLoginClicked(AuthLoginProviderUiKeys.NAVER))
            runCurrent()

            assertEquals(1, repository.loginCallCount)
            assertEquals(
                AuthLoginStatus.Loading(providerKey = AuthLoginProviderUiKeys.GOOGLE),
                viewModel.uiState.value.loginStatus,
            )
        }

    @Test
    fun `social login failure exposes error state`() =
        runTest {
            val repository = ControllableAuthLoginRepository()
            val viewModel = AuthViewModel(authLoginRepository = repository)

            viewModel.onAction(AuthUiAction.SocialLoginClicked(AuthLoginProviderUiKeys.KAKAO))
            runCurrent()
            repository.fail(IllegalStateException("임시 로그인 처리에 실패했습니다."))
            runCurrent()

            val loginStatus = viewModel.uiState.value.loginStatus
            assertTrue(loginStatus is AuthLoginStatus.Error)
            assertEquals(
                "임시 로그인 처리에 실패했습니다.",
                (loginStatus as AuthLoginStatus.Error).message,
            )
        }

    @Test
    fun `retry after failure starts a new provider loading flow`() =
        runTest {
            val repository = ControllableAuthLoginRepository()
            val viewModel = AuthViewModel(authLoginRepository = repository)

            viewModel.onAction(AuthUiAction.SocialLoginClicked(AuthLoginProviderUiKeys.KAKAO))
            runCurrent()
            repository.fail(IllegalStateException("임시 로그인 처리에 실패했습니다."))
            runCurrent()
            viewModel.onAction(AuthUiAction.SocialLoginClicked(AuthLoginProviderUiKeys.NAVER))
            runCurrent()

            assertEquals(2, repository.loginCallCount)
            assertEquals(AuthLoginProviderUiKeys.NAVER, repository.latestRequest?.providerKey)
            assertEquals(
                AuthLoginStatus.Loading(providerKey = AuthLoginProviderUiKeys.NAVER),
                viewModel.uiState.value.loginStatus,
            )
        }

    @Test
    fun `default UI state exposes only supported auth ui providers`() {
        val providerKeys = AuthUiState().providers.map { provider -> provider.key }

        assertEquals(
            listOf(
                AuthLoginProviderUiKeys.GOOGLE,
                AuthLoginProviderUiKeys.NAVER,
                AuthLoginProviderUiKeys.KAKAO,
            ),
            providerKeys,
        )
    }

    @Test
    fun `unknown provider key exposes default error without repository call`() =
        runTest {
            val repository = ControllableAuthLoginRepository()
            val viewModel = AuthViewModel(authLoginRepository = repository)

            viewModel.onAction(AuthUiAction.SocialLoginClicked("unknown-provider"))
            runCurrent()

            assertEquals(0, repository.loginCallCount)
            assertEquals(
                AuthLoginStatus.Error(DEFAULT_AUTH_LOGIN_ERROR_MESSAGE),
                viewModel.uiState.value.loginStatus,
            )
        }
}

private class ControllableAuthLoginRepository : AuthLoginRepository {
    var loginCallCount: Int = 0
        private set
    var latestRequest: AuthLoginRequest? = null
        private set

    private var continuation: CancellableContinuation<Unit>? = null

    override suspend fun login(request: AuthLoginRequest) {
        loginCallCount += 1
        latestRequest = request
        suspendCancellableCoroutine { nextContinuation ->
            continuation = nextContinuation
        }
    }

    fun complete() {
        val currentContinuation = requireNotNull(continuation)
        continuation = null
        currentContinuation.resume(Unit)
    }

    fun fail(throwable: Throwable) {
        val currentContinuation = requireNotNull(continuation)
        continuation = null
        currentContinuation.resumeWithException(throwable)
    }
}
