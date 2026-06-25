package com.ssafy.e102.global.external.kakao;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties(prefix = "external.kakao.local")
public record KakaoLocalProperties(
	String baseUrl,
	String apiKey) {

	public KakaoLocalProperties {
		Assert.hasText(baseUrl, "카카오 로컬 API 기본 URL은 필수입니다.");
	}
}
