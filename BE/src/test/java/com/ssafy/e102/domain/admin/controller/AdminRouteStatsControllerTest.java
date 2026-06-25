package com.ssafy.e102.domain.admin.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ssafy.e102.domain.admin.dto.response.AdminDashboardBottleneckResponse.BottleneckHotspotResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminDashboardBottleneckResponse.BottleneckRouteSegmentResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminDashboardBottleneckResponse.GeoPointResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminRouteStatsResponse;
import com.ssafy.e102.domain.admin.service.AdminRouteStatsService;

class AdminRouteStatsControllerTest {

	@Mock
	private AdminRouteStatsService adminRouteStatsService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		mockMvc = MockMvcBuilders.standaloneSetup(new AdminRouteStatsController(adminRouteStatsService))
			.build();
	}

	@Test
	@DisplayName("관리자 경로/이동 통계는 기간 기준 화면 데이터를 반환한다")
	void getRouteStats() throws Exception {
		LocalDate from = LocalDate.of(2026, 5, 11);
		LocalDate to = LocalDate.of(2026, 5, 17);
		AdminRouteStatsResponse response = new AdminRouteStatsResponse(
			new AdminRouteStatsResponse.PeriodResponse(from, to),
			new AdminRouteStatsResponse.SummaryResponse(152, "총 이동 건수"),
			new AdminRouteStatsResponse.FiltersResponse(
				List.of(new AdminRouteStatsResponse.SelectOptionResponse("ALL", "전체")),
				List.of(new AdminRouteStatsResponse.SelectOptionResponse("DAILY", "일별")),
				new AdminRouteStatsResponse.FilterDefaultsResponse("ALL", "DAILY")),
			new AdminRouteStatsResponse.MapResponse(
				"이동 경로 밀도 히트맵",
				"낮음",
				"높음",
				List.of(new AdminRouteStatsResponse.SelectOptionResponse("route-density", "경로 밀도")),
				"route-density",
				List.of(new AdminRouteStatsResponse.SelectOptionResponse("HEATMAP", "히트맵")),
				"HEATMAP",
				true,
				List.of(new BottleneckHotspotResponse("route-1-center", "신호동-명지1동 이동축", 35.09, 128.88, 0.9, 2, 6)),
				List.of(new BottleneckRouteSegmentResponse(
					"route-1",
					"신호동-명지1동 이동축",
					List.of(new GeoPointResponse(35.08, 128.87), new GeoPointResponse(35.09, 128.90)),
					0.9,
					2,
					6))),
			"집계 기준: 동일 geometry 경로 세션을 대표 이동축 1건으로 묶어 집계",
			List.of(new AdminRouteStatsResponse.TopRouteResponse(1, "신호동-명지1동 이동축", 6, 0.039, "danger")),
			List.of(new AdminRouteStatsResponse.BreakdownItemResponse("보행약자", 65, 0.428, "#2f7df6")),
			new AdminRouteStatsResponse.HeatmapMatrixResponse(
				"요일·시간대 이동 분포",
				"요일/시간대 이동량을 0~1 범위 밀도로 정규화한 값입니다.",
				List.of("00-04"),
				List.of("월"),
				List.of(List.of(1.0))),
			new AdminRouteStatsResponse.SpeedTrendResponse(
				List.of("00시"),
				List.of(new AdminRouteStatsResponse.SeriesResponse("보행약자", "#2f7df6", List.of(4.2)))),
			new AdminRouteStatsResponse.DistanceDistributionResponse(
				List.of("~1km"),
				List.of(new AdminRouteStatsResponse.DistanceSeriesResponse("보행약자", "#2f7df6", List.of(2L), List.of(0.25)))),
			List.of(new AdminRouteStatsResponse.AverageDistanceItemResponse("전체", 3.1, "#2f7df6")),
			List.of(new AdminRouteStatsResponse.InfoItemResponse("수집 기준", "route_sessions + users 실데이터 집계")));
		when(adminRouteStatsService.getRouteStats(from, to)).thenReturn(response);

		mockMvc.perform(get("/admin/dashboard/route-stats")
			.param("from", "2026-05-11")
			.param("to", "2026-05-17"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.summary.totalTrips").value(152))
			.andExpect(jsonPath("$.data.map.title").value("이동 경로 밀도 히트맵"))
			.andExpect(jsonPath("$.data.topRoutes[0].name").value("신호동-명지1동 이동축"))
			.andExpect(jsonPath("$.data.hourlyHeatmap.values[0][0]").value(1.0));

		verify(adminRouteStatsService).getRouteStats(from, to);
	}
}
