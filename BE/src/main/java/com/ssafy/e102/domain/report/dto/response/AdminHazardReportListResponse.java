package com.ssafy.e102.domain.report.dto.response;

import java.util.List;
import java.util.Map;

import com.ssafy.e102.domain.report.entity.HazardReport;
import com.ssafy.e102.global.geo.GeoPointConverter;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 제보 목록 응답")
public record AdminHazardReportListResponse(
	@Schema(description = "제보 목록")
	List<AdminHazardReportSummaryResponse> content,
	@Schema(description = "요청한 조회 개수", example = "10")
	int size,
	@Schema(description = "다음 조회에 사용할 cursor. 다음 데이터가 없으면 null", example = "15")
	Long nextCursor,
	@Schema(description = "다음 데이터 존재 여부", example = "true")
	boolean hasNext) {

	public static AdminHazardReportListResponse of(
		List<HazardReport> hazardReports,
		int size,
		boolean hasNext,
		Map<Long, String> representativeImageUrls,
		GeoPointConverter geoPointConverter,
		Map<Long, AdminHazardRouteReviewResponse> latestRouteReviews) {
		List<AdminHazardReportSummaryResponse> responses = hazardReports.stream()
			.map(hazardReport -> AdminHazardReportSummaryResponse.of(
				hazardReport,
				representativeImageUrls.get(hazardReport.getReportId()),
				geoPointConverter,
				latestRouteReviews.get(hazardReport.getReportId())))
			.toList();
		Long nextCursor = hasNext && !hazardReports.isEmpty()
			? hazardReports.getLast().getReportId()
			: null;

		return new AdminHazardReportListResponse(
			responses,
			size,
			nextCursor,
			hasNext);
	}
}
