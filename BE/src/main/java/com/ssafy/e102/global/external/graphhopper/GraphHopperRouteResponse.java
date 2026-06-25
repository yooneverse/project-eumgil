package com.ssafy.e102.global.external.graphhopper;

import java.util.List;

/**
 * GraphHopper `/route` 응답의 최상위 paths 배열만 받는 외부 응답 DTO다.
 *
 * <p>이 DTO는 global.external 경계 안에서만 사용하고 domain/route 응답으로 직접 노출하지 않는다.
 */
public record GraphHopperRouteResponse(
	List<GraphHopperPathResponse> paths) {
}
