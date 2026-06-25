package com.ssafy.e102.domain.report.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ssafy.e102.domain.report.dto.request.HazardReportRerouteRequest;
import com.ssafy.e102.domain.report.dto.response.HazardReportRerouteResponse;
import com.ssafy.e102.domain.report.service.HazardReportRerouteService;
import com.ssafy.e102.domain.route.dto.response.RouteLegResponse;
import com.ssafy.e102.domain.route.dto.response.RouteSummaryResponse;
import com.ssafy.e102.domain.route.type.RouteOption;
import com.ssafy.e102.domain.route.type.TransportMode;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;
import com.ssafy.e102.global.security.principal.AuthPrincipal;

class HazardReportRerouteControllerTest {

	@Mock
	private HazardReportRerouteService hazardReportRerouteService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		mockMvc = MockMvcBuilders.standaloneSetup(
				new HazardReportRerouteController(hazardReportRerouteService))
			.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
			.build();
	}

	@Test
	@DisplayName("hazard report reroute returns rerouted route for the authenticated owner")
	void rerouteAfterHazardReport() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(hazardReportRerouteService.reroute(
			eq(userId),
			eq(12L),
			any(HazardReportRerouteRequest.class)))
			.thenReturn(new HazardReportRerouteResponse(true, routeSummary("rr_active_123")));

		mockMvc.perform(post("/hazard/12/reroute")
			.principal(authentication)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "routeId": "rr_active_123",
				  "currentPoint": {
				    "lat": 35.1,
				    "lng": 129.1
				  }
				}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.rerouted").value(true))
			.andExpect(jsonPath("$.data.route.routeId").value("rr_active_123"))
			.andExpect(jsonPath("$.data.route.transportMode").value("WALK"));

		verify(hazardReportRerouteService).reroute(
			eq(userId),
			eq(12L),
			any(HazardReportRerouteRequest.class));
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("hazard report reroute keeps route field as null when no alternate route exists")
	void rerouteWithoutAlternateRouteIncludesNullRouteField() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(hazardReportRerouteService.reroute(
			eq(userId),
			eq(12L),
			any(HazardReportRerouteRequest.class)))
			.thenReturn(new HazardReportRerouteResponse(false, null));

		mockMvc.perform(post("/hazard/12/reroute")
			.principal(authentication)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "routeId": "rr_active_123",
				  "currentPoint": {
				    "lat": 35.1,
				    "lng": 129.1
				  }
				}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.rerouted").value(false))
			.andExpect(content().string(containsString("\"route\":null")));

		SecurityContextHolder.clearContext();
	}

	private RouteSummaryResponse routeSummary(String routeId) {
		return new RouteSummaryResponse(
			routeId,
			TransportMode.WALK,
			RouteOption.SAFE,
			List.of(RouteOption.SAFE),
			"safe route",
			BigDecimal.valueOf(120),
			90,
			2,
			List.of(),
			List.of(),
			"LINESTRING(129.1 35.1, 129.101 35.101)",
			List.of(new RouteLegResponse(
				1,
				TransportMode.WALK,
				com.ssafy.e102.domain.route.type.RouteLegRole.WALK_ONLY,
				"Walk",
				BigDecimal.valueOf(120),
				90,
				2,
				"LINESTRING(129.1 35.1, 129.101 35.101)",
				List.of())));
	}

	private UsernamePasswordAuthenticationToken authentication(UUID userId) {
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
			new AuthPrincipal(userId, "access-token"), null);
		SecurityContextHolder.getContext().setAuthentication(authentication);
		return authentication;
	}
}
