package com.ssafy.e102.eumgil.feature.guidance.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing
import com.ssafy.e102.eumgil.feature.navigation.NavigationGuidanceAction
import com.ssafy.e102.eumgil.feature.navigation.iconRes

@Composable
fun GuideSidePanelShell(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    expandedWidthFraction: Float = GuideSidePanelExpandedWidthFraction,
    collapsedWidth: Dp = GuideSidePanelCollapsedWidth,
    stateDescription: String? = null,
    content: @Composable BoxScope.(panelWidth: Dp) -> Unit,
) {
    val targetWidth =
        if (isExpanded) {
            androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp * expandedWidthFraction
        } else {
            collapsedWidth
        }
    val panelWidth by animateDpAsState(targetValue = targetWidth, label = "guide-side-panel-width")

    Box(
        modifier =
            modifier
                .width(panelWidth)
                .pointerInput(Unit) {
                    var dragOffsetPx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragOffsetPx = 0f },
                        onHorizontalDrag = { _, dragAmount -> dragOffsetPx += dragAmount },
                        onDragEnd = {
                            when {
                                dragOffsetPx < -GuideSidePanelSwipeThresholdPx -> onExpandedChange(false)
                                dragOffsetPx > GuideSidePanelSwipeThresholdPx -> onExpandedChange(true)
                            }
                        },
                    )
                }
                .semantics {
                    stateDescription?.let { this.stateDescription = it }
                },
    ) {
        Surface(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .width(panelWidth)
                    .fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.dp,
        ) {
            Box {
                content(panelWidth)
            }
        }
    }
}

@Composable
fun GuideSidePanelStepRow(
    title: String,
    action: NavigationGuidanceAction,
    modifier: Modifier = Modifier,
    description: String? = null,
    distanceLabel: String? = null,
    isOrigin: Boolean = false,
    isDestination: Boolean = false,
    isActive: Boolean = false,
    isFocused: Boolean = false,
    isSelected: Boolean = false,
    contentDescription: String? = null,
    stateDescription: String? = null,
    onClick: () -> Unit = {},
    leadingContentColor: Color = GuideSidePanelIconColor,
    minHeight: Dp = GuideSidePanelRowMinHeight,
    supportingContent: @Composable ColumnScope.() -> Unit = {},
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val tone = guideSidePanelItemTone(isActive = isActive, isFocused = isFocused, isSelected = isSelected)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .background(tone.expandedContainerColor)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = EumSpacing.medium, vertical = EumSpacing.small)
                .semantics(mergeDescendants = true) {
                    contentDescription?.let { this.contentDescription = it }
                    stateDescription?.let { this.stateDescription = it }
                    selected = isSelected || isFocused || isActive
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EumSpacing.medium),
    ) {
        GuideSidePanelStepIcon(
            action = action,
            isOrigin = isOrigin,
            isDestination = isDestination,
            contentColor = if (tone.isEmphasized) MaterialTheme.colorScheme.primary else leadingContentColor,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (tone.isEmphasized) FontWeight.SemiBold else FontWeight.Normal,
            )
            description?.takeIf(String::isNotBlank)?.let { descriptionText ->
                Text(
                    text = descriptionText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            distanceLabel?.takeIf(String::isNotBlank)?.let { distanceText ->
                Text(
                    text = distanceText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            supportingContent()
        }
        trailingContent()
    }
}

@Composable
fun GuideCollapsedRailItem(
    action: NavigationGuidanceAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isOrigin: Boolean = false,
    isDestination: Boolean = false,
    isActive: Boolean = false,
    isFocused: Boolean = false,
    isSelected: Boolean = false,
    enabled: Boolean = true,
    contentDescription: String? = null,
    stateDescription: String? = null,
    dividerColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
    height: Dp = GuideCollapsedRailItemHeight,
    isContentHidden: Boolean = false,
) {
    val tone =
        guideSidePanelItemTone(
            isActive = isActive,
            isFocused = isFocused,
            isSelected = isSelected,
            enabled = enabled,
        )
    val resolvedHeight = if (isContentHidden) 0.dp else height
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(resolvedHeight)
                    .background(tone.collapsedContainerColor)
                    .semantics {
                        contentDescription?.let { this.contentDescription = it }
                        stateDescription?.let { this.stateDescription = it }
                        selected = isSelected || isFocused || isActive
                        if (!enabled) {
                            disabled()
                        }
                    }
                    .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            GuideSidePanelStepIcon(
                action = action,
                isOrigin = isOrigin,
                isDestination = isDestination,
                contentColor = tone.iconTint,
                iconAlpha = if (isContentHidden) 0f else tone.iconAlpha,
                iconSize = action.collapsedIconSize(),
                pinWidth = GuideCollapsedRailPinWidth,
                pinHeight = GuideCollapsedRailPinHeight,
            )
        }
        if (!isContentHidden) {
            HorizontalDivider(color = dividerColor)
        }
    }
}

@Composable
fun GuideSidePanelStepIcon(
    action: NavigationGuidanceAction,
    contentColor: Color,
    modifier: Modifier = Modifier,
    isOrigin: Boolean = false,
    isDestination: Boolean = false,
    iconAlpha: Float = 1f,
    iconSize: Dp = GuideSidePanelIconSize,
    pinWidth: Dp = GuideSidePanelPinWidth,
    pinHeight: Dp = GuideSidePanelPinHeight,
) {
    val pinIcon =
        when {
            isOrigin -> R.drawable.ic_navigation_rail_origin_pin
            isDestination -> R.drawable.ic_navigation_rail_destination_pin
            else -> null
        }
    Box(
        modifier = modifier.size(GuideSidePanelIconFrameSize),
        contentAlignment = Alignment.Center,
    ) {
        if (pinIcon != null) {
            Image(
                painter = painterResource(id = pinIcon),
                contentDescription = null,
                modifier =
                    Modifier
                        .width(pinWidth)
                        .height(pinHeight)
                        .alpha(iconAlpha),
                contentScale = ContentScale.Fit,
            )
        } else {
            Icon(
                painter = painterResource(id = action.iconRes()),
                contentDescription = null,
                tint = contentColor,
                modifier =
                    Modifier
                        .size(iconSize)
                        .alpha(iconAlpha),
            )
        }
    }
}

@Composable
fun GuideSidePanelHandle(
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expandedContentDescription: String = "Collapse guide panel",
    collapsedContentDescription: String = "Expand guide panel",
) {
    Surface(
        modifier =
            modifier
                .size(width = GuideSidePanelHandleTouchWidth, height = GuideSidePanelHandleTouchHeight)
                .clickable(role = Role.Button, onClick = onClick),
        shape =
            RoundedCornerShape(
                topEnd = GuideSidePanelHandleRadius,
                bottomEnd = GuideSidePanelHandleRadius,
            ),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(GuideSidePanelHandleStrokeWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (isExpanded) "<" else ">",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier.semantics {
                        contentDescription = if (isExpanded) expandedContentDescription else collapsedContentDescription
                    },
            )
        }
    }
}

@Composable
fun guideSidePanelItemTone(
    isActive: Boolean,
    isFocused: Boolean,
    isSelected: Boolean,
    enabled: Boolean = true,
): GuideSidePanelItemTone {
    val emphasized = enabled && (isActive || isFocused || isSelected)
    return GuideSidePanelItemTone(
        expandedContainerColor = MaterialTheme.colorScheme.surface,
        collapsedContainerColor = if (emphasized) MaterialTheme.colorScheme.primary else Color.Transparent,
        iconTint =
            when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                emphasized -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        iconAlpha = if (enabled) 0.94f else 0.34f,
        isEmphasized = emphasized,
    )
}

data class GuideSidePanelItemTone(
    val expandedContainerColor: Color,
    val collapsedContainerColor: Color,
    val iconTint: Color,
    val iconAlpha: Float,
    val isEmphasized: Boolean,
)

internal fun resolveGuideRailPromotedItemIndex(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    firstVisibleItemSizePx: Int,
    itemCount: Int,
): Int? {
    if (itemCount <= 0) return null
    if (firstVisibleItemIndex < 0) return null

    val clampedFirstVisibleItemIndex = firstVisibleItemIndex.coerceAtMost(itemCount - 1)
    if (firstVisibleItemSizePx <= 0) return clampedFirstVisibleItemIndex

    val shouldPromoteNext =
        firstVisibleItemScrollOffset.coerceAtLeast(0) * 2 > firstVisibleItemSizePx
    return if (shouldPromoteNext) {
        (clampedFirstVisibleItemIndex + 1).coerceAtMost(itemCount - 1)
    } else {
        clampedFirstVisibleItemIndex
    }
}

internal fun shouldHideGuideRailItemForTopCard(
    itemIndex: Int,
    promotedItemIndex: Int?,
): Boolean = promotedItemIndex != null && itemIndex == promotedItemIndex

private fun NavigationGuidanceAction.collapsedIconSize(): Dp =
    if (this == NavigationGuidanceAction.BUS || this == NavigationGuidanceAction.SUBWAY) {
        GuideCollapsedRailTransitIconSize
    } else {
        GuideCollapsedRailIconSize
    }

private val GuideSidePanelCollapsedWidth = 56.dp
private const val GuideSidePanelExpandedWidthFraction = 0.88f
private const val GuideSidePanelSwipeThresholdPx = 80f
private val GuideSidePanelRowMinHeight = 76.dp
private val GuideSidePanelIconFrameSize = 54.dp
private val GuideSidePanelIconSize = 28.dp
private val GuideSidePanelIconColor = Color(0xFF2C2F36)
private val GuideWaypointOriginColor = Color(0xFF4D8FF9)
private val GuideWaypointDestinationColor = Color(0xFFF94D4D)
private val GuideCollapsedRailItemHeight = 96.dp
private val GuideCollapsedRailIconSize = 28.dp
private val GuideCollapsedRailTransitIconSize = 22.dp
private val GuideCollapsedRailPinWidth = 42.dp
private val GuideCollapsedRailPinHeight = GuideCollapsedRailPinWidth
private val GuideSidePanelPinWidth = GuideCollapsedRailPinWidth
private val GuideSidePanelPinHeight = GuideCollapsedRailPinHeight
private val GuideSidePanelHandleTouchWidth = 48.dp
private val GuideSidePanelHandleTouchHeight = 64.dp
private val GuideSidePanelHandleRadius = 24.dp
private val GuideSidePanelHandleStrokeWidth = 1.dp
