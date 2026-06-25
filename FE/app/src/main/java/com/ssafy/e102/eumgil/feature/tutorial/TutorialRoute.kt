package com.ssafy.e102.eumgil.feature.tutorial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun MobilityTutorialRoute(
    entryPoint: TutorialEntryPoint,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentStepSequence by rememberSaveable(entryPoint) {
        mutableStateOf(TutorialStep.first().sequence)
    }
    val currentStep =
        TutorialStep.entries.firstOrNull { step -> step.sequence == currentStepSequence }
            ?: TutorialStep.first()

    TutorialScreen(
        uiState = TutorialUiState(step = currentStep, entryPoint = entryPoint),
        onPrimaryActionClick = {
            val nextStep = currentStep.next()
            if (nextStep == null) {
                onCompleted()
            } else {
                currentStepSequence = nextStep.sequence
            }
        },
        onPreviousActionClick = {
            currentStep.previous()?.let { previousStep ->
                currentStepSequence = previousStep.sequence
            }
        },
        onPanelNextStepClick = {
            currentStep.next()?.let { nextStep ->
                currentStepSequence = nextStep.sequence
            }
        },
        onSkipClick = onCompleted,
        modifier = modifier,
    )
}
