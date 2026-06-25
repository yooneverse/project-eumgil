package com.ssafy.e102.domain.admin.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ssafy.e102.domain.admin.dto.response.AdminDashboardBottleneckResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminDashboardSummaryResponse;
import com.ssafy.e102.domain.admin.service.AdminDashboardService;

class AdminDashboardControllerTest {

	@Mock
	private AdminDashboardService adminDashboardService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		mockMvc = MockMvcBuilders.standaloneSetup(new AdminDashboardController(adminDashboardService))
			.build();
	}

	@Test
	@DisplayName("관리자 홈 요약은 기간 기준 운영 지표를 반환한다")
	void getDashboardSummary() throws Exception {
		LocalDate from = LocalDate.of(2026, 5, 14);
		LocalDate to = LocalDate.of(2026, 5, 14);
		AdminDashboardSummaryResponse response = new AdminDashboardSummaryResponse(
			new AdminDashboardSummaryResponse.PeriodResponse(from, to),
			new AdminDashboardSummaryResponse.UserMetricsResponse(100, 7, 2, 42, Map.of("LOW_VISION", 40L)),
			new AdminDashboardSummaryResponse.RouteMetricsResponse(
				80,
				12,
				9,
				0.75,
				14.5,
				3,
				5,
				0.82,
				List.of(new AdminDashboardSummaryResponse.DailyMovementMetricResponse(from, 12, 7))),
			new AdminDashboardSummaryResponse.ReportMetricsResponse(30, 4, 8, 12, 10, Map.of("STAIRS_STEP", 5L)),
			new AdminDashboardSummaryResponse.DataQualityMetricsResponse(500, 120, 10, 6, 0.7, 0.5),
			new AdminDashboardSummaryResponse.OperationsMetricsResponse(List.of(), List.of()),
			new AdminDashboardSummaryResponse.TelemetryMetricsResponse(false, "경로 텔레메트리 수집 후 활성화됩니다."));
		when(adminDashboardService.getSummary(from, to)).thenReturn(response);

		mockMvc.perform(get("/admin/dashboard/summary")
			.param("from", "2026-05-14")
			.param("to", "2026-05-14"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.period.from").value("2026-05-14"))
			.andExpect(jsonPath("$.data.users.totalUsers").value(100))
			.andExpect(jsonPath("$.data.routes.navigationCompletionRate").value(0.75))
			.andExpect(jsonPath("$.data.routes.averageRouteSpeedMps").value(0.82))
			.andExpect(jsonPath("$.data.routes.dailyMovement[0].routeCount").value(12))
			.andExpect(jsonPath("$.data.reports.pendingReports").value(8))
			.andExpect(jsonPath("$.data.telemetry.enabled").value(false));

		verify(adminDashboardService).getSummary(from, to);
	}

	@Test
	@DisplayName("관리자 병목 후보는 기간과 limit 기준 지도 데이터를 반환한다")
	void getDashboardBottlenecks() throws Exception {
		LocalDate from = LocalDate.of(2026, 5, 8);
		LocalDate to = LocalDate.of(2026, 5, 14);
		AdminDashboardBottleneckResponse response = new AdminDashboardBottleneckResponse(
			new AdminDashboardBottleneckResponse.PeriodResponse(from, to),
			false,
			"route_sessions.route_snapshot_json + hazard_reports",
			List.of(new AdminDashboardBottleneckResponse.TopBottleneckResponse(
				1,
				"route-a",
				"초량 이바구길 입구",
				0.8,
				3,
				16)),
			List.of(new AdminDashboardBottleneckResponse.BottleneckHotspotResponse(
				"route-a-center",
				"초량 이바구길 입구",
				35.116888,
				129.039221,
				0.8,
				3,
				16)),
			List.of(new AdminDashboardBottleneckResponse.BottleneckRouteSegmentResponse(
				"route-a",
				"초량 이바구길 입구",
				List.of(new AdminDashboardBottleneckResponse.GeoPointResponse(35.116888, 129.039221)),
				0.8,
				3,
				16)));
		when(adminDashboardService.getBottlenecks(from, to, 5)).thenReturn(response);

		mockMvc.perform(get("/admin/dashboard/bottlenecks")
			.param("from", "2026-05-08")
			.param("to", "2026-05-14")
			.param("limit", "5"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.telemetryBased").value(false))
			.andExpect(jsonPath("$.data.topBottlenecks[0].name").value("초량 이바구길 입구"))
			.andExpect(jsonPath("$.data.routeSegments[0].points[0].lat").value(35.116888));

		verify(adminDashboardService).getBottlenecks(from, to, 5);
	}
}
