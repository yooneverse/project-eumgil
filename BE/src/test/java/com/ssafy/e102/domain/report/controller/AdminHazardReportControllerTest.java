package com.ssafy.e102.domain.report.controller;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
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

import com.ssafy.e102.domain.report.dto.response.AdminHazardReportDetailResponse;
import com.ssafy.e102.domain.report.dto.response.AdminHazardReportDeleteResponse;
import com.ssafy.e102.domain.report.dto.response.AdminHazardReportListResponse;
import com.ssafy.e102.domain.report.dto.response.AdminHazardRouteReviewResponse;
import com.ssafy.e102.domain.report.dto.response.AdminHazardReportStatusResponse;
import com.ssafy.e102.domain.report.dto.response.AdminHazardReportSummaryResponse;
import com.ssafy.e102.domain.report.service.AdminHazardRouteReviewService;
import com.ssafy.e102.domain.report.service.AdminHazardReportService;
import com.ssafy.e102.domain.report.type.HazardRouteReviewIntent;
import com.ssafy.e102.domain.report.type.HazardRouteReviewStage;
import com.ssafy.e102.domain.report.type.ReportStatus;
import com.ssafy.e102.domain.report.type.ReportType;
import com.ssafy.e102.global.geo.dto.GeoPointResponse;
import com.ssafy.e102.global.security.principal.AuthPrincipal;

class AdminHazardReportControllerTest {

	@Mock
	private AdminHazardReportService adminHazardReportService;

	@Mock
	private AdminHazardRouteReviewService adminHazardRouteReviewService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		SecurityContextHolder.clearContext();
		mockMvc = MockMvcBuilders.standaloneSetup(
				new AdminHazardReportController(adminHazardReportService, adminHazardRouteReviewService))
			.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
			.build();
	}

	@Test
	@DisplayName("관리자 제보 목록은 status, cursor, size를 전달한다")
	void getHazardReports() throws Exception {
		UUID reporterUserId = UUID.randomUUID();
		when(adminHazardReportService.getHazardReports(ReportStatus.PENDING, 10L, 2))
			.thenReturn(new AdminHazardReportListResponse(
				List.of(new AdminHazardReportSummaryResponse(
					3L,
					reporterUserId,
					ReportType.SIDEWALK_MISSING,
					"부산 부산진구 시민공원로 73",
					"보행 가능한 인도가 없습니다.",
					new GeoPointResponse(35.1686, 129.0576),
					ReportStatus.PENDING,
					LocalDateTime.of(2026, 5, 7, 22, 0),
					"https://example.com/reports/3/image-1.jpg",
					null)),
				2,
				3L,
				true));

		mockMvc.perform(get("/admin/hazard-reports")
			.param("status", "PENDING")
			.param("cursor", "10")
			.param("size", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content[0].reportId").value(3))
			.andExpect(jsonPath("$.data.content[0].reporterUserId").value(reporterUserId.toString()))
			.andExpect(jsonPath("$.data.content[0].address").value("부산 부산진구 시민공원로 73"))
			.andExpect(jsonPath("$.data.content[0].description").value("보행 가능한 인도가 없습니다."))
			.andExpect(jsonPath("$.data.content[0].status").value("PENDING"))
			.andExpect(jsonPath("$.data.nextCursor").value(3))
			.andExpect(jsonPath("$.data.hasNext").value(true));

		verify(adminHazardReportService).getHazardReports(ReportStatus.PENDING, 10L, 2);
	}

	@Test
	@DisplayName("관리자 제보 상세는 처리 상태와 전체 이미지를 반환한다")
	void getHazardReportDetail() throws Exception {
		UUID reporterUserId = UUID.randomUUID();
		when(adminHazardReportService.getHazardReportDetail(1L))
			.thenReturn(new AdminHazardReportDetailResponse(
				1L,
				reporterUserId,
				ReportType.RAMP,
				"경사로가 파손되었습니다.",
				"부산 부산진구 시민공원로 73",
				new GeoPointResponse(35.1686, 129.0576),
				ReportStatus.PENDING,
				null,
				null,
				LocalDateTime.of(2026, 5, 7, 22, 0),
				List.of("https://example.com/reports/1/image-1.jpg"),
				null));

		mockMvc.perform(get("/admin/hazard-reports/1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.reportId").value(1))
			.andExpect(jsonPath("$.data.reporterUserId").value(reporterUserId.toString()))
			.andExpect(jsonPath("$.data.address").value("부산 부산진구 시민공원로 73"))
			.andExpect(jsonPath("$.data.status").value("PENDING"))
			.andExpect(jsonPath("$.data.imageUrls[0]").value("https://example.com/reports/1/image-1.jpg"));

		verify(adminHazardReportService).getHazardReportDetail(1L);
	}

	@Test
	@DisplayName("관리자 제보 승인은 변경된 상태를 반환한다")
	void approveHazardReport() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(adminHazardReportService.approveHazardReport(1L, userId))
			.thenReturn(new AdminHazardReportStatusResponse(1L, ReportStatus.APPROVED));

		mockMvc.perform(patch("/admin/hazard-reports/1/approve").principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.reportId").value(1))
			.andExpect(jsonPath("$.data.status").value("APPROVED"));

		verify(adminHazardReportService).approveHazardReport(1L, userId);
	}

	@Test
	@DisplayName("관리자 제보 반려는 변경된 상태를 반환한다")
	void rejectHazardReport() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(adminHazardReportService.rejectHazardReport(1L, userId))
			.thenReturn(new AdminHazardReportStatusResponse(1L, ReportStatus.REJECTED));

		mockMvc.perform(patch("/admin/hazard-reports/1/reject").principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.reportId").value(1))
			.andExpect(jsonPath("$.data.status").value("REJECTED"));

		verify(adminHazardReportService).rejectHazardReport(1L, userId);
	}

	@Test
	@DisplayName("관리자 제보 삭제는 삭제된 제보 ID를 반환한다")
	void deleteHazardReport() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(adminHazardReportService.deleteHazardReport(1L, userId))
			.thenReturn(new AdminHazardReportDeleteResponse(1L));

		mockMvc.perform(delete("/admin/hazard-reports/1").principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.reportId").value(1));

		verify(adminHazardReportService).deleteHazardReport(1L, userId);
	}

	@Test
	@DisplayName("관리자 제보 경로 검수 시작은 검수 draft를 반환한다")
	void startRouteReview() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
			when(adminHazardRouteReviewService.startRouteReview(
				userId,
				1L,
				new com.ssafy.e102.domain.report.dto.request.StartHazardRouteReviewRequest(
					HazardRouteReviewIntent.APPROVE)))
			.thenReturn(new AdminHazardRouteReviewResponse(
				7L,
				1L,
				HazardRouteReviewIntent.APPROVE,
				HazardRouteReviewStage.IN_PROGRESS,
				ReportStatus.PENDING,
				userId,
				"부산진구",
				"부전동",
				41231L,
				LocalDateTime.of(2026, 5, 18, 15, 0),
				LocalDateTime.of(2026, 5, 18, 15, 5),
				null,
				List.of()));

		mockMvc.perform(post("/admin/hazard-reports/1/route-review/start")
			.principal(authentication)
			.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "intent": "APPROVE"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.reviewId").value(7))
			.andExpect(jsonPath("$.data.intent").value("APPROVE"))
			.andExpect(jsonPath("$.data.stage").value("IN_PROGRESS"));
	}

	@Test
	@DisplayName("인증 principal이 비어 있어도 제보 경로 검수 시작에서 NPE가 나지 않는다")
	void startRouteReviewWithoutPrincipal() throws Exception {
		when(adminHazardRouteReviewService.startRouteReview(
			null,
			1L,
			new com.ssafy.e102.domain.report.dto.request.StartHazardRouteReviewRequest(
				HazardRouteReviewIntent.APPROVE)))
			.thenReturn(new AdminHazardRouteReviewResponse(
				7L,
				1L,
				HazardRouteReviewIntent.APPROVE,
				HazardRouteReviewStage.IN_PROGRESS,
				ReportStatus.PENDING,
				UUID.randomUUID(),
				"부산진구",
				"부전동",
				41231L,
				LocalDateTime.of(2026, 5, 18, 15, 0),
				LocalDateTime.of(2026, 5, 18, 15, 5),
				null,
				List.of()));

		mockMvc.perform(post("/admin/hazard-reports/1/route-review/start")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "intent": "APPROVE"
					}
					"""))
			.andExpect(status().isOk());

		verify(adminHazardRouteReviewService).startRouteReview(
			isNull(),
			eq(1L),
			argThat(request -> request != null && request.intent() == HazardRouteReviewIntent.APPROVE));
	}

	@Test
	@DisplayName("인증 principal이 비어 있어도 제보 경로 검수 draft 저장에서 NPE가 나지 않는다")
	void updateRouteReviewWithoutPrincipal() throws Exception {
		when(adminHazardRouteReviewService.updateRouteReview(
			null,
			1L,
			new com.ssafy.e102.domain.report.dto.request.UpdateHazardRouteReviewRequest(
				41231L,
				List.of())))
			.thenReturn(new AdminHazardRouteReviewResponse(
				7L,
				1L,
				HazardRouteReviewIntent.APPROVE,
				HazardRouteReviewStage.IN_PROGRESS,
				ReportStatus.PENDING,
				UUID.randomUUID(),
				"부산진구",
				"부전동",
				41231L,
				LocalDateTime.of(2026, 5, 18, 15, 0),
				LocalDateTime.of(2026, 5, 18, 15, 5),
				null,
				List.of()));

		mockMvc.perform(patch("/admin/hazard-reports/1/route-review")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "selectedSegmentEdgeId": 41231,
					  "segmentDrafts": []
					}
					"""))
			.andExpect(status().isOk());

		verify(adminHazardRouteReviewService).updateRouteReview(
			isNull(),
			eq(1L),
			argThat(request -> request != null && request.selectedSegmentEdgeId() != null && request.selectedSegmentEdgeId() == 41231L));
	}

	@Test
	@DisplayName("인증 principal이 비어 있어도 제보 경로 검수 완료에서 NPE가 나지 않는다")
	void completeRouteReviewWithoutPrincipal() throws Exception {
		when(adminHazardRouteReviewService.completeRouteReview(null, 1L))
			.thenReturn(new AdminHazardRouteReviewResponse(
				7L,
				1L,
				HazardRouteReviewIntent.APPROVE,
				HazardRouteReviewStage.COMPLETED,
				ReportStatus.APPROVED,
				UUID.randomUUID(),
				"부산진구",
				"부전동",
				41231L,
				LocalDateTime.of(2026, 5, 18, 15, 0),
				LocalDateTime.of(2026, 5, 18, 15, 10),
				LocalDateTime.of(2026, 5, 18, 15, 10),
				List.of()));

		mockMvc.perform(post("/admin/hazard-reports/1/route-review/complete"))
			.andExpect(status().isOk());

		verify(adminHazardRouteReviewService).completeRouteReview(null, 1L);
	}

	@Test
	@DisplayName("관리자 제보 경로 검수 완료는 완료된 검수 상태를 반환한다")
	void completeRouteReview() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(adminHazardRouteReviewService.completeRouteReview(userId, 1L))
			.thenReturn(new AdminHazardRouteReviewResponse(
				7L,
				1L,
				HazardRouteReviewIntent.APPROVE,
				HazardRouteReviewStage.COMPLETED,
				ReportStatus.APPROVED,
				userId,
				"부산진구",
				"부전동",
				41231L,
				LocalDateTime.of(2026, 5, 18, 15, 0),
				LocalDateTime.of(2026, 5, 18, 15, 10),
				LocalDateTime.of(2026, 5, 18, 15, 10),
				List.of()));

		mockMvc.perform(post("/admin/hazard-reports/1/route-review/complete")
			.principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.stage").value("COMPLETED"))
			.andExpect(jsonPath("$.data.reportStatus").value("APPROVED"));
	}

	private UsernamePasswordAuthenticationToken authentication(UUID userId) {
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
			new AuthPrincipal(userId, "access-token"), null);
		SecurityContextHolder.getContext().setAuthentication(authentication);
		return authentication;
	}
}
