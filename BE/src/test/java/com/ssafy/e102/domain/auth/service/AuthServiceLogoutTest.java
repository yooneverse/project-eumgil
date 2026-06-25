package com.ssafy.e102.domain.auth.service;

import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ssafy.e102.domain.auth.social.verifier.CompositeSocialTokenVerifier;
import com.ssafy.e102.domain.auth.token.AuthTokenStore;
import com.ssafy.e102.domain.user.repository.UserRepository;
import com.ssafy.e102.global.security.jwt.JwtProperties;
import com.ssafy.e102.global.security.jwt.JwtTokenProvider;

class AuthServiceLogoutTest {

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
			Duration.ofDays(14),
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
	@DisplayName("로그아웃은 현재 사용자 세션을 무효화한다")
	void logout() {
		UUID userId = UUID.randomUUID();

		authService.logout(userId, "access-token");

		verify(authSessionService).invalidateUserSession(userId, "access-token");
	}
}
