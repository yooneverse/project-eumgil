package com.ssafy.e102.eumgil.data.mock.datasource

import com.ssafy.e102.eumgil.data.remote.datasource.VoiceAnalyzeRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.dto.VoiceAnalyzeHistoryDto
import com.ssafy.e102.eumgil.data.remote.dto.VoiceAnalyzeResponseDto

class MockVoiceAnalyzeRemoteDataSource : VoiceAnalyzeRemoteDataSource {
    override suspend fun analyze(
        text: String,
        mode: String,
        history: List<VoiceAnalyzeHistoryDto>,
        currentRoute: String?,
    ): VoiceAnalyzeResponseDto =
        when (mode) {
            "MOBILITY_IMPAIRED" ->
                VoiceAnalyzeResponseDto(
                    intent = "PLACE_SEARCH",
                    placeName = "테스트장소",
                    category = null,
                    bookmarkAction = null,
                    departure = null,
                    destination = null,
                    reportType = null,
                    description = null,
                    confirmed = null,
                    confirmationMessage = null,
                )
            "LOW_VISION" ->
                VoiceAnalyzeResponseDto(
                    intent = "PLACE_SEARCH",
                    placeName = "테스트장소",
                    category = null,
                    bookmarkAction = null,
                    departure = null,
                    destination = null,
                    reportType = null,
                    description = null,
                    confirmed = null,
                    confirmationMessage = "테스트장소를 찾으시나요?",
                )
            else ->
                VoiceAnalyzeResponseDto(
                    intent = "UNKNOWN",
                    placeName = null,
                    category = null,
                    bookmarkAction = null,
                    departure = null,
                    destination = null,
                    reportType = null,
                    description = null,
                    confirmed = null,
                    confirmationMessage = null,
                )
        }
}
