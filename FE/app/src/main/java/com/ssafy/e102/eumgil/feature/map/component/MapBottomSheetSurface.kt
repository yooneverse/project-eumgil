package com.ssafy.e102.eumgil.feature.map.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing

private val MapBottomSheetTopPadding = EumSpacing.small
private val MapBottomSheetHandleBottomSpacing = EumSpacing.small
internal val MapBottomSheetHandleHeight = 16.dp

enum class MapBottomSheetEdgeTreatment {
    Floating,
    AttachedToBottomBar,
}

@Composable
fun MapBottomSheetSurface(
    modifier: Modifier = Modifier,
    showHandle: Boolean = true,
    handleModifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    edgeTreatment: MapBottomSheetEdgeTreatment = MapBottomSheetEdgeTreatment.Floating,
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolvedContainerColor =
        if (containerColor == Color.Unspecified) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.995f)
        } else {
            containerColor
        }
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    val isAttachedToBottomBar = edgeTreatment == MapBottomSheetEdgeTreatment.AttachedToBottomBar

    Surface(
        modifier =
            if (isAttachedToBottomBar) {
                modifier.attachedBottomBarBorder(
                    color = borderColor,
                    strokeWidth = 1.dp,
                    topCornerRadius = EumRadius.large,
                )
            } else {
                modifier
            },
        shape =
            RoundedCornerShape(
                topStart = EumRadius.large,
                topEnd = EumRadius.large,
            ),
        color = resolvedContainerColor,
        border =
            if (isAttachedToBottomBar) {
                null
            } else {
                BorderStroke(1.dp, borderColor)
            },
        shadowElevation =
            if (isAttachedToBottomBar) {
                0.dp
            } else {
                12.dp
            },
    ) {
        Column(
            modifier =
                Modifier.padding(
                    start = EumSpacing.medium,
                    top = MapBottomSheetTopPadding,
                    end = EumSpacing.medium,
                    bottom = EumSpacing.medium,
                ),
        ) {
            if (showHandle) {
                Box(
                    modifier = Modifier.fillMaxWidth().then(handleModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(42.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(EumRadius.full))
                                .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                Spacer(modifier = Modifier.height(MapBottomSheetHandleBottomSpacing))
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
                content = content,
            )
        }
    }
}

private fun Modifier.attachedBottomBarBorder(
    color: Color,
    strokeWidth: Dp,
    topCornerRadius: Dp,
): Modifier =
    drawWithContent {
        drawContent()

        val strokePx = strokeWidth.toPx()
        val halfStroke = strokePx / 2f
        val radiusPx = topCornerRadius.toPx().coerceAtMost(size.width / 2f)
        val path =
            Path().apply {
                moveTo(halfStroke, size.height)
                lineTo(halfStroke, radiusPx)
                quadraticBezierTo(halfStroke, halfStroke, radiusPx, halfStroke)
                lineTo(size.width - radiusPx, halfStroke)
                quadraticBezierTo(size.width - halfStroke, halfStroke, size.width - halfStroke, radiusPx)
                lineTo(size.width - halfStroke, size.height)
            }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokePx),
        )
    }
