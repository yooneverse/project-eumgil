package com.ssafy.e102.domain.auth.dto.request;

import com.ssafy.e102.domain.user.type.SocialProvider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SocialLoginRequest(
	@NotNull(message = "소셜 제공자는 필수입니다.")
	SocialProvider socialProvider,

	@NotBlank(message = "소셜 액세스 토큰은 필수입니다.")
	String socialAccessToken) {
}
