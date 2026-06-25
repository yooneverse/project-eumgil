package com.ssafy.e102.global.test.auth;

public record AuthTestConfigResponse(
	String kakaoJavaScriptKey,
	String naverClientId,
	String googleClientId) {
}
