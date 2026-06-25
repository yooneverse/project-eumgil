package com.ssafy.e102.domain.report.dto.response;

import java.util.List;

import com.ssafy.e102.domain.report.type.ReportType;

public record HazardMarkerResponse(
	Long reportId,
	ReportType reportType,
	double lat,
	double lng,
	String description,
	List<String> thumbnailUrls,
	List<String> imageUrls) {
}
