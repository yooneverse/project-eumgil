package com.example.llmtest.network.models

data class VoiceAnalyzeResponse(
    val success: Boolean,
    val intent: String?,
    val placeName: String?,
    val confirmed: Boolean?,
    val confirmationMessage: String?,
    val model: String?,
    val mode: String?,
    val latency_ms: Int?,
    val error: String?
)
