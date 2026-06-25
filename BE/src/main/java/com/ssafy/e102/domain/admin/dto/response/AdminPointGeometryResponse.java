package com.ssafy.e102.domain.admin.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "GeoJSON Point geometry")
public record AdminPointGeometryResponse(
	@Schema(description = "GeoJSON geometry 타입", example = "Point")
	String type,
	@Schema(description = "경도/위도 좌표")
	List<Double> coordinates) {

	public static AdminPointGeometryResponse of(double lng, double lat) {
		return new AdminPointGeometryResponse("Point", List.of(lng, lat));
	}
}
