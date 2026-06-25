package com.example.llmtest.network

import com.example.llmtest.network.models.CompareResult
import com.example.llmtest.network.models.LLMRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("/api/chat/llm")
    suspend fun chatWithLLM(@Body request: LLMRequest): CompareResult
}
