package com.ssafy.e102.eumgil.feature.onboarding.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing

private val OnboardingHeaderPrimary = Color(0xFF2563EB)
private val OnboardingHeaderTrack = Color(0xFFD1D5DB)
private val OnboardingHeaderTitle = Color(0xFF111827)
private val OnboardingHeaderDescription = Color(0xFF6B7280)

@Composable
fun OnboardingProgressHeader(
    currentStep: Int,
    totalSteps: Int,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge,
    titleFontWeight: FontWeight = FontWeight.SemiBold,
) {
    var targetProgress by remember(currentStep, totalSteps) {
        mutableFloatStateOf(((currentStep - 1).coerceAtLeast(0)).toFloat() / totalSteps.toFloat())
    }
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "onboarding_progress_header",
    )

    LaunchedEffect(currentStep, totalSteps) {
        targetProgress = currentStep.toFloat() / totalSteps.toFloat()
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(EumSpacing.xLarge),
    ) {
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = OnboardingHeaderPrimary,
            trackColor = OnboardingHeaderTrack,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
        ) {
            Text(
                text = title.stabilizeOnboardingWrap(),
                modifier = Modifier.fillMaxWidth(),
                style = titleStyle.onboardingHeadingLineBreak(),
                fontWeight = titleFontWeight,
                color = OnboardingHeaderTitle,
                textAlign = TextAlign.Center,
            )

            if (description.isNotBlank()) {
                Text(
                    text = description.stabilizeOnboardingWrap(),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium.onboardingBodyLineBreak(),
                    color = OnboardingHeaderDescription,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
