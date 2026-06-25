package com.ssafy.e102.domain.auth.social.verifier;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.e102.domain.auth.exception.AuthErrorCode;
import com.ssafy.e102.domain.auth.exception.AuthException;
import com.ssafy.e102.domain.auth.social.config.SocialProviderProperties;
import com.ssafy.e102.domain.user.type.SocialProvider;

@Component
public class NaverSocialTokenVerifier extends AbstractSocialTokenVerifier {

	public NaverSocialTokenVerifier(RestTemplate restTemplate, SocialProviderProperties.Naver properties) {
		super(restTemplate, properties.userInfoUri(), SocialProvider.NAVER);
	}

	@Override
	protected String extractSocialProviderUserId(JsonNode body) {
		if (body == null || !body.hasNonNull("response")) {
			throw new AuthException(AuthErrorCode.SOCIAL_PROVIDER_API_FAILED,
				"소셜 제공자 응답에서 사용자 식별자를 찾을 수 없습니다.");
		}
		return requireText(body.get("response"), "id");
	}
}
