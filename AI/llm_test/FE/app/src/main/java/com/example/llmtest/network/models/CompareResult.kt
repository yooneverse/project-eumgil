package com.example.llmtest.network.models

data class CompareResult(
    val provider: String,
    val raw_text: String,
    val departure: String?,
    val destination: String?,
    val intent: String,
    val llm_latency_ms: Double,
    val total_latency_ms: Double,
    val input_tokens: Int,
    val output_tokens: Int,
    val cost_credit: Double,
    val success: Boolean,
    val error: String?,
    val confirmation_message: String?
)
