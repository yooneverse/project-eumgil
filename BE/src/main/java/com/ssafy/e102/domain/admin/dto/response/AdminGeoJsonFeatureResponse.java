package com.ssafy.e102.domain.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 GeoJSON Feature 응답")
public record AdminGeoJsonFeatureResponse<G, P>(
	@Schema(description = "GeoJSON 타입", example = "Feature")
	String type,
	@Schema(description = "Feature geometry")
	G geometry,
	@Schema(description = "Feature properties")
	P properties) {

	public static <G, P> AdminGeoJsonFeatureResponse<G, P> of(G geometry, P properties) {
		return new AdminGeoJsonFeatureResponse<>("Feature", geometry, properties);
	}
}
