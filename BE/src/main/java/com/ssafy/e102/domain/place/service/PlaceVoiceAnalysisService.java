package com.ssafy.e102.domain.place.service;

import org.springframework.stereotype.Service;

import com.ssafy.e102.domain.place.dto.request.VoiceAnalyzeRequest;
import com.ssafy.e102.domain.place.dto.response.VoiceAnalyzeResponse;
import com.ssafy.e102.domain.place.exception.PlaceErrorCode;
import com.ssafy.e102.domain.place.exception.PlaceException;
import com.ssafy.e102.domain.place.type.VoiceAnalysisMode;
import com.ssafy.e102.domain.place.type.VoiceIntent;
import com.ssafy.e102.global.external.ai.AiVoiceAnalysisClient;
import com.ssafy.e102.global.external.ai.AiVoiceAnalyzeCommand;
import com.ssafy.e102.global.external.ai.AiVoiceAnalyzeResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlaceVoiceAnalysisService {

	private final AiVoiceAnalysisClient aiVoiceAnalysisClient;

	public VoiceAnalyzeResponse analyze(VoiceAnalyzeRequest request) {
		AiVoiceAnalyzeResult result = aiVoiceAnalysisClient.analyze(AiVoiceAnalyzeCommand.from(request));
		validateResult(request.mode(), result);
		return VoiceAnalyzeResponse.of(result, result.confirmed(), result.confirmationMessage());
	}

	private void validateResult(VoiceAnalysisMode mode, AiVoiceAnalyzeResult result) {
		if (result == null || result.intent() == null) {
			throw new PlaceException(PlaceErrorCode.VOICE_ANALYSIS_AI_FAILED);
		}
		switch (result.intent()) {
			case PLACE_SEARCH -> {
				requireNotBlank(result.placeName());
				requireLowVisionConfirmationMessage(mode, result);
			}
			case CATEGORY_SEARCH -> requireNotBlank(result.category());
			case BOOKMARK_ADD, BOOKMARK_DELETE -> {
				requireNotBlank(result.placeName());
				requireNotBlank(result.bookmarkAction());
			}
			case NAVIGATE -> requireNotBlank(result.destination());
			case REPORT -> requireNotBlank(result.reportType());
			default -> {}
		}
	}

	private void requireLowVisionConfirmationMessage(VoiceAnalysisMode mode, AiVoiceAnalyzeResult result) {
		if (mode == VoiceAnalysisMode.LOW_VISION && result.confirmed() == null) {
			requireNotBlank(result.confirmationMessage());
		}
	}

	private void requireNotBlank(String value) {
		if (value == null || value.isBlank()) {
			throw new PlaceException(PlaceErrorCode.VOICE_ANALYSIS_AI_FAILED);
		}
	}
}
