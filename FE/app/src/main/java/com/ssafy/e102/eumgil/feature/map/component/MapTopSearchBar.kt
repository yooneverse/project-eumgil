package com.ssafy.e102.eumgil.feature.map.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius

@Composable
fun MapTopSearchBar(
    title: String,
    subtitle: String?,
    accessibilityLabel: String,
    voiceInputAccessibilityLabel: String,
    onClick: () -> Unit,
    onVoiceInputClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(EumRadius.scaleS),
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button,
                            onClickLabel = accessibilityLabel,
                            onClick = onClick,
                        ).semantics {
                            contentDescription = accessibilityLabel
                        },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_nav_search),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            color =
                                if (subtitle == null) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            maxLines = 1,
                        )
                        subtitle?.let { supportingText ->
                            Text(
                                text = supportingText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Box(
                modifier =
                    Modifier
                        .padding(start = 12.dp)
                        .defaultMinSize(minWidth = 32.dp, minHeight = 32.dp)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = voiceInputAccessibilityLabel,
                            onClick = onVoiceInputClick,
                        )
                        .semantics {
                            contentDescription = voiceInputAccessibilityLabel
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_search_voice_mic),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
