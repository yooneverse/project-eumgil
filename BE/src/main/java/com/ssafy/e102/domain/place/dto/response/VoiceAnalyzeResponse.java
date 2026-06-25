package com.ssafy.e102.domain.place.dto.response;

import com.ssafy.e102.domain.place.type.VoiceIntent;
import com.ssafy.e102.global.external.ai.AiVoiceAnalyzeResult;

public record VoiceAnalyzeResponse(
	VoiceIntent intent,
	String placeName,
	String category,
	String bookmarkAction,
	String departure,
	String destination,
	String reportType,
	String description,
	Boolean confirmed,
	String confirmationMessage) {

	public static VoiceAnalyzeResponse of(AiVoiceAnalyzeResult result, Boolean confirmed,
		String confirmationMessage) {
		return new VoiceAnalyzeResponse(
			result.intent(),
			placeNameOrNull(result),
			result.category(),
			result.bookmarkAction(),
			result.departure(),
			result.destination(),
			result.reportType(),
			result.description(),
			confirmed,
			confirmationMessage);
	}

	private static String placeNameOrNull(AiVoiceAnalyzeResult result) {
		if (result.intent() == VoiceIntent.UNKNOWN) {
			return null;
		}
		return result.placeName();
	}
}
