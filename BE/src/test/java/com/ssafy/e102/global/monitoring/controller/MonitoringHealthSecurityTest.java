package com.ssafy.e102.global.monitoring.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ssafy.e102.global.logging.RequestLoggingFilter;
import com.ssafy.e102.global.external.graphhopper.GraphHopperActiveHealthChecker;
import com.ssafy.e102.global.security.config.CorsProperties;
import com.ssafy.e102.global.security.config.SecurityConfig;
import com.ssafy.e102.global.security.filter.JwtAuthenticationFilter;
import com.ssafy.e102.global.security.handler.RestAccessDeniedHandler;
import com.ssafy.e102.global.security.handler.RestAuthenticationEntryPoint;

@WebMvcTest(MonitoringHealthController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, RequestLoggingFilter.class})
class MonitoringHealthSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private HealthEndpoint healthEndpoint;

	@MockitoBean
	private GraphHopperActiveHealthChecker graphHopperHealthChecker;

	@MockitoBean
	private RestAuthenticationEntryPoint authenticationEntryPoint;

	@MockitoBean
	private RestAccessDeniedHandler accessDeniedHandler;

	@MockitoBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@MockitoBean
	private CorsProperties corsProperties;

	@Test
	@DisplayName("운영 health endpoint는 인증 없이 접근할 수 있다")
	void monitoringHealthIsPublic() throws Exception {
		when(corsProperties.allowedOrigins()).thenReturn(List.of("http://localhost:3001"));
		when(healthEndpoint.health()).thenReturn(Health.up().build());
		when(graphHopperHealthChecker.check()).thenReturn(
			new GraphHopperActiveHealthChecker.GraphHopperHealthStatus("UP", "green", "blue"));

		mockMvc.perform(get("/health"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/health/graphhopper"))
			.andExpect(status().isOk());
	}
}
