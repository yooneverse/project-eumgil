package com.ssafy.e102.domain.report.dto.response;

import java.util.List;
import java.util.Map;

import com.ssafy.e102.domain.report.entity.HazardReport;
import com.ssafy.e102.global.geo.GeoPointConverter;

public record HazardReportListResponse(
	List<HazardReportSummaryResponse> content,
	int size,
	Long nextCursor,
	boolean hasNext) {

	public static HazardReportListResponse of(
		List<HazardReport> hazardReports,
		int size,
		boolean hasNext,
		Map<Long, String> representativeImageUrls,
		GeoPointConverter geoPointConverter) {
		List<HazardReportSummaryResponse> responses = hazardReports.stream()
			.map(hazardReport -> HazardReportSummaryResponse.of(
				hazardReport,
				representativeImageUrls.get(hazardReport.getReportId()),
				geoPointConverter))
			.toList();
		Long nextCursor = hasNext && !hazardReports.isEmpty()
			? hazardReports.getLast().getReportId()
			: null;

		return new HazardReportListResponse(
			responses,
			size,
			nextCursor,
			hasNext);
	}
}
