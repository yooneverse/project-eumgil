package com.ssafy.e102.domain.auth.token;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.e102.domain.auth.exception.AuthErrorCode;
import com.ssafy.e102.domain.auth.exception.AuthException;

@Component
public class RedisAuthTokenStore implements AuthTokenStore {

	private static final String REFRESH_KEY_PREFIX = "auth:refresh:";
	private static final String REFRESH_USER_KEY_PREFIX = "auth:refresh:user:";
	private static final String SIGNUP_KEY_PREFIX = "auth:signup:";
	private static final String ACCESS_BLACKLIST_KEY_PREFIX = "auth:access-blacklist:";
	private static final RedisScript<Long> ROTATE_REFRESH_TOKEN_SCRIPT = new DefaultRedisScript<>("""
		local storedUserId = redis.call('GET', KEYS[1])
		if not storedUserId then
			return 0
		end
		if storedUserId ~= ARGV[1] then
			return 0
		end
		redis.call('DEL', KEYS[1])
		redis.call('SREM', KEYS[2], ARGV[3])
		redis.call('SET', KEYS[3], ARGV[1], 'EX', ARGV[2])
		redis.call('SADD', KEYS[2], ARGV[4])
		redis.call('EXPIRE', KEYS[2], ARGV[2])
		return 1
		""", Long.class);

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	public RedisAuthTokenStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
	}

	@Override
	public void saveRefreshToken(String refreshToken, UUID userId, Duration ttl) {
		String tokenHash = TokenHash.sha256(refreshToken);
		redisTemplate.opsForValue().set(refreshTokenKey(tokenHash), userId.toString(), ttl.toSeconds(),
			TimeUnit.SECONDS);
		redisTemplate.opsForSet().add(refreshUserKey(userId), tokenHash);
		redisTemplate.expire(refreshUserKey(userId), ttl.toSeconds(), TimeUnit.SECONDS);
	}

	@Override
	public Optional<UUID> findRefreshTokenUserId(String refreshToken) {
		return Optional.ofNullable(redisTemplate.opsForValue().get(refreshTokenKey(TokenHash.sha256(refreshToken))))
			.map(UUID::fromString);
	}

	@Override
	public boolean rotateRefreshToken(String oldRefreshToken, String newRefreshToken, UUID userId, Duration ttl) {
		String oldTokenHash = TokenHash.sha256(oldRefreshToken);
		String newTokenHash = TokenHash.sha256(newRefreshToken);
		String ttlSeconds = String.valueOf(ttl.toSeconds());
		Long result = redisTemplate.execute(
			ROTATE_REFRESH_TOKEN_SCRIPT,
			List.of(
				refreshTokenKey(oldTokenHash),
				refreshUserKey(userId),
				refreshTokenKey(newTokenHash)),
			userId.toString(),
			ttlSeconds,
			oldTokenHash,
			newTokenHash);
		return Long.valueOf(1L).equals(result);
	}

	@Override
	public void deleteRefreshToken(String refreshToken) {
		String tokenHash = TokenHash.sha256(refreshToken);
		String tokenKey = refreshTokenKey(tokenHash);
		Optional.ofNullable(redisTemplate.opsForValue().get(tokenKey))
			.map(UUID::fromString)
			.ifPresent(userId -> redisTemplate.opsForSet().remove(refreshUserKey(userId), tokenHash));
		redisTemplate.delete(tokenKey);
	}

	@Override
	public void deleteRefreshTokensByUserId(UUID userId) {
		String userKey = refreshUserKey(userId);
		Set<String> tokenHashes = redisTemplate.opsForSet().members(userKey);
		if (tokenHashes != null && !tokenHashes.isEmpty()) {
			redisTemplate.delete(tokenHashes.stream()
				.map(this::refreshTokenKey)
				.toList());
		}
		redisTemplate.delete(userKey);
	}

	@Override
	public void saveSignupToken(String signupToken, SignupTokenPayload signupTokenPayload, Duration ttl) {
		redisTemplate.opsForValue().set(signupTokenKey(signupToken), serialize(signupTokenPayload), ttl.toSeconds(),
			TimeUnit.SECONDS);
	}

	@Override
	public Optional<SignupTokenPayload> findSignupToken(String signupToken) {
		return Optional.ofNullable(redisTemplate.opsForValue().get(signupTokenKey(signupToken)))
			.map(this::deserialize);
	}

	@Override
	public void deleteSignupToken(String signupToken) {
		redisTemplate.delete(signupTokenKey(signupToken));
	}

	@Override
	public void saveAccessTokenBlacklist(String accessToken, Duration ttl) {
		if (ttl.isNegative() || ttl.isZero()) {
			return;
		}
		redisTemplate.opsForValue().set(accessBlacklistKey(accessToken), "1", ttl.toSeconds(), TimeUnit.SECONDS);
	}

	@Override
	public boolean containsAccessToken(String accessToken) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(accessBlacklistKey(accessToken)));
	}

	private String refreshTokenKey(String tokenHash) {
		return REFRESH_KEY_PREFIX + tokenHash;
	}

	private String refreshUserKey(UUID userId) {
		return REFRESH_USER_KEY_PREFIX + userId;
	}

	private String signupTokenKey(String token) {
		return SIGNUP_KEY_PREFIX + TokenHash.sha256(token);
	}

	private String accessBlacklistKey(String accessToken) {
		return ACCESS_BLACKLIST_KEY_PREFIX + TokenHash.sha256(accessToken);
	}

	private String serialize(SignupTokenPayload signupTokenPayload) {
		try {
			return objectMapper.writeValueAsString(signupTokenPayload);
		} catch (JsonProcessingException exception) {
			throw new AuthException(AuthErrorCode.TOKEN_STORE_OPERATION_FAILED,
				"회원가입 토큰 정보를 직렬화할 수 없습니다.", exception);
		}
	}

	private SignupTokenPayload deserialize(String value) {
		try {
			return objectMapper.readValue(value, SignupTokenPayload.class);
		} catch (JsonProcessingException exception) {
			throw new AuthException(AuthErrorCode.TOKEN_STORE_OPERATION_FAILED,
				"회원가입 토큰 정보를 역직렬화할 수 없습니다.", exception);
		}
	}
}
