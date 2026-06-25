package com.ssafy.e102.global.external.ai;

import com.ssafy.e102.domain.place.type.VoiceIntent;

public record AiVoiceAnalyzeResult(
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
}
