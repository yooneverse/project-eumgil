package com.ssafy.e102.global.external.ai;

import java.util.List;

import com.ssafy.e102.domain.place.dto.request.VoiceAnalyzeHistoryRequest;
import com.ssafy.e102.domain.place.dto.request.VoiceAnalyzeRequest;
import com.ssafy.e102.domain.place.type.VoiceAnalysisMode;

public record AiVoiceAnalyzeCommand(
	String text,
	VoiceAnalysisMode mode,
	List<AiVoiceAnalyzeHistoryMessage> history,
	String currentRoute) {

	public static AiVoiceAnalyzeCommand from(VoiceAnalyzeRequest request) {
		return new AiVoiceAnalyzeCommand(
			request.text(),
			request.mode(),
			historyFrom(request.history()),
			request.currentRoute());
	}

	private static List<AiVoiceAnalyzeHistoryMessage> historyFrom(List<VoiceAnalyzeHistoryRequest> history) {
		if (history == null) {
			return List.of();
		}
		return history.stream()
			.map(message -> new AiVoiceAnalyzeHistoryMessage(message.role(), message.content()))
			.toList();
	}
}
