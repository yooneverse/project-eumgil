package com.ssafy.e102.domain.auth.service;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ssafy.e102.domain.auth.token.AuthTokenStore;
import com.ssafy.e102.global.security.jwt.JwtTokenProvider;

@Service
public class AuthSessionService {

	private final AuthTokenStore authTokenStore;
	private final JwtTokenProvider jwtTokenProvider;

	public AuthSessionService(
		AuthTokenStore authTokenStore,
		JwtTokenProvider jwtTokenProvider) {
		this.authTokenStore = authTokenStore;
		this.jwtTokenProvider = jwtTokenProvider;
	}

	public void invalidateUserSession(UUID userId, String accessToken) {
		authTokenStore.deleteRefreshTokensByUserId(userId);
		jwtTokenProvider.getAccessTokenRemainingTtl(accessToken)
			.filter(this::isPositive)
			.ifPresent(remainingTtl -> authTokenStore.saveAccessTokenBlacklist(accessToken, remainingTtl));
	}

	private boolean isPositive(Duration ttl) {
		return !ttl.isNegative() && !ttl.isZero();
	}
}
