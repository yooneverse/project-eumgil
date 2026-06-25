package com.ssafy.e102.eumgil.feature.report

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.designsystem.component.navigation.EumCenteredTopBar
import com.ssafy.e102.eumgil.core.designsystem.theme.EumBorderSubtle
import com.ssafy.e102.eumgil.core.designsystem.theme.EumPrimary200
import com.ssafy.e102.eumgil.core.designsystem.theme.EumPrimary600
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSurfaceInfo
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSurfaceSubtle
import com.ssafy.e102.eumgil.core.designsystem.theme.EumTextMuted
import com.ssafy.e102.eumgil.core.designsystem.theme.EumTextSecondary
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraAnimation
import com.kakao.vectormap.camera.CameraUpdateFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    uiState: ReportUiState,
    onAction: (ReportUiAction) -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = reportTopBarShowsBackButton(uiState.currentStep)) {
        onAction(ReportUiAction.BackClicked)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ReportTopBar(
                title =
                    reportStepTitle(
                        step = uiState.currentStep,
                        selectedType = uiState.reportType.value,
                    ),
                showBackButton = reportTopBarShowsBackButton(uiState.currentStep),
                onBackClick = { onAction(ReportUiAction.BackClicked) },
            )
        },
        bottomBar = {
            ReportBottomBar(
                uiState = uiState,
                onAction = onAction,
                reserveNavigationBarPadding = shouldReserveReportBottomNavigationInsets(uiState.entryPoint),
            )
        },
    ) { innerPadding ->
        if (uiState.currentStep == ReportStep.Complete) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = EumSpacing.medium, vertical = EumSpacing.medium),
                contentAlignment = Alignment.Center,
            ) {
                ReportCompleteStep(uiState = uiState, onAction = onAction)
            }
        } else {
            // TypeSelection은 그리드가 남은 공간을 채워야 하므로 verticalScroll 미사용 (weight 사용 가능).
            // 나머지 스텝은 폼 길이가 가변적이라 scrollable Column 유지.
            // scrollState는 ReportRoute에서 hoist하여 ScrollToFirstError 이벤트로 외부 제어 가능.
            val isHomeStep = uiState.currentStep == ReportStep.Home
            val isFlexStep = isHomeStep || uiState.currentStep == ReportStep.TypeSelection
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .then(
                            if (isFlexStep) {
                                Modifier
                            } else {
                                Modifier.verticalScroll(scrollState)
                            },
                        )
                        .padding(
                            start = EumSpacing.medium,
                            top = EumSpacing.medium,
                            end = EumSpacing.medium,
                            bottom = EumSpacing.medium,
                        ),
                verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
            ) {
                when (uiState.currentStep) {
                    ReportStep.Home ->
                        ReportHomeStep(
                            uiState = uiState,
                            onAction = onAction,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    ReportStep.TypeSelection ->
                        ReportTypeStep(
                            input = uiState.reportType,
                            onAction = onAction,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    ReportStep.LocationConfirm ->
                        ReportLocationStep(
                            input = uiState.location,
                            selectedType = uiState.reportType.value,
                            onAction = onAction,
                        )
                    ReportStep.DetailInput ->
                        ReportDetailStep(
                            uiState = uiState,
                            onAction = onAction,
                        )
                    ReportStep.Complete -> Unit
                }
            }
        }
    }
}

@Composable
private fun ReportTopBar(
    title: String,
    showBackButton: Boolean,
    onBackClick: () -> Unit,
) {
    EumCenteredTopBar(
        title = title,
        onBackClick = if (showBackButton) onBackClick else null,
        backContentDescription =
            if (showBackButton) {
                stringResource(id = R.string.action_go_back_previous_step)
            } else {
                null
            },
        titleFontWeight = FontWeight.SemiBold,
    )
}

internal fun reportTopBarShowsBackButton(step: ReportStep): Boolean =
    when (step) {
        ReportStep.TypeSelection, ReportStep.LocationConfirm, ReportStep.DetailInput -> true
        ReportStep.Home, ReportStep.Complete -> false
    }

internal fun shouldReserveReportBottomNavigationInsets(entryPoint: ReportEntryPoint): Boolean =
    entryPoint == ReportEntryPoint.NavigationGuidance

@Composable
private fun ReportBottomBar(
    uiState: ReportUiState,
    onAction: (ReportUiAction) -> Unit,
    reserveNavigationBarPadding: Boolean,
) {
    when (uiState.currentStep) {
        ReportStep.Home -> Unit
        ReportStep.TypeSelection ->
            ReportPrimaryActionBar(
                label = "다음",
                enabled = uiState.reportType.value != null,
                onClick = { onAction(ReportUiAction.NextStepClicked) },
                suppressRipple = shouldSuppressReportPrimaryActionRipple(uiState.currentStep),
                reserveNavigationBarPadding = reserveNavigationBarPadding,
            )
        ReportStep.LocationConfirm ->
            ReportPrimaryActionBar(
                label = "다음",
                enabled = uiState.isLocationStepConfirmable,
                onClick = { onAction(ReportUiAction.NextStepClicked) },
                suppressRipple = shouldSuppressReportPrimaryActionRipple(uiState.currentStep),
                reserveNavigationBarPadding = reserveNavigationBarPadding,
            )
        ReportStep.DetailInput -> {
            val submitting = uiState.submitState is ReportSubmitState.Submitting
            ReportPrimaryActionBar(
                label = if (submitting) "접수 중" else "접수하기",
                enabled = uiState.isSubmitEnabled,
                onClick = { onAction(ReportUiAction.SubmitClicked) },
                suppressRipple = shouldSuppressReportPrimaryActionRipple(uiState.currentStep),
                reserveNavigationBarPadding = reserveNavigationBarPadding,
            )
        }
        ReportStep.Complete -> Unit
    }
}

internal fun shouldSuppressReportPrimaryActionRipple(step: ReportStep): Boolean =
    step == ReportStep.Complete

@Composable
private fun ReportPrimaryActionBar(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    suppressRipple: Boolean = false,
    reserveNavigationBarPadding: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 0.dp,
    ) {
        ReportPrimaryActionButton(
            label = label,
            enabled = enabled,
            onClick = onClick,
            suppressRipple = suppressRipple,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (reserveNavigationBarPadding) {
                            Modifier.navigationBarsPadding()
                        } else {
                            Modifier
                        },
                    )
                    .padding(EumSpacing.medium),
        )
    }
}

@Composable
private fun ReportPrimaryActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    suppressRipple: Boolean = false,
) {
    if (suppressRipple) {
        NoRippleReportPrimaryActionButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            contentPadding = PaddingValues(vertical = EumSpacing.small),
        ) {
            Text(text = label)
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            shape = RoundedCornerShape(EumRadius.small),
            contentPadding = PaddingValues(vertical = EumSpacing.small),
        ) {
            Text(text = label)
        }
    }
}

@Composable
private fun ReportStepActionBar(
    primaryLabel: String,
    enabled: Boolean,
    onPrimaryClick: () -> Unit,
    secondaryLabel: String,
    onSecondaryClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(EumSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(EumSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onSecondaryClick,
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                shape = RoundedCornerShape(EumRadius.small),
            ) {
                Text(text = secondaryLabel)
            }
            Button(
                onClick = onPrimaryClick,
                enabled = enabled,
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                shape = RoundedCornerShape(EumRadius.small),
            ) {
                Text(text = primaryLabel)
            }
        }
    }
}

@Composable
private fun NoRippleReportPrimaryActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(EumRadius.small),
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = EumSpacing.medium, vertical = EumSpacing.small),
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Surface(
        modifier = modifier,
        shape = shape,
        color = if (enabled) containerColor else disabledContainerColor,
        contentColor = if (enabled) contentColor else disabledContentColor,
        border = border,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    )
                    .padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun ReportHomeStep(
    uiState: ReportUiState,
    onAction: (ReportUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
    ) {
        ReportHomeCtaCard(onAction = onAction)
        ReportHomeStatusSection(
            pendingCount = uiState.processingCounts.pending,
            approvedCount = uiState.processingCounts.approved,
            onHistoryClick = { onAction(ReportUiAction.ReportHistoryClicked) },
        )
        ReportHomeRecentSection(
            reports = uiState.recentReports,
            onReportClick = { historyId -> onAction(ReportUiAction.RecentReportClicked(historyId)) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ReportHomeCtaCard(
    onAction: (ReportUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EumRadius.large),
        color = EumSurfaceInfo,
        border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.55f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(EumSpacing.large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EumSpacing.medium),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
            ) {
                Text(
                    text = "불편한 길을\n발견하셨나요?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "사진과 위치를 남겨주시면\n더 안전한 길 안내로 개선할게요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EumTextSecondary,
                )
                Spacer(modifier = Modifier.height(EumSpacing.xSmall))
                ReportHomeCtaButton(
                    onClick = { onAction(ReportUiAction.StartNewReportClicked) },
                )
            }
            Icon(
                painter = painterResource(id = R.drawable.ic_map_selected_pin_blue),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = Color.Unspecified,
            )
        }
    }
}

@Composable
private fun ReportHomeCtaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .height(52.dp)
                .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(EumRadius.small),
        color = EumPrimary600,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = EumSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(percent = 50),
                color = MaterialTheme.colorScheme.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier =
                            Modifier
                                .width(18.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(EumPrimary600),
                    )
                    Box(
                        modifier =
                            Modifier
                                .width(4.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(50))
                                .background(EumPrimary600),
                    )
                }
            }
            Spacer(modifier = Modifier.width(EumSpacing.small))
            Text(
                text = "불편한 길 제보하기",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ReportHomeStatusSection(
    pendingCount: Int,
    approvedCount: Int,
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "내 제보 현황",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "전체 내역 >",
                modifier = Modifier.clickable(onClick = onHistoryClick),
                style = MaterialTheme.typography.bodyMedium,
                color = EumTextMuted,
            )
        }
        ReportHomeStatusCard(
            pendingCount = pendingCount,
            approvedCount = approvedCount,
        )
    }
}

@Composable
private fun ReportHomeStatusCard(
    pendingCount: Int,
    approvedCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EumRadius.large),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.65f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(EumSpacing.medium),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReportHomeStatusCount(
                iconRes = R.drawable.ic_mypage_report_history,
                count = pendingCount,
                label = "접수됨",
                labelColor = EumTextMuted,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier =
                    Modifier
                        .height(56.dp)
                        .width(1.dp)
                        .background(EumBorderSubtle),
            )
            ReportHomeStatusCount(
                iconRes = R.drawable.ic_status_check,
                count = approvedCount,
                label = "반영 완료",
                labelColor = EumPrimary600,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReportHomeStatusCount(
    @DrawableRes iconRes: Int,
    count: Int,
    label: String,
    labelColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(percent = 50),
            color = if (labelColor == EumPrimary600) EumPrimary200.copy(alpha = 0.7f) else EumSurfaceSubtle,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = Color.Unspecified,
                )
            }
        }
        Spacer(modifier = Modifier.width(EumSpacing.small))
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "건",
                    modifier = Modifier.padding(bottom = 2.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = if (labelColor == EumPrimary600) EumPrimary200.copy(alpha = 0.55f) else EumSurfaceSubtle,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = EumSpacing.small, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = labelColor,
                )
            }
        }
    }
}

@Composable
private fun ReportHomeRecentSection(
    reports: List<ReportRecentUiModel>,
    onReportClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
    ) {
        Text(
            text = "최근 제보",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            shape = RoundedCornerShape(EumRadius.large),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.65f)),
            shadowElevation = 0.dp,
        ) {
            if (reports.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(144.dp)
                            .padding(EumSpacing.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "아직 접수된 제보가 없습니다.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = EumTextMuted,
                    )
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            start = EumSpacing.medium,
                            end = EumSpacing.medium,
                            bottom = EumSpacing.medium,
                        ),
                ) {
                    itemsIndexed(
                        items = reports,
                        key = { _, report -> report.historyId },
                    ) { index, report ->
                        Column {
                            ReportHomeRecentItem(
                                report = report,
                                onClick = { onReportClick(report.historyId) },
                            )
                            if (index != reports.lastIndex) {
                                HorizontalDivider(color = EumBorderSubtle)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportHomeRecentItem(
    report: ReportRecentUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = EumSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EumSpacing.medium),
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(EumRadius.medium),
            color = EumSurfaceInfo,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = report.title.toReportHomeIconRes()),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = Color.Unspecified,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = report.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = report.address,
                style = MaterialTheme.typography.bodyMedium,
                color = EumTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = report.submittedAtText,
                style = MaterialTheme.typography.bodyMedium,
                color = EumTextMuted,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = if (report.isApproved) EumPrimary200.copy(alpha = 0.55f) else EumSurfaceSubtle,
            ) {
                Text(
                    text = report.statusLabel,
                    modifier = Modifier.padding(horizontal = EumSpacing.small, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (report.isApproved) EumPrimary600 else EumTextMuted,
                )
            }
            Icon(
                painter = painterResource(id = R.drawable.ic_route_card_chevron),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = EumTextMuted,
            )
        }
    }
}

@Composable
private fun ReportTypeStep(
    input: ReportTypeInput,
    onAction: (ReportUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val helperText = reportTypeErrorText(input.error) ?: "해당하는 유형을 선택해주세요."
    val isError = input.error != null

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
    ) {
        ReportStepProgress(currentStep = ReportStep.TypeSelection)
        Spacer(modifier = Modifier.height(EumSpacing.medium))
        Text(
            text = "어떤 문제를 제보하시나요?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = helperText,
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        Spacer(modifier = Modifier.height(EumSpacing.xSmall))
        ReportTypeGrid(
            selectedType = input.value,
            onTypeSelected = { type -> onAction(ReportUiAction.ReportTypeSelected(type)) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun ReportStepProgress(
    currentStep: ReportStep,
    modifier: Modifier = Modifier,
) {
    val activeCount =
        when (currentStep) {
            ReportStep.TypeSelection -> 1
            ReportStep.LocationConfirm -> 2
            ReportStep.DetailInput -> 3
            ReportStep.Home, ReportStep.Complete -> 0
        }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
    ) {
        repeat(3) { index ->
            Surface(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(6.dp),
                shape = RoundedCornerShape(50),
                color = if (index < activeCount) EumPrimary600 else EumBorderSubtle,
            ) {}
        }
    }
}

@Composable
private fun ReportTypeGrid(
    selectedType: ReportType?,
    onTypeSelected: (ReportType) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 호출부에서 weight(1f)로 남은 수직 공간을 받아오면, 3개 row를 동일 weight로 분할하여
    // 그리드 전체가 화면 하단까지 채워지도록 한다. 부모가 verticalScroll이면 weight가
    // 동작하지 않으므로 호출부에서 스크롤을 끄고 호출해야 한다.
    val rows = ReportType.values().toList().chunked(2)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EumSpacing.medium),
            ) {
                rowItems.forEach { type ->
                    ReportTypeCard(
                        type = type,
                        selected = selectedType == type,
                        onClick = { onTypeSelected(type) },
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
                }
                if (rowItems.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ReportTypeCard(
    type: ReportType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor =
        if (selected) {
            EumPrimary600
        } else {
            EumBorderSubtle.copy(alpha = 0.6f)
        }
    val backgroundColor =
        if (selected) {
            EumSurfaceInfo.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    val selectionLabel = if (selected) "선택됨" else "선택 안 됨"

    Surface(
        modifier =
            modifier
                .clickable(onClick = onClick)
                .semantics {
                    this.selected = selected
                    contentDescription = "${type.label}. $selectionLabel"
                },
        shape = RoundedCornerShape(EumRadius.large),
        color = backgroundColor,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(EumSpacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EumSpacing.xxSmall, Alignment.CenterVertically),
        ) {
            Icon(
                painter = painterResource(id = type.iconRes),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.Unspecified,
            )
            Text(
                text = type.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReportLocationStep(
    input: ReportLocationInput,
    selectedType: ReportType?,
    onAction: (ReportUiAction) -> Unit,
) {
    val errorText = reportLocationErrorText(input.error)
    val isError = input.error != null
    var hasRequestedCurrentLocationOnEnter by remember { mutableStateOf(false) }

    LaunchedEffect(input.value, input.isResolvingCurrentLocation) {
        if (!hasRequestedCurrentLocationOnEnter &&
            input.value == null &&
            !input.isResolvingCurrentLocation
        ) {
            hasRequestedCurrentLocationOnEnter = true
            onAction(ReportUiAction.CurrentLocationResetClicked)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
    ) {
        ReportStepProgress(currentStep = ReportStep.LocationConfirm)
        Spacer(modifier = Modifier.height(EumSpacing.medium))
        Text(
            text = "위치를 확인해주세요",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "문제가 발생한 위치를 정확히 확인해주세요.",
            style = MaterialTheme.typography.bodyLarge,
            color = EumTextMuted,
        )
        ReportMapPanel(
            location = input.value,
            source = input.source,
            selectedType = selectedType,
            isResolvingCurrentLocation = input.isResolvingCurrentLocation,
            onCurrentLocationClick = { onAction(ReportUiAction.CurrentLocationResetClicked) },
            onCenterChanged = { latitude, longitude ->
                onAction(
                    ReportUiAction.LocationSelected(
                        location =
                            ReportLocation(
                                latitude = latitude,
                                longitude = longitude,
                                // 좌표 → 주소 reverse geocoding은 Task 2.3 영역. 그 전까지 address=null 유지.
                                address = null,
                            ),
                        source = ReportLocationSource.MapPin,
                    ),
                )
            },
        )
        ReportLocationBottomCard(
            location = input.value,
            addressText = input.addressText,
        )
        if (isError && errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ReportLocationCurrentButton(
    isResolving: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .size(48.dp)
                .clip(RoundedCornerShape(percent = 50))
                .clickable(enabled = !isResolving, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.75f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isResolving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = EumPrimary600,
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_report_map_current_location),
                    contentDescription = "현재 위치로 설정",
                    modifier = Modifier.size(28.dp),
                    tint = EumPrimary600,
                )
            }
        }
    }
}

@Composable
private fun ReportMapProblemChip(
    type: ReportType,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(EumRadius.medium),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = EumSpacing.medium, vertical = EumSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
        ) {
            Icon(
                painter = painterResource(id = type.iconRes),
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                tint = Color.Unspecified,
            )
            Text(
                text = type.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * REPORT-02 위치 단계의 지도 패널 — KakaoMap SDK 직접 사용 (Task 2.2).
 *
 * Map 화면이 사용하는 무거운 `KakaoMapViewport`(렌더러 retry / fallback overlay / marker 시스템 등
 * Report에 불필요한 기능 포함)를 거치지 않고, KakaoMap Android SDK의 `MapView`를 `AndroidView`로
 * 직접 wrap하여 가장 가벼운 형태로 지도를 렌더링한다.
 *
 * 동작:
 * - 화면 정중앙에 핀 아이콘 고정 오버레이 (drag-the-map 표준 패턴)
 * - 사용자가 지도를 드래그·줌하여 카메라가 멈출 때 `onCenterChanged` 호출
 * - 외부에서 `location`이 변경되면(`source != MapPin`) 카메라가 그 좌표로 이동
 * - 사용자 드래그로 인한 자동 dispatch와 외부 카메라 이동이 겹치지 않도록 flag로 가드
 *
 * Lifecycle:
 * - Activity ON_RESUME / ON_PAUSE에 맞춰 `MapView.resume()` / `pause()` 호출
 * - Composable dispose 시 `MapView.finish()`로 정리
 */
@Composable
private fun ReportMapPanel(
    location: ReportLocation?,
    source: ReportLocationSource,
    selectedType: ReportType?,
    isResolvingCurrentLocation: Boolean,
    onCurrentLocationClick: () -> Unit,
    onCenterChanged: (latitude: Double, longitude: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // 지도 ready 후 채워지는 KakaoMap 인스턴스. 외부 카메라 이동에 사용.
    val kakaoMapRef = remember { mutableStateOf<KakaoMap?>(null) }
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    // 외부 카메라 이동으로 인한 `onCameraMoveEnd` 콜백을 무시하기 위한 flag.
    // 사용자 드래그 후 LocationSelected dispatch → state 갱신 → LaunchedEffect → moveCamera → onCameraMoveEnd
    // 이 순환이 무한 루프 되는 것을 막는다.
    val suppressNextCameraMoveEnd = remember { mutableStateOf(false) }
    val hasUserMovedMap = remember { mutableStateOf(false) }

    // 초기 카메라 위치 — 진입 시점의 location 또는 부산시청.
    val initialPosition =
        remember {
            location?.let { LatLng.from(it.latitude, it.longitude) }
                ?: LatLng.from(DEFAULT_REPORT_MAP_LATITUDE, DEFAULT_REPORT_MAP_LONGITUDE)
        }

    // location 외부 변경(예: "현재 위치로 설정")에 따라 카메라 이동.
    LaunchedEffect(location, source) {
        if (location == null || source == ReportLocationSource.MapPin) return@LaunchedEffect
        val readyMap = kakaoMapRef.value ?: return@LaunchedEffect
        suppressNextCameraMoveEnd.value = true
        readyMap.moveCamera(
            CameraUpdateFactory.newCenterPosition(
                LatLng.from(location.latitude, location.longitude),
                REPORT_MAP_DEFAULT_ZOOM_LEVEL,
            ),
            CameraAnimation.from(REPORT_MAP_CAMERA_ANIMATION_DURATION_MS),
        )
    }

    // Activity lifecycle에 MapView resume/pause/finish 동기화.
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                val view = mapViewRef.value ?: return@LifecycleEventObserver
                when (event) {
                    Lifecycle.Event.ON_RESUME -> runCatching { view.resume() }
                    Lifecycle.Event.ON_PAUSE -> runCatching { view.pause() }
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { mapViewRef.value?.finish() }
            mapViewRef.value = null
            kakaoMapRef.value = null
        }
    }

    val mapShape = RoundedCornerShape(EumRadius.large)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                // scrollable parent(verticalScroll) 안에서 max height가 Infinity로 들어오면
                // heightIn(min=...)은 wrap_content로 collapse되어 SurfaceView가 0 height로 measure된다.
                // 고정 height로 강제해 KakaoMap GL surface가 일정 크기로 확보되도록 한다.
                .height(320.dp)
                // clip은 border보다 먼저 적용해야 지도 타일이 라운드 모서리를 넘지 않는다.
                .clip(mapShape)
                .border(
                    width = 1.dp,
                    color = EumBorderSubtle.copy(alpha = 0.45f),
                    shape = mapShape,
                ),
    ) {
        AndroidView(
            factory = { ctx ->
                // MapView를 직접 AndroidView에 wrap하면 Compose의 verticalScroll이 dispatchTouchEvent
                // 단계에서 vertical drag를 가로채 MapView가 vertical pan을 못 받는다.
                // FrameLayout.dispatchTouchEvent에서 ACTION_DOWN 시점에 부모(=AndroidComposeView)에게
                // intercept 금지를 요청하면 이후 MOVE 이벤트가 그대로 자식 MapView까지 전달된다.
                // ACTION_UP/CANCEL에 다시 허용해 다음 gesture는 부모도 자유롭게 처리할 수 있게 한다.
                val frame =
                    object : android.widget.FrameLayout(ctx) {
                        override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
                            when (ev.actionMasked) {
                                android.view.MotionEvent.ACTION_DOWN -> {
                                    hasUserMovedMap.value = true
                                    parent?.requestDisallowInterceptTouchEvent(true)
                                }
                                android.view.MotionEvent.ACTION_UP,
                                android.view.MotionEvent.ACTION_CANCEL,
                                -> parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            return super.dispatchTouchEvent(ev)
                        }
                    }
                MapView(ctx).also { view ->
                    mapViewRef.value = view
                    frame.addView(
                        view,
                        android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    view.start(
                        // MapLifeCycleCallback의 4개 메서드 모두 override 필요 (SDK 동작 보장).
                        object : MapLifeCycleCallback() {
                            override fun onMapDestroy() {
                                android.util.Log.d(REPORT_MAP_LOG_TAG, "MapView destroyed")
                            }

                            override fun onMapError(error: Exception?) {
                                android.util.Log.w(
                                    REPORT_MAP_LOG_TAG,
                                    "Kakao map error in Report tab",
                                    error,
                                )
                            }

                            override fun onMapResumed() {
                                android.util.Log.d(REPORT_MAP_LOG_TAG, "MapView resumed")
                            }

                            override fun onMapPaused() {
                                android.util.Log.d(REPORT_MAP_LOG_TAG, "MapView paused")
                            }
                        },
                        object : KakaoMapReadyCallback() {
                            override fun onMapReady(readyMap: KakaoMap) {
                                android.util.Log.i(REPORT_MAP_LOG_TAG, "Kakao map READY in Report tab")
                                kakaoMapRef.value = readyMap
                                readyMap.setOnCameraMoveEndListener { _, cameraPosition, _ ->
                                    if (suppressNextCameraMoveEnd.value) {
                                        // 외부 카메라 이동(moveCamera 호출)으로 인한 콜백은 한 번만 무시.
                                        suppressNextCameraMoveEnd.value = false
                                        return@setOnCameraMoveEndListener
                                    }
                                    if (!hasUserMovedMap.value) {
                                        return@setOnCameraMoveEndListener
                                    }
                                    val pos = cameraPosition.position
                                    onCenterChanged(pos.latitude, pos.longitude)
                                }
                            }

                            override fun getPosition(): LatLng = initialPosition

                            override fun getZoomLevel(): Int = REPORT_MAP_DEFAULT_ZOOM_LEVEL
                        },
                    )
                    // 첫 진입 시 Activity가 이미 RESUMED 상태인 경우, lifecycle observer의 ON_RESUME 이벤트가
                    // mapViewRef.value 채워지기 전에 fire되어 resume() 호출이 누락될 수 있다.
                    // view.post로 한 frame 미룬 후 attach·measure 정리된 시점에 명시적으로 resume() 호출.
                    view.post {
                        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            runCatching { view.resume() }
                                .onFailure { error ->
                                    android.util.Log.w(
                                        REPORT_MAP_LOG_TAG,
                                        "Initial MapView resume() failed",
                                        error,
                                    )
                                }
                        }
                    }
                }
                frame
            },
            // matchParentSize는 BoxScope 한정 — 부모 Box의 measure 결과(320.dp)에 강제로 맞춰
            // AndroidView 자식(FrameLayout → MapView)이 wrap_content로 collapse되는 것을 막는다.
            modifier = Modifier.matchParentSize(),
        )
        // 화면 정중앙 고정 핀 — 사용자가 지도를 드래그하면 핀은 그대로, 지도가 움직이며 핀이 가리키는
        // 좌표가 현재 선택된 위치가 된다. 일반 지도앱 표준 "drag-the-map" 패턴.
        //
        // ic_map_selected_pin_blue의 뾰족 끝은 viewport y=22(24기준), 즉 박스 중심에서 약 41.7%
        // 아래에 있어 단순 Alignment.Center로 두면 사용자가 인지하는 "핀 끝"이 카메라 중심보다
        // 아이콘 높이의 약 절반만큼 아래쪽 좌표를 가리키게 된다. 박스 자체를 위로 offset해서
        // 뾰족 끝(=좌표 anchor)이 정확히 카메라 중심에 오도록 보정한다. 계산: (22-12)/24 × 40dp ≈ 17dp.
        Icon(
            painter = painterResource(id = R.drawable.ic_map_selected_pin_blue),
            contentDescription = "위치 선택 핀",
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset(y = (-17).dp)
                    .size(40.dp),
            tint = Color.Unspecified,
        )
        if (selectedType != null) {
            ReportMapProblemChip(
                type = selectedType,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = EumSpacing.medium),
            )
        }
        ReportLocationCurrentButton(
            isResolving = isResolvingCurrentLocation,
            onClick = onCurrentLocationClick,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(EumSpacing.medium),
        )
    }
}

// 부산시청 좌표 — 사용자 위치 정보가 없을 때 지도 초기 중심.
private const val DEFAULT_REPORT_MAP_LATITUDE = 35.1796
private const val DEFAULT_REPORT_MAP_LONGITUDE = 129.0756
// 초기 줌 레벨 — 17은 카카오맵 기준 도로·건물이 명확히 보이는 수준 (Map 화면 기본값과 일관).
private const val REPORT_MAP_DEFAULT_ZOOM_LEVEL = 17
// 외부 카메라 이동(예: "현재 위치로 설정") 시 사용자가 변화를 자연스럽게 인지할 수 있는 짧은 애니메이션.
private const val REPORT_MAP_CAMERA_ANIMATION_DURATION_MS = 300
private const val REPORT_MAP_LOG_TAG = "ReportMapPanel"

@Composable
private fun ReportLocationBottomCard(
    location: ReportLocation?,
    addressText: String,
) {
    // 옵션 4: 자동 RGC 결과(자동 도로명)와 사용자 직접 보충(addressText)을 별도 라인으로 표시.
    // 한쪽이 다른 쪽을 가리지 않으므로 두 정보가 동시에 보존되고, 사용자의 멘탈 모델("자동은 객관,
    // 내 입력은 보충")과 UI가 일치한다.
    val autoAddress = location?.address?.takeIf { it.isNotBlank() }
    val userDetail = addressText.ifBlank { null }
    val hasAnyAddress = autoAddress != null || userDetail != null

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(EumRadius.large),
        border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.65f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(EumSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EumSpacing.medium),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_map_selected_pin_blue),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color.Unspecified,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
            ) {
                Text(
                    text = "선택된 위치",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (!hasAnyAddress) {
                    Text(
                        text = "아직 위치가 선택되지 않았습니다.",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (autoAddress != null) {
                    Text(
                        text = autoAddress,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (userDetail != null) {
                    Text(
                        text = userDetail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportDetailStep(
    uiState: ReportUiState,
    onAction: (ReportUiAction) -> Unit,
) {
    val reportType = uiState.reportType.value
    Column(
        verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
    ) {
        ReportStepProgress(currentStep = ReportStep.DetailInput)
        Spacer(modifier = Modifier.height(EumSpacing.medium))
        Text(
            text = "어떤 문제가 있었는지 알려주세요",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "상황을 구체적으로 적어주시면 더 빠르게 검토할 수 있어요.",
            style = MaterialTheme.typography.bodyLarge,
            color = EumTextMuted,
        )
        if (reportType != null) {
            ReportSelectedProblemCard(
                type = reportType,
                locationText =
                    uiState.location.value?.address
                        ?: uiState.location.addressText.ifBlank { "위치 정보 없음" },
            )
        }
        ReportDescriptionSection(
            input = uiState.description,
            onAction = onAction,
        )
        ReportPhotoSection(
            input = uiState.photo,
            onAction = onAction,
        )
        ReportPrivacyNotice()
        ReportSubmitFailureActions(
            uiState = uiState,
            onAction = onAction,
        )
    }
}

@Composable
private fun ReportSelectedProblemCard(
    type: ReportType,
    locationText: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EumRadius.large),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.65f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EumSpacing.medium, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(EumSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = type.iconRes),
                contentDescription = null,
                modifier = Modifier.size(54.dp),
                tint = Color.Unspecified,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
            ) {
                Text(
                    text = type.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = locationText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = EumTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ReportPrivacyNotice(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_status_safe_info),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = EumTextMuted,
        )
        Text(
            text = "개인정보가 보이지 않도록 주의해주세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = EumTextMuted,
        )
    }
}

@Composable
private fun ReportSubmitFailureActions(
    uiState: ReportUiState,
    onAction: (ReportUiAction) -> Unit,
) {
    val isSubmitRecoverable =
        uiState.screenState is ReportScreenState.Failure ||
            uiState.submitState is ReportSubmitState.Failed ||
            uiState.outboxState is ReportOutboxState.Failed

    if (!isSubmitRecoverable) return

    Column(
        verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
    ) {
        ReportSubmitFailureBanner(
            reason = (uiState.submitState as? ReportSubmitState.Failed)?.reason
                ?: (uiState.screenState as? ReportScreenState.Failure)?.reason,
            onRetryClick = { onAction(ReportUiAction.RetrySubmitClicked) },
        )
    }
}

@Composable
private fun ReportSubmitFailureBanner(
    reason: ReportFailureReason?,
    onRetryClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Assertive },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(EumRadius.large),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.36f)),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(EumSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_status_warning),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(id = R.string.report_submit_failure_banner_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(id = reason.toBannerDescriptionRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Button(
                onClick = onRetryClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(id = R.string.report_submit_failure_retry))
            }
        }
    }
}

private fun ReportFailureReason?.toBannerDescriptionRes(): Int =
    when (this) {
        ReportFailureReason.Unauthorized -> R.string.report_submit_failure_unauthorized
        ReportFailureReason.InvalidInput -> R.string.report_submit_failure_invalid_input
        ReportFailureReason.NetworkUnavailable -> R.string.report_submit_failure_network
        ReportFailureReason.LocalSaveFailed -> R.string.report_submit_failure_local_save
        ReportFailureReason.ServerSubmitFailed -> R.string.report_submit_failure_server
        else -> R.string.report_submit_failure_unknown
    }

@Composable
private fun ReportCompleteStep(
    uiState: ReportUiState,
    onAction: (ReportUiAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ReportCompleteHero()
        ReportCompleteSummaryCard(uiState = uiState)
        ReportCompleteCtaSection(
            entryPoint = uiState.entryPoint,
            onAction = onAction,
        )
    }
}

@Composable
private fun ReportCompleteCtaSection(
    entryPoint: ReportEntryPoint,
    onAction: (ReportUiAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
    ) {
        ReportPrimaryActionButton(
            label = stringResource(id = R.string.report_complete_cta_history),
            enabled = true,
            onClick = { onAction(ReportUiAction.ReportHistoryClicked) },
            modifier = Modifier.fillMaxWidth(),
        )
        ReportPrimaryActionButton(
            label = stringResource(id = R.string.report_complete_cta_new_report),
            enabled = true,
            onClick = { onAction(ReportUiAction.StartNewReportClicked) },
            modifier = Modifier.fillMaxWidth(),
        )
        ReportPrimaryActionButton(
            label = stringResource(id = entryPoint.completeReturnLabelRes()),
            enabled = true,
            onClick = { onAction(ReportUiAction.BackToMapClicked) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun ReportEntryPoint.completeReturnLabelRes(): Int =
    when (this) {
        ReportEntryPoint.TopLevel -> R.string.report_complete_cta_back_to_map
        ReportEntryPoint.NavigationGuidance -> R.string.report_complete_cta_back_to_navigation
        ReportEntryPoint.VoiceAssistant -> R.string.report_complete_cta_back_to_map
    }

@Composable
private fun ReportCompleteHero() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_status_check),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = "제보가 완료되었습니다!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "소중한 제보 감사합니다.\n검토 후 서비스에 반영하겠습니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReportCompleteSummaryCard(uiState: ReportUiState) {
    val photoCount = uiState.photo.count
    // 옵션 4 데이터 모델 — 자동 도로명(location.value.address)과 사용자 보충(addressText)을
    // 각각 별도 행으로 표시. 카드(LocationBottomCard)와 일관된 분리 표시.
    val autoAddress = uiState.location.value?.address?.takeIf { it.isNotBlank() }
    val userDetail = uiState.location.addressText.ifBlank { null }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(EumRadius.large),
        border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.65f)),
    ) {
        Column(
            modifier = Modifier.padding(EumSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
        ) {
            ReportCompleteSummaryRow(
                label = "일시",
                value = formatSubmittedAt(uiState.submittedAtMillis),
            )
            ReportCompleteSummaryRow(
                label = "유형",
                value = uiState.reportType.value?.label ?: "-",
            )
            // 자동 RGC 결과가 있으면 "위치"에 노출. 둘 다 없으면 "위치 정보 없음".
            ReportCompleteSummaryRow(
                label = "위치",
                value = autoAddress ?: userDetail ?: "위치 정보 없음",
            )
            // 자동 도로명이 표시될 때만 보충 메모를 별도 줄로 노출. 자동이 없으면 위 "위치"에
            // 이미 userDetail이 들어가므로 중복을 피한다.
            if (autoAddress != null && userDetail != null) {
                ReportCompleteSummaryRow(
                    label = "위치 보충",
                    value = userDetail,
                )
            }
            ReportCompleteSummaryRow(
                label = "설명",
                value = uiState.description.value.trim().ifBlank { "설명 없음" },
            )
            // 사진 미첨부 케이스도 명시적으로 노출 — 사용자가 자기 제보 내역에서 사진 유무를
            // 한눈에 확인할 수 있도록.
            ReportCompleteSummaryRow(
                label = "사진",
                value =
                    if (photoCount > 0) {
                        stringResource(id = R.string.report_complete_photo_attached, photoCount)
                    } else {
                        "첨부 없음"
                    },
            )
        }
    }
}

@Composable
private fun ReportCompleteSummaryRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(56.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatSubmittedAt(submittedAtMillis: Long?): String {
    val millis = submittedAtMillis ?: return "-"
    val formatter =
        java.text.SimpleDateFormat(
            "yyyy.MM.dd (E) HH:mm",
            java.util.Locale.KOREA,
        )
    return formatter.format(java.util.Date(millis))
}

@Composable
private fun ReportPhotoSection(
    input: ReportPhotoInput,
    onAction: (ReportUiAction) -> Unit,
) {
    val isError = input.error != null
    val errorText = reportPhotoErrorText(input.error)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(EumRadius.large),
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        EumBorderSubtle.copy(alpha = 0.65f)
                    },
            ),
    ) {
        Column(
            modifier = Modifier.padding(EumSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = input.canAddMore,
                            role = Role.Button,
                            onClick = { onAction(ReportUiAction.PhotoAddClicked) },
                        )
                        .semantics {
                            contentDescription = "사진 추가"
                        },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EumSpacing.medium),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_permission_camera),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(EumSpacing.xxSmall),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "사진 추가",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "(선택)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EumTextMuted,
                        )
                    }
                    Text(
                        text = "현장 사진을 첨부하면\n더 정확한 확인이 가능해요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EumTextMuted,
                    )
                }
                Icon(
                    painter = painterResource(id = R.drawable.ic_route_card_chevron),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = EumTextMuted,
                )
            }
            if (input.values.isNotEmpty()) {
                ReportPhotoGrid(
                    photos = input.values,
                    canAddMore = input.canAddMore,
                    onAddClick = { onAction(ReportUiAction.PhotoAddClicked) },
                    onRemoveClick = { index -> onAction(ReportUiAction.PhotoRemovedAt(index)) },
                )
            }
            if (errorText != null) {
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ReportPhotoGrid(
    photos: List<ReportPhoto>,
    canAddMore: Boolean,
    onAddClick: () -> Unit,
    onRemoveClick: (Int) -> Unit,
) {
    val itemsPerRow = 3
    val cells: List<PhotoCell> =
        buildList {
            photos.forEachIndexed { index, photo ->
                add(PhotoCell.Item(index = index, photo = photo))
            }
            if (canAddMore) {
                add(PhotoCell.Add)
            }
        }
    if (cells.isEmpty()) {
        // canAddMore=false 이면서 photos가 비어있을 수 없지만, 안전하게 처리.
        return
    }
    cells.chunked(itemsPerRow).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
        ) {
            row.forEach { cell ->
                when (cell) {
                    is PhotoCell.Item ->
                        ReportPhotoThumb(
                            photo = cell.photo,
                            onRemoveClick = { onRemoveClick(cell.index) },
                            modifier = Modifier.weight(1f),
                        )
                    PhotoCell.Add ->
                        ReportPhotoAddTile(
                            onClick = onAddClick,
                            modifier = Modifier.weight(1f),
                        )
                }
            }
            repeat(itemsPerRow - row.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

private sealed interface PhotoCell {
    data class Item(val index: Int, val photo: ReportPhoto) : PhotoCell
    data object Add : PhotoCell
}

@Composable
private fun ReportPhotoThumb(
    photo: ReportPhoto,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Box(
        modifier =
            modifier
                .heightIn(min = 96.dp)
                .semantics {
                    contentDescription = "첨부된 사진"
                },
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(EumRadius.small),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.65f)),
        ) {
            // Coil SubcomposeAsyncImage로 실제 사진 썸네일 렌더링. 로딩 중·실패 시에는
            // fallback Composable(회색 박스 + 라벨)로 graceful degradation.
            // mock URI(`content://mock/...`)이거나 권한 없는 URI는 자연스럽게 error 상태 처리.
            // (`AsyncImage`는 painter-only fallback만 받으므로 Composable 슬롯을 위해
            // `SubcomposeAsyncImage` 사용.)
            SubcomposeAsyncImage(
                model =
                    ImageRequest.Builder(context)
                        .data(photo.localUri)
                        .crossfade(true)
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { ReportPhotoThumbFallback(label = "사진") },
                error = { ReportPhotoThumbFallback(label = "불러오기 실패") },
            )
        }
        Surface(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(EumSpacing.xSmall)
                    .size(24.dp)
                    .clickable(onClick = onRemoveClick)
                    .semantics {
                        contentDescription = "사진 제거"
                    },
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.7f)),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_action_close),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * SubcomposeAsyncImage가 로딩 중이거나 실패했을 때 표시되는 fallback. 기존 텍스트 라벨 패턴
 * 그대로 유지하여 사용자 입장에서 카드 영역이 비어 보이지 않게 한다.
 */
@Composable
private fun ReportPhotoThumbFallback(label: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReportPhotoAddTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .heightIn(min = 96.dp)
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = "사진을 추가합니다."
                },
        shape = RoundedCornerShape(EumRadius.small),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.65f)),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(EumSpacing.xSmall),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_permission_camera),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(EumSpacing.xSmall))
            Text(
                text = "사진 추가",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReportDescriptionSection(
    input: ReportDescriptionInput,
    onAction: (ReportUiAction) -> Unit,
) {
    val isError = input.error != null
    val charCountText = "${input.value.length}/${ReportFormLimits.DESCRIPTION_MAX_LENGTH}"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(EumRadius.large),
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        EumBorderSubtle.copy(alpha = 0.65f)
                    },
            ),
    ) {
        Column(
            modifier = Modifier.padding(EumSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
        ) {
            Text(
                text = "상세 설명",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = input.value,
                    onValueChange = { onAction(ReportUiAction.DescriptionChanged(it)) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(168.dp)
                            .onFocusChanged { focusState: FocusState ->
                                if (!focusState.isFocused) {
                                    onAction(ReportUiAction.DescriptionBlurred)
                                }
                            },
                    placeholder = {
                        Text(
                            text = "예: 안내와 달리 계단이 있어\n휠체어 이동이 어려웠어요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EumTextMuted,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    isError = isError,
                    minLines = 5,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                )
                Text(
                    text =
                        reportDescriptionErrorText(input.error) ?: charCountText,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = EumSpacing.medium, bottom = EumSpacing.small),
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            EumTextMuted
                        },
                )
            }
        }
    }
}

/**
 * TopBar에 노출할 단계별 라벨.
 *
 * 단계 식별은 화면 콘텐츠(지도 영역 / 입력 필드 등)로 충분히 인지되므로, LocationConfirm /
 * DetailInput 단계에서는 단계명 대신 사용자가 선택한 type 라벨만 노출하여 "지금 어떤 유형의
 * 제보를 작성 중인지"를 시각 위계의 최상위로 끌어올린다.
 *
 * - TypeSelection: type 선택 전이므로 "제보"
 * - LocationConfirm / DetailInput: type이 있으면 type 라벨만, 없으면(비정상) 단계명 fallback
 * - Complete: 본문 요약 카드에 type이 이미 노출되므로 중복 회피 차원에서 "제보 완료" 유지
 */
internal fun reportStepTitle(
    step: ReportStep,
    selectedType: ReportType? = null,
): String {
    return when (step) {
        ReportStep.Home -> "제보"
        ReportStep.TypeSelection, ReportStep.LocationConfirm, ReportStep.DetailInput -> "제보하기"
        ReportStep.Complete -> "제보 완료"
    }
}

private val ReportType.label: String
    get() =
        when (this) {
            ReportType.STAIRS_STEP -> "계단·단차 있음"
            ReportType.BRAILLE_BLOCK -> "점자블록 문제"
            ReportType.SIDEWALK_MISSING -> "인도 없음"
            ReportType.RAMP -> "경사로 문제"
            ReportType.SIDEWALK_WIDTH -> "인도폭 문제"
            ReportType.OTHER_OBSTACLE -> "기타 장애물"
        }

@get:DrawableRes
private val ReportType.iconRes: Int
    get() =
        when (this) {
            ReportType.STAIRS_STEP -> R.drawable.ic_report_stairs
            ReportType.BRAILLE_BLOCK -> R.drawable.ic_report_tactile_damage
            ReportType.SIDEWALK_MISSING -> R.drawable.ic_report_sidewalk
            ReportType.RAMP -> R.drawable.ic_report_ramp
            ReportType.SIDEWALK_WIDTH -> R.drawable.ic_report_roadway
            ReportType.OTHER_OBSTACLE -> R.drawable.ic_report_other
        }

@DrawableRes
private fun String.toReportHomeIconRes(): Int =
    when (this) {
        "계단·단차 있음" -> R.drawable.ic_report_stairs
        "점자블록 문제" -> R.drawable.ic_report_tactile_damage
        "인도 없음" -> R.drawable.ic_report_sidewalk
        "경사로 문제" -> R.drawable.ic_report_ramp
        "인도폭 문제" -> R.drawable.ic_report_roadway
        else -> R.drawable.ic_report_other
    }

private fun reportTypeErrorText(error: ReportTypeError?): String? =
    when (error) {
        ReportTypeError.Required -> "제보 유형을 선택해주세요."
        null -> null
    }

private fun reportLocationErrorText(error: ReportLocationError?): String? =
    when (error) {
        ReportLocationError.Required -> "제보 위치를 선택해주세요."
        ReportLocationError.InvalidCoordinate -> "위치 좌표를 다시 확인해주세요."
        ReportLocationError.AddressTooLong -> "주소가 너무 깁니다."
        ReportLocationError.PermissionDenied -> "위치 권한이 필요합니다."
        ReportLocationError.CurrentLocationUnavailable -> "현재 위치를 불러올 수 없습니다."
        null -> null
    }

private fun reportPhotoErrorText(error: ReportPhotoError?): String? =
    when (error) {
        ReportPhotoError.UnsupportedFormat -> "지원하지 않는 사진 형식입니다."
        ReportPhotoError.TooLarge -> "사진 용량이 너무 큽니다."
        ReportPhotoError.Unreadable -> "사진을 읽을 수 없습니다."
        ReportPhotoError.TooMany ->
            "사진은 최대 ${ReportFormLimits.PHOTO_MAX_COUNT}장까지 첨부할 수 있습니다."
        null -> null
    }

private fun reportDescriptionErrorText(error: ReportDescriptionError?): String? =
    when (error) {
        ReportDescriptionError.TooLong -> "설명은 ${ReportFormLimits.DESCRIPTION_MAX_LENGTH}자까지 입력할 수 있습니다."
        null -> null
    }
