package com.ssafy.e102.domain.admin.dto.response;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 편의시설 지도 조회 응답")
public record AdminFacilityPayloadResponse(
	@Schema(description = "요약 정보")
	AdminFacilitySummaryResponse summary,
	@Schema(description = "조회 결과 bbox. [minLng, minLat, maxLng, maxLat]")
	List<Double> bbox,
	@Schema(description = "편의시설 GeoJSON")
	AdminGeoJsonFeatureCollectionResponse<AdminGeoJsonFeatureResponse<AdminPointGeometryResponse, AdminFacilityPropertiesResponse>> facilities,
	@Schema(description = "선택 구 경계 GeoJSON Feature. 편집 금지 영역이 아니라 참고선입니다.")
	AdminGeoJsonFeatureResponse<JsonNode, AdminAreaBoundaryPropertiesResponse> areaBoundary) {

	@Schema(description = "관리자 편의시설 요약 응답")
	public record AdminFacilitySummaryResponse(
		@Schema(description = "응답 편의시설 개수", example = "100")
		int facilityCount,
		@Schema(description = "지도에 표시할 편의시설 개수", example = "100")
		int visibleFacilityCount,
		@Schema(description = "providerPlaceId 보유 개수", example = "10")
		long providerPlaceIdCount,
		@Schema(description = "카테고리별 개수")
		Map<String, Long> categoryCounts,
		@Schema(description = "표시 카테고리별 개수")
		Map<String, Long> visibleCategoryCounts) {
	}
}
