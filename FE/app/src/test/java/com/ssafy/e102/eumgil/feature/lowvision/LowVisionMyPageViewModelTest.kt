package com.ssafy.e102.eumgil.feature.lowvision

import com.ssafy.e102.eumgil.data.repository.AuthLogoutRepository
import com.ssafy.e102.eumgil.data.repository.AuthLogoutResult
import com.ssafy.e102.eumgil.testing.MainDispatcherRule
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LowVisionMyPageViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun `logout failure keeps low vision my page active and emits snackbar`() =
        runTest {
            val repository = ControllableLowVisionLogoutRepository()
            val viewModel = LowVisionMyPageViewModel(authLogoutRepository = repository)
            val event = async { viewModel.uiEvent.first() }

            viewModel.onLogoutClick()
            runCurrent()
            repository.complete(AuthLogoutResult.Failure(message = "로그아웃 처리에 실패했습니다."))
            runCurrent()

            assertEquals(false, viewModel.uiState.value.isLogoutLoading)
            assertEquals(
                LowVisionMyPageUiEvent.ShowSnackbar(message = "로그아웃 처리에 실패했습니다."),
                event.await(),
            )
        }
}

private class ControllableLowVisionLogoutRepository : AuthLogoutRepository {
    private var continuation: CancellableContinuation<AuthLogoutResult>? = null

    override suspend fun logout(): AuthLogoutResult =
        kotlinx.coroutines.suspendCancellableCoroutine { nextContinuation ->
            continuation = nextContinuation
        }

    fun complete(result: AuthLogoutResult) {
        val currentContinuation = requireNotNull(continuation)
        continuation = null
        currentContinuation.resume(result)
    }
}
