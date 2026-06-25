package com.ssafy.e102.global.test.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.test")
public record AuthTestConfigProperties(
	boolean enabled,
	String kakaoJavaScriptKey,
	String naverClientId,
	String googleClientId) {
}
