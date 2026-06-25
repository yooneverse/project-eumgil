package com.ssafy.e102.domain.auth.social.verifier;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ssafy.e102.domain.auth.dto.SocialUserInfo;
import com.ssafy.e102.domain.user.type.SocialProvider;

class CompositeSocialTokenVerifierTest {

	@Test
	@DisplayName("소셜 제공자에 맞는 verifier를 선택한다")
	void getVerifierByProvider() {
		SocialTokenVerifier kakaoVerifier = new StubSocialTokenVerifier(SocialProvider.KAKAO);
		SocialTokenVerifier googleVerifier = new StubSocialTokenVerifier(SocialProvider.GOOGLE);
		CompositeSocialTokenVerifier composite = new CompositeSocialTokenVerifier(
			List.of(kakaoVerifier, googleVerifier));

		SocialUserInfo userInfo = composite.verify(SocialProvider.GOOGLE, "google-token");

		assertThat(userInfo.socialProvider()).isEqualTo(SocialProvider.GOOGLE);
		assertThat(userInfo.socialProviderUserId()).isEqualTo("GOOGLE-user");
	}

	private record StubSocialTokenVerifier(SocialProvider provider) implements SocialTokenVerifier {

		@Override
		public SocialProvider getProvider() {
			return provider;
		}

		@Override
		public SocialUserInfo verify(String socialAccessToken) {
			return new SocialUserInfo(provider, provider.name() + "-user");
		}
	}
}
