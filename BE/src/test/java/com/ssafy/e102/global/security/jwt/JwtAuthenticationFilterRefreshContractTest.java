package com.ssafy.e102.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ssafy.e102.domain.auth.token.AuthTokenStore;
import com.ssafy.e102.domain.user.repository.UserRepository;
import com.ssafy.e102.global.security.filter.JwtAuthenticationFilter;

class JwtAuthenticationFilterRefreshContractTest {

	private static final String SECRET = "bG9jYWwtand0LXNlY3JldC1mb3ItZTEwMi0zMmJ5dGVzISE=";
	private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-14T00:00:00Z"), ZoneOffset.UTC);

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("위조 액세스 토큰은 인증 주체를 만들지 않아 A4010 응답 흐름으로 넘긴다")
	void forgedAccessTokenDoesNotAuthenticate() throws Exception {
		JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
			tokenProvider(FIXED_CLOCK),
			tokenStore(),
			userRepository());

		filter.doFilter(
			requestWithBearer("forged-access-token"),
			new MockHttpServletResponse(),
			new MockFilterChain());

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	@DisplayName("만료된 액세스 토큰은 인증 주체를 만들지 않아 A4010 응답 흐름으로 넘긴다")
	void expiredAccessTokenDoesNotAuthenticate() throws Exception {
		JwtProperties properties = jwtProperties(Duration.ofSeconds(1));
		JwtTokenProvider issuer = new JwtTokenProvider(properties, FIXED_CLOCK);
		JwtTokenProvider parser = new JwtTokenProvider(
			properties,
			Clock.fixed(Instant.parse("2026-05-14T00:00:02Z"), ZoneOffset.UTC));
		String expiredAccessToken = issuer.createAccessToken(UUID.randomUUID());
		JwtAuthenticationFilter filter = new JwtAuthenticationFilter(parser, tokenStore(), userRepository());

		filter.doFilter(
			requestWithBearer(expiredAccessToken),
			new MockHttpServletResponse(),
			new MockFilterChain());

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	private MockHttpServletRequest requestWithBearer(String accessToken) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
		return request;
	}

	private JwtTokenProvider tokenProvider(Clock clock) {
		return new JwtTokenProvider(jwtProperties(Duration.ofMinutes(15)), clock);
	}

	private JwtProperties jwtProperties(Duration accessTokenTtl) {
		return new JwtProperties(
			SECRET,
			"e102-test",
			accessTokenTtl,
			Duration.ofDays(14),
			Duration.ofMinutes(10));
	}

	private AuthTokenStore tokenStore() {
		return org.mockito.Mockito.mock(AuthTokenStore.class);
	}

	private UserRepository userRepository() {
		return org.mockito.Mockito.mock(UserRepository.class);
	}
}
