package com.ssafy.e102.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.ssafy.e102.domain.admin.dto.response.AdminRoutingApplyStatus;
import com.ssafy.e102.domain.admin.repository.AdminAreaRepository;
import com.ssafy.e102.domain.admin.service.AdminAuditLogService;
import com.ssafy.e102.domain.admin.service.AdminMapService;
import com.ssafy.e102.domain.report.dto.request.AdminHazardRouteReviewSegmentDraftRequest;
import com.ssafy.e102.domain.report.dto.request.StartHazardRouteReviewRequest;
import com.ssafy.e102.domain.report.dto.request.UpdateHazardRouteReviewRequest;
import com.ssafy.e102.domain.report.dto.response.AdminHazardRouteReviewResponse;
import com.ssafy.e102.domain.report.entity.HazardReport;
import com.ssafy.e102.domain.report.entity.HazardReportRouteReview;
import com.ssafy.e102.domain.report.exception.HazardReportErrorCode;
import com.ssafy.e102.domain.report.exception.HazardReportException;
import com.ssafy.e102.domain.report.repository.HazardReportRepository;
import com.ssafy.e102.domain.report.repository.HazardReportRouteReviewRepository;
import com.ssafy.e102.domain.report.type.HazardRouteReviewIntent;
import com.ssafy.e102.domain.report.type.HazardRouteReviewStage;
import com.ssafy.e102.domain.report.type.ReportStatus;
import com.ssafy.e102.domain.report.type.ReportType;
import com.ssafy.e102.domain.route.repository.RoadSegmentRepository;
import com.ssafy.e102.domain.route.type.AccessibilityState;
import com.ssafy.e102.domain.route.type.WidthState;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.SocialProvider;
import com.ssafy.e102.global.geo.GeoPointConverter;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

class AdminHazardRouteReviewServiceTest {

	private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-18T06:00:00Z"), ZoneOffset.UTC);

	@Mock
	private HazardReportRepository hazardReportRepository;

	@Mock
	private HazardReportRouteReviewRepository hazardReportRouteReviewRepository;

	@Mock
	private RoadSegmentRepository roadSegmentRepository;

	@Mock
	private AdminAuditLogService adminAuditLogService;

	@Mock
	private AdminMapService adminMapService;

	@Mock
	private AdminAreaRepository adminAreaRepository;

	private AdminHazardRouteReviewService adminHazardRouteReviewService;
	private GeoPointConverter geoPointConverter;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		geoPointConverter = new GeoPointConverter();
		adminHazardRouteReviewService = new AdminHazardRouteReviewService(
			hazardReportRepository,
			hazardReportRouteReviewRepository,
				roadSegmentRepository,
				adminAuditLogService,
				adminMapService,
				adminAreaRepository,
				new NoOpPlatformTransactionManager(),
				FIXED_CLOCK);
	}

	@Test
	@DisplayName("route review completion must not run inside the class-level read-only transaction")
	void completeRouteReviewSuspendsClassLevelTransaction() throws Exception {
		Transactional transactional = AdminHazardRouteReviewService.class
			.getMethod("completeRouteReview", UUID.class, Long.class)
			.getAnnotation(Transactional.class);

		assertThat(transactional).isNotNull();
		assertThat(transactional.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
	}

	@Test
	@DisplayName("반려된 제보도 승인 검수를 시작할 수 있다")
	void startApproveReviewForRejectedReport() {
		UUID userId = UUID.randomUUID();
		HazardReport hazardReport = rejectedHazardReport(1L);
		when(hazardReportRepository.findWithImagesAndUserByReportId(1L)).thenReturn(Optional.of(hazardReport));
		when(hazardReportRouteReviewRepository.findTopByHazardReport_ReportIdOrderByReviewIdDesc(1L))
			.thenReturn(Optional.empty());
		when(adminAreaRepository.findAreaByPoint(129.0576, 35.1686))
			.thenReturn(Optional.of(new Object[] {"부산진구", "부전동"}));
		when(hazardReportRouteReviewRepository.save(org.mockito.ArgumentMatchers.any(HazardReportRouteReview.class)))
			.thenAnswer(invocation -> {
				HazardReportRouteReview review = invocation.getArgument(0);
				ReflectionTestUtils.setField(review, "reviewId", 10L);
				return review;
			});

			AdminHazardRouteReviewResponse response = adminHazardRouteReviewService.startRouteReview(
				userId,
				1L,
				new StartHazardRouteReviewRequest(HazardRouteReviewIntent.APPROVE));

		assertThat(response.reviewId()).isEqualTo(10L);
			assertThat(response.intent()).isEqualTo(HazardRouteReviewIntent.APPROVE);
			assertThat(response.stage()).isEqualTo(HazardRouteReviewStage.IN_PROGRESS);
			assertThat(response.reportStatus()).isEqualTo(ReportStatus.REJECTED);
			assertThat(response.gu()).isEqualTo("부산진구");
			assertThat(response.dong()).isEqualTo("부전동");
	}

	@Test
	@DisplayName("승인된 제보는 원상복구 검수를 시작할 수 있다")
	void startRestoreReviewForApprovedReport() {
		UUID userId = UUID.randomUUID();
		HazardReport hazardReport = approvedHazardReport(1L);
		when(hazardReportRepository.findWithImagesAndUserByReportId(1L)).thenReturn(Optional.of(hazardReport));
		when(hazardReportRouteReviewRepository.findTopByHazardReport_ReportIdOrderByReviewIdDesc(1L))
			.thenReturn(Optional.empty());
		when(adminAreaRepository.findAreaByPoint(129.0576, 35.1686))
			.thenReturn(Optional.of(new Object[] {"부산진구", "부전동"}));
		when(hazardReportRouteReviewRepository.save(org.mockito.ArgumentMatchers.any(HazardReportRouteReview.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		AdminHazardRouteReviewResponse response = adminHazardRouteReviewService.startRouteReview(
			userId,
			1L,
			new StartHazardRouteReviewRequest(HazardRouteReviewIntent.RESTORE));

		assertThat(response.intent()).isEqualTo(HazardRouteReviewIntent.RESTORE);
		assertThat(response.reportStatus()).isEqualTo(ReportStatus.APPROVED);
	}

	@Test
	@DisplayName("행정구역 조회 결과가 중첩 Object 배열이어도 승인 검수를 시작할 수 있다")
	void startApproveReviewWithNestedAreaTuple() {
		UUID userId = UUID.randomUUID();
		HazardReport hazardReport = pendingHazardReport(1L);
		when(hazardReportRepository.findWithImagesAndUserByReportId(1L)).thenReturn(Optional.of(hazardReport));
		when(hazardReportRouteReviewRepository.findTopByHazardReport_ReportIdOrderByReviewIdDesc(1L))
			.thenReturn(Optional.empty());
		when(adminAreaRepository.findAreaByPoint(129.0576, 35.1686))
			.thenReturn(Optional.of(new Object[] {new Object[] {"부산진구", "부전동"}}));
		when(hazardReportRouteReviewRepository.save(org.mockito.ArgumentMatchers.any(HazardReportRouteReview.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		AdminHazardRouteReviewResponse response = adminHazardRouteReviewService.startRouteReview(
			userId,
			1L,
			new StartHazardRouteReviewRequest(HazardRouteReviewIntent.APPROVE));

		assertThat(response.gu()).isEqualTo("부산진구");
		assertThat(response.dong()).isEqualTo("부전동");
	}

	@Test
	@DisplayName("동시에 같은 제보 검수를 시작해 unique 제약이 나면 경로 검수 충돌로 변환한다")
	void startRouteReviewMapsUniqueConstraintConflict() {
		UUID userId = UUID.randomUUID();
		HazardReport hazardReport = pendingHazardReport(1L);
		when(hazardReportRepository.findWithImagesAndUserByReportId(1L)).thenReturn(Optional.of(hazardReport));
		when(hazardReportRouteReviewRepository.findTopByHazardReport_ReportIdOrderByReviewIdDesc(1L))
			.thenReturn(Optional.empty());
		when(adminAreaRepository.findAreaByPoint(129.0576, 35.1686))
			.thenReturn(Optional.of(new Object[] {"부산진구", "부전동"}));
		when(hazardReportRouteReviewRepository.save(org.mockito.ArgumentMatchers.any(HazardReportRouteReview.class)))
			.thenThrow(new DataIntegrityViolationException("uk_hazard_report_route_reviews_in_progress"));

			assertThatThrownBy(() -> adminHazardRouteReviewService.startRouteReview(
				userId,
				1L,
				new StartHazardRouteReviewRequest(HazardRouteReviewIntent.APPROVE)))
				.isInstanceOf(HazardReportException.class)
				.extracting("errorCode")
				.isEqualTo(HazardReportErrorCode.HAZARD_ROUTE_REVIEW_CONFLICT);
	}

	@Test
	@DisplayName("진행 중인 동일 검수는 다른 관리자도 이어받을 수 있다")
	void startRouteReviewAllowsTakeoverForSameIntent() {
		UUID firstReviewer = UUID.randomUUID();
		UUID nextReviewer = UUID.randomUUID();
		HazardReport hazardReport = pendingHazardReport(1L);
		HazardReportRouteReview review = HazardReportRouteReview.start(
			hazardReport,
			HazardRouteReviewIntent.APPROVE,
			firstReviewer,
			"부산진구",
			"부전동",
			LocalDateTime.now(FIXED_CLOCK));
		ReflectionTestUtils.setField(review, "reviewId", 44L);
		when(hazardReportRepository.findWithImagesAndUserByReportId(1L)).thenReturn(Optional.of(hazardReport));
		when(hazardReportRouteReviewRepository.findTopByHazardReport_ReportIdOrderByReviewIdDesc(1L))
			.thenReturn(Optional.of(review));

		AdminHazardRouteReviewResponse response = adminHazardRouteReviewService.startRouteReview(
			nextReviewer,
			1L,
			new StartHazardRouteReviewRequest(HazardRouteReviewIntent.APPROVE));

		assertThat(response.reviewId()).isEqualTo(44L);
		assertThat(response.reviewerUserId()).isEqualTo(nextReviewer);
		assertThat(review.getReviewerUserId()).isEqualTo(nextReviewer);
	}

	@Test
	@DisplayName("경로 검수 draft 저장은 선택 세그먼트와 속성을 보존한다")
	void updateRouteReview() {
		UUID userId = UUID.randomUUID();
		HazardReport hazardReport = pendingHazardReport(1L);
		HazardReportRouteReview review = HazardReportRouteReview.start(
			hazardReport,
			HazardRouteReviewIntent.APPROVE,
			userId,
			"부산진구",
			"부전동",
			LocalDateTime.now(FIXED_CLOCK));
		ReflectionTestUtils.setField(review, "reviewId", 11L);
		when(hazardReportRouteReviewRepository.findTopByHazardReport_ReportIdAndStageOrderByReviewIdDesc(
			1L,
			HazardRouteReviewStage.IN_PROGRESS)).thenReturn(Optional.of(review));
		when(roadSegmentRepository.existsIntersectingAreaByEdgeId(41231L, "부산진구", "부전동")).thenReturn(true);

		AdminHazardRouteReviewResponse response = adminHazardRouteReviewService.updateRouteReview(
			userId,
			1L,
			new UpdateHazardRouteReviewRequest(
				41231L,
				List.of(new AdminHazardRouteReviewSegmentDraftRequest(
					41231L,
					AccessibilityState.NO,
					AccessibilityState.UNKNOWN,
					AccessibilityState.UNKNOWN,
					WidthState.NARROW,
					null,
					AccessibilityState.YES,
					AccessibilityState.UNKNOWN))));

		assertThat(response.selectedSegmentEdgeId()).isEqualTo(41231L);
		assertThat(response.segmentDrafts()).hasSize(1);
		assertThat(response.segmentDrafts().get(0).walkAccess()).isEqualTo(AccessibilityState.NO);
		assertThat(response.segmentDrafts().get(0).stairsState()).isEqualTo(AccessibilityState.YES);
	}

	@Test
	@DisplayName("진행 중인 경로 검수 draft 저장은 다른 관리자도 이어받을 수 있다")
	void updateRouteReviewAllowsTakeover() {
		UUID firstReviewer = UUID.randomUUID();
		UUID nextReviewer = UUID.randomUUID();
		HazardReport hazardReport = pendingHazardReport(1L);
		HazardReportRouteReview review = HazardReportRouteReview.start(
			hazardReport,
			HazardRouteReviewIntent.APPROVE,
			firstReviewer,
			"부산진구",
			"부전동",
			LocalDateTime.now(FIXED_CLOCK));
		ReflectionTestUtils.setField(review, "reviewId", 12L);
		when(hazardReportRouteReviewRepository.findTopByHazardReport_ReportIdAndStageOrderByReviewIdDesc(
			1L,
			HazardRouteReviewStage.IN_PROGRESS)).thenReturn(Optional.of(review));
		when(roadSegmentRepository.existsIntersectingAreaByEdgeId(41231L, "부산진구", "부전동")).thenReturn(true);

		AdminHazardRouteReviewResponse response = adminHazardRouteReviewService.updateRouteReview(
			nextReviewer,
			1L,
			new UpdateHazardRouteReviewRequest(
				41231L,
				List.of(new AdminHazardRouteReviewSegmentDraftRequest(
					41231L,
					AccessibilityState.NO,
					AccessibilityState.UNKNOWN,
					AccessibilityState.UNKNOWN,
					WidthState.NARROW,
					null,
					AccessibilityState.YES,
					AccessibilityState.UNKNOWN))));

		assertThat(response.reviewerUserId()).isEqualTo(nextReviewer);
		assertThat(review.getReviewerUserId()).isEqualTo(nextReviewer);
	}

	@Test
	@DisplayName("승인 검수 완료는 제보 상태를 APPROVED로 변경한다")
	void completeApproveRouteReview() {
		UUID userId = UUID.randomUUID();
		HazardReport hazardReport = rejectedHazardReport(1L);
		HazardReportRouteReview review = inProgressReview(hazardReport, userId, HazardRouteReviewIntent.APPROVE);
		when(hazardReportRouteReviewRepository.findTopByHazardReport_ReportIdAndStageOrderByReviewIdDesc(
			1L,
			HazardRouteReviewStage.IN_PROGRESS)).thenReturn(Optional.of(review));
		when(adminMapService.applyRouteReviewSegmentDraftsInCurrentTransaction(
			org.mockito.ArgumentMatchers.eq(userId),
			org.mockito.ArgumentMatchers.eq(review.getGu()),
			org.mockito.ArgumentMatchers.eq(review.getDong()),
			org.mockito.ArgumentMatchers.same(review.getSegmentDrafts()))).thenReturn(true);
		AdminHazardRouteReviewResponse response = adminHazardRouteReviewService.completeRouteReview(userId, 1L);

		assertThat(response.stage()).isEqualTo(HazardRouteReviewStage.COMPLETED);
		assertThat(response.reportStatus()).isEqualTo(ReportStatus.APPROVED);
		assertThat(hazardReport.getStatus()).isEqualTo(ReportStatus.APPROVED);
		assertThat(response.routingApplyStatus()).isEqualTo(AdminRoutingApplyStatus.PENDING);
		assertThat(response.routingApplyMessage()).contains("DB");
		assertThat(review.getRoutingApplyStatus()).isEqualTo(AdminRoutingApplyStatus.PENDING);
		verify(adminMapService).applyRouteReviewSegmentDraftsInCurrentTransaction(
			org.mockito.ArgumentMatchers.eq(userId),
			org.mockito.ArgumentMatchers.eq(review.getGu()),
			org.mockito.ArgumentMatchers.eq(review.getDong()),
			org.mockito.ArgumentMatchers.same(review.getSegmentDrafts()));
	}

	@Test
	@DisplayName("진행 중인 승인 검수 완료는 다른 관리자도 이어받아 마무리할 수 있다")
	void completeApproveRouteReviewAllowsTakeover() {
		UUID firstReviewer = UUID.randomUUID();
		UUID nextReviewer = UUID.randomUUID();
		HazardReport hazardReport = rejectedHazardReport(1L);
		HazardReportRouteReview review = inProgressReview(hazardReport, firstReviewer, HazardRouteReviewIntent.APPROVE);
		when(hazardReportRouteReviewRepository.findTopByHazardReport_ReportIdAndStageOrderByReviewIdDesc(
			1L,
			HazardRouteReviewStage.IN_PROGRESS)).thenReturn(Optional.of(review));
		when(adminMapService.applyRouteReviewSegmentDraftsInCurrentTransaction(
			org.mockito.ArgumentMatchers.eq(nextReviewer),
			org.mockito.ArgumentMatchers.eq(review.getGu()),
			org.mockito.ArgumentMatchers.eq(review.getDong()),
			org.mockito.ArgumentMatchers.same(review.getSegmentDrafts()))).thenReturn(true);

		AdminHazardRouteReviewResponse response = adminHazardRouteReviewService.completeRouteReview(nextReviewer, 1L);

		assertThat(response.reviewerUserId()).isEqualTo(nextReviewer);
		assertThat(review.getReviewerUserId()).isEqualTo(nextReviewer);
		assertThat(hazardReport.getProcessedByUserId()).isEqualTo(nextReviewer);
		assertThat(response.routingApplyStatus()).isEqualTo(AdminRoutingApplyStatus.PENDING);
	}

	@Test
	@DisplayName("원상복구 검수 완료는 승인 상태를 유지하고 처리 시각만 남긴다")
	void completeRestoreRouteReview() {
		UUID userId = UUID.randomUUID();
		HazardReport hazardReport = approvedHazardReport(1L);
		HazardReportRouteReview review = inProgressReview(hazardReport, userId, HazardRouteReviewIntent.RESTORE);
		when(hazardReportRouteReviewRepository.findTopByHazardReport_ReportIdAndStageOrderByReviewIdDesc(
			1L,
			HazardRouteReviewStage.IN_PROGRESS)).thenReturn(Optional.of(review));
		when(adminMapService.applyRouteReviewSegmentDraftsInCurrentTransaction(
			org.mockito.ArgumentMatchers.eq(userId),
			org.mockito.ArgumentMatchers.eq(review.getGu()),
			org.mockito.ArgumentMatchers.eq(review.getDong()),
			org.mockito.ArgumentMatchers.same(review.getSegmentDrafts()))).thenReturn(true);
		AdminHazardRouteReviewResponse response = adminHazardRouteReviewService.completeRouteReview(userId, 1L);

		assertThat(response.stage()).isEqualTo(HazardRouteReviewStage.COMPLETED);
		assertThat(response.reportStatus()).isEqualTo(ReportStatus.APPROVED);
		assertThat(hazardReport.getProcessedByUserId()).isEqualTo(userId);
		assertThat(hazardReport.getStatus()).isEqualTo(ReportStatus.APPROVED);
		assertThat(response.routingApplyStatus()).isEqualTo(AdminRoutingApplyStatus.PENDING);
	}

	@Test
	@DisplayName("route review complete no longer reloads GraphHopper immediately")
	void completeRouteReviewDoesNotReloadImmediately() {
		UUID userId = UUID.randomUUID();
		HazardReport hazardReport = rejectedHazardReport(1L);
		HazardReportRouteReview review = inProgressReview(hazardReport, userId, HazardRouteReviewIntent.APPROVE);
		when(hazardReportRouteReviewRepository.findTopByHazardReport_ReportIdAndStageOrderByReviewIdDesc(
			1L,
			HazardRouteReviewStage.IN_PROGRESS)).thenReturn(Optional.of(review));
		when(adminMapService.applyRouteReviewSegmentDraftsInCurrentTransaction(
			org.mockito.ArgumentMatchers.eq(userId),
			org.mockito.ArgumentMatchers.eq(review.getGu()),
			org.mockito.ArgumentMatchers.eq(review.getDong()),
			org.mockito.ArgumentMatchers.same(review.getSegmentDrafts()))).thenReturn(true);
		AdminHazardRouteReviewResponse response = adminHazardRouteReviewService.completeRouteReview(userId, 1L);

		assertThat(response.stage()).isEqualTo(HazardRouteReviewStage.COMPLETED);
		assertThat(response.routingApplyStatus()).isEqualTo(AdminRoutingApplyStatus.PENDING);
	}

	@Test
	@DisplayName("route review complete returns SKIPPED when drafts have no routing overlay values")
	void completeRouteReviewReturnsSkippedWhenNoOverlayDraftIsApplied() {
		UUID userId = UUID.randomUUID();
		HazardReport hazardReport = rejectedHazardReport(1L);
		HazardReportRouteReview review = inProgressReviewWithoutOverlayValues(hazardReport, userId);
		when(hazardReportRouteReviewRepository.findTopByHazardReport_ReportIdAndStageOrderByReviewIdDesc(
			1L,
			HazardRouteReviewStage.IN_PROGRESS)).thenReturn(Optional.of(review));
		when(adminMapService.applyRouteReviewSegmentDraftsInCurrentTransaction(
			org.mockito.ArgumentMatchers.eq(userId),
			org.mockito.ArgumentMatchers.eq(review.getGu()),
			org.mockito.ArgumentMatchers.eq(review.getDong()),
			org.mockito.ArgumentMatchers.same(review.getSegmentDrafts()))).thenReturn(false);
		AdminHazardRouteReviewResponse response = adminHazardRouteReviewService.completeRouteReview(userId, 1L);

		assertThat(response.stage()).isEqualTo(HazardRouteReviewStage.COMPLETED);
		assertThat(response.routingApplyStatus()).isEqualTo(AdminRoutingApplyStatus.SKIPPED);
		assertThat(response.routingApplyMessage()).contains("없");
		assertThat(review.getRoutingApplyStatus()).isEqualTo(AdminRoutingApplyStatus.SKIPPED);
	}

	@Test
	@DisplayName("검수 범위를 벗어난 세그먼트는 저장할 수 없다")
	void rejectSegmentOutsideAssignedArea() {
		UUID userId = UUID.randomUUID();
		HazardReport hazardReport = pendingHazardReport(1L);
		HazardReportRouteReview review = HazardReportRouteReview.start(
			hazardReport,
			HazardRouteReviewIntent.APPROVE,
			userId,
			"부산진구",
			"부전동",
			LocalDateTime.now(FIXED_CLOCK));
		when(hazardReportRouteReviewRepository.findTopByHazardReport_ReportIdAndStageOrderByReviewIdDesc(
			1L,
			HazardRouteReviewStage.IN_PROGRESS)).thenReturn(Optional.of(review));
		when(roadSegmentRepository.existsIntersectingAreaByEdgeId(99999L, "부산진구", "부전동")).thenReturn(false);

		assertThatThrownBy(() -> adminHazardRouteReviewService.updateRouteReview(
			userId,
			1L,
			new UpdateHazardRouteReviewRequest(
				99999L,
				List.of(new AdminHazardRouteReviewSegmentDraftRequest(
					99999L,
					AccessibilityState.NO,
					null,
					null,
					WidthState.NARROW,
					null,
					AccessibilityState.YES,
					null)))))
			.isInstanceOf(HazardReportException.class)
			.extracting("errorCode")
			.isEqualTo(HazardReportErrorCode.INVALID_HAZARD_ROUTE_REVIEW_REQUEST);
	}

	private HazardReport pendingHazardReport(Long reportId) {
		return hazardReport(reportId, ReportStatus.PENDING);
	}

	private HazardReport approvedHazardReport(Long reportId) {
		HazardReport hazardReport = hazardReport(reportId, ReportStatus.PENDING);
		hazardReport.approve(UUID.randomUUID(), LocalDateTime.of(2026, 5, 18, 12, 0));
		return hazardReport;
	}

	private HazardReport rejectedHazardReport(Long reportId) {
		HazardReport hazardReport = hazardReport(reportId, ReportStatus.PENDING);
		hazardReport.reject(UUID.randomUUID(), LocalDateTime.of(2026, 5, 18, 11, 0));
		return hazardReport;
	}

	private HazardReportRouteReview inProgressReview(
		HazardReport hazardReport,
		UUID reviewerUserId,
		HazardRouteReviewIntent intent) {
		HazardReportRouteReview review = HazardReportRouteReview.start(
			hazardReport,
			intent,
			reviewerUserId,
			"부산진구",
			"부전동",
			LocalDateTime.now(FIXED_CLOCK));
		review.replaceSegmentDrafts(List.of(com.ssafy.e102.domain.report.entity.HazardReportRouteReviewSegmentDraft.create(
			41231L,
			AccessibilityState.NO,
			AccessibilityState.UNKNOWN,
			AccessibilityState.UNKNOWN,
			WidthState.NARROW,
			null,
			AccessibilityState.YES,
			AccessibilityState.UNKNOWN)));
		return review;
	}

	private HazardReportRouteReview inProgressReviewWithoutOverlayValues(
		HazardReport hazardReport,
		UUID reviewerUserId) {
		HazardReportRouteReview review = HazardReportRouteReview.start(
			hazardReport,
			HazardRouteReviewIntent.APPROVE,
			reviewerUserId,
			"gu",
			"dong",
			LocalDateTime.now(FIXED_CLOCK));
		review.replaceSegmentDrafts(List.of(com.ssafy.e102.domain.report.entity.HazardReportRouteReviewSegmentDraft.create(
			41231L,
			null,
			null,
			AccessibilityState.UNKNOWN,
			null,
			null,
			null,
			AccessibilityState.UNKNOWN)));
		return review;
	}

	private HazardReport hazardReport(Long reportId, ReportStatus status) {
		HazardReport hazardReport = HazardReport.create(
			user(UUID.randomUUID()),
			ReportType.SIDEWALK_MISSING,
			"보행 가능한 인도가 없습니다.",
			"부산 부산진구 시민공원로 73",
			geoPointConverter.toPoint(new GeoPointRequest(35.1686, 129.0576)),
			List.of());
		ReflectionTestUtils.setField(hazardReport, "reportId", reportId);
		if (status == ReportStatus.APPROVED) {
			hazardReport.approve();
		} else if (status == ReportStatus.REJECTED) {
			hazardReport.reject();
		}
		return hazardReport;
	}

	private User user(UUID userId) {
		User user = User.create(SocialProvider.KAKAO, "kakao-user-id", PrimaryUserType.LOW_VISION, null);
		ReflectionTestUtils.setField(user, "userId", userId);
		return user;
	}

	private static final class NoOpPlatformTransactionManager implements PlatformTransactionManager {

		@Override
		public TransactionStatus getTransaction(TransactionDefinition definition) {
			return new SimpleTransactionStatus();
		}

		@Override
		public void commit(TransactionStatus status) {
		}

		@Override
		public void rollback(TransactionStatus status) {
		}
	}
}
