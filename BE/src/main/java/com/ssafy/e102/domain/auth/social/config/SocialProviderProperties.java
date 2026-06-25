package com.ssafy.e102.domain.auth.social.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties(prefix = "social.provider")
public record SocialProviderProperties(Kakao kakao, Naver naver, Google google) {

	public SocialProviderProperties {
		Assert.notNull(kakao, "카카오 소셜 제공자 설정은 필수입니다.");
		Assert.notNull(naver, "네이버 소셜 제공자 설정은 필수입니다.");
		Assert.notNull(google, "구글 소셜 제공자 설정은 필수입니다.");
	}

	public record Kakao(String userInfoUri) {

		public Kakao {
			Assert.hasText(userInfoUri, "카카오 사용자 정보 조회 URI는 필수입니다.");
		}
	}

	public record Naver(String userInfoUri) {

		public Naver {
			Assert.hasText(userInfoUri, "네이버 사용자 정보 조회 URI는 필수입니다.");
		}
	}

	public record Google(String userInfoUri) {

		public Google {
			Assert.hasText(userInfoUri, "구글 사용자 정보 조회 URI는 필수입니다.");
		}
	}
}
