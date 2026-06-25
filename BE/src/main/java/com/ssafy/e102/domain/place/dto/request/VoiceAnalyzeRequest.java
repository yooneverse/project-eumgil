package com.ssafy.e102.domain.place.dto.request;

import java.util.List;

import com.ssafy.e102.domain.place.type.VoiceAnalysisMode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VoiceAnalyzeRequest(
	@NotBlank
	String text,
	@NotNull
	VoiceAnalysisMode mode,
	List<@Valid VoiceAnalyzeHistoryRequest> history,
	String currentRoute) {
}
