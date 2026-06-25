package com.ssafy.e102.global.external.graphhopper;

import com.ssafy.e102.domain.route.type.WalkRouteProfile;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

/**
 * route service가 GraphHopperRouteClient에 넘기는 내부 요청 값이다.
 *
 * <p>API request DTO와 외부 GraphHopper query 사이의 경계 값으로, 좌표와 확정된 profile만 보관한다.
 */
public record GraphHopperRouteRequest(
	GeoPointRequest startPoint,
	GeoPointRequest endPoint,
	WalkRouteProfile profile,
	boolean enforceSnapDistanceLimit) {

	public GraphHopperRouteRequest(
		GeoPointRequest startPoint,
		GeoPointRequest endPoint,
		WalkRouteProfile profile) {
		this(startPoint, endPoint, profile, true);
	}
}
