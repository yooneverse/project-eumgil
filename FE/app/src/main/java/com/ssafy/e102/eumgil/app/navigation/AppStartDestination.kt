package com.ssafy.e102.eumgil.app.navigation

import com.ssafy.e102.eumgil.core.model.AuthGateState
import com.ssafy.e102.eumgil.core.model.InitSettings
import com.ssafy.e102.eumgil.feature.onboarding.PrimaryUserType

sealed interface AppStartDestination {
    val route: String

    data object Login : AppStartDestination {
        override val route: String = AuthRoute.Login.route
    }

    data object ProfileSetup : AppStartDestination {
        override val route: String = AuthRoute.ProfileSetup.route
    }

    data object UserTypePrimaryStep : AppStartDestination {
        override val route: String = OnboardingRoute.UserTypePrimary.route
    }

    data object LowVisionTermsGuide : AppStartDestination {
        override val route: String = OnboardingRoute.TermsGuide.createRoute()
    }

    data object Map : AppStartDestination {
        override val route: String = TopLevelRoute.Map.route
    }

    data object LowVisionHome : AppStartDestination {
        override val route: String = LOW_VISION_GRAPH_ROUTE
    }
}

@JvmOverloads
fun resolveAppStartDestination(
    authGateState: AuthGateState,
    initSettings: InitSettings,
    forceLowVisionTermsGuide: Boolean = false,
): AppStartDestination {
    if (authGateState.hasPendingSignup) return AppStartDestination.UserTypePrimaryStep
    if (!authGateState.hasSession) return AppStartDestination.Login
    if (!authGateState.isProfileCompleted) return AppStartDestination.ProfileSetup
    if (forceLowVisionTermsGuide) return AppStartDestination.LowVisionTermsGuide

    return if (initSettings.isOnboardingCompleted) {
        if (initSettings.selectedPrimaryUserType == PrimaryUserType.LOW_VISION.routeValue) {
            AppStartDestination.LowVisionHome
        } else {
            AppStartDestination.Map
        }
    } else {
        AppStartDestination.UserTypePrimaryStep
    }
}
