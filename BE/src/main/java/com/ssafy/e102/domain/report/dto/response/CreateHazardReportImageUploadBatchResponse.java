package com.ssafy.e102.domain.report.dto.response;

import java.util.List;

public record CreateHazardReportImageUploadBatchResponse(
	List<CreateHazardReportImageUploadUrlResponse> uploads) {
}
