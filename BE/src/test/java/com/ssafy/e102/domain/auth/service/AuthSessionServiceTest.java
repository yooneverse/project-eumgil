package com.ssafy.e102.domain.auth.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ssafy.e102.domain.auth.token.AuthTokenStore;
import com.ssafy.e102.global.security.jwt.JwtTokenProvider;

class AuthSessionServiceTest {

	@Mock
	private AuthTokenStore authTokenStore;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	private AuthSessionService authSessionService;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		authSessionService = new AuthSessionService(authTokenStore, jwtTokenProvider);
	}

	@Test
	@DisplayName("사용자 refresh token들을 삭제하고 현재 access token을 남은 만료 시간만큼 차단한다")
	void invalidateUserSession() {
		UUID userId = UUID.randomUUID();
		Duration remainingTtl = Duration.ofMinutes(7);
		when(jwtTokenProvider.getAccessTokenRemainingTtl("access-token")).thenReturn(Optional.of(remainingTtl));

		authSessionService.invalidateUserSession(userId, "access-token");

		verify(authTokenStore).deleteRefreshTokensByUserId(userId);
		verify(authTokenStore).saveAccessTokenBlacklist("access-token", remainingTtl);
	}

	@Test
	@DisplayName("이미 만료된 access token은 차단 저장하지 않고 refresh token만 삭제한다")
	void skipExpiredAccessTokenBlacklist() {
		UUID userId = UUID.randomUUID();
		when(jwtTokenProvider.getAccessTokenRemainingTtl("access-token")).thenReturn(Optional.empty());

		authSessionService.invalidateUserSession(userId, "access-token");

		verify(authTokenStore).deleteRefreshTokensByUserId(userId);
		verify(authTokenStore, never()).saveAccessTokenBlacklist(any(), any());
	}
}
