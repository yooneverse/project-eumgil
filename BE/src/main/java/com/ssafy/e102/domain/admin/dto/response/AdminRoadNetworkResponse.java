package com.ssafy.e102.domain.admin.dto.response;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 보행 네트워크 조회 응답")
public record AdminRoadNetworkResponse(
	@Schema(description = "요약 정보")
	AdminRoadNetworkSummaryResponse summary,
	@Schema(description = "조회 결과 bbox. [minLng, minLat, maxLng, maxLat]")
	List<Double> bbox,
	@Schema(description = "보행 네트워크 segment GeoJSON")
	AdminGeoJsonFeatureCollectionResponse<AdminGeoJsonFeatureResponse<AdminLineStringGeometryResponse, AdminRoadSegmentPropertiesResponse>> segments,
	@Schema(description = "보행 네트워크 node GeoJSON")
	AdminGeoJsonFeatureCollectionResponse<AdminGeoJsonFeatureResponse<AdminPointGeometryResponse, AdminRoadNodePropertiesResponse>> roadNodes,
	@Schema(description = "선택 구 경계 GeoJSON Feature. 편집 금지 영역이 아니라 참고선입니다.")
	AdminGeoJsonFeatureResponse<JsonNode, AdminAreaBoundaryPropertiesResponse> areaBoundary) {
}
