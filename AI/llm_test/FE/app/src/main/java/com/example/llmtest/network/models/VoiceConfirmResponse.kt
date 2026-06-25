package com.example.llmtest.network.models

data class VoiceConfirmResponse(
    val success: Boolean,
    val confirmed: Boolean,
    val message: String,
    val model: String?,
    val latency_ms: Int?,
    val error: String?
)
