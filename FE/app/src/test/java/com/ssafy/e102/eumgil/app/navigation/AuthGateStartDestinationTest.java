package com.ssafy.e102.eumgil.app.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.ssafy.e102.eumgil.core.model.AuthGateState;
import com.ssafy.e102.eumgil.core.model.AuthSession;
import com.ssafy.e102.eumgil.core.model.InitSettings;
import com.ssafy.e102.eumgil.feature.onboarding.PrimaryUserType;
import org.junit.Test;

public class AuthGateStartDestinationTest {
    @Test
    public void loggedOutUserCannotBypassAuthGateToMapEvenWhenOnboardingIsComplete() {
        AppStartDestination destination =
                AppStartDestinationKt.resolveAppStartDestination(
                        new AuthGateState(null, false),
                        completedInitSettings());

        assertEquals(AuthRoute.Login.INSTANCE.getRoute(), destination.getRoute());
        assertNotEquals(TopLevelRoute.Map.INSTANCE.getRoute(), destination.getRoute());
    }

    @Test
    public void loggedOutUserStartsAtLoginEvenWhenProfileFlagIsStaleTrue() {
        AppStartDestination destination =
                AppStartDestinationKt.resolveAppStartDestination(
                        new AuthGateState(null, true),
                        completedInitSettings());

        assertEquals(AuthRoute.Login.INSTANCE.getRoute(), destination.getRoute());
        assertNotEquals(TopLevelRoute.Map.INSTANCE.getRoute(), destination.getRoute());
    }

    @Test
    public void localOnlyLoginSessionReturnsToProfileGateBeforeOnboardingOrMap() {
        AppStartDestination destination =
                AppStartDestinationKt.resolveAppStartDestination(
                        new AuthGateState(new AuthSession("local-only-auth-session", null), false),
                        completedInitSettings());

        assertEquals(AuthRoute.ProfileSetup.INSTANCE.getRoute(), destination.getRoute());
        assertNotEquals(TopLevelRoute.Map.INSTANCE.getRoute(), destination.getRoute());
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
