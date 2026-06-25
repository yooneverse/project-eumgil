package com.ssafy.e102.eumgil.app.navigation

import androidx.lifecycle.SavedStateHandle
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.data.repository.RouteEditingTarget
import com.ssafy.e102.eumgil.feature.search.SearchSelectionMode
import com.ssafy.e102.eumgil.feature.voiceassistant.VoiceAssistantAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppNavHostRoutingTest {
    @Test
    fun `top level destination entries expose concrete routes`() {
        assertEquals(
            listOf(
                TopLevelRoute.Map.route,
                TopLevelRoute.SavedRoute.route,
                ReportRoute.Report.route,
                TopLevelRoute.MyPage.route,
            ),
            TopLevelDestination.entries.map { destination -> destination.route.route },
        )
    }

    @Test
    fun `voice assistant actions resolve to app navigation requests`() {
        assertEquals(
            VoiceAssistantNavigationRequest.TopLevel(TopLevelDestination.Report),
            VoiceAssistantAction.OpenReport().toNavigationRequest(),
        )
        assertEquals(
            VoiceAssistantNavigationRequest.TopLevel(TopLevelDestination.SavedRoute),
            VoiceAssistantAction.OpenSavedRoutes().toNavigationRequest(),
        )
        assertEquals(
            VoiceAssistantNavigationRequest.TopLevel(TopLevelDestination.MyPage),
            VoiceAssistantAction.OpenMyPage().toNavigationRequest(),
        )
        assertEquals(
            VoiceAssistantNavigationRequest.MapHomeEntry,
            VoiceAssistantAction.OpenMap().toNavigationRequest(),
        )
        assertEquals(
            VoiceAssistantNavigationRequest.Route(
                SearchRoute.Results.createRoute(
                    query = "부산역",
                    editingTarget = RouteEditingTarget.ORIGIN,
                ),
            ),
            VoiceAssistantAction.SearchPlace(
                query = "부산역",
                editingTarget = RouteEditingTarget.ORIGIN,
            ).toNavigationRequest(),
        )
        assertNull(VoiceAssistantAction.UnknownCommand(rawCommand = "unknown").toNavigationRequest())
    }

    @Test
    fun `search routes keep map tab active while arrival hides top level tab`() {
        assertEquals(TopLevelRoute.Map.route, SearchRoute.Entry.route.toCurrentTopLevelRoute())
        assertEquals(TopLevelRoute.Map.route, SearchRoute.Results.route.toCurrentTopLevelRoute())
        assertNull(ArrivalRoute.Entry.route.toCurrentTopLevelRoute())
    }

    @Test
    fun `my page text size route keeps my page tab active`() {
        assertEquals("my_page/text_size", MyPageChildRoute.TextSize.route)
        assertEquals(TopLevelRoute.MyPage.route, MyPageChildRoute.TextSize.route.toCurrentTopLevelRoute())
    }

    @Test
    fun `map tab skips home reentry reset for bookmark while keeping other visible non-map routes`() {
        assertEquals(
            true,
            shouldNavigateToTopLevelMapForHomeEntry(
                currentRoute = SearchRoute.Entry.route,
                destination = TopLevelDestination.Map,
            ),
        )
        assertEquals(
            true,
            shouldNavigateToTopLevelMapForHomeEntry(
                currentRoute = SearchRoute.Results.route,
                destination = TopLevelDestination.Map,
            ),
        )
        assertEquals(
            false,
            shouldNavigateToTopLevelMapForHomeEntry(
                currentRoute = ArrivalRoute.Entry.route,
                destination = TopLevelDestination.Map,
            ),
        )
        assertEquals(
            false,
            shouldNavigateToTopLevelMapForHomeEntry(
                currentRoute = TopLevelRoute.Map.route,
                destination = TopLevelDestination.Map,
            ),
        )
        assertEquals(
            false,
            shouldNavigateToTopLevelMapForHomeEntry(
                currentRoute = TopLevelRoute.SavedRoute.route,
                destination = TopLevelDestination.Map,
            ),
        )
        assertEquals(
            true,
            shouldNavigateToTopLevelMapForHomeEntry(
                currentRoute = ReportRoute.Report.route,
                destination = TopLevelDestination.Map,
            ),
        )
        assertEquals(
            true,
            shouldNavigateToTopLevelMapForHomeEntry(
                currentRoute = TopLevelRoute.MyPage.route,
                destination = TopLevelDestination.Map,
            ),
        )
        assertEquals(
            false,
            shouldNavigateToTopLevelMapForHomeEntry(
                currentRoute = SearchRoute.Entry.route,
                destination = TopLevelDestination.SavedRoute,
            ),
        )
    }

    @Test
    fun `guidance and route setting routes hide top level tab`() {
        assertNull(NavigationRoute.Guidance.route.toCurrentTopLevelRoute())
        assertNull(ReportRoute.Guidance.route.toCurrentTopLevelRoute())
        assertNull(RouteSettingRoute.Setting.route.toCurrentTopLevelRoute())
        assertNull(RouteSettingRoute.PermissionGate.route.toCurrentTopLevelRoute())
        assertNull(RouteSettingRoute.Detail.createRoute(RouteOption.SAFE).toCurrentTopLevelRoute())
    }

    @Test
    fun `guidance report route starts outside report top level tab`() {
        assertEquals("report/navigation_guidance", ReportRoute.Guidance.route)
        assertNull(ReportRoute.Guidance.route.toCurrentTopLevelRoute())
    }

    @Test
    fun `route setting routes preserve permission gate and prechecked query arguments`() {
        assertEquals(
            "route_setting/permission?autoStartNavigation=true&initialRouteOption=SAFE",
            RouteSettingRoute.PermissionGate.createRoute(
                autoStartNavigation = true,
                initialRouteOption = RouteOption.SAFE,
            ),
        )
        assertEquals(
            "route_setting?initialRouteOption=SAFE&locationPermissionPrechecked=true",
            RouteSettingRoute.Setting.createRoute(
                initialRouteOption = RouteOption.SAFE,
                locationPermissionPrechecked = true,
            ),
        )
    }

    @Test
    fun `search route defaults stay compatible with previous paths`() {
        assertEquals("search", SearchRoute.Entry.createRoute())
        assertEquals("search/voice", SearchRoute.VoiceInput.createRoute())
        assertEquals("search/results/Busan%20Station", SearchRoute.Results.createRoute("Busan Station"))
    }

    @Test
    fun `search routes preserve non default selection mode and editing target`() {
        assertEquals(
            "search?selectionMode=APPLY_TO_ROUTE",
            SearchRoute.Entry.createRoute(selectionMode = SearchSelectionMode.APPLY_TO_ROUTE),
        )
        assertEquals(
            "search?editingTarget=ORIGIN&selectionMode=APPLY_TO_ROUTE",
            SearchRoute.Entry.createRoute(
                editingTarget = RouteEditingTarget.ORIGIN,
                selectionMode = SearchSelectionMode.APPLY_TO_ROUTE,
            ),
        )
        assertEquals(
            "search/voice?editingTarget=ORIGIN&selectionMode=APPLY_TO_ROUTE",
            SearchRoute.VoiceInput.createRoute(
                editingTarget = RouteEditingTarget.ORIGIN,
                selectionMode = SearchSelectionMode.APPLY_TO_ROUTE,
            ),
        )
        assertEquals(
            "search/results/Busan%20Station?editingTarget=ORIGIN&selectionMode=APPLY_TO_ROUTE",
            SearchRoute.Results.createRoute(
                query = "Busan Station",
                editingTarget = RouteEditingTarget.ORIGIN,
                selectionMode = SearchSelectionMode.APPLY_TO_ROUTE,
            ),
        )
    }

    @Test
    fun `map route endpoint picker request is stored and consumed on map saved state`() {
        val savedStateHandle = SavedStateHandle()

        savedStateHandle.requestRouteEndpointMapPicker(RouteEditingTarget.ORIGIN)

        assertEquals(
            RouteEditingTarget.ORIGIN.name,
            savedStateHandle.get<String>("mapRouteEndpointPickerTarget"),
        )

        savedStateHandle.consumeRouteEndpointMapPickerTarget()

        assertNull(savedStateHandle.get<String>("mapRouteEndpointPickerTarget"))
    }

    @Test
    fun `search result name tap resolves to route briefing`() {
        assertEquals(
            LowVisionRoute.RouteBriefing.route,
            resolveSearchResultBriefingRoute(selectedPrimaryUserType = "low_vision"),
        )
    }

    @Test
    fun `arrival home return matches the active user type`() {
        assertEquals(
            LowVisionRoute.Home.route,
            resolveArrivalHomeRoute(selectedPrimaryUserType = "low_vision"),
        )
        assertEquals(
            TopLevelRoute.Map.route,
            resolveArrivalHomeRoute(selectedPrimaryUserType = "mobility_impaired"),
        )
        assertEquals(
            TopLevelRoute.Map.route,
            resolveArrivalHomeRoute(selectedPrimaryUserType = null),
        )
    }

    @Test
    fun `auth onboarding and low vision routes hide top level tab`() {
        assertNull(AuthRoute.Login.route.toCurrentTopLevelRoute())
        assertNull(OnboardingRoute.UserTypePrimary.route.toCurrentTopLevelRoute())
        assertNull(LowVisionRoute.Home.route.toCurrentTopLevelRoute())
        assertNull(LowVisionRoute.Search.route.toCurrentTopLevelRoute())
    }

    @Test
    fun `app route changes use instant destination transitions`() {
        assertEquals(true, shouldUseInstantAppDestinationTransitions())
    }
}
