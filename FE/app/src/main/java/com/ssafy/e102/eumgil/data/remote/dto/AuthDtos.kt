package com.ssafy.e102.eumgil.data.remote.dto

data class SocialLoginResponseDto(
    val signupRequired: Boolean,
    val signupToken: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val userId: String?,
    val selectedPrimaryUserType: String?,
    val selectedMobilitySubtype: String?,
)

data class SignupResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val selectedPrimaryUserType: String,
    val selectedMobilitySubtype: String?,
)

data class ReissueRequestDto(
    val refreshToken: String,
)

data class ReissueResponseDto(
    val accessToken: String,
    val refreshToken: String,
)
