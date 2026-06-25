package com.ssafy.e102.domain.route.service;

import com.ssafy.e102.domain.route.type.RouteOption;
import com.ssafy.e102.domain.route.type.WalkRouteProfile;
import com.ssafy.e102.global.external.graphhopper.GraphHopperRoutePath;

/**
 * GraphHopper에서 조회한 도보 후보 하나를 routeOption, profile, path로 묶는다.
 *
 * <p>다음 단계의 route payload 매핑 service가 이 값을 받아 routeId, geometry, leg, step 응답으로 확장한다.
 */
public record WalkRouteCandidate(
	RouteOption routeOption,
	WalkRouteProfile profile,
	GraphHopperRoutePath path) {
}
