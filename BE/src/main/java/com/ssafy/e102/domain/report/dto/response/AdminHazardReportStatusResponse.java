package com.ssafy.e102.domain.report.dto.response;

import com.ssafy.e102.domain.report.entity.HazardReport;
import com.ssafy.e102.domain.report.type.ReportStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 제보 처리 상태 응답")
public record AdminHazardReportStatusResponse(
	@Schema(description = "제보 ID", example = "1")
	Long reportId,
	@Schema(description = "변경 후 처리 상태", example = "APPROVED")
	ReportStatus status) {

	public static AdminHazardReportStatusResponse from(HazardReport hazardReport) {
		return new AdminHazardReportStatusResponse(
			hazardReport.getReportId(),
			hazardReport.getStatus());
	}
}
