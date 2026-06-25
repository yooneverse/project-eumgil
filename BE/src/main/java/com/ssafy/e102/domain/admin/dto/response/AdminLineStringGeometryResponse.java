package com.ssafy.e102.domain.admin.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "GeoJSON LineString geometry")
public record AdminLineStringGeometryResponse(
	@Schema(description = "GeoJSON geometry 타입", example = "LineString")
	String type,
	@Schema(description = "경도/위도 좌표 목록")
	List<List<Double>> coordinates) {

	public static AdminLineStringGeometryResponse of(List<List<Double>> coordinates) {
		return new AdminLineStringGeometryResponse("LineString", coordinates);
	}
}
