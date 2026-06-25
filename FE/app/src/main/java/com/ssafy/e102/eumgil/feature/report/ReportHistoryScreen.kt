package com.ssafy.e102.eumgil.feature.report

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.designsystem.component.feedback.EumLoadingState
import com.ssafy.e102.eumgil.core.designsystem.component.navigation.EumCenteredTopBar
import com.ssafy.e102.eumgil.core.designsystem.theme.EumBorderSubtle
import com.ssafy.e102.eumgil.core.designsystem.theme.EumPrimary200
import com.ssafy.e102.eumgil.core.designsystem.theme.EumPrimary600
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSurfaceInfo
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSurfaceSubtle
import com.ssafy.e102.eumgil.core.designsystem.theme.EumTextMuted
import com.ssafy.e102.eumgil.core.designsystem.theme.EumTextPrimary
import com.ssafy.e102.eumgil.core.designsystem.theme.EumTextSecondary
import com.ssafy.e102.eumgil.core.designsystem.theme.EumWhite

internal data class ReportHistoryLayoutSpec(
    val cardCornerRadiusDp: Int,
    val thumbnailCornerRadiusDp: Int,
    val buttonCornerRadiusDp: Int,
    val buttonMinHeightDp: Int,
    val cardShadowElevationDp: Int,
)

internal fun reportHistoryLayoutSpec(): ReportHistoryLayoutSpec =
    ReportHistoryLayoutSpec(
        cardCornerRadiusDp = 18,
        thumbnailCornerRadiusDp = 14,
        buttonCornerRadiusDp = 14,
        buttonMinHeightDp = 56,
        cardShadowElevationDp = 0,
    )

private enum class ReportHistoryFilter(
    val label: String,
) {
    All("전체"),
    Pending("접수됨"),
    Approved("반영 완료"),
}

@Composable
fun ReportHistoryScreen(
    uiState: ReportHistoryUiState,
    snackbarHostState: SnackbarHostState,
    onAction: (ReportHistoryUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedDetail = uiState.selectedDetail
    val isDetailMode = selectedDetail != null || uiState.detailLoadingHistoryId != null

    BackHandler(enabled = isDetailMode) {
        onAction(ReportHistoryUiAction.DetailBackClicked)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            ReportHistoryTopBar(
                title = if (isDetailMode) "제보 상세" else "내 제보 내역",
                onBackClick = {
                    onAction(
                        if (isDetailMode) {
                            ReportHistoryUiAction.DetailBackClicked
                        } else {
                            ReportHistoryUiAction.BackClicked
                        },
                    )
                },
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { innerPadding ->
        if (isDetailMode) {
            ReportHistoryDetailContent(
                detail = selectedDetail,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            )
        } else {
            ReportHistoryListContent(
                uiState = uiState,
                onAction = onAction,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            )
        }
    }
}

internal fun shouldShowReportHistoryCreateCta(screenState: ReportHistoryScreenState): Boolean =
    screenState == ReportHistoryScreenState.CONTENT

@Composable
private fun ReportHistoryTopBar(
    title: String,
    onBackClick: () -> Unit,
) {
    EumCenteredTopBar(
        title = title,
        onBackClick = onBackClick,
        backContentDescription = stringResource(id = R.string.my_page_back),
        titleFontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ReportHistoryListContent(
    uiState: ReportHistoryUiState,
    onAction: (ReportHistoryUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedFilter by remember { mutableStateOf(ReportHistoryFilter.All) }
    val filteredReports =
        remember(uiState.reports, selectedFilter) {
            uiState.reports.filter { report ->
                when (selectedFilter) {
                    ReportHistoryFilter.All -> true
                    ReportHistoryFilter.Pending -> !report.isApproved
                    ReportHistoryFilter.Approved -> report.isApproved
                }
            }
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = EumSpacing.medium),
    ) {
        ReportHistoryListControls(
            selectedFilter = selectedFilter,
            onFilterSelected = { selectedFilter = it },
            modifier = Modifier.padding(top = EumSpacing.medium),
        )

        when (uiState.screenState) {
            ReportHistoryScreenState.LOADING ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    ReportHistoryLoadingState(
                        title = "제보 내역을 불러오는 중입니다",
                        description = "저장된 제보 목록을 확인하고 있어요.",
                    )
                }

            ReportHistoryScreenState.EMPTY ->
                ReportHistoryEmptyState(
                    title = "아직 제보 내역이 없어요",
                    description = "이동 중 발견한 보행 불편 사항을 제보해 주세요.",
                    primaryActionLabel = "제보하기",
                    onPrimaryActionClick = {
                        onAction(ReportHistoryUiAction.ReportCtaClicked)
                    },
                    modifier = Modifier.weight(1f),
                )

            ReportHistoryScreenState.ERROR ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    ReportHistoryStateCard(
                        title = "제보 내역을 불러오지 못했습니다",
                        description = uiState.errorMessage ?: "잠시 후 다시 시도해 주세요.",
                        primaryActionLabel = "다시 시도",
                        onPrimaryActionClick = {
                            onAction(ReportHistoryUiAction.RetryClicked)
                        },
                        secondaryActionLabel = "제보하기",
                        secondaryActionSuppressRipple = true,
                        onSecondaryActionClick = {
                            onAction(ReportHistoryUiAction.ReportCtaClicked)
                        },
                        isError = true,
                    )
                }

            ReportHistoryScreenState.CONTENT -> {
                if (filteredReports.isEmpty()) {
                    ReportHistoryEmptyState(
                        title = "${selectedFilter.label} 제보가 없습니다",
                        description = "다른 상태를 선택하거나 새 제보를 등록해 주세요.",
                        primaryActionLabel = "제보하기",
                        onPrimaryActionClick = {
                            onAction(ReportHistoryUiAction.ReportCtaClicked)
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(top = EumSpacing.medium, bottom = EumSpacing.large),
                        verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
                    ) {
                        items(
                            items = filteredReports,
                            key = { report -> report.outboxId },
                        ) { report ->
                            ReportHistoryListCard(
                                report = report,
                                onClick = {
                                    onAction(ReportHistoryUiAction.ReportClicked(report.outboxId))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportHistoryListControls(
    selectedFilter: ReportHistoryFilter,
    onFilterSelected: (ReportHistoryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
        ) {
            ReportHistoryFilter.values().forEach { filter ->
                ReportHistoryFilterChip(
                    label = filter.label,
                    selected = selectedFilter == filter,
                    onClick = { onFilterSelected(filter) },
                )
            }
        }
    }
}

@Composable
private fun ReportHistoryFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .heightIn(min = 44.dp)
                .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (selected) EumPrimary600 else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(1.dp, EumBorderSubtle),
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) EumWhite else EumTextSecondary,
            )
        }
    }
}

@Composable
private fun ReportHistoryEmptyState(
    title: String,
    description: String,
    primaryActionLabel: String,
    onPrimaryActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EumSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            NoRippleReportHistoryNavigationButton(
                onClick = onPrimaryActionClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = reportHistoryLayoutSpec().buttonMinHeightDp.dp),
                shape = RoundedCornerShape(reportHistoryLayoutSpec().buttonCornerRadiusDp.dp),
            ) {
                Text(
                    text = primaryActionLabel,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ReportHistoryListCard(
    report: ReportHistoryUiModel,
    onClick: () -> Unit,
) {
    val spec = reportHistoryLayoutSpec()
    val accessibilityDescription =
        "제보 내역, ${report.title}, ${report.address}, ${report.submittedAtText}, ${report.statusLabel}"

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = accessibilityDescription },
        shape = RoundedCornerShape(spec.cardCornerRadiusDp.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.65f)),
        shadowElevation = spec.cardShadowElevationDp.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(EumSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(EumSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReportHistoryIssueIcon(title = report.title)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = report.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = EumTextPrimary,
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
            ReportHistoryStatusChip(
                label = report.statusLabel,
                isApproved = report.isApproved,
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_route_card_chevron),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = EumTextMuted,
            )
        }
    }
}

@Composable
private fun ReportHistoryIssueIcon(
    title: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(64.dp),
        shape = RoundedCornerShape(reportHistoryLayoutSpec().thumbnailCornerRadiusDp.dp),
        color = reportIconBackgroundColor(title),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(id = title.toReportIconRes()),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = Color.Unspecified,
            )
        }
    }
}

@Composable
private fun ReportHistoryStatusChip(
    label: String,
    isApproved: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isApproved) EumPrimary200.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = EumSpacing.small, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (isApproved) EumPrimary600 else EumTextSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun ReportHistoryDetailContent(
    detail: ReportHistoryDetailUiModel?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = EumSpacing.medium),
        contentPadding = PaddingValues(top = EumSpacing.medium, bottom = EumSpacing.large),
        verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
    ) {
        if (detail == null) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ReportHistoryLoadingState(
                        title = "제보 상세를 불러오는 중입니다",
                        description = "선택한 제보의 상세 정보를 확인하고 있어요.",
                    )
                }
            }
        } else {
            item { ReportHistoryDetailSummaryCard(detail = detail) }
            item { ReportHistoryDetailDescriptionCard(description = detail.description) }
            item { ReportHistoryProcessingCard(detail = detail) }
        }
    }
}

@Composable
private fun ReportHistoryDetailSummaryCard(detail: ReportHistoryDetailUiModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(reportHistoryLayoutSpec().cardCornerRadiusDp.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.65f)),
        shadowElevation = reportHistoryLayoutSpec().cardShadowElevationDp.dp,
    ) {
        Column(
            modifier = Modifier.padding(EumSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EumSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReportHistoryIssueIcon(title = detail.title, modifier = Modifier.size(72.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = detail.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = EumTextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.width(EumSpacing.small))
                        ReportHistoryStatusChip(
                            label = detail.statusLabel,
                            isApproved = detail.isApproved,
                        )
                    }
                    Text(
                        text = detail.locationText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = EumTextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = detail.submittedAtText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = EumTextMuted,
                    )
                }
            }
            HorizontalDivider(color = EumBorderSubtle)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EumSpacing.large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "접수 번호",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EumTextMuted,
                )
                Text(
                    text = detail.receiptNumberText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = EumTextPrimary,
                )
            }
        }
    }
}

@Composable
private fun ReportHistoryDetailDescriptionCard(description: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(reportHistoryLayoutSpec().cardCornerRadiusDp.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.65f)),
        shadowElevation = reportHistoryLayoutSpec().cardShadowElevationDp.dp,
    ) {
        Column(
            modifier = Modifier.padding(EumSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
        ) {
            Text(
                text = "제보 내용",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = EumTextPrimary,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = EumTextPrimary,
            )
        }
    }
}

@Composable
private fun ReportHistoryProcessingCard(detail: ReportHistoryDetailUiModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(reportHistoryLayoutSpec().cardCornerRadiusDp.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.65f)),
        shadowElevation = reportHistoryLayoutSpec().cardShadowElevationDp.dp,
    ) {
        Column(
            modifier = Modifier.padding(EumSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
        ) {
            Text(
                text = "처리 현황",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = EumTextPrimary,
            )
            ReportHistoryTimelineStep(
                index = 1,
                title = "접수됨",
                description = "관리자가 내용을 확인하고 있어요",
                active = true,
            )
            ReportHistoryTimelineDivider()
            ReportHistoryTimelineStep(
                index = 2,
                title = "반영 완료",
                description = "검토 후 지도 반영 결과를 안내해드려요",
                active = detail.isApproved,
            )
        }
    }
}

@Composable
private fun ReportHistoryTimelineStep(
    index: Int,
    title: String,
    description: String,
    active: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EumSpacing.medium),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (active) EumPrimary600 else MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (!active) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .semantics {},
                )
            }
            Surface(
                modifier = Modifier.matchParentSize(),
                shape = CircleShape,
                color = if (active) EumPrimary600 else MaterialTheme.colorScheme.surface,
                border = if (active) null else BorderStroke(2.dp, EumBorderSubtle),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) EumWhite else EumTextMuted,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (active) EumPrimary600 else EumTextPrimary,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = EumTextMuted,
            )
        }
    }
}

@Composable
private fun ReportHistoryTimelineDivider() {
    Box(
        modifier =
            Modifier
                .padding(start = 20.dp)
                .width(1.dp)
                .height(32.dp)
                .background(EumBorderSubtle),
    )
}

@Composable
private fun ReportHistoryLoadingState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    EumLoadingState(
        title = title,
        description = description,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = EumSpacing.large, vertical = 36.dp),
    )
}

@Composable
private fun ReportHistoryStateCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    primaryActionLabel: String? = null,
    primaryActionSuppressRipple: Boolean = false,
    onPrimaryActionClick: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    secondaryActionSuppressRipple: Boolean = false,
    onSecondaryActionClick: (() -> Unit)? = null,
    isLoading: Boolean = false,
    isError: Boolean = false,
) {
    val spec = reportHistoryLayoutSpec()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(spec.cardCornerRadiusDp.dp),
        color = MaterialTheme.colorScheme.surface,
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    if (isError) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.36f)
                    } else {
                        EumBorderSubtle
                    },
            ),
        shadowElevation = spec.cardShadowElevationDp.dp,
    ) {
        Column(
            modifier = Modifier.padding(EumSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
            horizontalAlignment = Alignment.Start,
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            if (primaryActionLabel != null && onPrimaryActionClick != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
                ) {
                    if (primaryActionSuppressRipple) {
                        NoRippleReportHistoryNavigationButton(
                            onClick = onPrimaryActionClick,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = spec.buttonMinHeightDp.dp),
                            shape = RoundedCornerShape(spec.buttonCornerRadiusDp.dp),
                        ) {
                            Text(
                                text = primaryActionLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        Button(
                            onClick = onPrimaryActionClick,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = spec.buttonMinHeightDp.dp),
                            shape = RoundedCornerShape(spec.buttonCornerRadiusDp.dp),
                            elevation =
                                ButtonDefaults.buttonElevation(
                                    defaultElevation = 0.dp,
                                    pressedElevation = 0.dp,
                                    focusedElevation = 0.dp,
                                    hoveredElevation = 0.dp,
                                    disabledElevation = 0.dp,
                                ),
                        ) {
                            Text(text = primaryActionLabel)
                        }
                    }
                    if (secondaryActionLabel != null && onSecondaryActionClick != null) {
                        if (secondaryActionSuppressRipple) {
                            NoRippleReportHistoryNavigationButton(
                                onClick = onSecondaryActionClick,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .heightIn(min = spec.buttonMinHeightDp.dp),
                                shape = RoundedCornerShape(spec.buttonCornerRadiusDp.dp),
                                isOutlined = true,
                            ) {
                                Text(
                                    text = secondaryActionLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = onSecondaryActionClick,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .heightIn(min = spec.buttonMinHeightDp.dp),
                                shape = RoundedCornerShape(spec.buttonCornerRadiusDp.dp),
                            ) {
                                Text(text = secondaryActionLabel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoRippleReportHistoryNavigationButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isOutlined: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(reportHistoryLayoutSpec().buttonCornerRadiusDp.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor =
        when {
            !enabled && isOutlined -> MaterialTheme.colorScheme.surface
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            isOutlined -> EumSurfaceSubtle
            else -> EumPrimary600
        }
    val contentColor =
        when {
            !enabled && isOutlined -> EumPrimary600.copy(alpha = 0.56f)
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            isOutlined -> EumTextPrimary
            else -> EumWhite
        }
    val border =
        if (isOutlined) {
            BorderStroke(1.dp, EumBorderSubtle)
        } else {
            null
        }

    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
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
                    .padding(horizontal = EumSpacing.medium),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@DrawableRes
private fun String.toReportIconRes(): Int =
    when (this) {
        "계단·단차 있음" -> R.drawable.ic_report_stairs
        "점자블록 문제" -> R.drawable.ic_report_tactile_damage
        "인도 없음" -> R.drawable.ic_report_sidewalk
        "경사로 문제" -> R.drawable.ic_report_ramp
        "인도폭 문제" -> R.drawable.ic_report_roadway
        else -> R.drawable.ic_report_other
    }

@Composable
private fun reportIconBackgroundColor(title: String): Color =
    if (title == "경사로 문제" || title == "기타 장애물") {
        Color(0xFFFFF4E5)
    } else {
        EumSurfaceInfo
    }
