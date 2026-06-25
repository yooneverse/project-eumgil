package com.ssafy.e102.domain.auth.dto.response;

public record TokenResponse(
	String accessToken,
	String refreshToken) {
}
