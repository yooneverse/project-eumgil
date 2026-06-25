package com.ssafy.e102.domain.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 보행 네트워크 node 속성")
public record AdminRoadNodePropertiesResponse(
	@Schema(description = "node ID", example = "10")
	Long vertexId,
	@Schema(description = "원천 node key")
	String sourceNodeKey) {
}
