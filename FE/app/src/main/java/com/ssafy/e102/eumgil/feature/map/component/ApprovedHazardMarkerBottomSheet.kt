package com.ssafy.e102.eumgil.feature.map.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing
import com.ssafy.e102.eumgil.data.repository.ApprovedHazardMarker
import com.ssafy.e102.eumgil.feature.report.displayLabel
import com.ssafy.e102.eumgil.feature.report.toReportTypeOrNull
import kotlin.math.roundToInt

@Composable
internal fun ApprovedHazardMarkerBottomSheet(
    marker: ApprovedHazardMarker?,
    onDismiss: () -> Unit = {},
    bottomInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    var viewerState by remember { mutableStateOf(ApprovedHazardMarkerImageViewerState()) }
    val density = LocalDensity.current
    val dragSettleVelocityThresholdPx = with(density) { 320.dp.toPx() }
    val dismissThresholdMinPx = with(density) { 72.dp.toPx() }
    val handleInteractionSource = remember { MutableInteractionSource() }
    LaunchedEffect(marker?.reportId) {
        if (marker == null) {
            viewerState = viewerState.close()
        }
    }
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .zIndex(ApprovedHazardMarkerOverlayZIndex),
    ) {
        val sheetMaxHeight = maxHeight * 0.72f
        var sheetHeightPx by remember(marker?.reportId) { mutableIntStateOf(0) }
        var sheetOffsetPx by remember(marker?.reportId) { mutableFloatStateOf(0f) }
        var isDragging by remember(marker?.reportId) { mutableStateOf(false) }
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
            label = "approvedHazardSheetOffset",
        )
        val dragState =
            rememberDraggableState { delta ->
                isDragging = true
                sheetOffsetPx = (sheetOffsetPx + delta).coerceIn(0f, maxSheetOffsetPx)
            }
        AnimatedVisibility(
            visible = marker != null,
            enter = slideInVertically { fullHeight -> fullHeight } + fadeIn(),
            exit = slideOutVertically { fullHeight -> fullHeight } + fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(bottom = bottomInset),
        ) {
            val resolvedMarker = marker ?: return@AnimatedVisibility
            MapBottomSheetSurface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = sheetMaxHeight)
                        .onSizeChanged { size ->
                            sheetHeightPx = size.height
                            sheetOffsetPx = sheetOffsetPx.coerceIn(0f, maxSheetOffsetPx)
                        }.offset { IntOffset(x = 0, y = animatedSheetOffsetPx.roundToInt()) },
                handleModifier =
                    Modifier
                        .height(MapBottomSheetHandleHeight)
                        .clickable(
                            interactionSource = handleInteractionSource,
                            indication = null,
                            onClick = onDismiss,
                        ).draggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity ->
                                isDragging = false
                                if (
                                    velocity >= dragSettleVelocityThresholdPx ||
                                    sheetOffsetPx >= dismissThresholdPx
                                ) {
                                    onDismiss()
                                }
                                sheetOffsetPx = 0f
                            },
                        ),
                edgeTreatment = MapBottomSheetEdgeTreatment.AttachedToBottomBar,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
                ) {
                    val thumbnailGallery = resolvedMarker.thumbnailUrls.ifEmpty { resolvedMarker.imageUrls }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = RoundedCornerShape(EumRadius.scaleM),
                                color = Color(0xFFFFFFFF),
                                border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                            ) {
                                Box(
                                    modifier = Modifier.testTag("approvedHazardHeaderWarningIcon"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_approved_hazard_warning),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = stringResource(id = R.string.approved_hazard_marker_sheet_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    text = resolvedMarker.reportTypeLabel,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    resolvedMarker.description?.takeIf { description -> description.isNotBlank() }?.let { description ->
                        Text(
                            text = description,
                            modifier = Modifier.testTag("approvedHazardDescription"),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (resolvedMarker.imageUrls.isEmpty()) {
                        HazardMarkerEmptyPhotoPlaceholder()
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
                        ) {
                            itemsIndexed(
                                items = thumbnailGallery,
                                key = { index, imageUrl -> "$index-$imageUrl" },
                            ) { index, imageUrl ->
                                HazardMarkerPhotoCard(
                                    imageUrl = imageUrl,
                                    index = index,
                                    onClick = {
                                        viewerState = viewerState.open(
                                            imageUrls = resolvedMarker.imageUrls,
                                            initialIndex = index,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        ApprovedHazardMarkerImageViewer(
            state = viewerState,
            onDismiss = { viewerState = viewerState.close() },
        )
    }
}

@Composable
private fun HazardMarkerEmptyPhotoPlaceholder() {
    Surface(
        modifier =
            Modifier
                .size(136.dp)
                .testTag("approvedHazardNoImagePlaceholder"),
        shape = RoundedCornerShape(EumRadius.scaleM),
        color = Color(0xFFFFFFFF),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CameraUnavailablePlaceholderIcon(
                modifier = Modifier.size(52.dp),
            )
        }
    }
}

@Composable
private fun CameraUnavailablePlaceholderIcon(
    modifier: Modifier = Modifier,
) {
    val mutedColor = Color(0xFFD9D9D9)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = size.minDimension * 0.08f
            val radius = (size.minDimension / 2f) - strokeWidth
            drawCircle(
                color = mutedColor,
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth),
            )
            drawLine(
                color = mutedColor,
                start = Offset(size.width * 0.24f, size.height * 0.24f),
                end = Offset(size.width * 0.76f, size.height * 0.76f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_permission_camera),
            contentDescription = null,
            tint = mutedColor,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun HazardMarkerPhotoCard(
    imageUrl: String,
    index: Int,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val thumbnailSizePx = with(LocalDensity.current) { 136.dp.roundToPx() }
    Surface(
        modifier =
            Modifier
                .size(136.dp)
                .testTag("approvedHazardThumbnail-$index")
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(EumRadius.scaleM),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        SubcomposeAsyncImage(
            model =
                ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(thumbnailSizePx)
                    .crossfade(false)
                    .build(),
            contentDescription = stringResource(id = R.string.approved_hazard_marker_thumbnail_content_description, index + 1),
            modifier = Modifier.fillMaxSize().aspectRatio(1f),
            contentScale = ContentScale.Crop,
            loading = {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(id = R.string.approved_hazard_marker_image_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            error = {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(id = R.string.approved_hazard_marker_image_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ApprovedHazardMarkerImageViewer(
    state: ApprovedHazardMarkerImageViewerState,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = state.isVisible && state.imageUrls.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        val pagerState = rememberPagerState(initialPage = state.selectedIndex) { state.imageUrls.size }
        LaunchedEffect(state.selectedIndex, state.imageUrls, state.isVisible) {
            if (state.isVisible && state.imageUrls.isNotEmpty()) {
                pagerState.scrollToPage(state.selectedIndex.coerceIn(0, state.imageUrls.lastIndex))
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f)),
        ) {
            val highlightedImageUrl =
                state.imageUrls.getOrNull(
                    pagerState.currentPage.coerceIn(0, state.imageUrls.lastIndex),
                )
            if (highlightedImageUrl != null) {
                SubcomposeAsyncImage(
                    model =
                        ImageRequest.Builder(LocalContext.current)
                            .data(highlightedImageUrl)
                            .crossfade(true)
                            .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(28.dp),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.58f)),
                )
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .testTag("approvedHazardViewerBackdrop")
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
            )

            IconButton(
                onClick = onDismiss,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .testTag("approvedHazardViewerClose")
                        .padding(EumSpacing.medium),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_action_close),
                    contentDescription = stringResource(id = R.string.approved_hazard_marker_viewer_close),
                    tint = ApprovedHazardMarkerViewerForegroundColor,
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = EumSpacing.medium, vertical = 72.dp),
            ) { page ->
                SubcomposeAsyncImage(
                    model =
                        ImageRequest.Builder(LocalContext.current)
                            .data(state.imageUrls[page])
                            .crossfade(true)
                            .build(),
                    contentDescription = stringResource(id = R.string.approved_hazard_marker_thumbnail_content_description, page + 1),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    loading = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(id = R.string.approved_hazard_marker_image_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = ApprovedHazardMarkerViewerForegroundColor,
                            )
                        }
                    },
                    error = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(id = R.string.approved_hazard_marker_image_error),
                                style = MaterialTheme.typography.bodyMedium,
                                color = ApprovedHazardMarkerViewerForegroundColor,
                            )
                        }
                    },
                )
            }

            Text(
                text = "${pagerState.currentPage + 1} / ${state.imageUrls.size}",
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .testTag("approvedHazardViewerIndicator")
                        .padding(bottom = 32.dp),
                style = MaterialTheme.typography.labelLarge,
                color = ApprovedHazardMarkerViewerForegroundColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

internal data class ApprovedHazardMarkerImageViewerState(
    val imageUrls: List<String> = emptyList(),
    val selectedIndex: Int = 0,
    val isVisible: Boolean = false,
) {
    fun open(
        imageUrls: List<String>,
        initialIndex: Int,
    ): ApprovedHazardMarkerImageViewerState {
        if (imageUrls.isEmpty()) return copy(imageUrls = emptyList(), selectedIndex = 0, isVisible = false)
        return copy(
            imageUrls = imageUrls,
            selectedIndex = initialIndex.coerceIn(0, imageUrls.lastIndex),
            isVisible = true,
        )
    }

    fun close(): ApprovedHazardMarkerImageViewerState = copy(isVisible = false)
}

private val ApprovedHazardMarkerViewerForegroundColor = Color.White
private const val ApprovedHazardMarkerOverlayZIndex = 10f

private val ApprovedHazardMarker.reportTypeLabel: String
    get() = reportType.toReportTypeOrNull()?.displayLabel ?: reportType
