package com.ssafy.e102.global.external.graphhopper;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Redis에 저장된 GraphHopper blue/green active slot을 읽어 backend 호출 endpoint를 고른다.
 *
 * <p>Redis 장애나 값 누락은 경로 검색 장애로 전파하지 않고 `graphhopper.base-url` fallback으로 흘린다.
 */
@Component
public class RedisGraphHopperEndpointProvider implements GraphHopperEndpointProvider {

	private static final Logger log = LoggerFactory.getLogger(RedisGraphHopperEndpointProvider.class);
	private static final Set<String> SLOT_NAMES = Set.of("blue", "green");

	private final StringRedisTemplate redisTemplate;
	private final GraphHopperProperties properties;

	public RedisGraphHopperEndpointProvider(StringRedisTemplate redisTemplate, GraphHopperProperties properties) {
		this.redisTemplate = redisTemplate;
		this.properties = properties;
	}

	@Override
	public GraphHopperEndpointSelection selectEndpoint() {
		try {
			String activeSlot = readSlot(properties.activeSlotKey()).orElse(null);
			String activeBaseUrl = resolveSlotBaseUrl(activeSlot).orElse(properties.baseUrl());
			String previousSlot = readSlot(properties.previousSlotKey()).orElse(null);
			String previousBaseUrl = resolveSlotBaseUrl(previousSlot)
				.filter(url -> !url.equals(activeBaseUrl))
				.orElse(null);
			return new GraphHopperEndpointSelection(activeBaseUrl, previousBaseUrl, activeSlot, previousSlot);
		} catch (RuntimeException exception) {
			log.warn(
				"failed to resolve graphhopper slot endpoint from redis. fallbackBaseUrl={} message={}",
				properties.baseUrl(),
				exception.getMessage());
			return GraphHopperEndpointSelection.fallback(properties.baseUrl());
		}
	}

	private Optional<String> readSlot(String key) {
		return readValue(key)
			.map(value -> value.toLowerCase(Locale.ROOT))
			.filter(SLOT_NAMES::contains);
	}

	private Optional<String> resolveSlotBaseUrl(String slot) {
		if ("blue".equals(slot)) {
			return readValue(properties.blueUrlKey()).or(() -> Optional.of(properties.blueUrl()));
		}
		if ("green".equals(slot)) {
			return readValue(properties.greenUrlKey()).or(() -> Optional.of(properties.greenUrl()));
		}
		return Optional.empty();
	}

	private Optional<String> readValue(String key) {
		if (!StringUtils.hasText(key)) {
			return Optional.empty();
		}
		return Optional.ofNullable(redisTemplate.opsForValue().get(key))
			.map(String::trim)
			.filter(StringUtils::hasText);
	}
}
