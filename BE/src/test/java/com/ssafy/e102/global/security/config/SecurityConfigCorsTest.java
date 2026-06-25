package com.ssafy.e102.global.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import com.ssafy.e102.global.security.filter.JwtAuthenticationFilter;
import com.ssafy.e102.global.security.handler.RestAccessDeniedHandler;
import com.ssafy.e102.global.security.handler.RestAuthenticationEntryPoint;

class SecurityConfigCorsTest {

	@Test
	@DisplayName("CORS 허용 헤더에 멱등성 키를 포함한다")
	void corsAllowedHeadersIncludeIdempotencyKey() {
		CorsProperties corsProperties = mock(CorsProperties.class);
		when(corsProperties.allowedOrigins()).thenReturn(List.of("http://localhost:3001"));
		SecurityConfig securityConfig = new SecurityConfig(
			mock(RestAuthenticationEntryPoint.class),
			mock(RestAccessDeniedHandler.class),
			mock(JwtAuthenticationFilter.class),
			corsProperties);

		CorsConfigurationSource source = securityConfig.corsConfigurationSource();
		CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest());

		assertThat(configuration).isNotNull();
		assertThat(configuration.getAllowedHeaders())
			.contains("Authorization", "Content-Type", "Idempotency-Key");
	}
}
