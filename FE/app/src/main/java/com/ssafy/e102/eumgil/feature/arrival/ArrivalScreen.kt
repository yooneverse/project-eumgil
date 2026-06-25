package com.ssafy.e102.eumgil.feature.arrival

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.designsystem.theme.EumPrimary600
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing
import com.ssafy.e102.eumgil.feature.map.component.MapBottomSheetHandleHeight
import com.ssafy.e102.eumgil.feature.map.component.MapBottomSheetSurface
import kotlin.math.roundToInt

private val ArrivalCloseButtonTint = Color(0xFF9CA3AF)
private val ArrivalRatingSelectedColor = Color(0xFFFACC15)
private val ArrivalEvaluationSectionSpacing = EumSpacing.small
private val ArrivalHeroBandHeight = 332.dp
private val ArrivalHeroBandTopSpacing = 36.dp
private val ArrivalHeroArtworkBottomSpacing = 28.dp
private val ArrivalHeroBackgroundFadeHeight = 40.dp
private val ArrivalHeroLogoTopPadding = 58.dp
private val ArrivalHeroLogoWidth = 108.dp
private val ArrivalHeroLogoHeight = 60.dp
private val ArrivalRatingButtonSize = 64.dp
private val ArrivalRatingIconSize = 56.dp
private val ArrivalRatingRowVerticalPadding = 22.dp
private const val ArrivalSheetDismissAnimationDurationMillis = 220
private const val ArrivalHeroArtworkAspectRatio = 1440f / 900f
private const val ArrivalRatingCount = 5

@Composable
fun ArrivalScreen(
    uiState: ArrivalUiState,
    onAction: (ArrivalUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        ArrivalCompletionContent(
            onHomeClicked = { onAction(ArrivalUiAction.HomeClicked) },
            onExploreNewRouteClicked = { onAction(ArrivalUiAction.ExploreNewRouteClicked) },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = uiState.isEvaluationSheetVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.22f)),
            )
        }

        ArrivalEvaluationBottomSheet(
            uiState = uiState,
            onAction = onAction,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ArrivalCompletionContent(
    onHomeClicked: () -> Unit,
    onExploreNewRouteClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(bottom = EumSpacing.medium),
    ) {
        Spacer(modifier = Modifier.height(ArrivalHeroBandTopSpacing))

        ArrivalHeroBand()

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EumSpacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(id = R.string.arrival_screen_headline),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(EumSpacing.small))
            Text(
                text = stringResource(id = R.string.arrival_screen_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        ArrivalCompletionActions(
            onHomeClicked = onHomeClicked,
            onExploreNewRouteClicked = onExploreNewRouteClicked,
        )
    }
}

@Composable
private fun ArrivalCompletionActions(
    onHomeClicked: () -> Unit,
    onExploreNewRouteClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = EumSpacing.large)
                .padding(bottom = EumSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
    ) {
        Button(
            onClick = onHomeClicked,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            shape = RoundedCornerShape(EumRadius.medium),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_nav_home_filled),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(EumSpacing.xxSmall))
            Text(
                text = stringResource(id = R.string.arrival_action_go_home),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
        OutlinedButton(
            onClick = onExploreNewRouteClicked,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            shape = RoundedCornerShape(EumRadius.medium),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_nav_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(EumSpacing.xxSmall))
            Text(
                text = stringResource(id = R.string.arrival_action_explore_new_route),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun ArrivalHeroBand(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(ArrivalHeroBandHeight)
                .background(MaterialTheme.colorScheme.background),
    ) {
        Image(
            painter = painterResource(id = R.drawable.arrival_completion_background),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(ArrivalHeroArtworkAspectRatio)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = ArrivalHeroArtworkBottomSpacing),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(ArrivalHeroBackgroundFadeHeight)
                    .align(Alignment.BottomCenter)
                    .background(
                        brush =
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                                        MaterialTheme.colorScheme.background,
                                    ),
                            ),
                    ),
        )
    }
}

@Composable
private fun ArrivalEvaluationBottomSheet(
    uiState: ArrivalUiState,
    onAction: (ArrivalUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val dragSettleVelocityThresholdPx = with(density) { 320.dp.toPx() }
    val dismissThresholdMinPx = with(density) { 72.dp.toPx() }
    val handleInteractionSource = remember { MutableInteractionSource() }
    val closeSheetLabel = stringResource(id = R.string.arrival_evaluation_close)
    var sheetHeightPx by remember(uiState.isEvaluationSheetVisible) { mutableIntStateOf(0) }
    var sheetOffsetPx by remember(uiState.isEvaluationSheetVisible) { mutableFloatStateOf(0f) }
    var isDragging by remember(uiState.isEvaluationSheetVisible) { mutableStateOf(false) }
    var isDismissAnimating by remember(uiState.isEvaluationSheetVisible) { mutableStateOf(false) }
    var shouldRenderSheet by remember(uiState.isEvaluationSheetVisible) { mutableStateOf(uiState.isEvaluationSheetVisible) }

    BoxWithConstraints(
        modifier = modifier,
    ) {
        val sheetMaxHeight = maxHeight * 0.82f
        val maxSheetOffsetPx = sheetHeightPx.toFloat().coerceAtLeast(0f)
        val dismissThresholdPx = (sheetHeightPx * 0.35f).coerceAtLeast(dismissThresholdMinPx)
        val isSheetVisible = shouldRenderSheet
        val animatedSheetOffsetPx by
            animateFloatAsState(
                targetValue =
                    when {
                        isDismissAnimating -> maxSheetOffsetPx
                        else -> sheetOffsetPx.coerceIn(0f, maxSheetOffsetPx)
                    },
                animationSpec =
                    when {
                        isDragging -> snap()
                        isDismissAnimating ->
                            tween(
                                durationMillis = ArrivalSheetDismissAnimationDurationMillis,
                                easing = FastOutSlowInEasing,
                            )

                        else ->
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            )
                    },
                finishedListener = { offsetPx ->
                    if (isDismissAnimating && maxSheetOffsetPx > 0f && offsetPx >= maxSheetOffsetPx) {
                        isDismissAnimating = false
                        shouldRenderSheet = false
                        sheetOffsetPx = 0f
                    }
                },
                label = "arrivalEvaluationSheetOffset",
            )
        val dragState =
            rememberDraggableState { delta ->
                isDragging = true
                sheetOffsetPx = (sheetOffsetPx + delta).coerceIn(0f, maxSheetOffsetPx)
            }

        fun requestDismiss() {
            if (isDismissAnimating) return
            isDragging = false
            if (maxSheetOffsetPx <= 0f) {
                onAction(ArrivalUiAction.EvaluationSheetDismissed)
                shouldRenderSheet = false
                return
            }
            isDismissAnimating = true
            onAction(ArrivalUiAction.EvaluationSheetDismissed)
        }

        LaunchedEffect(uiState.isEvaluationSheetVisible, maxSheetOffsetPx) {
            if (uiState.isEvaluationSheetVisible) {
                shouldRenderSheet = true
                isDismissAnimating = false
                sheetOffsetPx = sheetOffsetPx.coerceIn(0f, maxSheetOffsetPx)
            } else if (!isDismissAnimating) {
                isDragging = false
                shouldRenderSheet = false
                sheetOffsetPx = 0f
            }
        }

        AnimatedVisibility(
            visible = isSheetVisible,
            enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight }) + fadeIn(),
            exit = fadeOut(),
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
                            contentDescription = closeSheetLabel
                        }
                        .clickable(
                            interactionSource = handleInteractionSource,
                            indication = null,
                            onClick = {
                                requestDismiss()
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
                                    requestDismiss()
                                } else {
                                    sheetOffsetPx = 0f
                                }
                            },
                        ),
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ArrivalEvaluationSectionSpacing),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(id = R.string.arrival_evaluation_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(onClick = ::requestDismiss) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_action_close),
                                contentDescription = closeSheetLabel,
                                tint = ArrivalCloseButtonTint,
                            )
                        }
                    }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = ArrivalRatingRowVerticalPadding),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(ArrivalRatingCount) { index ->
                            val rating = index + 1
                            val isSelected = rating <= uiState.selectedRating
                            IconButton(
                                onClick = { onAction(ArrivalUiAction.RatingSelected(rating)) },
                                modifier = Modifier.size(ArrivalRatingButtonSize),
                            ) {
                                Icon(
                                    painter =
                                        painterResource(
                                            id =
                                                if (isSelected) {
                                                    R.drawable.ic_rating_star_filled
                                                } else {
                                                    R.drawable.ic_action_favorite
                                                },
                                        ),
                                    contentDescription =
                                        stringResource(
                                            id = R.string.arrival_evaluation_star_content_description,
                                            rating,
                                        ),
                                    tint =
                                        if (isSelected) {
                                            ArrivalRatingSelectedColor
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                    modifier = Modifier.size(ArrivalRatingIconSize),
                                )
                            }
                        }
                    }

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
                    ) {
                        val routeSaveContainerColor =
                            if (uiState.isRouteSaveSelected) {
                                EumPrimary600
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        val routeSaveContentColor =
                            if (uiState.isRouteSaveSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                EumPrimary600
                            }
                        val routeSaveBorderColor =
                            EumPrimary600.copy(alpha = if (uiState.isRouteSaveEnabled) 1f else 0.38f)
                        Button(
                            onClick = { onAction(ArrivalUiAction.SaveRouteClicked) },
                            enabled = uiState.isRouteSaveEnabled,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(EumRadius.medium),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = routeSaveContainerColor,
                                    contentColor = routeSaveContentColor,
                                    disabledContainerColor = routeSaveContainerColor.copy(alpha = 0.38f),
                                    disabledContentColor = routeSaveContentColor.copy(alpha = 0.38f),
                                ),
                            border = BorderStroke(1.dp, routeSaveBorderColor),
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        id =
                                            if (uiState.isRouteSaveSelected) {
                                                R.string.arrival_evaluation_route_saved
                                            } else {
                                                R.string.arrival_evaluation_save_route
                                            },
                                    ),
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }

                        Button(
                            onClick = { onAction(ArrivalUiAction.SubmitEvaluationClicked) },
                            enabled = uiState.isEvaluationSubmitEnabled,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(EumRadius.medium),
                        ) {
                            Text(
                                text = stringResource(id = R.string.arrival_evaluation_submit),
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
    }
}
