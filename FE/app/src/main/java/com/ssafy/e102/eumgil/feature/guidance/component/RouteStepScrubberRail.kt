package com.ssafy.e102.eumgil.feature.guidance.component

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ssafy.e102.eumgil.feature.navigation.NavigationGuidanceAction
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class RouteStepScrubberItem(
    val index: Int,
    val action: NavigationGuidanceAction,
    val contentDescription: String,
    val stateDescription: String,
    val isOrigin: Boolean = false,
    val isDestination: Boolean = false,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RouteStepScrubberRail(
    items: List<RouteStepScrubberItem>,
    focusedItemIndex: Int?,
    onFocusedItemChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onItemClick: ((Int) -> Unit)? = null,
    itemHeight: Dp = RouteStepScrubberItemHeight,
    trailingActionHeight: Dp = RouteStepScrubberTrailingActionHeight,
    dividerColor: Color = RouteStepScrubberDividerColor,
    trailingAction: (@Composable BoxScope.() -> Unit)? = null,
) {
    val itemHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { itemHeight.toPx() }
    val resolvedFocusedIndex = items.resolveFocusedScrubberIndex(focusedItemIndex)
    val currentOnFocusedItemChanged by rememberUpdatedState(onFocusedItemChanged)
    val currentOnItemClick by rememberUpdatedState(onItemClick)
    val currentResolvedFocusedIndex by rememberUpdatedState(resolvedFocusedIndex)
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    var lastScrubbedIndex by remember { mutableStateOf<Int?>(null) }
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    val state =
        remember {
            AnchoredDraggableState(
                initialValue = resolvedFocusedIndex,
                positionalThreshold = { distance -> distance * 0.5f },
                velocityThreshold = { Float.POSITIVE_INFINITY },
                animationSpec = RouteStepScrubberSettleSpec,
            )
        }
    val anchors =
        remember(items, itemHeightPx) {
            DraggableAnchors {
                items.forEachIndexed { position, item ->
                    item.index at resolveRouteStepScrubberAnchor(position, itemHeightPx)
                }
            }
        }
    val maxScrubberOffsetPx =
        remember(items, itemHeightPx) {
            resolveRouteStepScrubberAnchor(
                index = (items.size - 1).coerceAtLeast(0),
                itemHeightPx = itemHeightPx,
            )
        }

    LaunchedEffect(anchors, resolvedFocusedIndex, isDragged) {
        state.updateAnchors(anchors, newTarget = resolvedFocusedIndex)
        if (!isDragged && state.currentValue != resolvedFocusedIndex) {
            isProgrammaticScroll = true
            lastScrubbedIndex = resolvedFocusedIndex
            try {
                state.animateTo(resolvedFocusedIndex)
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    LaunchedEffect(state, items, itemHeightPx) {
        var hasObservedInitialPosition = false
        snapshotFlow {
            val offset = state.offset.takeUnless(Float::isNaN) ?: 0f
            resolveRouteStepScrubberIndex(
                offsetPx = offset,
                itemHeightPx = itemHeightPx,
                itemCount = items.size,
            )?.let { position -> items.getOrNull(position)?.index }
        }
            .distinctUntilChanged()
            .collect { index ->
                if (index == null) {
                    return@collect
                }
                if (!hasObservedInitialPosition) {
                    hasObservedInitialPosition = true
                    return@collect
                }
                if (!isProgrammaticScroll && index != currentResolvedFocusedIndex) {
                    lastScrubbedIndex = index
                    currentOnFocusedItemChanged(index)
                }
            }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clipToBounds()
                .anchoredDraggable(
                    state = state,
                    orientation = Orientation.Vertical,
                    reverseDirection = true,
                    interactionSource = interactionSource,
                ),
    ) {
        items.forEachIndexed { position, item ->
            val anchorPx = resolveRouteStepScrubberAnchor(position, itemHeightPx)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .offset {
                            IntOffset(
                                x = 0,
                                y =
                                    resolveRouteStepScrubberVisualOffset(
                                        anchorPx = anchorPx,
                                        scrubberOffsetPx = currentRouteStepScrubberOffsetPx(state, maxScrubberOffsetPx),
                                        itemHeightPx = itemHeightPx,
                                    ).roundToInt(),
                            )
                        },
            ) {
                if (item.index != resolvedFocusedIndex) {
                    GuideCollapsedRailItem(
                        action = item.action,
                        isOrigin = item.isOrigin,
                        isDestination = item.isDestination,
                        contentDescription = item.contentDescription,
                        stateDescription = item.stateDescription,
                        height = itemHeight,
                        dividerColor = dividerColor,
                        isContentHidden = false,
                        onClick = {
                            coroutineScope.launch {
                                isProgrammaticScroll = true
                                lastScrubbedIndex = item.index
                                try {
                                    state.animateTo(item.index)
                                    currentOnItemClick?.invoke(item.index)
                                        ?: currentOnFocusedItemChanged(item.index)
                                } finally {
                                    isProgrammaticScroll = false
                                }
                            }
                        },
                    )
                }
            }
        }
        trailingAction?.let { content ->
            val anchorPx = resolveRouteStepScrubberAnchor(items.size, itemHeightPx)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(trailingActionHeight)
                        .offset {
                            IntOffset(
                                x = 0,
                                y =
                                    resolveRouteStepScrubberVisualOffset(
                                        anchorPx = anchorPx,
                                        scrubberOffsetPx = currentRouteStepScrubberOffsetPx(state, maxScrubberOffsetPx),
                                        itemHeightPx = itemHeightPx,
                                    ).roundToInt(),
                            )
                        },
                content = content,
            )
        }
    }
}

internal fun resolveRouteStepScrubberIndex(
    offsetPx: Float,
    itemHeightPx: Float,
    itemCount: Int,
): Int? {
    if (itemCount <= 0 || itemHeightPx <= 0f) return null
    return (offsetPx / itemHeightPx)
        .roundToInt()
        .coerceIn(0, itemCount - 1)
}

internal fun resolveRouteStepScrubberAnchor(
    index: Int,
    itemHeightPx: Float,
): Float {
    if (index <= 0 || itemHeightPx <= 0f) return 0f
    return index * itemHeightPx
}

internal fun resolveRouteStepScrubberVisualOffset(
    anchorPx: Float,
    scrubberOffsetPx: Float,
    itemHeightPx: Float,
): Float {
    val visualOffsetPx = anchorPx - scrubberOffsetPx
    return if (itemHeightPx > 0f && anchorPx > scrubberOffsetPx) {
        visualOffsetPx - itemHeightPx
    } else {
        visualOffsetPx
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun currentRouteStepScrubberOffsetPx(
    state: AnchoredDraggableState<Int>,
    maxScrubberOffsetPx: Float,
): Float =
    (state.offset.takeUnless(Float::isNaN) ?: 0f).coerceIn(0f, maxScrubberOffsetPx)

private fun List<RouteStepScrubberItem>.resolveFocusedScrubberIndex(focusedItemIndex: Int?): Int =
    focusedItemIndex
        ?.takeIf { index -> any { item -> item.index == index } }
        ?: firstOrNull()?.index
        ?: 0

private val RouteStepScrubberSettleSpec: AnimationSpec<Float> =
    tween(durationMillis = 180, easing = FastOutSlowInEasing)

private val RouteStepScrubberItemHeight = 96.dp
private val RouteStepScrubberTrailingActionHeight = 56.dp
private val RouteStepScrubberDividerColor = Color(0xFFD9D9D9)
