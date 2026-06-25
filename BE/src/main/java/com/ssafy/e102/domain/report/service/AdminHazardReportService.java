package com.ssafy.e102.domain.report.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.e102.domain.admin.service.AdminAuditLogService;
import com.ssafy.e102.domain.report.dto.response.AdminHazardReportDetailResponse;
import com.ssafy.e102.domain.report.dto.response.AdminHazardReportDeleteResponse;
import com.ssafy.e102.domain.report.dto.response.AdminHazardReportListResponse;
import com.ssafy.e102.domain.report.dto.response.AdminHazardReportStatusResponse;
import com.ssafy.e102.domain.report.dto.response.AdminHazardRouteReviewResponse;
import com.ssafy.e102.domain.report.entity.HazardReport;
import com.ssafy.e102.domain.report.entity.HazardReportImage;
import com.ssafy.e102.domain.report.exception.HazardReportErrorCode;
import com.ssafy.e102.domain.report.exception.HazardReportException;
import com.ssafy.e102.domain.report.repository.HazardReportImageRepository;
import com.ssafy.e102.domain.report.repository.HazardReportRepository;
import com.ssafy.e102.domain.report.type.ReportStatus;
import com.ssafy.e102.global.geo.GeoPointConverter;

@Service
@Transactional(readOnly = true)
public class AdminHazardReportService {

	private static final short REPRESENTATIVE_IMAGE_ORDER = 0;
	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "reportId");

	private final HazardReportRepository hazardReportRepository;
	private final HazardReportImageRepository hazardReportImageRepository;
	private final GeoPointConverter geoPointConverter;
	private final HazardReportImageUploadService hazardReportImageUploadService;
	private final AdminHazardRouteReviewService adminHazardRouteReviewService;
	private final AdminAuditLogService adminAuditLogService;
	private final Clock clock;

	public AdminHazardReportService(
		HazardReportRepository hazardReportRepository,
		HazardReportImageRepository hazardReportImageRepository,
		GeoPointConverter geoPointConverter,
		HazardReportImageUploadService hazardReportImageUploadService,
		AdminHazardRouteReviewService adminHazardRouteReviewService,
		AdminAuditLogService adminAuditLogService,
		Clock clock) {
		this.hazardReportRepository = hazardReportRepository;
		this.hazardReportImageRepository = hazardReportImageRepository;
		this.geoPointConverter = geoPointConverter;
		this.hazardReportImageUploadService = hazardReportImageUploadService;
		this.adminHazardRouteReviewService = adminHazardRouteReviewService;
		this.adminAuditLogService = adminAuditLogService;
		this.clock = clock;
	}

	public AdminHazardReportListResponse getHazardReports(
		ReportStatus status,
		Long cursor,
		int size) {
		PageRequest pageRequest = PageRequest.of(0, size, NEWEST_FIRST);
		Slice<HazardReport> hazardReports = findHazardReports(status, cursor, pageRequest);
		Map<Long, AdminHazardRouteReviewResponse> latestRouteReviews =
			adminHazardRouteReviewService.getLatestRouteReviewsByReportIds(hazardReports.getContent());
		return AdminHazardReportListResponse.of(
			hazardReports.getContent(),
			size,
			hazardReports.hasNext(),
			getRepresentativeImageUrls(hazardReports.getContent()),
			geoPointConverter,
			latestRouteReviews == null ? Map.of() : latestRouteReviews);
	}

	public AdminHazardReportDetailResponse getHazardReportDetail(Long reportId) {
		HazardReport hazardReport = getHazardReport(reportId);
		return AdminHazardReportDetailResponse.of(
			hazardReport,
			geoPointConverter,
			createImageReadUrls(hazardReport),
			adminHazardRouteReviewService.getLatestRouteReview(reportId, hazardReport.getStatus()));
	}

	@Transactional
	public AdminHazardReportStatusResponse approveHazardReport(Long reportId) {
		return approveHazardReport(reportId, null);
	}

	@Transactional
	public AdminHazardReportStatusResponse approveHazardReport(Long reportId, UUID actorUserId) {
		LocalDateTime now = LocalDateTime.now(clock);
		updateHazardReportStatus(reportId, ReportStatus.PENDING, ReportStatus.APPROVED, actorUserId, now);
		adminAuditLogService.record(
			actorUserId,
			"HAZARD_REPORT_STATUS_UPDATE",
			"HAZARD_REPORT",
			String.valueOf(reportId),
			null,
			null,
			"제보 승인 처리 reportId=" + reportId,
			ReportStatus.PENDING,
			ReportStatus.APPROVED);
		return new AdminHazardReportStatusResponse(reportId, ReportStatus.APPROVED);
	}

	@Transactional
	public AdminHazardReportStatusResponse rejectHazardReport(Long reportId) {
		return rejectHazardReport(reportId, null);
	}

	@Transactional
	public AdminHazardReportStatusResponse rejectHazardReport(Long reportId, UUID actorUserId) {
		LocalDateTime now = LocalDateTime.now(clock);
		updateHazardReportStatus(reportId, ReportStatus.PENDING, ReportStatus.REJECTED, actorUserId, now);
		adminHazardRouteReviewService.clearInProgressRouteReview(reportId);
		adminAuditLogService.record(
			actorUserId,
			"HAZARD_REPORT_STATUS_UPDATE",
			"HAZARD_REPORT",
			String.valueOf(reportId),
			null,
			null,
			"제보 반려 처리 reportId=" + reportId,
			ReportStatus.PENDING,
			ReportStatus.REJECTED);
		return new AdminHazardReportStatusResponse(reportId, ReportStatus.REJECTED);
	}

	@Transactional
	public AdminHazardReportDeleteResponse deleteHazardReport(Long reportId, UUID actorUserId) {
		HazardReport hazardReport = getHazardReport(reportId);
		if (hazardReport.getStatus() != ReportStatus.APPROVED
			&& hazardReport.getStatus() != ReportStatus.REJECTED) {
			throw new HazardReportException(
				HazardReportErrorCode.HAZARD_REPORT_ALREADY_PROCESSED,
				"승인 완료 또는 반려된 제보만 삭제할 수 있습니다.");
		}
		AdminHazardReportStatusResponse before = AdminHazardReportStatusResponse.from(hazardReport);
		hazardReportRepository.delete(hazardReport);
		adminAuditLogService.record(
			actorUserId,
			"HAZARD_REPORT_DELETE",
			"HAZARD_REPORT",
			String.valueOf(reportId),
			null,
			null,
			"제보 삭제 처리 reportId=" + reportId,
			before,
			null);
		return new AdminHazardReportDeleteResponse(reportId);
	}

	private void updateHazardReportStatus(
		Long reportId,
		ReportStatus currentStatus,
		ReportStatus nextStatus,
		UUID actorUserId,
		LocalDateTime processedAt) {
		int updated = hazardReportRepository.updateStatusIfCurrentStatus(
			reportId,
			currentStatus,
			nextStatus,
			actorUserId,
			processedAt);
		if (updated > 0) {
			return;
		}

		HazardReport hazardReport = getHazardReport(reportId);
		throw new HazardReportException(
			HazardReportErrorCode.HAZARD_REPORT_ALREADY_PROCESSED,
			"현재 상태(" + hazardReport.getStatus() + ")에서는 해당 제보를 처리할 수 없습니다.");
	}

	private Slice<HazardReport> findHazardReports(ReportStatus status, Long cursor, PageRequest pageRequest) {
		if (status == null && cursor == null) {
			return hazardReportRepository.findAllForAdmin(pageRequest);
		}
		if (status == null) {
			return hazardReportRepository.findAllByReportIdLessThanForAdmin(cursor, pageRequest);
		}
		if (cursor == null) {
			return hazardReportRepository.findAllByStatusForAdmin(status, pageRequest);
		}
		return hazardReportRepository.findAllByStatusAndReportIdLessThanForAdmin(status, cursor, pageRequest);
	}

	private Map<Long, String> getRepresentativeImageUrls(List<HazardReport> hazardReports) {
		List<Long> reportIds = hazardReports.stream()
			.map(HazardReport::getReportId)
			.toList();
		if (reportIds.isEmpty()) {
			return Map.of();
		}
		return hazardReportImageRepository
			.findAllByHazardReport_ReportIdInAndDisplayOrder(reportIds, REPRESENTATIVE_IMAGE_ORDER)
			.stream()
			.collect(Collectors.toMap(
				image -> image.getHazardReport().getReportId(),
				image -> hazardReportImageUploadService.createReadUrl(image.getImageObjectKey()),
				(existing, ignored) -> existing));
	}

	private List<String> createImageReadUrls(HazardReport hazardReport) {
		return hazardReport.getImages()
			.stream()
			.map(HazardReportImage::getImageObjectKey)
			.map(hazardReportImageUploadService::createReadUrl)
			.toList();
	}

	private HazardReport getHazardReport(Long reportId) {
		return hazardReportRepository.findWithImagesAndUserByReportId(reportId)
			.orElseThrow(() -> new HazardReportException(HazardReportErrorCode.HAZARD_REPORT_NOT_FOUND));
	}
}
