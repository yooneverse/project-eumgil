package com.ssafy.e102.eumgil.core.designsystem.component.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ssafy.e102.eumgil.R

@Composable
fun EumCenteredTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    backContentDescription: String? = null,
    titleFontWeight: FontWeight? = null,
) {
    val spec = centeredTopBarLayoutSpec()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = spec.shadowElevationDp.dp,
        tonalElevation = spec.tonalElevationDp.dp,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .heightIn(min = spec.minHeightDp.dp)
                    .padding(horizontal = spec.horizontalPaddingDp.dp),
        ) {
            if (onBackClick != null && !backContentDescription.isNullOrBlank()) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_action_back),
                        contentDescription = backContentDescription,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Text(
                text = title,
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = titleFontWeight,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

internal data class CenteredTopBarLayoutSpec(
    val horizontalPaddingDp: Int,
    val minHeightDp: Int,
    val shadowElevationDp: Int,
    val tonalElevationDp: Int,
)

internal fun centeredTopBarLayoutSpec(): CenteredTopBarLayoutSpec =
    CenteredTopBarLayoutSpec(
        horizontalPaddingDp = 8,
        minHeightDp = 56,
        shadowElevationDp = 0,
        tonalElevationDp = 0,
    )
