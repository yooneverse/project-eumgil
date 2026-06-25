package com.ssafy.e102.eumgil.feature.onboarding.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing

data class OnboardingStepAction(
    val label: String,
    val highlighted: Boolean = false,
    val onClick: () -> Unit,
)

enum class OnboardingStepHeaderStyle {
    DEFAULT,
    CENTERED_COMPACT,
}

@Composable
fun OnboardingStepScaffold(
    currentStep: Int,
    totalSteps: Int,
    title: String,
    description: String,
    primaryActionLabel: String,
    primaryActionEnabled: Boolean,
    onPrimaryActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    headerStyle: OnboardingStepHeaderStyle = OnboardingStepHeaderStyle.DEFAULT,
    navigationAction: OnboardingStepAction? = null,
    topAction: OnboardingStepAction? = null,
    secondaryAction: OnboardingStepAction? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val contentSpacing =
        when (headerStyle) {
            OnboardingStepHeaderStyle.CENTERED_COMPACT -> OnboardingCompactLayoutSpacing
            OnboardingStepHeaderStyle.DEFAULT -> EumSpacing.large
        }
    val contentVerticalPadding =
        when (headerStyle) {
            OnboardingStepHeaderStyle.CENTERED_COMPACT -> OnboardingCompactLayoutVerticalPadding
            OnboardingStepHeaderStyle.DEFAULT -> EumSpacing.large
        }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 10.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = EumSpacing.medium, vertical = EumSpacing.medium),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    secondaryAction?.let { action ->
                        TextButton(
                            onClick = action.onClick,
                        ) {
                            Text(
                                text = action.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Button(
                        onClick = onPrimaryActionClick,
                        enabled = primaryActionEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(EumRadius.scaleM),
                    ) {
                        Text(text = primaryActionLabel)
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .statusBarsPadding()
                .padding(horizontal = EumSpacing.medium, vertical = contentVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
        ) {
            if (navigationAction != null || topAction != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    navigationAction?.let { action ->
                        IconButton(
                            onClick = action.onClick,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_action_back),
                                contentDescription = action.label,
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    topAction?.let { action ->
                        OutlinedButton(
                            onClick = action.onClick,
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(EumRadius.full),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor =
                                    if (action.highlighted) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                            ),
                        ) {
                            Text(
                                text = action.label,
                                style = MaterialTheme.typography.labelLarge,
                                color =
                                    if (action.highlighted) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                        }
                    }
                }
            }

            when (headerStyle) {
                OnboardingStepHeaderStyle.DEFAULT -> {
                    OnboardingProgressIndicator(
                        currentStep = currentStep,
                        totalSteps = totalSteps,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
                    ) {
                        Text(
                            text = title.stabilizeOnboardingWrap(),
                            style = MaterialTheme.typography.displayLarge.onboardingHeadingLineBreak(),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        if (description.isNotBlank()) {
                            Text(
                                text = description.stabilizeOnboardingWrap(),
                                style = MaterialTheme.typography.bodyLarge.onboardingBodyLineBreak(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                OnboardingStepHeaderStyle.CENTERED_COMPACT -> {
                    OnboardingProgressHeader(
                        currentStep = currentStep,
                        totalSteps = totalSteps,
                        title = title,
                        description = description,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            content()
        }
    }
}

@Composable
private fun OnboardingProgressIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(EumSpacing.xxSmall),
    ) {
        Text(
            text = stringResource(id = R.string.onboarding_progress_label, currentStep, totalSteps),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        LinearProgressIndicator(
            progress = { currentStep.toFloat() / totalSteps.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}
