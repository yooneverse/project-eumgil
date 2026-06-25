package com.ssafy.e102.domain.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 보행 네트워크 요약 응답")
public record AdminRoadNetworkSummaryResponse(
	@Schema(description = "전체 segment 개수", example = "100")
	long segmentCount,
	@Schema(description = "응답에 포함된 segment 개수", example = "100")
	int visibleSegmentCount,
	@Schema(description = "응답에 포함된 node 개수", example = "120")
	int nodeCount,
	@Schema(description = "응답에 포함된 node 개수", example = "120")
	int visibleNodeCount) {
}
