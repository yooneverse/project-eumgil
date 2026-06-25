package com.ssafy.e102.global.external.graphhopper;

import java.math.BigDecimal;
import java.util.List;

/**
 * GraphHopper points 객체 중 geometry type과 좌표 배열을 받는 외부 DTO다.
 *
 * <p>GraphHopperRouteClient가 path를 정제할 때만 사용하고, route API 응답 DTO와는 분리한다.
 */
public record GraphHopperPointsResponse(
	String type,
	List<List<BigDecimal>> coordinates) {
}
