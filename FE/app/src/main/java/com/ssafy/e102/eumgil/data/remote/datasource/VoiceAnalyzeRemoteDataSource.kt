package com.ssafy.e102.eumgil.data.remote.datasource

import com.ssafy.e102.eumgil.data.remote.dto.VoiceAnalyzeHistoryDto
import com.ssafy.e102.eumgil.data.remote.dto.VoiceAnalyzeResponseDto

interface VoiceAnalyzeRemoteDataSource {
    suspend fun analyze(
        text: String,
        mode: String,
        history: List<VoiceAnalyzeHistoryDto>,
        currentRoute: String? = null,
    ): VoiceAnalyzeResponseDto
}
