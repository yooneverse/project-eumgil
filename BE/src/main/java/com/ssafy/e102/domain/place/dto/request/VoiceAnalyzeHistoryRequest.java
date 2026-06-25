package com.ssafy.e102.domain.place.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VoiceAnalyzeHistoryRequest(
	@NotBlank @Pattern(regexp = "user|assistant", message = "user 또는 assistant만 허용됩니다.")
	String role,
	@NotBlank
	String content) {
}
