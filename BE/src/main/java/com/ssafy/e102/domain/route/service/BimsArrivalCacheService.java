package com.ssafy.e102.domain.route.service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.e102.global.external.bims.BusanBimsArrival;

/**
 * BUS 도착정보는 짧은 TTL만 유지하는 임시 캐시다.
 */
@Service
public class BimsArrivalCacheService {

	private static final String BIMS_ARRIVAL_KEY_PREFIX = "bims:arrival:";
	private static final Duration BIMS_ARRIVAL_TTL = Duration.ofMinutes(1);

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	public BimsArrivalCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
	}

	public Optional<BusanBimsArrival> find(String stopId, String lineId) {
		return Optional.ofNullable(redisTemplate.opsForValue().get(key(stopId, lineId)))
			.map(this::deserialize);
	}

	public void save(BusanBimsArrival arrival) {
		redisTemplate.opsForValue().set(
			key(arrival.stopId(), arrival.lineId()),
			serialize(arrival),
			BIMS_ARRIVAL_TTL.toSeconds(),
			TimeUnit.SECONDS);
	}

	private String key(String stopId, String lineId) {
		return BIMS_ARRIVAL_KEY_PREFIX + stopId + ":" + lineId;
	}

	private String serialize(BusanBimsArrival arrival) {
		try {
			return objectMapper.writeValueAsString(arrival);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("BIMS 도착정보를 직렬화할 수 없습니다.", exception);
		}
	}

	private BusanBimsArrival deserialize(String value) {
		try {
			return objectMapper.readValue(value, BusanBimsArrival.class);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("BIMS 도착정보를 역직렬화할 수 없습니다.", exception);
		}
	}
}
