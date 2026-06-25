package com.ssafy.e102.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ssafy.e102.domain.auth.social.verifier.CompositeSocialTokenVerifier;
import com.ssafy.e102.domain.auth.dto.request.ReissueRequest;
import com.ssafy.e102.domain.auth.dto.response.TokenResponse;
import com.ssafy.e102.domain.auth.exception.AuthErrorCode;
import com.ssafy.e102.domain.auth.exception.AuthException;
import com.ssafy.e102.domain.auth.token.AuthTokenStore;
import com.ssafy.e102.domain.user.repository.UserRepository;
import com.ssafy.e102.global.security.jwt.JwtProperties;
import com.ssafy.e102.global.security.jwt.JwtTokenProvider;

class AuthServiceReissueTest {

	private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);

	@Mock
	private CompositeSocialTokenVerifier socialTokenVerifier;

	@Mock
	private UserRepository userRepository;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private AuthTokenStore authTokenStore;

	@Mock
	private AuthSessionService authSessionService;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		JwtProperties jwtProperties = new JwtProperties(
			"bG9jYWwtand0LXNlY3JldC1mb3ItZTEwMi0zMmJ5dGVzISE=",
			"e102-test",
			Duration.ofMinutes(15),
			REFRESH_TOKEN_TTL,
			Duration.ofMinutes(10));
		authService = new AuthService(
			socialTokenVerifier,
			userRepository,
			jwtTokenProvider,
			authTokenStore,
			jwtProperties,
			authSessionService);
	}

	@Test
	@DisplayName("저장된 refresh token을 회전하고 새 토큰 쌍을 반환한다")
	void reissue() {
		UUID userId = UUID.randomUUID();
		when(jwtTokenProvider.getRefreshTokenSubject("old-refresh-token")).thenReturn(userId);
		when(jwtTokenProvider.createAccessToken(userId)).thenReturn("new-access-token");
		when(jwtTokenProvider.createRefreshToken(userId)).thenReturn("new-refresh-token");
		when(authTokenStore.rotateRefreshToken("old-refresh-token", "new-refresh-token", userId, REFRESH_TOKEN_TTL))
			.thenReturn(true);

		TokenResponse response = authService.reissue(new ReissueRequest("old-refresh-token"));

		assertThat(response.accessToken()).isEqualTo("new-access-token");
		assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
		verify(authTokenStore).rotateRefreshToken("old-refresh-token", "new-refresh-token", userId, REFRESH_TOKEN_TTL);
	}

	@Test
	@DisplayName("저장소에 없는 refresh token은 거부한다")
	void rejectMissingRefreshTokenInStore() {
		UUID userId = UUID.randomUUID();
		when(jwtTokenProvider.getRefreshTokenSubject("refresh-token")).thenReturn(userId);
		when(jwtTokenProvider.createAccessToken(userId)).thenReturn("new-access-token");
		when(jwtTokenProvider.createRefreshToken(userId)).thenReturn("new-refresh-token");
		when(authTokenStore.rotateRefreshToken("refresh-token", "new-refresh-token", userId, REFRESH_TOKEN_TTL))
			.thenReturn(false);

		assertThatThrownBy(() -> authService.reissue(new ReissueRequest("refresh-token")))
			.isInstanceOf(AuthException.class)
			.extracting("errorCode")
			.isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
	}

	@Test
	@DisplayName("토큰 subject와 저장소 사용자 ID가 다르면 refresh token을 거부한다")
	void rejectRefreshTokenSubjectMismatch() {
		UUID userId = UUID.randomUUID();
		when(jwtTokenProvider.getRefreshTokenSubject("refresh-token")).thenReturn(userId);
		when(jwtTokenProvider.createAccessToken(userId)).thenReturn("new-access-token");
		when(jwtTokenProvider.createRefreshToken(userId)).thenReturn("new-refresh-token");
		when(authTokenStore.rotateRefreshToken("refresh-token", "new-refresh-token", userId, REFRESH_TOKEN_TTL))
			.thenReturn(false);

		assertThatThrownBy(() -> authService.reissue(new ReissueRequest("refresh-token")))
			.isInstanceOf(AuthException.class)
			.extracting("errorCode")
			.isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
	}
}
