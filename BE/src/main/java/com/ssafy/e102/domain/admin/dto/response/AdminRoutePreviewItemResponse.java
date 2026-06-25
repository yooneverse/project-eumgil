package com.ssafy.e102.domain.admin.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.ssafy.e102.global.geo.dto.GeoPointResponse;

public record AdminRoutePreviewItemResponse(
	String profile,
	BigDecimal distanceMeter,
	int durationSecond,
	int estimatedTimeMinute,
	List<GeoPointResponse> coordinates) {
}
