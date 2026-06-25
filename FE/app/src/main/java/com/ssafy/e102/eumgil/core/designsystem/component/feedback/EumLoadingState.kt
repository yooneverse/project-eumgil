package com.ssafy.e102.eumgil.core.designsystem.component.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing

@Composable
fun EumLoadingState(
    title: String,
    description: String,
    indicatorSize: Dp = 28.dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
    ) {
        EumCircularLoadingIndicator(size = indicatorSize)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun EumCircularLoadingIndicator(
    size: Dp = 28.dp,
    modifier: Modifier = Modifier,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = MaterialTheme.colorScheme.secondary,
        trackColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
        strokeWidth = 3.dp,
    )
}
