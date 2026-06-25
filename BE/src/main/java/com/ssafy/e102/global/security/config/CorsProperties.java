package com.ssafy.e102.global.security.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cors")
public record CorsProperties(
	List<String> allowedOrigins) {

	public CorsProperties {
		allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream()
			.filter(origin -> origin != null && !origin.isBlank())
			.toList();
	}
}
