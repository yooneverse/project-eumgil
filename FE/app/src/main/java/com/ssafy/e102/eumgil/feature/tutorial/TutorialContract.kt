package com.ssafy.e102.eumgil.feature.tutorial

import androidx.annotation.StringRes
import com.ssafy.e102.eumgil.R

enum class TutorialEntryPoint {
    ONBOARDING,
    GUIDE,
}

enum class TutorialStep(
    val sequence: Int,
    @StringRes val titleRes: Int,
    @StringRes val headlineRes: Int,
    @StringRes val descriptionRes: Int,
) {
    DESTINATION(
        sequence = 1,
        titleRes = R.string.tutorial_destination_title,
        headlineRes = R.string.tutorial_destination_headline,
        descriptionRes = R.string.tutorial_destination_description,
    ),
    ROUTE_COMPARISON(
        sequence = 2,
        titleRes = R.string.tutorial_route_title,
        headlineRes = R.string.tutorial_route_headline,
        descriptionRes = R.string.tutorial_route_description,
    ),
    REPORT(
        sequence = 3,
        titleRes = R.string.tutorial_report_title,
        headlineRes = R.string.tutorial_report_headline,
        descriptionRes = R.string.tutorial_report_description,
    ),
    ;

    val isLast: Boolean
        get() = sequence == TOTAL_STEPS

    fun next(): TutorialStep? = entries.firstOrNull { step -> step.sequence == sequence + 1 }

    fun previous(): TutorialStep? = entries.firstOrNull { step -> step.sequence == sequence - 1 }

    companion object {
        const val TOTAL_STEPS: Int = 3

        fun first(): TutorialStep = DESTINATION
    }
}

data class TutorialUiState(
    val step: TutorialStep,
    val entryPoint: TutorialEntryPoint,
) {
    val currentStep: Int get() = step.sequence
    val totalSteps: Int get() = TutorialStep.TOTAL_STEPS
    val canMovePrevious: Boolean get() = step.previous() != null
    val canMoveNext: Boolean get() = step.next() != null
}

fun resolveTutorialPrimaryActionLabel(
    entryPoint: TutorialEntryPoint,
    isLastStep: Boolean,
): Int =
    if (!isLastStep) {
        R.string.action_next_step
    } else {
        when (entryPoint) {
            TutorialEntryPoint.ONBOARDING -> R.string.tutorial_action_start
            TutorialEntryPoint.GUIDE -> R.string.tutorial_action_close
        }
    }
