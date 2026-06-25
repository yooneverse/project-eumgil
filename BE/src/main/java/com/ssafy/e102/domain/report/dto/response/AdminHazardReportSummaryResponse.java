package com.ssafy.e102.domain.report.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ssafy.e102.domain.report.entity.HazardReport;
import com.ssafy.e102.domain.report.type.ReportStatus;
import com.ssafy.e102.domain.report.type.ReportType;
import com.ssafy.e102.global.geo.GeoPointConverter;
import com.ssafy.e102.global.geo.dto.GeoPointResponse;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 제보 목록 요약 응답")
public record AdminHazardReportSummaryResponse(
	@Schema(description = "제보 ID", example = "1")
	Long reportId,
	@Schema(description = "제보자 사용자 ID", example = "7dafc215-b297-4f6c-bd7f-bc77fbb421a2")
	UUID reporterUserId,
	@Schema(description = "제보 유형", example = "SIDEWALK_MISSING")
	ReportType reportType,
	@Schema(description = "제보 주소. 역지오코딩 실패 시 null")
	String address,
	@Schema(description = "제보 설명 preview. 80자 초과 시 ... suffix 포함")
	String description,
	@Schema(description = "제보 좌표")
	GeoPointResponse reportPoint,
	@Schema(description = "처리 상태", example = "PENDING")
	ReportStatus status,
	@Schema(description = "등록 일시", example = "2026-05-07T22:00:00")
	LocalDateTime createdAt,
	@Schema(description = "대표 첨부 이미지 URL. 사진이 없으면 null")
	String representativeImageUrl,
	@Schema(description = "최신 경로 검수 정보")
	AdminHazardRouteReviewResponse latestRouteReview) {

	private static final int DESCRIPTION_PREVIEW_LENGTH = 80;
	private static final String DESCRIPTION_PREVIEW_SUFFIX = "...";

	public static AdminHazardReportSummaryResponse of(
		HazardReport hazardReport,
		String representativeImageUrl,
		GeoPointConverter geoPointConverter,
		AdminHazardRouteReviewResponse latestRouteReview) {
		return new AdminHazardReportSummaryResponse(
			hazardReport.getReportId(),
			hazardReport.getUser().getUserId(),
			hazardReport.getReportType(),
			hazardReport.getAddress(),
			toDescriptionPreview(hazardReport.getDescription()),
			geoPointConverter.toResponse(hazardReport.getReportPoint()),
			hazardReport.getStatus(),
			hazardReport.getCreatedAt(),
			representativeImageUrl,
			latestRouteReview);
	}

	private static String toDescriptionPreview(String description) {
		if (description == null || description.length() <= DESCRIPTION_PREVIEW_LENGTH) {
			return description;
		}
		return description.substring(0, DESCRIPTION_PREVIEW_LENGTH) + DESCRIPTION_PREVIEW_SUFFIX;
	}
}
