package com.ssafy.e102.eumgil.feature.terms

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ssafy.e102.eumgil.feature.lowvision.LowVisionFontTheme

/**
 * Route wrapper for [TermsGuideScreen].
 *
 * Holds the active [TermsGuideStep] in saveable state so configuration changes
 * (rotation, process recreation) keep the user on the same step. Caller passes
 * the entry step and handlers; this layer drives the local advance flow:
 *
 *  - On double-tap of the main card while not on the last step → advance to next step
 *    locally, no NavController push required.
 *  - On double-tap while on the last step → invoke [onCompleted].
 *  - On "자세히 보기" → invoke [onRequestDetails] with the current step so the
 *    caller can route to the detailed terms screen for that topic.
 *
 * If the consumer wants each step to be a distinct backstack entry instead of an
 * in-place advance (e.g. to honor the "각 화면이 분기 시작점" requirement), they
 * can navigate to `OnboardingRoute.TermsGuide.createRoute(step.routeValue)` for
 * the next step inside [onCompleted] / a custom advance handler — the route is
 * already keyed by step.
 */
@Composable
fun TermsGuideRoute(
    initialStep: TermsGuideStep,
    onCompleted: () -> Unit,
    onRequestDetails: (TermsGuideStep) -> Unit,
    modifier: Modifier = Modifier,
) {
    var stepRouteValue by rememberSaveable(initialStep.routeValue) {
        mutableStateOf(initialStep.routeValue)
    }

    val currentStep = TermsGuideStep.fromRouteValue(stepRouteValue) ?: initialStep
    val uiState = TermsGuideUiState(step = currentStep)

    LowVisionFontTheme {
        TermsGuideScreen(
            uiState = uiState,
            onAdvance = {
                val next = currentStep.next()
                if (next == null) {
                    onCompleted()
                } else {
                    stepRouteValue = next.routeValue
                }
            },
            onMoreDetails = { onRequestDetails(currentStep) },
            modifier = modifier,
        )
    }
}
