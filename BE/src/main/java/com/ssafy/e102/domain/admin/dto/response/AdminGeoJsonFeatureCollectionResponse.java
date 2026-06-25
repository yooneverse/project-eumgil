package com.ssafy.e102.domain.admin.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 GeoJSON FeatureCollection 응답")
public record AdminGeoJsonFeatureCollectionResponse<T>(
	@Schema(description = "GeoJSON 타입", example = "FeatureCollection")
	String type,
	@Schema(description = "GeoJSON feature 목록")
	List<T> features) {

	public static <T> AdminGeoJsonFeatureCollectionResponse<T> of(List<T> features) {
		return new AdminGeoJsonFeatureCollectionResponse<>("FeatureCollection", features);
	}
}
