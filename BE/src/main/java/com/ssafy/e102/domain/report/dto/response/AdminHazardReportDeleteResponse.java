package com.ssafy.e102.domain.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 제보 삭제 응답")
public record AdminHazardReportDeleteResponse(
	@Schema(description = "삭제된 제보 ID", example = "1")
	Long reportId) {
}
