package com.ssafy.e102.eumgil.data.remote.dto

data class UserMeResponseDto(
    val userId: String?,
    val socialProvider: String?,
    val selectedPrimaryUserType: String?,
    val selectedMobilitySubtype: String?,
)
