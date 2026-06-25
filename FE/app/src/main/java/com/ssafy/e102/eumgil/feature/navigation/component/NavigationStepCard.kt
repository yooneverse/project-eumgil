package com.ssafy.e102.eumgil.feature.navigation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing
import com.ssafy.e102.eumgil.feature.navigation.NavigationFocusedSegmentCardUiState
import com.ssafy.e102.eumgil.feature.navigation.NavigationStepCardUiState
import com.ssafy.e102.eumgil.feature.navigation.NavigationStepMetricUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavigationStepCard(
    uiState: NavigationStepCardUiState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EumRadius.large),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(EumSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = uiState.sectionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                NavigationStepChip(
                    label = uiState.statusLabel,
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary,
                )
            }

            Text(
                text = uiState.instruction,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
                verticalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
            ) {
                NavigationStepChip(
                    label = uiState.emphasisLabel,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.68f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                NavigationStepChip(
                    label = uiState.distanceLabel,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            Text(
                text = uiState.supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
            ) {
                uiState.metrics.forEach { metric ->
                    NavigationStepMetricCard(
                        metric = metric,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationStepMetricCard(
    metric: NavigationStepMetricUiState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(EumRadius.medium),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(EumSpacing.small),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
        ) {
            Text(
                text = metric.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NavigationMetricValueText(
                text = metric.value,
            )
        }
    }
}

@Composable
private fun NavigationMetricValueText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val candidateStyles =
        listOf(
            MaterialTheme.typography.titleSmall,
            MaterialTheme.typography.bodyLarge,
            MaterialTheme.typography.bodyMedium,
            MaterialTheme.typography.labelLarge,
            MaterialTheme.typography.labelMedium,
        )

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val resolvedStyle =
            candidateStyles.firstOrNull { style ->
                textMeasurer.measure(
                    text = AnnotatedString(text),
                    style = style.metricValueTextStyle(),
                    maxLines = 1,
                ).size.width <= maxWidthPx
            } ?: candidateStyles.last()

        Text(
            text = text,
            style = resolvedStyle.metricValueTextStyle(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun TextStyle.metricValueTextStyle(): TextStyle =
    copy(fontWeight = FontWeight.SemiBold)

@Composable
private fun NavigationStepChip(
    label: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(EumRadius.full),
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = label,
            modifier =
                Modifier.padding(
                    horizontal = EumSpacing.small,
                    vertical = EumSpacing.xSmall,
                ),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavigationFocusedSegmentCard(
    uiState: NavigationFocusedSegmentCardUiState,
    showReturnToActiveAction: Boolean,
    onReturnToActiveClick: () -> Unit,
    pendingActiveChangeLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EumRadius.large),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(EumSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavigationStepChip(
                    label = uiState.sequenceLabel,
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary,
                )
                if (showReturnToActiveAction) {
                    TextButton(onClick = onReturnToActiveClick) {
                        Text(text = "현재 구간으로 돌아가기")
                    }
                }
            }

            pendingActiveChangeLabel?.takeIf { label -> label.isNotBlank() }?.let { label ->
                NavigationStepChip(
                    label = label,
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.56f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            Text(
                text = uiState.instruction,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
                verticalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
            ) {
                NavigationStepChip(
                    label = uiState.riskLabel,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                NavigationStepChip(
                    label = uiState.distanceLabel,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.66f),
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            Text(
                text = uiState.supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
