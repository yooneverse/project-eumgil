package com.ssafy.e102.eumgil.feature.tutorial

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.designsystem.theme.EumBorderInfo
import com.ssafy.e102.eumgil.core.designsystem.theme.EumPrimary600
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSurfaceSubtle
import com.ssafy.e102.eumgil.core.designsystem.theme.EumTextTertiary
import com.ssafy.e102.eumgil.core.designsystem.theme.EumWhite

@Composable
fun TutorialScreen(
    uiState: TutorialUiState,
    onPrimaryActionClick: () -> Unit,
    onPreviousActionClick: () -> Unit,
    onPanelNextStepClick: () -> Unit,
    onSkipClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryActionLabelRes =
        resolveTutorialPrimaryActionLabel(
            entryPoint = uiState.entryPoint,
            isLastStep = uiState.step.isLast,
        )

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(EumSurfaceSubtle)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = EumSpacing.medium)
                .padding(bottom = EumSpacing.medium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onSkipClick) {
                Text(
                    text = stringResource(id = R.string.tutorial_action_skip),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(top = TutorialLayoutDefaults.headerTopPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TutorialLayoutDefaults.headerVisualGap),
        ) {
            TutorialHeader(uiState = uiState)
            TutorialVisualPanel(
                step = uiState.step,
                currentStep = uiState.currentStep,
                totalSteps = uiState.totalSteps,
                canMovePrevious = uiState.canMovePrevious,
                canMoveNext = uiState.canMoveNext,
                onPreviousActionClick = onPreviousActionClick,
                onPanelNextStepClick = onPanelNextStepClick,
                modifier = Modifier.weight(TutorialLayoutDefaults.visualPanelWeight),
            )
        }

        Spacer(modifier = Modifier.height(TutorialLayoutDefaults.visualPanelButtonGap))

        TutorialBottomActions(
            primaryActionLabel = primaryActionLabelRes,
            canMovePrevious = uiState.canMovePrevious,
            onPreviousActionClick = onPreviousActionClick,
            onPrimaryActionClick = onPrimaryActionClick,
        )
    }
}

@Composable
private fun TutorialBottomActions(
    @StringRes primaryActionLabel: Int,
    canMovePrevious: Boolean,
    onPreviousActionClick: () -> Unit,
    onPrimaryActionClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canMovePrevious) {
            OutlinedButton(
                onClick = onPreviousActionClick,
                modifier =
                    Modifier
                        .width(TutorialLayoutDefaults.previousButtonMinWidth)
                        .heightIn(min = TutorialLayoutDefaults.primaryButtonMinHeight),
                shape = RoundedCornerShape(EumRadius.small),
                border = BorderStroke(TutorialLayoutDefaults.previousButtonBorderWidth, EumBorderInfo),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = EumPrimary600,
                    ),
            ) {
                Text(
                    text = stringResource(id = R.string.tutorial_action_previous),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Button(
            onClick = onPrimaryActionClick,
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = TutorialLayoutDefaults.primaryButtonMinHeight),
            shape = RoundedCornerShape(EumRadius.small),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = EumPrimary600,
                    contentColor = EumWhite,
                ),
        ) {
            Text(
                text = stringResource(id = primaryActionLabel),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun TutorialHeader(uiState: TutorialUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TutorialLayoutDefaults.headerSectionGap),
    ) {
        Text(
            text = stringResource(id = uiState.step.titleRes),
            color = EumPrimary600,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = TutorialLayoutDefaults.headerTitleLineHeight,
        )
        Text(
            text = stringResource(id = uiState.step.headlineRes),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = TutorialLayoutDefaults.headerHeadlineLineHeight,
        )
        Text(
            text = stringResource(id = uiState.step.descriptionRes),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            lineHeight = TutorialLayoutDefaults.headerDescriptionLineHeight,
        )
    }
}

@Composable
private fun TutorialVisualPanel(
    step: TutorialStep,
    currentStep: Int,
    totalSteps: Int,
    canMovePrevious: Boolean,
    canMoveNext: Boolean,
    onPreviousActionClick: () -> Unit,
    onPanelNextStepClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = step.visualContent()

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .widthIn(max = TutorialLayoutDefaults.visualPanelMaxWidth)
                .pointerInput(canMovePrevious, canMoveNext) {
                    detectTapGestures { offset ->
                        val isLeftSide = offset.x < size.width / 2f
                        when {
                            isLeftSide && canMovePrevious -> onPreviousActionClick()
                            !isLeftSide && canMoveNext -> onPanelNextStepClick()
                        }
                    }
                },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = EumSpacing.medium,
                        vertical = TutorialLayoutDefaults.visualPanelVerticalPadding,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TutorialLayoutDefaults.sceneContentVerticalGap),
        ) {
            TutorialFlatIllustration(
                content = content,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            )
            TutorialPagerIndicator(currentStep = currentStep, totalSteps = totalSteps)
        }
    }
}

@Composable
private fun TutorialFlatIllustration(
    content: TutorialVisualContent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        TutorialIllustrationScene(content = content)
        if (content.scene != TutorialIllustrationSceneType.DESTINATION_SEARCH && content.chips.isNotEmpty()) {
            Spacer(modifier = Modifier.height(TutorialLayoutDefaults.illustrationContentGap))
            TutorialFilterChipRow(chips = content.chips)
        }
    }
}

@Composable
private fun TutorialIllustrationScene(content: TutorialVisualContent) {
    when (content.scene) {
        TutorialIllustrationSceneType.DESTINATION_SEARCH -> TutorialDestinationSearchScene(content = content)
        TutorialIllustrationSceneType.ROUTE_COMPARISON -> TutorialRouteComparisonScene(content = content)
        TutorialIllustrationSceneType.REPORT_SUBMISSION -> TutorialReportSubmissionScene()
    }
}

@Composable
private fun TutorialDestinationSearchScene(content: TutorialVisualContent) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TutorialLayoutDefaults.illustrationHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TutorialLayoutDefaults.destinationControlVerticalGap),
    ) {
        TutorialSearchBar(
            iconRes = content.heroIconRes,
            labelRes = content.primaryLabelRes,
            trailingIconRes = R.drawable.ic_permission_mic,
        )
        TutorialFilterChipRow(
            chips = content.chips,
        )
        TutorialDestinationResultPanel(content = content)
        TutorialStatusStrip(
            iconRes = R.drawable.ic_nav_category_grid,
            titleRes = R.string.tutorial_destination_status_title,
            valueRes = R.string.tutorial_destination_status_value,
        )
    }
}

@Composable
private fun TutorialRouteComparisonScene(content: TutorialVisualContent) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TutorialLayoutDefaults.illustrationHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        TutorialRouteCard(
            iconRes = content.heroIconRes,
            labelRes = content.primaryLabelRes,
            time = stringResource(id = R.string.tutorial_route_recommended_time),
            selected = true,
        )
        Spacer(modifier = Modifier.height(TutorialLayoutDefaults.sceneContentVerticalGap))
        TutorialRouteCard(
            iconRes = R.drawable.ic_route_time,
            labelRes = content.secondaryLabelRes,
            time = stringResource(id = R.string.tutorial_route_efficient_time),
            selected = false,
        )
        Spacer(modifier = Modifier.height(TutorialLayoutDefaults.sceneContentVerticalGap))
        TutorialStatusStrip(
            iconRes = R.drawable.ic_map_selected_pin_blue,
            titleRes = R.string.tutorial_route_status_title,
            valueRes = R.string.tutorial_route_status_value,
        )
    }
}

@Composable
private fun TutorialReportSubmissionScene() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TutorialLayoutDefaults.illustrationHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TutorialLayoutDefaults.reportGridButtonGap),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TutorialLayoutDefaults.sceneContentVerticalGap),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(TutorialLayoutDefaults.sceneContentVerticalGap)) {
                TutorialReportTile(
                    iconRes = R.drawable.ic_report_stairs,
                    labelRes = R.string.tutorial_report_stairs_step,
                )
                TutorialReportTile(
                    iconRes = R.drawable.ic_report_tactile_damage,
                    labelRes = R.string.tutorial_report_braille_block,
                    selected = true,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(TutorialLayoutDefaults.sceneContentVerticalGap)) {
                TutorialReportTile(
                    iconRes = R.drawable.ic_report_sidewalk,
                    labelRes = R.string.tutorial_report_sidewalk_missing,
                )
                TutorialReportTile(
                    iconRes = R.drawable.ic_report_ramp,
                    labelRes = R.string.tutorial_report_ramp,
                )
            }
        }
        TutorialStatusStrip(
            iconRes = R.drawable.ic_report_tactile_damage,
            titleRes = R.string.tutorial_report_status_title,
            valueRes = R.string.tutorial_report_status_value,
        )
        TutorialReportSubmitButton()
    }
}

@Composable
private fun TutorialSearchBar(
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    @DrawableRes trailingIconRes: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .widthIn(max = TutorialLayoutDefaults.searchBarMaxWidth)
                .height(TutorialLayoutDefaults.searchBarHeight),
        shape = RoundedCornerShape(EumRadius.full),
        color = EumWhite,
        border = BorderStroke(TutorialLayoutDefaults.hairlineWidth, EumBorderInfo),
        shadowElevation = TutorialLayoutDefaults.floatingPanelElevation,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = EumSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(TutorialLayoutDefaults.floatingPanelIconSize),
                tint = EumPrimary600,
            )
            Text(
                text = stringResource(id = labelRes),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Icon(
                painter = painterResource(id = trailingIconRes),
                contentDescription = null,
                modifier = Modifier.size(TutorialLayoutDefaults.floatingPanelIconSize),
                tint = EumPrimary600,
            )
        }
    }
}

@Composable
private fun TutorialDestinationResultPanel(content: TutorialVisualContent) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .widthIn(max = TutorialLayoutDefaults.destinationResultPanelMaxWidth),
        shape = RoundedCornerShape(EumRadius.small),
        color = EumWhite,
        border = BorderStroke(TutorialLayoutDefaults.hairlineWidth, EumBorderInfo),
        shadowElevation = TutorialLayoutDefaults.mockPhoneElevation,
    ) {
        Column(
            modifier = Modifier.padding(EumSpacing.small),
            verticalArrangement = Arrangement.spacedBy(TutorialLayoutDefaults.destinationResultPanelGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TutorialLayoutDefaults.filterChipGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_nav_facility),
                    contentDescription = null,
                    modifier = Modifier.size(TutorialLayoutDefaults.filterChipIconSize),
                    tint = EumPrimary600,
                )
                Text(
                    text = stringResource(id = content.secondaryLabelRes),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(id = R.string.tutorial_destination_result_count),
                    color = EumPrimary600,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TutorialDestinationResultRow(
                iconRes = content.chips[0].iconRes,
                labelRes = content.chips[0].labelRes,
                statusRes = R.string.tutorial_destination_result_nearby,
            )
            TutorialDestinationResultRow(
                iconRes = content.chips[1].iconRes,
                labelRes = content.chips[1].labelRes,
                statusRes = R.string.tutorial_destination_result_available,
            )
        }
    }
}

@Composable
private fun TutorialDestinationResultRow(
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    @StringRes statusRes: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TutorialLayoutDefaults.filterChipGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(TutorialLayoutDefaults.filterChipIconSize),
            tint = EumPrimary600,
        )
        Text(
            text = stringResource(id = labelRes),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(id = statusRes),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun TutorialStatusStrip(
    @DrawableRes iconRes: Int,
    @StringRes titleRes: Int,
    @StringRes valueRes: Int,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .widthIn(max = TutorialLayoutDefaults.statusStripMaxWidth)
                .height(TutorialLayoutDefaults.statusStripHeight),
        shape = RoundedCornerShape(EumRadius.small),
        color = TutorialLayoutDefaults.statusStripContainerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = EumSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TutorialLayoutDefaults.filterChipGap),
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(TutorialLayoutDefaults.filterChipIconSize),
                tint = EumPrimary600,
            )
            Text(
                text = stringResource(id = titleRes),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(id = valueRes),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TutorialLayoutDefaults.statusStripValueAlpha),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun TutorialRouteCard(
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    time: String,
    selected: Boolean,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TutorialLayoutDefaults.routeCardHeight),
        shape = RoundedCornerShape(EumRadius.large),
        color = EumWhite,
        border =
            BorderStroke(
                width = TutorialLayoutDefaults.routeCardBorderWidth,
                color = if (selected) EumPrimary600 else EumBorderInfo,
            ),
        shadowElevation = if (selected) TutorialLayoutDefaults.mockPhoneElevation else 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = EumSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EumSpacing.medium),
        ) {
            TutorialRadioDot(selected = selected)
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(TutorialLayoutDefaults.smallCardIconSize),
                tint = EumPrimary600,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = labelRes),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = time,
                    color = EumPrimary600,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun TutorialRadioDot(selected: Boolean) {
    Box(
        modifier =
            Modifier
                .size(TutorialLayoutDefaults.radioDotOuterSize)
                .clip(RoundedCornerShape(TutorialLayoutDefaults.pillCorner))
                .background(if (selected) EumPrimary600.copy(alpha = 0.18f) else EumBorderInfo),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(TutorialLayoutDefaults.radioDotInnerSize)
                    .clip(RoundedCornerShape(TutorialLayoutDefaults.pillCorner))
                    .background(if (selected) EumPrimary600 else EumWhite),
        )
    }
}

@Composable
private fun TutorialReportTile(
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    selected: Boolean = false,
) {
    Surface(
        modifier =
            Modifier
                .width(TutorialLayoutDefaults.reportTileWidth)
                .height(TutorialLayoutDefaults.reportTileHeight),
        shape = RoundedCornerShape(EumRadius.large),
        color = if (selected) EumPrimary600 else EumWhite,
        border = BorderStroke(TutorialLayoutDefaults.hairlineWidth, if (selected) EumPrimary600 else EumBorderInfo),
        shadowElevation = if (selected) TutorialLayoutDefaults.mockPhoneElevation else 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(EumSpacing.small),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(TutorialLayoutDefaults.smallCardIconSize),
                tint = if (selected) EumWhite else EumPrimary600,
            )
            Spacer(modifier = Modifier.height(TutorialLayoutDefaults.tightGap))
            Text(
                text = stringResource(id = labelRes),
                color = if (selected) EumWhite else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TutorialReportSubmitButton() {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .widthIn(max = TutorialLayoutDefaults.reportSubmitButtonMaxWidth)
                .height(TutorialLayoutDefaults.reportSubmitButtonHeight),
        shape = RoundedCornerShape(EumRadius.small),
        color = EumPrimary600,
        shadowElevation = TutorialLayoutDefaults.mockPhoneElevation,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(id = R.string.tutorial_report_action_submit),
                color = EumWhite,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun TutorialFilterChipRow(
    chips: List<TutorialFilterChip>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .widthIn(max = TutorialLayoutDefaults.searchBarMaxWidth)
                .fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(
                space = TutorialLayoutDefaults.filterChipGap,
                alignment = Alignment.CenterHorizontally,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { chip ->
            TutorialFilterChip(chip = chip)
        }
    }
}

@Composable
private fun TutorialFilterChip(
    chip: TutorialFilterChip,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = TutorialLayoutDefaults.filterChipMinHeight),
        shape = RoundedCornerShape(TutorialLayoutDefaults.filterChipCornerRadius),
        color = EumWhite,
        border = BorderStroke(TutorialLayoutDefaults.hairlineWidth, TutorialLayoutDefaults.filterChipBorderColor),
        shadowElevation = TutorialLayoutDefaults.mockPhoneElevation,
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = TutorialLayoutDefaults.filterChipHorizontalPadding,
                    vertical = TutorialLayoutDefaults.filterChipVerticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TutorialLayoutDefaults.filterChipGap),
        ) {
            Icon(
                painter = painterResource(id = chip.iconRes),
                contentDescription = null,
                modifier = Modifier.size(TutorialLayoutDefaults.filterChipIconSize),
                tint = EumPrimary600,
            )
            Text(
                text = stringResource(id = chip.labelRes),
                color = EumPrimary600,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = TutorialLayoutDefaults.filterChipLabelLineHeight,
            )
        }
    }
}

private data class TutorialVisualContent(
    val scene: TutorialIllustrationSceneType,
    @DrawableRes val heroIconRes: Int,
    @StringRes val primaryLabelRes: Int,
    @StringRes val secondaryLabelRes: Int,
    val chips: List<TutorialFilterChip>,
)

private enum class TutorialIllustrationSceneType {
    DESTINATION_SEARCH,
    ROUTE_COMPARISON,
    REPORT_SUBMISSION,
}

private data class TutorialFilterChip(
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int,
)

private fun TutorialStep.visualContent(): TutorialVisualContent =
    when (this) {
        TutorialStep.DESTINATION ->
            TutorialVisualContent(
                scene = TutorialIllustrationSceneType.DESTINATION_SEARCH,
                heroIconRes = R.drawable.ic_nav_search,
                primaryLabelRes = R.string.tutorial_destination_item_search_title,
                secondaryLabelRes = R.string.tutorial_destination_item_filter_title,
                chips =
                    listOf(
                        TutorialFilterChip(
                            iconRes = R.drawable.ic_user_wheelchair_compact,
                            labelRes = R.string.tutorial_filter_toilet,
                        ),
                        TutorialFilterChip(
                            iconRes = R.drawable.ic_place_elevator,
                            labelRes = R.string.tutorial_filter_elevator,
                        ),
                    ),
            )

        TutorialStep.ROUTE_COMPARISON ->
            TutorialVisualContent(
                scene = TutorialIllustrationSceneType.ROUTE_COMPARISON,
                heroIconRes = R.drawable.ic_route_start_navigation,
                primaryLabelRes = R.string.tutorial_route_recommended,
                secondaryLabelRes = R.string.tutorial_route_efficient,
                chips = emptyList(),
            )

        TutorialStep.REPORT ->
            TutorialVisualContent(
                scene = TutorialIllustrationSceneType.REPORT_SUBMISSION,
                heroIconRes = R.drawable.ic_nav_report,
                primaryLabelRes = R.string.tutorial_report_item_location_title,
                secondaryLabelRes = R.string.tutorial_report_item_type_title,
                chips = emptyList(),
            )
    }

@Composable
private fun TutorialPagerIndicator(
    currentStep: Int,
    totalSteps: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(TutorialLayoutDefaults.tightGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalSteps) { index ->
            val selected = index + 1 == currentStep
            Box(
                modifier =
                    Modifier
                        .width(
                            if (selected) {
                                TutorialLayoutDefaults.indicatorSelectedWidth
                            } else {
                                TutorialLayoutDefaults.indicatorDefaultWidth
                            },
                        )
                        .height(TutorialLayoutDefaults.indicatorHeight)
                        .clip(RoundedCornerShape(TutorialLayoutDefaults.pillCorner))
                        .background(if (selected) EumPrimary600 else TutorialLayoutDefaults.indicatorTrackColor),
            )
        }
        Spacer(modifier = Modifier.width(TutorialLayoutDefaults.microGap))
        Text(
            text = stringResource(id = R.string.tutorial_progress_count, currentStep, totalSteps),
            color = EumTextTertiary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

internal object TutorialLayoutDefaults {
    const val totalStepCount: Int = TutorialStep.TOTAL_STEPS
    const val supportingItemCount: Int = 0
    const val destinationFilterChipCount: Int = 2
    const val routeAccessibilityChipCount: Int = 0
    const val reportCategoryChipCount: Int = 4
    const val showsEmphasisChip: Boolean = false
    const val usesLayeredFlatIllustration: Boolean = true
    const val usesGeneratedBitmapIllustration: Boolean = false
    const val usesWhitePanelFrame: Boolean = false
    const val distinctIllustrationSceneCount: Int = 3
    const val usesNavigationSafeZone: Boolean = true
    const val usesIllustrationHalo: Boolean = false
    const val destinationFiltersAttachToSearch: Boolean = true
    const val destinationSearchShowsMic: Boolean = true
    const val destinationShowsMapPreview: Boolean = false
    const val destinationUsesSearchAndFilterOnly: Boolean = true
    const val destinationFilterRowScrollable: Boolean = false
    const val destinationFilterUsesLatestMapIcons: Boolean = true
    const val destinationFilterUsesOriginalMapChipShape: Boolean = true
    const val destinationShowsFilterResultPanel: Boolean = true
    const val destinationFilterRowCount: Int = 1
    const val usesServiceLikeStatusStrip: Boolean = true
    const val statusStripUsesTransparentLayer: Boolean = true
    const val statusStripUsesSoftBlueLayer: Boolean = true
    const val statusStripUsesBorder: Boolean = false
    const val statusStripUsesLowEmphasisValue: Boolean = true
    const val routeStatusUsesMapMarkerIcon: Boolean = true
    const val statusStripCountPerScene: Int = 1
    const val routeDescriptionBreaksAfterSettingComma: Boolean = false
    const val routeDescriptionUsesSingleLine: Boolean = true
    const val routeCopyUsesRouteWording: Boolean = true
    const val reportUsesLatestReportTypeIcons: Boolean = true
    const val reportHighlightsSelectedCategory: Boolean = true
    const val reportShowsSubmitButtonBelowGrid: Boolean = true
    const val reportDescriptionMentionsRouteContribution: Boolean = true
    const val visualPanelWeight: Float = 1f
    const val hasHeroIconBackground: Boolean = false
    const val firstStepWithPreviousAction: Int = 2
    const val panelTouchNavigationZoneWeight: Float = 1f

    val primaryButtonMinHeight = 56.dp
    val previousButtonMinWidth = 88.dp
    val previousButtonBorderWidth = 1.dp
    val visualPanelButtonGap = 12.dp
    val visualPanelMaxWidth = 420.dp
    val visualPanelVerticalPadding = 4.dp
    val headerTopPadding = 20.dp

    val headerSectionGap = 8.dp
    val headerVisualGap = 24.dp
    val headerTitleLineHeight = 30.sp
    val headerHeadlineLineHeight = 34.sp
    val headerDescriptionLineHeight = 22.sp

    val tightGap = 8.dp
    val microGap = 4.dp
    val hairlineWidth = 1.dp

    val illustrationHeight = 316.dp
    val illustrationContentGap = 12.dp
    val sceneContentVerticalGap = 12.dp
    val heroIconSize = 56.dp
    val mockPhoneElevation = 2.dp
    val searchBarMaxWidth = 340.dp
    val searchBarHeight = 58.dp
    val destinationControlVerticalGap = 12.dp
    val destinationResultPanelMaxWidth = 340.dp
    val destinationResultPanelGap = 12.dp
    val statusStripMaxWidth = 340.dp
    val statusStripHeight = 40.dp
    val statusStripContainerAlpha = 0.86f
    val statusStripValueAlpha = 0.72f
    val floatingPanelHeight = 56.dp
    val floatingPanelElevation = 3.dp
    val floatingPanelIconSize = 22.dp
    val smallCardWidth = 72.dp
    val smallCardHeight = 60.dp
    val smallCardIconSize = 28.dp
    val routeCardHeight = 96.dp
    val routeCardBorderWidth = 2.dp
    val radioDotOuterSize = 24.dp
    val radioDotInnerSize = 12.dp
    val reportTileWidth = 152.dp
    val reportTileHeight = 100.dp
    val reportGridButtonGap = 12.dp
    val reportSubmitButtonMaxWidth = 340.dp
    val reportSubmitButtonHeight = 50.dp
    val filterChipHorizontalPadding = 13.dp
    val filterChipVerticalPadding = 9.dp
    val filterChipIconSize = 18.dp
    val filterChipGap = 8.dp
    val filterChipMinHeight = 38.dp
    val filterChipCornerRadius = 8.dp
    val filterChipLabelLineHeight = 16.sp
    val filterChipRowMaxItemCount: Int = 2

    val indicatorSelectedWidth = 70.dp
    val indicatorDefaultWidth = 54.dp
    val indicatorHeight = 6.dp
    val pillCorner = 99.dp

    val filterChipBorderColor = EumPrimary600.copy(alpha = 0.28f)
    val indicatorTrackColor = Color(0xFFD9DDE7)
    val statusStripContainerColor = Color(0xFFEAF4FF).copy(alpha = statusStripContainerAlpha)
}
