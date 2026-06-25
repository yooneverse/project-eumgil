package com.ssafy.e102.eumgil.data.remote.dto

data class VoiceAnalyzeHistoryDto(
    val role: String,
    val content: String,
)

data class VoiceAnalyzeRequestDto(
    val text: String,
    val mode: String,
    val history: List<VoiceAnalyzeHistoryDto>,
)
