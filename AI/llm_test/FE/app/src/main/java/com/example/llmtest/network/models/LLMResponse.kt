package com.example.llmtest.network.models

data class LLMResponse(
    val response: String,
    val action: String,
    val departure: String?,
    val destination: String?,
    val inference_time: Float,
    val model_name: String,
    val transcribed_text: String? = null,
    val next_state: String = "initial",
    val confirmed: Boolean = false
)
