package com.ssafy.e102.eumgil.core.designsystem.component.map

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing

data class EumMapFloatingActionButtonState(
    val contentDescription: String,
    @DrawableRes val iconRes: Int? = null,
    val label: String? = null,
    val enabled: Boolean = true,
    val tint: Color,
    val iconSize: Dp = MAP_FLOATING_ACTION_ICON_SIZE,
)

@Composable
fun EumMapFloatingControls(
    actionButtonState: EumMapFloatingActionButtonState,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    topActionButtonState: EumMapFloatingActionButtonState? = null,
    onTopActionClick: () -> Unit = {},
    onZoomInClick: () -> Unit = {},
    onZoomOutClick: () -> Unit = {},
    zoomInLabel: String = "+",
    zoomOutLabel: String = "-",
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(EumSpacing.xxSmall),
        horizontalAlignment = Alignment.End,
    ) {
        topActionButtonState?.let { state ->
            EumMapFloatingActionButton(
                state = state,
                onClick = onTopActionClick,
            )
        }

        Surface(
            modifier = Modifier.width(48.dp),
            shape = RoundedCornerShape(EumRadius.scaleS),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
            shadowElevation = 6.dp,
        ) {
            Column {
                EumMapZoomControlButton(
                    label = zoomInLabel,
                    onClick = onZoomInClick,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                )
                EumMapZoomControlButton(
                    label = zoomOutLabel,
                    onClick = onZoomOutClick,
                )
            }
        }

        EumMapFloatingActionButton(
            state = actionButtonState,
            onClick = onActionClick,
        )
    }
}

private val MAP_FLOATING_ACTION_ICON_SIZE = 18.dp

@Composable
private fun EumMapFloatingActionButton(
    state: EumMapFloatingActionButtonState,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = state.enabled,
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(EumRadius.scaleS),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
        shadowElevation = 6.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (state.iconRes != null) {
                Icon(
                    painter = painterResource(id = state.iconRes),
                    contentDescription = state.contentDescription,
                    modifier = Modifier.size(state.iconSize),
                    tint = state.tint,
                )
            } else {
                Text(
                    text = state.label.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = state.tint,
                )
            }
        }
    }
}

@Composable
private fun EumMapZoomControlButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable(
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 24.sp,
                ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
