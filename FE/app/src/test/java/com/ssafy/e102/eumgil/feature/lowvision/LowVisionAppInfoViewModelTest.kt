package com.ssafy.e102.eumgil.feature.lowvision

import com.ssafy.e102.eumgil.data.repository.AccountWithdrawalRepository
import com.ssafy.e102.eumgil.data.repository.AccountWithdrawalResult
import com.ssafy.e102.eumgil.testing.MainDispatcherRule
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LowVisionAppInfoViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun `withdraw click exposes loading state and emits login navigation on success`() =
        runTest {
            val repository = ControllableLowVisionWithdrawalRepository()
            val viewModel = LowVisionAppInfoViewModel(accountWithdrawalRepository = repository)
            val event = async { viewModel.uiEvent.first() }

            viewModel.onWithdrawClick()
            runCurrent()

            assertEquals(1, repository.withdrawCallCount)
            assertEquals(
                LowVisionAppInfoAccountActionState.Loading,
                viewModel.uiState.value.accountActionState,
            )

            repository.complete(AccountWithdrawalResult.Success(message = "회원탈퇴가 완료되었습니다."))
            runCurrent()

            assertEquals(
                LowVisionAppInfoAccountActionState.Success,
                viewModel.uiState.value.accountActionState,
            )
            assertEquals(LowVisionAppInfoUiEvent.NavigateToLogin, event.await())
        }

    @Test
    fun `withdraw click ignores duplicate taps while loading`() =
        runTest {
            val repository = ControllableLowVisionWithdrawalRepository()
            val viewModel = LowVisionAppInfoViewModel(accountWithdrawalRepository = repository)

            viewModel.onWithdrawClick()
            runCurrent()
            viewModel.onWithdrawClick()
            runCurrent()

            assertEquals(1, repository.withdrawCallCount)
            assertEquals(
                LowVisionAppInfoAccountActionState.Loading,
                viewModel.uiState.value.accountActionState,
            )
        }

    @Test
    fun `withdraw failure emits snackbar and restores idle state`() =
        runTest {
            val repository = ControllableLowVisionWithdrawalRepository()
            val viewModel = LowVisionAppInfoViewModel(accountWithdrawalRepository = repository)
            val event = async { viewModel.uiEvent.first() }
            val states =
                async {
                    viewModel.uiState
                        .map { state -> state.accountActionState }
                        .take(4)
                        .toList()
                }

            viewModel.onWithdrawClick()
            runCurrent()
            repository.complete(AccountWithdrawalResult.Failure(message = "회원탈퇴 처리에 실패했습니다."))
            runCurrent()

            assertEquals(
                listOf(
                    LowVisionAppInfoAccountActionState.Idle,
                    LowVisionAppInfoAccountActionState.Loading,
                    LowVisionAppInfoAccountActionState.Failure("회원탈퇴 처리에 실패했습니다."),
                    LowVisionAppInfoAccountActionState.Idle,
                ),
                states.await(),
            )
            assertEquals(
                LowVisionAppInfoUiEvent.ShowSnackbar(message = "회원탈퇴 처리에 실패했습니다."),
                event.await(),
            )
        }
}

private class ControllableLowVisionWithdrawalRepository : AccountWithdrawalRepository {
    var withdrawCallCount: Int = 0
        private set

    private var continuation: CancellableContinuation<AccountWithdrawalResult>? = null

    override suspend fun withdraw(): AccountWithdrawalResult {
        withdrawCallCount += 1
        return suspendCancellableCoroutine { nextContinuation ->
            continuation = nextContinuation
        }
    }

    fun complete(result: AccountWithdrawalResult) {
        val currentContinuation = requireNotNull(continuation)
        continuation = null
        currentContinuation.resume(result)
    }
}
