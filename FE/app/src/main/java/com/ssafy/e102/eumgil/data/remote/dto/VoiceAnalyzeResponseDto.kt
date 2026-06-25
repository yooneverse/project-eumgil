package com.ssafy.e102.eumgil.data.remote.dto

data class VoiceAnalyzeResponseDto(
    val intent: String,
    val placeName: String?,
    val category: String?,
    val bookmarkAction: String?,
    val departure: String?,
    val destination: String?,
    val reportType: String?,
    val description: String?,
    val confirmed: Boolean?,
    val confirmationMessage: String?,
)
