package com.example.llmtest.network.models

data class VoiceConfirmRequest(
    val text: String,
    val model: String = "gemini"
)
