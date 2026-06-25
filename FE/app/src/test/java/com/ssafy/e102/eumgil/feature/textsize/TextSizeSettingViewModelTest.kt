package com.ssafy.e102.eumgil.feature.textsize

import com.ssafy.e102.eumgil.core.model.TextSizePreference
import com.ssafy.e102.eumgil.data.repository.TextSizePreferenceRepository
import com.ssafy.e102.eumgil.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TextSizeSettingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `view model exposes repository preference as selected option`() =
        runTest {
            val repository = FakeTextSizePreferenceRepository(TextSizePreference.LARGE)
            val viewModel = TextSizeSettingViewModel(repository)

            advanceUntilIdle()

            assertEquals(TextSizePreference.LARGE, viewModel.uiState.value.selectedPreference)
            assertEquals(TextSizeSettingOptions, viewModel.uiState.value.options)
        }

    @Test
    fun `selecting a different option saves preference without snackbar feedback`() =
        runTest {
            val repository = FakeTextSizePreferenceRepository(TextSizePreference.DEFAULT)
            val viewModel = TextSizeSettingViewModel(repository)

            viewModel.onAction(TextSizeSettingUiAction.PreferenceSelected(TextSizePreference.EXTRA_LARGE))
            advanceUntilIdle()

            assertEquals(TextSizePreference.EXTRA_LARGE, repository.savedPreference)
            assertEquals(TextSizePreference.EXTRA_LARGE, viewModel.uiState.value.selectedPreference)
            assertEquals(null, withTimeoutOrNull(1) { viewModel.uiEvent.first() })
        }

    @Test
    fun `selecting current option does not save again`() =
        runTest {
            val repository = FakeTextSizePreferenceRepository(TextSizePreference.DEFAULT)
            val viewModel = TextSizeSettingViewModel(repository)
            advanceUntilIdle()

            viewModel.onAction(TextSizeSettingUiAction.PreferenceSelected(TextSizePreference.DEFAULT))
            advanceUntilIdle()

            assertEquals(0, repository.saveCallCount)
        }

    @Test
    fun `back click emits navigation event`() =
        runTest {
            val repository = FakeTextSizePreferenceRepository(TextSizePreference.DEFAULT)
            val viewModel = TextSizeSettingViewModel(repository)
            val event = async { viewModel.uiEvent.first() }
            runCurrent()

            viewModel.onAction(TextSizeSettingUiAction.BackClicked)
            advanceUntilIdle()

            assertSame(TextSizeSettingUiEvent.NavigateBack, event.await())
        }
}

private class FakeTextSizePreferenceRepository(
    initialPreference: TextSizePreference,
) : TextSizePreferenceRepository {
    private val preferenceFlow = MutableStateFlow(initialPreference)
    var savedPreference: TextSizePreference? = null
        private set
    var saveCallCount: Int = 0
        private set

    override fun observeTextSizePreference(): Flow<TextSizePreference> = preferenceFlow

    override suspend fun getTextSizePreference(): TextSizePreference = preferenceFlow.value

    override suspend fun saveTextSizePreference(preference: TextSizePreference) {
        saveCallCount += 1
        savedPreference = preference
        preferenceFlow.value = preference
    }
}
