package com.ssafy.e102.domain.auth.social.verifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.ssafy.e102.domain.auth.exception.AuthErrorCode;
import com.ssafy.e102.domain.auth.exception.AuthException;
import com.ssafy.e102.domain.auth.social.config.SocialProviderProperties;
import com.ssafy.e102.domain.auth.dto.SocialUserInfo;
import com.ssafy.e102.domain.user.type.SocialProvider;

class SocialTokenVerifierTest {

	private RestTemplate restTemplate;
	private MockRestServiceServer server;

	@BeforeEach
	void setUp() {
		restTemplate = new RestTemplate();
		server = MockRestServiceServer.createServer(restTemplate);
	}

	@Test
	@DisplayName("카카오 access token으로 카카오 사용자 ID를 조회한다")
	void verifyKakaoToken() {
		SocialTokenVerifier verifier = new KakaoSocialTokenVerifier(restTemplate,
			new SocialProviderProperties.Kakao("https://kapi.kakao.test/v2/user/me"));
		server.expect(requestTo("https://kapi.kakao.test/v2/user/me"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer kakao-token"))
			.andRespond(withSuccess("{\"id\":123456789}", MediaType.APPLICATION_JSON));

		SocialUserInfo userInfo = verifier.verify("kakao-token");

		assertThat(userInfo.socialProvider()).isEqualTo(SocialProvider.KAKAO);
		assertThat(userInfo.socialProviderUserId()).isEqualTo("123456789");
		server.verify();
	}

	@Test
	@DisplayName("네이버 access token으로 네이버 사용자 ID를 조회한다")
	void verifyNaverToken() {
		SocialTokenVerifier verifier = new NaverSocialTokenVerifier(restTemplate,
			new SocialProviderProperties.Naver("https://openapi.naver.test/v1/nid/me"));
		server.expect(requestTo("https://openapi.naver.test/v1/nid/me"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer naver-token"))
			.andRespond(withSuccess("{\"response\":{\"id\":\"naver-user-id\"}}", MediaType.APPLICATION_JSON));

		SocialUserInfo userInfo = verifier.verify("naver-token");

		assertThat(userInfo.socialProvider()).isEqualTo(SocialProvider.NAVER);
		assertThat(userInfo.socialProviderUserId()).isEqualTo("naver-user-id");
		server.verify();
	}

	@Test
	@DisplayName("구글 access token으로 구글 사용자 subject를 조회한다")
	void verifyGoogleToken() {
		SocialTokenVerifier verifier = new GoogleSocialTokenVerifier(restTemplate,
			new SocialProviderProperties.Google("https://openidconnect.google.test/v1/userinfo"));
		server.expect(requestTo("https://openidconnect.google.test/v1/userinfo"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer google-token"))
			.andRespond(withSuccess("{\"sub\":\"google-sub\"}", MediaType.APPLICATION_JSON));

		SocialUserInfo userInfo = verifier.verify("google-token");

		assertThat(userInfo.socialProvider()).isEqualTo(SocialProvider.GOOGLE);
		assertThat(userInfo.socialProviderUserId()).isEqualTo("google-sub");
		server.verify();
	}

	@Test
	@DisplayName("provider 4xx 응답은 유효하지 않은 소셜 토큰으로 매핑한다")
	void mapProviderClientErrorToInvalidToken() {
		SocialTokenVerifier verifier = new KakaoSocialTokenVerifier(restTemplate,
			new SocialProviderProperties.Kakao("https://kapi.kakao.test/v2/user/me"));
		server.expect(requestTo("https://kapi.kakao.test/v2/user/me"))
			.andRespond(withResourceNotFound());

		assertThatThrownBy(() -> verifier.verify("invalid-token"))
			.isInstanceOf(AuthException.class)
			.extracting("errorCode")
			.isEqualTo(AuthErrorCode.INVALID_SOCIAL_TOKEN);
	}

	@Test
	@DisplayName("provider 5xx 응답은 소셜 연동 실패로 매핑한다")
	void mapProviderServerErrorToApiFailed() {
		SocialTokenVerifier verifier = new GoogleSocialTokenVerifier(restTemplate,
			new SocialProviderProperties.Google("https://openidconnect.google.test/v1/userinfo"));
		server.expect(requestTo("https://openidconnect.google.test/v1/userinfo"))
			.andRespond(withServerError());

		assertThatThrownBy(() -> verifier.verify("google-token"))
			.isInstanceOf(AuthException.class)
			.extracting("errorCode")
			.isEqualTo(AuthErrorCode.SOCIAL_PROVIDER_API_FAILED);
	}
}
