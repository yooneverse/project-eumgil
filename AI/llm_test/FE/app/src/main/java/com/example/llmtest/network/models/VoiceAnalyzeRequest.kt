package com.example.llmtest.network.models

data class VoiceAnalyzeRequest(
    val text: String,
    val model: String = "gemini",
    val mode: String,
    val history: List<Map<String, String>> = emptyList()
)
