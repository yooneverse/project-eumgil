package com.ssafy.e102.domain.auth.token;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface AuthTokenStore {

	void saveRefreshToken(String refreshToken, UUID userId, Duration ttl);

	Optional<UUID> findRefreshTokenUserId(String refreshToken);

	boolean rotateRefreshToken(String oldRefreshToken, String newRefreshToken, UUID userId, Duration ttl);

	void deleteRefreshToken(String refreshToken);

	void deleteRefreshTokensByUserId(UUID userId);

	void saveSignupToken(String signupToken, SignupTokenPayload signupTokenPayload, Duration ttl);

	Optional<SignupTokenPayload> findSignupToken(String signupToken);

	void deleteSignupToken(String signupToken);

	void saveAccessTokenBlacklist(String accessToken, Duration ttl);

	boolean containsAccessToken(String accessToken);
}
