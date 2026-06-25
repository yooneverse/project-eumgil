package com.ssafy.e102.eumgil.feature.onboarding

import androidx.compose.ui.graphics.Color

data class PrimaryUserTypeButtonStyle(
    val containerColor: Color,
    val borderColor: Color,
    val titleColor: Color,
    val descriptionColor: Color,
    val iconContainerColor: Color,
    val iconTint: Color,
)

fun primaryUserTypeButtonStyleFor(primaryUserType: PrimaryUserType): PrimaryUserTypeButtonStyle =
    when (primaryUserType) {
        PrimaryUserType.LOW_VISION ->
            PrimaryUserTypeButtonStyle(
                containerColor = Color.Black,
                borderColor = Color(0xFFFFCC00),
                titleColor = Color(0xFFFFCC00),
                descriptionColor = Color.White,
                iconContainerColor = Color(0xFFFFCC00),
                iconTint = Color.Black,
            )

        PrimaryUserType.MOBILITY_IMPAIRED ->
            PrimaryUserTypeButtonStyle(
                containerColor = Color.White,
                borderColor = Color(0xFFE5E7EB),
                titleColor = Color(0xFF0047FF),
                descriptionColor = Color(0xFF374151),
                iconContainerColor = Color(0xFFEFF6FF),
                iconTint = Color(0xFF0047FF),
            )
    }
