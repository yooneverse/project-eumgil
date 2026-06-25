package com.ssafy.e102.eumgil.core.designsystem.component.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.common.model.PlaceholderAction
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing

@Composable
fun EumPlaceholderScaffold(
    title: String,
    description: String,
    featurePath: String,
    actions: List<PlaceholderAction>,
    environmentItems: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(EumSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(EumSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
            ) {
                Text(
                    text = stringResource(id = R.string.placeholder_feature_path_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = featurePath,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (environmentItems.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(EumSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
                ) {
                    Text(
                        text = stringResource(id = R.string.placeholder_environment_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    environmentItems.forEach { item ->
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        content()

        Column(
            verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }

        Text(
            text = stringResource(id = R.string.placeholder_navigation_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        actions.forEach { action ->
            if (action.isPrimary) {
                Button(
                    onClick = action.onClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = action.label)
                }
            } else {
                OutlinedButton(
                    onClick = action.onClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = action.label)
                }
            }
        }
    }
}
