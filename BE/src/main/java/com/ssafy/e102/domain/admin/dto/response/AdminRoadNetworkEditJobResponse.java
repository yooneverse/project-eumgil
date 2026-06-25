package com.ssafy.e102.domain.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ssafy.e102.domain.admin.type.AdminRoadNetworkEditJobStatus;

public record AdminRoadNetworkEditJobResponse(
	Long jobId,
	AdminRoadNetworkEditJobStatus status,
	int totalEdits,
	int processedEdits,
	String message,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	AdminRoadNetworkEditApplyResponse result) {
}
