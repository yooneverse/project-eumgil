package com.ssafy.e102.global.external.graphhopper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * GraphHopper 응답에서 route 도메인이 실제로 쓰는 path 값만 추린 결과다.
 *
 * <p>GraphHopperRouteClient가 원본 응답을 이 값으로 정리하고, 이후 service는 원본 JSON 구조에 의존하지 않는다.
 */
public record GraphHopperRoutePath(
	BigDecimal distanceMeter,
	long timeMs,
	List<GraphHopperCoordinate> coordinates,
	Map<String, List<GraphHopperPathDetail>> details) {

	public GraphHopperRoutePath {
		coordinates = coordinates == null ? List.of() : List.copyOf(coordinates);
		details = details == null ? Map.of() : Map.copyOf(details);
	}
}
