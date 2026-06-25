package com.ssafy.e102.eumgil.feature.tutorial

import com.ssafy.e102.eumgil.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TutorialContractTest {
    @Test
    fun `tutorial exposes three ordered mobility steps`() {
        assertEquals(
            listOf(
                TutorialStep.DESTINATION,
                TutorialStep.ROUTE_COMPARISON,
                TutorialStep.REPORT,
            ),
            TutorialStep.entries.sortedBy(TutorialStep::sequence),
        )
    }

    @Test
    fun `tutorial step advances until report step`() {
        assertEquals(TutorialStep.ROUTE_COMPARISON, TutorialStep.DESTINATION.next())
        assertEquals(TutorialStep.REPORT, TutorialStep.ROUTE_COMPARISON.next())
        assertNull(TutorialStep.REPORT.next())
    }

    @Test
    fun `tutorial step moves back until destination step`() {
        assertNull(TutorialStep.DESTINATION.previous())
        assertEquals(TutorialStep.DESTINATION, TutorialStep.ROUTE_COMPARISON.previous())
        assertEquals(TutorialStep.ROUTE_COMPARISON, TutorialStep.REPORT.previous())
    }

    @Test
    fun `tutorial ui state exposes whether panel can move next`() {
        assertEquals(
            true,
            TutorialUiState(
                step = TutorialStep.DESTINATION,
                entryPoint = TutorialEntryPoint.ONBOARDING,
            ).canMoveNext,
        )
        assertEquals(
            false,
            TutorialUiState(
                step = TutorialStep.REPORT,
                entryPoint = TutorialEntryPoint.ONBOARDING,
            ).canMoveNext,
        )
    }

    @Test
    fun `onboarding final action starts the app`() {
        assertEquals(
            R.string.tutorial_action_start,
            resolveTutorialPrimaryActionLabel(
                entryPoint = TutorialEntryPoint.ONBOARDING,
                isLastStep = true,
            ),
        )
    }

    @Test
    fun `guide final action closes the guide`() {
        assertEquals(
            R.string.tutorial_action_close,
            resolveTutorialPrimaryActionLabel(
                entryPoint = TutorialEntryPoint.GUIDE,
                isLastStep = true,
            ),
        )
    }

    @Test
    fun `non final action moves to next step`() {
        assertEquals(
            R.string.action_next_step,
            resolveTutorialPrimaryActionLabel(
                entryPoint = TutorialEntryPoint.ONBOARDING,
                isLastStep = false,
            ),
        )
    }
}
