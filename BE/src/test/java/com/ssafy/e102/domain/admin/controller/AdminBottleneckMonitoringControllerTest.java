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
import com.ssafy.e102.domain.admin.dto.response.AdminBottleneckMonitoringResponse;
import com.ssafy.e102.domain.admin.service.AdminBottleneckMonitoringService;

class AdminBottleneckMonitoringControllerTest {

	@Mock
	private AdminBottleneckMonitoringService adminBottleneckMonitoringService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		mockMvc = MockMvcBuilders.standaloneSetup(
			new AdminBottleneckMonitoringController(adminBottleneckMonitoringService))
			.build();
	}

	@Test
	@DisplayName("병목구간 통계 API는 화면 계약 데이터를 반환한다")
	void getMonitoring() throws Exception {
		LocalDate from = LocalDate.of(2026, 5, 11);
		LocalDate to = LocalDate.of(2026, 5, 17);
		AdminBottleneckMonitoringResponse response = new AdminBottleneckMonitoringResponse(
			"병목구간 통계",
			"실데이터 기반 병목구간 운영 통계입니다.",
			"2026.05.11 ~ 2026.05.17",
			"CSV 다운로드",
			List.of(
				new AdminBottleneckMonitoringResponse.SummaryCardResponse("병목구간 총수", "4건", "▲ 2건 (100.0%)", "지난 기간 대비", "danger", "alert")),
			new AdminBottleneckMonitoringResponse.TrendResponse(
				List.of("05.11"),
				List.of(
					new AdminBottleneckMonitoringResponse.SeriesResponse("전체", "#4b82f6", List.of(3.0)),
					new AdminBottleneckMonitoringResponse.SeriesResponse("심각", "#ff6b6b", List.of(2.0))),
				10),
			new AdminBottleneckMonitoringResponse.DistributionResponse(
				4,
				List.of(new AdminBottleneckMonitoringResponse.DistributionItemResponse("좁은 보행로", 2, 0.5, "#3b82f6"))),
			new AdminBottleneckMonitoringResponse.MapResponse(
				List.of(new BottleneckHotspotResponse("route-1-center", "신호동-명지1동 이동축", 35.09, 128.88, 0.74, 3, 17)),
				List.of(new BottleneckRouteSegmentResponse(
					"route-1",
					"신호동-명지1동 이동축",
					List.of(new GeoPointResponse(35.08, 128.87), new GeoPointResponse(35.09, 128.90)),
					0.74,
					3,
					17))),
			new AdminBottleneckMonitoringResponse.TableResponse(
				new AdminBottleneckMonitoringResponse.TableFiltersResponse("전체 유형", "전체 상태", "최신순", "10개씩 보기"),
				List.of(
					new AdminBottleneckMonitoringResponse.TableRowResponse(
						1,
						"신호동-명지1동 이동축",
						"부산광역시 강서구 신호동",
						"좁은 보행로",
						"blue",
						"9명",
						"심각",
						"danger",
						"2026.05.16")),
				new AdminBottleneckMonitoringResponse.PaginationResponse(1, List.of(1))),
			new AdminBottleneckMonitoringResponse.ImpactTopResponse(
				"영향 사용자",
				List.of(new AdminBottleneckMonitoringResponse.ImpactItemResponse(1, "신호동-명지1동 이동축", "9명", "심각", "danger"))));
		when(adminBottleneckMonitoringService.getMonitoring(from, to)).thenReturn(response);

		mockMvc.perform(get("/admin/dashboard/bottleneck-monitoring")
			.param("from", "2026-05-11")
			.param("to", "2026-05-17"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.summaryCards[0].label").value("병목구간 총수"))
			.andExpect(jsonPath("$.data.distribution.totalCount").value(4))
			.andExpect(jsonPath("$.data.table.rows[0].location").value("신호동-명지1동 이동축"));

		verify(adminBottleneckMonitoringService).getMonitoring(from, to);
	}
}
