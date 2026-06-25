package com.ssafy.e102.eumgil.feature.textsize

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.model.TextSizePreference
import com.ssafy.e102.eumgil.core.designsystem.component.navigation.EumCenteredTopBar
import com.ssafy.e102.eumgil.core.designsystem.theme.EumBorderSubtle
import com.ssafy.e102.eumgil.core.designsystem.theme.EumPrimary600
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing
import com.ssafy.e102.eumgil.core.designsystem.theme.EumWhite
import com.ssafy.e102.eumgil.core.designsystem.theme.LocalAppTextSizeScale

@Composable
fun TextSizeSettingScreen(
    uiState: TextSizeSettingUiState,
    onAction: (TextSizeSettingUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val appliedTextSizeScale = LocalAppTextSizeScale.current
    val controlDensity =
        remember(density, appliedTextSizeScale) {
            density.neutralizedForTextSizeSettingControls(appliedTextSizeScale)
        }

    CompositionLocalProvider(LocalDensity provides controlDensity) {
        TextSizeSettingScaffold(
            uiState = uiState,
            onAction = onAction,
            modifier = modifier,
        )
    }
}

@Composable
private fun TextSizeSettingScaffold(
    uiState: TextSizeSettingUiState,
    onAction: (TextSizeSettingUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            EumCenteredTopBar(
                title = stringResource(id = R.string.text_size_setting_title),
                onBackClick = { onAction(TextSizeSettingUiAction.BackClicked) },
                backContentDescription = stringResource(id = R.string.my_page_back),
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = EumSpacing.large, vertical = EumSpacing.large),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.large),
        ) {
            TextSizeOptionCard(
                options = uiState.options,
                selectedPreference = uiState.selectedPreference,
                onPreferenceSelected = { preference ->
                    onAction(TextSizeSettingUiAction.PreferenceSelected(preference))
                },
            )

            TextSizePreviewCard(selectedPreference = uiState.selectedPreference)
        }
    }
}

@Composable
private fun TextSizeOptionCard(
    options: List<TextSizeSettingOption>,
    selectedPreference: TextSizePreference,
    onPreferenceSelected: (TextSizePreference) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EumRadius.large),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.65f)),
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = EumSpacing.medium),
                        color = EumBorderSubtle.copy(alpha = 0.65f),
                    )
                }

                TextSizeOptionRow(
                    option = option,
                    selected = option.preference == selectedPreference,
                    onClick = { onPreferenceSelected(option.preference) },
                )
            }
        }
    }
}

@Composable
private fun TextSizeOptionRow(
    option: TextSizeSettingOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val labelDensity =
        remember(density, option.preference) {
            density.appliedForTextSizeSettingOption(option.preference)
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 108.dp)
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.RadioButton,
                )
                .padding(horizontal = EumSpacing.medium, vertical = EumSpacing.medium),
        horizontalArrangement = Arrangement.spacedBy(EumSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors =
                RadioButtonDefaults.colors(
                    selectedColor = EumPrimary600,
                ),
        )
        Text(
            modifier = Modifier.width(104.dp),
            text = "Aa",
            style =
                MaterialTheme.typography.displayLarge.copy(
                    fontSize = option.preference.optionSampleFontSize(),
                    lineHeight = option.preference.optionSampleLineHeight(),
                ),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        CompositionLocalProvider(LocalDensity provides labelDensity) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(id = option.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(id = option.scaleLabelRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TextSizePreviewCard(selectedPreference: TextSizePreference) {
    val density = LocalDensity.current
    val previewDensity =
        remember(density, selectedPreference) {
            density.appliedForTextSizeSettingPreview(selectedPreference)
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EumRadius.large),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.65f)),
        shadowElevation = 0.dp,
    ) {
        CompositionLocalProvider(LocalDensity provides previewDensity) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(EumSpacing.large),
                verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
            ) {
                Text(
                    text = stringResource(id = R.string.text_size_setting_preview_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = EumPrimary600,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(id = R.string.text_size_setting_preview_body),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(EumRadius.medium),
                    color = EumWhite.copy(alpha = 0.84f),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = EumSpacing.medium, vertical = EumSpacing.small),
                        horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InfoCircleMark()
                        Text(
                            modifier = Modifier.weight(1f),
                            text = stringResource(id = R.string.text_size_setting_preview_tip),
                            style = MaterialTheme.typography.bodyLarge,
                            color = EumPrimary600,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCircleMark() {
    Surface(
        modifier = Modifier.size(28.dp),
        shape = CircleShape,
        color = EumWhite.copy(alpha = 0.0f),
        border = BorderStroke(2.dp, EumPrimary600),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "i",
                style = MaterialTheme.typography.labelLarge,
                color = EumPrimary600,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private val TextSizeOptionSampleFontSize: TextUnit = 40.sp
private val TextSizeOptionSampleLineHeight: TextUnit = 48.sp

internal fun TextSizePreference.optionSampleFontSize(): TextUnit =
    (TextSizeOptionSampleFontSize.value * safeTextSizeScale()).sp

internal fun TextSizePreference.optionSampleLineHeight(): TextUnit =
    (TextSizeOptionSampleLineHeight.value * safeTextSizeScale()).sp

internal fun Density.neutralizedForTextSizeSettingControls(
    appliedTextSizeScale: Float,
): Density {
    val scale = appliedTextSizeScale.safeTextSizeScale()
    if (scale == 1f) return this

    return Density(
        density = density,
        fontScale = fontScale / scale,
    )
}

internal fun Density.appliedForTextSizeSettingOption(
    optionPreference: TextSizePreference,
): Density = appliedForTextSizeSettingPreview(optionPreference)

internal fun Density.appliedForTextSizeSettingPreview(
    selectedPreference: TextSizePreference,
): Density {
    val scale = selectedPreference.safeTextSizeScale()
    if (scale == 1f) return this

    return Density(
        density = density,
        fontScale = fontScale * scale,
    )
}

private fun TextSizePreference.safeTextSizeScale(): Float =
    scale.safeTextSizeScale()

private fun Float.safeTextSizeScale(): Float =
    takeIf { value -> value.isFinite() && value > 0f } ?: 1f
