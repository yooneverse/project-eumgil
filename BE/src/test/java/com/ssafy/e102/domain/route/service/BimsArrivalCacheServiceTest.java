package com.ssafy.e102.domain.route.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.e102.global.external.bims.BusanBimsArrival;

class BimsArrivalCacheServiceTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	private BimsArrivalCacheService cacheService;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		cacheService = new BimsArrivalCacheService(redisTemplate, new ObjectMapper());
	}

	@Test
	void savesArrivalWithOneMinuteTtl() {
		BusanBimsArrival arrival = new BusanBimsArrival("507700000", "5200177000", "1009", 3, true);

		cacheService.save(arrival);

		verify(valueOperations).set(
			org.mockito.ArgumentMatchers.eq("bims:arrival:507700000:5200177000"),
			org.mockito.ArgumentMatchers.contains("\"remainingMinute\":3"),
			org.mockito.ArgumentMatchers.eq(60L),
			org.mockito.ArgumentMatchers.eq(TimeUnit.SECONDS));
	}

	@Test
	void findsArrivalFromCachedValue() {
		when(valueOperations.get("bims:arrival:507700000:5200177000")).thenReturn("""
			{"stopId":"507700000","lineId":"5200177000","routeNo":"1009","remainingMinute":3,"isLowFloor":true}
			""");

		Optional<BusanBimsArrival> arrival = cacheService.find("507700000", "5200177000");

		assertThat(arrival).isPresent();
		assertThat(arrival.get().remainingMinute()).isEqualTo(3);
		assertThat(arrival.get().isLowFloor()).isTrue();
	}

	@Test
	void returnsEmptyWhenCachedValueIsMissing() {
		when(valueOperations.get("bims:arrival:507700000:5200177000")).thenReturn(null);

		Optional<BusanBimsArrival> arrival = cacheService.find("507700000", "5200177000");

		assertThat(arrival).isEmpty();
	}

	@Test
	void saveThrowsInternalFailureWhenArrivalCannotBeSerialized() throws Exception {
		ObjectMapper objectMapper = mock(ObjectMapper.class);
		when(objectMapper.writeValueAsString(any()))
			.thenThrow(new JsonProcessingException("serialize failed") {});
		BimsArrivalCacheService brokenCacheService = new BimsArrivalCacheService(redisTemplate, objectMapper);

		assertThatThrownBy(() -> brokenCacheService.save(
			new BusanBimsArrival("507700000", "5200177000", "1009", 3, true)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("BIMS 도착정보를 직렬화할 수 없습니다.");
	}

	@Test
	void findThrowsInternalFailureWhenArrivalCannotBeDeserialized() {
		when(valueOperations.get("bims:arrival:507700000:5200177000")).thenReturn("{broken");

		assertThatThrownBy(() -> cacheService.find("507700000", "5200177000"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("BIMS 도착정보를 역직렬화할 수 없습니다.");
	}
}
