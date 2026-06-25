package com.ssafy.e102.eumgil.feature.navigation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.feature.guidance.component.RouteStepScrubberItem
import com.ssafy.e102.eumgil.feature.guidance.component.RouteStepScrubberRail
import com.ssafy.e102.eumgil.feature.navigation.NavigationGuidanceAction
import com.ssafy.e102.eumgil.feature.navigation.NavigationSegmentRailItemUiState
import com.ssafy.e102.eumgil.feature.navigation.NavigationSegmentSyncUiState

@Composable
fun NavigationSegmentRail(
    uiState: NavigationSegmentSyncUiState,
    onSegmentTapped: (Int) -> Unit,
    onTopVisibleSegmentChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val dividerColor = NavigationSegmentRailDividerColor
    val railSlots = createNavigationSegmentRailSlots(uiState)
    val railFocusItems = railSlots.focusItems()
    val scrubberItems =
        remember(railSlots, uiState.focusedSegmentIndex) {
            railFocusItems.map { item ->
                RouteStepScrubberItem(
                    index = item.index,
                    action = item.guidanceAction,
                    isOrigin = item == railSlots.originItem,
                    isDestination = item == railSlots.destinationItem,
                    contentDescription = "${item.guidanceAction.label} ${item.distanceLabel}",
                    stateDescription = item.stateLabel,
                )
            }
        }
    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            ) {
                RouteStepScrubberRail(
                    items = scrubberItems,
                    focusedItemIndex = uiState.focusedSegmentIndex,
                    onFocusedItemChanged = onTopVisibleSegmentChanged,
                    onItemClick = onSegmentTapped,
                    itemHeight = NavigationSegmentRailItemHeight,
                    trailingActionHeight = NavigationSegmentRailTopActionHeight,
                    dividerColor = NavigationSegmentRailDividerColor,
                    trailingAction = {
                        NavigationSegmentRailTopAction(
                            enabled = railSlots.canScrollToTop,
                            dividerColor = dividerColor,
                            onClick = {
                                railFocusItems.firstOrNull()?.index?.let { firstIndex ->
                                    onTopVisibleSegmentChanged(firstIndex)
                                    onSegmentTapped(firstIndex)
                                }
                            },
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(dividerColor),
        )
    }
}

@Composable
private fun NavigationSegmentRailTopAction(
    enabled: Boolean,
    dividerColor: Color,
    onClick: () -> Unit,
) {
    val label = stringResource(id = R.string.navigation_rail_scroll_to_top_label)
    val outlineColor =
        if (enabled) {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
        }
    val iconAlpha = if (enabled) 0.86f else 0.34f

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(NavigationSegmentRailTopActionHeight)
                    .semantics {
                        contentDescription = label
                        if (!enabled) {
                            disabled()
                        }
                    }
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .border(
                            width = 1.dp,
                            color = outlineColor,
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_route_scroll_top),
                    contentDescription = null,
                    tint = NavigationSegmentRailIconColor,
                    modifier =
                        Modifier
                            .size(22.dp)
                            .alpha(iconAlpha),
                )
            }
        }
        HorizontalDivider(color = dividerColor)
    }
}

internal data class NavigationSegmentRailSlots(
    val originItem: NavigationSegmentRailItemUiState? = null,
    val intermediateItems: List<NavigationSegmentRailItemUiState> = emptyList(),
    val destinationItem: NavigationSegmentRailItemUiState? = null,
    val canScrollToTop: Boolean = false,
)

internal fun createNavigationSegmentRailSlots(uiState: NavigationSegmentSyncUiState): NavigationSegmentRailSlots {
    val railItems = uiState.railItems

    return NavigationSegmentRailSlots(
        originItem = railItems.firstOrNull(),
        intermediateItems =
            if (railItems.size <= 2) {
                emptyList()
            } else {
                railItems.subList(1, railItems.lastIndex)
            },
        destinationItem = railItems.lastOrNull(),
        canScrollToTop = railItems.isNotEmpty(),
    )
}

internal fun resolveGuideRailAutoScrollItemIndex(
    focusedItemPosition: Int,
    itemCount: Int,
): Int? {
    if (itemCount <= 0) return null
    if (focusedItemPosition !in 0 until itemCount) return null
    return (focusedItemPosition + 1).coerceAtMost(itemCount - 1)
}

internal fun resolveGuideRailEndSnapPadding(
    viewportHeight: Dp,
    guideItemHeight: Dp,
    trailingActionHeight: Dp,
): Dp =
    (viewportHeight - guideItemHeight - trailingActionHeight).coerceAtLeast(0.dp)

internal fun NavigationGuidanceAction.railIconSize(): Dp =
    if (this == NavigationGuidanceAction.BUS || this == NavigationGuidanceAction.SUBWAY) {
        NavigationSegmentRailTransitIconSize
    } else {
        NavigationSegmentRailIconSize
    }

private fun NavigationSegmentRailSlots.focusItems(): List<NavigationSegmentRailItemUiState> =
    buildList {
        originItem?.let(::add)
        addAll(intermediateItems)
        destinationItem?.let(::add)
    }

private val NavigationSegmentRailIconSize = 34.dp
internal val NavigationSegmentRailTransitIconSize = 30.dp

private val NavigationSegmentRailItemUiState.stateLabel: String
    get() =
        when {
            isFocused -> "Selected segment"
            isActive -> "Current segment"
            isCompleted -> "Completed segment"
            else -> "Guidance segment"
        }

private data class NavigationRailPromotionSnapshot(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
    val firstVisibleItemSizePx: Int,
    val isScrollInProgress: Boolean,
    val promotedItemPosition: Int?,
) {
    fun shouldSnapToPromotedItem(): Boolean =
        promotedItemPosition != null &&
            !isScrollInProgress &&
            (firstVisibleItemScrollOffset > 0 || firstVisibleItemIndex != promotedItemPosition)
}

private val NavigationSegmentRailItemHeight = 72.dp
private val NavigationSegmentRailTopActionHeight = NavigationSegmentRailItemHeight
private val NavigationSegmentRailDividerColor = Color(0xFFD9D9D9)
private val NavigationSegmentRailIconColor = Color(0xFF2B2B2B)
