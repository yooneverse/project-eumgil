package com.ssafy.e102.eumgil.feature.map.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ssafy.e102.eumgil.R
import kotlin.math.roundToInt

@Immutable
data class RecentDestinationBottomSheetState(
    val isVisible: Boolean = false,
    val title: String = "최근 목적지",
    val items: List<RecentDestinationRowState> = emptyList(),
)

@Immutable
data class RecentDestinationRowState(
    val placeId: String,
    val title: String,
    val address: String,
    val tags: List<String> = emptyList(),
    @DrawableRes val iconRes: Int,
)

@Composable
fun RecentDestinationBottomSheetShell(
    state: RecentDestinationBottomSheetState,
    onPreviewClick: (String) -> Unit,
    onRouteClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val dragSettleVelocityThresholdPx = with(density) { 320.dp.toPx() }
    val dismissThresholdMinPx = with(density) { 72.dp.toPx() }
    val handleInteractionSource = remember { MutableInteractionSource() }
    val restoreHandleInteractionSource = remember { MutableInteractionSource() }
    val listScrollState = rememberScrollState()
    var isExpanded by remember(state.items) { mutableStateOf(false) }
    var isDismissedByUser by remember(state.items) { mutableStateOf(false) }
    var sheetHeightPx by remember(state.items, state.isVisible, isExpanded) { mutableIntStateOf(0) }
    var sheetOffsetPx by remember(state.items, state.isVisible, isExpanded) { mutableFloatStateOf(0f) }
    var isDragging by remember(state.items, state.isVisible, isExpanded) { mutableStateOf(false) }
    val isSheetVisible = state.isVisible && !isDismissedByUser
    val isRestoreHandleVisible = state.isVisible && isDismissedByUser
    val restoreHandleDescription = stringResource(id = R.string.map_recent_destination_sheet_restore)
    val canExpand = state.items.size > RecentDestinationCollapsedItemCount
    val displayedItems =
        if (isExpanded && canExpand) {
            state.items.take(RecentDestinationExpandedItemLimit)
        } else {
            state.items.take(RecentDestinationCollapsedItemCount)
        }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val sheetMaxHeight =
            maxHeight *
                if (isExpanded && canExpand) {
                    RecentDestinationExpandedSheetMaxHeightFraction
                } else {
                    RecentDestinationCollapsedSheetMaxHeightFraction
                }
        val listMaxHeight =
            (sheetMaxHeight - RecentDestinationExpandedListReservedHeight)
                .coerceAtLeast(RecentDestinationExpandedListMinHeight)
        val maxSheetOffsetPx = sheetHeightPx.toFloat().coerceAtLeast(0f)
        val dismissThresholdPx = (sheetHeightPx * 0.35f).coerceAtLeast(dismissThresholdMinPx)
        val animatedSheetOffsetPx by animateFloatAsState(
            targetValue = sheetOffsetPx.coerceIn(0f, maxSheetOffsetPx),
            animationSpec =
                if (isDragging) {
                    snap()
                } else {
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    )
                },
            label = "recentDestinationSheetOffset",
        )
        val dragState =
            rememberDraggableState { delta ->
                isDragging = true
                sheetOffsetPx = (sheetOffsetPx + delta).coerceIn(0f, maxSheetOffsetPx)
            }
        val restoreDragState =
            rememberDraggableState { delta ->
                if (delta < 0f) {
                    isDismissedByUser = false
                    sheetOffsetPx = 0f
                }
            }

        LaunchedEffect(isSheetVisible, maxSheetOffsetPx) {
            if (!isSheetVisible) {
                isDragging = false
                sheetOffsetPx = 0f
            } else {
                sheetOffsetPx = sheetOffsetPx.coerceIn(0f, maxSheetOffsetPx)
            }
        }

        LaunchedEffect(canExpand) {
            if (!canExpand) {
                isExpanded = false
            }
        }

        LaunchedEffect(isExpanded, state.items) {
            listScrollState.scrollTo(0)
        }

        AnimatedVisibility(
            visible = isSheetVisible,
            enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight / 3 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight }) + fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
        ) {
            MapBottomSheetSurface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = sheetMaxHeight)
                        .onSizeChanged { size ->
                            sheetHeightPx = size.height
                            sheetOffsetPx = sheetOffsetPx.coerceIn(0f, maxSheetOffsetPx)
                        }
                        .offset { IntOffset(x = 0, y = animatedSheetOffsetPx.roundToInt()) },
                handleModifier =
                    Modifier
                        .height(MapBottomSheetHandleHeight)
                        .semantics {
                            role = Role.Button
                            contentDescription = "최근 목적지 시트 닫기"
                        }
                        .clickable(
                            interactionSource = handleInteractionSource,
                            indication = null,
                            onClick = {
                                isDragging = false
                                isDismissedByUser = true
                                sheetOffsetPx = 0f
                            },
                        )
                        .draggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity ->
                                isDragging = false
                                if (
                                    velocity >= dragSettleVelocityThresholdPx ||
                                    sheetOffsetPx >= dismissThresholdPx
                                ) {
                                    isDismissedByUser = true
                                }
                                sheetOffsetPx = 0f
                            },
                        ),
                containerColor = MaterialTheme.colorScheme.surface,
                edgeTreatment = MapBottomSheetEdgeTreatment.AttachedToBottomBar,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (canExpand) {
                        RecentDestinationSheetToggleAction(
                            onClick = {
                                isExpanded = !isExpanded
                                sheetOffsetPx = 0f
                                isDragging = false
                            },
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text =
                                        stringResource(
                                            id =
                                                if (isExpanded) {
                                                    R.string.map_recent_destination_collapse
                                                } else {
                                                    R.string.map_recent_destination_expand
                                                },
                                        ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_route_card_chevron),
                                    contentDescription = null,
                                    modifier =
                                        Modifier
                                            .size(16.dp)
                                            .rotate(
                                                if (isExpanded) {
                                                    -90f
                                                } else {
                                                    90f
                                                },
                                            ),
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier =
                        if (isExpanded && canExpand) {
                            Modifier
                                .heightIn(max = listMaxHeight)
                                .verticalScroll(listScrollState)
                        } else {
                            Modifier
                        },
                    verticalArrangement =
                        Arrangement.spacedBy(
                            if (isExpanded && canExpand) {
                                12.dp
                            } else {
                                16.dp
                            },
                        ),
                ) {
                    displayedItems.forEachIndexed { index, item ->
                        RecentDestinationRow(
                            state = item,
                            onPreviewClick = { onPreviewClick(item.placeId) },
                            onRouteClick = { onRouteClick(item.placeId) },
                        )
                        if (index != displayedItems.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isRestoreHandleVisible,
            enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight }) + fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
        ) {
            RecentDestinationRestoreHandle(
                contentDescription = restoreHandleDescription,
                interactionSource = restoreHandleInteractionSource,
                dragState = restoreDragState,
                onClick = {
                    isDismissedByUser = false
                    sheetOffsetPx = 0f
                },
            )
        }
    }
}

@Composable
private fun RecentDestinationRestoreHandle(
    contentDescription: String,
    interactionSource: MutableInteractionSource,
    dragState: DraggableState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .height(RecentDestinationRestoreHandleHeight),
        shape =
            RoundedCornerShape(
                topStart = 24.dp,
                topEnd = 24.dp,
            ),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        shadowElevation = 12.dp,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                    )
                    .semantics {
                        role = Role.Button
                        this.contentDescription = contentDescription
                    }
                    .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(42.dp)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp)),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RecentDestinationRow(
    state: RecentDestinationRowState,
    onPreviewClick: () -> Unit,
    onRouteClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onPreviewClick,
                    ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painter = painterResource(id = state.iconRes),
                contentDescription = null,
                modifier =
                    Modifier
                        .padding(top = 2.dp)
                        .size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        state.tags.forEach { label ->
                            RecentDestinationTagChip(
                                label = label,
                            )
                        }
                    }
                }
            }
        }

        RecentDestinationRouteButton(
            onClick = onRouteClick,
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_route_start_navigation_button),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "길찾기",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun RecentDestinationSheetToggleAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier =
            modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        content()
    }
}

@Composable
private fun RecentDestinationRouteButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier =
                Modifier
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                    .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
private fun RecentDestinationTagChip(
    label: String,
) {
    val iconRes = recentDestinationTagIconRes(label = label)
    val containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
    val contentColor = MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border =
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconRes?.let { resId ->
                Icon(
                    painter = painterResource(id = resId),
                    contentDescription = null,
                    modifier = Modifier.size(recentDestinationTagIconSizeDp(resId).dp),
                    tint = contentColor,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
@DrawableRes
private fun recentDestinationTagIconRes(
    label: String,
): Int? {
    val normalizedLabel = label.trim()
    val bareGuidanceLabel = stringResource(id = R.string.place_accessibility_label_guidance_facility).substringBeforeLast(' ')
    return when (normalizedLabel) {
        stringResource(id = R.string.place_accessibility_label_accessible_toilet),
        stringResource(id = R.string.map_facility_detail_tag_accessible_toilet),
        -> R.drawable.ic_accessibility_tag_accessible_toilet

        stringResource(id = R.string.place_accessibility_label_elevator),
        stringResource(id = R.string.map_facility_detail_tag_elevator),
        -> R.drawable.ic_accessibility_tag_elevator

        stringResource(id = R.string.place_accessibility_label_accessible_parking),
        stringResource(id = R.string.map_facility_detail_tag_accessible_parking),
        -> R.drawable.ic_accessibility_tag_accessible_parking

        stringResource(id = R.string.place_accessibility_label_step_free),
        stringResource(id = R.string.map_facility_detail_tag_step_free_entrance),
        -> R.drawable.ic_accessibility_tag_step_free

        stringResource(id = R.string.map_facility_detail_tag_charging_station),
        -> R.drawable.ic_accessibility_tag_charging_station

        bareGuidanceLabel,
        stringResource(id = R.string.place_accessibility_label_guidance_facility),
        stringResource(id = R.string.map_facility_detail_tag_guidance_facility),
        -> R.drawable.ic_accessibility_tag_guidance_facility

        stringResource(id = R.string.place_accessibility_label_accessible_room),
        stringResource(id = R.string.map_facility_detail_tag_accessible_room),
        -> R.drawable.ic_accessibility_tag_accessible_room

        else -> null
    }
}

private fun recentDestinationTagIconSizeDp(
    @DrawableRes iconRes: Int,
): Int =
    when (iconRes) {
        R.drawable.ic_accessibility_tag_accessible_toilet -> 14
        else -> 12
    }

private val RecentDestinationRestoreHandleHeight = 32.dp
private const val RecentDestinationCollapsedItemCount = 3
private const val RecentDestinationExpandedItemLimit = 10
private const val RecentDestinationCollapsedSheetMaxHeightFraction = 0.58f
private const val RecentDestinationExpandedSheetMaxHeightFraction = 0.65f
private val RecentDestinationExpandedListReservedHeight = 88.dp
private val RecentDestinationExpandedListMinHeight = 240.dp
