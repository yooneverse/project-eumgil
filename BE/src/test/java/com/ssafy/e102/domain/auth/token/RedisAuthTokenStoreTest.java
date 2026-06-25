package com.ssafy.e102.domain.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.e102.domain.user.type.SocialProvider;

class RedisAuthTokenStoreTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@Mock
	private SetOperations<String, String> setOperations;

	private RedisAuthTokenStore authTokenStore;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(redisTemplate.opsForSet()).thenReturn(setOperations);
		authTokenStore = new RedisAuthTokenStore(redisTemplate, new ObjectMapper());
	}

	@Test
	@DisplayName("리프레시 토큰은 원문 대신 해시 키로 저장한다")
	void saveRefreshTokenWithHashedKey() {
		UUID userId = UUID.randomUUID();
		Duration ttl = Duration.ofDays(14);

		authTokenStore.saveRefreshToken("refresh-token", userId, ttl);

		verify(valueOperations).set(
			"auth:refresh:0eb17643d4e9261163783a420859c92c7d212fa9624106a12b510afbec266120",
			userId.toString(),
			ttl.toSeconds(),
			TimeUnit.SECONDS);
		verify(setOperations).add(
			"auth:refresh:user:" + userId,
			"0eb17643d4e9261163783a420859c92c7d212fa9624106a12b510afbec266120");
		verify(redisTemplate).expire("auth:refresh:user:" + userId, ttl.toSeconds(), TimeUnit.SECONDS);
	}

	@Test
	@DisplayName("저장된 리프레시 토큰으로 사용자 ID를 조회한다")
	void findUserIdByRefreshToken() {
		UUID userId = UUID.randomUUID();
		when(valueOperations.get("auth:refresh:0eb17643d4e9261163783a420859c92c7d212fa9624106a12b510afbec266120"))
			.thenReturn(userId.toString());

		Optional<UUID> found = authTokenStore.findRefreshTokenUserId("refresh-token");

		assertThat(found).contains(userId);
	}

	@Test
	@DisplayName("리프레시 토큰 회전은 Redis 원자 연산으로 기존 토큰을 한 번만 소비하고 새 토큰을 저장한다")
	void rotateRefreshToken() {
		UUID userId = UUID.randomUUID();
		Duration ttl = Duration.ofDays(14);
		when(redisTemplate.execute(
			org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
			org.mockito.ArgumentMatchers.<List<String>>any(),
			eq(userId.toString()),
			eq(String.valueOf(ttl.toSeconds())),
			eq("66e4f4e9739a9ef9a9d6e414cfd05780c4ab0eb03e21fbf90ebf87e76d4db8f6"),
			eq("c40dd1765d767caae2588f0ee1de9181d8a44cc9306261eb2c9e526351188338")))
			.thenReturn(1L);

		boolean rotated = authTokenStore.rotateRefreshToken("old-refresh-token", "new-refresh-token", userId, ttl);

		assertThat(rotated).isTrue();
		verify(redisTemplate).execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
			org.mockito.ArgumentMatchers.<List<String>>argThat(
				keys -> keys.contains("auth:refresh:66e4f4e9739a9ef9a9d6e414cfd05780c4ab0eb03e21fbf90ebf87e76d4db8f6")
					&& keys.contains("auth:refresh:c40dd1765d767caae2588f0ee1de9181d8a44cc9306261eb2c9e526351188338")
					&& keys.contains("auth:refresh:user:" + userId)),
			eq(userId.toString()),
			eq(String.valueOf(ttl.toSeconds())),
			eq("66e4f4e9739a9ef9a9d6e414cfd05780c4ab0eb03e21fbf90ebf87e76d4db8f6"),
			eq("c40dd1765d767caae2588f0ee1de9181d8a44cc9306261eb2c9e526351188338"));
	}

	@Test
	@DisplayName("이미 소비된 리프레시 토큰 회전은 실패하고 새 토큰을 저장하지 않는다")
	void rejectAlreadyConsumedRefreshTokenRotation() {
		UUID userId = UUID.randomUUID();
		Duration ttl = Duration.ofDays(14);
		when(redisTemplate.execute(
			org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
			org.mockito.ArgumentMatchers.<List<String>>any(),
			eq(userId.toString()),
			eq(String.valueOf(ttl.toSeconds())),
			eq("66e4f4e9739a9ef9a9d6e414cfd05780c4ab0eb03e21fbf90ebf87e76d4db8f6"),
			eq("c40dd1765d767caae2588f0ee1de9181d8a44cc9306261eb2c9e526351188338")))
			.thenReturn(0L);

		boolean rotated = authTokenStore.rotateRefreshToken("old-refresh-token", "new-refresh-token", userId, ttl);

		assertThat(rotated).isFalse();
		verifyNoInteractions(valueOperations);
	}

	@Test
	@DisplayName("사용자 ID로 저장된 모든 리프레시 토큰을 삭제한다")
	void deleteRefreshTokensByUserId() {
		UUID userId = UUID.randomUUID();
		when(setOperations.members("auth:refresh:user:" + userId))
			.thenReturn(Set.of("token-hash-1", "token-hash-2"));

		authTokenStore.deleteRefreshTokensByUserId(userId);

		verify(redisTemplate).delete(org.mockito.ArgumentMatchers.<Collection<String>>argThat(keys -> keys.containsAll(
			Set.of("auth:refresh:token-hash-1", "auth:refresh:token-hash-2"))));
		verify(redisTemplate).delete("auth:refresh:user:" + userId);
	}

	@Test
	@DisplayName("회원가입 토큰은 소셜 식별 정보를 저장한다")
	void saveSignupToken() {
		Duration ttl = Duration.ofMinutes(10);

		authTokenStore.saveSignupToken("signup-token",
			new SignupTokenPayload(SocialProvider.KAKAO, "kakao-user-id"), ttl);

		verify(valueOperations).set(
			"auth:signup:932739eece2b7d31922b6d13a4a5f9caa895139a7d8bc549472a5682b624f9b5",
			"{\"socialProvider\":\"KAKAO\",\"socialProviderUserId\":\"kakao-user-id\"}",
			ttl.toSeconds(),
			TimeUnit.SECONDS);
	}

	@Test
	@DisplayName("저장된 회원가입 토큰으로 소셜 식별 정보를 조회한다")
	void findSignupTokenPayload() {
		when(valueOperations.get("auth:signup:932739eece2b7d31922b6d13a4a5f9caa895139a7d8bc549472a5682b624f9b5"))
			.thenReturn("{\"socialProvider\":\"NAVER\",\"socialProviderUserId\":\"naver-user-id\"}");

		Optional<SignupTokenPayload> found = authTokenStore.findSignupToken("signup-token");

		assertThat(found).contains(new SignupTokenPayload(SocialProvider.NAVER, "naver-user-id"));
	}

	@Test
	@DisplayName("회원가입 완료 후 임시 토큰을 삭제한다")
	void deleteSignupToken() {
		authTokenStore.deleteSignupToken("signup-token");

		verify(redisTemplate).delete("auth:signup:932739eece2b7d31922b6d13a4a5f9caa895139a7d8bc549472a5682b624f9b5");
	}

	@Test
	@DisplayName("액세스 토큰 차단 목록은 원문 대신 해시 키로 저장한다")
	void saveAccessTokenBlacklist() {
		Duration ttl = Duration.ofMinutes(7);

		authTokenStore.saveAccessTokenBlacklist("access-token", ttl);

		verify(valueOperations).set(
			"auth:access-blacklist:3f16bed7089f4653e5ef21bfd2824d7f3aaaecc7a598e7e89c580e1606a9cc52",
			"1",
			ttl.toSeconds(),
			TimeUnit.SECONDS);
	}

	@Test
	@DisplayName("액세스 토큰 차단 여부를 해시 키로 조회한다")
	void containsAccessToken() {
		when(redisTemplate.hasKey(
			"auth:access-blacklist:3f16bed7089f4653e5ef21bfd2824d7f3aaaecc7a598e7e89c580e1606a9cc52"))
			.thenReturn(true);

		assertThat(authTokenStore.containsAccessToken("access-token")).isTrue();
	}
}
