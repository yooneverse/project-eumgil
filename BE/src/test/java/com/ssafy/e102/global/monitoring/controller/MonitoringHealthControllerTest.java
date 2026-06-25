package com.ssafy.e102.global.monitoring.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ssafy.e102.global.external.graphhopper.GraphHopperActiveHealthChecker;

class MonitoringHealthControllerTest {

	private final HealthEndpoint healthEndpoint = mock(HealthEndpoint.class);
	private final GraphHopperActiveHealthChecker graphHopperHealthChecker = mock(GraphHopperActiveHealthChecker.class);
	private final MockMvc mockMvc = MockMvcBuilders
		.standaloneSetup(new MonitoringHealthController(healthEndpoint, graphHopperHealthChecker))
		.build();

	@Test
	@DisplayName("서비스 전체 health 상태는 공개 200 응답으로 노출한다")
	void overallHealthReturnsOk() throws Exception {
		when(healthEndpoint.health()).thenReturn(Health.up().build());

		mockMvc.perform(get("/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.component").value("overall"));
	}

	@Test
	@DisplayName("db health가 DOWN이면 503 응답으로 노출한다")
	void dbHealthDownReturnsServiceUnavailable() throws Exception {
		when(healthEndpoint.healthForPath("db")).thenReturn(Health.down().build());

		mockMvc.perform(get("/health/db"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.status").value("DOWN"))
			.andExpect(jsonPath("$.component").value("db"));
	}

	@Test
	@DisplayName("graphhopper health는 active slot 기준 상태를 공개한다")
	void graphhopperHealthReturnsActiveSlotStatus() throws Exception {
		when(graphHopperHealthChecker.check()).thenReturn(
			new GraphHopperActiveHealthChecker.GraphHopperHealthStatus("UP", "green", "blue"));

		mockMvc.perform(get("/health/graphhopper"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.component").value("graphhopper"));
	}

	@Test
	@DisplayName("지원하지 않는 health 컴포넌트는 404를 반환한다")
	void unsupportedComponentReturnsNotFound() throws Exception {
		mockMvc.perform(get("/health/queue"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status").value("NOT_FOUND"))
			.andExpect(jsonPath("$.component").value("queue"));
	}
}
