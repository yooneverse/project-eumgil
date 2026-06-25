package com.ssafy.e102.eumgil.feature.voiceassistant

import com.ssafy.e102.eumgil.app.navigation.TopLevelRoute
import com.ssafy.e102.eumgil.data.repository.RouteEditingTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAssistantContractTest {
    @Test
    fun `context keeps route top level user type and editing target independently`() {
        val context =
            VoiceAssistantContext(
                currentRoute = "search/results/{query}",
                currentTopLevelRoute = TopLevelRoute.Map.route,
                userType = VoiceAssistantUserType.MOBILITY_IMPAIRED,
                editingTarget = RouteEditingTarget.ORIGIN,
            )

        assertEquals("search/results/{query}", context.currentRoute)
        assertEquals(TopLevelRoute.Map.route, context.currentTopLevelRoute)
        assertEquals(VoiceAssistantUserType.MOBILITY_IMPAIRED, context.userType)
        assertEquals(RouteEditingTarget.ORIGIN, context.editingTarget)
    }

    @Test
    fun `default context does not collapse to a source route string`() {
        val context = VoiceAssistantContext()

        assertNull(context.currentRoute)
        assertNull(context.currentTopLevelRoute)
        assertNull(context.userType)
        assertEquals(RouteEditingTarget.DESTINATION, context.editingTarget)
    }

    @Test
    fun `assistant actions expose confirmation policy`() {
        assertFalse(VoiceAssistantAction.SearchPlace(query = "Busan Station").requiresConfirmation)
        assertFalse(VoiceAssistantAction.OpenReport().requiresConfirmation)
        assertFalse(VoiceAssistantAction.OpenSavedRoutes().requiresConfirmation)
        assertFalse(VoiceAssistantAction.OpenMyPage().requiresConfirmation)
        assertFalse(VoiceAssistantAction.OpenMap().requiresConfirmation)
        assertTrue(VoiceAssistantAction.StopNavigation().requiresConfirmation)
        assertFalse(VoiceAssistantAction.ResumeNavigationGuidance().requiresConfirmation)
        assertFalse(VoiceAssistantAction.UnknownCommand(rawCommand = "unknown").requiresConfirmation)
    }

    @Test
    fun `search is represented by one SearchPlace action`() {
        val action =
            VoiceAssistantAction.SearchPlace(
                query = "Busan City Hall",
                editingTarget = RouteEditingTarget.ORIGIN,
                requiresConfirmation = true,
            )

        assertEquals("Busan City Hall", action.query)
        assertEquals(RouteEditingTarget.ORIGIN, action.editingTarget)
        assertTrue(action.requiresConfirmation)
    }

    @Test
    fun `ui event can carry actions without nav controller dependency`() {
        val event = UiEvent.ExecuteAction(VoiceAssistantAction.OpenReport())

        assertEquals(VoiceAssistantAction.OpenReport(), event.action)
    }
}
