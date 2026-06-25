package com.ssafy.e102.domain.auth.social.verifier;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.e102.domain.auth.social.config.SocialProviderProperties;
import com.ssafy.e102.domain.user.type.SocialProvider;

@Component
public class KakaoSocialTokenVerifier extends AbstractSocialTokenVerifier {

	public KakaoSocialTokenVerifier(RestTemplate restTemplate, SocialProviderProperties.Kakao properties) {
		super(restTemplate, properties.userInfoUri(), SocialProvider.KAKAO);
	}

	@Override
	protected String extractSocialProviderUserId(JsonNode body) {
		return requireText(body, "id");
	}
}
