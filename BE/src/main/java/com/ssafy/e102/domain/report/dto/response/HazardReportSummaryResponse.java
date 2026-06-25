package com.ssafy.e102.domain.report.dto.response;

import java.time.LocalDateTime;

import com.ssafy.e102.domain.report.entity.HazardReport;
import com.ssafy.e102.domain.report.type.ReportStatus;
import com.ssafy.e102.domain.report.type.ReportType;
import com.ssafy.e102.global.geo.GeoPointConverter;
import com.ssafy.e102.global.geo.dto.GeoPointResponse;

public record HazardReportSummaryResponse(
	Long reportId,
	ReportType reportType,
	ReportStatus status,
	String address,
	String description,
	GeoPointResponse reportPoint,
	LocalDateTime createdAt,
	String representativeImageUrl) {

	private static final int DESCRIPTION_PREVIEW_LENGTH = 80;
	private static final String DESCRIPTION_PREVIEW_SUFFIX = "...";

	public static HazardReportSummaryResponse of(
		HazardReport hazardReport,
		String representativeImageUrl,
		GeoPointConverter geoPointConverter) {
		return new HazardReportSummaryResponse(
			hazardReport.getReportId(),
			hazardReport.getReportType(),
			hazardReport.getStatus(),
			hazardReport.getAddress(),
			toDescriptionPreview(hazardReport.getDescription()),
			geoPointConverter.toResponse(hazardReport.getReportPoint()),
			hazardReport.getCreatedAt(),
			representativeImageUrl);
	}

	private static String toDescriptionPreview(String description) {
		if (description == null || description.length() <= DESCRIPTION_PREVIEW_LENGTH) {
			return description;
		}
		return description.substring(0, DESCRIPTION_PREVIEW_LENGTH) + DESCRIPTION_PREVIEW_SUFFIX;
	}
}
