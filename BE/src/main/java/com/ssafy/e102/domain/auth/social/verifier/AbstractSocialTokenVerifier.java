package com.ssafy.e102.domain.auth.social.verifier;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.e102.domain.auth.dto.SocialUserInfo;
import com.ssafy.e102.domain.auth.exception.AuthErrorCode;
import com.ssafy.e102.domain.auth.exception.AuthException;
import com.ssafy.e102.domain.user.type.SocialProvider;

abstract class AbstractSocialTokenVerifier implements SocialTokenVerifier {

	private final RestTemplate restTemplate;
	private final String userInfoUri;
	private final SocialProvider socialProvider;

	protected AbstractSocialTokenVerifier(RestTemplate restTemplate, String userInfoUri,
		SocialProvider socialProvider) {
		Assert.notNull(restTemplate, "RestTemplate은 필수입니다.");
		Assert.hasText(userInfoUri, "사용자 정보 조회 URI는 필수입니다.");
		Assert.notNull(socialProvider, "소셜 제공자는 필수입니다.");
		this.restTemplate = restTemplate;
		this.userInfoUri = userInfoUri;
		this.socialProvider = socialProvider;
	}

	@Override
	public SocialProvider getProvider() {
		return socialProvider;
	}

	@Override
	public SocialUserInfo verify(String socialAccessToken) {
		Assert.hasText(socialAccessToken, "소셜 액세스 토큰은 필수입니다.");
		try {
			ResponseEntity<JsonNode> response = restTemplate.exchange(userInfoUri, HttpMethod.GET,
				new HttpEntity<>(bearerHeaders(socialAccessToken)), JsonNode.class);
			String socialProviderUserId = extractSocialProviderUserId(response.getBody());
			return new SocialUserInfo(socialProvider, socialProviderUserId);
		} catch (HttpClientErrorException exception) {
			throw new AuthException(AuthErrorCode.INVALID_SOCIAL_TOKEN,
				"유효하지 않은 소셜 액세스 토큰입니다.", exception);
		} catch (HttpServerErrorException exception) {
			throw new AuthException(AuthErrorCode.SOCIAL_PROVIDER_API_FAILED,
				"소셜 제공자 API 호출에 실패했습니다.", exception);
		} catch (RestClientException exception) {
			throw new AuthException(AuthErrorCode.SOCIAL_PROVIDER_API_FAILED,
				"소셜 제공자 API 호출에 실패했습니다.", exception);
		}
	}

	protected abstract String extractSocialProviderUserId(JsonNode body);

	private HttpHeaders bearerHeaders(String socialAccessToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(socialAccessToken);
		return headers;
	}

	protected String requireText(JsonNode body, String fieldName) {
		if (body == null || !body.hasNonNull(fieldName) || body.get(fieldName).asText().isBlank()) {
			throw new AuthException(AuthErrorCode.SOCIAL_PROVIDER_API_FAILED,
				"소셜 제공자 응답에서 사용자 식별자를 찾을 수 없습니다.");
		}
		return body.get(fieldName).asText();
	}
}
