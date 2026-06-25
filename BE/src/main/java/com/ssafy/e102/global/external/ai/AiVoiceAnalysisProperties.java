package com.ssafy.e102.global.external.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties(prefix = "external.ai")
public record AiVoiceAnalysisProperties(String baseUrl) {

	private static final String VOICE_ANALYZE_PATH = "/voice/analyze";

	public AiVoiceAnalysisProperties {
		Assert.hasText(baseUrl, "AI 서버 기본 URL은 필수입니다.");
	}

	public String voiceAnalyzeUri() {
		return baseUrl.replaceAll("/+$", "") + VOICE_ANALYZE_PATH;
	}
}
