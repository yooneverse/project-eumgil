package com.example.llmtest.network.models

data class LLMRequest(
    val text: String,
    val model: String,
    val stt_start_ms: Long
)
