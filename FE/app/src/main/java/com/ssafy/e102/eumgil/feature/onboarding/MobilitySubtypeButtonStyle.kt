package com.ssafy.e102.eumgil.feature.onboarding

import androidx.compose.ui.graphics.Color
import com.ssafy.e102.eumgil.core.designsystem.theme.EumPrimary600

data class MobilitySubtypeButtonStyle(
    val containerColor: Color,
    val selectedContainerColor: Color,
    val borderColor: Color,
    val titleColor: Color,
    val descriptionColor: Color,
    val iconTint: Color,
    val trailingTint: Color,
)

fun mobilitySubtypeButtonStyleFor(selected: Boolean): MobilitySubtypeButtonStyle =
    if (selected) {
        MobilitySubtypeButtonStyle(
            containerColor = Color.White,
            selectedContainerColor = Color(0xFFDBEAFE),
            borderColor = EumPrimary600,
            titleColor = EumPrimary600,
            descriptionColor = Color(0xFF374151),
            iconTint = EumPrimary600,
            trailingTint = EumPrimary600,
        )
    } else {
        MobilitySubtypeButtonStyle(
            containerColor = Color.White,
            selectedContainerColor = Color(0xFFDBEAFE),
            borderColor = Color(0xFFD1D5DB),
            titleColor = EumPrimary600,
            descriptionColor = Color(0xFF374151),
            iconTint = EumPrimary600,
            trailingTint = Color(0xFF6B7280),
        )
    }
