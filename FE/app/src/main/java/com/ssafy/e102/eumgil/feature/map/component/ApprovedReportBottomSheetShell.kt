package com.ssafy.e102.eumgil.feature.map.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing
import com.ssafy.e102.eumgil.feature.map.model.ApprovedReportSheetState
import java.util.Locale

@Composable
fun ApprovedReportBottomSheetShell(
    state: ApprovedReportSheetState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val report = state.report

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val sheetMaxHeight = maxHeight * 0.82f
        AnimatedVisibility(
            visible = state.isVisible && report != null,
            enter =
                slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                ) + fadeIn(animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)),
            exit =
                slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                ) + fadeOut(animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
        ) {
            if (report == null) return@AnimatedVisibility
            val photoUrl = report.imageUrls.firstOrNull { url -> url.isNotBlank() }

            MapBottomSheetSurface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = sheetMaxHeight),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(EumSpacing.xxSmall),
                        ) {
                            Text(
                                text = stringResource(id = R.string.map_approved_report_sheet_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = report.reportTypeLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        IconButton(onClick = onDismissRequest) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_action_close),
                                contentDescription = stringResource(id = R.string.map_approved_report_sheet_close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = sheetMaxHeight - 120.dp)
                                .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
                    ) {
                        ApprovedReportInfoBlock(
                            label = stringResource(id = R.string.map_approved_report_sheet_type_label),
                            value = report.reportTypeLabel,
                        )
                        ApprovedReportInfoBlock(
                            label = stringResource(id = R.string.map_approved_report_sheet_location_label),
                            value =
                                report.address
                                    ?.takeIf(String::isNotBlank)
                                    ?: approvedReportCoordinateText(
                                        latitude = report.coordinate.latitude,
                                        longitude = report.coordinate.longitude,
                                    ),
                        )
                        ApprovedReportInfoBlock(
                            label = stringResource(id = R.string.map_approved_report_sheet_description_label),
                            value =
                                report.description
                                    ?.takeIf(String::isNotBlank)
                                    ?: stringResource(id = R.string.map_approved_report_sheet_no_description),
                        )
                        ApprovedReportPhoto(
                            photoUrl = photoUrl,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(164.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovedReportInfoBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EumSpacing.xxSmall),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ApprovedReportPhoto(
    photoUrl: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(EumRadius.medium),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        if (photoUrl == null) {
            ApprovedReportPhotoFallback(
                text = stringResource(id = R.string.map_approved_report_sheet_no_photo),
            )
        } else {
            SubcomposeAsyncImage(
                model =
                    ImageRequest.Builder(context)
                        .data(photoUrl)
                        .crossfade(true)
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { ApprovedReportPhotoFallback(text = stringResource(id = R.string.map_approved_report_sheet_no_photo)) },
                error = { ApprovedReportPhotoFallback(text = stringResource(id = R.string.map_approved_report_sheet_no_photo)) },
            )
        }
    }
}

@Composable
private fun ApprovedReportPhotoFallback(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(EumSpacing.medium),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun approvedReportCoordinateText(
    latitude: Double,
    longitude: Double,
): String =
    String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
