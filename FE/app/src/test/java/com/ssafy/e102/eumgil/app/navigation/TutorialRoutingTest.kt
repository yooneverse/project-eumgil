package com.ssafy.e102.eumgil.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TutorialRoutingTest {
    @Test
    fun `tutorial has separate onboarding and guide routes`() {
        assertEquals("tutorial/onboarding", TutorialRoute.Onboarding.route)
        assertEquals("tutorial/guide", TutorialRoute.Guide.route)
    }

    @Test
    fun `tutorial routes hide the top level tab bar`() {
        assertNull(TutorialRoute.Onboarding.route.toCurrentTopLevelRoute())
        assertNull(TutorialRoute.Guide.route.toCurrentTopLevelRoute())
    }

    @Test
    fun `guide completion returns to my page`() {
        assertEquals(TopLevelRoute.MyPage.route, resolveTutorialGuideCompletedRoute())
    }

    @Test
    fun `my page guide action opens guide tutorial`() {
        assertEquals(TutorialRoute.Guide.route, resolveMyPageGuideRoute())
    }

    @Test
    fun `onboarding completion moves to map`() {
        assertEquals(TopLevelRoute.Map.route, resolveTutorialOnboardingCompletedRoute())
    }
}
