package com.ssafy.e102.global.external.graphhopper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisGraphHopperEndpointProviderTest {

	@Test
	@DisplayName("Redis active/previous slot과 slot URL을 GraphHopper endpoint로 변환한다")
	void selectEndpointUsesRedisSlotState() {
		RedisGraphHopperEndpointProvider provider = new RedisGraphHopperEndpointProvider(
			redisTemplate(Map.of(
				"active", "green",
				"previous", "blue",
				"blue-url", "http://blue:8989",
				"green-url", "http://green:8989")),
			properties());

		GraphHopperEndpointSelection endpoint = provider.selectEndpoint();

		assertThat(endpoint.activeSlot()).isEqualTo("green");
		assertThat(endpoint.activeBaseUrl()).isEqualTo("http://green:8989");
		assertThat(endpoint.previousSlot()).isEqualTo("blue");
		assertThat(endpoint.previousBaseUrl()).isEqualTo("http://blue:8989");
	}

	@Test
	@DisplayName("Redis 장애나 값 누락 시 기본 base-url로 fallback한다")
	void selectEndpointFallsBackWhenRedisFails() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));
		RedisGraphHopperEndpointProvider provider = new RedisGraphHopperEndpointProvider(redisTemplate, properties());

		GraphHopperEndpointSelection endpoint = provider.selectEndpoint();

		assertThat(endpoint.activeSlot()).isEqualTo("fallback");
		assertThat(endpoint.activeBaseUrl()).isEqualTo("http://fallback:8989");
		assertThat(endpoint.hasPrevious()).isFalse();
	}

	private GraphHopperProperties properties() {
		return new GraphHopperProperties(
			"http://fallback:8989",
			Duration.ofSeconds(5),
			Duration.ofSeconds(5),
			"active",
			"previous",
			"blue-url",
			"green-url",
			"http://blue-default:8989",
			"http://green-default:8989",
			"http://fallback-default:8990/healthcheck",
			"http://blue-default:8990/healthcheck",
			"http://green-default:8990/healthcheck");
	}

	@SuppressWarnings("unchecked")
	private StringRedisTemplate redisTemplate(Map<String, String> values) {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(anyString())).thenAnswer(invocation -> values.get(invocation.getArgument(0)));
		return redisTemplate;
	}
}
