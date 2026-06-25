package com.ssafy.e102.eumgil.data.remote.dto

data class UserTypeResponseDto(
    val userId: String?,
    val selectedPrimaryUserType: String,
    val selectedMobilitySubtype: String?,
)
