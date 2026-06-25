package com.ssafy.e102.global.external.bims;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "busan.bims")
public record BusanBimsProperties(
	String baseUrl,
	String serviceKey,
	Duration connectTimeout,
	Duration readTimeout) {
}
