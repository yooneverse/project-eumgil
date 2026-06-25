package com.example.llmtest.network.models

data class AudioRequest(
    val model_name: String,
    val session_id: String,
    val conversation_state: String = "initial"
)
