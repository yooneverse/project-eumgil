package com.ssafy.e102.eumgil.feature.onboarding.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing

enum class OnboardingSelectionCardStyle {
    Default,
    HighContrast,
}

@Composable
fun OnboardingSelectionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIconRes: Int? = null,
    style: OnboardingSelectionCardStyle = OnboardingSelectionCardStyle.Default,
) {
    val selectedState = stringResource(id = R.string.a11y_option_selected)
    val unselectedState = stringResource(id = R.string.a11y_option_unselected)
    val colors = selectionCardColors(style = style, selected = selected)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                stateDescription =
                    if (selected) {
                        selectedState
                    } else {
                        unselectedState
                    }
            }
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        color = colors.containerColor,
        shape = RoundedCornerShape(EumRadius.scaleL),
        tonalElevation = if (selected) 3.dp else 0.dp,
        border = BorderStroke(width = colors.borderWidth, color = colors.borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(EumSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(EumSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIconRes?.let { iconRes ->
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = colors.iconContainerColor,
                            shape = CircleShape,
                        )
                        .border(
                            width = 1.dp,
                            color = colors.iconBorderColor,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = colors.iconTint,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(EumSpacing.xSmall),
            ) {
                Text(
                    text = title.stabilizeOnboardingWrap(),
                    style = MaterialTheme.typography.titleMedium.onboardingHeadingLineBreak(),
                    color = colors.titleColor,
                )
                Text(
                    text = description.stabilizeOnboardingWrap(),
                    style = MaterialTheme.typography.bodyLarge.onboardingBodyLineBreak(),
                    color = colors.descriptionColor,
                )
            }

            Icon(
                painter = painterResource(id = if (selected) R.drawable.ic_user_selected else R.drawable.ic_user_check),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint =
                    if (selected) {
                        colors.indicatorFillColor
                    } else {
                        colors.indicatorBorderColor
                    },
            )
        }
    }
}

private data class OnboardingSelectionCardColors(
    val containerColor: Color,
    val borderColor: Color,
    val borderWidth: Dp,
    val titleColor: Color,
    val descriptionColor: Color,
    val iconTint: Color,
    val iconContainerColor: Color,
    val iconBorderColor: Color,
    val indicatorBorderColor: Color,
    val indicatorFillColor: Color,
)

@Composable
private fun selectionCardColors(
    style: OnboardingSelectionCardStyle,
    selected: Boolean,
): OnboardingSelectionCardColors =
    when (style) {
        OnboardingSelectionCardStyle.Default -> {
            val borderColor =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                }

            OnboardingSelectionCardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                borderColor = borderColor,
                borderWidth = if (selected) 2.dp else 1.dp,
                titleColor = MaterialTheme.colorScheme.onSurface,
                descriptionColor = MaterialTheme.colorScheme.onSurfaceVariant,
                iconTint =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    },
                iconContainerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                iconBorderColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                    } else {
                        Color.Transparent
                    },
                indicatorBorderColor = borderColor,
                indicatorFillColor = MaterialTheme.colorScheme.primary,
            )
        }

        OnboardingSelectionCardStyle.HighContrast -> {
            val accentColor = MaterialTheme.colorScheme.secondary

            OnboardingSelectionCardColors(
                containerColor = MaterialTheme.colorScheme.onSurface,
                borderColor = accentColor,
                borderWidth = if (selected) 3.dp else 2.dp,
                titleColor = accentColor,
                descriptionColor = MaterialTheme.colorScheme.surface,
                iconTint = accentColor,
                iconContainerColor = accentColor.copy(alpha = if (selected) 0.18f else 0.1f),
                iconBorderColor = accentColor.copy(alpha = 0.5f),
                indicatorBorderColor = accentColor,
                indicatorFillColor = accentColor,
            )
        }
    }
