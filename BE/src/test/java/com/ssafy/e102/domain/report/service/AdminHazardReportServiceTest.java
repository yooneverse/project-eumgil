package com.ssafy.e102.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.ssafy.e102.domain.admin.service.AdminAuditLogService;
import com.ssafy.e102.domain.report.dto.response.AdminHazardReportDetailResponse;
import com.ssafy.e102.domain.report.dto.response.AdminHazardRouteReviewResponse;
import com.ssafy.e102.domain.report.dto.response.AdminHazardReportListResponse;
import com.ssafy.e102.domain.report.dto.response.AdminHazardReportStatusResponse;
import com.ssafy.e102.domain.report.entity.HazardReport;
import com.ssafy.e102.domain.report.exception.HazardReportErrorCode;
import com.ssafy.e102.domain.report.exception.HazardReportException;
import com.ssafy.e102.domain.report.repository.HazardReportImageRepository;
import com.ssafy.e102.domain.report.repository.HazardReportRepository;
import com.ssafy.e102.domain.report.type.ReportStatus;
import com.ssafy.e102.domain.report.type.ReportType;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.SocialProvider;
import com.ssafy.e102.global.geo.GeoPointConverter;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

class AdminHazardReportServiceTest {

	@Mock
	private HazardReportRepository hazardReportRepository;

	@Mock
	private HazardReportImageRepository hazardReportImageRepository;

	@Mock
	private HazardReportImageUploadService hazardReportImageUploadService;

	@Mock
	private AdminHazardRouteReviewService adminHazardRouteReviewService;

	@Mock
	private AdminAuditLogService adminAuditLogService;

	private AdminHazardReportService adminHazardReportService;
	private GeoPointConverter geoPointConverter;
	private Clock clock;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		geoPointConverter = new GeoPointConverter();
		clock = Clock.fixed(Instant.parse("2026-05-18T05:00:00Z"), ZoneOffset.UTC);
		adminHazardReportService = new AdminHazardReportService(
			hazardReportRepository,
			hazardReportImageRepository,
			geoPointConverter,
			hazardReportImageUploadService,
			adminHazardRouteReviewService,
			adminAuditLogService,
			clock);
	}

	@Test
	@DisplayName("관리자 제보 목록은 status와 cursor 기준으로 조회한다")
	void getHazardReports() {
		HazardReport hazardReport = hazardReport(user(UUID.randomUUID()), 3L,
			List.of("hazard-reports/user-3/20260514/image-1.jpg"));
		PageRequest pageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "reportId"));
		when(hazardReportRepository.findAllByStatusAndReportIdLessThanForAdmin(
			ReportStatus.PENDING,
			10L,
			pageable))
			.thenReturn(new SliceImpl<>(List.of(hazardReport), pageable, true));
		when(hazardReportImageRepository.findAllByHazardReport_ReportIdInAndDisplayOrder(List.of(3L), (short)0))
			.thenReturn(List.of(hazardReport.getImages().get(0)));
		when(hazardReportImageUploadService.createReadUrl("hazard-reports/user-3/20260514/image-1.jpg"))
			.thenReturn("https://storage.example.com/read?key=image-1");
		AdminHazardRouteReviewResponse latestReview = new AdminHazardRouteReviewResponse(
			31L,
			3L,
			null,
			null,
			ReportStatus.PENDING,
			null,
			"부산진구",
			"부전동",
			null,
			LocalDateTime.of(2026, 5, 18, 13, 0),
			LocalDateTime.of(2026, 5, 18, 13, 10),
			null,
			List.of());
		when(adminHazardRouteReviewService.getLatestRouteReviewsByReportIds(List.of(hazardReport)))
			.thenReturn(Map.of(3L, latestReview));

		AdminHazardReportListResponse response = adminHazardReportService.getHazardReports(
			ReportStatus.PENDING,
			10L,
			2);

		assertThat(response.content()).hasSize(1);
		assertThat(response.content().get(0).reportId()).isEqualTo(3L);
		assertThat(response.content().get(0).address()).isEqualTo("부산 부산진구 시민공원로 73");
		assertThat(response.content().get(0).description()).isEqualTo("보행 가능한 인도가 없습니다.");
		assertThat(response.content().get(0).status()).isEqualTo(ReportStatus.PENDING);
		assertThat(response.content().get(0).representativeImageUrl())
			.isEqualTo("https://storage.example.com/read?key=image-1");
		assertThat(response.content().get(0).latestRouteReview()).isEqualTo(latestReview);
		assertThat(response.nextCursor()).isEqualTo(3L);
		assertThat(response.hasNext()).isTrue();
	}

	@Test
	@DisplayName("관리자 제보 상세는 처리 상태와 조회용 presigned 이미지 URL을 반환한다")
	void getHazardReportDetail() {
		UUID reporterUserId = UUID.randomUUID();
		HazardReport hazardReport = hazardReport(user(reporterUserId), 1L, List.of(
			"hazard-reports/user-1/20260514/image-1.jpg",
			"hazard-reports/user-1/20260514/image-2.jpg"));
		when(hazardReportRepository.findWithImagesAndUserByReportId(1L)).thenReturn(Optional.of(hazardReport));
		when(hazardReportImageUploadService.createReadUrl("hazard-reports/user-1/20260514/image-1.jpg"))
			.thenReturn("https://storage.example.com/read?key=image-1");
		when(hazardReportImageUploadService.createReadUrl("hazard-reports/user-1/20260514/image-2.jpg"))
			.thenReturn("https://storage.example.com/read?key=image-2");
		when(adminHazardRouteReviewService.getLatestRouteReview(1L, ReportStatus.PENDING))
			.thenReturn(new AdminHazardRouteReviewResponse(
				11L,
				1L,
				null,
				null,
				ReportStatus.PENDING,
				null,
				"부산진구",
				"부전동",
				null,
				LocalDateTime.of(2026, 5, 18, 13, 0),
				LocalDateTime.of(2026, 5, 18, 13, 10),
				null,
				List.of()));

		AdminHazardReportDetailResponse response = adminHazardReportService.getHazardReportDetail(1L);

		assertThat(response.reportId()).isEqualTo(1L);
		assertThat(response.reporterUserId()).isEqualTo(reporterUserId);
		assertThat(response.address()).isEqualTo("부산 부산진구 시민공원로 73");
		assertThat(response.status()).isEqualTo(ReportStatus.PENDING);
		assertThat(response.latestRouteReview()).isNotNull();
		assertThat(response.latestRouteReview().reviewId()).isEqualTo(11L);
		assertThat(response.imageUrls()).containsExactly(
			"https://storage.example.com/read?key=image-1",
			"https://storage.example.com/read?key=image-2");
	}

	@Test
	@DisplayName("관리자 제보 승인은 PENDING 제보를 APPROVED로 변경한다")
	void approveHazardReport() {
		UUID actorUserId = UUID.randomUUID();
		when(hazardReportRepository.updateStatusIfCurrentStatus(
			1L,
			ReportStatus.PENDING,
			ReportStatus.APPROVED,
			actorUserId,
			LocalDateTime.ofInstant(clock.instant(), clock.getZone())))
			.thenReturn(1);

		AdminHazardReportStatusResponse response = adminHazardReportService.approveHazardReport(1L, actorUserId);

		assertThat(response.reportId()).isEqualTo(1L);
		assertThat(response.status()).isEqualTo(ReportStatus.APPROVED);
		verify(adminAuditLogService).record(
			eq(actorUserId),
			eq("HAZARD_REPORT_STATUS_UPDATE"),
			eq("HAZARD_REPORT"),
			eq("1"),
			eq(null),
			eq(null),
			eq("제보 승인 처리 reportId=1"),
			eq(ReportStatus.PENDING),
			eq(ReportStatus.APPROVED));
	}

	@Test
	@DisplayName("관리자 제보 반려는 PENDING 제보를 REJECTED로 변경한다")
	void rejectHazardReport() {
		UUID actorUserId = UUID.randomUUID();
		when(hazardReportRepository.updateStatusIfCurrentStatus(
			1L,
			ReportStatus.PENDING,
			ReportStatus.REJECTED,
			actorUserId,
			LocalDateTime.ofInstant(clock.instant(), clock.getZone())))
			.thenReturn(1);

		AdminHazardReportStatusResponse response = adminHazardReportService.rejectHazardReport(1L, actorUserId);

		assertThat(response.reportId()).isEqualTo(1L);
		assertThat(response.status()).isEqualTo(ReportStatus.REJECTED);
		verify(adminHazardRouteReviewService).clearInProgressRouteReview(1L);
	}

	@Test
	@DisplayName("이미 처리된 제보는 다시 승인할 수 없다")
	void rejectAlreadyProcessedReport() {
		HazardReport hazardReport = hazardReport(user(UUID.randomUUID()), 1L, List.of());
		hazardReport.approve(UUID.randomUUID(), LocalDateTime.of(2026, 5, 18, 12, 0));
		when(hazardReportRepository.updateStatusIfCurrentStatus(
			eq(1L),
			eq(ReportStatus.PENDING),
			eq(ReportStatus.APPROVED),
			any(),
			any()))
			.thenReturn(0);
		when(hazardReportRepository.findWithImagesAndUserByReportId(1L)).thenReturn(Optional.of(hazardReport));

		assertThatThrownBy(() -> adminHazardReportService.approveHazardReport(1L))
			.isInstanceOf(HazardReportException.class)
			.extracting("errorCode")
			.isEqualTo(HazardReportErrorCode.HAZARD_REPORT_ALREADY_PROCESSED);
	}

	@Test
	@DisplayName("존재하지 않는 제보는 승인할 수 없다")
	void rejectUnknownReportStatusUpdate() {
		when(hazardReportRepository.updateStatusIfCurrentStatus(
			eq(1L),
			eq(ReportStatus.PENDING),
			eq(ReportStatus.APPROVED),
			any(),
			any()))
			.thenReturn(0);
		when(hazardReportRepository.findWithImagesAndUserByReportId(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> adminHazardReportService.approveHazardReport(1L))
			.isInstanceOf(HazardReportException.class)
			.extracting("errorCode")
			.isEqualTo(HazardReportErrorCode.HAZARD_REPORT_NOT_FOUND);
	}

	@Test
	@DisplayName("반려된 제보는 기존 승인 API로 다시 승인할 수 없다")
	void approveRejectedHazardReport() {
		HazardReport hazardReport = hazardReport(user(UUID.randomUUID()), 1L, List.of());
		hazardReport.reject();
		when(hazardReportRepository.updateStatusIfCurrentStatus(
			eq(1L),
			eq(ReportStatus.PENDING),
			eq(ReportStatus.APPROVED),
			any(),
			any()))
			.thenReturn(0);
		when(hazardReportRepository.findWithImagesAndUserByReportId(1L)).thenReturn(Optional.of(hazardReport));

		assertThatThrownBy(() -> adminHazardReportService.approveHazardReport(1L, UUID.randomUUID()))
			.isInstanceOf(HazardReportException.class)
			.extracting("errorCode")
			.isEqualTo(HazardReportErrorCode.HAZARD_REPORT_ALREADY_PROCESSED);
		verify(adminAuditLogService, never()).record(any(), any(), any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("승인된 관리자 제보 삭제는 제보 row만 삭제하고 감사 로그를 남긴다")
	void deleteApprovedHazardReport() {
		UUID actorUserId = UUID.randomUUID();
		HazardReport hazardReport = hazardReport(user(UUID.randomUUID()), 1L, List.of("hazard-reports/user-1/image.jpg"));
		hazardReport.approve(actorUserId, LocalDateTime.of(2026, 5, 18, 12, 0));
		when(hazardReportRepository.findWithImagesAndUserByReportId(1L)).thenReturn(Optional.of(hazardReport));

		var response = adminHazardReportService.deleteHazardReport(1L, actorUserId);

		assertThat(response.reportId()).isEqualTo(1L);
		verify(hazardReportRepository).delete(hazardReport);
		verify(adminAuditLogService).record(
			eq(actorUserId),
			eq("HAZARD_REPORT_DELETE"),
			eq("HAZARD_REPORT"),
			eq("1"),
			eq(null),
			eq(null),
			eq("제보 삭제 처리 reportId=1"),
			any(),
			eq(null));
		verify(adminHazardRouteReviewService, never()).clearInProgressRouteReview(any());
	}

	@Test
	@DisplayName("대기 제보는 관리자 삭제 API로 삭제할 수 없다")
	void deletePendingHazardReport() {
		HazardReport hazardReport = hazardReport(user(UUID.randomUUID()), 1L, List.of());
		when(hazardReportRepository.findWithImagesAndUserByReportId(1L)).thenReturn(Optional.of(hazardReport));

		assertThatThrownBy(() -> adminHazardReportService.deleteHazardReport(1L, UUID.randomUUID()))
			.isInstanceOf(HazardReportException.class)
			.extracting("errorCode")
			.isEqualTo(HazardReportErrorCode.HAZARD_REPORT_ALREADY_PROCESSED);
		verify(hazardReportRepository, never()).delete(any());
	}

	@Test
	@DisplayName("반려된 관리자 제보 삭제는 제보 row를 삭제하고 감사 로그를 남긴다")
	void deleteRejectedHazardReport() {
		UUID actorUserId = UUID.randomUUID();
		HazardReport hazardReport = hazardReport(user(UUID.randomUUID()), 1L, List.of());
		hazardReport.reject(actorUserId, LocalDateTime.of(2026, 5, 18, 12, 0));
		when(hazardReportRepository.findWithImagesAndUserByReportId(1L)).thenReturn(Optional.of(hazardReport));

		var response = adminHazardReportService.deleteHazardReport(1L, actorUserId);

		assertThat(response.reportId()).isEqualTo(1L);
		verify(hazardReportRepository).delete(hazardReport);
		verify(adminAuditLogService).record(
			eq(actorUserId),
			eq("HAZARD_REPORT_DELETE"),
			eq("HAZARD_REPORT"),
			eq("1"),
			eq(null),
			eq(null),
			eq("제보 삭제 처리 reportId=1"),
			any(),
			eq(null));
	}

	private HazardReport hazardReport(User user, Long reportId, List<String> imageObjectKeys) {
		HazardReport hazardReport = HazardReport.create(
			user,
			ReportType.SIDEWALK_MISSING,
			"보행 가능한 인도가 없습니다.",
			"부산 부산진구 시민공원로 73",
			geoPointConverter.toPoint(new GeoPointRequest(35.1686, 129.0576)),
			imageObjectKeys);
		ReflectionTestUtils.setField(hazardReport, "reportId", reportId);
		ReflectionTestUtils.setField(hazardReport, "createdAt", LocalDateTime.of(2026, 5, 7, 22, 0));
		return hazardReport;
	}

	private User user(UUID userId) {
		User user = User.create(SocialProvider.KAKAO, "kakao-user-id", PrimaryUserType.LOW_VISION, null);
		ReflectionTestUtils.setField(user, "userId", userId);
		return user;
	}
}
