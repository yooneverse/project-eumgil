package com.ssafy.e102.domain.admin.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 보행 네트워크 연결 후보 응답")
public record AdminRoadNetworkBridgePayloadResponse(
	@Schema(description = "연결 후보 요약")
	AdminRoadNetworkBridgeSummaryResponse summary,
	@Schema(description = "연결 후보 전체 bbox")
	List<Double> bbox,
	@Schema(description = "연결 후보 GeoJSON")
	AdminGeoJsonFeatureCollectionResponse<AdminGeoJsonFeatureResponse<AdminLineStringGeometryResponse, AdminRoadNetworkBridgePropertiesResponse>> bridges) {
}
