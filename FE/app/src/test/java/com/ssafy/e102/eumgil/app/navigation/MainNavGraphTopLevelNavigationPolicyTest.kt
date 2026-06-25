package com.ssafy.e102.eumgil.app.navigation

import androidx.lifecycle.SavedStateHandle
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainNavGraphTopLevelNavigationPolicyTest {
    @Test
    fun `bottom tab selection preserves each tab state across reentry`() {
        assertTrue(DefaultTopLevelNavigationPolicy.launchSingleTop)
        assertTrue(DefaultTopLevelNavigationPolicy.restoreState)
        assertTrue(DefaultTopLevelNavigationPolicy.saveState)
    }

    @Test
    fun `top level navigation policy remains stable`() {
        assertEquals(
            TopLevelNavigationPolicy(
                launchSingleTop = true,
                restoreState = true,
                saveState = true,
            ),
            DefaultTopLevelNavigationPolicy,
        )
    }

    @Test
    fun `map home reentry signal is consumed once`() {
        val savedStateHandle = SavedStateHandle()

        savedStateHandle.requestMapHomeReentryReset()

        assertTrue(savedStateHandle.consumeMapHomeReentryReset())
        assertFalse(savedStateHandle.consumeMapHomeReentryReset())
    }

    @Test
    fun `map facility detail dismiss request uses incrementing one shot ids`() {
        val savedStateHandle = SavedStateHandle()

        val firstRequestId = savedStateHandle.requestMapFacilityDetailDismiss()

        assertTrue(firstRequestId > 0L)
        assertTrue(savedStateHandle.consumeMapFacilityDetailDismissRequest(firstRequestId))
        assertFalse(savedStateHandle.consumeMapFacilityDetailDismissRequest(firstRequestId))

        val secondRequestId = savedStateHandle.requestMapFacilityDetailDismiss()

        assertTrue(secondRequestId > firstRequestId)
        assertTrue(savedStateHandle.consumeMapFacilityDetailDismissRequest(secondRequestId))
        assertFalse(savedStateHandle.consumeMapFacilityDetailDismissRequest(secondRequestId))
    }

    @Test
    fun `map facility detail dismiss request ignores stale ids after newer request`() {
        val savedStateHandle = SavedStateHandle()

        val firstRequestId = savedStateHandle.requestMapFacilityDetailDismiss()
        val secondRequestId = savedStateHandle.requestMapFacilityDetailDismiss()

        assertFalse(savedStateHandle.consumeMapFacilityDetailDismissRequest(firstRequestId))
        assertTrue(savedStateHandle.consumeMapFacilityDetailDismissRequest(secondRequestId))
    }

    @Test
    fun `navigation hazard report reroute result is consumed once`() {
        val savedStateHandle = SavedStateHandle()

        savedStateHandle.setNavigationHazardReportSubmittedReportId(42L)

        assertEquals(42L, savedStateHandle.consumeNavigationHazardReportSubmittedReportId())
        assertEquals(null, savedStateHandle.consumeNavigationHazardReportSubmittedReportId())
    }

    @Test
    fun `map home reentry helper pops the existing map entry before fallback navigate`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/app/navigation/MainNavGraph.kt")
                .readText()

        assertTrue(
            "Home reentry should first try to pop back to the existing map entry so alias routes like search return to the actual map screen immediately.",
            source.contains("popBackStack(") &&
                source.contains("route = TopLevelRoute.Map.route") &&
                source.contains("inclusive = false"),
        )
        assertTrue(
            "Home reentry should still fall back to top-level map navigation when no existing map entry is available.",
            source.contains("if (!didPopToMap) {") &&
                source.contains("navigateToTopLevel(TopLevelDestination.Map)"),
        )
    }

    @Test
    fun `route setting close actions navigate through map home reentry helper`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/app/navigation/MainNavGraph.kt")
                .readText()
        val entryRouteSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingEntryRoute.kt")
                .readText()
        val routeSettingDestination =
            source
                .substringAfter("route = RouteSettingRoute.Setting.route")
                .substringBefore("route = RouteSettingRoute.Detail.route")
        val routeDetailDestination =
            source
                .substringAfter("route = RouteSettingRoute.Detail.route")
                .substringBefore("composable(route = ReportRoute.Report.route)")
        val routeDetailEntry =
            entryRouteSource
                .substringAfter("fun RouteDetailEntryRoute(")

        assertTrue(
            "Route setting close should return to map home instead of a plain back-stack pop.",
            routeSettingDestination.contains("onNavigateToMap = {") &&
                routeSettingDestination.contains("navController.navigateToTopLevelMapForHomeEntry()"),
        )
        assertTrue(
            "Route setting back should also collapse the search stack and return to map home in one step.",
            routeSettingDestination.contains("onNavigateBack = {") &&
                routeSettingDestination.contains("navController.navigateToTopLevelMapForHomeEntry()"),
        )
        assertTrue(
            "Route detail close should also return to map home.",
            routeDetailDestination.contains("onNavigateToMap = {") &&
                routeDetailDestination.contains("navController.navigateToTopLevelMapForHomeEntry()"),
        )
        assertTrue(
            "Route detail top-bar X should invoke the map-home callback directly instead of falling back through the route flow back stack.",
            routeDetailEntry.contains("onCloseClick = onNavigateToMap"),
        )
    }

    @Test
    fun `map search entry opens place preview search flow`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/app/navigation/MainNavGraph.kt")
                .readText()
        val mapDestination =
            source
                .substringAfter("composable(route = TopLevelRoute.Map.route)")
                .substringBefore("composable(route = TopLevelRoute.SavedRoute.route)")

        assertTrue(
            "Home map search should use the selection mode carried by the map event instead of forcing route endpoint assignment.",
            mapDestination.contains(
                "SearchRoute.Entry.createRoute(editingTarget, selectionMode)",
            ),
        )
        assertTrue(
            "Home map voice/search result navigation should open preview-only results so result taps show the place detail sheet first.",
            mapDestination.contains(
                "SearchRoute.Results.createRoute(query, editingTarget, SearchSelectionMode.PREVIEW_ON_MAP)",
            ),
        )
    }

    @Test
    fun `search mic actions open global voice assistant instead of navigating to search voice route`() {
        val mainNavGraphSource =
            File("src/main/java/com/ssafy/e102/eumgil/app/navigation/MainNavGraph.kt")
                .readText()
        val appNavHostSource =
            File("src/main/java/com/ssafy/e102/eumgil/app/navigation/AppNavHost.kt")
                .readText()
        val entryDestination =
            mainNavGraphSource
                .substringAfter("SearchEntryRoute(")
                .substringBefore("onNavigateToRouteSetting = {")
        val resultsDestination =
            mainNavGraphSource
                .substringAfter("SearchResultsRoute(")
                .substringBefore("onNavigateToRouteSetting = {")

        assertTrue(
            "Main graph should expose a global voice assistant opener with the active search editing target.",
            mainNavGraphSource.contains("onOpenVoiceAssistant: (RouteEditingTarget) -> Unit"),
        )
        assertTrue(
            "Search entry mic should request the global voice assistant for the current editing target.",
            entryDestination.contains("onOpenVoiceAssistant(initialEditingTarget)"),
        )
        assertFalse(
            "Search entry mic should no longer navigate to the legacy search voice route.",
            entryDestination.contains("SearchRoute.VoiceInput.createRoute"),
        )
        assertTrue(
            "Search results mic should request the global voice assistant for the current editing target.",
            resultsDestination.contains("onOpenVoiceAssistant(initialEditingTarget)"),
        )
        assertFalse(
            "Search results mic should no longer navigate to the legacy search voice route.",
            resultsDestination.contains("SearchRoute.VoiceInput.createRoute"),
        )
        assertTrue(
            "The legacy search voice route should remain registered for compatibility.",
            mainNavGraphSource.contains("route = SearchRoute.VoiceInput.route"),
        )
        assertTrue(
            "AppNavHost should preserve the search editing target in the global voice assistant context.",
            appNavHostSource.contains("onOpenVoiceAssistant = { editingTarget ->") &&
                appNavHostSource.contains("currentVoiceAssistantSourceContext.copy(editingTarget = editingTarget)"),
        )
    }

    @Test
    fun `my page text size destination is registered as a my page child route`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/app/navigation/MainNavGraph.kt")
                .readText()
        val myPageDestination =
            source
                .substringAfter("composable(route = TopLevelRoute.MyPage.route)")
                .substringBefore("composable(route = MyPageChildRoute.TextSize.route)")
        val textSizeDestination =
            source
                .substringAfter("composable(route = MyPageChildRoute.TextSize.route)")
                .substringBefore("composable(")

        assertTrue(
            "MyPageRoute should pass the text-size navigation event into the my_page/text_size child route.",
            myPageDestination.contains("onNavigateToTextSizeSetting = {") &&
                myPageDestination.contains("navController.navigate(MyPageChildRoute.TextSize.route)"),
        )
        assertTrue(
            "The main graph should reuse the common TextSizeSettingRoute instead of creating a duplicate screen.",
            textSizeDestination.contains("TextSizeSettingRoute(") &&
                textSizeDestination.contains("onNavigateBack = {") &&
                textSizeDestination.contains("navController.popBackStack()"),
        )
    }

    @Test
    fun `map home reentry reset excludes bookmark while keeping other visible non-map routes`() {
        assertTrue(
            shouldNavigateToTopLevelMapForHomeEntry(
                currentRoute = SearchRoute.Entry.route,
                destination = TopLevelDestination.Map,
            ),
        )
        assertFalse(
            shouldNavigateToTopLevelMapForHomeEntry(
                currentRoute = TopLevelRoute.SavedRoute.route,
                destination = TopLevelDestination.Map,
            ),
        )
        assertTrue(
            shouldNavigateToTopLevelMapForHomeEntry(
                currentRoute = ReportRoute.Report.route,
                destination = TopLevelDestination.Map,
            ),
        )
        assertTrue(
            shouldNavigateToTopLevelMapForHomeEntry(
                currentRoute = TopLevelRoute.MyPage.route,
                destination = TopLevelDestination.Map,
            ),
        )
        assertFalse(
            shouldNavigateToTopLevelMapForHomeEntry(
                currentRoute = TopLevelRoute.Map.route,
                destination = TopLevelDestination.Map,
            ),
        )
        assertFalse(
            shouldNavigateToTopLevelMapForHomeEntry(
                currentRoute = ArrivalRoute.Entry.route,
                destination = TopLevelDestination.Map,
            ),
        )
    }
}
