package com.ssafy.e102.eumgil.app.navigation;

import static org.junit.Assert.assertEquals;

import com.ssafy.e102.eumgil.core.model.AuthGateState;
import com.ssafy.e102.eumgil.core.model.AuthSession;
import com.ssafy.e102.eumgil.core.model.InitSettings;
import com.ssafy.e102.eumgil.feature.onboarding.PrimaryUserType;
import org.junit.Test;

public class AppStartDestinationTest {
    @Test
    public void sessionMissingStartsAtLoginRegardlessOfCompletedOnboarding() {
        AppStartDestination destination =
                AppStartDestinationKt.resolveAppStartDestination(
                        new AuthGateState(null, false),
                        completedInitSettings());

        assertEquals(AuthRoute.Login.INSTANCE.getRoute(), destination.getRoute());
    }

    @Test
    public void profileIncompleteSessionStartsAtProfileSetup() {
        AppStartDestination destination =
                AppStartDestinationKt.resolveAppStartDestination(
                        new AuthGateState(authSession(), false),
                        completedInitSettings());

        assertEquals(AuthRoute.ProfileSetup.INSTANCE.getRoute(), destination.getRoute());
    }

    @Test
    public void profileCompletedSessionStartsOnboardingWhenOnboardingIsMissing() {
        AppStartDestination destination =
                AppStartDestinationKt.resolveAppStartDestination(
                        new AuthGateState(authSession(), true),
                        new InitSettings(null, null, false, false, false));

        assertEquals(OnboardingRoute.UserTypePrimary.INSTANCE.getRoute(), destination.getRoute());
    }

    @Test
    public void profileCompletedSessionReturnsToOnb001WhenOnlyPrimaryUserTypeExists() {
        AppStartDestination destination =
                AppStartDestinationKt.resolveAppStartDestination(
                        new AuthGateState(authSession(), true),
                        new InitSettings(
                                PrimaryUserType.LOW_VISION.getRouteValue(),
                                null,
                                false,
                                false,
                                false));

        assertEquals(OnboardingRoute.UserTypePrimary.INSTANCE.getRoute(), destination.getRoute());
    }

    @Test
    public void completedLowVisionSessionStartsAtLowVisionHome() {
        AppStartDestination destination =
                AppStartDestinationKt.resolveAppStartDestination(
                        new AuthGateState(authSession(), true),
                        new InitSettings(
                                PrimaryUserType.LOW_VISION.getRouteValue(),
                                null,
                                true,
                                true,
                                true));

        assertEquals("low_vision_graph", destination.getRoute());
    }

    @Test
    public void completedSessionProfileAndOnboardingStartsAtMap() {
        AppStartDestination destination =
                AppStartDestinationKt.resolveAppStartDestination(
                        new AuthGateState(authSession(), true),
                        completedInitSettings());

        assertEquals(TopLevelRoute.Map.INSTANCE.getRoute(), destination.getRoute());
    }

    private static AuthSession authSession() {
        return new AuthSession("access-token", "refresh-token");
    }

    private static InitSettings completedInitSettings() {
        return new InitSettings(
                PrimaryUserType.MOBILITY_IMPAIRED.getRouteValue(),
                "manual_wheelchair",
                false,
                true,
                true);
    }
}
