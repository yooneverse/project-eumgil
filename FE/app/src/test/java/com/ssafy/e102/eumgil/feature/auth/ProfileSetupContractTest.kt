package com.ssafy.e102.eumgil.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSetupContractTest {
    @Test
    fun `default profile setup state stays on local mock boundary`() {
        val uiState = ProfileSetupUiState()

        assertEquals(ProfileSetupCompletionBoundary.LOCAL_MOCK, uiState.completionBoundary)
        assertEquals(ProfileSetupPendingBackendField.entries.toSet(), uiState.pendingBackendFields)
        assertFalse(uiState.canSubmitLocally)
    }

    @Test
    fun `profile setup can submit locally when local draft is complete`() {
        val uiState =
            ProfileSetupUiState(
                draft = ProfileSetupDraft(
                    nickname = "tester",
                    userTypeSelection = ProfileSetupUserTypeSelection.WHEELCHAIR_USER,
                ),
            )

        assertTrue(uiState.canSubmitLocally)
    }
}
