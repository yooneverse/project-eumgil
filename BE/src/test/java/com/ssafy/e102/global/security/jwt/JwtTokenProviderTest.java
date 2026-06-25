package com.ssafy.e102.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ssafy.e102.domain.auth.token.SignupTokenPayload;
import com.ssafy.e102.domain.user.type.SocialProvider;

class JwtTokenProviderTest {

	private static final String SECRET = "bG9jYWwtand0LXNlY3JldC1mb3ItZTEwMi0zMmJ5dGVzISE=";
	private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-04-30T00:00:00Z"), ZoneOffset.UTC);

	@Test
	@DisplayName("액세스 토큰은 UUID subject를 복원할 수 있다")
	void createAndParseAccessToken() {
		JwtTokenProvider tokenProvider = tokenProvider();
		UUID userId = UUID.randomUUID();

		String token = tokenProvider.createAccessToken(userId);

		assertThat(tokenProvider.getAccessTokenSubject(token)).isEqualTo(userId);
	}

	@Test
	@DisplayName("리프레시 토큰은 액세스 토큰으로 사용할 수 없다")
	void rejectRefreshTokenAsAccessToken() {
		JwtTokenProvider tokenProvider = tokenProvider();
		String refreshToken = tokenProvider.createRefreshToken(UUID.randomUUID());

		assertThatThrownBy(() -> tokenProvider.getAccessTokenSubject(refreshToken))
			.isInstanceOf(JwtTokenException.class)
			.hasMessage("토큰 유형이 올바르지 않습니다.");
	}

	@Test
	@DisplayName("회원가입 토큰은 소셜 제공자와 소셜 사용자 ID를 담는다")
	void createAndParseSignupToken() {
		JwtTokenProvider tokenProvider = tokenProvider();

		String token = tokenProvider.createSignupToken(SocialProvider.KAKAO, "kakao-user-id");
		SignupTokenPayload payload = tokenProvider.getSignupTokenPayload(token);

		assertThat(payload.socialProvider()).isEqualTo(SocialProvider.KAKAO);
		assertThat(payload.socialProviderUserId()).isEqualTo("kakao-user-id");
	}

	@Test
	@DisplayName("만료된 토큰은 파싱할 수 없다")
	void rejectExpiredToken() {
		JwtProperties properties = new JwtProperties(
			SECRET,
			"e102-test",
			Duration.ofSeconds(1),
			Duration.ofDays(14),
			Duration.ofMinutes(10));
		JwtTokenProvider issuer = new JwtTokenProvider(properties, FIXED_CLOCK);
		JwtTokenProvider parser = new JwtTokenProvider(
			properties,
			Clock.fixed(Instant.parse("2026-04-30T00:00:02Z"), ZoneOffset.UTC));
		String token = issuer.createAccessToken(UUID.randomUUID());

		assertThatThrownBy(() -> parser.getAccessTokenSubject(token))
			.isInstanceOf(JwtTokenException.class)
			.hasMessage("토큰이 만료되었습니다.");
	}

	private JwtTokenProvider tokenProvider() {
		return new JwtTokenProvider(
			new JwtProperties(
				SECRET,
				"e102-test",
				Duration.ofMinutes(15),
				Duration.ofDays(14),
				Duration.ofMinutes(10)),
			FIXED_CLOCK);
	}
}
