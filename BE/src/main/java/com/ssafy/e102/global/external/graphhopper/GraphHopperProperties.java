package com.ssafy.e102.global.external.graphhopper;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/**
 * Backend가 GraphHopper runtime을 호출할 때 쓰는 baseUrl과 timeout 설정이다.
 *
 * <p>application.yml 또는 환경변수에서 값을 받고, 누락된 timeout은 경로 구현 계획의 기본값 5초로 맞춘다.
 */
@ConfigurationProperties(prefix = "graphhopper")
public record GraphHopperProperties(
	String baseUrl,
	Duration connectTimeout,
	Duration readTimeout,
	String activeSlotKey,
	String previousSlotKey,
	String blueUrlKey,
	String greenUrlKey,
	String blueUrl,
	String greenUrl,
	String healthUrl,
	String blueHealthUrl,
	String greenHealthUrl) {

	public GraphHopperProperties {
		Assert.hasText(baseUrl, "GraphHopper 기본 URL은 필수입니다.");
		// 환경별 설정 누락 시에도 외부 호출이 무제한 대기하지 않도록 5초 기본값을 고정한다.
		connectTimeout = defaultIfNull(connectTimeout, Duration.ofSeconds(5));
		readTimeout = defaultIfNull(readTimeout, Duration.ofSeconds(5));
		activeSlotKey = defaultIfBlank(activeSlotKey, "graphhopper:active-slot");
		previousSlotKey = defaultIfBlank(previousSlotKey, "graphhopper:previous-slot");
		blueUrlKey = defaultIfBlank(blueUrlKey, "graphhopper:blue:url");
		greenUrlKey = defaultIfBlank(greenUrlKey, "graphhopper:green:url");
		blueUrl = defaultIfBlank(blueUrl, "http://graphhopper-blue:8989");
		greenUrl = defaultIfBlank(greenUrl, "http://graphhopper-green:8989");
		healthUrl = defaultIfBlank(healthUrl, "http://localhost:8990/healthcheck");
		blueHealthUrl = defaultIfBlank(blueHealthUrl, "http://graphhopper-blue:8990/healthcheck");
		greenHealthUrl = defaultIfBlank(greenHealthUrl, "http://graphhopper-green:8990/healthcheck");
	}

	public String healthUrlForSlot(String slot) {
		if ("blue".equals(slot)) {
			return blueHealthUrl;
		}
		if ("green".equals(slot)) {
			return greenHealthUrl;
		}
		return healthUrl;
	}

	private static Duration defaultIfNull(Duration value, Duration defaultValue) {
		return value == null ? defaultValue : value;
	}

	private static String defaultIfBlank(String value, String defaultValue) {
		return value == null || value.isBlank() ? defaultValue : value;
	}
}
