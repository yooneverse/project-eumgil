package com.ssafy.e102.eumgil.feature.map.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.designsystem.component.map.EumMapFloatingActionButtonState
import com.ssafy.e102.eumgil.core.designsystem.component.map.EumMapFloatingControls
import com.ssafy.e102.eumgil.feature.map.MapRecenterButtonState

@Composable
fun MapFloatingControls(
    recenterButtonState: MapRecenterButtonState,
    isRecenterButtonActive: Boolean,
    onRecenterClick: () -> Unit,
    onZoomInClick: () -> Unit,
    onZoomOutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EumMapFloatingControls(
        actionButtonState = recenterButtonStyle(recenterButtonState, isRecenterButtonActive),
        onActionClick = onRecenterClick,
        onZoomInClick = onZoomInClick,
        onZoomOutClick = onZoomOutClick,
        modifier = modifier,
    )
}

@Composable
private fun recenterButtonStyle(
    state: MapRecenterButtonState,
    isRecenterButtonActive: Boolean,
): EumMapFloatingActionButtonState =
    when (state) {
        MapRecenterButtonState.REQUEST_PERMISSION ->
            EumMapFloatingActionButtonState(
                iconRes = R.drawable.ic_map_current_location_disabled,
                tint = Color.Unspecified,
                contentDescription = stringResource(id = R.string.map_location_action_request_permission),
                enabled = true,
            )

        MapRecenterButtonState.LOADING ->
            EumMapFloatingActionButtonState(
                iconRes = R.drawable.ic_map_current_location_loading,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = stringResource(id = R.string.map_location_action_loading),
                enabled = false,
            )

        MapRecenterButtonState.RETRY ->
            EumMapFloatingActionButtonState(
                iconRes = R.drawable.ic_map_current_location_retry,
                tint = MaterialTheme.colorScheme.onSurface,
                contentDescription = stringResource(id = R.string.map_location_action_retry),
                enabled = true,
            )

        MapRecenterButtonState.DISABLED ->
            EumMapFloatingActionButtonState(
                iconRes = R.drawable.ic_map_current_location_disabled,
                tint = Color.Unspecified,
                contentDescription = stringResource(id = R.string.map_location_action_disabled),
                enabled = false,
            )

        MapRecenterButtonState.ENABLED ->
            if (isRecenterButtonActive) {
                EumMapFloatingActionButtonState(
                    iconRes = R.drawable.ic_route_start_navigation_button,
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = stringResource(id = R.string.map_location_action_recenter),
                    enabled = true,
                )
            } else {
                EumMapFloatingActionButtonState(
                    iconRes = R.drawable.ic_map_current_location_disabled,
                    tint = Color.Unspecified,
                    contentDescription = stringResource(id = R.string.map_location_action_recenter),
                    enabled = true,
                )
            }
    }
