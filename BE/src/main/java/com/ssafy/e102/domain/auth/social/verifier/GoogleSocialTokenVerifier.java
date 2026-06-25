package com.ssafy.e102.domain.auth.social.verifier;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.e102.domain.auth.social.config.SocialProviderProperties;
import com.ssafy.e102.domain.user.type.SocialProvider;

@Component
public class GoogleSocialTokenVerifier extends AbstractSocialTokenVerifier {

	public GoogleSocialTokenVerifier(RestTemplate restTemplate, SocialProviderProperties.Google properties) {
		super(restTemplate, properties.userInfoUri(), SocialProvider.GOOGLE);
	}

	@Override
	protected String extractSocialProviderUserId(JsonNode body) {
		return requireText(body, "sub");
	}
}
