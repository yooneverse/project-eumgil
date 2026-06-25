package com.ssafy.e102.global.external.odsay;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "odsay")
public record OdsayProperties(
	String baseUrl,
	String apiKey,
	Duration connectTimeout,
	Duration readTimeout) {
}
