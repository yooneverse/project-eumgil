package com.ssafy.e102.eumgil.app.navigation

import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.ssafy.e102.eumgil.data.repository.AuthSessionRepository
import com.ssafy.e102.eumgil.data.repository.SettingsRepository
import com.ssafy.e102.eumgil.feature.auth.LoginRoute
import com.ssafy.e102.eumgil.feature.auth.ProfileSetupRoute
import kotlinx.coroutines.launch

fun NavGraphBuilder.authNavGraph(
    navController: NavHostController,
    authSessionRepository: AuthSessionRepository,
    settingsRepository: SettingsRepository,
) {
    composable(route = AuthRoute.Login.route) {
        val coroutineScope = rememberCoroutineScope()

        LoginRoute(
            onLoginCompleted = {
                coroutineScope.launch {
                    val nextDestination =
                        resolveAppStartDestination(
                            authGateState = authSessionRepository.getAuthGateState(),
                            initSettings = settingsRepository.getInitSettings(),
                        )

                    navController.navigate(nextDestination.route) {
                        launchSingleTop = true
                        popUpTo(AuthRoute.Login.route) {
                            inclusive = true
                        }
                    }
                }
            },
        )
    }
    composable(route = AuthRoute.ProfileSetup.route) {
        val coroutineScope = rememberCoroutineScope()

        ProfileSetupRoute(
            onProfileSetupCompleted = {
                coroutineScope.launch {
                    authSessionRepository.markProfileCompleted()
                    val nextDestination =
                        resolveAppStartDestination(
                            authGateState = authSessionRepository.getAuthGateState(),
                            initSettings = settingsRepository.getInitSettings(),
                        )

                    navController.navigate(nextDestination.route) {
                        launchSingleTop = true
                        popUpTo(AuthRoute.ProfileSetup.route) {
                            inclusive = true
                        }
                    }
                }
            },
        )
    }
}
