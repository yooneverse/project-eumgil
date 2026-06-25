package com.ssafy.e102.domain.report.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
import com.ssafy.e102.domain.report.entity.HazardReportRouteReviewSegmentDraft;
import com.ssafy.e102.domain.report.exception.HazardReportErrorCode;
import com.ssafy.e102.domain.report.exception.HazardReportException;
import com.ssafy.e102.domain.report.repository.HazardReportRepository;
import com.ssafy.e102.domain.report.repository.HazardReportRouteReviewRepository;
import com.ssafy.e102.domain.report.type.HazardRouteReviewIntent;
import com.ssafy.e102.domain.report.type.HazardRouteReviewStage;
import com.ssafy.e102.domain.report.type.ReportStatus;
import com.ssafy.e102.domain.route.repository.RoadSegmentRepository;

@Service
@Transactional(readOnly = true)
public class AdminHazardRouteReviewService {

	private final HazardReportRepository hazardReportRepository;
	private final HazardReportRouteReviewRepository hazardReportRouteReviewRepository;
	private final RoadSegmentRepository roadSegmentRepository;
	private final AdminAuditLogService adminAuditLogService;
	private final AdminMapService adminMapService;
	private final AdminAreaRepository adminAreaRepository;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	@Autowired
	public AdminHazardRouteReviewService(
		HazardReportRepository hazardReportRepository,
		HazardReportRouteReviewRepository hazardReportRouteReviewRepository,
		RoadSegmentRepository roadSegmentRepository,
		AdminAuditLogService adminAuditLogService,
		AdminMapService adminMapService,
		AdminAreaRepository adminAreaRepository,
		PlatformTransactionManager transactionManager,
		Clock clock) {
		this.hazardReportRepository = hazardReportRepository;
		this.hazardReportRouteReviewRepository = hazardReportRouteReviewRepository;
		this.roadSegmentRepository = roadSegmentRepository;
		this.adminAuditLogService = adminAuditLogService;
		this.adminMapService = adminMapService;
		this.adminAreaRepository = adminAreaRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	@Transactional
	public AdminHazardRouteReviewResponse startRouteReview(
		UUID userId,
		Long reportId,
		StartHazardRouteReviewRequest request) {
		HazardReport hazardReport = getHazardReport(reportId);
		HazardReportRouteReview latestReview = findLatestReview(reportId);
		validateStartReview(hazardReport, latestReview, request.intent());

		if (latestReview != null && latestReview.isInProgress() && latestReview.getIntent() == request.intent()) {
			latestReview.continueBy(userId);
			return AdminHazardRouteReviewResponse.from(latestReview, hazardReport.getStatus());
		}

		ResolvedReviewArea reviewArea = resolveReviewArea(hazardReport);
		LocalDateTime now = LocalDateTime.now(clock);
		HazardReportRouteReview review = HazardReportRouteReview.start(
			hazardReport,
			request.intent(),
			userId,
			reviewArea.gu(),
			reviewArea.dong(),
			now);
		HazardReportRouteReview savedReview = saveRouteReview(review);
		adminAuditLogService.record(
			userId,
			"HAZARD_REPORT_ROUTE_REVIEW_START",
			"HAZARD_REPORT",
			String.valueOf(reportId),
			reviewArea.gu(),
			reviewArea.dong(),
			"제보 경로 검수 시작 reportId=" + reportId + " intent=" + request.intent(),
			null,
			AdminHazardRouteReviewResponse.from(savedReview, hazardReport.getStatus()));
		return AdminHazardRouteReviewResponse.from(savedReview, hazardReport.getStatus());
	}

	@Transactional
	public AdminHazardRouteReviewResponse updateRouteReview(
		UUID userId,
		Long reportId,
		UpdateHazardRouteReviewRequest request) {
		HazardReportRouteReview review = getInProgressReview(reportId);
		review.continueBy(userId);
		AdminHazardRouteReviewResponse before = AdminHazardRouteReviewResponse.from(review, review.getHazardReport().getStatus());

		if (request.selectedSegmentEdgeId() != null) {
			validateEdgeInArea(request.selectedSegmentEdgeId(), review.getGu(), review.getDong());
			review.selectSegment(request.selectedSegmentEdgeId());
		}
		if (request.segmentDrafts() != null) {
			review.replaceSegmentDrafts(toSegmentDraftEntities(request.segmentDrafts(), review.getGu(), review.getDong()));
		}

		adminAuditLogService.record(
			userId,
			"HAZARD_REPORT_ROUTE_REVIEW_UPDATE",
			"HAZARD_REPORT",
			String.valueOf(reportId),
			review.getGu(),
			review.getDong(),
			"제보 경로 검수 draft 저장 reportId=" + reportId,
			before,
			AdminHazardRouteReviewResponse.from(review, review.getHazardReport().getStatus()));
		return AdminHazardRouteReviewResponse.from(review, review.getHazardReport().getStatus());
	}
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public AdminHazardRouteReviewResponse completeRouteReview(UUID userId, Long reportId) {
		RouteReviewCompletion completion = transactionTemplate.execute(transactionStatus -> {
			HazardReportRouteReview review = getInProgressReview(reportId);
			HazardReport hazardReport = review.getHazardReport();
			review.continueBy(userId);
			AdminHazardRouteReviewResponse before = AdminHazardRouteReviewResponse.from(review, hazardReport.getStatus());
			LocalDateTime now = LocalDateTime.now(clock);
			if (review.getIntent() == HazardRouteReviewIntent.RESTORE) {
				validateRestorable(hazardReport, review);
			}
			review.complete(now);
			boolean routingOverlayReloadRequired = adminMapService.applyRouteReviewSegmentDraftsInCurrentTransaction(
				userId,
				review.getGu(),
				review.getDong(),
				review.getSegmentDrafts());
			review.recordRoutingApplyStatus(
				routingOverlayReloadRequired ? AdminRoutingApplyStatus.PENDING : AdminRoutingApplyStatus.SKIPPED,
				routingOverlayReloadRequired
					? "DB 저장이 완료되었습니다. 경로 반영이 필요합니다."
					: "경로 반영 대상 변경이 없습니다.",
				null);

			if (review.getIntent() == HazardRouteReviewIntent.APPROVE) {
				hazardReport.approve(userId, now);
			} else {
				hazardReport.markProcessed(userId, now);
			}
			return new RouteReviewCompletion(
				reportId,
				review.getGu(),
				review.getDong(),
				before,
				AdminHazardRouteReviewResponse.from(review, hazardReport.getStatus()));
		});
		if (completion == null) {
			throw new HazardReportException(HazardReportErrorCode.HAZARD_ROUTE_REVIEW_NOT_FOUND);
		}
		AdminHazardRouteReviewResponse after = completion.after();

		adminAuditLogService.record(
			userId,
			"HAZARD_REPORT_ROUTE_REVIEW_COMPLETE",
			"HAZARD_REPORT",
			String.valueOf(reportId),
			completion.gu(),
			completion.dong(),
			"hazard route review complete reportId=" + reportId + " intent=" + completion.after().intent(),
			completion.before(),
			after);
		return after;
	}

	public AdminHazardRouteReviewResponse getLatestRouteReview(Long reportId, ReportStatus reportStatus) {
		return AdminHazardRouteReviewResponse.from(findLatestReview(reportId), reportStatus);
	}

	public Map<Long, AdminHazardRouteReviewResponse> getLatestRouteReviewsByReportIds(
		Collection<HazardReport> hazardReports) {
		List<Long> reportIds = hazardReports.stream()
			.map(HazardReport::getReportId)
			.toList();
		if (reportIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, ReportStatus> reportStatusById = hazardReports.stream()
			.collect(Collectors.toMap(HazardReport::getReportId, HazardReport::getStatus));
		return hazardReportRouteReviewRepository.findLatestByReportIds(reportIds)
			.stream()
			.collect(Collectors.toMap(
				review -> review.getHazardReport().getReportId(),
				review -> AdminHazardRouteReviewResponse.from(
					review,
					reportStatusById.get(review.getHazardReport().getReportId())),
				(existing, ignored) -> existing));
	}

	@Transactional
	public void clearInProgressRouteReview(Long reportId) {
		hazardReportRouteReviewRepository
			.findTopByHazardReport_ReportIdAndStageOrderByReviewIdDesc(reportId, HazardRouteReviewStage.IN_PROGRESS)
			.ifPresent(hazardReportRouteReviewRepository::delete);
	}

	private HazardReport getHazardReport(Long reportId) {
		return hazardReportRepository.findWithImagesAndUserByReportId(reportId)
			.orElseThrow(() -> new HazardReportException(HazardReportErrorCode.HAZARD_REPORT_NOT_FOUND));
	}

	private HazardReportRouteReview saveRouteReview(HazardReportRouteReview review) {
		try {
			return hazardReportRouteReviewRepository.save(review);
		} catch (DataIntegrityViolationException exception) {
			throw new HazardReportException(
				HazardReportErrorCode.HAZARD_ROUTE_REVIEW_CONFLICT,
				"이미 진행 중인 제보 경로 검수가 있어 새 검수를 시작할 수 없습니다.");
		}
	}

	private HazardReportRouteReview findLatestReview(Long reportId) {
		return hazardReportRouteReviewRepository.findTopByHazardReport_ReportIdOrderByReviewIdDesc(reportId)
			.orElse(null);
	}

	private HazardReportRouteReview getInProgressReview(Long reportId) {
		return hazardReportRouteReviewRepository
			.findTopByHazardReport_ReportIdAndStageOrderByReviewIdDesc(reportId, HazardRouteReviewStage.IN_PROGRESS)
			.orElseThrow(() -> new HazardReportException(
				HazardReportErrorCode.HAZARD_ROUTE_REVIEW_NOT_FOUND,
				"진행 중인 제보 경로 검수가 없습니다."));
	}

	private void validateStartReview(
		HazardReport hazardReport,
		HazardReportRouteReview latestReview,
		HazardRouteReviewIntent intent) {
		if (intent == HazardRouteReviewIntent.APPROVE) {
			validateApprovable(hazardReport, latestReview);
			return;
		}
		validateRestorable(hazardReport, latestReview);
	}

	private void validateApprovable(HazardReport hazardReport, HazardReportRouteReview latestReview) {
		if (latestReview != null && latestReview.isInProgress() && latestReview.getIntent() != HazardRouteReviewIntent.APPROVE) {
			throw new HazardReportException(
				HazardReportErrorCode.HAZARD_ROUTE_REVIEW_CONFLICT,
				"이미 다른 검수가 진행 중입니다.");
		}
		if (hazardReport.getStatus() != ReportStatus.PENDING && hazardReport.getStatus() != ReportStatus.REJECTED) {
			throw new HazardReportException(
				HazardReportErrorCode.HAZARD_ROUTE_REVIEW_CONFLICT,
				"현재 상태에서는 승인 검수를 시작할 수 없습니다.");
		}
	}

	private void validateRestorable(HazardReport hazardReport, HazardReportRouteReview latestReview) {
		if (latestReview != null && latestReview.isInProgress() && latestReview.getIntent() != HazardRouteReviewIntent.RESTORE) {
			throw new HazardReportException(
				HazardReportErrorCode.HAZARD_ROUTE_REVIEW_CONFLICT,
				"이미 다른 검수가 진행 중입니다.");
		}
		if (latestReview != null && latestReview.isCompleted() && latestReview.getIntent() == HazardRouteReviewIntent.RESTORE) {
			throw new HazardReportException(
				HazardReportErrorCode.HAZARD_ROUTE_REVIEW_CONFLICT,
				"이미 원상복구 검수가 완료된 제보입니다.");
		}
		if (hazardReport.getStatus() != ReportStatus.APPROVED) {
			throw new HazardReportException(
				HazardReportErrorCode.HAZARD_ROUTE_REVIEW_CONFLICT,
				"승인 완료된 제보만 원상복구 검수를 시작할 수 있습니다.");
		}
	}

	private List<HazardReportRouteReviewSegmentDraft> toSegmentDraftEntities(
		List<AdminHazardRouteReviewSegmentDraftRequest> requests,
		String gu,
		String dong) {
		return requests.stream()
			.map(request -> {
				validateEdgeInArea(request.edgeId(), gu, dong);
				return HazardReportRouteReviewSegmentDraft.create(
					request.edgeId(),
					request.walkAccess(),
					request.brailleBlockState(),
					request.audioSignalState(),
					request.widthState(),
					request.surfaceState(),
					request.stairsState(),
					request.signalState());
			})
			.toList();
	}

	private void validateEdgeInArea(Long edgeId, String gu, String dong) {
		if (!roadSegmentRepository.existsIntersectingAreaByEdgeId(edgeId, gu, dong)) {
			throw new HazardReportException(
				HazardReportErrorCode.INVALID_HAZARD_ROUTE_REVIEW_REQUEST,
				"검수 범위에 속한 세그먼트만 저장할 수 있습니다.");
		}
	}

	private ResolvedReviewArea resolveReviewArea(HazardReport hazardReport) {
		double lng = hazardReport.getReportPoint().getX();
		double lat = hazardReport.getReportPoint().getY();
		Object[] area = normalizeAreaRow(adminAreaRepository.findAreaByPoint(lng, lat)
			.orElseThrow(() -> new HazardReportException(
				HazardReportErrorCode.INVALID_HAZARD_ROUTE_REVIEW_REQUEST,
				"제보 위치에 대응하는 검수 행정구역을 찾을 수 없습니다.")));
		return new ResolvedReviewArea(readAreaField(area, 0, "검수 구"), readAreaField(area, 1, "검수 동"));
	}

	private Object[] normalizeAreaRow(Object area) {
		Object current = area;
		while (current instanceof Object[] array && array.length == 1 && array[0] instanceof Object[]) {
			current = array[0];
		}
		if (current instanceof Object[] array && array.length >= 2) {
			return array;
		}
		throw new HazardReportException(
			HazardReportErrorCode.INVALID_HAZARD_ROUTE_REVIEW_REQUEST,
			"제보 위치에 대응하는 검수 행정구역 형식이 올바르지 않습니다.");
	}

	private String readAreaField(Object[] area, int index, String label) {
		if (index >= area.length || !(area[index] instanceof String value) || value.isBlank()) {
			throw new HazardReportException(
				HazardReportErrorCode.INVALID_HAZARD_ROUTE_REVIEW_REQUEST,
				label + " 정보를 읽을 수 없습니다.");
		}
		return value;
	}

	private record RouteReviewCompletion(
		Long reportId,
		String gu,
		String dong,
		AdminHazardRouteReviewResponse before,
		AdminHazardRouteReviewResponse after) {
	}

	private record ResolvedReviewArea(String gu, String dong) {
	}
}
