package com.example.llmtest.network

import com.example.llmtest.BuildConfig
import com.example.llmtest.network.models.VoiceAnalyzeRequest
import com.example.llmtest.network.models.VoiceAnalyzeResponse
import com.example.llmtest.network.models.VoiceConfirmRequest
import com.example.llmtest.network.models.VoiceConfirmResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface VoiceApiService {
    @POST("/voice/analyze")
    suspend fun analyze(@Body request: VoiceAnalyzeRequest): VoiceAnalyzeResponse

    @POST("/api/voice/confirm")
    suspend fun confirm(@Body request: VoiceConfirmRequest): VoiceConfirmResponse
}

object VoiceApiClient {
    private val BASE_URL: String get() = BuildConfig.SERVER_URL

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    val service: VoiceApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(VoiceApiService::class.java)
}
