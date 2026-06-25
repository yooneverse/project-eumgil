package com.ssafy.e102.eumgil.app.navigation

import com.ssafy.e102.eumgil.feature.lowvision.LowVisionBottomTab
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LowVisionNavGraphRoutingTest {
    @Test
    fun `recording completion moves to low vision search results`() {
        assertEquals(
            LowVisionRoute.Search.route,
            resolveLowVisionRecordingCompletedRoute(),
        )
        assertEquals(
            LowVisionRoute.VoiceInput.route,
            resolveLowVisionRecordingPopUpRoute(),
        )
    }

    @Test
    fun `voice input cancel returns to low vision home`() {
        assertEquals(
            LowVisionRoute.Home.route,
            resolveLowVisionVoiceInputCancelRoute(),
        )
    }

    @Test
    fun `low vision search result uses low vision guidance route`() {
        assertEquals(
            LowVisionRoute.Guidance.route,
            resolveLowVisionSearchResultRoute(),
        )
        assertEquals(
            LowVisionRoute.Search.route,
            resolveLowVisionSearchPopUpRoute(),
        )
        assertEquals(
            LowVisionRoute.CategorySearch.route,
            resolveLowVisionSearchPopUpRoute(LowVisionBottomTab.CATEGORY),
        )
    }

    @Test
    fun `navigation completion moves to arrival screen`() {
        assertEquals(
            ArrivalRoute.Entry.route,
            resolveNavigationCompletionRoute(),
        )
    }

    @Test
    fun `low vision navigation completion moves to low vision complete screen`() {
        assertEquals(
            LowVisionRoute.NavigationComplete.route,
            resolveNavigationCompletionRoute(selectedPrimaryUserType = "low_vision"),
        )
    }

    @Test
    fun `low vision navigation exit moves to low vision complete screen`() {
        assertEquals(
            LowVisionRoute.NavigationComplete.route,
            resolveLowVisionNavigationExitRoute(),
        )
    }

    @Test
    fun `low vision navigation complete done moves to low vision home`() {
        assertEquals(
            LowVisionRoute.Home.route,
            resolveLowVisionNavigationCompleteDoneRoute(),
        )
    }

    @Test
    fun `navigation bookmark save returns to low vision bookmark for low vision user`() {
        assertEquals(
            LowVisionRoute.Bookmark.route,
            resolveNavigationSavedRoute(selectedPrimaryUserType = "low_vision"),
        )
        assertEquals(
            TopLevelRoute.SavedRoute.route,
            resolveNavigationSavedRoute(selectedPrimaryUserType = "mobility_impaired"),
        )
    }

    @Test
    fun `search result briefing uses low vision route only for low vision user`() {
        assertEquals(
            LowVisionRoute.RouteBriefing.route,
            resolveSearchResultBriefingRoute(selectedPrimaryUserType = "low_vision"),
        )
        assertEquals(
            TopLevelRoute.Map.route,
            resolveSearchResultBriefingRoute(selectedPrimaryUserType = "mobility_impaired"),
        )
        assertEquals(
            TopLevelRoute.Map.route,
            resolveSearchResultBriefingRoute(selectedPrimaryUserType = null),
        )
    }

    @Test
    fun `current location action stays in low vision home until routing is wired`() {
        assertNull(resolveLowVisionCurrentLocationRoute())
    }

    @Test
    fun `low vision uses dedicated navigation ui only for low vision user type`() {
        assertEquals(true, shouldUseLowVisionNavigationUi(selectedPrimaryUserType = "low_vision"))
        assertEquals(false, shouldUseLowVisionNavigationUi(selectedPrimaryUserType = "mobility_impaired"))
        assertEquals(false, shouldUseLowVisionNavigationUi(selectedPrimaryUserType = null))
    }

    @Test
    fun `low vision bottom tabs resolve to app destinations`() {
        assertEquals(LowVisionRoute.Home.route, resolveLowVisionBottomTabRoute(LowVisionBottomTab.HOME))
        assertEquals(LowVisionRoute.Bookmark.route, resolveLowVisionBottomTabRoute(LowVisionBottomTab.BOOKMARK))
        assertEquals(LowVisionRoute.CategorySearch.route, resolveLowVisionBottomTabRoute(LowVisionBottomTab.CATEGORY))
        assertEquals(LowVisionRoute.MyPage.route, resolveLowVisionBottomTabRoute(LowVisionBottomTab.MY_PAGE))
    }

    @Test
    fun `low vision category result route carries selected category`() {
        assertEquals(
            "low_vision/category_result/%ED%99%94%EC%9E%A5%EC%8B%A4",
            LowVisionRoute.CategoryResult.createRoute("화장실"),
        )
    }

    @Test
    fun `low vision category result decodes selected category before search`() {
        assertEquals(
            "음식점",
            decodeLowVisionCategoryRouteArgument("%EC%9D%8C%EC%8B%9D%EC%A0%90"),
        )
        assertEquals(
            "숙박시설",
            decodeLowVisionCategoryRouteArgument("숙박시설"),
        )
    }

    @Test
    fun `low vision selected tab follows current route`() {
        assertEquals(LowVisionBottomTab.HOME, resolveLowVisionSelectedBottomTab(LowVisionRoute.Home.route))
        assertEquals(LowVisionBottomTab.HOME, resolveLowVisionSelectedBottomTab(LowVisionRoute.VoiceInput.route))
        assertEquals(LowVisionBottomTab.HOME, resolveLowVisionSelectedBottomTab(LowVisionRoute.Search.route))
        assertEquals(LowVisionBottomTab.HOME, resolveLowVisionSelectedBottomTab(LowVisionRoute.RouteBriefing.route))
        assertEquals(LowVisionBottomTab.HOME, resolveLowVisionSelectedBottomTab(LowVisionRoute.Guidance.route))
        assertEquals(LowVisionBottomTab.HOME, resolveLowVisionSelectedBottomTab(LowVisionRoute.NavigationComplete.route))
        assertEquals(LowVisionBottomTab.BOOKMARK, resolveLowVisionSelectedBottomTab(LowVisionRoute.Bookmark.route))
        assertEquals(LowVisionBottomTab.CATEGORY, resolveLowVisionSelectedBottomTab(LowVisionRoute.CategorySearch.route))
        assertEquals(LowVisionBottomTab.CATEGORY, resolveLowVisionSelectedBottomTab(LowVisionRoute.CategoryResult.route))
        assertEquals(LowVisionBottomTab.MY_PAGE, resolveLowVisionSelectedBottomTab(LowVisionRoute.MyPage.route))
        assertEquals(LowVisionBottomTab.MY_PAGE, resolveLowVisionSelectedBottomTab(LowVisionRoute.AppInfo.route))
        assertEquals(LowVisionBottomTab.MY_PAGE, resolveLowVisionSelectedBottomTab(LowVisionRoute.TextSize.route))
    }

    @Test
    fun `low vision bottom tab ignores already selected tab`() {
        assertEquals(
            false,
            shouldNavigateLowVisionBottomTab(
                currentRoute = LowVisionRoute.Bookmark.route,
                selectedTab = LowVisionBottomTab.BOOKMARK,
            ),
        )
        assertEquals(
            false,
            shouldNavigateLowVisionBottomTab(
                currentRoute = LowVisionRoute.AppInfo.route,
                selectedTab = LowVisionBottomTab.MY_PAGE,
            ),
        )
        assertEquals(
            false,
            shouldNavigateLowVisionBottomTab(
                currentRoute = LowVisionRoute.Search.route,
                selectedTab = LowVisionBottomTab.HOME,
            ),
        )
    }

    @Test
    fun `low vision route changes use instant destination transitions`() {
        assertEquals(true, shouldUseInstantLowVisionDestinationTransitions())
    }

    @Test
    fun `low vision kws stays disabled on initial graph entry until voice input is explicitly opened`() {
        assertEquals(
            false,
            shouldAutoResumeLowVisionKws(
                autoResumeEnabled = false,
                currentRoute = LowVisionRoute.Home.route,
            ),
        )
    }

    @Test
    fun `low vision kws resumes after activation on non recording routes only`() {
        assertEquals(
            true,
            shouldAutoResumeLowVisionKws(
                autoResumeEnabled = true,
                currentRoute = LowVisionRoute.Home.route,
            ),
        )
        assertEquals(
            false,
            shouldAutoResumeLowVisionKws(
                autoResumeEnabled = true,
                currentRoute = LowVisionRoute.VoiceInput.route,
            ),
        )
    }

    @Test
    fun `low vision my page actions resolve to concrete destinations`() {
        assertEquals(
            OnboardingRoute.ProfileUserTypePrimary.route,
            resolveLowVisionModeChangeRoute(),
        )
        assertEquals(
            LowVisionRoute.AppInfo.route,
            resolveLowVisionAppInfoRoute(),
        )
        assertEquals(
            LowVisionRoute.TextSize.route,
            resolveLowVisionTextSizeRoute(),
        )
        assertEquals(
            AuthRoute.Login.route,
            resolveLowVisionLogoutRoute(),
        )
    }

    @Test
    fun `low vision app info wires text size action to dedicated setting route with bottom tab`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/app/navigation/LowVisionNavGraph.kt")
                .readText()

        assertTrue(source.contains("onNavigateToTextSizeSetting = {"))
        assertTrue(source.contains("navController.navigate(resolveLowVisionTextSizeRoute())"))
        assertTrue(source.contains("lowVisionComposable(route = LowVisionRoute.TextSize.route)"))
        assertTrue(source.contains("TextSizeSettingRoute("))
        assertTrue(source.contains("selectedTab = LowVisionBottomTab.MY_PAGE"))
    }

    @Test
    fun `low vision home voice input action enables future kws auto resume before navigation`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/app/navigation/LowVisionNavGraph.kt")
                .readText()
        val homeRouteSource =
            source
                .substringAfter("LowVisionHomeRoute(")
                .substringBefore("onCurrentLocationClick = {")

        assertTrue(
            "LowVision home should arm future KWS resumes before opening the manual voice input flow.",
            homeRouteSource.contains("viewModel.enableAutoResume()"),
        )
    }
}
