package com.ssafy.e102.eumgil.app.navigation

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ssafy.e102.eumgil.core.model.InitSettings
import com.ssafy.e102.eumgil.data.repository.AuthSignupRepository
import com.ssafy.e102.eumgil.data.repository.PendingSignupTokenExpiredException
import com.ssafy.e102.eumgil.data.repository.ProfileUserTypeUpdateRepository
import com.ssafy.e102.eumgil.data.repository.ProfileUserTypeUpdateResult
import com.ssafy.e102.eumgil.data.repository.SettingsRepository
import com.ssafy.e102.eumgil.feature.onboarding.LocationTermsItem
import com.ssafy.e102.eumgil.feature.onboarding.LocationTermsRoute
import com.ssafy.e102.eumgil.feature.onboarding.LowVisionFollowUpRoute
import com.ssafy.e102.eumgil.feature.onboarding.PermissionRoute
import com.ssafy.e102.eumgil.feature.onboarding.PrimaryUserType
import com.ssafy.e102.eumgil.feature.onboarding.PrimaryUserTypeRoute
import com.ssafy.e102.eumgil.feature.onboarding.MobilityTypeSecondaryRoute
import com.ssafy.e102.eumgil.feature.onboarding.MobilityTypeSecondaryTermsRoute
import com.ssafy.e102.eumgil.feature.terms.TermsGuideRoute
import com.ssafy.e102.eumgil.feature.terms.TermsGuideStep
import com.ssafy.e102.eumgil.feature.tutorial.MobilityTutorialRoute
import com.ssafy.e102.eumgil.feature.tutorial.TutorialEntryPoint
import kotlinx.coroutines.launch

fun NavGraphBuilder.onboardingNavGraph(
    navController: NavHostController,
    settingsRepository: SettingsRepository,
    authSignupRepository: AuthSignupRepository,
    profileUserTypeUpdateRepository: ProfileUserTypeUpdateRepository,
) {
    composable(route = OnboardingRoute.UserTypePrimary.route) {
        val coroutineScope = rememberCoroutineScope()

        PrimaryUserTypeRoute(
            onTypeSelected = { primaryUserType ->
                coroutineScope.launch {
                    settingsRepository.savePrimaryUserType(primaryUserType.routeValue)
                    navController.navigate(resolvePrimaryUserTypeNextRoute(primaryUserType))
                }
            },
        )
    }

    composable(route = OnboardingRoute.ProfileUserTypePrimary.route) {
        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current

        PrimaryUserTypeRoute(
            onTypeSelected = { primaryUserType ->
                coroutineScope.launch {
                    when (primaryUserType) {
                        PrimaryUserType.LOW_VISION ->
                            completeProfileEditAndNavigate(
                                navController = navController,
                                context = context,
                                profileUserTypeUpdateRepository = profileUserTypeUpdateRepository,
                                selectedPrimaryUserType = primaryUserType.routeValue,
                                selectedMobilitySubtype = null,
                            )

                        PrimaryUserType.MOBILITY_IMPAIRED ->
                            navController.navigate(
                                resolvePrimaryUserTypeNextRoute(
                                    primaryUserType = primaryUserType,
                                    entryPoint = OnboardingEntryPoint.PROFILE_EDIT,
                                ),
                            )
                    }
                }
            },
        )
    }

    composable(route = OnboardingRoute.LowVisionFollowUp.route) {
        val coroutineScope = rememberCoroutineScope()

        LowVisionFollowUpRoute(
            onNavigateNext = {
                coroutineScope.launch {
                    settingsRepository.saveLowVisionFollowUpCompleted(isCompleted = true)
                    navController.navigate(OnboardingRoute.Terms.route)
                }
            },
        )
    }

    composable(route = OnboardingRoute.MobilityTypeSecondary.route) {
        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current

        MobilityTypeSecondaryTermsRoute(
            onRequestDetails = { item ->
                openLocationTermsDetails(context = context, item = item)
            },
            onConsentCompleted = { mobilitySubtype, agreement ->
                coroutineScope.launch {
                    completeLocationTermsAndNavigate(
                        navController = navController,
                        context = context,
                        settingsRepository = settingsRepository,
                        authSignupRepository = authSignupRepository,
                        agreement = agreement,
                        popUpToRoute = OnboardingRoute.MobilityTypeSecondary.route,
                        beforeSaveTerms = {
                            settingsRepository.saveMobilitySubtype(mobilitySubtype.routeValue)
                        },
                    )
                }
            },
        )
    }

    composable(route = OnboardingRoute.ProfileMobilityTypeSecondary.route) {
        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current

        MobilityTypeSecondaryRoute(
            onNavigateNext = { mobilitySubtype ->
                coroutineScope.launch {
                    completeProfileEditAndNavigate(
                        navController = navController,
                        context = context,
                        profileUserTypeUpdateRepository = profileUserTypeUpdateRepository,
                        selectedPrimaryUserType = PrimaryUserType.MOBILITY_IMPAIRED.routeValue,
                        selectedMobilitySubtype = mobilitySubtype.routeValue,
                    )
                }
            },
        )
    }

    composable(route = OnboardingRoute.Terms.route) {
        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current
        val initSettings by
            settingsRepository
                .observeInitSettings()
                .collectAsStateWithLifecycle(initialValue = InitSettings())

        LocationTermsRoute(
            initialLocationTermsChecked = initSettings.isLocationTermsAgreed,
            onRequestDetails = { item ->
                openLocationTermsDetails(context = context, item = item)
            },
            onConsentCompleted = { agreement ->
                coroutineScope.launch {
                    completeLocationTermsAndNavigate(
                        navController = navController,
                        context = context,
                        settingsRepository = settingsRepository,
                        authSignupRepository = authSignupRepository,
                        agreement = agreement,
                        popUpToRoute = OnboardingRoute.Terms.route,
                    )
                }
            },
        )
    }

    composable(route = TutorialRoute.Onboarding.route) {
        MobilityTutorialRoute(
            entryPoint = TutorialEntryPoint.ONBOARDING,
            onCompleted = {
                navController.navigateToCompletedOnboarding(
                    route = resolveTutorialOnboardingCompletedRoute(),
                )
            },
        )
    }

    composable(
        route = OnboardingRoute.TermsGuide.route,
        arguments =
            listOf(
                navArgument(OnboardingRoute.TermsGuide.ARG_STEP) {
                    type = NavType.StringType
                    defaultValue = OnboardingRoute.TermsGuide.DEFAULT_STEP
                },
            ),
    ) { backStackEntry ->
        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current

        val initialStep =
            TermsGuideStep.fromRouteValue(
                backStackEntry.arguments?.getString(OnboardingRoute.TermsGuide.ARG_STEP),
            ) ?: TermsGuideStep.AGREE

        TermsGuideRoute(
            initialStep = initialStep,
            onCompleted = {
                // 5단계(처리방침)까지 통과 = 위치/개인정보 항목 모두 확인 완료.
                coroutineScope.launch {
                    runCatching {
                        settingsRepository.saveLowVisionFollowUpCompleted(isCompleted = true)
                        settingsRepository.saveLocationTermsAgreement(
                            isLocationTermsAgreed = true,
                            isPrivacyPolicyAgreed = true,
                        )
                        authSignupRepository.completePendingSignup(requiredTermsAccepted = true)
                        val completedSettings = settingsRepository.getInitSettings()
                        navController.navigate(
                            OnboardingRoute.Permission.createRoute(
                                resolveOnboardingCompletedRoute(completedSettings.selectedPrimaryUserType),
                            ),
                        ) {
                            launchSingleTop = true
                        }
                    }.onFailure { throwable ->
                        Toast
                            .makeText(
                                context,
                                throwable.message ?: DEFAULT_ONBOARDING_COMPLETION_ERROR_MESSAGE,
                                Toast.LENGTH_SHORT,
                            ).show()
                        if (throwable is PendingSignupTokenExpiredException) {
                            navController.navigateToLoginAfterAuthenticationFailure()
                        }
                    }
                }
            },
            onRequestDetails = { step ->
                createTermsGuideDetailIntent(step)?.let(context::startActivity)
            },
        )
    }

    composable(
        route = OnboardingRoute.Permission.route,
        arguments = listOf(
            navArgument(OnboardingRoute.Permission.ARG_NEXT_ROUTE) {
                type = NavType.StringType
            },
        ),
    ) { backStackEntry ->
        val nextRoute = backStackEntry.arguments
            ?.getString(OnboardingRoute.Permission.ARG_NEXT_ROUTE)
            .orEmpty()

        PermissionRoute(
            onPermissionHandled = {
                navController.navigateToCompletedOnboarding(route = nextRoute)
            },
        )
    }
}

internal enum class OnboardingEntryPoint {
    SIGN_UP,
    PROFILE_EDIT,
}

internal fun resolvePrimaryUserTypeNextRoute(
    primaryUserType: PrimaryUserType,
    entryPoint: OnboardingEntryPoint = OnboardingEntryPoint.SIGN_UP,
): String =
    when (entryPoint) {
        OnboardingEntryPoint.SIGN_UP ->
            when (primaryUserType) {
                PrimaryUserType.LOW_VISION -> OnboardingRoute.TermsGuide.createRoute()
                PrimaryUserType.MOBILITY_IMPAIRED -> OnboardingRoute.MobilityTypeSecondary.route
            }

        OnboardingEntryPoint.PROFILE_EDIT ->
            when (primaryUserType) {
                PrimaryUserType.LOW_VISION -> LowVisionRoute.Home.route
                PrimaryUserType.MOBILITY_IMPAIRED -> OnboardingRoute.ProfileMobilityTypeSecondary.route
            }
    }

internal fun resolveOnboardingCompletedRoute(selectedPrimaryUserType: String?): String =
    if (selectedPrimaryUserType == PrimaryUserType.LOW_VISION.routeValue) {
        LowVisionRoute.Home.route
    } else {
        TopLevelRoute.Map.route
    }

internal fun resolveMobilityOnboardingAfterTermsRoute(): String = TutorialRoute.Onboarding.route

internal fun shouldCompletePendingSignupBeforeOnboardingTutorial(selectedPrimaryUserType: String?): Boolean =
    selectedPrimaryUserType == PrimaryUserType.MOBILITY_IMPAIRED.routeValue

internal fun resolveOnboardingTermsCompletedRoute(selectedPrimaryUserType: String?): String =
    if (selectedPrimaryUserType == PrimaryUserType.MOBILITY_IMPAIRED.routeValue) {
        resolveMobilityOnboardingAfterTermsRoute()
    } else {
        resolveOnboardingCompletedRoute(selectedPrimaryUserType)
    }

internal fun resolveTutorialOnboardingCompletedRoute(): String = TopLevelRoute.Map.route

internal fun resolveTutorialGuideCompletedRoute(): String = TopLevelRoute.MyPage.route

internal fun resolveProfileEditCompletedRoute(selectedPrimaryUserType: String?): String =
    if (selectedPrimaryUserType == PrimaryUserType.LOW_VISION.routeValue) {
        LowVisionRoute.Home.route
    } else {
        TopLevelRoute.MyPage.route
    }

private fun NavHostController.navigateToCompletedOnboarding(route: String) {
    navigate(route) {
        launchSingleTop = true
        popUpTo(graph.findStartDestination().id) {
            inclusive = true
        }
    }
}

private fun NavHostController.navigateToMyPageAfterProfileEdit() {
    val didPopToMyPage =
        popBackStack(
            route = TopLevelRoute.MyPage.route,
            inclusive = false,
        )

    if (!didPopToMyPage) {
        navigate(TopLevelRoute.MyPage.route) {
            launchSingleTop = true
        }
    }
}

private fun NavHostController.navigateToLowVisionHomeAfterProfileEdit() {
    navigate(LowVisionRoute.Home.route) {
        launchSingleTop = true
        popUpTo(TopLevelRoute.MyPage.route) {
            inclusive = true
        }
    }
}

private suspend fun completeProfileEditAndNavigate(
    navController: NavHostController,
    context: android.content.Context,
    profileUserTypeUpdateRepository: ProfileUserTypeUpdateRepository,
    selectedPrimaryUserType: String,
    selectedMobilitySubtype: String?,
) {
    when (
        val result =
            profileUserTypeUpdateRepository.completeProfileEdit(
                selectedPrimaryUserType = selectedPrimaryUserType,
                selectedMobilitySubtype = selectedMobilitySubtype,
            )
    ) {
        is ProfileUserTypeUpdateResult.Success -> {
            when (resolveProfileEditCompletedRoute(result.selectedPrimaryUserType)) {
                LowVisionRoute.Home.route -> navController.navigateToLowVisionHomeAfterProfileEdit()
                else -> navController.navigateToMyPageAfterProfileEdit()
            }
        }

        ProfileUserTypeUpdateResult.MissingSession,
        ProfileUserTypeUpdateResult.AuthenticationFailed,
        -> {
            Toast
                .makeText(
                    context,
                    PROFILE_EDIT_AUTHENTICATION_ERROR_MESSAGE,
                    Toast.LENGTH_SHORT,
                ).show()
            navController.navigateToLoginAfterAuthenticationFailure()
        }

        is ProfileUserTypeUpdateResult.Failure ->
            Toast
                .makeText(
                    context,
                    result.message.ifBlank { DEFAULT_PROFILE_EDIT_COMPLETION_ERROR_MESSAGE },
                    Toast.LENGTH_SHORT,
                ).show()
    }
}

private suspend fun completeLocationTermsAndNavigate(
    navController: NavHostController,
    context: android.content.Context,
    settingsRepository: SettingsRepository,
    authSignupRepository: AuthSignupRepository,
    agreement: com.ssafy.e102.eumgil.feature.onboarding.LocationTermsAgreement,
    popUpToRoute: String,
    beforeSaveTerms: suspend () -> Unit = {},
) {
    runCatching {
        beforeSaveTerms()
        settingsRepository.saveLocationTermsAgreement(
            isLocationTermsAgreed = agreement.isLocationTermsAgreed,
            isPrivacyPolicyAgreed = agreement.isPrivacyPolicyAgreed,
        )
        val completedSettings = settingsRepository.getInitSettings()
        val nextRoute = resolveOnboardingTermsCompletedRoute(completedSettings.selectedPrimaryUserType)
        val shouldCompleteSignupBeforeTutorial =
            shouldCompletePendingSignupBeforeOnboardingTutorial(
                completedSettings.selectedPrimaryUserType,
            )
        if (shouldCompleteSignupBeforeTutorial || nextRoute != TutorialRoute.Onboarding.route) {
            authSignupRepository.completePendingSignup(
                requiredTermsAccepted = agreement.isLocationTermsAgreed,
            )
        }
        navController.navigate(
            OnboardingRoute.Permission.createRoute(
                nextRoute,
            ),
        ) {
            launchSingleTop = true
            popUpTo(popUpToRoute) {
                inclusive = true
            }
        }
    }.onFailure { throwable ->
        Toast
            .makeText(
                context,
                throwable.message ?: DEFAULT_ONBOARDING_COMPLETION_ERROR_MESSAGE,
                Toast.LENGTH_SHORT,
            ).show()
        if (throwable is PendingSignupTokenExpiredException) {
            navController.navigateToLoginAfterAuthenticationFailure()
        }
    }
}

private fun openLocationTermsDetails(
    context: android.content.Context,
    item: LocationTermsItem,
) {
    val intent = createLocationTermsDetailIntent(item) ?: return
    runCatching { context.startActivity(intent) }.onFailure {
        Toast
            .makeText(
                context,
                DEFAULT_TERMS_DETAIL_OPEN_FAILURE_MESSAGE,
                Toast.LENGTH_SHORT,
            ).show()
    }
}

private fun NavHostController.navigateToLoginAfterAuthenticationFailure() {
    navigate(AuthRoute.Login.route) {
        launchSingleTop = true
        popUpTo(graph.findStartDestination().id) {
            inclusive = true
        }
    }
}

internal data class ExternalDetailIntentSpec(
    val action: String,
    val dataString: String,
    val flags: Int,
)

internal fun createTermsGuideDetailIntent(step: TermsGuideStep): Intent? =
    resolveTermsGuideDetailIntentSpec(step)?.toIntent()

internal fun resolveTermsGuideDetailIntentSpec(step: TermsGuideStep): ExternalDetailIntentSpec? =
    resolveTermsGuideDetailUrl(step)?.toExternalDetailIntentSpec()

internal fun resolveTermsGuideDetailUrl(step: TermsGuideStep): String? = step.detailUrl

internal fun createLocationTermsDetailIntent(item: LocationTermsItem): Intent? =
    resolveLocationTermsDetailIntentSpec(item)?.toIntent()

internal fun resolveLocationTermsDetailIntentSpec(item: LocationTermsItem): ExternalDetailIntentSpec? =
    resolveLocationTermsDetailUrl(item)?.toExternalDetailIntentSpec()

internal fun resolveLocationTermsDetailUrl(item: LocationTermsItem): String? =
    when (item) {
        LocationTermsItem.SERVICE_AND_LOCATION_BASED_SERVICE -> SERVICE_AND_LOCATION_TERMS_URL
        LocationTermsItem.SENSITIVE_INFO -> SENSITIVE_INFO_TERMS_URL
        LocationTermsItem.PERSONAL_LOCATION_INFO -> PERSONAL_LOCATION_INFO_TERMS_URL
        LocationTermsItem.OVER_FOURTEEN -> null
    }

private fun String.toExternalDetailIntentSpec(): ExternalDetailIntentSpec =
    ExternalDetailIntentSpec(
        action = Intent.ACTION_VIEW,
        dataString = this,
        flags = Intent.FLAG_ACTIVITY_NEW_TASK,
    )

private fun ExternalDetailIntentSpec.toIntent(): Intent =
    Intent(action, Uri.parse(dataString))
        .addFlags(flags)

private const val SERVICE_AND_LOCATION_TERMS_URL =
    "https://www.notion.so/ryuwon-project/350a58d49be680ab9931f226486dac58?source=copy_link"
private const val SENSITIVE_INFO_TERMS_URL =
    "https://www.notion.so/ryuwon-project/350a58d49be6804a925ef3e41000c3cd?source=copy_link"
private const val PERSONAL_LOCATION_INFO_TERMS_URL =
    "https://www.notion.so/ryuwon-project/350a58d49be68063bbd1f633be85badb?source=copy_link"

private const val DEFAULT_ONBOARDING_COMPLETION_ERROR_MESSAGE: String =
    "온보딩 완료 처리에 실패했습니다. 다시 시도해주세요."

private const val DEFAULT_TERMS_DETAIL_OPEN_FAILURE_MESSAGE: String =
    "약관 페이지를 열 수 없습니다. 잠시 후 다시 시도해주세요."

private const val DEFAULT_PROFILE_EDIT_COMPLETION_ERROR_MESSAGE: String =
    "프로필 변경에 실패했습니다. 다시 시도해주세요."

private const val PROFILE_EDIT_AUTHENTICATION_ERROR_MESSAGE: String =
    "로그인 정보가 만료되었습니다. 다시 로그인해주세요."
