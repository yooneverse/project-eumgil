package com.ssafy.e102.domain.auth.dto;

import org.springframework.util.Assert;

import com.ssafy.e102.domain.user.type.SocialProvider;

public record SocialUserInfo(SocialProvider socialProvider, String socialProviderUserId) {

	public SocialUserInfo {
		Assert.notNull(socialProvider, "소셜 제공자는 필수입니다.");
		Assert.hasText(socialProviderUserId, "소셜 제공자 사용자 ID는 필수입니다.");
	}
}
