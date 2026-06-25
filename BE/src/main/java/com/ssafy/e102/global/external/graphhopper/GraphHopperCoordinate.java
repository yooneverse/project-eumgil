package com.ssafy.e102.global.external.graphhopper;

import java.math.BigDecimal;
import java.util.List;

/**
 * GraphHopper geometry 좌표 하나를 보관한다.
 *
 * <p>외부 응답의 coordinates 배열은 [lng, lat] 순서이므로 API 요청 좌표의 lat/lng 순서와 섞이지 않게 분리한다.
 */
public record GraphHopperCoordinate(
	BigDecimal lng,
	BigDecimal lat) {

	static GraphHopperCoordinate from(List<BigDecimal> coordinate) {
		if (coordinate == null || coordinate.size() < 2) {
			throw new IllegalArgumentException("GraphHopper 좌표에는 경도와 위도가 포함되어야 합니다.");
		}
		// GraphHopper/GeoJSON geometry는 longitude가 먼저 온다.
		return new GraphHopperCoordinate(coordinate.get(0), coordinate.get(1));
	}
}
